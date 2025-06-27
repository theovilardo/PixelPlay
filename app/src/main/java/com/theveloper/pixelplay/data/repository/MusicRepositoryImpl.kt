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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes
import java.io.File

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val musicDao: MusicDao
) : MusicRepository {

    private val directoryScanMutex = Mutex()

    /**
     * Flow auxiliar que obtiene TODAS las canciones permitidas según los directorios
     * seleccionados por el usuario. Es la base para todas las demás funciones de obtención de datos.
     */
    private fun getPermittedSongsFlow(): Flow<List<SongEntity>> {
        // Obtenemos TODAS las canciones de la base de datos de una sola vez.
        val allSongsFlow = musicDao.getSongs(pageSize = Int.MAX_VALUE, offset = 0)
        val allowedDirectoriesFlow = userPreferencesRepository.allowedDirectoriesFlow
        val initialSetupDoneFlow = userPreferencesRepository.initialSetupDoneFlow

        return combine(
            allSongsFlow,
            allowedDirectoriesFlow,
            initialSetupDoneFlow
        ) { songsFromDb, allowedDirs, initialSetupDone ->
            when {
                // Si la configuración está hecha y no hay directorios permitidos, devuelve una lista vacía.
                initialSetupDone && allowedDirs.isEmpty() -> emptyList()
                // Si la configuración inicial no se ha hecho, muestra todas las canciones.
                !initialSetupDone -> songsFromDb
                // De lo contrario, filtra las canciones por los directorios permitidos.
                else -> songsFromDb.filter { songEntity ->
                    File(songEntity.filePath).parent?.let { allowedDirs.contains(it) } ?: false
                }
            }
        }
    }

    /**
     * Obtiene una lista paginada de canciones, aplicando la paginación DESPUÉS de filtrar por directorio.
     */
    override fun getAudioFiles(page: Int, pageSize: Int): Flow<List<Song>> {
        Log.d("MusicRepo/Songs", "getAudioFiles (Corrected) - Page: $page, PageSize: $pageSize")
        val offset = (page - 1).coerceAtLeast(0) * pageSize

        return getPermittedSongsFlow().map { permittedSongs ->
            if (offset >= permittedSongs.size) {
                emptyList() // La página está fuera de los límites
            } else {
                val toIndex = (offset + pageSize).coerceAtMost(permittedSongs.size)
                permittedSongs.subList(offset, toIndex).map { it.toSong() }
            }
        }
    }

    /**
     * Obtiene una lista paginada de álbumes, derivada de las canciones permitidas.
     */
    override fun getAlbums(page: Int, pageSize: Int): Flow<List<Album>> {
        Log.d("MusicRepo/Albums", "getAlbums (Corrected) - Page: $page, PageSize: $pageSize")
        val offset = (page - 1).coerceAtLeast(0) * pageSize

        return getPermittedSongsFlow().map { permittedSongs ->
            // 1. Deriva la lista de álbumes únicos a partir de las canciones permitidas
            val albumsFromSongs = permittedSongs
                .groupBy { it.albumId }
                .map { (albumId, songsInAlbum) ->
                    val firstSong = songsInAlbum.first()
                    Album(
                        id = albumId,
                        title = firstSong.albumName,
                        artist = firstSong.artistName, // Se usa el artista de la primera canción como representativo
                        albumArtUriString = firstSong.albumArtUriString,
                        songCount = songsInAlbum.size
                    )
                }
                .sortedBy { it.title.lowercase() } // Ordena la lista completa de álbumes

            // 2. Pagina la lista de álbumes ya derivada y ordenada
            if (offset >= albumsFromSongs.size) {
                emptyList()
            } else {
                val toIndex = (offset + pageSize).coerceAtMost(albumsFromSongs.size)
                albumsFromSongs.subList(offset, toIndex)
            }
        }
    }

    /**
     * Obtiene una lista paginada de artistas, derivada de las canciones permitidas.
     */
    override fun getArtists(page: Int, pageSize: Int): Flow<List<Artist>> {
        Log.d("MusicRepo/Artists", "getArtists (Corrected) - Page: $page, PageSize: $pageSize")
        val offset = (page - 1).coerceAtLeast(0) * pageSize

        return getPermittedSongsFlow().map { permittedSongs ->
            // 1. Deriva la lista de artistas únicos a partir de las canciones permitidas
            val artistsFromSongs = permittedSongs
                .groupBy { it.artistId }
                .map { (artistId, songsByArtist) ->
                    val firstSong = songsByArtist.first()
                    Artist(
                        id = artistId,
                        name = firstSong.artistName,
                        songCount = songsByArtist.size
                    )
                }
                .sortedBy { it.name.lowercase() } // Ordena la lista completa de artistas

            // 2. Pagina la lista de artistas ya derivada y ordenada
            if (offset >= artistsFromSongs.size) {
                emptyList()
            } else {
                val toIndex = (offset + pageSize).coerceAtMost(artistsFromSongs.size)
                artistsFromSongs.subList(offset, toIndex)
            }
        }
    }


    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .filter { it.albumId == albumId }
                .map { it.toSong() }
        }
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .filter { it.artistId == artistId }
                .map { it.toSong() }
        }
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
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
                runBlocking { userPreferencesRepository.updateAllowedDirectories(directories) }
            }
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

    // --- Métodos de Búsqueda ---

    override fun searchSongs(query: String): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        // La búsqueda se hace sobre las canciones permitidas
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .filter { it.title.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true) }
                .map { it.toSong() }
        }
    }

    override fun searchAlbums(query: String): Flow<List<Album>> {
        if (query.isBlank()) return flowOf(emptyList())
        // La búsqueda se hace sobre los álbumes derivados de las canciones permitidas
        return getAlbums(1, Int.MAX_VALUE).map { allPermittedAlbums ->
            allPermittedAlbums.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
        }
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        // La búsqueda se hace sobre los artistas derivados de las canciones permitidas
        return getArtists(1, Int.MAX_VALUE).map { allPermittedArtists ->
            allPermittedArtists.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()
        Log.d("MusicRepositoryImpl", "searchPlaylists called with query: $query. Not implemented.")
        return emptyList()
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val playlistsFlow = flow { emit(searchPlaylists(query)) }

        return when (filterType) {
            SearchFilterType.ALL -> {
                combine(
                    searchSongs(query),
                    searchAlbums(query),
                    searchArtists(query),
                    playlistsFlow
                ) { songs, albums, artists, playlists ->
                    mutableListOf<SearchResultItem>().apply {
                        songs.forEach { add(SearchResultItem.SongItem(it)) }
                        albums.forEach { add(SearchResultItem.AlbumItem(it)) }
                        artists.forEach { add(SearchResultItem.ArtistItem(it)) }
                        playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
                    }
                }
            }
            SearchFilterType.SONGS -> searchSongs(query).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
            SearchFilterType.ALBUMS -> searchAlbums(query).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
            SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
            SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
        }
    }

    override suspend fun addSearchHistoryItem(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
        }
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return withContext(Dispatchers.IO) {
            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
        }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    override suspend fun clearSearchHistory() {
        withContext(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }

    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        val staticGenre = GenreDataSource.staticGenres.find { it.id.equals(genreId, ignoreCase = true) }
        val targetGenreName = staticGenre?.name ?: genreId
        if (targetGenreName.isBlank()) {
            return flowOf(emptyList())
        }
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .filter { it.genre.equals(targetGenreName, ignoreCase = true) }
                .map { it.toSong() }
        }
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        val longIds = songIds.mapNotNull { it.toLongOrNull() }
        if (longIds.isEmpty()) return flowOf(emptyList())

        return musicDao.getSongsByIds(longIds)
            .map { entities ->
                val songsMap = entities.associateBy { it.id.toString() }
                songIds.mapNotNull { songsMap[it] }.map { it.toSong() }
            }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    suspend fun syncMusicFromContentResolver() {
        // Esta función ahora está en SyncWorker. Se deja el esqueleto por si se llama desde otro lugar.
        Log.w("MusicRepo", "syncMusicFromContentResolver was called directly on repository. This should be handled by SyncWorker.")
    }
}

//@Singleton
//class MusicRepositoryImpl @Inject constructor(
//    @ApplicationContext private val context: Context,
//    private val userPreferencesRepository: UserPreferencesRepository,
//    private val searchHistoryDao: SearchHistoryDao,
//    private val musicDao: MusicDao
//) : MusicRepository {
//
//    // Mutex para el escaneo inicial de directorios (getAllUniqueAudioDirectories)
//    private val directoryScanMutex = Mutex()
//    private var cachedAudioDirectories: Set<String>? = null
//
//    /**
//     * Obtiene una lista paginada de canciones, filtrada por los directorios permitidos.
//     * Usa Flows de Room para reaccionar a los cambios en la base de datos y en las preferencias.
//     */
//    override fun getAudioFiles(page: Int, pageSize: Int): Flow<List<Song>> {
//        Log.d("MusicRepo/Songs", "getAudioFiles (Room) - Page: $page, PageSize: $pageSize")
//        val offset = (page - 1).coerceAtLeast(0) * pageSize
//
//        return combine(
//            musicDao.getSongs(pageSize, offset), // Flow<List<SongEntity>>
//            userPreferencesRepository.allowedDirectoriesFlow, // Flow<Set<String>>
//            userPreferencesRepository.initialSetupDoneFlow // Flow<Boolean>
//        ) { songsFromDb, allowedDirs, initialSetupDone ->
//            when {
//                initialSetupDone && allowedDirs.isEmpty() -> emptyList()
//                !initialSetupDone -> songsFromDb // Si el setup no está hecho, mostrar todo
//                else -> songsFromDb.filter { songEntity ->
//                    File(songEntity.filePath).parent?.let { allowedDirs.contains(it) } ?: false
//                }
//            }
//        }.map { songEntities ->
//            songEntities.map { it.toSong() }
//        }
//    }
//
//    override fun getAlbums(page: Int, pageSize: Int): Flow<List<Album>> {
//        Log.d("MusicRepo/Albums", "getAlbums (Room) - Page: $page, PageSize: $pageSize")
//        val offset = (page - 1).coerceAtLeast(0) * pageSize
//
//        // Obtenemos todos los álbumes paginados del DAO
//        val allAlbumsFromDaoFlow = musicDao.getAlbums(pageSize, offset) // Asume que esta función existe en MusicDao
//
//        // Combinamos con el permittedSongsFlow para saber qué álbumes tienen canciones permitidas
//        // y para obtener el recuento de canciones correctas por álbum.
//        return combine(
//            allAlbumsFromDaoFlow,
//            getPermittedSongsFlow() // Usamos la función auxiliar que ya filtra canciones
//        ) { albumsFromDb, permittedSongs ->
//            val permittedAlbumIds = permittedSongs.map { it.albumId }.toSet()
//            val songCountByAlbumId = permittedSongs
//                .groupBy { it.albumId }
//                .mapValues { it.value.size }
//
//            albumsFromDb
//                .filter { albumEntity -> albumEntity.id in permittedAlbumIds } // Solo álbumes con canciones permitidas
//                .mapNotNull { albumEntity ->
//                    val currentSongCount = songCountByAlbumId[albumEntity.id] ?: 0
//                    if (currentSongCount > 0) {
//                        // Actualiza el songCount del Album con el recuento de canciones permitidas
//                        albumEntity.toAlbum().copy(songCount = currentSongCount)
//                    } else {
//                        null // No debería ocurrir si filtramos por permittedAlbumIds primero, pero es una salvaguarda
//                    }
//                }
//        }
//    }
//
//    override fun getArtists(page: Int, pageSize: Int): Flow<List<Artist>> {
//        Log.d("MusicRepo/Artists", "getArtists (Room) - Page: $page, PageSize: $pageSize")
//        val offset = (page - 1).coerceAtLeast(0) * pageSize
//
//        // Obtenemos todos los artistas paginados del DAO
//        val allArtistsFromDaoFlow = musicDao.getArtists(pageSize, offset) // Asume que esta función existe en MusicDao
//
//        // Combinamos con el permittedSongsFlow para saber qué artistas tienen canciones permitidas
//        // y para obtener el recuento de canciones correctas por artista.
//        return combine(
//            allArtistsFromDaoFlow,
//            getPermittedSongsFlow() // Usamos la función auxiliar que ya filtra canciones
//        ) { artistsFromDb, permittedSongs ->
//            val permittedArtistIds = permittedSongs.map { it.artistId }.toSet()
//            val songCountByArtistId = permittedSongs
//                .groupBy { it.artistId }
//                .mapValues { it.value.size }
//
//            artistsFromDb
//                .filter { artistEntity -> artistEntity.id in permittedArtistIds } // Solo artistas con canciones permitidas
//                .mapNotNull { artistEntity ->
//                    val currentSongCount = songCountByArtistId[artistEntity.id] ?: 0
//                    if (currentSongCount > 0) {
//                        // Actualiza el songCount del Artist con el recuento de canciones permitidas
//                        // Asumiendo que tu modelo Artist tiene un campo songCount o similar
//                        artistEntity.toArtist().copy(songCount = currentSongCount)
//                    } else {
//                        null
//                    }
//                }
//        }
//    }
//
//    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
//        Log.d("MusicRepo/Songs", "getSongsForAlbum (Room) - AlbumId: $albumId")
//
//        val songsForAlbumFromDaoFlow = musicDao.getSongsByAlbumId(albumId) // Asume que esta función existe en MusicDao
//        val allowedDirectoriesFlow = userPreferencesRepository.allowedDirectoriesFlow
//        val initialSetupDoneFlow = userPreferencesRepository.initialSetupDoneFlow
//
//        return combine(
//            songsForAlbumFromDaoFlow,
//            allowedDirectoriesFlow,
//            initialSetupDoneFlow
//        ) { songsFromDb, allowedDirs, initialSetupDone ->
//            when {
//                initialSetupDone && allowedDirs.isEmpty() -> emptyList<SongEntity>()
//                !initialSetupDone -> songsFromDb // Si el setup no está hecho, mostrar todo para este álbum específico
//                else -> songsFromDb.filter { songEntity ->
//                    File(songEntity.filePath).parent?.let { parentDir ->
//                        allowedDirs.contains(parentDir)
//                    } ?: false
//                }
//            }
//        }.map { entities -> entities.map { it.toSong() } }
//    }
//
//    /**
//     * Flow auxiliar que obtiene TODAS las canciones permitidas según los directorios
//     * seleccionados por el usuario. Es la base para muchas otras funciones de búsqueda y filtrado.
//     */
//    private fun getPermittedSongsFlow(): Flow<List<SongEntity>> {
//        val allSongsFlow = musicDao.getSongs(pageSize = Int.MAX_VALUE, offset = 0) // Obtiene todas las canciones de la BD
//        val allowedDirectoriesFlow = userPreferencesRepository.allowedDirectoriesFlow
//        val initialSetupDoneFlow = userPreferencesRepository.initialSetupDoneFlow
//
//        return combine(
//            allSongsFlow,
//            allowedDirectoriesFlow,
//            initialSetupDoneFlow
//        ) { songsFromDb, allowedDirs, initialSetupDone ->
//            // La lógica de tu lambda de transformación permanece igual
//            when {
//                initialSetupDone && allowedDirs.isEmpty() -> emptyList()
//                !initialSetupDone -> songsFromDb
//                else -> songsFromDb.filter { songEntity ->
//                    File(songEntity.filePath).parent?.let { parentDir ->
//                        allowedDirs.contains(parentDir)
//                    } ?: false
//                }
//            }
//        }
//    }
//
//    /**
//     * Escanea MediaStore para encontrar todos los directorios únicos que contienen archivos de audio.
//     * Si es la primera vez que se ejecuta, guarda todos los directorios encontrados como permitidos.
//     */
//    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
//        directoryScanMutex.withLock {
//            val directories = mutableSetOf<String>()
//            val projection = arrayOf(MediaStore.Audio.Media.DATA)
//            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
//            context.contentResolver.query(
//                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
//                projection, selection, null, null
//            )?.use { c ->
//                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
//                while (c.moveToNext()) {
//                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
//                }
//            }
//            val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
//            if (!initialSetupDone && directories.isNotEmpty()) {
//                Log.i("MusicRepo", "Initial setup: saving all found audio directories (${directories.size}) as allowed.")
//                runBlocking { userPreferencesRepository.updateAllowedDirectories(directories) }
//            }
//            cachedAudioDirectories = directories
//            return@withLock directories
//        }
//    }
//
//    /**
//     * Obtiene una lista de todas las carátulas de álbumes únicos de las canciones permitidas.
//     */
//    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
//        return getPermittedSongsFlow().map { permittedSongs ->
//            permittedSongs
//                .mapNotNull { it.albumArtUriString?.toUri() }
//                .distinct()
//        }
//    }
//
//    // --- Métodos de Búsqueda ---
//
//    override fun searchSongs(query: String): Flow<List<Song>> {
//        if (query.isBlank()) return flowOf(emptyList())
//
//        val searchedSongsFlow = musicDao.searchSongs(query)
//        val allowedDirectoriesFlow = userPreferencesRepository.allowedDirectoriesFlow
//        val initialSetupDoneFlow = userPreferencesRepository.initialSetupDoneFlow
//
//        return combine(
//            searchedSongsFlow,
//            allowedDirectoriesFlow,
//            initialSetupDoneFlow
//        ) { songsFromDb, allowedDirs, initialSetupDone ->
//            // La lógica de tu lambda de transformación
//            when {
//                initialSetupDone && allowedDirs.isEmpty() -> emptyList<SongEntity>() // Especificar tipo para lista vacía
//                !initialSetupDone -> songsFromDb
//                else -> songsFromDb.filter { songEntity ->
//                    File(songEntity.filePath).parent?.let { parentDir ->
//                        allowedDirs.contains(parentDir)
//                    } ?: false
//                }
//            }
//        }.map { entities -> entities.map { it.toSong() } }
//    }
//
//    override fun searchAlbums(query: String): Flow<List<Album>> {
//        if (query.isBlank()) return flowOf(emptyList())
//
//        return getPermittedSongsFlow().combine(musicDao.searchAlbums(query)) { permittedSongs, searchedAlbumsFromDb ->
//            val permittedAlbumIds = permittedSongs.map { it.albumId }.toSet()
//            val songCountByAlbumId = permittedSongs.groupBy { it.albumId }.mapValues { it.value.size }
//
//            searchedAlbumsFromDb.filter { albumEntity ->
//                albumEntity.id in permittedAlbumIds
//            }.mapNotNull { albumEntity ->
//                val currentSongCount = songCountByAlbumId[albumEntity.id] ?: 0
//                if (currentSongCount > 0) albumEntity.toAlbum().copy(songCount = currentSongCount) else null
//            }
//        }
//    }
//
//    override fun searchArtists(query: String): Flow<List<Artist>> {
//        if (query.isBlank()) return flowOf(emptyList())
//
//        return getPermittedSongsFlow().combine(musicDao.searchArtists(query)) { permittedSongs, searchedArtistsFromDb ->
//            val permittedArtistIds = permittedSongs.map { it.artistId }.toSet()
//            val trackCountByArtistId = permittedSongs.groupBy { it.artistId }.mapValues { it.value.size }
//
//            searchedArtistsFromDb.filter { artistEntity ->
//                artistEntity.id in permittedArtistIds
//            }.mapNotNull { artistEntity ->
//                val currentTrackCount = trackCountByArtistId[artistEntity.id] ?: 0
//                if (currentTrackCount > 0) artistEntity.toArtist().copy(songCount = currentTrackCount) else null
//            }
//        }
//    }
//
//    override suspend fun searchPlaylists(query: String): List<Playlist> {
//        if (query.isBlank()) return emptyList()
//        // Placeholder: La implementación real depende de cómo se almacenen las playlists.
//        Log.d("MusicRepositoryImpl", "searchPlaylists called with query: $query. Not implemented.")
//        return emptyList()
//    }
//
//    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
//        if (query.isBlank()) return flowOf(emptyList())
//
//        val playlistsFlow = flow { emit(searchPlaylists(query)) }
//
//        return when (filterType) {
//            SearchFilterType.ALL -> {
//                combine(
//                    searchSongs(query),
//                    searchAlbums(query),
//                    searchArtists(query),
//                    playlistsFlow
//                ) { songs, albums, artists, playlists ->
//                    mutableListOf<SearchResultItem>().apply {
//                        songs.forEach { add(SearchResultItem.SongItem(it)) }
//                        albums.forEach { add(SearchResultItem.AlbumItem(it)) }
//                        artists.forEach { add(SearchResultItem.ArtistItem(it)) }
//                        playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
//                    }
//                }
//            }
//            SearchFilterType.SONGS -> searchSongs(query).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
//            SearchFilterType.ALBUMS -> searchAlbums(query).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
//            SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
//            SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
//        }
//    }
//
//    // --- Historial de Búsqueda ---
//
//    override suspend fun addSearchHistoryItem(query: String) {
//        withContext(Dispatchers.IO) {
//            searchHistoryDao.deleteByQuery(query)
//            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
//        }
//    }
//
//    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
//        return withContext(Dispatchers.IO) {
//            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
//        }
//    }
//
//    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
//        withContext(Dispatchers.IO) {
//            searchHistoryDao.deleteByQuery(query)
//        }
//    }
//
//    override suspend fun clearSearchHistory() {
//        withContext(Dispatchers.IO) {
//            searchHistoryDao.clearAll()
//        }
//    }
//
//    // --- Métodos de obtención por ID ---
//
//    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
//        Log.i("MusicRepositoryImpl", "getMusicByGenre called for genreId: \"$genreId\"")
//
//        // Encuentra el nombre real del género a partir del ID, usando tu GenreDataSource
//        // Aquí asumo que GenreDataSource.staticGenres es una lista de objetos con propiedades 'id' y 'name'
//        val staticGenre = GenreDataSource.staticGenres.find { it.id.equals(genreId, ignoreCase = true) }
//        val targetGenreName = staticGenre?.name ?: genreId // Usa el nombre encontrado o el ID como fallback
//
//        if (targetGenreName.isBlank()) {
//            Log.w("MusicRepositoryImpl", "Target genre name is blank for genreId: \"$genreId\".")
//            return flowOf(emptyList())
//        }
//
//        val songsByGenreFlow = musicDao.getSongsByGenre(targetGenreName)
//        val allowedDirectoriesFlow = userPreferencesRepository.allowedDirectoriesFlow
//        val initialSetupDoneFlow = userPreferencesRepository.initialSetupDoneFlow
//
//        return combine(
//            songsByGenreFlow,
//            allowedDirectoriesFlow,
//            initialSetupDoneFlow
//        ) { songsFromDb, allowedDirs, initialSetupDone ->
//            when {
//                initialSetupDone && allowedDirs.isEmpty() -> emptyList<SongEntity>() // Especifica el tipo aquí
//                !initialSetupDone -> songsFromDb
//                else -> songsFromDb.filter { songEntity ->
//                    File(songEntity.filePath).parent?.let { parentDir ->
//                        allowedDirs.contains(parentDir)
//                    } ?: false
//                }
//            }
//        }.map { entities -> entities.map { it.toSong() } }
//    }
//
//    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
//        val songsByArtistFlow = musicDao.getSongsByArtistId(artistId)
//        val allowedDirectoriesFlow = userPreferencesRepository.allowedDirectoriesFlow
//        val initialSetupDoneFlow = userPreferencesRepository.initialSetupDoneFlow
//
//        return combine(
//            songsByArtistFlow,
//            allowedDirectoriesFlow,
//            initialSetupDoneFlow
//        ) { songsFromDb, allowedDirs, initialSetupDone ->
//            when {
//                initialSetupDone && allowedDirs.isEmpty() -> emptyList<SongEntity>() // Especifica el tipo aquí
//                !initialSetupDone -> songsFromDb
//                else -> songsFromDb.filter { songEntity ->
//                    File(songEntity.filePath).parent?.let { parentDir -> // Renombré 'it' a 'parentDir' para claridad
//                        allowedDirs.contains(parentDir)
//                    } ?: false
//                }
//            }
//        }.map { entities -> entities.map { it.toSong() } }
//    }
//
//    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
//        if (songIds.isEmpty()) return flowOf(emptyList())
//        val longIds = songIds.mapNotNull { it.toLongOrNull() }
//        if (longIds.isEmpty()) return flowOf(emptyList())
//
//        // Este método asume que los IDs ya provienen de una fuente filtrada, por lo que no
//        // se aplica el filtro de directorios aquí. Si fuera necesario, se añadiría el .combine().
//        return musicDao.getSongsByIds(longIds)
//            .map { entities ->
//                val songsMap = entities.associateBy { it.id.toString() }
//                // Preserva el orden original de los IDs proporcionados
//                songIds.mapNotNull { songsMap[it] }.map { it.toSong() }
//            }
//    }
//
//    // --- Funciones de invalidación de caché (re-evaluar su necesidad) ---
//
//    private fun invalidatePermittedSongReferencesCache() { // TODO: Eliminar esta función
//        Log.d("MusicRepo", "invalidatePermittedSongReferencesCache called, but cache no longer exists.")
//    }
//
//    override suspend fun invalidateCachesDependentOnAllowedDirectories() { // TODO: Re-evaluar o eliminar.
//        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Flows should update reactively.")
//    }
//}