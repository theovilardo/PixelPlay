package com.theveloper.pixelplay.presentation.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.DirectoryItem
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val mediaPermissionGranted: Boolean = false,
    val notificationsPermissionGranted: Boolean = false,
    val allFilesAccessGranted: Boolean = false,
    val directoryItems: ImmutableList<DirectoryItem> = persistentListOf(),
    val isLoadingDirectories: Boolean = false,
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
    private val musicRepository: MusicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadDirectoryPreferences()
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
            }.catch {
                _uiState.update { it.copy(isLoadingDirectories = false, directoryItems = persistentListOf()) }
            }.collect { directoryItems ->
                _uiState.update { it.copy(directoryItems = directoryItems.toImmutableList(), isLoadingDirectories = false) }
            }
        }
    }

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

    fun setSetupComplete() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(true)
        }
    }
}
