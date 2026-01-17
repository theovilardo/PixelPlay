package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

import timber.log.Timber

@Singleton
class TelegramRepository @Inject constructor(
    private val clientManager: TelegramClientManager
) {

    val authorizationState: Flow<TdApi.AuthorizationState?> = clientManager.authorizationState
    /**
     * Clear memory caches in the repository.
     * For full cache clearing including files, use TelegramCacheManager.
     */
    fun clearMemoryCache() {
        resolvedPathCache.clear()
        Timber.d("TelegramRepository: Memory cache cleared")
    }

    fun sendPhoneNumber(phoneNumber: String) {
        clientManager.sendPhoneNumber(phoneNumber)
    }

    fun checkAuthenticationCode(code: String) {
        clientManager.checkAuthenticationCode(code)
    }
    
    fun checkAuthenticationPassword(password: String) {
        clientManager.checkAuthenticationPassword(password)
    }

    suspend fun searchPublicChat(username: String): TdApi.Chat? {
        return try {
            clientManager.sendRequest(TdApi.SearchPublicChat(username))
        } catch (e: Exception) {
            Timber.e(e, "Error searching public chat: $username")
            null
        }
    }

    suspend fun getAudioMessages(chatId: Long): List<Song> {
        Timber.d("Fetching chat history for chat: $chatId")
        try {
            clientManager.sendRequest(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Timber.w("Failed to open chat: $chatId")
        }

        val allSongs = mutableListOf<Song>()
        var nextFromMessageId = 0L
        val batchSize = 100 // TdApi limit

        try {
            while (true) {
                // Use SearchChatMessages to find audio files specifically
                val request = TdApi.SearchChatMessages()
                request.chatId = chatId
                request.query = ""
                request.senderId = null // Use null for any sender
                request.fromMessageId = nextFromMessageId
                request.offset = 0
                request.limit = batchSize
                request.filter = TdApi.SearchMessagesFilterAudio()
                
                val response = clientManager.sendRequest<TdApi.FoundChatMessages>(request)
                Timber.d("SearchChatMessages batch (fromId $nextFromMessageId): found ${response.messages.size} / total ${response.totalCount}")
                
                if (response.messages.isEmpty()) {
                    break
                }
                
                response.messages.forEach { message ->
                    mapMessageToSong(message)?.let { allSongs.add(it) }
                }
                
                nextFromMessageId = response.nextFromMessageId
                if (nextFromMessageId == 0L) {
                    break // No more results
                }
            }

            Timber.d("Total mapped audio songs: ${allSongs.size}")
            return allSongs
        } catch (e: Exception) {
            Timber.e(e, "Error fetching chat history for chat $chatId")
            return allSongs // Return partial results if we crash mid-way
        }
    }
    
    private suspend fun mapMessageToSong(message: TdApi.Message): Song? {
        val content = message.content
        
        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                Timber.d("Mapping MessageAudio: ${audio.fileName} (${audio.title} - ${audio.performer})")
                
                var albumArtPath: String? = null
                // Priority 1: Main album cover thumbnail (embedded)
                var thumbnail = audio.albumCoverThumbnail
                
                // Priority 2: External album covers (e.g., from Spotify/Apple Music metadata)
                // These have size=0 initially but TDLib CAN download them - just needs time to resolve
                if (thumbnail == null && audio.externalAlbumCovers?.isNotEmpty() == true) {
                    thumbnail = audio.externalAlbumCovers.maxByOrNull { it.width * it.height }
                }

                if (thumbnail != null) {
                     // Use custom URI scheme for Coil Fetcher with ChatID/MessageID for robust lookup
                     albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                     Timber.d("MessageAudio has thumbnail: $albumArtPath (fileId=${thumbnail.file.id}, size=${thumbnail.file.size})")
                     
                     // OPTIMIZATION: Populate cache if already downloaded
                     if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                         resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                     }
                } else {
                     Timber.d("MessageAudio has NO thumbnail")
                }
                
                Song(
                    id = "${message.chatId}_${message.id}", // Unique ID
                    title = audio.title.ifEmpty { "Unknown Title" },
                    artist = audio.performer.ifEmpty { "Unknown Artist" },
                    artistId = -1,
                album = "Telegram Stream",
                albumId = -1,
                path = "", // Will be filled when downloaded
                contentUriString = "telegram://${message.chatId}/${message.id}", // Persistent URI scheme
                albumArtUriString = albumArtPath,
                duration = audio.duration * 1000L,
                telegramFileId = audio.audio.id,
                telegramChatId = message.chatId,
                    mimeType = audio.mimeType,
                    bitrate = 0,
                    sampleRate = 0,
                    year = 0,
                    trackNumber = 0,
                    dateAdded = message.date.toLong(),
                    isFavorite = false
                )
            }
            is TdApi.MessageDocument -> {
                val document = content.document
                // Timber.d("Checking MessageDocument: ${document.fileName}, Mime: ${document.mimeType}")
                
                val isAudioMime = document.mimeType.startsWith("audio/") || document.mimeType == "application/ogg"
                val isAudioExtension = document.fileName.lowercase().run {
                    endsWith(".mp3") || endsWith(".flac") || endsWith(".wav") || endsWith(".m4a") || endsWith(".ogg") || endsWith(".aac")
                }
                
                if (isAudioMime || isAudioExtension) {
                    val title = document.fileName.ifEmpty { "Unknown Track" }
                    val artist = "Telegram Audio"
                    
                    var albumArtPath: String? = null
                    val thumbnail = document.thumbnail
                    if (thumbnail != null) {
                         albumArtPath = "telegram_art://${message.chatId}/${message.id}"
                         // OPTIMIZATION: Populate cache if already downloaded
                         if (thumbnail.file.local.isDownloadingCompleted && thumbnail.file.local.path.isNotEmpty()) {
                             resolvedPathCache[thumbnail.file.id] = thumbnail.file.local.path
                         }
                    }

                    Song(
                    id = "${message.chatId}_${message.id}",
                    title = title,
                    artist = artist,
                    artistId = -1,
                    album = "Telegram Stream",
                    albumId = -1,
                    path = "",
                    contentUriString = "telegram://${message.chatId}/${message.id}",
                    albumArtUriString = albumArtPath,
                    duration = 0L,
                    telegramFileId = document.document.id,
                    telegramChatId = message.chatId,
                        mimeType = document.mimeType,
                        bitrate = 0,
                        sampleRate = 0,
                        year = 0,
                        trackNumber = 0,
                        dateAdded = message.date.toLong(),
                        isFavorite = false
                    )
                } else {
                    null
                }
            }
            else -> {
                Timber.d("Skipped message ${message.id} with content: ${content.javaClass.simpleName}")
                null
            }
        }
    }

    suspend fun downloadFile(fileId: Int, priority: Int = 1): TdApi.File? {
        return try {
            clientManager.sendRequest(TdApi.DownloadFile(fileId, priority, 0, 0, false))
        } catch (e: Exception) {
            Timber.e(e, "Error evaluating DownloadFile for fileId: $fileId")
            null
        }
    }

    suspend fun getFile(fileId: Int): TdApi.File? {
        return try {
            clientManager.sendRequest(TdApi.GetFile(fileId))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return try {
            clientManager.sendRequest(TdApi.GetMessage(chatId, messageId))
        } catch (e: Exception) {
            Timber.e(e, "Error fetching message: $chatId / $messageId")
            null
        }
    }

    suspend fun resolveTelegramUri(uriString: String): Int? {
        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme != "telegram") return null
        
        val chatId = uri.host?.toLongOrNull()
        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()
        
        if (chatId == null || messageId == null) return null
        
        // Fetch fresh message to get valid fileId for this session
        // we use getMessage which internally calls TdApi.GetMessage
        val message = getMessage(chatId, messageId) ?: return null
        
        return when (val content = message.content) {
            is TdApi.MessageAudio -> content.audio.audio.id
            is TdApi.MessageDocument -> content.document.document.id
            else -> null
        }
    }

    /**
     * Forces a refresh of the message from the server using GetChatHistory.
     * This handles stale file references/access hashes.
     */
    suspend fun refreshMessage(chatId: Long, messageId: Long): TdApi.Message? {
        return try {
            // Using GetChatHistory with limit=1 mostly fetches from server if not cached fresh.
            // There is no explicit "Force Network" flag in TDLib for messages, but this is the standard workaround.
            val history = clientManager.sendRequest<TdApi.Messages>(
                TdApi.GetChatHistory(chatId, messageId, 0, 1, false)
            )
            history.messages.firstOrNull { it.id == messageId }
                ?: clientManager.sendRequest(TdApi.GetMessage(chatId, messageId))
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing message: $messageId")
            null
        }
    }

    // Cache for resolved paths to avoid repeated IPC calls
    private val resolvedPathCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.Deferred<String?>>()
    
    // Limit concurrent downloads to prevent TDLib overwhelm and reduce GC pressure
    // Reduced from 12 to 4 for thumbnails - higher values cause timeouts and frame drops
    private val downloadSemaphore = kotlinx.coroutines.sync.Semaphore(4)

    suspend fun downloadFileAwait(fileId: Int, priority: Int = 1): String? {
        // 1. Check Memory Cache first
        resolvedPathCache[fileId]?.let { path ->
            if (java.io.File(path).exists()) return path
            resolvedPathCache.remove(fileId)
        }

        // Dedup: If already downloading, join that job
        val existingJob = activeDownloads[fileId]
        if (existingJob != null && existingJob.isActive) {
            return existingJob.await()
        }

        val newJob = repositoryScope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                // Use withPermit to limit concurrent heavyweight downloads
                downloadSemaphore.withPermit {
                    // Double check status after acquiring permit
                    val currentFile = getFile(fileId)
                    if (currentFile?.local?.isDownloadingCompleted == true) {
                        currentFile.local.path.takeIf { it.isNotEmpty() }?.let {
                             resolvedPathCache[fileId] = it
                             return@withPermit it
                        }
                    }

                    val initialFile = getFile(fileId)
                    // Use synchronous download for thumbnails (size=0 or small < 1MB)
                    // This forces TDLib to resolve the file immediately, fixing size=0 issues
                    val isSmallFile = initialFile?.size == 0L || (initialFile?.size ?: 0) < 1024 * 1024
                    
                    if (isSmallFile) {
                        Timber.d("Starting SYNCHRONOUS download for fileId: $fileId (Priority $priority)")
                        return@withPermit try {
                            // 15 seconds timeout for sync download
                            val resultFile = withTimeout(15_000L) {
                                clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, true))
                            }
                            
                            if (resultFile.local.isDownloadingCompleted && resultFile.local.path.isNotEmpty()) {
                                Timber.d("Sync download SUCCESS for $fileId. Path: ${resultFile.local.path}")
                                resolvedPathCache[fileId] = resultFile.local.path
                                resultFile.local.path
                            } else {
                                Timber.w("Sync download returned incomplete for $fileId")
                                null
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Sync download FAILED for $fileId")
                            null
                        }
                    }

                    // Fallback to Async for larger files (if needed in future)
                    Timber.d("Starting Async Download for fileId: $fileId (Large file)")
                    try {
                        clientManager.sendRequest<TdApi.File>(TdApi.DownloadFile(fileId, priority, 0, 0, false))
                    } catch(e: Exception) {
                        Timber.e(e, "DownloadFile request FAILED for $fileId")
                        return@withPermit null
                    }
                    
                    // Wait for updateFile events
                    val completedPath = withTimeoutOrNull(60_000L) {
                        clientManager.updates
                            .filterIsInstance<TdApi.UpdateFile>()
                            .filter { it.file.id == fileId }
                            .first { update ->
                                val file = update.file
                                when {
                                    file.local.isDownloadingCompleted && file.local.path.isNotEmpty() -> true
                                    !file.local.canBeDownloaded -> throw Exception("File cannot be downloaded")
                                    else -> false
                                }
                            }
                            .file.local.path
                    }
                    
                    if (completedPath != null) {
                        resolvedPathCache[fileId] = completedPath
                        return@withPermit completedPath
                    }
                    
                    // Final check
                    val finalFile = getFile(fileId)
                    return@withPermit if (finalFile?.local?.isDownloadingCompleted == true && finalFile.local.path.isNotEmpty()) {
                        finalFile.local.path
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Error in downloadFileAwait for fileId: $fileId")
                }
                throw e
            } finally {
                activeDownloads.remove(fileId)
            }
        }

        activeDownloads[fileId] = newJob
        
        // Correctly handle cancellation propagation
        try {
            newJob.start()
            return newJob.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            newJob.cancel(e) // Cancel the background work if the caller cancels
            throw e
        }
    }
}
