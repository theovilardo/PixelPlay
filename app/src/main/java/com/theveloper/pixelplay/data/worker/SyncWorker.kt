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

    companion object {
        const val WORK_NAME = "com.theveloper.pixelplay.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"
    }

    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting MediaStore synchronization...")
            val startTime = System.currentTimeMillis()

            val songs = fetchAllSongsFromMediaStore()
            val albums = fetchAllAlbumsFromMediaStore(songs) // Pass songs to derive artistId for albums if needed
            val artists = fetchAllArtistsFromMediaStore(songs) // Pass songs to accurately count tracks for artists

            Log.i(TAG, "Fetched ${songs.size} songs, ${albums.size} albums, ${artists.size} artists from MediaStore.")

            musicDao.insertMusicData(songs, albums, artists)

            val endTime = System.currentTimeMillis()
            Log.i(TAG, "MediaStore synchronization completed successfully in ${endTime - startTime}ms.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaStore synchronization", e)
            Result.failure()
        }
    }

    private fun fetchAllSongsFromMediaStore(): List<SongEntity> {
        val songs = mutableListOf<SongEntity>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA // Crucial for filePath
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("10000") // Songs at least 10 seconds long
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
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA) // Now used for filePath

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val songArtistId = c.getLong(artistIdCol)

                val contentUriString = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()
                val albumArtUriString = ContentUris.withAppendedId(
                    "content://media/external/audio/albumart".toUri(), albumId
                )?.toString()

                // Genre fetching logic (similar to MusicRepositoryImpl)
                var genreName: String? = null
                try {
                    val genreUri = MediaStore.Audio.Genres.getContentUriForAudioId("external", id.toInt())
                    val genreProjection = arrayOf(MediaStore.Audio.GenresColumns.NAME)
                    contentResolver.query(genreUri, genreProjection, null, null, null)?.use { genreCursor ->
                        if (genreCursor.moveToFirst()) {
                            val genreNameColumn = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.GenresColumns.NAME)
                            genreName = genreCursor.getString(genreNameColumn)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching genre for song ID: $id", e)
                }
                 if (genreName.isNullOrEmpty()) {
                    val staticGenres = GenreDataSource.staticGenres
                    if (staticGenres.isNotEmpty()) {
                        val genreIndex = (id % staticGenres.size.toLong()).toInt()
                        genreName = staticGenres[genreIndex].name
                    } else {
                        genreName = "Unknown Genre"
                    }
                }

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
                        genre = genreName,
                        filePath = c.getString(dataCol) ?: ""
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
            MediaStore.Audio.Albums.ARTIST // This is album artist
            // MediaStore.Audio.Albums.NUMBER_OF_SONGS - we will count this from our songs
        )
        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"

        // Create a map of albumId to actual song count from our filtered songs
        val songCountByAlbumId = songEntities.groupBy { it.albumId }.mapValues { it.value.size }
        // Create a map of albumId to the first artistId found for that album (heuristic)
        val artistIdByAlbumId = songEntities.associate { Pair(it.albumId, it.artistId) }


        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null, // No selection, get all albums
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

                if (actualSongCount > 0) { // Only include albums that have songs in our song list
                    val albumArtUriString = ContentUris.withAppendedId(
                        "content://media/external/audio/albumart".toUri(), id
                    )?.toString()

                    // Use the artistId from one of the songs in this album.
                    // If no songs for this album were found in songEntities (actualSongCount is 0),
                    // this album wouldn't be added anyway.
                    // Fallback to a default if somehow it's not found (should not happen if actualSongCount > 0)
                    val representativeArtistId = artistIdByAlbumId[id] ?: 0L


                    albums[id] = AlbumEntity(
                        id = id,
                        title = c.getString(titleCol) ?: "Unknown Album",
                        artistName = c.getString(artistCol) ?: "Unknown Artist",
                        artistId = representativeArtistId, // Store the representative artist ID
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
            // MediaStore.Audio.Artists.NUMBER_OF_TRACKS - we will count this from our songs
        )
        val sortOrder = "${MediaStore.Audio.Artists.ARTIST} ASC"

        // Create a map of artistId to actual track count from our filtered songs
        val trackCountByArtistId = songEntities.groupBy { it.artistId }.mapValues { it.value.size }

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            null, // No selection, get all artists
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val actualTrackCount = trackCountByArtistId[id] ?: 0

                if (actualTrackCount > 0) { // Only include artists that have songs in our song list
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
}
