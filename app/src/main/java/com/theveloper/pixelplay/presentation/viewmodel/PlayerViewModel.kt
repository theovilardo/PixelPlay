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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import android.util.Log
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
    val currentQueueSourceNname: String = "Todas las canciones",
    val lavaLampColors: ImmutableList<Color> = persistentListOf(),
    val albums: ImmutableList<Album> = persistentListOf(),
    val artists: ImmutableList<Artist> = persistentListOf(),
    val isLoadingLibraryCategories: Boolean = true,
    val canLoadMoreAlbums: Boolean = true,
    val canLoadMoreArtists: Boolean = true
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
    private val _globalThemePreference = MutableStateFlow(ThemePreference.DYNAMIC) // Default a DYNAMIC
    val globalThemePreference: StateFlow<String> = _globalThemePreference.asStateFlow()
    private val _playerThemePreference = MutableStateFlow(ThemePreference.GLOBAL)
    val playerThemePreference: StateFlow<String> = _playerThemePreference.asStateFlow()

    private val _isInitialThemePreloadComplete = MutableStateFlow(false)
    val isInitialThemePreloadComplete: StateFlow<Boolean> = _isInitialThemePreloadComplete.asStateFlow()

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

    // Nuevo StateFlow para la lista de objetos Song favoritos
    val favoriteSongs: StateFlow<ImmutableList<Song>> = combine( // CAMBIO AQUÍ TAMBIÉN
        _favoriteSongIds,
        _playerUiState
    ) { ids, uiState ->
        val allSongs = uiState.allSongs // Esto ya es ImmutableList
        allSongs.filter { song -> ids.contains(song.id) }.toImmutableList() // Convertir resultado del filtro
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

    init {
        viewModelScope.launch { userPreferencesRepository.globalThemePreferenceFlow.collect { _globalThemePreference.value = it } }
        viewModelScope.launch { userPreferencesRepository.playerThemePreferenceFlow.collect { _playerThemePreference.value = it } }
        // Observar favoritos
        viewModelScope.launch {
            userPreferencesRepository.favoriteSongIdsFlow.collect { ids ->
                _favoriteSongIds.value = ids
                // La actualización de stablePlayerState.isCurrentSongFavorite ya se hace en updateFavoriteStatusForCurrentSong
            }
        }
        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                setupMediaControllerListeners() // Configurar listeners y sincronizar estado inicial
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) } // Asegurar que el loading se detenga en error
                // Log error
            }
        }, MoreExecutors.directExecutor())
        // Iniciar precarga y carga de datos iniciales
        preloadThemesAndInitialData()
    }

    private fun preloadThemesAndInitialData() {
        viewModelScope.launch { // Main.immediate by default
            val overallInitStartTime = System.currentTimeMillis()
            _isInitialThemePreloadComplete.value = false // Mantener esto

            // Introduce a delay before starting the theme preloading job
            launch { // This launch uses viewModelScope's context, which is fine
                delay(1500L) // Delay for 1.5 seconds (configurable)
                Log.d("PlayerViewModel", "Delay complete, starting themePreloadingJob.")
                val themeJobStartTime = System.currentTimeMillis()
                // Launch theme preloading in a separate, controlled background job
                val themePreloadingJob: Job = launch(Dispatchers.IO) { // Explicitly Dispatchers.IO
                    val getAllUrisStartTime = System.currentTimeMillis()
                    val allAlbumArtUris = musicRepository.getAllUniqueAlbumArtUris()
                    Log.d("PlayerViewModel", "getAllUniqueAlbumArtUris took ${System.currentTimeMillis() - getAllUrisStartTime} ms")
                    allAlbumArtUris.forEach { uri ->
                        if (!isActive) return@forEach // Check if coroutine is still active
                        try {
                            extractAndGenerateColorScheme(uri, isPreload = true)
                        } catch (e: Exception) {
                            Log.e("PlayerViewModel", "Error preloading theme for $uri", e)
                            // Consider adding a check !isActive here too if extractAndGenerateColorScheme is very long
                        }
                    }
                    Log.d("PlayerViewModel", "themePreloadingJob actual work took ${System.currentTimeMillis() - themeJobStartTime} ms before finishing log")
                    Log.d("PlayerViewModel", "themePreloadingJob finished.")
                }
                // Optional: you might want to handle cancellation of themePreloadingJob explicitly
                // if the viewModel is cleared during the delay or during its execution.
                // However, viewModelScope cancellation should propagate.
            }

            // Start loading initial UI data (concurrently with the delay timer)
            val resetLoadDataStartTime = System.currentTimeMillis()
            resetAndLoadInitialData() // Esto lanza sus propias corrutinas
            Log.d("PlayerViewModel", "resetAndLoadInitialData() call took ${System.currentTimeMillis() - resetLoadDataStartTime} ms (Note: actual loading is async)")

            // Wait for the initial songs to be loaded before marking initial theme/UI setup as complete.
            // This ensures that the primary content is available when the loading screen disappears.
            viewModelScope.launch {
                // Wait until isLoadingInitialSongs is false
                _playerUiState.first { !it.isLoadingInitialSongs }
                // At this point, the first page of songs is loaded.
                _isInitialThemePreloadComplete.value = true
                Log.d("PlayerViewModel", "Initial song load complete, _isInitialThemePreloadComplete set to true. Total time since init start: ${System.currentTimeMillis() - overallInitStartTime} ms")
            }
        }
    }
//    private fun preloadThemesAndInitialData() {
//        viewModelScope.launch { // Main.immediate by default
//            _isInitialThemePreloadComplete.value = false
//
//            // Launch theme preloading in a separate, controlled background job
//            val themePreloadingJob: Job = launch(Dispatchers.IO) {
//                val allAlbumArtUris = musicRepository.getAllUniqueAlbumArtUris() // Already on IO dispatcher
//                allAlbumArtUris.forEach { uri ->
//                    try {
//                        extractAndGenerateColorScheme(uri, isPreload = true)
//                    } catch (e: Exception) {
//                        Log.e("PlayerViewModel", "Error preloading theme for $uri", e)
//                    }
//                }
//            }
//
//            // Concurrently load initial UI data
//            resetAndLoadInitialData()
//
//            // Wait for the theme preloading to complete
//            themePreloadingJob.join()
//
//            _isInitialThemePreloadComplete.value = true
//        }
//    }

    private fun resetAndLoadInitialData() {
        currentSongPage = 1
        currentAlbumPage = 1
        currentArtistPage = 1
        _playerUiState.update {
            it.copy(
                allSongs = emptyList(), albums = emptyList(), artists = emptyList(),
                canLoadMoreSongs = true, canLoadMoreAlbums = true, canLoadMoreArtists = true
            )
        }
        loadSongsFromRepository(isInitialLoad = true)
        loadLibraryCategories(isInitialLoad = true)
    }

    private fun loadSongsFromRepository(isInitialLoad: Boolean = false) {
        if (_playerUiState.value.isLoadingMoreSongs && !isInitialLoad) return // Evitar cargas múltiples
        if (!_playerUiState.value.canLoadMoreSongs && !isInitialLoad) return

        viewModelScope.launch {
            if (isInitialLoad) _playerUiState.update { it.copy(isLoadingInitialSongs = true) }
            else _playerUiState.update { it.copy(isLoadingMoreSongs = true) }

            val newSongs = musicRepository.getAudioFiles(currentSongPage, PAGE_SIZE)
            _playerUiState.update {
                it.copy(
                    allSongs = if (isInitialLoad) newSongs.toImmutableList() else (it.allSongs + newSongs).toImmutableList(),
                    isLoadingInitialSongs = false,
                    isLoadingMoreSongs = false,
                    canLoadMoreSongs = newSongs.size == PAGE_SIZE
                )
            }
            if (newSongs.isNotEmpty()) currentSongPage++
        }
    }

    fun loadMoreSongs() {
        loadSongsFromRepository()
    }

    private fun loadLibraryCategories(isInitialLoad: Boolean = false) {
        if (_playerUiState.value.isLoadingLibraryCategories && !isInitialLoad) return

        viewModelScope.launch {
            _playerUiState.update { it.copy(isLoadingLibraryCategories = true) }
            // Cargar Álbumes
            if (_playerUiState.value.canLoadMoreAlbums || isInitialLoad) {
                val newAlbums = musicRepository.getAlbums(currentAlbumPage, PAGE_SIZE)
                _playerUiState.update {
                    it.copy(
                        albums = if (isInitialLoad) newAlbums.toImmutableList() else (it.albums + newAlbums).toImmutableList(),
                        canLoadMoreAlbums = newAlbums.size == PAGE_SIZE
                    )
                }
                if (newAlbums.isNotEmpty()) currentAlbumPage++
            }
            // Cargar Artistas
            if (_playerUiState.value.canLoadMoreArtists || isInitialLoad) {
                val newArtists = musicRepository.getArtists(currentArtistPage, PAGE_SIZE)
                _playerUiState.update {
                    it.copy(
                        artists = if (isInitialLoad) newArtists.toImmutableList() else (it.artists + newArtists).toImmutableList(),
                        canLoadMoreArtists = newArtists.size == PAGE_SIZE
                    )
                }
                if (newArtists.isNotEmpty()) currentArtistPage++
            }
            _playerUiState.update { it.copy(isLoadingLibraryCategories = false) }
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

    // showAndPlaySong ahora usa playSongs con allSongs como la lista
    fun showAndPlaySong(song: Song) {
        // Usar la lista actual de allSongs (que es paginada) como la cola por defecto.
        playSongs(_playerUiState.value.allSongs, song, "Todas las canciones")
        _isSheetVisible.value = true
        _predictiveBackCollapseFraction.value = 0f
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
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _stablePlayerState.update { it.copy(totalDuration = controller.duration.coerceAtLeast(0L)) }
                }
                if (playbackState == Player.STATE_IDLE && controller.mediaItemCount == 0) {
                    _stablePlayerState.update { it.copy(currentSong = null, isPlaying = false) }
                    _playerUiState.update { it.copy(currentPosition = 0L) }
                }
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
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "Lista de reproducción") {
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
                _playerUiState.update { it.copy(currentPlaybackQueue = songsToPlay.toImmutableList(), currentQueueSourceNname = queueName) }
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

    // Función para ser llamada por AlbumGridItem
    fun getAlbumColorSchemeFlow(albumArtUri: Uri?): StateFlow<ColorSchemePair?> {
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
    private suspend fun getOrGenerateColorSchemeForUri(albumArtUri: Uri, isPreload: Boolean): ColorSchemePair? {
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
            return ColorScheme(
                primary = sv.primary.toComposeColor(), onPrimary = sv.onPrimary.toComposeColor(), primaryContainer = sv.primaryContainer.toComposeColor(), onPrimaryContainer = sv.onPrimaryContainer.toComposeColor(),
                secondary = sv.secondary.toComposeColor(), onSecondary = sv.onSecondary.toComposeColor(), secondaryContainer = sv.secondaryContainer.toComposeColor(), onSecondaryContainer = sv.onSecondaryContainer.toComposeColor(),
                tertiary = sv.tertiary.toComposeColor(), onTertiary = sv.onTertiary.toComposeColor(), tertiaryContainer = sv.tertiaryContainer.toComposeColor(), onTertiaryContainer = sv.onTertiaryContainer.toComposeColor(),
                background = sv.background.toComposeColor(), onBackground = sv.onBackground.toComposeColor(), surface = sv.surface.toComposeColor(), onSurface = sv.onSurface.toComposeColor(),
                surfaceVariant = sv.surfaceVariant.toComposeColor(), onSurfaceVariant = sv.onSurfaceVariant.toComposeColor(), error = sv.error.toComposeColor(), onError = sv.onError.toComposeColor(),
                outline = sv.outline.toComposeColor(), errorContainer = sv.errorContainer.toComposeColor(), onErrorContainer = sv.onErrorContainer.toComposeColor(),
                inversePrimary = sv.inversePrimary.toComposeColor(), surfaceTint = sv.surfaceTint.toComposeColor(), outlineVariant = sv.outlineVariant.toComposeColor(), scrim = sv.scrim.toComposeColor(),
                inverseSurface = sv.inverseSurface.toComposeColor(), inverseOnSurface = sv.inverseOnSurface.toComposeColor()
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
                delay(1000)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun searchSongs(query: String): List<Song> {
        if (query.isBlank()) return _playerUiState.value.allSongs
        return _playerUiState.value.allSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true)
        }
    }


    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        // No liberar mediaControllerFuture aquí si es compartido o gestionado por la Activity/Service
        // MediaController.releaseFuture(mediaControllerFuture) // Solo si este ViewModel es el único propietario
    }
}