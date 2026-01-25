package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Trace
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.toArgb
import android.util.Log
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the data state of the music library: Songs, Albums, Artists, Folders.
 * Handles loading from Repository and applying SortOptions.
 */
@Singleton
class LibraryStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    // --- State ---
    private val _allSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val allSongs = _allSongs.asStateFlow()

    private val _albums = MutableStateFlow<ImmutableList<Album>>(persistentListOf())
    val albums = _albums.asStateFlow()

    private val _artists = MutableStateFlow<ImmutableList<Artist>>(persistentListOf())
    val artists = _artists.asStateFlow()

    private val _musicFolders = MutableStateFlow<ImmutableList<MusicFolder>>(persistentListOf())
    val musicFolders = _musicFolders.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary = _isLoadingLibrary.asStateFlow()

    private val _isLoadingCategories = MutableStateFlow(false)
    val isLoadingCategories = _isLoadingCategories.asStateFlow()

    // Sort Options
    private val _currentSongSortOption = MutableStateFlow<SortOption>(SortOption.SongDefaultOrder)
    val currentSongSortOption = _currentSongSortOption.asStateFlow()

    private val _currentAlbumSortOption = MutableStateFlow<SortOption>(SortOption.AlbumTitleAZ)
    val currentAlbumSortOption = _currentAlbumSortOption.asStateFlow()

    private val _currentArtistSortOption = MutableStateFlow<SortOption>(SortOption.ArtistNameAZ)
    val currentArtistSortOption = _currentArtistSortOption.asStateFlow()

    private val _currentFolderSortOption = MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
    val currentFolderSortOption = _currentFolderSortOption.asStateFlow()

    private val _currentFavoriteSortOption = MutableStateFlow<SortOption>(SortOption.LikedSongTitleAZ)
    val currentFavoriteSortOption = _currentFavoriteSortOption.asStateFlow()



    @OptIn(ExperimentalStdlibApi::class)
    val genres: kotlinx.coroutines.flow.Flow<ImmutableList<com.theveloper.pixelplay.data.model.Genre>> = _allSongs
        .map { songs ->
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

            genreMap.toList().mapIndexedNotNull { index, (genreName, songs) ->
                if (songs.isNotEmpty()) {
                    val id = if (genreName.equals(unknownGenreName, ignoreCase = true)) {
                        "unknown"
                    } else {
                        genreName.lowercase().replace(" ", "_").replace("/", "_")
                    }
                    val color = com.theveloper.pixelplay.ui.theme.GenreColors.colors[index % com.theveloper.pixelplay.ui.theme.GenreColors.colors.size]
                    com.theveloper.pixelplay.data.model.Genre(
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
        }
        .flowOn(Dispatchers.Default)

    
    // Internal state
    private var scope: CoroutineScope? = null

    // --- Initialization ---

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        // Initial load of sort preferences
        scope.launch {
            val songSortKey = userPreferencesRepository.songsSortOptionFlow.first()
            _currentSongSortOption.value = SortOption.SONGS.find { it.storageKey == songSortKey } ?: SortOption.SongDefaultOrder

            val albumSortKey = userPreferencesRepository.albumsSortOptionFlow.first()
            _currentAlbumSortOption.value = SortOption.ALBUMS.find { it.storageKey == albumSortKey } ?: SortOption.AlbumTitleAZ
            
            val artistSortKey = userPreferencesRepository.artistsSortOptionFlow.first()
            _currentArtistSortOption.value = SortOption.ARTISTS.find { it.storageKey == artistSortKey } ?: SortOption.ArtistNameAZ
            
            
            val likedSortKey = userPreferencesRepository.likedSongsSortOptionFlow.first()
            _currentFavoriteSortOption.value = SortOption.LIKED.find { it.storageKey == likedSortKey } ?: SortOption.LikedSongDateLiked
        }
    }

    fun onCleared() {
        scope = null
    }

    // --- Data Loading ---
    
    // We observe the repository flows permanently in initialize(), or we start collecting here?
    // Better to start collecting in initialize() or have these functions just be "ensure active".
    // Actually, explicit "load" functions are legacy imperative style.
    // We should launch collectors in initialize() that update the state.
    
    private var songsJob: Job? = null
    private var albumsJob: Job? = null
    private var artistsJob: Job? = null
    private var foldersJob: Job? = null
    
    fun startObservingLibraryData() {
        if (songsJob?.isActive == true) return
        
        Log.d("LibraryStateHolder", "startObservingLibraryData called.")
        
        songsJob = scope?.launch {
            _isLoadingLibrary.value = true
            musicRepository.getAudioFiles().collect { songs ->
                 // When the repository emits a new list (triggered by directory changes),
                 // we update our state and re-apply current sorting.
                 _allSongs.value = songs.toImmutableList()
                 // Apply sort to the new data
                 sortSongs(_currentSongSortOption.value, persist = false)
                 _isLoadingLibrary.value = false
            }
        }
        
        albumsJob = scope?.launch {
            _isLoadingCategories.value = true
            musicRepository.getAlbums().collect { albums ->
                _albums.value = albums.toImmutableList()
                sortAlbums(_currentAlbumSortOption.value, persist = false)
                _isLoadingCategories.value = false
            }
        }
        
        artistsJob = scope?.launch {
            _isLoadingCategories.value = true
            musicRepository.getArtists().collect { artists ->
                _artists.value = artists.toImmutableList()
                sortArtists(_currentArtistSortOption.value, persist = false)
                _isLoadingCategories.value = false
            }
        }
        
        foldersJob = scope?.launch {
            musicRepository.getMusicFolders().collect { folders ->
                 _musicFolders.value = folders.toImmutableList()
                 sortFolders(_currentFolderSortOption.value)
            }
        }
    }

    // Deprecated imperative loaders - redirected to observer start
    fun loadSongsFromRepository() {
         startObservingLibraryData()
    }

    fun loadAlbumsFromRepository() {
         startObservingLibraryData()
    }

    fun loadArtistsFromRepository() {
         startObservingLibraryData()
    }
    
    fun loadFoldersFromRepository() {
        startObservingLibraryData()
    }
    
    // --- Lazy Loading Checks ---

    // --- Lazy Loading Checks ---
    // We replace conditional "check if empty" with "ensure observing".
    // If we are already observing, startObservingLibraryData returns early.
    // If we are not (e.g. process death recovery?), it restarts.
    
    fun loadSongsIfNeeded() {
         startObservingLibraryData()
    }

    fun loadAlbumsIfNeeded() {
        startObservingLibraryData()
    }

    fun loadArtistsIfNeeded() {
        startObservingLibraryData()
    }

    // --- Sorting ---

    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setSongsSortOption(sortOption.storageKey)
            }
            _currentSongSortOption.value = sortOption

            val sorted = when (sortOption) {
                SortOption.SongTitleAZ -> _allSongs.value.sortedBy { it.title.lowercase() }
                SortOption.SongTitleZA -> _allSongs.value.sortedByDescending { it.title.lowercase() }
                SortOption.SongArtist -> _allSongs.value.sortedBy { it.artist.lowercase() }
                SortOption.SongAlbum -> _allSongs.value.sortedBy { it.album.lowercase() }
                SortOption.SongDateAdded -> _allSongs.value.sortedByDescending { it.dateAdded }
                SortOption.SongDuration -> _allSongs.value.sortedBy { it.duration }
                else -> _allSongs.value // Default or unhandled
            }
            _allSongs.value = sorted.toImmutableList()
        }
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setAlbumsSortOption(sortOption.storageKey)
            }
            _currentAlbumSortOption.value = sortOption

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
        }
    }
    
    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setArtistsSortOption(sortOption.storageKey)
            }
            _currentArtistSortOption.value = sortOption

            val sorted = when (sortOption) {
                SortOption.ArtistNameAZ -> _artists.value.sortedBy { it.name.lowercase() }
                SortOption.ArtistNameZA -> _artists.value.sortedByDescending { it.name.lowercase() }
                else -> _artists.value
            }
            _artists.value = sorted.toImmutableList()
        }
    }

    fun sortFolders(sortOption: SortOption) {
        scope?.launch {
            // Folders sort preference might not be persisted in the same way or done elsewhere?
            // ViewModel checked "setFoldersPlaylistView" but not explicitly saving sort option in "sortFolders" function 
            // except locally in state?
            // Checking ViewModel: it just updates _playerUiState.
            // But wait, initialize() loads getFolderSortOption(). So it should be persisted.
            // Looking at ViewModel code again: sortFolders(sortOption) implementation at 4150 DOES NOT persist.
            // But initialize calls userPreferencesRepository.getFolderSortOption().
            // So perhaps persistence is missing in ViewModel or handled differently?
            // I will add persistence if 'persist' arg is added or just match ViewModel behavior.
            // The ViewModel sortFolders takes only sortOption.
            
            _currentFolderSortOption.value = sortOption
            
            val sorted = when (sortOption) {
                SortOption.FolderNameAZ -> _musicFolders.value.sortedBy { it.name.lowercase() }
                SortOption.FolderNameZA -> _musicFolders.value.sortedByDescending { it.name.lowercase() }
                else -> _musicFolders.value
            }
            _musicFolders.value = sorted.toImmutableList()
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setLikedSongsSortOption(sortOption.storageKey)
            }
            _currentFavoriteSortOption.value = sortOption
            // The actual filtering/sorting of favorites happens in ViewModel using this flow
        }
    }

    /**
     * Updates a single song in the in-memory list.
     * Used effectively after metadata edits to reflect changes immediately.
     */
    fun updateSong(updatedSong: Song) {
        _allSongs.update { currentList ->
            currentList.map { if (it.id == updatedSong.id) updatedSong else it }.toImmutableList()
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}
