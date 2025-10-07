package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class SettingsUiState(
    val directoryItems: List<DirectoryItem> = emptyList(),
    val isLoadingDirectories: Boolean = true,
    val playerThemePreference: String = ThemePreference.ALBUM_ART,
    val mockGenresEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val carouselStyle: String = CarouselStyle.ONE_PEEK
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val geminiApiKey: StateFlow<String> = userPreferencesRepository.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        viewModelScope.launch {
            userPreferencesRepository.playerThemePreferenceFlow.collect { preference ->
                _uiState.update { it.copy(playerThemePreference = preference) }
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

    fun refreshLibrary() {
        viewModelScope.launch {
            syncManager.forceRefresh()
        }
    }

    fun onGeminiApiKeyChange(apiKey: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGeminiApiKey(apiKey)
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCornerRadius(radius)
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
}