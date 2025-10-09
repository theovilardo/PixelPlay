package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.net.toUri
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
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
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
            val response = lrcLibApiService.getLyrics(
                trackName = song.title,
                artistName = song.artist,
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
                LogUtils.d(this@LyricsRepositoryImpl, "Fetched and cached remote lyrics for: ${song.title}")
                
                Result.success(Pair(parsedLyrics, rawLyricsToSave))
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException())
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error fetching lyrics from remote")
            // If no lyrics are found lrclib returns a 404 which also raises an exception.
            // We still want to present that info nicely to the user.
            if (e is HttpException && e.code() == 404) {
                Result.failure(NoLyricsFoundException())
            } else {
                Result.failure(LyricsException("Failed to fetch lyrics from remote", e))
            }
        }
    }

    override suspend fun searchRemote(song: Song): Result<Pair<String, List<LyricsSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            val query = song.title
            LogUtils.d(this@LyricsRepositoryImpl, "Searching remote for lyrics for: $query")
            val responses = lrcLibApiService.searchLyrics(query)
            LogUtils.d(this@LyricsRepositoryImpl, "  Found ${responses?.size ?: 0} responses")

            if (responses != null && responses.isNotEmpty()) {
                val results = responses.map { response ->
                    // check duration first
                    if (abs(response.duration - song.duration / 1000) > 2) {
                        return@map null
                    }

                    val rawLyrics = response.syncedLyrics ?: response.plainLyrics!!
                    val parsedLyrics = LyricsUtils.parseLyrics(rawLyrics).copy(areFromRemote = true)
                    if (!parsedLyrics.isValid()) {
                        LogUtils.w(this@LyricsRepositoryImpl, "Parsed lyrics are empty for: ${song.title}")
                        return@map null
                    }
                    LogUtils.d(this@LyricsRepositoryImpl, "  Found: ${response.name} (${response.id})")
                    LyricsSearchResult(response, parsedLyrics, rawLyrics)
                }.filterNotNull()

                if (results.isNotEmpty()) {
                    LogUtils.d(this@LyricsRepositoryImpl, "Found ${results.size} lyrics for: ${song.title}")
                    Result.success(Pair(query, results))
                } else {
                    LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                    Result.failure(NoLyricsFoundException(query))
                }
            } else {
                LogUtils.d(this@LyricsRepositoryImpl, "No lyrics found remotely for: ${song.title}")
                Result.failure(NoLyricsFoundException(query))
            }
        } catch (e: Exception) {
            LogUtils.e(this@LyricsRepositoryImpl, e, "Error searching remote for lyrics")
            Result.failure(LyricsException(context.getString(R.string.failed_to_search_for_lyrics), e))
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
                val audioFile = AudioFileIO.read(tempFile)
                val tag = audioFile.tag
                val lyricsField = tag?.getFirst(FieldKey.LYRICS)
                
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
