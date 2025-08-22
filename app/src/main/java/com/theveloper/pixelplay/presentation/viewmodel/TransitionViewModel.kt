package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Curve
import com.theveloper.pixelplay.data.model.TransitionMode
import com.theveloper.pixelplay.data.model.TransitionRule
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.data.repository.TransitionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransitionUiState(
    val settings: TransitionSettings = TransitionSettings(),
    val isPlaylistRule: Boolean = false,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false
)

@HiltViewModel
class TransitionViewModel @Inject constructor(
    private val transitionRepository: TransitionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String? = savedStateHandle["playlistId"]

    private val _uiState = MutableStateFlow(TransitionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settingsFlow = if (playlistId != null) {
                transitionRepository.getPlaylistOrDefaultSettings(playlistId)
            } else {
                // For this ViewModel, null playlistId means we edit global settings
                transitionRepository.getGlobalSettings()
            }

            settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        settings = settings,
                        isPlaylistRule = playlistId != null,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateDuration(durationMs: Int) {
        _uiState.update { it.copy(settings = it.settings.copy(durationMs = durationMs), isSaved = false) }
    }

    fun updateMode(mode: TransitionMode) {
        _uiState.update { it.copy(settings = it.settings.copy(mode = mode), isSaved = false) }
    }

    fun updateCurve(curve: Curve) {
        // For V1, apply the same curve to both fade-in and fade-out for simplicity.
        _uiState.update {
            it.copy(
                settings = it.settings.copy(curveIn = curve, curveOut = curve),
                isSaved = false
            )
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val currentSettings = _uiState.value.settings
            if (playlistId != null) {
                // This creates/updates the default rule for the playlist.
                val rule = TransitionRule(
                    playlistId = playlistId,
                    settings = currentSettings
                )
                transitionRepository.saveRule(rule)
            } else {
                transitionRepository.saveGlobalSettings(currentSettings)
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
