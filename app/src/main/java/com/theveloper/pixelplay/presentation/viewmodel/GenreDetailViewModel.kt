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

    init {
        savedStateHandle.get<String>("genreId")?.let { genreId ->
            val decodedGenreId = java.net.URLDecoder.decode(genreId, "UTF-8")
            loadGenreDetails(decodedGenreId)
        } ?: run {
            _uiState.value = _uiState.value.copy(error = "Genre ID not found", isLoadingGenreName = false, isLoadingSongs = false)
        }
    }

    private fun loadGenreDetails(genreId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingGenreName = true, error = null)

            try {
                // Step 1: Find the genre object by its ID.
                val genres = musicRepository.getGenres().first()
                val foundGenre = genres.find { it.id.equals(genreId, ignoreCase = true) }
                    ?: Genre(
                        id = genreId,
                        name = genreId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, // Fallback name from ID
                        lightColorHex = "#9E9E9E", onLightColorHex = "#000000",
                        darkColorHex = "#616161", onDarkColorHex = "#FFFFFF"
                    )

                _uiState.value = _uiState.value.copy(genre = foundGenre, isLoadingGenreName = false, isLoadingSongs = true)

                // Step 2: Fetch songs using the genre's NAME.
                val listOfSongs = musicRepository.getMusicByGenre(foundGenre.name).first()

                // Step 3: Group the songs for display.
                val groupedSongs = groupSongs(listOfSongs)

                _uiState.value = _uiState.value.copy(
                    songs = listOfSongs,
                    groupedSongs = groupedSongs,
                    isLoadingSongs = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load genre details: ${e.message}",
                    isLoadingGenreName = false,
                    isLoadingSongs = false
                )
            }
        }
    }

    private fun groupSongs(songs: List<Song>): List<GroupedSongListItem> {
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
        return newGroupedList
    }
}
