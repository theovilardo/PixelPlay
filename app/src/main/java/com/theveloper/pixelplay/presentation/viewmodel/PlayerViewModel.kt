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
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.worker.SyncManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.core.net.toUri
import kotlinx.coroutines.flow.flowOn

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
    val isLoadingLibraryCategories: Boolean = false, // Default to false
    val canLoadMoreAlbums: Boolean = true,
    val canLoadMoreArtists: Boolean = true,
    val currentSongSortOption: SortOption = SortOption.SongTitleAZ,
    val currentAlbumSortOption: SortOption = SortOption.AlbumTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.ArtistNameAZ,
    val currentFavoriteSortOption: SortOption = SortOption.LikedSongTitleAZ,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf(),
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchHistory: ImmutableList<SearchHistoryItem> = persistentListOf(),
    val isSyncingLibrary: Boolean = false, // Nuevo estado para la sincronización

    // State for dismiss/undo functionality
    val showDismissUndoBar: Boolean = false,
    val dismissedSong: Song? = null,
    val dismissedQueue: ImmutableList<Song> = persistentListOf(),
    val dismissedQueueName: String = "",
    val dismissedPosition: Long = 0L,
    val undoBarVisibleDuration: Long = 4000L // 4 seconds
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    private val syncManager: SyncManager // Inyectar SyncManager
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
    val globalThemePreference: StateFlow<String> = userPreferencesRepository.globalThemePreferenceFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.DYNAMIC)
    val playerThemePreference: StateFlow<String> = userPreferencesRepository.playerThemePreferenceFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.GLOBAL)

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

    // Last Library Tab Index
    val lastLibraryTabIndexFlow: StateFlow<Int> =
        userPreferencesRepository.lastLibraryTabIndexFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Default to Songs tab
        )

    // StateFlow to hold the sync status, converted from syncManager.isSyncing (Flow)
    // Initial value true, as we might assume sync is active on app start until proven otherwise.
    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Assuming sync might be initially active
        )

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
        globalThemePreference, _currentAlbumArtColorSchemePair
    ) { globalPref, albumScheme ->
        when (globalPref) {
            ThemePreference.ALBUM_ART -> albumScheme // Si es null, Theme.kt usará dynamic/default
            ThemePreference.DYNAMIC -> null // Señal para usar dynamic colors del sistema
            ThemePreference.DEFAULT -> ColorSchemePair(LightColorScheme, DarkColorScheme)
            else -> null // Fallback a dynamic
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null) // Eagerly para que esté disponible al inicio

    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = combine(
        playerThemePreference, activeGlobalColorSchemePair, _currentAlbumArtColorSchemePair
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

    // Cola para procesar solicitudes de generación de ColorScheme
    private val colorSchemeRequestChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val urisBeingProcessed = mutableSetOf<String>() // Para evitar encolar duplicados

    private var mediaController: MediaController? = null
    private val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
    private val mediaControllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken).buildAsync()

    val favoriteSongIds: StateFlow<Set<String>> = userPreferencesRepository.favoriteSongIdsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // StateFlow separado para la opción de ordenación de canciones favoritas
    private val _currentFavoriteSortOptionStateFlow = MutableStateFlow<SortOption>(SortOption.LikedSongTitleAZ) // Default. Especificar el tipo general SortOption.
    val currentFavoriteSortOptionStateFlow: StateFlow<SortOption> = _currentFavoriteSortOptionStateFlow.asStateFlow()

    // Nuevo StateFlow para la lista de objetos Song favoritos
    val favoriteSongs: StateFlow<ImmutableList<Song>> = combine(
        favoriteSongIds,
        allSongsFlow, // Depende del allSongsFlow más granular
        currentFavoriteSortOptionStateFlow // Depende del StateFlow de ordenación dedicado
    ) { ids, allSongsList, sortOption ->
        Log.d("PlayerViewModel", "Calculating favoriteSongs. IDs size: ${ids.size}, All songs size: ${allSongsList.size}, SortOption: $sortOption")
        val favoriteSongsList = allSongsList.filter { song -> ids.contains(song.id) }
        Log.d("PlayerViewModel", "Filtered favoriteSongsList size: ${favoriteSongsList.size}")
        when (sortOption) {
            SortOption.LikedSongTitleAZ -> favoriteSongsList.sortedBy { it.title }
            SortOption.LikedSongTitleZA -> favoriteSongsList.sortedByDescending { it.title }
            SortOption.LikedSongArtist -> favoriteSongsList.sortedBy { it.artist }
            SortOption.LikedSongAlbum -> favoriteSongsList.sortedBy { it.album }
            SortOption.LikedSongDateLiked -> favoriteSongsList.sortedByDescending { it.id } // Assuming dateAdded for Liked
            else -> favoriteSongsList // Should not happen
        }.toImmutableList()
    }
    .flowOn(Dispatchers.Default) // Execute combine and transformations on Default dispatcher
    .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    private var progressJob: Job? = null

    // *** NUEVA FUNCIÓN AÑADIDA PARA SOLUCIONAR EL ERROR ***
    /**
     * Ensures the connection to the MusicService is being established.
     * This function is called from MainActivity's onStart to ensure listeners are set up
     * if the ViewModel is created late or the connection needs to be re-established.
     * The actual connection logic is handled by `mediaControllerFuture` and its listener
     * in the `init` block.
     */
    fun connectToService() {
        if (mediaController == null && !mediaControllerFuture.isDone) {
            Log.d("PlayerViewModel", "connectToService() called. Connection already in progress via mediaControllerFuture.")
        } else if (mediaController == null && mediaControllerFuture.isDone) {
            Log.w("PlayerViewModel", "connectToService() called, but future is done and controller is still null. There might have been a connection error.")
        } else {
            Log.d("PlayerViewModel", "connectToService() called. MediaController already available.")
        }
    }

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
        Log.i("PlayerViewModel", "init started.")
        

        launchColorSchemeProcessor()

        viewModelScope.launch {
            userPreferencesRepository.songsSortOptionFlow.collect { optionName ->
                getSortOptionFromString(optionName)?.let { sortOption ->
                    if (_playerUiState.value.currentSongSortOption != sortOption) {
                        _playerUiState.update { it.copy(currentSongSortOption = sortOption) }
                        if (!_playerUiState.value.isLoadingInitialSongs && _playerUiState.value.allSongs.isNotEmpty()) {
                            sortSongs(sortOption)
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
                    // _playerUiState.update { it.copy(currentFavoriteSortOption = sortOption) } // Ya no se actualiza aquí
                    _currentFavoriteSortOptionStateFlow.value = sortOption // Actualizar el StateFlow dedicado
                }
            }
        }

        viewModelScope.launch {
            isSyncingStateFlow.collect { isSyncing ->
                val oldSyncingLibraryState = _playerUiState.value.isSyncingLibrary
                _playerUiState.update { it.copy(isSyncingLibrary = isSyncing) }

                if (oldSyncingLibraryState && !isSyncing) {
                    resetAndLoadInitialData("isSyncingStateFlow observer")
                }
            }
        }

        viewModelScope.launch {
            if (!isSyncingStateFlow.value &&
                _playerUiState.value.isLoadingInitialSongs &&
                _playerUiState.value.allSongs.isEmpty()
            ) {
                resetAndLoadInitialData("Initial Check")
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
        }, ContextCompat.getMainExecutor(context))


    }

    fun onMainActivityStart() {
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
            // Note: resetAndLoadInitialData is now called conditionally based on sync state in the init block.
            // We might not need to call it unconditionally here anymore if the init block's logic is robust.
            // However, if sync is not involved (e.g. app already synced), this ensures data is loaded.
            // The conditional calls in init block aim to prevent redundant loads if sync just finished.
            // For safety, ensuring it's called if still in initial loading phase and no songs.
            if (_playerUiState.value.isLoadingInitialSongs && _playerUiState.value.allSongs.isEmpty() && isSyncingStateFlow.value) { // Use new StateFlow
                 Log.i("PlayerViewModel", "preloadThemesAndInitialData: Sync is active, deferring initial load to sync completion handler.")
            } else if (_playerUiState.value.isLoadingInitialSongs && _playerUiState.value.allSongs.isEmpty()) {
                Log.i("PlayerViewModel", "preloadThemesAndInitialData: No sync active or already finished, and no songs loaded. Calling resetAndLoadInitialData from preload.")
                resetAndLoadInitialData("preloadThemesAndInitialData")
            } else {
                Log.i("PlayerViewModel", "preloadThemesAndInitialData: Initial songs already loading or loaded. Skipping direct call to resetAndLoadInitialData.")
            }
            //Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData() call took ${System.currentTimeMillis() - resetLoadDataStartTime} ms (Note: actual loading is async). Time from overallInitStart: ${System.currentTimeMillis() - overallInitStartTime} ms")

            // The UI will observe isLoadingInitialSongs and isLoadingLibraryCategories directly.
            // We set _isInitialThemePreloadComplete to true immediately after dispatching async work.
            _isInitialThemePreloadComplete.value = true
            val timeToComplete = System.currentTimeMillis() - overallInitStartTime
            Log.d("PlayerViewModelPerformance", "Initial theme preload complete (async data loading dispatched). Total time since overallInitStart: ${timeToComplete} ms")
        }
        Log.d("PlayerViewModelPerformance", "preloadThemesAndInitialData END. Total function time: ${System.currentTimeMillis() - functionStartTime} ms (dispatching async work)")
    }

    private fun resetAndLoadInitialData(caller: String = "Unknown") {
        val functionStartTime = System.currentTimeMillis()
        Log.i("PlayerViewModel", "resetAndLoadInitialData called from: $caller")
        Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData START - Called by: $caller")
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
        // Ya no se cargan todas las categorías de la biblioteca inicialmente.
        // Se cargarán bajo demanda cuando el usuario navegue a la pestaña correspondiente.
        // loadLibraryCategories(isInitialLoad = true)
        Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData END. Total function time: ${System.currentTimeMillis() - functionStartTime} ms")
    }

    private fun loadSongsFromRepository(isInitialLoad: Boolean = false) {
        Log.d("PlayerViewModel", "loadSongsFromRepository called. isInitialLoad: $isInitialLoad")
        // Estas comprobaciones iniciales están bien
        if (_playerUiState.value.isLoadingMoreSongs && !isInitialLoad) {
            Log.d("PlayerViewModelPerformance", "loadSongsFromRepository: Already loading more songs. Skipping.")
            return
        }
        if (!_playerUiState.value.canLoadMoreSongs && !isInitialLoad) {
            Log.d("PlayerViewModelPerformance", "loadSongsFromRepository: Cannot load more songs. Skipping.")
            return
        }

        viewModelScope.launch {
            val functionStartTime = System.currentTimeMillis()
            val loadType = if (isInitialLoad) "Initial" else "More"
            Log.d("PlayerViewModelPerformance", "loadSongsFromRepository ($loadType) START")

            // Actualizar el estado de carga
            if (isInitialLoad) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            } else {
                _playerUiState.update { it.copy(isLoadingMoreSongs = true) }
            }

            try {
                val repoCallStartTime = System.currentTimeMillis()

                // Colecta la lista del Flow.
                // Asumimos que getAudioFiles emite una sola lista para la página solicitada.
                val actualNewSongsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getAudioFiles(currentSongPage, PAGE_SIZE).first()
                }

                val repoCallDuration = System.currentTimeMillis() - repoCallStartTime
                Log.d("PlayerViewModelPerformance", "musicRepository.getAudioFiles ($loadType) took $repoCallDuration ms for ${actualNewSongsList.size} songs. Page: $currentSongPage")

                // UI update must be on the Main thread, which is the default context for viewModelScope.launch
                _playerUiState.update { currentState ->
                    val updatedAllSongs = if (isInitialLoad) {
                        actualNewSongsList.toImmutableList()
                    } else {
                        // Asegurarse de que no haya duplicados si se recarga la misma página por error
                        // Esto es opcional y depende de cómo manejes la lógica de currentSongPage
                        val currentSongIds = currentState.allSongs.map { it.id }.toSet()
                        val uniqueNewSongs = actualNewSongsList.filterNot { currentSongIds.contains(it.id) }
                        (currentState.allSongs + uniqueNewSongs).toImmutableList()
                    }
                    currentState.copy(
                        allSongs = updatedAllSongs,
                        isLoadingInitialSongs = false,
                        isLoadingMoreSongs = false,
                        canLoadMoreSongs = actualNewSongsList.size == PAGE_SIZE
                    )
                }
                Log.d("PlayerViewModel", "allSongs updated. New size: ${_playerUiState.value.allSongs.size}. isLoadingInitialSongs: ${_playerUiState.value.isLoadingInitialSongs}")

                // Incrementar la página solo si se cargaron canciones y se espera que haya más
                if (actualNewSongsList.isNotEmpty() && actualNewSongsList.size == PAGE_SIZE) {
                    currentSongPage++
                    Log.d("PlayerViewModelPerformance", "loadSongsFromRepository ($loadType): Incremented currentSongPage to $currentSongPage")
                } else if (actualNewSongsList.isEmpty()) {
                    // Si no se cargaron canciones y se esperaba alguna, probablemente no haya más
                    _playerUiState.update { it.copy(canLoadMoreSongs = false) }
                    Log.d("PlayerViewModelPerformance", "loadSongsFromRepository ($loadType): No songs returned, setting canLoadMoreSongs to false.")
                }


                val totalFunctionTime = System.currentTimeMillis() - functionStartTime
                Log.d("PlayerViewModelPerformance", "loadSongsFromRepository ($loadType) END. Data update complete. Total time: $totalFunctionTime ms")

            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading songs from repository ($loadType)", e)
                _playerUiState.update {
                    it.copy(
                        isLoadingInitialSongs = false,
                        isLoadingMoreSongs = false,
                        //errorLoadingSongs = "Failed to load songs: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
                val totalFunctionTime = System.currentTimeMillis() - functionStartTime
                Log.d("PlayerViewModelPerformance", "loadSongsFromRepository ($loadType) FAILED. Total time: $totalFunctionTime ms")
            }
        }
    }

    fun loadMoreSongs() {
        loadSongsFromRepository()
    }

    // Funciones para cargar álbumes
    private fun loadAlbumsFromRepository(isInitialLoad: Boolean = false) {
        if (_playerUiState.value.isLoadingLibraryCategories && !isInitialLoad) { // isLoadingLibraryCategories puede necesitar ser más granular
            Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository: Already loading. Skipping.")
            return
        }
        if (!_playerUiState.value.canLoadMoreAlbums && !isInitialLoad) {
            Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository: Cannot load more albums. Skipping.")
            return
        }

        viewModelScope.launch {
            val functionStartTime = System.currentTimeMillis()
            val loadTypeLog = if (isInitialLoad) "(Initial)" else "(More)"
            Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository $loadTypeLog START")

            _playerUiState.update { it.copy(isLoadingLibraryCategories = true) } // Considerar un isLoadingAlbums

            try {
                val repoCallAlbumsStartTime = System.currentTimeMillis()
                val actualNewAlbums: List<Album> = withContext(Dispatchers.IO) {
                    musicRepository.getAlbums(currentAlbumPage, PAGE_SIZE).first()
                }
                val albumsLoadDuration = System.currentTimeMillis() - repoCallAlbumsStartTime
                Log.d("PlayerViewModelPerformance", "musicRepository.getAlbums $loadTypeLog took $albumsLoadDuration ms for ${actualNewAlbums.size} albums. Page: $currentAlbumPage")

                _playerUiState.update { currentState ->
                    val updatedAlbums = if (isInitialLoad) {
                        actualNewAlbums.toImmutableList()
                    } else {
                        val currentAlbumIds = currentState.albums.map { it.id }.toSet()
                        val uniqueNewAlbums = actualNewAlbums.filterNot { currentAlbumIds.contains(it.id) }
                        (currentState.albums + uniqueNewAlbums).toImmutableList()
                    }
                    currentState.copy(
                        albums = updatedAlbums,
                        canLoadMoreAlbums = actualNewAlbums.size == PAGE_SIZE,
                        isLoadingLibraryCategories = false // O isLoadingAlbums = false
                    )
                }

                if (actualNewAlbums.isNotEmpty() && actualNewAlbums.size == PAGE_SIZE) {
                    currentAlbumPage++
                } else if (actualNewAlbums.isEmpty()) {
                    _playerUiState.update { it.copy(canLoadMoreAlbums = false) }
                }
                Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository $loadTypeLog END. Total time: ${System.currentTimeMillis() - functionStartTime} ms")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading albums $loadTypeLog", e)
                _playerUiState.update { it.copy(isLoadingLibraryCategories = false) } // O isLoadingAlbums = false
            }
        }
    }

    fun loadMoreAlbums() {
        if (!_playerUiState.value.isLoadingLibraryCategories && _playerUiState.value.canLoadMoreAlbums) {
            loadAlbumsFromRepository(isInitialLoad = false)
        }
    }

    fun loadAlbumsIfNeeded() {
        val albumsEmpty = _playerUiState.value.albums.isEmpty()
        val canLoadMore = _playerUiState.value.canLoadMoreAlbums
        val notLoading = !_playerUiState.value.isLoadingLibraryCategories
        Log.d("PlayerViewModel", "loadAlbumsIfNeeded: albumsEmpty=$albumsEmpty, canLoadMore=$canLoadMore, notLoadingLibraryCategories=$notLoading")
        if (albumsEmpty && canLoadMore && notLoading) {
            Log.i("PlayerViewModel", "loadAlbumsIfNeeded: Conditions met. Loading initial albums.")
            loadAlbumsFromRepository(isInitialLoad = true)
        } else {
            var reason = ""
            if (!albumsEmpty) reason += "Albums not empty. "
            if (!canLoadMore) reason += "Cannot load more albums. "
            if (!notLoading) reason += "Currently loading library categories. "
            Log.w("PlayerViewModel", "loadAlbumsIfNeeded: Conditions NOT met. Skipping load. Reason: $reason")
        }
    }

    // Funciones para cargar artistas
    private fun loadArtistsFromRepository(isInitialLoad: Boolean = false) {
        if (_playerUiState.value.isLoadingLibraryCategories && !isInitialLoad) { // isLoadingLibraryCategories puede necesitar ser más granular
            Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository: Already loading. Skipping.")
            return
        }
        if (!_playerUiState.value.canLoadMoreArtists && !isInitialLoad) {
            Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository: Cannot load more artists. Skipping.")
            return
        }

        viewModelScope.launch {
            val functionStartTime = System.currentTimeMillis()
            val loadTypeLog = if (isInitialLoad) "(Initial)" else "(More)"
            Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository $loadTypeLog START")

            _playerUiState.update { it.copy(isLoadingLibraryCategories = true) } // Considerar un isLoadingArtists

            try {
                val repoCallArtistsStartTime = System.currentTimeMillis()
                val actualNewArtists: List<Artist> = withContext(Dispatchers.IO) {
                    musicRepository.getArtists(currentArtistPage, PAGE_SIZE).first()
                }
                val artistsLoadDuration = System.currentTimeMillis() - repoCallArtistsStartTime
                Log.d("PlayerViewModelPerformance", "musicRepository.getArtists $loadTypeLog took $artistsLoadDuration ms for ${actualNewArtists.size} artists. Page: $currentArtistPage")

                _playerUiState.update { currentState ->
                    val updatedArtists = if (isInitialLoad) {
                        actualNewArtists.toImmutableList()
                    } else {
                        val currentArtistIds = currentState.artists.map { it.id }.toSet()
                        val uniqueNewArtists = actualNewArtists.filterNot { currentArtistIds.contains(it.id) }
                        (currentState.artists + uniqueNewArtists).toImmutableList()
                    }
                    currentState.copy(
                        artists = updatedArtists,
                        canLoadMoreArtists = actualNewArtists.size == PAGE_SIZE,
                        isLoadingLibraryCategories = false // O isLoadingArtists = false
                    )
                }

                if (actualNewArtists.isNotEmpty() && actualNewArtists.size == PAGE_SIZE) {
                    currentArtistPage++
                } else if (actualNewArtists.isEmpty()) {
                    _playerUiState.update { it.copy(canLoadMoreArtists = false) }
                }
                Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository $loadTypeLog END. Total time: ${System.currentTimeMillis() - functionStartTime} ms")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading artists $loadTypeLog", e)
                _playerUiState.update { it.copy(isLoadingLibraryCategories = false) } // O isLoadingArtists = false
            }
        }
    }

    fun loadMoreArtists() {
        if (!_playerUiState.value.isLoadingLibraryCategories && _playerUiState.value.canLoadMoreArtists) {
            loadArtistsFromRepository(isInitialLoad = false)
        }
    }

    fun loadArtistsIfNeeded() {
        val artistsEmpty = _playerUiState.value.artists.isEmpty()
        val canLoadMore = _playerUiState.value.canLoadMoreArtists
        val notLoading = !_playerUiState.value.isLoadingLibraryCategories
        Log.d("PlayerViewModel", "loadArtistsIfNeeded: artistsEmpty=$artistsEmpty, canLoadMore=$canLoadMore, notLoadingLibraryCategories=$notLoading")
        if (artistsEmpty && canLoadMore && notLoading) {
            Log.i("PlayerViewModel", "loadArtistsIfNeeded: Conditions met. Loading initial artists.")
            loadArtistsFromRepository(isInitialLoad = true)
        } else {
            var reason = ""
            if (!artistsEmpty) reason += "Artists not empty. "
            if (!canLoadMore) reason += "Cannot load more artists. "
            if (!notLoading) reason += "Currently loading library categories. "
            Log.w("PlayerViewModel", "loadArtistsIfNeeded: Conditions NOT met. Skipping load. Reason: $reason")
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
            try {
                // Colecta la lista de canciones del Flow
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForAlbum(album.id).first()
                }

                if (songsList.isNotEmpty()) {
                    // Ahora songsList es una List<Song>, y songsList.first() es una Song
                    // UI updates and calls to playSongs (which might interact with MediaController) should be on Main
                    playSongs(songsList, songsList.first(), album.title)
                    _isSheetVisible.value = true // Mostrar reproductor
                } else {
                    // Opcional: manejar el caso donde el álbum no tiene canciones (o no permitidas)
                    Log.w("PlayerViewModel", "Album '${album.title}' has no playable songs.")
                    // podrías emitir un evento Toast o actualizar el UI de alguna manera
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing album ${album.title}", e)
                // Manejar el error, quizás mostrar un Toast
            }
        }
    }

    fun playArtist(artist: Artist) {
        viewModelScope.launch {
            try {
                // Colecta la lista de canciones del Flow
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForArtist(artist.id).first()
                }

                if (songsList.isNotEmpty()) {
                    // Ahora songsList es una List<Song>, y songsList.first() es una Song
                    // UI updates and calls to playSongs should be on Main
                    playSongs(songsList, songsList.first(), artist.name)
                    _isSheetVisible.value = true
                } else {
                    Log.w("PlayerViewModel", "Artist '${artist.name}' has no playable songs.")
                    // podrías emitir un evento Toast
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing artist ${artist.name}", e)
                // Manejar el error
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

    private fun updateCurrentPlaybackQueueFromPlayer(playerCtrl: MediaController?) { // Accept playerCtrl as parameter
        val currentMediaController = playerCtrl ?: mediaController ?: return // Use passed controller or fallback to class property
        val count = currentMediaController.mediaItemCount
        val queue = mutableListOf<Song>()
        val allSongsMasterList = _playerUiState.value.allSongs
        for (i in 0 until count) {
            val mediaItem = currentMediaController.getMediaItemAt(i)
            allSongsMasterList.find { it.id == mediaItem.mediaId }?.let { song -> queue.add(song) }
        }
        _playerUiState.update { it.copy(currentPlaybackQueue = queue.toImmutableList()) }
    }

    private fun setupMediaControllerListeners() {
        val playerCtrl = mediaController ?: return // Renamed to avoid shadowing
        _stablePlayerState.update {
            it.copy(
                isShuffleEnabled = playerCtrl.shuffleModeEnabled, // Use playerCtrl
                repeatMode = playerCtrl.repeatMode, // Use playerCtrl
                isPlaying = playerCtrl.isPlaying // Use playerCtrl
            )
        }
        updateCurrentPlaybackQueueFromPlayer(playerCtrl) // Pass playerCtrl

        playerCtrl.currentMediaItem?.mediaId?.let { songId -> // Use playerCtrl
            val song = _playerUiState.value.currentPlaybackQueue.find { s -> s.id == songId }
                ?: _playerUiState.value.allSongs.find { s -> s.id == songId }

            if (song != null) {
                _stablePlayerState.update {
                    it.copy(
                        currentSong = song,
                        totalDuration = playerCtrl.duration.coerceAtLeast(0L) // Use playerCtrl
                    )
                }
                _playerUiState.update { it.copy(currentPosition = playerCtrl.currentPosition.coerceAtLeast(0L)) } // Use playerCtrl
                viewModelScope.launch { // ver si causa problemas, extractAndGenerateColorScheme ahora es suspend
                    song.albumArtUriString?.toUri()?.let { uri ->
                        extractAndGenerateColorScheme(uri)
                    }
                }
                updateFavoriteStatusForCurrentSong()
                if (playerCtrl.isPlaying) startProgressUpdates() // Use playerCtrl
                if (_stablePlayerState.value.currentSong != null && !_isSheetVisible.value) _isSheetVisible.value = true
            } else {
                _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false) }
                _playerUiState.update { it.copy(currentPosition = 0L) }
                //if (_isSheetVisible.value) { /* hideSheetCompletely() */ }
            }
        }

        playerCtrl.addListener(object : Player.Listener { // Use playerCtrl to add listener
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _stablePlayerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgressUpdates() else stopProgressUpdates()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // val controller = mediaController ?: return // No longer needed, use playerCtrl from outer scope

                // --- EOT Completion Logic ---
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val activeEotSongId = com.theveloper.pixelplay.data.EotStateHolder.eotTargetSongId.value
                    // Correctly get previousMediaItem using the playerCtrl instance
                    val previousSongId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                    if (_isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                        // EOT Condition Met: The EOT target song (previousSongId) just finished naturally.
                        playerCtrl.seekTo(0L) // Seek new current item (mediaItem which is the next song) to its start
                        playerCtrl.pause()

                        val finishedSongTitle = _playerUiState.value.allSongs.find { it.id == previousSongId }?.title
                            ?: "Track" // Fallback title

                        viewModelScope.launch {
                            _toastEvents.emit("Playback stopped: $finishedSongTitle finished (End of Track).")
                        }
                        // This clears _isEndOfTrackTimerActive, EotStateHolder.eotTargetSongId, and other timer states.
                        cancelSleepTimer(suppressDefaultToast = true)
                        // eotHandled = true // Conceptually, EOT was handled. Player is now paused.
                                         // The onIsPlayingChanged listener will update isPlaying state.
                    }
                }

                // --- Update state for the new mediaItem ---
                mediaItem?.mediaId?.let { songId ->
                    val song = _playerUiState.value.currentPlaybackQueue.find { s -> s.id == songId }
                        ?: _playerUiState.value.allSongs.find { s -> s.id == songId }

                    _stablePlayerState.update {
                        it.copy(
                            currentSong = song,
                            // Duration might be C.TIME_UNSET if not yet known, ensure it's non-negative
                            totalDuration = playerCtrl.duration.coerceAtLeast(0L) // Use playerCtrl
                        )
                    }
                    // Reset position for the new song. If EOT handled, this is fine as player is paused.
                    // If not EOT, this correctly sets starting position for the new track.
                    _playerUiState.update { it.copy(currentPosition = 0L) }

                    song?.let { currentSongValue ->
                        viewModelScope.launch {
                            currentSongValue.albumArtUriString?.toUri()?.let { uri ->
                                extractAndGenerateColorScheme(uri)
                            }
                        }
                        updateFavoriteStatusForCurrentSong() // Depends on currentSong being set in _stablePlayerState
                    }
                } ?: _stablePlayerState.update {
                    // Handles case where mediaItem is null (e.g., end of playlist, queue cleared)
                    it.copy(currentSong = null, isPlaying = false, isCurrentSongFavorite = false)
                }
                // Other non-EOT related onMediaItemTransition logic for the new song can continue here if any.
                // The player's play/pause state (isPlaying) is managed by onIsPlayingChanged listener.
                // If EOT was handled, player was paused, onIsPlayingChanged(false) would have been triggered.
                // If EOT not handled and player was playing, it continues playing the new mediaItem.
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _stablePlayerState.update { it.copy(totalDuration = playerCtrl.duration.coerceAtLeast(0L)) } // Use playerCtrl
                }
                if (playbackState == Player.STATE_IDLE && playerCtrl.mediaItemCount == 0) { // Use playerCtrl
                    _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false) }
                    _playerUiState.update { it.copy(currentPosition = 0L) }
                }
                // Old EOT completion logic (based on _isEndOfTrackTimerActive and _endOfTrackSongId/EotStateHolder.eotTargetSongId) removed from here.
                // Assertive EOT actions in MusicService and natural EOT completion in onMediaItemTransition cover this.
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { _stablePlayerState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) } }
            override fun onRepeatModeChanged(repeatMode: Int) { _stablePlayerState.update { it.copy(repeatMode = repeatMode) } }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) updateCurrentPlaybackQueueFromPlayer(playerCtrl) // Pass playerCtrl
            }
        })
    }

    // Modificado para establecer una lista de reproducción
    // Modificar playSongs para que la cola sea la lista completa de allSongs si se inicia desde ahí
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None") {
        viewModelScope.launch {
            internalPlaySongs(songsToPlay, startSong, queueName)
        }
    }

    private suspend fun internalPlaySongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None") {
        // Old EOT deactivation logic removed, handled by eotSongMonitorJob
        mediaController?.let { controller ->
            // Si la lista de canciones a reproducir es la lista 'allSongs' (paginada),
            // idealmente deberíamos cargar todas las canciones para la cola.
            // Esto es un compromiso: o cargamos todo para la cola (puede ser lento),
            // o la cola se limita a lo ya cargado.
            // Por ahora, usaremos `songsToPlay` como viene.
            val mediaItems = songsToPlay.map { song ->
                val artworkBytes = loadArtworkData(song.albumArtUriString)
                val metadataBuilder = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    // .setAlbumTitle(song.album) // Opcional

                if (artworkBytes != null) {
                    metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                } else {
                    // Fallback: still set the URI if bytes are not available.
                    // Media3 might handle this URI loading asynchronously or it might be null.
                    // This is better than crashing if loadArtworkData fails.
                    // The original StrictMode violation might still occur if this URI is problematic AND resolution fails.
                    song.albumArtUriString?.toUri()?.let { metadataBuilder.setArtworkUri(it) }
                }

                val metadata = metadataBuilder.build()

                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.contentUriString.toUri())
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
                // The extractAndGenerateColorScheme call will happen via onMediaItemTransition listener setup,
                // so no need to explicitly call it here for startSong after this refactor.
                // The listener should pick up the current song and process its artwork URI.
                updateFavoriteStatusForCurrentSong() // This depends on stablePlayerState.currentSong, ensure it's updated timely.
            }
        }
        _playerUiState.update { it.copy(isLoadingInitialSongs = false) } // Marcar que la carga inicial de esta canción terminó
    }


    private fun loadAndPlaySong(song: Song) {
        mediaController?.let { controller ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(song.albumArtUriString?.toUri())
                // .setAlbumTitle(song.album) // Opcional: Considerar añadir si es útil
                .build()
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.contentUriString.toUri())
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
                song.albumArtUriString?.toUri()?.let { uri ->
                    extractAndGenerateColorScheme(uri, isPreload = false)
                }
            }
            //extractAndGeneratePalette(song.albumArtUri)
        }
    }

    private fun updateFavoriteStatusForCurrentSong() {
        val currentSongId = _stablePlayerState.value.currentSong?.id
        _stablePlayerState.update {
            it.copy(isCurrentSongFavorite = currentSongId != null && favoriteSongIds.value.contains(currentSongId))
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
                .setUri(song.contentUriString.toUri())
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.albumArtUriString?.toUri())
                    .build())
                .build()
            controller.addMediaItem(mediaItem)
            // Optionally update local queue state if not automatically handled by listeners
            // updateCurrentPlaybackQueueFromPlayer() // Call if needed and if it's safe
        }
    }

    // Función para ser llamada por AlbumGridItem
    fun getAlbumColorSchemeFlow(albumArtUri: String?): StateFlow<ColorSchemePair?> {
        val uriString = albumArtUri ?: "default_fallback_key" // Usar el operador Elvis para el valor por defecto

        // Devolver flujo existente si ya está en la caché en memoria.
        individualAlbumColorSchemes[uriString]?.let { return it }

        // Si el flujo no existe, créalo, encola la tarea (si es una URI válida) y devuélvelo.
        val newFlow = MutableStateFlow<ColorSchemePair?>(null)
        individualAlbumColorSchemes[uriString] = newFlow

        if (albumArtUri != null) { // Solo procesa URIs válidas, no la clave de fallback aquí.
            // urisBeingProcessed previene que la misma URI se encole múltiples veces si la llamada es rápida.
            // El procesador del canal (launchColorSchemeProcessor) se encargará de la generación.
            // No es necesario añadir a urisBeingProcessed aquí, launchColorSchemeProcessor lo maneja
            // al consumir del canal y antes de llamar a getOrGenerateColorSchemeForUri.
            // Sin embargo, el check original en launchColorSchemeProcessor es para evitar que se procese
            // algo que ya se está procesando activamente. La adición a urisBeingProcessed
            // debería ocurrir ANTES de enviar al canal para evitar que múltiples llamadas a esta función
            // encolen la misma URI antes de que el procesador la tome.

            // Sincronizar el acceso a urisBeingProcessed y el envío al canal
            synchronized(urisBeingProcessed) {
                if (!urisBeingProcessed.contains(uriString)) {
                    urisBeingProcessed.add(uriString) // Marcar como "intención de procesar"
                    val successfullySent = colorSchemeRequestChannel.trySend(albumArtUri) // Enviar a la cola para procesamiento
                    if (successfullySent.isSuccess) {
                        Log.d("PlayerViewModel", "Enqueued $uriString for color scheme processing.")
                    } else {
                        Log.w("PlayerViewModel", "Failed to enqueue $uriString, channel might be closed or full (if not UNLIMITED). Removing from urisBeingProcessed.")
                        urisBeingProcessed.remove(uriString) // Limpiar si no se pudo encolar
                    }
                } else {
                    Log.d("PlayerViewModel", "$uriString is already being processed or pending. Not re-enqueuing.")
                }
            }
        } else if (uriString == "default_fallback_key") {
            // Para la clave de fallback (URI nula), establece directamente el esquema por defecto.
            newFlow.value = ColorSchemePair(LightColorScheme, DarkColorScheme)
        }
        return newFlow
    }

    private fun launchColorSchemeProcessor() {
        viewModelScope.launch(Dispatchers.IO) { // Usar Dispatchers.IO para el bucle del canal
            for (albumArtUri in colorSchemeRequestChannel) { // Consume de la cola
                try {
                    Log.d("PlayerViewModel", "Processing $albumArtUri from queue.")
                    val scheme = getOrGenerateColorSchemeForUri(albumArtUri, false) // isPreload = false
                    individualAlbumColorSchemes[albumArtUri]?.value = scheme
                    Log.d("PlayerViewModel", "Finished processing $albumArtUri. Scheme: ${scheme != null}")
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Error processing $albumArtUri in ColorSchemeProcessor", e)
                    individualAlbumColorSchemes[albumArtUri]?.value = null // O un esquema de error/default
                } finally {
                    synchronized(urisBeingProcessed) {
                        urisBeingProcessed.remove(albumArtUri) // Eliminar de procesándose
                    }
                }
            }
        }
    }

    // Modificada para devolver el ColorSchemePair y ser usada por getAlbumColorSchemeFlow y la precarga
    private suspend fun getOrGenerateColorSchemeForUri(albumArtUri: String, isPreload: Boolean): ColorSchemePair? {
        val uriString = albumArtUri // uriString ya es el albumArtUri
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
            if (isDark) DarkColorScheme else LightColorScheme
            val placeholderColor = Color.Magenta
            return ColorScheme(
                primary = sv.primary.toComposeColor(), onPrimary = sv.onPrimary.toComposeColor(), primaryContainer = sv.primaryContainer.toComposeColor(), onPrimaryContainer = sv.onPrimaryContainer.toComposeColor(),
                secondary = sv.secondary.toComposeColor(), onSecondary = sv.onSecondary.toComposeColor(), secondaryContainer = sv.secondaryContainer.toComposeColor(), onSecondaryContainer = sv.onSecondaryContainer.toComposeColor(),
                tertiary = sv.tertiary.toComposeColor(), onTertiary = sv.onTertiary.toComposeColor(), tertiaryContainer = sv.tertiaryContainer.toComposeColor(), onTertiaryContainer = sv.onTertiaryContainer.toComposeColor(),
                background = sv.background.toComposeColor(), onBackground = sv.onBackground.toComposeColor(), surface = sv.surface.toComposeColor(), onSurface = sv.onSurface.toComposeColor(),
                surfaceVariant = sv.surfaceVariant.toComposeColor(), onSurfaceVariant = sv.onSurfaceVariant.toComposeColor(), error = sv.error.toComposeColor(), onError = sv.onError.toComposeColor(),
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

    private suspend fun loadArtworkData(uriString: String?): ByteArray? {
        if (uriString == null) return null
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uriString.toUri())
                    .size(Size(256, 256)) // Consistent with MusicService, can be adjusted
                    .allowHardware(false) // Important for direct manipulation/conversion
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .build()
                val drawable = context.imageLoader.execute(request).drawable
                drawable?.let {
                    val bitmap = it.toBitmap(
                        width = it.intrinsicWidth.coerceAtMost(256).coerceAtLeast(1),
                        height = it.intrinsicHeight.coerceAtMost(256).coerceAtLeast(1),
                        config = Bitmap.Config.ARGB_8888
                    )

                    val stream = java.io.ByteArrayOutputStream()
                    // Use WEBP for better quality/compression if possible, or JPEG with decent quality.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, stream)
                    } else {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    }
                    stream.toByteArray()
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading artwork data for MediaMetadata: $uriString", e)
                null
            }
        }
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
        // _playerUiState.update { it.copy(currentFavoriteSortOption = sortOption) } // Ya no se actualiza aquí
        _currentFavoriteSortOptionStateFlow.value = sortOption // Actualizar el StateFlow dedicado
        // The actual sorting is handled by the 'favoriteSongs' StateFlow reacting to 'currentFavoriteSortOptionStateFlow'.
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
            val history = withContext(Dispatchers.IO) {
                musicRepository.getRecentSearchHistory(limit)
            }
            _playerUiState.update { it.copy(searchHistory = history.toImmutableList()) }
        }
    }

    fun performSearch(query: String) {
        viewModelScope.launch {
            try {
                if (query.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory() // Refresh history after adding new item (loadSearchHistory also needs to be checked)
                }

                if (query.isBlank()) {
                    _playerUiState.update { it.copy(searchResults = persistentListOf()) }
                    // Opcionalmente, puedes decidir si quieres cargar el historial de búsqueda aquí también
                    // loadSearchHistory()
                    return@launch
                }

                val currentFilter = _playerUiState.value.selectedSearchFilter

                // Colecta la lista de resultados del Flow.
                // Asumimos que musicRepository.searchAll devuelve Flow<List<SearchResultItem>>
                // y que _playerUiState.value.searchResults espera ImmutableList<SearchResultItem>
                val resultsList: List<SearchResultItem> = withContext(Dispatchers.IO) {
                    musicRepository.searchAll(query, currentFilter).first()
                }

                _playerUiState.update { it.copy(searchResults = resultsList.toImmutableList()) }

            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error performing search for query: $query", e)
                _playerUiState.update {
                    it.copy(
                        searchResults = persistentListOf(), // Limpiar resultados en caso de error
                        // Opcional: añadir un campo de error específico para la búsqueda en el UiState
                        // errorSearch = "Search failed: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun deleteSearchHistoryItem(query: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                musicRepository.deleteSearchHistoryItemByQuery(query)
            }
            loadSearchHistory() // Refresh the list
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                musicRepository.clearSearchHistory()
            }
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

    fun dismissPlaylistAndShowUndo() {
        viewModelScope.launch {
            val songToDismiss = _stablePlayerState.value.currentSong
            val queueToDismiss = _playerUiState.value.currentPlaybackQueue
            val queueNameToDismiss = _playerUiState.value.currentQueueSourceName
            val positionToDismiss = _playerUiState.value.currentPosition

            if (songToDismiss == null && queueToDismiss.isEmpty()) {
                // Nothing to dismiss
                return@launch
            }

            Log.d("PlayerViewModel", "Dismissing playlist. Song: ${songToDismiss?.title}, Queue size: ${queueToDismiss.size}")

            // Store state for potential undo
            _playerUiState.update {
                it.copy(
                    dismissedSong = songToDismiss,
                    dismissedQueue = queueToDismiss,
                    dismissedQueueName = queueNameToDismiss,
                    dismissedPosition = positionToDismiss,
                    showDismissUndoBar = true
                )
            }

            // Stop playback and clear current player state
            mediaController?.stop() // This should also clear Media3's playlist
            mediaController?.clearMediaItems() // Ensure items are cleared

            _stablePlayerState.update {
                it.copy(
                    currentSong = null,
                    isPlaying = false,
                    totalDuration = 0L,
                    isCurrentSongFavorite = false
                )
            }
            _playerUiState.update {
                it.copy(
                    currentPosition = 0L,
                    currentPlaybackQueue = persistentListOf(),
                    currentQueueSourceName = ""
                )
            }
            _isSheetVisible.value = false // Hide the player sheet

            // Launch timer to hide the undo bar
            launch {
                delay(_playerUiState.value.undoBarVisibleDuration)
                // Only hide if it's still showing (i.e., undo wasn't pressed)
                if (_playerUiState.value.showDismissUndoBar) {
                    _playerUiState.update { it.copy(showDismissUndoBar = false, dismissedSong = null, dismissedQueue = persistentListOf()) }
                }
            }
        }
    }

    fun undoDismissPlaylist() {
        viewModelScope.launch {
            val songToRestore = _playerUiState.value.dismissedSong
            val queueToRestore = _playerUiState.value.dismissedQueue
            val queueNameToRestore = _playerUiState.value.dismissedQueueName
            val positionToRestore = _playerUiState.value.dismissedPosition

            if (songToRestore != null && queueToRestore.isNotEmpty()) {
                // Restore the playlist and song
                playSongs(queueToRestore.toList(), songToRestore, queueNameToRestore) // playSongs handles setting media items and playing

                // Seek to the original position after media is prepared
                // Need to observe player state or add a callback to know when it's safe to seek.
                // For simplicity, adding a small delay, but a more robust solution would be better.
                delay(500) // Small delay to allow player to prepare
                mediaController?.seekTo(positionToRestore)


                _playerUiState.update {
                    it.copy(
                        showDismissUndoBar = false, // Hide undo bar
                        dismissedSong = null,
                        dismissedQueue = persistentListOf(),
                        dismissedQueueName = "",
                        dismissedPosition = 0L
                    )
                }
                _isSheetVisible.value = true // Ensure player sheet is visible again
                _sheetState.value = PlayerSheetState.COLLAPSED // Start collapsed

                Log.d("PlayerViewModel", "Playlist restored. Song: ${songToRestore.title}")
                _toastEvents.emit("Playlist restored")
            } else {
                // Nothing to restore, hide bar anyway
                _playerUiState.update { it.copy(showDismissUndoBar = false) }
            }
        }
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

    fun saveLastLibraryTabIndex(tabIndex: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveLastLibraryTabIndex(tabIndex)
        }
    }
}