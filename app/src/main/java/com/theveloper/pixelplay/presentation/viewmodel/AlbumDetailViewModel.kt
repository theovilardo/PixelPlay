package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository // Importar MusicRepository
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
    private val musicRepository: MusicRepository, // Inyectar MusicRepository
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private val albumIdString: String? = savedStateHandle.get<String>("albumId")

    init {
        albumIdString?.let { idStr ->
            val idLong = idStr.toLongOrNull()
            if (idLong != null) {
                loadAlbumData(idLong)
            } else {
                _uiState.update { it.copy(error = "Album ID inválido: $idStr", isLoading = false) }
            }
        } ?: run {
            _uiState.update { it.copy(error = "Album ID no encontrado en SavedStateHandle", isLoading = false) }
        }
    }

    private fun loadAlbumData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Simulación de llamadas al MusicRepository
                // En un caso real:
                // val albumDetails = musicRepository.getAlbumDetails(id).first()
                // val albumSongs = musicRepository.getSongsForAlbum(id).first()

                // --- Inicio de Datos de Placeholder Corregidos ---
                val placeholderAlbum = Album(
                    id = id, // Usa el Long ID
                    title = "Nombre del Álbum Cargado",
                    artist = "Artista del Álbum Cargado",
                    albumArtUriString = "content://media/external/audio/albumart/$id", // URI de ejemplo
                    songCount = 2 // Actualizar si la lista de canciones cambia
                )

                val placeholderSongs = listOf(
                    Song(
                        id = "song_1_${id}", // ID único de canción
                        title = "Canción Cargada 1",
                        artist = placeholderAlbum.artist,
                        artistId = 101L, // Placeholder artistId
                        album = placeholderAlbum.title,
                        albumId = id, // ID del álbum actual
                        contentUriString = "content://media/external/audio/media/song_1_${id}", // URI de ejemplo
                        albumArtUriString = placeholderAlbum.albumArtUriString,
                        duration = 190000L, // Long
                        genre = "Pop"
                    ),
                    Song(
                        id = "song_2_${id}",
                        title = "Canción Cargada 2",
                        artist = placeholderAlbum.artist,
                        artistId = 101L, // Placeholder artistId
                        album = placeholderAlbum.title,
                        albumId = id,
                        contentUriString = "content://media/external/audio/media/song_2_${id}",
                        albumArtUriString = placeholderAlbum.albumArtUriString,
                        duration = 210000L, // Long
                        genre = "Rock"
                    )
                )
                // --- Fin de Datos de Placeholder Corregidos ---

                // Simular una pequeña demora como si fuera una llamada de red/DB
                kotlinx.coroutines.delay(500)

                _uiState.update {
                    it.copy(
                        album = placeholderAlbum,
                        songs = placeholderSongs,
                        isLoading = false
                    )
                }

            } catch (e: NumberFormatException) {
                 _uiState.update {
                    it.copy(
                        error = "Error de formato en Album ID: ${e.localizedMessage}",
                        isLoading = false
                    )
                }
            }catch (e: Exception) {
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
