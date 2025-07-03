package com.theveloper.pixelplay.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
// import androidx.media3.common.util.UnstableApi // Potentially remove if not used elsewhere
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
// import com.google.protobuf.ByteString // No longer needed
import com.theveloper.pixelplay.MainActivity
// import com.theveloper.pixelplay.PlayerInfoProto // Replaced
import com.theveloper.pixelplay.data.model.PlayerInfo // Import new data class
import com.theveloper.pixelplay.R
// import com.theveloper.pixelplay.data.EotStateHolder // Removed
import com.theveloper.pixelplay.ui.glancewidget.PixelPlayGlanceWidget
import com.theveloper.pixelplay.ui.glancewidget.PlayerActions
import com.theveloper.pixelplay.ui.glancewidget.PlayerInfoStateDefinition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class MusicService : MediaSessionService() {
    @Inject lateinit var exoPlayer: ExoPlayer
    private var mediaSession: MediaSession? = null

    // serviceScope usa Main para ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG           = "MusicService_PixelPlay"
        private const val CHANNEL_ID    = "pixelplay_media_playback_service"
        private const val CHANNEL_NAME  = "PixelPlay Reproducción"
        private const val NOTIF_ID      = 101
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // *** FIX: Start foreground immediately with a loading notification to prevent ANR. ***
        // This is the critical change. We show a basic notification right away.
        startForeground(NOTIF_ID, buildLoadingNotification())
        Log.d(TAG, "Service started in foreground immediately to prevent ANR.")

        // Now, defer the heavy initialization.
        serviceScope.launch {
            Log.d(TAG, "Starting deferred initialization...")
            initializePlayer()
            initializeMediaSession()
            Log.d(TAG, "Deferred initialization complete.")
            // The notification will be updated automatically by the player listener
            // when the state changes.
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.action?.let { action ->
            handleIntentAction(action) // Llamar a requestWidgetFullUpdate DENTRO de handleIntentAction
        }
        return START_STICKY
    }

    // This notification is shown for a very short time while the player initializes.
    private fun buildLoadingNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Iniciando servicio de música...")
            .setSmallIcon(R.drawable.rounded_circle_notifications_24)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()


    private fun buildServiceNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Reproduciendo música")
            .setSmallIcon(R.drawable.rounded_circle_notifications_24)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

    private fun handleIntentAction(action: String) {
        when (action) {
            PlayerActions.PLAY_PAUSE -> togglePlayPause()
            PlayerActions.NEXT       -> playNext()
            PlayerActions.PREVIOUS   -> playPrevious()
            // No es necesario llamar a requestWidgetFullUpdate() aquí explícitamente
            // si las funciones togglePlayPause, playNext, playPrevious
            // ya modifican el estado de ExoPlayer, lo que activará el playerListener.
            // Sin embargo, para asegurar la inmediatez de la respuesta a la acción del usuario,
            // una llamada directa puede ser beneficiosa, y el debounce se encargará de la eficiencia.
        }
        requestWidgetFullUpdate() // Solicitar actualización después de una acción. El debounce lo manejará.
    }

    // Listener de ExoPlayer (ya modificado en el paso anterior para usar requestWidgetFullUpdate)
    // ... (playerListener definido previamente) ...

//    private fun initializePlayer() {
//        val attrs = AudioAttributes.Builder()
//            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
//            .setUsage(C.USAGE_MEDIA)
//            .build()
//        exoPlayer.setAudioAttributes(attrs, true)
//        exoPlayer.setHandleAudioBecomingNoisy(true)
//        exoPlayer.addListener(playerListener) // Usar la instancia del listener definida arriba
//    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(chan)
        }
    }

    private fun initializeMediaSession() {
        val pendingIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
            ?: PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) =
        mediaSession

    private fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause()
        else {
            if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    private fun playNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        }
    }

    private fun playPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.play()
        } else if (exoPlayer.isCurrentMediaItemSeekable) {
            exoPlayer.seekTo(0)
            exoPlayer.play()
        }
    }

    private var lastProcessedWidgetUpdateTimeMs = 0L
    private var lastWidgetArtUriString = ""
    private var debouncedWidgetUpdateJob: Job? = null // Para actualizaciones completas (no solo progreso)
    private var lastWidgetIsPlayingState = false

    // LruCache para ByteArrays de carátulas, clave es URI en String
    private val widgetArtByteArrayCache = LruCache<String, ByteArray>(5)
    private var progressUpdateJob: Job? = null

    // Intervalo para la actualización del progreso del widget (en milisegundos)
    private val WIDGET_PROGRESS_UPDATE_INTERVAL_MS = 1000L // Actualizar cada segundo como sugiere el informe
    // Tiempo de debounce para actualizaciones de estado completas (en milisegundos)
    private val WIDGET_STATE_DEBOUNCE_MS = 300L


    private fun startProgressUpdater() {
        stopProgressUpdater()
        if (exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED) {
            progressUpdateJob = serviceScope.launch {
                while (true) {
                    // Esta llamada es específicamente para una actualización de progreso.
                    processWidgetUpdateInternal(isProgressOnlyUpdate = true)
                    delay(WIDGET_PROGRESS_UPDATE_INTERVAL_MS)
                }
            }
        }
    }

    private fun stopProgressUpdater() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    // Esta función se llamará cuando cambie el estado del reproductor (canción, play/pausa, etc.)
    // O cuando una acción del usuario (botón de siguiente/anterior) lo requiera.
    private fun requestWidgetFullUpdate() {
        debouncedWidgetUpdateJob?.cancel() // Cancela el job de debounce anterior
        debouncedWidgetUpdateJob = serviceScope.launch {
            delay(WIDGET_STATE_DEBOUNCE_MS) // Espera a que cesen los cambios rápidos
            // Esta llamada es para una actualización completa, no solo de progreso.
            processWidgetUpdateInternal(isProgressOnlyUpdate = false)
        }
    }

    // Listener de ExoPlayer
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            requestWidgetFullUpdate()
            if (playbackState == Player.STATE_READY && exoPlayer.isPlaying) {
                startProgressUpdater()
            } else if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                stopProgressUpdater()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            requestWidgetFullUpdate()
            if (isPlaying) {
                startProgressUpdater()
            } else {
                stopProgressUpdater()
            }
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            requestWidgetFullUpdate() // Actualización completa al cambiar de canción
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "PlayerError: ${error.message}")
            stopProgressUpdater()
            exoPlayer.stop() // Considerar limpiar la cola o manejar el error de forma más robusta
            exoPlayer.clearMediaItems()
            requestWidgetFullUpdate() // Reflejar el estado de error en el widget
        }
    }

    private fun initializePlayer() {
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        exoPlayer.setAudioAttributes(attrs, true)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        exoPlayer.addListener(playerListener) // Usar la instancia del listener definida arriba
    }

    // Esta es la función principal que ahora construye y envía la actualización del widget.
    // isProgressOnlyUpdate: true si esta actualización es solo para el progreso de la canción.
    //                       false si es una actualización completa (cambio de canción, estado play/pausa, etc.).
    private fun processWidgetUpdateInternal(isProgressOnlyUpdate: Boolean) {
        val currentTimeMs = System.currentTimeMillis()

        // Throttle: Si es una actualización de progreso y la última actualización (de cualquier tipo) fue hace muy poco, podríamos saltarla.
        // Sin embargo, el progressUpdateJob ya tiene un delay de 1 segundo.
        // Para actualizaciones completas, el debounce ya maneja la frecuencia.
        // Así que un throttle adicional aquí podría ser excesivo, a menos que observemos problemas.

        serviceScope.launch { // Asegurarse de que se ejecuta en el serviceScope
            val currentItem = exoPlayer.currentMediaItem
            val artUriString = currentItem?.mediaMetadata?.artworkUri?.toString().orEmpty()
            val isPlaying = exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED
            val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
            val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
            val currentPositionMs = exoPlayer.currentPosition
            val totalDurationMs = exoPlayer.duration.coerceAtLeast(0)

            var artBytes: ByteArray? = null
            val artUriChanged = artUriString != lastWidgetArtUriString

            // Solo cargar/recargar la imagen si:
            // 1. No es solo una actualización de progreso (es una actualización completa) Y la URI del arte es válida.
            // 2. O si la URI del arte ha cambiado (incluso para una actualización de progreso, aunque es menos común).
            if ((!isProgressOnlyUpdate && artUriString.isNotEmpty()) || (artUriString.isNotEmpty() && artUriChanged)) {
                artBytes = widgetArtByteArrayCache.get(artUriString) // Intentar desde la caché
                if (artBytes == null) {
                    Log.d(TAG, "Widget Art Cache MISS for URI: $artUriString. Loading image.")
                    artBytes = loadBitmapDataFromUri(applicationContext, Uri.parse(artUriString)) // Cargar desde la red/disco
                    if (artBytes != null) {
                        widgetArtByteArrayCache.put(artUriString, artBytes) // Añadir a la caché si es exitoso
                    } else {
                        Log.w(TAG, "Failed to load album art for widget for URI: $artUriString")
                    }
                } else {
                    Log.d(TAG, "Widget Art Cache HIT for URI: $artUriString.")
                }
            } else if (artUriString.isEmpty() && !isProgressOnlyUpdate) {
                // Si la URI está vacía y es una actualización completa, limpiar la caché de arte.
                widgetArtByteArrayCache.evictAll()
            } else if (isProgressOnlyUpdate && artUriString.isNotEmpty() && artUriString == lastWidgetArtUriString) {
                // Si es solo progreso y la URI no ha cambiado, usar la versión en caché si existe.
                artBytes = widgetArtByteArrayCache.get(artUriString)
            }


            // Determinar si realmente necesitamos enviar una actualización al widget.
            // Esto es crucial para evitar el "shedding".
            val significantChangeOccurred = lastWidgetIsPlayingState != isPlaying ||
                                            lastWidgetArtUriString != artUriString || // Si la URI del arte cambió
                                            (artBytes != null && widgetArtByteArrayCache.get(artUriString) != artBytes) || // Si el arte cargado es nuevo
                                            !isProgressOnlyUpdate // Siempre actualizar si es una actualización completa (debounced)

            // Para actualizaciones de progreso, solo actualizamos si ha pasado el tiempo mínimo.
            // Para actualizaciones completas (debounced), siempre actualizamos.
            val shouldSendUpdateToWidget = if (isProgressOnlyUpdate) {
                // Para actualizaciones de progreso, solo enviar si el estado de reproducción o la posición son significativamente diferentes
                // o si es la primera actualización de progreso después de un cambio de estado.
                // La cadencia ya está controlada por WIDGET_PROGRESS_UPDATE_INTERVAL_MS.
                // Aquí podríamos añadir una comprobación de si la posición ha cambiado realmente,
                // pero PlayerInfo siempre se construye con la posición actual.
                // El chequeo principal es si la información que se envía es diferente a la última enviada.
                // Por ahora, dejaremos que se envíe cada segundo si está reproduciendo.
                true // El progressUpdateJob ya controla la frecuencia a 1Hz.
            } else {
                significantChangeOccurred // Para actualizaciones completas, enviar si algo significativo cambió.
            }

            if (!shouldSendUpdateToWidget && isProgressOnlyUpdate) {
                 Log.v(TAG, "Skipping progress-only widget update as no significant change detected or too soon.")
                 return@launch // No enviar actualización al widget
            }

            val playerInfoData = PlayerInfo(
                songTitle = title,
                artistName = artist,
                isPlaying = isPlaying,
                albumArtUri = artUriString.ifEmpty { null },
                albumArtBitmapData = artBytes,
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs
            )

            // All Glance AppWidget operations should be off the main thread.
            val successfullySent = withContext(Dispatchers.IO) {
                try {
                    val glanceManager = GlanceAppWidgetManager(applicationContext)
                    val glanceIds = glanceManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
                    if (glanceIds.isNotEmpty()) {
                        glanceIds.forEach { id ->
                            updateAppWidgetState(
                                applicationContext,
                                PlayerInfoStateDefinition,
                                id
                            ) { playerInfoData } // Proporcionar PlayerInfo directamente
                        }
                        // Actualizar todos los widgets una vez después de establecer el estado
                        glanceIds.forEach { id -> PixelPlayGlanceWidget().update(applicationContext, id) }
                        Log.d(TAG, "Widget state data sent. Playing: $isPlaying, Title: $title, Art URI: $artUriString, Progress: $currentPositionMs")
                    } else {
                        Log.d(TAG, "No Glance widget IDs found. Skipping widget update.")
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget state", e)
                    false
                }
            }

            if (successfullySent || !isProgressOnlyUpdate) { // Actualizar los 'last' estados si el envío fue exitoso o si es una actualización completa (para mantener la coherencia del estado interno)
                lastProcessedWidgetUpdateTimeMs = currentTimeMs
                lastWidgetIsPlayingState = isPlaying
                if (artUriString.isNotEmpty() && artBytes != null) { // Solo actualizar lastWidgetArtUriString si el arte se cargó/obtuvo de caché con éxito
                    lastWidgetArtUriString = artUriString
                } else if (artUriString.isEmpty()) {
                    lastWidgetArtUriString = "" // Limpiar si la URI actual está vacía
                }
                // Si artUriString no está vacío pero artBytes es nulo (fallo de carga), lastWidgetArtUriString no cambia,
                // para que se intente cargar de nuevo en la próxima actualización completa.
            }
        }
    }

    // widgetArtByteArrayCache se define arriba
    // private val widgetArtCache = LruCache<String, ByteArray>(5) // Movido y renombrado

    private suspend fun loadBitmapDataFromUri(
        context: Context,
        uri: Uri
    ): ByteArray? = withContext(Dispatchers.IO) {
        val uriString = uri.toString() // Clave para la caché

        // La verificación de caché ahora se hace en processWidgetUpdateInternal antes de llamar a esta función.
        // Esta función ahora solo carga y comprime.

        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(Size(256, 256)) // Aumentado un poco para mejor calidad si el widget es grande
                .allowHardware(false)
                .bitmapConfig(Bitmap.Config.ARGB_8888)
                .build()
            val drawable = context.imageLoader.execute(request).drawable
            drawable?.let {
                val originalBitmap = it.toBitmap(
                    width = if (it.intrinsicWidth > 0) it.intrinsicWidth.coerceAtMost(256) else 128,
                    height = if (it.intrinsicHeight > 0) it.intrinsicHeight.coerceAtMost(256) else 128,
                    config = Bitmap.Config.ARGB_8888
                )

                val bitmapToCompress = if (!originalBitmap.isMutable) {
                    originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    originalBitmap
                }

                val stream = ByteArrayOutputStream()
                // Usar WEBP para mejor calidad/compresión si es posible, o JPEG con calidad decente.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmapToCompress.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
                } else {
                    bitmapToCompress.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                }
                val byteArray = stream.toByteArray()
                Log.d(TAG, "Bitmap loaded and compressed for URI: $uriString, size: ${byteArray.size} bytes")
                byteArray
            } ?: run {
                Log.w(TAG, "Drawable was null for URI: $uriString")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI: $uriString", e)
            null
        }
    }

    override fun onDestroy() {
        stopProgressUpdater()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
        serviceScope.cancel() // Cancelar el scope del servicio
    }
}