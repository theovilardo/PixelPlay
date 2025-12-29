package com.theveloper.pixelplay.presentation.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

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
                    SortOption.PlaylistNameAZ -> playlists.sortedBy { it.name }
                    SortOption.PlaylistNameZA -> playlists.sortedByDescending { it.name }
                    SortOption.PlaylistDateCreated -> playlists.sortedByDescending { it.lastModified }
                    else -> playlists.sortedBy { it.name } // Default to NameAZ
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
        songIds: List<String> = emptyList(),
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
    ) {
        viewModelScope.launch {
            userPreferencesRepository.createPlaylist(name, songIds, isAiGenerated, isQueueGenerated)
        }
    }

    fun deletePlaylist(playlistId: String) {
        if (isFolderPlaylistId(playlistId)) return
        viewModelScope.launch {
            userPreferencesRepository.deletePlaylist(playlistId)
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
            SortOption.PlaylistNameAZ -> currentPlaylists.sortedBy { it.name }
            SortOption.PlaylistNameZA -> currentPlaylists.sortedByDescending { it.name }
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

        val currentSongs = _uiState.value.currentPlaylistSongs
        val sortedSongs = when (sortOption) {
            SortOption.SongTitleAZ -> currentSongs.sortedBy { it.title }
            SortOption.SongTitleZA -> currentSongs.sortedByDescending { it.title }
            SortOption.SongArtist -> currentSongs.sortedBy { it.artist }
            SortOption.SongAlbum -> currentSongs.sortedBy { it.album }
            SortOption.SongDuration -> currentSongs.sortedBy { it.duration }
            SortOption.SongDateAdded -> currentSongs.sortedByDescending { it.dateAdded } // Or dateModified if available/relevant
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
            SortOption.SongTitleAZ -> songs.sortedBy { it.title }
            SortOption.SongTitleZA -> songs.sortedByDescending { it.title }
            SortOption.SongArtist -> songs.sortedBy { it.artist }
            SortOption.SongAlbum -> songs.sortedBy { it.album }
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
