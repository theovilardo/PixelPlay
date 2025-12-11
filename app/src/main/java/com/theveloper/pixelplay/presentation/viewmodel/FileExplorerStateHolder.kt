package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import java.io.File

data class DirectoryEntry(
    val file: File,
    val directAudioCount: Int,
    val totalAudioCount: Int,
    val canonicalPath: String
)

class FileExplorerStateHolder(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val visibleRoot: File = Environment.getExternalStorageDirectory()
) {

    private val rootCanonicalPath: String = normalizePath(visibleRoot)
    private val audioCountCache = mutableMapOf<String, AudioCount>()
    private val directoryChildrenCache = mutableMapOf<String, List<DirectoryEntry>>()

    private val _currentPath = MutableStateFlow(visibleRoot)
    val currentPath: StateFlow<File> = _currentPath.asStateFlow()

    private val _currentDirectoryChildren = MutableStateFlow<List<DirectoryEntry>>(emptyList())
    val currentDirectoryChildren: StateFlow<List<DirectoryEntry>> = _currentDirectoryChildren.asStateFlow()

    private val _allowedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val allowedDirectories: StateFlow<Set<String>> = _allowedDirectories.asStateFlow()

    private val _smartViewEnabled = MutableStateFlow(false)
    val smartViewEnabled: StateFlow<Boolean> = _smartViewEnabled.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma", "alac", "aiff", "ape"
    )

    init {
        scope.launch {
            userPreferencesRepository.allowedDirectoriesFlow.collect { allowed ->
                _allowedDirectories.value = allowed.map(::normalizePath).toSet()
            }
        }
        refreshCurrentDirectory()
    }

    fun refreshCurrentDirectory() {
        loadDirectory(_currentPath.value, updatePath = false, forceRefresh = true)
    }

    fun loadDirectory(file: File, updatePath: Boolean = true, forceRefresh: Boolean = false) {
        scope.launch {
            val target = if (file.isDirectory) file else visibleRoot
            val targetKey = normalizePath(target)

            if (updatePath) {
                _currentPath.value = target
            }

            if (!forceRefresh) {
                directoryChildrenCache[targetKey]?.let { cached ->
                    _currentDirectoryChildren.value = applySmartView(cached)
                    _isLoading.value = false
                    if (!updatePath) {
                        _currentPath.value = target
                    }
                    return@launch
                }
            }

            _isLoading.value = true
            _currentDirectoryChildren.value = emptyList()

            val cachedChildren = if (forceRefresh) null else directoryChildrenCache[targetKey]
            val children = cachedChildren ?: withContext(Dispatchers.IO) {
                runCatching {
                    target.listFiles()
                        ?.mapNotNull { child ->
                            if (child.isDirectory && !child.isHidden) {
                                val counts = countAudioFiles(child, forceRefresh)
                                if (counts.total > 0) DirectoryEntry(child, counts.direct, counts.total, normalizePath(child)) else null
                            } else {
                                null
                            }
                        }
                        ?.sortedWith(compareBy({ it.file.name.lowercase() }))
                        ?: emptyList()
                }.getOrElse { emptyList() }
                    .also { directoryChildrenCache[targetKey] = it }
            }

            if (!updatePath) {
                _currentPath.value = target
            }
            _currentDirectoryChildren.value = applySmartView(children)
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
            val currentAllowed = userPreferencesRepository.allowedDirectoriesFlow.first()
                .map(::normalizePath)
                .toMutableSet()
            val path = normalizePath(file)

            if (currentAllowed.contains(path)) {
                currentAllowed.removeAll { it == path || it.startsWith("$path/") }
            } else {
                currentAllowed.removeAll { it.startsWith("$path/") }
                currentAllowed.add(path)
            }
            userPreferencesRepository.updateAllowedDirectories(currentAllowed)
        }
    }

    fun setSmartViewEnabled(enabled: Boolean) {
        if (_smartViewEnabled.value == enabled) return
        _smartViewEnabled.value = enabled

        val targetKey = normalizePath(_currentPath.value)
        directoryChildrenCache[targetKey]?.let { cached ->
            _currentDirectoryChildren.value = applySmartView(cached)
        }
    }

    fun isPathAllowed(path: String): Boolean = _allowedDirectories.value.any { path.startsWith(it) }

    private fun countAudioFiles(directory: File, forceRefresh: Boolean): AudioCount {
        val key = normalizePath(directory)
        if (!forceRefresh) {
            audioCountCache[key]?.let { return it }
        }

        val filesQueue: ArrayDeque<File> = ArrayDeque()
        filesQueue.add(directory)

        var totalCount = 0
        var directCount = 0

        while (filesQueue.isNotEmpty()) {
            val current = filesQueue.removeFirst()
            val listed = current.listFiles() ?: continue
            for (child in listed) {
                if (child.isHidden) continue
                if (child.isDirectory) {
                    filesQueue.add(child)
                } else {
                    val extension = child.extension.lowercase()
                    if (audioExtensions.contains(extension)) {
                        totalCount++
                        if (current.path == directory.path) {
                            directCount++
                        }
                    }
                    if (totalCount > 100) {
                        return AudioCount(directCount, totalCount).also { audioCountCache[key] = it }
                    }
                }
            }
        }

        return AudioCount(directCount, totalCount).also { audioCountCache[key] = it }
    }

    fun isAtRoot(): Boolean = _currentPath.value.path == visibleRoot.path

    fun rootDirectory(): File = visibleRoot

    private fun applySmartView(children: List<DirectoryEntry>): List<DirectoryEntry> {
        return if (_smartViewEnabled.value) {
            children.filter { it.directAudioCount > 0 }
        } else {
            children
        }
    }

    private fun normalizePath(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
    private fun normalizePath(path: String): String = runCatching { File(path).canonicalPath }.getOrDefault(path)

    private data class AudioCount(val direct: Int, val total: Int)
}
