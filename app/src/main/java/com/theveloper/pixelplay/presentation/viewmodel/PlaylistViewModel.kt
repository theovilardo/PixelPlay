package com.theveloper.pixelplay.presentation.viewmodel

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
    val songSelectionForPlaylist: List<Song> = emptyList(), // Para el diálogo de selección
    val isLoadingSongSelection: Boolean = false
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository // Para obtener detalles de canciones
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        loadPlaylists()
        // Cargar todas las canciones una vez para el selector de canciones
        // Podría paginarse también si la lista es muy grande.
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSongSelection = true) }
            // Cargar todas las canciones (o una cantidad razonable) para el selector
            // Esta es una simplificación; idealmente, el selector también sería paginado.
            val allSongs = musicRepository.getAudioFiles(1, 1000) // Cargar hasta 1000 canciones
            _uiState.update { it.copy(songSelectionForPlaylist = allSongs, isLoadingSongSelection = false) }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            userPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                _uiState.update { it.copy(playlists = playlists.sortedByDescending { p -> p.lastModified }) }
            }
        }
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
                _uiState.update { it.copy(isLoading = false) } // Playlist no encontrada
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.createPlaylist(name)
            // El flujo userPlaylistsFlow se actualizará automáticamente
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
            // Actualizar detalles si esta es la playlist actual
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                _uiState.update { it.copy(currentPlaylistDetails = it.currentPlaylistDetails?.copy(name = newName)) }
            }
        }
    }

    fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.addSongsToPlaylist(playlistId, songIdsToAdd)
            if (_uiState.value.currentPlaylistDetails?.id == playlistId) {
                loadPlaylistDetails(playlistId) // Recargar canciones de la playlist actual
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
                _uiState.update { it.copy(currentPlaylistSongs = currentSongs) } // Actualizar UI inmediatamente
            }
        }
    }
}