package com.theveloper.pixelplay.presentation.viewmodel

import android.annotation.SuppressLint
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
import kotlinx.coroutines.joinAll
import androidx.core.graphics.drawable.toBitmap
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.worker.SyncManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import android.os.Trace // Import Trace
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.ai.AiMetadataGenerator
import com.theveloper.pixelplay.data.ai.AiPlaylistGenerator
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Lyrics
import timber.log.Timber
import com.theveloper.pixelplay.ui.theme.GenreColors
import com.theveloper.pixelplay.utils.toHexString
import kotlinx.coroutines.flow.Flow
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

// Estado para datos que no cambian cada segundo
data class StablePlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val totalDuration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isCurrentSongFavorite: Boolean = false,
    val lyrics: Lyrics? = null,
    val isLoadingLyrics: Boolean = false
)

data class PlayerUiState(
    // currentSong, isPlaying, totalDuration, shuffle, repeat, favorite se mueven a StablePlayerState
    val currentPosition: Long = 0L, // Este se actualiza frecuentemente
    val isLoadingInitialSongs: Boolean = true,
    val isGeneratingAiMetadata: Boolean = false,
    // val isLoadingMoreSongs: Boolean = false, // Removed
    val allSongs: ImmutableList<Song> = persistentListOf(),
    // val canLoadMoreSongs: Boolean = true, // Removed
    val currentPlaybackQueue: ImmutableList<Song> = persistentListOf(),
    val currentQueueSourceName: String = "All Songs",
    val lavaLampColors: ImmutableList<Color> = persistentListOf(),
    val albums: ImmutableList<Album> = persistentListOf(),
    val artists: ImmutableList<Artist> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = false, // Default to false, might need separate flags for albums/artists
    // val canLoadMoreAlbums: Boolean = true, // Removed
    // val canLoadMoreArtists: Boolean = true, // Removed
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

// Estado para la UI de búsqueda de letras
sealed interface LyricsSearchUiState {
    object Idle : LyricsSearchUiState
    object Loading : LyricsSearchUiState
    data class Success(val lyrics: Lyrics) : LyricsSearchUiState
    data class Error(val message: String) : LyricsSearchUiState
}

@UnstableApi
@SuppressLint("LogNotTimber")
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    private val syncManager: SyncManager, // Inyectar SyncManager
    private val songMetadataEditor: SongMetadataEditor,
    private val dailyMixManager: DailyMixManager,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val aiMetadataGenerator: AiMetadataGenerator
) : ViewModel() {

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()
    private val _masterAllSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
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

    // AI Playlist Generation State
    private val _showAiPlaylistSheet = MutableStateFlow(false)
    val showAiPlaylistSheet: StateFlow<Boolean> = _showAiPlaylistSheet.asStateFlow()

    private val _isGeneratingAiPlaylist = MutableStateFlow(false)
    val isGeneratingAiPlaylist: StateFlow<Boolean> = _isGeneratingAiPlaylist.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _selectedSongForInfo = MutableStateFlow<Song?>(null)
    val selectedSongForInfo: StateFlow<Song?> = _selectedSongForInfo.asStateFlow()

    private val _currentAlbumArtColorSchemePair = MutableStateFlow<ColorSchemePair?>(null)
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = _currentAlbumArtColorSchemePair.asStateFlow()
    // Global and Player theme preferences are now managed by UserPreferencesRepository,
    // but PlayerViewModel still needs to observe them to react to changes.
    //val globalThemePreference: StateFlow<String> = userPreferencesRepository.globalThemePreferenceFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.DYNAMIC)
    val playerThemePreference: StateFlow<String> = userPreferencesRepository.playerThemePreferenceFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.GLOBAL)

    val navBarCornerRadius: StateFlow<Int> = userPreferencesRepository.navBarCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 32)

    private val _isInitialThemePreloadComplete = MutableStateFlow(false)
    val isInitialThemePreloadComplete: StateFlow<Boolean> = _isInitialThemePreloadComplete.asStateFlow()

    // Sleep Timer StateFlows
    private val _sleepTimerEndTimeMillis = MutableStateFlow<Long?>(null)
    val sleepTimerEndTimeMillis: StateFlow<Long?> = _sleepTimerEndTimeMillis.asStateFlow()

    private val _isEndOfTrackTimerActive = MutableStateFlow<Boolean>(false)
    val isEndOfTrackTimerActive: StateFlow<Boolean> = _isEndOfTrackTimerActive.asStateFlow()

    private val _activeTimerValueDisplay = MutableStateFlow<String?>(null)
    val activeTimerValueDisplay: StateFlow<String?> = _activeTimerValueDisplay.asStateFlow()

    private val _lyricsSearchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val lyricsSearchUiState = _lyricsSearchUiState.asStateFlow()

    private var sleepTimerJob: Job? = null
    // private val _endOfTrackSongId = MutableStateFlow<String?>(null) // Removed, EotStateHolder is the source of truth
    private var eotSongMonitorJob: Job? = null

    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    fun sendToast(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(message)
        }
    }

    // Last Library Tab Index
    val lastLibraryTabIndexFlow: StateFlow<Int> =
        userPreferencesRepository.lastLibraryTabIndexFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Default to Songs tab
        )

    

    // CAMBIO 1: Añadir estado para rastrear las pestañas ya cargadas.
    private val _loadedTabs = MutableStateFlow(emptySet<Int>())

    // CAMBIO 3: Mover la lógica de las opciones de ordenamiento al ViewModel.
    val availableSortOptions: StateFlow<List<SortOption>> =
        lastLibraryTabIndexFlow.map { tabIndex ->
            Trace.beginSection("PlayerViewModel.availableSortOptionsMapping")
            val options = when (tabIndex) {
                0 -> listOf(
                    SortOption.SongTitleAZ,
                    SortOption.SongTitleZA,
                    SortOption.SongArtist,
                    SortOption.SongAlbum,
                    SortOption.SongDateAdded,
                    SortOption.SongDuration
                )
                1 -> listOf(
                    SortOption.AlbumTitleAZ,
                    SortOption.AlbumTitleZA,
                    SortOption.AlbumArtist,
                    SortOption.AlbumReleaseYear
                )
                2 -> listOf(SortOption.ArtistNameAZ, SortOption.ArtistNameZA)
                3 -> listOf( // Assuming Playlist sort options might exist
                    SortOption.PlaylistNameAZ,
                    SortOption.PlaylistNameZA,
                    SortOption.PlaylistDateCreated
                )
                4 -> listOf(
                    SortOption.LikedSongTitleAZ,
                    SortOption.LikedSongTitleZA,
                    SortOption.LikedSongArtist,
                    SortOption.LikedSongAlbum,
                    SortOption.LikedSongDateLiked
                )
                else -> emptyList()
            }
            Trace.endSection()
            options
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf( // Provide a default initial value based on initialTab index 0
                SortOption.SongTitleAZ, SortOption.SongTitleZA, SortOption.SongArtist,
                SortOption.SongAlbum, SortOption.SongDateAdded, SortOption.SongDuration
            )
        )

    // StateFlow to hold the sync status, converted from syncManager.isSyncing (Flow)
    // Initial value true, as we might assume sync is active on app start until proven otherwise.
    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Assuming sync might be initially active
        )

    private val _isInitialDataLoaded = MutableStateFlow(false) // Flag to prevent double loading

    // Paginación - REMOVED
    // private var currentSongPage = 1
    // private var currentAlbumPage = 1
    // private var currentArtistPage = 1

    // Flow dedicado sólo a la lista de canciones:
    val allSongsFlow: StateFlow<List<Song>> =
        _playerUiState
            .map { it.allSongs }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val genres: StateFlow<ImmutableList<Genre>> = allSongsFlow
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

            genreMap.toList().mapIndexed { index, (genreName, songs) ->
                if (songs.isNotEmpty()) {
                    val id = if (genreName.equals(unknownGenreName, ignoreCase = true)) "unknown" else genreName.lowercase().replace(" ", "_")
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
            }.filterNotNull()
                .sortedBy { it.name } // Sort genres alphabetically
                .toImmutableList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    // val activeGlobalColorSchemePair: StateFlow<ColorSchemePair?> = combine( // Removed
    //     globalThemePreference, _currentAlbumArtColorSchemePair
    // ) { globalPref, albumScheme ->
    //     when (globalPref) {
    //         ThemePreference.ALBUM_ART -> albumScheme
    //         ThemePreference.DYNAMIC -> null
    //         ThemePreference.DEFAULT -> ColorSchemePair(LightColorScheme, DarkColorScheme)
    //         else -> null
    //     }
    // }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = combine(
        playerThemePreference, _currentAlbumArtColorSchemePair
    ) { playerPref, albumScheme ->
        when (playerPref) {
            ThemePreference.ALBUM_ART -> albumScheme
            ThemePreference.DYNAMIC -> null // Signal to use system's MaterialTheme.colorScheme
            ThemePreference.DEFAULT -> null // Effectively makes DEFAULT same as DYNAMIC (use system theme)
            // ThemePreference.GLOBAL has been removed from defaults and options
            else -> albumScheme // Fallback to album art if preference is somehow unknown or old 'GLOBAL'
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null) // Initial value null (system theme)

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
        _masterAllSongs, // Depende de la lista maestra, no de la lista ordenada de la UI
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

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private var dailyMixJob: Job? = null

    private fun updateDailyMix() {
        // Cancel any previous job to avoid multiple updates running
        dailyMixJob?.cancel()
        dailyMixJob = viewModelScope.launch(Dispatchers.IO) {
            // We need all songs to generate the mix
            val allSongs = allSongsFlow.first()
            if (allSongs.isNotEmpty()) {
                val mix = dailyMixManager.generateDailyMix(allSongs)
                _dailyMixSongs.value = mix.toImmutableList()
                // Save the new mix
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })
            }
        }
    }

    private fun loadPersistedDailyMix() {
        viewModelScope.launch {
            // Combine the flow of persisted IDs with the flow of all songs
            userPreferencesRepository.dailyMixSongIdsFlow.combine(allSongsFlow) { ids, allSongs ->
                if (ids.isNotEmpty() && allSongs.isNotEmpty()) {
                    // Create a map for quick lookups
                    val songMap = allSongs.associateBy { it.id }
                    // Reconstruct the playlist in the correct order
                    ids.mapNotNull { songMap[it] }.toImmutableList()
                } else {
                    persistentListOf()
                }
            }.collect { persistedMix ->
                // Only update if the current mix is empty, to avoid overwriting a newly generated one
                if (_dailyMixSongs.value.isEmpty() && persistedMix.isNotEmpty()) {
                    _dailyMixSongs.value = persistedMix
                }
            }
        }
    }

    fun forceUpdateDailyMix() {
        viewModelScope.launch {
            updateDailyMix()
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    private var progressJob: Job? = null

    fun incrementSongScore(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dailyMixManager.incrementScore(songId)
        }
    }

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
        Trace.beginSection("PlayerViewModel.init")
        Log.i("PlayerViewModel", "init started.")

        // Load initial sort options ONCE at startup.
        viewModelScope.launch {
            val initialSongSort = getSortOptionFromString(userPreferencesRepository.songsSortOptionFlow.first()) ?: SortOption.SongTitleAZ
            val initialAlbumSort = getSortOptionFromString(userPreferencesRepository.albumsSortOptionFlow.first()) ?: SortOption.AlbumTitleAZ
            val initialArtistSort = getSortOptionFromString(userPreferencesRepository.artistsSortOptionFlow.first()) ?: SortOption.ArtistNameAZ
            val initialLikedSort = getSortOptionFromString(userPreferencesRepository.likedSongsSortOptionFlow.first()) ?: SortOption.LikedSongTitleAZ

            _playerUiState.update {
                it.copy(
                    currentSongSortOption = initialSongSort,
                    currentAlbumSortOption = initialAlbumSort,
                    currentArtistSortOption = initialArtistSort,
                    currentFavoriteSortOption = initialLikedSort
                )
            }
            // Also update the dedicated flow for favorites to ensure consistency
            _currentFavoriteSortOptionStateFlow.value = initialLikedSort
        }

        launchColorSchemeProcessor()
        loadPersistedDailyMix()

        viewModelScope.launch {
            isSyncingStateFlow.collect { isSyncing ->
                val oldSyncingLibraryState = _playerUiState.value.isSyncingLibrary
                _playerUiState.update { it.copy(isSyncingLibrary = isSyncing) }

        if (oldSyncingLibraryState && !isSyncing) {
            Log.i("PlayerViewModel", "Sync completed. Calling resetAndLoadInitialData from isSyncingStateFlow observer.")
                    resetAndLoadInitialData("isSyncingStateFlow observer")
                }
            }
        }

        viewModelScope.launch {
            // This check ensures that if sync is already complete (e.g. on subsequent app starts or if sync was very fast)
            // and data hasn't been loaded yet, it gets loaded.
            if (!isSyncingStateFlow.value && !_isInitialDataLoaded.value && _playerUiState.value.allSongs.isEmpty()) {
                Log.i("PlayerViewModel", "Initial check: Sync not active and initial data not loaded. Calling resetAndLoadInitialData.")
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

        Trace.endSection() // End PlayerViewModel.init
    }

    fun onMainActivityStart() {
        Trace.beginSection("PlayerViewModel.onMainActivityStart")
        preloadThemesAndInitialData()
        checkAndUpdateDailyMixIfNeeded()
        Trace.endSection()
    }

    private fun checkAndUpdateDailyMixIfNeeded() {
        viewModelScope.launch {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
            val lastUpdateDay = java.util.Calendar.getInstance().apply { timeInMillis = lastUpdate }.get(java.util.Calendar.DAY_OF_YEAR)

            if (today != lastUpdateDay) {
                updateDailyMix()
                userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            }
        }
    }

    private fun preloadThemesAndInitialData() {
        Trace.beginSection("PlayerViewModel.preloadThemesAndInitialData")
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
            if (isSyncingStateFlow.value && !_isInitialDataLoaded.value) {
                 Log.i("PlayerViewModel", "preloadThemesAndInitialData: Sync is active and initial data not yet loaded, deferring initial load to sync completion handler.")
            } else if (!_isInitialDataLoaded.value && _playerUiState.value.allSongs.isEmpty()) { // Check _isInitialDataLoaded
                Log.i("PlayerViewModel", "preloadThemesAndInitialData: Sync not active or already finished, and initial data not loaded. Calling resetAndLoadInitialData from preload.")
                resetAndLoadInitialData("preloadThemesAndInitialData")
            } else {
                Log.i("PlayerViewModel", "preloadThemesAndInitialData: Initial data already loaded or sync is active and will trigger load. Skipping direct call to resetAndLoadInitialData from preload.")
            }
            //Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData() call took ${System.currentTimeMillis() - resetLoadDataStartTime} ms (Note: actual loading is async). Time from overallInitStart: ${System.currentTimeMillis() - overallInitStartTime} ms")

            // The UI will observe isLoadingInitialSongs and isLoadingLibraryCategories directly.
            // We set _isInitialThemePreloadComplete to true immediately after dispatching async work.
            _isInitialThemePreloadComplete.value = true
            val timeToComplete = System.currentTimeMillis() - overallInitStartTime
            Log.d("PlayerViewModelPerformance", "Initial theme preload complete (async data loading dispatched). Total time since overallInitStart: ${timeToComplete} ms")
        }
        Log.d("PlayerViewModelPerformance", "preloadThemesAndInitialData END. Total function time: ${System.currentTimeMillis() - functionStartTime} ms (dispatching async work)")
        Trace.endSection() // End PlayerViewModel.preloadThemesAndInitialData
    }

    // Nueva función para carga paralela
    private fun loadInitialLibraryDataParallel() {
        _playerUiState.update {
            it.copy(
                isLoadingInitialSongs = true,
                isLoadingLibraryCategories = true // Marcar ambos como cargando
            )
        }

        val songsJob = viewModelScope.launch {
            Log.d("PlayerViewModel", "Loading songs in parallel...")
            try {
                val songsList = musicRepository.getAudioFiles().first()
                _masterAllSongs.value = songsList.toImmutableList()

                // Apply initial sort to the displayed list
                val sortedSongs = when (_playerUiState.value.currentSongSortOption) {
                    SortOption.SongTitleAZ -> songsList.sortedBy { it.title }
                    SortOption.SongTitleZA -> songsList.sortedByDescending { it.title }
                    SortOption.SongArtist -> songsList.sortedBy { it.artist }
                    SortOption.SongAlbum -> songsList.sortedBy { it.album }
                    SortOption.SongDateAdded -> songsList.sortedByDescending { it.albumId }
                    SortOption.SongDuration -> songsList.sortedBy { it.duration }
                    else -> songsList
                }.toImmutableList()

                _playerUiState.update { currentState ->
                    currentState.copy(
                        allSongs = sortedSongs,
                        isLoadingInitialSongs = false
                    )
                }
                Log.d("PlayerViewModel", "Songs loaded in parallel. Master count: ${songsList.size}")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading songs in parallel", e)
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }
        }

        val albumsJob = viewModelScope.launch {
            Log.d("PlayerViewModel", "Loading albums in parallel...")
            try {
                val albumsList = musicRepository.getAllAlbumsOnce()
                _playerUiState.update { it.copy(albums = albumsList.toImmutableList()) }
                Log.d("PlayerViewModel", "Albums loaded in parallel. Count: ${albumsList.size}")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading albums in parallel", e)
            }
        }

        val artistsJob = viewModelScope.launch {
            Log.d("PlayerViewModel", "Loading artists in parallel...")
            try {
                val artistsList = musicRepository.getAllArtistsOnce()
                _playerUiState.update { it.copy(artists = artistsList.toImmutableList()) }
                Log.d("PlayerViewModel", "Artists loaded in parallel. Count: ${artistsList.size}")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading artists in parallel", e)
            }
        }

        viewModelScope.launch {
            try {
                joinAll(songsJob, albumsJob, artistsJob) // Esperar a que todas las cargas finalicen
                Log.d("PlayerViewModel", "All parallel loads (songs, albums, artists) completed.")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error during parallel data loading completion", e)
            } finally {
                _playerUiState.update {
                    it.copy(
                        isLoadingInitialSongs = false, // Asegurarse de que estén en false
                        isLoadingLibraryCategories = false
                    )
                }
                _isInitialDataLoaded.value = true
                Log.d("PlayerViewModel", "_isInitialDataLoaded set to true after all parallel loads completed.")
            }
        }
    }

    private fun resetAndLoadInitialData(caller: String = "Unknown") {
        Trace.beginSection("PlayerViewModel.resetAndLoadInitialData")
        val functionStartTime = System.currentTimeMillis()
        Log.i("PlayerViewModel", "resetAndLoadInitialData called from: $caller. Proceeding with load.")
        Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData START - Called by: $caller")

        // Reset relevant parts of UI state is handled by loadInitialLibraryDataParallel
        // by setting isLoading flags to true at its beginning.

        loadInitialLibraryDataParallel() // Call the new parallel loading function
        updateDailyMix()

        // Initial load for albums and artists will be triggered by their respective tabs if needed.
        Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData END (dispatching parallel async work). Total function time: ${System.currentTimeMillis() - functionStartTime} ms")
        Trace.endSection() // End PlayerViewModel.resetAndLoadInitialData
    }

    // This function might still be called by loadSongsIfNeeded,
    // but _isInitialDataLoaded should now be primarily managed by loadInitialLibraryDataParallel
    private fun loadSongsFromRepository() {
        Log.d("PlayerViewModel", "loadSongsFromRepository called (potentially for individual tab load or refresh).")
        // No longer need checks for isLoadingMoreSongs or canLoadMoreSongs

        viewModelScope.launch { // Default dispatcher is Main.Immediate which is fine for launching.
            Trace.beginSection("PlayerViewModel.loadSongsFromRepository_coroutine")
            val functionStartTime = System.currentTimeMillis()
            Log.d("PlayerViewModelPerformance", "loadSongsFromRepository (Single Action) START")

            if (!_playerUiState.value.isLoadingInitialSongs) {
                 _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            }

            try {
                val repoCallStartTime = System.currentTimeMillis()
                val allSongsList: List<Song> = musicRepository.getAudioFiles().first()
                val repoCallDuration = System.currentTimeMillis() - repoCallStartTime
                Log.d("PlayerViewModelPerformance", "musicRepository.getAudioFiles (Single Action) took $repoCallDuration ms for ${allSongsList.size} songs.")

                _playerUiState.update { currentState ->
                    currentState.copy(
                        allSongs = allSongsList.toImmutableList(),
                        isLoadingInitialSongs = false
                    )
                }
                // _isInitialDataLoaded.value = true; // This flag is now set by loadInitialLibraryDataParallel
                Log.d("PlayerViewModel", "allSongs updated by loadSongsFromRepository. New size: ${_playerUiState.value.allSongs.size}. isLoadingInitialSongs: ${_playerUiState.value.isLoadingInitialSongs}.")

                val totalFunctionTime = System.currentTimeMillis() - functionStartTime
                Log.d("PlayerViewModelPerformance", "loadSongsFromRepository (Single Action) END. Data update complete. Total time: $totalFunctionTime ms")

            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading songs from repository (Single Action)", e)
                _playerUiState.update {
                    it.copy(isLoadingInitialSongs = false)
                }
                val totalFunctionTime = System.currentTimeMillis() - functionStartTime
                Log.d("PlayerViewModelPerformance", "loadSongsFromRepository (Single Action) FAILED. Total time: $totalFunctionTime ms")
            } finally {
                Trace.endSection() // End PlayerViewModel.loadSongsFromRepository_coroutine
            }
        }
    }

    // fun loadMoreSongs() { // REMOVED
    //     loadSongsFromRepository()
    // }

    // Funciones para cargar álbumes
    private fun loadAlbumsFromRepository() {
        // No longer need checks for isLoadingLibraryCategories (for more) or canLoadMoreAlbums
        Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository (All) called.")

        viewModelScope.launch {
            Trace.beginSection("PlayerViewModel.loadAlbumsFromRepository_coroutine")
            val functionStartTime = System.currentTimeMillis()
            Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository (All) START")

            // Use isLoadingLibraryCategories for initial load of albums for now.
            // Consider separate isLoadingAlbums if more granularity is needed.
            _playerUiState.update { it.copy(isLoadingLibraryCategories = true) }

            try {
                val repoCallAlbumsStartTime = System.currentTimeMillis()
                // Usar la nueva función suspend del repositorio
                val allAlbumsList: List<Album> = musicRepository.getAllAlbumsOnce()
                val albumsLoadDuration = System.currentTimeMillis() - repoCallAlbumsStartTime
                Log.d("PlayerViewModelPerformance", "musicRepository.getAllAlbumsOnce (All) took $albumsLoadDuration ms for ${allAlbumsList.size} albums.")

                _playerUiState.update { currentState ->
                    currentState.copy(
                        albums = allAlbumsList.toImmutableList(),
                        isLoadingLibraryCategories = false
                    )
                }
                Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository (All) END. Total time: ${System.currentTimeMillis() - functionStartTime} ms. Albums loaded: ${allAlbumsList.size}")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading all albums from getAllAlbumsOnce", e)
                _playerUiState.update { it.copy(isLoadingLibraryCategories = false) }
            } finally {
                Trace.endSection() // End PlayerViewModel.loadAlbumsFromRepository_coroutine
            }
        }
    }

    fun loadSongsIfNeeded() {
        val songsEmpty = _playerUiState.value.allSongs.isEmpty()
        val notLoading = !_playerUiState.value.isLoadingInitialSongs

        Log.d("PlayerViewModel", "loadSongsIfNeeded: songsEmpty=$songsEmpty, notLoadingInitialSongs=$notLoading")
        if (songsEmpty && notLoading) {
            Log.i("PlayerViewModel", "loadSongsIfNeeded: Conditions met. Loading all songs.")
            loadSongsFromRepository()
        } else {
            var reason = ""
            if (!songsEmpty) reason += "Songs not empty. "
            if (!notLoading) reason += "Currently loading initial songs. "
            Log.w("PlayerViewModel", "loadSongsIfNeeded: Conditions NOT met. Skipping load. Reason: $reason")
        }
    }

    // fun loadMoreAlbums() { // REMOVED
    // }

    fun loadAlbumsIfNeeded() {
        val albumsEmpty = _playerUiState.value.albums.isEmpty()
        // val canLoadMore = _playerUiState.value.canLoadMoreAlbums // Removed
        val notLoading = !_playerUiState.value.isLoadingLibraryCategories // Still relevant for initial load
        Log.d("PlayerViewModel", "loadAlbumsIfNeeded: albumsEmpty=$albumsEmpty, notLoadingLibraryCategories=$notLoading")
        if (albumsEmpty && notLoading) { // Simplified condition
            Log.i("PlayerViewModel", "loadAlbumsIfNeeded: Conditions met. Loading all albums.")
            loadAlbumsFromRepository() // No isInitialLoad parameter
        } else {
            var reason = ""
            if (!albumsEmpty) reason += "Albums not empty. "
            if (!notLoading) reason += "Currently loading library categories. "
            Log.w("PlayerViewModel", "loadAlbumsIfNeeded: Conditions NOT met. Skipping load. Reason: $reason")
        }
    }

    // Funciones para cargar artistas
    private fun loadArtistsFromRepository() {
        // No longer need checks for isLoadingLibraryCategories (for more) or canLoadMoreArtists
        Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository (All) called.")

        viewModelScope.launch {
            val functionStartTime = System.currentTimeMillis()
            Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository (All) START")

            // Use isLoadingLibraryCategories for initial load of artists for now.
            _playerUiState.update { it.copy(isLoadingLibraryCategories = true) }

            try {
                val repoCallArtistsStartTime = System.currentTimeMillis()
                // Usar la nueva función suspend del repositorio
                val allArtistsList: List<Artist> = musicRepository.getAllArtistsOnce()
                val artistsLoadDuration = System.currentTimeMillis() - repoCallArtistsStartTime
                Log.d("PlayerViewModelPerformance", "musicRepository.getAllArtistsOnce (All) took $artistsLoadDuration ms for ${allArtistsList.size} artists.")

                _playerUiState.update { currentState ->
                    currentState.copy(
                        artists = allArtistsList.toImmutableList(),
                        isLoadingLibraryCategories = false
                    )
                }
                 Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository (All) END. Total time: ${System.currentTimeMillis() - functionStartTime} ms. Artists loaded: ${allArtistsList.size}")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading all artists from getAllArtistsOnce", e)
                _playerUiState.update { it.copy(isLoadingLibraryCategories = false) }
            }
        }
    }

    // fun loadMoreArtists() { // REMOVED
    // }

    fun loadArtistsIfNeeded() {
        val artistsEmpty = _playerUiState.value.artists.isEmpty()
        // val canLoadMore = _playerUiState.value.canLoadMoreArtists // Removed
        val notLoading = !_playerUiState.value.isLoadingLibraryCategories // Still relevant for initial load
        Log.d("PlayerViewModel", "loadArtistsIfNeeded: artistsEmpty=$artistsEmpty, notLoadingLibraryCategories=$notLoading")
        if (artistsEmpty && notLoading) { // Simplified condition
            Log.i("PlayerViewModel", "loadArtistsIfNeeded: Conditions met. Loading all artists.")
            loadArtistsFromRepository() // No isInitialLoad parameter
        } else {
            var reason = ""
            if (!artistsEmpty) reason += "Artists not empty. "
            if (!notLoading) reason += "Currently loading library categories. "
            Log.w("PlayerViewModel", "loadArtistsIfNeeded: Conditions NOT met. Skipping load. Reason: $reason")
        }
    }

    // showAndPlaySong ahora usa playSongs con la lista de contexto proporcionada.
    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true
    ) {
        // Utiliza la lista de canciones del contexto actual (ej: canciones de un género específico) como la cola.
        if (isVoluntaryPlay) {
            incrementSongScore(song.id)
        }
        playSongs(contextSongs, song, queueName, null)
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
                    playSongs(songsList, songsList.first(), album.title, null)
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
                    playSongs(songsList, songsList.first(), artist.name, null)
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
        _sheetState.value = if (_sheetState.value == PlayerSheetState.COLLAPSED) {
            PlayerSheetState.EXPANDED
        } else {
            PlayerSheetState.COLLAPSED
        }
        _predictiveBackCollapseFraction.value = 0f
    }

    fun expandPlayerSheet() {
        _sheetState.value = PlayerSheetState.EXPANDED
        _predictiveBackCollapseFraction.value = 0f
    }

    fun collapsePlayerSheet() {
        _sheetState.value = PlayerSheetState.COLLAPSED
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
        Trace.beginSection("PlayerViewModel.setupMediaControllerListeners")
        val playerCtrl = mediaController ?: return Trace.endSection() // Renamed to avoid shadowing, ensure trace ends if early exit
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

                        loadLyricsForCurrentSong()
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
        Trace.endSection() // End PlayerViewModel.setupMediaControllerListeners
    }

    // Modificado para establecer una lista de reproducción
    // Modificar playSongs para que la cola sea la lista completa de allSongs si se inicia desde ahí
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        viewModelScope.launch {
            internalPlaySongs(songsToPlay, startSong, queueName, playlistId)
        }
    }

    private suspend fun internalPlaySongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        Log.d("PlayerViewModel_MediaItem", "internalPlaySongs called. Songs count: ${songsToPlay.size}, StartSong: ${startSong.title}, QueueName: $queueName")
        Log.d("PlayerViewModel_MediaItem", "internalPlaySongs: mediaController is null: ${mediaController == null}")

        // Old EOT deactivation logic removed, handled by eotSongMonitorJob
        mediaController?.let { controller ->
            // Si la lista de canciones a reproducir es la lista 'allSongs' (paginada),
            // idealmente deberíamos cargar todas las canciones para la cola.
            // Esto es un compromiso: o cargamos todo para la cola (puede ser lento),
            // o la cola se limita a lo ya cargado.
            // Por ahora, usaremos `songsToPlay` como viene.
            val mediaItems = songsToPlay.map { song ->
                Log.d("PlayerViewModel_MediaItem", "Creating MediaItem for Song ID: ${song.id}, Title: ${song.title}")
                Log.d("PlayerViewModel_MediaItem", "Song's albumArtUriString: ${song.albumArtUriString}")

                val metadataBuilder = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                // .setAlbumTitle(song.album) // Opcional

                playlistId?.let {
                    val extras = android.os.Bundle()
                    extras.putString("playlistId", it)
                    metadataBuilder.setExtras(extras)
                }

                // Set artwork URI without pre-loading byte data
                song.albumArtUriString?.toUri()?.let { uri ->
                    metadataBuilder.setArtworkUri(uri)
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
        Log.d("PlayerViewModel_MediaItem", "(loadAndPlaySong) called for Song ID: ${song.id}, Title: ${song.title}")
        Log.d("PlayerViewModel_MediaItem", "(loadAndPlaySong) mediaController is null: ${mediaController == null}")
        mediaController?.let { controller ->
            Log.d("PlayerViewModel_MediaItem", "(loadAndPlaySong) Creating MediaItem for Song ID: ${song.id}, Title: ${song.title}") // Log original, mantenido para contexto dentro del let.
            Log.d("PlayerViewModel_MediaItem", "(loadAndPlaySong) Song's albumArtUriString: ${song.albumArtUriString}")
            val artworkUriForMediaItem = song.albumArtUriString?.toUri()
            Log.d("PlayerViewModel_MediaItem", "(loadAndPlaySong) Setting artworkUri on MediaItem: $artworkUriForMediaItem")

            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(artworkUriForMediaItem)
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
            Trace.beginSection("PlayerViewModel.colorSchemeProcessorLoop")
            try {
                for (albumArtUri in colorSchemeRequestChannel) { // Consume de la cola
                    Trace.beginSection("PlayerViewModel.processColorSchemeForUri")
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
                        Trace.endSection() // End PlayerViewModel.processColorSchemeForUri
                    }
                }
            } finally {
                Trace.endSection() // End PlayerViewModel.colorSchemeProcessorLoop
            }
        }
    }

    // Modificada para devolver el ColorSchemePair y ser usada por getAlbumColorSchemeFlow y la precarga
    private suspend fun getOrGenerateColorSchemeForUri(albumArtUri: String, isPreload: Boolean): ColorSchemePair? {
        Trace.beginSection("PlayerViewModel.getOrGenerateColorSchemeForUri")
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
                val request = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .allowHardware(false)
                    .size(Size(128, 128))
                    .bitmapConfig(Bitmap.Config.ARGB_8888) // Asegurar consistencia
                    .memoryCachePolicy(CachePolicy.ENABLED) // Consistencia con extractAndGenerateColorScheme
                    .diskCachePolicy(CachePolicy.ENABLED)   // Consistencia con extractAndGenerateColorScheme
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
                schemePair
            } ?: run {
                if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                    _currentAlbumArtColorSchemePair.value = null
                    updateLavaLampColorsBasedOnActivePlayerScheme()
                }
                Trace.endSection() // End PlayerViewModel.getOrGenerateColorSchemeForUri (bitmap was null)
                null
            }
        } catch (e: Exception) {
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = null
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            Trace.endSection() // End PlayerViewModel.getOrGenerateColorSchemeForUri (exception)
            null
        }
    }

    private suspend fun extractAndGenerateColorScheme(albumArtUriAsUri: Uri?, isPreload: Boolean = false) {
        Trace.beginSection("PlayerViewModel.extractAndGenerateColorScheme")
        if (albumArtUriAsUri == null) {
            // Check current song's string URI when determining if it's the one without art
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == null) {
                _currentAlbumArtColorSchemePair.value = null
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            Trace.endSection() // End PlayerViewModel.extractAndGenerateColorScheme (URI null)
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
            Trace.endSection() // End PlayerViewModel.extractAndGenerateColorScheme (cached)
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
        finally {
            Trace.endSection() // End PlayerViewModel.extractAndGenerateColorScheme (after generation attempt)
        }
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
        val sortedSongs = when (sortOption) {
            SortOption.SongTitleAZ -> _masterAllSongs.value.sortedBy { it.title }
            SortOption.SongTitleZA -> _masterAllSongs.value.sortedByDescending { it.title }
            SortOption.SongArtist -> _masterAllSongs.value.sortedBy { it.artist }
            SortOption.SongAlbum -> _masterAllSongs.value.sortedBy { it.album }
            SortOption.SongDateAdded -> _masterAllSongs.value.sortedByDescending { it.albumId } // need to implement date added for Song
            SortOption.SongDuration -> _masterAllSongs.value.sortedBy { it.duration }
            else -> _masterAllSongs.value
        }.toImmutableList()
        _playerUiState.update {
            it.copy(
                allSongs = sortedSongs,
                currentSongSortOption = sortOption
            )
        }

        viewModelScope.launch {
            userPreferencesRepository.setSongsSortOption(sortOption.displayName)
        }
    }

    fun sortAlbums(sortOption: SortOption) {
        val sortedAlbums = when (sortOption) {
            SortOption.AlbumTitleAZ -> _playerUiState.value.albums.sortedBy { it.title }
            SortOption.AlbumTitleZA -> _playerUiState.value.albums.sortedByDescending { it.title }
            SortOption.AlbumArtist -> _playerUiState.value.albums.sortedBy { it.artist }
            SortOption.AlbumReleaseYear -> _playerUiState.value.albums.sortedByDescending { it.id } //need to implement album release date
            else -> _playerUiState.value.albums
        }.toImmutableList()
        _playerUiState.update {
            it.copy(
                albums = sortedAlbums,
                currentAlbumSortOption = sortOption
            )
        }

        viewModelScope.launch {
            userPreferencesRepository.setAlbumsSortOption(sortOption.displayName)
        }
    }

    fun sortArtists(sortOption: SortOption) {
        val sortedArtists = when (sortOption) {
            SortOption.ArtistNameAZ -> _playerUiState.value.artists.sortedBy { it.name }
            SortOption.ArtistNameZA -> _playerUiState.value.artists.sortedByDescending { it.name }
            else -> _playerUiState.value.artists
        }.toImmutableList()
        _playerUiState.update {
            it.copy(
                artists = sortedArtists,
                currentArtistSortOption = sortOption
            )
        }

        viewModelScope.launch {
            userPreferencesRepository.setArtistsSortOption(sortOption.displayName)
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption) {
        _playerUiState.update { it.copy(currentFavoriteSortOption = sortOption) }
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

    // --- AI Playlist Generation ---

    fun showAiPlaylistSheet() {
        _showAiPlaylistSheet.value = true
    }

    fun dismissAiPlaylistSheet() {
        _showAiPlaylistSheet.value = false
        _aiError.value = null
        _isGeneratingAiPlaylist.value = false
    }

    fun generateAiPlaylist(prompt: String, minLength: Int, maxLength: Int, saveAsPlaylist: Boolean = false) {
        viewModelScope.launch {
            _isGeneratingAiPlaylist.value = true
            _aiError.value = null

            try {
                val result = aiPlaylistGenerator.generate(prompt, allSongsFlow.value, minLength, maxLength)

                result.onSuccess { generatedSongs ->
                    if (generatedSongs.isNotEmpty()) {
                        if (saveAsPlaylist) {
                            val playlistName = "AI: ${prompt.take(25)}${if (prompt.length > 25) "..." else ""}"
                            val songIds = generatedSongs.map { it.id }
                            userPreferencesRepository.createPlaylist(
                                name = playlistName,
                                songIds = songIds,
                                isAiGenerated = true
                            )
                            sendToast("AI Playlist '$playlistName' created!")
                            dismissAiPlaylistSheet()
                        } else {
                            // Original Daily Mix logic
                            _dailyMixSongs.value = generatedSongs.toImmutableList()
                            viewModelScope.launch {
                                userPreferencesRepository.saveDailyMixSongIds(generatedSongs.map { it.id })
                            }
                            playSongs(generatedSongs, generatedSongs.first(), "AI: $prompt")
                            _isSheetVisible.value = true
                            dismissAiPlaylistSheet()
                        }
                    } else {
                        _aiError.value = "The AI couldn't find any songs for your prompt."
                    }
                }.onFailure { error ->
                    _aiError.value = if (error.message?.contains("API Key") == true) {
                        "Please, configure your Gemini API Key in Settings."
                    } else {
                        "AI Error: ${error.message}"
                    }
                }
            } finally {
                _isGeneratingAiPlaylist.value = false
            }
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

    fun getSongUrisForGenre(genreId: String): Flow<List<String>> {
        return musicRepository.getMusicByGenre(genreId).map { songs ->
            songs.take(4).mapNotNull { it.albumArtUriString }
        }
    }

    fun saveLastLibraryTabIndex(tabIndex: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveLastLibraryTabIndex(tabIndex)
        }
    }

    // CAMBIO 1: Nueva función para manejar la carga de datos de forma diferida (lazy).
    fun onLibraryTabSelected(tabIndex: Int) {
        Trace.beginSection("PlayerViewModel.onLibraryTabSelected")
        // Guarda el índice para la próxima vez que el usuario abra la app.
        // Esta llamada ya está en el ViewModel, así que la lógica de persistencia está bien.
        // No necesitamos llamarla explícitamente aquí si ya se llama cuando cambia el tab en la UI.
        // Sin embargo, si esta función es la *única* fuente de verdad para la selección de tabs,
        // entonces sí es necesario llamar a saveLastLibraryTabIndex(tabIndex).
        // La especificación indica que LibraryScreen lo llamará, así que aquí es correcto.
        saveLastLibraryTabIndex(tabIndex)

        // Si la pestaña ya fue cargada, no hacemos nada para evitar trabajo innecesario.
        if (_loadedTabs.value.contains(tabIndex)) {
            Log.d("PlayerViewModel", "Tab $tabIndex already loaded. Skipping data load.")
            Trace.endSection()
            return
        }

        Log.d("PlayerViewModel", "Tab $tabIndex selected. Attempting to load data.")
        // Inicia la carga de datos para la pestaña seleccionada en un hilo de fondo.
        viewModelScope.launch {
            Trace.beginSection("PlayerViewModel.onLibraryTabSelected_coroutine_load")
            try {
                when (tabIndex) {
                    0 -> loadSongsIfNeeded() // Carga canciones si es necesario
                    1 -> loadAlbumsIfNeeded() // Carga álbumes si es necesario
                    2 -> loadArtistsIfNeeded() // Carga artistas si es necesario
                    // Las pestañas 3 (Playlists) y 4 (Liked) ya tienen su propia lógica
                    // de carga a través de otros ViewModels o flujos, lo cual está bien.
                }
                // Marca la pestaña como cargada para no volver a cargarla.
                _loadedTabs.update { currentTabs -> currentTabs + tabIndex }
                Log.d("PlayerViewModel", "Tab $tabIndex marked as loaded. Current loaded tabs: ${_loadedTabs.value}")
            } finally {
                Trace.endSection() // End PlayerViewModel.onLibraryTabSelected_coroutine_load
            }
        }
        Trace.endSection() // End PlayerViewModel.onLibraryTabSelected
    }

    fun selectSongForInfo(song: Song) {
        _selectedSongForInfo.value = song
    }

    private fun loadLyricsForCurrentSong() {
        val currentSong = _stablePlayerState.value.currentSong ?: return

        viewModelScope.launch {
            // 1. Indicar que estamos cargando
            _stablePlayerState.update { it.copy(isLoadingLyrics = true, lyrics = null) }

            // 2. Obtener las letras desde el repositorio
            val fetchedLyrics = musicRepository.getLyrics(currentSong)

            // 3. Actualizar el estado con el resultado
            _stablePlayerState.update { it.copy(isLoadingLyrics = false, lyrics = fetchedLyrics) }
        }
    }

    fun editSongMetadata(song: Song, newTitle: String, newArtist: String, newAlbum: String, newGenre: String, newLyrics: String) {
        viewModelScope.launch {
            Timber.d("Editing metadata for song: ${song.title} with URI: ${song.contentUriString}")
            Timber.d("New metadata: title=$newTitle, artist=$newArtist, album=$newAlbum, genre=$newGenre, lyrics=$newLyrics")
            val success = withContext(Dispatchers.IO) {
                songMetadataEditor.editSongMetadata(song.contentUriString, newTitle, newArtist, newAlbum, newGenre, newLyrics)
            }

            if (success) {
                val updatedSong = song.copy(
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    genre = newGenre,
                    lyrics = newLyrics
                )

                // Manually update the song in the UI state
                val currentSongs = _playerUiState.value.allSongs.toMutableList()
                val index = currentSongs.indexOfFirst { it.id == song.id }
                if (index != -1) {
                    currentSongs[index] = updatedSong
                    _playerUiState.update { it.copy(allSongs = currentSongs.toImmutableList()) }
                }

                if (_stablePlayerState.value.currentSong?.id == song.id) {
                    _stablePlayerState.update { it.copy(currentSong = updatedSong) }
                }

                if (_selectedSongForInfo.value?.id == song.id) {
                    _selectedSongForInfo.value = updatedSong
                }

                syncManager.sync()
                _toastEvents.emit("Metadata updated successfully")
            } else {
                _toastEvents.emit("Failed to update metadata")
            }
        }
    }

    suspend fun generateAiMetadata(song: Song, fields: List<String>): Result<SongMetadata> {
        return aiMetadataGenerator.generate(song, fields)
    }

    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    fun fetchLyricsForCurrentSong() {
        val currentSong = stablePlayerState.value.currentSong
        viewModelScope.launch {
            _lyricsSearchUiState.value = LyricsSearchUiState.Loading
            if (currentSong != null) {
                musicRepository.getLyricsFromRemote(currentSong)
                    .onSuccess { (lyrics, rawLyrics) -> // Deconstruct the pair
                        _lyricsSearchUiState.value = LyricsSearchUiState.Success(lyrics)
                        // Actualizamos la letra en el estado del reproductor
                        // Y TAMBIÉN en la instancia de la canción actual para mantener la consistencia
                        _stablePlayerState.update { state ->
                            state.copy(
                                lyrics = lyrics,
                                currentSong = state.currentSong?.copy(lyrics = rawLyrics)
                            )
                        }
                    }
                    .onFailure { error ->
                        _lyricsSearchUiState.value = LyricsSearchUiState.Error(error.message ?: "Unknown error")
                    }
            }
        }
    }

    /**
     * Resetea el estado de la búsqueda de letras a Idle.
     */
    fun resetLyricsSearchState() {
        _lyricsSearchUiState.value = LyricsSearchUiState.Idle
    }
}