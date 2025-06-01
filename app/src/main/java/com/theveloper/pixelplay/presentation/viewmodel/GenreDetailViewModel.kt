package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.datasource.GenreDataSource // For fetching genre name by ID
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GenreDetailUiState(
    val genre: Genre? = null,
    val songs: List<Song> = emptyList(),
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
            _uiState.value = _uiState.value.copy(isLoadingGenreName = true)
            val genre = GenreDataSource.staticGenres.find { it.id == id }
            if (genre != null) {
                _uiState.value = _uiState.value.copy(genre = genre, isLoadingGenreName = false)
            } else {
                _uiState.value = _uiState.value.copy(error = (_uiState.value.error ?: "") + " Genre name not found.", isLoadingGenreName = false)
            }
        }
    }

    // Updated function
    private fun fetchSongsForGenre(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSongs = true, error = null) // Reset error specific to song fetching
            try {
                val songs = musicRepository.getMusicByGenre(id)
                _uiState.value = _uiState.value.copy(songs = songs, isLoadingSongs = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = (_uiState.value.error ?: "") + " Failed to load songs: ${e.message}", isLoadingSongs = false)
            }
        }
    }
}
