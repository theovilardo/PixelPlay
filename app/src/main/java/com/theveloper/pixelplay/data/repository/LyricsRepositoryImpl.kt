package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.net.toUri
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lyrics.LrcLibApiService
import com.theveloper.pixelplay.data.network.lyrics.LrcLibResponse
import com.theveloper.pixelplay.utils.LogUtils
import com.theveloper.pixelplay.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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

private fun Lyrics.isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()

@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lrcLibApiService: LrcLibApiService,
    private val musicDao: MusicDao
) : LyricsRepository {

    private val lyricsCache = LruCache<String, Lyrics>(100)

    override suspend fun getLyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(song.id)
        
        lyricsCache.get(cacheKey)?.let { 
            LogUtils.d(this@LyricsRepositoryImpl, "Cache hit for song: ${song.title}")
            return@withContext it 
        }

        LogUtils.d(this@LyricsRepositoryImpl, "Cache miss for song: ${song.title}, loading from storage")
        val lyrics = loadLyricsFromStorage(song)
        lyrics?.let { 
            lyricsCache.put(cacheKey, it)
            LogUtils.d(this@LyricsRepositoryImpl, "Cached lyrics for song: ${song.title}")
        }
        
        return@withContext lyrics
    }

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
                    
                    musicDao.updateLyrics(song.id.toLong(), rawLyricsToSave)
                    
                    val cacheKey = generateCacheKey(song.id)
                    lyricsCache.put(cacheKey, best.lyrics)
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
                
                musicDao.updateLyrics(song.id.toLong(), rawLyricsToSave)
                
                val cacheKey = generateCacheKey(song.id)
                lyricsCache.put(cacheKey, parsedLyrics)
                LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics (exact match) for: ${song.title}")
                
                Result.success(Pair(parsedLyrics, rawLyricsToSave))
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException())
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error fetching lyrics from remote")
            // If no lyrics are found lrclib returns a 404 which also raises an exception.
            // We still want to present that info nicely to the user.
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
            // This avoids rate limiting issues that can occur with parallel requests
            // Stop as soon as we get a valid result
            val strategies: List<suspend () -> Array<LrcLibResponse>?> = listOf(
                // Strategy 1: Combined query with artist (most specific)
                { runCatching { lrcLibApiService.searchLyrics(query = combinedQuery, artistName = song.displayArtist) }.getOrNull() },
                // Strategy 2: Track name with artist
                { runCatching { lrcLibApiService.searchLyrics(trackName = song.title, artistName = song.displayArtist) }.getOrNull() },
                // Strategy 3: Track name only
                { runCatching { lrcLibApiService.searchLyrics(trackName = song.title) }.getOrNull() },
                // Strategy 4: Simple query (fallback)
                { runCatching { lrcLibApiService.searchLyrics(query = song.title) }.getOrNull() }
            )
            
            var allResults: List<LrcLibResponse> = emptyList()
            for ((index, strategy) in strategies.withIndex()) {
                LogUtils.d(this@LyricsRepositoryImpl, "Trying search strategy ${index + 1}/4...")
                val result = strategy()
                if (!result.isNullOrEmpty()) {
                    LogUtils.d(this@LyricsRepositoryImpl, "Strategy ${index + 1} returned ${result.size} results")
                    allResults = result.toList()
                    break // Stop on first successful result
                }
                LogUtils.d(this@LyricsRepositoryImpl, "Strategy ${index + 1} returned no results, trying next...")
            }

            // Results are already flattened since we use sequential strategy
            val uniqueResults = allResults.distinctBy { it.id }

            if (uniqueResults.isNotEmpty()) {
                val songDurationSeconds = song.duration / 1000
                val results = uniqueResults.mapNotNull { response ->
                    // Increased duration tolerance from 5 to 15 seconds for better matching
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
                // Sort results: prioritize entries with synced lyrics
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

    override suspend fun updateLyrics(songId: Long, lyricsContent: String): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this@LyricsRepositoryImpl, "Updating lyrics for songId: $songId")
        
        val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
        if (!parsedLyrics.isValid()) {
            LogUtils.w(this@LyricsRepositoryImpl, "Attempted to save empty lyrics for songId: $songId")
            return@withContext
        }
        
        musicDao.updateLyrics(songId, lyricsContent)
        
        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.put(cacheKey, parsedLyrics)
        LogUtils.d(this@LyricsRepositoryImpl, "Updated and cached lyrics for songId: $songId")
    }

    override suspend fun resetLyrics(songId: Long): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting lyrics for songId: $songId")
        val cacheKey = generateCacheKey(songId.toString())
        lyricsCache.remove(cacheKey)
        musicDao.resetLyrics(songId)
    }

    override suspend fun resetAllLyrics(): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting all lyrics")
        lyricsCache.evictAll()
        musicDao.resetAllLyrics()
    }

    override fun clearCache() {
        LogUtils.d(this, "Clearing lyrics cache")
        lyricsCache.evictAll()
    }

    private suspend fun loadLyricsFromStorage(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        if (!song.lyrics.isNullOrBlank()) {
            val parsedLyrics = LyricsUtils.parseLyrics(song.lyrics)
            if (parsedLyrics.isValid()) {
                return@withContext parsedLyrics.copy(areFromRemote = false)
            }
        }

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
