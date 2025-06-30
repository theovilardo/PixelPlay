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
import com.theveloper.pixelplay.data.datasource.GenreDataSource // Using the same static genre source
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

            var songs = fetchAllSongsFromMediaStore() // mutable list now
            val genreMappings = fetchGenreMappings()

            Log.i(TAG, "Fetched ${songs.size} songs initially. Fetched ${genreMappings.size} genre mappings.")

            // Populate genres for songs
            songs = songs.map { song ->
                val genreName = genreMappings[song.id]
                if (genreName != null) {
                    song.copy(genre = genreName)
                } else {
                    val staticGenres = GenreDataSource.staticGenres
                    if (staticGenres.isNotEmpty()) {
                        val genreIndex = (song.id % staticGenres.size.toLong()).toInt()
                        song.copy(genre = staticGenres[genreIndex].name)
                    } else {
                        song.copy(genre = "Unknown Genre")
                    }
                }
            }.toMutableList() // ensure it's a List for subsequent functions

            val albums = fetchAllAlbumsFromMediaStore(songs)
            val artists = fetchAllArtistsFromMediaStore(songs)

            Log.i(TAG, "Processed ${songs.size} songs with genres, ${albums.size} albums, ${artists.size} artists from MediaStore.")

            if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                Log.w(TAG, "MediaStore fetch resulted in empty lists for songs, albums, and artists. No data will be inserted.")
            } else {
                Log.i(TAG, "Attempting to insert music data into DAO. Songs: ${songs.size}, Albums: ${albums.size}, Artists: ${artists.size}")
                musicDao.insertMusicData(songs, albums, artists)
                Log.i(TAG, "Music data insertion call completed.")
            }

            val endTime = System.currentTimeMillis()
            Log.i(TAG, "MediaStore synchronization completed successfully in ${endTime - startTime}ms.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaStore synchronization", e)
            Result.failure()
        }
    }

    private fun fetchGenreMappings(): Map<Long, String> {
        val genreMap = mutableMapOf<Long, String>()
        val genreProjection = arrayOf(
            MediaStore.Audio.Genres._ID,
            MediaStore.Audio.Genres.NAME
        )

        // 1. Get all genres
        contentResolver.query(
            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
            genreProjection,
            null,
            null,
            null
        )?.use { genreCursor ->
            val genreIdCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val genreNameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)

            while (genreCursor.moveToNext()) {
                val genreId = genreCursor.getLong(genreIdCol)
                val genreName = genreCursor.getString(genreNameCol) ?: "Unknown Genre"

                // 2. For each genre, get its member songs
                val memberUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                val membersProjection = arrayOf(MediaStore.Audio.Media._ID) // Or MediaStore.Audio.Genres.Members.AUDIO_ID

                contentResolver.query(memberUri, membersProjection, null, null, null)
                    ?.use { membersCursor ->
                        val audioIdCol = membersCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        while (membersCursor.moveToNext()) {
                            val audioId = membersCursor.getLong(audioIdCol)
                            // A song might belong to multiple genres.
                            // We are taking the first one found, or you could decide on a strategy (e.g., concatenate).
                            // For simplicity, MediaStore often links a song to one primary genre via this table.
                            if (!genreMap.containsKey(audioId)) {
                                genreMap[audioId] = genreName
                            }
                        }
                    }
            }
        }
        Log.i(TAG, "Built genre map with ${genreMap.size} entries.")
        return genreMap
    }

    private fun fetchAllSongsFromMediaStore(): MutableList<SongEntity> { // Return MutableList
        val songs = mutableListOf<SongEntity>()
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

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val songArtistId = c.getLong(artistIdCol)
                val filePath = c.getString(dataCol) ?: ""
                val parentDir = java.io.File(filePath).parent ?: ""

                val contentUriString = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()
                val albumArtUriString = ContentUris.withAppendedId(
                    "content://media/external/audio/albumart".toUri(), albumId
                )?.toString()

                // Genre will be populated in a later step
                songs.add(
                    SongEntity(
                        id = id,
                        title = c.getString(titleCol) ?: "Unknown Title",
                        artistName = c.getString(artistCol) ?: "Unknown Artist",
                        artistId = songArtistId,
                        albumName = c.getString(albumCol) ?: "Unknown Album",
                        albumId = albumId,
                        contentUriString = contentUriString,
                        albumArtUriString = albumArtUriString,
                        duration = c.getLong(durationCol),
                        genre = null, // Initially null, to be populated later
                        filePath = filePath,
                        parentDirectoryPath = parentDir
                    )
                )
            }
        }
        return songs
    }

    private fun fetchAllAlbumsFromMediaStore(songEntities: List<SongEntity>): List<AlbumEntity> {
        val albums = mutableMapOf<Long, AlbumEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST
        )
        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"
        val songCountByAlbumId = songEntities.groupBy { it.albumId }.mapValues { it.value.size }
        val artistIdByAlbumId = songEntities.associate { Pair(it.albumId, it.artistId) }

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val actualSongCount = songCountByAlbumId[id] ?: 0

                if (actualSongCount > 0) {
                    val albumArtUriString = ContentUris.withAppendedId(
                        "content://media/external/audio/albumart".toUri(), id
                    )?.toString()
                    val representativeArtistId = artistIdByAlbumId[id] ?: 0L
                    albums[id] = AlbumEntity(
                        id = id,
                        title = c.getString(titleCol) ?: "Unknown Album",
                        artistName = c.getString(artistCol) ?: "Unknown Artist",
                        artistId = representativeArtistId,
                        albumArtUriString = albumArtUriString,
                        songCount = actualSongCount
                    )
                }
            }
        }
        return albums.values.toList()
    }

    private fun fetchAllArtistsFromMediaStore(songEntities: List<SongEntity>): List<ArtistEntity> {
        val artists = mutableMapOf<Long, ArtistEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST
        )
        val sortOrder = "${MediaStore.Audio.Artists.ARTIST} ASC"
        val trackCountByArtistId = songEntities.groupBy { it.artistId }.mapValues { it.value.size }

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val actualTrackCount = trackCountByArtistId[id] ?: 0
                if (actualTrackCount > 0) {
                    artists[id] = ArtistEntity(
                        id = id,
                        name = c.getString(nameCol) ?: "Unknown Artist",
                        trackCount = actualTrackCount
                    )
                }
            }
        }
        return artists.values.toList()
    }

    companion object {
        const val WORK_NAME = "com.theveloper.pixelplay.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"

        // *** MÉTODO AÑADIDO PARA SOLUCIONAR EL ERROR ***
        /**
         * Creates a one-time work request for this worker.
         */
        fun startUpSyncWork() = OneTimeWorkRequestBuilder<SyncWorker>().build()
    }
}
