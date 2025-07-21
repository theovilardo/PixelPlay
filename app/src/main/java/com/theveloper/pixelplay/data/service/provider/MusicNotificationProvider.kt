package com.theveloper.pixelplay.data.service.provider

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaSession.Token
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.Player
import androidx.media3.ui.PlayerNotificationManager
import coil.imageLoader
import coil.request.ImageRequest
import com.theveloper.pixelplay.PixelPlayApplication
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.service.MusicService
import dagger.hilt.android.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.media3.common.util.UnstableApi
class MusicNotificationProvider(private val context: Context, private val mediaSessionToken: Token) :
    PlayerNotificationManager.NotificationListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val notificationManager = PlayerNotificationManager.Builder(
        context,
        MusicService.NOTIFICATION_ID,
        PixelPlayApplication.NOTIFICATION_CHANNEL_ID
    )
        .setMediaDescriptionAdapter(Adapter())
        .setNotificationListener(this)
        .build()

    fun showNotificationForPlayer(player: Player) {
        notificationManager.setPlayer(player)
        notificationManager.setMediaSessionToken(mediaSessionToken)
    }

    fun hideNotification() {
        notificationManager.setPlayer(null)
    }

    override fun onNotificationPosted(notificationId: Int, notification: android.app.Notification, ongoing: Boolean) {
        if (ongoing) {
            // El servicio debe estar en primer plano
            (context as MusicService).startForeground(notificationId, notification)
        }
    }

    override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
        (context as MusicService).stopSelf()
    }

    private inner class Adapter : PlayerNotificationManager.MediaDescriptionAdapter {
        private var currentBitmap: Bitmap? = null

        override fun getCurrentContentTitle(player: Player): CharSequence {
            return player.mediaMetadata.title ?: "Unknown Title"
        }

        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            // Intent para abrir la app al tocar la notificación
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            return if (intent != null) {
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            } else {
                null
            }
        }

        override fun getCurrentContentText(player: Player): CharSequence? {
            return player.mediaMetadata.artist ?: "Unknown Artist"
        }

        override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? {
            val artworkUri = player.mediaMetadata.artworkUri
            if (artworkUri == null) {
                currentBitmap = null
                return null
            }

            serviceScope.launch {
                currentBitmap = loadBitmapFromUri(artworkUri)
                currentBitmap?.let { callback.onBitmap(it) }
            }
            // Retorna un placeholder mientras se carga la imagen real
            return BitmapFactory.decodeResource(context.resources, R.drawable.genre_default)
        }

        private suspend fun loadBitmapFromUri(uri: android.net.Uri): Bitmap? = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(256, 256) // Tamaño razonable para una notificación
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).drawable?.toBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
}