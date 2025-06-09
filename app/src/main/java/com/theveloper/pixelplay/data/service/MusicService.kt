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
import com.google.protobuf.ByteString
import com.theveloper.pixelplay.MainActivity
import com.theveloper.pixelplay.PlayerInfoProto
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
            // Removed @OptIn(UnstableApi::class) if stop(false) was the only reason
            override fun onPlaybackStateChanged(playbackState: Int) {
                // EOT specific logic removed from here
                updateWidgetFullState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean)                = updateWidgetFullState()
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

    // Track last widget update time to implement throttling
    private var lastWidgetUpdateTime = 0L
    private var lastWidgetArtUri = ""
    private var pendingWidgetUpdate = false
    private var widgetUpdateJob: Job? = null
    private var lastWidgetPlayState = false

    // Throttle widget updates to reduce image decoding frequency
    private fun updateWidgetFullState() {
        val currentTime = System.currentTimeMillis()
        val currentItem = exoPlayer.currentMediaItem
        val uriString = currentItem?.mediaMetadata?.artworkUri?.toString().orEmpty()
        val isPlaying = exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_ENDED
        
        // Only process immediate updates for:
        // 1. Play/pause state changes (affects UI controls)
        // 2. Media item changes (new song)
        // 3. First update after a long time (>5 seconds)
        val isPlayStateChange = lastWidgetPlayState != isPlaying
        val isMediaItemChange = lastWidgetArtUri != uriString
        val isTimeToUpdate = currentTime - lastWidgetUpdateTime > 5000
        
        if (isPlayStateChange || isMediaItemChange || isTimeToUpdate) {
            // Cancel any pending updates
            widgetUpdateJob?.cancel()
            pendingWidgetUpdate = false
            
            // Update immediately
            processWidgetUpdate(uriString, isPlaying)
        } else if (!pendingWidgetUpdate) {
            // Schedule a delayed update to avoid too frequent updates
            pendingWidgetUpdate = true
            widgetUpdateJob = serviceScope.launch {
                delay(1000) // Wait 1 second before updating
                if (pendingWidgetUpdate) {
                    processWidgetUpdate(uriString, isPlaying)
                    pendingWidgetUpdate = false
                }
            }
        }
        
        // Always update the last play state
        lastWidgetPlayState = isPlaying
    }

    private fun processWidgetUpdate(uriString: String, isPlaying: Boolean) {
        serviceScope.launch {
            // --- 1) Toda lectura de exoPlayer en Main ---
            val currentItem = exoPlayer.currentMediaItem
            val title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
            val artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
            val currentPositionMs = exoPlayer.currentPosition
            val totalDurationMs = exoPlayer.duration

            // --- 2) Carga de bitmap en I/O solo si ha cambiado ---
            val artBytes = if (uriString.isNotEmpty() && uriString != lastWidgetArtUri) {
                withContext(Dispatchers.IO) {
                    loadBitmapDataFromUri(applicationContext, Uri.parse(uriString))
                }
            } else null

            // --- 3) Construcción del proto en Main ---
            val proto = PlayerInfoProto.newBuilder()
                .setSongTitle(title)
                .setArtistName(artist)
                .setIsPlaying(isPlaying)
                .setAlbumArtUri(uriString)
                .setCurrentPositionMs(currentPositionMs)
                .setTotalDurationMs(totalDurationMs)
                .apply { 
                    // Only update bitmap data if it changed
                    if (artBytes != null) {
                        albumArtBitmapData = ByteString.copyFrom(artBytes)
                    } else if (uriString != lastWidgetArtUri) {
                        // Clear bitmap data if URI changed but we couldn't load new bitmap
                        albumArtBitmapData = ByteString.EMPTY
                    }
                }
                .build()

            // --- 4) Guardar en DataStore en I/O ---
            val glanceManager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = glanceManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
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
            
            // Update tracking variables
            lastWidgetUpdateTime = System.currentTimeMillis()
            lastWidgetArtUri = uriString
        }
    }

    // Cache for widget album art to avoid repeated decoding
    private val widgetArtCache = LruCache<String, ByteArray>(5) // Cache last 5 album arts
    
    private suspend fun loadBitmapDataFromUri(
        context: Context,
        uri: Uri
    ): ByteArray? = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        
        // Check cache first
        widgetArtCache.get(uriString)?.let { return@withContext it }
        
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(Size(128, 128)) // Request a reasonably sized image from Coil
                .allowHardware(false) // Important for software bitmap access
                .bitmapConfig(Bitmap.Config.ARGB_8888) // Ensure config is compatible with JPEG (which ignores alpha)
                .build()
            val drawable = context.imageLoader.execute(request).drawable
            drawable?.let {
                // Ensure we get a bitmap that we can compress. ARGB_8888 is fine for JPEG, alpha is ignored.
                val originalBitmap = it.toBitmap(
                    width = if (it.intrinsicWidth > 0) it.intrinsicWidth.coerceAtMost(256) else 128, // Cap size before compression
                    height = if (it.intrinsicHeight > 0) it.intrinsicHeight.coerceAtMost(256) else 128,
                    config = Bitmap.Config.ARGB_8888 // Use ARGB_8888, JPEG will handle it
                )

                // Create a mutable copy if necessary, though toBitmap should often provide a new one.
                // For safety, especially if originalBitmap might be recycled by Coil or is immutable.
                val bitmapToCompress = if (!originalBitmap.isMutable) {
                    originalBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    originalBitmap
                }

                val stream = ByteArrayOutputStream()
                // Use JPEG for better compression for photographic album art
                bitmapToCompress.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                
                // If originalBitmap was copied to bitmapToCompress, and originalBitmap is not needed elsewhere,
                // it could be recycled if we were manually managing it. However, Coil manages its own cache,
                // and originalBitmap comes from Coil's drawable. Let GC and Coil handle originalBitmap.
                // bitmapToCompress (if it's a copy) will also be GC'd after stream.toByteArray().

                stream.toByteArray()
            }
        } catch (e: Exception) { Log.e(TAG, "Failed to load bitmap from URI: $uri", e); null }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}