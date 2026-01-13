package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.ai.GeminiModelService
import com.theveloper.pixelplay.data.ai.GeminiModel
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.model.Song
import java.io.File

data class SettingsUiState(
    val isLoadingDirectories: Boolean = false,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val playerThemePreference: String = ThemePreference.ALBUM_ART,
    val mockGenresEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val carouselStyle: String = CarouselStyle.NO_PEEK,
    val libraryNavigationMode: String = LibraryNavigationMode.TAB_ROW,
    val launchTab: String = LaunchTab.HOME,
    val keepPlayingInBackground: Boolean = true,
    val disableCastAutoplay: Boolean = false,
    val showQueueHistory: Boolean = true,
    val isCrossfadeEnabled: Boolean = true,
    val crossfadeDuration: Int = 6000,
    val blockedDirectories: Set<String> = emptySet(),
    val availableModels: List<GeminiModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val appRebrandDialogShown: Boolean = false,
    val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks()
)

data class FailedSongInfo(
    val id: String,
    val title: String,
    val artist: String
)

data class LyricsRefreshProgress(
    val totalSongs: Int = 0,
    val currentCount: Int = 0,
    val savedCount: Int = 0,
    val notFoundCount: Int = 0,
    val skippedCount: Int = 0,
    val isComplete: Boolean = false,
    val failedSongs: List<FailedSongInfo> = emptyList()
) {
    val hasProgress: Boolean get() = totalSongs > 0
    val progress: Float get() = if (totalSongs > 0) currentCount.toFloat() / totalSongs else 0f
    val hasFailedSongs: Boolean get() = failedSongs.isNotEmpty()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
    private val geminiModelService: GeminiModelService,
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val geminiApiKey: StateFlow<String> = userPreferencesRepository.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiModel: StateFlow<String> = userPreferencesRepository.geminiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiSystemPrompt: StateFlow<String> = userPreferencesRepository.geminiSystemPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesRepository.DEFAULT_SYSTEM_PROMPT)

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading
    val isExplorerPriming = fileExplorerStateHolder.isPrimingExplorer
    val isExplorerReady = fileExplorerStateHolder.isExplorerReady

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val syncProgress: StateFlow<SyncProgress> = syncManager.syncProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncProgress()
        )

    init {
        viewModelScope.launch {
            userPreferencesRepository.appRebrandDialogShownFlow.collect { wasShown ->
                _uiState.update { it.copy(appRebrandDialogShown = wasShown) }
            }
        }

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
            userPreferencesRepository.libraryNavigationModeFlow.collect { mode ->
                _uiState.update { it.copy(libraryNavigationMode = mode) }
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
            userPreferencesRepository.disableCastAutoplayFlow.collect { disabled ->
                _uiState.update { it.copy(disableCastAutoplay = disabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.showQueueHistoryFlow.collect { show ->
                _uiState.update { it.copy(showQueueHistory = show) }
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

        viewModelScope.launch {
            userPreferencesRepository.blockedDirectoriesFlow.collect { blocked ->
                _uiState.update { it.copy(blockedDirectories = blocked) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.fullPlayerLoadingTweaksFlow.collect { tweaks ->
                _uiState.update { it.copy(fullPlayerLoadingTweaks = tweaks) }
            }
        }

        viewModelScope.launch {
            fileExplorerStateHolder.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoadingDirectories = loading) }
            }
        }
    }

    fun setAppRebrandDialogShown(wasShown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAppRebrandDialogShown(wasShown)
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        fileExplorerStateHolder.toggleDirectoryAllowed(file)
        syncManager.sync()
    }

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun primeExplorer() {
        fileExplorerStateHolder.primeExplorerRoot()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun refreshExplorer() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun selectStorage(index: Int) {
        fileExplorerStateHolder.selectStorage(index)
    }

    fun refreshAvailableStorages() {
        fileExplorerStateHolder.refreshAvailableStorages()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

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

    fun setLibraryNavigationMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLibraryNavigationMode(mode)
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

    fun setDisableCastAutoplay(disabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDisableCastAutoplay(disabled)
        }
    }

    fun setShowQueueHistory(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowQueueHistory(show)
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

    fun setDelayAllFullPlayerContent(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAllFullPlayerContent(enabled)
        }
    }

    fun setDelayAlbumCarousel(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAlbumCarousel(enabled)
        }
    }

    fun setDelaySongMetadata(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelaySongMetadata(enabled)
        }
    }

    fun setDelayProgressBar(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayProgressBar(enabled)
        }
    }

    fun setDelayControls(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayControls(enabled)
        }
    }

    fun setFullPlayerPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerPlaceholders(enabled)
            if (!enabled) {
                userPreferencesRepository.setTransparentPlaceholders(false)
            }
        }
    }

    fun setTransparentPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTransparentPlaceholders(enabled)
        }
    }

    fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerAppearThreshold(thresholdPercent)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.forceRefresh()
        }
    }



    /**
     * Performs a full library rescan - rescans all files from scratch.
     * Use when songs are missing or metadata is incorrect.
     */
    fun fullSyncLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.fullSync()
        }
    }

    /**
     * Completely rebuilds the database from scratch.
     * Clears all data including user edits (lyrics, favorites) and rescans.
     * Use when database is corrupted or as a last resort.
     */
    fun rebuildDatabase() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.rebuildDatabase()
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



    /**
     * Triggers a test crash to verify the crash handler is working correctly.
     * This should only be used for testing in Developer Options.
     */
    fun triggerTestCrash() {
        throw RuntimeException("Test crash triggered from Developer Options - This is intentional for testing the crash reporting system")
    }

    fun resetSetupFlow() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(false)
        }
    }
}
