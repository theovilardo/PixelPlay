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
import com.theveloper.pixelplay.data.database.MusicDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
// import kotlinx.coroutines.withContext // May not be needed for Flow transformations
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
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.toAlbum
import com.theveloper.pixelplay.data.database.toArtist
import com.theveloper.pixelplay.data.database.toSong
import kotlinx.coroutines.flow.first // Still needed for initialSetupDoneFlow.first() if used that way
import kotlinx.coroutines.sync.Mutex
// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes
import java.io.File

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context, // Keep for getAllUniqueAudioDirectories (initial scan)
    private val userPreferencesRepository: UserPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao, // Stays for search history
    private val musicDao: MusicDao // Injected MusicDao
) : MusicRepository {

    // Proyección común para canciones, AHORA INCLUYE ARTIST_ID - This is for MediaStore, less relevant here.
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
    // TODO: Re-evaluar la necesidad de esto con Room como SSoT.
    private val directoryScanMutex = Mutex()
    private var cachedAudioDirectories: Set<String>? = null

    // Las funciones queryAndFilterSongs y getPermittedSongReferences han sido eliminadas
    // ya que su lógica ha sido reemplazada por SyncWorker y consultas directas a Room con Flows.

    // Refactorizada para usar Room y Flow
    override fun getAudioFiles(page: Int, pageSize: Int): kotlinx.coroutines.flow.Flow<List<Song>> {
        Log.d("MusicRepo/Songs", "getAudioFiles (Room) - Page: $page, PageSize: $pageSize")
        val offset = (page - 1).coerceAtLeast(0) * pageSize

        return kotlinx.coroutines.flow.combine(
            musicDao.getSongs(pageSize, offset), // Flow<List<SongEntity>>
            userPreferencesRepository.allowedDirectoriesFlow, // Flow<Set<String>>
            userPreferencesRepository.initialSetupDoneFlow // Flow<Boolean>
        ) { songsFromDb, allowedDirs, initialSetupDone ->
            if (initialSetupDone && allowedDirs.isEmpty()) {
                emptyList<SongEntity>()
            } else if (!initialSetupDone) {
                // Si el setup inicial no está hecho, mostrar todas las canciones (como antes)
                songsFromDb
            } else {
                songsFromDb.filter { songEntity ->
                    val directoryPath = File(songEntity.filePath).parent
                    directoryPath != null && allowedDirs.contains(directoryPath)
                }
            }
        }.map { songEntities ->
            songEntities.map { it.toSong() }
        }
    }


    // IMPORTANT: Also update queryAndFilterSongs if it's used to populate song lists displayed in the UI
    // where genre might be needed. For now, focusing on getAudioFiles as per subtask emphasis.

    // Helper function
    private fun getPermittedSongsFlow(): Flow<List<SongEntity>> {
        return musicDao.getSongs(pageSize = Int.MAX_VALUE, offset = 0) // Effectively all songs
            .combine(
                userPreferencesRepository.allowedDirectoriesFlow,
                userPreferencesRepository.initialSetupDoneFlow
            ) { songsFromDb, allowedDirs, initialSetupDone ->
                if (initialSetupDone && allowedDirs.isEmpty()) {
                    emptyList<SongEntity>()
                } else if (!initialSetupDone) {
                    songsFromDb
                } else {
                    songsFromDb.filter { songEntity ->
                        val directoryPath = File(songEntity.filePath).parent
                        directoryPath != null && allowedDirs.contains(directoryPath)
                    }
                }
            }
    }

    // Necesitarás una forma de invalidar esta caché si allowedDirectories cambia.
// Podrías llamarla desde UserPreferencesRepository o desde un ViewModel que observe los cambios de directorio.
    // Esta caché ya no existe, Room es la SSoT.
    private fun invalidatePermittedSongReferencesCache() { // TODO: Eliminar esta función
        Log.d("MusicRepo", "invalidatePermittedSongReferencesCache called, but cache no longer exists.")
    }

    // Méthod para invalidar la caché cuando cambian los directorios permitidos.
    // Con Flows reactivos, esto podría no ser necesario, o su lógica cambiaría drásticamente (ej. forzar un refresh de un Flow).
    override suspend fun invalidateCachesDependentOnAllowedDirectories() { // TODO: Re-evaluar o eliminar.
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Flows should update reactively.")
        // Si se usara alguna caché manual que dependa de esto, se limpiaría aquí.
        // Por ahora, las principales fuentes de datos (canciones, álbumes, artistas) son Flows de Room.
    }


    // --- Funciones de Directorio y URIs de Carátulas (sin cambios mayores) ---
    override suspend fun getAllUniqueAudioDirectories(): Set<String> = kotlinx.coroutines.withContext(Dispatchers.IO) { // IO-bound, keep withContext
        kotlinx.coroutines.sync.withLock(directoryScanMutex) {
            // cachedAudioDirectories?.let { return@withContext it } // TODO: Considerar si esta caché de directorios aún es útil o si debe leerse siempre.
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
                kotlinx.coroutines.runBlocking { userPreferencesRepository.updateAllowedDirectories(directories) } // Esto debería marcar initialSetupDone internamente
                // invalidateCachesDependentOnAllowedDirectories() // Ya no es relevante de la misma manera. Los flows reaccionarán.
            }
            cachedAudioDirectories = directories // Se sigue cacheando para evitar re-escaneos frecuentes de MediaStore para esta función específica.
            return@withLock directories
        }
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .mapNotNull { it.albumArtUriString?.toUri() }
                .distinct()
        }
    }

    // Search Methods Implementation

    override fun searchSongs(query: String): Flow<List<Song>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return musicDao.searchSongs(query) // Returns Flow<List<SongEntity>>
            .combine(
                userPreferencesRepository.allowedDirectoriesFlow,
                userPreferencesRepository.initialSetupDoneFlow
            ) { songsFromDb, allowedDirs, initialSetupDone ->
                if (initialSetupDone && allowedDirs.isEmpty()) emptyList()
                else if (!initialSetupDone) songsFromDb
                else songsFromDb.filter { songEntity ->
                    File(songEntity.filePath).parent?.let { allowedDirs.contains(it) } ?: false
                }
            }.map { entities -> entities.map { it.toSong() } }
    }

    override fun searchAlbums(query: String): Flow<List<Album>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        // Combina el resultado de la búsqueda de DAO con las canciones permitidas para filtrar álbumes.
        return getPermittedSongsFlow().combine(musicDao.searchAlbums(query)) { permittedSongs, searchedAlbumsFromDb ->
            val permittedAlbumIds = permittedSongs.map { it.albumId }.toSet()
            val songCountByAlbumId = permittedSongs.groupBy { it.albumId }.mapValues { it.value.size }

            searchedAlbumsFromDb.filter { albumEntity ->
                albumEntity.id in permittedAlbumIds
            }.mapNotNull { albumEntity ->
                val currentSongCount = songCountByAlbumId[albumEntity.id] ?: 0
                if (currentSongCount > 0) albumEntity.toAlbum().copy(songCount = currentSongCount) else null
            }
        }
        /* // Old MediaStore Logic
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
        */
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        val artistsToReturn = mutableListOf<Artist>()
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        var permittedArtistIds: Set<Long>? = null

        // if (initialSetupDone) { // TODO: This old logic relying on getPermittedSongReferences needs to be removed/re-evaluated with Flows
            // val songRefs = getPermittedSongReferences() // Usa la caché
            // if (songRefs.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
            //     Log.w("MusicRepo/Artists", "Initial setup done, allowed directories exist, but no permitted song references found. No artists will be loaded based on song refs.")
            //     return@withContext emptyList()
            // }
            // permittedArtistIds = songRefs.map { it.artistId }.distinct().toSet()
            // if (permittedArtistIds.isEmpty() && userPreferencesRepository.allowedDirectoriesFlow.first().isNotEmpty()) {
            //     return@withContext emptyList()
            // }
            // if (userPreferencesRepository.allowedDirectoriesFlow.first().isEmpty()) {
            //     return@withContext emptyList()
            // }
        // }

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

                // if (initialSetupDone) { // TODO: This old logic relying on cachedPermittedArtistSongCounts needs to be removed
                //      if (permittedArtistIds?.contains(id) == true) {
                //         actualTrackCount = cachedPermittedArtistSongCounts?.get(id) ?: 0
                //     } else {
                //         actualTrackCount = 0 // Not in permitted list or no songs in permitted directories
                //     }
                // }
                // If not initial setup, all artists are considered, use MediaStore track count.

                if (actualTrackCount > 0) {
                    val name = c.getString(nameCol) ?: "Artista Desconocido"
                    artistsToReturn.add(Artist(id, name, actualTrackCount))
                }
            }
        }
        // return@withContext artistsToReturn // Old MediaStore Logic

        // New Room based logic for searchArtists
        return getPermittedSongsFlow().combine(musicDao.searchArtists(query)) { permittedSongs, searchedArtistsFromDb ->
            val permittedArtistIds = permittedSongs.map { it.artistId }.toSet()
            val trackCountByArtistId = permittedSongs.groupBy { it.artistId }.mapValues { it.value.size }

            searchedArtistsFromDb.filter { artistEntity ->
                artistEntity.id in permittedArtistIds
            }.mapNotNull { artistEntity ->
                val currentTrackCount = trackCountByArtistId[artistEntity.id] ?: 0
                if (currentTrackCount > 0) artistEntity.toArtist().copy(songCount = currentTrackCount) else null
            }
        }
    }

    // searchPlaylists es suspend en la interfaz, pero actualmente no hace IO.
    // Si se implementara con Room, el DAO sería suspend.
    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) {
            return emptyList()
        }
        // Placeholder: Actual implementation depends on how playlists are stored.
        // If using Room, inject PlaylistDao and query: e.g., playlistDao.searchByName("%$query%")
        // For now, returning an empty list.
        Log.d("MusicRepositoryImpl", "searchPlaylists called with query: $query. Returning empty list as not implemented.")
        return emptyList<Playlist>()
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        // searchPlaylists is still suspend, wrap its call for flow combination
        val playlistsFlow = kotlinx.coroutines.flow.flow { emit(searchPlaylists(query)) }

        return when (filterType) {
            SearchFilterType.ALL -> {
                combine(
                    searchSongs(query),
                    searchAlbums(query),
                    searchArtists(query),
                    playlistsFlow
                ) { songs, albums, artists, playlists ->
                    val results = mutableListOf<SearchResultItem>()
                    songs.forEach { results.add(SearchResultItem.SongItem(it)) }
                    albums.forEach { results.add(SearchResultItem.AlbumItem(it)) }
                    artists.forEach { results.add(SearchResultItem.ArtistItem(it)) }
                    playlists.forEach { results.add(SearchResultItem.PlaylistItem(it)) }
                    results
                }
            }
            SearchFilterType.SONGS -> searchSongs(query).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
            SearchFilterType.ALBUMS -> searchAlbums(query).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
            SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
            SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
        }
    }

    // Search History Implementation
    override suspend fun addSearchHistoryItem(query: String) {
        searchHistoryDao.deleteByQuery(query) // Remove old entry if exists
        searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
        searchHistoryDao.deleteByQuery(query)
    }

    override suspend fun clearSearchHistory() {
        searchHistoryDao.clearAll()
    }

    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        Log.i("MusicRepositoryImpl", "getMusicByGenre called for genreId: \"$genreId\"")

        val targetGenreName: String?
        val staticGenre = GenreDataSource.staticGenres.find { it.id.equals(genreId, ignoreCase = true) }

        targetGenreName = if (staticGenre != null) {
            Log.d("MusicRepositoryImpl", "Static genre found for ID \"$genreId\". Target name: \"${staticGenre.name}\"")
            staticGenre.name
        } else {
            Log.d("MusicRepositoryImpl", "No static genre found for ID \"$genreId\". Treating ID as target name: \"$genreId\"")
            genreId // Treat the ID as the name for dynamic genres
        }

        if (targetGenreName.isNullOrBlank()) {
            Log.w("MusicRepositoryImpl", "Target genre name is null or blank for genreId: \"$genreId\". Returning empty list.")
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        return musicDao.getSongsByGenre(targetGenreName)
            .combine(userPreferencesRepository.allowedDirectoriesFlow, userPreferencesRepository.initialSetupDoneFlow) { songsFromDb, allowedDirs, initialSetupDone ->
                if (initialSetupDone && allowedDirs.isEmpty()) emptyList()
                else if (!initialSetupDone) songsFromDb
                else songsFromDb.filter { songEntity -> File(songEntity.filePath).parent?.let { allowedDirs.contains(it) } ?: false }
            }.map { entities -> entities.map { it.toSong() } }
        }
}
                userPreferencesRepository.allowedDirectoriesFlow,
                userPreferencesRepository.initialSetupDoneFlow
            ) { songsFromDb, allowedDirs, initialSetupDone ->
                if (initialSetupDone && allowedDirs.isEmpty()) emptyList()
                else if (!initialSetupDone) songsFromDb
                else songsFromDb.filter { songEntity ->
                    File(songEntity.filePath).parent?.let { allowedDirs.contains(it) } ?: false
                }
            }.map { entities -> entities.map { it.toSong() } }
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        return musicDao.getSongsByArtistId(artistId)
            .combine(
                userPreferencesRepository.allowedDirectoriesFlow,
                userPreferencesRepository.initialSetupDoneFlow
            ) { songsFromDb, allowedDirs, initialSetupDone ->
                if (initialSetupDone && allowedDirs.isEmpty()) emptyList()
                else if (!initialSetupDone) songsFromDb
                else songsFromDb.filter { songEntity ->
                    File(songEntity.filePath).parent?.let { allowedDirs.contains(it) } ?: false
                }
            }.map { entities -> entities.map { it.toSong() } }
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val longIds = songIds.mapNotNull { it.toLongOrNull() }
        if (longIds.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())

        // Nota: El filtrado por directorio para getSongsByIds es un poco diferente.
        // Normalmente, si tienes IDs, esperas obtener esas canciones específicas.
        // Si estas canciones pudieran estar fuera de los directorios permitidos, necesitarías
        // el mismo .combine de filtrado que en getSongsForAlbum/Artist.
        // Por ahora, se asume que los IDs provienen de una fuente ya filtrada o no requieren este filtro aquí.
        // Si se requiere, se debe añadir el combine similar a los otros métodos.
        return musicDao.getSongsByIds(longIds)
            .map { entities ->
                val songsMap = entities.associateBy { it.id.toString() }
                songIds.mapNotNull { songsMap[it]?.toSong() } // Preserva el orden original y maneja IDs no encontrados
            }
    }

    // Función para obtener (y cachear) las referencias mínimas de canciones permitidas
    // Función para obtener (y cachear) las referencias mínimas de canciones permitidas
    private suspend fun getPermittedSongReferences(forceRefresh: Boolean = false): List<MinimalSongInfo> = withContext(Dispatchers.IO) {
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        val allowedDirectories = userPreferencesRepository.allowedDirectoriesFlow.first()
        if (initialSetupDone && allowedDirectories.isEmpty()) {
            cachedPermittedSongReferences = emptyList()
            cachedPermittedAlbumSongCounts = emptyMap()
            cachedPermittedArtistSongCounts = emptyMap()
            Log.i("MusicRepo/Refs", "Initial setup done and no allowed directories. Returning empty song references.")
            return@withContext emptyList<MinimalSongInfo>()
        }
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
    // Esta caché ya no existe, Room es la SSoT.
    fun invalidatePermittedSongReferencesCache() { // TODO: Eliminar esta función
        Log.d("MusicRepo", "invalidatePermittedSongReferencesCache called, but cache no longer exists.")
    }

    // Méthod para invalidar la caché cuando cambian los directorios permitidos.
    // Con Flows reactivos, esto podría no ser necesario, o su lógica cambiaría drásticamente (ej. forzar un refresh de un Flow).
    override suspend fun invalidateCachesDependentOnAllowedDirectories() { // TODO: Re-evaluar o eliminar.
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Flows should update reactively.")
        // Si se usara alguna caché manual que dependa de esto, se limpiaría aquí.
        // Por ahora, las principales fuentes de datos (canciones, álbumes, artistas) son Flows de Room.
    }


    // --- Funciones de Directorio y URIs de Carátulas (sin cambios mayores) ---
    override suspend fun getAllUniqueAudioDirectories(): Set<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        kotlinx.coroutines.sync.withLock(directoryScanMutex) {
            // cachedAudioDirectories?.let { return@withContext it } // TODO: Considerar si esta caché de directorios aún es útil o si debe leerse siempre.
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
                // invalidateCachesDependentOnAllowedDirectories() // Ya no es relevante de la misma manera. Los flows reaccionarán.
            }
            cachedAudioDirectories = directories // Se sigue cacheando para evitar re-escaneos frecuentes de MediaStore para esta función específica.
            return@withLock directories
        }
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .mapNotNull { it.albumArtUriString?.toUri() }
                .distinct()
        }
    }

    // Search Methods Implementation

    override fun searchSongs(query: String): Flow<List<Song>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return musicDao.searchSongs(query) // Returns Flow<List<SongEntity>>
            .combine(
                userPreferencesRepository.allowedDirectoriesFlow,
                userPreferencesRepository.initialSetupDoneFlow
            ) { songsFromDb, allowedDirs, initialSetupDone ->
                if (initialSetupDone && allowedDirs.isEmpty()) emptyList()
                else if (!initialSetupDone) songsFromDb
                else songsFromDb.filter { songEntity ->
                    File(songEntity.filePath).parent?.let { allowedDirs.contains(it) } ?: false
                }
            }.map { entities -> entities.map { it.toSong() } }
    }

    override fun searchAlbums(query: String): Flow<List<Album>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        // Combina el resultado de la búsqueda de DAO con las canciones permitidas para filtrar álbumes.
        return getPermittedSongsFlow().combine(musicDao.searchAlbums(query)) { permittedSongs, searchedAlbumsFromDb ->
            val permittedAlbumIds = permittedSongs.map { it.albumId }.toSet()
            val songCountByAlbumId = permittedSongs.groupBy { it.albumId }.mapValues { it.value.size }

            searchedAlbumsFromDb.filter { albumEntity ->
                albumEntity.id in permittedAlbumIds
            }.mapNotNull { albumEntity ->
                val currentSongCount = songCountByAlbumId[albumEntity.id] ?: 0
                if (currentSongCount > 0) albumEntity.toAlbum().copy(songCount = currentSongCount) else null
            }
        }
        /* // Old MediaStore Logic
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
        */
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
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
        // return@withContext artistsToReturn // Old MediaStore Logic

        // New Room based logic for searchArtists
        return getPermittedSongsFlow().combine(musicDao.searchArtists(query)) { permittedSongs, searchedArtistsFromDb ->
            val permittedArtistIds = permittedSongs.map { it.artistId }.toSet()
            val trackCountByArtistId = permittedSongs.groupBy { it.artistId }.mapValues { it.value.size }

            searchedArtistsFromDb.filter { artistEntity ->
                artistEntity.id in permittedArtistIds
            }.mapNotNull { artistEntity ->
                val currentTrackCount = trackCountByArtistId[artistEntity.id] ?: 0
                if (currentTrackCount > 0) artistEntity.toArtist().copy(songCount = currentTrackCount) else null
            }
        }
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext emptyList()
        }
        // Placeholder: Actual implementation depends on how playlists are stored.
        // If using Room, inject PlaylistDao and query: e.g., playlistDao.searchByName("%$query%")
        // For now, returning an empty list.
        Log.d("MusicRepositoryImpl", "searchPlaylists called with query: $query. Returning empty list as not implemented.")
        return@withContext emptyList<Playlist>()
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        // searchPlaylists is still suspend, wrap its call for flow combination
        val playlistsFlow = kotlinx.coroutines.flow.flow { emit(searchPlaylists(query)) }

        return when (filterType) {
            SearchFilterType.ALL -> {
                combine(
                    searchSongs(query),
                    searchAlbums(query),
                    searchArtists(query),
                    playlistsFlow
                ) { songs, albums, artists, playlists ->
                    val results = mutableListOf<SearchResultItem>()
                    songs.forEach { results.add(SearchResultItem.SongItem(it)) }
                    albums.forEach { results.add(SearchResultItem.AlbumItem(it)) }
                    artists.forEach { results.add(SearchResultItem.ArtistItem(it)) }
                    playlists.forEach { results.add(SearchResultItem.PlaylistItem(it)) }
                    results
                }
            }
            SearchFilterType.SONGS -> searchSongs(query).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
            SearchFilterType.ALBUMS -> searchAlbums(query).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
            SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
            SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
        }
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

    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        Log.i("MusicRepositoryImpl", "getMusicByGenre called for genreId: \"$genreId\"")

        val targetGenreName: String?
        val staticGenre = GenreDataSource.staticGenres.find { it.id.equals(genreId, ignoreCase = true) }

        targetGenreName = if (staticGenre != null) {
            Log.d("MusicRepositoryImpl", "Static genre found for ID \"$genreId\". Target name: \"${staticGenre.name}\"")
            staticGenre.name
        } else {
            Log.d("MusicRepositoryImpl", "No static genre found for ID \"$genreId\". Treating ID as target name: \"$genreId\"")
            genreId // Treat the ID as the name for dynamic genres
        }

        if (targetGenreName.isNullOrBlank()) {
            Log.w("MusicRepositoryImpl", "Target genre name is null or blank for genreId: \"$genreId\". Returning empty list.")
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }

        return musicDao.getSongsByGenre(targetGenreName)
            .combine(userPreferencesRepository.allowedDirectoriesFlow, userPreferencesRepository.initialSetupDoneFlow) { songsFromDb, allowedDirs, initialSetupDone ->
                if (initialSetupDone && allowedDirs.isEmpty()) emptyList()
                else if (!initialSetupDone) songsFromDb
                else songsFromDb.filter { songEntity -> File(songEntity.filePath).parent?.let { allowedDirs.contains(it) } ?: false }
            }.map { entities -> entities.map { it.toSong() } }
        }
}