package com.theveloper.pixelplay.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.os.Trace
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
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
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.app.AlarmManager
import android.app.PendingIntent
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.runtime.snapshotFlow
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.Timeline
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import coil.imageLoader
import coil.memory.MemoryCache
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.common.images.WebImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.EotStateHolder
import com.theveloper.pixelplay.data.ai.AiMetadataGenerator
import com.theveloper.pixelplay.data.ai.AiPlaylistGenerator
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.database.AlbumArtThemeEntity
import com.theveloper.pixelplay.data.database.StoredColorSchemeValues
import com.theveloper.pixelplay.data.database.toComposeColor
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.media.guessImageMimeType
import com.theveloper.pixelplay.data.media.imageExtensionFromMimeType
import com.theveloper.pixelplay.data.media.isValidImageData
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.toLibraryTabIdOrNull
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.NoLyricsFoundException
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.service.player.CastPlayer
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.service.http.MediaFileHttpServerService
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.ui.theme.DarkColorScheme
import com.theveloper.pixelplay.ui.theme.GenreColors
import com.theveloper.pixelplay.ui.theme.LightColorScheme
import com.theveloper.pixelplay.ui.theme.extractSeedColor
import com.theveloper.pixelplay.ui.theme.generateColorSchemeFromSeed
import com.theveloper.pixelplay.utils.FileDeletionUtils
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.toHexString
import com.theveloper.pixelplay.utils.QueueUtils
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.ArrayDeque
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.random.Random



// Constants migrated to MediaItemBuilder
private const val EXTERNAL_MEDIA_ID_PREFIX = "external:"
// Other EXTERNAL_EXTRA_* constants removed as they are now in MediaItemBuilder

private const val CAST_LOG_TAG = "PlayerCastTransfer"

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
    val currentPosition: Long = 0L,
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
    // REMOVED: allSongs moved to separate flow to avoid copying on every state update
    val allSongs: ImmutableList<Song> = persistentListOf(),
    // val allSongs: ImmutableList<Song> = persistentListOf(),
    val songCount: Int = 0, // Lightweight count instead of full list
    val currentPlaybackQueue: ImmutableList<Song> = persistentListOf(),
    val currentQueueSourceName: String = "All Songs",
    val lavaLampColors: ImmutableList<Color> = persistentListOf(),
    val albums: ImmutableList<Album> = persistentListOf(),
    val artists: ImmutableList<Artist> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = false,
    val currentSongSortOption: SortOption = SortOption.SongTitleAZ,
    val currentAlbumSortOption: SortOption = SortOption.AlbumTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.ArtistNameAZ,
    val currentFavoriteSortOption: SortOption = SortOption.LikedSongDateLiked,
    val currentFolderSortOption: SortOption = SortOption.FolderNameAZ,
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf(),
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL,
    val searchHistory: ImmutableList<SearchHistoryItem> = persistentListOf(),
    val isSyncingLibrary: Boolean = false,
    val musicFolders: ImmutableList<MusicFolder> = persistentListOf(),
    val currentFolderPath: String? = null,
    val isFolderFilterActive: Boolean = false,

    val currentFolder: MusicFolder? = null,
    val isFoldersPlaylistView: Boolean = false,

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
    data class PickResult(val query: String, val results: List<LyricsSearchResult>) : LyricsSearchUiState
    data class Success(val lyrics: Lyrics) : LyricsSearchUiState
    data class NotFound(val message: String, val allowManualSearch: Boolean = true) : LyricsSearchUiState
    data class Error(val message: String, val query: String? = null) : LyricsSearchUiState
}

// ActiveSession class moved to ListeningStatsTracker.kt

@UnstableApi
@SuppressLint("LogNotTimber")
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    val syncManager: SyncManager, // Inyectar SyncManager

    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val aiMetadataGenerator: AiMetadataGenerator,
    private val artistImageRepository: ArtistImageRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    private val appShortcutManager: com.theveloper.pixelplay.utils.AppShortcutManager,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val colorSchemeProcessor: ColorSchemeProcessor,
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
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val externalMediaStateHolder: ExternalMediaStateHolder
) : ViewModel() {

    // AlarmManager is now managed by SleepTimerStateHolder
    
    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()
    
    val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState
    
    private val _masterAllSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    
    /**
     * Paginated songs for efficient display in LibraryScreen.
     * Uses Paging 3 for memory-efficient loading of large libraries.
     */
    val paginatedSongs: Flow<PagingData<Song>> = musicRepository.getPaginatedSongs()
        .cachedIn(viewModelScope)

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

    init {
        // Initialize helper classes with our coroutine scope
        listeningStatsTracker.initialize(viewModelScope)
        dailyMixStateHolder.initialize(viewModelScope)
        lyricsStateHolder.initialize(viewModelScope, lyricsLoadCallback)
        playbackStateHolder.initialize(viewModelScope)
        
        viewModelScope.launch {
            playbackStateHolder.stablePlayerState.collect { state ->
                _playerUiState.update { it.copy(currentPosition = state.currentPosition) }
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

    private val disableCastAutoplay: StateFlow<Boolean> = userPreferencesRepository.disableCastAutoplayFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
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

    fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        lyricsStateHolder.setSyncOffset(songId, offsetMs)
    }

    private fun observeCurrentSongLyricsOffset() {
        viewModelScope.launch {
            stablePlayerState.collect { state ->
                state.currentSong?.id?.let { songId ->
                    lyricsStateHolder.updateSyncOffsetForSong(songId)
                }
            }
        }
    }

    private val _isInitialThemePreloadComplete = MutableStateFlow(false)
    val isInitialThemePreloadComplete: StateFlow<Boolean> = _isInitialThemePreloadComplete.asStateFlow()

    // Manual shuffle state - now managed by QueueStateHolder
    private val originalQueueOrder: List<Song> get() = queueStateHolder.originalQueueOrder
    private val originalQueueName: String get() = queueStateHolder.originalQueueName

    // Sleep Timer StateFlows - delegated to SleepTimerStateHolder
    val sleepTimerEndTimeMillis: StateFlow<Long?> = sleepTimerStateHolder.sleepTimerEndTimeMillis
    val isEndOfTrackTimerActive: StateFlow<Boolean> = sleepTimerStateHolder.isEndOfTrackTimerActive
    val activeTimerValueDisplay: StateFlow<String?> = sleepTimerStateHolder.activeTimerValueDisplay
    val playCount: StateFlow<Float> = sleepTimerStateHolder.playCount

    // Lyrics search UI state - managed by LyricsStateHolder
    val lyricsSearchUiState: StateFlow<LyricsSearchUiState> = lyricsStateHolder.searchUiState

    // lyricsLoadingJob moved to LyricsStateHolder
    private var countedMediaListener: Player.Listener? = null
    private var countedOriginalSongId: String? = null

    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _artistNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val artistNavigationRequests = _artistNavigationRequests.asSharedFlow()
    private var artistNavigationJob: Job? = null

    private val _castRoutes = MutableStateFlow<List<MediaRouter.RouteInfo>>(emptyList())
    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = _castRoutes.asStateFlow()
    private val _selectedRoute = MutableStateFlow<MediaRouter.RouteInfo?>(null)
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = _selectedRoute.asStateFlow()
    private val _routeVolume = MutableStateFlow(0)
    val routeVolume: StateFlow<Int> = _routeVolume.asStateFlow()
    private val _isRefreshingRoutes = MutableStateFlow(false)
    val isRefreshingRoutes: StateFlow<Boolean> = _isRefreshingRoutes.asStateFlow()

    // Connectivity state delegated to ConnectivityStateHolder
    val isWifiEnabled: StateFlow<Boolean> = connectivityStateHolder.isWifiEnabled
    val isWifiRadioOn: StateFlow<Boolean> = connectivityStateHolder.isWifiRadioOn
    val wifiName: StateFlow<String?> = connectivityStateHolder.wifiName
    val isBluetoothEnabled: StateFlow<Boolean> = connectivityStateHolder.isBluetoothEnabled
    val bluetoothName: StateFlow<String?> = connectivityStateHolder.bluetoothName
    val bluetoothAudioDevices: StateFlow<List<String>> = connectivityStateHolder.bluetoothAudioDevices

    private val mediaRouter: MediaRouter
    private val mediaRouterCallback: MediaRouter.Callback
    
    // Connectivity is now managed by ConnectivityStateHolder
    
    // Cast state is now managed by CastStateHolder
    private val sessionManager: SessionManager get() = castStateHolder.sessionManager
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private val castSession: StateFlow<CastSession?> get() = castStateHolder.castSession
    private val castPlayer: CastPlayer? get() = castStateHolder.castPlayer
    val isRemotePlaybackActive: StateFlow<Boolean> = castStateHolder.isRemotePlaybackActive
    val isCastConnecting: StateFlow<Boolean> = castStateHolder.isCastConnecting
    private val castControlCategory get() = CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
    private val pendingCastRouteId: String? get() = castStateHolder.pendingCastRouteId
    val remotePosition: StateFlow<Long> = castStateHolder.remotePosition
    // Keep lastRemote* as local variables for safety - many internal assignments
    private var lastRemoteMediaStatus: MediaStatus? = null
    private var lastRemoteQueue: List<Song> = emptyList()
    private var lastRemoteSongId: String? = null
    private var lastRemoteStreamPosition: Long = 0L
    private var lastRemoteRepeatMode: Int = Player.REPEAT_MODE_OFF
    private var lastRemoteItemId: Int? = null
    private var pendingRemoteSongId: String? = null
    private var pendingRemoteSongMarkedAt: Long = 0L
    private val _trackVolume = MutableStateFlow(1.0f)
    val trackVolume: StateFlow<Float> = _trackVolume.asStateFlow()
    private val isRemotelySeeking = MutableStateFlow(false)
    private var remoteMediaClientCallback: RemoteMediaClient.Callback? = null
    private var remoteProgressListener: RemoteMediaClient.ProgressListener? = null

    private data class QueueTransferData(
        val finalQueue: List<Song>,
        val targetSongId: String?,
        val isShuffleEnabled: Boolean
    )

    private data class RebuildArtifacts(
        val startIndex: Int,
        val mediaItems: List<MediaItem>,
        val targetSong: Song?
    )

    private data class TransferSnapshot(
        val lastKnownStatus: MediaStatus?,
        val lastRemoteQueue: List<Song>,
        val lastRemoteSongId: String?,
        val lastRemoteStreamPosition: Long,
        val lastRemoteRepeatMode: Int,
        val wasPlaying: Boolean,
        val lastPosition: Long,
        val isShuffleEnabled: Boolean
    )

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

    private fun markPendingRemoteSong(song: Song?) {
        if (song == null) return
        pendingRemoteSongId = song.id
        pendingRemoteSongMarkedAt = SystemClock.elapsedRealtime()
        lastRemoteSongId = song.id
        lastRemoteItemId = null
        Timber.tag(CAST_LOG_TAG).d("Marked pending remote song: %s", song.id)
        playbackStateHolder.updateStablePlayerState { state -> state.copy(currentSong = song) }
        _isSheetVisible.value = true
        song.albumArtUriString?.toUri()?.let { uri ->
            viewModelScope.launch {
                extractAndGenerateColorScheme(uri)
            }
        }
        _playerUiState.update { state ->
            val queue = if (state.currentPlaybackQueue.isNotEmpty()) {
                state.currentPlaybackQueue
            } else {
                lastRemoteQueue
            }
            val updatedQueue = if (queue.any { it.id == song.id } || queue.isEmpty()) {
                queue
            } else {
                queue + song
            }
            state.copy(currentPlaybackQueue = updatedQueue.toImmutableList(), currentPosition = 0L)
        }
        castStateHolder.setRemotePosition(0L)
    }

    private fun resolvePendingRemoteSong(
        reportedSong: Song?,
        reportedSongId: String?,
        songMap: Map<String, Song>
    ): Song? {
        val pendingId = pendingRemoteSongId ?: return reportedSong
        val now = SystemClock.elapsedRealtime()
        val isFresh = now - pendingRemoteSongMarkedAt < 4000
        if (!isFresh) {
            pendingRemoteSongId = null
            return reportedSong
        }
        if (reportedSongId == pendingId) {
            pendingRemoteSongId = null
            return reportedSong ?: songMap[pendingId] ?: lastRemoteQueue.firstOrNull { it.id == pendingId }
        }
        return songMap[pendingId]
            ?: reportedSong
            ?: lastRemoteQueue.firstOrNull { it.id == pendingId }
            ?: playbackStateHolder.stablePlayerState.value.currentSong
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

            genreMap.toList().mapIndexedNotNull { index, (genreName, songs) ->
                if (songs.isNotEmpty()) {
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
        }
        .flowOn(Dispatchers.Default) // Move heavy computation off main thread
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

    // LRU-limited cache for album color schemes (max 30 albums to prevent unbounded memory growth)
    private val individualAlbumColorSchemes = object : LinkedHashMap<String, MutableStateFlow<ColorSchemePair?>>(
        32, 0.75f, true // accessOrder = true for LRU behavior
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableStateFlow<ColorSchemePair?>>?): Boolean {
            return size > 30
        }
    }

    private val colorSchemeRequestChannel = Channel<String>(Channel.UNLIMITED)
    private val urisBeingProcessed = mutableSetOf<String>()

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
    // val currentFavoriteSortOptionStateFlow: StateFlow<SortOption> = _currentFavoriteSortOptionStateFlow.asStateFlow() // Removed, use libraryStateHolder

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

    private var progressJob: Job? = null
    private var remoteProgressObserverJob: Job? = null
    private var transitionSchedulerJob: Job? = null
    // ListeningStatsTracker is now injected via constructor
    private var lastKnownRemoteIsPlaying = false
    
    // Lifecycle-aware progress updates for battery optimization
    // Uses sheet visibility and state as proxy for UI visibility
    private fun isPlayerUiActive(): Boolean {
        return _isSheetVisible.value && _sheetState.value == PlayerSheetState.EXPANDED
    }

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
    // Connectivity permission checks delegated to ConnectivityStateHolder
    fun hasBluetoothPermission(): Boolean = connectivityStateHolder.hasBluetoothPermission()
    fun hasLocationPermission(): Boolean = connectivityStateHolder.hasLocationPermission()

    private fun MediaRouter.RouteInfo.isCastRoute(): Boolean {
        return supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK) ||
            supportsControlCategory(castControlCategory)
    }

    private fun buildCastRouteSelector(): MediaRouteSelector {
        return MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .addControlCategory(castControlCategory)
            .build()
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

        // Observe current song lyrics offset
        observeCurrentSongLyricsOffset()

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

        mediaRouter = MediaRouter.getInstance(context)
        val mediaRouteSelector = buildCastRouteSelector()

        mediaRouterCallback = object : MediaRouter.Callback() {
            private fun updateRoutes(router: MediaRouter) {
                val routes = router.routes.filter { it.isCastRoute() }.distinctBy { it.id }
                _castRoutes.value = routes
                _selectedRoute.value = router.selectedRoute
                _routeVolume.value = router.selectedRoute.volume
            }

            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateRoutes(router)
                if (route.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK) && !route.isDefault) {
                    viewModelScope.launch {
                        ensureHttpServerRunning()
                    }
                } else if (route.isDefault) {
                    context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                }
            }
            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) { updateRoutes(router) }
            override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                if (route.id == _selectedRoute.value?.id) {
                    _routeVolume.value = route.volume
                }
            }
        }
        // Initial route setup
        mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        _castRoutes.value = mediaRouter.routes.filter { it.isCastRoute() }.distinctBy { it.id }
        _selectedRoute.value = mediaRouter.selectedRoute

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
        remoteProgressListener = RemoteMediaClient.ProgressListener { progress, _ ->
            if (!isRemotelySeeking.value) {
                val pendingId = pendingRemoteSongId
                if (pendingId != null && SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000) {
                    val status = castSession.value?.remoteMediaClient?.mediaStatus
                    val activeId = status
                        ?.getQueueItemById(status.getCurrentItemId())
                        ?.customData
                        ?.optString("songId")
                    if (activeId == null || activeId != pendingId) {
                        Timber.tag(CAST_LOG_TAG)
                            .d(
                                "Ignoring remote progress %d while pending target %s (active %s)",
                                progress,
                                pendingId,
                                activeId
                            )
                        return@ProgressListener
                    }
                }
                castStateHolder.setRemotePosition(progress)
                lastRemoteStreamPosition = progress
                listeningStatsTracker.onProgress(progress, lastKnownRemoteIsPlaying)
                Timber.tag(CAST_LOG_TAG).d("Remote progress update: %d", progress)
            }
        }

        remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val remoteMediaClient = castSession.value?.remoteMediaClient ?: return
                val mediaStatus = remoteMediaClient.mediaStatus ?: return
                Timber.tag(CAST_LOG_TAG)
                    .d(
                        "Remote status: state=%d position=%d duration=%d repeat=%d queueCount=%d currentItemId=%d",
                        mediaStatus.playerState,
                        mediaStatus.streamPosition,
                        remoteMediaClient.streamDuration,
                        mediaStatus.queueRepeatMode,
                        mediaStatus.queueItemCount,
                        mediaStatus.currentItemId
                    )
                lastRemoteMediaStatus = mediaStatus
                val songMap = _masterAllSongs.value.associateBy { it.id }
                val newQueue = mediaStatus.queueItems.mapNotNull { item ->
                    item.customData?.optString("songId")?.let { songId ->
                        songMap[songId]
                    }
                }.toImmutableList()
                val currentItemId = mediaStatus.getCurrentItemId()
                val currentRemoteItem = mediaStatus.getQueueItemById(currentItemId)
                val currentSongId = currentRemoteItem?.customData?.optString("songId")
                val streamPosition = mediaStatus.streamPosition
                val pendingId = pendingRemoteSongId
                val pendingIsFresh = pendingId != null &&
                    SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000
                if (pendingIsFresh && currentSongId != null && currentSongId != pendingId) {
                    Timber.tag(CAST_LOG_TAG)
                        .d("Ignoring outdated status with item %s while pending target %s", currentSongId, pendingId)
                    remoteMediaClient.requestStatus()
                    return
                }
                val itemChanged = lastRemoteItemId != currentItemId
                if (itemChanged) {
                    lastRemoteItemId = currentItemId
                    if (pendingRemoteSongId != null && pendingRemoteSongId != currentSongId) {
                        Timber.tag(CAST_LOG_TAG)
                            .d(
                                "Clearing stale pending id %s after remote item %s became active",
                                pendingRemoteSongId,
                                currentSongId
                            )
                        pendingRemoteSongId = null
                    }
                    isRemotelySeeking.value = false
                    castStateHolder.setRemotePosition(streamPosition)
                    _playerUiState.update { it.copy(currentPosition = streamPosition) }
                }
                val reportedSong = currentSongId?.let { songMap[it] }
                if (newQueue.isNotEmpty()) {
                    val isShrunkSubset =
                        newQueue.size < lastRemoteQueue.size && newQueue.all { song ->
                            lastRemoteQueue.any { it.id == song.id }
                        }
                    if (!isShrunkSubset || lastRemoteQueue.isEmpty()) {
                        lastRemoteQueue = newQueue
                        Timber.tag(CAST_LOG_TAG).d("Cached remote queue items: %d", newQueue.size)
                    } else {
                        Timber.tag(CAST_LOG_TAG)
                            .d(
                                "Skipping remote queue cache shrink: cached=%d new=%d",
                                lastRemoteQueue.size,
                                newQueue.size
                            )
                    }
                }
                val effectiveSong = resolvePendingRemoteSong(reportedSong, currentSongId, songMap)
                val effectiveSongId = effectiveSong?.id ?: currentSongId ?: lastRemoteSongId
                if (effectiveSongId != null) {
                    lastRemoteSongId = effectiveSongId
                    Timber.tag(CAST_LOG_TAG).d("Cached current remote song id: %s", effectiveSongId)
                }
                val currentSongFallback = effectiveSong
                    ?: run {
                        val pendingId = pendingRemoteSongId
                        val stableSong = playbackStateHolder.stablePlayerState.value.currentSong
                        if (
                            pendingId != null &&
                            pendingId == stableSong?.id &&
                            SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000
                        ) {
                            stableSong
                        } else {
                            playbackStateHolder.stablePlayerState.value.currentSong
                        }
                    }
                    ?: lastRemoteQueue.firstOrNull { it.id == lastRemoteSongId }
                if (currentSongFallback?.id != playbackStateHolder.stablePlayerState.value.currentSong?.id) {
                    viewModelScope.launch {
                        currentSongFallback?.albumArtUriString?.toUri()?.let { uri ->
                            extractAndGenerateColorScheme(uri)
                        }
                    }
                }
                val previousQueue = _playerUiState.value.currentPlaybackQueue
                val isSubsetOfPrevious =
                    previousQueue.isNotEmpty() && newQueue.isNotEmpty() && newQueue.all { song ->
                        previousQueue.any { it.id == song.id }
                    }
                val queueForUi = when {
                    newQueue.isEmpty() -> previousQueue
                    isSubsetOfPrevious && newQueue.size < previousQueue.size -> previousQueue
                    else -> newQueue
                }
                if (queueForUi.isNotEmpty() || previousQueue.isNotEmpty()) {
                    _playerUiState.update {
                        it.copy(currentPlaybackQueue = queueForUi)
                    }
                }
                if (!_isSheetVisible.value && (queueForUi.isNotEmpty() || previousQueue.isNotEmpty())) {
                    _isSheetVisible.value = true
                }
                val isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING
                lastKnownRemoteIsPlaying = isPlaying
                lastRemoteStreamPosition = streamPosition
                lastRemoteRepeatMode = mediaStatus.queueRepeatMode
                if (!isRemotelySeeking.value) {
                    castStateHolder.setRemotePosition(streamPosition)
                    _playerUiState.update { it.copy(currentPosition = streamPosition) }
                }
                Timber.tag(CAST_LOG_TAG)
                    .d(
                        "Status update applied: song=%s position=%d repeat=%d playing=%s",
                        currentSongId,
                        streamPosition,
                        mediaStatus.queueRepeatMode,
                        isPlaying
                    )
                val streamDuration = listOf(
                    remoteMediaClient.streamDuration,
                    effectiveSong?.duration ?: 0L,
                    0L
                ).maxOrNull() ?: 0L
                val effectiveSongForStats = effectiveSong ?: playbackStateHolder.stablePlayerState.value.currentSong
                listeningStatsTracker.ensureSession(
                    song = effectiveSongForStats,
                    positionMs = streamPosition,
                    durationMs = streamDuration,
                    isPlaying = isPlaying
                )
                if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_IDLE && mediaStatus.queueItemCount == 0) {
                    listeningStatsTracker.onPlaybackStopped()
                }
                playbackStateHolder.updateStablePlayerState {
                    var nextSong = currentSongFallback
                    // Prevent clearing the song if we are in the middle of a connection attempt
                    if (isCastConnecting.value && nextSong == null) {
                        nextSong = it.currentSong
                    }
                    it.copy(
                        isPlaying = isPlaying,
                        isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE,
                        repeatMode = mediaStatus.queueRepeatMode,
                        currentSong = nextSong,
                        totalDuration = streamDuration
                    )
                }
                if (castSession.value != null) {
                    _isSheetVisible.value = true
                }
            }
        }

        castSessionManagerListener = object : SessionManagerListener<CastSession> {
            private fun transferPlayback(session: CastSession) {
                viewModelScope.launch {
                    castStateHolder.setPendingCastRouteId(null)
                    castStateHolder.setCastConnecting(true)
                    if (!ensureHttpServerRunning()) {
                        castStateHolder.setCastConnecting(false)
                        disconnect()
                        return@launch
                    }

                    val serverAddress = MediaFileHttpServerService.serverAddress
                    val localPlayer = mediaController
                    val currentQueue = _playerUiState.value.currentPlaybackQueue
                    if (serverAddress == null || localPlayer == null || currentQueue.isEmpty()) {
                        castStateHolder.setCastConnecting(false)
                        return@launch
                    }

                    val wasPlaying = localPlayer.isPlaying
                    val currentSongIndex = localPlayer.currentMediaItemIndex
                    val currentPosition = localPlayer.currentPosition

                    val shouldAutoPlayOnCast = wasPlaying && !disableCastAutoplay.value

                    val castRepeatMode = if (localPlayer.shuffleModeEnabled) {
                        MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
                    } else {
                        when (localPlayer.repeatMode) {
                            Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                            Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                            else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                        }
                    }

                    lastRemoteMediaStatus = null
                    lastRemoteQueue = currentQueue
                    lastRemoteSongId = currentQueue.getOrNull(currentSongIndex)?.id
                    lastRemoteStreamPosition = currentPosition
                    lastRemoteRepeatMode = castRepeatMode

                    _isSheetVisible.value = true

                    localPlayer.pause()
                    stopProgressUpdates()

                    castStateHolder.setCastPlayer(CastPlayer(session))
                    castStateHolder.setCastSession(session)
                    castStateHolder.setRemotePlaybackActive(false)

                    castPlayer?.loadQueue(
                        songs = currentQueue,
                        startIndex = currentSongIndex,
                        startPosition = currentPosition,
                        repeatMode = castRepeatMode,
                        serverAddress = serverAddress,
                        autoPlay = shouldAutoPlayOnCast,
                        onComplete = { success ->
                            if (!success) {
                                sendToast("Failed to load media on cast device.")
                                disconnect()
                                castStateHolder.setCastConnecting(false)
                            }
                            castStateHolder.setRemotePlaybackActive(success)
                            castStateHolder.setCastConnecting(false)
                        }
                    )

                    session.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
                    session.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)

                    remoteProgressObserverJob?.cancel()
                    remoteProgressObserverJob = viewModelScope.launch {
                        remotePosition.collect { position ->
                            _playerUiState.update { it.copy(currentPosition = position) }
                        }
                    }
                }
            }

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                transferPlayback(session)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                transferPlayback(session)
            }

            private suspend fun stopServerAndTransferBack() {
                val startMs = SystemClock.elapsedRealtime()
                val targetRouteId = pendingCastRouteId
                val session = castSession.value ?: return
                val remoteMediaClient = session.remoteMediaClient
                Timber.tag(CAST_LOG_TAG).i(
                    "Stop server and transfer back initiated. targetRouteId=%s mainThread=%s",
                    targetRouteId,
                    Looper.myLooper() == Looper.getMainLooper()
                )
                val liveStatus = remoteMediaClient?.mediaStatus
                val lastKnownStatus = liveStatus ?: lastRemoteMediaStatus
                val lastPosition = (
                    liveStatus?.streamPosition
                        ?: lastKnownStatus?.streamPosition
                        ?: lastRemoteStreamPosition
                    )
                    .takeIf { it > 0 } ?: remotePosition.value
                val wasPlaying = (liveStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING)
                    || (lastKnownStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING)
                    || lastKnownRemoteIsPlaying
                val shouldResumePlaying = wasPlaying && !disableCastAutoplay.value
                val lastItemId = liveStatus?.currentItemId ?: lastKnownStatus?.currentItemId
                val lastRepeatMode = liveStatus?.queueRepeatMode
                    ?: lastKnownStatus?.queueRepeatMode
                    ?: lastRemoteRepeatMode
                val isShuffleEnabled = lastRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE

                val transferSnapshot = TransferSnapshot(
                    lastKnownStatus = lastKnownStatus,
                    lastRemoteQueue = lastRemoteQueue,
                    lastRemoteSongId = lastRemoteSongId,
                    lastRemoteStreamPosition = lastRemoteStreamPosition,
                    lastRemoteRepeatMode = lastRemoteRepeatMode,
                    wasPlaying = wasPlaying,
                    lastPosition = lastPosition,
                    isShuffleEnabled = isShuffleEnabled
                )
                Timber.tag(CAST_LOG_TAG)
                    .i(
                        "Transfer back start: lastStatus=%s lastItemId=%s lastSongId=%s position=%d playing=%s repeat=%d shuffle=%s",
                        lastKnownStatus != null,
                        lastItemId,
                        lastRemoteSongId,
                        lastPosition,
                        wasPlaying,
                        lastRepeatMode,
                        isShuffleEnabled
                    )
                remoteProgressObserverJob?.cancel()
                remoteMediaClient?.removeProgressListener(remoteProgressListener!!)
                remoteMediaClient?.unregisterCallback(remoteMediaClientCallback!!)
                lastRemoteItemId = null
                Timber.tag(CAST_LOG_TAG).i(
                    "Transfer back: removed remote callbacks at +%dms",
                    SystemClock.elapsedRealtime() - startMs
                )
                castStateHolder.setCastPlayer(null)
                castStateHolder.setCastSession(null)
                castStateHolder.setRemotePlaybackActive(false)
                if (targetRouteId == null) {
                    context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                    disconnect(resetConnecting = false) // Don't reset connecting flag yet
                } else {
                    castStateHolder.setCastConnecting(true)
                }
                Timber.tag(CAST_LOG_TAG).i(
                    "Transfer back: local session state cleared at +%dms (targetRouteId=%s)",
                    SystemClock.elapsedRealtime() - startMs,
                    targetRouteId
                )

                val localPlayer = mediaController ?: run {
                    if (targetRouteId == null) {
                        castStateHolder.setCastConnecting(false)
                    } else {
                        castStateHolder.setPendingCastRouteId(null)
                        castStateHolder.setCastConnecting(false)
                    }
                    return
                }
                Timber.tag(CAST_LOG_TAG).i(
                    "Transfer back: mediaController available=%s at +%dms",
                    localPlayer != null,
                    SystemClock.elapsedRealtime() - startMs
                )

                val queueData = withContext(Dispatchers.Default) {
                    val fallbackQueue = if (transferSnapshot.lastKnownStatus?.queueItems?.isNotEmpty() == true) {
                        transferSnapshot.lastKnownStatus.queueItems.mapNotNull { item ->
                            item.customData?.optString("songId")?.let { songId ->
                                _masterAllSongs.value.firstOrNull { it.id == songId }
                            }
                        }.toImmutableList()
                    } else {
                        transferSnapshot.lastRemoteQueue
                    }
                    val chosenQueue = when {
                        fallbackQueue.isEmpty() -> transferSnapshot.lastRemoteQueue
                        fallbackQueue.size < transferSnapshot.lastRemoteQueue.size && fallbackQueue.all { song ->
                            transferSnapshot.lastRemoteQueue.any { it.id == song.id }
                        } -> transferSnapshot.lastRemoteQueue
                        else -> fallbackQueue
                    }
                    val songMap = _masterAllSongs.value.associateBy { it.id }
                    val finalQueue = chosenQueue.mapNotNull { song ->
                        songMap[song.id]
                    }
                    val targetSongId = transferSnapshot.lastKnownStatus?.getQueueItemById(lastItemId ?: 0)?.customData?.optString("songId")
                        ?: transferSnapshot.lastRemoteSongId
                    QueueTransferData(
                        finalQueue = finalQueue,
                        targetSongId = targetSongId,
                        isShuffleEnabled = transferSnapshot.isShuffleEnabled
                    )
                }
                Timber.tag(CAST_LOG_TAG).i(
                    "Transfer back: queueData ready (size=%d target=%s) at +%dms",
                    queueData.finalQueue.size,
                    queueData.targetSongId,
                    SystemClock.elapsedRealtime() - startMs
                )

                Timber.tag(CAST_LOG_TAG)
                    .i(
                        "Finalized transfer data: queueSize=%d targetSongId=%s lastRemoteQueueSize=%d",
                        queueData.finalQueue.size,
                        queueData.targetSongId,
                        lastRemoteQueue.size
                    )

                if (queueData.finalQueue.isNotEmpty() && queueData.targetSongId != null) {
                    val desiredRepeatMode = when (lastRepeatMode) {
                        MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                        MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                    val desiredIds = queueData.finalQueue.map { it.id }
                    val existingIds = (0 until localPlayer.mediaItemCount).map { index ->
                        localPlayer.getMediaItemAt(index).mediaId
                    }
                    val targetIndexInExisting = existingIds.indexOf(queueData.targetSongId)
                    val queueMatchesExisting = existingIds.size == desiredIds.size &&
                        existingIds.zip(desiredIds).all { (existing, desired) -> existing == desired }

                    val targetSong = queueData.finalQueue.firstOrNull { it.id == queueData.targetSongId }

                    if (queueMatchesExisting && targetIndexInExisting >= 0) {
                        Timber.tag(CAST_LOG_TAG)
                            .i(
                                "Reusing existing local queue; seeking to index=%d position=%d",
                                targetIndexInExisting,
                                transferSnapshot.lastPosition
                            )

                        localPlayer.shuffleModeEnabled = queueData.isShuffleEnabled
                        localPlayer.repeatMode = desiredRepeatMode
                        localPlayer.seekTo(targetIndexInExisting, transferSnapshot.lastPosition)
                        if (shouldResumePlaying) {
                            localPlayer.play()
                        } else {
                            localPlayer.pause()
                        }
                        Timber.tag(CAST_LOG_TAG).i(
                            "Transfer back: reused queue; seek applied at +%dms",
                            SystemClock.elapsedRealtime() - startMs
                        )
                    } else {
                        val rebuildResult = withContext(Dispatchers.Default) {
                            val startIndex = queueData.finalQueue.indexOfFirst { it.id == queueData.targetSongId }.coerceAtLeast(0)
                            Timber.tag(CAST_LOG_TAG)
                                .i(
                                    "Restoring local playback: startIndex=%d position=%d songId=%s",
                                    startIndex,
                                    lastPosition,
                                    queueData.targetSongId
                                )
                            val mediaItems = queueData.finalQueue.map { song ->
                                MediaItem.Builder()
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
                            }

                            RebuildArtifacts(
                                startIndex = startIndex,
                                mediaItems = mediaItems,
                                targetSong = queueData.finalQueue.getOrNull(startIndex)
                            )
                        }
                        Timber.tag(CAST_LOG_TAG).i(
                            "Transfer back: rebuilt media items (count=%d) at +%dms",
                            queueData.finalQueue.size,
                            SystemClock.elapsedRealtime() - startMs
                        )

                        localPlayer.shuffleModeEnabled = queueData.isShuffleEnabled
                        localPlayer.repeatMode = desiredRepeatMode
                        localPlayer.setMediaItems(
                            rebuildResult.mediaItems,
                            rebuildResult.startIndex,
                            transferSnapshot.lastPosition
                        )
                        localPlayer.prepare()
                        if (shouldResumePlaying) {
                            localPlayer.play()
                        } else {
                            localPlayer.pause()
                        }

                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = rebuildResult.targetSong,
                                isPlaying = shouldResumePlaying,
                                totalDuration = rebuildResult.targetSong?.duration ?: it.totalDuration,
                                isShuffleEnabled = queueData.isShuffleEnabled,
                                repeatMode = localPlayer.repeatMode
                            )
                        }
                        Timber.tag(CAST_LOG_TAG).i(
                            "Transfer back: setMediaItems completed at +%dms",
                            SystemClock.elapsedRealtime() - startMs
                        )
                    }

                    _playerUiState.update {
                        it.copy(
                            currentPlaybackQueue = queueData.finalQueue.toImmutableList(),
                            currentPosition = transferSnapshot.lastPosition
                        )
                    }
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            currentSong = targetSong ?: it.currentSong,
                            isPlaying = shouldResumePlaying,
                            totalDuration = targetSong?.duration ?: it.totalDuration,
                            isShuffleEnabled = queueData.isShuffleEnabled,
                            repeatMode = localPlayer.repeatMode
                        )
                    }
                    Timber.tag(CAST_LOG_TAG).i(
                        "Transfer back: state updates applied at +%dms (shouldResumePlaying=%s)",
                        SystemClock.elapsedRealtime() - startMs,
                        shouldResumePlaying
                    )
                    if (shouldResumePlaying) {
                        startProgressUpdates()
                        Timber.tag(CAST_LOG_TAG).i("Local playback resumed with play at position=%d", transferSnapshot.lastPosition)
                    } else {
                        _playerUiState.update { it.copy(currentPosition = transferSnapshot.lastPosition) }
                        Timber.tag(CAST_LOG_TAG).i("Local playback prepared without play at position=%d", transferSnapshot.lastPosition)
                    }
                }
                lastRemoteMediaStatus = null
                lastRemoteQueue = emptyList()
                lastRemoteSongId = null
                lastRemoteStreamPosition = 0L
                if (targetRouteId == null) {
                    Timber.tag(CAST_LOG_TAG).i("Transfer back complete. Clearing castConnecting=false")
                    castStateHolder.setCastConnecting(false) // NOW we reset the flag
                    flushPendingRepeatMode()
                } else {
                    val pendingRoute = mediaRouter.routes.firstOrNull { it.id == targetRouteId }
                    if (pendingRoute != null) {
                        Timber.tag(CAST_LOG_TAG).i("Selecting pending route %s after transfer back.", targetRouteId)
                        mediaRouter.selectRoute(pendingRoute)
                    } else {
                        Timber.tag(CAST_LOG_TAG).w("Pending route %s not found after transfer back. Resetting state.", targetRouteId)
                        castStateHolder.setPendingCastRouteId(null)
                        castStateHolder.setCastConnecting(false)
                        flushPendingRepeatMode()
                    }
                }
                Timber.tag(CAST_LOG_TAG).i(
                    "Transfer back finished at +%dms",
                    SystemClock.elapsedRealtime() - startMs
                )
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                viewModelScope.launch { stopServerAndTransferBack() }
            }

            override fun onSessionSuspended(session: CastSession, reason: Int) {
                viewModelScope.launch { stopServerAndTransferBack() }
            }

            // Other listener methods can be overridden if needed
            override fun onSessionStarting(session: CastSession) {
                castStateHolder.setCastConnecting(true)
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                castStateHolder.setPendingCastRouteId(null)
                castStateHolder.setCastConnecting(false)
            }
            override fun onSessionEnding(session: CastSession) {
                Timber.tag(CAST_LOG_TAG).i("Cast session ending; keeping connecting flag=%s", isCastConnecting.value)
            }
            override fun onSessionResuming(session: CastSession, sessionId: String) {
                castStateHolder.setCastConnecting(true)
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                castStateHolder.setPendingCastRouteId(null)
                castStateHolder.setCastConnecting(false)
            }
        }
        sessionManager.addSessionManagerListener(castSessionManagerListener as SessionManagerListener<CastSession>, CastSession::class.java)
        castStateHolder.setCastSession(sessionManager.currentCastSession)
        castStateHolder.setRemotePlaybackActive(castSession.value != null)
        castSession.value?.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
        castSession.value?.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)

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

        Trace.endSection() // End PlayerViewModel.init
    }

    fun onMainActivityStart() {
        Trace.beginSection("PlayerViewModel.onMainActivityStart")
        preloadThemesAndInitialData()
        checkAndUpdateDailyMixIfNeeded()
        Trace.endSection()
    }

    fun loadDummyDataForBenchmark() {
        Log.i("PlayerViewModel", "Loading dummy data for benchmark")
        val dummySong = Song(
            id = "dummy_1",
            title = "Benchmark Song",
            artist = "Benchmark Artist",
            artistId = 1L,
            album = "Benchmark Album",
            albumId = 1L,
            path = "",
            contentUriString = "",
            albumArtUriString = null,
            duration = 180000L,
            genre = "Benchmark",
            lyrics = null,
            isFavorite = false,
            trackNumber = 1,
            year = 2024,
            dateAdded = System.currentTimeMillis(),
            mimeType = "audio/mpeg",
            bitrate = 320,
            sampleRate = 44100
        )

        _playerUiState.update {
            it.copy(
                isLoadingInitialSongs = false,
                isLoadingLibraryCategories = false
            )
        }

        val dummyList = persistentListOf(dummySong)
        _masterAllSongs.value = dummyList
        _playerUiState.update {
            it.copy(
                currentPlaybackQueue = dummyList,
                currentQueueSourceName = "Benchmark",
                currentPosition = 30000L
            )
        }

        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = dummySong,
                isPlaying = true,
                totalDuration = 180000L
            )
        }

        _isSheetVisible.value = true
        viewModelScope.launch {
            delay(500)
            _sheetState.value = PlayerSheetState.EXPANDED
        }
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
        val functionStartTime = System.currentTimeMillis()
        Log.d("PlayerViewModelPerformance", "preloadThemesAndInitialData START")

        viewModelScope.launch { // Main.immediate by default
            val overallInitStartTime = System.currentTimeMillis()
            _isInitialThemePreloadComplete.value = false // Mantener esto
            Log.d("PlayerViewModelPerformance", "preloadThemesAndInitialData: _isInitialThemePreloadComplete set to false. Time from start: ${System.currentTimeMillis() - overallInitStartTime} ms")
            if (isSyncingStateFlow.value && !_isInitialDataLoaded.value) {
                Log.i("PlayerViewModel", "preloadThemesAndInitialData: Sync is active and initial data not yet loaded, deferring initial load to sync completion handler.")
            } else if (!_isInitialDataLoaded.value && _masterAllSongs.value.isEmpty()) { // Check _isInitialDataLoaded
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
        Log.d("PlayerViewModel", "Delegating initial load to LibraryStateHolder...")
        // Trigger loading in LibraryStateHolder. 
        // These methods launch their own coroutines on the scope passed in initialize().
        libraryStateHolder.loadSongsFromRepository()
        libraryStateHolder.loadAlbumsFromRepository()
        libraryStateHolder.loadArtistsFromRepository()
        libraryStateHolder.loadFoldersFromRepository()
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
        libraryStateHolder.loadSongsFromRepository()
    }

    private fun loadAlbumsFromRepository() {
        libraryStateHolder.loadAlbumsFromRepository()
    }

    fun loadSongsIfNeeded() {
        libraryStateHolder.loadSongsIfNeeded()
    }

    fun loadAlbumsIfNeeded() {
        libraryStateHolder.loadAlbumsIfNeeded()
    }

    // Funciones para cargar artistas
    private fun loadArtistsFromRepository() {
        libraryStateHolder.loadArtistsFromRepository()
    }

    fun loadArtistsIfNeeded() {
        libraryStateHolder.loadArtistsIfNeeded()
    }

    fun loadFoldersFromRepository() {
        libraryStateHolder.loadFoldersFromRepository()
    }

    private fun onAllowedDirectoriesChanged() {
        _loadedTabs.value = emptySet()

        _playerUiState.update { state ->
            if (_currentLibraryTabId.value == LibraryTabId.FOLDERS) {
                state.copy(currentFolder = null, currentFolderPath = null)
            } else {
                state
            }
        }

        loadSongsFromRepository()
        loadAlbumsFromRepository()
        loadArtistsFromRepository()
        loadFoldersFromRepository()
    }

    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true
    ) {
        val castSession = castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            val mediaStatus = remoteMediaClient.mediaStatus
            val remoteQueueItems = mediaStatus?.queueItems ?: emptyList()
            val itemInQueue = remoteQueueItems.find { it.customData?.optString("songId") == song.id }

            if (itemInQueue != null) {
                // Song is already in the remote queue; prefer adjacent navigation commands to
                // mirror the no-glitch behavior of next/previous buttons regardless of context
                // mismatches.
                markPendingRemoteSong(song)
                val currentItemId = mediaStatus?.currentItemId
                val currentIndex = remoteQueueItems.indexOfFirst { it.itemId == currentItemId }
                val targetIndex = remoteQueueItems.indexOf(itemInQueue)
                when {
                    currentIndex >= 0 && targetIndex - currentIndex == 1 -> castPlayer?.next()
                    currentIndex >= 0 && targetIndex - currentIndex == -1 -> castPlayer?.previous()
                    else -> castPlayer?.jumpToItem(itemInQueue.itemId, 0L)
                }
                if (isVoluntaryPlay) incrementSongScore(song)
            } else {
                val lastQueue = lastRemoteQueue
                val currentRemoteId = mediaStatus
                    ?.let { status ->
                        status.getQueueItemById(status.getCurrentItemId())
                            ?.customData?.optString("songId")
                    } ?: lastRemoteSongId
                val currentIndex = lastQueue.indexOfFirst { it.id == currentRemoteId }
                val targetIndex = lastQueue.indexOfFirst { it.id == song.id }
                if (currentIndex != -1 && targetIndex != -1) {
                    markPendingRemoteSong(song)
                    when (targetIndex - currentIndex) {
                        1 -> castPlayer?.next()
                        -1 -> castPlayer?.previous()
                        else -> playSongs(contextSongs, song, queueName, null)
                    }
                    if (isVoluntaryPlay) incrementSongScore(song)
                } else {
                    // Song not in remote queue, so start a new playback session.
                    if (isVoluntaryPlay) incrementSongScore(song)
                    playSongs(contextSongs, song, queueName, null)
                }
            }
        } else {
            // Local playback logic
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

    private fun List<MediaQueueItem>.matchesQueueSongOrder(contextSongs: List<Song>): Boolean {
        if (size != contextSongs.size) return false

        for (index in indices) {
            val queueSongId = this[index].customData?.optString("songId")
            if (queueSongId.isNullOrEmpty() || queueSongId != contextSongs[index].id) {
                return false
            }
        }

        return true
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

        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val contentUri = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
            ?: mediaItem.localConfiguration?.uri?.toString()
            ?: return null
        
        // If content URI is missing, we can't play it.
        // It's possible we are playing a local library song but the MediaItem 
        // somehow has 'external:' prefix or we are re-parsing. 
        // But logic below specifically handles external songs.

        if (contentUri == null) {
            // Log or handle error? For now fallback to defaults or null?
            // Existing logic didn't null check strictly but `getString` returns null.
        }

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_song_title)
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_artist)
        val album = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM)?.takeIf { it.isNotBlank() }
            ?: metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_album)
        // Use 0L as default if not present or <= 0
        val duration = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION)?.takeIf { it > 0 } ?: 0L
        val albumArt = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
            ?: metadata.artworkUri?.toString()
        val genre = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_GENRE)
        val trackNumber = extras?.getInt(MediaItemBuilder.EXTERNAL_EXTRA_TRACK) ?: 0
        val year = extras?.getInt(MediaItemBuilder.EXTERNAL_EXTRA_YEAR) ?: 0
        val dateAdded = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DATE_ADDED)?.takeIf { it > 0 }
            ?: System.currentTimeMillis()
        val mimeType = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_MIME_TYPE)?.takeIf { true }
            ?: "-"
        // Use 0 as default
        val bitrate = extras?.getInt(MediaItemBuilder.EXTERNAL_EXTRA_BITRATE)?.takeIf { it > 0 }
            ?: 0
        // Use 0 as default
        val sampleRate = extras?.getInt(MediaItemBuilder.EXTERNAL_EXTRA_SAMPLE_RATE)?.takeIf { it > 0 }
            ?: 0

        return Song(
            id = mediaItem.mediaId,
            title = title,
            artist = artist,
            artistId = -1L,
            album = album,
            albumId = -1L,
            path = contentUri,
            contentUriString = contentUri,
            albumArtUriString = albumArt,
            duration = duration,
            genre = genre,
            lyrics = null,
            isFavorite = false,
            trackNumber = trackNumber,
            year = year,
            dateAdded = dateAdded,
            mimeType = mimeType,
            bitrate = bitrate,
            sampleRate = sampleRate
        )
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

        val castSession = castSession.value
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
                        extractAndGenerateColorScheme(uri)
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
                                    extractAndGenerateColorScheme(uri)
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

// Helper functions (fisherYatesCopy, generateShuffleOrder, buildAnchoredShuffleQueue, buildMediaItemFromSong) moved to Utils

// rebuildPlayerQueue functionality moved to PlaybackStateHolder (simplified)
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        viewModelScope.launch {
            transitionSchedulerJob?.cancel()
            // Store the original order so we can "unshuffle" later if the user turns shuffle off
            queueStateHolder.setOriginalQueueOrder(songsToPlay)
            queueStateHolder.saveOriginalQueueState(songsToPlay, queueName)

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
                QueueUtils.buildAnchoredShuffleQueue(songsToPlay, songsToPlay.indexOf(startSong).coerceAtLeast(0))
            } else {
                // Otherwise, just use the normal sequential order
                songsToPlay
            }

            // Send the final list (shuffled or not) to the player engine
            internalPlaySongs(finalSongsToPlay, startSong, queueName, playlistId)
        }
    }

    // Start playback with shuffle enabled in one coroutine to avoid racing queue updates
    fun playSongsShuffled(songsToPlay: List<Song>, queueName: String = "None", playlistId: String? = null) {
        viewModelScope.launch {
            if (songsToPlay.isEmpty()) {
                sendToast("No songs to shuffle.")
                return@launch
            }

            transitionSchedulerJob?.cancel()

            val startSong = songsToPlay.random()
            queueStateHolder.setOriginalQueueOrder(songsToPlay)
            queueStateHolder.saveOriginalQueueState(songsToPlay, queueName)
            playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = false) }

            internalPlaySongs(songsToPlay, startSong, queueName, playlistId)
            val isCastSessionActive = castSession.value?.remoteMediaClient != null
            if (isCastSessionActive || mediaController != null) {
                toggleShuffle(currentSongOverride = startSong)
            } else {
                pendingPlaybackAction = pendingPlaybackAction?.let { action ->
                    {
                        action()
                        toggleShuffle(currentSongOverride = startSong)
                    }
                }
            }
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
        
        val castSession = castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            if (!ensureHttpServerRunning()) return

            val serverAddress = MediaFileHttpServerService.serverAddress ?: return

            val startIndex = songsToPlay.indexOf(startSong).coerceAtLeast(0)
            val repeatMode = playbackStateHolder.stablePlayerState.value.repeatMode

            // Keep UI and callbacks pinned to the intended target while the cast queue rebuilds
            // to avoid bouncing back to the previous track when switching contexts.
            markPendingRemoteSong(startSong)
            lastRemoteQueue = songsToPlay
            lastRemoteSongId = startSong.id
            lastRemoteStreamPosition = 0L
            lastRemoteRepeatMode = repeatMode

            castPlayer?.loadQueue(
                songs = songsToPlay,
                startIndex = startIndex,
                startPosition = 0L,
                repeatMode = repeatMode,
                serverAddress = serverAddress,
                autoPlay = true,
                onComplete = { success ->
                    if (!success) {
                        sendToast("Failed to load media on cast device.")
                    }
                }
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
                extractAndGenerateColorScheme(uri, isPreload = false)
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

    fun repeatSingle(){
        val castSession = castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val newMode = MediaStatus.REPEAT_MODE_REPEAT_SINGLE;
            castPlayer?.setRepeatMode(newMode)
        } else {
            val newMode = Player.REPEAT_MODE_ONE
            mediaController?.repeatMode = newMode
        }
        viewModelScope.launch { userPreferencesRepository.setRepeatMode(Player.REPEAT_MODE_ONE) }
        playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = Player.REPEAT_MODE_ONE) }
    }

    fun repeatOff(){
        val castSession = castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val newMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
            castPlayer?.setRepeatMode(newMode)
        } else {
            val newMode = Player.REPEAT_MODE_OFF
            mediaController?.repeatMode = newMode
        }
        viewModelScope.launch { userPreferencesRepository.setRepeatMode(Player.REPEAT_MODE_OFF) }
        playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = Player.REPEAT_MODE_OFF) }
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

                    // If the currently playing song was removed, handle playback
//                    val currentMediaItem = controller.currentMediaItem
//                    if (currentMediaItem?.mediaId == songId) {
//                        when {
//                            controller.hasNextMediaItem() -> controller.seekToNextMediaItem()
//                            controller.hasPreviousMediaItem() -> controller.seekToPreviousMediaItem()
//                            else -> controller.stop()
//                        }
//                    }
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("MediaController", "Error removing from queue: ${e.message}")
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
        try {
            // Use the optimized ColorSchemeProcessor with LRU cache
            val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(albumArtUri)
            
            if (schemePair != null && !isPreload && playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString == albumArtUri) {
                _currentAlbumArtColorSchemePair.value = schemePair
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            
            return schemePair
        } catch (e: Exception) {
            if (!isPreload && playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString == albumArtUri) {
                _currentAlbumArtColorSchemePair.value = null
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
            return null
        } finally {
            Trace.endSection()
        }
    }

    private suspend fun extractAndGenerateColorScheme(albumArtUriAsUri: Uri?, isPreload: Boolean = false) {
        Trace.beginSection("PlayerViewModel.extractAndGenerateColorScheme")
        try {
            if (albumArtUriAsUri == null) {
                if (!isPreload && playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString == null) {
                    _currentAlbumArtColorSchemePair.value = null
                    updateLavaLampColorsBasedOnActivePlayerScheme()
                }
                return
            }
            
            val uriString = albumArtUriAsUri.toString()
            // Use the optimized ColorSchemeProcessor with LRU cache
            val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(uriString)
            
            if (!isPreload && playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString == uriString) {
                _currentAlbumArtColorSchemePair.value = schemePair
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
        } catch (e: Exception) {
            if (!isPreload && albumArtUriAsUri != null && 
                playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString == albumArtUriAsUri.toString()) {
                _currentAlbumArtColorSchemePair.value = null
                updateLavaLampColorsBasedOnActivePlayerScheme()
            }
        } finally {
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
        val castSession = castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            if (remoteMediaClient.isPlaying) {
                castPlayer?.pause()
            } else {
                // If there are items in the remote queue, just play.
                // Otherwise, load the current local queue to the remote player.
                if (remoteMediaClient.mediaQueue != null && remoteMediaClient.mediaQueue.itemCount > 0) {
                    castPlayer?.play()
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

                    val stream = ByteArrayOutputStream()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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

    suspend fun getSongs(songIds: List<String>) : List<Song>{
        return musicRepository.getSongsByIds(songIds).first()
    }

    //Sorting
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

    fun toggleFolderFilter() {
        viewModelScope.launch {
            val newFilterState = !_playerUiState.value.isFolderFilterActive
            userPreferencesRepository.setFolderFilterActive(newFilterState)
            _playerUiState.update { it.copy(isFolderFilterActive = newFilterState) }
            loadFoldersFromRepository()
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
        val selectedRouteId = _selectedRoute.value?.id
        val isCastRoute = route.isCastRoute() && !route.isDefault
        val isSwitchingBetweenRemotes = isCastRoute &&
            (isRemotePlaybackActive.value || isCastConnecting.value) &&
            selectedRouteId != null &&
            selectedRouteId != route.id

        if (isSwitchingBetweenRemotes) {
            castStateHolder.setPendingCastRouteId(route.id)
            castStateHolder.setCastConnecting(true)
            sessionManager.currentCastSession?.let { sessionManager.endCurrentSession(true) }
        } else {
            castStateHolder.setPendingCastRouteId(null)
        }
        mediaRouter.selectRoute(route)
    }

    fun disconnect(resetConnecting: Boolean = true) {
        val start = SystemClock.elapsedRealtime()
        castStateHolder.setPendingCastRouteId(null)
        val wasRemote = isRemotePlaybackActive.value
        if (wasRemote) {
            Timber.tag(CAST_LOG_TAG).i(
                "Manual disconnect requested; marking castConnecting=true until session ends. mainThread=%s",
                Looper.myLooper() == Looper.getMainLooper()
            )
            castStateHolder.setCastConnecting(true)
        }
        mediaRouter.selectRoute(mediaRouter.defaultRoute)
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
        _routeVolume.value = volume
        _selectedRoute.value?.requestSetVolume(volume)
    }

    fun refreshCastRoutes() {
        viewModelScope.launch {
            _isRefreshingRoutes.value = true
            mediaRouter.removeCallback(mediaRouterCallback)
            val mediaRouteSelector = buildCastRouteSelector()
            mediaRouter.addCallback(
                mediaRouteSelector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            _castRoutes.value = mediaRouter.routes.filter { it.isCastRoute() }.distinctBy { it.id }
            _selectedRoute.value = mediaRouter.selectedRoute
            delay(1800) // Allow active scan to run briefly
            mediaRouter.removeCallback(mediaRouterCallback)
            mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            _castRoutes.value = mediaRouter.routes.filter { it.isCastRoute() }.distinctBy { it.id }
            _selectedRoute.value = mediaRouter.selectedRoute
            _isRefreshingRoutes.value = false
        }
    }

    private suspend fun ensureHttpServerRunning(): Boolean {
        if (MediaFileHttpServerService.isServerRunning && MediaFileHttpServerService.serverAddress != null) {
            return true
        }

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        val hasLocalTransport = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

        if (activeNetwork == null || !hasLocalTransport) {
            sendToast("Connect to a Wi‑Fi or local network to cast.")
            Timber.w("Cannot start cast server: no suitable local network")
            return false
        }

        MediaFileHttpServerService.lastFailureReason = null

        context.startService(Intent(context, MediaFileHttpServerService::class.java).apply {
            action = MediaFileHttpServerService.ACTION_START_SERVER
        })

        val startTime = SystemClock.elapsedRealtime()
        val backoffDelays = listOf(100L, 200L, 350L, 500L, 700L, 900L)
        var attempt = 0

        while (SystemClock.elapsedRealtime() - startTime < 5500L) {
            if (MediaFileHttpServerService.isServerRunning && MediaFileHttpServerService.serverAddress != null) {
                return true
            }

            when (MediaFileHttpServerService.lastFailureReason) {
                MediaFileHttpServerService.FailureReason.NO_NETWORK_ADDRESS -> {
                    sendToast("Could not find a local IP address. Verify Wi‑Fi connectivity and try again.")
                    Timber.e("HTTP server failed: no network address available")
                    return false
                }

                MediaFileHttpServerService.FailureReason.START_EXCEPTION -> {
                    sendToast("Cast server failed to start. Check your network and try again.")
                    Timber.e("HTTP server failed to start due to exception")
                    return false
                }

                else -> {}
            }

            val delayDuration = backoffDelays.getOrElse(attempt) { 1000L }
            delay(delayDuration)
            attempt++
        }

        sendToast("Starting cast server is taking longer than expected. Check Wi‑Fi and retry.")
        Timber.e("HTTP server start timed out after waiting for address: %s", MediaFileHttpServerService.serverAddress)
        return false
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        listeningStatsTracker.onCleared()
        mediaRouter.removeCallback(mediaRouterCallback)
        searchStateHolder.onCleared()
        aiStateHolder.onCleared()
        libraryStateHolder.onCleared()
        sleepTimerStateHolder.onCleared()
        connectivityStateHolder.onCleared()
        sessionManager.removeSessionManagerListener(castSessionManagerListener as SessionManagerListener<CastSession>, CastSession::class.java)
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
                
                 // Logic to update color scheme if art changed
                 if (coverArtUpdate != null) {
                     purgeAlbumArtThemes(previousAlbumArt, refreshedAlbumArtUri)
                     if (refreshedAlbumArtUri != null) {
                         getAlbumColorSchemeFlow(refreshedAlbumArtUri)
                         extractAndGenerateColorScheme(refreshedAlbumArtUri.toUri(), isPreload = false)
                     } else {
                         extractAndGenerateColorScheme(null, isPreload = false)
                     }
                 }

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
                }

                if (_selectedSongForInfo.value?.id == song.id) {
                    _selectedSongForInfo.value = updatedSong
                }

                if (coverArtUpdate != null) {
                    purgeAlbumArtThemes(previousAlbumArt, updatedSong.albumArtUriString)
                    val paletteTargetUri = updatedSong.albumArtUriString
                    if (paletteTargetUri != null) {
                        getAlbumColorSchemeFlow(paletteTargetUri)
                        extractAndGenerateColorScheme(paletteTargetUri.toUri(), isPreload = false)
                    } else {
                        extractAndGenerateColorScheme(null, isPreload = false)
                    }
                }

                // No need for full library sync - file, MediaStore, and local DB are already updated
                // syncManager.sync() was removed to avoid unnecessary wait time
                _toastEvents.emit("Metadata updated successfully")
            } else {
                _toastEvents.emit("Failed to update metadata")
            }
        }
    }

    private fun invalidateCoverArtCaches(vararg uriStrings: String?) {
        val imageLoader = context.imageLoader
        val memoryCache = imageLoader.memoryCache
        val diskCache = imageLoader.diskCache
        if (memoryCache == null && diskCache == null) return

        val knownSizeSuffixes = listOf(null, "128x128", "150x150", "168x168", "256x256", "300x300", "512x512", "600x600")

        uriStrings.mapNotNull { it?.takeIf(String::isNotBlank) }.forEach { baseUri ->
            knownSizeSuffixes.forEach { suffix ->
                val cacheKey = suffix?.let { "${baseUri}_${it}" } ?: baseUri
                memoryCache?.remove(MemoryCache.Key(cacheKey))
                diskCache?.remove(cacheKey)
            }
        }
    }

    private suspend fun purgeAlbumArtThemes(vararg uriStrings: String?) {
        val uris = uriStrings.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct()
        if (uris.isEmpty()) return

        withContext(Dispatchers.IO) {
            albumArtThemeDao.deleteThemesByUris(uris)
        }

        uris.forEach { uri ->
            individualAlbumColorSchemes.remove(uri)?.value = null
            synchronized(urisBeingProcessed) {
                urisBeingProcessed.remove(uri)
            }
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
    fun fetchLyricsForCurrentSong(forcePickResults: Boolean = false) {
        val currentSong = stablePlayerState.value.currentSong
        viewModelScope.launch {
            lyricsStateHolder.setSearchState(LyricsSearchUiState.Loading)
            if (currentSong != null) {
                if (forcePickResults) {
                    musicRepository.searchRemoteLyrics(currentSong)
                        .onSuccess { (query, results) ->
                            lyricsStateHolder.setSearchState(LyricsSearchUiState.PickResult(query, results))
                        }
                        .onFailure { error ->
                            if (error is NoLyricsFoundException) {
                                lyricsStateHolder.setSearchState(
                                    LyricsSearchUiState.NotFound(
                                        message = context.getString(R.string.lyrics_not_found)
                                    )
                                )
                            } else {
                                lyricsStateHolder.setSearchState(
                                    LyricsSearchUiState.Error(error.message ?: "Unknown error")
                                )
                            }
                        }
                } else {
                    musicRepository.getLyricsFromRemote(currentSong)
                        .onSuccess { (lyrics, rawLyrics) ->
                            lyricsStateHolder.setSearchState(LyricsSearchUiState.Success(lyrics))
                            val updatedSong = currentSong.copy(lyrics = rawLyrics)
                            updateSongInStates(updatedSong, lyrics)
                        }
                        .onFailure { error ->
                            if (error is NoLyricsFoundException) {
                                musicRepository.searchRemoteLyrics(currentSong)
                                    .onSuccess { (query, results) ->
                                        lyricsStateHolder.setSearchState(LyricsSearchUiState.PickResult(query, results))
                                    }
                                    .onFailure { searchError ->
                                        if (searchError is NoLyricsFoundException) {
                                            lyricsStateHolder.setSearchState(
                                                LyricsSearchUiState.NotFound(
                                                    message = context.getString(R.string.lyrics_not_found)
                                                )
                                            )
                                        } else {
                                            lyricsStateHolder.setSearchState(
                                                LyricsSearchUiState.Error(error.message ?: "Unknown error")
                                            )
                                        }
                                    }
                            } else {
                                lyricsStateHolder.setSearchState(LyricsSearchUiState.Error(error.message ?: "Unknown error"))
                            }
                        }
                }
            } else {
                lyricsStateHolder.setSearchState(LyricsSearchUiState.Error("No song is currently playing."))
            }
        }
    }

    /**
     * Manual search lyrics using query provided by user (title and artist)
     */
    fun searchLyricsManually(title: String, artist: String? = null) {
        if (title.isBlank()) return

        viewModelScope.launch {
            lyricsStateHolder.setSearchState(LyricsSearchUiState.Loading)

            musicRepository.searchRemoteLyricsByQuery(title, artist)
                .onSuccess { (q, results) ->
                    lyricsStateHolder.setSearchState(LyricsSearchUiState.PickResult(q, results))
                }
                .onFailure { error ->
                    lyricsStateHolder.setSearchState(
                        if (error is NoLyricsFoundException) {
                            LyricsSearchUiState.NotFound(
                                message = context.getString(R.string.lyrics_not_found)
                            )
                        } else {
                            LyricsSearchUiState.Error(error.message ?: "Unknown error")
                        }
                    )
                }
        }
    }

    fun acceptLyricsSearchResultForCurrentSong(result: LyricsSearchResult) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.setSearchState(LyricsSearchUiState.Success(result.lyrics))

        val updatedSong = currentSong.copy(lyrics = result.rawLyrics)
        updateSongInStates(updatedSong, result.lyrics)

        viewModelScope.launch {
            musicRepository.updateLyrics(
                currentSong.id.toLong(),
                result.rawLyrics
            )
        }
    }

    fun resetLyricsForCurrentSong() {
        resetLyricsSearchState()
        viewModelScope.launch {
            musicRepository.resetLyrics(stablePlayerState.value.currentSong!!.id.toLong())
            playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
            // loadLyricsForCurrentSong()
        }
    }

    fun resetAllLyrics() {
        resetLyricsSearchState()
        viewModelScope.launch {
            musicRepository.resetAllLyrics()
            playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
        }
    }

    /**
     * Procesa la letra importada de un archivo, la guarda y actualiza la UI.
     * @param songId El ID de la canción para la que se importa la letra.
     * @param lyricsContent El contenido de la letra como String.
     */
    fun importLyricsFromFile(songId: Long, lyricsContent: String) {
        viewModelScope.launch {
            musicRepository.updateLyrics(songId, lyricsContent)
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            if (currentSong != null && currentSong.id.toLong() == songId) {
                val updatedSong = currentSong.copy(lyrics = lyricsContent)
                val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
                updateSongInStates(updatedSong, parsedLyrics)
                lyricsStateHolder.setSearchState(LyricsSearchUiState.Success(parsedLyrics))
                _toastEvents.emit("Lyrics imported successfully!")
            } else {
                lyricsStateHolder.setSearchState(LyricsSearchUiState.Error("Could not associate lyrics with the current song."))
            }
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
