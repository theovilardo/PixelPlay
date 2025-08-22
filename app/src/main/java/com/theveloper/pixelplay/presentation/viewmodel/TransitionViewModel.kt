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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransitionUiState(
    // The rule object itself is now the state. It can be null if no rule is set.
    val rule: TransitionRule? = null,
    // We also hold the global settings separately to use as a fallback for display.
    val globalSettings: TransitionSettings = TransitionSettings(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false
)

@HiltViewModel
class TransitionViewModel @Inject constructor(
    private val transitionRepository: TransitionRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: String? = savedStateHandle["playlistId"]

    private val _uiState = MutableStateFlow(TransitionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Fetch both the specific rule (if it exists) and the global settings.
            val playlistRule = playlistId?.let { transitionRepository.getPlaylistDefaultRule(it).first() }
            val globalSettings = transitionRepository.getGlobalSettings().first()

            _uiState.update {
                it.copy(
                    rule = playlistRule,
                    globalSettings = globalSettings,
                    isLoading = false
                )
            }
        }
    }

    // The settings currently displayed to the user.
    // If a specific rule exists for the playlist, use its settings.
    // Otherwise, fall back to the global settings.
    private fun getCurrentSettings(): TransitionSettings {
        return _uiState.value.rule?.settings ?: _uiState.value.globalSettings
    }

    fun updateDuration(durationMs: Int) {
        val currentSettings = getCurrentSettings()
        val newSettings = currentSettings.copy(durationMs = durationMs)
        updateRuleWithNewSettings(newSettings)
    }

    fun updateMode(mode: TransitionMode) {
        val currentSettings = getCurrentSettings()
        val newSettings = currentSettings.copy(mode = mode)
        updateRuleWithNewSettings(newSettings)
    }

    fun updateCurve(curve: Curve) {
        val currentSettings = getCurrentSettings()
        val newSettings = currentSettings.copy(curveIn = curve, curveOut = curve)
        updateRuleWithNewSettings(newSettings)
    }

    private fun updateRuleWithNewSettings(newSettings: TransitionSettings) {
        // If a rule didn't exist, create a new one to hold the new settings.
        val ruleToUpdate = _uiState.value.rule ?: TransitionRule(
            playlistId = playlistId ?: "", // Should not be null if we are updating a playlist rule
            settings = TransitionSettings() // Start with default settings
        )
        _uiState.update {
            it.copy(rule = ruleToUpdate.copy(settings = newSettings), isSaved = false)
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val ruleToSave = _uiState.value.rule

            if (playlistId != null) {
                if (ruleToSave != null) {
                    // If the mode is NONE, we delete the rule.
                    // Otherwise, we save (upsert) it.
                    if (ruleToSave.settings.mode == TransitionMode.NONE) {
                        transitionRepository.deletePlaylistDefaultRule(playlistId)
                    } else {
                        transitionRepository.saveRule(ruleToSave)
                    }
                }
            } else {
                // Editing global settings. The rule object is not used here.
                transitionRepository.saveGlobalSettings(getCurrentSettings())
            }
            // After saving, reload the settings from the source of truth.
            loadSettings()
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
