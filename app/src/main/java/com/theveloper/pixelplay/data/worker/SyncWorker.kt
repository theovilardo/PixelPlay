package com.theveloper.pixelplay.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import android.os.Trace // Import Trace
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.AudioMetaUtils.getAudioMetadata
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicDao: MusicDao
) : CoroutineWorker(appContext, workerParams) {

    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Trace.beginSection("SyncWorker.doWork")
        try {
            Log.i(TAG, "Starting MediaStore synchronization...")
            val startTime = System.currentTimeMillis()

            val mediaStoreSongs = fetchAllMusicData { current, total ->
                setProgress(workDataOf(
                    PROGRESS_CURRENT to current,
                    PROGRESS_TOTAL to total
                ))
            }
            Log.i(TAG, "Fetched ${mediaStoreSongs.size} songs from MediaStore.")

            if (mediaStoreSongs.isNotEmpty()) {
                // Fetch existing local songs to preserve their editable metadata
                val localSongsMap = musicDao.getAllSongsList().associateBy { it.id }

                // Prepare the final list of songs for insertion
                val songsToInsert = mediaStoreSongs.map { mediaStoreSong ->
                    val localSong = localSongsMap[mediaStoreSong.id]
                    if (localSong != null) {
                        // This song exists locally - preserve user-edited fields if they differ from MediaStore
                        // We check if local values are different from what MediaStore would provide,
                        // which suggests user editing. We preserve dateAdded, lyrics, and all editable metadata.
                        mediaStoreSong.copy(
                            dateAdded = localSong.dateAdded,
                            lyrics = localSong.lyrics,
                            // Preserve user-edited metadata if local is different from MediaStore default
                            title = if (localSong.title != mediaStoreSong.title && localSong.title.isNotBlank()) localSong.title else mediaStoreSong.title,
                            artistName = if (localSong.artistName != mediaStoreSong.artistName && localSong.artistName.isNotBlank()) localSong.artistName else mediaStoreSong.artistName,
                            albumName = if (localSong.albumName != mediaStoreSong.albumName && localSong.albumName.isNotBlank()) localSong.albumName else mediaStoreSong.albumName,
                            genre = localSong.genre ?: mediaStoreSong.genre,
                            trackNumber = if (localSong.trackNumber != 0 && localSong.trackNumber != mediaStoreSong.trackNumber) localSong.trackNumber else mediaStoreSong.trackNumber,
                            albumArtUriString = localSong.albumArtUriString ?: mediaStoreSong.albumArtUriString
                        )
                    } else {
                        // This is a new song. Keep the MediaStore provided data.
                        mediaStoreSong
                    }
                }

                val (correctedSongs, albums, artists) = preProcessAndDeduplicate(songsToInsert)

                // Perform the "clear and insert" operation
                musicDao.clearAllMusicData()
                musicDao.insertMusicData(correctedSongs, albums, artists)

                Log.i(TAG, "Music data synchronization completed. ${correctedSongs.size} songs processed.")
                
                val endTime = System.currentTimeMillis()
                Log.i(TAG, "MediaStore synchronization finished successfully in ${endTime - startTime}ms.")
                Result.success(workDataOf(OUTPUT_TOTAL_SONGS to correctedSongs.size))
            } else {
                // MediaStore is empty, so clear the local database
                musicDao.clearAllMusicData()
                Log.w(TAG, "MediaStore fetch resulted in empty list. Local music data cleared.")
                
                val endTime = System.currentTimeMillis()
                Log.i(TAG, "MediaStore synchronization finished successfully in ${endTime - startTime}ms.")
                Result.success(workDataOf(OUTPUT_TOTAL_SONGS to 0))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaStore synchronization", e)
            Result.failure()
        } finally {
            Trace.endSection() // End SyncWorker.doWork
        }
    }

    private fun preProcessAndDeduplicate(songs: List<SongEntity>): Triple<List<SongEntity>, List<AlbumEntity>, List<ArtistEntity>> {
        // Artist de-duplication
        val artistMap = mutableMapOf<String, Long>()
        songs.forEach { song ->
            if (!artistMap.containsKey(song.artistName)) {
                artistMap[song.artistName] = song.artistId
            }
        }

        // Album de-duplication
        val albumMap = mutableMapOf<Pair<String, String>, Long>()
        songs.forEach { song ->
            val key = Pair(song.albumName, song.artistName)
            if (!albumMap.containsKey(key)) {
                albumMap[key] = song.albumId
            }
        }

        val correctedSongs = songs.map { song ->
            val canonicalArtistId = artistMap[song.artistName]!!
            val canonicalAlbumId = albumMap[Pair(song.albumName, song.artistName)]!!
            song.copy(artistId = canonicalArtistId, albumId = canonicalAlbumId)
        }

        // Create unique albums
        val albums = correctedSongs.groupBy { it.albumId }.map { (albumId, songsInAlbum) ->
            val firstSong = songsInAlbum.first()
            AlbumEntity(
                id = albumId,
                title = firstSong.albumName,
                artistName = firstSong.artistName,
                artistId = firstSong.artistId,
                albumArtUriString = firstSong.albumArtUriString,
                songCount = songsInAlbum.size,
                year = firstSong.year
            )
        }

        // Create unique artists
        val artists = correctedSongs.groupBy { it.artistId }.map { (artistId, songsByArtist) ->
            val firstSong = songsByArtist.first()
            ArtistEntity(
                id = artistId,
                name = firstSong.artistName,
                trackCount = songsByArtist.size
            )
        }

        return Triple(correctedSongs, albums, artists)
    }

    private fun fetchAlbumArtUrisByAlbumId(): Map<Long, String> {
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM_ART
        )

        return buildMap {
            contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val artCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)

                while (cursor.moveToNext()) {
                    val albumId = cursor.getLong(idCol)
                    val storedArtPath = cursor.getString(artCol)
                    val uriString = when {
                        !storedArtPath.isNullOrBlank() -> File(storedArtPath).toURI().toString()
                        albumId > 0 -> ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(),
                            albumId
                        ).toString()
                        else -> null
                    }

                    if (uriString != null) put(albumId, uriString)
                }
            }
        }
    }

    private suspend fun fetchAllMusicData(onProgress: suspend (current: Int, total: Int) -> Unit): List<SongEntity> {
        Trace.beginSection("SyncWorker.fetchAllMusicData")
        val songs = mutableListOf<SongEntity>()
        // Removed genre mapping from initial sync for performance.
        // Genre will be "Unknown Genre" or from static genres for now.

        val deepScan = inputData.getBoolean(INPUT_FORCE_METADATA, false)
        val albumArtByAlbumId = if (!deepScan) fetchAlbumArtUrisByAlbumId() else emptyMap()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "((${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?) OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
        val selectionArgs = arrayOf("10000")
        val sortOrder = null // Avoid extra sorting work; we'll sort downstream if needed

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val totalCount = cursor.count
            var processedCount = 0
            
            // Report initial progress (0 of total)
            onProgress(0, totalCount)
            
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)


            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val songArtistId = cursor.getLong(artistIdCol)
                val filePath = cursor.getString(dataCol) ?: ""
                val parentDir = java.io.File(filePath).parent ?: ""

                val contentUriString = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()

                var albumArtUriString = albumArtByAlbumId[albumId]
                if (deepScan) {
                    albumArtUriString = AlbumArtUtils.getAlbumArtUri(applicationContext, musicDao, filePath, albumId, id, true)
                        ?: albumArtUriString
                }
                val audioMetadata = if (deepScan) getAudioMetadata(musicDao, id, filePath, true) else null

                var title = cursor.getString(titleCol).normalizeMetadataTextOrEmpty().ifEmpty { "Unknown Title" }
                var artist = cursor.getString(artistCol).normalizeMetadataTextOrEmpty().ifEmpty { "Unknown Artist" }
                var album = cursor.getString(albumCol).normalizeMetadataTextOrEmpty().ifEmpty { "Unknown Album" }
                var trackNumber = cursor.getInt(trackCol)
                var year = cursor.getInt(yearCol)


                // Fix for WAV files (Issue #462): Read metadata directly from file if it is a WAV
                if (deepScan && filePath.endsWith(".wav", ignoreCase = true)) {
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        try {
                            AudioMetadataReader.read(file)?.let { meta ->
                                if (!meta.title.isNullOrBlank()) title = meta.title
                                if (!meta.artist.isNullOrBlank()) artist = meta.artist
                                if (!meta.album.isNullOrBlank()) album = meta.album
                                if (meta.trackNumber != null) trackNumber = meta.trackNumber
                                if (meta.year != null) year = meta.year

                                meta.artwork?.let { art ->
                                    val uri = AlbumArtUtils.saveAlbumArtToCache(applicationContext, art.bytes, id)
                                    albumArtUriString = uri.toString()
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to read WAV metadata for $filePath", e)
                        }
                    }
                }

                songs.add(
                    SongEntity(
                        id = id,
                        title = title,
                        artistName = artist,
                        artistId = songArtistId,
                        albumName = album,
                        albumId = albumId,
                        contentUriString = contentUriString,
                        albumArtUriString = albumArtUriString,
                        duration = cursor.getLong(durationCol),
                        genre = null,
                        filePath = filePath,
                        parentDirectoryPath = parentDir,
                        trackNumber = trackNumber,
                        year = year,
                        dateAdded = cursor.getLong(dateAddedCol).let { seconds ->
                            if (seconds > 0) TimeUnit.SECONDS.toMillis(seconds) else System.currentTimeMillis()
                        },
                        mimeType = audioMetadata?.mimeType,
                        sampleRate = audioMetadata?.sampleRate,
                        bitrate = audioMetadata?.bitrate
                    )
                )
                
                // Report progress after each song
                processedCount++
                onProgress(processedCount, totalCount)
            }
        }
        Trace.endSection() // End SyncWorker.fetchAllMusicData
        return songs
    }


    companion object {
        const val WORK_NAME = "com.theveloper.pixelplay.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"
        const val INPUT_FORCE_METADATA = "input_force_metadata" // new key
        
        // Progress reporting constants
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val OUTPUT_TOTAL_SONGS = "output_total_songs"

        fun startUpSyncWork(deepScan: Boolean = false) = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(INPUT_FORCE_METADATA to deepScan))
            .build()
    }
}