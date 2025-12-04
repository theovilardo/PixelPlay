package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.DirectoryItem
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.worker.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.ai.GeminiModelService
import com.theveloper.pixelplay.data.ai.GeminiModel
import com.theveloper.pixelplay.data.preferences.LaunchTab

data class SettingsUiState(
    val directoryItems: List<DirectoryItem> = emptyList(),
    val isLoadingDirectories: Boolean = true,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val playerThemePreference: String = ThemePreference.ALBUM_ART,
    val mockGenresEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val carouselStyle: String = CarouselStyle.ONE_PEEK,
    val launchTab: String = LaunchTab.HOME,
    val keepPlayingInBackground: Boolean = true,
    val isCrossfadeEnabled: Boolean = true,
    val crossfadeDuration: Int = 6000,
    val availableModels: List<GeminiModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val syncManager: SyncManager,
    private val geminiModelService: GeminiModelService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val geminiApiKey: StateFlow<String> = userPreferencesRepository.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiModel: StateFlow<String> = userPreferencesRepository.geminiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiSystemPrompt: StateFlow<String> = userPreferencesRepository.geminiSystemPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesRepository.DEFAULT_SYSTEM_PROMPT)

    init {
        viewModelScope.launch {
            userPreferencesRepository.appThemeModeFlow.collect { appThemeMode ->
                _uiState.update { it.copy(appThemeMode = appThemeMode) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.playerThemePreferenceFlow.collect { preference ->
                _uiState.update{ it.copy(playerThemePreference = preference) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.mockGenresEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(mockGenresEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.navBarCornerRadiusFlow.collect { radius ->
                _uiState.update { it.copy(navBarCornerRadius = radius) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.navBarStyleFlow.collect { style ->
                _uiState.update { it.copy(navBarStyle = style) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.carouselStyleFlow.collect { style ->
                _uiState.update { it.copy(carouselStyle = style) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.launchTabFlow.collect { tab ->
                _uiState.update { it.copy(launchTab = tab) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.keepPlayingInBackgroundFlow.collect { enabled ->
                _uiState.update { it.copy(keepPlayingInBackground = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.isCrossfadeEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(isCrossfadeEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.crossfadeDurationFlow.collect { duration ->
                _uiState.update { it.copy(crossfadeDuration = duration) }
            }
        }

        loadDirectoryPreferences()
    }

    private fun loadDirectoryPreferences() {
        viewModelScope.launch {
            userPreferencesRepository.allowedDirectoriesFlow.combine(
                flow {
                    emit(musicRepository.getAllUniqueAudioDirectories())
                }.onStart { _uiState.update { it.copy(isLoadingDirectories = true) } }
            ) { allowedDirs, allFoundDirs ->
                val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()

                allFoundDirs.map { dirPath ->
                    val isAllowed = if (!initialSetupDone) true else allowedDirs.contains(dirPath)
                    DirectoryItem(path = dirPath, isAllowed = isAllowed)
                }.sortedBy { it.displayName }
            }.catch { e ->
                _uiState.update { it.copy(isLoadingDirectories = false, directoryItems = emptyList()) }
            }.collectLatest { directoryItems ->
                _uiState.update { it.copy(directoryItems = directoryItems, isLoadingDirectories = false) }
            }
        }
    }

    // Método para alternar el estado de un directorio y guardar en preferencias
    fun toggleDirectoryAllowed(directoryItem: DirectoryItem) {
        viewModelScope.launch {
            val currentAllowed = userPreferencesRepository.allowedDirectoriesFlow.first().toMutableSet()
            if (directoryItem.isAllowed) {
                currentAllowed.remove(directoryItem.path)
            } else {
                currentAllowed.add(directoryItem.path)
            }
            userPreferencesRepository.updateAllowedDirectories(currentAllowed)
        }
    }

    // Método para guardar la preferencia de tema del reproductor
    fun setPlayerThemePreference(preference: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPlayerThemePreference(preference)
        }
    }

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAppThemeMode(mode)
        }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setCarouselStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setCarouselStyle(style)
        }
    }

    fun setLaunchTab(tab: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLaunchTab(tab)
        }
    }

    fun setKeepPlayingInBackground(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepPlayingInBackground(enabled)
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeEnabled(enabled)
        }
    }

    fun setCrossfadeDuration(duration: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeDuration(duration)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            syncManager.forceRefresh()
        }
    }

    fun onGeminiApiKeyChange(apiKey: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGeminiApiKey(apiKey)

            // Fetch models when API key changes and is not empty
            if (apiKey.isNotBlank()) {
                fetchAvailableModels(apiKey)
            } else {
                // Clear models if API key is empty
                _uiState.update {
                    it.copy(
                        availableModels = emptyList(),
                        modelsFetchError = null
                    )
                }
                userPreferencesRepository.setGeminiModel("")
            }
        }
    }

    fun fetchAvailableModels(apiKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, modelsFetchError = null) }

            val result = geminiModelService.fetchAvailableModels(apiKey)

            result.onSuccess { models ->
                _uiState.update {
                    it.copy(
                        availableModels = models,
                        isLoadingModels = false,
                        modelsFetchError = null
                    )
                }

                // Auto-select first model if none is selected
                val currentModel = userPreferencesRepository.geminiModel.first()
                if (currentModel.isEmpty() && models.isNotEmpty()) {
                    userPreferencesRepository.setGeminiModel(models.first().name)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingModels = false,
                        modelsFetchError = error.message ?: "Failed to fetch models"
                    )
                }
            }
        }
    }

    fun onGeminiModelChange(modelName: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGeminiModel(modelName)
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCornerRadius(radius)
        }
    }

    fun onGeminiSystemPromptChange(prompt: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGeminiSystemPrompt(prompt)
        }
    }

    fun resetGeminiSystemPrompt() {
        viewModelScope.launch {
            userPreferencesRepository.resetGeminiSystemPrompt()
        }
    }
}
