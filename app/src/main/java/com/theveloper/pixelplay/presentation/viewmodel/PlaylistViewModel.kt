package com.theveloper.pixelplay.presentation.viewmodel

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
import javax.inject.Inject

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val currentPlaylistSongs: List<Song> = emptyList(),
    val currentPlaylistDetails: Playlist? = null,
    val isLoading: Boolean = false,

    // Para el diálogo/pantalla de selección de canciones
    val songSelectionPage: Int = 1, // Nuevo: para rastrear la página actual de selección
    val songSelectionForPlaylist: List<Song> = emptyList(),
    val isLoadingSongSelection: Boolean = false,
    val canLoadMoreSongsForSelection: Boolean = true, // Nuevo: para saber si hay más canciones para cargar

    //Sort option
    val currentPlaylistSortOption: SortOption = SortOption.PlaylistNameAZ
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    companion object {
        private const val SONG_SELECTION_PAGE_SIZE = 100 // Cargar 100 canciones a la vez para el selector
    }

    // Helper function to convert SortOption name string to SortOption object for playlists
    private fun getPlaylistSortOptionFromString(optionName: String?): SortOption {
        return when (optionName) {
            SortOption.PlaylistNameAZ.displayName -> SortOption.PlaylistNameAZ
            SortOption.PlaylistNameZA.displayName -> SortOption.PlaylistNameZA
            SortOption.PlaylistDateCreated.displayName -> SortOption.PlaylistDateCreated
            else -> SortOption.PlaylistNameAZ // Default if unknown or null
        }
    }

    init {
        loadPlaylistsAndInitialSortOption()
        loadMoreSongsForSelection(isInitialLoad = true)
    }

    private fun loadPlaylistsAndInitialSortOption() {
        viewModelScope.launch {
            // First, get the initial sort option
            val initialSortOptionName = userPreferencesRepository.playlistsSortOptionFlow.first()
            val initialSortOption = getPlaylistSortOptionFromString(initialSortOptionName)
            _uiState.update { it.copy(currentPlaylistSortOption = initialSortOption) }

            // Then, collect playlists and apply the sort option
            userPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                val currentSortOption = _uiState.value.currentPlaylistSortOption // Use the most up-to-date sort option
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
                val newSortOption = getPlaylistSortOptionFromString(optionName)
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

            Log.d("PlaylistVM", "Loading songs for selection. Page: $pageToLoad, PageSize: $SONG_SELECTION_PAGE_SIZE")

            try {
                // Colectar la lista de canciones del Flow en un hilo de IO
                val actualNewSongsList: List<Song> = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    musicRepository.getAudioFiles(pageToLoad, SONG_SELECTION_PAGE_SIZE).first()
                }
                Log.d("PlaylistVM", "Loaded ${actualNewSongsList.size} songs for selection.")

                // La actualización del UI se hace en el hilo principal (contexto por defecto de viewModelScope.launch)
                _uiState.update { currentStateAfterLoad ->
                    val updatedSongSelectionList = if (isInitialLoad) {
                        actualNewSongsList
                    } else {
                        // Evitar duplicados si por alguna razón se recarga la misma página
                        val currentSongIds = currentStateAfterLoad.songSelectionForPlaylist.map { it.id }.toSet()
                        val uniqueNewSongs = actualNewSongsList.filterNot { currentSongIds.contains(it.id) }
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
                        // Opcional: añadir un campo de error al PlaylistUiState
                        // errorLoadingSongSelection = "Failed to load songs: ${e.message}"
                    )
                }
            }
        }
    }

    // Función para refrescar la lista de selección de canciones (ej. si cambian los directorios)
    fun refreshSongSelection() {
        _uiState.update {
            it.copy(
                songSelectionForPlaylist = emptyList(), // Limpiar lista actual
                canLoadMoreSongsForSelection = true, // Permitir cargar de nuevo
                // isLoadingSongSelection = true // El estado de carga se maneja en loadMoreSongsForSelection
            )
        }
        loadMoreSongsForSelection(isInitialLoad = true)
    }


    fun loadPlaylistDetails(playlistId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentPlaylistDetails = null, currentPlaylistSongs = emptyList()) } // Resetear detalles y canciones
            try {
                // Obtener la playlist de las preferencias del usuario
                val playlist = userPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId }

                if (playlist != null) {
                    // Colectar la lista de canciones del Flow devuelto por el repositorio en un hilo de IO
                    val songsList: List<Song> = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        musicRepository.getSongsByIds(playlist.songIds).first()
                    }
                    // La actualización del UI se hace en el hilo principal
                    _uiState.update {
                        it.copy(
                            currentPlaylistDetails = playlist,
                            currentPlaylistSongs = songsList, // Asignar la List<Song>
                            isLoading = false
                        )
                    }
                } else {
                    Log.w("PlaylistVM", "Playlist with id $playlistId not found.")
                    _uiState.update { it.copy(isLoading = false) } // Mantener isLoading en false
                    // Opcional: podrías establecer un error o un estado específico de "no encontrado"
                }
            } catch (e: Exception) {
                Log.e("PlaylistVM", "Error loading playlist details for id $playlistId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false
                        // Opcional: añadir un campo de error al PlaylistUiState
                        // errorLoadingPlaylistDetails = "Failed to load playlist details: ${e.message}"
                    )
                }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            userPreferencesRepository.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            userPreferencesRepository.renamePlaylist(playlistId, newName)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                _uiState.update { it.copy(currentPlaylistDetails = it.currentPlaylistDetails?.copy(name = newName)) }
            }
        }
    }

    fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.addSongsToPlaylist(playlistId, songIdsToAdd)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                loadPlaylistDetails(playlistId)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
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
        viewModelScope.launch {
            val currentSongs = _uiState.value.currentPlaylistSongs.toMutableList()
            if (fromIndex in currentSongs.indices && toIndex in currentSongs.indices) {
                val item = currentSongs.removeAt(fromIndex)
                currentSongs.add(toIndex, item)
                val newSongOrderIds = currentSongs.map { it.id }
                userPreferencesRepository.reorderSongsInPlaylist(playlistId, newSongOrderIds)
                _uiState.update { it.copy(currentPlaylistSongs = currentSongs) }
            }
        }
    }

    //Sort funs
    fun sortPlaylists(sortOption: SortOption) {
        // Update the state with the new sort option first
        _uiState.update { it.copy(currentPlaylistSortOption = sortOption) }

        val currentPlaylists = _uiState.value.playlists // Get potentially updated list if any other op happened
        val sortedPlaylists = when (sortOption) {
            SortOption.PlaylistNameAZ -> currentPlaylists.sortedBy { it.name }
            SortOption.PlaylistNameZA -> currentPlaylists.sortedByDescending { it.name }
            SortOption.PlaylistDateCreated -> currentPlaylists.sortedByDescending { it.lastModified }
            else -> currentPlaylists // Should not happen for playlist specific options
        }.toList() // Ensure a new list

        _uiState.update { it.copy(playlists = sortedPlaylists) } // Update with the sorted list

        viewModelScope.launch {
            userPreferencesRepository.setPlaylistsSortOption(sortOption.displayName)
        }
    }
}