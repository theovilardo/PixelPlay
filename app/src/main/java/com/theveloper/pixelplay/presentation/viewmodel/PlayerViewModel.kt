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

private const val EXTERNAL_MEDIA_ID_PREFIX = "external:"
private const val EXTERNAL_EXTRA_PREFIX = "com.theveloper.pixelplay.external."
private const val EXTERNAL_EXTRA_FLAG = EXTERNAL_EXTRA_PREFIX + "FLAG"
private const val EXTERNAL_EXTRA_ALBUM = EXTERNAL_EXTRA_PREFIX + "ALBUM"
private const val EXTERNAL_EXTRA_DURATION = EXTERNAL_EXTRA_PREFIX + "DURATION"
private const val EXTERNAL_EXTRA_CONTENT_URI = EXTERNAL_EXTRA_PREFIX + "CONTENT_URI"
private const val EXTERNAL_EXTRA_ALBUM_ART = EXTERNAL_EXTRA_PREFIX + "ALBUM_ART"
private const val EXTERNAL_EXTRA_GENRE = EXTERNAL_EXTRA_PREFIX + "GENRE"
private const val EXTERNAL_EXTRA_TRACK = EXTERNAL_EXTRA_PREFIX + "TRACK"
private const val EXTERNAL_EXTRA_YEAR = EXTERNAL_EXTRA_PREFIX + "YEAR"
private const val EXTERNAL_EXTRA_DATE_ADDED = EXTERNAL_EXTRA_PREFIX + "DATE_ADDED"
private const val EXTERNAL_EXTRA_MIME_TYPE = EXTERNAL_EXTRA_PREFIX + "MIME_TYPE"
private const val EXTERNAL_EXTRA_BITRATE = EXTERNAL_EXTRA_PREFIX + "BITRATE"
private const val EXTERNAL_EXTRA_SAMPLE_RATE = EXTERNAL_EXTRA_PREFIX + "SAMPLE_RATE"
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

private data class ExternalSongLoadResult(
    val song: Song,
    val relativePath: String?,
    val bucketId: Long?,
    val displayName: String?
)

sealed interface LyricsSearchUiState {
    object Idle : LyricsSearchUiState
    object Loading : LyricsSearchUiState
    data class PickResult(val query: String, val results: List<LyricsSearchResult>) : LyricsSearchUiState
    data class Success(val lyrics: Lyrics) : LyricsSearchUiState
    data class Error(val message: String, val query: String? = null) : LyricsSearchUiState
}

private data class ActiveSession(
    val songId: String,
    var totalDurationMs: Long,
    val startedAtEpochMs: Long,
    var lastKnownPositionMs: Long,
    var accumulatedListeningMs: Long,
    var lastRealtimeMs: Long,
    var lastUpdateEpochMs: Long,
    var isPlaying: Boolean,
    val isVoluntary: Boolean
)

@UnstableApi
@SuppressLint("LogNotTimber")
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    val syncManager: SyncManager, // Inyectar SyncManager
    private val songMetadataEditor: SongMetadataEditor,
    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val aiMetadataGenerator: AiMetadataGenerator,
    private val artistImageRepository: ArtistImageRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    private val appShortcutManager: com.theveloper.pixelplay.utils.AppShortcutManager
) : ViewModel() {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()
    private val _masterAllSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    private val _stablePlayerState = MutableStateFlow(StablePlayerState())
    val stablePlayerState: StateFlow<StablePlayerState> = _stablePlayerState.asStateFlow()

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

    // Lyrics sync offset per song in milliseconds (positive = lyrics later, negative = lyrics earlier)
    // This is derived from the current song's ID
    private val _currentSongLyricsSyncOffset = MutableStateFlow(0)
    val currentSongLyricsSyncOffset: StateFlow<Int> = _currentSongLyricsSyncOffset.asStateFlow()

    fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setLyricsSyncOffset(songId, offsetMs)
            _currentSongLyricsSyncOffset.value = offsetMs
        }
    }

    private fun observeCurrentSongLyricsOffset() {
        viewModelScope.launch {
            stablePlayerState.collect { state ->
                state.currentSong?.id?.let { songId ->
                    val offset = userPreferencesRepository.getLyricsSyncOffset(songId)
                    _currentSongLyricsSyncOffset.value = offset
                }
            }
        }
    }

    private val _isInitialThemePreloadComplete = MutableStateFlow(false)
    val isInitialThemePreloadComplete: StateFlow<Boolean> = _isInitialThemePreloadComplete.asStateFlow()

    // Manual shuffle state - stores original queue order for unshuffling (Spotify-like behavior)
    private var _originalQueueOrder: List<Song> = emptyList()
    private var _originalQueueName: String = "None"

    // Sleep Timer StateFlows
    private val _sleepTimerEndTimeMillis = MutableStateFlow<Long?>(null)
    val sleepTimerEndTimeMillis: StateFlow<Long?> = _sleepTimerEndTimeMillis.asStateFlow()

    private val _isEndOfTrackTimerActive = MutableStateFlow<Boolean>(false)
    val isEndOfTrackTimerActive: StateFlow<Boolean> = _isEndOfTrackTimerActive.asStateFlow()

    private val _activeTimerValueDisplay = MutableStateFlow<String?>(null)
    val activeTimerValueDisplay: StateFlow<String?> = _activeTimerValueDisplay.asStateFlow()

    private val _playCount = MutableStateFlow<Float>(1f)
    val playCount: StateFlow<Float> = _playCount.asStateFlow()

    private val _lyricsSearchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val lyricsSearchUiState = _lyricsSearchUiState.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var eotSongMonitorJob: Job? = null
    private var lyricsLoadingJob: Job? = null
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

    private val _isWifiEnabled = MutableStateFlow(false)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()
    private val _isWifiRadioOn = MutableStateFlow(false)
    val isWifiRadioOn: StateFlow<Boolean> = _isWifiRadioOn.asStateFlow()
    private val _wifiName = MutableStateFlow<String?>(null)
    val wifiName: StateFlow<String?> = _wifiName.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()
    private val _bluetoothName = MutableStateFlow<String?>(null)
    val bluetoothName: StateFlow<String?> = _bluetoothName.asStateFlow()

    private val _bluetoothAudioDevices = MutableStateFlow<List<String>>(emptyList())
    val bluetoothAudioDevices: StateFlow<List<String>> = _bluetoothAudioDevices.asStateFlow()

    private val mediaRouter: MediaRouter
    private val mediaRouterCallback: MediaRouter.Callback
    private val connectivityManager: ConnectivityManager
    private val wifiManager: WifiManager?
    private var wifiStateReceiver: BroadcastReceiver? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val bluetoothAdapter: BluetoothAdapter?
    private val bluetoothManager: BluetoothManager
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val audioDeviceCallback: android.media.AudioDeviceCallback
    private val sessionManager: SessionManager = CastContext.getSharedInstance(context).sessionManager
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private val _castSession = MutableStateFlow<CastSession?>(null)
    private var castPlayer: CastPlayer? = null
    private val _isRemotePlaybackActive = MutableStateFlow(false)
    val isRemotePlaybackActive: StateFlow<Boolean> = _isRemotePlaybackActive.asStateFlow()
    private val _isCastConnecting = MutableStateFlow(false)
    val isCastConnecting: StateFlow<Boolean> = _isCastConnecting.asStateFlow()
    private val castControlCategory = CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
    private var pendingCastRouteId: String? = null
    private val _remotePosition = MutableStateFlow(0L)
    val remotePosition: StateFlow<Long> = _remotePosition.asStateFlow()
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
        _stablePlayerState.update { state -> state.copy(currentSong = song) }
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
        _remotePosition.value = 0L
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
            ?: _stablePlayerState.value.currentSong
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
                    if (enabled != _stablePlayerState.value.isShuffleEnabled) {
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

    private val _currentFavoriteSortOptionStateFlow = MutableStateFlow<SortOption>(SortOption.LikedSongDateLiked) // Default aligned with LibraryTabId default.
    val currentFavoriteSortOptionStateFlow: StateFlow<SortOption> = _currentFavoriteSortOptionStateFlow.asStateFlow()

    val favoriteSongs: StateFlow<ImmutableList<Song>> = combine(
        favoriteSongIds,
        _masterAllSongs,
        currentFavoriteSortOptionStateFlow
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
    .flowOn(Dispatchers.Default) // Execute combine and transformations on Default dispatcher
    .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    private var dailyMixJob: Job? = null

    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
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
        // Cancel any previous job to avoid multiple updates running
        dailyMixJob?.cancel()
        dailyMixJob = viewModelScope.launch(Dispatchers.IO) {
            // We need all songs to generate the mix
            val allSongs = allSongsFlow.first()
            if (allSongs.isNotEmpty()) {
                val favoriteIds = userPreferencesRepository.favoriteSongIdsFlow.first()
                val mix = dailyMixManager.generateDailyMix(allSongs, favoriteIds)
                _dailyMixSongs.value = mix.toImmutableList()
                // Save the new mix
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                val yourMix = dailyMixManager.generateYourMix(allSongs, favoriteIds)
                _yourMixSongs.value = yourMix.toImmutableList()
            } else {
                _yourMixSongs.value = persistentListOf()
            }
        }
    }

    fun shuffleAllSongs() {
        Log.d("ShuffleDebug", "shuffleAllSongs called.")
        // Don't use ExoPlayer's shuffle mode - we manually shuffle instead
        val currentSong = _stablePlayerState.value.currentSong
        val isPlaying = _stablePlayerState.value.isPlaying
        
        // If something is playing, just toggle shuffle on current queue
        if (currentSong != null && isPlaying) {
            if (!_stablePlayerState.value.isShuffleEnabled) {
                toggleShuffle()
            }
            return
        }
        
        // Otherwise start a new shuffled queue
        val allSongs = _playerUiState.value.allSongs
        if (allSongs.isNotEmpty()) {
            playSongsShuffled(allSongs, "All Songs (Shuffled)")
        }
    }

    fun playRandomSong() {
        val allSongs = _playerUiState.value.allSongs
        if (allSongs.isNotEmpty()) {
            playSongsShuffled(allSongs, "All Songs (Shuffled)")
        }
    }

    fun shuffleFavoriteSongs() {
        Log.d("ShuffleDebug", "shuffleFavoriteSongs called.")
        // Don't use ExoPlayer's shuffle mode - we manually shuffle instead
        val currentSong = _stablePlayerState.value.currentSong
        val isPlaying = _stablePlayerState.value.isPlaying
        
        // If something is playing, just toggle shuffle on current queue
        if (currentSong != null && isPlaying) {
            if (!_stablePlayerState.value.isShuffleEnabled) {
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
            val albumSongs = _playerUiState.value.allSongs.filter { it.albumId == randomAlbum.id }
            if (albumSongs.isNotEmpty()) {
                playSongsShuffled(albumSongs, randomAlbum.title)
            }
        }
    }

    fun shuffleRandomArtist() {
        val allArtists = _playerUiState.value.artists
        if (allArtists.isNotEmpty()) {
            val randomArtist = allArtists.random()
            val artistSongs = _playerUiState.value.allSongs.filter { it.artistId == randomArtist.id }
            if (artistSongs.isNotEmpty()) {
                playSongsShuffled(artistSongs, randomArtist.name)
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
    private var remoteProgressObserverJob: Job? = null
    private var transitionSchedulerJob: Job? = null
    private val listeningStatsTracker = ListeningStatsTracker()
    private var lastKnownRemoteIsPlaying = false

    private fun incrementSongScore(song: Song) {
        listeningStatsTracker.onVoluntarySelection(song.id)
    }

    companion object {
        private val MIN_SESSION_LISTEN_MS = TimeUnit.SECONDS.toMillis(5)
    }

    private var currentSession: ActiveSession? = null

    private inner class ListeningStatsTracker {
        private var pendingVoluntarySongId: String? = null

        fun onVoluntarySelection(songId: String) {
            pendingVoluntarySongId = songId
        }

        fun onSongChanged(
            song: Song?,
            positionMs: Long,
            durationMs: Long,
            isPlaying: Boolean
        ) {
            finalizeCurrentSession()
            if (song == null) {
                return
            }

            val nowRealtime = SystemClock.elapsedRealtime()
            val nowEpoch = System.currentTimeMillis()
            val normalizedDuration = when {
                durationMs > 0 && durationMs != C.TIME_UNSET -> durationMs
                song.duration > 0 -> song.duration
                else -> 0L
            }

            currentSession = ActiveSession(
                songId = song.id,
                totalDurationMs = normalizedDuration,
                startedAtEpochMs = nowEpoch,
                lastKnownPositionMs = positionMs.coerceAtLeast(0L),
                accumulatedListeningMs = 0L,
                lastRealtimeMs = nowRealtime,
                lastUpdateEpochMs = nowEpoch,
                isPlaying = isPlaying,
                isVoluntary = pendingVoluntarySongId == song.id
            )
            if (pendingVoluntarySongId == song.id) {
                pendingVoluntarySongId = null
            }
        }

        fun onPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
            val session = currentSession ?: return
            val nowRealtime = SystemClock.elapsedRealtime()
            if (session.isPlaying) {
                session.accumulatedListeningMs += (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
            }
            session.isPlaying = isPlaying
            session.lastRealtimeMs = nowRealtime
            session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
            session.lastUpdateEpochMs = System.currentTimeMillis()
        }

        fun onProgress(positionMs: Long, isPlaying: Boolean) {
            val session = currentSession ?: return
            val nowRealtime = SystemClock.elapsedRealtime()
            if (session.isPlaying) {
                val delta = (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
                if (delta > 0) {
                    session.accumulatedListeningMs += delta
                }
            }
            session.isPlaying = isPlaying
            session.lastRealtimeMs = nowRealtime
            session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
            session.lastUpdateEpochMs = System.currentTimeMillis()
        }

        fun ensureSession(
            song: Song?,
            positionMs: Long,
            durationMs: Long,
            isPlaying: Boolean
        ) {
            if (song == null) {
                finalizeCurrentSession()
                return
            }
            val existing = currentSession
            if (existing?.songId == song.id) {
                updateDuration(durationMs)
                val nowRealtime = SystemClock.elapsedRealtime()
                if (existing.isPlaying) {
                    existing.accumulatedListeningMs += (nowRealtime - existing.lastRealtimeMs).coerceAtLeast(0L)
                }
                existing.isPlaying = isPlaying
                existing.lastRealtimeMs = nowRealtime
                existing.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
                existing.lastUpdateEpochMs = System.currentTimeMillis()
                return
            }
            onSongChanged(song, positionMs, durationMs, isPlaying)
        }

        fun updateDuration(durationMs: Long) {
            val session = currentSession ?: return
            if (durationMs > 0 && durationMs != C.TIME_UNSET) {
                session.totalDurationMs = durationMs
            }
        }

        fun finalizeCurrentSession() {
            val session = currentSession ?: return
            val nowRealtime = SystemClock.elapsedRealtime()
            if (session.isPlaying) {
                session.accumulatedListeningMs += (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
            }
            val totalCap = if (session.totalDurationMs > 0) session.totalDurationMs else Long.MAX_VALUE
            val listened = session.accumulatedListeningMs.coerceAtMost(totalCap).coerceAtLeast(0L)
            if (listened >= MIN_SESSION_LISTEN_MS) {
                val rawEndTimestamp = session.lastUpdateEpochMs.takeIf { it > 0L }
                    ?: (session.startedAtEpochMs + listened)
                val timestamp = rawEndTimestamp
                    .coerceAtLeast(session.startedAtEpochMs.coerceAtLeast(0L))
                    .coerceAtMost(System.currentTimeMillis())
                val songId = session.songId
                viewModelScope.launch(Dispatchers.IO) {
                    dailyMixManager.recordPlay(
                        songId = songId,
                        songDurationMs = listened,
                        timestamp = timestamp
                    )
                    playbackStatsRepository.recordPlayback(
                        songId = songId,
                        durationMs = listened,
                        timestamp = timestamp
                    )
                }
            }
            currentSession = null
            if (pendingVoluntarySongId == session.songId) {
                pendingVoluntarySongId = null
            }
        }

        fun onPlaybackStopped() {
            finalizeCurrentSession()
        }

        fun onCleared() {
            finalizeCurrentSession()
        }
    }


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

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

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

    private fun updateWifiInfo(network: Network?) {
        if (!hasLocationPermission()) {
            _wifiName.value = null
            return
        }
        if (network == null) {
            _wifiName.value = null
            return
        }
        val caps = connectivityManager.getNetworkCapabilities(network)
        if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            val wifiInfo = caps.transportInfo as? android.net.wifi.WifiInfo
            val rawSsid = wifiInfo?.ssid
            // Android sometimes returns <unknown ssid> if no location permission
            if (rawSsid != null && rawSsid != "<unknown ssid>") {
                _wifiName.value = rawSsid.trim('"')
            } else {
                // Fallback to WifiManager for older APIs or if transportInfo failed
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                val info = wifiManager?.connectionInfo
                val fallbackSsid = info?.ssid
                if (fallbackSsid != null && fallbackSsid != "<unknown ssid>") {
                    _wifiName.value = fallbackSsid.trim('"')
                } else {
                    _wifiName.value = null
                }
            }
        }
    }

    private fun updateWifiRadioState() {
        val state = wifiManager?.wifiState
        _isWifiRadioOn.value = when (state) {
            WifiManager.WIFI_STATE_ENABLED, WifiManager.WIFI_STATE_ENABLING -> true
            WifiManager.WIFI_STATE_DISABLED, WifiManager.WIFI_STATE_DISABLING -> false
            else -> wifiManager?.isWifiEnabled == true
        }
    }

    private fun updateBluetoothName(forceClear: Boolean = false) {
        if (!hasBluetoothPermission()) {
            if (forceClear) _bluetoothName.value = null
            _bluetoothAudioDevices.value = emptyList()
            return
        }

        val connectedDevice = safeGetConnectedDevices(BluetoothProfile.A2DP).firstOrNull()
            ?: safeGetConnectedDevices(BluetoothProfile.HEADSET).firstOrNull()
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                safeGetConnectedDevices(BluetoothProfile.LE_AUDIO).firstOrNull()
            } else {
                null
            }

        val audioDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
        val activeBluetoothAudioName = audioDevices.firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
        }?.productName?.toString()

        val resolvedName = connectedDevice?.name ?: activeBluetoothAudioName

        when {
            resolvedName != null -> _bluetoothName.value = resolvedName
            forceClear || !(bluetoothAdapter?.isEnabled ?: false) -> _bluetoothName.value = null
        }

        updateBluetoothAudioDevices()
    }

    private fun updateBluetoothAudioDevices() {
        if (!hasBluetoothPermission()) {
            _bluetoothAudioDevices.value = emptyList()
            return
        }

        val connectedDevices = buildSet {
            safeGetConnectedDevices(BluetoothProfile.A2DP).mapNotNullTo(this) { it.name }
            safeGetConnectedDevices(BluetoothProfile.HEADSET).mapNotNullTo(this) { it.name }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                safeGetConnectedDevices(BluetoothProfile.LE_AUDIO).mapNotNullTo(this) { it.name }
            }

            audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .filter {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
                }
                .mapNotNull { it.productName?.toString() }
                .forEach { add(it) }
        }

        _bluetoothAudioDevices.value = connectedDevices.toList().sorted()
    }

    private fun safeGetConnectedDevices(profile: Int): List<BluetoothDevice> {
        return runCatching { bluetoothManager.getConnectedDevices(profile) }.getOrElse { emptyList() }
    }

    fun refreshLocalConnectionInfo() {
        val currentNetwork = connectivityManager.activeNetwork
        val currentCaps = connectivityManager.getNetworkCapabilities(currentNetwork)
        updateWifiRadioState()
        _isWifiEnabled.value = currentCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (_isWifiEnabled.value) updateWifiInfo(currentNetwork)

        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
        updateBluetoothName()
        updateBluetoothAudioDevices()
    }

    init {
        Log.i("PlayerViewModel", "init started.")

        // Cast initialization if already connected
        val currentSession = sessionManager.currentCastSession
        if (currentSession != null) {
            castPlayer = CastPlayer(currentSession)
            _isRemotePlaybackActive.value = true
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
            _currentFavoriteSortOptionStateFlow.value = initialLikedSort

            sortSongs(initialSongSort, persist = false)
            sortAlbums(initialAlbumSort, persist = false)
            sortArtists(initialArtistSort, persist = false)
            sortFavoriteSongs(initialLikedSort, persist = false)
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
                flushPendingRepeatMode()
                syncShuffleStateWithSession(_stablePlayerState.value.isShuffleEnabled)
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

        // Connectivity listeners
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Initial state check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        updateWifiRadioState()
        _isWifiEnabled.value = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (_isWifiEnabled.value) {
            updateWifiInfo(activeNetwork)
        }

        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false
        updateBluetoothName()

        // Wi-Fi listener
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    _isWifiEnabled.value = true
                    updateWifiInfo(network)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    _isWifiEnabled.value = true
                    updateWifiInfo(network)
                }
            }

            override fun onLost(network: Network) {
                // A specific network was lost; check if another Wi-Fi network is active.
                val currentNetwork = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
                _isWifiEnabled.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (!_isWifiEnabled.value) _wifiName.value = null
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)

        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                    updateWifiRadioState()
                }
            }
        }
        wifiStateReceiver?.let {
            context.registerReceiver(it, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        }

        // Bluetooth listener
        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        _isBluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                        if (state == BluetoothAdapter.STATE_OFF) {
                            _bluetoothName.value = null
                            _bluetoothAudioDevices.value = emptyList()
                        } else updateBluetoothName(forceClear = false)
                    }
                    android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED,
                    android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        updateBluetoothName(forceClear = intent?.action == android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    }
                }
            }
        }
        val btFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothStateReceiver, btFilter)

        audioDeviceCallback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateBluetoothName()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateBluetoothName(forceClear = removedDevices?.any {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                        it.type == android.media.AudioDeviceInfo.TYPE_HEARING_AID
                } == true)
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        remoteProgressListener = RemoteMediaClient.ProgressListener { progress, _ ->
            if (!isRemotelySeeking.value) {
                val pendingId = pendingRemoteSongId
                if (pendingId != null && SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000) {
                    val status = _castSession.value?.remoteMediaClient?.mediaStatus
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
                _remotePosition.value = progress
                lastRemoteStreamPosition = progress
                listeningStatsTracker.onProgress(progress, lastKnownRemoteIsPlaying)
                Timber.tag(CAST_LOG_TAG).d("Remote progress update: %d", progress)
            }
        }

        remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val remoteMediaClient = _castSession.value?.remoteMediaClient ?: return
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
                    _remotePosition.value = streamPosition
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
                        val stableSong = _stablePlayerState.value.currentSong
                        if (
                            pendingId != null &&
                            pendingId == stableSong?.id &&
                            SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000
                        ) {
                            stableSong
                        } else {
                            _stablePlayerState.value.currentSong
                        }
                    }
                    ?: lastRemoteQueue.firstOrNull { it.id == lastRemoteSongId }
                if (currentSongFallback?.id != _stablePlayerState.value.currentSong?.id) {
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
                    _remotePosition.value = streamPosition
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
                val effectiveSongForStats = effectiveSong ?: _stablePlayerState.value.currentSong
                listeningStatsTracker.ensureSession(
                    song = effectiveSongForStats,
                    positionMs = streamPosition,
                    durationMs = streamDuration,
                    isPlaying = isPlaying
                )
                if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_IDLE && mediaStatus.queueItemCount == 0) {
                    listeningStatsTracker.onPlaybackStopped()
                }
                _stablePlayerState.update {
                    var nextSong = currentSongFallback
                    // Prevent clearing the song if we are in the middle of a connection attempt
                    if (_isCastConnecting.value && nextSong == null) {
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
                if (_castSession.value != null) {
                    _isSheetVisible.value = true
                }
            }
        }

        castSessionManagerListener = object : SessionManagerListener<CastSession> {
            private fun transferPlayback(session: CastSession) {
                viewModelScope.launch {
                    pendingCastRouteId = null
                    _isCastConnecting.value = true
                    if (!ensureHttpServerRunning()) {
                        _isCastConnecting.value = false
                        disconnect()
                        return@launch
                    }

                    val serverAddress = MediaFileHttpServerService.serverAddress
                    val localPlayer = mediaController
                    val currentQueue = _playerUiState.value.currentPlaybackQueue
                    if (serverAddress == null || localPlayer == null || currentQueue.isEmpty()) {
                        _isCastConnecting.value = false
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

                    castPlayer = CastPlayer(session)
                    _castSession.value = session
                    _isRemotePlaybackActive.value = false

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
                                _isCastConnecting.value = false
                            }
                            _isRemotePlaybackActive.value = success
                            _isCastConnecting.value = false
                        }
                    )

                    session.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
                    session.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)

                    remoteProgressObserverJob?.cancel()
                    remoteProgressObserverJob = viewModelScope.launch {
                        _remotePosition.collect { position ->
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
                val session = _castSession.value ?: return
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
                    .takeIf { it > 0 } ?: _remotePosition.value
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
                castPlayer = null
                _castSession.value = null
                _isRemotePlaybackActive.value = false
                if (targetRouteId == null) {
                    context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                    disconnect(resetConnecting = false) // Don't reset connecting flag yet
                } else {
                    _isCastConnecting.value = true
                }
                Timber.tag(CAST_LOG_TAG).i(
                    "Transfer back: local session state cleared at +%dms (targetRouteId=%s)",
                    SystemClock.elapsedRealtime() - startMs,
                    targetRouteId
                )

                val localPlayer = mediaController ?: run {
                    if (targetRouteId == null) {
                        _isCastConnecting.value = false
                    } else {
                        pendingCastRouteId = null
                        _isCastConnecting.value = false
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

                        _stablePlayerState.update {
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
                    _stablePlayerState.update {
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
                    _isCastConnecting.value = false // NOW we reset the flag
                    flushPendingRepeatMode()
                } else {
                    val pendingRoute = mediaRouter.routes.firstOrNull { it.id == targetRouteId }
                    if (pendingRoute != null) {
                        Timber.tag(CAST_LOG_TAG).i("Selecting pending route %s after transfer back.", targetRouteId)
                        mediaRouter.selectRoute(pendingRoute)
                    } else {
                        Timber.tag(CAST_LOG_TAG).w("Pending route %s not found after transfer back. Resetting state.", targetRouteId)
                        pendingCastRouteId = null
                        _isCastConnecting.value = false
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
                _isCastConnecting.value = true
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                pendingCastRouteId = null
                _isCastConnecting.value = false
            }
            override fun onSessionEnding(session: CastSession) {
                Timber.tag(CAST_LOG_TAG).i("Cast session ending; keeping connecting flag=%s", _isCastConnecting.value)
            }
            override fun onSessionResuming(session: CastSession, sessionId: String) {
                _isCastConnecting.value = true
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                pendingCastRouteId = null
                _isCastConnecting.value = false
            }
        }
        sessionManager.addSessionManagerListener(castSessionManagerListener as SessionManagerListener<CastSession>, CastSession::class.java)
        _castSession.value = sessionManager.currentCastSession
        _isRemotePlaybackActive.value = _castSession.value != null
        _castSession.value?.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
        _castSession.value?.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)

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
                allSongs = dummyList,
                currentPlaybackQueue = dummyList,
                currentQueueSourceName = "Benchmark",
                currentPosition = 30000L
            )
        }

        _stablePlayerState.update {
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
        viewModelScope.launch {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastUpdateDay = Calendar.getInstance().apply { timeInMillis = lastUpdate }.get(
                Calendar.DAY_OF_YEAR)

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
                sortSongs(_playerUiState.value.currentSongSortOption, persist = false)

                _playerUiState.update { currentState ->
                    currentState.copy(
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
                sortAlbums(_playerUiState.value.currentAlbumSortOption, persist = false)
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
                
                // Fetch Deezer images for artists without images in background
                launch(Dispatchers.IO) {
                    val artistsWithoutImages = artistsList.filter { it.imageUrl.isNullOrEmpty() }
                    Log.d("PlayerViewModel", "Fetching Deezer images for ${artistsWithoutImages.size} artists...")
                    
                    for (artist in artistsWithoutImages) {
                        try {
                            val imageUrl = artistImageRepository.getArtistImageUrl(artist.name, artist.id)
                            if (!imageUrl.isNullOrEmpty()) {
                                // Update artist in the UI state
                                _playerUiState.update { currentState ->
                                    val updatedArtists = currentState.artists.map { a ->
                                        if (a.id == artist.id) a.copy(imageUrl = imageUrl) else a
                                    }
                                    currentState.copy(artists = updatedArtists.toImmutableList())
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("PlayerViewModel", "Failed to fetch image for ${artist.name}: ${e.message}")
                        }
                    }
                    Log.d("PlayerViewModel", "Deezer artist image fetch completed")
                }
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

                _masterAllSongs.value = allSongsList.toImmutableList()
                sortSongs(_playerUiState.value.currentSongSortOption, persist = false)

                _playerUiState.update { currentState ->
                    currentState.copy(
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
                sortAlbums(_playerUiState.value.currentAlbumSortOption, persist = false)
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

    fun loadFoldersFromRepository() {
        Log.d("PlayerViewModelPerformance", "loadFoldersFromRepository (All) called.")

        viewModelScope.launch {
            val functionStartTime = System.currentTimeMillis()
            Log.d("PlayerViewModelPerformance", "loadFoldersFromRepository (All) START")

            _playerUiState.update { it.copy(isLoadingLibraryCategories = true) }

            try {
                val repoCallFoldersStartTime = System.currentTimeMillis()
                val allFoldersList: List<MusicFolder> = musicRepository.getMusicFolders().first()
                val foldersLoadDuration = System.currentTimeMillis() - repoCallFoldersStartTime
                Log.d("PlayerViewModelPerformance", "musicRepository.getMusicFolders (All) took $foldersLoadDuration ms for ${allFoldersList.size} folders.")

                _playerUiState.update { currentState ->
                    currentState.copy(
                        musicFolders = allFoldersList.toImmutableList(),
                        isLoadingLibraryCategories = false
                    )
                }
                 Log.d("PlayerViewModelPerformance", "loadFoldersFromRepository (All) END. Total time: ${System.currentTimeMillis() - functionStartTime} ms. Folders loaded: ${allFoldersList.size}")
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error loading all folders from getMusicFolders", e)
                _playerUiState.update { it.copy(isLoadingLibraryCategories = false) }
            }
        }
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
        val castSession = _castSession.value
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
        val allSongs = playerUiState.value.allSongs.toList()
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
            val currentSong = _stablePlayerState.value.currentSong
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
        val contentUri = extras?.getString(EXTERNAL_EXTRA_CONTENT_URI)
            ?: mediaItem.localConfiguration?.uri?.toString()
            ?: return null

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_song_title)
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_artist)
        val album = extras?.getString(EXTERNAL_EXTRA_ALBUM)?.takeIf { it.isNotBlank() }
            ?: metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_album)
        val duration = extras?.getLong(EXTERNAL_EXTRA_DURATION)?.takeIf { it > 0 } ?: 0L
        val albumArt = extras?.getString(EXTERNAL_EXTRA_ALBUM_ART)
            ?: metadata.artworkUri?.toString()
        val genre = extras?.getString(EXTERNAL_EXTRA_GENRE)
        val trackNumber = extras?.getInt(EXTERNAL_EXTRA_TRACK) ?: 0
        val year = extras?.getInt(EXTERNAL_EXTRA_YEAR) ?: 0
        val dateAdded = extras?.getLong(EXTERNAL_EXTRA_DATE_ADDED)?.takeIf { it > 0 }
            ?: System.currentTimeMillis()
        val mimeType = extras?.getString(EXTERNAL_EXTRA_MIME_TYPE)?.takeIf { true }
            ?: "-"
        val bitrate = extras?.getInt(EXTERNAL_EXTRA_BITRATE)?.takeIf { it > 0 }
            ?: 0
        val sampleRate = extras?.getInt(EXTERNAL_EXTRA_SAMPLE_RATE)?.takeIf { it > 0 }
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
        _stablePlayerState.update { it.copy(repeatMode = mode) }

        val castSession = _castSession.value
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
        _stablePlayerState.update {
            it.copy(
                isShuffleEnabled = playerCtrl.shuffleModeEnabled,
                repeatMode = playerCtrl.repeatMode,
                isPlaying = playerCtrl.isPlaying
            )
        }

        updateCurrentPlaybackQueueFromPlayer(playerCtrl)

        playerCtrl.currentMediaItem?.let { mediaItem ->
            val song = resolveSongFromMediaItem(mediaItem)

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
                _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false) }
                _playerUiState.update { it.copy(currentPosition = 0L) }
            }
        }

        playerCtrl.addListener(object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                _trackVolume.value = volume
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _stablePlayerState.update { it.copy(isPlaying = isPlaying) }
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
                lyricsLoadingJob?.cancel()
                transitionSchedulerJob = viewModelScope.launch {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val activeEotSongId = EotStateHolder.eotTargetSongId.value
                        val previousSongId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                        if (_isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                            playerCtrl.seekTo(0L)
                            playerCtrl.pause()

                            val finishedSongTitle = _playerUiState.value.allSongs.find { it.id == previousSongId }?.title
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
                        _stablePlayerState.update {
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
                        if (!_isCastConnecting.value && !_isRemotePlaybackActive.value) {
                            lyricsLoadingJob?.cancel()
                            _stablePlayerState.update {
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
                    _stablePlayerState.update { it.copy(totalDuration = playerCtrl.duration.coerceAtLeast(0L)) }
                    listeningStatsTracker.updateDuration(playerCtrl.duration.coerceAtLeast(0L))
                    startProgressUpdates()
                }
                if (playbackState == Player.STATE_ENDED) {
                    listeningStatsTracker.finalizeCurrentSession()
                }
                if (playbackState == Player.STATE_IDLE && playerCtrl.mediaItemCount == 0) {
                    if (!_isCastConnecting.value && !_isRemotePlaybackActive.value) {
                        listeningStatsTracker.onPlaybackStopped()
                        lyricsLoadingJob?.cancel()
                        _stablePlayerState.update {
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
                    if (!_stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                _stablePlayerState.update { it.copy(repeatMode = repeatMode) }
                viewModelScope.launch { userPreferencesRepository.setRepeatMode(repeatMode) }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                transitionSchedulerJob?.cancel()
                updateCurrentPlaybackQueueFromPlayer(mediaController)
            }
        })
        Trace.endSection()
    }

    private fun createShuffledQueue(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return emptyList()

        val shuffledQueue = fisherYatesCopy(songs)
        val chosenSong = shuffledQueue.firstOrNull()
        Log.d(
            "ShuffleDebug",
            "createShuffledQueue called. Input size: ${songs.size}. Chosen first song: '${chosenSong?.title}'"
        )

        return shuffledQueue
    }

    private fun <T> fisherYatesCopy(source: List<T>, random: Random = Random.Default): List<T> {
        if (source.size <= 1) return source.toList()
        val mutable = source.toMutableList()
        for (i in mutable.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            if (i != j) {
                val tmp = mutable[i]
                mutable[i] = mutable[j]
                mutable[j] = tmp
            }
        }
        return mutable
    }

    private fun generateShuffleOrder(size: Int, anchorIndex: Int, random: Random = Random.Default): IntArray {
        if (size <= 1) return IntArray(size) { it }

        val clampedAnchor = anchorIndex.coerceIn(0, size - 1)
        val pool = IntArray(size - 1)
        var cursor = 0
        for (i in 0 until size) {
            if (i != clampedAnchor) {
                pool[cursor++] = i
            }
        }

        for (i in pool.lastIndex downTo 1) {
            val swapIndex = random.nextInt(i + 1)
            if (i != swapIndex) {
                val tmp = pool[i]
                pool[i] = pool[swapIndex]
                pool[swapIndex] = tmp
            }
        }

        val order = IntArray(size)
        var poolIndex = 0
        for (i in 0 until size) {
            order[i] = if (i == clampedAnchor) clampedAnchor else pool[poolIndex++]
        }
        return order
    }

    private fun buildMediaItemFromSong(song: Song): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.contentUriString.toUri())
            .setMediaMetadata(buildMediaMetadataForSong(song))
            .build()
    }

    private fun buildAnchoredShuffleQueue(
        currentQueue: List<Song>,
        anchorIndex: Int,
        random: Random = Random.Default
    ): List<Song> {
        if (currentQueue.size <= 1) return currentQueue.toList()
        val order = generateShuffleOrder(currentQueue.size, anchorIndex, random)
        return List(order.size) { idx -> currentQueue[order[idx]] }
    }

    private suspend fun rebuildPlayerQueue(
        targetQueue: List<Song>,
        targetIndex: Int,
        positionMs: Long
    ) {
        val enginePlayer = dualPlayerEngine.masterPlayer

        withContext(Dispatchers.Main) {
            val currentIndex = enginePlayer.currentMediaItemIndex
            val listSize = enginePlayer.mediaItemCount
            
            // If the player is empty or index is out of bounds, fall back to full reset
            if (listSize == 0 || currentIndex < 0 || currentIndex >= listSize) {
                val (mediaItems, safeIndex) = withContext(Dispatchers.Default) {
                    val items = targetQueue.map { buildMediaItemFromSong(it) }
                    val index = targetIndex.coerceIn(items.indices)
                    Pair(items, index)
                }
                enginePlayer.setMediaItems(mediaItems, safeIndex, positionMs)
                enginePlayer.prepare()
                return@withContext
            }

            // Optimization: Preserve the currently playing MediaItem instance to prevent audio glitches/re-buffering.
            // When we shuffle, we essentially just want to replace the neighbors of the current song.
            
            // Calculate ranges to replace
            // 1. Items AFTER the current song
            val itemsAfter = withContext(Dispatchers.Default) {
                if (targetIndex + 1 < targetQueue.size) {
                    targetQueue.subList(targetIndex + 1, targetQueue.size).map { buildMediaItemFromSong(it) }
                } else {
                    emptyList()
                }
            }
            
            // 2. Items BEFORE the current song
            val itemsBefore = withContext(Dispatchers.Default) {
                if (targetIndex > 0) {
                     targetQueue.subList(0, targetIndex).map { buildMediaItemFromSong(it) }
                } else {
                    emptyList()
                }
            }

            // Replace items AFTER current index first (indices > currentIndex)
            if (currentIndex + 1 < listSize) {
                enginePlayer.removeMediaItems(currentIndex + 1, listSize)
            }
            if (itemsAfter.isNotEmpty()) {
                enginePlayer.addMediaItems(currentIndex + 1, itemsAfter)
            }

            // Replace items BEFORE current index (indices < currentIndex)
            if (currentIndex > 0) {
                 enginePlayer.removeMediaItems(0, currentIndex)
            }
            if (itemsBefore.isNotEmpty()) {
                enginePlayer.addMediaItems(0, itemsBefore)
            }
            
            // Note: We intentionally DO NOT touch the item at 'currentIndex'.
            // This ensures playback continues seamlessly.
        }
    }

    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        viewModelScope.launch {
            transitionSchedulerJob?.cancel()
            // Store original queue order for unshuffling later
            _originalQueueOrder = songsToPlay.toList()
            _originalQueueName = queueName
            // Reset shuffle state when starting a new queue
            _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
            internalPlaySongs(songsToPlay, startSong, queueName, playlistId)
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
            _originalQueueOrder = songsToPlay.toList()
            _originalQueueName = queueName
            _stablePlayerState.update { it.copy(isShuffleEnabled = false) }

            internalPlaySongs(songsToPlay, startSong, queueName, playlistId)
            val isCastSessionActive = _castSession.value?.remoteMediaClient != null
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
            val externalResult = buildExternalSongFromUri(uri)
            if (externalResult == null) {
                sendToast(context.getString(R.string.external_playback_error))
                return@launch
            }

            transitionSchedulerJob?.cancel()

            val queueSongs = buildExternalQueue(externalResult, uri)
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

            _stablePlayerState.update { state ->
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

    private suspend fun buildExternalQueue(
        result: ExternalSongLoadResult,
        originalUri: Uri
    ): List<Song> {
        val continuation = loadAdditionalSongsFromFolder(result, originalUri)
        if (continuation.isEmpty()) {
            return listOf(result.song)
        }

        val queue = mutableListOf(result.song)
        continuation.forEach { song ->
            if (queue.none { it.id == song.id }) {
                queue.add(song)
            }
        }

        return queue
    }

    private suspend fun loadAdditionalSongsFromFolder(
        reference: ExternalSongLoadResult,
        originalUri: Uri
    ): List<Song> = withContext(Dispatchers.IO) {
        val relativePath = reference.relativePath
        val bucketId = reference.bucketId
        if (relativePath.isNullOrEmpty() && bucketId == null) {
            return@withContext emptyList()
        }

        val selection: String
        val selectionArgs: Array<String>
        if (bucketId != null) {
            selection = "${MediaStore.Audio.Media.BUCKET_ID} = ?"
            selectionArgs = arrayOf(bucketId.toString())
        } else {
            selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf(relativePath!!)
        }

        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        val siblings = mutableListOf<Pair<Uri, String?>>()
        try {
            resolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "LOWER(${MediaStore.Audio.Media.DISPLAY_NAME}) ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                if (idIndex != -1) {
                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idIndex)
                        val mediaUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
                        val siblingName = if (nameIndex != -1) cursor.getString(nameIndex) else null
                        siblings.add(mediaUri to siblingName)
                    }
                }
            }
        } catch (securityException: SecurityException) {
            Timber.w(securityException, "Unable to load sibling songs for uri: $originalUri")
            return@withContext emptyList()
        } catch (illegalArgumentException: IllegalArgumentException) {
            Timber.w(illegalArgumentException, "Invalid query while loading sibling songs for uri: $originalUri")
            return@withContext emptyList()
        }

        if (siblings.isEmpty()) return@withContext emptyList()

        val normalizedTargetUri = originalUri.toString()
        val normalizedDisplayName = reference.displayName?.lowercase()?.trim()

        val startIndex = siblings.indexOfFirst { (itemUri, displayName) ->
            itemUri == originalUri ||
                itemUri.toString() == normalizedTargetUri ||
                (normalizedDisplayName != null && displayName?.lowercase()?.trim() == normalizedDisplayName)
        }

        val candidates = if (startIndex != -1) {
            siblings.drop(startIndex + 1)
        } else {
            siblings.filterNot { (itemUri, displayName) ->
                itemUri == originalUri ||
                    itemUri.toString() == normalizedTargetUri ||
                    (normalizedDisplayName != null && displayName?.lowercase()?.trim() == normalizedDisplayName)
            }
        }

        if (candidates.isEmpty()) return@withContext emptyList()

        val resolved = mutableListOf<Song>()
        for ((candidateUri, _) in candidates) {
            val additional = buildExternalSongFromUri(candidateUri, captureFolderInfo = false)
            val song = additional?.song ?: continue
            if (song.id != reference.song.id) {
                resolved.add(song)
            }
        }

        resolved
    }

    fun showPlayer() {
        if (stablePlayerState.value.currentSong != null) {
            _isSheetVisible.value = true
        }
    }

    private suspend fun buildExternalSongFromUri(
        uri: Uri,
        captureFolderInfo: Boolean = true
    ): ExternalSongLoadResult? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        var displayName: String? = null
        var relativePath: String? = null
        var bucketId: Long? = null
        var storeTitle: String? = null
        var storeArtist: String? = null
        var storeAlbum: String? = null
        var storeDuration: Long? = null
        var storeTrack: Int? = null
        var storeYear: Int? = null
        var storeDateAddedSeconds: Long? = null

        val projection = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.BUCKET_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED
        )

        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) displayName = cursor.getString(displayNameIndex)

                    val relativePathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                    if (relativePathIndex != -1) relativePath = cursor.getString(relativePathIndex)

                    val bucketIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_ID)
                    if (bucketIdIndex != -1) bucketId = cursor.getLong(bucketIdIndex)

                    val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    if (titleIndex != -1) storeTitle = cursor.getString(titleIndex)

                    val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    if (artistIndex != -1) storeArtist = cursor.getString(artistIndex)

                    val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    if (albumIndex != -1) storeAlbum = cursor.getString(albumIndex)

                    val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    if (durationIndex != -1) storeDuration = cursor.getLong(durationIndex)

                    val trackIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                    if (trackIndex != -1) storeTrack = cursor.getInt(trackIndex)

                    val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                    if (yearIndex != -1) storeYear = cursor.getInt(yearIndex)

                    val dateAddedIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    if (dateAddedIndex != -1) storeDateAddedSeconds = cursor.getLong(dateAddedIndex)
                }
            }
        } catch (exception: Exception) {
            Timber.w(exception, "Failed querying metadata for external uri: $uri")
        }

        val metadataRetriever = MediaMetadataRetriever()
        return@withContext try {
            metadataRetriever.setDataSource(context, uri)

            var metadataTitle = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
            var metadataArtist = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            var metadataAlbum = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
            var metadataDuration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            var metadataTrack = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')
                ?.toIntOrNull()
            var metadataYear = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.toIntOrNull()
            val mimeType = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val bitrate = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            val sampleRate = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            var genre = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            var embeddedArt = metadataRetriever.embeddedPicture?.takeIf { it.isNotEmpty() }
            var embeddedArtMimeType: String? = null

            if (embeddedArt != null) {
                if (isValidImageData(embeddedArt)) {
                    embeddedArtMimeType = guessImageMimeType(embeddedArt)
                } else {
                    Timber.w("MediaMetadataRetriever returned invalid embedded art for uri: $uri; falling back")
                    embeddedArt = null
                }
            }

            if (metadataTitle == null || metadataArtist == null || metadataAlbum == null ||
                metadataDuration == null || metadataDuration <= 0 ||
                metadataTrack == null || metadataYear == null ||
                embeddedArt == null || genre.isNullOrBlank()
            ) {
                AudioMetadataReader.read(context, uri)?.let { fallback ->
                    if (metadataTitle == null) metadataTitle = fallback.title
                    if (metadataArtist == null) metadataArtist = fallback.artist
                    if (metadataAlbum == null) metadataAlbum = fallback.album
                    if (genre.isNullOrBlank()) genre = fallback.genre
                    if (metadataDuration == null || metadataDuration!! <= 0) {
                        metadataDuration = fallback.durationMs
                    }
                    if (metadataTrack == null) metadataTrack = fallback.trackNumber
                    if (metadataYear == null) metadataYear = fallback.year
                    if (embeddedArt == null) {
                        fallback.artwork?.let { artwork ->
                            if (artwork.bytes.isNotEmpty() && isValidImageData(artwork.bytes)) {
                                embeddedArt = artwork.bytes
                                embeddedArtMimeType = artwork.mimeType
                                    ?: guessImageMimeType(artwork.bytes)
                            } else if (artwork.bytes.isNotEmpty()) {
                                Timber.w("JAudioTagger returned invalid artwork for uri: $uri")
                            }
                        }
                    }
                }
            }

            val albumArtUriString = embeddedArt?.let { artBytes ->
                persistExternalAlbumArt(uri, artBytes, embeddedArtMimeType)
            }

            val duration = metadataDuration
                ?: storeDuration?.takeIf { it > 0 }
                ?: 0L

            val trackNumber = metadataTrack
                ?: storeTrack?.takeIf { it > 0 }?.let { track ->
                    if (track > 1000) track % 1000 else track
                }
                ?: 0

            val year = metadataYear ?: storeYear ?: 0

            val dateAdded = storeDateAddedSeconds?.takeIf { it > 0 }
                ?.let { TimeUnit.SECONDS.toMillis(it) }
                ?: System.currentTimeMillis()

            val song = Song(
                id = "${EXTERNAL_MEDIA_ID_PREFIX}${uri}",
                title = metadataTitle
                    ?: storeTitle?.takeIf { it.isNotBlank() }
                    ?: displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                    ?: displayName?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.unknown_song_title),
                artist = metadataArtist
                    ?: storeArtist?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.unknown_artist),
                artistId = -1L,
                album = metadataAlbum
                    ?: storeAlbum?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.unknown_album),
                albumId = -1L,
                path = uri.toString(),
                contentUriString = uri.toString(),
                albumArtUriString = albumArtUriString,
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

            ExternalSongLoadResult(
                song = song,
                relativePath = if (captureFolderInfo) relativePath else null,
                bucketId = if (captureFolderInfo) bucketId else null,
                displayName = displayName
            )
        } catch (error: Exception) {
            Timber.e(error, "Failed to read metadata for external uri: $uri")
            null
        } finally {
            runCatching { metadataRetriever.release() }
        }
    }

    private fun persistExternalAlbumArt(uri: Uri, data: ByteArray, mimeType: String? = null): String? {
        return runCatching {
            if (!isValidImageData(data)) {
                throw IllegalArgumentException("Invalid embedded album art for uri: $uri")
            }
            val directory = File(context.cacheDir, "external_artwork")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val resolvedMimeType = mimeType ?: guessImageMimeType(data)
            val extension = imageExtensionFromMimeType(resolvedMimeType) ?: "jpg"
            val fileNamePrefix = "art_${uri.toString().hashCode()}."
            directory.listFiles { file ->
                file.name.startsWith(fileNamePrefix)
            }?.forEach { it.delete() }
            val fileName = "$fileNamePrefix$extension"
            val file = File(directory, fileName)
            file.outputStream().use { output ->
                output.write(data)
            }
            Uri.fromFile(file).toString()
        }.onFailure { throwable ->
            Timber.w(throwable, "Unable to persist album art for external uri: $uri")
        }.getOrNull()
    }

    private suspend fun internalPlaySongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        // Update dynamic shortcut for last played playlist
        if (playlistId != null && queueName != "None") {
            appShortcutManager.updateLastPlaylistShortcut(playlistId, queueName)
        }
        
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            if (!ensureHttpServerRunning()) return

            val serverAddress = MediaFileHttpServerService.serverAddress ?: return

            val startIndex = songsToPlay.indexOf(startSong).coerceAtLeast(0)
            val repeatMode = _stablePlayerState.value.repeatMode

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
            _stablePlayerState.update {
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
                    _stablePlayerState.update {
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
            .setMediaMetadata(buildMediaMetadataForSong(song))
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

    private fun buildMediaMetadataForSong(song: Song): MediaMetadata {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.album)

        song.albumArtUriString?.toUri()?.let { artworkUri ->
            metadataBuilder.setArtworkUri(artworkUri)
        }

        val extras = Bundle().apply {
            putBoolean(EXTERNAL_EXTRA_FLAG, song.id.startsWith(EXTERNAL_MEDIA_ID_PREFIX))
            putString(EXTERNAL_EXTRA_ALBUM, song.album)
            putLong(EXTERNAL_EXTRA_DURATION, song.duration)
            putString(EXTERNAL_EXTRA_CONTENT_URI, song.contentUriString)
            song.albumArtUriString?.let { putString(EXTERNAL_EXTRA_ALBUM_ART, it) }
            song.genre?.let { putString(EXTERNAL_EXTRA_GENRE, it) }
            putInt(EXTERNAL_EXTRA_TRACK, song.trackNumber)
            putInt(EXTERNAL_EXTRA_YEAR, song.year)
            putLong(EXTERNAL_EXTRA_DATE_ADDED, song.dateAdded)
            putString(EXTERNAL_EXTRA_MIME_TYPE, song.mimeType)
            putInt(EXTERNAL_EXTRA_BITRATE, song.bitrate ?: 0)
            putInt(EXTERNAL_EXTRA_SAMPLE_RATE, song.sampleRate ?: 0)
        }

        metadataBuilder.setExtras(extras)
        return metadataBuilder.build()
    }

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
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient
            val newRepeatMode = if (remoteMediaClient?.mediaStatus?.getQueueRepeatMode() == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE) {
                MediaStatus.REPEAT_MODE_REPEAT_ALL
            } else {
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
            }
            castPlayer?.setRepeatMode(newRepeatMode)
        } else {
            viewModelScope.launch {
                val player = mediaController ?: return@launch
                val currentQueue = _playerUiState.value.currentPlaybackQueue.toList()
                if (currentQueue.isEmpty()) return@launch

                val currentSong = currentSongOverride
                    ?: _stablePlayerState.value.currentSong
                    ?: player.currentMediaItem?.let { resolveSongFromMediaItem(it) }
                    ?: currentQueue.firstOrNull()
                    ?: return@launch
                val isCurrentlyShuffled = _stablePlayerState.value.isShuffleEnabled

                if (!isCurrentlyShuffled) {
                    Log.d("ShuffleDebug", "Enabling shuffle - current song: ${currentSong.title}")
                    // Store original order if not already stored
                    if (_originalQueueOrder.isEmpty()) {
                        _originalQueueOrder = currentQueue.toList()
                        _originalQueueName = _playerUiState.value.currentQueueSourceName
                    }

                    val currentIndex = player.currentMediaItemIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))
                    val currentPosition = player.currentPosition
                    val currentMediaId = player.currentMediaItem?.mediaId

                    val shuffledQueue = withContext(Dispatchers.Default) {
                        buildAnchoredShuffleQueue(currentQueue, currentIndex)
                    }

                    val targetIndex = shuffledQueue.indexOfFirst { it.id == currentMediaId }
                        .takeIf { it != -1 } ?: currentIndex

                    rebuildPlayerQueue(shuffledQueue, targetIndex, currentPosition)

                    _playerUiState.update { it.copy(currentPlaybackQueue = shuffledQueue.toImmutableList()) }
                    _stablePlayerState.update { it.copy(isShuffleEnabled = true) }
                    Log.d("ShuffleDebug", "Shuffle enabled - queue size: ${shuffledQueue.size}")
                } else {
                    Log.d("ShuffleDebug", "Disabling shuffle - restoring original order")
                    
                    if (_originalQueueOrder.isEmpty()) {
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        return@launch
                    }

                    val originalQueue = _originalQueueOrder
                    val currentPosition = player.currentPosition
                    val currentSongId = currentSong.id
                    val originalIndex = withContext(Dispatchers.Default) {
                        originalQueue.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }
                    }

                    if (originalIndex == null) {
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        return@launch
                    }

                    rebuildPlayerQueue(originalQueue, originalIndex, currentPosition)

                    _playerUiState.update { 
                        it.copy(
                            currentPlaybackQueue = originalQueue.toImmutableList(),
                            currentQueueSourceName = _originalQueueName
                        ) 
                    }
                    _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                    Log.d("ShuffleDebug", "Shuffle disabled - restored to original order, current at index $originalIndex")
                }
            }
        }
    }

    fun cycleRepeatMode() {
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient
            val currentRepeatMode = remoteMediaClient?.mediaStatus?.getQueueRepeatMode() ?: MediaStatus.REPEAT_MODE_REPEAT_OFF
            val newMode = when (currentRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_OFF -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                MediaStatus.REPEAT_MODE_REPEAT_ALL -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                // If user cycles repeat while shuffling, turn everything off.
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castPlayer?.setRepeatMode(newMode)
            val mappedLocalMode = when (newMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            viewModelScope.launch { userPreferencesRepository.setRepeatMode(mappedLocalMode) }
            _stablePlayerState.update { it.copy(repeatMode = mappedLocalMode) }
        } else {
            val currentMode = _stablePlayerState.value.repeatMode
            val newMode = when (currentMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            mediaController?.repeatMode = newMode
            viewModelScope.launch { userPreferencesRepository.setRepeatMode(newMode) }
            _stablePlayerState.update { it.copy(repeatMode = newMode) }
        }
    }

    fun repeatSingle(){
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val newMode = MediaStatus.REPEAT_MODE_REPEAT_SINGLE;
            castPlayer?.setRepeatMode(newMode)
        } else {
            val newMode = Player.REPEAT_MODE_ONE
            mediaController?.repeatMode = newMode
        }
        viewModelScope.launch { userPreferencesRepository.setRepeatMode(Player.REPEAT_MODE_ONE) }
        _stablePlayerState.update { it.copy(repeatMode = Player.REPEAT_MODE_ONE) }
    }

    fun repeatOff(){
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val newMode = MediaStatus.REPEAT_MODE_REPEAT_OFF;
            castPlayer?.setRepeatMode(newMode)
        } else {
            val newMode = Player.REPEAT_MODE_OFF
            mediaController?.repeatMode = newMode
        }
        viewModelScope.launch { userPreferencesRepository.setRepeatMode(Player.REPEAT_MODE_OFF) }
        _stablePlayerState.update { it.copy(repeatMode = Player.REPEAT_MODE_OFF) }
    }

    fun toggleFavorite() {
        _stablePlayerState.value.currentSong?.id?.let { songId ->
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
            if (currentSession?.songId == song.id) {
                mediaController?.pause()
                mediaController?.stop()
                mediaController?.clearMediaItems()
                currentSession
                _stablePlayerState.update {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        totalDuration = 0L
                    )
                }
            }
            val fileInfo = FileDeletionUtils.getFileInfo(song.path)
            if (fileInfo.exists && fileInfo.canWrite) {
                val success = FileDeletionUtils.deleteFile(context, song.path)
                if (success) {
                    _toastEvents.emit("File deleted")
                    removeFromMediaControllerQueue(song.id)
                    removeSong(song)
                    onResult(true)
                } else {
                    _toastEvents.emit("Can't delete the file")
                }
            } else
                _toastEvents.emit("File does not exist or not permitted")
            onResult(false)
        }
    }

    suspend fun removeSong(song: Song) {
        toggleFavoriteSpecificSong(song, true)
        _playerUiState.update { currentState ->
            currentState.copy(
                allSongs = currentState.allSongs.filter { it.id != song.id }.toImmutableList(),
                currentPosition = 0L,
                currentPlaybackQueue = currentState.currentPlaybackQueue.filter { it.id != song.id }.toImmutableList(),
                currentQueueSourceName = ""
            )
        }
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
        val castSession = _castSession.value
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
                        val startSong = _stablePlayerState.value.currentSong ?: queue.first()
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
                        val currentSong = _stablePlayerState.value.currentSong
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
                            _playerUiState.value.allSongs.isNotEmpty() -> {
                                loadAndPlaySong(_playerUiState.value.allSongs.first())
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
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            isRemotelySeeking.value = true

            castPlayer?.seek(position)

            // We set isRemotelySeeking to false after a delay or in a callback ideally,
            // but CastPlayer.seek now forces status update.
            // We can rely on remoteProgressListener logic to respect isRemotelySeeking.
            // For now, let's reset it after a short delay or when next status comes?
            // Existing logic had a callback. CastPlayer.seek doesn't expose one yet.
            // I'll update it here to reset after a small delay to simulate callback/wait for status
            viewModelScope.launch {
                delay(500)
                isRemotelySeeking.value = false
            }

            // Optimistically update the UI state for responsiveness
            _remotePosition.value = position
            _playerUiState.update { it.copy(currentPosition = position) }
        } else {
            mediaController?.seekTo(position)
            _playerUiState.update { it.copy(currentPosition = position) }
        }
    }

    fun nextSong() {
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val queue = _playerUiState.value.currentPlaybackQueue
            val currentId = _stablePlayerState.value.currentSong?.id
            val currentIndex = queue.indexOfFirst { it.id == currentId }
            val target = when {
                currentIndex >= 0 && currentIndex + 1 < queue.size -> queue[currentIndex + 1]
                queue.isNotEmpty() -> queue.last()
                else -> null
            }
            markPendingRemoteSong(target)
            castPlayer?.next()
        } else {
            mediaController?.let {
                if (it.hasNextMediaItem()) {
                    it.seekToNextMediaItem()
                    it.play()
                }
            }
        }
    }

    fun previousSong() {
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val queue = _playerUiState.value.currentPlaybackQueue
            val currentId = _stablePlayerState.value.currentSong?.id
            val currentIndex = queue.indexOfFirst { it.id == currentId }
            val target = when {
                currentIndex > 0 -> queue[currentIndex - 1]
                queue.isNotEmpty() -> queue.first()
                else -> null
            }
            markPendingRemoteSong(target)
            castPlayer?.previous()
        } else {
            mediaController?.let { controller ->
                if (controller.currentPosition > 10000 && controller.isCurrentMediaItemSeekable) { // 10 segundos
                    controller.seekTo(0)
                } else {
                    controller.seekToPreviousMediaItem()
                }
            }
        }
    }

    private fun startProgressUpdates() {
        if (_castSession.value != null) return

        stopProgressUpdates()
        progressJob = viewModelScope.launch {
            var lastPublishedPosition = Long.MIN_VALUE
            while (isActive) {
                if (_castSession.value != null) break
                val controller = mediaController ?: break

                val position = controller.currentPosition.coerceAtLeast(0L)
                listeningStatsTracker.onProgress(position, controller.isPlaying)
                if (position != lastPublishedPosition) {
                    _playerUiState.update { it.copy(currentPosition = position) }
                    lastPublishedPosition = position
                }

                val isActivelyPlaying = controller.isPlaying
                val shouldKeepPolling = isActivelyPlaying || controller.playWhenReady
                if (!shouldKeepPolling) break

                val delayMillis = if (isActivelyPlaying || controller.playWhenReady) 200L else 500L
                delay(delayMillis)
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
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        val sortedSongs = when (sortOption) {
            SortOption.SongTitleAZ -> _masterAllSongs.value.sortedBy { it.title.lowercase() }
            SortOption.SongTitleZA -> _masterAllSongs.value.sortedByDescending { it.title.lowercase() }
            SortOption.SongArtist -> _masterAllSongs.value.sortedBy { it.artist.lowercase() }
            SortOption.SongAlbum -> _masterAllSongs.value.sortedBy { it.album.lowercase() }
            SortOption.SongDateAdded -> _masterAllSongs.value.sortedByDescending { it.dateAdded }
            SortOption.SongDuration -> _masterAllSongs.value.sortedBy { it.duration }
            else -> _masterAllSongs.value
        }.toImmutableList()
        _playerUiState.update {
            it.copy(
                allSongs = sortedSongs,
                currentSongSortOption = sortOption
            )
        }

        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setSongsSortOption(sortOption.storageKey)
            }
        }
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        val sortedAlbums = when (sortOption) {
            SortOption.AlbumTitleAZ -> _playerUiState.value.albums.sortedBy { it.title.lowercase() }
            SortOption.AlbumTitleZA -> _playerUiState.value.albums.sortedByDescending { it.title.lowercase() }
            SortOption.AlbumArtist -> _playerUiState.value.albums.sortedBy { it.artist.lowercase() }
            SortOption.AlbumReleaseYear -> _playerUiState.value.albums.sortedByDescending { it.year }
            SortOption.AlbumSizeAsc -> _playerUiState.value.albums.sortedWith(compareBy<Album> { it.songCount }.thenBy { it.title.lowercase() })
            SortOption.AlbumSizeDesc -> _playerUiState.value.albums.sortedWith(compareByDescending<Album> { it.songCount }.thenBy { it.title.lowercase() })
            else -> _playerUiState.value.albums
        }.toImmutableList()
        _playerUiState.update {
            it.copy(
                albums = sortedAlbums,
                currentAlbumSortOption = sortOption
            )
        }

        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setAlbumsSortOption(sortOption.storageKey)
            }
        }
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        val sortedArtists = when (sortOption) {
            SortOption.ArtistNameAZ -> _playerUiState.value.artists.sortedBy { it.name.lowercase() }
            SortOption.ArtistNameZA -> _playerUiState.value.artists.sortedByDescending { it.name.lowercase() }
            else -> _playerUiState.value.artists
        }.toImmutableList()
        _playerUiState.update {
            it.copy(
                artists = sortedArtists,
                currentArtistSortOption = sortOption
            )
        }

        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setArtistsSortOption(sortOption.storageKey)
            }
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        _playerUiState.update { it.copy(currentFavoriteSortOption = sortOption) }
        _currentFavoriteSortOptionStateFlow.value = sortOption // Actualizar el StateFlow dedicado
        // The actual sorting is handled by the 'favoriteSongs' StateFlow reacting to 'currentFavoriteSortOptionStateFlow'.
        if (persist) {
            viewModelScope.launch {
                userPreferencesRepository.setLikedSongsSortOption(sortOption.storageKey)
            }
        }
    }

    fun sortFolders(sortOption: SortOption) {
        val sortedFolders = when (sortOption) {
            SortOption.FolderNameAZ -> _playerUiState.value.musicFolders.sortedBy { it.name.lowercase() }
            SortOption.FolderNameZA -> _playerUiState.value.musicFolders.sortedByDescending { it.name.lowercase() }
            else -> _playerUiState.value.musicFolders
        }.toImmutableList()
        _playerUiState.update {
            it.copy(
                musicFolders = sortedFolders,
                currentFolderSortOption = sortOption
            )
        }
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
                val candidatePool = dailyMixManager.generateDailyMix(
                    allSongs = allSongsFlow.value,
                    favoriteSongIds = favoriteSongIds.value,
                    limit = 120
                )

                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongsFlow.value,
                    minLength = minLength,
                    maxLength = maxLength,
                    candidateSongs = candidatePool
                )

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

    fun regenerateDailyMixWithPrompt(prompt: String) {
        viewModelScope.launch {
            if (prompt.isBlank()) {
                sendToast("Escribe una idea para tu Daily Mix")
                return@launch
            }

            _isGeneratingAiPlaylist.value = true
            _aiError.value = null

            try {
                val desiredSize = _dailyMixSongs.value.size.takeIf { it > 0 } ?: 25
                val minLength = (desiredSize * 0.6).toInt().coerceAtLeast(10)
                val maxLength = desiredSize.coerceAtLeast(20)
                val candidatePool = dailyMixManager.generateDailyMix(
                    allSongs = allSongsFlow.value,
                    favoriteSongIds = favoriteSongIds.value,
                    limit = 100
                )

                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongsFlow.value,
                    minLength = minLength,
                    maxLength = maxLength,
                    candidateSongs = candidatePool
                )

                result.onSuccess { generatedSongs ->
                    if (generatedSongs.isNotEmpty()) {
                        val updatedMix = generatedSongs.toImmutableList()
                        _dailyMixSongs.value = updatedMix
                        userPreferencesRepository.saveDailyMixSongIds(updatedMix.map { it.id })
                        sendToast("Daily Mix actualizado con IA")
                    } else {
                        sendToast("La IA no encontró canciones para este mix")
                    }
                }.onFailure { error ->
                    _aiError.value = error.message
                    sendToast("No se pudo actualizar: ${error.message}")
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

    fun selectRoute(route: MediaRouter.RouteInfo) {
        val selectedRouteId = _selectedRoute.value?.id
        val isCastRoute = route.isCastRoute() && !route.isDefault
        val isSwitchingBetweenRemotes = isCastRoute &&
            (isRemotePlaybackActive.value || _isCastConnecting.value) &&
            selectedRouteId != null &&
            selectedRouteId != route.id

        if (isSwitchingBetweenRemotes) {
            pendingCastRouteId = route.id
            _isCastConnecting.value = true
            sessionManager.currentCastSession?.let { sessionManager.endCurrentSession(true) }
        } else {
            pendingCastRouteId = null
        }
        mediaRouter.selectRoute(route)
    }

    fun disconnect(resetConnecting: Boolean = true) {
        val start = SystemClock.elapsedRealtime()
        pendingCastRouteId = null
        val wasRemote = _isRemotePlaybackActive.value
        if (wasRemote) {
            Timber.tag(CAST_LOG_TAG).i(
                "Manual disconnect requested; marking castConnecting=true until session ends. mainThread=%s",
                Looper.myLooper() == Looper.getMainLooper()
            )
            _isCastConnecting.value = true
        }
        mediaRouter.selectRoute(mediaRouter.defaultRoute)
        _isRemotePlaybackActive.value = false
        if (resetConnecting && !wasRemote) {
            _isCastConnecting.value = false
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
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        wifiStateReceiver?.let { context.unregisterReceiver(it) }
        bluetoothStateReceiver?.let { context.unregisterReceiver(it) }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        sessionManager.removeSessionManagerListener(castSessionManagerListener as SessionManagerListener<CastSession>, CastSession::class.java)
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

        // Use AlarmManager for reliability
        val intent = Intent(context, com.theveloper.pixelplay.data.service.SleepTimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endTime,
                        pendingIntent
                    )
                } else {
                    // Fallback or just set inexact/normal exact?
                    // For now, let's try setExactAndAllowWhileIdle if we can, or just setExact.
                    // If permission missing, it might crash if we call setExact...
                    // But the user should grant it. If not, try setWindow or similar?
                    // Actually setExactAndAllowWhileIdle requires permission ONLY if targeting 33+ (Android 13) and using USE_EXACT_ALARM or SCHEDULE_EXACT_ALARM.
                    // We added SCHEDULE_EXACT_ALARM.
                    // If we don't have it, we should probably warn or just use setAndAllowWhileIdle (inexact).
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        endTime,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    endTime,
                    pendingIntent
                )
            } else {
                 alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    endTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to schedule exact alarm for sleep timer")
             // Fallback to inexact?
             alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                endTime,
                pendingIntent
            )
        }

        // Keep the coroutine for UI updates (countdown) if we want, but the actual trigger is the alarm.
        // However, the original code used the coroutine to TRIGGER the pause.
        // We will keep the coroutine to update the UI or valididity check, but the actual pause comes from the broadcast.
        // Actually, let's just use the alarm for the trigger. The coroutine was:
        // sleepTimerJob = viewModelScope.launch { delay... pause... }
        // We can remove the pause logic from here or keep it as a backup?
        // Better to remove it to avoid double pause or confusion.
        // But we might want to clear the state when the time comes.
        // Let's launch a coroutine just to clear the UI state when it finishes?
        // Or rely on the receiver triggering the service which pauses -> updates playback state.
        // The UI shows "timer active". We need to clear that when it fires.
        // We can listen to the pause event.

        viewModelScope.launch { _toastEvents.emit("Timer set for $durationMinutes minutes.") }
    }


    fun playCounted(count: Int) {
        val args = Bundle().apply { putInt("count", count) }

        mediaController?.sendCustomCommand(
            SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY, Bundle.EMPTY),
            args
        )
    }

    fun cancelCountedPlay(){
        val args = Bundle()
        _playCount.value = 1f
        mediaController?.sendCustomCommand(
            SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_COUNTED_PLAY, Bundle.EMPTY), args
        )
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

        // Cancel Alarm
        val intent = Intent(context, com.theveloper.pixelplay.data.service.SleepTimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)


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
        val currentSong = _stablePlayerState.value.currentSong ?: return

        lyricsLoadingJob?.cancel()
        val targetSongId = currentSong.id

        lyricsLoadingJob = viewModelScope.launch {
            _stablePlayerState.update { state ->
                if (state.currentSong?.id != targetSongId) state
                else state.copy(isLoadingLyrics = true, lyrics = null)
            }

            val fetchedLyrics = try {
                musicRepository.getLyrics(currentSong)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                null
            }

            _stablePlayerState.update { state ->
                if (state.currentSong?.id != targetSongId) state
                else state.copy(isLoadingLyrics = false, lyrics = fetchedLyrics)
            }
        }
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
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Starting editSongMetadata for song: ${song.title} (${song.contentUriString})")
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: New metadata - title=$newTitle, artist=$newArtist, album=$newAlbum")
            val previousAlbumArt = song.albumArtUriString
            val trimmedLyrics = newLyrics.trim()
            val normalizedLyrics = trimmedLyrics.takeIf { it.isNotBlank() }
            val parsedLyrics = normalizedLyrics?.let { LyricsUtils.parseLyrics(it) }
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Calling songMetadataEditor.editSongMetadata...")
            val result = withContext(Dispatchers.IO) {
                songMetadataEditor.editSongMetadata(
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    newGenre = newGenre,
                    newLyrics = trimmedLyrics,
                    newTrackNumber = newTrackNumber,
                    coverArtUpdate = coverArtUpdate,
                    songId = song.id.toLong(),
                )
            }
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Result received - success=${result.success}")

            if (result.success) {
                val refreshedAlbumArtUri = result.updatedAlbumArtUri ?: song.albumArtUriString
                invalidateCoverArtCaches(previousAlbumArt, refreshedAlbumArtUri)
                withContext(Dispatchers.IO) {
                    if (normalizedLyrics != null) {
                        musicRepository.updateLyrics(song.id.toLong(), normalizedLyrics)
                    } else {
                        musicRepository.resetLyrics(song.id.toLong())
                    }
                }
                val updatedSong = song.copy(
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    genre = newGenre,
                    lyrics = normalizedLyrics,
                    trackNumber = newTrackNumber,
                    albumArtUriString = refreshedAlbumArtUri,
                )

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

                // Manually update the song in the UI state
                val currentSongs = _playerUiState.value.allSongs.toMutableList()
                val index = currentSongs.indexOfFirst { it.id == song.id }
                if (index != -1) {
                    currentSongs[index] = updatedSong
                    _playerUiState.update { it.copy(allSongs = currentSongs.toImmutableList()) }
                }

                _masterAllSongs.update { songs ->
                    songs.map { existing ->
                        if (existing.id == song.id) updatedSong else existing
                    }.toImmutableList()
                }

                if (_stablePlayerState.value.currentSong?.id == song.id) {
                    _stablePlayerState.update {
                        it.copy(
                            currentSong = updatedSong,
                            lyrics = parsedLyrics
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
        return aiMetadataGenerator.generate(song, fields)
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
        _stablePlayerState.update { state ->
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
            _lyricsSearchUiState.value = LyricsSearchUiState.Loading
            if (currentSong != null) {
                if (forcePickResults) {
                    musicRepository.searchRemoteLyrics(currentSong)
                        .onSuccess { (query, results) ->
                            _lyricsSearchUiState.value = LyricsSearchUiState.PickResult(query, results)
                        }
                        .onFailure { error ->
                            _lyricsSearchUiState.value = if (error is NoLyricsFoundException) {
                                LyricsSearchUiState.Error(context.getString(R.string.lyrics_not_found), error.query)
                            } else {
                                LyricsSearchUiState.Error(error.message ?: "Unknown error")
                            }
                        }
                } else {
                    musicRepository.getLyricsFromRemote(currentSong)
                        .onSuccess { (lyrics, rawLyrics) -> // Deconstruct the pair
                            _lyricsSearchUiState.value = LyricsSearchUiState.Success(lyrics)
                            val updatedSong = currentSong.copy(lyrics = rawLyrics)
                            updateSongInStates(updatedSong, lyrics)
                        }
                        .onFailure { error ->
                            if (error is NoLyricsFoundException) {
                                musicRepository.searchRemoteLyrics(currentSong)
                                    .onSuccess { (query, results) ->
                                        _lyricsSearchUiState.value = LyricsSearchUiState.PickResult(query, results)
                                    }
                                    .onFailure { searchError ->
                                        _lyricsSearchUiState.value = if (searchError is NoLyricsFoundException) {
                                            LyricsSearchUiState.Error(
                                                context.getString(R.string.lyrics_not_found),
                                                searchError.query
                                            )
                                        } else {
                                            LyricsSearchUiState.Error(searchError.message ?: "Unknown error")
                                        }
                                    }
                            } else {
                                _lyricsSearchUiState.value = LyricsSearchUiState.Error(error.message ?: "Unknown error")
                            }
                        }
                }
            } else {
                _lyricsSearchUiState.value = LyricsSearchUiState.Error("No song is currently playing.")
            }
        }
    }

    fun acceptLyricsSearchResultForCurrentSong(result: LyricsSearchResult) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        _lyricsSearchUiState.value = LyricsSearchUiState.Success(result.lyrics)

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
            _stablePlayerState.update { state -> state.copy(lyrics = null) }
            // loadLyricsForCurrentSong()
        }
    }

    fun resetAllLyrics() {
        resetLyricsSearchState()
        viewModelScope.launch {
            musicRepository.resetAllLyrics()
            _stablePlayerState.update { state -> state.copy(lyrics = null) }
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
            val currentSong = _stablePlayerState.value.currentSong
            if (currentSong != null && currentSong.id.toLong() == songId) {
                val updatedSong = currentSong.copy(lyrics = lyricsContent)
                val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
                updateSongInStates(updatedSong, parsedLyrics)
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

    private fun onBlockedDirectoriesChanged() {
        viewModelScope.launch {
            musicRepository.invalidateCachesDependentOnAllowedDirectories()
            resetAndLoadInitialData("Blocked directories changed")
        }
    }
}
