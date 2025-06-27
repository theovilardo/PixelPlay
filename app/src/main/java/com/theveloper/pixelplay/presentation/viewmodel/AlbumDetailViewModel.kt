package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.domain.usecase.GetAlbumDetailsUseCase
import com.theveloper.pixelplay.domain.usecase.GetSongsForAlbumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    private val getAlbumDetailsUseCase: GetAlbumDetailsUseCase, // Se creará/inyectará después
    private val getSongsForAlbumUseCase: GetSongsForAlbumUseCase, // Se creará/inyectará después
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private val albumId: String? = savedStateHandle.get<String>("albumId")

    init {
        albumId?.let {
            loadAlbumData(it)
        } ?: run {
            _uiState.update { it.copy(error = "Album ID no encontrado", isLoading = false) }
        }
    }

    private fun loadAlbumData(id: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Simulación de carga - Reemplazar con llamadas a casos de uso reales
                // val albumDetails = getAlbumDetailsUseCase(id)
                // val albumSongs = getSongsForAlbumUseCase(id)

                // Datos de placeholder mientras no tengamos los casos de uso
                val placeholderAlbum = Album(
                    id = id,
                    title = "Nombre del Álbum Cargado",
                    artist = "Artista Cargado",
                    albumArtUriString = "https.via.placeholder.com/600/771796", // Otra imagen
                    songCount = 2
                )
                val placeholderSongs = listOf(
                    Song(id = "s1", title = "Canción Cargada 1", artist = "Artista Cargado", album = placeholderAlbum.title, duration = 190000, path = "", albumId = id, albumArtUriString = placeholderAlbum.albumArtUriString),
                    Song(id = "s2", title = "Canción Cargada 2", artist = "Artista Cargado", album = placeholderAlbum.title, duration = 210000, path = "", albumId = id, albumArtUriString = placeholderAlbum.albumArtUriString)
                )

                _uiState.update {
                    it.copy(
                        album = placeholderAlbum, // albumDetails
                        songs = placeholderSongs, // albumSongs
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Error al cargar datos del álbum: ${e.localizedMessage}",
                        isLoading = false
                    )
                }
            }
        }
    }
}
