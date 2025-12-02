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
    val rule: TransitionRule? = null,
    val globalSettings: TransitionSettings = TransitionSettings(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val useGlobalDefaults: Boolean = false,
    val playlistId: String? = null
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
            val playlistRule = playlistId?.let { transitionRepository.getPlaylistDefaultRule(it).first() }
            val globalSettings = transitionRepository.getGlobalSettings().first()

            _uiState.update {
                it.copy(
                    rule = playlistRule,
                    globalSettings = globalSettings,
                    isLoading = false,
                    useGlobalDefaults = playlistRule == null,
                    isSaved = false,
                    playlistId = playlistId
                )
            }
        }
    }

    private fun getCurrentSettings(): TransitionSettings {
        val state = _uiState.value
        return if (state.useGlobalDefaults) {
            state.globalSettings
        } else {
            state.rule?.settings ?: state.globalSettings
        }
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

    fun updateCurveIn(curve: Curve) {
        val currentSettings = getCurrentSettings()
        val newSettings = currentSettings.copy(curveIn = curve)
        updateRuleWithNewSettings(newSettings)
    }

    fun updateCurveOut(curve: Curve) {
        val currentSettings = getCurrentSettings()
        val newSettings = currentSettings.copy(curveOut = curve)
        updateRuleWithNewSettings(newSettings)
    }

    private fun updateRuleWithNewSettings(newSettings: TransitionSettings) {
        val ruleToUpdate = _uiState.value.rule ?: TransitionRule(
            playlistId = playlistId ?: "",
            settings = TransitionSettings()
        )
        _uiState.update {
            it.copy(
                rule = ruleToUpdate.copy(settings = newSettings),
                isSaved = false,
                useGlobalDefaults = false
            )
        }
    }

    fun useGlobalDefaults() {
        if (playlistId == null) return
        _uiState.update {
            it.copy(
                rule = null,
                useGlobalDefaults = true,
                isSaved = false
            )
        }
    }

    fun enablePlaylistOverride() {
        if (playlistId == null) return
        val baseSettings = getCurrentSettings()
        val rule = _uiState.value.rule ?: TransitionRule(
            playlistId = playlistId,
            settings = baseSettings
        )
        _uiState.update {
            it.copy(
                rule = rule.copy(settings = baseSettings),
                useGlobalDefaults = false,
                isSaved = false
            )
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val ruleToSave = _uiState.value.rule

            if (playlistId != null) {
                when {
                    _uiState.value.useGlobalDefaults -> transitionRepository.deletePlaylistDefaultRule(playlistId)
                    ruleToSave != null && ruleToSave.settings.mode == TransitionMode.NONE ->
                        transitionRepository.deletePlaylistDefaultRule(playlistId)
                    ruleToSave != null -> transitionRepository.saveRule(ruleToSave)
                }
            } else {
                transitionRepository.saveGlobalSettings(getCurrentSettings())
            }
            loadSettings()
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
