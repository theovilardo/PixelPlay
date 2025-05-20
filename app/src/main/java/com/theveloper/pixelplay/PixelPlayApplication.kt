package com.theveloper.pixelplay

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import android.os.StrictMode // Importar StrictMode

@HiltAndroidApp
class PixelPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Habilitar StrictMode solo en builds de depuración
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork() // O detectAll() para todo
                    .penaltyLog()    // Registrar violaciones en Logcat
                    // .penaltyDeath() // Descomentar para crashear en violaciones (más estricto)
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    // .penaltyDeath()
                    .build()
            )
        }

        // Crear canal de notificación
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pixelplay_music_channel",
                "PixelPlay Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}

//@HiltAndroidApp
//class PixelPlayApplication : Application() {
//    override fun onCreate() {
//        super.onCreate()
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                "pixelplay_music_channel",
//                "PixelPlay Music Playback",
//                NotificationManager.IMPORTANCE_LOW // O IMPORTANCE_DEFAULT
//            )
//            val notificationManager = getSystemService(NotificationManager::class.java)
//            notificationManager.createNotificationChannel(channel)
//        }
//    }
//}