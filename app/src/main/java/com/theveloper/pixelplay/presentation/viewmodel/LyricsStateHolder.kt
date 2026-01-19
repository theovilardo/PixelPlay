package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Callback interface for lyrics loading results.
 * Used to update StablePlayerState in PlayerViewModel.
 */
interface LyricsLoadCallback {
    fun onLoadingStarted(songId: String)
    fun onLyricsLoaded(songId: String, lyrics: Lyrics?)
}

/**
 * Manages lyrics loading, search state, and sync offset.
 * Extracted from PlayerViewModel to improve modularity.
 */
@Singleton
class LyricsStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var scope: CoroutineScope? = null
    private var loadingJob: Job? = null
    private var loadCallback: LyricsLoadCallback? = null

    // Sync offset per song in milliseconds
    private val _currentSongSyncOffset = MutableStateFlow(0)
    val currentSongSyncOffset: StateFlow<Int> = _currentSongSyncOffset.asStateFlow()

    // Lyrics search UI state
    private val _searchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val searchUiState: StateFlow<LyricsSearchUiState> = _searchUiState.asStateFlow()

    /**
     * Initialize with coroutine scope and callback from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope, callback: LyricsLoadCallback) {
        scope = coroutineScope
        loadCallback = callback
    }

    /**
     * Load lyrics for a song.
     * @param song The song to load lyrics for
     * @param sourcePreference The preferred source for lyrics
     */
    fun loadLyricsForSong(song: Song, sourcePreference: LyricsSourcePreference) {
        loadingJob?.cancel()
        val targetSongId = song.id

        loadingJob = scope?.launch {
            loadCallback?.onLoadingStarted(targetSongId)

            val fetchedLyrics = try {
                withContext(Dispatchers.IO) {
                    musicRepository.getLyrics(
                        song = song,
                        sourcePreference = sourcePreference
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                null
            }

            loadCallback?.onLyricsLoaded(targetSongId, fetchedLyrics)
        }
    }

    /**
     * Cancel any ongoing lyrics loading.
     */
    fun cancelLoading() {
        loadingJob?.cancel()
    }

    /**
     * Set sync offset for a song.
     */
    fun setSyncOffset(songId: String, offsetMs: Int) {
        scope?.launch {
            userPreferencesRepository.setLyricsSyncOffset(songId, offsetMs)
            _currentSongSyncOffset.value = offsetMs
        }
    }

    /**
     * Update sync offset from song ID (called when song changes).
     */
    suspend fun updateSyncOffsetForSong(songId: String) {
        val offset = userPreferencesRepository.getLyricsSyncOffset(songId)
        _currentSongSyncOffset.value = offset
    }

    /**
     * Set the lyrics search UI state.
     */
    fun setSearchState(state: LyricsSearchUiState) {
        _searchUiState.value = state
    }

    /**
     * Reset the lyrics search state to idle.
     */
    fun resetSearchState() {
        _searchUiState.value = LyricsSearchUiState.Idle
    }

    fun onCleared() {
        loadingJob?.cancel()
        scope = null
        loadCallback = null
    }
}
