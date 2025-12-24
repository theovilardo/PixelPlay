package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.os.Environment
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.DirectoryRuleResolver
import com.theveloper.pixelplay.utils.StorageInfo
import com.theveloper.pixelplay.utils.StorageUtils
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
    val isBlocked: Boolean = false
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
    private val context: Context,
    initialRoot: File = Environment.getExternalStorageDirectory()
) {

    private var visibleRoot: File = initialRoot
    private var rootCanonicalPath: String = normalizePath(visibleRoot)
    private val audioCountCache = ConcurrentHashMap<String, AudioCount>()

    // Available storages (Internal, SD Card, USB)
    private val _availableStorages = MutableStateFlow<List<StorageInfo>>(emptyList())
    val availableStorages: StateFlow<List<StorageInfo>> = _availableStorages.asStateFlow()

    private val _selectedStorageIndex = MutableStateFlow(0)
    val selectedStorageIndex: StateFlow<Int> = _selectedStorageIndex.asStateFlow()

    // Cache for "Raw" entries (without selection state)
    private val directoryChildrenCache = mutableMapOf<String, List<RawDirectoryEntry>>()

    private val _currentPath = MutableStateFlow(visibleRoot)
    val currentPath: StateFlow<File> = _currentPath.asStateFlow()

    private val _rawCurrentDirectoryChildren = MutableStateFlow<List<RawDirectoryEntry>>(emptyList())

    private val _allowedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val allowedDirectories: StateFlow<Set<String>> = _allowedDirectories.asStateFlow()

    private val _blockedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val blockedDirectories: StateFlow<Set<String>> = _blockedDirectories.asStateFlow()

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
        // Load available storages
        refreshAvailableStorages()

        // Observer for preferences
        combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowed, blocked ->
            Pair(allowed, blocked)
        }
            .onEach { (allowed, blocked) ->
                _allowedDirectories.value = allowed.map(::normalizePath).toSet()
                _blockedDirectories.value = blocked.map(::normalizePath).toSet()
            }
            .launchIn(scope)

        // Combiner to produce final UI list with isBlocked state
        combine(
            _rawCurrentDirectoryChildren,
            _allowedDirectories,
            _blockedDirectories
        ) { rawEntries, allowed, blocked ->
            Triple(rawEntries, allowed, blocked)
        }
            .mapLatest { (rawEntries, allowed, blocked) ->
                val resolver = DirectoryRuleResolver(allowed, blocked)
                rawEntries.map { raw ->
                    DirectoryEntry(
                        file = raw.file,
                        directAudioCount = raw.directAudioCount,
                        totalAudioCount = raw.totalAudioCount,
                        canonicalPath = raw.canonicalPath,
                        displayName = raw.displayName,
                        isBlocked = resolver.isBlocked(raw.canonicalPath)
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

    fun refreshAvailableStorages() {
        _availableStorages.value = StorageUtils.getAvailableStorages(context)
        // Ensure selected index is valid
        if (_selectedStorageIndex.value >= _availableStorages.value.size) {
            _selectedStorageIndex.value = 0
        }
    }

    fun selectStorage(index: Int) {
        val storages = _availableStorages.value
        if (index < 0 || index >= storages.size) return

        _selectedStorageIndex.value = index
        val selectedStorage = storages[index]

        // Update the visible root
        visibleRoot = selectedStorage.path
        rootCanonicalPath = normalizePath(visibleRoot)
        _currentPath.value = visibleRoot

        // Load the new storage root
        loadDirectory(visibleRoot, updatePath = true, forceRefresh = false)
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

            val resolver = DirectoryRuleResolver(currentAllowed, currentBlocked)
            val isCurrentlyBlocked = resolver.isBlocked(path)

            if (isCurrentlyBlocked) {
                currentBlocked.remove(path)
                currentAllowed.removeAll { it.startsWith("$path/") }
                currentAllowed.add(path)
                userPreferencesRepository.updateDirectorySelections(currentAllowed, currentBlocked)
                return@launch
            }

            currentBlocked.removeAll { it.startsWith("$path/") }
            currentAllowed.removeAll { it == path || it.startsWith("$path/") }
            currentBlocked.add(path)

            userPreferencesRepository.updateDirectorySelections(currentAllowed, currentBlocked)
        }
    }

    private suspend fun loadDirectoryInternal(file: File, updatePath: Boolean, forceRefresh: Boolean) {
        val target = if (file.isDirectory) file else visibleRoot
        val targetKey = normalizePath(target)

        if (forceRefresh) {
            directoryChildrenCache.remove(targetKey)
        }

        if (updatePath) {
            _currentPath.value = target
        }

        val cachedEntries = if (!forceRefresh) {
            directoryChildrenCache[targetKey]
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
