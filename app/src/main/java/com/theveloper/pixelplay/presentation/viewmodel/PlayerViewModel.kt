package com.theveloper.pixelplay.presentation.viewmodel

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.theveloper.pixelplay.data.EotStateHolder // Ensure this import is present
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.database.AlbumArtThemeEntity
import com.theveloper.pixelplay.data.database.toComposeColor
import com.theveloper.pixelplay.data.database.toHexString
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.LightColorScheme
import com.theveloper.pixelplay.ui.theme.extractSeedColor
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.abs
import androidx.core.graphics.createBitmap
import com.theveloper.pixelplay.data.database.StoredColorSchemeValues
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import android.util.Log
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.SortOption
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.TimeUnit

// Nuevo enum para el estado del sheet
enum class PlayerSheetState {
    COLLAPSED,
    EXPANDED
}

data class ColorSchemePair( // Definición local si no está en un archivo común
    val light: ColorScheme,
    val dark: ColorScheme
)

private const val PAGE_SIZE = 30 // Número de items por página

// Estado para datos que no cambian cada segundo
data class StablePlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val totalDuration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isCurrentSongFavorite: Boolean = false
)

data class PlayerUiState(
    // currentSong, isPlaying, totalDuration, shuffle, repeat, favorite se mueven a StablePlayerState
    val currentPosition: Long = 0L, // Este se actualiza frecuentemente
    val isLoadingInitialSongs: Boolean = true,
    val isLoadingMoreSongs: Boolean = false,
    val allSongs: ImmutableList<Song> = persistentListOf(),
    val canLoadMoreSongs: Boolean = true,
    val currentPlaybackQueue: ImmutableList<Song> = persistentListOf(),
    val currentQueueSourceName: String = "All Songs",
    val lavaLampColors: ImmutableList<Color> = persistentListOf(),
    val albums: ImmutableList<Album> = persistentListOf(),
    val artists: ImmutableList<Artist> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = true,
    val canLoadMoreAlbums: Boolean = true,
    val canLoadMoreArtists: Boolean = true,
    val currentSongSortOption: SortOption = SortOption.SongTitleAZ,
    val currentAlbumSortOption: SortOption = SortOption.AlbumTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.ArtistNameAZ,
    val currentFavoriteSortOption: SortOption = SortOption.LikedSongTitleAZ,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf(),
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchHistory: ImmutableList<SearchHistoryItem> = persistentListOf()
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao
) : ViewModel() {

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()
    private val _stablePlayerState = MutableStateFlow(StablePlayerState())
    val stablePlayerState: StateFlow<StablePlayerState> = _stablePlayerState.asStateFlow()

    private val _sheetState = MutableStateFlow(PlayerSheetState.COLLAPSED)
    val sheetState: StateFlow<PlayerSheetState> = _sheetState.asStateFlow()
    private val _isSheetVisible = MutableStateFlow(false)
    val isSheetVisible: StateFlow<Boolean> = _isSheetVisible.asStateFlow()
    private val _bottomBarHeight = MutableStateFlow(0)
    val bottomBarHeight: StateFlow<Int> = _bottomBarHeight.asStateFlow()
    private val _predictiveBackCollapseFraction = MutableStateFlow(0f)
    val predictiveBackCollapseFraction: StateFlow<Float> = _predictiveBackCollapseFraction.asStateFlow()

    private val _currentAlbumArtColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = _currentAlbumArtColorSchemePair.asStateFlow()
    // Global and Player theme preferences are now managed by UserPreferencesRepository,
    // but PlayerViewModel still needs to observe them to react to changes.
    private val _globalThemePreference = MutableStateFlow(ThemePreference.DYNAMIC)
    val globalThemePreference: StateFlow<String> = _globalThemePreference.asStateFlow()
    private val _playerThemePreference = MutableStateFlow(ThemePreference.GLOBAL)
    val playerThemePreference: StateFlow<String> = _playerThemePreference.asStateFlow()

    private val _isInitialThemePreloadComplete = MutableStateFlow(false)
    val isInitialThemePreloadComplete: StateFlow<Boolean> = _isInitialThemePreloadComplete.asStateFlow()

    // Sleep Timer StateFlows
    private val _sleepTimerEndTimeMillis = MutableStateFlow<Long?>(null)
    val sleepTimerEndTimeMillis: StateFlow<Long?> = _sleepTimerEndTimeMillis.asStateFlow()

    private val _isEndOfTrackTimerActive = MutableStateFlow<Boolean>(false)
    val isEndOfTrackTimerActive: StateFlow<Boolean> = _isEndOfTrackTimerActive.asStateFlow()

    private val _activeTimerValueDisplay = MutableStateFlow<String?>(null)
    val activeTimerValueDisplay: StateFlow<String?> = _activeTimerValueDisplay.asStateFlow()

    private var sleepTimerJob: Job? = null
    // private val _endOfTrackSongId = MutableStateFlow<String?>(null) // Removed, EotStateHolder is the source of truth
    private var eotSongMonitorJob: Job? = null

    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    // Paginación
    private var currentSongPage = 1
    private var currentAlbumPage = 1
    private var currentArtistPage = 1

    // Flow dedicado sólo a la lista de canciones:
    val allSongsFlow: StateFlow<List<Song>> =
        _playerUiState
            .map { it.allSongs }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeGlobalColorSchemePair: StateFlow<ColorSchemePair?> = combine(
        _globalThemePreference, _currentAlbumArtColorSchemePair
    ) { globalPref, albumScheme ->
        when (globalPref) {
            ThemePreference.ALBUM_ART -> albumScheme // Si es null, Theme.kt usará dynamic/default
            ThemePreference.DYNAMIC -> null // Señal para usar dynamic colors del sistema
            ThemePreference.DEFAULT -> ColorSchemePair(LightColorScheme, DarkColorScheme)
            else -> null // Fallback a dynamic
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null) // Eagerly para que esté disponible al inicio

    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = combine(
        _playerThemePreference, activeGlobalColorSchemePair, _currentAlbumArtColorSchemePair
    ) { playerPref, globalSchemeResult, albumScheme ->
        when (playerPref) {
            ThemePreference.ALBUM_ART -> albumScheme // Puede ser null, Theme.kt en MainActivity lo manejará
            ThemePreference.GLOBAL -> globalSchemeResult // Puede ser null (si global es DYNAMIC)
            ThemePreference.DEFAULT -> ColorSchemePair(LightColorScheme, DarkColorScheme)
            else -> ColorSchemePair(LightColorScheme, DarkColorScheme)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ColorSchemePair(LightColorScheme, DarkColorScheme))

    // Caché en memoria para los ColorSchemePair de álbumes individuales, para acceso rápido en la UI.
    // Room es el caché persistente.
    private val individualAlbumColorSchemes = mutableMapOf<String, MutableStateFlow<ColorSchemePair?>>()

    private var mediaController: MediaController? = null
    private val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
    private val mediaControllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken).buildAsync()

    private val _favoriteSongIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteSongIds: StateFlow<Set<String>> = _favoriteSongIds.asStateFlow() // Exposed as StateFlow

    // Nuevo StateFlow para la lista de objetos Song favoritos
    val favoriteSongs: StateFlow<ImmutableList<Song>> = combine(
        _favoriteSongIds, // Now uses the public favoriteSongIds or private _favoriteSongIds
        _playerUiState // Depends on allSongs and currentFavoriteSortOption from uiState
    ) { ids, uiState ->
        val favoriteSongsList = uiState.allSongs.filter { song -> ids.contains(song.id) }
        when (uiState.currentFavoriteSortOption) {
            SortOption.LikedSongTitleAZ -> favoriteSongsList.sortedBy { it.title }
            SortOption.LikedSongTitleZA -> favoriteSongsList.sortedByDescending { it.title }
            SortOption.LikedSongArtist -> favoriteSongsList.sortedBy { it.artist }
            SortOption.LikedSongAlbum -> favoriteSongsList.sortedBy { it.album }
            SortOption.LikedSongDateLiked -> favoriteSongsList.sortedByDescending { it.id } // Assuming dateAdded for Liked
            else -> favoriteSongsList // Should not happen
        }.toImmutableList()
    }.stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    private var progressJob: Job? = null

    fun updateBottomBarHeight(heightPx: Int) {
        if (_bottomBarHeight.value != heightPx) {
            _bottomBarHeight.value = heightPx
        }
    }

    fun updatePredictiveBackCollapseFraction(fraction: Float) {
        _predictiveBackCollapseFraction.value = fraction.coerceIn(0f, 1f)
    }

    fun resetPredictiveBackCollapseFractionAfterAnimation() {
        _predictiveBackCollapseFraction.value = 0f
    }

    // Helper function to convert SortOption name string to SortOption object
    private fun getSortOptionFromString(optionName: String?): SortOption? {
        return when (optionName) {
            SortOption.SongTitleAZ.displayName -> SortOption.SongTitleAZ
            SortOption.SongTitleZA.displayName -> SortOption.SongTitleZA
            SortOption.SongArtist.displayName -> SortOption.SongArtist
            SortOption.SongAlbum.displayName -> SortOption.SongAlbum
            SortOption.SongDateAdded.displayName -> SortOption.SongDateAdded
            SortOption.SongDuration.displayName -> SortOption.SongDuration
            SortOption.AlbumTitleAZ.displayName -> SortOption.AlbumTitleAZ
            SortOption.AlbumTitleZA.displayName -> SortOption.AlbumTitleZA
            SortOption.AlbumArtist.displayName -> SortOption.AlbumArtist
            SortOption.AlbumReleaseYear.displayName -> SortOption.AlbumReleaseYear
            SortOption.ArtistNameAZ.displayName -> SortOption.ArtistNameAZ
            SortOption.ArtistNameZA.displayName -> SortOption.ArtistNameZA
            SortOption.LikedSongTitleAZ.displayName -> SortOption.LikedSongTitleAZ
            SortOption.LikedSongTitleZA.displayName -> SortOption.LikedSongTitleZA
            SortOption.LikedSongArtist.displayName -> SortOption.LikedSongArtist
            SortOption.LikedSongAlbum.displayName -> SortOption.LikedSongAlbum
            SortOption.LikedSongDateLiked.displayName -> SortOption.LikedSongDateLiked
            // Playlist options are not handled by PlayerViewModel
            else -> null // Or a default SortOption if appropriate
        }
    }

    init {
        // Observe theme preferences
        viewModelScope.launch { userPreferencesRepository.globalThemePreferenceFlow.collect { _globalThemePreference.value = it } }
        viewModelScope.launch { userPreferencesRepository.playerThemePreferenceFlow.collect { _playerThemePreference.value = it } }

        // Observe favorite songs
        viewModelScope.launch {
            userPreferencesRepository.favoriteSongIdsFlow.collect { ids ->
                _favoriteSongIds.value = ids
            }
        }

        // Observe sort option preferences
        viewModelScope.launch {
            userPreferencesRepository.songsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                    if (_playerUiState.value.currentSongSortOption != sortOption) { // Avoid re-sorting if option hasn't changed
                        // Update state first, then call sort which uses the state
                        _playerUiState.update { it.copy(currentSongSortOption = sortOption) }
                        if (!_playerUiState.value.isLoadingInitialSongs && _playerUiState.value.allSongs.isNotEmpty()) {
                             sortSongs(sortOption) // This will use the updated state
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.albumsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                    if (_playerUiState.value.currentAlbumSortOption != sortOption) {
                        _playerUiState.update { it.copy(currentAlbumSortOption = sortOption) }
                        if (!_playerUiState.value.isLoadingLibraryCategories && _playerUiState.value.albums.isNotEmpty()) {
                            sortAlbums(sortOption)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.artistsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                    if (_playerUiState.value.currentArtistSortOption != sortOption) {
                        _playerUiState.update { it.copy(currentArtistSortOption = sortOption) }
                        if (!_playerUiState.value.isLoadingLibraryCategories && _playerUiState.value.artists.isNotEmpty()) {
                            sortArtists(sortOption)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.likedSongsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                     // The favoriteSongs flow automatically uses currentFavoriteSortOption from playerUiState.
                     // Just updating the state is enough to trigger re-composition and re-sorting.
                    _playerUiState.update { it.copy(currentFavoriteSortOption = sortOption) }
                }
            }
        }

        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                setupMediaControllerListeners()
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
            }
        }, MoreExecutors.directExecutor())

        preloadThemesAndInitialData()
    }

    private fun preloadThemesAndInitialData() {
        val functionStartTime = System.currentTimeMillis()
        Log.d("PlayerViewModelPerformance", "preloadThemesAndInitialData START")

        viewModelScope.launch { // Main.immediate by default
            val overallInitStartTime = System.currentTimeMillis()
            _isInitialThemePreloadComplete.value = false // Mantener esto
            Log.d("PlayerViewModelPerformance", "preloadThemesAndInitialData: _isInitialThemePreloadComplete set to false. Time from start: ${System.currentTimeMillis() - overallInitStartTime} ms")

            // Start loading initial UI data
            val resetLoadDataStartTime = System.currentTimeMillis()
            resetAndLoadInitialData() // Esto lanza sus propias corrutinas
            Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData() call took ${System.currentTimeMillis() - resetLoadDataStartTime} ms (Note: actual loading is async). Time from overallInitStart: ${System.currentTimeMillis() - overallInitStartTime} ms")

            // Wait for the initial songs, albums, and artists to be loaded before marking initial theme/UI setup as complete.
            // This ensures that the primary content for all main library tabs is available when the loading screen disappears.
            viewModelScope.launch {
                _playerUiState.first { state ->
                    !state.isLoadingInitialSongs && !state.isLoadingLibraryCategories
                }
                // At this point, the first page of songs, albums, and artists has been loaded.
                _isInitialThemePreloadComplete.value = true
                val timeToComplete = System.currentTimeMillis() - overallInitStartTime
                Log.d("PlayerViewModelPerformance", "Initial song, album, and artist load complete. _isInitialThemePreloadComplete set to true. Total time since overallInitStart: ${timeToComplete} ms")
            }
        }
        Log.d("PlayerViewModelPerformance", "preloadThemesAndInitialData END. Total function time: ${System.currentTimeMillis() - functionStartTime} ms (dispatching async work)")
    }

    private fun resetAndLoadInitialData() {
        val functionStartTime = System.currentTimeMillis()
        Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData START")
        currentSongPage = 1
        currentAlbumPage = 1
        currentArtistPage = 1
        _playerUiState.update {
            it.copy(
                allSongs = emptyList<Song>().toImmutableList(),
                albums = emptyList<Album>().toImmutableList(),
                artists = emptyList<Artist>().toImmutableList(),
                canLoadMoreSongs = true,
                canLoadMoreAlbums = true,
                canLoadMoreArtists = true
            )
        }
        loadSongsFromRepository(isInitialLoad = true)
        loadLibraryCategories(isInitialLoad = true)
        Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData END. Total function time: ${System.currentTimeMillis() - functionStartTime} ms")
    }

    private fun loadSongsFromRepository(isInitialLoad: Boolean = false) {
        if (_playerUiState.value.isLoadingMoreSongs && !isInitialLoad) return // Evitar cargas múltiples
        if (!_playerUiState.value.canLoadMoreSongs && !isInitialLoad) return

        viewModelScope.launch {
            val initialLoadStartTime = System.currentTimeMillis()
            if (isInitialLoad) {
                Log.d("PlayerViewModelPerformance", "loadSongsFromRepository (Initial) START")
                _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            } else {
                _playerUiState.update { it.copy(isLoadingMoreSongs = true) }
            }

            val repoCallStartTime = System.currentTimeMillis()
            val newSongs = musicRepository.getAudioFiles(currentSongPage, PAGE_SIZE)
            if (isInitialLoad) {
                Log.d("PlayerViewModelPerformance", "musicRepository.getAudioFiles (Initial) took ${System.currentTimeMillis() - repoCallStartTime} ms for ${newSongs.size} songs.")
            }

            _playerUiState.update {
                it.copy(
                    allSongs = if (isInitialLoad) newSongs.toImmutableList() else (it.allSongs + newSongs).toImmutableList(),
                    isLoadingInitialSongs = false,
                    isLoadingMoreSongs = false,
                    canLoadMoreSongs = newSongs.size == PAGE_SIZE
                )
            }
            if (newSongs.isNotEmpty()) currentSongPage++
            if (isInitialLoad) {
                Log.d("PlayerViewModelPerformance", "loadSongsFromRepository (Initial) END. Data update complete. Total time: ${System.currentTimeMillis() - initialLoadStartTime} ms")
            }
        }
    }

    fun loadMoreSongs() {
        loadSongsFromRepository()
    }

    private fun loadLibraryCategories(isInitialLoad: Boolean = false) {
        if (_playerUiState.value.isLoadingLibraryCategories && !isInitialLoad) return

        viewModelScope.launch {
            val initialLoadStartTime = System.currentTimeMillis()
            if (isInitialLoad) {
                Log.d("PlayerViewModelPerformance", "loadLibraryCategories (Initial) START")
            }
            _playerUiState.update { it.copy(isLoadingLibraryCategories = true) }

            // Cargar Álbumes
            if (_playerUiState.value.canLoadMoreAlbums || isInitialLoad) {
                val repoCallAlbumsStartTime = System.currentTimeMillis()
                val newAlbums = musicRepository.getAlbums(currentAlbumPage, PAGE_SIZE)
                if (isInitialLoad) {
                    Log.d("PlayerViewModelPerformance", "musicRepository.getAlbums (Initial) took ${System.currentTimeMillis() - repoCallAlbumsStartTime} ms for ${newAlbums.size} albums.")
                }
                _playerUiState.update {
                    it.copy(
                        albums = if (isInitialLoad) newAlbums.toImmutableList() else (it.albums + newAlbums).toImmutableList(),
                        canLoadMoreAlbums = newAlbums.size == PAGE_SIZE
                    )
                }
                if (newAlbums.isNotEmpty()) currentAlbumPage++
                if (isInitialLoad) {
                    Log.d("PlayerViewModelPerformance", "loadLibraryCategories (Initial) Album data update complete. Time from start: ${System.currentTimeMillis() - initialLoadStartTime} ms")
                }
            }

            // Cargar Artistas
            if (_playerUiState.value.canLoadMoreArtists || isInitialLoad) {
                val repoCallArtistsStartTime = System.currentTimeMillis()
                val newArtists = musicRepository.getArtists(currentArtistPage, PAGE_SIZE)
                if (isInitialLoad) {
                    Log.d("PlayerViewModelPerformance", "musicRepository.getArtists (Initial) took ${System.currentTimeMillis() - repoCallArtistsStartTime} ms for ${newArtists.size} artists.")
                }
                _playerUiState.update {
                    it.copy(
                        artists = if (isInitialLoad) newArtists.toImmutableList() else (it.artists + newArtists).toImmutableList(),
                        canLoadMoreArtists = newArtists.size == PAGE_SIZE
                    )
                }
                if (newArtists.isNotEmpty()) currentArtistPage++
                if (isInitialLoad) {
                    Log.d("PlayerViewModelPerformance", "loadLibraryCategories (Initial) Artist data update complete. Time from start: ${System.currentTimeMillis() - initialLoadStartTime} ms")
                }
            }
            _playerUiState.update { it.copy(isLoadingLibraryCategories = false) }
            if (isInitialLoad) {
                Log.d("PlayerViewModelPerformance", "loadLibraryCategories (Initial) END. All categories loaded. Total time: ${System.currentTimeMillis() - initialLoadStartTime} ms")
            }
        }
    }

    fun loadMoreAlbums() {
        if (!_playerUiState.value.isLoadingLibraryCategories && _playerUiState.value.canLoadMoreAlbums) {
            loadLibraryCategories()
        }
    }

    fun loadMoreArtists() {
        if (!_playerUiState.value.isLoadingLibraryCategories && _playerUiState.value.canLoadMoreArtists) {
            loadLibraryCategories()
        }
    }

    // showAndPlaySong ahora usa playSongs con la lista de contexto proporcionada.
    fun showAndPlaySong(song: Song, contextSongs: List<Song>, queueName: String = "Current Context") {
        // Utiliza la lista de canciones del contexto actual (ej: canciones de un género específico) como la cola.
        playSongs(contextSongs, song, queueName)
        _isSheetVisible.value = true
        _predictiveBackCollapseFraction.value = 0f
    }

    // Overloaded method for playing a single song, assuming it's from the main "all songs" list.
    fun showAndPlaySong(song: Song) {
        // Uses the current 'allSongs' list from the UI state as the default playback context.
        // This list is paginated, so the full queue might only contain currently loaded songs.
        // For a true "play from library" feel where the queue is all songs,
        // 'allSongs' should ideally represent the fully loaded library if performance allows,
        // or this method might need to trigger loading all songs for the queue.
        // For now, it uses the existing playerUiState.value.allSongs.
        showAndPlaySong(song, playerUiState.value.allSongs.toList(), "Library")
    }

    fun playAlbum(album: Album) {
        viewModelScope.launch {
            val songs = musicRepository.getSongsForAlbum(
                album.id
            )
            if (songs.isNotEmpty()) {
                playSongs(songs, songs.first(), album.title)
                _isSheetVisible.value = true // Mostrar reproductor
            }
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            val songs = musicRepository.getSongsForArtist(
                artist.id
            )
            if (songs.isNotEmpty()) {
                playSongs(songs, songs.first(), artist.name)
                _isSheetVisible.value = true
            }
        }
    }

    fun removeSongFromQueue(songId: String) {
        mediaController?.let { controller ->
            val currentQueue = _playerUiState.value.currentPlaybackQueue
            val indexToRemove = currentQueue.indexOfFirst { it.id == songId }
            if (indexToRemove != -1) {
                controller.removeMediaItem(indexToRemove)
                // La UI se actualizará a través de onTimelineChanged -> updateCurrentPlaybackQueueFromPlayer
            }
        }
    }

    fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.let { controller ->
            if (fromIndex >= 0 && fromIndex < controller.mediaItemCount &&
                toIndex >= 0 && toIndex < controller.mediaItemCount) {
                controller.moveMediaItem(fromIndex, toIndex)
                // La UI se actualizará a través de onTimelineChanged
            }
        }
    }

    fun togglePlayerSheetState() {
        if (_isSheetVisible.value) {
            _sheetState.value = if (_sheetState.value == PlayerSheetState.COLLAPSED) {
                PlayerSheetState.EXPANDED
            } else {
                PlayerSheetState.COLLAPSED
            }
            _predictiveBackCollapseFraction.value = 0f
        }
    }

    fun expandPlayerSheet() {
        if (_isSheetVisible.value) _sheetState.value = PlayerSheetState.EXPANDED
        _predictiveBackCollapseFraction.value = 0f
    }

    fun collapsePlayerSheet() {
        if (_isSheetVisible.value) _sheetState.value = PlayerSheetState.COLLAPSED
        _predictiveBackCollapseFraction.value = 0f
    }

    private fun updateCurrentPlaybackQueueFromPlayer() {
        val controller = mediaController ?: return
        val count = controller.mediaItemCount
        val queue = mutableListOf<Song>()
        val allSongsMasterList = _playerUiState.value.allSongs
        for (i in 0 until count) {
            val mediaItem = controller.getMediaItemAt(i)
            allSongsMasterList.find { it.id == mediaItem.mediaId }?.let { song -> queue.add(song) }
        }
        _playerUiState.update { it.copy(currentPlaybackQueue = queue.toImmutableList()) }
    }

    private fun setupMediaControllerListeners() {
        val controller = mediaController ?: return
        _stablePlayerState.update {
            it.copy(
                isShuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode,
                isPlaying = controller.isPlaying
            )
        }
        updateCurrentPlaybackQueueFromPlayer()

        controller.currentMediaItem?.mediaId?.let { songId ->
            val song = _playerUiState.value.currentPlaybackQueue.find { s -> s.id == songId }
                ?: _playerUiState.value.allSongs.find { s -> s.id == songId }

            if (song != null) {
                _stablePlayerState.update {
                    it.copy(
                        currentSong = song,
                        totalDuration = controller.duration.coerceAtLeast(0L)
                    )
                }
                _playerUiState.update { it.copy(currentPosition = controller.currentPosition.coerceAtLeast(0L)) }
                viewModelScope.launch { // ver si causa problemas, extractAndGenerateColorScheme ahora es suspend
                    song.albumArtUriString?.let { Uri.parse(it) }?.let { uri ->
                        extractAndGenerateColorScheme(uri)
                    }
                }
                updateFavoriteStatusForCurrentSong()
                if (controller.isPlaying) startProgressUpdates()
                if (_stablePlayerState.value.currentSong != null && !_isSheetVisible.value) _isSheetVisible.value = true
            } else {
                _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false) }
                _playerUiState.update { it.copy(currentPosition = 0L) }
                if (_isSheetVisible.value) { /* hideSheetCompletely() */ }
            }
        }

        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _stablePlayerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { songId ->
                    val song = _playerUiState.value.currentPlaybackQueue.find { s -> s.id == songId }
                        ?: _playerUiState.value.allSongs.find { s -> s.id == songId }
                    _stablePlayerState.update {
                        it.copy(
                            currentSong = song,
                            totalDuration = controller.duration.coerceAtLeast(0L)
                        )
                    }
                    _playerUiState.update { it.copy(currentPosition = 0L) } // Reset position
                    song?.let { currentSongValue ->
                        viewModelScope.launch {
                            currentSongValue.albumArtUriString?.let { Uri.parse(it) }?.let { uri ->
                                extractAndGenerateColorScheme(uri)
                            }
                        }
                        updateFavoriteStatusForCurrentSong()
                    }
                } ?: _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false, isCurrentSongFavorite = false) }

                // EOT Completion Logic moved to onMediaItemTransition
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val activeEotSongId = com.theveloper.pixelplay.data.EotStateHolder.eotTargetSongId.value
                    val previousSongId = mediaController?.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex)?.mediaId else null }

                    if (_isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                        // EOT Condition Met: The EOT target song (previousSongId) just finished naturally.

                        mediaController?.seekTo(0L) // Seek new current item (mediaItem) to its start
                        mediaController?.pause()

                        val finishedSongTitle = _playerUiState.value.allSongs.find { it.id == previousSongId }?.title
                            ?: "Track" // Fallback title

                        viewModelScope.launch {
                            _toastEvents.emit("Playback stopped: $finishedSongTitle finished (End of Track).")
                        }
                        cancelSleepTimer(suppressDefaultToast = true)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _stablePlayerState.update { it.copy(totalDuration = controller.duration.coerceAtLeast(0L)) }
                }
                if (playbackState == Player.STATE_IDLE && controller.mediaItemCount == 0) {
                    _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false) }
                    _playerUiState.update { it.copy(currentPosition = 0L) }
                }
                // Old EOT completion logic (based on _isEndOfTrackTimerActive and _endOfTrackSongId/EotStateHolder.eotTargetSongId) removed from here.
                // Assertive EOT actions in MusicService and natural EOT completion in onMediaItemTransition cover this.
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { _stablePlayerState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) } }
            override fun onRepeatModeChanged(repeatMode: Int) { _stablePlayerState.update { it.copy(repeatMode = repeatMode) } }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) updateCurrentPlaybackQueueFromPlayer()
            }
        })
    }

    // Modificado para establecer una lista de reproducción
    // Modificar playSongs para que la cola sea la lista completa de allSongs si se inicia desde ahí
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None") {
        // Old EOT deactivation logic removed, handled by eotSongMonitorJob
        mediaController?.let { controller ->
            // Si la lista de canciones a reproducir es la lista 'allSongs' (paginada),
            // idealmente deberíamos cargar todas las canciones para la cola.
            // Esto es un compromiso: o cargamos todo para la cola (puede ser lento),
            // o la cola se limita a lo ya cargado.
            // Por ahora, usaremos `songsToPlay` como viene.
            val mediaItems = songsToPlay.map { song ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.albumArtUriString?.let { Uri.parse(it) })
                    // .setAlbumTitle(song.album) // Opcional: Considerar añadir si es útil
                    .build()
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(Uri.parse(song.contentUriString))
                    .setMediaMetadata(metadata)
                    .build()
            }
            val startIndex = songsToPlay.indexOf(startSong).coerceAtLeast(0)

            if (mediaItems.isNotEmpty()) {
                controller.setMediaItems(mediaItems, startIndex, 0L)
                controller.prepare()
                controller.play()
                _playerUiState.update { it.copy(currentPlaybackQueue = songsToPlay.toImmutableList(), currentQueueSourceName = queueName) }
                //_stablePlayerState.update { it.copy(currentSong = startSong, isPlaying = true) }
                viewModelScope.launch {
                    startSong.albumArtUriString?.let { Uri.parse(it) }?.let { uri ->
                        extractAndGenerateColorScheme(uri)
                    }
                }
                updateFavoriteStatusForCurrentSong()
            }
        }
        _playerUiState.update { it.copy(isLoadingInitialSongs = false) } // Marcar que la carga inicial de esta canción terminó
    }

    private fun loadAndPlaySong(song: Song) {
        mediaController?.let { controller ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(song.albumArtUriString?.let { Uri.parse(it) })
                // .setAlbumTitle(song.album) // Opcional: Considerar añadir si es útil
                .build()
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(Uri.parse(song.contentUriString))
                .setMediaMetadata(metadata)
                .build()
            if (controller.currentMediaItem?.mediaId == song.id) {
                if (!controller.isPlaying) controller.play()
            } else {
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
            _stablePlayerState.update { it.copy(currentSong = song, isPlaying = true) }
            viewModelScope.launch {
                song.albumArtUriString?.let { Uri.parse(it) }?.let { uri ->
                    extractAndGenerateColorScheme(uri, isPreload = false)
                }
            }
            //extractAndGeneratePalette(song.albumArtUri)
        }
    }

    private fun updateFavoriteStatusForCurrentSong() {
        val currentSongId = _stablePlayerState.value.currentSong?.id
        _stablePlayerState.update {
            it.copy(isCurrentSongFavorite = currentSongId != null && _favoriteSongIds.value.contains(currentSongId))
        }
    }

    fun toggleShuffle() {
        val newShuffleState = !_stablePlayerState.value.isShuffleEnabled
        mediaController?.shuffleModeEnabled = newShuffleState
        // El listener onShuffleModeEnabledChanged actualizará el _uiState
    }

    fun cycleRepeatMode() {
        val currentMode = _stablePlayerState.value.repeatMode
        val newMode = when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = newMode
        // El listener onRepeatModeChanged actualizará el _uiState
    }

    fun toggleFavorite() {
        _stablePlayerState.value.currentSong?.id?.let { songId ->
            viewModelScope.launch {
                userPreferencesRepository.toggleFavoriteSong(songId)
                updateFavoriteStatusForCurrentSong()
                // _favoriteSongIds y, por ende, isCurrentSongFavorite se actualizarán por el flujo
            }
        }
    }

    fun toggleFavoriteSpecificSong(song: Song) {
        viewModelScope.launch {
            userPreferencesRepository.toggleFavoriteSong(song.id)
            // _favoriteSongIds will update automatically via its flow from UserPreferencesRepository
            // If current playing song is the one toggled, update its status too
            if (_stablePlayerState.value.currentSong?.id == song.id) {
                updateFavoriteStatusForCurrentSong()
            }
        }
    }

    fun addSongToQueue(song: Song) {
        mediaController?.let { controller ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(Uri.parse(song.contentUriString))
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.albumArtUriString?.let { Uri.parse(it) })
                    .build())
                .build()
            controller.addMediaItem(mediaItem)
            // Optionally update local queue state if not automatically handled by listeners
            // updateCurrentPlaybackQueueFromPlayer() // Call if needed and if it's safe
        }
    }

    // Función para ser llamada por AlbumGridItem
    fun getAlbumColorSchemeFlow(albumArtUri: String?): StateFlow<ColorSchemePair?> {
        val uriString = albumArtUri?.toString() ?: "default_fallback_key" // Usar una clave de fallback si la URI es null

        // Devolver flujo existente o crear uno nuevo
        return individualAlbumColorSchemes.getOrPut(uriString) {
            MutableStateFlow(null) // Inicialmente null, se poblará asíncronamente
        }.also { flow ->
            // Si el flujo es nuevo o no tiene valor, e la URI no es la de fallback, intentar cargar/generar
            if (flow.value == null && albumArtUri != null) {
                viewModelScope.launch {
                    val scheme = getOrGenerateColorSchemeForUri(albumArtUri, false)
                    flow.value = scheme // Emitir el esquema al flujo específico del álbum
                }
            } else if (albumArtUri == null && flow.value == null) {
                // Para el caso de fallback (sin URI), usar los defaults
                flow.value = ColorSchemePair(LightColorScheme, DarkColorScheme)
            }
        }
    }

    // Modificada para devolver el ColorSchemePair y ser usada por getAlbumColorSchemeFlow y la precarga
    private suspend fun getOrGenerateColorSchemeForUri(albumArtUri: String, isPreload: Boolean): ColorSchemePair? {
        val uriString = albumArtUri.toString()
        val cachedEntity = withContext(Dispatchers.IO) { albumArtThemeDao.getThemeByUri(uriString) }

        if (cachedEntity != null) {
            val schemePair = mapEntityToColorSchemePair(cachedEntity)
            // Si es la canción actual Y no es precarga, actualizar el tema del reproductor
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = schemePair
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            return schemePair
        }

        // Si no está en caché, generar
        return try {
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context).data(albumArtUri).allowHardware(false).size(Size(128, 128)).build()
                val result = context.imageLoader.execute(request).drawable
                result?.let { drawable ->
                    createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1)
                    ).also { bmp -> Canvas(bmp).let { canvas -> drawable.setBounds(0, 0, canvas.width, canvas.height); drawable.draw(canvas) } }
                }
            }
            bitmap?.let { bmp ->
                val schemePair = withContext(Dispatchers.Default) {
                    val seed = extractSeedColor(bmp)
                    generateColorSchemeFromSeed(seed)
                }
                withContext(Dispatchers.IO) { albumArtThemeDao.insertTheme(mapColorSchemePairToEntity(uriString, schemePair)) }
                if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                    _currentAlbumArtColorSchemePair.value = schemePair
                    updateLavaLampColorsBasedOnActivePlayerScheme()
                }
                schemePair
            } ?: run {
                if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                    _currentAlbumArtColorSchemePair.value = null
                    updateLavaLampColorsBasedOnActivePlayerScheme()
                }
                null
            }
        } catch (e: Exception) {
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = null
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            null
        }
    }

    private suspend fun extractAndGenerateColorScheme(albumArtUriAsUri: Uri?, isPreload: Boolean = false) {
        if (albumArtUriAsUri == null) {
            // Check current song's string URI when determining if it's the one without art
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == null) {
                _currentAlbumArtColorSchemePair.value = null
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            return
        }
        val uriString = albumArtUriAsUri.toString()
        val cachedThemeEntity = withContext(Dispatchers.IO) { albumArtThemeDao.getThemeByUri(uriString) }

        if (cachedThemeEntity != null) {
            val schemePair = mapEntityToColorSchemePair(cachedThemeEntity)
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = schemePair
                updateLavaLampColorsBasedOnActivePlayerScheme()
            } else if (isPreload) {
                // No es necesario actualizar _currentAlbumArtColorSchemePair durante la precarga
            }
            return
        }

        // Si no está en caché, generar (incluso durante la precarga)
        try {
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(albumArtUriAsUri)
                    .allowHardware(false) // Palette necesita ARGB_8888
                    .size(Size(128, 128)) // Redimensionar para Palette, más pequeño es más rápido
                    .bitmapConfig(Bitmap.Config.ARGB_8888) // Explicitly set config
                    .memoryCachePolicy(CachePolicy.ENABLED) // Ensure caching
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = context.imageLoader.execute(request).drawable
                result?.let { drawable ->
                    createBitmap(
                        drawable.intrinsicWidth.coerceAtLeast(1),
                        drawable.intrinsicHeight.coerceAtLeast(1)
                    ).also { bmp -> Canvas(bmp).let { canvas -> drawable.setBounds(0, 0, canvas.width, canvas.height); drawable.draw(canvas) } }
                }
            }
            bitmap?.let { bmp ->
                val schemePair = withContext(Dispatchers.Default) {
                    val seed = extractSeedColor(bmp)
                    generateColorSchemeFromSeed(seed)
                }
                withContext(Dispatchers.IO) { albumArtThemeDao.insertTheme(mapColorSchemePairToEntity(uriString, schemePair)) }
                if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                    _currentAlbumArtColorSchemePair.value = schemePair
                    updateLavaLampColorsBasedOnActivePlayerScheme()
                }
            } ?: run { if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) { _currentAlbumArtColorSchemePair.value = null; updateLavaLampColorsBasedOnActivePlayerScheme() } }
        } catch (e: Exception) { if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) { _currentAlbumArtColorSchemePair.value = null; updateLavaLampColorsBasedOnActivePlayerScheme() } }
    }

    // Funciones de Mapeo Entity <-> ColorSchemePair (Corregidas)
    private fun mapColorSchemePairToEntity(uriString: String, pair: ColorSchemePair): AlbumArtThemeEntity {
        fun mapSchemeToStoredValues(cs: ColorScheme) = StoredColorSchemeValues(
            primary = cs.primary.toHexString(), onPrimary = cs.onPrimary.toHexString(), primaryContainer = cs.primaryContainer.toHexString(), onPrimaryContainer = cs.onPrimaryContainer.toHexString(),
            secondary = cs.secondary.toHexString(), onSecondary = cs.onSecondary.toHexString(), secondaryContainer = cs.secondaryContainer.toHexString(), onSecondaryContainer = cs.onSecondaryContainer.toHexString(),
            tertiary = cs.tertiary.toHexString(), onTertiary = cs.onTertiary.toHexString(), tertiaryContainer = cs.tertiaryContainer.toHexString(), onTertiaryContainer = cs.onTertiaryContainer.toHexString(),
            background = cs.background.toHexString(), onBackground = cs.onBackground.toHexString(), surface = cs.surface.toHexString(), onSurface = cs.onSurface.toHexString(),
            surfaceVariant = cs.surfaceVariant.toHexString(), onSurfaceVariant = cs.onSurfaceVariant.toHexString(), error = cs.error.toHexString(), onError = cs.onError.toHexString(),
            outline = cs.outline.toHexString(), errorContainer = cs.errorContainer.toHexString(), onErrorContainer = cs.onErrorContainer.toHexString(),
            inversePrimary = cs.inversePrimary.toHexString(), inverseSurface = cs.inverseSurface.toHexString(), inverseOnSurface = cs.inverseOnSurface.toHexString(),
            surfaceTint = cs.surfaceTint.toHexString(), outlineVariant = cs.outlineVariant.toHexString(), scrim = cs.scrim.toHexString()
        )
        return AlbumArtThemeEntity(uriString, mapSchemeToStoredValues(pair.light), mapSchemeToStoredValues(pair.dark))
    }

    private fun mapEntityToColorSchemePair(entity: AlbumArtThemeEntity): ColorSchemePair {
        fun mapStoredValuesToScheme(sv: StoredColorSchemeValues, isDark: Boolean): ColorScheme {
            val defaultForRole = if (isDark) DarkColorScheme else LightColorScheme
            val placeholderColor = Color.Magenta
            return ColorScheme(
                primary = sv.primary.toComposeColor(), onPrimary = sv.onPrimary.toComposeColor(), primaryContainer = sv.primaryContainer.toComposeColor(), onPrimaryContainer = sv.onPrimaryContainer.toComposeColor(),
                secondary = sv.secondary.toComposeColor(), onSecondary = sv.onSecondary.toComposeColor(), secondaryContainer = sv.secondaryContainer.toComposeColor(), onSecondaryContainer = sv.onSecondaryContainer.toComposeColor(),
                tertiary = sv.tertiary.toComposeColor(), onTertiary = sv.onTertiary.toComposeColor(), tertiaryContainer = sv.tertiaryContainer.toComposeColor(), onTertiaryContainer = sv.onTertiaryContainer.toComposeColor(),
                background = sv.background.toComposeColor(), onBackground = sv.onBackground.toComposeColor(), surface = sv.surface.toComposeColor(), onSurface = sv.onSurface.toComposeColor(),
                surfaceVariant = sv.surfaceVariant.toComposeColor(), onSurfaceVariant = sv.onSurfaceVariant.toComposeColor(), error = sv.error.toComposeColor(), onError = sv.onError.toHexString(),
                outline = sv.outline.toComposeColor(), errorContainer = sv.errorContainer.toComposeColor(), onErrorContainer = sv.onErrorContainer.toComposeColor(),
                inversePrimary = sv.inversePrimary.toComposeColor(), surfaceTint = sv.surfaceTint.toComposeColor(), outlineVariant = sv.outlineVariant.toComposeColor(), scrim = sv.scrim.toComposeColor(),
                inverseSurface = sv.inverseSurface.toComposeColor(), inverseOnSurface = sv.inverseOnSurface.toComposeColor(),
                // Fill missing parameters with the placeholderColor
                surfaceBright = placeholderColor,
                surfaceDim = placeholderColor,
                surfaceContainer = placeholderColor,
                surfaceContainerHigh = placeholderColor,
                surfaceContainerHighest = placeholderColor,
                surfaceContainerLow = placeholderColor,
                surfaceContainerLowest = placeholderColor,
                primaryFixed = placeholderColor,
                primaryFixedDim = placeholderColor,
                onPrimaryFixed = placeholderColor,
                onPrimaryFixedVariant = placeholderColor,
                secondaryFixed = placeholderColor,
                secondaryFixedDim = placeholderColor,
                onSecondaryFixed = placeholderColor,
                onSecondaryFixedVariant = placeholderColor,
                tertiaryFixed = placeholderColor,
                tertiaryFixedDim = placeholderColor,
                onTertiaryFixed = placeholderColor,
                onTertiaryFixedVariant = placeholderColor
            )
        }
        return ColorSchemePair(mapStoredValuesToScheme(entity.lightThemeValues, false), mapStoredValuesToScheme(entity.darkThemeValues, true))
    }

    // Helper para determinar el color del texto/icono basado en la luminancia del fondo
    private fun getOnColor(backgroundColor: Color, lightColor: Color = Color.White, darkColor: Color = Color.Black): Color {
        val r = backgroundColor.red * 255
        val g = backgroundColor.green * 255
        val b = backgroundColor.blue * 255
        val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
        return if (luminance > 0.5) darkColor else lightColor
    }

    private fun updateLavaLampColorsBasedOnActivePlayerScheme() {
        viewModelScope.launch {
            val currentPlayerSchemePair = activePlayerColorSchemePair.first() // Puede ser null
            val schemeForLava = currentPlayerSchemePair?.dark ?: DarkColorScheme // Fallback si es null
            _playerUiState.update {
                it.copy(lavaLampColors = listOf(schemeForLava.primary, schemeForLava.secondary, schemeForLava.tertiary).distinct().toImmutableList())
            }
        }
    }

    fun playPause() {
        mediaController?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                if (it.currentMediaItem == null && _playerUiState.value.allSongs.isNotEmpty()) {
                    // Si no hay nada cargado, carga la primera canción de la lista
                    loadAndPlaySong(_playerUiState.value.allSongs.first())
                } else {
                    it.play()
                }
            }
        }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        _playerUiState.update { it.copy(currentPosition = position) }
    }

    fun nextSong() {
        // Old EOT deactivation logic removed, handled by eotSongMonitorJob
        mediaController?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
                it.play()
            }
        }
    }

    fun previousSong() {
        // Old EOT deactivation logic removed, handled by eotSongMonitorJob
        mediaController?.let { controller ->
            if (controller.currentPosition > 10000 && controller.isCurrentMediaItemSeekable) { // 10 segundos
                controller.seekTo(0)
            } else {
                controller.seekToPreviousMediaItem()
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = viewModelScope.launch {
            while (isActive && _stablePlayerState.value.isPlaying) {
                val newPosition = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                if (newPosition != _playerUiState.value.currentPosition) {
                    _playerUiState.update { it.copy(currentPosition = newPosition) }
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    //Sorting
    fun sortSongs(sortOption: SortOption) {
        // It's important that currentSongSortOption in uiState is updated BEFORE this function
        // is called if triggered by the preference flow, or that this function updates it first.
        // For user-initiated sort, this function is the source of truth for the new option.
        _playerUiState.update { it.copy(currentSongSortOption = sortOption) } // Ensure state is set

        val sortedSongs = when (sortOption) {
            SortOption.SongTitleAZ -> _playerUiState.value.allSongs.sortedBy { it.title }
            SortOption.SongTitleZA -> _playerUiState.value.allSongs.sortedByDescending { it.title }
            SortOption.SongArtist -> _playerUiState.value.allSongs.sortedBy { it.artist }
            SortOption.SongAlbum -> _playerUiState.value.allSongs.sortedBy { it.album }
            SortOption.SongDateAdded -> _playerUiState.value.allSongs.sortedByDescending { it.albumId } // need to implement date added for Song
            SortOption.SongDuration -> _playerUiState.value.allSongs.sortedBy { it.duration }
            else -> _playerUiState.value.allSongs
        }.toImmutableList()
        _playerUiState.update { it.copy(allSongs = sortedSongs) } // Update the list

        viewModelScope.launch {
            userPreferencesRepository.setSongsSortOption(sortOption.displayName)
        }
    }

    fun sortAlbums(sortOption: SortOption) {
        _playerUiState.update { it.copy(currentAlbumSortOption = sortOption) }

        val sortedAlbums = when (sortOption) {
            SortOption.AlbumTitleAZ -> _playerUiState.value.albums.sortedBy { it.title }
            SortOption.AlbumTitleZA -> _playerUiState.value.albums.sortedByDescending { it.title }
            SortOption.AlbumArtist -> _playerUiState.value.albums.sortedBy { it.artist }
            SortOption.AlbumReleaseYear -> _playerUiState.value.albums.sortedByDescending { it.id } //need to implement album release date
            else -> _playerUiState.value.albums
        }.toImmutableList()
        _playerUiState.update { it.copy(albums = sortedAlbums) }

        viewModelScope.launch {
            userPreferencesRepository.setAlbumsSortOption(sortOption.displayName)
        }
    }

    fun sortArtists(sortOption: SortOption) {
        _playerUiState.update { it.copy(currentArtistSortOption = sortOption) }

        val sortedArtists = when (sortOption) {
            SortOption.ArtistNameAZ -> _playerUiState.value.artists.sortedBy { it.name }
            SortOption.ArtistNameZA -> _playerUiState.value.artists.sortedByDescending { it.name }
            else -> _playerUiState.value.artists
        }.toImmutableList()
        _playerUiState.update { it.copy(artists = sortedArtists) }

        viewModelScope.launch {
            userPreferencesRepository.setArtistsSortOption(sortOption.displayName)
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption) {
        _playerUiState.update { it.copy(currentFavoriteSortOption = sortOption) }
        // The actual sorting is handled by the 'favoriteSongs' StateFlow reacting to 'currentFavoriteSortOption'.
        viewModelScope.launch {
            userPreferencesRepository.setLikedSongsSortOption(sortOption.displayName)
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        _playerUiState.update { it.copy(selectedSearchFilter = filterType) }
        // Trigger a new search with the updated filter
        // We get the current query from the state, assuming it's maintained elsewhere or not needed if query is also changing.
        // For simplicity, let's assume the query is available and a search should be re-triggered.
        // If performSearch is called from UI based on query text changes, this might be redundant
        // unless the query is stable and only filter changes.
        // Consider if the current query should be passed or fetched from a state field if it exists.
        // For now, let's assume the UI will call performSearch again after updating the filter.
    }

    fun loadSearchHistory(limit: Int = 15) { // Default limit to 15 or configurable
        viewModelScope.launch {
            val history = musicRepository.getRecentSearchHistory(limit)
            _playerUiState.update { it.copy(searchHistory = history.toImmutableList()) }
        }
    }

    fun performSearch(query: String) {
        viewModelScope.launch {
            if (query.isNotBlank()) {
                musicRepository.addSearchHistoryItem(query)
                loadSearchHistory() // Refresh history after adding new item
            }

            if (query.isBlank()) {
                _playerUiState.update { it.copy(searchResults = persistentListOf()) }
                // Optionally, when query is blank, still show full search history or recent searches
                // loadSearchHistory() // Or handle this based on UI requirements for blank query state
                return@launch
            }

            // Use the selectedSearchFilter from the uiState
            val currentFilter = _playerUiState.value.selectedSearchFilter
            val results = musicRepository.searchAll(query, currentFilter)
            _playerUiState.update { it.copy(searchResults = results.toImmutableList()) }
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            musicRepository.deleteSearchHistoryItemByQuery(query)
            loadSearchHistory() // Refresh the list
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            musicRepository.clearSearchHistory()
            _playerUiState.update { it.copy(searchHistory = persistentListOf()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        // No liberar mediaControllerFuture aquí si es compartido o gestionado por la Activity/Service
        // MediaController.releaseFuture(mediaControllerFuture) // Solo si este ViewModel es el único propietario
    }

    // Sleep Timer Control Functions
    fun setSleepTimer(durationMinutes: Int) {
        if (_isEndOfTrackTimerActive.value) {
            eotSongMonitorJob?.cancel()
            // Suppress default toast because we are setting a new timer, which will have its own toast.
            cancelSleepTimer(suppressDefaultToast = true)
            // _isEndOfTrackTimerActive and _endOfTrackSongId are cleared by cancelSleepTimer
        }

        sleepTimerJob?.cancel() // Cancel any existing duration-based timer job
        val durationMillis = TimeUnit.MINUTES.toMillis(durationMinutes.toLong())
        val endTime = System.currentTimeMillis() + durationMillis
        _sleepTimerEndTimeMillis.value = endTime
        // _isEndOfTrackTimerActive is already false or set false above
        _activeTimerValueDisplay.value = "$durationMinutes minutes"

        sleepTimerJob = viewModelScope.launch {
            delay(durationMillis)
            if (isActive && _sleepTimerEndTimeMillis.value == endTime) { // Check if timer wasn't cancelled or changed
                mediaController?.pause()
                cancelSleepTimer() // Clear state after pausing
            }
        }
        viewModelScope.launch { _toastEvents.emit("Timer set for $durationMinutes minutes.") }
    }

    fun setEndOfTrackTimer(enable: Boolean) {
        if (enable) {
            val currentSong = stablePlayerState.value.currentSong
            if (currentSong == null) {
                viewModelScope.launch { _toastEvents.emit("Cannot enable End of Track: No active song.") }
                return
            }
            _activeTimerValueDisplay.value = "End of Track" // Set this first for cancelSleepTimer toast logic
            _isEndOfTrackTimerActive.value = true
            EotStateHolder.setEotTargetSong(currentSong.id) // Use EotStateHolder

            sleepTimerJob?.cancel()
            _sleepTimerEndTimeMillis.value = null

            eotSongMonitorJob?.cancel()
            eotSongMonitorJob = viewModelScope.launch {
                stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged().collect { newSongId ->
                    // Primary condition: Is EOT *still supposed to be active* according to our state?
                    // And was it set for a specific song? And has the song ID actually changed from that target?
                    if (_isEndOfTrackTimerActive.value &&
                        EotStateHolder.eotTargetSongId.value != null &&
                        newSongId != EotStateHolder.eotTargetSongId.value) {

                        // If all the above are true, it implies that:
                        // 1. EOT was active for a specific song (EotStateHolder.eotTargetSongId.value).
                        // 2. The current song (newSongId) is now different.
                        // 3. The EOT did NOT complete naturally via onPlaybackStateChanged
                        //    (because if it did, _isEndOfTrackTimerActive.value would be false now).
                        // This strongly suggests a manual skip or programmatic song change.

                        val oldSongIdForToast = EotStateHolder.eotTargetSongId.value // Capture before it's cleared by cancelSleepTimer
                        val oldSongTitle = _playerUiState.value.allSongs.find { it.id == oldSongIdForToast }?.title
                            ?: "Previous track" // Fallback
                        val newSongTitleText = _playerUiState.value.allSongs.find { it.id == newSongId }?.title
                            ?: "Current track" // Fallback

                        viewModelScope.launch {
                            _toastEvents.emit("End of Track timer deactivated: song changed from $oldSongTitle to $newSongTitleText.")
                        }

                        // Call cancelSleepTimer to perform the full cleanup.
                        // Pass suppressDefaultToast = true because we've just emitted a more specific toast.
                        cancelSleepTimer(suppressDefaultToast = true)

                        // It's good practice to also cancel the job itself if its purpose is fulfilled,
                        // though cancelSleepTimer should also be cancelling it. This is belt-and-suspenders.
                        eotSongMonitorJob?.cancel()
                        eotSongMonitorJob = null
                    }
                }
            }
            viewModelScope.launch { _toastEvents.emit("Playback will stop at end of track.") }
        } else {
            // This branch is for explicit disabling, e.g., via UI toggle if it doesn't call cancelSleepTimer directly.
            eotSongMonitorJob?.cancel()
            if (_isEndOfTrackTimerActive.value && EotStateHolder.eotTargetSongId.value != null) { // Check shared state
                cancelSleepTimer() // Ensures full cleanup and default "Timer Cancelled" toast
            }
        }
    }

    fun cancelSleepTimer(overrideToastMessage: String? = null, suppressDefaultToast: Boolean = false) {
        val wasAnythingActive = _activeTimerValueDisplay.value != null

        // Cancel and Nullify Duration Timer Job & States
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndTimeMillis.value = null

        // Cancel and Nullify EOT Monitor Job & States
        eotSongMonitorJob?.cancel()
        eotSongMonitorJob = null
        _isEndOfTrackTimerActive.value = false
        EotStateHolder.setEotTargetSong(null) // Clear shared EOT state

        // Clear Generic Timer Display State
        _activeTimerValueDisplay.value = null

        // Handle Toast Logic
        if (overrideToastMessage != null) {
            viewModelScope.launch { _toastEvents.emit(overrideToastMessage) }
        } else if (!suppressDefaultToast && wasAnythingActive) {
            viewModelScope.launch { _toastEvents.emit("Timer cancelled.") }
        }
    }


    init {
        // Observe theme preferences
        viewModelScope.launch { userPreferencesRepository.globalThemePreferenceFlow.collect { _globalThemePreference.value = it } }
        viewModelScope.launch { userPreferencesRepository.playerThemePreferenceFlow.collect { _playerThemePreference.value = it } }

        // Observe favorite songs
        viewModelScope.launch {
            userPreferencesRepository.favoriteSongIdsFlow.collect { ids ->
                _favoriteSongIds.value = ids
            }
        }

        // Observe sort option preferences
        viewModelScope.launch {
            userPreferencesRepository.songsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                    if (_playerUiState.value.currentSongSortOption != sortOption) { // Avoid re-sorting if option hasn't changed
                        // Update state first, then call sort which uses the state
                        _playerUiState.update { it.copy(currentSongSortOption = sortOption) }
                        if (!_playerUiState.value.isLoadingInitialSongs && _playerUiState.value.allSongs.isNotEmpty()) {
                             sortSongs(sortOption) // This will use the updated state
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.albumsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                    if (_playerUiState.value.currentAlbumSortOption != sortOption) {
                        _playerUiState.update { it.copy(currentAlbumSortOption = sortOption) }
                        if (!_playerUiState.value.isLoadingLibraryCategories && _playerUiState.value.albums.isNotEmpty()) {
                            sortAlbums(sortOption)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.artistsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                    if (_playerUiState.value.currentArtistSortOption != sortOption) {
                        _playerUiState.update { it.copy(currentArtistSortOption = sortOption) }
                        if (!_playerUiState.value.isLoadingLibraryCategories && _playerUiState.value.artists.isNotEmpty()) {
                            sortArtists(sortOption)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.likedSongsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                     // The favoriteSongs flow automatically uses currentFavoriteSortOption from playerUiState.
                     // Just updating the state is enough to trigger re-composition and re-sorting.
                    _playerUiState.update { it.copy(currentFavoriteSortOption = sortOption) }
                }
            }
        }

        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                setupMediaControllerListeners()
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
            }
        }, MoreExecutors.directExecutor())

        preloadThemesAndInitialData()
        loadSearchHistory() // Load initial search history
    }

    fun getSongUrisForGenre(genreName: String): List<String> {
        val currentSongs = _playerUiState.value.allSongs

        if (currentSongs.isEmpty()) {
            return emptyList()
        }

        return currentSongs
            .filter { song -> song.genre.equals(genreName, ignoreCase = true) }
            .take(3)
            .mapNotNull { song -> song.albumArtUriString?.ifEmpty { null } } // Ensure URI is not null and not empty
    }
}