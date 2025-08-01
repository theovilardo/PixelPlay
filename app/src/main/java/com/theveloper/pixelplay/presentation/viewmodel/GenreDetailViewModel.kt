package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.foundation.gestures.forEach
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Song

import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Define Item Types for LazyColumn
sealed interface GroupedSongListItem {
    data class ArtistHeader(val name: String) : GroupedSongListItem
    data class AlbumHeader(val name: String, val artistName: String, val albumArtUri: String?) : GroupedSongListItem
    data class SongItem(val song: Song) : GroupedSongListItem
}

data class GenreDetailUiState(
    val genre: Genre? = null,
    val songs: List<Song> = emptyList(), // Keep the flat list for playback context
    val groupedSongs: List<GroupedSongListItem> = emptyList(), // For display
    val isLoadingGenreName: Boolean = false, // For initial genre name loading
    val isLoadingSongs: Boolean = false,    // For song loading
    val error: String? = null
)

@HiltViewModel
class GenreDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreDetailUiState())
    val uiState: StateFlow<GenreDetailUiState> = _uiState.asStateFlow()

    private val genreId: String? = savedStateHandle.get<String>("genreId")?.let { java.net.URLDecoder.decode(it, "UTF-8") }

    init {
        if (genreId != null) {
            loadGenreName(genreId) // Load genre name first
            fetchSongsForGenre(genreId) // Then load songs
        } else {
            _uiState.value = _uiState.value.copy(error = "Genre ID not found", isLoadingGenreName = false, isLoadingSongs = false)
        }
    }

    private fun loadGenreName(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingGenreName = true, error = null) // Clear previous error
            if (id.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    error = "Genre ID is blank.",
                    isLoadingGenreName = false
                )
                return@launch
            }

            try {
                val genres = musicRepository.getGenres().first() // Get all genres from the repository
                val foundGenre = genres.find { it.id.equals(id, ignoreCase = true) }

                if (foundGenre != null) {
                    _uiState.value = _uiState.value.copy(genre = foundGenre, isLoadingGenreName = false)
                } else {
                    // If not found, it means there are no songs with this genre in the library.
                    // We can still create a basic Genre object for display, but it will be empty.
                    val dynamicGenre = Genre(
                        id = id,
                        name = id, // Use id as name
                        lightColorHex = "#9E9E9E", // Default grey
                        onLightColorHex = "#000000", // Default black
                        darkColorHex = "#616161", // Default dark grey
                        onDarkColorHex = "#FFFFFF"
                    )
                    _uiState.value = _uiState.value.copy(genre = dynamicGenre, isLoadingGenreName = false)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load genre details: ${e.message}",
                    isLoadingGenreName = false
                )
            }
        }
    }

    // Updated function
    private fun fetchSongsForGenre(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSongs = true, error = null)
            try {
                // Colecta la lista del Flow.
                // Si getMusicByGenre siempre emite una sola lista actualizada, 'first()' es apropiado.
                // Si pudiera emitir múltiples listas a lo largo del tiempo y quieres reaccionar a cada una,
                // necesitarías una estructura diferente o usar .collect { listOfSongs -> ... }
                val listOfSongs = musicRepository.getMusicByGenre(id).first() // Obtiene la List<Song>

                val newGroupedList = mutableListOf<GroupedSongListItem>()

                // Ahora 'listOfSongs' es una List<Song>, y groupBy funcionará.
                listOfSongs.groupBy { it.artist }
                    .forEach { (artistName, artistSongs) ->
                        newGroupedList.add(GroupedSongListItem.ArtistHeader(artistName))
                        artistSongs.groupBy { it.album } // Esto también opera sobre una List<Song> (artistSongs)
                            .forEach { (albumName, albumSongs) ->
                                val albumArtUri = albumSongs.firstOrNull()?.albumArtUriString // albumArtUriString es String?
                                newGroupedList.add(GroupedSongListItem.AlbumHeader(albumName, artistName, albumArtUri))
                                albumSongs.forEach { song ->
                                    newGroupedList.add(GroupedSongListItem.SongItem(song))
                                }
                            }
                    }

                _uiState.value = _uiState.value.copy(
                    songs = listOfSongs, // Ahora 'listOfSongs' es la lista real, no el Flow
                    groupedSongs = newGroupedList,
                    isLoadingSongs = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = (_uiState.value.error ?: "") + " Failed to load songs: ${e.message}",
                    isLoadingSongs = false
                )
            }
        }
    }
}
