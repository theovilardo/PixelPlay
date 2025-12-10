package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Environment
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileExplorerStateHolder @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope,
) {

    private val explorerRoot: File = Environment.getExternalStorageDirectory().absoluteFile

    private val _currentPath = MutableStateFlow(explorerRoot)
    val currentPath: StateFlow<File> = _currentPath.asStateFlow()

    private val _currentDirectoryChildren = MutableStateFlow<List<File>>(emptyList())
    val currentDirectoryChildren: StateFlow<List<File>> = _currentDirectoryChildren.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val allowedDirectories: StateFlow<Set<String>> = userPreferencesRepository.allowedDirectoriesFlow
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        loadDirectory(explorerRoot)
    }

    fun loadDirectory(file: File) {
        scope.launch {
            val target = file.takeIf { it.exists() && it.isDirectory } ?: explorerRoot
            _isLoading.value = true
            _currentPath.value = target
            val children = withContext(Dispatchers.IO) {
                runCatching {
                    target.listFiles()
                        ?.filter { it.isDirectory && !it.isHidden }
                        ?.sortedBy { it.name.lowercase() }
                }.getOrNull().orEmpty()
            }
            _currentDirectoryChildren.value = children
            _isLoading.value = false
        }
    }

    fun navigateUp(): Boolean {
        val parent = _currentPath.value.parentFile ?: return false
        val isAboveRoot = !parent.absolutePath.startsWith(explorerRoot.absolutePath)
        return if (!isAboveRoot) {
            loadDirectory(parent)
            true
        } else {
            false
        }
    }

    fun resetToRoot() {
        loadDirectory(explorerRoot)
    }

    fun toggleDirectoryAllowed(file: File) {
        scope.launch {
            val currentAllowed = allowedDirectories.value.toMutableSet()
            val path = file.absolutePath
            if (currentAllowed.contains(path)) {
                currentAllowed.remove(path)
            } else {
                currentAllowed.add(path)
            }
            userPreferencesRepository.updateAllowedDirectories(currentAllowed)
        }
    }

    fun canNavigateUp(): Boolean = _currentPath.value.absolutePath != explorerRoot.absolutePath

    val rootPath: File
        get() = explorerRoot
}
