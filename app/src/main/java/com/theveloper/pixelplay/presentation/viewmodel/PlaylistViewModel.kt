package com.theveloper.pixelplay.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
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
    val canLoadMoreSongsForSelection: Boolean = true // Nuevo: para saber si hay más canciones para cargar
)

//data class PlaylistUiState(
//    val playlists: List<Playlist> = emptyList(),
//    val currentPlaylistSongs: List<Song> = emptyList(),
//    val currentPlaylistDetails: Playlist? = null,
//    val isLoading: Boolean = false,
//    val songSelectionForPlaylist: List<Song> = emptyList(), // Para el diálogo de selección
//    val isLoadingSongSelection: Boolean = false
//)

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

    init {
        loadPlaylists()
        // Iniciar la carga de la primera página para el selector de canciones
        loadMoreSongsForSelection(isInitialLoad = true)
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            userPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists.sortedByDescending { p -> p.lastModified }) }
            }
        }
    }

    // Nueva función para cargar canciones para el selector de forma paginada
    fun loadMoreSongsForSelection(isInitialLoad: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.isLoadingSongSelection && !isInitialLoad) return
        if (!currentState.canLoadMoreSongsForSelection && !isInitialLoad) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingSongSelection = true,
                    // Resetear la página a 1 si es carga inicial
                    songSelectionPage = if (isInitialLoad) 1 else it.songSelectionPage
                )
            }

            // Usar el songSelectionPage del estado actualizado
            val pageToLoad = _uiState.value.songSelectionPage

            Log.d("PlaylistVM", "Loading songs for selection. Page: $pageToLoad, PageSize: $SONG_SELECTION_PAGE_SIZE")
            val newSongs = musicRepository.getAudioFiles(pageToLoad, SONG_SELECTION_PAGE_SIZE)
            Log.d("PlaylistVM", "Loaded ${newSongs.size} songs for selection.")

            _uiState.update {
                it.copy(
                    songSelectionForPlaylist = if (isInitialLoad) newSongs else it.songSelectionForPlaylist + newSongs,
                    isLoadingSongSelection = false,
                    canLoadMoreSongsForSelection = newSongs.size == SONG_SELECTION_PAGE_SIZE,
                    songSelectionPage = if (newSongs.isNotEmpty()) it.songSelectionPage + 1 else it.songSelectionPage
                )
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
            _uiState.update { it.copy(isLoading = true) }
            val playlist = userPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
            if (playlist != null) {
                val songs = musicRepository.getSongsByIds(playlist.songIds)
                _uiState.update {
                    it.copy(
                        currentPlaylistDetails = playlist,
                        currentPlaylistSongs = songs,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
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
}
//@HiltViewModel
//class PlaylistViewModel @Inject constructor(
//    private val userPreferencesRepository: UserPreferencesRepository,
//    private val musicRepository: MusicRepository // Para obtener detalles de canciones
//) : ViewModel() {
//
//    private val _uiState = MutableStateFlow(PlaylistUiState())
//    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()
//
//    init {
//        loadPlaylists()
//        // Cargar todas las canciones una vez para el selector de canciones
//        // Podría paginarse también si la lista es muy grande.
//        viewModelScope.launch {
//            _uiState.update { it.copy(isLoadingSongSelection = true) }
//            // Cargar todas las canciones (o una cantidad razonable) para el selector
//            // Esta es una simplificación; idealmente, el selector también sería paginado.
//            val allSongs = musicRepository.getAudioFiles(1, 1000) // Cargar hasta 1000 canciones
//            _uiState.update { it.copy(songSelectionForPlaylist = allSongs, isLoadingSongSelection = false) }
//        }
//    }
//
//    private fun loadPlaylists() {
//        viewModelScope.launch {
//            userPreferencesRepository.userPlaylistsFlow.collect { playlists ->
//                _uiState.update { it.copy(playlists = playlists.sortedByDescending { p -> p.lastModified }) }
//            }
//        }
//    }
//
//    fun loadPlaylistDetails(playlistId: String) {
//        viewModelScope.launch {
//            _uiState.update { it.copy(isLoading = true) }
//            val playlist = userPreferencesRepository.userPlaylistsFlow.first().find { it.id == playlistId }
//            if (playlist != null) {
//                val songs = musicRepository.getSongsByIds(playlist.songIds)
//                _uiState.update {
//                    it.copy(
//                        currentPlaylistDetails = playlist,
//                        currentPlaylistSongs = songs,
//                        isLoading = false
//                    )
//                }
//            } else {
//                _uiState.update { it.copy(isLoading = false) } // Playlist no encontrada
//            }
//        }
//    }
//
//    fun createPlaylist(name: String) {
//        viewModelScope.launch {
//            userPreferencesRepository.createPlaylist(name)
//            // El flujo userPlaylistsFlow se actualizará automáticamente
//        }
//    }
//
//    fun deletePlaylist(playlistId: String) {
//        viewModelScope.launch {
//            userPreferencesRepository.deletePlaylist(playlistId)
//        }
//    }
//
//    fun renamePlaylist(playlistId: String, newName: String) {
//        viewModelScope.launch {
//            userPreferencesRepository.renamePlaylist(playlistId, newName)
//            // Actualizar detalles si esta es la playlist actual
//            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
//                _uiState.update { it.copy(currentPlaylistDetails = it.currentPlaylistDetails?.copy(name = newName)) }
//            }
//        }
//    }
//
//    fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
//        viewModelScope.launch {
//            userPreferencesRepository.addSongsToPlaylist(playlistId, songIdsToAdd)
//            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
//                loadPlaylistDetails(playlistId) // Recargar canciones de la playlist actual
//            }
//        }
//    }
//
//    fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
//        viewModelScope.launch {
//            userPreferencesRepository.removeSongFromPlaylist(playlistId, songIdToRemove)
//            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
//                _uiState.update {
//                    it.copy(currentPlaylistSongs = it.currentPlaylistSongs.filterNot { s -> s.id == songIdToRemove })
//                }
//            }
//        }
//    }
//
//    fun reorderSongsInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
//        viewModelScope.launch {
//            val currentSongs = _uiState.value.currentPlaylistSongs.toMutableList()
//            if (fromIndex in currentSongs.indices && toIndex in currentSongs.indices) {
//                val item = currentSongs.removeAt(fromIndex)
//                currentSongs.add(toIndex, item)
//                val newSongOrderIds = currentSongs.map { it.id }
//                userPreferencesRepository.reorderSongsInPlaylist(playlistId, newSongOrderIds)
//                _uiState.update { it.copy(currentPlaylistSongs = currentSongs) } // Actualizar UI inmediatamente
//            }
//        }
//    }
//}