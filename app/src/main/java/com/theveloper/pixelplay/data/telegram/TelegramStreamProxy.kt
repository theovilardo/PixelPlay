package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.utils.LogUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.ByteWriteChannel
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramStreamProxy @Inject constructor(
    private val telegramRepository: TelegramRepository
) {
    private var server = embeddedServer(Netty, port = 0) { // Port 0 lets OS pick a free port
        routing {
            get("/stream/{fileId}") {
                val fileId = call.parameters["fileId"]?.toIntOrNull()
                if (fileId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid File ID")
                    return@get
                }

                LogUtils.d("StreamProxy", "Request for fileId: $fileId")
                
                // Wait for TDLib to be ready before attempting download
                // This fixes playback failures after app restart
                if (!telegramRepository.isReady()) {
                    LogUtils.w("StreamProxy", "TDLib not ready, waiting...")
                    val ready = telegramRepository.awaitReady(10_000L) // 10 second timeout
                    if (!ready) {
                        LogUtils.e("StreamProxy", null, "TDLib not ready after timeout")
                        call.respond(HttpStatusCode.ServiceUnavailable, "Telegram client not ready")
                        return@get
                    }
                    LogUtils.d("StreamProxy", "TDLib ready, proceeding with request")
                }
                
                // 1. Ensure download is started/active
                val fileInfo = telegramRepository.downloadFile(fileId, 32) // High priority
                if (fileInfo?.local?.path.isNullOrEmpty()) {
                    call.respond(HttpStatusCode.InternalServerError, "Could not get file path")
                    return@get
                }
                
                val path = fileInfo!!.local.path
                var expectedSize = fileInfo.expectedSize

                // Use known size from query param if available (authoritative)
                val knownSize = call.parameters["size"]?.toLongOrNull() ?: 0L
                if (knownSize > 0) {
                    expectedSize = knownSize
                }
                
                // Fallback to disk size if still 0
                if (expectedSize == 0L) {
                     expectedSize = File(path).length()
                }

                val file = File(path)

                // Wait for file to be created by TDLib
                var waitCount = 0
                while (!file.exists() && waitCount < 100) {
                     delay(50)
                     waitCount++
                }
                
                if (!file.exists()) {
                     call.respond(HttpStatusCode.InternalServerError, "File not created by TDLib")
                     return@get
                }

                // Range Handling
                val rangeHeader = call.request.headers["Range"]
                var start = 0L
                var end = if (expectedSize > 0) expectedSize - 1 else Long.MAX_VALUE - 1

                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    try {
                        val ranges = rangeHeader.substring(6).split("-")
                        if (ranges.isNotEmpty() && ranges[0].isNotEmpty()) {
                            start = ranges[0].toLong()
                        }
                        if (ranges.size > 1 && ranges[1].isNotEmpty()) {
                            end = ranges[1].toLong()
                        }
                    } catch (e: NumberFormatException) {
                        // Ignore invalid range
                    }
                }

                // Cap end at expectedSize if known
                if (expectedSize > 0 && end >= expectedSize) {
                    end = expectedSize - 1
                }
                
                if (start > end) {
                     call.respond(HttpStatusCode(416, "Range Not Satisfiable"))
                     return@get
                }

                val contentLength = end - start + 1
                
                call.response.header("Accept-Ranges", "bytes")
                if (expectedSize > 0) {
                    call.response.header("Content-Range", "bytes $start-$end/$expectedSize")
                    call.response.header("Content-Length", contentLength.toString())
                    call.response.status(HttpStatusCode.PartialContent)
                }

                // Stream the file
                call.respondBytesWriter(contentType = ContentType.Audio.Any) {
                    val raf = RandomAccessFile(file, "r")
                    try {
                        var currentPos = start
                        val buffer = ByteArray(64 * 1024) // Increased to 64KB for smoother streaming
                        var noDataCount = 0
                        
                        raf.seek(currentPos)
                        
                        while (true) {
                            // Check max read
                            val remaining = end - currentPos + 1
                            if (remaining <= 0) break
                            
                            val toRead = min(buffer.size.toLong(), remaining).toInt()
                            
                            val fileLength = raf.length()
                            if (currentPos < fileLength) {
                                val read = raf.read(buffer, 0, toRead)
                                if (read > 0) {
                                    writeFully(buffer, 0, read)
                                    // writeFully(buffer, 0, read) - already done above
                                    // flush() // Remove explicit flush to let underlying engine handle buffering efficiency
                                    currentPos += read
                                    noDataCount = 0
                                } else {
                                     // Should not happen if currentPos < fileLength, but safer to break or delay
                                     delay(5)
                                }
                            } else {
                                // Reached current end of file
                                
                                // Optimization: Trust expectedSize if available causing "stream starvation" by blocking on getFile
                                if (expectedSize > 0 && currentPos >= expectedSize) {
                                    break // Done (reached expected end)
                                }

                                // Rate limit status checks (JNI calls) to avoid throttling the stream
                                // Check only every ~2 seconds (200 * 10ms) instead of every iteration
                                noDataCount++
                                if (noDataCount % 200 == 0) {
                                    val currentFileInfo = telegramRepository.getFile(fileId)
                                    // Verify completion
                                    if (currentFileInfo?.local?.isDownloadingCompleted == true && currentPos >= currentFileInfo.size) {
                                         break // Done
                                    }
                                    // Verify cancellation/failure
                                    if (currentFileInfo?.local?.isDownloadingCompleted == false && !currentFileInfo.local.canBeDownloaded) {
                                         break // Failed/Cancelled
                                    }
                                }
                                
                                if (noDataCount > 5000) { // ~50 seconds idle (5000 * 10ms)
                                      break // Timeout
                                }
                                delay(10) // 10ms polling for lower latency
                            }
                        }
                    } catch (e: Exception) {
                        // Check for common specific errors to avoid noise
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") || 
                            msg.contains("ClosedChannelException") || 
                            msg.contains("Broken pipe") ||
                            msg.contains("WriteTimeoutException") ||
                            msg.contains("JobCancellationException")) {
                             // Client disconnected, normal behavior
                        } else {
                             LogUtils.e("StreamProxy", e, "Streaming error")
                        }
                    } finally {
                        raf.close()
                    }
                }
            }
        }
    }

    private var actualPort: Int = 0

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                server.start(wait = false)
                
                // Fix Race Condition: Wait for server to bind and assign port
                var attempts = 0
                while (attempts < 20) {
                    val connectors = server.resolvedConnectors()
                    val port = connectors.firstOrNull()?.port ?: 0
                    if (port > 0) {
                        actualPort = port
                        break
                    }
                    delay(100)
                    attempts++
                }
                
                if (actualPort == 0) {
                     LogUtils.e("StreamProxy", null, "Failed to resolve port after wait. Server might not be bound.")
                } else {
                     LogUtils.d("StreamProxy", "Started on port $actualPort")
                }
            } catch (e: Exception) {
                LogUtils.e("StreamProxy", e, "Failed to start server")
            }
        }
    }
    
    fun getProxyUrl(fileId: Int, knownSize: Long = 0): String {
        if (actualPort == 0) {
            LogUtils.w("StreamProxy", "getProxyUrl called but actualPort is 0")
            return ""
        }
        val url = "http://127.0.0.1:$actualPort/stream/$fileId?size=$knownSize"
        LogUtils.d("StreamProxy", "Generated Proxy URL: $url")
        return url
    }
    
    /**
     * Quick check if the proxy server is ready (port is bound).
     */
    fun isReady(): Boolean = actualPort > 0
    
    /**
     * Suspends until the proxy server is ready (port bound).
     * @param timeoutMs Maximum time to wait
     * @return true if ready, false if timed out
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        
        val stepMs = 50L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isReady()) {
                LogUtils.d("StreamProxy", "awaitReady: Server ready after ${elapsed}ms")
                return true
            }
            delay(stepMs)
            elapsed += stepMs
        }
        LogUtils.e("StreamProxy", null, "awaitReady: Timeout after ${timeoutMs}ms")
        return false
    }
    
}
