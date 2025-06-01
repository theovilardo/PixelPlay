package com.theveloper.pixelplay.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SearchHistoryEntity
import com.theveloper.pixelplay.data.database.toSearchHistoryItem
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.datasource.GenreDataSource // To know the list of genres for placeholder logic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao
) : MusicRepository {

    // Proyección común para canciones, AHORA INCLUYE ARTIST_ID
    private val songProjection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ARTIST_ID, // <-- INCLUIDO
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA
    )

    // Mutex para el escaneo inicial de directorios (getAllUniqueAudioDirectories)
    private val directoryScanMutex = Mutex()
    private var cachedAudioDirectories: Set<String>? = null

    // --- Para getPermittedSongReferences ---
    data class MinimalSongInfo(val songId: String, val albumId: Long, val artistId: Long)
    private var cachedPermittedSongReferences: List<MinimalSongInfo>? = null
    // ¡¡¡CAMBIO AQUÍ!!! Declara estas propiedades como miembros de la clase
    private var cachedPermittedAlbumSongCounts: Map<Long, Int>? = null
    private var cachedPermittedArtistSongCounts: Map<Long, Int>? = null
    private val permittedSongReferencesMutex = Mutex() // Mutex dedicado

    /**
     * Consulta MediaStore para obtener canciones y aplica el filtrado por directorio.
     * **Esta función NO usa paginación en la consulta de MediaStore.**
     * @param selection La cláusula WHERE.
     * @param selectionArgs Los argumentos para la cláusula WHERE.
     * @param sortOrder La cláusula ORDER BY (sin LIMIT/OFFSET).
     * @return Lista de objetos Song, filtrada por directorios permitidos.
     */
    // Debes actualizar esta función para usar la 'songProjection' (que ahora tiene ARTIST_ID)
    // y para poblar el nuevo campo 'artistId' en tu objeto 'Song'.
    private suspend fun queryAndFilterSongs(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        val allowedDirectories = userPreferencesRepository.allowedDirectoriesFlow.first()

        Log.d("MusicRepo/QueryFilter", "Querying with selection: $selection, initialSetupDone: $initialSetupDone, allowedDirs: ${allowedDirectories.size}")

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songProjection, // Esta proyección ahora DEBE incluir ARTIST_ID
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID) // Leer el índice
            val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val filePath = c.getString(dataColumn)
                val directoryPath = File(filePath).parent ?: ""

                val isAllowed = if (!initialSetupDone) {
                    true
                } else {
                    if (allowedDirectories.isEmpty()) false else allowedDirectories.contains(directoryPath)
                }

                if (isAllowed) {
                    val id = c.getLong(idColumn)
                    val title = c.getString(titleColumn)
                    val artistName = c.getString(artistColumn) ?: "Artista Desconocido"
                    val songArtistId = c.getLong(artistIdColumn) // Obtener el ID del artista
                    val albumName = c.getString(albumColumn) ?: "Álbum Desconocido"
                    val albumId = c.getLong(albumIdColumn)
                    val duration = c.getLong(durationColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val albumArtUri: Uri? = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )
                    songs.add(
                        Song(
                            id = id.toString(),
                            title = title,
                            artist = artistName,
                            artistId = songArtistId, // Pasar el artistId al constructor de Song
                            album = albumName,
                            albumId = albumId,
                            contentUriString = contentUri.toString(),
                            albumArtUriString = albumArtUri?.toString(),
                            duration = duration,
                            genre = genreName // Add genre here as well
                        )
                    )
                }
            }
        }
        Log.d("MusicRepo/QueryFilter", "Query '$selection' returned ${songs.size} songs after directory filter.")
        return@withContext songs
    }

    // Implementación de getAudioFiles con lógica de "Llenado de Página"
    override suspend fun getAudioFiles(page: Int, pageSize: Int): List<Song> = withContext(Dispatchers.IO) {
        val getAudioFilesStartTime = System.currentTimeMillis()
        Log.d("MusicRepo/Songs", "getAudioFiles (filling_page_logic) - ViewModel Page: $page, PageSize: $pageSize")

        val songsToReturn = mutableListOf<Song>()
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        val allowedDirectories = userPreferencesRepository.allowedDirectoriesFlow.first()

        Log.d("MusicRepo/Songs", "InitialSetupDone: $initialSetupDone, AllowedDirs Count: ${allowedDirectories.size}")
        if (allowedDirectories.size < 10 && initialSetupDone) { // Loguear directorios si son pocos y el setup está hecho
            Log.d("MusicRepo/Songs", "AllowedDirs List: $allowedDirectories")
        }

        // Si el setup está hecho y no hay directorios permitidos, no tiene sentido buscar.
        if (initialSetupDone && allowedDirectories.isEmpty()) {
            Log.w("MusicRepo/Songs", "Setup is done but no directories are allowed. Returning empty list.")
            return@withContext emptyList()
        }

        var currentMediaStoreOffset = (page - 1) * pageSize
        // Pedir un poco más a MediaStore para tener margen, especialmente si muchos se filtran.
        // Si pageSize es 30, pedir 60 o 90 a MediaStore puede ser un buen comienzo.
        val internalMediaStorePageSize = pageSize * 3
        val maxMediaStoreQueries = 10 // Límite de seguridad para evitar bucles muy largos.

        for (queryAttempt in 0 until maxMediaStoreQueries) {
            Log.d("MusicRepo/Songs", "Fill Attempt #${queryAttempt + 1}. Songs collected so far: ${songsToReturn.size}. Querying MediaStore with offset: $currentMediaStoreOffset, limit: $internalMediaStorePageSize")

            val selectionClause = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
            val selectionArgsArray = arrayOf("30000")
            val baseSortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
            val cursor: Cursor?

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionClause)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgsArray)
                    putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Media.TITLE)
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
                    putInt(ContentResolver.QUERY_ARG_LIMIT, internalMediaStorePageSize)
                    putInt(ContentResolver.QUERY_ARG_OFFSET, currentMediaStoreOffset)
                }
                cursor = context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songProjection, queryArgs, null)
            } else {
                val sortOrderWithPaging = "$baseSortOrder LIMIT $internalMediaStorePageSize OFFSET $currentMediaStoreOffset"
                cursor = context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songProjection, selectionClause, selectionArgsArray, sortOrderWithPaging)
            }

            Log.d("MusicRepo/Songs", "Attempt #${queryAttempt + 1}: MediaStore cursor count (raw for this internal query): ${cursor?.count}")

            var newSongsFoundInThisMediaStoreQuery = 0
            var permittedSongsAddedInThisAttempt = 0

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
                    newSongsFoundInThisMediaStoreQuery++
                    if (songsToReturn.size >= pageSize) break // Ya hemos llenado la página deseada

                    val filePath = c.getString(dataCol)
                    val directoryPath = File(filePath).parent ?: ""

                    val isAllowed = if (!initialSetupDone) true else allowedDirectories.contains(directoryPath)

                    if (isAllowed) {
                        val id = c.getLong(idCol)
                        val title = c.getString(titleCol)
                                val songId = id // Already have song ID as 'id'
                                var genreName: String? = null

                                // Query for genre
                                val genreUri = MediaStore.Audio.Genres.getContentUriForAudioId("external", songId.toInt())
                                val genreProjection = arrayOf(MediaStore.Audio.GenresColumns.NAME)
                                try {
                                    context.contentResolver.query(genreUri, genreProjection, null, null, null)?.use { genreCursor ->
                                        if (genreCursor.moveToFirst()) {
                                            val genreNameColumn = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.GenresColumns.NAME)
                                            genreName = genreCursor.getString(genreNameColumn)
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MusicRepositoryImpl", "Error fetching genre for song ID: $songId", e)
                                }

                        songsToReturn.add(
                            Song(
                                id = id.toString(),
                                title = title,
                                artist = c.getString(artistCol) ?: "Artista Desconocido",
                                artistId = c.getLong(artistIdCol),
                                album = c.getString(albumCol) ?: "Álbum Desconocido",
                                albumId = c.getLong(albumIdCol),
                                contentUriString = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                                albumArtUriString = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), c.getLong(albumIdCol))?.toString(),
                                        duration = c.getLong(durationCol),
                                        genre = genreName // Populate the genre
                            )
                        )
                        permittedSongsAddedInThisAttempt++
                    }
                }
            }
            Log.d("MusicRepo/Songs", "Attempt #${queryAttempt + 1}: MediaStore items processed: $newSongsFoundInThisMediaStoreQuery. Permitted songs added in this attempt: $permittedSongsAddedInThisAttempt.")

            if (songsToReturn.size >= pageSize || cursor == null || newSongsFoundInThisMediaStoreQuery < internalMediaStorePageSize) {
                // Salir si:
                // 1. Hemos llenado la página para el ViewModel.
                // 2. El cursor es null (error).
                // 3. MediaStore devolvió menos items de los que pedimos en esta consulta interna (indica que MediaStore se está quedando sin items que coincidan con la selección base).
                Log.d("MusicRepo/Songs", "Exiting fill loop. Songs to return: ${songsToReturn.size}. Reason: pageFilled=${songsToReturn.size >= pageSize}, cursorNull=${cursor == null}, mediaStoreExhausted=${newSongsFoundInThisMediaStoreQuery < internalMediaStorePageSize}")
                break
            }
            currentMediaStoreOffset += internalMediaStorePageSize
        }
        Log.i("MusicRepo/Songs", "getAudioFiles (filling_page_logic) - FINAL Returning ${songsToReturn.size} songs for ViewModel Page: $page.")
        if (page == 1) Log.i("MusicRepo/Songs", "getAudioFiles (page 1) took ${System.currentTimeMillis() - getAudioFilesStartTime} ms to return ${songsToReturn.size} songs.")
        return@withContext songsToReturn
    }

    // IMPORTANT: Also update queryAndFilterSongs if it's used to populate song lists displayed in the UI
    // where genre might be needed. For now, focusing on getAudioFiles as per subtask emphasis.

    override suspend fun getAlbums(page: Int, pageSize: Int): List<Album> = withContext(Dispatchers.IO) {
        val getAlbumsStartTime = System.currentTimeMillis()
        Log.d("MusicRepo/Albums", "getAlbums - Page: $page, PageSize: $pageSize")
        val offset = (page - 1) * pageSize
        if (offset < 0) throw IllegalArgumentException("Page number must be 1 or greater")

        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        val albumsToReturn = mutableListOf<Album>()

        val projection = arrayOf(
            MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )
        val baseSortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"

        var selectionClause: String? = null
        var selectionArgsArray: Array<String>? = null
        var permittedAlbumIds: Set<Long>? = null

        if (initialSetupDone) {
            val songRefs = getPermittedSongReferences() // Obtiene referencias cacheadas/nuevas
            if (songRefs.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                Log.w("MusicRepo/Albums", "Initial setup done, allowed directories exist, but no permitted song references found. No albums will be loaded based on song refs.")
                return@withContext emptyList() // No hay canciones permitidas, por lo tanto no hay álbumes que mostrar basados en ellas
            }
            permittedAlbumIds = songRefs.map { it.albumId }.distinct().toSet()
            Log.d("MusicRepo/Albums", "Permitted Album IDs count: ${permittedAlbumIds.size}")

            if (permittedAlbumIds.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                // Si hay directorios permitidos pero ningún álbum coincide con canciones en ellos.
                return@withContext emptyList()
            }
            // Solo aplicar el filtro IN si hay IDs permitidos, de lo contrario, la cláusula IN vacía podría dar error.
            if (permittedAlbumIds.isNotEmpty()) {
                selectionClause = "${MediaStore.Audio.Albums._ID} IN (${Array(permittedAlbumIds.size) { "?" }.joinToString(",")})"
                selectionArgsArray = permittedAlbumIds.map { it.toString() }.toTypedArray()
            } else if (userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                // Hay directorios permitidos, pero ningún albumId de canciones permitidas. Devolver vacío.
                return@withContext emptyList()
            }
            // Si initialSetupDone es true Y allowedDirectories está vacío, permittedAlbumIds también estará vacío.
            // En este caso, no se aplica selectionClause y MediaStore devolverá todos los álbumes,
            // lo cual es incorrecto si queremos filtrar estrictamente.
            // Necesitamos asegurar que si allowedDirectories está vacío (y setup hecho), no se devuelvan álbumes.
            if (userPreferencesRepository.allowedDirectoriesFlow.first().isEmpty()) {
                Log.w("MusicRepo/Albums", "Initial setup done, but no allowed directories. Returning empty list of albums.")
                return@withContext emptyList()
            }
        }
        // Si initialSetupDone es false, selectionClause y selectionArgsArray permanecen null (se muestran todos los álbumes para el setup).

        Log.d("MusicRepo/Albums", "Querying MediaStore Albums with selection: '$selectionClause', args: '${selectionArgsArray?.joinToString()}', offset: $offset, limit: $pageSize")

        val cursor: Cursor?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                if (selectionClause != null) {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionClause)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgsArray)
                }
                putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Albums.ALBUM)
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            cursor = context.contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, projection, queryArgs, null)
        } else {
            val sortOrderWithPaging = "$baseSortOrder LIMIT $pageSize OFFSET $offset"
            cursor = context.contentResolver.query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, projection, selectionClause, selectionArgsArray, sortOrderWithPaging)
        }

        Log.d("MusicRepo/Albums", "MediaStore cursor count for albums: ${cursor?.count}")

        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val songCountCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "Álbum Desconocido"
                val artist = c.getString(artistCol) ?: "Varios Artistas"
                var actualSongCount = c.getInt(songCountCol) // songCount from MediaStore

                if (initialSetupDone) {
                    // If setup is done, the album MUST be in permittedAlbumIds to be considered.
                    // And its count is from our cache.
                    if (permittedAlbumIds?.contains(id) == true) {
                        actualSongCount = cachedPermittedAlbumSongCounts?.get(id) ?: 0
                    } else {
                        // This case should ideally not be hit if permittedAlbumIds filter is applied correctly at query time.
                        // If it is hit, it means an album was returned that isn't in the permitted set.
                        actualSongCount = 0
                    }
                }

                if (actualSongCount > 0) {
                    val albumArtUriVal: Uri? = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), id)
                    albumsToReturn.add(Album(id, title, artist, albumArtUriVal?.toString(), actualSongCount))
                }
            }
        }
        Log.i("MusicRepo/Albums", "Returning ${albumsToReturn.size} albums for Page: $page.")
        if (page == 1) Log.i("MusicRepo/Albums", "getAlbums (page 1) took ${System.currentTimeMillis() - getAlbumsStartTime} ms to return ${albumsToReturn.size} albums.")
        return@withContext albumsToReturn
    }

    override suspend fun getArtists(page: Int, pageSize: Int): List<Artist> = withContext(Dispatchers.IO) {
        val getArtistsStartTime = System.currentTimeMillis()
        Log.d("MusicRepo/Artists", "getArtists - Page: $page, PageSize: $pageSize")
        val offset = (page - 1) * pageSize
        if (offset < 0) throw IllegalArgumentException("Page number must be 1 or greater")

        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        val artistsToReturn = mutableListOf<Artist>()

        val projection = arrayOf(
            MediaStore.Audio.Artists._ID, MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        )
        val baseSortOrder = "${MediaStore.Audio.Artists.ARTIST} ASC"

        var selectionClause: String? = null
        var selectionArgsArray: Array<String>? = null
        var permittedArtistIds: Set<Long>? = null


        if (initialSetupDone) {
            val songRefs = getPermittedSongReferences() // Usa la caché
            if (songRefs.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                Log.w("MusicRepo/Artists", "Initial setup done, allowed directories exist, but no permitted song references found. No artists will be loaded based on song refs.")
                return@withContext emptyList()
            }
            permittedArtistIds = songRefs.map { it.artistId }.distinct().toSet()
            Log.d("MusicRepo/Artists", "Permitted Artist IDs count: ${permittedArtistIds.size}")

            if (permittedArtistIds.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                return@withContext emptyList()
            }
            if (permittedArtistIds.isNotEmpty()) {
                selectionClause = "${MediaStore.Audio.Artists._ID} IN (${Array(permittedArtistIds.size) { "?" }.joinToString(",")})"
                selectionArgsArray = permittedArtistIds.map { it.toString() }.toTypedArray()
            } else if (userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()){
                return@withContext emptyList()
            }
            if (userPreferencesRepository.allowedDirectoriesFlow.first().isEmpty()) {
                Log.w("MusicRepo/Artists", "Initial setup done, but no allowed directories. Returning empty list of artists.")
                return@withContext emptyList()
            }
        }
        Log.d("MusicRepo/Artists", "Querying MediaStore Artists with selection: '$selectionClause', args: '${selectionArgsArray?.joinToString()}', offset: $offset, limit: $pageSize")


        val cursor: Cursor?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                if (selectionClause != null) {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selectionClause)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgsArray)
                }
                putString(ContentResolver.QUERY_ARG_SORT_COLUMNS, MediaStore.Audio.Artists.ARTIST)
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_ASCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            cursor = context.contentResolver.query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, projection, queryArgs, null)
        } else {
            val sortOrderWithPaging = "$baseSortOrder LIMIT $pageSize OFFSET $offset"
            cursor = context.contentResolver.query(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, projection, selectionClause, selectionArgsArray, sortOrderWithPaging)
        }
        Log.d("MusicRepo/Artists", "MediaStore cursor count for artists: ${cursor?.count}")


        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val trackCountCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: "Artista Desconocido"
                var actualTrackCount = c.getInt(trackCountCol) // trackCount from MediaStore

                if (initialSetupDone) {
                    if (permittedArtistIds?.contains(id) == true) {
                        actualTrackCount = cachedPermittedArtistSongCounts?.get(id) ?: 0
                    } else {
                        actualTrackCount = 0
                    }
                }

                if (actualTrackCount > 0) {
                    artistsToReturn.add(Artist(id, name, actualTrackCount))
                }
            }
        }
        Log.i("MusicRepo/Artists", "Returning ${artistsToReturn.size} artists for Page: $page.")
        if (page == 1) Log.i("MusicRepo/Artists", "getArtists (page 1) took ${System.currentTimeMillis() - getArtistsStartTime} ms to return ${artistsToReturn.size} artists.")
        return@withContext artistsToReturn
    }

    // --- Funciones que usan queryAndFilterSongs (sin paginación propia, filtran por directorio) ---
    override suspend fun getSongsForAlbum(albumId: Long): List<Song> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ALBUM_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        return@withContext queryAndFilterSongs(selection, selectionArgs, sortOrder)
    }

    override suspend fun getSongsForArtist(artistId: Long): List<Song> = withContext(Dispatchers.IO) {
        // Necesitas ARTIST_ID para esta consulta
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ARTIST_ID} = ?"
        val selectionArgs = arrayOf(artistId.toString())
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        return@withContext queryAndFilterSongs(selection, selectionArgs, sortOrder)
    }

    override suspend fun getSongsByIds(songIds: List<String>): List<Song> = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) return@withContext emptyList()
        val selection = "${MediaStore.Audio.Media._ID} IN (${Array(songIds.size) { "?" }.joinToString(",")})"
        val selectionArgs = songIds.toTypedArray()
        val filteredSongs = queryAndFilterSongs(selection, selectionArgs, null) // Sin orden específico en MediaStore

        // Reordenar según la lista original de IDs
        val filteredSongsMap = filteredSongs.associateBy { it.id }
        return@withContext songIds.mapNotNull { filteredSongsMap[it] }
    }

    // Función para obtener (y cachear) las referencias mínimas de canciones permitidas
    // Función para obtener (y cachear) las referencias mínimas de canciones permitidas
    private suspend fun getPermittedSongReferences(forceRefresh: Boolean = false): List<MinimalSongInfo> = withContext(Dispatchers.IO) {
        permittedSongReferencesMutex.withLock {
            if (cachedPermittedSongReferences == null || forceRefresh) {
                val coldLoadStartTime = System.currentTimeMillis()
                Log.i("MusicRepo/Refs", "Populating cachedPermittedSongReferences. Force refresh: $forceRefresh")
                val allPermittedSongs = queryAndFilterSongs(
                    selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?",
                    selectionArgs = arrayOf("30000"),
                    sortOrder = null
                )
                cachedPermittedSongReferences = allPermittedSongs.map { song ->
                    MinimalSongInfo(song.id, song.albumId, song.artistId)
                }
                // Populate new caches
                val refs = cachedPermittedSongReferences ?: emptyList()
                cachedPermittedAlbumSongCounts = refs.groupBy { it.albumId }.mapValues { entry -> entry.value.size }
                cachedPermittedArtistSongCounts = refs.groupBy { it.artistId }.mapValues { entry -> entry.value.size }
                Log.i("MusicRepo/Refs", "Populated cachedPermittedSongReferences with ${cachedPermittedSongReferences?.size} items. Album counts: ${cachedPermittedAlbumSongCounts?.size}, Artist counts: ${cachedPermittedArtistSongCounts?.size}. Cold load took ${System.currentTimeMillis() - coldLoadStartTime} ms")
            }
        }
        return@withContext cachedPermittedSongReferences ?: emptyList()
    }

    // Necesitarás una forma de invalidar esta caché si allowedDirectories cambia.
// Podrías llamarla desde UserPreferencesRepository o desde un ViewModel que observe los cambios de directorio.
    fun invalidatePermittedSongReferencesCache() {
        Log.d("MusicRepositoryImpl", "Invalidating cachedPermittedSongReferences.")
        this.cachedPermittedSongReferences = null
    }

    // Méthod para invalidar la caché cuando cambian los directorios permitidos.
    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "Invalidating caches dependent on allowed directories (cachedPermittedSongReferences).")
        this.cachedPermittedSongReferences = null
        this.cachedPermittedAlbumSongCounts = null
        this.cachedPermittedArtistSongCounts = null
    }


    // --- Funciones de Directorio y URIs de Carátulas (sin cambios mayores) ---
    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        directoryScanMutex.withLock {
            cachedAudioDirectories?.let { return@withContext it }
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA) // Solo necesitamos DATA
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
            )?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
                }
            }
            val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
            if (!initialSetupDone && directories.isNotEmpty()) {
                Log.i("MusicRepo", "Initial setup: saving all found audio directories (${directories.size}) as allowed.")
                userPreferencesRepository.updateAllowedDirectories(directories) // Esto debería marcar initialSetupDone internamente
                invalidateCachesDependentOnAllowedDirectories() // Invalidar cachés porque los directorios permitidos han cambiado
            }
            cachedAudioDirectories = directories
            return@withContext directories
        }
    }

    override suspend fun getAllUniqueAlbumArtUris(): List<Uri> = withContext(Dispatchers.IO) {
        val uris = mutableSetOf<Uri>()
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0" // Solo de archivos de música
        // Podríamos añadir aquí un filtro para que solo obtenga URIs de álbumes que realmente tienen canciones permitidas,
        // usando los permittedAlbumIds de getPermittedSongReferences().
        // Pero para la precarga de temas, obtener todas las URIs y dejar que Coil maneje errores puede ser aceptable.
        // Si se vuelve un problema de rendimiento, se puede optimizar más.
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        var albumIdSelection: String? = null
        var albumIdSelectionArgs: Array<String>? = null

        if(initialSetupDone){
            val permittedAlbumIds = getPermittedSongReferences(false).map { it.albumId }.distinct().toSet()
            if(permittedAlbumIds.isNotEmpty()){
                albumIdSelection = "${MediaStore.Audio.Media.ALBUM_ID} IN (${Array(permittedAlbumIds.size){"?"}.joinToString(",")})"
                albumIdSelectionArgs = permittedAlbumIds.map { it.toString() }.toTypedArray()
            } else if (userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()){
                // Hay directorios permitidos pero no hay albumIDs, entonces no hay URIs que devolver
                return@withContext emptyList()
            }
            // Si initialSetupDone es true y allowedDirectories está vacío, albumIdSelection será null,
            // y se obtendrán todas las URIs. Esto está bien, ya que no hay canciones que mostrar de todos modos.
        }

        val finalSelection = if (albumIdSelection != null) {
            "$selection AND $albumIdSelection"
        } else {
            selection
        }

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, // Solo necesitamos ALBUM_ID
            finalSelection, // Aplicar el filtro de albumId si está disponible
            albumIdSelectionArgs, // Argumentos para el filtro de albumId
            null // El orden no importa
        )?.use { c ->
            val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (c.moveToNext()) {
                val albumId = c.getLong(albumIdColumn)
                uris.add(
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    )
                )
            }
        }
        Log.d("MusicRepo", "getAllUniqueAlbumArtUris returning ${uris.size} URIs.")
        return@withContext uris.toList()
    }

    // Search Methods Implementation

    override suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.TITLE} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        // Using the existing queryAndFilterSongs which handles directory permissions
        return@withContext queryAndFilterSongs(selection, selectionArgs, MediaStore.Audio.Media.TITLE + " ASC")
    }

    override suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        val albumsToReturn = mutableListOf<Album>()
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        var permittedAlbumIds: Set<Long>? = null

        if (initialSetupDone) {
            val songRefs = getPermittedSongReferences()
            if (songRefs.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                return@withContext emptyList()
            }
            permittedAlbumIds = songRefs.map { it.albumId }.distinct().toSet()
            if (permittedAlbumIds.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                return@withContext emptyList()
            }
            if (userPreferencesRepository.allowedDirectoriesFlow.first().isEmpty()) {
                return@withContext emptyList()
            }
        }

        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS
        )
        val selection = "${MediaStore.Audio.Albums.ALBUM} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val songCountCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                var actualSongCount = c.getInt(songCountCol)

                if (initialSetupDone) {
                    if (permittedAlbumIds?.contains(id) == true) {
                        actualSongCount = cachedPermittedAlbumSongCounts?.get(id) ?: 0
                    } else {
                        actualSongCount = 0 // Not in permitted list or no songs in permitted directories
                    }
                }
                 // If not initial setup, all albums are considered, use MediaStore song count.

                if (actualSongCount > 0) {
                    val title = c.getString(titleCol) ?: "Álbum Desconocido"
                    val artist = c.getString(artistCol) ?: "Varios Artistas"
                    val albumArtUriVal: Uri? = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), id
                    )
                    albumsToReturn.add(Album(id, title, artist, albumArtUriVal?.toString(), actualSongCount))
                }
            }
        }
        return@withContext albumsToReturn
    }

    override suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        val artistsToReturn = mutableListOf<Artist>()
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        var permittedArtistIds: Set<Long>? = null

        if (initialSetupDone) {
            val songRefs = getPermittedSongReferences()
            if (songRefs.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                return@withContext emptyList()
            }
            permittedArtistIds = songRefs.map { it.artistId }.distinct().toSet()
            if (permittedArtistIds.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
                return@withContext emptyList()
            }
            if (userPreferencesRepository.allowedDirectoriesFlow.first().isEmpty()) {
                return@withContext emptyList()
            }
        }

        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS // Corresponds to NUMBER_OF_SONGS for artists in MediaStore
        )
        val selection = "${MediaStore.Audio.Artists.ARTIST} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${MediaStore.Audio.Artists.ARTIST} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val trackCountCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                var actualTrackCount = c.getInt(trackCountCol)

                if (initialSetupDone) {
                     if (permittedArtistIds?.contains(id) == true) {
                        actualTrackCount = cachedPermittedArtistSongCounts?.get(id) ?: 0
                    } else {
                        actualTrackCount = 0 // Not in permitted list or no songs in permitted directories
                    }
                }
                // If not initial setup, all artists are considered, use MediaStore track count.

                if (actualTrackCount > 0) {
                    val name = c.getString(nameCol) ?: "Artista Desconocido"
                    artistsToReturn.add(Artist(id, name, actualTrackCount))
                }
            }
        }
        return@withContext artistsToReturn
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        // Placeholder: Actual implementation depends on how playlists are stored.
        // If using Room, inject PlaylistDao and query: e.g., playlistDao.searchByName("%$query%")
        // For now, returning an empty list.
        Log.d("MusicRepositoryImpl", "searchPlaylists called with query: $query. Returning empty list as not implemented.")
        return@withContext emptyList<Playlist>()
    }

    override suspend fun searchAll(query: String, filterType: SearchFilterType): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        val results = mutableListOf<SearchResultItem>()

        when (filterType) {
            SearchFilterType.ALL -> {
                val songs = searchSongs(query)
                val albums = searchAlbums(query)
                val artists = searchArtists(query)
                val playlists = searchPlaylists(query) // Will be empty for now

                songs.forEach { results.add(SearchResultItem.SongItem(it)) }
                albums.forEach { results.add(SearchResultItem.AlbumItem(it)) }
                artists.forEach { results.add(SearchResultItem.ArtistItem(it)) }
                playlists.forEach { results.add(SearchResultItem.PlaylistItem(it)) }
            }
            SearchFilterType.SONGS -> {
                val songs = searchSongs(query)
                songs.forEach { results.add(SearchResultItem.SongItem(it)) }
            }
            SearchFilterType.ALBUMS -> {
                val albums = searchAlbums(query)
                albums.forEach { results.add(SearchResultItem.AlbumItem(it)) }
            }
            SearchFilterType.ARTISTS -> {
                val artists = searchArtists(query)
                artists.forEach { results.add(SearchResultItem.ArtistItem(it)) }
            }
            SearchFilterType.PLAYLISTS -> {
                val playlists = searchPlaylists(query)
                playlists.forEach { results.add(SearchResultItem.PlaylistItem(it)) }
            }
        }

        // Consider limiting results per category or total results if needed in the future.
        // For example, take top N from each category.
        // results.sortBy { /* some criteria if needed, e.g. relevance score if calculated */ }
        return@withContext results
    }

    // Search History Implementation
    override suspend fun addSearchHistoryItem(query: String) = withContext(Dispatchers.IO) {
        searchHistoryDao.deleteByQuery(query) // Remove old entry if exists
        searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> = withContext(Dispatchers.IO) {
        return@withContext searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) = withContext(Dispatchers.IO) {
        searchHistoryDao.deleteByQuery(query)
    }

    override suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearAll()
    }

    override suspend fun getMusicByGenre(genreId: String): List<Song> = withContext(Dispatchers.IO) {
        Log.d("MusicRepositoryImpl", "getMusicByGenre called for genreId: $genreId (Placeholder)")

        // Fetch a batch of all available (and permitted) songs to work with.
        // Using queryAndFilterSongs to respect directory permissions.
        // Fetching up to 100 songs as a base for our placeholder logic.
        // Adjust sort order or selection as needed if you want more variety.
        val allPermittedSongs = queryAndFilterSongs(
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?",
            selectionArgs = arrayOf("30000"), // Songs longer than 30 seconds
            sortOrder = "${MediaStore.Audio.Media.TITLE} ASC" // Consistent ordering
        ).take(100) // Take up to 100 songs to form a base pool

        if (allPermittedSongs.isEmpty()) {
            return@withContext emptyList()
        }

        // Placeholder Logic: Distribute songs among known genres somewhat deterministically
        // This is a very basic placeholder. A real implementation would filter on actual genre metadata.
        val genreIndex = GenreDataSource.staticGenres.indexOfFirst { it.id == genreId }
        if (genreIndex == -1) {
            // Unknown genreId, return a small subset or empty list
            return@withContext allPermittedSongs.take(5)
        }

        val songsPerGenreApproximation = (allPermittedSongs.size / GenreDataSource.staticGenres.size).coerceAtLeast(1)
        val startIndex = (genreIndex * songsPerGenreApproximation) % allPermittedSongs.size
        val endIndex = (startIndex + songsPerGenreApproximation).coerceAtMost(allPermittedSongs.size)

        val genreSpecificSongs = if (startIndex < endIndex) {
            allPermittedSongs.subList(startIndex, endIndex)
        } else {
            // Fallback if calculation is off (e.g. very few songs)
            allPermittedSongs.shuffled().take( (5..10).random() ) // Random small subset
        }

        Log.d("MusicRepositoryImpl", "Placeholder: Returning ${genreSpecificSongs.size} songs for genre $genreId")
        return@withContext genreSpecificSongs
    }
}