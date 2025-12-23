
package com.theveloper.pixelplay.presentation.viewmodel

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
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
    val albumSections: List<ArtistAlbumSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@Immutable
data class ArtistAlbumSection(
    val albumId: Long,
    val title: String,
    val year: Int?,
    val albumArtUriString: String?,
    val songs: List<Song>
)

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val artistImageRepository: ArtistImageRepository,
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
            Log.d("ArtistDebug", "loadArtistData: id=$id")
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val artistDetailsFlow = musicRepository.getArtistById(id)
                val artistSongsFlow = musicRepository.getSongsForArtist(id)

                combine(artistDetailsFlow, artistSongsFlow) { artist, songs ->
                    Log.d("ArtistDebug", "loadArtistData: id=$id found=${artist != null} songs=${songs.size}")
                    if (artist != null) {
                        val albumSections = buildAlbumSections(songs)
                        val orderedSongs = albumSections.flatMap { it.songs }
                        ArtistDetailUiState(
                            artist = artist,
                            songs = orderedSongs,
                            albumSections = albumSections,
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
                        
                        // Fetch artist image from Deezer if not already cached
                        newState.artist?.let { artist ->
                            if (artist.imageUrl.isNullOrEmpty()) {
                                launch {
                                    try {
                                        val imageUrl = artistImageRepository.getArtistImageUrl(artist.name, artist.id)
                                        if (!imageUrl.isNullOrEmpty()) {
                                            _uiState.update { state ->
                                                state.copy(artist = state.artist?.copy(imageUrl = imageUrl))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ArtistDebug", "Failed to fetch artist image: ${e.message}")
                                    }
                                }
                            }
                        }
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
    fun removeSongFromAlbumSection(songId: String) {
        _uiState.update { currentState ->
            val updatedAlbumSections = currentState.albumSections.map { section ->
                // Remove the song from this section if it exists
                val updatedSongs = section.songs.filterNot { it.id == songId }
                // Return updated section only if it still has songs, otherwise filter out empty sections
                section.copy(songs = updatedSongs)
            }.filter { it.songs.isNotEmpty() } // Remove empty album sections

            currentState.copy(
                albumSections = updatedAlbumSections,
                songs = currentState.songs.filterNot { it.id == songId } // Also update the main songs list
            )
        }
    }
}

private val songDisplayComparator = compareBy<Song> {
    if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
}.thenBy { it.title.lowercase() }

private fun buildAlbumSections(songs: List<Song>): List<ArtistAlbumSection> {
    if (songs.isEmpty()) return emptyList()

    val sections = songs
        .groupBy { it.albumId to it.album }
        .map { (key, albumSongs) ->
            val sortedSongs = albumSongs.sortedWith(songDisplayComparator)
            val albumYear = albumSongs.mapNotNull { song -> song.year.takeIf { it > 0 } }.maxOrNull()
            val albumArtUri = albumSongs.firstNotNullOfOrNull { it.albumArtUriString }
            ArtistAlbumSection(
                albumId = key.first,
                title = (key.second.takeIf { it.isNotBlank() } ?: "Unknown Album"),
                year = albumYear,
                albumArtUriString = albumArtUri,
                songs = sortedSongs
            )
        }

    val (withYear, withoutYear) = sections.partition { it.year != null }
    val withYearSorted = withYear.sortedWith(
        compareByDescending<ArtistAlbumSection> { it.year ?: Int.MIN_VALUE }
            .thenBy { it.title.lowercase() }
    )
    val withoutYearSorted = withoutYear.sortedBy { it.title.lowercase() }

    return withYearSorted + withoutYearSorted
}
