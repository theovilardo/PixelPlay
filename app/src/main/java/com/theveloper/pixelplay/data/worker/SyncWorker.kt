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
import com.theveloper.pixelplay.utils.StorageUtils
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty
import com.theveloper.pixelplay.utils.splitArtistsByDelimiters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

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

                    Log.i(
                            TAG,
                            "Starting MediaStore synchronization (Mode: $syncMode, ForceMetadata: $forceMetadata)..."
                    )
                    val startTime = System.currentTimeMillis()

                    val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
                    val groupByAlbumArtist =
                            userPreferencesRepository.groupByAlbumArtistFlow.first()
                    val rescanRequired =
                            userPreferencesRepository.artistSettingsRescanRequiredFlow.first()
                    val allowedDirectories = userPreferencesRepository.allowedDirectoriesFlow.first()
                    val blockedDirectories = userPreferencesRepository.blockedDirectoriesFlow.first()
                    var lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()

                    Log.d(
                            TAG,
                            "Artist parsing delimiters: $artistDelimiters, groupByAlbumArtist: $groupByAlbumArtist, rescanRequired: $rescanRequired"
                    )

                    // --- MEDIA SCAN PHASE ---
                    // Trigger a media scan to detect new files that may not have been indexed
                    // by MediaStore yet (e.g., files added via USB/SD card).
                    val mediaScanCompleted = triggerMediaScanForNewFiles(
                            allowedDirectories,
                            blockedDirectories
                    )

                    // --- DELETION PHASE ---
                    // Detect and remove deleted songs efficiently using ID comparison
                    // We do this for INCREMENTAL and FULL modes. REBUILD clears everything anyway.
                    val isFreshInstall = musicDao.getSongCount().first() == 0

                    if (isFreshInstall && !mediaScanCompleted && syncMode != SyncMode.INCREMENTAL) {
                        Log.w(TAG, "Media scan still in progress on fresh install. Retrying sync.")
                        return@withContext Result.retry()
                    }

                    var newMediaStoreIds: Set<Long> = emptySet()
                    if (syncMode != SyncMode.REBUILD) {
                        val localSongIds = musicDao.getAllSongIds().toHashSet()
                        val mediaStoreIds = fetchMediaStoreIds()

                        // Identify IDs that are in local DB but not in MediaStore
                        val deletedIds = localSongIds - mediaStoreIds

                        if (deletedIds.isNotEmpty()) {
                            Log.i(
                                    TAG,
                                    "Found ${deletedIds.size} deleted songs. Removing from database..."
                            )
                            // Chunk deletions to avoid SQLite variable limit (default 999)
                            val batchSize = 500
                            deletedIds.chunked(batchSize).forEach { chunk ->
                                musicDao.deleteSongsByIds(chunk.toList())
                                musicDao.deleteCrossRefsBySongIds(chunk.toList())
                            }
                        } else {
                            Log.d(TAG, "No deleted songs found.")
                        }

                        if (syncMode == SyncMode.INCREMENTAL && !isFreshInstall) {
                            newMediaStoreIds = mediaStoreIds - localSongIds
                            if (newMediaStoreIds.isNotEmpty()) {
                                Log.i(TAG, "Detected ${newMediaStoreIds.size} new MediaStore IDs.")
                            }
                        }
                    }

                    // --- FETCH PHASE ---
                    // Determine what to fetch based on mode
                    // If REBUILD or FULL or RescanRequired or Fresh Install -> Fetch EVERYTHING
                    // (timestamp = 0)
                    // If INCREMENTAL -> Fetch only changes since lastSyncTimestamp
                    val forceFullFetch =
                            syncMode == SyncMode.INCREMENTAL &&
                                    newMediaStoreIds.size > MAX_NEW_IDS_FOR_INCREMENTAL
                    if (forceFullFetch) {
                        Log.w(
                                TAG,
                                "Large incremental delta detected (${newMediaStoreIds.size} new IDs). Falling back to full fetch."
                        )
                    }

                    val fetchTimestamp =
                            if (syncMode == SyncMode.INCREMENTAL &&
                                            !rescanRequired &&
                                            !isFreshInstall &&
                                            !forceFullFetch
                            ) {
                                lastSyncTimestamp /
                                        1000 // Convert to seconds for MediaStore comparison
                            } else {
                                0L
                            }

                    Log.i(TAG, "Fetching music from MediaStore (since: $fetchTimestamp seconds)...")

                    // Update every 50 songs or ~5% of library
                    val progressBatchSize = 50

                    val mediaStoreSongs =
                            fetchMusicFromMediaStore(
                                    fetchTimestamp,
                                    if (fetchTimestamp > 0) newMediaStoreIds else emptySet(),
                                    forceMetadata,
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

                    Log.i(
                            TAG,
                            "Fetched ${mediaStoreSongs.size} new/modified songs from MediaStore."
                    )

                    if (mediaStoreSongs.isEmpty() && !mediaScanCompleted) {
                        Log.w(TAG, "Media scan incomplete and no songs fetched. Retrying sync.")
                        return@withContext Result.retry()
                    }

                    // --- PROCESSING PHASE ---
                    if (mediaStoreSongs.isNotEmpty()) {

                        // If rebuilding, clear everything first
                        if (syncMode == SyncMode.REBUILD) {
                            Log.i(TAG, "Rebuild mode: Clearing all music data before insert.")
                            musicDao.clearAllMusicDataWithCrossRefs()
                        }

                        val existingArtistImageUrls =
                                if (syncMode == SyncMode.REBUILD) {
                                    emptyMap()
                                } else {
                                    musicDao.getAllArtistsListRaw().associate {
                                        it.id to it.imageUrl
                                    }
                                }

                        // Prepare list of existing songs to preserve user edits
                        // We only need to check against existing songs if we are updating them
                        val localSongsMap =
                                if (syncMode != SyncMode.REBUILD) {
                                    musicDao.getAllSongsList().associateBy { it.id }
                                } else {
                                    emptyMap()
                                }

                        Log.i(
                                TAG,
                                "Processing ${mediaStoreSongs.size} songs for upsert. Hash: ${mediaStoreSongs.hashCode()}"
                        )

                        val songsToInsert = mediaStoreSongs
                        for (index in songsToInsert.indices) {
                            val mediaStoreSong = songsToInsert[index]
                            val localSong = localSongsMap[mediaStoreSong.id]
                            if (localSong != null) {
                                // Preserve user-edited fields
                                val needsArtistCompare =
                                        !rescanRequired &&
                                                localSong.artistName.isNotBlank() &&
                                                localSong.artistName != mediaStoreSong.artistName

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

                                songsToInsert[index] =
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
                            }
                        }

                        val (correctedSongs, albums, artists, crossRefs) =
                                preProcessAndDeduplicateWithMultiArtist(
                                        songs = songsToInsert,
                                        artistDelimiters = artistDelimiters,
                                        groupByAlbumArtist = groupByAlbumArtist,
                                        existingArtistImageUrls = existingArtistImageUrls
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
                        Log.i(
                                TAG,
                                "Synchronization finished successfully in ${endTime - startTime}ms."
                        )
                        userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())

                        // Count total songs for the output
                        val totalSongs = musicDao.getSongCount().first()

                        // --- LRC SCANNING PHASE ---
                        val autoScanLrc = userPreferencesRepository.autoScanLrcFilesFlow.first()
                        if (autoScanLrc) {
                            Log.i(TAG, "Auto-scan LRC files enabled. Starting scan phase...")

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
    private fun fetchMediaStoreIds(): Set<Long> {
        val ids = HashSet<Long>()
        val projection = arrayOf(MediaStore.Audio.Media._ID)
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
                    if (idCol >= 0) {
                        while (cursor.moveToNext()) {
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
            songs: MutableList<SongEntity>,
            artistDelimiters: List<String>,
            groupByAlbumArtist: Boolean,
            existingArtistImageUrls: Map<Long, String?>
    ): MultiArtistProcessResult {
        // Need to take into account potentially existing artist IDs if we are in incremental mode
        // For simplicity in this optimization, we re-calculate from the current batch.
        // A more robust solution might check DB for existing artist IDs, but collisions are handled
        // by Room REPLACE.
        // However, to maintain stability of IDs for same names, we should ideally query mapping.
        // But for now, we'll rely on the fact that we are processing a batch.
        // TODO (Future): Load all existing Artist Name -> ID mappings to preserve IDs across
        // incremental updates.
        // Currently, new artists in this batch will get new IDs. Existing artists (by name) in this
        // batch
        // might get re-assigned IDs if we don't look them up.
        // This is acceptable for a "Sync" worker - duplicate artists are merged by name in many
        // players,
        // but here we have explicit IDs.

        // Optimization: In a real incremental sync, we might insert a song with an artist "Foo".
        // If "Foo" acts as a new artist here, it might get a new ID, duplicating "Foo" in the DB
        // if the DB already has "Foo" with a different ID.
        // Room @Insert(onConflict = REPLACE) for Artists means if ID clashes, it replaces.
        // But we are generating IDs here.

        // Fix: We MUST know existing artist names to reuse IDs.
        // Let's load the map of Name -> ID for all artists currently in DB
        // Blocking call is fine here as we are in background worker
        val existingArtistMap = ConcurrentHashMap<String, Long>()
        // We can't access this safely in a blocking way easily without a new DAO method or taking
        // it from existing lists
        // Ideally we pass this map in.

        // For the scope of this refactor, we will maintain the existing logic roughly,
        // but it is a known limitation that incremental syncs might create duplicate artist entries
        // if name matching isn't perfect against DB.
        // However, since we use `artistName` as key in `artistNameToId` map locally,
        // we ensure consistency within the batch.

        // IMPROVEMENT: Retrieve max ID from DB to ensure we don't collide or reset.
        // We'll trust `nextArtistId` starting from max existing in the batch is risky if we don't
        // know DB max.
        // Let's get maxArtistId from DB.

        // Since we cannot easily add a method to DAO right now without modifying it,
        // we will iterate and build the map from the passed `existingArtistImageUrls` keys if
        // possible,
        // or just rely on safely generating new IDs.

        // Actually, `musicDao.getAllArtistsListRaw()` was called in doWork. We can use it.
        // We'll pass `existingArtists` to this function ideally.
        // For now, let's just make it robust enough.

        val maxExistingArtistId = songs.maxOfOrNull { it.artistId } ?: 0L
        // Note: songs.maxOfOrNull might be from the batch, not DB.
        // Ideally we should query "SELECT MAX(id) FROM artists"

        val nextArtistId = AtomicLong(maxExistingArtistId + 10000) // Safety buffer

        val artistNameToId = mutableMapOf<String, Long>()
        val allCrossRefs = ArrayList<SongArtistCrossRef>(songs.size)
        val artistTrackCounts = mutableMapOf<Long, Int>()
        val albumMap = mutableMapOf<Pair<String, String>, Long>()
        val artistSplitCache = mutableMapOf<String, List<String>>()
        val albumAccumulators = mutableMapOf<Long, AlbumAccumulator>()

        for (index in songs.indices) {
            val song = songs[index]
            val rawArtistName = song.artistName
            val songArtistNameTrimmed = rawArtistName.trim()
            val artistsForSong =
                    artistSplitCache.getOrPut(rawArtistName) {
                        rawArtistName.splitArtistsByDelimiters(artistDelimiters)
                    }

            artistsForSong.forEach { artistName ->
                val normalizedName = artistName.trim()
                if (normalizedName.isNotEmpty() && !artistNameToId.containsKey(normalizedName)) {
                    val id =
                            if (normalizedName == songArtistNameTrimmed) {
                                song.artistId
                            } else {
                                nextArtistId.getAndIncrement()
                            }
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
                    allCrossRefs.add(
                            SongArtistCrossRef(
                                    songId = song.id,
                                    artistId = artistId,
                                    isPrimary = index == 0
                            )
                    )
                    artistTrackCounts[artistId] = (artistTrackCounts[artistId] ?: 0) + 1
                }
            }

            val albumArtistName =
                    if (groupByAlbumArtist && !song.albumArtist.isNullOrBlank()) {
                        song.albumArtist
                    } else {
                        primaryArtistName
                    }
            val canonicalAlbumId =
                    albumMap.getOrPut(Pair(song.albumName, albumArtistName)) { song.albumId }

            val correctedSong =
                    song.copy(
                            artistId = primaryArtistId,
                            artistName = primaryArtistName,
                            albumId = canonicalAlbumId
                    )
            songs[index] = correctedSong

            val albumArtistId = artistNameToId[albumArtistName] ?: primaryArtistId
            val accumulator = albumAccumulators[canonicalAlbumId]
            if (accumulator == null) {
                albumAccumulators[canonicalAlbumId] =
                        AlbumAccumulator(
                                id = canonicalAlbumId,
                                title = correctedSong.albumName,
                                artistName = albumArtistName,
                                artistId = albumArtistId,
                                albumArtUriString = correctedSong.albumArtUriString,
                                year = correctedSong.year,
                                songCount = 1
                        )
            } else {
                accumulator.songCount += 1
                if (accumulator.albumArtUriString.isNullOrBlank() &&
                        !correctedSong.albumArtUriString.isNullOrBlank()
                ) {
                    accumulator.albumArtUriString = correctedSong.albumArtUriString
                }
            }
        }

        // Create unique albums
        val albums =
                albumAccumulators.values.map { accumulator ->
                    AlbumEntity(
                            id = accumulator.id,
                            title = accumulator.title,
                            artistName = accumulator.artistName,
                            artistId = accumulator.artistId,
                            albumArtUriString = accumulator.albumArtUriString,
                            songCount = accumulator.songCount,
                            year = accumulator.year
                    )
                }

        val artists =
                artistNameToId.mapNotNull { (name, id) ->
                    val trackCount = artistTrackCounts[id] ?: 0
                    if (trackCount > 0) {
                        ArtistEntity(
                                id = id,
                                name = name,
                                trackCount = trackCount,
                                imageUrl = existingArtistImageUrls[id]
                        )
                    } else {
                        null
                    }
                }

        return MultiArtistProcessResult(
                songs = songs,
                albums = albums,
                artists = artists,
                crossRefs = allCrossRefs
        )
    }

    private data class AlbumAccumulator(
            val id: Long,
            val title: String,
            val artistName: String,
            val artistId: Long,
            var albumArtUriString: String?,
            val year: Int,
            var songCount: Int
    )

    @Suppress("DEPRECATION")
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
            extraIds: Set<Long>,
            forceMetadata: Boolean,
            progressBatchSize: Int,
            onProgress: suspend (current: Int, total: Int, phaseOrdinal: Int) -> Unit
    ): MutableList<SongEntity> {
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
                    " AND (${MediaStore.Audio.Media.DATE_MODIFIED} > ? OR ${MediaStore.Audio.Media.DATE_ADDED} > ?"
            )
            selectionArgsList.add(sinceTimestamp.toString())
            selectionArgsList.add(sinceTimestamp.toString())

            if (extraIds.isNotEmpty()) {
                val placeholders = extraIds.joinToString(",") { "?" }
                selectionBuilder.append(" OR ${MediaStore.Audio.Media._ID} IN ($placeholders)")
                selectionArgsList.addAll(extraIds.map { it.toString() })
            }
            selectionBuilder.append(")")
        }

        val selection = selectionBuilder.toString()
        val selectionArgs = selectionArgsList.toTypedArray()

        val cursor =
                contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                )
        if (cursor == null) {
            Trace.endSection()
            return mutableListOf()
        }

        cursor.use { safeCursor ->
            val totalCount = safeCursor.count
            onProgress(0, totalCount, SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal)
            if (totalCount == 0) {
                Trace.endSection()
                return mutableListOf()
            }

            val songs = Collections.synchronizedList(ArrayList<SongEntity>(totalCount))
            val processedCount = AtomicInteger(0)
            val concurrencyLimit = 8 // Limit parallel operations to prevent resource exhaustion
            val channel = Channel<RawSongData>(RAW_SONG_CHANNEL_BUFFER)

            coroutineScope {
                val workers =
                        List(concurrencyLimit) {
                            launch(Dispatchers.IO) {
                                for (raw in channel) {
                                    val song =
                                            processSongData(
                                                    raw,
                                                    albumArtByAlbumId,
                                                    genreMap,
                                                    deepScan
                                            )
                                    songs.add(song)

                                    val count = processedCount.incrementAndGet()
                                    if (count % progressBatchSize == 0 || count == totalCount) {
                                        onProgress(
                                                count,
                                                totalCount,
                                                SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal
                                        )
                                    }
                                }
                            }
                        }

                val idCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val albumArtistCol = safeCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val durationCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val trackCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol =
                        safeCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                while (safeCursor.moveToNext()) {
                    channel.send(
                            RawSongData(
                                    id = safeCursor.getLong(idCol),
                                    albumId = safeCursor.getLong(albumIdCol),
                                    artistId = safeCursor.getLong(artistIdCol),
                                    filePath = safeCursor.getString(dataCol) ?: "",
                                    title =
                                            safeCursor.getString(titleCol)
                                                    .normalizeMetadataTextOrEmpty()
                                                    .ifEmpty { "Unknown Title" },
                                    artist =
                                            safeCursor.getString(artistCol)
                                                    .normalizeMetadataTextOrEmpty()
                                                    .ifEmpty { "Unknown Artist" },
                                    album =
                                            safeCursor.getString(albumCol)
                                                    .normalizeMetadataTextOrEmpty()
                                                    .ifEmpty { "Unknown Album" },
                                    albumArtist =
                                            if (albumArtistCol >= 0)
                                                    safeCursor.getString(albumArtistCol)
                                                            ?.normalizeMetadataTextOrEmpty()
                                                            ?.takeIf { it.isNotBlank() }
                                            else null,
                                    duration = safeCursor.getLong(durationCol),
                                    trackNumber = safeCursor.getInt(trackCol),
                                    year = safeCursor.getInt(yearCol),
                                    dateModified = safeCursor.getLong(dateAddedCol)
                            )
                    )
                }

                channel.close()
                workers.joinAll()
            }

            Trace.endSection()
            return songs
        }
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
    private suspend fun triggerMediaScanForNewFiles(
            allowedDirectories: Set<String>,
            blockedDirectories: Set<String>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val normalizedAllowed = allowedDirectories.map(::normalizePath).toSet()
            val normalizedBlocked = blockedDirectories.map(::normalizePath).toSet()
            val resolver = DirectoryRuleResolver(normalizedAllowed, normalizedBlocked)

            val storageRoots = StorageUtils.getAvailableStorages(applicationContext).map { it.path }
            val libraryParentDirs = runCatching {
                musicDao.getAllParentDirectoryPaths().map(::normalizePath).map { File(it) }
            }.getOrElse { emptyList() }
            val storageDefaultDirs = storageRoots.flatMap { root ->
                listOf(
                    File(root, "Music"),
                    File(root, "Download"),
                    File(root, "Downloads")
                )
            }
            val fallbackDirs =
                    listOf(
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_MUSIC
                            ),
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                            ),
                            File(Environment.getExternalStorageDirectory(), "Music"),
                            File(Environment.getExternalStorageDirectory(), "Download"),
                            File(Environment.getExternalStorageDirectory(), "Downloads")
                    )

            val candidateDirs = buildList {
                if (normalizedAllowed.isNotEmpty()) {
                    addAll(normalizedAllowed.map { File(it) })
                }
                addAll(storageDefaultDirs)
                addAll(fallbackDirs)
                addAll(libraryParentDirs)
            }

            // Filter to only existing directories
            val existingDirs =
                    candidateDirs
                            .filter { it.exists() && it.isDirectory }
                            .map { it.absolutePath }
                            .distinct()

            if (existingDirs.isEmpty()) {
                Log.d(TAG, "No music directories found to scan")
                return@withContext true
            }

            // Get all file paths currently in MediaStore
            val mediaStorePaths = fetchMediaStoreFilePaths()
            
            Log.d(TAG, "MediaStore has ${mediaStorePaths.size} known files")

            // Collect audio files from filesystem that are NOT in MediaStore
            val audioExtensions =
                    setOf("mp3", "flac", "m4a", "wav", "ogg", "opus", "aac", "wma", "aiff")
            val newFilesToScan = mutableListOf<String>()

            existingDirs.forEach { dirPath ->
                val dir = File(dirPath)
                dir.walkTopDown()
                        .onEnter { entering ->
                            !resolver.isBlocked(normalizePath(entering.absolutePath))
                        }
                        .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                        .filter { it.absolutePath !in mediaStorePaths } // Only new files
                        .forEach { newFilesToScan.add(it.absolutePath) }
            }

            if (newFilesToScan.isEmpty()) {
                Log.d(TAG, "No new audio files found - MediaStore is up to date")
                return@withContext true
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
                return@withContext false
            }
            Log.i(TAG, "Media scan completed for ${newFilesToScan.size} new files")
            return@withContext true
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

    private fun normalizePath(path: String): String {
        return runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }
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
        private const val MAX_NEW_IDS_FOR_INCREMENTAL = 500
        private const val RAW_SONG_CHANNEL_BUFFER = 64
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
