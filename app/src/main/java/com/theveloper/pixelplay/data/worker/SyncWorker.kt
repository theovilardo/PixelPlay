package com.theveloper.pixelplay.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.hilt.work.HiltWorker
import android.os.Trace // Import Trace
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongArtistCrossRef
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.AudioMetaUtils.getAudioMetadata
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty
import com.theveloper.pixelplay.utils.splitArtistsByDelimiters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class SyncMode {
    INCREMENTAL,
    FULL,
    REBUILD
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Trace.beginSection("SyncWorker.doWork")
        try {
            val syncModeName = inputData.getString(INPUT_SYNC_MODE) ?: SyncMode.INCREMENTAL.name
            val syncMode = SyncMode.valueOf(syncModeName)
            val forceMetadata = inputData.getBoolean(INPUT_FORCE_METADATA, false)
            
            Log.i(TAG, "Starting MediaStore synchronization (Mode: $syncMode, ForceMetadata: $forceMetadata)...")
            val startTime = System.currentTimeMillis()

            val artistDelimiters = userPreferencesRepository.artistDelimitersFlow.first()
            val groupByAlbumArtist = userPreferencesRepository.groupByAlbumArtistFlow.first()
            val rescanRequired = userPreferencesRepository.artistSettingsRescanRequiredFlow.first()

            Log.d(TAG, "Artist parsing delimiters: $artistDelimiters, groupByAlbumArtist: $groupByAlbumArtist, rescanRequired: $rescanRequired")

            // Fetch existing artist image URLs to preserve them
            val existingArtistImageUrls = musicDao.getAllArtistsListRaw().associate { it.id to it.imageUrl }

            // If rebuilding, clear everything first
            if (syncMode == SyncMode.REBUILD) {
                Log.i(TAG, "Rebuild mode: Clearing all music data before rescan.")
                musicDao.clearAllMusicDataWithCrossRefs()
            }

            // Use granular progress updates (1 by 1) for Full and Rebuild modes as requested
            val progressBatchSize = if (syncMode == SyncMode.FULL || syncMode == SyncMode.REBUILD) 1 else 50
            
            val mediaStoreSongs = fetchAllMusicData(progressBatchSize) { current, total ->
                setProgress(workDataOf(
                    PROGRESS_CURRENT to current,
                    PROGRESS_TOTAL to total
                ))
            }
            Log.i(TAG, "Fetched ${mediaStoreSongs.size} songs from MediaStore.")

            if (mediaStoreSongs.isNotEmpty()) {
                // Fetch existing local songs to preserve their editable metadata
                val localSongsMap = musicDao.getAllSongsList().associateBy { it.id }
                val isFreshInstall = localSongsMap.isEmpty()
                
                // For incremental sync, identify which songs actually need processing
                val songsToProcess = if (syncMode == SyncMode.INCREMENTAL && !rescanRequired && !isFreshInstall) {
                    mediaStoreSongs.filter { mediaStoreSong ->
                        val localSong = localSongsMap[mediaStoreSong.id]
                        // Process if song is new OR if it was modified since last sync
                        localSong == null || mediaStoreSong.dateAdded > localSong.dateAdded
                    }
                } else {
                    mediaStoreSongs
                }
                
                Log.i(TAG, "Processing ${songsToProcess.size} songs (${mediaStoreSongs.size - songsToProcess.size} skipped). Hash: ${songsToProcess.hashCode()}")

                // Prepare the final list of songs for insertion
                val songsToInsert = songsToProcess.map { mediaStoreSong ->
                    val localSong = localSongsMap[mediaStoreSong.id]
                    if (localSong != null) {
                        // This song exists locally - preserve user-edited fields if they differ from MediaStore
                        // We check if local values are different from what MediaStore would provide,
                        // which suggests user editing. We preserve dateAdded, lyrics, and all editable metadata.
                        val needsArtistCompare = !rescanRequired &&
                            localSong.artistName.isNotBlank() &&
                            localSong.artistName != mediaStoreSong.artistName
                        val shouldPreserveArtistName = if (needsArtistCompare) {
                            val mediaStoreArtists = mediaStoreSong.artistName
                                .splitArtistsByDelimiters(artistDelimiters)
                            val mediaStorePrimaryArtist = mediaStoreArtists.firstOrNull()?.trim()
                            val mediaStoreHasMultipleArtists = mediaStoreArtists.size > 1
                            !(mediaStoreHasMultipleArtists &&
                                mediaStorePrimaryArtist != null &&
                                localSong.artistName.trim() == mediaStorePrimaryArtist)
                        } else {
                            false
                        }
                        mediaStoreSong.copy(
                            dateAdded = localSong.dateAdded,
                            lyrics = localSong.lyrics,
                            // Preserve user-edited metadata if local is different from MediaStore default
                            title = if (localSong.title != mediaStoreSong.title && localSong.title.isNotBlank()) localSong.title else mediaStoreSong.title,
                            artistName = if (shouldPreserveArtistName) localSong.artistName else mediaStoreSong.artistName,
                            albumName = if (localSong.albumName != mediaStoreSong.albumName && localSong.albumName.isNotBlank()) localSong.albumName else mediaStoreSong.albumName,
                            genre = localSong.genre ?: mediaStoreSong.genre,
                            trackNumber = if (localSong.trackNumber != 0 && localSong.trackNumber != mediaStoreSong.trackNumber) localSong.trackNumber else mediaStoreSong.trackNumber,
                            albumArtUriString = localSong.albumArtUriString ?: mediaStoreSong.albumArtUriString
                        )
                    } else {
                        // This is a new song. Keep the MediaStore provided data.
                        mediaStoreSong
                    }
                }

                val (correctedSongs, albums, artists, crossRefs) = preProcessAndDeduplicateWithMultiArtist(
                    songs = songsToInsert,
                    artistDelimiters = artistDelimiters,
                    groupByAlbumArtist = groupByAlbumArtist,
                    existingArtistImageUrls = existingArtistImageUrls
                )

                if (syncMode == SyncMode.INCREMENTAL && !rescanRequired && !isFreshInstall) {
                    // Identify deleted songs
                    val mediaStoreIds = mediaStoreSongs.map { it.id }.toSet()
                    val deletedSongIds = localSongsMap.keys.filter { it !in mediaStoreIds }
                    
                    musicDao.incrementalSyncMusicData(
                        songs = correctedSongs,
                        albums = albums,
                        artists = artists,
                        crossRefs = crossRefs,
                        deletedSongIds = deletedSongIds
                    )
                    Log.i(TAG, "Incremental sync completed. ${correctedSongs.size} upserted, ${deletedSongIds.size} deleted.")
                } else {
                    // Perform the "clear and insert" operation with cross-references
                    if (!isFreshInstall && syncMode != SyncMode.INCREMENTAL) {
                         // Only clear if explicitly requested (FULL/REBUILD) and not a fresh start.
                         // If it's a fresh start (INCREMENTAL turned FULL), no need to clear.
                         // Actually, if it's FULL or REBUILD, we SHOULD clear to be safe, unless it IS fresh.
                         // But if it was INCREMENTAL and we promoted it because it's fresh, we definitely don't need to clear.
                         musicDao.clearAllMusicDataWithCrossRefs()
                    }
                    musicDao.insertMusicDataWithCrossRefs(correctedSongs, albums, artists, crossRefs)
                    Log.i(TAG, "Full sync completed (Fresh: $isFreshInstall). ${correctedSongs.size} songs, ${artists.size} artists processed.")
                }
                
                // Clear the rescan required flag
                userPreferencesRepository.clearArtistSettingsRescanRequired()
                
                val endTime = System.currentTimeMillis()
                Log.i(TAG, "MediaStore synchronization finished successfully in ${endTime - startTime}ms.")
                Result.success(workDataOf(OUTPUT_TOTAL_SONGS to correctedSongs.size))
            } else {
                // MediaStore is empty, so clear the local database
                musicDao.clearAllMusicDataWithCrossRefs()
                Log.w(TAG, "MediaStore fetch resulted in empty list. Local music data cleared.")
                
                val endTime = System.currentTimeMillis()
                Log.i(TAG, "MediaStore synchronization finished successfully in ${endTime - startTime}ms.")
                Result.success(workDataOf(OUTPUT_TOTAL_SONGS to 0))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during MediaStore synchronization", e)
            Result.failure()
        } finally {
            Trace.endSection() // End SyncWorker.doWork
        }
    }

    /**
     * Data class to hold the result of multi-artist preprocessing.
     */
    private data class MultiArtistProcessResult(
        val songs: List<SongEntity>,
        val albums: List<AlbumEntity>,
        val artists: List<ArtistEntity>,
        val crossRefs: List<SongArtistCrossRef>
    )

    /**
     * Process songs with multi-artist support.
     * Splits artist names by delimiters and creates proper cross-references.
     */
    private fun preProcessAndDeduplicateWithMultiArtist(
        songs: List<SongEntity>,
        artistDelimiters: List<String>,
        groupByAlbumArtist: Boolean,
        existingArtistImageUrls: Map<Long, String?>
    ): MultiArtistProcessResult {
        val maxExistingArtistId = songs.maxOfOrNull { it.artistId } ?: 0L
        val nextArtistId = AtomicLong(maxExistingArtistId + 1)

        val artistNameToId = mutableMapOf<String, Long>()
        val allCrossRefs = mutableListOf<SongArtistCrossRef>()
        val artistTrackCounts = mutableMapOf<Long, Int>()
        val albumMap = mutableMapOf<Pair<String, String>, Long>()
        val artistSplitCache = mutableMapOf<String, List<String>>()
        val correctedSongs = ArrayList<SongEntity>(songs.size)

        songs.forEach { song ->
            val rawArtistName = song.artistName
            val songArtistNameTrimmed = rawArtistName.trim()
            val artistsForSong = artistSplitCache.getOrPut(rawArtistName) {
                rawArtistName.splitArtistsByDelimiters(artistDelimiters)
            }

            artistsForSong.forEach { artistName ->
                val normalizedName = artistName.trim()
                if (normalizedName.isNotEmpty() && !artistNameToId.containsKey(normalizedName)) {
                    val id = if (normalizedName == songArtistNameTrimmed) {
                        song.artistId
                    } else {
                        nextArtistId.getAndIncrement()
                    }
                    artistNameToId[normalizedName] = id
                }
            }

            val primaryArtistName = artistsForSong.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
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

            val albumArtistName = if (groupByAlbumArtist && !song.albumArtist.isNullOrBlank()) {
                song.albumArtist
            } else {
                primaryArtistName
            }
            val canonicalAlbumId = albumMap.getOrPut(Pair(song.albumName, albumArtistName)) { song.albumId }

            correctedSongs.add(
                song.copy(
                    artistId = primaryArtistId,
                    artistName = primaryArtistName,
                    albumId = canonicalAlbumId
                )
            )
        }

        // Create unique albums
        val albums = correctedSongs.groupBy { it.albumId }.map { (albumId, songsInAlbum) ->
            val firstSong = songsInAlbum.first()
            // correctedSongs already have the primary artist as artistName, no need to split again
            val albumArtistName = if (groupByAlbumArtist && !firstSong.albumArtist.isNullOrBlank()) {
                firstSong.albumArtist
            } else {
                firstSong.artistName
            }
            val albumArtistId = artistNameToId[albumArtistName] ?: firstSong.artistId

            AlbumEntity(
                id = albumId,
                title = firstSong.albumName,
                artistName = albumArtistName,
                artistId = albumArtistId,
                albumArtUriString = firstSong.albumArtUriString,
                songCount = songsInAlbum.size,
                year = firstSong.year
            )
        }

        val artists = artistNameToId.mapNotNull { (name, id) ->
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
            songs = correctedSongs,
            albums = albums,
            artists = artists,
            crossRefs = allCrossRefs
        )
    }

    private fun fetchAlbumArtUrisByAlbumId(): Map<Long, String> {
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM_ART
        )

        return buildMap {
            contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val artCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)

                while (cursor.moveToNext()) {
                    val albumId = cursor.getLong(idCol)
                    val storedArtPath = cursor.getString(artCol)
                    val uriString = when {
                        !storedArtPath.isNullOrBlank() -> File(storedArtPath).toURI().toString()
                        albumId > 0 -> ContentUris.withAppendedId(
                            "content://media/external/audio/albumart".toUri(),
                            albumId
                        ).toString()
                        else -> null
                    }

                    if (uriString != null) put(albumId, uriString)
                }
            }
        }
    }

    private suspend fun fetchAllMusicData(progressBatchSize: Int, onProgress: suspend (current: Int, total: Int) -> Unit): List<SongEntity> {
        Trace.beginSection("SyncWorker.fetchAllMusicData")
        val songs = mutableListOf<SongEntity>()
        // Removed genre mapping from initial sync for performance.
        // Genre will be "Unknown Genre" or from static genres for now.

        val deepScan = inputData.getBoolean(INPUT_FORCE_METADATA, false)
        val albumArtByAlbumId = if (!deepScan) fetchAlbumArtUrisByAlbumId() else emptyMap()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST, // Added for multi-artist support
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        val selection = "((${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?) " +
                "OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' " +
                "OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac' " +
                "OR ${MediaStore.Audio.Media.DATA} LIKE '%.wav' " +
                "OR ${MediaStore.Audio.Media.DATA} LIKE '%.opus' " +
                "OR ${MediaStore.Audio.Media.DATA} LIKE '%.ogg')"
        val selectionArgs = arrayOf("10000")
        val sortOrder = null // Avoid extra sorting work; we'll sort downstream if needed

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val totalCount = cursor.count
            var processedCount = 0
            var lastReportedCount = 0
            
            // Report initial progress (0 of total)
            onProgress(0, totalCount)
            
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)


            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val songArtistId = cursor.getLong(artistIdCol)
                val filePath = cursor.getString(dataCol) ?: ""
                val parentDir = java.io.File(filePath).parent ?: ""

                val contentUriString = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                ).toString()

                var albumArtUriString = albumArtByAlbumId[albumId]
                if (deepScan) {
                    albumArtUriString = AlbumArtUtils.getAlbumArtUri(applicationContext, musicDao, filePath, albumId, id, true)
                        ?: albumArtUriString
                }
                val audioMetadata = if (deepScan) getAudioMetadata(musicDao, id, filePath, true) else null

                var title = cursor.getString(titleCol).normalizeMetadataTextOrEmpty().ifEmpty { "Unknown Title" }
                var artist = cursor.getString(artistCol).normalizeMetadataTextOrEmpty().ifEmpty { "Unknown Artist" }
                var album = cursor.getString(albumCol).normalizeMetadataTextOrEmpty().ifEmpty { "Unknown Album" }
                var albumArtist = cursor.getString(albumArtistCol)?.normalizeMetadataTextOrEmpty()?.takeIf { it.isNotBlank() }
                var trackNumber = cursor.getInt(trackCol)
                var year = cursor.getInt(yearCol)
                var genre: String? = null


                val shouldAugmentMetadata = deepScan || filePath.endsWith(".wav", true) ||
                        filePath.endsWith(".opus", true) || filePath.endsWith(".ogg", true) ||
                        filePath.endsWith(".oga", true) || filePath.endsWith(".aiff", true)
                if (shouldAugmentMetadata) {
                    val file = java.io.File(filePath)
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
                                    val uri = AlbumArtUtils.saveAlbumArtToCache(applicationContext, art.bytes, id)
                                    albumArtUriString = uri.toString()
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to read metadata via TagLib for $filePath", e)
                        }
                    }
                }

                songs.add(
                    SongEntity(
                        id = id,
                        title = title,
                        artistName = artist,
                        artistId = songArtistId,
                        albumArtist = albumArtist,
                        albumName = album,
                        albumId = albumId,
                        contentUriString = contentUriString,
                        albumArtUriString = albumArtUriString,
                        duration = cursor.getLong(durationCol),
                        genre = genre,
                        filePath = filePath,
                        parentDirectoryPath = parentDir,
                        trackNumber = trackNumber,
                        year = year,
                        dateAdded = cursor.getLong(dateAddedCol).let { seconds ->
                            if (seconds > 0) TimeUnit.SECONDS.toMillis(seconds) else System.currentTimeMillis()
                        },
                        mimeType = audioMetadata?.mimeType,
                        sampleRate = audioMetadata?.sampleRate,
                        bitrate = audioMetadata?.bitrate
                    )
                )
                
                // Report progress in batches to avoid excessive updates
                processedCount++
                if (processedCount - lastReportedCount >= progressBatchSize || processedCount == totalCount) {
                    lastReportedCount = processedCount
                    onProgress(processedCount, totalCount)
                }
            }
        }
        Trace.endSection() // End SyncWorker.fetchAllMusicData
        return songs
    }


    companion object {
        const val WORK_NAME = "com.theveloper.pixelplay.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"
        const val INPUT_FORCE_METADATA = "input_force_metadata"
        const val INPUT_SYNC_MODE = "input_sync_mode"

        
        // Progress reporting constants
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val OUTPUT_TOTAL_SONGS = "output_total_songs"

        fun startUpSyncWork(deepScan: Boolean = false) = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(
                INPUT_FORCE_METADATA to deepScan,
                INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name
            ))
            .build()

        fun incrementalSyncWork() = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name))
            .build()

        fun fullSyncWork(deepScan: Boolean = false) = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(
                INPUT_SYNC_MODE to SyncMode.FULL.name,
                INPUT_FORCE_METADATA to deepScan
            ))
            .build()

        fun rebuildDatabaseWork() = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.REBUILD.name))
            .build()
    }
}
