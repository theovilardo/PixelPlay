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
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.google.protobuf.ByteString
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.PlayerInfoProto
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.glancewidget.PixelPlayGlanceWidget
import com.theveloper.pixelplay.ui.glancewidget.PlayerActions
import com.theveloper.pixelplay.ui.glancewidget.PlayerInfoStateDefinition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        initializePlayer()
        initializeMediaSession()
        // Solo aquí arrancamos en foreground
        startForeground(NOTIF_ID, buildServiceNotification())
        Log.d(TAG, "Service started in foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.action?.let { handleIntentAction(it) }
        return START_STICKY
    }

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
        }
        updateWidgetFullState()
    }

    private fun initializePlayer() {
        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
        exoPlayer.setAudioAttributes(attrs, true)
        exoPlayer.setHandleAudioBecomingNoisy(true)
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean)                = updateWidgetFullState()
            override fun onPlaybackStateChanged(state: Int)                   = updateWidgetFullState()
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) = updateWidgetFullState()
            override fun onPlayerError(error: PlaybackException) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                updateWidgetFullState()
            }
        })
    }

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

    private fun updateWidgetFullState() {
        serviceScope.launch {
            // --- 1) Toda lectura de exoPlayer en Main ---
            val currentItem = exoPlayer.currentMediaItem
            val title       = currentItem?.mediaMetadata?.title?.toString().orEmpty()
            val artist      = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
            val uriString   = currentItem?.mediaMetadata?.artworkUri?.toString().orEmpty()
            val isPlaying   = exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED

            // --- 2) Carga de bitmap en I/O ---
            val artBytes = withContext(Dispatchers.IO) {
                if (uriString.isNotEmpty()) {
                    loadBitmapDataFromUri(applicationContext, Uri.parse(uriString))
                } else null
            }

            // --- 3) Construcción del proto en Main ---
            val proto = PlayerInfoProto.newBuilder()
                .setSongTitle(title)
                .setArtistName(artist)
                .setIsPlaying(isPlaying)
                .setAlbumArtUri(uriString)
                .apply { artBytes?.let { albumArtBitmapData = ByteString.copyFrom(it) } }
                .build()

            // --- 4) Guardar en DataStore en I/O ---
            val glanceManager = GlanceAppWidgetManager(applicationContext)
            val glanceIds     = glanceManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
            withContext(Dispatchers.IO) {
                glanceIds.forEach { id ->
                    updateAppWidgetState(
                        applicationContext,
                        PlayerInfoStateDefinition,
                        id
                    ) { proto }
                }
            }

            // --- 5) Actualizar UI del widget en Main ---
            glanceIds.forEach { id ->
                PixelPlayGlanceWidget().update(applicationContext, id)
            }
        }
    }

    private suspend fun loadBitmapDataFromUri(
        context: Context,
        uri: Uri
    ): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val req = ImageRequest.Builder(context)
                .data(uri)
                .size(Size(128, 128))
                .allowHardware(false)
                .build()
            val drawable = context.imageLoader.execute(req).drawable ?: return@withContext null
            val bmp = drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
            ByteArrayOutputStream().use { stream ->
                bmp.compress(Bitmap.CompressFormat.PNG, 85, stream)
                return@withContext stream.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap: $e")
            null
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}


//@AndroidEntryPoint
//class MusicService : MediaSessionService() {
//    @Inject lateinit var exoPlayer: ExoPlayer
//    private var mediaSession: MediaSession? = null
//    private val serviceJob = SupervisorJob()
//    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
//
//    companion object {
//        private const val TAG = "MusicService_PixelPlay"
//        private const val NOTIFICATION_CHANNEL_ID = "pixelplay_media_playback_service"
//        private const val NOTIFICATION_CHANNEL_NAME = "PixelPlay Reproducción"
//        private const val NOTIFICATION_ID = 101 // Asegúrate que sea un ID único para la notificación del servicio
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.d(TAG, "onCreate: Service creating...")
//        try {
//            initializePlayer()
//            createNotificationChannel() // Asegúrate que esto se llame
//            initializeMediaSession()
//            Log.d(TAG, "onCreate: Service creation successful.")
//        } catch (e: Exception) {
//            Log.e(TAG, "onCreate: CRITICAL Error: ${e.message}", e)
//        }
//    }
//
//    private fun initializePlayer() {
//        Log.d(TAG, "initializePlayer: Initializing ExoPlayer...")
//        val audioAttributes = AudioAttributes.Builder()
//            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
//            .setUsage(C.USAGE_MEDIA)
//            .build()
//        exoPlayer.setAudioAttributes(audioAttributes, true)
//        exoPlayer.setHandleAudioBecomingNoisy(true)
//
//        exoPlayer.addListener(object : Player.Listener {
//            override fun onPlayerError(error: PlaybackException) {
//                Log.e(TAG, "ExoPlayer Error: ${error.errorCodeName}, ${error.message}", error)
//                try {
//                    exoPlayer.stop()
//                    exoPlayer.clearMediaItems()
//                } catch (e: Exception) { Log.e(TAG, "Error handling player error: $e") }
//                updateWidgetFullState()
//            }
//
//            override fun onPlaybackStateChanged(playbackState: Int) {
//                Log.d(TAG, "PlaybackStateChanged: $playbackState")
//                updateWidgetFullState()
//            }
//
//            override fun onIsPlayingChanged(isPlaying: Boolean) {
//                Log.d(TAG, "IsPlayingChanged: $isPlaying")
//                updateWidgetFullState()
//            }
//
//            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
//                super.onMediaItemTransition(mediaItem, reason)
//                Log.d(TAG, "MediaItemTransition: New mediaId: ${mediaItem?.mediaId}, Reason: $reason")
//                updateWidgetFullState()
//            }
//        })
//        Log.d(TAG, "initializePlayer: ExoPlayer ready.")
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                NOTIFICATION_CHANNEL_ID,
//                NOTIFICATION_CHANNEL_NAME,
//                NotificationManager.IMPORTANCE_LOW // O IMPORTANCE_DEFAULT si quieres que sea más visible
//            ).apply {
//                description = "Notificaciones de reproducción de PixelPlay"
//                setSound(null, null) // Sin sonido para notificaciones de foreground persistentes
//            }
//            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            manager.createNotificationChannel(channel)
//            Log.d(TAG, "Notification channel created: $NOTIFICATION_CHANNEL_ID")
//        }
//    }
//
//    private fun initializeMediaSession() {
//        Log.d(TAG, "initializeMediaSession: Initializing MediaSession...")
//        val sessionActivityPendingIntent = packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
//            PendingIntent.getActivity(this, 0, sessionIntent, PendingIntent.FLAG_IMMUTABLE)
//        } ?: PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
//
//        mediaSession = MediaSession.Builder(this, exoPlayer)
//            .setSessionActivity(sessionActivityPendingIntent)
//            .build()
//        Log.d(TAG, "initializeMediaSession: MediaSession instance created.")
//    }
//
//    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
//        Log.d(TAG, "onGetSession for ${controllerInfo.packageName}")
//        return mediaSession
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        val action = intent?.action
//        Log.i(TAG, "onStartCommand: Received start id $startId, action: $action, flags: $flags")
//
//        if (action != null) {
//            when (action) {
//                PlayerActions.PLAY_PAUSE -> {
//                    Log.d(TAG, "Handling PLAY_PAUSE. Currently: isPlaying=${exoPlayer.isPlaying}, playWhenReady=${exoPlayer.playWhenReady}, playbackState=${exoPlayer.playbackState}")
//                    if (exoPlayer.isPlaying) {
//                        exoPlayer.pause()
//                    } else {
//                        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
//                            exoPlayer.prepare()
//                        }
//                        exoPlayer.play()
//                    }
//                }
//                PlayerActions.NEXT -> {
//                    Log.d(TAG, "Handling NEXT.")
//                    if (exoPlayer.hasNextMediaItem()) {
//                        exoPlayer.seekToNextMediaItem()
//                        if(!exoPlayer.isPlaying) exoPlayer.play() // Asegurar que empiece a reproducir
//                    } else {
//                        Log.d(TAG, "No next item to play.")
//                        // Opcional: ir al inicio de la cola si no hay siguiente y no está en modo repeat all
//                        if (exoPlayer.mediaItemCount > 0 && exoPlayer.repeatMode != Player.REPEAT_MODE_ALL) {
//                            exoPlayer.seekToDefaultPosition(0)
//                            // No llamar a play() aquí si se quiere que se quede pausado al final de la cola
//                        }
//                    }
//                }
//                PlayerActions.PREVIOUS -> {
//                    Log.d(TAG, "Handling PREVIOUS.")
//                    if (exoPlayer.hasPreviousMediaItem()) {
//                        exoPlayer.seekToPreviousMediaItem()
//                        if(!exoPlayer.isPlaying) exoPlayer.play()
//                    } else if (exoPlayer.isCurrentMediaItemSeekable) {
//                        exoPlayer.seekTo(0) // Reiniciar si es la primera canción
//                        if(!exoPlayer.isPlaying) exoPlayer.play()
//                    } else {
//                        Log.d(TAG, "No previous item or current not seekable.")
//                    }
//                }
//            }
//            // Forzar una actualización del widget después de una acción manual,
//            // ya que los listeners de ExoPlayer podrían no activarse si el estado no cambia
//            // (ej. tocar next en la última canción sin repeat).
//            updateWidgetFullState()
//        }
//        return super.onStartCommand(intent, flags, startId)
//    }
//
//    private fun updateWidgetFullState() {
//        Log.d(TAG, "updateWidgetFullState: Preparing to update widget state.")
//        serviceScope.launch {
//            val currentMediaItem = exoPlayer.currentMediaItem
//            val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
//            val artist = currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
//            val albumArtUri = currentMediaItem?.mediaMetadata?.artworkUri
//            val isPlaying = exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED
//
//            Log.d(TAG, "Extracted from ExoPlayer: Title='$title', Artist='$artist', isPlaying=$isPlaying, ArtUri='$albumArtUri'")
//
//            var albumArtBitmapData: ByteArray? = null
//            if (albumArtUri != null) {
//                try {
//                    albumArtBitmapData = loadBitmapDataFromUri(applicationContext, albumArtUri)
//                    Log.d(TAG, "Album art loaded for widget, size: ${albumArtBitmapData?.size ?: 0} bytes")
//                } catch (e: Exception) { Log.e(TAG, "Error loading album art for widget: $e") }
//            } else {
//                Log.d(TAG, "No album art URI for current media item.")
//            }
//
//            val playerInfo = PlayerInfoProto.newBuilder()
//                .setSongTitle(title)
//                .setArtistName(artist)
//                .setIsPlaying(isPlaying)
//                .setAlbumArtUri(albumArtUri?.toString() ?: "")
//                .apply { albumArtBitmapData?.let { this.albumArtBitmapData = ByteString.copyFrom(it) } }
//                .build()
//
//            Log.d(TAG, "PlayerInfoProto to save: title='${playerInfo.songTitle}', artist='${playerInfo.artistName}', isPlaying=${playerInfo.isPlaying}, hasBitmap=${playerInfo.albumArtBitmapData != ByteString.EMPTY}")
//
//            val glanceAppWidgetManager = GlanceAppWidgetManager(applicationContext)
//            val glanceIds = glanceAppWidgetManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
//            if (glanceIds.isEmpty()) {
//                Log.d(TAG, "No glanceIds found for PixelPlayGlanceWidget.")
//                return@launch
//            }
//            glanceIds.forEach { glanceId ->
//                try {
//                    updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, glanceId) { playerInfo }
//                    PixelPlayGlanceWidget().update(applicationContext, glanceId)
//                    Log.d(TAG, "Widget state updated and UI refresh requested for glanceId: $glanceId")
//                } catch (e: Exception) { Log.e(TAG, "Error updating widget $glanceId: ${e.message}", e) }
//            }
//        }
//    }
//
//    private suspend fun loadBitmapDataFromUri(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
//        try {
//            val request = ImageRequest.Builder(context).data(uri).size(Size(128, 128)).allowHardware(false).build()
//            val drawable = context.imageLoader.execute(request).drawable
//            drawable?.let {
//                val originalWidth = if (it.intrinsicWidth > 0) it.intrinsicWidth else 128
//                val originalHeight = if (it.intrinsicHeight > 0) it.intrinsicHeight else 128
//                // Create the initial bitmap from the drawable
//                val originalBitmap = it.toBitmap(width = originalWidth, height = originalHeight, config = Bitmap.Config.ARGB_8888)
//
//                // Create a mutable copy to ensure we're not working with a bitmap Coil might recycle.
//                // This copy is the one we'll compress and then let be garbage collected.
//                val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
//
//                // It's generally good practice to recycle bitmaps you create if they are different from the source,
//                // but in this case, originalBitmap is derived from Coil's drawable. Coil might have its own lifecycle
//                // management. Let's avoid recycling originalBitmap directly here to prevent double recycling
//                // if Coil also tries to manage it or if toBitmap() sometimes returns a shared instance.
//                // The primary goal is to ensure mutableBitmap is valid for compression.
//
//                val stream = ByteArrayOutputStream()
//                mutableBitmap.compress(Bitmap.CompressFormat.PNG, 85, stream) // Compress the mutable copy
//                // No explicit recycle() on mutableBitmap here. It's a local var and will be GC'd.
//                // The underlying data of originalBitmap will be handled by Coil/GC.
//                stream.toByteArray()
//            }
//        } catch (e: Exception) { Log.e(TAG, "Failed to load bitmap from URI: $uri", e); null }
//    }
//
//    override fun onDestroy() {
//        Log.d(TAG, "onDestroy: Service destroying...")
//        serviceJob.cancel()
//        mediaSession?.release()
//        mediaSession = null
//        Log.d(TAG, "onDestroy: Service destroyed completely.")
//        super.onDestroy()
//    }
//}