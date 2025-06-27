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
import kotlinx.coroutines.flow.flowOn
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
        }.flowOn(Dispatchers.Default) // Use Dispatchers.Default for CPU-intensive operations
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
        }.flowOn(Dispatchers.Default)
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
        }.flowOn(Dispatchers.Default)
    }


    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .filter { it.albumId == albumId }
                .map { it.toSong() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .filter { it.artistId == artistId }
                .map { it.toSong() }
        }.flowOn(Dispatchers.Default)
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
        }.flowOn(Dispatchers.Default)
    }

    // --- Métodos de Búsqueda ---

    override fun searchSongs(query: String): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        // La búsqueda se hace sobre las canciones permitidas
        return getPermittedSongsFlow().map { permittedSongs ->
            permittedSongs
                .filter { it.title.contains(query, ignoreCase = true) || it.artistName.contains(query, ignoreCase = true) }
                .map { it.toSong() }
        }.flowOn(Dispatchers.Default)
    }

    override fun searchAlbums(query: String): Flow<List<Album>> {
        if (query.isBlank()) return flowOf(emptyList())
        // La búsqueda se hace sobre los álbumes derivados de las canciones permitidas
        return getAlbums(1, Int.MAX_VALUE).map { allPermittedAlbums ->
            allPermittedAlbums.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
        }.flowOn(Dispatchers.Default)
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        // La búsqueda se hace sobre los artistas derivados de las canciones permitidas
        return getArtists(1, Int.MAX_VALUE).map { allPermittedArtists ->
            allPermittedArtists.filter { it.name.contains(query, ignoreCase = true) }
        }.flowOn(Dispatchers.Default)
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
        }.flowOn(Dispatchers.Default)
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
        }.flowOn(Dispatchers.Default)
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        val longIds = songIds.mapNotNull { it.toLongOrNull() }
        if (longIds.isEmpty()) return flowOf(emptyList())

        return musicDao.getSongsByIds(longIds)
            .map { entities ->
                val songsMap = entities.associateBy { it.id.toString() }
                songIds.mapNotNull { songsMap[it] }.map { it.toSong() }
            }.flowOn(Dispatchers.Default)
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    suspend fun syncMusicFromContentResolver() {
        // Esta función ahora está en SyncWorker. Se deja el esqueleto por si se llama desde otro lugar.
        Log.w("MusicRepo", "syncMusicFromContentResolver was called directly on repository. This should be handled by SyncWorker.")
    }
}