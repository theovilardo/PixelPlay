package com.theveloper.pixelplay.data.image

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.fetch.DrawableResult
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import okio.Path.Companion.toPath
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Custom Coil Fetcher for Telegram album art.
 * Handles URIs in format: telegram_art://chatId/messageId
 * 
 * Priority chain (Nekogram-style):
 * 1. Embedded art from downloaded audio file (MediaMetadataRetriever)
 * 2. TDLib albumCoverThumbnail (from message)
 * 3. TDLib externalAlbumCovers (Spotify/Apple Music metadata)
 */
class TelegramCoilFetcher(
    private val context: Context,
    private val uri: Uri,
    private val telegramRepository: TelegramRepository,
    private val cacheDir: File,
    private val telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager?
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        Timber.d("TelegramCoilFetcher: Fetching $uri")

        // Parse URI: telegram_art://chatId/messageId
        val chatId = uri.host?.toLongOrNull()
        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()

        if (chatId == null || messageId == null) {
            Timber.e("TelegramCoilFetcher: Invalid URI format: $uri")
            return null
        }

        // Priority 1: Try embedded art from downloaded audio file
        val embeddedArtPath = tryExtractEmbeddedArt(chatId, messageId)
        if (embeddedArtPath != null) {
            Timber.d("TelegramCoilFetcher: Using embedded art from audio file: $embeddedArtPath")
            return SourceResult(
                source = coil.decode.ImageSource(
                    file = embeddedArtPath.toPath(),
                    fileSystem = okio.FileSystem.SYSTEM
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }
        
        // Fetch message to get thumbnail info and minithumbnail
        val message = telegramRepository.getMessage(chatId, messageId) ?: return null
        
        var downloadPath: String? = null
        val isRecentlyFailed = telegramCacheManager?.isArtFailed(chatId, messageId) == true

        // Priority 2: TDLib Thumbnail Download (High Res)
        // Skip if recently failed to avoid network hammer
        if (!isRecentlyFailed) {
            val fileId = extractFileIdFromContent(message.content)
            if (fileId != null) {
                Timber.d("TelegramCoilFetcher: Resolved TDLib thumbnail fileId: $fileId from $uri")
                downloadPath = downloadWithRetry(fileId, chatId, messageId)
            }
        } else {
             Timber.d("TelegramCoilFetcher: Skipping high-res download (recently failed): $uri")
        }

        if (downloadPath != null) {
            Timber.d("TelegramCoilFetcher: Success with TDLib thumbnail! Path: $downloadPath")
            return SourceResult(
                source = coil.decode.ImageSource(
                    file = downloadPath.toPath(),
                    fileSystem = okio.FileSystem.SYSTEM
                ),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        }
        
        // Priority 3: Minithumbnail Fallback (Low Res - embedded in message)
        // This runs if download failed OR was skipped due to failure cache
        val minithumbnailData = extractMinithumbnail(message.content)
        if (minithumbnailData != null) {
             Timber.d("TelegramCoilFetcher: Using Minithumbnail fallback for $uri")
             val bitmap = BitmapFactory.decodeByteArray(minithumbnailData, 0, minithumbnailData.size)
             if (bitmap != null) {
                 return DrawableResult(
                     drawable = BitmapDrawable(context.resources, bitmap),
                     isSampled = true, // It's a low-res sample
                     dataSource = DataSource.MEMORY
                 )
             }
        }
        
        Timber.w("TelegramCoilFetcher: No art found (High-res failed/missing, No embedded, No minithumbnail)")
        return null
    }

    private fun extractMinithumbnail(content: TdApi.MessageContent): ByteArray? {
        return when (content) {
            is TdApi.MessageAudio -> content.audio.albumCoverMinithumbnail?.data
            is TdApi.MessageDocument -> content.document.minithumbnail?.data
            else -> null
        }
    }

    /**
     * Tries to extract embedded album art from the downloaded audio file.
     * Returns the path to the cached art file if successful, null otherwise.
     */
    private suspend fun tryExtractEmbeddedArt(chatId: Long, messageId: Long): String? {
        // Check if we already have cached embedded art
        val cachedArtFile = File(cacheDir, "telegram_embedded_art_${chatId}_${messageId}.jpg")
        if (cachedArtFile.exists() && cachedArtFile.length() > 0) {
            return cachedArtFile.absolutePath
        }

        // Check if there's a "no embedded art" marker to avoid repeated extraction attempts
        val noArtMarker = File(cacheDir, "telegram_embedded_art_${chatId}_${messageId}_none")
        if (noArtMarker.exists()) {
            return null
        }

        // Get the message to find the audio file ID
        val message = telegramRepository.getMessage(chatId, messageId) ?: return null
        val audioFileId = extractAudioFileId(message.content) ?: return null

        // Check if the audio file is already downloaded
        val audioFile = telegramRepository.getFile(audioFileId)
        if (audioFile?.local?.isDownloadingCompleted != true || audioFile.local.path.isNullOrEmpty()) {
            // Audio not downloaded yet - don't wait, just return null to use thumbnail fallback
            Timber.d("TelegramCoilFetcher: Audio file not downloaded yet, using thumbnail fallback")
            return null
        }

        val audioFilePath = audioFile.local.path
        Timber.d("TelegramCoilFetcher: Attempting embedded art extraction from: $audioFilePath")

        // Extract embedded art using MediaMetadataRetriever
        val extractedPath = extractAndCacheEmbeddedArt(audioFilePath, cachedArtFile, noArtMarker)
        
        // Notify that embedded art was extracted (for UI color refresh)
        if (extractedPath != null) {
            telegramCacheManager?.notifyEmbeddedArtExtracted(chatId, messageId)
        }
        
        return extractedPath
    }

    /**
     * Extracts the audio file ID from message content.
     */
    private fun extractAudioFileId(content: TdApi.MessageContent?): Int? {
        return when (content) {
            is TdApi.MessageAudio -> content.audio.audio.id
            is TdApi.MessageDocument -> content.document.document.id
            else -> null
        }
    }

    /**
     * Extracts embedded art from an audio file and caches it.
     * Creates a marker file if no art is found to avoid repeated extraction attempts.
     */
    private fun extractAndCacheEmbeddedArt(
        audioFilePath: String,
        cacheFile: File,
        noArtMarker: File
    ): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFilePath)
            val embeddedPicture = retriever.embeddedPicture

            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                // Validate that it's actually an image
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size, options)
                
                if (options.outWidth > 0 && options.outHeight > 0) {
                    // Valid image - save to cache
                    FileOutputStream(cacheFile).use { fos ->
                        fos.write(embeddedPicture)
                    }
                    Timber.d("TelegramCoilFetcher: Extracted and cached embedded art (${options.outWidth}x${options.outHeight})")
                    cacheFile.absolutePath
                } else {
                    Timber.w("TelegramCoilFetcher: Embedded picture data is not a valid image")
                    noArtMarker.createNewFile()
                    null
                }
            } else {
                Timber.d("TelegramCoilFetcher: No embedded picture in audio file")
                noArtMarker.createNewFile()
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "TelegramCoilFetcher: Failed to extract embedded art from $audioFilePath")
            noArtMarker.createNewFile()
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    /**
     * Extracts the thumbnail file ID from a message.
     * Supports MessageAudio and MessageDocument content types.
     */
    private suspend fun extractThumbnailFileId(chatId: Long, messageId: Long): Int? {
        val message = telegramRepository.getMessage(chatId, messageId) ?: return null
        return extractFileIdFromContent(message.content)
    }

    /**
     * Extracts the file ID from message content (audio thumbnail or document thumbnail).
     * Priority: albumCoverThumbnail > externalAlbumCovers > document thumbnail
     */
    private fun extractFileIdFromContent(content: TdApi.MessageContent?): Int? {
        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                // Priority 1: Main album cover thumbnail (from TDLib)
                // Priority 2: External album covers (from Spotify/Apple Music metadata)
                val thumbnail = audio.albumCoverThumbnail
                    ?: audio.externalAlbumCovers?.maxByOrNull { it.width * it.height }
                thumbnail?.file?.id
            }
            is TdApi.MessageDocument -> {
                content.document.thumbnail?.file?.id
            }
            else -> null
        }
    }

    /**
     * Downloads a file with automatic retry using refreshed message data.
     * If the first download fails (stale reference), it refreshes the message
     * from the server and retries with the new file ID.
     */
    private suspend fun downloadWithRetry(
        initialFileId: Int,
        chatId: Long,
        messageId: Long
    ): String? {
        // Attempt 1: Download with initial file ID
        try {
            val path = telegramRepository.downloadFileAwait(initialFileId, 32)
            if (path != null) return path
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.w("TelegramCoilFetcher: Request cancelled for fileId: $initialFileId")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "TelegramCoilFetcher: First download attempt failed for fileId: $initialFileId")
        }

        // Attempt 2: Refresh message and retry with fresh file ID
        Timber.d("TelegramCoilFetcher: Attempting refresh for message $messageId")
        val refreshedMessage = telegramRepository.refreshMessage(chatId, messageId)
        val refreshedFileId = extractFileIdFromContent(refreshedMessage?.content)

        if (refreshedFileId == null) {
            Timber.e("TelegramCoilFetcher: Refresh failed - no thumbnail in refreshed message")
            telegramCacheManager?.markArtFailed(chatId, messageId)
            return null
        }

        // Logic Change: Retry even if file ID is same, as refresh might have fixed internal access hash/file state
        if (refreshedFileId == initialFileId) {
             Timber.d("TelegramCoilFetcher: Retrying download after refresh (same fileId: $refreshedFileId)")
        } else {
             Timber.d("TelegramCoilFetcher: Refreshed fileId: $refreshedFileId (was: $initialFileId)")
        }

        return try {
            val result = telegramRepository.downloadFileAwait(refreshedFileId, 32)
            if (result == null) {
                // If it fails twice, mark as failed to avoid immediate retries
                telegramCacheManager?.markArtFailed(chatId, messageId)
            }
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "TelegramCoilFetcher: Retry download failed for fileId: $refreshedFileId")
            telegramCacheManager?.markArtFailed(chatId, messageId)
            null
        }
    }

    /**
     * Factory for creating TelegramCoilFetcher instances.
     * Registered with Coil's ImageLoader to handle telegram_art:// URIs.
     */
    class Factory @Inject constructor(
        private val telegramRepository: TelegramRepository,
        private val telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager
    ) : Fetcher.Factory<Uri> {
        
        private var cacheDir: File? = null
        
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "telegram_art") {
                val cache = cacheDir ?: options.context.cacheDir.also { cacheDir = it }
                TelegramCoilFetcher(options.context, data, telegramRepository, cache, telegramCacheManager)
            } else {
                null
            }
        }
    }
}
