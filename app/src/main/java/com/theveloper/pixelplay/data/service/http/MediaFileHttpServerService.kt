package com.theveloper.pixelplay.data.service.http

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.IBinder
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import javax.inject.Inject
import timber.log.Timber

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
        @Volatile
        var lastFailureReason: FailureReason? = null
    }

    enum class FailureReason {
        NO_NETWORK_ADDRESS,
        START_EXCEPTION
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_SERVER) {
            // Ensure we are in foreground immediately if started this way
            startForegroundService()
            startServer()
        } else if (intent?.action == ACTION_STOP_SERVER) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "pixelplay_cast_server"
        val channelName = "Cast Media Server"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                channelName,
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setContentTitle("Casting to device")
            .setContentText("Serving media to Cast device")
            .setSmallIcon(android.R.drawable.ic_menu_upload) // Placeholder, ideally use app icon
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .build()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                1002, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(1002, notification)
        }
    }

    private fun startServer() {
        if (server?.application?.isActive != true) {
            serviceScope.launch {
                try {
                    val ipAddress = getIpAddress(applicationContext)
                    if (ipAddress == null) {
                        Timber.w("No suitable IP address found; cannot start HTTP server")
                        lastFailureReason = FailureReason.NO_NETWORK_ADDRESS
                        stopSelf()
                        return@launch
                    }
                    serverAddress = "http://$ipAddress:8080"
                    lastFailureReason = null

                    server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
                        routing {
                            get("/song/{songId}") {
                                val songId = call.parameters["songId"]
                                if (songId == null) {
                                    call.respond(HttpStatusCode.BadRequest, "Song ID is missing")
                                    return@get
                                }

                                val song = musicRepository.getSong(songId).firstOrNull()
                                if (song == null) {
                                    call.respond(HttpStatusCode.NotFound, "Song not found")
                                    return@get
                                }

                                try {
                                    val uri = song.contentUriString.toUri()
                                    // Use 'use' to ensure the FileDescriptor is closed
                                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                        val fileSize = pfd.statSize
                                        val rangeHeader = call.request.headers[HttpHeaders.Range]

                                        if (rangeHeader != null) {
                                            val rangesSpecifier = io.ktor.http.parseRangesSpecifier(rangeHeader)
                                            val ranges = rangesSpecifier?.ranges

                                            if (ranges.isNullOrEmpty()) {
                                                call.respond(HttpStatusCode.BadRequest, "Invalid range")
                                                return@use
                                            }

                                            // We only handle the first range request for simplicity
                                            val range = ranges.first()
                                            val start = when (range) {
                                                is io.ktor.http.ContentRange.Bounded -> range.from
                                                is io.ktor.http.ContentRange.TailFrom -> range.from
                                                is io.ktor.http.ContentRange.Suffix -> fileSize - range.lastCount
                                                else -> 0L
                                            }
                                            val end = when (range) {
                                                is io.ktor.http.ContentRange.Bounded -> range.to
                                                is io.ktor.http.ContentRange.TailFrom -> fileSize - 1
                                                is io.ktor.http.ContentRange.Suffix -> fileSize - 1
                                                else -> fileSize - 1
                                            }

                                            val clampedStart = start.coerceAtLeast(0L)
                                            val clampedEnd = end.coerceAtMost(fileSize - 1)
                                            val length = clampedEnd - clampedStart + 1

                                            if (length <= 0) {
                                                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable, "Range not satisfiable")
                                                return@use
                                            }

                                            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)

                                            var skipped = 0L
                                            while (skipped < clampedStart) {
                                                val s = inputStream.skip(clampedStart - skipped)
                                                if (s <= 0) break
                                                skipped += s
                                            }

                                            call.response.header(HttpHeaders.ContentRange, "bytes $clampedStart-$clampedEnd/$fileSize")
                                            call.response.header(HttpHeaders.AcceptRanges, "bytes")

                                            val bytes = withContext(Dispatchers.IO) {
                                                inputStream.readNBytes(length.toInt())
                                            }

                                            call.respondBytes(bytes, ContentType.Audio.MPEG, HttpStatusCode.PartialContent)
                                        } else {
                                            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
                                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                            val bytes = withContext(Dispatchers.IO) {
                                                inputStream.readBytes()
                                            }
                                            call.respondBytes(bytes, ContentType.Audio.MPEG)
                                        }
                                    } ?: run {
                                        call.respond(HttpStatusCode.NotFound, "File not found")
                                    }
                                } catch (e: Exception) {
                                    call.respond(HttpStatusCode.InternalServerError, "Error serving file: ${e.message}")
                                }
                            }
                            get("/art/{songId}") {
                                val songId = call.parameters["songId"]
                                if (songId == null) {
                                    call.respond(HttpStatusCode.BadRequest, "Song ID is missing")
                                    return@get
                                }

                                val song = musicRepository.getSong(songId).firstOrNull()
                                if (song?.albumArtUriString == null) {
                                    call.respond(HttpStatusCode.NotFound, "Album art not found")
                                    return@get
                                }

                                val artUri = song.albumArtUriString.toUri()
                                contentResolver.openInputStream(artUri)?.use { inputStream ->
                                    val bytes = withContext(Dispatchers.IO) {
                                        inputStream.readBytes()
                                    }
                                    call.respondBytes(bytes, ContentType.Image.JPEG)
                                } ?: call.respond(HttpStatusCode.InternalServerError, "Could not open album art file")
                            }
                        }
                    }.start(wait = false)
                    isServerRunning = true
                } catch (e: Exception) {
                    Timber.e(e, "Failed to start HTTP cast server")
                    lastFailureReason = FailureReason.START_EXCEPTION
                    stopSelf()
                }
            }
        }
    }

    private fun getIpAddress(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Prefer IPv4 Wi-Fi addresses; fall back to any non-loopback address so the server can bind.
        val networks = connectivityManager.allNetworks
        val wifiNetworkAddress = networks.asSequence()
            .mapNotNull { network ->
                val caps = connectivityManager.getNetworkCapabilities(network)
                val linkProps = connectivityManager.getLinkProperties(network)
                if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true && linkProps != null) {
                    linkProps.linkAddresses.firstOrNull { it.address is Inet4Address }
                } else null
            }
            .firstOrNull()

        val fallbackAddress = connectivityManager.activeNetwork?.let { active ->
            connectivityManager.getLinkProperties(active)?.linkAddresses?.firstOrNull { !it.address.isLoopbackAddress }
        }

        val chosenAddress = wifiNetworkAddress ?: fallbackAddress
        return chosenAddress?.address?.hostAddress
    }

    override fun onDestroy() {
        super.onDestroy()
        isServerRunning = false
        serverAddress = null
        lastFailureReason = null

        val serverInstance = server
        server = null

        // Stop server in a background thread to avoid blocking the Main Thread
        Thread {
            try {
                // Grace period 100ms, timeout 2000ms
                serverInstance?.stop(100, 2000)
                Timber.d("MediaFileHttpServerService: Ktor server stopped")
            } catch (e: Exception) {
                Timber.e(e, "MediaFileHttpServerService: Error stopping Ktor server")
            }
        }.start()

        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
