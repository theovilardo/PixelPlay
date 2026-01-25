package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import androidx.core.net.toUri
import com.google.gson.Gson
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lyrics.LrcLibApiService
import com.theveloper.pixelplay.data.network.lyrics.LrcLibResponse
import com.theveloper.pixelplay.utils.LogUtils
import com.theveloper.pixelplay.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private fun Lyrics.isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()

/**
 * LyricsData for JSON disk cache (matches Rhythm's format)
 */
private data class LyricsData(
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val wordByWordLyrics: String? = null
) {
    fun hasLyrics(): Boolean = !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()
}

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lrcLibApiService: LrcLibApiService,
    private val lyricsDao: com.theveloper.pixelplay.data.database.LyricsDao
) : LyricsRepository {


    companion object {
        private const val TAG = "LyricsRepository"
        
        // Cache sizes (matching Rhythm)
        private const val MAX_LYRICS_CACHE_SIZE = 150
        
        // API rate limiting constants (matching Rhythm)
        private const val LRCLIB_MIN_DELAY = 100L
        private const val MAX_CALLS_PER_MINUTE = 30
    }

    // Repository scope for background tasks
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // LRU Cache with Rhythm-style LinkedHashMap (access-order for true LRU)
    private val lyricsCache = object : LinkedHashMap<String, Lyrics>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Lyrics>?): Boolean {
            return size > MAX_LYRICS_CACHE_SIZE
        }
    }

    // Rate limiting state (matching Rhythm)
    private val lastApiCalls = mutableMapOf<String, Long>()
    private val apiCallCounts = mutableMapOf<String, Int>()

    // Gson for JSON cache
    private val gson = Gson()

    /**
     * Calculate delay needed before next API call (matching Rhythm)
     */
    private fun calculateApiDelay(apiName: String, currentTime: Long): Long {
        val lastCall = lastApiCalls[apiName] ?: 0L
        val minDelay = when (apiName.lowercase()) {
            "lrclib" -> LRCLIB_MIN_DELAY
            else -> 250L
        }

        val timeSinceLastCall = currentTime - lastCall
        if (timeSinceLastCall < minDelay) {
            return minDelay - timeSinceLastCall
        }

        // Check if we're making too many calls per minute
        val callsInLastMinute = apiCallCounts[apiName] ?: 0
        if (callsInLastMinute >= MAX_CALLS_PER_MINUTE) {
            // Exponential backoff
            return minDelay * 2
        }

        return 0L
    }

    /**
     * Update last API call timestamp (matching Rhythm)
     */
    private fun updateLastApiCall(apiName: String, timestamp: Long) {
        lastApiCalls[apiName] = timestamp

        // Update call count for rate limiting
        val currentCount = apiCallCounts[apiName] ?: 0
        apiCallCounts[apiName] = currentCount + 1

        // Reset counter every minute
        if (currentCount == 0) {
            repositoryScope.launch {
                delay(60000)
                apiCallCounts[apiName] = 0
            }
        }
    }

    /**
     * Main lyrics fetching method with source preference support (matching Rhythm)
     */
    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        
        Log.d(TAG, "===== FETCH LYRICS START: ${song.displayArtist} - ${song.title} (forceRefresh=$forceRefresh, source=$sourcePreference) =====")

        // Check in-memory cache unless force refresh (early return - matching Rhythm)
        if (!forceRefresh) {
            synchronized(lyricsCache) {
                lyricsCache[cacheKey]?.let { cached ->
                    Log.d(TAG, "===== RETURNING IN-MEMORY CACHED LYRICS =====")
                    return@withContext cached
                }
            }
            Log.d(TAG, "===== NO IN-MEMORY CACHE HIT, proceeding to fetch =====")
        } else {
            Log.d(TAG, "===== FORCE REFRESH - BYPASSING IN-MEMORY CACHE =====")
        }

        // Define source fetchers (matching Rhythm pattern)
        val fetchFromLocal: suspend () -> Lyrics? = {
            findLocalLrcFile(song)
        }

        val fetchFromEmbedded: suspend () -> Lyrics? = {
            loadLyricsFromStorage(song)
        }

        val fetchFromAPI: suspend () -> Lyrics? = {
            fetchLyricsFromAPI(song)
        }

        // Try sources in order based on preference, with fallback (matching Rhythm)
        val sourceFetchers = when (sourcePreference) {
            LyricsSourcePreference.API_FIRST -> listOf(fetchFromAPI, fetchFromEmbedded, fetchFromLocal)
            LyricsSourcePreference.EMBEDDED_FIRST -> listOf(fetchFromEmbedded, fetchFromAPI, fetchFromLocal)
            LyricsSourcePreference.LOCAL_FIRST -> listOf(fetchFromLocal, fetchFromEmbedded, fetchFromAPI)
        }

        // Try each source in order until we find lyrics (early return on success)
        for ((index, fetcher) in sourceFetchers.withIndex()) {
            try {
                val lyrics = fetcher()
                if (lyrics != null && lyrics.isValid()) {
                    val sourceName = when (index) {
                        0 -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "API"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "Embedded"
                            LyricsSourcePreference.LOCAL_FIRST -> "Local"
                        }
                        1 -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "Embedded"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "API"
                            LyricsSourcePreference.LOCAL_FIRST -> "Embedded"
                        }
                        else -> when (sourcePreference) {
                            LyricsSourcePreference.API_FIRST -> "Local"
                            LyricsSourcePreference.EMBEDDED_FIRST -> "Local"
                            LyricsSourcePreference.LOCAL_FIRST -> "API"
                        }
                    }
                    Log.d(TAG, "Found lyrics from $sourceName for: ${song.displayArtist} - ${song.title}")
                    
                    // Cache the result
                    synchronized(lyricsCache) {
                        lyricsCache[cacheKey] = lyrics
                    }
                    
                    // Save to JSON disk cache if from API
                    if (sourceName == "API") {
                        saveLocalLyricsJson(song, lyrics)
                    }
                    
                    return@withContext lyrics
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from source ${index + 1}: ${e.message}")
                // Continue to next source
            }
        }

        // No lyrics found from any source
        Log.d(TAG, "No lyrics found from any source for: ${song.displayArtist} - ${song.title}")
        return@withContext null
    }

    /**
     * Fetches lyrics from LRCLIB API with rate limiting (matching Rhythm)
     */
    private suspend fun fetchLyricsFromAPI(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        // Check JSON disk cache first (matching Rhythm)
        val cachedJson = loadLocalLyricsJson(song)
        if (cachedJson != null) {
            Log.d(TAG, "===== LOADED LYRICS FROM JSON DISK CACHE =====")
            return@withContext cachedJson
        }

        // Apply rate limiting
        val currentTime = System.currentTimeMillis()
        val delayNeeded = calculateApiDelay("lrclib", currentTime)
        if (delayNeeded > 0) {
            Log.d(TAG, "Rate limiting: waiting ${delayNeeded}ms before API call")
            delay(delayNeeded)
        }
        updateLastApiCall("lrclib", System.currentTimeMillis())

        try {
            val cleanArtist = song.displayArtist.trim().replace(Regex("\\(.*?\\)"), "").trim()
            val cleanTitle = song.title.trim().replace(Regex("\\(.*?\\)"), "").trim()

            // Strategy 1: Search by track name and artist name (matching Rhythm)
            var results = runCatching {
                lrcLibApiService.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
            }.getOrNull()

            // Strategy 2: Combined query (matching Rhythm)
            if (results.isNullOrEmpty()) {
                val query = "$cleanArtist $cleanTitle"
                results = runCatching {
                    lrcLibApiService.searchLyrics(query = query)
                }.getOrNull()
            }

            // Strategy 3: Simplified names without feat. etc (matching Rhythm)
            if (results.isNullOrEmpty()) {
                val simplifiedArtist = cleanArtist.split(" feat.", " ft.", " featuring").first().trim()
                val simplifiedTitle = cleanTitle.split(" feat.", " ft.", " featuring").first().trim()
                results = runCatching {
                    lrcLibApiService.searchLyrics(trackName = simplifiedTitle, artistName = simplifiedArtist)
                }.getOrNull()
            }

            if (results.isNullOrEmpty()) {
                Log.d(TAG, "No results from LRCLIB API")
                return@withContext null
            }

            // Find best match - prioritize exact matches, then synced lyrics (matching Rhythm)
            val songDurationSeconds = song.duration / 1000
            val bestMatch = results.firstOrNull { result ->
                val artistMatch = result.artistName.lowercase().contains(cleanArtist.lowercase()) ||
                        cleanArtist.lowercase().contains(result.artistName.lowercase())
                val titleMatch = result.name.lowercase().contains(cleanTitle.lowercase()) ||
                        cleanTitle.lowercase().contains(result.name.lowercase())
                val durationDiff = abs(result.duration - songDurationSeconds)

                (artistMatch && titleMatch) && durationDiff <= 15 && hasLyrics(result)
            } ?: results.firstOrNull { hasSyncedLyrics(it) && abs(it.duration - songDurationSeconds) <= 15 }
            ?: results.firstOrNull { hasLyrics(it) && abs(it.duration - songDurationSeconds) <= 15 }

            if (bestMatch != null) {
                val rawLyrics = bestMatch.syncedLyrics ?: bestMatch.plainLyrics
                if (!rawLyrics.isNullOrBlank()) {
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                    if (parsedLyrics.isValid()) {
                        Log.d(TAG, "LRCLIB lyrics found - Synced: ${!bestMatch.syncedLyrics.isNullOrBlank()}, Plain: ${!bestMatch.plainLyrics.isNullOrBlank()}")
                        
                        // Save to database
                        // Save to database
                        lyricsDao.insert(
                            com.theveloper.pixelplay.data.database.LyricsEntity(
                                songId = song.id.toLong(),
                                content = rawLyrics,
                                isSynced = !bestMatch.syncedLyrics.isNullOrBlank(),
                                source = "remote"
                            )
                        )
                        
                        return@withContext parsedLyrics
                    }
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB lyrics fetch failed: ${e.message}", e)
            return@withContext null
        }
    }

    private fun hasLyrics(response: LrcLibResponse): Boolean =
        !response.plainLyrics.isNullOrBlank() || !response.syncedLyrics.isNullOrBlank()

    private fun hasSyncedLyrics(response: LrcLibResponse): Boolean =
        !response.syncedLyrics.isNullOrBlank()

    /**
     * Find local .lrc file next to the music file (matching Rhythm)
     */
    private suspend fun findLocalLrcFile(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val songFile = File(song.path)
            val directory = songFile.parentFile ?: return@withContext null
            val songNameWithoutExt = songFile.nameWithoutExtension

            if (directory.exists()) {
                // Look for .lrc file with same name as the song
                val lrcFile = File(directory, "$songNameWithoutExt.lrc")
                if (lrcFile.exists() && lrcFile.canRead()) {
                    val lrcContent = lrcFile.readText()
                    val parsed = parseLrcFile(lrcContent)
                    if (parsed != null) {
                        Log.d(TAG, "===== FOUND LOCAL .LRC FILE: ${lrcFile.name} =====")
                        return@withContext parsed
                    }
                }

                // Also try with artist - title pattern
                val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                val alternativeLrcFile = File(directory, "${cleanArtist}_${cleanTitle}.lrc")
                if (alternativeLrcFile.exists() && alternativeLrcFile.canRead()) {
                    val lrcContent = alternativeLrcFile.readText()
                    val parsed = parseLrcFile(lrcContent)
                    if (parsed != null) {
                        Log.d(TAG, "===== FOUND LOCAL .LRC FILE (alt pattern): ${alternativeLrcFile.name} =====")
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for .lrc file", e)
        }
        return@withContext null
    }

    /**
     * Parse .lrc file content into Lyrics format (matching Rhythm)
     */
    private fun parseLrcFile(lrcContent: String): Lyrics? {
        if (lrcContent.isBlank()) return null
        
        // Use existing LyricsUtils parser
        val parsed = LyricsUtils.parseLyrics(lrcContent)
        return if (parsed.isValid()) parsed else null
    }

    /**
     * Save lyrics to JSON disk cache (matching Rhythm)
     */
    private fun saveLocalLyricsJson(song: Song, lyrics: Lyrics) {
        try {
            val fileName = "${song.id}.json"
            val lyricsDir = File(context.filesDir, "lyrics")
            lyricsDir.mkdirs()

            val lyricsData = LyricsData(
                plainLyrics = lyrics.plain?.joinToString("\n"),
                syncedLyrics = lyrics.synced?.joinToString("\n") { "[${formatTimestamp(it.time)}]${it.line}" }
            )

            val file = File(lyricsDir, fileName)
            val json = gson.toJson(lyricsData)
            file.writeText(json)
            Log.d(TAG, "Saved lyrics to JSON cache: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving lyrics to JSON cache: ${e.message}", e)
        }
    }

    /**
     * Load lyrics from JSON disk cache (matching Rhythm)
     */
    private fun loadLocalLyricsJson(song: Song): Lyrics? {
        try {
            val fileName = "${song.id}.json"
            val file = File(context.filesDir, "lyrics/$fileName")
            
            if (file.exists()) {
                val json = file.readText()
                val data = gson.fromJson(json, LyricsData::class.java)
                if (data.hasLyrics()) {
                    val rawLyrics = data.syncedLyrics ?: data.plainLyrics
                    val parsed = LyricsUtils.parseLyrics(rawLyrics)
                    if (parsed.isValid()) {
                        return parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading JSON cache: ${e.message}", e)
        }
        return null
    }

    private fun formatTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    /**
     * Load embedded lyrics from audio file metadata
     */
    private suspend fun loadLyricsFromStorage(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        // First check database for persisted lyrics (was user-imported or cached)
        val persisted = lyricsDao.getLyrics(song.id.toLong())
        if (persisted != null && !persisted.content.isBlank()) {
            val parsedLyrics = LyricsUtils.parseLyrics(persisted.content)
            if (parsedLyrics.isValid()) {
                // If we found it in DB, we treat it as "embedded" or "locally cached" for this flow
                return@withContext parsedLyrics.copy(areFromRemote = false)
            }
        }

        // Then try to read from file metadata
        return@withContext try {
            val uri = song.contentUriString.toUri()
            val tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                LogUtils.w(this@LyricsRepositoryImpl, "Could not create temp file from URI: ${song.contentUriString}")
                return@withContext null
            }

            try {
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val metadata = TagLib.getMetadata(fd.detachFd())
                    val lyricsField = metadata?.propertyMap?.get("LYRICS")?.firstOrNull()

                    if (!lyricsField.isNullOrBlank()) {
                        val parsedLyrics = LyricsUtils.parseLyrics(lyricsField)
                        if (parsedLyrics.isValid()) {
                            Log.d(TAG, "===== FOUND EMBEDDED LYRICS =====")
                            parsedLyrics.copy(areFromRemote = false)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error reading lyrics from file metadata")
            null
        }
    }

    // ========== Original methods (kept for backward compatibility) ==========

    override suspend fun fetchFromRemote(song: Song): Result<Pair<Lyrics, String>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@LyricsRepositoryImpl, "Fetching lyrics from remote for: ${song.title}")

            // First, try the search API which is more flexible, then pick the best match
            val searchResult = searchRemote(song)
            if (searchResult.isSuccess) {
                val (_, results) = searchResult.getOrThrow()
                if (results.isNotEmpty()) {
                    // Pick the first result (already sorted by synced priority)
                    val best = results.first()
                    val rawLyricsToSave = best.rawLyrics



                    lyricsDao.insert(
                         com.theveloper.pixelplay.data.database.LyricsEntity(
                             songId = song.id.toLong(),
                             content = rawLyricsToSave,
                             isSynced = !best.lyrics.synced.isNullOrEmpty(),
                             source = "remote"
                         )
                    )

                    val cacheKey = generateCacheKey(song.id)
                    synchronized(lyricsCache) {
                        lyricsCache[cacheKey] = best.lyrics
                    }
                    LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics for: ${song.title}")

                    return@withContext Result.success(Pair(best.lyrics, rawLyricsToSave))
                }
            }

            // Fallback: Try the exact match API (less likely to succeed, but worth a shot)
            val response = lrcLibApiService.getLyrics(
                trackName = song.title,
                artistName = song.displayArtist,
                albumName = song.album,
                duration = (song.duration / 1000).toInt()
            )

            if (response != null && (!response.syncedLyrics.isNullOrEmpty() || !response.plainLyrics.isNullOrEmpty())) {
                val rawLyricsToSave = response.syncedLyrics ?: response.plainLyrics!!

                val parsedLyrics = LyricsUtils.parseLyrics(rawLyricsToSave).copy(areFromRemote = true)
                if (!parsedLyrics.isValid()) {
                    return@withContext Result.failure(LyricsException("Parsed lyrics are empty"))
                }

                lyricsDao.insert(
                     com.theveloper.pixelplay.data.database.LyricsEntity(
                         songId = song.id.toLong(),
                         content = rawLyricsToSave,
                         isSynced = !parsedLyrics.synced.isNullOrEmpty(),
                         source = "remote"
                     )
                )

                val cacheKey = generateCacheKey(song.id)
                synchronized(lyricsCache) {
                    lyricsCache[cacheKey] = parsedLyrics
                }
                LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics (exact match) for: ${song.title}")

                Result.success(Pair(parsedLyrics, rawLyricsToSave))
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException())
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error fetching lyrics from remote")
            when {
                e is HttpException && e.code() == 404 -> Result.failure(NoLyricsFoundException())
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.failed_to_fetch_lyrics_from_remote), e))
            }
        }
    }

    override suspend fun searchRemote(song: Song): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@LyricsRepositoryImpl, "Searching remote for lyrics for: ${song.title} by ${song.displayArtist}")

            val combinedQuery = "${song.title} ${song.displayArtist}"

            // SEQUENTIAL STRATEGY: Try each search strategy one by one
            val strategies: List<suspend () -> Array<LrcLibResponse>?> = listOf(
                { runCatching { lrcLibApiService.searchLyrics(query = combinedQuery, artistName = song.displayArtist) }.getOrNull() },
                { runCatching { lrcLibApiService.searchLyrics(trackName = song.title, artistName = song.displayArtist) }.getOrNull() },
                { runCatching { lrcLibApiService.searchLyrics(trackName = song.title) }.getOrNull() },
                { runCatching { lrcLibApiService.searchLyrics(query = song.title) }.getOrNull() }
            )

            var allResults: List<LrcLibResponse> = emptyList()
            for ((index, strategy) in strategies.withIndex()) {
                LogUtils.d(this@LyricsRepositoryImpl, "Trying search strategy ${index + 1}/4...")
                val result = strategy()
                if (!result.isNullOrEmpty()) {
                    LogUtils.d(this@LyricsRepositoryImpl, "Strategy ${index + 1} returned ${result.size} results")
                    allResults = result.toList()
                    break
                }
                LogUtils.d(this@LyricsRepositoryImpl, "Strategy ${index + 1} returned no results, trying next...")
            }

            val uniqueResults = allResults.distinctBy { it.id }

            if (uniqueResults.isNotEmpty()) {
                val songDurationSeconds = song.duration / 1000
                val results = uniqueResults.mapNotNull { response ->
                    val durationDiff = abs(response.duration - songDurationSeconds)
                    if (durationDiff > 15) {
                        LogUtils.d(this@LyricsRepositoryImpl, "  Skipping '${response.name}' - duration mismatch: ${response.duration}s vs ${songDurationSeconds}s (diff: ${durationDiff}s)")
                        return@mapNotNull null
                    }

                    val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                    if (!parsedLyrics.isValid()) {
                        LogUtils.w(this@LyricsRepositoryImpl, "Parsed lyrics are empty for: ${song.title}")
                        return@mapNotNull null
                    }
                    val hasSynced = !response.syncedLyrics.isNullOrEmpty()
                    LogUtils.d(this@LyricsRepositoryImpl, "  Found: ${response.name} by ${response.artistName} (synced: $hasSynced)")
                    LyricsSearchResult(response, parsedLyrics, rawLyrics)
                }
                    .sortedByDescending { !it.record.syncedLyrics.isNullOrEmpty() }

                if (results.isNotEmpty()) {
                    val syncedCount = results.count { !it.record.syncedLyrics.isNullOrEmpty() }
                    LogUtils.d(this@LyricsRepositoryImpl, "Found ${results.size} lyrics for: ${song.title} ($syncedCount with synced)")
                    Result.success(Pair(combinedQuery, results))
                } else {
                    LogUtils.d(this@LyricsRepositoryImpl, "No matching lyrics found for: ${song.title}")
                    Result.failure(NoLyricsFoundException(combinedQuery))
                }
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException(combinedQuery))
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error searching remote for lyrics")
            when {
                e is SocketTimeoutException -> Result.failure(LyricsException(context.getString(R.string.lyrics_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is IOException -> Result.failure(LyricsException(context.getString(R.string.lyrics_network_error), e))
                e is HttpException -> Result.failure(LyricsException(context.getString(R.string.lyrics_server_error, e.code()), e))
                else -> Result.failure(LyricsException(context.getString(R.string.failed_to_search_for_lyrics), e))
            }
        }
    }

    override suspend fun searchRemoteByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            val query = listOfNotNull(
                title.takeIf { it.isNotBlank() },
                artist?.takeIf { it.isNotBlank() }
            ).joinToString(" ")

            LogUtils.d(this@LyricsRepositoryImpl, "Manual lyrics search: title=$title, artist=$artist")

            // Search using the custom query provided by user
            val responses = lrcLibApiService.searchLyrics(query = query)
                ?.distinctBy { it.id }
                ?: emptyList()

            if (responses.isEmpty()) {
                return@withContext Result.failure(NoLyricsFoundException(query))
            }

            val results = responses.mapNotNull { response ->
                val rawLyrics = response.syncedLyrics ?: response.plainLyrics ?: return@mapNotNull null
                val parsed = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                if (!parsed.isValid()) return@mapNotNull null

                LyricsSearchResult(response, parsed, rawLyrics)
            }.sortedByDescending { !it.record.syncedLyrics.isNullOrEmpty() }

            if (results.isEmpty()) {
                Result.failure(NoLyricsFoundException(query))
            } else {
                Result.success(Pair(query, results))
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Manual search failed")
            Result.failure(LyricsException(context.getString(R.string.failed_to_search_for_lyrics), e)
            )
        }
    }

    override suspend fun updateLyrics(songId: Long, lyricsContent: String): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Updating lyrics for songId: $songId")

        val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
        if (!parsedLyrics.isValid()) {
            LogUtils.w(this@LyricsRepositoryImpl, "Attempted to save empty lyrics for songId: $songId")
            return@withContext
        }

        lyricsDao.insert(
             com.theveloper.pixelplay.data.database.LyricsEntity(
                 songId = songId,
                 content = lyricsContent,
                 isSynced = parsedLyrics.synced?.isNotEmpty() == true,
                 source = "manual"
             )
        )

        val cacheKey = generateCacheKey(songId.toString())
        synchronized(lyricsCache) {
            lyricsCache[cacheKey] = parsedLyrics
        }
        LogUtils.d(this@LyricsRepositoryImpl, "Updated and cached lyrics for songId: $songId")
    }

    override suspend fun resetLyrics(songId: Long): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting lyrics for songId: $songId")
        val cacheKey = generateCacheKey(songId.toString())
        synchronized(lyricsCache) {
            lyricsCache.remove(cacheKey)
        }
        lyricsDao.deleteLyrics(songId)
        
        // Also remove JSON cache
        try {
            val file = File(context.filesDir, "lyrics/${songId}.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting JSON cache: ${e.message}")
        }
    }

    override suspend fun resetAllLyrics(): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting all lyrics")
        synchronized(lyricsCache) {
            lyricsCache.clear()
        }
        lyricsDao.deleteAll()
        
        // Also clear JSON cache directory
        try {
            val lyricsDir = File(context.filesDir, "lyrics")
            if (lyricsDir.exists()) {
                lyricsDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing JSON cache: ${e.message}")
        }
    }

    override suspend fun scanAndAssignLocalLrcFiles(
        songs: List<Song>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Starting bulk scan for .lrc files for ${songs.size} songs")
        val updatedCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val total = songs.size
        
        // Only scan songs that don't have lyrics
        val songsToScan = songs.filter { it.lyrics.isNullOrBlank() }
        val skippedCount = total - songsToScan.size
        processedCount.addAndGet(skippedCount)
        
        LogUtils.d(this@LyricsRepositoryImpl, "Skipping $skippedCount songs that already have lyrics. Scanning ${songsToScan.size} songs.")
        
        onProgress(processedCount.get(), total)
        
        if (songsToScan.isEmpty()) {
            return@withContext 0
        }

        val semaphore = Semaphore(8) // Limit concurrency

        coroutineScope {
            songsToScan.map { song ->
                async {
                    semaphore.withPermit {
                        try {
                            // Find lyrics file
                            val songFile = File(song.path)
                            val directory = songFile.parentFile
                            
                            if (directory != null && directory.exists()) {
                                var foundFile: File? = null
                                
                                // Strategy 1: Exact match name
                                val exactMatch = File(directory, "${songFile.nameWithoutExtension}.lrc")
                                if (exactMatch.exists() && exactMatch.canRead()) {
                                    foundFile = exactMatch
                                }
                                
                                // Strategy 2: Artist - Title
                                if (foundFile == null) {
                                    val cleanArtist = song.displayArtist.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val cleanTitle = song.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val altMatch = File(directory, "${cleanArtist}_${cleanTitle}.lrc")
                                    if (altMatch.exists() && altMatch.canRead()) {
                                        foundFile = altMatch
                                    }
                                }
                                
                                if (foundFile != null) {
                                    val content = foundFile.readText()
                                    // Verify validity
                                    if (LyricsUtils.parseLyrics(content).isValid()) {
                                        lyricsDao.insert(
                                             com.theveloper.pixelplay.data.database.LyricsEntity(
                                                 songId = song.id.toLong(),
                                                 content = content,
                                                 isSynced = LyricsUtils.parseLyrics(content).synced?.isNotEmpty() == true,
                                                 source = "local_file"
                                             )
                                        )
                                        updatedCount.incrementAndGet()
                                        LogUtils.d(this@LyricsRepositoryImpl, "Auto-assigned lyrics from ${foundFile.name}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error scanning lyrics for ${song.title}: ${e.message}")
                        }
                        
                        val current = processedCount.incrementAndGet()
                        if (current % 20 == 0 || current == total) {
                            onProgress(current, total)
                        }
                    }
                }
            }.awaitAll()
        }
        
        LogUtils.d(this@LyricsRepositoryImpl, "Bulk scan complete. Updated ${updatedCount.get()} songs.")
        return@withContext updatedCount.get()
    }

    override fun clearCache() {
        LogUtils.d(this, "Clearing lyrics in-memory cache")
        synchronized(lyricsCache) {
            lyricsCache.clear()
        }
    }

    private fun generateCacheKey(songId: String): String = songId

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else "temp_audio"
                    } else "temp_audio"
                } ?: "temp_audio"

                val tempFile = File.createTempFile("lyrics_", "_$fileName", context.cacheDir)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                tempFile
            }
        } catch (e: Exception) {
            LogUtils.e(this, e, "Error creating temp file from URI")
            null
        }
    }
}

data class LyricsSearchResult(val record: LrcLibResponse, val lyrics: Lyrics, val rawLyrics: String)

data class NoLyricsFoundException(val query: String? = null) : Exception()

class LyricsException(message: String, cause: Throwable? = null) : Exception(message, cause)
