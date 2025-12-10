package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import java.io.File

class FileExplorerStateHolder(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val visibleRoot: File = Environment.getExternalStorageDirectory()
) {

    private val rootCanonicalPath: String = runCatching { visibleRoot.canonicalPath }.getOrDefault(visibleRoot.absolutePath)

    private val _currentPath = MutableStateFlow(visibleRoot)
    val currentPath: StateFlow<File> = _currentPath.asStateFlow()

    private val _currentDirectoryChildren = MutableStateFlow<List<File>>(emptyList())
    val currentDirectoryChildren: StateFlow<List<File>> = _currentDirectoryChildren.asStateFlow()

    private val _allowedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val allowedDirectories: StateFlow<Set<String>> = _allowedDirectories.asStateFlow()

    init {
        scope.launch {
            userPreferencesRepository.allowedDirectoriesFlow.collect { allowed ->
                _allowedDirectories.value = allowed
            }
        }
        refreshCurrentDirectory()
    }

    fun refreshCurrentDirectory() {
        loadDirectory(_currentPath.value, updatePath = false)
    }

    fun loadDirectory(file: File, updatePath: Boolean = true) {
        scope.launch {
            val target = if (file.isDirectory) file else visibleRoot
            val children = runCatching {
                target.listFiles()
                    ?.filter { it.isDirectory && !it.isHidden }
                    ?.sortedBy { it.name.lowercase() }
                    ?: emptyList()
            }.getOrElse { emptyList() }

            if (updatePath) {
                _currentPath.value = target
            }
            _currentDirectoryChildren.value = children
        }
    }

    fun navigateUp() {
        val current = _currentPath.value
        val parent = current.parentFile ?: return
        val parentCanonical = runCatching { parent.canonicalPath }.getOrNull()
        val isAboveRoot = parentCanonical?.startsWith(rootCanonicalPath) == false

        if (isAboveRoot || current.path == visibleRoot.path) {
            loadDirectory(visibleRoot)
        } else {
            loadDirectory(parent)
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        scope.launch {
            val currentAllowed = userPreferencesRepository.allowedDirectoriesFlow.first().toMutableSet()
            val path = file.absolutePath
            if (currentAllowed.contains(path)) {
                currentAllowed.remove(path)
            } else {
                currentAllowed.add(path)
            }
            userPreferencesRepository.updateAllowedDirectories(currentAllowed)
        }
    }

    fun isAtRoot(): Boolean = _currentPath.value.path == visibleRoot.path

    fun rootDirectory(): File = visibleRoot
}
