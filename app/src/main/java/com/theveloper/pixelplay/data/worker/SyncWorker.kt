package com.theveloper.pixelplay.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Trace // Import Trace
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.utils.AlbumArtCacheManager
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.AudioMetaUtils.getAudioMetadata
import com.theveloper.pixelplay.utils.DirectoryRuleResolver
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty
import com.theveloper.pixelplay.utils.splitArtistsByDelimiters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class SyncMode {
    INCREMENTAL,
    FULL,
    REBUILD
}

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val musicDao: MusicDao,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val lyricsRepository: LyricsRepository
) : CoroutineWorker(appContext, workerParams) {

    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                Trace.beginSection("SyncWorker.doWork")
                try {
                    val syncModeName =
                            inputData.getString(INPUT_SYNC_MODE) ?: SyncMode.INCREMENTAL.name
                    val syncMode = SyncMode.valueOf(syncModeName)
                    val forceMetadata = inputData.getBoolean(INPUT_FORCE_METADATA, false)

                    Timber.tag(TAG)
                        .i("Starting MediaStore synchronization (Mode: $syncMode, ForceMetadata: $forceMetadata)...")
                    val startTime = System.currentTimeMillis()

                    val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
                    val groupByAlbumArtist =
                            userPreferencesRepository.groupByAlbumArtistFlow.first()
                    val rescanRequired =
                            userPreferencesRepository.artistSettingsRescanRequiredFlow.first()

                    // Feature: Directory Filtering
                    val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
                    val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
                    val directoryResolver = DirectoryRuleResolver(allowedDirs, blockedDirs)
                    
                    var lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()

                    Timber.tag(TAG)
                        .d("Artist parsing delimiters: $artistDelimiters, groupByAlbumArtist: $groupByAlbumArtist, rescanRequired: $rescanRequired")

                    // --- MEDIA SCAN PHASE ---
                    // For INCREMENTAL or FULL sync, trigger a media scan to detect new files
                    // that may not have been indexed by MediaStore yet (e.g., files added via USB)
                    if (syncMode != SyncMode.REBUILD) {
                        triggerMediaScanForNewFiles()
                    }

                    // --- DELETION PHASE ---
                    // Detect and remove deleted songs efficiently using ID comparison
                    // We do this for INCREMENTAL and FULL modes. REBUILD clears everything anyway.
                    if (syncMode != SyncMode.REBUILD) {
                        val localSongIds = musicDao.getAllSongIds().toHashSet()
                        val mediaStoreIds = fetchMediaStoreIds(directoryResolver)

                        // Identify IDs that are in local DB but not in MediaStore
                        val deletedIds = localSongIds - mediaStoreIds

                        if (deletedIds.isNotEmpty()) {
                            Timber.tag(TAG)
                                .i("Found ${deletedIds.size} deleted songs. Removing from database...")
                            // Chunk deletions to avoid SQLite variable limit (default 999)
                            val batchSize = 500
                            deletedIds.chunked(batchSize).forEach { chunk ->
                                musicDao.deleteSongsByIds(chunk.toList())
                                musicDao.deleteCrossRefsBySongIds(chunk.toList())
                            }
                        } else {
                            Timber.tag(TAG).d("No deleted songs found.")
                        }
                    }

                    // --- FETCH PHASE ---
                    // Determine what to fetch based on mode
                    val isFreshInstall = musicDao.getSongCount().first() == 0

                    // If REBUILD or FULL or RescanRequired or Fresh Install -> Fetch EVERYTHING
                    // (timestamp = 0)
                    // If INCREMENTAL -> Fetch only changes since lastSyncTimestamp
                    val fetchTimestamp =
                            if (syncMode == SyncMode.INCREMENTAL &&
                                            !rescanRequired &&
                                            !isFreshInstall
                            ) {
                                lastSyncTimestamp /
                                        1000 // Convert to seconds for MediaStore comparison
                            } else {
                                0L
                            }

                    Timber.tag(TAG)
                        .i("Fetching music from MediaStore (since: $fetchTimestamp seconds)...")

                    // Update every 50 songs or ~5% of library
                    val progressBatchSize = 50

                    val mediaStoreSongs =
                            fetchMusicFromMediaStore(
                                    fetchTimestamp,
                                    forceMetadata,
                                    directoryResolver,
                                    progressBatchSize
                            ) { current, total, phaseOrdinal ->
                                setProgress(
                                        workDataOf(
                                                PROGRESS_CURRENT to current,
                                                PROGRESS_TOTAL to total,
                                                PROGRESS_PHASE to phaseOrdinal
                                        )
                                )
                            }

                    Timber.tag(TAG)
                        .i("Fetched ${mediaStoreSongs.size} new/modified songs from MediaStore.")

                    // --- PROCESSING PHASE ---
                    if (mediaStoreSongs.isNotEmpty()) {

                        // If rebuilding, clear everything first
                        if (syncMode == SyncMode.REBUILD) {
                            Timber.tag(TAG)
                                .i("Rebuild mode: Clearing all music data before insert.")
                            musicDao.clearAllMusicDataWithCrossRefs()
                        }

                        val allExistingArtists =
                                if (syncMode == SyncMode.REBUILD) {
                                    emptyList()
                                } else {
                                    musicDao.getAllArtistsListRaw()
                                }

                        val existingArtistImageUrls =
                                allExistingArtists.associate { it.id to it.imageUrl }
                        
                        // Load all existing artist IDs to ensure stability across incremental syncs
                        val existingArtistIdMap = allExistingArtists.associate { it.name to it.id }.toMutableMap()
                        val maxArtistId = musicDao.getMaxArtistId() ?: 0L

                        // Prepare list of existing songs to preserve user edits
                        // We only need to check against existing songs if we are updating them
                        val localSongsMap =
                                if (syncMode != SyncMode.REBUILD) {
                                    musicDao.getAllSongsList().associateBy { it.id }
                                } else {
                                    emptyMap()
                                }

                        val songsToProcess = mediaStoreSongs

                        Timber.tag(TAG)
                            .i("Processing ${songsToProcess.size} songs for upsert. Hash: ${songsToProcess.hashCode()}")

                        val songsToInsert =
                                songsToProcess.map { mediaStoreSong ->
                                    val localSong = localSongsMap[mediaStoreSong.id]
                                    if (localSong != null) {
                                        // Preserve user-edited fields
                                        val needsArtistCompare =
                                                !rescanRequired &&
                                                        localSong.artistName.isNotBlank() &&
                                                        localSong.artistName !=
                                                                mediaStoreSong.artistName

                                        val shouldPreserveArtistName =
                                                if (needsArtistCompare) {
                                                    val mediaStoreArtists =
                                                            mediaStoreSong.artistName
                                                                    .splitArtistsByDelimiters(
                                                                            artistDelimiters
                                                                    )
                                                    val mediaStorePrimaryArtist =
                                                            mediaStoreArtists.firstOrNull()?.trim()
                                                    val mediaStoreHasMultipleArtists =
                                                            mediaStoreArtists.size > 1
                                                    !(mediaStoreHasMultipleArtists &&
                                                            mediaStorePrimaryArtist != null &&
                                                            localSong.artistName.trim() ==
                                                                    mediaStorePrimaryArtist)
                                                } else {
                                                    false
                                                }

                                        mediaStoreSong.copy(
                                                dateAdded =
                                                        localSong.dateAdded, // Preserve original
                                                // date added if needed
                                                lyrics = localSong.lyrics,
                                                title =
                                                        if (localSong.title !=
                                                                        mediaStoreSong.title &&
                                                                        localSong.title.isNotBlank()
                                                        )
                                                                localSong.title
                                                        else mediaStoreSong.title,
                                                artistName =
                                                        if (shouldPreserveArtistName)
                                                                localSong.artistName
                                                        else mediaStoreSong.artistName,
                                                albumName =
                                                        if (localSong.albumName !=
                                                                        mediaStoreSong.albumName &&
                                                                        localSong.albumName
                                                                                .isNotBlank()
                                                        )
                                                                localSong.albumName
                                                        else mediaStoreSong.albumName,
                                                genre = localSong.genre ?: mediaStoreSong.genre,
                                                trackNumber =
                                                        if (localSong.trackNumber != 0 &&
                                                                        localSong.trackNumber !=
                                                                                mediaStoreSong
                                                                                        .trackNumber
                                                        )
                                                                localSong.trackNumber
                                                        else mediaStoreSong.trackNumber,
                                                albumArtUriString = localSong.albumArtUriString
                                                                ?: mediaStoreSong.albumArtUriString
                                        )
                                    } else {
                                        mediaStoreSong
                                    }
                                }

                        val (correctedSongs, albums, artists, crossRefs) =
                                preProcessAndDeduplicateWithMultiArtist(
                                        songs = songsToInsert,
                                        artistDelimiters = artistDelimiters,
                                        groupByAlbumArtist = groupByAlbumArtist,
                                        existingArtistImageUrls = existingArtistImageUrls,
                                        existingArtistIdMap = existingArtistIdMap,
                                        initialMaxArtistId = maxArtistId
                                )

                        // Use incrementalSyncMusicData for all modes except REBUILD
                        // Even for FULL sync, we can just upsert the values
                        if (syncMode == SyncMode.REBUILD) {
                            musicDao.insertMusicDataWithCrossRefs(
                                    correctedSongs,
                                    albums,
                                    artists,
                                    crossRefs
                            )
                        } else {
                            // incrementalSyncMusicData handles upserts efficiently
                            // processing deleted songs was already handled at the start
                            musicDao.incrementalSyncMusicData(
                                    songs = correctedSongs,
                                    albums = albums,
                                    artists = artists,
                                    crossRefs = crossRefs,
                                    deletedSongIds = emptyList() // Already handled
                            )
                        }

                        // Clear the rescan required flag
                        userPreferencesRepository.clearArtistSettingsRescanRequired()

                        val endTime = System.currentTimeMillis()
                        Timber.tag(TAG)
                            .i("Synchronization finished successfully in ${endTime - startTime}ms.")
                        userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())

                        // Count total songs for the output
                        val totalSongs = musicDao.getSongCount().first()

                        // --- LRC SCANNING PHASE ---
                        val autoScanLrc = userPreferencesRepository.autoScanLrcFilesFlow.first()
                        if (autoScanLrc) {
                            Timber.tag(TAG).i("Auto-scan LRC files enabled. Starting scan phase...")

                            val allSongsEntities = musicDao.getAllSongsList()
                            val allSongs =
                                    allSongsEntities.map { entity ->
                                        Song(
                                                id = entity.id.toString(),
                                                title = entity.title,
                                                artist = entity.artistName,
                                                artistId = entity.artistId,
                                                album = entity.albumName,
                                                albumId = entity.albumId,
                                                path = entity.filePath,
                                                contentUriString = entity.contentUriString,
                                                albumArtUriString = entity.albumArtUriString,
                                                duration = entity.duration,
                                                lyrics = entity.lyrics,
                                                dateAdded = entity.dateAdded,
                                                trackNumber = entity.trackNumber,
                                                year = entity.year,
                                                mimeType = entity.mimeType,
                                                bitrate = entity.bitrate,
                                                sampleRate = entity.sampleRate
                                        )
                                    }

                            val scannedCount =
                                    lyricsRepository.scanAndAssignLocalLrcFiles(allSongs) {
                                            current,
                                            total ->
                                        setProgress(
                                                workDataOf(
                                                        PROGRESS_CURRENT to current,
                                                        PROGRESS_TOTAL to total,
                                                        PROGRESS_PHASE to
                                                                SyncProgress.SyncPhase.SCANNING_LRC
                                                                        .ordinal
                                                )
                                        )
                                    }

                            Log.i(TAG, "LRC Scan finished. Assigned lyrics to $scannedCount songs.")
                        }
                        
                        // Clean orphaned album art cache files
                        val allSongIds = musicDao.getAllSongIds().toSet()
                        AlbumArtCacheManager.cleanOrphanedCacheFiles(applicationContext, allSongIds)

                        Result.success(workDataOf(OUTPUT_TOTAL_SONGS to totalSongs))
                    } else {
                        Log.i(TAG, "No new or modified songs found.")

                        // If it was a fresh install/rebuild and we found nothing, clear everything
                        if ((syncMode == SyncMode.REBUILD || isFreshInstall) &&
                                        mediaStoreSongs.isEmpty()
                        ) {
                            musicDao.clearAllMusicDataWithCrossRefs()
                            Log.w(
                                    TAG,
                                    "MediaStore fetch resulted in empty list. Local music data cleared."
                            )
                        }

                        val endTime = System.currentTimeMillis()
                        Log.i(
                                TAG,
                                "Synchronization (No Changes) finished in ${endTime - startTime}ms."
                        )
                        userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())

                        val totalSongs = musicDao.getSongCount().first()
                        
                        // Clean orphaned album art cache files
                        val allSongIds = musicDao.getAllSongIds().toSet()
                        AlbumArtCacheManager.cleanOrphanedCacheFiles(applicationContext, allSongIds)
                        
                        Result.success(workDataOf(OUTPUT_TOTAL_SONGS to totalSongs))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during MediaStore synchronization", e)
                    Result.failure()
                } finally {
                    Trace.endSection() // End SyncWorker.doWork
                }
            }

    /**
     * Efficiently fetches ONLY the IDs of all songs in MediaStore. Used for fast deletion
     * detection.
     */
    private fun getBaseSelection(): Pair<String, Array<String>> {
        val selectionBuilder = StringBuilder()
        val selectionArgsList = mutableListOf<String>()

        selectionBuilder.append(
                "((${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?) "
        )
        selectionArgsList.add("10000")

        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' ")
        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac' ")
        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.wav' ")
        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.opus' ")
        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.ogg')")

        return Pair(selectionBuilder.toString(), selectionArgsList.toTypedArray())
    }

    /**
     * Efficiently fetches ONLY the IDs of all songs in MediaStore. Used for fast deletion
     * detection.
     */
    private fun fetchMediaStoreIds(directoryResolver: DirectoryRuleResolver): Set<Long> {
        val ids = HashSet<Long>()
        // We need DATA to check path filtering
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = getBaseSelection()

        contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                )
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idCol >= 0 && dataCol >= 0) {
                        while (cursor.moveToNext()) {
                            val path = cursor.getString(dataCol)
                            val parentPath = File(path).parent
                            if (parentPath != null && directoryResolver.isBlocked(parentPath)) {
                                continue 
                            }
                            ids.add(cursor.getLong(idCol))
                        }
                    }
                }
        return ids
    }

    /** Data class to hold the result of multi-artist preprocessing. */
    private data class MultiArtistProcessResult(
            val songs: List<SongEntity>,
            val albums: List<AlbumEntity>,
            val artists: List<ArtistEntity>,
            val crossRefs: List<SongArtistCrossRef>
    )

    /**
     * Process songs with multi-artist support. Splits artist names by delimiters and creates proper
     * cross-references.
     */
    private fun preProcessAndDeduplicateWithMultiArtist(
            songs: List<SongEntity>,
            artistDelimiters: List<String>,
            groupByAlbumArtist: Boolean,
            existingArtistImageUrls: Map<Long, String?>,
            existingArtistIdMap: MutableMap<String, Long>,
            initialMaxArtistId: Long
    ): MultiArtistProcessResult {
        
        val nextArtistId = AtomicLong(initialMaxArtistId + 1)
        val artistNameToId = existingArtistIdMap // Re-use the map passed in
        
        val allCrossRefs = mutableListOf<SongArtistCrossRef>()
        val artistTrackCounts = mutableMapOf<Long, Int>()
        val albumMap = mutableMapOf<Pair<String, String>, Long>()
        val artistSplitCache = mutableMapOf<String, List<String>>()
        val correctedSongs = ArrayList<SongEntity>(songs.size)

        songs.forEach { song ->
            val rawArtistName = song.artistName
            val songArtistNameTrimmed = rawArtistName.trim()
            val artistsForSong =
                    artistSplitCache.getOrPut(rawArtistName) {
                        rawArtistName.splitArtistsByDelimiters(artistDelimiters)
                    }

            artistsForSong.forEach { artistName ->
                val normalizedName = artistName.trim()
                if (normalizedName.isNotEmpty() && !artistNameToId.containsKey(normalizedName)) {
                     // Check if it's the song's primary artist and we want to preserve that ID if possible?
                     // Actually, just generate new ID if not found in map.
                     val id = nextArtistId.getAndIncrement()
                     artistNameToId[normalizedName] = id
                }
            }
            
            val primaryArtistName =
                    artistsForSong.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                            ?: songArtistNameTrimmed
            val primaryArtistId = artistNameToId[primaryArtistName] ?: song.artistId

            artistsForSong.forEachIndexed { index, artistName ->
                val normalizedName = artistName.trim()
                val artistId = artistNameToId[normalizedName]
                if (artistId != null) {
                    val isPrimary = (index == 0) // First artist is primary
                    allCrossRefs.add(
                            SongArtistCrossRef(
                                    songId = song.id,
                                    artistId = artistId,
                                    isPrimary = isPrimary
                            )
                    )
                    artistTrackCounts[artistId] = (artistTrackCounts[artistId] ?: 0) + 1
                }
            }

            // --- Album Logic ---
            val rawAlbumName = song.albumName.trim()
            val albumIdentityArtist = if (groupByAlbumArtist) {
                song.albumArtist?.trim()?.takeIf { it.isNotEmpty() } ?: primaryArtistName
            } else {
                primaryArtistName
            }
            
            val albumKey = rawAlbumName to albumIdentityArtist
            
            if (!albumMap.containsKey(albumKey)) {
                albumMap[albumKey] = song.albumId 
            }
            val finalAlbumId = albumMap[albumKey] ?: song.albumId // fallback

            correctedSongs.add(
                    song.copy(
                            artistId = primaryArtistId,
                            artistName = primaryArtistName,
                            albumId = finalAlbumId
                    )
            )
        }

        // Build Entities
        val artistEntities = artistNameToId.map { (name, id) ->
            val count = artistTrackCounts[id] ?: 0
            ArtistEntity(
                id = id,
                name = name,
                trackCount = count,
                imageUrl = existingArtistImageUrls[id]
            )
        }
        
        // Re-calculate Album Entities from the corrected songs to ensure we have valid metadata (Art, Year)
        // which isn't available in the simple albumMap (which only has ID)
        val albumEntities = correctedSongs.groupBy { it.albumId }.map { (catAlbumId, songsInAlbum) ->
             val firstSong = songsInAlbum.first()
             // Determine Album Artist Name
             val determinedAlbumArtist = firstSong.albumArtist?.takeIf { it.isNotBlank() } 
                 ?: firstSong.artistName
                 
             // Determine Album Artist ID (best effort lookup)
             val determinedAlbumArtistId = artistNameToId[determinedAlbumArtist] ?: 0L

             AlbumEntity(
                 id = catAlbumId,
                 title = firstSong.albumName,
                 artistName = determinedAlbumArtist,
                 artistId = determinedAlbumArtistId,
                 albumArtUriString = firstSong.albumArtUriString,
                 songCount = songsInAlbum.size, 
                 year = firstSong.year
             )
        }

        return MultiArtistProcessResult(
                songs = correctedSongs, // Corrected songs have the right Album IDs now
                albums = albumEntities,
                artists = artistEntities,
                crossRefs = allCrossRefs
        )
    }

    private fun fetchAlbumArtUrisByAlbumId(): Map<Long, String> {
        val projection = arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART)

        return buildMap {
            contentResolver.query(
                            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            projection,
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                        val artCol = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
                        if (artCol >= 0) {
                            while (cursor.moveToNext()) {
                                val albumId = cursor.getLong(idCol)
                                val storedArtPath = cursor.getString(artCol)
                                val uriString =
                                        when {
                                            !storedArtPath.isNullOrBlank() ->
                                                    File(storedArtPath).toURI().toString()
                                            albumId > 0 ->
                                                    ContentUris.withAppendedId(
                                                                    "content://media/external/audio/albumart".toUri(),
                                                                    albumId
                                                            )
                                                            .toString()
                                            else -> null
                                        }

                                if (uriString != null) put(albumId, uriString)
                            }
                        }
                    }
        }
    }

    /**
     * Fetches a map of Song ID -> Genre Name using the MediaStore.Audio.Genres table. This is
     * necessary because the GENRE column in MediaStore.Audio.Media is not reliably available or
     * populated on all Android versions (especially pre-API 30).
     * 
     * Optimized: 
     * 1. Caches results for 1 hour to avoid refetching on incremental syncs
     * 2. Fetches all genres first, then queries members in parallel with controlled concurrency
     */
    private suspend fun fetchGenreMap(forceRefresh: Boolean = false): Map<Long, String> = coroutineScope {
        // Check cache first (valid for 1 hour)
        val now = System.currentTimeMillis()
        val cacheAge = now - genreMapCacheTimestamp
        if (!forceRefresh && genreMapCache.isNotEmpty() && cacheAge < GENRE_CACHE_TTL_MS) {
            Log.d(TAG, "Using cached genre map (${genreMapCache.size} entries, age: ${cacheAge/1000}s)")
            return@coroutineScope genreMapCache
        }
        
        val genreMap = ConcurrentHashMap<Long, String>()
        val genreProjection = arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME)
        
        // Semaphore to limit concurrent queries (avoid overwhelming ContentResolver)
        val querySemaphore = Semaphore(4)

        try {
            // Step 1: Fetch all genres (single query)
            val genres = mutableListOf<Pair<Long, String>>()
            
            contentResolver.query(
                            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                            genreProjection,
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.Audio.Genres._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)

                        if (idCol >= 0 && nameCol >= 0) {
                            while (cursor.moveToNext()) {
                                val genreId = cursor.getLong(idCol)
                                val genreName = cursor.getString(nameCol)

                                if (!genreName.isNullOrBlank() &&
                                                !genreName.equals("unknown", ignoreCase = true)
                                ) {
                                    genres.add(genreId to genreName)
                                }
                            }
                        }
                    }
            
            // Step 2: Fetch members for each genre in parallel (controlled concurrency)
            genres.map { (genreId, genreName) ->
                async(Dispatchers.IO) {
                    querySemaphore.withPermit {
                        val membersUri =
                                MediaStore.Audio.Genres.Members.getContentUri(
                                        "external",
                                        genreId
                                )
                        val membersProjection =
                                arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID)

                        contentResolver.query(
                                        membersUri,
                                        membersProjection,
                                        null,
                                        null,
                                        null
                                )
                                ?.use { membersCursor ->
                                    val audioIdCol =
                                            membersCursor.getColumnIndex(
                                                    MediaStore.Audio.Genres.Members.AUDIO_ID
                                            )
                                    if (audioIdCol >= 0) {
                                        while (membersCursor.moveToNext()) {
                                            val audioId = membersCursor.getLong(audioIdCol)
                                            // If a song has multiple genres, the last one processed wins.
                                            // This is acceptable as a primary genre for display.
                                            genreMap[audioId] = genreName
                                        }
                                    }
                                }
                    }
                }
            }.awaitAll()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching genre map", e)
        }
        
        // Update cache
        if (genreMap.isNotEmpty()) {
            genreMapCache = genreMap.toMap()
            genreMapCacheTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Genre map cache updated with ${genreMap.size} entries")
        }
        
        genreMap
    }

    /** Raw data extracted from cursor - lightweight class for fast iteration */
    private data class RawSongData(
            val id: Long,
            val albumId: Long,
            val artistId: Long,
            val filePath: String,
            val title: String,
            val artist: String,
            val album: String,
            val albumArtist: String?,
            val duration: Long,
            val trackNumber: Int,
            val year: Int,
            val dateModified: Long
    )

    private suspend fun fetchMusicFromMediaStore(
            sinceTimestamp: Long, // Seconds
            forceMetadata: Boolean,
            directoryResolver: DirectoryRuleResolver,
            progressBatchSize: Int,
            onProgress: suspend (current: Int, total: Int, phaseOrdinal: Int) -> Unit
    ): List<SongEntity> {
        Trace.beginSection("SyncWorker.fetchMusicFromMediaStore")

        val deepScan = forceMetadata
        val albumArtByAlbumId = if (!deepScan) fetchAlbumArtUrisByAlbumId() else emptyMap()
        val genreMap = fetchGenreMap() // Load genres upfront

        val projection =
                arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ARTIST_ID,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.ALBUM_ARTIST,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.TRACK,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.DATE_MODIFIED
                )

        val (baseSelection, baseArgs) = getBaseSelection()
        val selectionBuilder = StringBuilder(baseSelection)
        val selectionArgsList = baseArgs.toMutableList()

        // Incremental selection
        if (sinceTimestamp > 0) {
            selectionBuilder.append(
                    " AND (${MediaStore.Audio.Media.DATE_MODIFIED} > ? OR ${MediaStore.Audio.Media.DATE_ADDED} > ?)"
            )
            selectionArgsList.add(sinceTimestamp.toString())
            selectionArgsList.add(sinceTimestamp.toString())
        }

        val selection = selectionBuilder.toString()
        val selectionArgs = selectionArgsList.toTypedArray()

        // Phase 1: Fast cursor iteration to collect raw data
        val rawDataList = mutableListOf<RawSongData>()

        contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                )
                ?.use { cursor ->
                    val totalCount = cursor.count
                    onProgress(0, totalCount, SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal)

                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val dateAddedCol =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        try {
                            val data = cursor.getString(dataCol)
                            val parentPath = File(data).parent
                            if (parentPath != null) {
                                val normalizedParent = File(parentPath).absolutePath
                                if (directoryResolver.isBlocked(normalizedParent)) {
                                    continue
                                }
                            }
                        } catch (e: Exception) {
                            // Proceed on error
                        }

                        val songId = cursor.getLong(idCol)
                        rawDataList.add(
                                RawSongData(
                                        id = cursor.getLong(idCol),
                                        albumId = cursor.getLong(albumIdCol),
                                        artistId = cursor.getLong(artistIdCol),
                                        filePath = cursor.getString(dataCol) ?: "",
                                        title =
                                                cursor.getString(titleCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Title" },
                                        artist =
                                                cursor.getString(artistCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Artist" },
                                        album =
                                                cursor.getString(albumCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Album" },
                                        albumArtist =
                                                if (albumArtistCol >= 0)
                                                        cursor.getString(albumArtistCol)
                                                                ?.normalizeMetadataTextOrEmpty()
                                                                ?.takeIf { it.isNotBlank() }
                                                else null,
                                        duration = cursor.getLong(durationCol),
                                        trackNumber = cursor.getInt(trackCol),
                                        year = cursor.getInt(yearCol),
                                        dateModified = cursor.getLong(dateAddedCol)
                                )
                        )
                    }
                }

        val totalCount = rawDataList.size
        if (totalCount == 0) {
            Trace.endSection()
            return emptyList()
        }

        // Phase 2: Parallel processing of songs
        val processedCount = AtomicInteger(0)
        val concurrencyLimit = 8 // Limit parallel operations to prevent resource exhaustion
        val semaphore = Semaphore(concurrencyLimit)

        val songs = coroutineScope {
            rawDataList
                    .map { raw ->
                        async {
                            semaphore.withPermit {
                                val song =
                                        processSongData(raw, albumArtByAlbumId, genreMap, deepScan)

                                // Report progress
                                val count = processedCount.incrementAndGet()
                                if (count % progressBatchSize == 0 || count == totalCount) {
                                    onProgress(
                                            count,
                                            totalCount,
                                            SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal
                                    )
                                }

                                song
                            }
                        }
                    }
                    .awaitAll()
        }

        Trace.endSection()
        return songs
    }

    /**
     * Process a single song's raw data into a SongEntity. This is the CPU/IO intensive work that
     * benefits from parallelization.
     */
    private suspend fun processSongData(
            raw: RawSongData,
            albumArtByAlbumId: Map<Long, String>,
            genreMap: Map<Long, String>,
            deepScan: Boolean
    ): SongEntity {
        val parentDir = java.io.File(raw.filePath).parent ?: ""
        val contentUriString =
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, raw.id)
                        .toString()

        var albumArtUriString = albumArtByAlbumId[raw.albumId]
        if (deepScan) {
            albumArtUriString =
                    AlbumArtUtils.getAlbumArtUri(
                            applicationContext,
                            musicDao,
                            raw.filePath,
                            raw.albumId,
                            raw.id,
                            true
                    )
                            ?: albumArtUriString
        }
        val audioMetadata =
                if (deepScan) getAudioMetadata(musicDao, raw.id, raw.filePath, true) else null

        var title = raw.title
        var artist = raw.artist
        var album = raw.album
        var trackNumber = raw.trackNumber
        var year = raw.year
        var genre: String? = genreMap[raw.id] // Use mapped genre as default

        val shouldAugmentMetadata =
                deepScan ||
                        raw.filePath.endsWith(".wav", true) ||
                        raw.filePath.endsWith(".opus", true) ||
                        raw.filePath.endsWith(".ogg", true) ||
                        raw.filePath.endsWith(".oga", true) ||
                        raw.filePath.endsWith(".aiff", true)

        if (shouldAugmentMetadata) {
            val file = java.io.File(raw.filePath)
            if (file.exists()) {
                try {
                    AudioMetadataReader.read(file)?.let { meta ->
                        if (!meta.title.isNullOrBlank()) title = meta.title
                        if (!meta.artist.isNullOrBlank()) artist = meta.artist
                        if (!meta.album.isNullOrBlank()) album = meta.album
                        if (!meta.genre.isNullOrBlank()) genre = meta.genre
                        if (meta.trackNumber != null) trackNumber = meta.trackNumber
                        if (meta.year != null) year = meta.year

                        meta.artwork?.let { art ->
                            val uri =
                                    AlbumArtUtils.saveAlbumArtToCache(
                                            applicationContext,
                                            art.bytes,
                                            raw.id
                                    )
                            albumArtUriString = uri.toString()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read metadata via TagLib for ${raw.filePath}", e)
                }
            }
        }

        return SongEntity(
                id = raw.id,
                title = title,
                artistName = artist,
                artistId = raw.artistId,
                albumArtist = raw.albumArtist,
                albumName = album,
                albumId = raw.albumId,
                contentUriString = contentUriString,
                albumArtUriString = albumArtUriString,
                duration = raw.duration,
                genre = genre,
                filePath = raw.filePath,
                parentDirectoryPath = parentDir,
                trackNumber = trackNumber,
                year = year,
                dateAdded =
                        raw.dateModified.let { seconds ->
                            if (seconds > 0) TimeUnit.SECONDS.toMillis(seconds)
                            else System.currentTimeMillis()
                        },
                mimeType = audioMetadata?.mimeType,
                sampleRate = audioMetadata?.sampleRate,
                bitrate = audioMetadata?.bitrate
        )
    }

    /**
     * Triggers a media scan ONLY for new files that are not yet in MediaStore.
     * This is a fast, incremental scan optimized for pull-to-refresh.
     * It compares filesystem files with MediaStore entries and only scans the difference.
     */
    private suspend fun triggerMediaScanForNewFiles() {
        withContext(Dispatchers.IO) {
            // "Global Augmented Scan"
            // We always scan the External Storage Root, but we use the 'allowedDirs' list
            // as "Explicit Includes" to bypass default filters (like skipping Android).
            val searchRoots = listOf(Environment.getExternalStorageDirectory())
            val allowedSet = userPreferencesRepository.allowedDirectoriesFlow.first().map { File(it) }.toSet()
            
            Log.i(TAG, "Starting Global Augmented Scan. Explicit includes: ${allowedSet.size}")
            
            val existingRoots = searchRoots.filter { it.exists() && it.isDirectory }

            if (existingRoots.isEmpty()) {
                Log.d(TAG, "No storage roots found")
                return@withContext
            }

            // Get all file paths currently in MediaStore
            val mediaStorePaths = fetchMediaStoreFilePaths()
            
            Log.d(TAG, "MediaStore has ${mediaStorePaths.size} known files")

            // Collect audio files from filesystem that are NOT in MediaStore
            val audioExtensions =
                    setOf("mp3", "flac", "m4a", "wav", "ogg", "opus", "aac", "wma", "aiff")
            val newFilesToScan = mutableListOf<String>()

            existingRoots.forEach { root ->
                root.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name
                        if (dir.isHidden || name.startsWith(".")) return@onEnter false
                        
                        // Default Skip Rules (System Folders)
                        val isSystemFolder = (name == "Android" || name == "data" || name == "obb")
                        if (isSystemFolder) {
                            // Check if this specific folder is Explicitly Allowed or is a Parent of an allowed folder
                            // e.g. if Allowed is "Android/media", we MUST enter "Android".
                            // e.g. if Allowed is "Android" (root), we MUST enter "Android".
                            val path = dir.absolutePath
                            val isAllowed = allowedSet.any { allowed -> 
                                allowed.absolutePath == path || allowed.absolutePath.startsWith("$path/")
                            }
                            
                            if (!isAllowed) {
                                // Apply strict skipping for Android/data and Android/obb if not allowed
                                val parent = dir.parentFile
                                if (name == "Android" && parent?.absolutePath == Environment.getExternalStorageDirectory().absolutePath) return@onEnter false
                                if (parent?.name == "Android" && (name == "data" || name == "obb")) return@onEnter false
                            }
                        }
                        true
                    }
                    .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                    .filter { it.absolutePath !in mediaStorePaths } // Only new files
                    .forEach { newFilesToScan.add(it.absolutePath) }
            }

            if (newFilesToScan.isEmpty()) {
                Log.d(TAG, "No new audio files found - MediaStore is up to date")
                return@withContext
            }

            Log.i(TAG, "Found ${newFilesToScan.size} NEW audio files to scan")

            // Scan only the new files
            val latch = CountDownLatch(1)
            var scannedCount = 0

            MediaScannerConnection.scanFile(
                applicationContext, 
                newFilesToScan.toTypedArray(), 
                null
            ) { _, _ ->
                scannedCount++
                if (scannedCount >= newFilesToScan.size) {
                    latch.countDown()
                }
            }

            // Wait for scan to complete (max 15 seconds)
            val completed = latch.await(15, TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "Media scan timeout after scanning $scannedCount/${newFilesToScan.size} files")
            } else {
                Log.i(TAG, "Media scan completed for ${newFilesToScan.size} new files")
            }
        }
    }

    /**
     * Fetches all file paths currently known to MediaStore.
     * Used to identify new files that need scanning.
     */
    private fun fetchMediaStoreFilePaths(): Set<String> {
        val paths = HashSet<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = getBaseSelection()
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataCol >= 0) {
                while (cursor.moveToNext()) {
                    cursor.getString(dataCol)?.let { paths.add(it) }
                }
            }
        }
        return paths
    }

    companion object {
        const val WORK_NAME = "com.theveloper.pixelplay.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"
        const val INPUT_FORCE_METADATA = "input_force_metadata"
        const val INPUT_SYNC_MODE = "input_sync_mode"

        // Progress reporting constants
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_PHASE = "progress_phase"
        const val OUTPUT_TOTAL_SONGS = "output_total_songs"
        
        // Genre cache - shared across worker instances to avoid refetching on incremental syncs
        private const val GENRE_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
        @Volatile private var genreMapCache: Map<Long, String> = emptyMap()
        @Volatile private var genreMapCacheTimestamp: Long = 0L
        
        fun invalidateGenreCache() {
            genreMapCache = emptyMap()
            genreMapCacheTimestamp = 0L
            Log.d(TAG, "Genre cache invalidated")
        }

        fun startUpSyncWork(deepScan: Boolean = false) =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(
                                workDataOf(
                                        INPUT_FORCE_METADATA to deepScan,
                                        INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name
                                )
                        )
                        .build()

        fun incrementalSyncWork() =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name))
                        .build()

        fun fullSyncWork(deepScan: Boolean = false) =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(
                                workDataOf(
                                        INPUT_SYNC_MODE to SyncMode.FULL.name,
                                        INPUT_FORCE_METADATA to deepScan
                                )
                        )
                        .build()

        fun rebuildDatabaseWork() =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.REBUILD.name))
                        .build()
    }
}
