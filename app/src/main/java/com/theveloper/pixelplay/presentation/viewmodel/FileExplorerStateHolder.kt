package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma", "alac", "aiff", "ape"
    )

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

            if (updatePath) {
                _currentPath.value = target
            }

            _isLoading.value = true
            _currentDirectoryChildren.value = emptyList()

            val children = withContext(Dispatchers.IO) {
                runCatching {
                    target.listFiles()
                        ?.filter { it.isDirectory && !it.isHidden && directoryContainsAudio(it) }
                        ?.sortedBy { it.name.lowercase() }
                        ?: emptyList()
                }.getOrElse { emptyList() }
            }

            if (!updatePath) {
                _currentPath.value = target
            }
            _currentDirectoryChildren.value = children
            _isLoading.value = false
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

    private fun directoryContainsAudio(directory: File): Boolean {
        val filesQueue: ArrayDeque<File> = ArrayDeque()
        filesQueue.add(directory)

        while (filesQueue.isNotEmpty()) {
            val current = filesQueue.removeFirst()
            val listed = current.listFiles() ?: continue
            for (child in listed) {
                if (child.isHidden) continue
                if (child.isDirectory) {
                    filesQueue.add(child)
                } else {
                    val extension = child.extension.lowercase()
                    if (audioExtensions.contains(extension)) return true
                }
            }
        }

        return false
    }

    fun isAtRoot(): Boolean = _currentPath.value.path == visibleRoot.path

    fun rootDirectory(): File = visibleRoot
}
