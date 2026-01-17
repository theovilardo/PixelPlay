package com.theveloper.pixelplay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.utils.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import android.os.StrictMode // Importar StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import timber.log.Timber

@HiltAndroidApp
class PixelPlayApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    @Inject
    lateinit var telegramStreamProxy: com.theveloper.pixelplay.data.telegram.TelegramStreamProxy
    
    @Inject
    lateinit var telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager
    
    @Inject
    lateinit var telegramCoilFetcherFactory: com.theveloper.pixelplay.data.image.TelegramCoilFetcher.Factory

    // AÑADE EL COMPANION OBJECT
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelplay_music_channel"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Install crash handler to catch and save uncaught exceptions
        CrashHandler.install(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PixelPlayer Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        telegramStreamProxy.start()
        
        // Trigger robust cache cleanup on startup to remove orphaned files from previous sessions
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // Wait a bit for TDLib to initialize
                kotlinx.coroutines.delay(5000)
                Timber.d("Performing startup Telegram cache cleanup...")
                telegramCacheManager.clearTdLibCache()
                telegramCacheManager.trimEmbeddedArtCache()
            } catch (e: Exception) {
                Timber.e(e, "Error during startup cache cleanup")
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get().newBuilder()
            .components {
                add(telegramCoilFetcherFactory)
            }
            .build()
    }

    // 3. Sobrescribe el método para proveer la configuración de WorkManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}