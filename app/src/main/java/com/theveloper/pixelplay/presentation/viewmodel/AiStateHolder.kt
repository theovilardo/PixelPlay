package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.ai.AiMetadataGenerator
import com.theveloper.pixelplay.data.ai.AiPlaylistGenerator
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages AI-powered features: AI Playlist Generation and AI Metadata Generation.
 * Extracted from PlayerViewModel.
 */
@Singleton
class AiStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val aiMetadataGenerator: AiMetadataGenerator,
    private val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dailyMixStateHolder: DailyMixStateHolder
) {
    // State
    private val _showAiPlaylistSheet = MutableStateFlow(false)
    val showAiPlaylistSheet = _showAiPlaylistSheet.asStateFlow()

    private val _isGeneratingAiPlaylist = MutableStateFlow(false)
    val isGeneratingAiPlaylist = _isGeneratingAiPlaylist.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError = _aiError.asStateFlow()

    private val _isGeneratingMetadata = MutableStateFlow(false)
    val isGeneratingMetadata = _isGeneratingMetadata.asStateFlow()

    private var scope: CoroutineScope? = null
    private var allSongsProvider: (() -> List<Song>)? = null
    private var favoriteSongIdsProvider: (() -> Set<String>)? = null
    
    // Callbacks to interact with PlayerViewModel/UI
    private var toastEmitter: ((String) -> Unit)? = null
    private var playSongsCallback: ((List<Song>, Song, String) -> Unit)? = null // songs, startSong, queueName
    private var openPlayerSheetCallback: (() -> Unit)? = null

    fun initialize(
        scope: CoroutineScope,
        allSongsProvider: () -> List<Song>,
        favoriteSongIdsProvider: () -> Set<String>,
        toastEmitter: (String) -> Unit,
        playSongsCallback: (List<Song>, Song, String) -> Unit,
        openPlayerSheetCallback: () -> Unit
    ) {
        this.scope = scope
        this.allSongsProvider = allSongsProvider
        this.favoriteSongIdsProvider = favoriteSongIdsProvider
        this.toastEmitter = toastEmitter
        this.playSongsCallback = playSongsCallback
        this.openPlayerSheetCallback = openPlayerSheetCallback
    }

    fun showAiPlaylistSheet() {
        _showAiPlaylistSheet.value = true
    }

    fun dismissAiPlaylistSheet() {
        _showAiPlaylistSheet.value = false
        _aiError.value = null
        _isGeneratingAiPlaylist.value = false
    }

    fun generateAiPlaylist(prompt: String, minLength: Int, maxLength: Int, saveAsPlaylist: Boolean = false) {
        val scope = this.scope ?: return
        val allSongs = allSongsProvider?.invoke() ?: emptyList()
        val favoriteIds = favoriteSongIdsProvider?.invoke() ?: emptySet()

        scope.launch {
            _isGeneratingAiPlaylist.value = true
            _aiError.value = null

            try {
                // Generate candidate pool using DailyMixManager logic
                val candidatePool = dailyMixManager.generateDailyMix(
                    allSongs = allSongs,
                    favoriteSongIds = favoriteIds,
                    limit = 120
                )

                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongs,
                    minLength = minLength,
                    maxLength = maxLength,
                    candidateSongs = candidatePool
                )

                result.onSuccess { generatedSongs ->
                    if (generatedSongs.isNotEmpty()) {
                        if (saveAsPlaylist) {
                            val playlistName = "AI: ${prompt.take(25)}${if (prompt.length > 25) "..." else ""}"
                            val songIds = generatedSongs.map { it.id }
                            userPreferencesRepository.createPlaylist(
                                name = playlistName,
                                songIds = songIds,
                                isAiGenerated = true
                            )
                            toastEmitter?.invoke("AI Playlist '$playlistName' created!")
                            dismissAiPlaylistSheet()
                        } else {
                            // Play immediately logic
                            dailyMixStateHolder.setDailyMixSongs(generatedSongs)
                            playSongsCallback?.invoke(generatedSongs, generatedSongs.first(), "AI: $prompt")
                            openPlayerSheetCallback?.invoke()
                            dismissAiPlaylistSheet()
                        }
                    } else {
                        _aiError.value = context.getString(R.string.ai_no_songs_found)
                    }
                }.onFailure { error ->
                    _aiError.value = if (error.message?.contains("API Key") == true) {
                        context.getString(R.string.ai_error_api_key)
                    } else {
                        context.getString(R.string.ai_error_generic, error.message ?: "")
                    }
                }
            } finally {
                _isGeneratingAiPlaylist.value = false
            }
        }
    }

    fun regenerateDailyMixWithPrompt(prompt: String) {
        val scope = this.scope ?: return
        val allSongs = allSongsProvider?.invoke() ?: emptyList()
        val favoriteIds = favoriteSongIdsProvider?.invoke() ?: emptySet()
        val currentDailyMixSongs = dailyMixStateHolder.dailyMixSongs.value

        scope.launch {
            if (prompt.isBlank()) {
                toastEmitter?.invoke(context.getString(R.string.ai_prompt_empty))
                return@launch
            }

            _isGeneratingAiPlaylist.value = true
            _aiError.value = null

            try {
                val desiredSize = currentDailyMixSongs.size.takeIf { it > 0 } ?: 25
                val minLength = (desiredSize * 0.6).toInt().coerceAtLeast(10)
                val maxLength = desiredSize.coerceAtLeast(20)
                
                val candidatePool = dailyMixManager.generateDailyMix(
                    allSongs = allSongs,
                    favoriteSongIds = favoriteIds,
                    limit = 100
                )

                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongs,
                    minLength = minLength,
                    maxLength = maxLength,
                    candidateSongs = candidatePool
                )

                result.onSuccess { generatedSongs ->
                    if (generatedSongs.isNotEmpty()) {
                        dailyMixStateHolder.setDailyMixSongs(generatedSongs)
                        toastEmitter?.invoke(context.getString(R.string.ai_daily_mix_updated))
                    } else {
                        toastEmitter?.invoke(context.getString(R.string.ai_no_songs_for_mix))
                    }
                }.onFailure { error ->
                    _aiError.value = error.message
                    toastEmitter?.invoke(context.getString(R.string.could_not_update, error.message ?: ""))
                }
            } finally {
                _isGeneratingAiPlaylist.value = false
            }
        }
    }

    suspend fun generateAiMetadata(song: Song, fields: List<String>): Result<SongMetadata> {
        _isGeneratingMetadata.value = true
        return try {
            aiMetadataGenerator.generate(song, fields)
        } finally {
            _isGeneratingMetadata.value = false
        }
    }

    fun onCleared() {
        scope = null
    }
}
