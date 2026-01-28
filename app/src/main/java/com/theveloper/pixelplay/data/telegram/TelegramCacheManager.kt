package com.theveloper.pixelplay.data.telegram

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized cache management for Telegram files.
 * 
 * Responsibilities:
 * - Track active playback to avoid deleting in-use files
 * - Clean up audio files after playback ends
 * - Manage embedded art cache with size limits
 * - Provide full cache clearing for database rebuild
 * - Emit events when embedded art is extracted (for UI color refresh)
 */
@Singleton
class TelegramCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramClientManager: TelegramClientManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Track currently playing Telegram file to avoid deleting it
    @Volatile
    private var activeFileId: Int? = null
    
    // Track recently played files for potential cleanup
    private val recentlyPlayedFileIds = ConcurrentHashMap.newKeySet<Int>()
    
    // Maximum embedded art cache size in bytes (50MB)
    private val maxEmbeddedArtCacheSize = 50L * 1024 * 1024
    
    // Event flow for when embedded art is extracted (emits the telegram_art:// URI)
    // PlayerViewModel observes this to refresh album colors
    private val _embeddedArtUpdated = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val embeddedArtUpdated: SharedFlow<String> = _embeddedArtUpdated.asSharedFlow()
    
    /**
     * Called when embedded art is successfully extracted for a Telegram song.
     * Emits the album art URI so UI can refresh colors.
     */
    fun notifyEmbeddedArtExtracted(chatId: Long, messageId: Long) {
        val artUri = "telegram_art://$chatId/$messageId"
        Timber.d("TelegramCacheManager: Embedded art extracted, emitting update for $artUri")
        _embeddedArtUpdated.tryEmit(artUri)
    }

    // Cache for failed art requests to prevent repeated network calls (URI -> Timestamp)
    private val failedArtCache = ConcurrentHashMap<String, Long>()
    private val FAILED_ART_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes retry backoff

    fun isArtFailed(chatId: Long, messageId: Long): Boolean {
        val key = "${chatId}_${messageId}"
        val timestamp = failedArtCache[key] ?: return false
        
        if (System.currentTimeMillis() - timestamp > FAILED_ART_EXPIRY_MS) {
            failedArtCache.remove(key)
            return false
        }
        return true
    }

    fun markArtFailed(chatId: Long, messageId: Long) {
        val key = "${chatId}_${messageId}"
        failedArtCache[key] = System.currentTimeMillis()
    }
    
    /**
     * Mark a file as currently being played.
     * This prevents it from being cleaned up.
     */
    // LRU Cache for audio files to keep recent songs (Buffer of ~5 songs)
    // This allows "Previous" / "Next" navigation without re-downloading, while keeping storage low.
    private val audioFileHistory = java.util.Collections.synchronizedList(java.util.LinkedList<Int>())
    private val HISTORY_CACHE_LIMIT = 5 

    /**
     * Mark a file as currently being played and manage the rolling cache.
     */
    fun setActivePlayback(fileId: Int?) {
        if (fileId == null) return
        
        // LRU Logic: Move to end (most recent)
        synchronized(audioFileHistory) {
            if (audioFileHistory.contains(fileId)) {
                audioFileHistory.remove(fileId)
            }
            audioFileHistory.add(fileId)
            
            Timber.d("TelegramCacheManager: Active file $fileId. History size: ${audioFileHistory.size}")
            
            // Trim One Oldest file if over limit
            while (audioFileHistory.size > HISTORY_CACHE_LIMIT) {
                val oldestFileId = audioFileHistory.removeAt(0) // Remove first (oldest)
                Timber.d("TelegramCacheManager: Cache full. Deleting oldest file: $oldestFileId")
                cleanupAudioFile(oldestFileId)
            }
        }
        
        activeFileId = fileId
    }
    
    /**
     * Called when playback stops completely.
     * We do NOT delete immediately anymore; we let the LRU history handle it
     * or the Startup Cleanup handle it. This improves "resume" performance.
     */
    fun onPlaybackStopped() {
        val fileId = activeFileId
        if (fileId != null) {
            Timber.d("TelegramCacheManager: Playback stopped (File retained in history buffer)")
            activeFileId = null
        }
    }
    
    /**
     * Clean up a specific audio file that is evicted from the history buffer.
     */
    private fun cleanupAudioFile(fileId: Int) {
        scope.launch {
            // No strict 10s delay needed here since we have a buffer of 4 other songs 
            // before this one gets pushed out. But a small safety delay is always good.
            kotlinx.coroutines.delay(2_000) 
            
            try {
                // Use TDLib's DeleteFile to remove the downloaded file
                @Suppress("UNUSED_VARIABLE")
                val result: TdApi.Ok = telegramClientManager.sendRequest(TdApi.DeleteFile(fileId))
                Timber.d("TelegramCacheManager: Deleted evicted audio file $fileId")
            } catch (e: Exception) {
                Timber.w("TelegramCacheManager: Could not delete file $fileId: ${e.message}")
            }
        }
    }
    
    /**
     * Clear embedded art cache files.
     * These are stored in app cache directory with telegram_embedded_art_ prefix.
     */
    fun clearEmbeddedArtCache() {
        scope.launch {
            try {
                val cacheDir = context.cacheDir
                val embeddedArtFiles = cacheDir.listFiles { file ->
                    file.name.startsWith("telegram_embedded_art_")
                } ?: emptyArray()
                
                var deletedCount = 0
                embeddedArtFiles.forEach { file ->
                    if (file.delete()) deletedCount++
                }
                Timber.d("TelegramCacheManager: Cleared $deletedCount embedded art files")
            } catch (e: Exception) {
                Timber.e(e, "TelegramCacheManager: Error clearing embedded art cache")
            }
        }
    }
    
    /**
     * Trim embedded art cache to stay within size limits.
     * Uses LRU strategy based on last modified time.
     */
    fun trimEmbeddedArtCache() {
        scope.launch {
            try {
                val cacheDir = context.cacheDir
                val embeddedArtFiles = cacheDir.listFiles { file ->
                    file.name.startsWith("telegram_embedded_art_") && 
                    !file.name.endsWith("_none") // Skip marker files
                }?.toMutableList() ?: return@launch
                
                // Calculate total size
                var totalSize = embeddedArtFiles.sumOf { it.length() }
                
                if (totalSize <= maxEmbeddedArtCacheSize) {
                    return@launch // Within limits
                }
                
                // Sort by last modified (oldest first) for LRU eviction
                embeddedArtFiles.sortBy { it.lastModified() }
                
                var deletedCount = 0
                for (file in embeddedArtFiles) {
                    if (totalSize <= maxEmbeddedArtCacheSize * 0.8) { // Trim to 80% of max
                        break
                    }
                    val fileSize = file.length()
                    if (file.delete()) {
                        totalSize -= fileSize
                        deletedCount++
                    }
                }
                Timber.d("TelegramCacheManager: Trimmed $deletedCount old embedded art files")
            } catch (e: Exception) {
                Timber.e(e, "TelegramCacheManager: Error trimming embedded art cache")
            }
        }
    }
    
    /**
     * Clear all TDLib cached files using OptimizeStorage API.
     * This is called during database rebuild.
     */
    suspend fun clearTdLibCache() {
        try {
            // OptimizeStorage parameters:
            // - size: Maximum size of TDLib files (0 = delete all)
            // - ttl: Time to live in seconds (0 = delete based on size only)
            // - count: Maximum file count (0 = unlimited)
            // - immunityDelay: Don't delete files accessed recently (in seconds)
            // - fileTypes: Which file types to optimize (null = all)
            // - chatIds: Which chats to optimize (null = all)
            // - excludeChatIds: Chats to exclude (null = none)
            // - returnDeletedFileStatistics: Return stats
            // - chatLimit: Max number of chats (0 = all)
            
            val result = telegramClientManager.sendRequest<TdApi.StorageStatistics>(
                TdApi.OptimizeStorage(
                    0L,     // maxSize: 0 = clear everything
                    0,      // ttl: 0 = ignore TTL
                    0,      // count: unlimited
                    0,      // immunityDelay: 0 = delete all
                    null,   // fileTypes: all types
                    null,   // chatIds: all chats
                    null,   // excludeChatIds: none
                    false,  // returnDeletedFileStatistics
                    0       // chatLimit: all chats
                )
            )
            Timber.d("TelegramCacheManager: Cleared TDLib cache. Stats: ${result.size} bytes in ${result.count} files")
        } catch (e: Exception) {
            Timber.e(e, "TelegramCacheManager: Error clearing TDLib cache")
        }
    }
    
    /**
     * Full cache clear - called during database rebuild.
     * Clears:
     * 1. TDLib downloaded files (audio + thumbnails)
     * 2. Embedded art cache
     * 3. Memory caches
     */
    suspend fun clearAllCache() {
        Timber.d("TelegramCacheManager: Clearing all Telegram cache")
        
        // Clear memory tracking
        activeFileId = null
        recentlyPlayedFileIds.clear()
        
        // Clear failed art cache
        failedArtCache.clear()
        
        // Clear embedded art cache
        clearEmbeddedArtCache()
        
        // Clear TDLib cache
        clearTdLibCache()
        
        Timber.d("TelegramCacheManager: All Telegram cache cleared")
    }
}
