package com.theveloper.pixelplay.data.service.http

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

@AndroidEntryPoint
class MediaFileHttpServerService : Service() {

    @Inject
    lateinit var musicRepository: MusicRepository

    private var server: NettyApplicationEngine? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_START_SERVER = "ACTION_START_SERVER"
        const val ACTION_STOP_SERVER = "ACTION_STOP_SERVER"
        var isServerRunning = false
        var serverAddress: String? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVER -> startServer()
            ACTION_STOP_SERVER -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        if (server == null) {
            serviceScope.launch {
                try {
                    val ipAddress = getIpAddress(applicationContext)
                    if (ipAddress == null) {
                        stopSelf()
                        return@launch
                    }
                    serverAddress = "http://$ipAddress:8080"

                    server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
                        routing {
                            get("/song/{songId}") {
                                val songId = call.parameters["songId"]
                                if (songId == null) {
                                    call.respondText("Song ID is missing", status = io.ktor.http.HttpStatusCode.BadRequest)
                                    return@get
                                }

                                val song = musicRepository.getSongById(songId).firstOrNull()
                                if (song == null) {
                                    call.respondText("Song not found", status = io.ktor.http.HttpStatusCode.NotFound)
                                    return@get
                                }

                                val inputStream: InputStream? = contentResolver.openInputStream(android.net.Uri.parse(song.contentUriString))
                                if (inputStream == null) {
                                     call.respondText("Could not open song file", status = io.ktor.http.HttpStatusCode.InternalServerError)
                                     return@get
                                }
                                call.respondInputStream(contentType = io.ktor.http.ContentType.Audio.MPEG) { inputStream }
                            }
                            get("/art/{songId}") {
                                val songId = call.parameters["songId"]
                                if (songId == null) {
                                    call.respondText("Song ID is missing", status = io.ktor.http.HttpStatusCode.BadRequest)
                                    return@get
                                }

                                val song = musicRepository.getSongById(songId).firstOrNull()
                                if (song?.albumArtUriString == null) {
                                    call.respondText("Album art not found", status = io.ktor.http.HttpStatusCode.NotFound)
                                    return@get
                                }

                                val artUri = android.net.Uri.parse(song.albumArtUriString)
                                val inputStream: InputStream? = contentResolver.openInputStream(artUri)
                                if (inputStream == null) {
                                    call.respondText("Could not open album art file", status = io.ktor.http.HttpStatusCode.InternalServerError)
                                    return@get
                                }
                                call.respondInputStream(contentType = io.ktor.http.ContentType.Image.JPEG) { inputStream }
                            }
                        }
                    }.start(wait = false)
                    isServerRunning = true
                } catch (e: Exception) {
                    stopSelf()
                }
            }
        }
    }

    private fun getIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        if (ipAddress == 0) return null
        return (ipAddress and 0xFF).toString() + "." +
                (ipAddress shr 8 and 0xFF) + "." +
                (ipAddress shr 16 and 0xFF) + "." +
                (ipAddress shr 24 and 0xFF)
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 2000)
        isServerRunning = false
        serverAddress = null
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}