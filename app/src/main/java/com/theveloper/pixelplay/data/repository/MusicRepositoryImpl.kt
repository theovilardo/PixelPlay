package com.theveloper.pixelplay.data.repository

// import kotlinx.coroutines.withContext // May not be needed for Flow transformations

// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.repository.SongRepository
// import kotlinx.coroutines.withContext // May not be needed for Flow transformations
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SearchHistoryEntity
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.database.TelegramChannelEntity
import com.theveloper.pixelplay.data.database.TelegramDao
import com.theveloper.pixelplay.data.database.toAlbum
import com.theveloper.pixelplay.data.database.toArtist
import com.theveloper.pixelplay.data.database.toSearchHistoryItem
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.database.toSongWithArtistRefs
import com.theveloper.pixelplay.data.database.toTelegramEntity
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.DirectoryRuleResolver
import com.theveloper.pixelplay.utils.LogUtils

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.filter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val musicDao: MusicDao,
    private val lyricsRepository: LyricsRepository,
    private val telegramDao: TelegramDao,
    private val telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager,
    private val telegramRepository: com.theveloper.pixelplay.data.telegram.TelegramRepository,
    private val songRepository: SongRepository,
    private val favoritesDao: FavoritesDao
) : MusicRepository {

    private val directoryScanMutex = Mutex()

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }

    private val allArtistsFlow: Flow<List<ArtistEntity>> = musicDao.getAllArtistsRaw()

    private val allCrossRefsFlow: Flow<List<SongArtistCrossRef>> = musicDao.getAllSongArtistCrossRefs()

    private val directoryFilterConfig: Flow<DirectoryRuleResolver?> = combine(
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow,
        userPreferencesRepository.isFolderFilterActiveFlow
    ) { allowed, blocked, active ->
        if (active) {
            DirectoryRuleResolver(
                allowed.map(::normalizePath).toSet(),
                blocked.map(::normalizePath).toSet()
            )
        } else {
            null
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAudioFiles(): Flow<List<Song>> {
        return combine(
            songRepository.getSongs(),
            telegramDao.getAllTelegramSongs(),
            telegramDao.getAllChannels()
        ) { localSongs, telegramSongs, channels ->
            val channelMap = channels.associateBy { it.chatId }
            val mappedTelegramSongs = telegramSongs.map { entity ->
                entity.toSong(channelTitle = channelMap[entity.chatId]?.title)
            }
            localSongs + mappedTelegramSongs
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(): Flow<PagingData<Song>> {
       // Delegate to reactive repository for correct filtering and paging
       return songRepository.getPaginatedSongs()
    }

    override fun getSongCountFlow(): Flow<Int> {
        return musicDao.getSongCount()
    }

    override suspend fun getRandomSongs(limit: Int): List<Song> = withContext(Dispatchers.IO) {
        musicDao.getRandomSongs(limit).map { it.toSong() }
    }

    override suspend fun saveTelegramSongs(songs: List<Song>) {
         val entities = songs.mapNotNull { it.toTelegramEntity() }
         if (entities.isNotEmpty()) {
             telegramDao.insertSongs(entities)
             // Trigger sync to update main DB
             androidx.work.WorkManager.getInstance(context).enqueue(
                 com.theveloper.pixelplay.data.worker.SyncWorker.incrementalSyncWork()
             )
         }
    }

    override fun getAlbums(): Flow<List<Album>> {
        return getAudioFiles().map { songs ->
            songs.groupBy { it.albumId }
                .map { (albumId, songs) ->
                    val first = songs.first()
                    Album(
                        id = albumId,
                        title = first.album,
                        artist = first.artist, // Or albumArtist if available
                        albumArtUriString = first.albumArtUriString,
                        songCount = songs.size,
                        year = first.year
                    )
                }
                .sortedBy { it.title.lowercase() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getAlbumById(id: Long): Flow<Album?> {
        return getAlbums().map { albums -> 
            albums.find { it.id == id }
        }
    }

    override fun getArtists(): Flow<List<Artist>> {
        return getAudioFiles().map { songs ->
            songs.groupBy { it.artistId }
                .map { (artistId, songs) ->
                    val first = songs.first()
                    Artist(
                        id = artistId,
                        name = first.artist,
                        songCount = songs.size
                    )
                }
                .sortedBy { it.name.lowercase() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        return getAudioFiles().map { songs ->
            songs.filter { it.albumId == albumId }
                .sortedBy { it.trackNumber }
        }
    }

    override fun getArtistById(artistId: Long): Flow<Artist?> {
         return getArtists().map { artists ->
             artists.find { it.id == artistId }
         }
    }

    override fun getArtistsForSong(songId: Long): Flow<List<Artist>> {
        // Simple implementation assuming single artist per song as per MediaStore
        // For multi-artist, we would parse the separator/delimiter here.
        return getAudioFiles().map { songs ->
            val song = songs.find { it.id == songId.toString() }
            if (song != null) {
                listOf(Artist(id = song.artistId, name = song.artist, songCount = 1, imageUrl = null))
            } else {
                emptyList()
            }
        }
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        return getAudioFiles().map { songs ->
            songs.filter { it.artistId == artistId }
                .sortedBy { it.title }
        }
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        LogUtils.d(this, "getAllUniqueAudioDirectories")
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
            )?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
                }
            }
            LogUtils.i(this, "Found ${directories.size} unique audio directories")
            return@withLock directories
        }
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return musicDao.getAllUniqueAlbumArtUrisFromSongs().map { uriStrings ->
            uriStrings.mapNotNull { it.toUri() }
        }.flowOn(Dispatchers.IO)
    }

    // --- Métodos de Búsqueda ---

    override fun searchSongs(query: String): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        
        val localSearchFlow = combine(
            musicDao.searchSongs(
                query = query,
                allowedParentDirs = emptyList(),
                applyDirectoryFilter = false
            ),
            directoryFilterConfig,
            allArtistsFlow,
            allCrossRefsFlow
        ) { songs, config, artists, crossRefs ->
            mapSongList(songs, config, artists, crossRefs)
        }

        val telegramSearchFlow = combine(
            telegramDao.searchSongs(query),
            telegramDao.getAllChannels()
        ) { songs, channels ->
            val channelMap = channels.associateBy { it.chatId }
            songs.map { it.toSong(channelTitle = channelMap[it.chatId]?.title) }
        }

        return combine(localSearchFlow, telegramSearchFlow) { local, telegram ->
            local + telegram
        }.conflate().flowOn(Dispatchers.IO)
    }


    override fun searchAlbums(query: String): Flow<List<Album>> {
       if (query.isBlank()) return flowOf(emptyList())
       return musicDao.searchAlbums(query, emptyList(), false).map { entities ->
           entities.map { it.toAlbum() }
       }.flowOn(Dispatchers.IO)
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        return musicDao.searchArtists(query, emptyList(), false).map { entities ->
            entities.map { it.toArtist() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()
        return userPreferencesRepository.userPlaylistsFlow.first()
            .filter { playlist ->
                playlist.name.contains(query, ignoreCase = true)
            }
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
        return userPreferencesRepository.mockGenresEnabledFlow.flatMapLatest { mockEnabled ->
            if (mockEnabled) {
                // Mock mode: Use the static genre name for filtering.
                val genreName = "Mock"//GenreDataSource.getStaticGenres().find { it.id.equals(genreId, ignoreCase = true) }?.name ?: genreId
                getAudioFiles().map { songs ->
                    songs.filter { it.genre.equals(genreName, ignoreCase = true) }
                }
            } else {
                // Real mode: Use the genreId directly, which corresponds to the actual genre name from metadata.
                getAudioFiles().map { songs ->
                    if (genreId.equals("unknown", ignoreCase = true)) {
                        // Filter for songs with no genre or an empty genre string.
                        songs.filter { it.genre.isNullOrBlank() }
                    } else {
                        // Filter for songs that match the given genre name.
                        songs.filter { it.genre.equals(genreId, ignoreCase = true) }
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        
        val localIds = songIds.mapNotNull { it.toLongOrNull() }
        val telegramIds = songIds.filter { it.toLongOrNull() == null }

        val localSongsFlow = if (localIds.isNotEmpty()) {
            combine(
                musicDao.getSongsByIds(
                    songIds = localIds,
                    allowedParentDirs = emptyList(),
                    applyDirectoryFilter = false
                ),
                directoryFilterConfig,
                allArtistsFlow,
                allCrossRefsFlow
            ) { entities: List<SongEntity>, config: DirectoryRuleResolver?, artists: List<ArtistEntity>, crossRefs: List<SongArtistCrossRef> ->
                val permittedEntities = entities.filterBlocked(config)
                mapSongList(permittedEntities, null, artists, crossRefs)
            }
        } else {
            flowOf(emptyList<Song>())
        }

        val telegramSongsFlow = if (telegramIds.isNotEmpty()) {
             combine(
                 telegramDao.getSongsByIds(telegramIds),
                 telegramDao.getAllChannels()
             ) { songs, channels ->
                 val channelMap = channels.associateBy { it.chatId }
                 songs.map { 
                     val channelTitle = channelMap[it.chatId]?.title
                     it.toSong(channelTitle = channelTitle) 
                 }
             }
        } else {
             flowOf(emptyList<Song>())
        }

        return combine(localSongsFlow, telegramSongsFlow) { local: List<Song>, telegram: List<Song> ->
            val allSongsMap = (local + telegram).associateBy { it.id }
            // Preserve original order of songIds
            songIds.mapNotNull { id -> allSongsMap[id] }
        }.conflate().flowOn(Dispatchers.IO)
    }

    override suspend fun getSongByPath(path: String): Song? {
        return withContext(Dispatchers.IO) {
            musicDao.getSongByPath(path)?.toSong()
        }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    suspend fun syncMusicFromContentResolver() {
        // Esta función ahora está en SyncWorker. Se deja el esqueleto por si se llama desde otro lugar.
        Log.w("MusicRepo", "syncMusicFromContentResolver was called directly on repository. This should be handled by SyncWorker.")
    }

    // Implementación de las nuevas funciones suspend para carga única
    override suspend fun getAllSongsOnce(): List<Song> = withContext(Dispatchers.IO) {
        musicDao.getAllSongsList().map { it.toSong() }
    }

    override suspend fun getAllAlbumsOnce(): List<Album> = withContext(Dispatchers.IO) {
        musicDao.getAllAlbumsList(emptyList(), false).map { it.toAlbum() }
    }

    override suspend fun getAllArtistsOnce(): List<Artist> = withContext(Dispatchers.IO) {
        musicDao.getAllArtistsListRaw().map { it.toArtist() }
    }

    override suspend fun toggleFavoriteStatus(songId: String): Boolean = withContext(Dispatchers.IO) {
        val id = songId.toLongOrNull() ?: return@withContext false
        val isFav = favoritesDao.isFavorite(id) ?: false
        val newFav = !isFav
        if (newFav) {
            favoritesDao.setFavorite(com.theveloper.pixelplay.data.database.FavoritesEntity(id, true))
        } else {
            favoritesDao.removeFavorite(id)
        }
        return@withContext newFav
    }

    override fun getSong(songId: String): Flow<Song?> {
        val longId = songId.toLongOrNull()
        return if (longId != null) {
            musicDao.getSongById(longId).map { it?.toSong() }.flowOn(Dispatchers.IO)
        } else {
            combine(
                telegramDao.getSongsByIds(listOf(songId)),
                telegramDao.getAllChannels()
            ) { songs, channels ->
                val channelMap = channels.associateBy { it.chatId }
                songs.firstOrNull()?.let { 
                    it.toSong(channelTitle = channelMap[it.chatId]?.title)
                }
            }.flowOn(Dispatchers.IO)
        }
    }

    override fun getGenres(): Flow<List<Genre>> {
        return getAudioFiles().map { songs ->
            val genresMap = songs.groupBy { song ->
                song.genre?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"
            }

            val dynamicGenres = genresMap.keys.mapNotNull { genreName ->
                val id = if (genreName.equals("Unknown", ignoreCase = true)) "unknown" else genreName.lowercase().replace(" ", "_")
                // Generate colors dynamically or use a default for "Unknown"
                val colorInt = genreName.hashCode()
                val lightColorHex = "#${(colorInt and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"
                // Simple inversion for dark color, or use a predefined set
                val darkColorHex = "#${((colorInt xor 0xFFFFFF) and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"

                Genre(
                    id = id,
                    name = genreName,
                    lightColorHex = lightColorHex,
                    onLightColorHex = "#000000", // Default black for light theme text
                    darkColorHex = darkColorHex,
                    onDarkColorHex = "#FFFFFF"  // Default white for dark theme text
                )
            }.sortedBy { it.name.lowercase() }

            // Ensure "Unknown" genre is last if it exists.
            val unknownGenre = dynamicGenres.find { it.id == "unknown" }
            if (unknownGenre != null) {
                (dynamicGenres.filterNot { it.id == "unknown" } + unknownGenre)
            } else {
                dynamicGenres
            }
        }.conflate().flowOn(Dispatchers.IO)
    }

    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? {
        return lyricsRepository.getLyrics(song, sourcePreference, forceRefresh)
    }

    /**
     * Obtiene la letra de una canción desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Lyrics parseado.
     *
     * @param song La canción para la cual se buscará la letra.
     * @return Un objeto Result que contiene el objeto Lyrics si se encontró, o un error.
     */
    override suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>> {
        return lyricsRepository.fetchFromRemote(song)
    }

    override suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemote(song)
    }

    override suspend fun searchRemoteLyricsByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemoteByQuery(title, artist)
    }

    override suspend fun updateLyrics(songId: Long, lyrics: String) {
        lyricsRepository.updateLyrics(songId, lyrics)
    }

    override suspend fun resetLyrics(songId: Long) {
        lyricsRepository.resetLyrics(songId)
    }

    override suspend fun resetAllLyrics() {
        lyricsRepository.resetAllLyrics()
    }

    override fun getMusicFolders(): Flow<List<MusicFolder>> {
        return combine(
            getAudioFiles(),
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.isFolderFilterActiveFlow
        ) { songs, allowedDirs, blockedDirs, isFolderFilterActive ->
            val resolver = DirectoryRuleResolver(
                allowedDirs.map(::normalizePath).toSet(),
                blockedDirs.map(::normalizePath).toSet()
            )
            val songsToProcess = if (isFolderFilterActive && blockedDirs.isNotEmpty()) {
                songs.filter { song ->
                    val songDir = File(song.path).parentFile ?: return@filter false
                    val normalized = normalizePath(songDir.path)
                    !resolver.isBlocked(normalized)
                }
            } else {
                songs
            }

            if (songsToProcess.isEmpty()) return@combine emptyList()

            data class TempFolder(
                val path: String,
                val name: String,
                val songs: MutableList<Song> = mutableListOf(),
                val subFolderPaths: MutableSet<String> = mutableSetOf()
            )

            val tempFolders = mutableMapOf<String, TempFolder>()

            // Optimization: Group songs by parent folder first to reduce File object creations and loop iterations
            val songsByFolder = songsToProcess.groupBy { File(it.path).parent }

            songsByFolder.forEach { (folderPath, songsInFolder) ->
                if (folderPath != null) {
                    val folderFile = File(folderPath)
                    // Create or get the leaf folder
                    val leafFolder = tempFolders.getOrPut(folderPath) { TempFolder(folderPath, folderFile.name) }
                    leafFolder.songs.addAll(songsInFolder)

                    // Build hierarchy upwards
                    var currentPath: String = folderPath
                    var currentFile = folderFile

                    while (currentFile.parentFile != null) {
                        val parentFile = currentFile.parentFile!!
                        val parentPath = parentFile.path

                        val parentFolder = tempFolders.getOrPut(parentPath) { TempFolder(parentPath, parentFile.name) }
                        val added = parentFolder.subFolderPaths.add(currentPath)

                        if (!added) {
                            // If the link already existed, we have processed this branch up to the root already.
                            break
                        }

                        currentFile = parentFile
                        currentPath = parentPath
                    }
                }
            }

            fun buildImmutableFolder(path: String, visited: MutableSet<String>): MusicFolder? {
                if (path in visited) return null
                visited.add(path)
                val tempFolder = tempFolders[path] ?: return null
                val subFolders = tempFolder.subFolderPaths
                    .mapNotNull { subPath -> buildImmutableFolder(subPath, visited.toMutableSet()) }
                    .sortedBy { it.name.lowercase() }
                    .toImmutableList()
                return MusicFolder(
                    path = tempFolder.path,
                    name = tempFolder.name,
                    songs = tempFolder.songs
                        .sortedWith(
                            compareBy<Song> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy { it.title.lowercase() }
                        )
                        .toImmutableList(),
                    subFolders = subFolders
                )
            }

            val storageRootPath = Environment.getExternalStorageDirectory().path
            val rootTempFolder = tempFolders[storageRootPath]

            val result = rootTempFolder?.subFolderPaths?.mapNotNull { path ->
                buildImmutableFolder(path, mutableSetOf())
            }?.filter { it.totalSongCount > 0 }?.sortedBy { it.name.lowercase() } ?: emptyList()

            // Fallback for devices that might not use the standard storage root path
            if (result.isEmpty() && tempFolders.isNotEmpty()) {
                 val allSubFolderPaths = tempFolders.values.flatMap { it.subFolderPaths }.toSet()
                 val topLevelPaths = tempFolders.keys - allSubFolderPaths
                 return@combine topLevelPaths
                     .mapNotNull { buildImmutableFolder(it, mutableSetOf()) }
                     .filter { it.totalSongCount > 0 }
                    .sortedBy { it.name.lowercase() }
             }

            result
        }.conflate().flowOn(Dispatchers.IO)
    }

    private fun mapSongList(
        songs: List<SongEntity>,
        config: DirectoryRuleResolver?,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>
    ): List<Song> {
        val artistMap = artists.associateBy { it.id }
        val crossRefMap = crossRefs.groupBy { it.songId }

        return songs.map { songEntity ->
            val songCrossRefs = crossRefMap[songEntity.id] ?: emptyList()
            val songArtists = songCrossRefs.mapNotNull { artistMap[it.artistId] }
            songEntity.toSongWithArtistRefs(songArtists, songCrossRefs)
        }
    }

    private fun List<SongEntity>.filterBlocked(resolver: DirectoryRuleResolver?): List<SongEntity> {
        if (resolver == null) return this
        return this.filter { entity ->
            !resolver.isBlocked(entity.parentDirectoryPath)
        }
    }

    override suspend fun deleteById(id: Long) {
        musicDao.deleteById(id)
    }

    override suspend fun clearTelegramData() {
        telegramDao.clearAll()
        // Clear all Telegram caches (TDLib files, embedded art, memory)
        telegramRepository.clearMemoryCache()
        telegramCacheManager.clearAllCache()
    }

    override suspend fun saveTelegramChannel(channel: TelegramChannelEntity) {
        telegramDao.insertChannel(channel)
    }

    override fun getAllTelegramChannels(): Flow<List<TelegramChannelEntity>> {
        return telegramDao.getAllChannels()
    }

    override suspend fun deleteTelegramChannel(chatId: Long) {
        telegramDao.deleteSongsByChatId(chatId) // Cascade delete songs
        telegramDao.deleteChannel(chatId)
    }
}
