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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

// Acciones personalizadas para compatibilidad con el widget existente


@UnstableApi
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject
    lateinit var exoPlayer: ExoPlayer
    @Inject
    lateinit var musicRepository: MusicRepository

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "MusicService_PixelPlay"
        const val NOTIFICATION_ID = 101
    }

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        exoPlayer.setAudioAttributes(audioAttributes, true)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        exoPlayer.addListener(playerListener)

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(getOpenAppPendingIntent())
            .build()
    }

    // SOLUCIÓN WIDGET: Re-introducimos onStartCommand para traducir las acciones del widget.
    // Esto asegura la compatibilidad con los intents que ya envía tu widget.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            val player = mediaSession?.player ?: return@let
            when (action) {
                PlayerActions.PLAY_PAUSE -> player.playWhenReady = !player.playWhenReady
                PlayerActions.NEXT -> player.seekToNext()
                PlayerActions.PREVIOUS -> player.seekToPrevious()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            requestWidgetFullUpdate()
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            requestWidgetFullUpdate(force = true)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Error en el reproductor: ", error)
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
            player.removeListener(playerListener)
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun getOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        val currentItem = withContext(Dispatchers.Main) { exoPlayer.currentMediaItem }
        val isPlaying = withContext(Dispatchers.Main) { exoPlayer.isPlaying }
        val currentPosition = withContext(Dispatchers.Main) { exoPlayer.currentPosition }
        val totalDuration = withContext(Dispatchers.Main) { exoPlayer.duration.coerceAtLeast(0) }

        val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
        val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
        val mediaId = currentItem?.mediaId
        val artworkUri = currentItem?.mediaMetadata?.artworkUri
        val artworkData = currentItem?.mediaMetadata?.artworkData

        val (artBytes, artUriString) = getAlbumArtForWidget(artworkData, artworkUri)

        // CORRECCIÓN: Obtenemos el estado de favorito desde el Flow del repositorio.
        val isFavorite = false
//        val isFavorite = mediaId?.let {
//            //musicRepository.getFavoriteSongs().firstOrNull()?.any { song -> song.id.toString() == it }
//        } ?: false

        return PlayerInfo(
            songTitle = title,
            artistName = artist,
            isPlaying = isPlaying,
            albumArtUri = artUriString,
            albumArtBitmapData = artBytes,
            currentPositionMs = currentPosition,
            totalDurationMs = totalDuration,
            isFavorite = isFavorite,
            queue = emptyList() // TODO: Implementar la lógica de la cola si es necesario
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
}

