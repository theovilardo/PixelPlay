package com.theveloper.pixelplay.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import android.os.Trace
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.model.LibraryTabId
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Timeline
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.EotStateHolder
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.toLibraryTabIdOrNull
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.service.player.CastPlayer
import com.theveloper.pixelplay.data.service.http.MediaFileHttpServerService
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.utils.AppShortcutManager
import com.theveloper.pixelplay.utils.QueueUtils
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject

private const val CAST_LOG_TAG = "PlayerCastTransfer"

@UnstableApi
@SuppressLint("LogNotTimber")
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    val syncManager: SyncManager, // Inyectar SyncManager

    private val dualPlayerEngine: DualPlayerEngine,
    private val appShortcutManager: AppShortcutManager,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val dailyMixStateHolder: DailyMixStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val castStateHolder: CastStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val sleepTimerStateHolder: SleepTimerStateHolder,
    private val searchStateHolder: SearchStateHolder,
    private val aiStateHolder: AiStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val externalMediaStateHolder: ExternalMediaStateHolder,
    val themeStateHolder: ThemeStateHolder
) : ViewModel() {

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()
    
    val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState
    
    private val _masterAllSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())

    // Lyrics load callback for LyricsStateHolder
    private val lyricsLoadCallback = object : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = true, lyrics = null)
            }
        }

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = false, lyrics = lyrics)
            }
        }
    }



    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSongArtists: StateFlow<List<Artist>> = stablePlayerState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            val idLong = songId?.toLongOrNull()
            if (idLong == null) flowOf(emptyList())
            else musicRepository.getArtistsForSong(idLong)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sheetState = MutableStateFlow(PlayerSheetState.COLLAPSED)
    val sheetState: StateFlow<PlayerSheetState> = _sheetState.asStateFlow()
    private val _isSheetVisible = MutableStateFlow(false)
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

    // Theme & Colors - delegated to ThemeStateHolder
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.currentAlbumArtColorSchemePair
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.activePlayerColorSchemePair

    val navBarCornerRadius: StateFlow<Int> = userPreferencesRepository.navBarCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 32)

    val navBarStyle: StateFlow<String> = userPreferencesRepository.navBarStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NavBarStyle.DEFAULT
        )

    val libraryNavigationMode: StateFlow<String> = userPreferencesRepository.libraryNavigationModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryNavigationMode.TAB_ROW
        )

    val carouselStyle: StateFlow<String> = userPreferencesRepository.carouselStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CarouselStyle.NO_PEEK
        )

    val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks> = userPreferencesRepository.fullPlayerLoadingTweaksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FullPlayerLoadingTweaks()
        )

    /**
     * Whether tapping the background of the player sheet toggles its state.
     * When disabled, users must use gestures or buttons to expand/collapse.
     */
    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Lyrics sync offset - now managed by LyricsStateHolder
    val currentSongLyricsSyncOffset: StateFlow<Int> = lyricsStateHolder.currentSongSyncOffset

    // Lyrics source preference (API_FIRST, EMBEDDED_FIRST, LOCAL_FIRST)
    val lyricsSourcePreference: StateFlow<LyricsSourcePreference> = userPreferencesRepository.lyricsSourcePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LyricsSourcePreference.EMBEDDED_FIRST
        )

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.MEDIUM)

    fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        lyricsStateHolder.setSyncOffset(songId, offsetMs)
    }

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )



    private val _isInitialThemePreloadComplete = MutableStateFlow(false)

    val isEndOfTrackTimerActive: StateFlow<Boolean> = sleepTimerStateHolder.isEndOfTrackTimerActive
    val activeTimerValueDisplay: StateFlow<String?> = sleepTimerStateHolder.activeTimerValueDisplay
    val playCount: StateFlow<Float> = sleepTimerStateHolder.playCount

    // Lyrics search UI state - managed by LyricsStateHolder
    val lyricsSearchUiState: StateFlow<LyricsSearchUiState> = lyricsStateHolder.searchUiState


    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _artistNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val artistNavigationRequests = _artistNavigationRequests.asSharedFlow()
    private var artistNavigationJob: Job? = null

    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = castStateHolder.castRoutes
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = castStateHolder.selectedRoute
    val routeVolume: StateFlow<Int> = castStateHolder.routeVolume
    val isRefreshingRoutes: StateFlow<Boolean> = castStateHolder.isRefreshingRoutes

    // Connectivity state delegated to ConnectivityStateHolder
    val isWifiEnabled: StateFlow<Boolean> = connectivityStateHolder.isWifiEnabled
    val isWifiRadioOn: StateFlow<Boolean> = connectivityStateHolder.isWifiRadioOn
    val wifiName: StateFlow<String?> = connectivityStateHolder.wifiName
    val isBluetoothEnabled: StateFlow<Boolean> = connectivityStateHolder.isBluetoothEnabled
    val bluetoothName: StateFlow<String?> = connectivityStateHolder.bluetoothName
    val bluetoothAudioDevices: StateFlow<List<String>> = connectivityStateHolder.bluetoothAudioDevices


    
    // Connectivity is now managed by ConnectivityStateHolder
    
    // Cast state is now managed by CastStateHolder
    private val sessionManager: SessionManager get() = castStateHolder.sessionManager

    val isRemotePlaybackActive: StateFlow<Boolean> = castStateHolder.isRemotePlaybackActive
    val isCastConnecting: StateFlow<Boolean> = castStateHolder.isCastConnecting
    private val castControlCategory get() = CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
    val remotePosition: StateFlow<Long> = castStateHolder.remotePosition

    private val _trackVolume = MutableStateFlow(1.0f)
    val trackVolume: StateFlow<Float> = _trackVolume.asStateFlow()


    @Inject
    lateinit var mediaMapper: com.theveloper.pixelplay.data.media.MediaMapper

    @Inject
    lateinit var imageCacheManager: com.theveloper.pixelplay.data.media.ImageCacheManager

    init {
        // Initialize helper classes with our coroutine scope
        listeningStatsTracker.initialize(viewModelScope)
        dailyMixStateHolder.initialize(viewModelScope)
        lyricsStateHolder.initialize(viewModelScope, lyricsLoadCallback, playbackStateHolder.stablePlayerState)
        playbackStateHolder.initialize(viewModelScope)
        themeStateHolder.initialize(viewModelScope)

        viewModelScope.launch {
            playbackStateHolder.stablePlayerState.collect { state ->
                _playerUiState.update { it.copy(currentPosition = state.currentPosition) }
            }
        }

        viewModelScope.launch {
            lyricsStateHolder.songUpdates.collect { update: Pair<com.theveloper.pixelplay.data.model.Song, com.theveloper.pixelplay.data.model.Lyrics?> ->
                val song = update.first
                val lyrics = update.second
                // Check if this update is relevant to the currently playing song OR the selected song
                if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                     updateSongInStates(song, lyrics)
                }
                if (_selectedSongForInfo.value?.id == song.id) {
                    _selectedSongForInfo.value = song
                }
            }
        }

        lyricsStateHolder.messageEvents
            .onEach { msg: String -> _toastEvents.emit(msg) }
            .launchIn(viewModelScope)
    }

    fun setTrackVolume(volume: Float) {
        mediaController?.let {
            val clampedVolume = volume.coerceIn(0f, 1f)
            it.volume = clampedVolume
            _trackVolume.value = clampedVolume
        }
    }

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

    val libraryTabsFlow: StateFlow<List<String>> = userPreferencesRepository.libraryTabsOrderFlow
        .map { orderJson ->
            if (orderJson != null) {
                try {
                    Json.decodeFromString<List<String>>(orderJson)
                } catch (e: Exception) {
                    listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
                }
            } else {
                listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED"))

    private val _loadedTabs = MutableStateFlow(emptySet<String>())
    private var lastBlockedDirectories: Set<String>? = null

    private val _currentLibraryTabId = MutableStateFlow(LibraryTabId.SONGS)
    val currentLibraryTabId: StateFlow<LibraryTabId> = _currentLibraryTabId.asStateFlow()

    private val _isSortingSheetVisible = MutableStateFlow(false)
    val isSortingSheetVisible: StateFlow<Boolean> = _isSortingSheetVisible.asStateFlow()

    val availableSortOptions: StateFlow<List<SortOption>> =
        currentLibraryTabId.map { tabId ->
            Trace.beginSection("PlayerViewModel.availableSortOptionsMapping")
            val options = when (tabId) {
                LibraryTabId.SONGS -> SortOption.SONGS
                LibraryTabId.ALBUMS -> SortOption.ALBUMS
                LibraryTabId.ARTISTS -> SortOption.ARTISTS
                LibraryTabId.PLAYLISTS -> SortOption.PLAYLISTS
                LibraryTabId.FOLDERS -> SortOption.FOLDERS
                LibraryTabId.LIKED -> SortOption.LIKED
            }
            Trace.endSection()
            options
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SortOption.SONGS
        )

    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isInitialDataLoaded = MutableStateFlow(false)

    // Public read-only access to all songs (using _masterAllSongs declared at class level)
    // Library State - delegated to LibraryStateHolder
    val allSongsFlow: StateFlow<ImmutableList<Song>> = libraryStateHolder.allSongs

    // Genres StateFlow - delegated to LibraryStateHolder
    val genres: StateFlow<ImmutableList<Genre>> = libraryStateHolder.genres
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )





    private var mediaController: MediaController? = null
    private val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
    private val mediaControllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command.customAction == MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE) {
                val enabled = args.getBoolean(
                    MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                    false
                )
                viewModelScope.launch {
                    if (enabled != playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }
    private val mediaControllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken)
            .setListener(mediaControllerListener)
            .buildAsync()
    private var pendingRepeatMode: Int? = null

    private var pendingPlaybackAction: (() -> Unit)? = null

    val favoriteSongIds: StateFlow<Set<String>> = userPreferencesRepository.favoriteSongIdsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isCurrentSongFavorite: StateFlow<Boolean> = combine(
        stablePlayerState,
        favoriteSongIds
    ) { state, ids ->
        state.currentSong?.id?.let { ids.contains(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Library State - delegated to LibraryStateHolder

    val favoriteSongs: StateFlow<ImmutableList<Song>> = combine(
        favoriteSongIds,
        _masterAllSongs,
        libraryStateHolder.currentFavoriteSortOption
    ) { ids: Set<String>, allSongsList: List<Song>, sortOption: SortOption ->
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
    .flowOn(Dispatchers.Default) // Execute combine and transformations on Default dispatcher
    .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    // Daily mix state is now managed by DailyMixStateHolder
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.dailyMixSongs
    val yourMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.yourMixSongs

    fun removeFromDailyMix(songId: String) {
        dailyMixStateHolder.removeFromDailyMix(songId)
    }

    /**
     * Observes a song by ID, combining the latest metadata from [allSongsFlow]
     * with the latest favorite status from [favoriteSongIds].
     * Returns null if the song is not found in the library.
     */
    fun observeSong(songId: String?): Flow<Song?> {
        if (songId == null) return flowOf(null)
        return combine(allSongsFlow, favoriteSongIds) { songs, favorites ->
            songs.find { it.id == songId }?.copy(isFavorite = favorites.contains(songId))
        }.distinctUntilChanged()
    }

    private fun updateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.updateDailyMix(
            allSongsFlow = allSongsFlow,
            favoriteSongIdsFlow = userPreferencesRepository.favoriteSongIdsFlow
        )
    }

    fun shuffleAllSongs() {
        Log.d("ShuffleDebug", "shuffleAllSongs called.")
        // Don't use ExoPlayer's shuffle mode - we manually shuffle instead
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
        val isPlaying = playbackStateHolder.stablePlayerState.value.isPlaying
        
        // If something is playing, just toggle shuffle on current queue
        if (currentSong != null && isPlaying) {
            if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                toggleShuffle()
            }
            return
        }
        
        // Otherwise start a new shuffled queue
        val allSongs = _masterAllSongs.value
        if (allSongs.isNotEmpty()) {
            playSongsShuffled(allSongs, "All Songs (Shuffled)")
        }
    }

    fun playRandomSong() {
        val allSongs = _masterAllSongs.value
        if (allSongs.isNotEmpty()) {
            playSongsShuffled(allSongs, "All Songs (Shuffled)")
        }
    }

    fun shuffleFavoriteSongs() {
        Log.d("ShuffleDebug", "shuffleFavoriteSongs called.")
        // Don't use ExoPlayer's shuffle mode - we manually shuffle instead
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
        val isPlaying = playbackStateHolder.stablePlayerState.value.isPlaying
        
        // If something is playing, just toggle shuffle on current queue
        if (currentSong != null && isPlaying) {
            if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                toggleShuffle()
            }
            return
        }
        
        // Otherwise start a new shuffled queue
        val favSongs = favoriteSongs.value
        if (favSongs.isNotEmpty()) {
            playSongsShuffled(favSongs, "Liked Songs (Shuffled)")
        }
    }

    fun shuffleRandomAlbum() {
        val allAlbums = _playerUiState.value.albums
        if (allAlbums.isNotEmpty()) {
            val randomAlbum = allAlbums.random()
            val albumSongs = _masterAllSongs.value.filter { it.albumId == randomAlbum.id }
            if (albumSongs.isNotEmpty()) {
                playSongsShuffled(albumSongs, randomAlbum.title)
            }
        }
    }

    fun shuffleRandomArtist() {
        val allArtists = _playerUiState.value.artists
        if (allArtists.isNotEmpty()) {
            val randomArtist = allArtists.random()
            val artistSongs = _masterAllSongs.value.filter { it.artistId == randomArtist.id }
            if (artistSongs.isNotEmpty()) {
                playSongsShuffled(artistSongs, randomArtist.name)
            }
        }
    }


    private fun loadPersistedDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.loadPersistedDailyMix(allSongsFlow)
    }

    fun forceUpdateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.forceUpdate(
            allSongsFlow = allSongsFlow,
            favoriteSongIdsFlow = userPreferencesRepository.favoriteSongIdsFlow
        )
    }

    private var transitionSchedulerJob: Job? = null

    private fun incrementSongScore(song: Song) {
        listeningStatsTracker.onVoluntarySelection(song.id)
    }

    // MIN_SESSION_LISTEN_MS, currentSession, and ListeningStatsTracker class
    // have been moved to ListeningStatsTracker.kt for better modularity


    fun updatePredictiveBackCollapseFraction(fraction: Float) {
        _predictiveBackCollapseFraction.value = fraction.coerceIn(0f, 1f)
    }

    // Helper to resolve stored sort keys against the allowed group
    private fun resolveSortOption(
        optionKey: String?,
        allowed: Collection<SortOption>,
        fallback: SortOption
    ): SortOption {
        return SortOption.fromStorageKey(optionKey, allowed, fallback)
    }

    private fun MediaRouter.RouteInfo.isCastRoute(): Boolean {
        return supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK) ||
            supportsControlCategory(castControlCategory)
    }

    // Connectivity refresh delegated to ConnectivityStateHolder
    fun refreshLocalConnectionInfo() {
        connectivityStateHolder.refreshLocalConnectionInfo()
    }

    init {
        Log.i("PlayerViewModel", "init started.")

        // Cast initialization if already connected
        val currentSession = sessionManager.currentCastSession
        if (currentSession != null) {
            castStateHolder.setCastPlayer(CastPlayer(currentSession))
            castStateHolder.setRemotePlaybackActive(true)
        }



        viewModelScope.launch {
            userPreferencesRepository.migrateTabOrder()
        }

        viewModelScope.launch {
            userPreferencesRepository.ensureLibrarySortDefaults()
        }

        viewModelScope.launch {
            userPreferencesRepository.isFoldersPlaylistViewFlow.collect { isPlaylistView ->
                setFoldersPlaylistViewState(isPlaylistView)
            }
        }

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

        viewModelScope.launch {
            combine(libraryTabsFlow, lastLibraryTabIndexFlow) { tabs, index ->
                tabs.getOrNull(index)?.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
            }.collect { tabId ->
                _currentLibraryTabId.value = tabId
            }
        }

        // Load initial sort options ONCE at startup.
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

            _playerUiState.update {
                it.copy(
                    currentSongSortOption = initialSongSort,
                    currentAlbumSortOption = initialAlbumSort,
                    currentArtistSortOption = initialArtistSort,
                    currentFavoriteSortOption = initialLikedSort
                )
            }
            // Also update the dedicated flow for favorites to ensure consistency
            // _currentFavoriteSortOptionStateFlow.value = initialLikedSort // Delegated to LibraryStateHolder

            sortSongs(initialSongSort, persist = false)
            sortAlbums(initialAlbumSort, persist = false)
            sortArtists(initialArtistSort, persist = false)
            sortFavoriteSongs(initialLikedSort, persist = false)
        }

        viewModelScope.launch {
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (isPersistent) {
                // If persistent shuffle is on, read the last used shuffle state (On/Off)
                val savedShuffle = userPreferencesRepository.isShuffleOnFlow.first()
                // Update the UI state so the shuffle button reflects the saved setting immediately
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = savedShuffle) }
            }
        }

        // launchColorSchemeProcessor() - Handled by ThemeStateHolder and on-demand calls

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
            if (!isSyncingStateFlow.value && !_isInitialDataLoaded.value && _masterAllSongs.value.isEmpty()) {
                Log.i("PlayerViewModel", "Initial check: Sync not active and initial data not loaded. Calling resetAndLoadInitialData.")
                resetAndLoadInitialData("Initial Check")
            }
        }

        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                // Pass controller to PlaybackStateHolder
                playbackStateHolder.setMediaController(mediaController)
                
                setupMediaControllerListeners()
                flushPendingRepeatMode()
                syncShuffleStateWithSession(playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                // Execute any pending action that was queued while the controller was connecting
                pendingPlaybackAction?.invoke()
                pendingPlaybackAction = null
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
            }
        }, ContextCompat.getMainExecutor(context))

        
        // Start Cast discovery
        castStateHolder.startDiscovery()
        
        // Observe selection for HTTP server management
        viewModelScope.launch {
            castStateHolder.selectedRoute.collect { route ->
                if (route != null && !route.isDefault && route.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {
                     castTransferStateHolder.ensureHttpServerRunning()
                } else if (route?.isDefault == true) {
                     context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                }
            }
        }

        // Initialize connectivity monitoring (WiFi/Bluetooth)
        connectivityStateHolder.initialize()

        // Initialize sleep timer state holder
        sleepTimerStateHolder.initialize(
            scope = viewModelScope,
            toastEmitter = { msg -> _toastEvents.emit(msg) },
            mediaControllerProvider = { mediaController },
            currentSongIdProvider = { stablePlayerState.map { it.currentSong?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null) },
            songTitleResolver = { songId -> _masterAllSongs.value.find { it.id == songId }?.title ?: "Unknown" }
        )

        // Initialize SearchStateHolder
        searchStateHolder.initialize(viewModelScope)

        // Collect SearchStateHolder flows
        viewModelScope.launch {
            searchStateHolder.searchResults.collect { results ->
                _playerUiState.update { it.copy(searchResults = results) }
            }
        }
        viewModelScope.launch {
            searchStateHolder.selectedSearchFilter.collect { filter ->
                _playerUiState.update { it.copy(selectedSearchFilter = filter) }
            }
        }
        viewModelScope.launch {
            searchStateHolder.searchHistory.collect { history ->
                _playerUiState.update { it.copy(searchHistory = history) }
            }
        }

        // Initialize AiStateHolder
        aiStateHolder.initialize(
            scope = viewModelScope,
            allSongsProvider = { _masterAllSongs.value },
            favoriteSongIdsProvider = { favoriteSongIds.value },
            toastEmitter = { msg -> viewModelScope.launch { _toastEvents.emit(msg) } },
            playSongsCallback = { songs, startSong, queueName -> playSongs(songs, startSong, queueName) },
            openPlayerSheetCallback = { _isSheetVisible.value = true }
        )

        // Collect AiStateHolder flows
        viewModelScope.launch {
            aiStateHolder.showAiPlaylistSheet.collect { show ->
                _showAiPlaylistSheet.value = show
            }
        }
        viewModelScope.launch {
            aiStateHolder.isGeneratingAiPlaylist.collect { generating ->
                _isGeneratingAiPlaylist.value = generating
            }
        }
        viewModelScope.launch {
            aiStateHolder.aiError.collect { error ->
                _aiError.value = error
            }
        }
        viewModelScope.launch {
            aiStateHolder.isGeneratingMetadata.collect { generating ->
                _playerUiState.update { it.copy(isGeneratingAiMetadata = generating) }
            }
        }

        viewModelScope.launch {
            aiStateHolder.isGeneratingMetadata.collect { generating ->
                _playerUiState.update { it.copy(isGeneratingAiMetadata = generating) }
            }
        }

        // Initialize LibraryStateHolder
        libraryStateHolder.initialize(viewModelScope)

        // Collect LibraryStateHolder flows to sync with UI State
        viewModelScope.launch {
            libraryStateHolder.allSongs.collect { songs ->
                _playerUiState.update { it.copy(allSongs = songs, songCount = songs.size) }
                // Update master songs for Cast usage if needed
                _masterAllSongs.value = songs
            }
        }
        viewModelScope.launch {
            libraryStateHolder.albums.collect { albums ->
                _playerUiState.update { it.copy(albums = albums) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.artists.collect { artists ->
                _playerUiState.update { it.copy(artists = artists) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.musicFolders.collect { folders ->
                _playerUiState.update { it.copy(musicFolders = folders) }
            }
        }
        // Sync loading states
        viewModelScope.launch {
            libraryStateHolder.isLoadingLibrary.collect { loading ->
                _playerUiState.update { it.copy(isLoadingInitialSongs = loading) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.isLoadingCategories.collect { loading ->
                _playerUiState.update { it.copy(isLoadingLibraryCategories = loading) }
            }
        }
        
        // Sync sort options
        viewModelScope.launch {
            libraryStateHolder.currentSongSortOption.collect { sort ->
                _playerUiState.update { it.copy(currentSongSortOption = sort) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentAlbumSortOption.collect { sort ->
                _playerUiState.update { it.copy(currentAlbumSortOption = sort) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentArtistSortOption.collect { sort ->
                _playerUiState.update { it.copy(currentArtistSortOption = sort) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentFolderSortOption.collect { sort ->
                _playerUiState.update { it.copy(currentFolderSortOption = sort) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentFavoriteSortOption.collect { sort ->
                _playerUiState.update { it.copy(currentFavoriteSortOption = sort) }
            }
        }


        castTransferStateHolder.initialize(
            scope = viewModelScope,
            getCurrentQueue = { _playerUiState.value.currentPlaybackQueue },
            updateQueue = { newQueue -> 
                _playerUiState.update { 
                    it.copy(currentPlaybackQueue = newQueue.toImmutableList()) 
                }
            },
            getMasterAllSongs = { _masterAllSongs.value },
            onTransferBackComplete = { startProgressUpdates() },
            onSheetVisible = { _isSheetVisible.value = true },
            onDisconnect = { disconnect() },
            onSongChanged = { uriString ->
                uriString?.toUri()?.let { uri ->
                     viewModelScope.launch { 
                         val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                         themeStateHolder.extractAndGenerateColorScheme(uri, currentUri) 
                     }
                }
            }
        )



        viewModelScope.launch {
            userPreferencesRepository.repeatModeFlow.collect { mode ->
                applyPreferredRepeatMode(mode)
            }
        }

        viewModelScope.launch {
            stablePlayerState
                .map { it.isShuffleEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    syncShuffleStateWithSession(enabled)
                }
        }

        // Auto-hide undo bar when a new song starts playing
        setupUndoBarPlaybackObserver()

        Trace.endSection() // End PlayerViewModel.init
    }

    fun onMainActivityStart() {
        Trace.beginSection("PlayerViewModel.onMainActivityStart")
        preloadThemesAndInitialData()
        checkAndUpdateDailyMixIfNeeded()
        Trace.endSection()
    }


    private fun checkAndUpdateDailyMixIfNeeded() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.checkAndUpdateIfNeeded(
            allSongsFlow = allSongsFlow,
            favoriteSongIdsFlow = userPreferencesRepository.favoriteSongIdsFlow
        )
    }

    private fun preloadThemesAndInitialData() {
        Trace.beginSection("PlayerViewModel.preloadThemesAndInitialData")
        viewModelScope.launch {
            _isInitialThemePreloadComplete.value = false
            if (isSyncingStateFlow.value && !_isInitialDataLoaded.value) {
                // Sync is active - defer to sync completion handler
            } else if (!_isInitialDataLoaded.value && _masterAllSongs.value.isEmpty()) {
                resetAndLoadInitialData("preloadThemesAndInitialData")
            }
            _isInitialThemePreloadComplete.value = true
        }
        Trace.endSection()
    }

    private fun loadInitialLibraryDataParallel() {
        libraryStateHolder.loadSongsFromRepository()
        libraryStateHolder.loadAlbumsFromRepository()
        libraryStateHolder.loadArtistsFromRepository()
        libraryStateHolder.loadFoldersFromRepository()
    }

    private fun resetAndLoadInitialData(caller: String = "Unknown") {
        Trace.beginSection("PlayerViewModel.resetAndLoadInitialData")
        loadInitialLibraryDataParallel()
        updateDailyMix()
        Trace.endSection()
    }

    fun loadSongsIfNeeded() = libraryStateHolder.loadSongsIfNeeded()
    fun loadAlbumsIfNeeded() = libraryStateHolder.loadAlbumsIfNeeded()
    fun loadArtistsIfNeeded() = libraryStateHolder.loadArtistsIfNeeded()
    fun loadFoldersFromRepository() = libraryStateHolder.loadFoldersFromRepository()

    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true
    ) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            val mediaStatus = remoteMediaClient.mediaStatus
            val remoteQueueItems = mediaStatus?.queueItems ?: emptyList()
            val itemInQueue = remoteQueueItems.find { it.customData?.optString("songId") == song.id }

            if (itemInQueue != null) {
                // Song is already in the remote queue; prefer adjacent navigation commands to
                // mirror the no-glitch behavior of next/previous buttons regardless of context
                // mismatches.
                castTransferStateHolder.markPendingRemoteSong(song)
                val currentItemId = mediaStatus?.currentItemId
                val currentIndex = remoteQueueItems.indexOfFirst { it.itemId == currentItemId }
                val targetIndex = remoteQueueItems.indexOf(itemInQueue)
                val castPlayer = castStateHolder.castPlayer
                when {
                    currentIndex >= 0 && targetIndex - currentIndex == 1 -> castPlayer?.next()
                    currentIndex >= 0 && targetIndex - currentIndex == -1 -> castPlayer?.previous()
                    else -> castPlayer?.jumpToItem(itemInQueue.itemId, 0L)
                }
                if (isVoluntaryPlay) incrementSongScore(song)
            } else {
                val lastQueue = castTransferStateHolder.lastRemoteQueue
                val currentRemoteId = mediaStatus
                    ?.let { status ->
                        status.getQueueItemById(status.getCurrentItemId())
                            ?.customData?.optString("songId")
                    } ?: castTransferStateHolder.lastRemoteSongId
                val currentIndex = lastQueue.indexOfFirst { it.id == currentRemoteId }
                val targetIndex = lastQueue.indexOfFirst { it.id == song.id }
                if (currentIndex != -1 && targetIndex != -1) {
                    castTransferStateHolder.markPendingRemoteSong(song)
                    val castPlayer = castStateHolder.castPlayer
                    when (targetIndex - currentIndex) {
                        1 -> castPlayer?.next()
                        -1 -> castPlayer?.previous()
                        else -> {
                           viewModelScope.launch {
                               castTransferStateHolder.playRemoteQueue(contextSongs, song, playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                           }
                        }
                    }
                } else {
                    viewModelScope.launch {
                        castTransferStateHolder.playRemoteQueue(contextSongs, song, playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                    }
                }
                if (isVoluntaryPlay) incrementSongScore(song)
            }
            return
        }    // Local playback logic
            mediaController?.let { controller ->
                val currentQueue = _playerUiState.value.currentPlaybackQueue
                val songIndexInQueue = currentQueue.indexOfFirst { it.id == song.id }
                val queueMatchesContext = currentQueue.matchesSongOrder(contextSongs)

                if (songIndexInQueue != -1 && queueMatchesContext) {
                    if (controller.currentMediaItemIndex == songIndexInQueue) {
                        if (!controller.isPlaying) controller.play()
                    } else {
                        controller.seekTo(songIndexInQueue, 0L)
                        controller.play()
                    }
                    if (isVoluntaryPlay) incrementSongScore(song)
                } else {
                    if (isVoluntaryPlay) incrementSongScore(song)
                    playSongs(contextSongs, song, queueName, null)
                }
            }
            _predictiveBackCollapseFraction.value = 0f
        }

    fun showAndPlaySong(song: Song) {
        Log.d("ShuffleDebug", "showAndPlaySong (single song overload) called for '${song.title}'")
        val allSongs = _masterAllSongs.value.toList()
        // Look up the current version of the song in allSongs to get the most up-to-date metadata
        val currentSong = allSongs.find { it.id == song.id } ?: song
        showAndPlaySong(currentSong, allSongs, "Library")
    }

    private fun List<Song>.matchesSongOrder(contextSongs: List<Song>): Boolean {
        if (size != contextSongs.size) return false
        return indices.all { this[it].id == contextSongs[it].id }
    }

    fun playAlbum(album: Album) {
        Log.d("ShuffleDebug", "playAlbum called for album: ${album.title}")
        viewModelScope.launch {
            try {
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForAlbum(album.id).first()
                }

                if (songsList.isNotEmpty()) {
                    val sortedSongs = songsList.sortedWith(
                        compareBy<Song> {
                            if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
                        }.thenBy { it.title.lowercase() }
                    )

                    playSongs(sortedSongs, sortedSongs.first(), album.title, null)
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

    fun triggerArtistNavigationFromPlayer(artistId: Long) {
        if (artistId <= 0) {
            Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored invalid artistId=$artistId")
            return
        }

        val existingJob = artistNavigationJob
        if (existingJob != null && existingJob.isActive) {
            Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored; navigation already in progress for artistId=$artistId")
            return
        }

        artistNavigationJob?.cancel()
        artistNavigationJob = viewModelScope.launch {
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            Log.d(
                "ArtistDebug",
                "triggerArtistNavigationFromPlayer: artistId=$artistId, songId=${currentSong?.id}, title=${currentSong?.title}"
            )
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _artistNavigationRequests.emit(artistId)
        }
    }

    suspend fun awaitSheetState(target: PlayerSheetState) {
        sheetState.first { it == target }
    }

    suspend fun awaitPlayerCollapse(threshold: Float = 0.1f, timeoutMillis: Long = 800L) {
        withTimeoutOrNull(timeoutMillis) {
            snapshotFlow { playerContentExpansionFraction.value }
                .first { it <= threshold }
        }
    }

    private fun resolveSongFromMediaItem(mediaItem: MediaItem): Song? {
        _playerUiState.value.currentPlaybackQueue.find { it.id == mediaItem.mediaId }?.let { return it }
        _masterAllSongs.value.find { it.id == mediaItem.mediaId }?.let { return it }

        return mediaMapper.resolveSongFromMediaItem(mediaItem)
    }

    private fun updateCurrentPlaybackQueueFromPlayer(playerCtrl: MediaController?) {
        val currentMediaController = playerCtrl ?: mediaController ?: return
        val count = currentMediaController.mediaItemCount

        if (count == 0) {
            _playerUiState.update { it.copy(currentPlaybackQueue = persistentListOf()) }
            return
        }

        val queue = mutableListOf<Song>()

        for (i in 0 until count) {
            val mediaItem = currentMediaController.getMediaItemAt(i)
            resolveSongFromMediaItem(mediaItem)?.let { queue.add(it) }
        }

        _playerUiState.update { it.copy(currentPlaybackQueue = queue.toImmutableList()) }
        if (queue.isNotEmpty()) {
            _isSheetVisible.value = true
        }
    }

    private fun applyPreferredRepeatMode(@Player.RepeatMode mode: Int) {
        playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = mode) }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            pendingRepeatMode = mode
            return
        }

        val controller = mediaController
        if (controller == null) {
            pendingRepeatMode = mode
            return
        }

        if (controller.repeatMode != mode) {
            controller.repeatMode = mode
        }
        pendingRepeatMode = null
    }

    private fun flushPendingRepeatMode() {
        pendingRepeatMode?.let { applyPreferredRepeatMode(it) }
    }

    private fun setupMediaControllerListeners() {
        Trace.beginSection("PlayerViewModel.setupMediaControllerListeners")
        val playerCtrl = mediaController ?: return Trace.endSection()
        _trackVolume.value = playerCtrl.volume
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                isShuffleEnabled = it.isShuffleEnabled,
                repeatMode = playerCtrl.repeatMode,
                isPlaying = playerCtrl.isPlaying
            )
        }

        updateCurrentPlaybackQueueFromPlayer(playerCtrl)

        playerCtrl.currentMediaItem?.let { mediaItem ->
            val song = resolveSongFromMediaItem(mediaItem)

            if (song != null) {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = song,
                        totalDuration = playerCtrl.duration.coerceAtLeast(0L)
                    )
                }
                _playerUiState.update { it.copy(currentPosition = playerCtrl.currentPosition.coerceAtLeast(0L)) }
                viewModelScope.launch {
                    song.albumArtUriString?.toUri()?.let { uri ->
                        val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                        themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                    }
                }
                listeningStatsTracker.onSongChanged(
                    song = song,
                    positionMs = playerCtrl.currentPosition.coerceAtLeast(0L),
                    durationMs = playerCtrl.duration.coerceAtLeast(0L),
                    isPlaying = playerCtrl.isPlaying
                )
                if (playerCtrl.isPlaying) {
                    _isSheetVisible.value = true
                    startProgressUpdates()
                }
            } else {
                playbackStateHolder.updateStablePlayerState { it.copy(currentSong = null, isPlaying = false) }
                _playerUiState.update { it.copy(currentPosition = 0L) }
            }
        }

        playerCtrl.addListener(object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                _trackVolume.value = volume
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackStateHolder.updateStablePlayerState { it.copy(isPlaying = isPlaying) }
                listeningStatsTracker.onPlayStateChanged(
                    isPlaying = isPlaying,
                    positionMs = playerCtrl.currentPosition.coerceAtLeast(0L)
                )
                if (isPlaying) {
                    _isSheetVisible.value = true
                    if (_playerUiState.value.preparingSongId != null) {
                        _playerUiState.update { it.copy(preparingSongId = null) }
                    }
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                    val pausedPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    if (pausedPosition != _playerUiState.value.currentPosition) {
                        _playerUiState.update { it.copy(currentPosition = pausedPosition) }
                    }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitionSchedulerJob?.cancel()
                lyricsStateHolder.cancelLoading()
                transitionSchedulerJob = viewModelScope.launch {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val activeEotSongId = EotStateHolder.eotTargetSongId.value
                        val previousSongId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                        if (isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                            playerCtrl.seekTo(0L)
                            playerCtrl.pause()

                            val finishedSongTitle = _masterAllSongs.value.find { it.id == previousSongId }?.title
                                ?: "Track"

                            viewModelScope.launch {
                                _toastEvents.emit("Playback stopped: $finishedSongTitle finished (End of Track).")
                            }
                            cancelSleepTimer(suppressDefaultToast = true)
                        }
                    }

                    mediaItem?.let { transitionedItem ->
                        listeningStatsTracker.finalizeCurrentSession()
                        val song = resolveSongFromMediaItem(transitionedItem)
                        resetLyricsSearchState()
                        playbackStateHolder.updateStablePlayerState {
                            val hasSong = song != null
                            it.copy(
                                currentSong = song,
                                totalDuration = if (hasSong) playerCtrl.duration.coerceAtLeast(0L) else 0L,
                                lyrics = null,
                                isLoadingLyrics = hasSong
                            )
                        }
                        _playerUiState.update { it.copy(currentPosition = 0L) }

                        song?.let { currentSongValue ->
                            listeningStatsTracker.onSongChanged(
                                song = currentSongValue,
                                positionMs = 0L,
                                durationMs = playerCtrl.duration.coerceAtLeast(0L),
                                isPlaying = playerCtrl.isPlaying
                            )
                            viewModelScope.launch {
                                currentSongValue.albumArtUriString?.toUri()?.let { uri ->
                                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                                    themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                                }
                            }
                            loadLyricsForCurrentSong()
                        }
                    } ?: run {
                        if (!isCastConnecting.value && !isRemotePlaybackActive.value) {
                            lyricsStateHolder.cancelLoading()
                            playbackStateHolder.updateStablePlayerState {
                                it.copy(
                                    currentSong = null,
                                    isPlaying = false,
                                    lyrics = null,
                                    isLoadingLyrics = false,
                                    totalDuration = 0L
                                )
                            }
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackStateHolder.updateStablePlayerState { it.copy(totalDuration = playerCtrl.duration.coerceAtLeast(0L)) }
                    listeningStatsTracker.updateDuration(playerCtrl.duration.coerceAtLeast(0L))
                    startProgressUpdates()
                }
                if (playbackState == Player.STATE_ENDED) {
                    listeningStatsTracker.finalizeCurrentSession()
                }
                if (playbackState == Player.STATE_IDLE && playerCtrl.mediaItemCount == 0) {
                    if (!isCastConnecting.value && !isRemotePlaybackActive.value) {
                        listeningStatsTracker.onPlaybackStopped()
                        lyricsStateHolder.cancelLoading()
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = null,
                                isPlaying = false,
                                lyrics = null,
                                isLoadingLyrics = false,
                                totalDuration = 0L
                            )
                        }
                        _playerUiState.update { it.copy(currentPosition = 0L) }
                    }
                }
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // IMPORTANT: We don't use ExoPlayer's shuffle mode anymore
                // Instead, we manually shuffle the queue to fix crossfade issues
                // If ExoPlayer's shuffle gets enabled (e.g., from media button), turn it off and use our toggle
                if (shuffleModeEnabled) {
                    playerCtrl.shuffleModeEnabled = false
                    // Trigger our manual shuffle instead
                    if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = repeatMode) }
                viewModelScope.launch { userPreferencesRepository.setRepeatMode(repeatMode) }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                transitionSchedulerJob?.cancel()
                updateCurrentPlaybackQueueFromPlayer(mediaController)
            }
        })
        Trace.endSection()
    }


    // rebuildPlayerQueue functionality moved to PlaybackStateHolder (simplified)
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        viewModelScope.launch {
            transitionSchedulerJob?.cancel()
            
            // Validate songs - filter out any with missing files (efficient: uses contentUri check)
            val validSongs = songsToPlay.filter { song ->
                try {
                    // Use ContentResolver to check if URI is still valid (more efficient than File check)
                    val uri = song.contentUriString.toUri()
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (e: Exception) {
                    Timber.w("Song file missing or inaccessible: ${song.title}")
                    false
                }
            }
            
            if (validSongs.isEmpty()) {
                _toastEvents.emit(context.getString(R.string.no_valid_songs))
                return@launch
            }
            
            // Adjust startSong if it was filtered out
            val validStartSong = if (validSongs.contains(startSong)) startSong else validSongs.first()
            
            // Store the original order so we can "unshuffle" later if the user turns shuffle off
            queueStateHolder.setOriginalQueueOrder(validSongs)
            queueStateHolder.saveOriginalQueueState(validSongs, queueName)

            // Check if the user wants shuffle to be persistent across different albums
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            // Check if shuffle is currently active in the player
            val isShuffleOn = playbackStateHolder.stablePlayerState.value.isShuffleEnabled

            // If Persistent Shuffle is OFF, we reset shuffle to "false" every time a new album starts
            if (!isPersistent) {
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = false) }
            }

            // If shuffle is persistent and currently ON, we shuffle the new songs immediately
            val finalSongsToPlay = if (isPersistent && isShuffleOn) {
                // Shuffle the list but make sure the song you clicked stays at its current index or starts first
                QueueUtils.buildAnchoredShuffleQueue(validSongs, validSongs.indexOf(validStartSong).coerceAtLeast(0))
            } else {
                // Otherwise, just use the normal sequential order
                validSongs
            }

            // Send the final list (shuffled or not) to the player engine
            internalPlaySongs(finalSongsToPlay, validStartSong, queueName, playlistId)
        }
    }

    // Start playback with shuffle enabled in one coroutine to avoid racing queue updates
    fun playSongsShuffled(songsToPlay: List<Song>, queueName: String = "None", playlistId: String? = null) {
        viewModelScope.launch {
            val result = queueStateHolder.prepareShuffledQueue(songsToPlay, queueName)
            if (result == null) {
                sendToast("No songs to shuffle.")
                return@launch
            }
            
            val (shuffledQueue, startSong) = result
            transitionSchedulerJob?.cancel()

            // Optimistically update shuffle state
            playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = true) }
            launch { userPreferencesRepository.setShuffleOn(true) }

            internalPlaySongs(shuffledQueue, startSong, queueName, playlistId)
        }
    }

    fun playExternalUri(uri: Uri) {
        viewModelScope.launch {
            val externalResult = externalMediaStateHolder.buildExternalSongFromUri(uri)
            if (externalResult == null) {
                sendToast(context.getString(R.string.external_playback_error))
                return@launch
            }

            transitionSchedulerJob?.cancel()

            val queueSongs = externalMediaStateHolder.buildExternalQueue(externalResult, uri)
            val immutableQueue = queueSongs.toImmutableList()

            _playerUiState.update { state ->
                state.copy(
                    currentPosition = 0L,
                    currentPlaybackQueue = immutableQueue,
                    currentQueueSourceName = context.getString(R.string.external_queue_label),
                    showDismissUndoBar = false,
                    dismissedSong = null,
                    dismissedQueue = persistentListOf(),
                    dismissedQueueName = "",
                    dismissedPosition = 0L
                )
            }

            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(
                    currentSong = externalResult.song,
                    isPlaying = true,
                    totalDuration = externalResult.song.duration,
                    lyrics = null,
                    isLoadingLyrics = false
                )
            }

            _sheetState.value = PlayerSheetState.COLLAPSED
            _isSheetVisible.value = true

            internalPlaySongs(queueSongs, externalResult.song, context.getString(R.string.external_queue_label), null)
            showPlayer()
        }
    }

    fun showPlayer() {
        if (stablePlayerState.value.currentSong != null) {
            _isSheetVisible.value = true
        }
    }



    private suspend fun internalPlaySongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        // Update dynamic shortcut for last played playlist
        if (playlistId != null && queueName != "None") {
            appShortcutManager.updateLastPlaylistShortcut(playlistId, queueName)
        }
        
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castTransferStateHolder.playRemoteQueue(
                songsToPlay = songsToPlay,
                startSong = startSong,
                isShuffleEnabled = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
            )

            _playerUiState.update { it.copy(currentPlaybackQueue = songsToPlay.toImmutableList(), currentQueueSourceName = queueName) }
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = startSong,
                    isPlaying = true,
                    totalDuration = startSong.duration.coerceAtLeast(0L)
                )
            }
        } else {
            val playSongsAction = {
                // Use Direct Engine Access to avoid TransactionTooLargeException on Binder
                val enginePlayer = dualPlayerEngine.masterPlayer
                
                val mediaItems = songsToPlay.map { song ->
                    val metadataBuilder = MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.displayArtist)
                    playlistId?.let {
                        val extras = Bundle()
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
                    // Direct access: No IPC limit involved
                    enginePlayer.setMediaItems(mediaItems, startIndex, 0L)
                    enginePlayer.prepare()
                    enginePlayer.play()
                    
                    _playerUiState.update { it.copy(currentPlaybackQueue = songsToPlay.toImmutableList(), currentQueueSourceName = queueName) }
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            currentSong = startSong,
                            isPlaying = true,
                            totalDuration = startSong.duration.coerceAtLeast(0L)
                        )
                    }
                }
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }

            // We still check for mediaController to ensure the Service is bound and active
            // even though we aren't using it for the heavy lifting anymore.
            if (mediaController == null) {
                Timber.w("MediaController not available. Queuing playback action.")
                pendingPlaybackAction = playSongsAction
            } else {
                playSongsAction()
            }
        }
    }


    private fun loadAndPlaySong(song: Song) {
        val controller = mediaController
        if (controller == null) {
            pendingPlaybackAction = {
                loadAndPlaySong(song)
            }
            return
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.contentUriString.toUri())
            .setMediaMetadata(MediaItemBuilder.build(song).mediaMetadata)
            .build()
        if (controller.currentMediaItem?.mediaId == song.id) {
            if (!controller.isPlaying) controller.play()
        } else {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
        playbackStateHolder.updateStablePlayerState { it.copy(currentSong = song, isPlaying = true) }
        viewModelScope.launch {
            song.albumArtUriString?.toUri()?.let { uri ->
                val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                themeStateHolder.extractAndGenerateColorScheme(uri, currentUri, isPreload = false)
            }
        }
    }

// buildMediaMetadataForSong moved to MediaItemBuilder

    private fun syncShuffleStateWithSession(enabled: Boolean) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
        }
        controller.sendCustomCommand(
            SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle.EMPTY),
            args
        )
    }

    fun toggleShuffle(currentSongOverride: Song? = null) {
        val currentQueue = _playerUiState.value.currentPlaybackQueue.toList()
        val currentSong = currentSongOverride
            ?: playbackStateHolder.stablePlayerState.value.currentSong
            ?: mediaController?.currentMediaItem?.let { resolveSongFromMediaItem(it) }
            ?: currentQueue.firstOrNull()
        
        playbackStateHolder.toggleShuffle(
            currentSongs = currentQueue,
            currentSong = currentSong,
            currentQueueSourceName = _playerUiState.value.currentQueueSourceName,
            updateQueueCallback = { newQueue ->
                _playerUiState.update { it.copy(currentPlaybackQueue = newQueue.toImmutableList()) }
            }
        )
    }

    fun cycleRepeatMode() {
        playbackStateHolder.cycleRepeatMode()
    }

    fun toggleFavorite() {
        playbackStateHolder.stablePlayerState.value.currentSong?.id?.let { songId ->
            viewModelScope.launch {
                userPreferencesRepository.toggleFavoriteSong(songId)
            }
        }
    }

    fun toggleFavoriteSpecificSong(song: Song, removing: Boolean = false) {
        viewModelScope.launch {
            userPreferencesRepository.toggleFavoriteSong(song.id, removing)
        }
    }

    fun addSongToQueue(song: Song) {
        mediaController?.let { controller ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.contentUriString.toUri())
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.displayArtist)
                    .setArtworkUri(song.albumArtUriString?.toUri())
                    .build())
                .build()
            controller.addMediaItem(mediaItem)
            // Queue UI is synced via onTimelineChanged listener
        }
    }

    fun addSongNextToQueue(song: Song) {
        mediaController?.let { controller ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.contentUriString.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.displayArtist)
                        .setArtworkUri(song.albumArtUriString?.toUri())
                        .build()
                )
                .build()

            val insertionIndex = if (controller.currentMediaItemIndex != C.INDEX_UNSET) {
                (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            } else {
                controller.mediaItemCount
            }

            controller.addMediaItem(insertionIndex, mediaItem)
            // Queue UI is synced via onTimelineChanged listener
        }
    }
    private suspend fun showMaterialDeleteConfirmation(activity: Activity, song: Song): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()

                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle("Delete song?")
                    .setMessage("""
                    "${song.title}" by ${song.displayArtist}

                    This song will be permanently deleted from your device and cannot be recovered.
                """.trimIndent())
                    .setPositiveButton("Delete") { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()

                // Wait for user response - this will suspend until complete is called
                userChoice.await()
            } catch (e: Exception) {
                false
            }
        }
    }

    fun deleteFromDevice(activity: Activity, song: Song, onResult: (Boolean) -> Unit = {}){
        viewModelScope.launch {
            val userConfirmed = showMaterialDeleteConfirmation(activity, song)
            if (!userConfirmed) {
                onResult(false)
                return@launch
            }
            // Check if we're currently playing the song being deleted
            if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                listeningStatsTracker.finalizeCurrentSession()
                mediaController?.pause()
                mediaController?.stop()
                mediaController?.clearMediaItems()
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        totalDuration = 0L
                    )
                }
            }

            val success = metadataEditStateHolder.deleteSong(song)
            if (success) {
                _toastEvents.emit("File deleted")
                removeFromMediaControllerQueue(song.id)
                removeSong(song)
                onResult(true)
            } else {
                _toastEvents.emit("Can't delete the file or file not found")
                onResult(false)
            }
        }
    }

    suspend fun removeSong(song: Song) {
        toggleFavoriteSpecificSong(song, true)
        _playerUiState.update { currentState ->
            currentState.copy(
                currentPosition = 0L,
                currentPlaybackQueue = currentState.currentPlaybackQueue.filter { it.id != song.id }.toImmutableList(),
                currentQueueSourceName = ""
            )
        }
        _masterAllSongs.value = _masterAllSongs.value.filter { it.id != song.id }.toImmutableList()
        _isSheetVisible.value = false
        musicRepository.deleteById(song.id.toLong())
        userPreferencesRepository.removeSongFromAllPlaylists(song.id)
    }

    private fun removeFromMediaControllerQueue(songId: String) {
        val controller = mediaController ?: return

        try {
            // Get the current timeline and media item count
            val timeline = controller.currentTimeline
            val mediaItemCount = timeline.windowCount

            // Find the media item to remove by iterating through windows
            for (i in 0 until mediaItemCount) {
                val window = timeline.getWindow(i, Timeline.Window())
                if (window.mediaItem.mediaId == songId) {
                    // Remove the media item by index
                    controller.removeMediaItem(i)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("MediaController", "Error removing from queue: ${e.message}")
        }
    }

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
            } else {
                // If there are items in the remote queue, just play.
                // Otherwise, load the current local queue to the remote player.
                if (remoteMediaClient.mediaQueue != null && remoteMediaClient.mediaQueue.itemCount > 0) {
                    castStateHolder.castPlayer?.play()
                } else {
                    val queue = _playerUiState.value.currentPlaybackQueue
                    if (queue.isNotEmpty()) {
                        val startSong = playbackStateHolder.stablePlayerState.value.currentSong ?: queue.first()
                        viewModelScope.launch {
                            internalPlaySongs(queue.toList(), startSong, _playerUiState.value.currentQueueSourceName)
                        }
                    }
                }
            }
        } else {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.currentMediaItem == null) {
                        val currentQueue = _playerUiState.value.currentPlaybackQueue
                        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                        when {
                            currentQueue.isNotEmpty() && currentSong != null -> {
                                viewModelScope.launch {
                                    transitionSchedulerJob?.cancel()
                                    internalPlaySongs(
                                        currentQueue.toList(),
                                        currentSong,
                                        _playerUiState.value.currentQueueSourceName
                                    )
                                }
                            }
                            currentSong != null -> {
                                loadAndPlaySong(currentSong)
                            }
                            _masterAllSongs.value.isNotEmpty() -> {
                                loadAndPlaySong(_masterAllSongs.value.first())
                            }
                            else -> {
                                controller.play()
                            }
                        }
                    } else {
                        controller.play()
                    }
                }
            }
        }
    }

    fun seekTo(position: Long) {
        playbackStateHolder.seekTo(position)
    }

    fun nextSong() {
        playbackStateHolder.nextSong()
    }

    fun previousSong() {
        playbackStateHolder.previousSong()
    }

    private fun startProgressUpdates() {
        playbackStateHolder.startProgressUpdates()
    }

    private fun stopProgressUpdates() {
        playbackStateHolder.stopProgressUpdates()
    }

    suspend fun getSongs(songIds: List<String>) : List<Song>{
        return musicRepository.getSongsByIds(songIds).first()
    }

    //Sorting
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortSongs(sortOption, persist)
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortAlbums(sortOption, persist)
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortArtists(sortOption, persist)
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortFavoriteSongs(sortOption, persist)
    }

    fun sortFolders(sortOption: SortOption) {
        libraryStateHolder.sortFolders(sortOption)
    }

    fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFoldersPlaylistView(isPlaylistView)
            setFoldersPlaylistViewState(isPlaylistView)
        }
    }

    fun navigateToFolder(path: String) {
        val storageRootPath = android.os.Environment.getExternalStorageDirectory().path
        if (path == storageRootPath) {
            _playerUiState.update {
                it.copy(
                    currentFolderPath = null,
                    currentFolder = null
                )
            }
            return
        }

        val folder = findFolder(path, _playerUiState.value.musicFolders)
        if (folder != null) {
            _playerUiState.update {
                it.copy(
                    currentFolderPath = path,
                    currentFolder = folder
                )
            }
        }
    }

    fun navigateBackFolder() {
        _playerUiState.update {
            val currentFolder = it.currentFolder
            if (currentFolder != null) {
                val parentPath = File(currentFolder.path).parent
                val parentFolder = findFolder(parentPath, _playerUiState.value.musicFolders)
                it.copy(
                    currentFolderPath = parentPath,
                    currentFolder = parentFolder
                )
            } else {
                it
            }
        }
    }

    private fun findFolder(path: String?, folders: List<MusicFolder>): MusicFolder? {
        if (path == null) {
            return null
        }
        val queue = ArrayDeque(folders)
        while (queue.isNotEmpty()) {
            val folder = queue.remove()
            if (folder.path == path) {
                return folder
            }
            queue.addAll(folder.subFolders)
        }
        return null
    }

    private fun setFoldersPlaylistViewState(isPlaylistView: Boolean) {
        _playerUiState.update { currentState ->
            currentState.copy(
                isFoldersPlaylistView = isPlaylistView,
                currentFolderPath = null,
                currentFolder = null
            )
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        searchStateHolder.updateSearchFilter(filterType)
    }

    fun loadSearchHistory(limit: Int = 15) {
        searchStateHolder.loadSearchHistory(limit)
    }

    fun onSearchQuerySubmitted(query: String) {
        searchStateHolder.onSearchQuerySubmitted(query)
    }

    fun performSearch(query: String) {
        searchStateHolder.performSearch(query)
    }

    fun deleteSearchHistoryItem(query: String) {
        searchStateHolder.deleteSearchHistoryItem(query)
    }

    fun clearSearchHistory() {
        searchStateHolder.clearSearchHistory()
    }

    // --- AI Playlist Generation ---

    // --- AI Playlist Generation ---

    fun showAiPlaylistSheet() {
        aiStateHolder.showAiPlaylistSheet()
    }

    fun dismissAiPlaylistSheet() {
        aiStateHolder.dismissAiPlaylistSheet()
    }

    fun generateAiPlaylist(prompt: String, minLength: Int, maxLength: Int, saveAsPlaylist: Boolean = false) {
        aiStateHolder.generateAiPlaylist(prompt, minLength, maxLength, saveAsPlaylist)
    }

    fun regenerateDailyMixWithPrompt(prompt: String) {
        aiStateHolder.regenerateDailyMixWithPrompt(prompt)
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

    fun selectRoute(route: MediaRouter.RouteInfo) {
        val selectedRouteId = castStateHolder.selectedRoute.value?.id
        val isCastRoute = route.isCastRoute() && !route.isDefault
        // Use castStateHolder.isRemotePlaybackActive directly
        val isSwitchingBetweenRemotes = isCastRoute &&
            (castStateHolder.isRemotePlaybackActive.value || castStateHolder.isCastConnecting.value) &&
            selectedRouteId != null &&
            selectedRouteId != route.id

        if (isSwitchingBetweenRemotes) {
            castStateHolder.setPendingCastRouteId(route.id)
            castStateHolder.setCastConnecting(true)
            sessionManager.currentCastSession?.let { sessionManager.endCurrentSession(true) }
        } else {
            castStateHolder.setPendingCastRouteId(null)
        }
        
        castStateHolder.selectRoute(route)
    }

    fun disconnect(resetConnecting: Boolean = true) {
        val start = SystemClock.elapsedRealtime()
        castStateHolder.setPendingCastRouteId(null)
        val wasRemote = castStateHolder.isRemotePlaybackActive.value
        if (wasRemote) {
            Timber.tag(CAST_LOG_TAG).i(
                "Manual disconnect requested; marking castConnecting=true until session ends. mainThread=%s",
                Looper.myLooper() == Looper.getMainLooper()
            )
            castStateHolder.setCastConnecting(true)
        }
        castStateHolder.disconnect()
        castStateHolder.setRemotePlaybackActive(false)
        if (resetConnecting && !wasRemote) {
            castStateHolder.setCastConnecting(false)
        }
        Timber.tag(CAST_LOG_TAG).i(
            "Disconnect call finished in %dms (wasRemote=%s resetConnecting=%s)",
            SystemClock.elapsedRealtime() - start,
            wasRemote,
            resetConnecting
        )
    }

    fun setRouteVolume(volume: Int) {
        castStateHolder.setRouteVolume(volume)
    }

    fun refreshCastRoutes() {
        castStateHolder.refreshRoutes(viewModelScope)
    }



    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        listeningStatsTracker.onCleared()
        listeningStatsTracker.onCleared()
        castStateHolder.onCleared()
        searchStateHolder.onCleared()
        aiStateHolder.onCleared()
        libraryStateHolder.onCleared()
        sleepTimerStateHolder.onCleared()
        connectivityStateHolder.onCleared()

    }

    // Sleep Timer Control Functions - delegated to SleepTimerStateHolder
    fun setSleepTimer(durationMinutes: Int) {
        sleepTimerStateHolder.setSleepTimer(durationMinutes)
    }

    fun playCounted(count: Int) {
        sleepTimerStateHolder.playCounted(count)
    }

    fun cancelCountedPlay() {
        sleepTimerStateHolder.cancelCountedPlay()
    }

    fun setEndOfTrackTimer(enable: Boolean) {
        val currentSongId = stablePlayerState.value.currentSong?.id
        sleepTimerStateHolder.setEndOfTrackTimer(enable, currentSongId)
    }

    fun cancelSleepTimer(overrideToastMessage: String? = null, suppressDefaultToast: Boolean = false) {
        sleepTimerStateHolder.cancelSleepTimer(overrideToastMessage, suppressDefaultToast)
    }

    fun dismissPlaylistAndShowUndo() {
        viewModelScope.launch {
            val songToDismiss = playbackStateHolder.stablePlayerState.value.currentSong
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

            playbackStateHolder.updateStablePlayerState {
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

    /**
     * Monitors song changes and automatically hides the dismiss undo bar
     * when the user plays a different song, as the undo option becomes irrelevant.
     */
    private fun setupUndoBarPlaybackObserver() {
        viewModelScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { newSongId ->
                    val uiState = _playerUiState.value
                    // If undo bar is showing and a different song is now playing,
                    // hide the undo bar as it's no longer relevant
                    if (uiState.showDismissUndoBar &&
                        newSongId != null &&
                        newSongId != uiState.dismissedSong?.id
                    ) {
                        hideDismissUndoBar()
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

    fun showSortingSheet() {
        _isSortingSheetVisible.value = true
    }

    fun hideSortingSheet() {
        _isSortingSheetVisible.value = false
    }

    fun onLibraryTabSelected(tabIndex: Int) {
        Trace.beginSection("PlayerViewModel.onLibraryTabSelected")
        saveLastLibraryTabIndex(tabIndex)

        val tabIdentifier = libraryTabsFlow.value.getOrNull(tabIndex) ?: return
        val tabId = tabIdentifier.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
        _currentLibraryTabId.value = tabId

        if (_loadedTabs.value.contains(tabIdentifier)) {
            Log.d("PlayerViewModel", "Tab '$tabIdentifier' already loaded. Skipping data load.")
            Trace.endSection()
            return
        }

        Log.d("PlayerViewModel", "Tab '$tabIdentifier' selected. Attempting to load data.")
        viewModelScope.launch {
            Trace.beginSection("PlayerViewModel.onLibraryTabSelected_coroutine_load")
            try {
                when (tabId) {
                    LibraryTabId.SONGS -> loadSongsIfNeeded()
                    LibraryTabId.ALBUMS -> loadAlbumsIfNeeded()
                    LibraryTabId.ARTISTS -> loadArtistsIfNeeded()
                    LibraryTabId.FOLDERS -> loadFoldersFromRepository()
                    else -> Unit
                }
                _loadedTabs.update { currentTabs -> currentTabs + tabIdentifier }
                Log.d("PlayerViewModel", "Tab '$tabIdentifier' marked as loaded. Current loaded tabs: ${_loadedTabs.value}")
            } finally {
                Trace.endSection()
            }
        }
        Trace.endSection()
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

    fun selectSongForInfo(song: Song) {
        _selectedSongForInfo.value = song
    }

    private fun loadLyricsForCurrentSong() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        // Delegate to LyricsStateHolder
        lyricsStateHolder.loadLyricsForSong(currentSong, lyricsSourcePreference.value)
    }

    fun editSongMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate?,
    ) {
        viewModelScope.launch {
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Starting editSongMetadata via Holder")
            
            val previousAlbumArt = song.albumArtUriString
            
            val result = metadataEditStateHolder.saveMetadata(
                song = song,
                newTitle = newTitle,
                newArtist = newArtist,
                newAlbum = newAlbum,
                newGenre = newGenre,
                newLyrics = newLyrics,
                newTrackNumber = newTrackNumber,
                coverArtUpdate = coverArtUpdate
            )

            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Result success=${result.success}")

            if (result.success && result.updatedSong != null) {
                val updatedSong = result.updatedSong
                val refreshedAlbumArtUri = result.updatedAlbumArtUri

                invalidateCoverArtCaches(previousAlbumArt, refreshedAlbumArtUri)

                _playerUiState.update { state ->
                    val queueIndex = state.currentPlaybackQueue.indexOfFirst { it.id == song.id }
                    if (queueIndex == -1) {
                        state
                    } else {
                        val newQueue = state.currentPlaybackQueue.toMutableList()
                        newQueue[queueIndex] = updatedSong
                        state.copy(currentPlaybackQueue = newQueue.toImmutableList())
                    }
                }

                // Update the song in the master songs flow
                _masterAllSongs.update { songs ->
                    songs.map { existing ->
                        if (existing.id == song.id) updatedSong else existing
                    }.toImmutableList()
                }
                
                // Update the LibraryStateHolder which drives the UI
                libraryStateHolder.updateSong(updatedSong)

                if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            currentSong = updatedSong,
                            lyrics = result.parsedLyrics
                        )
                    }
                    
                    // Update the player's current MediaItem to refresh notification artwork
                    // This is efficient: only replaces metadata, not the media stream
                    val controller = playbackStateHolder.mediaController
                    if (controller != null) {
                        val currentIndex = controller.currentMediaItemIndex
                        if (currentIndex >= 0 && currentIndex < controller.mediaItemCount) {
                            val currentPosition = controller.currentPosition
                            val newMediaItem = MediaItemBuilder.build(updatedSong)
                            controller.replaceMediaItem(currentIndex, newMediaItem)
                            // Restore position since replaceMediaItem may reset it
                            controller.seekTo(currentIndex, currentPosition)
                        }
                    }
                }

                if (_selectedSongForInfo.value?.id == song.id) {
                    _selectedSongForInfo.value = updatedSong
                }

                if (coverArtUpdate != null) {
                    purgeAlbumArtThemes(previousAlbumArt, updatedSong.albumArtUriString)
                    val paletteTargetUri = updatedSong.albumArtUriString
                    if (paletteTargetUri != null) {
                        themeStateHolder.getAlbumColorSchemeFlow(paletteTargetUri)
                        val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                        themeStateHolder.extractAndGenerateColorScheme(paletteTargetUri.toUri(), currentUri, isPreload = false)
                    } else {
                        val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                        themeStateHolder.extractAndGenerateColorScheme(null, currentUri, isPreload = false)
                    }
                }

                // No need for full library sync - file, MediaStore, and local DB are already updated
                // syncManager.sync() was removed to avoid unnecessary wait time
                _toastEvents.emit("Metadata updated successfully")
            } else {
                val errorMessage = result.getUserFriendlyErrorMessage()
                Log.e("PlayerViewModel", "METADATA_EDIT_VM: Failed - ${result.error}: $errorMessage")
                _toastEvents.emit(errorMessage)
            }
        }
    }

    private fun invalidateCoverArtCaches(vararg uriStrings: String?) {
        imageCacheManager.invalidateCoverArtCaches(*uriStrings)
    }

    private suspend fun purgeAlbumArtThemes(vararg uriStrings: String?) {
        val uris = uriStrings.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct()
        if (uris.isEmpty()) return

        withContext(Dispatchers.IO) {
            albumArtThemeDao.deleteThemesByUris(uris)
        }

        uris.forEach { uri ->
            // Cache invalidation delegated to ThemeStateHolder (if implemented) or relied on re-generation
            // individualAlbumColorSchemes was removed.
        }
    }

    suspend fun generateAiMetadata(song: Song, fields: List<String>): Result<SongMetadata> {
        return aiStateHolder.generateAiMetadata(song, fields)
    }

    private fun updateSongInStates(updatedSong: Song, newLyrics: Lyrics? = null) {
        // Update the queue first
        val currentQueue = _playerUiState.value.currentPlaybackQueue
        val songIndex = currentQueue.indexOfFirst { it.id == updatedSong.id }

        if (songIndex != -1) {
            val newQueue = currentQueue.toMutableList()
            newQueue[songIndex] = updatedSong
            _playerUiState.update { it.copy(currentPlaybackQueue = newQueue.toImmutableList()) }
        }

        // Then, update the stable state
        playbackStateHolder.updateStablePlayerState { state ->
            // Only update lyrics if they are explicitly passed
            val finalLyrics = newLyrics ?: state.lyrics
            state.copy(
                currentSong = updatedSong,
                lyrics = if (state.currentSong?.id == updatedSong.id) finalLyrics else state.lyrics
            )
        }
    }

    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    fun fetchLyricsForCurrentSong(forcePickResults: Boolean = false) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.fetchLyricsForSong(currentSong, forcePickResults) { resId ->
            context.getString(resId)
        }
    }

    /**
     * Manual search lyrics using query provided by user (title and artist)
     */
    fun searchLyricsManually(title: String, artist: String? = null) {
        lyricsStateHolder.searchLyricsManually(title, artist)
    }

    fun acceptLyricsSearchResultForCurrentSong(result: LyricsSearchResult) {
         val currentSong = stablePlayerState.value.currentSong ?: return
         lyricsStateHolder.acceptLyricsSearchResult(result, currentSong)
    }

    fun resetLyricsForCurrentSong() {
        val songId = stablePlayerState.value.currentSong?.id?.toLongOrNull() ?: return
        lyricsStateHolder.resetLyrics(songId)
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    fun resetAllLyrics() {
        lyricsStateHolder.resetAllLyrics()
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    /**
     * Procesa la letra importada de un archivo, la guarda y actualiza la UI.
     * @param songId El ID de la canción para la que se importa la letra.
     * @param lyricsContent El contenido de la letra como String.
     */
    fun importLyricsFromFile(songId: Long, lyricsContent: String) {
        val currentSong = stablePlayerState.value.currentSong
        lyricsStateHolder.importLyricsFromFile(songId, lyricsContent, currentSong)
        
        // Optimistic local update since holder event handles persistence
        if (currentSong?.id?.toLong() == songId) {
             val parsed = com.theveloper.pixelplay.utils.LyricsUtils.parseLyrics(lyricsContent)
             val updatedSong = currentSong.copy(lyrics = lyricsContent)
             updateSongInStates(updatedSong, parsed)
        }
    }

    /**
     * Resetea el estado de la búsqueda de letras a Idle.
     */
    fun resetLyricsSearchState() {
        lyricsStateHolder.resetSearchState()
    }

    private fun onBlockedDirectoriesChanged() {
        viewModelScope.launch {
            musicRepository.invalidateCachesDependentOnAllowedDirectories()
            resetAndLoadInitialData("Blocked directories changed")
        }
    }
}
