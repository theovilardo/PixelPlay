package com.theveloper.pixelplay.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.worker.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.File

data class SetupUiState(
    val mediaPermissionGranted: Boolean = false,
    val notificationsPermissionGranted: Boolean = false,
    val allFilesAccessGranted: Boolean = false,
    val isLoadingDirectories: Boolean = false,
    val blockedDirectories: Set<String> = emptySet(),
    val libraryNavigationMode: String = "tab_row",
    val navBarStyle: String = "default",
    val navBarCornerRadius: Int = 28,
    val alarmsPermissionGranted: Boolean = false
) {
    val allPermissionsGranted: Boolean
        get() {
            val mediaOk = mediaPermissionGranted
            val notificationsOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationsPermissionGranted else true
            val allFilesOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) allFilesAccessGranted else true
            val alarmsOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmsPermissionGranted else true
            return mediaOk && notificationsOk && allFilesOk && alarmsOk
        }
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState = _uiState.asStateFlow()

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading

    init {
        viewModelScope.launch {
            userPreferencesRepository.blockedDirectoriesFlow.collect { blocked ->
                _uiState.update { it.copy(blockedDirectories = blocked) }
            }
        }

        viewModelScope.launch {
            fileExplorerStateHolder.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoadingDirectories = loading) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.libraryNavigationModeFlow.collect { mode ->
                _uiState.update { it.copy(libraryNavigationMode = mode) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.navBarStyleFlow.collect { style ->
                _uiState.update { it.copy(navBarStyle = style) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.navBarCornerRadiusFlow.collect { radius ->
                _uiState.update { it.copy(navBarCornerRadius = radius) }
            }
        }
    }

    fun checkPermissions(context: Context) {
        val mediaPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val notificationsPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required before Android 13 (Tiramisu)
        }

        val allFilesAccessGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Not required before Android 11 (R)
        }

        val alarmsPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        _uiState.update {
            it.copy(
                mediaPermissionGranted = mediaPermissionGranted,
                notificationsPermissionGranted = notificationsPermissionGranted,
                allFilesAccessGranted = allFilesAccessGranted,
                alarmsPermissionGranted = alarmsPermissionGranted
            )
        }
    }

    fun loadMusicDirectories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDirectories = true) }
            if (!userPreferencesRepository.initialSetupDoneFlow.first()) {
                // Blacklist model: default is allow all, so no setup needed.
            }

            userPreferencesRepository.blockedDirectoriesFlow.first().let { blocked ->
                _uiState.update { it.copy(blockedDirectories = blocked) }
            }
            fileExplorerStateHolder.refreshCurrentDirectory()
            _uiState.update { it.copy(isLoadingDirectories = false) }
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        fileExplorerStateHolder.toggleDirectoryAllowed(file)
        syncManager.sync()
    }

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun selectStorage(index: Int) {
        fileExplorerStateHolder.selectStorage(index)
    }

    fun refreshAvailableStorages() {
        fileExplorerStateHolder.refreshAvailableStorages()
    }

    fun refreshCurrentDirectory() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

    fun setLibraryNavigationMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLibraryNavigationMode(mode)
        }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCornerRadius(radius)
        }
    }

    fun setSetupComplete() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(true)
            // Use fullSync which bypasses MIN_SYNC_INTERVAL check and uses FULL mode
            syncManager.fullSync()
        }
    }
}
