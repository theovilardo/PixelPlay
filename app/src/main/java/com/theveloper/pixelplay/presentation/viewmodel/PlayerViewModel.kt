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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.Timeline
import androidx.media3.session.SessionCommand
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
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.NoLyricsFoundException
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.service.player.CastPlayer
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.service.http.MediaFileHttpServerService
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.ArrayDeque
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.collections.map

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

    val carouselStyle: StateFlow<String> = userPreferencesRepository.carouselStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CarouselStyle.ONE_PEEK
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

    private val _playCount = MutableStateFlow<Float>(1f)
    val playCount: StateFlow<Float> = _playCount.asStateFlow()

    private val _lyricsSearchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val lyricsSearchUiState = _lyricsSearchUiState.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var eotSongMonitorJob: Job? = null
    private var countedMediaListener: Player.Listener? = null
    private var countedOriginalSongId: String? = null

    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

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
    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val mediaRouter: MediaRouter
    private val mediaRouterCallback: MediaRouter.Callback
    private val connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val bluetoothAdapter: BluetoothAdapter?
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private val sessionManager: SessionManager = CastContext.getSharedInstance(context).sessionManager
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private val _castSession = MutableStateFlow<CastSession?>(null)
    private var castPlayer: CastPlayer? = null
    private val _isRemotePlaybackActive = MutableStateFlow(false)
    val isRemotePlaybackActive: StateFlow<Boolean> = _isRemotePlaybackActive.asStateFlow()
    private val _remotePosition = MutableStateFlow(0L)
    val remotePosition: StateFlow<Long> = _remotePosition.asStateFlow()
    private val _trackVolume = MutableStateFlow(1.0f)
    val trackVolume: StateFlow<Float> = _trackVolume.asStateFlow()
    private val isRemotelySeeking = MutableStateFlow(false)
    private var remoteMediaClientCallback: RemoteMediaClient.Callback? = null
    private var remoteProgressListener: RemoteMediaClient.ProgressListener? = null

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

    private val colorSchemeRequestChannel = Channel<String>(Channel.UNLIMITED)
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

    private val _currentFavoriteSortOptionStateFlow = MutableStateFlow<SortOption>(SortOption.LikedSongDateLiked) // Default aligned with LibraryTabId default.
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

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    private var dailyMixJob: Job? = null

    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
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

    init {
        Log.i("PlayerViewModel", "init started.")

        // Cast initialization if already connected
        val currentSession = sessionManager.currentCastSession
        if (currentSession != null) {
            castPlayer = CastPlayer(currentSession)
            _isRemotePlaybackActive.value = true
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
                // Execute any pending action that was queued while the controller was connecting
                pendingPlaybackAction?.invoke()
                pendingPlaybackAction = null
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
            }
        }, ContextCompat.getMainExecutor(context))

        mediaRouter = MediaRouter.getInstance(context)
        val mediaRouteSelector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()

        mediaRouterCallback = object : MediaRouter.Callback() {
            private fun updateRoutes(router: MediaRouter) {
                val routes = router.routes.filter {
                    it.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK) ||
                    it.supportsControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                }.distinctBy { it.id }
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
        _castRoutes.value = mediaRouter.routes.filter {
            it.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK) ||
            it.supportsControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        }.distinctBy { it.id }
        _selectedRoute.value = mediaRouter.selectedRoute

        // Connectivity listeners
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Initial state check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        _isWifiEnabled.value = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled ?: false

        // Wi-Fi listener
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    _isWifiEnabled.value = true
                }
            }

            override fun onLost(network: Network) {
                // A specific network was lost; check if another Wi-Fi network is active.
                val currentNetwork = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
                _isWifiEnabled.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)

        // Bluetooth listener
        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = state == BluetoothAdapter.STATE_ON
                }
            }
        }
        context.registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))

        remoteProgressListener = RemoteMediaClient.ProgressListener { progress, _ ->
            if (!isRemotelySeeking.value) {
                _remotePosition.value = progress
                listeningStatsTracker.onProgress(progress, lastKnownRemoteIsPlaying)
            }
        }

        remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val remoteMediaClient = _castSession.value?.remoteMediaClient ?: return
                val mediaStatus = remoteMediaClient.mediaStatus ?: return
                val songMap = _masterAllSongs.value.associateBy { it.id }
                val newQueue = mediaStatus.queueItems.mapNotNull { item ->
                    item.customData?.optString("songId")?.let { songId ->
                        songMap[songId]
                    }
                }.toImmutableList()
                val currentItemId = mediaStatus.getCurrentItemId()
                val currentRemoteItem = mediaStatus.getQueueItemById(currentItemId)
                val currentSongId = currentRemoteItem?.customData?.optString("songId")
                val currentSong = currentSongId?.let { songMap[it] }
                if (currentSong?.id != _stablePlayerState.value.currentSong?.id) {
                    viewModelScope.launch {
                        currentSong?.albumArtUriString?.toUri()?.let { uri ->
                            extractAndGenerateColorScheme(uri)
                        }
                    }
                }
                _playerUiState.update {
                    it.copy(currentPlaybackQueue = newQueue)
                }
                val isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING
                lastKnownRemoteIsPlaying = isPlaying
                val streamPosition = mediaStatus.streamPosition
                val streamDuration = listOf(
                    remoteMediaClient.streamDuration,
                    currentSong?.duration ?: 0L,
                    0L
                ).maxOrNull() ?: 0L
                listeningStatsTracker.ensureSession(
                    song = currentSong,
                    positionMs = streamPosition,
                    durationMs = streamDuration,
                    isPlaying = isPlaying
                )
                if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_IDLE && mediaStatus.queueItemCount == 0) {
                    listeningStatsTracker.onPlaybackStopped()
                }
                _stablePlayerState.update {
                    it.copy(
                        isPlaying = isPlaying,
                        isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE,
                        repeatMode = mediaStatus.queueRepeatMode,
                        currentSong = currentSong,
                        totalDuration = streamDuration
                    )
                }
            }
        }

        castSessionManagerListener = object : SessionManagerListener<CastSession> {
            private fun transferPlayback(session: CastSession) {
                viewModelScope.launch {
                    if (!ensureHttpServerRunning()) {
                        sendToast("Could not start cast server. Check connection.")
                        disconnect()
                        return@launch
                    }

                    val serverAddress = MediaFileHttpServerService.serverAddress ?: return@launch
                    val localPlayer = mediaController ?: return@launch
                    val currentQueue = _playerUiState.value.currentPlaybackQueue
                    if (currentQueue.isEmpty()) return@launch

                    val wasPlaying = localPlayer.isPlaying
                    val currentSongIndex = localPlayer.currentMediaItemIndex
                    val currentPosition = localPlayer.currentPosition

                    localPlayer.pause()
                    stopProgressUpdates()

                    val castRepeatMode = if (localPlayer.shuffleModeEnabled) {
                        MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
                    } else {
                        when (localPlayer.repeatMode) {
                            Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                            Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                            else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                        }
                    }

                    castPlayer = CastPlayer(session)
                    _castSession.value = session
                    _isRemotePlaybackActive.value = true

                    castPlayer?.loadQueue(
                        songs = currentQueue,
                        startIndex = currentSongIndex,
                        startPosition = currentPosition,
                        repeatMode = castRepeatMode,
                        serverAddress = serverAddress,
                        autoPlay = wasPlaying,
                        onComplete = { success ->
                            if (!success) {
                                sendToast("Failed to load media on cast device.")
                                disconnect()
                            }
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

            private fun stopServerAndTransferBack() {
                val session = _castSession.value ?: return
                val remoteMediaClient = session.remoteMediaClient ?: return
                val lastKnownStatus = remoteMediaClient.mediaStatus
                val lastPosition = _remotePosition.value
                val wasPlaying = lastKnownStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING
                val lastItemId = lastKnownStatus?.currentItemId
                val lastRepeatMode = lastKnownStatus?.queueRepeatMode ?: Player.REPEAT_MODE_OFF
                val isShuffleEnabled = lastRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
                remoteProgressObserverJob?.cancel()
                remoteMediaClient.removeProgressListener(remoteProgressListener!!)
                remoteMediaClient.unregisterCallback(remoteMediaClientCallback!!)
                castPlayer = null
                _castSession.value = null
                _isRemotePlaybackActive.value = false
                context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                disconnect()
                val localPlayer = mediaController ?: return
                if (lastKnownStatus != null && lastItemId != null) {
                    val songMap = _masterAllSongs.value.associateBy { it.id }
                    val finalQueue = lastKnownStatus.queueItems.mapNotNull { item ->
                        item.customData?.optString("songId")?.let { songId ->
                            songMap[songId]
                        }
                    }
                    if (finalQueue.isNotEmpty()) {
                        val lastPlayedRemoteItem = lastKnownStatus.getQueueItemById(lastItemId)
                        val lastPlayedSongId = lastPlayedRemoteItem?.customData?.optString("songId")
                        val startIndex = finalQueue.indexOfFirst { it.id == lastPlayedSongId }.coerceAtLeast(0)
                        val mediaItems = finalQueue.map { song ->
                            MediaItem.Builder()
                                .setMediaId(song.id)
                                .setUri(song.contentUriString.toUri())
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(song.title)
                                        .setArtist(song.artist)
                                        .setArtworkUri(song.albumArtUriString?.toUri())
                                        .build()
                                )
                                .build()
                        }
                        localPlayer.shuffleModeEnabled = isShuffleEnabled
                        localPlayer.repeatMode = when (lastRepeatMode) {
                            MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                            MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                            else -> Player.REPEAT_MODE_OFF
                        }
                        localPlayer.setMediaItems(mediaItems, startIndex, lastPosition)
                        localPlayer.prepare()
                        if (wasPlaying) {
                            localPlayer.play()
                            startProgressUpdates()
                        } else {
                            _playerUiState.update { it.copy(currentPosition = lastPosition) }
                        }
                    }
                }
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                stopServerAndTransferBack()
            }

            override fun onSessionSuspended(session: CastSession, reason: Int) {
                stopServerAndTransferBack()
            }

            // Other listener methods can be overridden if needed
            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) {}
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
            override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        }
        sessionManager.addSessionManagerListener(castSessionManagerListener as SessionManagerListener<CastSession>, CastSession::class.java)
        _castSession.value = sessionManager.currentCastSession
        _isRemotePlaybackActive.value = _castSession.value != null
        _castSession.value?.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
        _castSession.value?.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)

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

    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true
    ) {
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            val remoteQueueItems = remoteMediaClient.mediaStatus?.queueItems ?: emptyList()
            val itemInQueue = remoteQueueItems.find { it.customData?.optString("songId") == song.id }

            val queueMatchesContext = remoteQueueItems.matchesQueueSongOrder(contextSongs)

            if (itemInQueue != null && queueMatchesContext) {
                // Song is already in the remote queue, just jump to it.
                castPlayer?.jumpToItem(itemInQueue.itemId, 0L)
                if (isVoluntaryPlay) incrementSongScore(song)
            } else {
                // Song not in remote queue, so start a new playback session.
                if (isVoluntaryPlay) incrementSongScore(song)
                playSongs(contextSongs, song, queueName, null)
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
        showAndPlaySong(song, playerUiState.value.allSongs.toList(), "Library")
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
                            it.copy(
                                currentSong = song,
                                totalDuration = playerCtrl.duration.coerceAtLeast(0L)
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
                    } ?: _stablePlayerState.update {
                        it.copy(currentSong = null, isPlaying = false)
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
                    listeningStatsTracker.onPlaybackStopped()
                    _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false) }
                    _playerUiState.update { it.copy(currentPosition = 0L) }
                }
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _stablePlayerState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
                if (playerCtrl.mediaItemCount == 0 && shuffleModeEnabled) {
                    val shuffledQueue = createShuffledQueue(_masterAllSongs.value)
                    if (shuffledQueue.isNotEmpty()) {
                        playSongs(shuffledQueue, shuffledQueue.first(), "Shuffled Queue", null)
                    }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) { _stablePlayerState.update { it.copy(repeatMode = repeatMode) } }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
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
        val castSession = _castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            if (!ensureHttpServerRunning()) return

            val serverAddress = MediaFileHttpServerService.serverAddress ?: return

            val startIndex = songsToPlay.indexOf(startSong).coerceAtLeast(0)
            val repeatMode = _stablePlayerState.value.repeatMode

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
        } else {
            val playSongsAction = {
                mediaController?.let { controller ->
                    val mediaItems = songsToPlay.map { song ->
                        val metadataBuilder = MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
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
                        controller.setMediaItems(mediaItems, startIndex, 0L)
                        controller.prepare()
                        controller.play()
                        _playerUiState.update { it.copy(currentPlaybackQueue = songsToPlay.toImmutableList(), currentQueueSourceName = queueName) }
                    }
                }
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }

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
            .setArtist(song.artist)
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

    fun toggleShuffle() {
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
            val newShuffleState = !_stablePlayerState.value.isShuffleEnabled
            mediaController?.shuffleModeEnabled = newShuffleState
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
        } else {
            val currentMode = _stablePlayerState.value.repeatMode
            val newMode = when (currentMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            mediaController?.repeatMode = newMode
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
                    .setArtist(song.artist)
                    .setArtworkUri(song.albumArtUriString?.toUri())
                    .build())
                .build()
            controller.addMediaItem(mediaItem)
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
                        .setArtist(song.artist)
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
                    "${song.title}" by ${song.artist}

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
            SortOption.SongTitleAZ -> _masterAllSongs.value.sortedBy { it.title }
            SortOption.SongTitleZA -> _masterAllSongs.value.sortedByDescending { it.title }
            SortOption.SongArtist -> _masterAllSongs.value.sortedBy { it.artist }
            SortOption.SongAlbum -> _masterAllSongs.value.sortedBy { it.album }
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
            SortOption.AlbumTitleAZ -> _playerUiState.value.albums.sortedBy { it.title }
            SortOption.AlbumTitleZA -> _playerUiState.value.albums.sortedByDescending { it.title }
            SortOption.AlbumArtist -> _playerUiState.value.albums.sortedBy { it.artist }
            SortOption.AlbumReleaseYear -> _playerUiState.value.albums.sortedByDescending { it.year }
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
            SortOption.FolderNameAZ -> _playerUiState.value.musicFolders.sortedBy { it.name }
            SortOption.FolderNameZA -> _playerUiState.value.musicFolders.sortedByDescending { it.name }
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
        mediaRouter.selectRoute(route)
    }

    fun disconnect() {
        mediaRouter.selectRoute(mediaRouter.defaultRoute)
        _isRemotePlaybackActive.value = false
    }

    fun setRouteVolume(volume: Int) {
        _routeVolume.value = volume
        _selectedRoute.value?.requestSetVolume(volume)
    }

    fun refreshCastRoutes() {
        viewModelScope.launch {
            _isRefreshingRoutes.value = true
            mediaRouter.removeCallback(mediaRouterCallback)
            val mediaRouteSelector = MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build()
            mediaRouter.addCallback(mediaRouteSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
            delay(1500) // Simulate a refresh delay
            _isRefreshingRoutes.value = false
        }
    }

    private suspend fun ensureHttpServerRunning(): Boolean {
        if (MediaFileHttpServerService.isServerRunning && MediaFileHttpServerService.serverAddress != null) {
            return true
        }

        context.startService(Intent(context, MediaFileHttpServerService::class.java).apply {
            action = MediaFileHttpServerService.ACTION_START_SERVER
        })

        val startTime = System.currentTimeMillis()
        val timeout = 5000L // 5 seconds
        while (!MediaFileHttpServerService.isServerRunning || MediaFileHttpServerService.serverAddress == null) {
            if (System.currentTimeMillis() - startTime > timeout) {
                sendToast("Cast server failed to start. Check Wi-Fi connection.")
                Timber.e("HTTP server start timed out.")
                return false
            }
            delay(100)
        }
        return true
    }

    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        listeningStatsTracker.onCleared()
        mediaRouter.removeCallback(mediaRouterCallback)
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        bluetoothStateReceiver?.let { context.unregisterReceiver(it) }
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

        sleepTimerJob = viewModelScope.launch {
            delay(durationMillis)
            if (isActive && _sleepTimerEndTimeMillis.value == endTime) { // Check if timer wasn't cancelled or changed
                mediaController?.pause()
                cancelSleepTimer() // Clear state after pausing
            }
        }
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

        viewModelScope.launch {
            // 1. Indicar que estamos cargando
            _stablePlayerState.update { it.copy(isLoadingLyrics = true, lyrics = null) }

            // 2. Obtener las letras desde el repositorio
            val fetchedLyrics = musicRepository.getLyrics(currentSong)

            // 3. Actualizar el estado con el resultado
            _stablePlayerState.update { it.copy(isLoadingLyrics = false, lyrics = fetchedLyrics) }
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
            Timber.d("Editing metadata for song: ${song.title} with URI: ${song.contentUriString}")
            Timber.d("New metadata: title=$newTitle, artist=$newArtist, album=$newAlbum, genre=$newGenre, lyrics=$newLyrics, trackNumber=$newTrackNumber")
            val previousAlbumArt = song.albumArtUriString
            val result = withContext(Dispatchers.IO) {
                songMetadataEditor.editSongMetadata(
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    newGenre = newGenre,
                    newLyrics = newLyrics,
                    newTrackNumber = newTrackNumber,
                    coverArtUpdate = coverArtUpdate,
                    songId = song.id.toLong(),
                )
            }

            if (result.success) {
                val refreshedAlbumArtUri = result.updatedAlbumArtUri ?: song.albumArtUriString
                invalidateCoverArtCaches(previousAlbumArt, refreshedAlbumArtUri)
                val updatedSong = song.copy(
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    genre = newGenre,
                    lyrics = newLyrics,
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
                    _stablePlayerState.update { it.copy(currentSong = updatedSong) }
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

                syncManager.sync()
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
    fun fetchLyricsForCurrentSong() {
        val currentSong = stablePlayerState.value.currentSong
        viewModelScope.launch {
            _lyricsSearchUiState.value = LyricsSearchUiState.Loading
            if (currentSong != null) {
                musicRepository.getLyricsFromRemote(currentSong)
                    .onSuccess { (lyrics, rawLyrics) -> // Deconstruct the pair
                        _lyricsSearchUiState.value = LyricsSearchUiState.Success(lyrics)
                        val updatedSong = currentSong.copy(lyrics = rawLyrics)
                        updateSongInStates(updatedSong, lyrics)
                    }
                    .onFailure { error ->
                        if (error is NoLyricsFoundException) {
                            // Perform a generic search and let the user pick
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
}
