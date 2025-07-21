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
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.PixelPlayApplication
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlayerInfo // Import new data class
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.provider.MusicNotificationProvider
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

@UnstableApi
@AndroidEntryPoint
class MusicService : MediaSessionService() {
    @Inject
    lateinit var exoPlayer: ExoPlayer
    @Inject
    lateinit var musicRepository: MusicRepository

    private var mediaSession: MediaSession? = null
    private lateinit var notificationProvider: MusicNotificationProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var stopServiceJob: Job? = null
    private val STOP_DELAY = 30000L // 30 segundos

    companion object {
        private const val TAG = "MusicService_PixelPlay"
        const val NOTIFICATION_ID = 101
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // --- INICIO DE LA SOLUCIÓN DEFINITIVA ---
        // 1. Crear una notificación temporal inmediatamente.
        val loadingNotification = createLoadingNotification()
        // 2. Llamar a startForeground() de forma síncrona en onCreate.
        //    Esto cumple con el requisito de Android al instante y previene el ANR.
        startForeground(NOTIFICATION_ID, loadingNotification)
        // --- FIN DE LA SOLUCIÓN DEFINITIVA ---

        initializePlayer()

        serviceScope.launch(Dispatchers.Main) {
            mediaSession = MediaSession.Builder(this@MusicService, exoPlayer).build()
            notificationProvider = MusicNotificationProvider(this@MusicService, mediaSession!!.platformToken)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.action?.let { action ->
            handleIntentAction(action)
        }
        return START_NOT_STICKY
    }

    private fun handleIntentAction(action: String) {
        serviceScope.launch(Dispatchers.Main) {
            when (action) {
                PlayerActions.PLAY_PAUSE -> togglePlayPause()
                PlayerActions.NEXT -> playNext()
                PlayerActions.PREVIOUS -> playPrevious()
                PlayerActions.FAVORITE -> toggleFavorite()
            }
        }
        requestWidgetFullUpdate(force = true)
    }

    private fun initializePlayer() {
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        exoPlayer.setAudioAttributes(attrs, true)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        exoPlayer.addListener(playerListener)
    }

    private val playerListener = object : Player.Listener {
        @OptIn(UnstableApi::class)
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            LogUtils.d(this, "onIsPlayingChanged: $isPlaying")
            stopServiceJob?.cancel()

            if (isPlaying) {
                serviceScope.launch(Dispatchers.Main) {
                    // Esto ahora actualizará la notificación temporal a la de medios.
                    notificationProvider.showNotificationForPlayer(exoPlayer)
                }
            } else {
                stopServiceJob = serviceScope.launch {
                    delay(STOP_DELAY)
                    Log.d(TAG, "Stopping service due to inactivity.")
                    withContext(Dispatchers.Main) {
                        notificationProvider.hideNotification()
                    }
                }
            }
            requestWidgetFullUpdate()
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            requestWidgetFullUpdate(force = true)
        }

        override fun onPlayerError(error: PlaybackException) {
            LogUtils.e(this, error, "onPlayerError")
            serviceScope.launch(Dispatchers.Main) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                requestWidgetFullUpdate(force = true)
            }
        }
    }

    // --- INICIO DE LA SOLUCIÓN DEFINITIVA ---
    // 3. Función para crear la notificación temporal.
    private fun createLoadingNotification(): Notification {
        return NotificationCompat.Builder(this, PixelPlayApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("PixelPlay")
            .setContentText("Inicializando reproductor...")
            .setSmallIcon(R.drawable.rounded_music_note_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    // --- FIN DE LA SOLUCIÓN DEFINITIVA ---

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    private fun playNext() {
        if (exoPlayer.hasNextMediaItem()) exoPlayer.seekToNextMediaItem()
    }

    private fun playPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) exoPlayer.seekToPreviousMediaItem() else exoPlayer.seekTo(0)
    }

    private fun toggleFavorite() {
        val currentSongId = exoPlayer.currentMediaItem?.mediaId ?: return
        serviceScope.launch(Dispatchers.IO) {
            musicRepository.toggleFavoriteStatus(currentSongId)
        }
    }

    private val isFavoriteMap = mutableMapOf<String, Boolean>()
    private var debouncedWidgetUpdateJob: Job? = null
    private var lastWidgetArtUriString = ""
    private var lastWidgetIsPlayingState = false
    private var lastWidgetFavoriteState = false
    private val widgetArtByteArrayCache = LruCache<String, ByteArray>(5)
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
        val playerData = withContext(Dispatchers.Main) {
            val currentItem = exoPlayer.currentMediaItem
            val queueItemsData = mutableListOf<Pair<String, ByteArray?>>()
            if (exoPlayer.mediaItemCount > 0) {
                for (i in exoPlayer.currentMediaItemIndex + 1 until exoPlayer.mediaItemCount) {
                    val mediaItem = exoPlayer.getMediaItemAt(i)
                    queueItemsData.add(Pair(mediaItem.mediaId, mediaItem.mediaMetadata.artworkData))
                }
            }
            object {
                val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
                val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
                val isPlaying = exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED
                val currentPosition = exoPlayer.currentPosition
                val totalDuration = exoPlayer.duration.coerceAtLeast(0)
                val mediaId = currentItem?.mediaId
                val artworkUri = currentItem?.mediaMetadata?.artworkUri
                val artworkData = currentItem?.mediaMetadata?.artworkData
                val queueData = queueItemsData
            }
        }

        return withContext(Dispatchers.IO) {
            val (artBytes, artUriString) = getAlbumArtForWidget(playerData.artworkData, playerData.artworkUri)
            val actualIsFavorite = isFavoriteMap[playerData.mediaId] ?: false
//            val actualIsFavorite = playerData.mediaId?.let { musicRepository.isFavorite(it) } ?: false
//            isFavoriteMap[playerData.mediaId ?: ""] = actualIsFavorite

            val queueItems = playerData.queueData.map {
                com.theveloper.pixelplay.data.model.QueueItem(it.first.toLong(), it.second)
            }

            PlayerInfo(
                songTitle = playerData.title,
                artistName = playerData.artist,
                isPlaying = playerData.isPlaying,
                albumArtUri = artUriString,
                albumArtBitmapData = artBytes,
                currentPositionMs = playerData.currentPosition,
                totalDurationMs = playerData.totalDuration,
                isFavorite = actualIsFavorite,
                queue = queueItems
            )
        }
    }

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
            requestWidgetFullUpdate(force = true)
        }
        return@withContext loadedArt to artUriString
    }

    private suspend fun updateGlanceWidgets(playerInfo: PlayerInfo) = withContext(Dispatchers.IO) {
        val artUriForComparison = playerInfo.albumArtUri ?: ""
        val significantChangeOccurred = lastWidgetIsPlayingState != playerInfo.isPlaying ||
                lastWidgetArtUriString != artUriForComparison ||
                lastWidgetFavoriteState != playerInfo.isFavorite

        if (!significantChangeOccurred) return@withContext

        try {
            val glanceManager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = glanceManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
            if (glanceIds.isNotEmpty()) {
                glanceIds.forEach { id ->
                    updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { playerInfo }
                }
                glanceIds.forEach { id -> PixelPlayGlanceWidget().update(applicationContext, id) }
                Log.d(TAG, "Successfully sent widget update. Playing: ${playerInfo.isPlaying}, Title: ${playerInfo.songTitle}")
                lastWidgetIsPlayingState = playerInfo.isPlaying
                lastWidgetFavoriteState = playerInfo.isFavorite
                lastWidgetArtUriString = artUriForComparison
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget state", e)
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
            Log.e(TAG, "Failed to load bitmap from URI: $uri", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // --- INICIO DE LA SOLUCIÓN DEFINITIVA ---
        // 4. Asegurarse de que el servicio salga del primer plano al ser destruido.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        // --- FIN DE LA SOLUCIÓN DEFINITIVA ---
        serviceScope.cancel()
        exoPlayer.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        exoPlayer.release()
    }
}

//@AndroidEntryPoint
//class MusicService : MediaSessionService() {
//    @Inject lateinit var exoPlayer: ExoPlayer
//    @Inject lateinit var musicRepository: MusicRepository
//    private var mediaSession: MediaSession? = null
//
//    // serviceScope usa IO para operaciones de música y Default para el resto
//    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
//
//    companion object {
//        private const val TAG = "MusicService_PixelPlay"
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        LogUtils.d(this, "onCreate")
//        // La inicialización ahora es síncrona y más simple.
//        // MediaSessionService gestionará el foreground state.
//        initializePlayer()
//        initializeMediaSession()
//        Log.d(TAG, "MusicService created and initialized.")
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        super.onStartCommand(intent, flags, startId)
//        LogUtils.d(this, "onStartCommand: ${intent?.action}")
//        intent?.action?.let { action ->
//            handleIntentAction(action)
//        }
//        return START_NOT_STICKY
//    }
//
//    private fun handleIntentAction(action: String) {
//        LogUtils.d(this, "handleIntentAction: $action")
//        when (action) {
//            PlayerActions.PLAY_PAUSE -> togglePlayPause()
//            PlayerActions.NEXT       -> playNext()
//            PlayerActions.PREVIOUS   -> playPrevious()
//            PlayerActions.FAVORITE   -> toggleFavorite()
//        }
//        requestWidgetFullUpdate(force = true)
//    }
//
//    private fun initializeMediaSession() {
//        serviceScope.launch(Dispatchers.Main) {
//            val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)
//                ?.let { PendingIntent.getActivity(this@MusicService, 0, it, PendingIntent.FLAG_IMMUTABLE) }
//                ?: PendingIntent.getActivity(
//                    this@MusicService, 0,
//                    Intent(this@MusicService, MainActivity::class.java),
//                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
//                )
//            mediaSession = MediaSession.Builder(this@MusicService, exoPlayer)
//                .setSessionActivity(pendingIntent)
//                .build()
//        }
//    }
//
//    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
//        LogUtils.d(this, "onGetSession")
//        return mediaSession
//    }
//
//    private fun togglePlayPause() = serviceScope.launch(Dispatchers.Main) {
//        LogUtils.d(this, "togglePlayPause")
//        if (exoPlayer.isPlaying) {
//            exoPlayer.pause()
//        } else {
//            if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
//                exoPlayer.prepare()
//            }
//            exoPlayer.play()
//        }
//    }
//
//    private fun playNext() = serviceScope.launch(Dispatchers.Main) {
//        LogUtils.d(this, "playNext")
//        if (exoPlayer.hasNextMediaItem()) {
//            exoPlayer.seekToNextMediaItem()
//            exoPlayer.play()
//        }
//    }
//
//    private fun playPrevious() = serviceScope.launch(Dispatchers.Main) {
//        LogUtils.d(this, "playPrevious")
//        if (exoPlayer.hasPreviousMediaItem()) {
//            exoPlayer.seekToPreviousMediaItem()
//            exoPlayer.play()
//        } else if (exoPlayer.isCurrentMediaItemSeekable) {
//            exoPlayer.seekTo(0)
//            exoPlayer.play()
//        }
//    }
//
//    private fun toggleFavorite() {
//        LogUtils.d(this, "toggleFavorite")
//        val currentSongId = exoPlayer.currentMediaItem?.mediaId ?: return
//        serviceScope.launch {
//            val newFavoriteStatus = musicRepository.toggleFavoriteStatus(currentSongId)
//            // Actualizar el isFavoriteMap local para reflejar inmediatamente el cambio,
//            // aunque la SSoT vendrá del repositorio en la próxima actualización completa del widget.
//            // Esto es opcional si confías en que requestWidgetFullUpdate será lo suficientemente rápido.
//            isFavoriteMap[currentSongId] = newFavoriteStatus
//            Log.d(TAG, "Toggled favorite for song $currentSongId to $newFavoriteStatus")
//            // No es necesario llamar a requestWidgetFullUpdate aquí si la acción ya lo hace.
//            // handleIntentAction ya llama a requestWidgetFullUpdate(force = true)
//        }
//    }
//
//    // isFavoriteMap se usará como una caché temporal o para acceso rápido,
//    // pero la fuente de verdad es el repositorio.
//    private val isFavoriteMap = mutableMapOf<String, Boolean>()
//
//
//    private var lastProcessedWidgetUpdateTimeMs = 0L
//    private var lastWidgetArtUriString = ""
//    private var debouncedWidgetUpdateJob: Job? = null // Para actualizaciones completas (no solo progreso)
//    private var lastWidgetIsPlayingState = false
//    private var lastWidgetFavoriteState = false
//
//    // LruCache para ByteArrays de carátulas, clave es URI en String
//    private val widgetArtByteArrayCache = LruCache<String, ByteArray>(5)
//    // Tiempo de debounce para actualizaciones de estado completas (en milisegundos)
//    private val WIDGET_STATE_DEBOUNCE_MS = 300L
//
//    // Esta función se llamará cuando cambie el estado del reproductor (canción, play/pausa, etc.)
//    // O cuando una acción del usuario (botón de siguiente/anterior) lo requiera.
//    private fun requestWidgetFullUpdate(force: Boolean = false) {
//        debouncedWidgetUpdateJob?.cancel() // Cancela el job de debounce anterior
//        debouncedWidgetUpdateJob = serviceScope.launch {
//            if (!force) {
//                delay(WIDGET_STATE_DEBOUNCE_MS) // Espera a que cesen los cambios rápidos
//            }
//            // Esta llamada es para una actualización completa, no solo de progreso.
//            processWidgetUpdateInternal()
//        }
//    }
//
//    private var stopServiceJob: Job? = null
//    private val STOP_DELAY = 30000L // 30 segundos
//
//    // Listener de ExoPlayer
//    private val playerListener = object : Player.Listener {
//        override fun onPlaybackStateChanged(playbackState: Int) {
//            LogUtils.d(this, "onPlaybackStateChanged: $playbackState")
//            serviceScope.launch(Dispatchers.Main) {
//                requestWidgetFullUpdate()
//            }
//        }
//
//        override fun onIsPlayingChanged(isPlaying: Boolean) {
//            LogUtils.d(this, "onIsPlayingChanged: $isPlaying")
//            serviceScope.launch(Dispatchers.Main) {
//                requestWidgetFullUpdate()
//                if (!isPlaying) {
//                    // Si la reproducción se detiene, programa la detención del servicio
//                    stopServiceJob = serviceScope.launch {
//                        delay(STOP_DELAY)
//                        Log.d(TAG, "Stopping service due to inactivity.")
//                        stopSelf()
//                    }
//                } else {
//                    // Si la reproducción se reanuda, cancela la detención programada
//                    stopServiceJob?.cancel()
//                    stopServiceJob = null
//                }
//            }
//        }
//
//        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
//            LogUtils.d(this, "onMediaItemTransition: ${item?.mediaId}, reason: $reason")
//            serviceScope.launch(Dispatchers.Main) {
//                requestWidgetFullUpdate() // Actualización completa al cambiar de canción
//            }
//        }
//
//        override fun onPlayerError(error: PlaybackException) {
//            LogUtils.e(this, error, "onPlayerError")
//            serviceScope.launch(Dispatchers.Main) {
//                Log.e(TAG, "PlayerError: ${error.message}")
//                exoPlayer.stop() // Considerar limpiar la cola o manejar el error de forma más robusta
//                exoPlayer.clearMediaItems()
//                requestWidgetFullUpdate(force = true) // Reflejar el estado de error en el widget
//            }
//        }
//    }
//
//    private fun initializePlayer() = serviceScope.launch(Dispatchers.Main) {
//        LogUtils.d(this, "initializePlayer")
//        val attrs = AudioAttributes.Builder()
//            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
//            .setUsage(C.USAGE_MEDIA)
//            .build()
//        exoPlayer.setAudioAttributes(attrs, true)
//        exoPlayer.setHandleAudioBecomingNoisy(true)
//        exoPlayer.addListener(playerListener) // Usar la instancia del listener definida arriba
//    }
//
//    private fun processWidgetUpdateInternal() {
//        serviceScope.launch {
//            val playerInfo = buildPlayerInfo()
//            updateGlanceWidgets(playerInfo)
//        }
//    }
//
//    private suspend fun buildPlayerInfo(): PlayerInfo = withContext(Dispatchers.Main) {
//        val currentItem = exoPlayer.currentMediaItem
//        val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
//        val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
//        val isPlaying = exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED
//        val (artBytes, artUriString) = getAlbumArt(currentItem)
//
//        val actualIsFavorite = currentItem?.mediaId?.let { isFavoriteMap[it] } ?: false
//
//        val queueItems = mutableListOf<com.theveloper.pixelplay.data.model.QueueItem>()
//        if (exoPlayer.mediaItemCount > 0) {
//            for (i in exoPlayer.currentMediaItemIndex + 1 until exoPlayer.mediaItemCount) {
//                val mediaItem = exoPlayer.getMediaItemAt(i)
//                val artworkData = mediaItem.mediaMetadata.artworkData
//                queueItems.add(com.theveloper.pixelplay.data.model.QueueItem(mediaItem.mediaId.toLong(), artworkData))
//            }
//        }
//
//        PlayerInfo(
//            songTitle = title,
//            artistName = artist,
//            isPlaying = isPlaying,
//            albumArtUri = artUriString,
//            albumArtBitmapData = artBytes,
//            currentPositionMs = exoPlayer.currentPosition,
//            totalDurationMs = exoPlayer.duration.coerceAtLeast(0),
//            isFavorite = actualIsFavorite,
//            queue = queueItems
//        )
//    }
//
//    private fun getAlbumArt(currentItem: MediaItem?): Pair<ByteArray?, String?> {
//        if (currentItem == null) return null to null
//
//        // Prioridad 1: Datos de arte incrustados
//        val embeddedArt = currentItem.mediaMetadata.artworkData
//        if (embeddedArt != null && embeddedArt.isNotEmpty()) {
//            Log.d(TAG, "getAlbumArt: Found embedded art, size: ${embeddedArt.size}")
//            return embeddedArt to currentItem.mediaMetadata.artworkUri?.toString()
//        }
//
//        // Prioridad 2: URI del arte
//        val artUri = currentItem.mediaMetadata.artworkUri ?: return null to null
//        val artUriString = artUri.toString()
//
//        // Comprobar caché
//        val cachedArt = widgetArtByteArrayCache.get(artUriString)
//        if (cachedArt != null) {
//            Log.d(TAG, "getAlbumArt: Cache HIT for URI: $artUriString")
//            return cachedArt to artUriString
//        }
//
//        // Cargar desde URI si no está en caché
//        serviceScope.launch {
//            Log.d(TAG, "getAlbumArt: Cache MISS for URI: $artUriString. Loading.")
//            val loadedArt = loadBitmapDataFromUri(
//                uri = artUri,
//                context = baseContext
//            )
//            if (loadedArt != null) {
//                widgetArtByteArrayCache.put(artUriString, loadedArt)
//                // Request a widget update now that the art is loaded
//                requestWidgetFullUpdate(force = true)
//            }
//        }
//        return null to artUriString // Return null for now, the update will come later
//    }
//
//
//    private suspend fun updateGlanceWidgets(playerInfo: PlayerInfo) {
//        val artUriForComparison = playerInfo.albumArtUri ?: ""
//        val significantChangeOccurred = lastWidgetIsPlayingState != playerInfo.isPlaying ||
//                                        lastWidgetArtUriString != artUriForComparison ||
//                                        lastWidgetFavoriteState != playerInfo.isFavorite
//
//        // Aunque el debounce ya ayuda, este chequeo evita trabajo innecesario en el hilo de IO
//        // si el estado es idéntico al último enviado.
//        if (!significantChangeOccurred) {
//            Log.v(TAG, "Skipping widget update as state is identical to the last one.")
//            return
//        }
//
//        withContext(Dispatchers.IO) {
//            try {
//                val glanceManager = GlanceAppWidgetManager(applicationContext)
//                val glanceIds = glanceManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
//                if (glanceIds.isNotEmpty()) {
//                    glanceIds.forEach { id ->
//                        updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { playerInfo }
//                    }
//                    glanceIds.forEach { id -> PixelPlayGlanceWidget().update(applicationContext, id) }
//                    Log.d(TAG, "Successfully sent widget update. Playing: ${playerInfo.isPlaying}, Title: ${playerInfo.songTitle}")
//
//                    // Actualizar el último estado conocido
//                    lastWidgetIsPlayingState = playerInfo.isPlaying
//                    lastWidgetFavoriteState = playerInfo.isFavorite
//                    lastWidgetArtUriString = artUriForComparison
//                } else {
//                    Log.d(TAG, "No Glance widget IDs found. Skipping widget update.")
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error updating widget state", e)
//            }
//        }
//    }
//
//    // widgetArtByteArrayCache se define arriba
//    // private val widgetArtCache = LruCache<String, ByteArray>(5) // Movido y renombrado
//
//    private suspend fun loadBitmapDataFromUri(
//        context: Context,
//        uri: Uri
//    ): ByteArray? = withContext(Dispatchers.IO) {
//        val uriString = uri.toString() // Clave para la caché
//
//        // La verificación de caché ahora se hace en processWidgetUpdateInternal antes de llamar a esta función.
//        // Esta función ahora solo carga y comprime.
//
//        try {
//            val request = ImageRequest.Builder(context)
//                .data(uri)
//                .size(Size(256, 256)) // Aumentado un poco para mejor calidad si el widget es grande
//                .allowHardware(false)
//                .bitmapConfig(Bitmap.Config.ARGB_8888)
//                .build()
//            val drawable = context.imageLoader.execute(request).drawable
//            drawable?.let {
//                val originalBitmap = it.toBitmap(
//                    width = if (it.intrinsicWidth > 0) it.intrinsicWidth.coerceAtMost(256) else 128,
//                    height = if (it.intrinsicHeight > 0) it.intrinsicHeight.coerceAtMost(256) else 128,
//                    config = Bitmap.Config.ARGB_8888
//                )
//
//                val bitmapToCompress = if (!originalBitmap.isMutable) {
//                    originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
//                } else {
//                    originalBitmap
//                }
//
//                val stream = ByteArrayOutputStream()
//                // Usar WEBP para mejor calidad/compresión si es posible, o JPEG con calidad decente.
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                    bitmapToCompress.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
//                } else {
//                    bitmapToCompress.compress(Bitmap.CompressFormat.JPEG, 85, stream)
//                }
//                val byteArray = stream.toByteArray()
//                Log.d(TAG, "Bitmap loaded and compressed for URI: $uriString, size: ${byteArray.size} bytes")
//                byteArray
//            } ?: run {
//                Log.w(TAG, "Drawable was null for URI: $uriString")
//                null
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to load bitmap from URI: $uriString", e)
//            null
//        }
//    }
//
//    override fun onDestroy() {
//        LogUtils.d(this, "onDestroy")
//        mediaSession?.release()
//        mediaSession = null
//        super.onDestroy()
//        serviceScope.cancel() // Cancelar el scope del servicio
//    }
//}