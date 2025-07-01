package com.theveloper.pixelplay.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.datasource.GenreDataSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicDao: MusicDao
) : CoroutineWorker(appContext, workerParams) {

    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting MediaStore synchronization...")
            val startTime = System.currentTimeMillis()

            val songs = fetchAllMusicData()
            Log.i(TAG, "Fetched ${songs.size} songs from MediaStore.")

            if (songs.isNotEmpty()) {
                val albums = songs.distinctBy { it.albumId }.map {
                    AlbumEntity(
                        id = it.albumId,
                        title = it.albumName,
                        artistName = it.artistName,
                        artistId = it.artistId,
                        albumArtUriString = it.albumArtUriString,
                        songCount = songs.count { s -> s.albumId == it.albumId }
                    )
                }
                val artists = songs.distinctBy { it.artistId }.map {
                    ArtistEntity(
                        id = it.artistId,
                        name = it.artistName,
                        trackCount = songs.count { s -> s.artistId == it.artistId }
                    )
                }

                Log.i(TAG, "Processed ${songs.size} songs, ${albums.size} albums, ${artists.size} artists.")
                musicDao.insertArtists(artists)
                musicDao.insertAlbums(albums)
                musicDao.insertSongs(songs)

                // Delete songs that are no longer present in MediaStore
                val currentSongIds = songs.map { it.id }
                musicDao.deleteMissingSongs(currentSongIds)
                musicDao.deleteOrphanedAlbums()
                musicDao.deleteOrphanedArtists()
                Log.i(TAG, "Music data insertion call completed.")
            } else {
                Log.w(TAG, "MediaStore fetch resulted in empty list for songs. No data will be inserted.")
            }

            val endTime = System.currentTimeMillis()
            Log.i(TAG, "MediaStore synchronization completed successfully in ${endTime - startTime}ms.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaStore synchronization", e)
            Result.failure()
        }
    }

    private fun fetchAllMusicData(): List<SongEntity> {
        val songs = mutableListOf<SongEntity>()
        // Removed genre mapping from initial sync for performance.
        // Genre will be "Unknown Genre" or from static genres for now.

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("10000")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val songArtistId = cursor.getLong(artistIdCol)
                val filePath = cursor.getString(dataCol) ?: ""
                val parentDir = java.io.File(filePath).parent ?: ""

                val contentUriString = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()
                val albumArtUriString = ContentUris.withAppendedId(
                    "content://media/external/audio/albumart".toUri(), albumId
                )?.toString()

                val genreName = run {
                    val staticGenres = GenreDataSource.staticGenres
                    if (staticGenres.isNotEmpty()) {
                        staticGenres[(id % staticGenres.size).toInt()].name
                    } else {
                        "Unknown Genre"
                    }
                }

                songs.add(
                    SongEntity(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown Title",
                        artistName = cursor.getString(artistCol) ?: "Unknown Artist",
                        artistId = songArtistId,
                        albumName = cursor.getString(albumCol) ?: "Unknown Album",
                        albumId = albumId,
                        contentUriString = contentUriString,
                        albumArtUriString = albumArtUriString,
                        duration = cursor.getLong(durationCol),
                        genre = genreName,
                        filePath = filePath,
                        parentDirectoryPath = parentDir
                    )
                )
            }
        }
        return songs
    }

        

    companion object {
        const val WORK_NAME = "com.theveloper.pixelplay.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"

        fun startUpSyncWork() = OneTimeWorkRequestBuilder<SyncWorker>().build()
    }
}