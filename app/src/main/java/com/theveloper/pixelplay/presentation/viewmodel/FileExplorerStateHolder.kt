package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Environment
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class DirectoryEntry(
    val file: File,
    val directAudioCount: Int,
    val totalAudioCount: Int,
    val canonicalPath: String,
    val displayName: String? = null,
    val isSelected: Boolean = false
)

private data class RawDirectoryEntry(
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
    private val audioCountCache = ConcurrentHashMap<String, AudioCount>()

    // Caches for "Raw" entries (without selection state)
    private val directoryChildrenCache = mutableMapOf<String, List<RawDirectoryEntry>>()
    private val smartViewCache = mutableMapOf<String, List<RawDirectoryEntry>>()

    private val _currentPath = MutableStateFlow(visibleRoot)
    val currentPath: StateFlow<File> = _currentPath.asStateFlow()

    private val _rawCurrentDirectoryChildren = MutableStateFlow<List<RawDirectoryEntry>>(emptyList())

    private val _allowedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val allowedDirectories: StateFlow<Set<String>> = _allowedDirectories.asStateFlow()

    private val _blockedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val blockedDirectories: StateFlow<Set<String>> = _blockedDirectories.asStateFlow()

    private val _smartViewEnabled = MutableStateFlow(false)
    val smartViewEnabled: StateFlow<Boolean> = _smartViewEnabled.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPrimingExplorer = MutableStateFlow(false)
    val isPrimingExplorer: StateFlow<Boolean> = _isPrimingExplorer.asStateFlow()

    private val _isExplorerReady = MutableStateFlow(false)
    val isExplorerReady: StateFlow<Boolean> = _isExplorerReady.asStateFlow()

    // Combined flow for UI consumption
    private val _currentDirectoryChildren = MutableStateFlow<List<DirectoryEntry>>(emptyList())
    val currentDirectoryChildren: StateFlow<List<DirectoryEntry>> = _currentDirectoryChildren.asStateFlow()

    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma", "alac", "aiff", "ape"
    )

    private val mapperDispatcher = Dispatchers.Default
    private val loadMutex = Mutex()
    private var loadJob: Job? = null

    init {
        // Observer for preferences
        userPreferencesRepository.allowedDirectoriesFlow
            .onEach { allowed ->
                _allowedDirectories.value = allowed.map(::normalizePath).toSet()
            }
            .launchIn(scope)

        userPreferencesRepository.blockedDirectoriesFlow
            .onEach { blocked ->
                _blockedDirectories.value = blocked.map(::normalizePath).toSet()
            }
            .launchIn(scope)

        // Combiner to produce final UI list with isSelected state
        combine(
            _rawCurrentDirectoryChildren,
            _allowedDirectories,
            _blockedDirectories,
            _smartViewEnabled
        ) { rawEntries, allowed, blocked, isSmartView ->
            Triple(rawEntries, Pair(allowed, blocked), isSmartView)
        }
            .mapLatest { (rawEntries, selection, isSmartView) ->
                val (allowed, blocked) = selection
                rawEntries.map { raw ->
                    val normalizedPath = raw.canonicalPath
                    val isBlocked = blocked.any { normalizedPath == it || normalizedPath.startsWith("$it/") }
                    val isSelected = if (isBlocked) {
                        false
                    } else if (isSmartView) {
                        allowed.any { normalizedPath == it || normalizedPath.startsWith("$it/") }
                    } else {
                        allowed.any { normalizedPath == it || normalizedPath.startsWith("$it/") }
                    }
                    DirectoryEntry(
                        file = raw.file,
                        directAudioCount = raw.directAudioCount,
                        totalAudioCount = raw.totalAudioCount,
                        canonicalPath = raw.canonicalPath,
                        displayName = raw.displayName,
                        isSelected = isSelected
                    )
                }
            }
            .flowOn(mapperDispatcher)
            .onEach {
                _currentDirectoryChildren.value = it
            }.launchIn(scope)

        // Initial load
        refreshCurrentDirectory()
    }

    fun refreshCurrentDirectory() {
        loadDirectory(_currentPath.value, updatePath = false, forceRefresh = true)
    }

    fun loadDirectory(file: File, updatePath: Boolean = true, forceRefresh: Boolean = false): Job {
        loadJob?.cancel()
        val job = scope.launch {
            loadMutex.withLock {
                loadDirectoryInternal(file, updatePath, forceRefresh)
            }
        }
        loadJob = job
        return job
    }

    fun primeExplorerRoot(): Job? {
        if (_isExplorerReady.value && directoryChildrenCache.containsKey(rootCanonicalPath)) return null
        if (_isPrimingExplorer.value) return null

        _isPrimingExplorer.value = true
        return scope.launch {
            loadMutex.withLock {
                loadDirectoryInternal(visibleRoot, updatePath = true, forceRefresh = false)
            }
            _isPrimingExplorer.value = false
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
            val currentBlocked = _blockedDirectories.value.toMutableSet()
            val path = normalizePath(file)

            val isCurrentlyBlocked = currentBlocked.any { path == it || path.startsWith("$it/") }
            if (isCurrentlyBlocked) {
                currentBlocked.removeAll { it == path || it.startsWith("$path/") }
                userPreferencesRepository.updateDirectorySelections(currentAllowed, currentBlocked)
                return@launch
            }

            val hasExplicitAllowance = currentAllowed.contains(path)
            val hasAllowedAncestor = currentAllowed.any { path != it && path.startsWith("$it/") }

            when {
                hasExplicitAllowance -> {
                    currentAllowed.remove(path)
                    currentBlocked.removeAll { it.startsWith("$path/") }
                }

                hasAllowedAncestor -> {
                    currentBlocked.add(path)
                    currentAllowed.removeAll { it.startsWith("$path/") }
                }

                else -> {
                    currentAllowed.add(path)
                    currentBlocked.removeAll { it == path || it.startsWith("$path/") || path.startsWith("$it/") }
                }
            }

            userPreferencesRepository.updateDirectorySelections(currentAllowed, currentBlocked)
        }
    }

    fun setSmartViewEnabled(enabled: Boolean) {
        if (_smartViewEnabled.value == enabled) return
        _smartViewEnabled.value = enabled

        scope.launch {
            val target = if (enabled) visibleRoot else _currentPath.value // Reset to root for smart view usually? Or keep context?
            // Actually, smart view is usually global flat list. Let's use visibleRoot for smart view base.
            // But if user was deep in folders, toggling smart view usually shows everything.

            val loadTarget = if(enabled) visibleRoot else target

            // Just trigger load, the logic inside handles smart view switch
            loadDirectoryInternal(loadTarget, updatePath = true, forceRefresh = false)
        }
    }

    // Fast check for internal logic
    fun isPathAllowed(path: String): Boolean {
        val normalized = normalizePath(path)
        if (_blockedDirectories.value.any { normalized == it || normalized.startsWith("$it/") }) return false
        return _allowedDirectories.value.any { normalized == it || normalized.startsWith("$it/") }
    }

    private suspend fun loadDirectoryInternal(file: File, updatePath: Boolean, forceRefresh: Boolean) {
        val target = if (file.isDirectory) file else visibleRoot
        val targetKey = normalizePath(target)
        val isSmartView = _smartViewEnabled.value
        val cacheKey = if (isSmartView) normalizePath(visibleRoot) else targetKey

        if (forceRefresh) {
            directoryChildrenCache.remove(targetKey)
            smartViewCache.remove(cacheKey)
        }

        if (updatePath) {
            _currentPath.value = target
        }

        val cachedEntries = if (!forceRefresh) {
            if (isSmartView) smartViewCache[cacheKey] else directoryChildrenCache[targetKey]
        } else null

        if (cachedEntries != null) {
            _rawCurrentDirectoryChildren.value = cachedEntries
            _isLoading.value = false
            _isExplorerReady.value = true
            return
        }

        _isLoading.value = true
        _rawCurrentDirectoryChildren.value = emptyList()

        val resultEntries = withContext(Dispatchers.IO) {
            if (isSmartView) {
                buildSmartViewEntries(visibleRoot, forceRefresh)
            } else {
                val children = runCatching {
                    target.listFiles()?.filter { it.isDirectory && !it.isHidden } ?: emptyList()
                }.getOrElse { emptyList() }

                coroutineScope {
                    val dispatcher = Dispatchers.IO.limitedParallelism(4)
                    children.map { child ->
                        async(dispatcher) {
                            val counts = countAudioFiles(child, forceRefresh)
                            if (counts.total > 0) {
                                RawDirectoryEntry(child, counts.direct, counts.total, normalizePath(child))
                            } else null
                        }
                    }.mapNotNull { it.await() }
                        .sortedWith(compareBy({ it.file.name.lowercase() }))
                }.also { directoryChildrenCache[targetKey] = it }
            }
        }

        _rawCurrentDirectoryChildren.value = resultEntries
        _isLoading.value = false
        _isExplorerReady.value = true
    }

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
                    // Optimization: If we have found enough files to know it's populated, we can stop counting strictly?
                    // But we need the counts for the UI ("12 songs").
                    // If the user has 10,000 songs, this loop is slow.
                    // Let's cap at 500 for display purposes to keep it fast, or rely on MediaStore later?
                    // For now, let's keep the 100 limit from original code or bump slightly,
                    // but the original code had `if (totalCount > 100) return`.
                    // This implies the UI shows "100+" or similar?
                    // The UI shows `"$displayCount songs"`. If we return 100, it says "100 songs".
                    // If we stop at 100, the user won't know the real count.
                    // But for performance, avoiding deep traversal of massive folders is key.
                    if (totalCount > 99) {
                         // We can stop here for performance. The UI handles "99+" logic if we want,
                         // but currently the UI code says: `if (audioCount > 99) "99+" else audioCount.toString()`.
                         // So we MUST return at least 100 to trigger "99+".
                         return AudioCount(directCount, totalCount).also { audioCountCache[key] = it }
                    }
                }
            }
        }

        return AudioCount(directCount, totalCount).also { audioCountCache[key] = it }
    }

    fun isAtRoot(): Boolean = _currentPath.value.path == visibleRoot.path

    fun rootDirectory(): File = visibleRoot

    private suspend fun buildSmartViewEntries(root: File, forceRefresh: Boolean): List<RawDirectoryEntry> {
        val key = normalizePath(root)
        if (!forceRefresh) {
            smartViewCache[key]?.let { return it }
        }

        // Flattened Mode: "Shows only folders containing audio files directly... all in same level."
        val aggregated = withContext(Dispatchers.IO) {
            val results = mutableListOf<RawDirectoryEntry>()
            val queue: ArrayDeque<File> = ArrayDeque()
            queue.add(root)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val children = current.listFiles()?.filter { it.isDirectory && !it.isHidden } ?: continue

                // Add children to queue for traversal
                children.forEach { queue.add(it) }

                // Check direct audio files in THIS folder
                // We can use a simplified check or reuse countAudioFiles but we only care about DIRECT count here.
                var directAudioCount = 0
                val files = current.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && !file.isHidden && audioExtensions.contains(file.extension.lowercase())) {
                            directAudioCount++
                            // We don't need to count all of them if we just want to know "exists",
                            // BUT we need the count for the UI badge.
                            if (directAudioCount > 99) break
                        }
                    }
                }

                if (directAudioCount > 0) {
                     results.add(
                        RawDirectoryEntry(
                            file = current,
                            directAudioCount = directAudioCount,
                            totalAudioCount = directAudioCount, // In flattened mode, total is direct for that item
                            canonicalPath = normalizePath(current)
                        )
                    )
                }
            }

            results.sortedBy { it.file.name.lowercase() }
        }

        smartViewCache[key] = aggregated
        return aggregated
    }

    private fun normalizePath(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
    private fun normalizePath(path: String): String = runCatching { File(path).canonicalPath }.getOrDefault(path)

    // Used for logic when breaking down parents (Nav Mode logic)
    private fun getChildrenWithAudio(directory: File): List<RawDirectoryEntry> {
        val targetKey = normalizePath(directory)
        // We might not have cached raw entries for this specific sub-folder if we haven't visited it.
        // So we might need to compute it.

        return runCatching {
            directory.listFiles()
                ?.mapNotNull { child ->
                    if (child.isDirectory && !child.isHidden) {
                        val counts = countAudioFiles(child, forceRefresh = false)
                        if (counts.total > 0) RawDirectoryEntry(child, counts.direct, counts.total, normalizePath(child)) else null
                    } else {
                        null
                    }
                }
                ?: emptyList()
        }.getOrElse { emptyList() }
    }

    private data class AudioCount(val direct: Int, val total: Int)

    private fun collectCoverageExcluding(root: File, excludePath: String): Set<String> {
        val normalizedExclude = normalizePath(excludePath)
        val results = mutableSetOf<String>()

        fun visit(node: File) {
            val nodePath = normalizePath(node)
            // Optimization: If nodePath is the exclude path, stop.
            // If we found the node we want to exclude, we stop and DO NOT add it.
            // This ensures the subtree starting at this node is not selected.
            if (nodePath == normalizedExclude) return

            val children = getChildrenWithAudio(node)
            if (children.isEmpty()) {
                // Leaf node (has audio but no children folders with audio)
                // If this is NOT the excluded path, keep it.
                if (nodePath != normalizedExclude) {
                    results.add(nodePath)
                }
                return
            }

            var coveredByChildren = false
            for (child in children) {
                val childPath = child.canonicalPath
                if (normalizedExclude == childPath || normalizedExclude.startsWith("$childPath/")) {
                    // This child contains the target to exclude (or IS the target).
                    // If it IS the target, we should stop and not add it.
                    // The recursive call will hit `if (nodePath == normalizedExclude) return` at start of visit.
                    visit(File(childPath))
                    coveredByChildren = true
                } else {
                    // This child is disjoint from the excluded path. Keep it whole.
                    results.add(childPath)
                }
            }

            // Note: If 'node' itself has direct files, they are implicitly deselected because
            // we are breaking the parent into children.
            // If the user wants those direct files, they'd need to select 'node'
            // but that would re-select the excluded child in the recursive model.
            // This is an inherent limitation of "Set of Roots" model when you want "Root minus Child".
            // The standard behavior is you get the siblings.
        }

        visit(root)
        return results
    }
}
