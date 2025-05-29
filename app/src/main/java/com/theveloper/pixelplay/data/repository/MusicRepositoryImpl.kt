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
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
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
                            duration = duration
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
                                duration = c.getLong(durationCol)
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
    // Implementación de getAudioFiles con paginación MANUAL
//    override suspend fun getAudioFiles(page: Int, pageSize: Int): List<Song> = withContext(Dispatchers.IO) {
//        val offset = (page - 1) * pageSize
//        if (offset < 0) throw IllegalArgumentException("Page number must be 1 or greater")
//
//        // Si la caché de todas las canciones filtradas no existe, la cargamos
//        if (cachedAllSongs == null) {
//            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
//            val selectionArgs = arrayOf("30000") // Mínimo 30 segundos
//            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC" // Ordenar pero SIN paginación en la consulta
//
//            // Consulta MediaStore para obtener *todas* las canciones válidas y filtrarlas
//            cachedAllSongs = queryAndFilterSongs(selection, selectionArgs, sortOrder)
//        }
//
//        // Aplicar Paginación MANUAL a la lista cacheada
//        return@withContext cachedAllSongs?.drop(offset)?.take(pageSize) ?: emptyList()
//    }

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
    // Implementación de getAlbums con paginación MANUAL
//    override suspend fun getAlbums(page: Int, pageSize: Int): List<Album> = withContext(Dispatchers.IO) {
//        val allAlbums = mutableListOf<Album>()
//        val offset = (page - 1) * pageSize
//        if (offset < 0) throw IllegalArgumentException("Page number must be 1 or greater")
//
//        val projection = arrayOf(
//            MediaStore.Audio.Albums._ID,
//            MediaStore.Audio.Albums.ALBUM,
//            MediaStore.Audio.Albums.ARTIST,
//            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
//            MediaStore.Audio.Albums.ALBUM_ART // Disponible a partir de API 29
//        )
//
//        // Consulta SIN paginación en MediaStore
//        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC" // Solo ordenar, sin LIMIT/OFFSET
//
//        val cursor: Cursor? = context.contentResolver.query(
//            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
//            projection,
//            null, // Sin selección específica por ahora
//            null,
//            sortOrder // Usamos el sortOrder SIN paginación
//        )
//        cursor?.use { c ->
//            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
//            val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
//            val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
//            val songCountColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
//            // val albumArtColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART) // API 29+
//
//            while (c.moveToNext()) {
//                val id = c.getLong(idColumn)
//                val title = c.getString(titleColumn) ?: "Álbum Desconocido"
//                val artist = c.getString(artistColumn) ?: "Varios Artistas"
//                val songCount = c.getInt(songCountColumn)
//                val albumArtUri: Uri? = ContentUris.withAppendedId(
//                    Uri.parse("content://media/external/audio/albumart"), id
//                )
//
//                if (songCount > 0) { // Solo mostrar álbumes con canciones
//                    // Añadir *todos* los álbumes válidos a la lista temporal
//                    allAlbums.add(Album(id, title, artist, albumArtUri, songCount))
//                }
//            }
//        }
//
//        // --- Aplicar Paginación MANUAL a la lista completa ---
//        return@withContext allAlbums.drop(offset).take(pageSize)
//        // ----------------------------------------------------
//    }

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
    // Implementación de getArtists con paginación MANUAL
//    override suspend fun getArtists(page: Int, pageSize: Int): List<Artist> = withContext(Dispatchers.IO) {
//        val allArtists = mutableListOf<Artist>()
//        val offset = (page - 1) * pageSize
//        if (offset < 0) throw IllegalArgumentException("Page number must be 1 or greater")
//
//        val projection = arrayOf(
//            MediaStore.Audio.Artists._ID,
//            MediaStore.Audio.Artists.ARTIST,
//            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
//        )
//
//        // Consulta SIN paginación en MediaStore
//        val sortOrder = "${MediaStore.Audio.Artists.ARTIST} ASC" // Solo ordenar, sin LIMIT/OFFSET
//
//        val cursor: Cursor? = context.contentResolver.query(
//            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
//            projection,
//            null,
//            null,
//            sortOrder // Usamos el sortOrder SIN paginación
//        )
//        cursor?.use { c ->
//            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
//            val nameColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
//            val trackCountColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
//            while (c.moveToNext()) {
//                val id = c.getLong(idColumn)
//                val name = c.getString(nameColumn) ?: "Artista Desconocido"
//                val trackCount = c.getInt(trackCountColumn)
//                if (trackCount > 0) { // Solo mostrar artistas con canciones
//                    // Añadir *todos* los artistas válidos a la lista temporal
//                    allArtists.add(Artist(id, name, trackCount))
//                }
//            }
//        }
//
//        // --- Aplicar Paginación MANUAL a la lista completa ---
//        return@withContext allArtists.drop(offset).take(pageSize)
//        // ---------------------------------------------------
//    }

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
                this.cachedPermittedAlbumSongCounts = refs.groupBy { it.albumId }.mapValues { entry -> entry.value.size }
                this.cachedPermittedArtistSongCounts = refs.groupBy { it.artistId }.mapValues { entry -> entry.value.size }
                Log.i("MusicRepo/Refs", "Populated cachedPermittedSongReferences with ${cachedPermittedSongReferences?.size} items. Album counts: ${cachedPermittedAlbumSongCounts?.size}, Artist counts: ${cachedPermittedArtistSongCounts?.size}. Cold load took ${System.currentTimeMillis() - coldLoadStartTime} ms")
            }
        }
        return@withContext cachedPermittedSongReferences ?: emptyList()
    }

    // Necesitarás una forma de invalidar esta caché si allowedDirectories cambia.
// Podrías llamarla desde UserPreferencesRepository o desde un ViewModel que observe los cambios de directorio.
    fun invalidatePermittedSongReferencesCache() {
        Log.d("MusicRepositoryImpl", "Invalidating cachedPermittedSongReferences.")
        // Considera el scope de la corrutina si no estás en un ViewModel.
        // Si estás en un ViewModel, usa viewModelScope.launch.
        // Aquí, como es una función del repositorio, no tiene viewModelScope.
        // Se puede hacer que el ViewModel llame a esta función dentro de su propio scope.
        // O, si este repositorio tiene su propio CoroutineScope, usarlo.
        // Por simplicidad, la invalidación es síncrona respecto al Mutex para la próxima lectura.
        // kotlinx.coroutines.GlobalScope.launch { // ¡Evitar GlobalScope en producción! Usar un scope gestionado.
        //     permittedSongReferencesMutex.withLock {
        //         cachedPermittedSongReferences = null
        //     }
        // }
        // Una forma más simple de invalidar para la próxima lectura:
        this.cachedPermittedSongReferences = null
        // La próxima llamada a getPermittedSongReferences (con el mutex) lo recargará.
    }

    // Método para invalidar la caché cuando cambian los directorios permitidos.
    // Llamar desde el ViewModel/lugar apropiado cuando UserPreferencesRepository.allowedDirectoriesFlow emita un nuevo valor.
    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "Invalidating caches dependent on allowed directories (cachedPermittedSongReferences).")
        // No es necesario GlobalScope aquí, simplemente nulificar para la próxima carga.
        // El mutex en getPermittedSongReferences manejará la recarga segura.
        this.cachedPermittedSongReferences = null
        this.cachedPermittedAlbumSongCounts = null
        this.cachedPermittedArtistSongCounts = null
        // Considera si cachedAudioDirectories también necesita invalidarse aquí.
        // this.cachedAudioDirectories = null; // Si getAllUniqueAudioDirectories debe re-escanear
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
            // La lógica de actualizar UserPreferencesRepository si !initialSetupDone está bien aquí.
            // Asegúrate que UserPreferencesRepository.updateAllowedDirectories también llame a
            // UserPreferencesRepository.setInitialSetupDone(true) o que lo hagas explícitamente.
            // También, considera llamar a invalidatePermittedSongReferencesCache() si los directorios cambian.
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
    // Función para obtener todos los directorios únicos que contienen audio
    // Usado por SettingsViewModel
//    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
//        // Usamos un Mutex para asegurar que solo un escaneo de directorios se ejecute a la vez
//        directoryScanMutex.withLock {
//            // Si ya hemos escaneado los directorios recientemente, devolvemos la caché.
//            cachedAudioDirectories?.let { return@withContext it }
//
//            // Si no hay caché, realizamos la consulta a MediaStore para *todos* los directorios.
//            val directories = mutableSetOf<String>()
//            val projection = arrayOf(MediaStore.Audio.Media.DATA)
//            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
//
//            // Consulta SIN paginación para obtener TODOS los directorios
//            val cursor: Cursor? = context.contentResolver.query(
//                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
//                projection,
//                selection,
//                null,
//                null // No hay LIMIT/OFFSET ni ORDER BY aquí
//            )
//            cursor?.use { c ->
//                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
//                while (c.moveToNext()) {
//                    val filePath = c.getString(dataColumn)
//                    File(filePath).parent?.let { directories.add(it) }
//                }
//            }
//
//            // Si el setup inicial no se ha hecho, guardar todos los directorios encontrados como permitidos.
//            val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
//            if (!initialSetupDone && directories.isNotEmpty()) {
//                userPreferencesRepository.updateAllowedDirectories(directories) // Esto ya marca el setup como hecho
//                // La siguiente línea ya no es necesaria si updateAllowedDirectories lo maneja,
//                // pero si quieres llamarla explícitamente por claridad o en otros escenarios:
//                // userPreferencesRepository.setInitialSetupDone(true)
//            }
//
//            // Cachear los directorios encontrados en este escaneo completo
//            cachedAudioDirectories = directories
//            return@withContext directories
//        } // Fin del Mutex lock
//    }

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
//    override suspend fun getAllUniqueAlbumArtUris(): List<Uri> = withContext(Dispatchers.IO) {
//        val uris = mutableSetOf<Uri>() // Usar Set para evitar duplicados
//        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
//        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
//        val cursor: Cursor? = context.contentResolver.query(
//            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
//            projection,
//            selection,
//            null,
//            null // No necesitamos orden específico aquí
//        )
//        cursor?.use { c ->
//            val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
//            while (c.moveToNext()) {
//                val albumId = c.getLong(albumIdColumn)
//                val albumArtUri: Uri = ContentUris.withAppendedId(
//                    Uri.parse("content://media/external/audio/albumart"),
//                    albumId
//                )
//                // Verificar si la URI es válida o si la carátula existe podría ser una optimización,
//                // pero Coil ya maneja errores de carga.
//                uris.add(albumArtUri)
//            }
//        }
//        return@withContext uris.toList()
//    }
}