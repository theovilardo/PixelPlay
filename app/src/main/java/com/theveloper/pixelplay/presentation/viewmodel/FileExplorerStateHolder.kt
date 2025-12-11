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
    val canonicalPath: String,
    val displayName: String? = null
)

class FileExplorerStateHolder(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val visibleRoot: File = Environment.getExternalStorageDirectory()
) {

    private val rootCanonicalPath: String = normalizePath(visibleRoot)
    private val audioCountCache = mutableMapOf<String, AudioCount>()
    private val directoryChildrenCache = mutableMapOf<String, List<DirectoryEntry>>()
    private val smartViewCache = mutableMapOf<String, List<DirectoryEntry>>()

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

            if (forceRefresh) {
                directoryChildrenCache.remove(targetKey)
                smartViewCache.remove(targetKey)
            }

            if (updatePath) {
                _currentPath.value = target
            }

            if (_smartViewEnabled.value && !forceRefresh) {
                smartViewCache[targetKey]?.let { cached ->
                    _currentDirectoryChildren.value = cached
                    _isLoading.value = false
                    if (!updatePath) {
                        _currentPath.value = target
                    }
                    return@launch
                }
            }

            if (!forceRefresh) {
                directoryChildrenCache[targetKey]?.let { cached ->
                    _currentDirectoryChildren.value = cached
                    _isLoading.value = false
                    if (!updatePath) {
                        _currentPath.value = target
                    }
                    if (_smartViewEnabled.value) {
                        val smartChildren = buildSmartViewEntries(target, forceRefresh = false)
                        _currentDirectoryChildren.value = smartChildren
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
            _currentDirectoryChildren.value = if (_smartViewEnabled.value) {
                buildSmartViewEntries(target, forceRefresh)
            } else {
                children
            }
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
            val currentAllowed = _allowedDirectories.value.toMutableSet()
            val path = normalizePath(file)

            val explicit = currentAllowed.firstOrNull { it == path }
            val ancestor = currentAllowed.firstOrNull { path != it && path.startsWith("$it/") }

            when {
                explicit != null -> currentAllowed.removeAll { it == path || it.startsWith("$path/") }
                ancestor != null -> {
                    currentAllowed.removeAll { it == ancestor || it.startsWith("$ancestor/") }
                    addCoverageExcluding(ancestor, path, currentAllowed)
                }
                else -> {
                    currentAllowed.removeAll { it.startsWith("$path/") }
                    currentAllowed.add(path)
                }
            }
            userPreferencesRepository.updateAllowedDirectories(currentAllowed)
        }
    }

    fun setSmartViewEnabled(enabled: Boolean) {
        if (_smartViewEnabled.value == enabled) return
        _smartViewEnabled.value = enabled

        scope.launch {
            val target = _currentPath.value
            val targetKey = normalizePath(target)
            if (!enabled) {
                smartViewCache.remove(targetKey)
                directoryChildrenCache[targetKey]?.let { cached ->
                    _currentDirectoryChildren.value = cached
                }
                return@launch
            }

            val smartChildren = buildSmartViewEntries(target, forceRefresh = false)
            _currentDirectoryChildren.value = smartChildren
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

    private suspend fun buildSmartViewEntries(root: File, forceRefresh: Boolean): List<DirectoryEntry> {
        val key = normalizePath(root)
        if (!forceRefresh) {
            smartViewCache[key]?.let { return it }
        }

        val aggregated = withContext(Dispatchers.IO) {
            val results = mutableListOf<DirectoryEntry>()
            val queue: ArrayDeque<File> = ArrayDeque()
            queue.add(root)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val children = current.listFiles()?.filter { it.isDirectory && !it.isHidden } ?: continue

                val entriesWithAudio = mutableListOf<DirectoryEntry>()
                val traversable = mutableListOf<File>()

                for (child in children) {
                    val counts = countAudioFiles(child, forceRefresh)
                    if (counts.total == 0) continue
                    traversable.add(child)

                    if (counts.direct > 0) {
                        entriesWithAudio.add(
                            DirectoryEntry(
                                file = child,
                                directAudioCount = counts.direct,
                                totalAudioCount = counts.total,
                                canonicalPath = normalizePath(child)
                            )
                        )
                    }
                }

                val shouldGroup = current != root && entriesWithAudio.size >= GROUPING_THRESHOLD
                if (shouldGroup) {
                    val groupedDirect = entriesWithAudio.sumOf { it.directAudioCount }
                    val groupedTotal = entriesWithAudio.sumOf { it.totalAudioCount }
                    results.add(
                        DirectoryEntry(
                            file = current,
                            directAudioCount = groupedDirect,
                            totalAudioCount = groupedTotal,
                            canonicalPath = normalizePath(current),
                            displayName = "${current.name.ifEmpty { current.path }} (${entriesWithAudio.size} subfolders)"
                        )
                    )
                    continue
                }

                results.addAll(entriesWithAudio)
                traversable.forEach(queue::add)
            }

            results
                .distinctBy { it.canonicalPath }
                .sortedBy { it.file.name.lowercase() }
        }

        smartViewCache[key] = aggregated
        return aggregated
    }

    private fun normalizePath(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
    private fun normalizePath(path: String): String = runCatching { File(path).canonicalPath }.getOrDefault(path)

    fun isDirectoryFullySelected(file: File): Boolean = isDirectoryFullySelected(normalizePath(file), mutableMapOf())

    private fun isDirectoryFullySelected(path: String, memo: MutableMap<String, Boolean>): Boolean {
        memo[path]?.let { return it }
        val allowed = _allowedDirectories.value
        if (allowed.any { path.startsWith(it) }) {
            memo[path] = true
            return true
        }

        val childEntries = getChildrenWithAudio(File(path))
        if (childEntries.isEmpty()) {
            memo[path] = false
            return false
        }

        val result = childEntries.all { child -> isDirectoryFullySelected(child.canonicalPath, memo) }
        memo[path] = result
        return result
    }

    private fun addCoverageExcluding(rootPath: String, excludePath: String, accumulator: MutableSet<String>) {
        val normalizedExclude = normalizePath(excludePath)
        val children = getChildrenWithAudio(File(rootPath))

        if (children.isEmpty()) {
            if (normalizedExclude != rootPath) {
                accumulator.add(rootPath)
            }
            return
        }

        children.forEach { child ->
            val childPath = child.canonicalPath
            if (normalizedExclude == childPath || normalizedExclude.startsWith("$childPath/")) {
                addCoverageExcluding(childPath, normalizedExclude, accumulator)
            } else {
                accumulator.add(childPath)
            }
        }
    }

    private fun getChildrenWithAudio(directory: File): List<DirectoryEntry> {
        val targetKey = normalizePath(directory)
        directoryChildrenCache[targetKey]?.let { return it }

        val computed = runCatching {
            directory.listFiles()
                ?.mapNotNull { child ->
                    if (child.isDirectory && !child.isHidden) {
                        val counts = countAudioFiles(child, forceRefresh = false)
                        if (counts.total > 0) DirectoryEntry(child, counts.direct, counts.total, normalizePath(child)) else null
                    } else {
                        null
                    }
                }
                ?.sortedWith(compareBy({ it.file.name.lowercase() }))
                ?: emptyList()
        }.getOrElse { emptyList() }

        directoryChildrenCache[targetKey] = computed
        return computed
    }

    private data class AudioCount(val direct: Int, val total: Int)

    private companion object {
        const val GROUPING_THRESHOLD = 40
    }
}
