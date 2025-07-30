
package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    init {
        val artistIdString: String? = savedStateHandle.get("artistId")
        if (artistIdString != null) {
            val artistId = artistIdString.toLongOrNull()
            if (artistId != null) {
                loadArtistData(artistId)
            } else {
                _uiState.update { it.copy(error = "El ID del artista no es válido.", isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = "Artist ID no encontrado", isLoading = false) }
        }
    }

    private fun loadArtistData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val artistDetailsFlow = musicRepository.getArtistById(id)
                val artistSongsFlow = musicRepository.getSongsForArtist(id)

                combine(artistDetailsFlow, artistSongsFlow) { artist, songs ->
                    if (artist != null) {
                        ArtistDetailUiState(
                            artist = artist,
                            songs = songs,
                            isLoading = false
                        )
                    } else {
                        ArtistDetailUiState(
                            error = "No se pudo encontrar el artista.",
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(ArtistDetailUiState(error = "Error al cargar datos del artista: ${e.localizedMessage}", isLoading = false))
                    }
                    .collect { newState ->
                        _uiState.value = newState
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Error al cargar datos del artista: ${e.localizedMessage}",
                        isLoading = false
                    )
                }
            }
        }
    }
}
