package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.ui.theme.GenreColors
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.model.toLibraryTabIdOrNull
import com.theveloper.pixelplay.utils.toHexString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * ViewModel responsible for library data management:
 * - Songs, Albums, Artists, Folders
 * - Sorting and filtering
 * - Library tab navigation
 * - Favorites
 *
 * This ViewModel is intentionally separated from PlayerViewModel to:
 * 1. Reduce cascading recompositions when library data changes
 * 2. Allow library screens to collect only the state they need
 * 3. Improve testability and maintainability
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val artistImageRepository: ArtistImageRepository,
    val syncManager: SyncManager
) : ViewModel() {

    // ============ LIBRARY DATA ============

    private val _allSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val allSongs: StateFlow<ImmutableList<Song>> = _allSongs.asStateFlow()

    private val _albums = MutableStateFlow<ImmutableList<Album>>(persistentListOf())
    val albums: StateFlow<ImmutableList<Album>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<ImmutableList<Artist>>(persistentListOf())
    val artists: StateFlow<ImmutableList<Artist>> = _artists.asStateFlow()

    private val _musicFolders = MutableStateFlow<ImmutableList<MusicFolder>>(persistentListOf())
    val musicFolders: StateFlow<ImmutableList<MusicFolder>> = _musicFolders.asStateFlow()

    // ============ LOADING STATES ============

    private val _isLoadingInitialSongs = MutableStateFlow(true)
    val isLoadingInitialSongs: StateFlow<Boolean> = _isLoadingInitialSongs.asStateFlow()

    private val _isLoadingLibraryCategories = MutableStateFlow(false)
    val isLoadingLibraryCategories: StateFlow<Boolean> = _isLoadingLibraryCategories.asStateFlow()

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ============ SORTING ============

    private val _currentSongSortOption = MutableStateFlow<SortOption>(SortOption.SongTitleAZ)
    val currentSongSortOption: StateFlow<SortOption> = _currentSongSortOption.asStateFlow()

    private val _currentAlbumSortOption = MutableStateFlow<SortOption>(SortOption.AlbumTitleAZ)
    val currentAlbumSortOption: StateFlow<SortOption> = _currentAlbumSortOption.asStateFlow()

    private val _currentArtistSortOption = MutableStateFlow<SortOption>(SortOption.ArtistNameAZ)
    val currentArtistSortOption: StateFlow<SortOption> = _currentArtistSortOption.asStateFlow()

    private val _currentFolderSortOption = MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
    val currentFolderSortOption: StateFlow<SortOption> = _currentFolderSortOption.asStateFlow()

    private val _currentFavoriteSortOption = MutableStateFlow<SortOption>(SortOption.LikedSongDateLiked)
    val currentFavoriteSortOption: StateFlow<SortOption> = _currentFavoriteSortOption.asStateFlow()

    private val _isSortingSheetVisible = MutableStateFlow(false)
    val isSortingSheetVisible: StateFlow<Boolean> = _isSortingSheetVisible.asStateFlow()

    // ============ LIBRARY TABS ============

    val lastLibraryTabIndex: StateFlow<Int> = userPreferencesRepository.lastLibraryTabIndexFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val libraryTabs: StateFlow<List<String>> = userPreferencesRepository.libraryTabsOrderFlow
        .map { orderJson ->
            if (orderJson != null) {
                try {
                    Json.decodeFromString<List<String>>(orderJson)
                } catch (e: Exception) {
                    defaultLibraryTabs
                }
            } else {
                defaultLibraryTabs
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), defaultLibraryTabs)

    private val _currentLibraryTabId = MutableStateFlow(LibraryTabId.SONGS)
    val currentLibraryTabId: StateFlow<LibraryTabId> = _currentLibraryTabId.asStateFlow()

    val availableSortOptions: StateFlow<List<SortOption>> = currentLibraryTabId.map { tabId ->
        when (tabId) {
            LibraryTabId.SONGS -> SortOption.SONGS
            LibraryTabId.ALBUMS -> SortOption.ALBUMS
            LibraryTabId.ARTISTS -> SortOption.ARTISTS
            LibraryTabId.PLAYLISTS -> SortOption.PLAYLISTS
            LibraryTabId.FOLDERS -> SortOption.FOLDERS
            LibraryTabId.LIKED -> SortOption.LIKED
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOption.SONGS)

    // ============ FAVORITES ============

    val favoriteSongIds: StateFlow<Set<String>> = userPreferencesRepository.favoriteSongIdsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val favoriteSongs: StateFlow<ImmutableList<Song>> = combine(
        favoriteSongIds,
        _allSongs,
        _currentFavoriteSortOption
    ) { ids, allSongsList, sortOption ->
        val favoriteSongsList = allSongsList.filter { song -> ids.contains(song.id) }
        when (sortOption) {
            SortOption.LikedSongTitleAZ -> favoriteSongsList.sortedBy { it.title.lowercase() }
            SortOption.LikedSongTitleZA -> favoriteSongsList.sortedByDescending { it.title.lowercase() }
            SortOption.LikedSongArtist -> favoriteSongsList.sortedBy { it.artist.lowercase() }
            SortOption.LikedSongAlbum -> favoriteSongsList.sortedBy { it.album.lowercase() }
            SortOption.LikedSongDateLiked -> favoriteSongsList.sortedByDescending { it.id }
            else -> favoriteSongsList
        }.toImmutableList()
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    // ============ GENRES ============

    val genres: StateFlow<ImmutableList<Genre>> = allSongs.map { songs ->
        val genreMap = mutableMapOf<String, MutableList<Song>>()
        val unknownGenreName = "Unknown Genre"

        songs.forEach { song ->
            val genreName = song.genre?.trim()
            if (genreName.isNullOrBlank()) {
                genreMap.getOrPut(unknownGenreName) { mutableListOf() }.add(song)
            } else {
                genreMap.getOrPut(genreName) { mutableListOf() }.add(song)
            }
        }

        genreMap.toList().mapIndexedNotNull { index, (genreName, genreSongs) ->
            if (genreSongs.isNotEmpty()) {
                val id = if (genreName.equals(unknownGenreName, ignoreCase = true)) {
                    "unknown"
                } else {
                    genreName.lowercase().replace(" ", "_").replace("/", "_")
                }
                val color = GenreColors.colors[index % GenreColors.colors.size]
                Genre(
                    id = id,
                    name = genreName,
                    lightColorHex = color.lightColor.toHexString(),
                    onLightColorHex = color.onLightColor.toHexString(),
                    darkColorHex = color.darkColor.toHexString(),
                    onDarkColorHex = color.onDarkColor.toHexString()
                )
            } else {
                null
            }
        }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
            .toImmutableList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), persistentListOf())

    // ============ FOLDER NAVIGATION ============

    private val _currentFolderPath = MutableStateFlow<String?>(null)
    val currentFolderPath: StateFlow<String?> = _currentFolderPath.asStateFlow()

    private val _currentFolder = MutableStateFlow<MusicFolder?>(null)
    val currentFolder: StateFlow<MusicFolder?> = _currentFolder.asStateFlow()

    private val _isFoldersPlaylistView = MutableStateFlow(false)
    val isFoldersPlaylistView: StateFlow<Boolean> = _isFoldersPlaylistView.asStateFlow()

    private val _isFolderFilterActive = MutableStateFlow(false)
    val isFolderFilterActive: StateFlow<Boolean> = _isFolderFilterActive.asStateFlow()

    // Track blocked directories to detect changes
    private var lastBlockedDirectories: Set<String>? = null
    private var isInitialDataLoaded = false

    init {
        Log.i("LibraryViewModel", "init started")

        // Migrate tab order (one-time migration)
        viewModelScope.launch {
            userPreferencesRepository.migrateTabOrder()
        }

        // Ensure sort defaults
        viewModelScope.launch {
            userPreferencesRepository.ensureLibrarySortDefaults()
        }

        // Observe folders playlist view preference
        viewModelScope.launch {
            userPreferencesRepository.isFoldersPlaylistViewFlow.collect { isPlaylistView ->
                _isFoldersPlaylistView.value = isPlaylistView
            }
        }

        // Observe blocked directories changes
        viewModelScope.launch {
            userPreferencesRepository.blockedDirectoriesFlow
                .distinctUntilChanged()
                .collect { blocked ->
                    if (lastBlockedDirectories == null) {
                        lastBlockedDirectories = blocked
                        return@collect
                    }
                    if (blocked != lastBlockedDirectories) {
                        lastBlockedDirectories = blocked
                        onBlockedDirectoriesChanged()
                    }
                }
        }

        // Observe tab changes
        viewModelScope.launch {
            combine(libraryTabs, lastLibraryTabIndex) { tabs, index ->
                tabs.getOrNull(index)?.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
            }.collect { tabId ->
                _currentLibraryTabId.value = tabId
            }
        }

        // Load initial sort options
        viewModelScope.launch {
            val initialSongSort = resolveSortOption(
                userPreferencesRepository.songsSortOptionFlow.first(),
                SortOption.SONGS,
                SortOption.SongTitleAZ
            )
            val initialAlbumSort = resolveSortOption(
                userPreferencesRepository.albumsSortOptionFlow.first(),
                SortOption.ALBUMS,
                SortOption.AlbumTitleAZ
            )
            val initialArtistSort = resolveSortOption(
                userPreferencesRepository.artistsSortOptionFlow.first(),
                SortOption.ARTISTS,
                SortOption.ArtistNameAZ
            )
            val initialLikedSort = resolveSortOption(
                userPreferencesRepository.likedSongsSortOptionFlow.first(),
                SortOption.LIKED,
                SortOption.LikedSongDateLiked
            )

            _currentSongSortOption.value = initialSongSort
            _currentAlbumSortOption.value = initialAlbumSort
            _currentArtistSortOption.value = initialArtistSort
            _currentFavoriteSortOption.value = initialLikedSort
        }

        // Observe sync state and reload data when sync completes
        viewModelScope.launch {
            isSyncing.collect { syncing ->
                if (!syncing && !isInitialDataLoaded) {
                    Log.i("LibraryViewModel", "Initial sync complete, loading data")
                    loadInitialData()
                }
            }
        }

        // Initial data load if not syncing
        viewModelScope.launch {
            if (!isSyncing.value && !isInitialDataLoaded && _allSongs.value.isEmpty()) {
                Log.i("LibraryViewModel", "Not syncing, loading initial data")
                loadInitialData()
            }
        }
    }

    // ============ DATA LOADING ============

    private suspend fun loadInitialData() {
        _isLoadingInitialSongs.value = true
        _isLoadingLibraryCategories.value = true

        try {
            // Load songs first (using Flow.first() like PlayerViewModel)
            val songs = musicRepository.getAudioFiles().first().toImmutableList()
            _allSongs.value = songs
            sortSongsInternal(_currentSongSortOption.value)
            _isLoadingInitialSongs.value = false

            // Load albums (using suspend function)
            val albumsList = musicRepository.getAllAlbumsOnce().toImmutableList()
            _albums.value = albumsList
            sortAlbumsInternal(_currentAlbumSortOption.value)

            // Load artists (using suspend function)
            val artistsList = musicRepository.getAllArtistsOnce().toImmutableList()
            _artists.value = artistsList
            sortArtistsInternal(_currentArtistSortOption.value)
            
            // Fetch missing artist images
            fetchMissingArtistImages(artistsList)

            // Load folders (using Flow.first())
            val folders = musicRepository.getMusicFolders().first().toImmutableList()
            _musicFolders.value = folders

            isInitialDataLoaded = true
            _isLoadingLibraryCategories.value = false
            Log.i("LibraryViewModel", "Initial data loaded: ${songs.size} songs, ${albumsList.size} albums, ${artistsList.size} artists")
        } catch (e: Exception) {
            Log.e("LibraryViewModel", "Error loading initial data", e)
            _isLoadingInitialSongs.value = false
            _isLoadingLibraryCategories.value = false
        }
    }

    fun reloadAllData() {
        viewModelScope.launch {
            isInitialDataLoaded = false
            loadInitialData()
        }
    }

    private fun onBlockedDirectoriesChanged() {
        viewModelScope.launch {
            // Reload folders
            val folders = musicRepository.getMusicFolders().first().toImmutableList()
            _musicFolders.value = folders
            // Reload songs as blocked directories affect visible songs
            val songs = musicRepository.getAudioFiles().first().toImmutableList()
            _allSongs.value = songs
            sortSongsInternal(_currentSongSortOption.value)
        }
    }

    // ============ SORTING (Internal) ============

    private fun sortSongsInternal(sortOption: SortOption) {
        val sorted = when (sortOption) {
            SortOption.SongTitleAZ -> _allSongs.value.sortedBy { it.title.lowercase() }
            SortOption.SongTitleZA -> _allSongs.value.sortedByDescending { it.title.lowercase() }
            SortOption.SongArtist -> _allSongs.value.sortedBy { it.artist.lowercase() }
            SortOption.SongAlbum -> _allSongs.value.sortedBy { it.album.lowercase() }
            SortOption.SongDateAdded -> _allSongs.value.sortedByDescending { it.dateAdded }
            SortOption.SongDuration -> _allSongs.value.sortedBy { it.duration }
            else -> _allSongs.value
        }
        _allSongs.value = sorted.toImmutableList()
        _currentSongSortOption.value = sortOption
    }

    private fun sortAlbumsInternal(sortOption: SortOption) {
        val sorted = when (sortOption) {
            SortOption.AlbumTitleAZ -> _albums.value.sortedBy { it.title.lowercase() }
            SortOption.AlbumTitleZA -> _albums.value.sortedByDescending { it.title.lowercase() }
            SortOption.AlbumArtist -> _albums.value.sortedBy { it.artist.lowercase() }
            SortOption.AlbumReleaseYear -> _albums.value.sortedByDescending { it.year }
            SortOption.AlbumSizeAsc -> _albums.value.sortedWith(compareBy<Album> { it.songCount }.thenBy { it.title.lowercase() })
            SortOption.AlbumSizeDesc -> _albums.value.sortedWith(compareByDescending<Album> { it.songCount }.thenBy { it.title.lowercase() })
            else -> _albums.value
        }
        _albums.value = sorted.toImmutableList()
        _currentAlbumSortOption.value = sortOption
    }

    private fun sortArtistsInternal(sortOption: SortOption) {
        val sorted = when (sortOption) {
            SortOption.ArtistNameAZ -> _artists.value.sortedBy { it.name.lowercase() }
            SortOption.ArtistNameZA -> _artists.value.sortedByDescending { it.name.lowercase() }
            else -> _artists.value
        }
        _artists.value = sorted.toImmutableList()
        _currentArtistSortOption.value = sortOption
    }

    // ============ SORTING (Public API) ============

    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        sortSongsInternal(sortOption)
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setSongsSortOption(sortOption.storageKey)
            }
        }
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        sortAlbumsInternal(sortOption)
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setAlbumsSortOption(sortOption.storageKey)
            }
        }
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        sortArtistsInternal(sortOption)
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setArtistsSortOption(sortOption.storageKey)
            }
        }
    }

    fun sortFolders(sortOption: SortOption) {
        _currentFolderSortOption.value = sortOption
        val sorted = when (sortOption) {
            SortOption.FolderNameAZ -> _musicFolders.value.sortedBy { it.name.lowercase() }
            SortOption.FolderNameZA -> _musicFolders.value.sortedByDescending { it.name.lowercase() }
            else -> _musicFolders.value
        }
        _musicFolders.value = sorted.toImmutableList()
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        _currentFavoriteSortOption.value = sortOption
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setLikedSongsSortOption(sortOption.storageKey)
            }
        }
    }

    // ============ SORTING SHEET ============

    fun showSortingSheet() {
        _isSortingSheetVisible.value = true
    }

    fun hideSortingSheet() {
        _isSortingSheetVisible.value = false
    }

    // ============ LIBRARY TAB NAVIGATION ============

    fun saveLastLibraryTabIndex(tabIndex: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveLastLibraryTabIndex(tabIndex)
        }
    }

    fun onLibraryTabSelected(tabIndex: Int) {
        val tabId = libraryTabs.value.getOrNull(tabIndex)?.toLibraryTabIdOrNull() ?: return
        _currentLibraryTabId.value = tabId
        saveLastLibraryTabIndex(tabIndex)
    }

    fun saveLibraryTabsOrder(tabs: List<String>) {
        viewModelScope.launch {
            val orderJson = Json.encodeToString(tabs)
            userPreferencesRepository.saveLibraryTabsOrder(orderJson)
        }
    }

    fun resetLibraryTabsOrder() {
        viewModelScope.launch {
            userPreferencesRepository.resetLibraryTabsOrder()
        }
    }

    // ============ FOLDER NAVIGATION ============

    fun navigateToFolder(path: String) {
        val folder = findFolder(path, _musicFolders.value)
        if (folder != null) {
            _currentFolderPath.value = path
            _currentFolder.value = folder
        }
    }

    fun navigateBackFolder() {
        val current = _currentFolderPath.value
        if (current == null) {
            return
        }
        val parentPath = current.substringBeforeLast("/", "")
        if (parentPath.isEmpty()) {
            _currentFolderPath.value = null
            _currentFolder.value = null
        } else {
            navigateToFolder(parentPath)
        }
    }

    private fun findFolder(path: String?, folders: List<MusicFolder>): MusicFolder? {
        if (path == null) return null
        for (folder in folders) {
            if (folder.path == path) return folder
            val found = findFolder(path, folder.subFolders)
            if (found != null) return found
        }
        return null
    }

    fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFoldersPlaylistView(isPlaylistView)
        }
    }

    fun toggleFolderFilter() {
        _isFolderFilterActive.update { !it }
    }

    // ============ FAVORITES ============

    fun toggleFavorite(songId: String) {
        viewModelScope.launch {
            userPreferencesRepository.toggleFavoriteSong(songId)
        }
    }

    // ============ UTILITY ============

    private fun resolveSortOption(
        optionKey: String?,
        allowed: Collection<SortOption>,
        fallback: SortOption
    ): SortOption {
        return SortOption.fromStorageKey(optionKey, allowed, fallback)
    }

    fun getSongsForGenre(genreId: String): List<Song> {
        val genreName = genres.value.firstOrNull { it.id == genreId }?.name ?: return emptyList()
        return _allSongs.value.filter { song ->
            song.genre?.trim()?.equals(genreName, ignoreCase = true) == true ||
                (genreName == "Unknown Genre" && song.genre.isNullOrBlank())
        }
    }

    fun getSongById(songId: String): Song? {
        return _allSongs.value.firstOrNull { it.id == songId }
    }

    fun getAlbumById(albumId: Long): Album? {
        return _albums.value.firstOrNull { it.id == albumId }
    }

    fun getArtistById(artistId: Long): Artist? {
        return _artists.value.firstOrNull { it.id == artistId }
    }

    fun getSongsForAlbum(albumId: Long): List<Song> {
        return _allSongs.value.filter { it.albumId == albumId }
    }

    fun getSongsForArtist(artistId: Long): List<Song> {
        return _allSongs.value.filter { it.artistId == artistId }
    }


    private fun fetchMissingArtistImages(artistsList: List<Artist>) {
        viewModelScope.launch {
            if (artistsList.isNotEmpty()) {
                val artistsWithoutImages = artistsList.filter { it.imageUrl.isNullOrEmpty() }
                Log.d("LibraryViewModel", "Fetching Deezer images for ${artistsWithoutImages.size} artists...")
                for (artist in artistsWithoutImages) {
                    try {
                        val imageUrl = artistImageRepository.getArtistImageUrl(artist.name, artist.id)
                        if (imageUrl != null) {
                            // Update local state if needed
                            val updatedArtists = _artists.value.map { a ->
                                if (a.id == artist.id) a.copy(imageUrl = imageUrl) else a
                            }
                            _artists.value = updatedArtists.toImmutableList()
                        }
                    } catch (e: Exception) {
                        Log.e("LibraryViewModel", "Error fetching image for artist: ${artist.name}", e)
                    }
                }
            }
        }
    }

    companion object {
        private val defaultLibraryTabs = listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
    }
}
