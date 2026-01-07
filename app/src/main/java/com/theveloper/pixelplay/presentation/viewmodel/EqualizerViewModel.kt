package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.equalizer.EqualizerManager
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json // Added import

data class EqualizerUiState(
    val isEnabled: Boolean = false,
    val currentPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Float = 0f, // Changed to Float
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Float = 0f, // Changed to Float
    val loudnessEnhancerEnabled: Boolean = false,
    val loudnessEnhancerStrength: Float = 0f, // Changed to Float
    val isBassBoostSupported: Boolean = true,
    val isVirtualizerSupported: Boolean = true,
    val isLoudnessEnhancerSupported: Boolean = true,
    val viewMode: UserPreferencesRepository.EqualizerViewMode = UserPreferencesRepository.EqualizerViewMode.SLIDERS,
    val isBassBoostDismissed: Boolean = false,
    val isVirtualizerDismissed: Boolean = false,
    val isLoudnessDismissed: Boolean = false,
    val customPresets: List<EqualizerPreset> = emptyList(), // Added
    val pinnedPresetsNames: List<String> = emptyList(), // Added
) {
    // Computed property for accessible presets (Pinned)
    val accessiblePresets: List<EqualizerPreset>
        get() {
            // Map pinned names to actual Presets (Default or Custom)
            return pinnedPresetsNames.mapNotNull { name ->
                // First check custom presets
                customPresets.find { it.name == name }
                    ?: EqualizerPreset.fromName(name) // Then standard defaults
            }
        }
        
    // Computed property for All Available Presets (for Edit Sheet)
    val allAvailablePresets: List<EqualizerPreset>
        get() = EqualizerPreset.ALL_PRESETS + customPresets
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "EqualizerViewModel"
        private val json = Json { ignoreUnknownKeys = true } // Assuming Json is needed
    }

    private val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    
    // UI-only state for view mode
    // UI-only state for view mode - Now persisted
    // val isGraphView: StateFlow<Boolean> = _isGraphView.asStateFlow() // Removed local state

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private val _systemVolume = MutableStateFlow(0f)
    val systemVolume: StateFlow<Float> = _systemVolume.asStateFlow()
    
    init {
        initializeEqualizer()
        observeEqualizerState()
        loadSystemVolume()
    }
    
    private fun loadSystemVolume() {
        try {
            val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            _systemVolume.value = if (max > 0) current.toFloat() / max.toFloat() else 0.5f
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load system volume")
        }
    }

    fun setSystemVolume(percent: Float) {
        viewModelScope.launch {
            try {
                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val target = (percent * max).roundToInt().coerceIn(0, max)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0) // flag 0 to not show system UI
                _systemVolume.value = percent
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to set system volume")
            }
        }
    }
    
    private fun initializeEqualizer() {
        viewModelScope.launch {
            Timber.tag(TAG).d("Initializing equalizer...")
            
            // Restore preferences first
            val enabled = userPreferencesRepository.equalizerEnabledFlow.first()
            val presetName = userPreferencesRepository.equalizerPresetFlow.first()
            val customBands = userPreferencesRepository.equalizerCustomBandsFlow.first()
            val bassBoostEnabled = userPreferencesRepository.bassBoostEnabledFlow.first()
            val bassBoost = userPreferencesRepository.bassBoostStrengthFlow.first()
            val virtualizerEnabled = userPreferencesRepository.virtualizerEnabledFlow.first()
            val virtualizer = userPreferencesRepository.virtualizerStrengthFlow.first()
            val loudnessEnabled = userPreferencesRepository.loudnessEnhancerEnabledFlow.first()
            val loudnessStrength = userPreferencesRepository.loudnessEnhancerStrengthFlow.first()
            
            // Restore state in manager before attaching
            equalizerManager.restoreState(
                enabled, presetName, customBands, 
                bassBoostEnabled, bassBoost, 
                virtualizerEnabled, virtualizer,
                loudnessEnabled, loudnessStrength
            )
            
            // Attach to initial audio session
            val initialSessionId = dualPlayerEngine.getAudioSessionId()
            if (initialSessionId != 0) {
                equalizerManager.attachToAudioSession(initialSessionId)
            }
            
            // Update UI state with device capabilities
            _uiState.value = _uiState.value.copy(
                isBassBoostSupported = equalizerManager.isBassBoostSupported(),
                isVirtualizerSupported = equalizerManager.isVirtualizerSupported(),
                isLoudnessEnhancerSupported = equalizerManager.isLoudnessEnhancerSupported()
            )

            // Observe audio session ID changes from DualPlayerEngine
            dualPlayerEngine.activeAudioSessionId.collect { sessionId ->
                if (sessionId != 0) {
                    Timber.tag(TAG).d("Audio Session ID changed to $sessionId. Re-attaching equalizer.")
                    equalizerManager.attachToAudioSession(sessionId)
                    
                    // Update supported flags potentially? 
                    // Usually capabilities are device-wide, but good to refresh if needed.
                    _uiState.value = _uiState.value.copy(
                        isBassBoostSupported = equalizerManager.isBassBoostSupported(),
                        isVirtualizerSupported = equalizerManager.isVirtualizerSupported(),
                        isLoudnessEnhancerSupported = equalizerManager.isLoudnessEnhancerSupported()
                    )
                }
            }
        }
    }
    
    private fun observeEqualizerState() {
        viewModelScope.launch {
            // Combine flows for UI State
            combine(
                userPreferencesRepository.equalizerEnabledFlow,
                userPreferencesRepository.equalizerPresetFlow,
                userPreferencesRepository.equalizerCustomBandsFlow,
                userPreferencesRepository.bassBoostEnabledFlow,
                userPreferencesRepository.bassBoostStrengthFlow,
                userPreferencesRepository.virtualizerEnabledFlow,
                userPreferencesRepository.virtualizerStrengthFlow,
                userPreferencesRepository.loudnessEnhancerEnabledFlow,
                userPreferencesRepository.loudnessEnhancerStrengthFlow,
                userPreferencesRepository.bassBoostDismissedFlow,
                userPreferencesRepository.virtualizerDismissedFlow,
                userPreferencesRepository.loudnessDismissedFlow,
                userPreferencesRepository.equalizerViewModeFlow,
                userPreferencesRepository.customPresetsFlow, // Added
                userPreferencesRepository.pinnedPresetsFlow // Added
            ) { values -> // Too many args for standard destructuring, use array/list access
                 val enabled = values[0] as Boolean
                 val presetName = values[1] as String
                 val customBands = values[2] as List<Int> // Already decoded in Repository
                 val bbEnabled = values[3] as Boolean
                 val bbStrength = values[4] as Int
                 val vEnabled = values[5] as Boolean
                 val vStrength = values[6] as Int
                 val lEnabled = values[7] as Boolean
                 val lStrength = values[8] as Int
                 val bbDismissed = values[9] as Boolean
                 val vDismissed = values[10] as Boolean
                 val lDismissed = values[11] as Boolean
                 val viewMode = values[12] as UserPreferencesRepository.EqualizerViewMode
                 val customPresets = values[13] as List<EqualizerPreset>
                 val pinnedPresets = values[14] as List<String>

                val currentPreset = if (presetName == "custom") {
                    EqualizerPreset.custom(customBands)
                } else {
                     // Check custom presets first
                     customPresets.find { it.name == presetName }
                        ?: EqualizerPreset.fromName(presetName)
                }

                EqualizerUiState(
                    isEnabled = enabled,
                    currentPreset = currentPreset,
                    bandLevels = if (currentPreset.name == "custom") customBands else currentPreset.bandLevels,
                    bassBoostEnabled = bbEnabled,
                    bassBoostStrength = bbStrength.toFloat(), // Raw 0-1000
                    virtualizerEnabled = vEnabled,
                    virtualizerStrength = vStrength.toFloat(), // Raw 0-1000
                    loudnessEnhancerEnabled = lEnabled,
                    loudnessEnhancerStrength = lStrength.toFloat(), // Raw 0-1000 (or 3000)
                    isBassBoostDismissed = bbDismissed,
                    isVirtualizerDismissed = vDismissed,
                    isLoudnessDismissed = lDismissed,
                    viewMode = viewMode,
                    // New State
                    customPresets = customPresets,
                    pinnedPresetsNames = pinnedPresets,
                    // Capabilities (Keep existing values)
                    isBassBoostSupported = _uiState.value.isBassBoostSupported,
                    isVirtualizerSupported = _uiState.value.isVirtualizerSupported,
                    isLoudnessEnhancerSupported = _uiState.value.isLoudnessEnhancerSupported
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun cycleViewMode() {
        viewModelScope.launch {
            val currentMode = _uiState.value.viewMode
            val nextMode = when (currentMode) {
                UserPreferencesRepository.EqualizerViewMode.SLIDERS -> UserPreferencesRepository.EqualizerViewMode.GRAPH
                UserPreferencesRepository.EqualizerViewMode.GRAPH -> UserPreferencesRepository.EqualizerViewMode.HYBRID
                UserPreferencesRepository.EqualizerViewMode.HYBRID -> UserPreferencesRepository.EqualizerViewMode.SLIDERS
            }
            userPreferencesRepository.setEqualizerViewMode(nextMode)
        }
    }
    
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerManager.setEnabled(enabled)
            userPreferencesRepository.setEqualizerEnabled(enabled)
        }
    }

    fun toggleEqualizer() {
        setEnabled(!_uiState.value.isEnabled)
    }
    
    fun selectPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            equalizerManager.applyPreset(preset)
            userPreferencesRepository.setEqualizerPreset(preset.name)
            if (!preset.isCustom) {
                userPreferencesRepository.setEqualizerCustomBands(preset.bandLevels)
            }
        }
    }
    
    fun setBandLevel(bandIndex: Int, level: Int) {
        viewModelScope.launch {
            equalizerManager.setBandLevel(bandIndex, level)
            // Save custom bands when user adjusts manually
            userPreferencesRepository.setEqualizerCustomBands(equalizerManager.bandLevels.value)
            userPreferencesRepository.setEqualizerPreset("custom")
        }
    }
    
    fun saveCurrentAsCustomPreset(name: String) {
        viewModelScope.launch {
            // Create preset from current custom bands
            val bands = equalizerManager.bandLevels.value
            val preset = EqualizerPreset(name, name, bands, true)
            userPreferencesRepository.saveCustomPreset(preset)
            
            // Also pin it automatically
            togglePinPreset(name)
            
            // Select it
            selectPreset(preset)
        }
    }
    
    fun deleteCustomPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            userPreferencesRepository.deleteCustomPreset(preset.name)
            // If deleting current, revert to Flat
            if (_uiState.value.currentPreset.name == preset.name) {
                selectPreset(EqualizerPreset.FLAT)
            }
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerManager.setBassBoostEnabled(enabled)
            userPreferencesRepository.setBassBoostEnabled(enabled)
        }
    }
    
    fun setBassBoostStrength(strength: Int) {
        viewModelScope.launch {
            equalizerManager.setBassBoostStrength(strength)
            userPreferencesRepository.setBassBoostStrength(strength)
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerManager.setVirtualizerEnabled(enabled)
            userPreferencesRepository.setVirtualizerEnabled(enabled)
        }
    }
    
    fun setVirtualizerStrength(strength: Int) {
        viewModelScope.launch {
            equalizerManager.setVirtualizerStrength(strength)
            userPreferencesRepository.setVirtualizerStrength(strength)
        }
    }

    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            equalizerManager.setLoudnessEnhancerEnabled(enabled)
            userPreferencesRepository.setLoudnessEnhancerEnabled(enabled)
        }
    }

    fun setLoudnessEnhancerStrength(strength: Int) {
        viewModelScope.launch {
            equalizerManager.setLoudnessEnhancerStrength(strength)
            userPreferencesRepository.setLoudnessEnhancerStrength(strength)
        }
    }

    fun setBassBoostDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBassBoostDismissed(dismissed)
        }
    }

    fun setVirtualizerDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setVirtualizerDismissed(dismissed)
        }
    }

    fun setLoudnessDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setLoudnessDismissed(dismissed)
        }
    }
    
    fun updatePinnedPresetsOrder(newOrder: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setPinnedPresets(newOrder)
        }
    }
    
    fun resetPinnedPresetsToDefault() {
        viewModelScope.launch {
            // Reset to default order: all standard presets visible, in original order
            val defaultOrder = EqualizerPreset.ALL_PRESETS.map { it.name }
            userPreferencesRepository.setPinnedPresets(defaultOrder)
        }
    }
    
    fun togglePinPreset(presetName: String) {
        viewModelScope.launch {
            val currentPinned = _uiState.value.pinnedPresetsNames.toMutableList()
            if (currentPinned.contains(presetName)) {
                currentPinned.remove(presetName)
            } else {
                currentPinned.add(presetName)
            }
            userPreferencesRepository.setPinnedPresets(currentPinned)
        }
    }
    
    /**
     * Reattaches the equalizer to a new audio session.
     * Call this when the player swaps during crossfade.
     */
    fun reattachToPlayer() {
        viewModelScope.launch {
            val audioSessionId = dualPlayerEngine.getAudioSessionId()
            Timber.tag(TAG).d("Reattaching equalizer to new audio session: $audioSessionId")
            equalizerManager.attachToAudioSession(audioSessionId)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Don't release equalizer here - it should persist across screen navigation
        Timber.tag(TAG).d("ViewModel cleared")
    }
}
