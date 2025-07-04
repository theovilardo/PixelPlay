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
import com.theveloper.pixelplay.data.repository.MusicRepository
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
    @Inject lateinit var musicRepository: MusicRepository
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
            PlayerActions.FAVORITE   -> toggleFavorite()
            // No es necesario llamar a requestWidgetFullUpdate() aquí explícitamente
            // si las funciones togglePlayPause, playNext, playPrevious
            // ya modifican el estado de ExoPlayer, lo que activará el playerListener.
            // Sin embargo, para asegurar la inmediatez de la respuesta a la acción del usuario,
            // una llamada directa puede ser beneficiosa, y el debounce se encargará de la eficiencia.
        }
        requestWidgetFullUpdate(force = true) // Solicitar actualización después de una acción. El debounce lo manejará.
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

    private fun toggleFavorite() {
        val currentSongId = exoPlayer.currentMediaItem?.mediaId ?: return
        serviceScope.launch {
            val newFavoriteStatus = musicRepository.toggleFavoriteStatus(currentSongId)
            // Actualizar el isFavoriteMap local para reflejar inmediatamente el cambio,
            // aunque la SSoT vendrá del repositorio en la próxima actualización completa del widget.
            // Esto es opcional si confías en que requestWidgetFullUpdate será lo suficientemente rápido.
            isFavoriteMap[currentSongId] = newFavoriteStatus
            Log.d(TAG, "Toggled favorite for song $currentSongId to $newFavoriteStatus")
            // No es necesario llamar a requestWidgetFullUpdate aquí si la acción ya lo hace.
            // handleIntentAction ya llama a requestWidgetFullUpdate(force = true)
        }
    }

    // isFavoriteMap se usará como una caché temporal o para acceso rápido,
    // pero la fuente de verdad es el repositorio.
    private val isFavoriteMap = mutableMapOf<String, Boolean>()


    private var lastProcessedWidgetUpdateTimeMs = 0L
    private var lastWidgetArtUriString = ""
    private var debouncedWidgetUpdateJob: Job? = null // Para actualizaciones completas (no solo progreso)
    private var lastWidgetIsPlayingState = false
    private var lastWidgetFavoriteState = false

    // LruCache para ByteArrays de carátulas, clave es URI en String
    private val widgetArtByteArrayCache = LruCache<String, ByteArray>(5)
    // Tiempo de debounce para actualizaciones de estado completas (en milisegundos)
    private val WIDGET_STATE_DEBOUNCE_MS = 300L

    // Esta función se llamará cuando cambie el estado del reproductor (canción, play/pausa, etc.)
    // O cuando una acción del usuario (botón de siguiente/anterior) lo requiera.
    private fun requestWidgetFullUpdate(force: Boolean = false) {
        debouncedWidgetUpdateJob?.cancel() // Cancela el job de debounce anterior
        debouncedWidgetUpdateJob = serviceScope.launch {
            if (!force) {
                delay(WIDGET_STATE_DEBOUNCE_MS) // Espera a que cesen los cambios rápidos
            }
            // Esta llamada es para una actualización completa, no solo de progreso.
            processWidgetUpdateInternal()
        }
    }

    // Listener de ExoPlayer
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            requestWidgetFullUpdate()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            requestWidgetFullUpdate()
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            requestWidgetFullUpdate() // Actualización completa al cambiar de canción
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "PlayerError: ${error.message}")
            exoPlayer.stop() // Considerar limpiar la cola o manejar el error de forma más robusta
            exoPlayer.clearMediaItems()
            requestWidgetFullUpdate(force = true) // Reflejar el estado de error en el widget
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
    private fun processWidgetUpdateInternal() {
        val currentTimeMs = System.currentTimeMillis()

        // Throttle: Si es una actualización de progreso y la última actualización (de cualquier tipo) fue hace muy poco, podríamos saltarla.
        // Sin embargo, el progressUpdateJob ya tiene un delay de 1 segundo.
        // Para actualizaciones completas, el debounce ya maneja la frecuencia.
        // Así que un throttle adicional aquí podría ser excesivo, a menos que observemos problemas.

        serviceScope.launch { // Asegurarse de que se ejecuta en el serviceScope
            val currentItem = exoPlayer.currentMediaItem
            val isPlaying = exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED
            val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
            val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
            val currentPositionMs = exoPlayer.currentPosition
            val totalDurationMs = exoPlayer.duration.coerceAtLeast(0)

            var artBytes: ByteArray? = null
            var artUriStringForPlayerInfo: String? = null // Para PlayerInfo.albumArtUri

            if (currentItem == null) {
                Log.d(TAG, "processWidgetUpdateInternal: currentItem is null. artBytes will be null.")
            } else {
                // 1. Intentar obtener artworkData directamente del MediaItem
                artBytes = currentItem.mediaMetadata.artworkData
                if (artBytes != null && artBytes.isNotEmpty()) {
                    Log.d(TAG, "processWidgetUpdateInternal: Got artBytes directly from MediaMetadata, size: ${artBytes.size}")
                    // Si tenemos artworkData, la URI original podría no ser relevante o ser diferente.
                    // Podríamos intentar obtenerla también para PlayerInfo, o dejarla null.
                    artUriStringForPlayerInfo = currentItem.mediaMetadata.artworkUri?.toString()
                    Log.d(TAG, "processWidgetUpdateInternal: MediaMetadata artworkUri (if any, with direct data): $artUriStringForPlayerInfo")
                } else {
                    Log.d(TAG, "processWidgetUpdateInternal: artworkData is null or empty in MediaMetadata. Trying artworkUri.")
                    // 2. Si no hay artworkData, intentar con artworkUri
                    val artworkUriFromMetadata = currentItem.mediaMetadata.artworkUri
                    artUriStringForPlayerInfo = artworkUriFromMetadata?.toString() // Guardar para PlayerInfo

                    if (artworkUriFromMetadata != null) {
                        val localArtUriString = artworkUriFromMetadata.toString() // Variable local para esta rama
                        Log.d(TAG, "processWidgetUpdateInternal: artworkUri found in MediaMetadata: $localArtUriString")
                        artBytes = widgetArtByteArrayCache.get(localArtUriString) // Intentar desde la caché del servicio
                        if (artBytes != null) {
                            Log.d(TAG, "processWidgetUpdateInternal: Widget Art Cache HIT for URI: $localArtUriString, size: ${artBytes.size}")
                        } else {
                            Log.d(TAG, "processWidgetUpdateInternal: Widget Art Cache MISS for URI: $localArtUriString. Loading image via loadBitmapDataFromUri.")
                            artBytes = loadBitmapDataFromUri(applicationContext, artworkUriFromMetadata)
                            if (artBytes != null) {
                                widgetArtByteArrayCache.put(localArtUriString, artBytes)
                                Log.d(TAG, "processWidgetUpdateInternal: Loaded and cached artBytes from URI: $localArtUriString, size: ${artBytes.size}")
                            } else {
                                Log.w(TAG, "processWidgetUpdateInternal: Failed to load album art from URI: $localArtUriString (loadBitmapDataFromUri returned null)")
                            }
                        }
                    } else {
                        Log.d(TAG, "processWidgetUpdateInternal: artworkUri is also null in MediaMetadata.")
                    }
                }
            }

            // Log final sobre artBytes antes de construir PlayerInfo
            if (artBytes == null) {
                Log.d(TAG, "processWidgetUpdateInternal: Final artBytes is NULL.")
            } else {
                Log.d(TAG, "processWidgetUpdateInternal: Final artBytes is NOT NULL, size = ${artBytes.size}")
            }

            // Obtener el estado real de favorito
            var actualIsFavorite = false
            currentItem?.mediaId?.let { songId ->
                actualIsFavorite = isFavoriteMap[songId] ?: false
            }
            // El log para currentItem null ya está arriba.
            // No es necesario loguear artUriString aquí ya que su propósito principal era cargar artBytes.
            // artUriStringForPlayerInfo es lo que se pasará a PlayerInfo.


            // Determinar si realmente necesitamos enviar una actualización al widget.
            val artActuallyChanged = (artUriStringForPlayerInfo != lastWidgetArtUriString) || (artBytes != null && lastWidgetArtUriString.isEmpty()) || (artBytes == null && lastWidgetArtUriString.isNotEmpty())
            // Considera artActuallyChanged si la URI cambió, o si el arte apareció (artBytes != null) cuando antes no había URI,
            // o si el arte desapareció (artBytes == null) cuando antes sí había URI.

            val significantChangeOccurred = lastWidgetIsPlayingState != isPlaying ||
                                            artActuallyChanged ||
                                            lastWidgetFavoriteState != actualIsFavorite
                                            // No necesitamos comparar el contenido de artBytes aquí si artActuallyChanged ya cubre los casos relevantes.
                                            // La URI es la clave principal para el arte. Si la URI es la misma, asumimos que el arte es el mismo.
                                            // El widgetArtByteArrayCache en el servicio ayuda a no recargar innecesariamente.

            // Si es una actualización forzada (ej. después de una acción del usuario), siempre enviar.
            // La variable 'force' no está disponible aquí directamente, pero requestWidgetFullUpdate la usa para el debounce.
            // Si estamos aquí después de un debounce, es una actualización de estado.
            // Si estamos aquí por una actualización de progreso (no implementada por separado aún), el throttling sería diferente.

            if (!significantChangeOccurred && exoPlayer.playbackState != Player.STATE_ENDED && exoPlayer.duration > 0) {
                // Si no hubo cambios significativos Y la canción está en curso (no terminada, con duración),
                // aún podríamos necesitar enviar una actualización de PROGRESO.
                // Sin embargo, el widget actualmente no se actualiza solo por progreso mediante este path.
                // Este path es para actualizaciones de ESTADO.
                // Si el progreso es el único cambio, y no hay un path dedicado para actualizaciones de progreso,
                // podríamos decidir no actualizar.
                // Pero si el widget debe mostrar progreso actualizado, este chequeo es muy agresivo.
                // Por ahora, si no hay cambio de estado, no actualizamos. El progreso se actualizará
                // cuando haya un cambio de estado o la próxima vez que el widget se fuerce a actualizar.
                 Log.v(TAG, "Skipping widget state update as no significant metadata/state change detected.")
                 // No retornar aquí aún si queremos que el progreso se actualice si es el único cambio.
                 // return@launch // Descomentar si queremos ser estrictos y no enviar nada si solo el progreso cambió.
            }


            // Construir PlayerInfo siempre, la decisión de enviar se toma después.
            val playerInfoData = PlayerInfo(
                songTitle = title,
                artistName = artist,
                isPlaying = isPlaying,
                albumArtUri = artUriStringForPlayerInfo, // Usar la URI obtenida, puede ser null
                albumArtBitmapData = artBytes,
                currentPositionMs = currentPositionMs,
                totalDurationMs = totalDurationMs,
                isFavorite = actualIsFavorite // Usar el estado de favorito obtenido
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
                        Log.d(TAG, "Widget state data sent. Playing: $isPlaying, Title: $title, Art URI: $artUriStringForPlayerInfo, Progress: $currentPositionMs")
                    } else {
                        Log.d(TAG, "No Glance widget IDs found. Skipping widget update.")
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget state", e)
                    false
                }
            }

            if (successfullySent) { // Actualizar los 'last' estados si el envío fue exitoso
                lastProcessedWidgetUpdateTimeMs = currentTimeMs
                lastWidgetIsPlayingState = isPlaying
                lastWidgetFavoriteState = actualIsFavorite
                // Actualizar lastWidgetArtUriString con la URI que se usó/intentó para PlayerInfo
                lastWidgetArtUriString = artUriStringForPlayerInfo ?: ""
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
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
        serviceScope.cancel() // Cancelar el scope del servicio
    }
}