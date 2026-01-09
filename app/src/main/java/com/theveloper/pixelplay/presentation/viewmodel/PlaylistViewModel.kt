package com.theveloper.pixelplay.presentation.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.playlist.M3uManager
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.OutputStreamWriter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val currentPlaylistSongs: List<Song> = emptyList(),
    val currentPlaylistDetails: Playlist? = null,
    val isLoading: Boolean = false,
    val playlistNotFound: Boolean = false,

    // Para el diálogo/pantalla de selección de canciones
    val songSelectionPage: Int = 1, // Nuevo: para rastrear la página actual de selección
    val songSelectionForPlaylist: List<Song> = emptyList(),
    val isLoadingSongSelection: Boolean = false,
    val canLoadMoreSongsForSelection: Boolean = true, // Nuevo: para saber si hay más canciones para cargar

    //Sort option
    val currentPlaylistSortOption: SortOption = SortOption.PlaylistNameAZ,
    val currentPlaylistSongsSortOption: SortOption = SortOption.SongTitleAZ,
    val playlistSongsOrderMode: PlaylistSongsOrderMode = PlaylistSongsOrderMode.Sorted(SortOption.SongTitleAZ),
    val playlistOrderModes: Map<String, PlaylistSongsOrderMode> = emptyMap()
)

sealed class PlaylistSongsOrderMode {
    object Manual : PlaylistSongsOrderMode()
    data class Sorted(val option: SortOption) : PlaylistSongsOrderMode()
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val m3uManager: M3uManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    private val _playlistCreationEvent = MutableSharedFlow<Boolean>()
    val playlistCreationEvent: SharedFlow<Boolean> = _playlistCreationEvent.asSharedFlow()

    companion object {
        private const val SONG_SELECTION_PAGE_SIZE =
            100 // Cargar 100 canciones a la vez para el selector
        const val FOLDER_PLAYLIST_PREFIX = "folder_playlist:"
        private const val MANUAL_ORDER_MODE = "manual"
    }

    // Helper function to resolve stored playlist sort keys
    private fun resolvePlaylistSortOption(optionKey: String?): SortOption {
        return SortOption.fromStorageKey(
            optionKey,
            SortOption.PLAYLISTS,
            SortOption.PlaylistNameAZ
        )
    }

    init {
        loadPlaylistsAndInitialSortOption()
        loadMoreSongsForSelection(isInitialLoad = true)
        observePlaylistOrderModes()
    }

    private fun observePlaylistOrderModes() {
        viewModelScope.launch {
            userPreferencesRepository.playlistSongOrderModesFlow.collect { storedModes ->
                val resolvedModes = storedModes.mapValues { (_, value) ->
                    decodeOrderMode(value)
                }
                _uiState.update { it.copy(playlistOrderModes = resolvedModes) }
            }
        }
    }

    private fun loadPlaylistsAndInitialSortOption() {
        viewModelScope.launch {
            // First, get the initial sort option
            val initialSortOptionName = userPreferencesRepository.playlistsSortOptionFlow.first()
            val initialSortOption = resolvePlaylistSortOption(initialSortOptionName)
            _uiState.update { it.copy(currentPlaylistSortOption = initialSortOption) }

            // Then, collect playlists and apply the sort option
            userPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                val currentSortOption =
                    _uiState.value.currentPlaylistSortOption // Use the most up-to-date sort option
                val sortedPlaylists = when (currentSortOption) {
                    SortOption.PlaylistNameAZ -> playlists.sortedBy { it.name.lowercase() }
                    SortOption.PlaylistNameZA -> playlists.sortedByDescending { it.name.lowercase() }
                    SortOption.PlaylistDateCreated -> playlists.sortedByDescending { it.lastModified }
                    else -> playlists.sortedBy { it.name.lowercase() } // Default to NameAZ
                }
                _uiState.update { it.copy(playlists = sortedPlaylists) }
            }
        }
        // Collect subsequent changes to sort option from preferences
        viewModelScope.launch {
            userPreferencesRepository.playlistsSortOptionFlow.collect { optionName ->
                val newSortOption = resolvePlaylistSortOption(optionName)
                if (_uiState.value.currentPlaylistSortOption != newSortOption) {
                    // If the option from preferences is different, re-sort the current list
                    sortPlaylists(newSortOption)
                }
            }
        }
    }

    // Nueva función para cargar canciones para el selector de forma paginada
    fun loadMoreSongsForSelection(isInitialLoad: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.isLoadingSongSelection && !isInitialLoad) {
            Log.d("PlaylistVM", "loadMoreSongsForSelection: Already loading. Skipping.")
            return
        }
        if (!currentState.canLoadMoreSongsForSelection && !isInitialLoad) {
            Log.d("PlaylistVM", "loadMoreSongsForSelection: Cannot load more. Skipping.")
            return
        }

        viewModelScope.launch {
            val initialPageForLoad = if (isInitialLoad) 1 else _uiState.value.songSelectionPage

            _uiState.update {
                it.copy(
                    isLoadingSongSelection = true,
                    songSelectionPage = initialPageForLoad // Establecer la página correcta antes de la llamada
                )
            }

            // Usar el songSelectionPage del estado que acabamos de actualizar para la llamada al repo
            val pageToLoad = _uiState.value.songSelectionPage // Esta ahora es la página correcta

            Log.d(
                "PlaylistVM",
                "Loading songs for selection. Page: $pageToLoad, PageSize: $SONG_SELECTION_PAGE_SIZE"
            )

            try {
                // Colectar la lista de canciones del Flow en un hilo de IO
                val actualNewSongsList: List<Song> =
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        musicRepository.getAudioFiles().first()
                    }
                Log.d("PlaylistVM", "Loaded ${actualNewSongsList.size} songs for selection.")

                // La actualización del UI se hace en el hilo principal (contexto por defecto de viewModelScope.launch)
                _uiState.update { currentStateAfterLoad ->
                    val updatedSongSelectionList = if (isInitialLoad) {
                        actualNewSongsList
                    } else {
                        // Evitar duplicados si por alguna razón se recarga la misma página
                        val currentSongIds =
                            currentStateAfterLoad.songSelectionForPlaylist.map { it.id }.toSet()
                        val uniqueNewSongs =
                            actualNewSongsList.filterNot { currentSongIds.contains(it.id) }
                        currentStateAfterLoad.songSelectionForPlaylist + uniqueNewSongs
                    }

                    currentStateAfterLoad.copy(
                        songSelectionForPlaylist = updatedSongSelectionList,
                        isLoadingSongSelection = false,
                        canLoadMoreSongsForSelection = actualNewSongsList.size == SONG_SELECTION_PAGE_SIZE,
                        // Incrementar la página solo si se cargaron canciones y se espera que haya más
                        songSelectionPage = if (actualNewSongsList.isNotEmpty() && actualNewSongsList.size == SONG_SELECTION_PAGE_SIZE) {
                            currentStateAfterLoad.songSelectionPage + 1
                        } else {
                            currentStateAfterLoad.songSelectionPage // No incrementar si no hay más o si la carga fue parcial
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("PlaylistVM", "Error loading songs for selection. Page: $pageToLoad", e)
                _uiState.update {
                    it.copy(
                        isLoadingSongSelection = false
                    )
                }
            }
        }
    }


    fun loadPlaylistDetails(playlistId: String) {
        viewModelScope.launch {
            val shouldKeepExisting = _uiState.value.currentPlaylistDetails?.id == playlistId
            _uiState.update {
                it.copy(
                    isLoading = true,
                    playlistNotFound = false,
                    currentPlaylistDetails = if (shouldKeepExisting) it.currentPlaylistDetails else null,
                    currentPlaylistSongs = if (shouldKeepExisting) it.currentPlaylistSongs else emptyList()
                )
            } // Resetear detalles y canciones
            try {
                if (isFolderPlaylistId(playlistId)) {
                    val folderPath = Uri.decode(playlistId.removePrefix(FOLDER_PLAYLIST_PREFIX))
                    val folders = musicRepository.getMusicFolders().first()
                    val folder = findFolder(folderPath, folders)

                    if (folder != null) {
                        val songsList = folder.collectAllSongs()
                        val pseudoPlaylist = Playlist(
                            id = playlistId,
                            name = folder.name,
                            songIds = songsList.map { it.id }
                        )

                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = pseudoPlaylist,
                                currentPlaylistSongs = applySortToSongs(songsList, it.currentPlaylistSongsSortOption),
                                playlistSongsOrderMode = PlaylistSongsOrderMode.Sorted(it.currentPlaylistSongsSortOption),
                                isLoading = false,
                                playlistNotFound = false
                            )
                        }
                    } else {
                        Log.w("PlaylistVM", "Folder playlist with path $folderPath not found.")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                playlistNotFound = true,
                                currentPlaylistDetails = null,
                                currentPlaylistSongs = emptyList()
                            )
                        }
                    }
                } else {
                    // Obtener la playlist de las preferencias del usuario
                        val playlist = userPreferencesRepository.userPlaylistsFlow.first()
                            .find { it.id == playlistId }

                    if (playlist != null) {
                        val orderMode = _uiState.value.playlistOrderModes[playlistId]
                            ?: PlaylistSongsOrderMode.Manual

                        // Colectar la lista de canciones del Flow devuelto por el repositorio en un hilo de IO
                        val songsList: List<Song> = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            musicRepository.getSongsByIds(playlist.songIds).first()
                        }

                        val orderedSongs = when (orderMode) {
                            is PlaylistSongsOrderMode.Sorted -> applySortToSongs(songsList, orderMode.option)
                            PlaylistSongsOrderMode.Manual -> songsList
                        }

                        // La actualización del UI se hace en el hilo principal
                        _uiState.update {
                            it.copy(
                                currentPlaylistDetails = playlist,
                                currentPlaylistSongs = orderedSongs,
                                currentPlaylistSongsSortOption = (orderMode as? PlaylistSongsOrderMode.Sorted)?.option
                                    ?: it.currentPlaylistSongsSortOption,
                                playlistSongsOrderMode = orderMode,
                                playlistOrderModes = it.playlistOrderModes + (playlistId to orderMode),
                                isLoading = false,
                                playlistNotFound = false
                            )
                        }
                    } else {
                        Log.w("PlaylistVM", "Playlist with id $playlistId not found.")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                playlistNotFound = true,
                                currentPlaylistDetails = null,
                                currentPlaylistSongs = emptyList()
                            )
                        } // Mantener isLoading en false
                        // Opcional: podrías establecer un error o un estado específico de "no encontrado"
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistVM", "Error loading playlist details for id $playlistId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        playlistNotFound = true,
                        currentPlaylistDetails = null,
                        currentPlaylistSongs = emptyList()
                    )
                }
            }
        }
    }

    fun createPlaylist(
        name: String,
        coverImageUri: String? = null,
        coverColor: Int? = null,
        coverIcon: String? = null,
        songIds: List<String> = emptyList(), // Added songIds parameter
        cropScale: Float = 1f,
        cropPanX: Float = 0f,
        cropPanY: Float = 0f,
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
    ) {
        viewModelScope.launch {
            var savedCoverPath: String? = null
            
            if (coverImageUri != null) {
                // Generate a unique ID for the image file since we don't have the playlist ID yet
                val imageId = UUID.randomUUID().toString()
                savedCoverPath = saveCoverImageToInternalStorage(
                    Uri.parse(coverImageUri), 
                    imageId,
                    cropScale,
                    cropPanX,
                    cropPanY
                )
            }

            userPreferencesRepository.createPlaylist(
                name = name,
                songIds = songIds, // Use passed songIds
                isAiGenerated = isAiGenerated,
                isQueueGenerated = isQueueGenerated,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4
            )
            _playlistCreationEvent.emit(true)
        }
    }


    suspend fun saveCoverImageToInternalStorage(
        uri: Uri, 
        uniqueId: String,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Load original bitmap
                val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                         // Optimization: Mutable to support software rendering if needed
                         decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE 
                         // Use HARWARE if possible but need to copy for Canvas? 
                         // Software is safer for manual Canvas drawing.
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                
                // Target dimensions (Square)
                val targetSize = 1024
                
                // create target bitmap
                val targetBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(targetBitmap)
                
                // Calculate base dimensions (fitting smallest dimension to target)
                // Logic must match ImageCropView
                val bitmapWidth = originalBitmap.width.toFloat()
                val bitmapHeight = originalBitmap.height.toFloat()
                val bitmapRatio = bitmapWidth / bitmapHeight
                
                val (baseWidth, baseHeight) = if (bitmapRatio > 1f) {
                     // Wide: Height matches target
                     targetSize * bitmapRatio to targetSize.toFloat()
                } else {
                     // Tall: Width matches target
                     targetSize.toFloat() to targetSize / bitmapRatio
                }
                
                // Calculate transformations
                // Scaled Dimensions
                val scaledWidth = baseWidth * cropScale
                val scaledHeight = baseHeight * cropScale
                
                // Center + Pan
                // Center of target is targetSize/2
                // We want to center the Scaled Image at (Center + Pan)
                // TopLeft = CenterX - ScaledW/2 + PanX
                
                // Pan is normalized relative to Viewport (TargetSize)
                val panPxX = cropPanX * targetSize
                val panPxY = cropPanY * targetSize
                
                val dx = (targetSize - scaledWidth) / 2f + panPxX
                val dy = (targetSize - scaledHeight) / 2f + panPxY
                
                // Draw
                // We draw the original bitmap scaled to (scaledWidth, scaledHeight) at (dx, dy)
                val matrix = android.graphics.Matrix()
                matrix.postScale(scaledWidth / bitmapWidth, scaledHeight / bitmapHeight)
                matrix.postTranslate(dx, dy)
                
                canvas.drawBitmap(originalBitmap, matrix, null)
                
                // Save
                val fileName = "playlist_cover_$uniqueId.jpg"
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { out ->
                    targetBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                // Recycle
                if (originalBitmap != targetBitmap) originalBitmap.recycle()
                // Target bitmap is not recycled here, let GC handle? 
                // Or recycle explicitly if immediate memory pressure concern.
                
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            userPreferencesRepository.deletePlaylist(playlistId)
        }
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, songIds) = m3uManager.parseM3u(uri)
                if (songIds.isNotEmpty()) {
                    userPreferencesRepository.createPlaylist(name, songIds)
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error importing M3U", e)
            }
        }
    }

    fun exportM3u(playlist: Playlist, uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                val m3uContent = m3uManager.generateM3u(playlist, songs)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(m3uContent)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaylistViewModel", "Error exporting M3U", e)
            }
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            userPreferencesRepository.renamePlaylist(playlistId, newName)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                _uiState.update {
                    it.copy(
                        currentPlaylistDetails = it.currentPlaylistDetails?.copy(
                            name = newName
                        )
                    )
                }
            }
        }
    }

    fun updatePlaylistParameters(
        playlistId: String,
        name: String,
        coverImageUri: String?,
        coverColor: Int?,
        coverIcon: String?,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float,
        coverShapeType: String?,
        coverShapeDetail1: Float?,
        coverShapeDetail2: Float?,
        coverShapeDetail3: Float?,
        coverShapeDetail4: Float?
    ) {
        if (isFolderPlaylistId(playlistId)) return
        val currentPlaylist = _uiState.value.currentPlaylistDetails ?: return
        if (currentPlaylist.id != playlistId) return

        viewModelScope.launch {
            var savedCoverPath: String? = currentPlaylist.coverImageUri

            // If a new URI is provided and it's different from the existing one (and not null)
            // Or if we need to re-save because crop params changed?
            // For simplicity, if coverImageUri is passed and it's a content URI, we save it.
            // If it's the same string as savedCoverPath, we assume it's unchanged unless we want to force re-crop.
            // The UI will pass the Uri string. If it's a local file path, it's likely already saved.
            // But if the user selected a new image, it will be a content content:// uri.

            if (coverImageUri != null && coverImageUri != currentPlaylist.coverImageUri) {
                 // Check if it is a content URI or a file path that is NOT the existing saved path
                 if (coverImageUri.startsWith("content://") || (coverImageUri.startsWith("/") && coverImageUri != currentPlaylist.coverImageUri)) {
                     val imageId = UUID.randomUUID().toString()
                     val newPath = saveCoverImageToInternalStorage(
                         Uri.parse(coverImageUri),
                         imageId,
                         cropScale,
                         cropPanX,
                         cropPanY
                     )
                     if (newPath != null) {
                         savedCoverPath = newPath
                     }
                 }
            } else if (coverImageUri == null) {
                // If passed null, it might mean remove cover? Or just no change?
                // For this implementation let's assume if the user cleared it, the UI passes null.
                // But we need to distinguish "no change" vs "remove".
                // In CreatePlaylist we have "selectedImageUri".
                // Let's assume the UI sends the desired final state.
                // NOTE: If the user didn't change the image, the UI might send the existing coverImageUri (which is a file path).
                // Or if they removed it, they send null.
                
                // However, we also have crop parameters. If image is unchanged but crop changed, we should re-save (re-crop)
                // if we have the original source. But we don't have the original source for the existing cover (we only have the cropped result).
                // So, we can only re-crop if we have a source URI.
                // This limitation implies: We can only update crop if we pick an image.
                // So if coverImageUri is the existing path, we ignore crop params.
                savedCoverPath = null // If explicit null passed, we remove it.
            }
            // Logic correction: 
            // If the UI passes the EXISTING file path, implies NO CHANGE to image.
            // If the UI passes a NEW content URI, implies NEW IMAGE (and we use crop params).
            // If the UI passes NULL, implies REMOVE IMAGE.
            if (coverImageUri == currentPlaylist.coverImageUri) {
                savedCoverPath = currentPlaylist.coverImageUri
            }


            val updatedPlaylist = currentPlaylist.copy(
                name = name,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4
            )

            // Optimistic update
            _uiState.update {
                it.copy(currentPlaylistDetails = updatedPlaylist)
            }

            userPreferencesRepository.updatePlaylist(updatedPlaylist)
        }
    }

    fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            userPreferencesRepository.addSongsToPlaylist(playlistId, songIdsToAdd)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                loadPlaylistDetails(playlistId)
            }
        }
    }

    /**
     * @param playlistIds Ids of playlists to add the song to
     * */
    fun addOrRemoveSongFromPlaylists(
        songId: String,
        playlistIds: List<String>,
        currentPlaylistId: String?
    ) {
        viewModelScope.launch {
            val removedFromPlaylists =
                userPreferencesRepository.addOrRemoveSongFromPlaylists(songId, playlistIds)
            if (currentPlaylistId != null && removedFromPlaylists.contains (currentPlaylistId)) {
                removeSongFromPlaylist(currentPlaylistId, songId)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            userPreferencesRepository.removeSongFromPlaylist(playlistId, songIdToRemove)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                _uiState.update {
                    it.copy(currentPlaylistSongs = it.currentPlaylistSongs.filterNot { s -> s.id == songIdToRemove })
                }
            }
        }
    }

    fun reorderSongsInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            val currentSongs = _uiState.value.currentPlaylistSongs.toMutableList()
            if (fromIndex in currentSongs.indices && toIndex in currentSongs.indices) {
                val item = currentSongs.removeAt(fromIndex)
                currentSongs.add(toIndex, item)
                val newSongOrderIds = currentSongs.map { it.id }
                userPreferencesRepository.reorderSongsInPlaylist(playlistId, newSongOrderIds)
                userPreferencesRepository.setPlaylistSongOrderMode(
                    playlistId,
                    MANUAL_ORDER_MODE
                )
                _uiState.update {
                    val updatedModes = it.playlistOrderModes + (playlistId to PlaylistSongsOrderMode.Manual)
                    it.copy(
                        currentPlaylistSongs = currentSongs,
                        playlistSongsOrderMode = PlaylistSongsOrderMode.Manual,
                        playlistOrderModes = updatedModes
                    )
                }
            }
        }
    }

    //Sort funs
    fun sortPlaylists(sortOption: SortOption) {
        _uiState.update { it.copy(currentPlaylistSortOption = sortOption) }

        val currentPlaylists = _uiState.value.playlists
        val sortedPlaylists = when (sortOption) {
            SortOption.PlaylistNameAZ -> currentPlaylists.sortedBy { it.name.lowercase() }
            SortOption.PlaylistNameZA -> currentPlaylists.sortedByDescending { it.name.lowercase() }
            SortOption.PlaylistDateCreated -> currentPlaylists.sortedByDescending { it.lastModified }
            else -> currentPlaylists
        }.toList()

        _uiState.update { it.copy(playlists = sortedPlaylists) }

        viewModelScope.launch {
            userPreferencesRepository.setPlaylistsSortOption(sortOption.storageKey)
        }
    }

    fun sortPlaylistSongs(sortOption: SortOption) {
        val playlistId = _uiState.value.currentPlaylistDetails?.id
        
        // If SongDefaultOrder is selected, reload the playlist to get original order
        if (sortOption == SortOption.SongDefaultOrder) {
            if (playlistId != null) {
                viewModelScope.launch {
                    // Set order mode to Manual (which preserves original order)
                    userPreferencesRepository.setPlaylistSongOrderMode(
                        playlistId,
                        MANUAL_ORDER_MODE
                    )
                    // Reload the playlist to get original song order
                    loadPlaylistDetails(playlistId)
                }
            }
            return
        }

        val currentSongs = _uiState.value.currentPlaylistSongs
        val sortedSongs = when (sortOption) {
            SortOption.SongTitleAZ -> currentSongs.sortedBy { it.title.lowercase() }
            SortOption.SongTitleZA -> currentSongs.sortedByDescending { it.title.lowercase() }
            SortOption.SongArtist -> currentSongs.sortedBy { it.artist.lowercase() }
            SortOption.SongAlbum -> currentSongs.sortedBy { it.album.lowercase() }
            SortOption.SongDuration -> currentSongs.sortedBy { it.duration }
            SortOption.SongDateAdded -> currentSongs.sortedByDescending { it.dateAdded }
            else -> currentSongs
        }

        _uiState.update {
            val updatedModes = if (playlistId != null) {
                it.playlistOrderModes + (playlistId to PlaylistSongsOrderMode.Sorted(sortOption))
            } else {
                it.playlistOrderModes
            }
            it.copy(
                currentPlaylistSongs = sortedSongs,
                currentPlaylistSongsSortOption = sortOption,
                playlistSongsOrderMode = PlaylistSongsOrderMode.Sorted(sortOption),
                playlistOrderModes = updatedModes
            )
        }

        if (playlistId != null) {
            viewModelScope.launch {
                userPreferencesRepository.setPlaylistSongOrderMode(
                    playlistId,
                    sortOption.storageKey
                )
            }
        }

        // Persist local sort preference if needed (optional, not requested but good UX)
        // For now, we keep it in memory as per request focus.
    }

    private fun isFolderPlaylistId(playlistId: String): Boolean =
        playlistId.startsWith(FOLDER_PLAYLIST_PREFIX)

    private fun findFolder(
        targetPath: String,
        folders: List<com.theveloper.pixelplay.data.model.MusicFolder>
    ): com.theveloper.pixelplay.data.model.MusicFolder? {
        val queue: ArrayDeque<com.theveloper.pixelplay.data.model.MusicFolder> = ArrayDeque(folders)
        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            if (folder.path == targetPath) {
                return folder
            }
            folder.subFolders.forEach { queue.addLast(it) }
        }
        return null
    }

    private fun com.theveloper.pixelplay.data.model.MusicFolder.collectAllSongs(): List<Song> {
        return songs + subFolders.flatMap { it.collectAllSongs() }
    }

    private fun applySortToSongs(songs: List<Song>, sortOption: SortOption): List<Song> {
        return when (sortOption) {
            SortOption.SongTitleAZ -> songs.sortedBy { it.title.lowercase() }
            SortOption.SongTitleZA -> songs.sortedByDescending { it.title.lowercase() }
            SortOption.SongArtist -> songs.sortedBy { it.artist.lowercase() }
            SortOption.SongAlbum -> songs.sortedBy { it.album.lowercase() }
            SortOption.SongDuration -> songs.sortedBy { it.duration }
            SortOption.SongDateAdded -> songs.sortedByDescending { it.dateAdded }
            else -> songs
        }
    }

    private fun decodeOrderMode(value: String): PlaylistSongsOrderMode {
        return if (value == MANUAL_ORDER_MODE) {
            PlaylistSongsOrderMode.Manual
        } else {
            val option = SortOption.fromStorageKey(value, SortOption.SONGS, SortOption.SongTitleAZ)
            PlaylistSongsOrderMode.Sorted(option)
        }
    }
}
