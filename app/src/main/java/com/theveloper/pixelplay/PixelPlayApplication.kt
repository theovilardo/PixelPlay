package com.theveloper.pixelplay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import com.theveloper.pixelplay.data.network.youtube.OkHttpDownloader
import com.theveloper.pixelplay.utils.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import coil.ImageLoader
import coil.ImageLoaderFactory
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import timber.log.Timber
import org.schabi.newpipe.extractor.NewPipe

@HiltAndroidApp
class PixelPlayApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "pixelplay_music_channel"
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize critical components first
        if (checkSelfPermission(android.Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            try {
                val downloader = OkHttpDownloader.getInstance()
                NewPipe.init(downloader)
                Timber.d("NewPipe initialized successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize NewPipe")
            }
        } else {
            Timber.e("Internet permission not granted")
        }
        
        // Install crash handler early
        CrashHandler.install(this)

        // Initialize notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "PixelPlayer Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Defer non-critical initializations to background thread
        Thread {
            // Initialize any background services here if needed
            // This prevents blocking the main thread during app start
        }.start()
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get()
    }

    // 3. Sobrescribe el método para proveer la configuración de WorkManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

}