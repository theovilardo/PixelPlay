package com.theveloper.pixelplay.data.repository

import android.util.Log
import android.util.LruCache
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching and caching artist images from Deezer API.
 * Uses both in-memory LRU cache and Room database for persistent storage.
 */
@Singleton
class ArtistImageRepository @Inject constructor(
    private val deezerApiService: DeezerApiService,
    private val musicDao: MusicDao
) {
    companion object {
        private const val TAG = "ArtistImageRepository"
        private const val CACHE_SIZE = 100 // Number of artist images to cache in memory
    }

    // In-memory LRU cache for quick access
    private val memoryCache = LruCache<String, String>(CACHE_SIZE)
    
    // Mutex to prevent duplicate API calls for the same artist
    private val fetchMutex = Mutex()
    private val pendingFetches = mutableSetOf<String>()

    /**
     * Get artist image URL, fetching from Deezer if not cached.
     * @param artistName Name of the artist
     * @param artistId Room database ID of the artist (for caching)
     * @return Image URL or null if not found
     */
    suspend fun getArtistImageUrl(artistName: String, artistId: Long): String? {
        if (artistName.isBlank()) return null

        val normalizedName = artistName.trim().lowercase()

        // Check memory cache first
        memoryCache.get(normalizedName)?.let { cachedUrl ->
            return cachedUrl
        }

        // Check database cache
        val dbCachedUrl = withContext(Dispatchers.IO) {
            musicDao.getArtistImageUrl(artistId)
        }
        if (!dbCachedUrl.isNullOrEmpty()) {
            memoryCache.put(normalizedName, dbCachedUrl)
            return dbCachedUrl
        }

        // Fetch from Deezer API
        return fetchAndCacheArtistImage(artistName, artistId, normalizedName)
    }

    /**
     * Prefetch artist images for a list of artists in background.
     * Useful for batch loading when displaying artist lists.
     */
    suspend fun prefetchArtistImages(artists: List<Pair<Long, String>>) {
        withContext(Dispatchers.IO) {
            artists.forEach { (artistId, artistName) ->
                try {
                    getArtistImageUrl(artistName, artistId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to prefetch image for $artistName: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchAndCacheArtistImage(
        artistName: String,
        artistId: Long,
        normalizedName: String
    ): String? {
        // Prevent duplicate fetches for the same artist
        fetchMutex.withLock {
            if (pendingFetches.contains(normalizedName)) {
                return null // Already fetching
            }
            pendingFetches.add(normalizedName)
        }

        return try {
            withContext(Dispatchers.IO) {
                val response = deezerApiService.searchArtist(artistName, limit = 1)
                val deezerArtist = response.data.firstOrNull()

                if (deezerArtist != null) {
                    // Use picture_medium for list views, picture_big for detail views
                    // We store the medium size as default, UI can request bigger sizes if needed
                    val imageUrl = deezerArtist.pictureMedium 
                        ?: deezerArtist.pictureBig 
                        ?: deezerArtist.picture

                    if (!imageUrl.isNullOrEmpty()) {
                        // Cache in memory
                        memoryCache.put(normalizedName, imageUrl)
                        
                        // Cache in database
                        musicDao.updateArtistImageUrl(artistId, imageUrl)
                        
                        Log.d(TAG, "Fetched and cached image for $artistName: $imageUrl")
                        imageUrl
                    } else {
                        null
                    }
                } else {
                    Log.d(TAG, "No Deezer artist found for: $artistName")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist image for $artistName: ${e.message}")
            null
        } finally {
            fetchMutex.withLock {
                pendingFetches.remove(normalizedName)
            }
        }
    }

    /**
     * Clear all cached images. Useful for debugging or forced refresh.
     */
    fun clearCache() {
        memoryCache.evictAll()
    }
}
