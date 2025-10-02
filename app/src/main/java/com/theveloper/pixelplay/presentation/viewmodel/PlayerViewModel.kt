package com.theveloper.pixelplay.presentation.viewmodel

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Trace
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.google.common.util.concurrent.ListenableFuture
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.EotStateHolder
import com.theveloper.pixelplay.data.ai.AiMetadataGenerator
import com.theveloper.pixelplay.data.ai.AiPlaylistGenerator
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.database.AlbumArtThemeEntity
import com.theveloper.pixelplay.data.database.StoredColorSchemeValues
import com.theveloper.pixelplay.data.database.toComposeColor
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.GenreColors
import com.theveloper.pixelplay.ui.theme.LightColorScheme
import com.theveloper.pixelplay.ui.theme.extractSeedColor
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.toHexString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.lang.Math.random
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.collections.map

enum class PlayerSheetState {
    COLLAPSED,
    EXPANDED
}

data class ColorSchemePair(
    val light: ColorScheme,
    val dark: ColorScheme
)

data class StablePlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val totalDuration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val lyrics: Lyrics? = null,
    val isLoadingLyrics: Boolean = false
)

data class PlayerUiState(
    val currentPosition: Long = 0L,
    val isLoadingInitialSongs: Boolean = true,
    val isGeneratingAiMetadata: Boolean = false,
    val allSongs: ImmutableList<Song> = persistentListOf(),
    val currentPlaybackQueue: ImmutableList<Song> = persistentListOf(),
    val currentQueueSourceName: String = "All Songs",
    val lavaLampColors: ImmutableList<Color> = persistentListOf(),
    val albums: ImmutableList<Album> = persistentListOf(),
    val artists: ImmutableList<Artist> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = false,
    val currentSongSortOption: SortOption = SortOption.SongTitleAZ,
    val currentAlbumSortOption: SortOption = SortOption.AlbumTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.ArtistNameAZ,
    val currentFavoriteSortOption: SortOption = SortOption.LikedSongTitleAZ,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf(),
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchHistory: ImmutableList<SearchHistoryItem> = persistentListOf(),
    val isSyncingLibrary: Boolean = false,

    // State for dismiss/undo functionality
    val showDismissUndoBar: Boolean = false,
    val dismissedSong: Song? = null,
    val dismissedQueue: ImmutableList<Song> = persistentListOf(),
    val dismissedQueueName: String = "",
    val dismissedPosition: Long = 0L,
    val undoBarVisibleDuration: Long = 4000L,
    val preparingSongId: String? = null
)

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

    val playerContentExpansionFraction = Animatable(0f)

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

    val playerThemePreference: StateFlow<String> = userPreferencesRepository.playerThemePreferenceFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreference.GLOBAL)

    val navBarCornerRadius: StateFlow<Int> = userPreferencesRepository.navBarCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 32)

    val navBarStyle: StateFlow<String> = userPreferencesRepository.navBarStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NavBarStyle.DEFAULT
        )

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


    private val _loadedTabs = MutableStateFlow(emptySet<Int>())

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
                3 -> listOf(
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

    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isInitialDataLoaded = MutableStateFlow(false)

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
                .sortedBy { it.name }
                .toImmutableList()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = combine(
        playerThemePreference, _currentAlbumArtColorSchemePair
    ) { playerPref, albumScheme ->
        when (playerPref) {
            ThemePreference.ALBUM_ART -> albumScheme
            ThemePreference.DYNAMIC -> null // Signal to use system's MaterialTheme.colorScheme
            ThemePreference.DEFAULT -> null // Effectively makes DEFAULT same as DYNAMIC (use system theme)
            else -> albumScheme // Fallback to album art if preference is somehow unknown or old 'GLOBAL'
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null) // Initial value null (system theme)

    private val individualAlbumColorSchemes = mutableMapOf<String, MutableStateFlow<ColorSchemePair?>>()

    private val colorSchemeRequestChannel = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val urisBeingProcessed = mutableSetOf<String>()

    private var mediaController: MediaController? = null
    private val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
    private val mediaControllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken).buildAsync()

    private var pendingPlaybackAction: (() -> Unit)? = null

    val favoriteSongIds: StateFlow<Set<String>> = userPreferencesRepository.favoriteSongIdsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isCurrentSongFavorite: StateFlow<Boolean> = combine(
        stablePlayerState,
        favoriteSongIds
    ) { state, ids ->
        state.currentSong?.id?.let { ids.contains(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _currentFavoriteSortOptionStateFlow = MutableStateFlow<SortOption>(SortOption.LikedSongTitleAZ) // Default. Especificar el tipo general SortOption.
    val currentFavoriteSortOptionStateFlow: StateFlow<SortOption> = _currentFavoriteSortOptionStateFlow.asStateFlow()

    val favoriteSongs: StateFlow<ImmutableList<Song>> = combine(
        favoriteSongIds,
        _masterAllSongs,
        currentFavoriteSortOptionStateFlow
    ) { ids, allSongsList, sortOption ->
        val favoriteSongsList = allSongsList.filter { song -> ids.contains(song.id) }
        when (sortOption) {
            SortOption.LikedSongTitleAZ -> favoriteSongsList.sortedBy { it.title }
            SortOption.LikedSongTitleZA -> favoriteSongsList.sortedByDescending { it.title }
            SortOption.LikedSongArtist -> favoriteSongsList.sortedBy { it.artist }
            SortOption.LikedSongAlbum -> favoriteSongsList.sortedBy { it.album }
            SortOption.LikedSongDateLiked -> favoriteSongsList.sortedByDescending { it.id }
            else -> favoriteSongsList
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

    fun shuffleAllSongs() {
        Log.d("ShuffleDebug", "shuffleAllSongs called.")
        mediaController?.shuffleModeEnabled = true
        val allSongs = _playerUiState.value.allSongs
        if (allSongs.isNotEmpty()) {
            val shuffledList = allSongs.shuffled().toMutableList()
            val randomSong = shuffledList.first() // Pick the first from the already shuffled list
            playSongs(shuffledList, randomSong, "All Songs (Shuffled)")
        }
    }

    fun shuffleFavoriteSongs() {
        Log.d("ShuffleDebug", "shuffleFavoriteSongs called.")
        mediaController?.shuffleModeEnabled = true
        val favSongs = favoriteSongs.value
        if (favSongs.isNotEmpty()) {
            val shuffledList = favSongs.shuffled()
            playSongs(shuffledList, shuffledList.first(), "Liked Songs (Shuffled)")
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
    private var transitionSchedulerJob: Job? = null

    private fun incrementSongScore(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dailyMixManager.incrementScore(songId)
        }
    }


    fun updatePredictiveBackCollapseFraction(fraction: Float) {
        _predictiveBackCollapseFraction.value = fraction.coerceIn(0f, 1f)
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
        loadSearchHistory()

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
            if (!isSyncingStateFlow.value && !_isInitialDataLoaded.value && _playerUiState.value.allSongs.isEmpty()) {
                Log.i("PlayerViewModel", "Initial check: Sync not active and initial data not loaded. Calling resetAndLoadInitialData.")
                resetAndLoadInitialData("Initial Check")
            }
        }

        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                setupMediaControllerListeners()
                // Execute any pending action that was queued while the controller was connecting
                pendingPlaybackAction?.invoke()
                pendingPlaybackAction = null
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
            if (isSyncingStateFlow.value && !_isInitialDataLoaded.value) {
                Log.i("PlayerViewModel", "preloadThemesAndInitialData: Sync is active and initial data not yet loaded, deferring initial load to sync completion handler.")
            } else if (!_isInitialDataLoaded.value && _playerUiState.value.allSongs.isEmpty()) { // Check _isInitialDataLoaded
                Log.i("PlayerViewModel", "preloadThemesAndInitialData: Sync not active or already finished, and initial data not loaded. Calling resetAndLoadInitialData from preload.")
                resetAndLoadInitialData("preloadThemesAndInitialData")
            } else {
                Log.i("PlayerViewModel", "preloadThemesAndInitialData: Initial data already loaded or sync is active and will trigger load. Skipping direct call to resetAndLoadInitialData from preload.")
            }
            _isInitialThemePreloadComplete.value = true
            val timeToComplete = System.currentTimeMillis() - overallInitStartTime
            Log.d("PlayerViewModelPerformance", "Initial theme preload complete (async data loading dispatched). Total time since overallInitStart: ${timeToComplete} ms")
        }
        Log.d("PlayerViewModelPerformance", "preloadThemesAndInitialData END. Total function time: ${System.currentTimeMillis() - functionStartTime} ms (dispatching async work)")
        Trace.endSection()
    }

    // Nueva función para carga paralela
    private fun loadInitialLibraryDataParallel() {
        _playerUiState.update {
            it.copy(
                isLoadingInitialSongs = true,
                isLoadingLibraryCategories = true
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
                joinAll(songsJob, albumsJob, artistsJob)
                Log.d("PlayerViewModel", "All parallel loads (songs, albums, artists) completed.")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error during parallel data loading completion", e)
            } finally {
                _playerUiState.update {
                    it.copy(
                        isLoadingInitialSongs = false,
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

        loadInitialLibraryDataParallel()
        updateDailyMix()

        Log.d("PlayerViewModelPerformance", "resetAndLoadInitialData END (dispatching parallel async work). Total function time: ${System.currentTimeMillis() - functionStartTime} ms")
        Trace.endSection() // End PlayerViewModel.resetAndLoadInitialData
    }

    // This function might still be called by loadSongsIfNeeded,
    // but _isInitialDataLoaded should now be primarily managed by loadInitialLibraryDataParallel
    private fun loadSongsFromRepository() {
        Log.d("PlayerViewModel", "loadSongsFromRepository called (potentially for individual tab load or refresh).")
        // No longer need checks for isLoadingMoreSongs or canLoadMoreSongs

        viewModelScope.launch { // Default dispatcher is Main.Immediate which is fine for launching.
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

    private fun loadAlbumsFromRepository() {
        Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository (All) called.")

        viewModelScope.launch {
            Trace.beginSection("PlayerViewModel.loadAlbumsFromRepository_coroutine")
            val functionStartTime = System.currentTimeMillis()
            Log.d("PlayerViewModelPerformance", "loadAlbumsFromRepository (All) START")

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

    fun loadAlbumsIfNeeded() {
        val albumsEmpty = _playerUiState.value.albums.isEmpty()
        val notLoading = !_playerUiState.value.isLoadingLibraryCategories
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
        Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository (All) called.")

        viewModelScope.launch {
            val functionStartTime = System.currentTimeMillis()
            Log.d("PlayerViewModelPerformance", "loadArtistsFromRepository (All) START")

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

    fun loadArtistsIfNeeded() {
        val artistsEmpty = _playerUiState.value.artists.isEmpty()
        val notLoading = !_playerUiState.value.isLoadingLibraryCategories
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

    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true
    ) {
        mediaController?.let { controller ->
            val currentQueue = _playerUiState.value.currentPlaybackQueue
            val songIndexInQueue = currentQueue.indexOfFirst { it.id == song.id }

            // If the song is already in the current playback queue, just seek to it.
            // This avoids resetting the queue and preserves user modifications (reordering, etc.).
            if (songIndexInQueue != -1) {
                // Don't seek if it's already the current item, just ensure it plays.
                if (controller.currentMediaItemIndex == songIndexInQueue) {
                    if (!controller.isPlaying) controller.play()
                } else {
                    controller.seekTo(songIndexInQueue, 0L)
                    controller.play() // Ensure playback starts after seeking
                }
                if (isVoluntaryPlay) {
                    incrementSongScore(song.id)
                }
            } else {
                // The song is not in the current queue, so treat it as a new playback context.
                // This will reset the queue with the new contextSongs list.
                Log.d("ShuffleDebug", "showAndPlaySong (new context) for '${song.title}' with queue: $queueName")
                _playerUiState.update { it.copy(preparingSongId = song.id) }
                if (isVoluntaryPlay) {
                    incrementSongScore(song.id)
                }
                playSongs(contextSongs, song, queueName, null)
            }
        }
        _predictiveBackCollapseFraction.value = 0f
    }

    fun showAndPlaySong(song: Song) {
        Log.d("ShuffleDebug", "showAndPlaySong (single song overload) called for '${song.title}'")
        showAndPlaySong(song, playerUiState.value.allSongs.toList(), "Library")
    }

    fun playAlbum(album: Album) {
        Log.d("ShuffleDebug", "playAlbum called for album: ${album.title}")
        viewModelScope.launch {
            try {
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForAlbum(album.id).first()
                }

                if (songsList.isNotEmpty()) {
                    playSongs(songsList, songsList.first(), album.title, null)
                    _isSheetVisible.value = true // Mostrar reproductor
                } else {
                    Log.w("PlayerViewModel", "Album '${album.title}' has no playable songs.")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing album ${album.title}", e)
            }
        }
    }

    fun playArtist(artist: Artist) {
        Log.d("ShuffleDebug", "playArtist called for artist: ${artist.name}")
        viewModelScope.launch {
            try {
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForArtist(artist.id).first()
                }

                if (songsList.isNotEmpty()) {
                    playSongs(songsList, songsList.first(), artist.name, null)
                    _isSheetVisible.value = true
                } else {
                    Log.w("PlayerViewModel", "Artist '${artist.name}' has no playable songs.")
                    // podrías emitir un evento Toast
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing artist ${artist.name}", e)
            }
        }
    }

    fun removeSongFromQueue(songId: String) {
        mediaController?.let { controller ->
            val currentQueue = _playerUiState.value.currentPlaybackQueue
            val indexToRemove = currentQueue.indexOfFirst { it.id == songId }

            if (indexToRemove != -1) {
                // Command the player to remove the item. This is the source of truth for playback.
                controller.removeMediaItem(indexToRemove)

            }
        }
    }

    fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.let { controller ->
            if (fromIndex >= 0 && fromIndex < controller.mediaItemCount &&
                toIndex >= 0 && toIndex < controller.mediaItemCount) {

                // Move the item in the MediaController's timeline.
                // This is the source of truth for playback.
                controller.moveMediaItem(fromIndex, toIndex)

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

    private fun updateCurrentPlaybackQueueFromPlayer(playerCtrl: MediaController?) {
        val currentMediaController = playerCtrl ?: mediaController ?: return
        val count = currentMediaController.mediaItemCount

        if (count == 0) {
            _playerUiState.update { it.copy(currentPlaybackQueue = persistentListOf()) }
            return
        }

        val queue = mutableListOf<Song>()
        val allSongsMasterList = _masterAllSongs.value // Use the master list for lookup

        // This reflects the timeline order as defined by the MediaController.
        // If shuffle mode is on, the controller plays in a shuffled order, but the
        // underlying timeline list remains in its original order.
        for (i in 0 until count) {
            val mediaItem = currentMediaController.getMediaItemAt(i)
            allSongsMasterList.find { it.id == mediaItem.mediaId }?.let { song -> queue.add(song) }
        }
        _playerUiState.update { it.copy(currentPlaybackQueue = queue.toImmutableList()) }
    }

    private fun setupMediaControllerListeners() {
        Trace.beginSection("PlayerViewModel.setupMediaControllerListeners")
        val playerCtrl = mediaController ?: return Trace.endSection()
        _stablePlayerState.update {
            it.copy(
                isShuffleEnabled = playerCtrl.shuffleModeEnabled,
                repeatMode = playerCtrl.repeatMode,
                isPlaying = playerCtrl.isPlaying
            )
        }

        updateCurrentPlaybackQueueFromPlayer(playerCtrl)

        playerCtrl.currentMediaItem?.mediaId?.let { songId ->
            val song = _playerUiState.value.currentPlaybackQueue.find { s -> s.id == songId }
                ?: _playerUiState.value.allSongs.find { s -> s.id == songId }

            if (song != null) {
                _stablePlayerState.update {
                    it.copy(
                        currentSong = song,
                        totalDuration = playerCtrl.duration.coerceAtLeast(0L)
                    )
                }
                _playerUiState.update { it.copy(currentPosition = playerCtrl.currentPosition.coerceAtLeast(0L)) }
                viewModelScope.launch {
                    song.albumArtUriString?.toUri()?.let { uri ->
                        extractAndGenerateColorScheme(uri)
                    }
                }
                if (playerCtrl.isPlaying) {
                    startProgressUpdates()
                }
            } else {
                _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false) }
                _playerUiState.update { it.copy(currentPosition = 0L) }
            }
        }

        playerCtrl.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _stablePlayerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    _isSheetVisible.value = true
                    if (_playerUiState.value.preparingSongId != null) {
                        _playerUiState.update { it.copy(preparingSongId = null) }
                    }
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Cancel any pending transition job to handle rapid swipes and prevent race conditions.
                transitionSchedulerJob?.cancel()
                transitionSchedulerJob = viewModelScope.launch {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val activeEotSongId = EotStateHolder.eotTargetSongId.value
                        val previousSongId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                        if (_isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                            playerCtrl.seekTo(0L)
                            playerCtrl.pause()

                            val finishedSongTitle = _playerUiState.value.allSongs.find { it.id == previousSongId }?.title
                                ?: "Track" // Fallback title

                            viewModelScope.launch {
                                _toastEvents.emit("Playback stopped: $finishedSongTitle finished (End of Track).")
                            }
                            cancelSleepTimer(suppressDefaultToast = true)
                        }
                    }

                    // --- Update state for the new mediaItem ---
                    mediaItem?.mediaId?.let { songId ->
                        // Robustly find the song in the master list to ensure consistency.
                        val song = _masterAllSongs.value.find { s -> s.id == songId }

                        // Reset lyrics state for the new song
                        resetLyricsSearchState()

                        _stablePlayerState.update {
                            it.copy(
                                currentSong = song,
                                // Duration might be C.TIME_UNSET if not yet known, ensure it's non-negative
                                totalDuration = playerCtrl.duration.coerceAtLeast(0L)
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

                            loadLyricsForCurrentSong()
                        }
                    } ?: _stablePlayerState.update {
                        it.copy(currentSong = null, isPlaying = false)
                    }
                }
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
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                Log.d("ShuffleDebug", "onShuffleModeEnabledChanged: new state: $shuffleModeEnabled. Player has ${playerCtrl.mediaItemCount} items.")
                _stablePlayerState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
                if (playerCtrl.mediaItemCount == 0 && shuffleModeEnabled) {
                    Log.d("ShuffleDebug", "Player empty and shuffle enabled, creating and playing a new shuffled queue.")
                    val shuffledQueue = createShuffledQueue(_masterAllSongs.value)
                    if (shuffledQueue.isNotEmpty()) {
                        playSongs(shuffledQueue, shuffledQueue.first(), "Shuffled Queue", null)
                    }
                }
                //   updateCurrentPlaybackQueueFromPlayer(playerCtrl)
            }
            override fun onRepeatModeChanged(repeatMode: Int) { _stablePlayerState.update { it.copy(repeatMode = repeatMode) } }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                // Cancel any pending song transition job. This is crucial to prevent a race condition
                // where a transition update might use a stale queue, right before we update it here.
                // This ensures that when the queue is modified (reorder, remove), the UI state
                // remains perfectly in sync with the player's new timeline.
                transitionSchedulerJob?.cancel()
                updateCurrentPlaybackQueueFromPlayer(mediaController)
            }
        })
        Trace.endSection()
    }

    private fun createShuffledQueue(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return emptyList()
       
        val shuffledQueue = songs.shuffled()
        val chosenSong = shuffledQueue.firstOrNull()
        Log.d(
            "ShuffleDebug",
            "createShuffledQueue called. Input size: ${songs.size}. Chosen first song: '${chosenSong?.title}'"
        )
        
        return shuffledQueue
    }

    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        viewModelScope.launch {
            transitionSchedulerJob?.cancel()
            internalPlaySongs(songsToPlay, startSong, queueName, playlistId)
        }
    }

    fun showPlayer() {
        if (stablePlayerState.value.currentSong != null) {
            _isSheetVisible.value = true
        }
    }

    private suspend fun internalPlaySongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        val playSongsAction = {
            mediaController?.let { controller ->
                val mediaItems = songsToPlay.map { song ->
                    val metadataBuilder = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)

                    playlistId?.let {
                        val extras = android.os.Bundle()
                        extras.putString("playlistId", it)
                        metadataBuilder.setExtras(extras)
                    }

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
                }
            }
            _playerUiState.update { it.copy(isLoadingInitialSongs = false) } // Marcar que la carga inicial de esta canción terminó
        }

        if (mediaController == null) {
            Log.w("PlayerViewModel", "MediaController not available. Queuing playback action.")
            pendingPlaybackAction = playSongsAction
        } else {
            playSongsAction()
        }
    }


    private fun loadAndPlaySong(song: Song) {
        mediaController?.let { controller ->
            val artworkUriForMediaItem = song.albumArtUriString?.toUri()

            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(artworkUriForMediaItem)
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
        }
    }

    fun toggleShuffle() {
        val newShuffleState = !_stablePlayerState.value.isShuffleEnabled
        mediaController?.shuffleModeEnabled = newShuffleState
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
    }

    fun toggleFavorite() {
        _stablePlayerState.value.currentSong?.id?.let { songId ->
            viewModelScope.launch {
                userPreferencesRepository.toggleFavoriteSong(songId)
            }
        }
    }

    fun toggleFavoriteSpecificSong(song: Song) {
        viewModelScope.launch {
            userPreferencesRepository.toggleFavoriteSong(song.id)
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
        }
    }

    fun getAlbumColorSchemeFlow(albumArtUri: String?): StateFlow<ColorSchemePair?> {
        val uriString = albumArtUri ?: "default_fallback_key"

        individualAlbumColorSchemes[uriString]?.let { return it }

        val newFlow = MutableStateFlow<ColorSchemePair?>(null)
        individualAlbumColorSchemes[uriString] = newFlow

        if (albumArtUri != null) {
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
            newFlow.value = ColorSchemePair(LightColorScheme, DarkColorScheme)
        }
        return newFlow
    }

    private fun launchColorSchemeProcessor() {
        viewModelScope.launch(Dispatchers.IO) {
            Trace.beginSection("PlayerViewModel.colorSchemeProcessorLoop")
            try {
                for (albumArtUri in colorSchemeRequestChannel) {
                    Trace.beginSection("PlayerViewModel.processColorSchemeForUri")
                    try {
                        Log.d("PlayerViewModel", "Processing $albumArtUri from queue.")
                        val scheme = getOrGenerateColorSchemeForUri(albumArtUri, false)
                        individualAlbumColorSchemes[albumArtUri]?.value = scheme
                        Log.d("PlayerViewModel", "Finished processing $albumArtUri. Scheme: ${scheme != null}")
                    } catch (e: Exception) {
                        Log.e("PlayerViewModel", "Error processing $albumArtUri in ColorSchemeProcessor", e)
                        individualAlbumColorSchemes[albumArtUri]?.value = null
                    } finally {
                        synchronized(urisBeingProcessed) {
                            urisBeingProcessed.remove(albumArtUri)
                        }
                        Trace.endSection()
                    }
                }
            } finally {
                Trace.endSection()
            }
        }
    }

    private suspend fun getOrGenerateColorSchemeForUri(albumArtUri: String, isPreload: Boolean): ColorSchemePair? {
        Trace.beginSection("PlayerViewModel.getOrGenerateColorSchemeForUri")
        val uriString = albumArtUri
        val cachedEntity = withContext(Dispatchers.IO) { albumArtThemeDao.getThemeByUri(uriString) }

        if (cachedEntity != null) {
            val schemePair = mapEntityToColorSchemePair(cachedEntity)
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = schemePair
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            return schemePair
        }

        return try {
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .allowHardware(false)
                    .size(Size(128, 128))
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .memoryCachePolicy(CachePolicy.ENABLED)
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
                schemePair
            } ?: run {
                if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                    _currentAlbumArtColorSchemePair.value = null
                    updateLavaLampColorsBasedOnActivePlayerScheme()
                }
                Trace.endSection()
                null
            }
        } catch (e: Exception) {
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = null
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            Trace.endSection()
            null
        }
    }

    private suspend fun extractAndGenerateColorScheme(albumArtUriAsUri: Uri?, isPreload: Boolean = false) {
        Trace.beginSection("PlayerViewModel.extractAndGenerateColorScheme")
        if (albumArtUriAsUri == null) {
            if (!isPreload && _stablePlayerState.value.currentSong?.albumArtUriString == null) {
                _currentAlbumArtColorSchemePair.value = null
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            Trace.endSection()
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
            }
            Trace.endSection()
            return
        }

        try {
            val bitmap = withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(albumArtUriAsUri)
                    .allowHardware(false)
                    .size(Size(128, 128))
                    .bitmapConfig(Bitmap.Config.ARGB_8888)
                    .memoryCachePolicy(CachePolicy.ENABLED)
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
            Trace.endSection()
        }
    }

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

    private fun updateLavaLampColorsBasedOnActivePlayerScheme() {
        viewModelScope.launch {
            val currentPlayerSchemePair = activePlayerColorSchemePair.first()
            val schemeForLava = currentPlayerSchemePair?.dark ?: DarkColorScheme
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
        mediaController?.let {
            if (it.hasNextMediaItem()) {
                it.seekToNextMediaItem()
                it.play()
            }
        }
    }

    fun previousSong() {
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
                delay(40)
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
                    .size(Size(256, 256))
                    .allowHardware(false)
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
            SortOption.SongDateAdded -> _masterAllSongs.value.sortedByDescending { it.albumId }
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
    }

    fun loadSearchHistory(limit: Int = 15) {
        viewModelScope.launch {
            val history = withContext(Dispatchers.IO) {
                musicRepository.getRecentSearchHistory(limit)
            }
            _playerUiState.update { it.copy(searchHistory = history.toImmutableList()) }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        viewModelScope.launch {
            if (query.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    musicRepository.addSearchHistoryItem(query)
                }
                loadSearchHistory()
            }
        }
    }

    fun performSearch(query: String) {
        viewModelScope.launch {
            try {
                if (query.isBlank()) {
                    _playerUiState.update { it.copy(searchResults = persistentListOf()) }
                    return@launch
                }

                val currentFilter = _playerUiState.value.selectedSearchFilter

                val resultsList: List<SearchResultItem> = withContext(Dispatchers.IO) {
                    musicRepository.searchAll(query, currentFilter).first()
                }

                _playerUiState.update { it.copy(searchResults = resultsList.toImmutableList()) }

            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error performing search for query: $query", e)
                _playerUiState.update {
                    it.copy(
                        searchResults = persistentListOf(),
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
            loadSearchHistory()
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

    fun clearQueueExceptCurrent() {
        mediaController?.let { controller ->
            val currentSongIndex = controller.currentMediaItemIndex
            if (currentSongIndex == C.INDEX_UNSET) return@let
            val indicesToRemove = (0 until controller.mediaItemCount)
                .filter { it != currentSongIndex }
                .sortedDescending()

            for (index in indicesToRemove) {
                controller.removeMediaItem(index)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
    }

    // Sleep Timer Control Functions
    fun setSleepTimer(durationMinutes: Int) {
        if (_isEndOfTrackTimerActive.value) {
            eotSongMonitorJob?.cancel()
            cancelSleepTimer(suppressDefaultToast = true)
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
                    if (_isEndOfTrackTimerActive.value &&
                        EotStateHolder.eotTargetSongId.value != null &&
                        newSongId != EotStateHolder.eotTargetSongId.value) {

                        val oldSongIdForToast = EotStateHolder.eotTargetSongId.value // Capture before it's cleared by cancelSleepTimer
                        val oldSongTitle = _playerUiState.value.allSongs.find { it.id == oldSongIdForToast }?.title
                            ?: "Previous track" // Fallback
                        val newSongTitleText = _playerUiState.value.allSongs.find { it.id == newSongId }?.title
                            ?: "Current track" // Fallback

                        viewModelScope.launch {
                            _toastEvents.emit("End of Track timer deactivated: song changed from $oldSongTitle to $newSongTitleText.")
                        }

                        cancelSleepTimer(suppressDefaultToast = true)

                        eotSongMonitorJob?.cancel()
                        eotSongMonitorJob = null
                    }
                }
            }
            viewModelScope.launch { _toastEvents.emit("Playback will stop at end of track.") }
        } else {
            eotSongMonitorJob?.cancel()
            if (_isEndOfTrackTimerActive.value && EotStateHolder.eotTargetSongId.value != null) {
                cancelSleepTimer()
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
                    //isCurrentSongFavorite = false
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

    fun hideDismissUndoBar() {
        _playerUiState.update {
            it.copy(
                showDismissUndoBar = false,
                dismissedSong = null,
                dismissedQueue = persistentListOf(),
                dismissedQueueName = "",
                dismissedPosition = 0L
            )
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
                    0 -> loadSongsIfNeeded()
                    1 -> loadAlbumsIfNeeded()
                    2 -> loadArtistsIfNeeded()

                }
                // Marca la pestaña como cargada para no volver a cargarla.
                _loadedTabs.update { currentTabs -> currentTabs + tabIndex }
                Log.d("PlayerViewModel", "Tab $tabIndex marked as loaded. Current loaded tabs: ${_loadedTabs.value}")
            } finally {
                Trace.endSection()
            }
        }
        Trace.endSection()
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

    fun editSongMetadata(song: Song, newTitle: String, newArtist: String, newAlbum: String, newGenre: String, newLyrics: String, newTrackNumber: Int) {
        viewModelScope.launch {
            Timber.d("Editing metadata for song: ${song.title} with URI: ${song.contentUriString}")
            Timber.d("New metadata: title=$newTitle, artist=$newArtist, album=$newAlbum, genre=$newGenre, lyrics=$newLyrics, trackNumber=$newTrackNumber")
            val success = withContext(Dispatchers.IO) {
                songMetadataEditor.editSongMetadata(song.contentUriString, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber)
            }

            if (success) {
                val updatedSong = song.copy(
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    genre = newGenre,
                    lyrics = newLyrics,
                    trackNumber = newTrackNumber
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
     * Procesa la letra importada de un archivo, la guarda y actualiza la UI.
     * @param songId El ID de la canción para la que se importa la letra.
     * @param lyricsContent El contenido de la letra como String.
     */
    fun importLyricsFromFile(songId: Long, lyricsContent: String) {
        viewModelScope.launch {
            // 1. Guardar la nueva letra en la base de datos.
            musicRepository.updateLyrics(songId, lyricsContent)

            // 2. Volver a cargar la letra para la canción actual para actualizar la UI.
            val currentSong = _stablePlayerState.value.currentSong
            if (currentSong != null && currentSong.id.toLong() == songId) {
                // Actualizar la instancia de la canción en el estado estable con la nueva letra.
                val updatedSong = currentSong.copy(lyrics = lyricsContent)
                val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)

                _stablePlayerState.update {
                    it.copy(
                        currentSong = updatedSong,
                        lyrics = parsedLyrics,
                        isLoadingLyrics = false
                    )
                }
                _lyricsSearchUiState.value = LyricsSearchUiState.Success(parsedLyrics)
                _toastEvents.emit("Lyrics imported successfully!")
            } else {
                _lyricsSearchUiState.value = LyricsSearchUiState.Error("Could not associate lyrics with the current song.")
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
