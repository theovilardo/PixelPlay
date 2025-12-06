package com.theveloper.pixelplay.data.service.http

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.ContentType
import io.ktor.http.ContentRange
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
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
        if (server?.application?.isActive != true) {
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
                                            val ranges = io.ktor.http.parseRangesSpecifier(rangeHeader)
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
                                                call.respond(HttpStatusCode.RangeNotSatisfiable, "Range not satisfiable")
                                                return@use
                                            }

                                            // Re-open stream for reading; FileInputStream using the FD doesn't support seeking well if the FD is shared/offset
                                            // Ideally we create a FileInputStream from the FD.
                                            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
                                            // Skip to start
                                            // Note: skipping on FileInputStream from PFD might depend on current position.
                                            // Since we just opened it, it should be at 0.

                                            // For reliable seeking, we can use the FD channel, but InputStream skip is usually ok for read-only.
                                            // However, `skip` is not guaranteed to skip fully.
                                            var skipped = 0L
                                            while (skipped < clampedStart) {
                                                val s = inputStream.skip(clampedStart - skipped)
                                                if (s <= 0) break
                                                skipped += s
                                            }

                                            call.response.header(HttpHeaders.ContentRange, "bytes $clampedStart-$clampedEnd/$fileSize")
                                            call.response.header(HttpHeaders.AcceptRanges, "bytes")

                                            // Read only the requested chunk
                                            val bytes = withContext(Dispatchers.IO) {
                                                inputStream.readNBytes(length.toInt())
                                            }

                                            call.respond(HttpStatusCode.PartialContent, bytes)
                                        } else {
                                            val inputStream = java.io.FileInputStream(pfd.fileDescriptor)
                                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                            // We cannot use respondOutputStream with 'use' block easily because 'use' closes the FD
                                            // when this block ends, potentially before the stream is written.
                                            // For full file, we read it all or stream it carefully.
                                            // Given we are inside 'use', we should read to bytes or ensure blocking write.
                                            // Ktor's respondOutputStream is a coroutine, so 'use' might exit before completion.
                                            // To fix resource leak with respondOutputStream, we shouldn't use `use` around the whole block
                                            // if we pass the stream out.
                                            // But here we are just reading fully.
                                            val bytes = withContext(Dispatchers.IO) {
                                                inputStream.readBytes()
                                            }
                                            call.respond(ContentType.Audio.MPEG, bytes)
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
                                    call.respond(ContentType.Image.JPEG, bytes)
                                } ?: call.respond(HttpStatusCode.InternalServerError, "Could not open album art file")
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
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null
        val ipAddress = linkProperties.linkAddresses.find { it.address is Inet4Address }
        return ipAddress?.address?.hostAddress
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
