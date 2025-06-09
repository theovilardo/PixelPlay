package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.datasource.GenreDataSource
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val genreId: String? = savedStateHandle.get<String>("genreId")

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

            val staticGenre = GenreDataSource.staticGenres.find { it.id.equals(id, ignoreCase = true) } // Case-insensitive find

            if (staticGenre != null) {
                _uiState.value = _uiState.value.copy(genre = staticGenre, isLoadingGenreName = false)
            } else {
                // If not found in static genres, create a dynamic one.
                // The ID itself is treated as the name for dynamic genres.
                val dynamicGenre = Genre(
                    id = id,
                    name = id, // Use id as name
                    iconResId = null, // No icon for dynamic genres by default
                    lightColorHex = null, // Colors will be handled by fallback in UI
                    onLightColorHex = null,
                    darkColorHex = null,
                    onDarkColorHex = null
                )
                _uiState.value = _uiState.value.copy(genre = dynamicGenre, isLoadingGenreName = false)
            }
        }
    }

    // Updated function
    private fun fetchSongsForGenre(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSongs = true, error = null) // Reset error specific to song fetching
            try {
                val songs = musicRepository.getMusicByGenre(id) // Flat list
                val newGroupedList = mutableListOf<GroupedSongListItem>()
                songs.groupBy { it.artist }
                    .forEach { (artistName, artistSongs) ->
                        newGroupedList.add(GroupedSongListItem.ArtistHeader(artistName))
                        artistSongs.groupBy { it.album }
                            .forEach { (albumName, albumSongs) ->
                                val albumArtUri = albumSongs.firstOrNull()?.albumArtUriString
                                newGroupedList.add(GroupedSongListItem.AlbumHeader(albumName, artistName, albumArtUri))
                                albumSongs.forEach { song ->
                                    newGroupedList.add(GroupedSongListItem.SongItem(song))
                                }
                            }
                    }
                _uiState.value = _uiState.value.copy(
                    songs = songs, // Keep original flat list for playback
                    groupedSongs = newGroupedList,
                    isLoadingSongs = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = (_uiState.value.error ?: "") + " Failed to load songs: ${e.message}", isLoadingSongs = false)
            }
        }
    }
}
