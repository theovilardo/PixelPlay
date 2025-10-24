package com.theveloper.pixelplay.data.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.PixelPlayApplication
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlayerInfo // Import new data class
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.ui.glancewidget.PixelPlayGlanceWidget
import com.theveloper.pixelplay.ui.glancewidget.PlayerActions
import com.theveloper.pixelplay.ui.glancewidget.PlayerInfoStateDefinition
import com.theveloper.pixelplay.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.service.player.TransitionController
import javax.inject.Inject

// Acciones personalizadas para compatibilidad con el widget existente


@UnstableApi
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject
    lateinit var engine: DualPlayerEngine
    @Inject
    lateinit var controller: TransitionController
    @Inject
    lateinit var musicRepository: MusicRepository
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private var favoriteSongIds = emptySet<String>()
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "MusicService_PixelPlay"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()

        engine.masterPlayer.addListener(playerListener)
        controller.initialize()

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val connectionResult = super.onConnect(session, controller)
                if (!connectionResult.isAccepted) {
                    return connectionResult
                }

                val sessionCommandsBuilder = connectionResult.availableSessionCommands.buildUpon()
                    .add(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
                    .add(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON, Bundle.EMPTY))
                    .add(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF, Bundle.EMPTY))
                    .add(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE, Bundle.EMPTY))

                updateSessionCustomLayout(session)

                return MediaSession.ConnectionResult.accept(
                    sessionCommandsBuilder.build(),
                    connectionResult.availablePlayerCommands
                )
            }

            override fun onPostConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ) {
                super.onPostConnect(session, controller)
                updateSessionCustomLayout(session)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                Timber.tag("MusicService")
                    .d("onCustomCommand received: ${customCommand.customAction}")
                when (customCommand.customAction) {
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON -> {
                        Timber.tag("MusicService")
                            .d("Executing SHUFFLE_ON. Current shuffleMode: ${session.player.shuffleModeEnabled}")
                        session.player.shuffleModeEnabled = true
                        Timber.tag("MusicService")
                            .d("Executed SHUFFLE_ON. New shuffleMode: ${session.player.shuffleModeEnabled}")
                        refreshSessionNotification(session)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF -> {
                        Timber.tag("MusicService")
                            .d("Executing SHUFFLE_OFF. Current shuffleMode: ${session.player.shuffleModeEnabled}")
                        session.player.shuffleModeEnabled = false
                        Timber.tag("MusicService")
                            .d("Executed SHUFFLE_OFF. New shuffleMode: ${session.player.shuffleModeEnabled}")
                        refreshSessionNotification(session)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE -> {
                        val currentMode = session.player.repeatMode
                        val newMode = when (currentMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                            else -> Player.REPEAT_MODE_OFF
                        }
                        session.player.repeatMode = newMode
                        refreshSessionNotification(session)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_LIKE -> {
                        val songId = session.player.currentMediaItem?.mediaId ?: return@onCustomCommand Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_UNKNOWN))
                        Timber.tag("MusicService").d("Executing LIKE for songId: $songId")
                        serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            Timber.tag("MusicService").d("Toggling favorite status for $songId")
                            val wasFavorite = favoriteSongIds.contains(songId)
                            favoriteSongIds = if (wasFavorite) {
                                favoriteSongIds - songId
                            } else {
                                favoriteSongIds + songId
                            }
                            Timber.tag("MusicService")
                                .d("Updated favorite cache. wasFavorite=$wasFavorite")
                            refreshSessionNotification(session)
                            userPreferencesRepository.toggleFavoriteSong(songId)
                            Timber.tag("MusicService")
                                .d("Persisted favorite change for $songId")
                        }
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, engine.masterPlayer)
            .setSessionActivity(getOpenAppPendingIntent())
            .setCallback(callback)
            .build()

        setMediaNotificationProvider(MusicNotificationProvider(this, this))

        mediaSession?.let { updateSessionCustomLayout(it) }

        serviceScope.launch {
            userPreferencesRepository.favoriteSongIdsFlow.collect { ids ->
                Timber.tag("MusicService")
                    .d("favoriteSongIdsFlow collected. New ids size: ${ids.size}")
                val oldIds = favoriteSongIds
                favoriteSongIds = ids
                val currentSongId = mediaSession?.player?.currentMediaItem?.mediaId
                if (currentSongId != null) {
                    val wasFavorite = oldIds.contains(currentSongId)
                    val isFavorite = ids.contains(currentSongId)
                    if (wasFavorite != isFavorite) {
                        Timber.tag("MusicService")
                            .d("Favorite status changed for current song. Updating notification.")
                        mediaSession?.let { refreshSessionNotification(it) }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            val player = mediaSession?.player ?: return@let
            when (action) {
                PlayerActions.PLAY_PAUSE -> player.playWhenReady = !player.playWhenReady
                PlayerActions.NEXT -> player.seekToNext()
                PlayerActions.PREVIOUS -> player.seekToPrevious()
                PlayerActions.PLAY_FROM_QUEUE -> {
                    val songId = intent.getLongExtra("song_id", -1L)
                    if (songId != -1L) {
                        val timeline = player.currentTimeline
                        if (!timeline.isEmpty) {
                            val window = androidx.media3.common.Timeline.Window()
                            for (i in 0 until timeline.windowCount) {
                                timeline.getWindow(i, window)
                                if (window.mediaItem.mediaId.toLongOrNull() == songId) {
                                    player.seekTo(i, C.TIME_UNSET)
                                    player.play()
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            requestWidgetFullUpdate()
            mediaSession?.let { refreshSessionNotification(it) }
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshSessionNotification(it) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            Timber.tag("MusicService")
                .d("playerListener.onShuffleModeEnabledChanged: $shuffleModeEnabled")
            mediaSession?.let { refreshSessionNotification(it) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            mediaSession?.let { refreshSessionNotification(it) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Error en el reproductor: ")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        engine.release()
        controller.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun getOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ACTION_SHOW_PLAYER", true) // Signal to MainActivity to show the player
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // --- LÓGICA PARA ACTUALIZACIÓN DE WIDGETS Y DATOS ---
    private var debouncedWidgetUpdateJob: Job? = null
    private val WIDGET_STATE_DEBOUNCE_MS = 300L

    private fun requestWidgetFullUpdate(force: Boolean = false) {
        debouncedWidgetUpdateJob?.cancel()
        debouncedWidgetUpdateJob = serviceScope.launch {
            if (!force) {
                delay(WIDGET_STATE_DEBOUNCE_MS)
            }
            processWidgetUpdateInternal()
        }
    }

    private suspend fun processWidgetUpdateInternal() {
        val playerInfo = buildPlayerInfo()
        updateGlanceWidgets(playerInfo)
    }

    private suspend fun buildPlayerInfo(): PlayerInfo {
        val player = engine.masterPlayer
        val currentItem = withContext(Dispatchers.Main) { player.currentMediaItem }
        val isPlaying = withContext(Dispatchers.Main) { player.isPlaying }
        val currentPosition = withContext(Dispatchers.Main) { player.currentPosition }
        val totalDuration = withContext(Dispatchers.Main) { player.duration.coerceAtLeast(0) }

        val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
        val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
        val mediaId = currentItem?.mediaId
        val artworkUri = currentItem?.mediaMetadata?.artworkUri
        val artworkData = currentItem?.mediaMetadata?.artworkData

        val (artBytes, artUriString) = getAlbumArtForWidget(artworkData, artworkUri)

        val isFavorite = false
//        val isFavorite = mediaId?.let {
//            //musicRepository.getFavoriteSongs().firstOrNull()?.any { song -> song.id.toString() == it }
//        } ?: false

        val queueItems = mutableListOf<com.theveloper.pixelplay.data.model.QueueItem>()
        val timeline = withContext(Dispatchers.Main) { player.currentTimeline }
        if (!timeline.isEmpty) {
            val window = androidx.media3.common.Timeline.Window()
            val currentWindowIndex = withContext(Dispatchers.Main) { player.currentMediaItemIndex }

            // Empezar desde la siguiente canción en la cola
            val startIndex = if (currentWindowIndex + 1 < timeline.windowCount) currentWindowIndex + 1 else 0

            // Limitar el número de elementos de la cola a 4
            val endIndex = (startIndex + 4).coerceAtMost(timeline.windowCount)
            for (i in startIndex until endIndex) {
                timeline.getWindow(i, window)
                val mediaItem = window.mediaItem
                val songId = mediaItem.mediaId.toLongOrNull()
                if (songId != null) {
                    val (artBytes, _) = getAlbumArtForWidget(
                        embeddedArt = mediaItem.mediaMetadata?.artworkData,
                        artUri = mediaItem.mediaMetadata?.artworkUri
                    )
                    queueItems.add(
                        com.theveloper.pixelplay.data.model.QueueItem(
                            id = songId,
                            albumArtBitmapData = artBytes
                        )
                    )
                }
            }
        }

        return PlayerInfo(
            songTitle = title,
            artistName = artist,
            isPlaying = isPlaying,
            albumArtUri = artUriString,
            albumArtBitmapData = artBytes,
            currentPositionMs = currentPosition,
            totalDurationMs = totalDuration,
            isFavorite = isFavorite,
            queue = queueItems
        )
    }

    private val widgetArtByteArrayCache = LruCache<String, ByteArray>(5)

    private suspend fun getAlbumArtForWidget(embeddedArt: ByteArray?, artUri: Uri?): Pair<ByteArray?, String?> = withContext(Dispatchers.IO) {
        if (embeddedArt != null && embeddedArt.isNotEmpty()) {
            return@withContext embeddedArt to artUri?.toString()
        }
        val uri = artUri ?: return@withContext null to null
        val artUriString = uri.toString()
        val cachedArt = widgetArtByteArrayCache.get(artUriString)
        if (cachedArt != null) {
            return@withContext cachedArt to artUriString
        }
        val loadedArt = loadBitmapDataFromUri(uri = uri, context = baseContext)
        if (loadedArt != null) {
            widgetArtByteArrayCache.put(artUriString, loadedArt)
        }
        return@withContext loadedArt to artUriString
    }

    private suspend fun updateGlanceWidgets(playerInfo: PlayerInfo) = withContext(Dispatchers.IO) {
        try {
            val glanceManager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = glanceManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
            if (glanceIds.isNotEmpty()) {
                glanceIds.forEach { id ->
                    updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { playerInfo }
                }
                PixelPlayGlanceWidget().update(applicationContext, glanceIds.first())
                Log.d(TAG, "Widget actualizado: ${playerInfo.songTitle}")
            } else {
                Log.w(TAG, "No se encontraron widgets para actualizar")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al actualizar el widget", e)
        }
    }

    private suspend fun loadBitmapDataFromUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context).data(uri).size(Size(256, 256)).allowHardware(false).build()
            val drawable = context.imageLoader.execute(request).drawable
            drawable?.let {
                val bitmap = it.toBitmap(256, 256)
                val stream = ByteArrayOutputStream()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                }
                stream.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al cargar bitmap desde URI: $uri", e)
            null
        }
    }

    fun isSongFavorite(songId: String?): Boolean {
        return songId != null && favoriteSongIds.contains(songId)
    }

    private fun refreshSessionNotification(session: MediaSession) {
        updateSessionCustomLayout(session)
        super.onUpdateNotification(session)
    }

    private fun updateSessionCustomLayout(session: MediaSession) {
        session.setCustomLayout(buildCustomLayout(session))
    }

    private fun buildCustomLayout(session: MediaSession): List<CommandButton> {
        return listOf(
            buildLikeCommandButton(session),
            buildShuffleCommandButton(session),
            buildRepeatCommandButton(session)
        )
    }

    private fun buildLikeCommandButton(session: MediaSession): CommandButton {
        val isFavorite = isSongFavorite(session.player.currentMediaItem?.mediaId)
        val likeIcon = if (isFavorite) {
            R.drawable.round_favorite_24
        } else {
            R.drawable.round_favorite_border_24
        }

        return CommandButton.Builder()
            .setIconResId(likeIcon)
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
            .build()
    }

    private fun buildShuffleCommandButton(session: MediaSession): CommandButton {
        val shuffleEnabled = session.player.shuffleModeEnabled
        val shuffleCommandAction = if (shuffleEnabled) {
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF
        } else {
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON
        }
        val shuffleIcon = if (shuffleEnabled) {
            R.drawable.rounded_shuffle_on_24
        } else {
            R.drawable.rounded_shuffle_24
        }

        return CommandButton.Builder()
            .setIconResId(shuffleIcon)
            .setSessionCommand(SessionCommand(shuffleCommandAction, Bundle.EMPTY))
            .build()
    }

    private fun buildRepeatCommandButton(session: MediaSession): CommandButton {
        val repeatIcon = when (session.player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_on_24
            Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_on_24
            else -> R.drawable.rounded_repeat_24
        }

        return CommandButton.Builder()
            .setIconResId(repeatIcon)
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE, Bundle.EMPTY))
            .build()
    }
}

