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
    val allowedDirectories: Set<String> = emptySet()
) {
    val allPermissionsGranted: Boolean
        get() {
            val mediaOk = mediaPermissionGranted
            val notificationsOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationsPermissionGranted else true
            val allFilesOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) allFilesAccessGranted else true
            return mediaOk && notificationsOk && allFilesOk
        }
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState = _uiState.asStateFlow()

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val allowedDirectories = fileExplorerStateHolder.allowedDirectories
    val smartViewEnabled = fileExplorerStateHolder.smartViewEnabled
    val isLoadingDirectories = fileExplorerStateHolder.isLoading

    init {
        viewModelScope.launch {
            userPreferencesRepository.allowedDirectoriesFlow.collect { allowed ->
                _uiState.update { it.copy(allowedDirectories = allowed) }
            }
        }

        viewModelScope.launch {
            fileExplorerStateHolder.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoadingDirectories = loading) }
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

        _uiState.update {
            it.copy(
                mediaPermissionGranted = mediaPermissionGranted,
                notificationsPermissionGranted = notificationsPermissionGranted,
                allFilesAccessGranted = allFilesAccessGranted
            )
        }
    }

    fun loadMusicDirectories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDirectories = true) }
            if (!userPreferencesRepository.initialSetupDoneFlow.first()) {
                val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
                if (allowedDirs.isEmpty()) {
                    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val defaultAllowed = if (musicDir.exists()) {
                        runCatching { musicDir.canonicalPath }.getOrDefault(musicDir.absolutePath)
                    } else null
                    userPreferencesRepository.updateAllowedDirectories(defaultAllowed?.let { setOf(it) } ?: emptySet())
                }
            }

            userPreferencesRepository.allowedDirectoriesFlow.first().let { allowed ->
                _uiState.update { it.copy(allowedDirectories = allowed) }
            }
            fileExplorerStateHolder.refreshCurrentDirectory()
            _uiState.update { it.copy(isLoadingDirectories = false) }
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        fileExplorerStateHolder.toggleDirectoryAllowed(file)
    }

    fun isDirectorySelected(file: File): Boolean = fileExplorerStateHolder.isDirectoryFullySelected(file)

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun setSmartViewEnabled(enabled: Boolean) {
        fileExplorerStateHolder.setSmartViewEnabled(enabled)
    }

    fun refreshCurrentDirectory() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

    fun setSetupComplete() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(true)
            syncManager.forceRefresh()
        }
    }
}
