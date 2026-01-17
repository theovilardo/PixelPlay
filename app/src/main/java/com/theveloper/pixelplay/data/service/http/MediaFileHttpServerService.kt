package com.theveloper.pixelplay.data.service.http

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
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
import io.ktor.server.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
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
        private const val NOTIFICATION_CHANNEL_ID = "cast_server_channel"
        private const val NOTIFICATION_ID = 1002
        
        var isServerRunning = false
        var serverAddress: String? = null
        @Volatile
        var lastFailureReason: FailureReason? = null
    }

    enum class FailureReason {
        NO_NETWORK_ADDRESS,
        START_EXCEPTION
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isServerRunning) return START_STICKY

        startForegroundServiceNotification()

        when (intent?.action) {
            ACTION_START_SERVER -> startServer()
            ACTION_STOP_SERVER -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Cast Media Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running local server for casting media"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Casting to Device")
            .setContentText("Local media server is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        // Type mediaPlayback is required for Android 14+ if declared in Manifest
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } catch (e: Exception) {
                // Fallback for older SDKs or if type is not strictly enforced/available at compilation
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
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
                    
                    // Bind to port 0 (random available port) to avoid conflicts
                    server = embeddedServer(Netty, port = 0, host = "0.0.0.0") {
                        // Add CORS headers manually - required for Chromecast to access media
                        // Global Request Logger
                        intercept(io.ktor.server.application.ApplicationCallPipeline.Monitoring) {
                             val uri = call.request.uri
                             val method = call.request.httpMethod.value
                             val remoteHost = call.request.local.remoteHost
                             val range = call.request.headers[HttpHeaders.Range]
                             Timber.tag("PixelPlayCastDebug").d("Incoming Request: $method $uri from $remoteHost | Range: $range")
                        }
                        
                        // Add manual CORS headers - required for Chromecast
                        intercept(io.ktor.server.application.ApplicationCallPipeline.Plugins) {
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.response.header(HttpHeaders.AccessControlAllowMethods, "GET, HEAD, OPTIONS")
                            call.response.header(HttpHeaders.AccessControlAllowHeaders, "Content-Type, Accept-Encoding, Range")
                            call.response.header(HttpHeaders.AccessControlExposeHeaders, "Content-Length, Content-Range, Accept-Ranges")
                        }

                        routing {
                            get("/") {
                                call.respond(HttpStatusCode.OK, "PixelPlay Cast Server Running")
                            }

                            get("/song/{songId}") {
                                serveSong(call, sendBody = true)
                            }
                            head("/song/{songId}") {
                                serveSong(call, sendBody = false)
                            }
                            options("/song/{songId}") {
                                Timber.tag("PixelPlayCastDebug").d("Handling OPTIONS for song")
                                call.respond(HttpStatusCode.OK)
                            }
                            
                            get("/art/{songId}") {
                                serveArt(call, sendBody = true)
                            }
                            head("/art/{songId}") {
                                serveArt(call, sendBody = false)
                            }
                            options("/art/{songId}") {
                                Timber.tag("PixelPlayCastDebug").d("Handling OPTIONS for art")
                                call.respond(HttpStatusCode.OK)
                            }
                        }
                    }.start(wait = false)
                    
                    // Retrieve actual bound port from connectors
                    val port = server?.resolvedConnectors()?.firstOrNull()?.port ?: 8080
                    serverAddress = "http://$ipAddress:$port"
                    lastFailureReason = null
                    isServerRunning = true
                    Timber.tag("PixelPlayCastDebug").i("MediaFileHttpServerService started at $serverAddress (Port binding successful)")

                } catch (e: Exception) {
                    Timber.tag("PixelPlayCastDebug").e(e, "Failed to start HTTP cast server")
                    lastFailureReason = FailureReason.START_EXCEPTION
                    stopSelf()
                }
            }
        }
    }

    private fun getIpAddress(context: Context): String? {
        // Method 1: WifiManager (Most reliable for Local Cast)
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt != 0) {
                // Convert Little Endian Int to IP String
                val ip = String.format(
                    "%d.%d.%d.%d",
                    (ipInt and 0xff),
                    (ipInt shr 8 and 0xff),
                    (ipInt shr 16 and 0xff),
                    (ipInt shr 24 and 0xff)
                )
                Timber.tag("PixelPlayCastDebug").d("Found WifiManager IP: $ip")
                return ip
            }
        } catch (e: Exception) {
            Timber.tag("PixelPlayCastDebug").e(e, "WifiManager IP detection failed")
        }

        // Method 2: NetworkInterfaces (Fallback)
        try {
            // First try to find a valid WiFi address via interfaces
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networks = connectivityManager.allNetworks
            for (network in networks) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                val linkProps = connectivityManager.getLinkProperties(network)
                if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true && linkProps != null) {
                    for (linkAddr in linkProps.linkAddresses) {
                        if (linkAddr.address is Inet4Address && !linkAddr.address.isLoopbackAddress) {
                            val ip = linkAddr.address.hostAddress
                            Timber.tag("PixelPlayCastDebug").d("Found WiFi IP: $ip")
                            return ip
                        }
                    }
                }
            }

            // Fallback: iterate over all network interfaces directly (more robust)
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress
                        Timber.tag("PixelPlayCastDebug").d("Found Fallback IP: $ip")
                        return ip
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting IP address")
        }
        Timber.tag("PixelPlayCastDebug").e("Could not find any suitable IP address")
        return null
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
                Timber.tag("PixelPlayCastDebug").d("MediaFileHttpServerService: Ktor server stopped")
            } catch (e: Exception) {
                Timber.e(e, "MediaFileHttpServerService: Error stopping Ktor server")
            }
        }.start()

        serviceJob.cancel()
    }

    private suspend fun serveSong(call: ApplicationCall, sendBody: Boolean) {
        // Strip optional extension (e.g. .mp3) provided for Cast compatibility
        val rawId = call.parameters["songId"]
        val songId = rawId?.substringBeforeLast(".")
        
        Timber.tag("PixelPlayCastDebug").d("serveSong called for ID: $songId (Raw: $rawId)")
        
        if (songId == null) {
            call.respond(HttpStatusCode.BadRequest, "Song ID is missing")
            return
        }

        val song = musicRepository.getSong(songId).firstOrNull()
        if (song == null) {
            Timber.tag("PixelPlayCastDebug").w("Song not found for ID: $songId")
            call.respond(HttpStatusCode.NotFound, "Song not found")
            return
        }

        try {
            val uri = song.contentUriString.toUri()
            // Determine the correct content type from song metadata
            // Priority: 1. ContentResolver (System Truth) 2. DB Metadata 3. Fallback
            val crMime = contentResolver.getType(uri)
            val audioContentType = crMime?.let { ContentType.parse(it) }
                ?: song.mimeType?.takeIf { it.isNotBlank() && it != "-" }?.let { ContentType.parse(it) }
                ?: ContentType.Audio.MPEG

            Timber.tag("PixelPlayCastDebug").d("Serving song: ${song.title} | Resolved Mime: $audioContentType (CR: $crMime, DB: ${song.mimeType}) | URI: $uri")

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
                    
                    Timber.tag("PixelPlayCastDebug").d(
                        "Serving PARTIAL content. Range: $rangeHeader fileSize=%d",
                        fileSize
                    )

                    // We only handle the first range request for simplicity
                    val range = ranges.first()
                    // ... (rest of simple logic)
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

                    val contentRange = "bytes $clampedStart-$clampedEnd/$fileSize"
                    call.response.header(HttpHeaders.ContentRange, contentRange)
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")

                    call.response.header(HttpHeaders.ContentType, audioContentType.toString())
                    if (sendBody) {
                        val bytes = withContext(Dispatchers.IO) {
                            inputStream.readNBytes(length.toInt())
                        }
                        Timber.tag("PixelPlayCastDebug").d(
                            "Responding PARTIAL: contentType=%s contentRange=%s contentLength=%d sendBody=true",
                            audioContentType,
                            contentRange,
                            length
                        )
                        call.respondBytes(bytes, audioContentType, HttpStatusCode.PartialContent)
                    } else {
                        call.response.header(HttpHeaders.ContentLength, length.toString())
                        Timber.tag("PixelPlayCastDebug").d(
                            "Responding PARTIAL: contentType=%s contentRange=%s contentLength=%d sendBody=false",
                            audioContentType,
                            contentRange,
                            length
                        )
                        call.respond(HttpStatusCode.PartialContent)
                    }
                } else {
                    Timber.tag("PixelPlayCastDebug").d(
                        "Serving FULL content. contentType=%s fileSize=%d sendBody=%s",
                        audioContentType,
                        fileSize,
                        sendBody
                    )
                    val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    if (sendBody) {
                        val bytes = withContext(Dispatchers.IO) {
                            inputStream.readBytes()
                        }
                        call.respondBytes(bytes, audioContentType)
                    } else {
                        call.response.header(HttpHeaders.ContentType, audioContentType.toString())
                        call.response.header(HttpHeaders.ContentLength, fileSize.toString())
                        call.respond(HttpStatusCode.OK)
                    }
                }
            } ?: run {
                Timber.tag("PixelPlayCastDebug").e("Could not open file descriptor for URI: $uri")
                call.respond(HttpStatusCode.NotFound, "File not found")
            }
        } catch (e: Exception) {
            Timber.tag("PixelPlayCastDebug").e(e, "Error serving file")
            call.respond(HttpStatusCode.InternalServerError, "Error serving file: ${e.message}")
        }
    }

    private suspend fun serveArt(call: ApplicationCall, sendBody: Boolean) {
        val rawId = call.parameters["songId"]
        val songId = rawId?.substringBeforeLast(".")
        if (songId == null) {
            call.respond(HttpStatusCode.BadRequest, "Song ID is missing")
            return
        }

        val song = musicRepository.getSong(songId).firstOrNull()
        if (song?.albumArtUriString == null) {
            call.respond(HttpStatusCode.NotFound, "Album art not found")
            return
        }

        val artUri = song.albumArtUriString.toUri()
        val contentType = contentResolver.getType(artUri)
            ?.let { ContentType.parse(it) }
            ?: ContentType.Image.JPEG

        if (!sendBody) {
            contentResolver.openFileDescriptor(artUri, "r")?.use { pfd ->
                val fileSize = pfd.statSize.takeIf { it > 0 } ?: 0L
                call.response.header(HttpHeaders.ContentType, contentType.toString())
                if (fileSize > 0) {
                    call.response.header(HttpHeaders.ContentLength, fileSize.toString())
                }
                call.respond(HttpStatusCode.OK)
                return
            }
        }

        contentResolver.openInputStream(artUri)?.use { inputStream ->
            val bytes = withContext(Dispatchers.IO) {
                inputStream.readBytes()
            }
            call.respondBytes(bytes, contentType)
        } ?: call.respond(HttpStatusCode.InternalServerError, "Could not open album art file")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
