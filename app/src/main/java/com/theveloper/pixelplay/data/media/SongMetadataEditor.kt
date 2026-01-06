package com.theveloper.pixelplay.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.theveloper.pixelplay.data.database.MusicDao
import kotlinx.coroutines.runBlocking
import org.gagravarr.opus.OpusFile
import org.gagravarr.opus.OpusTags
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private const val TAG = "SongMetadataEditor"


class SongMetadataEditor(private val context: Context, private val musicDao: MusicDao) {

    // File extensions that require VorbisJava (TagLib has issues with these via file descriptors)
    private val opusExtensions = setOf("opus", "ogg")

    fun editSongMetadata(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate? = null,
    ): SongMetadataEditResult {
        return try {
            val trimmedLyrics = newLyrics.trim()
            val trimmedGenre = newGenre.trim()
            val normalizedGenre = trimmedGenre.takeIf { it.isNotBlank() }
            val normalizedLyrics = trimmedLyrics.takeIf { it.isNotBlank() }

            // Get file path to determine which library to use
            val filePath = getFilePathFromMediaStore(songId)
            if (filePath == null) {
                Log.e(TAG, "Could not get file path for songId: $songId")
                return SongMetadataEditResult(success = false, updatedAlbumArtUri = null)
            }

            // Get file extension to determine which library to use
            val extension = filePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val useVorbisJava = extension in opusExtensions

            // 1. FIRST: Update the actual file with ALL metadata
            // For Opus/Ogg files, we skip file modification because:
            // - TagLib can't detect Opus via file descriptors
            // - jaudiotagger doesn't support Opus
            // - VorbisJava corrupts files (adds .pending, changes extension to .oga)
            // Instead, we only update MediaStore and local DB, which is enough for the app
            val fileUpdateSuccess = if (useVorbisJava) {
                Log.e(TAG, "METADATA_EDIT: Opus/Ogg file detected - skipping file modification to prevent corruption")
                Log.e(TAG, "METADATA_EDIT: Will update MediaStore and local DB only for: $filePath")
                true // Skip file modification, proceed to MediaStore update
            } else {
                Log.e(TAG, "METADATA_EDIT: Using TagLib for $extension file: $filePath")
                updateFileMetadataWithTagLib(
                    filePath = filePath,
                    newTitle = newTitle,
                    newArtist = newArtist,
                    newAlbum = newAlbum,
                    newGenre = trimmedGenre,
                    newLyrics = trimmedLyrics,
                    newTrackNumber = newTrackNumber,
                    coverArtUpdate = coverArtUpdate
                )
            }

            if (!fileUpdateSuccess) {
                Log.e(TAG, "Failed to update file metadata for songId: $songId")
                return SongMetadataEditResult(success = false, updatedAlbumArtUri = null)
            }

            // 2. SECOND: Update MediaStore to reflect the changes
            val mediaStoreSuccess = updateMediaStoreMetadata(
                songId = songId,
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = trimmedGenre,
                trackNumber = newTrackNumber
            )

            if (!mediaStoreSuccess) {
                Timber.w("MediaStore update failed, but file was updated for songId: $songId")
                // Continue anyway since the file was updated
            }

            // 3. Update local database and save cover art preview
            var storedCoverArtUri: String? = null
            runBlocking {
                musicDao.updateSongMetadata(
                    songId,
                    newTitle,
                    newArtist,
                    newAlbum,
                    normalizedGenre,
                    normalizedLyrics,
                    newTrackNumber
                )

                coverArtUpdate?.let { update ->
                    storedCoverArtUri = saveCoverArtPreview(songId, update)
                    storedCoverArtUri?.let { coverUri ->
                        musicDao.updateSongAlbumArt(songId, coverUri)
                    }
                }
            }

            // 4. Force media rescan with the known file path
            forceMediaRescan(filePath)

            Log.e(TAG, "METADATA_EDIT: Successfully updated metadata for songId: $songId")
            SongMetadataEditResult(success = true, updatedAlbumArtUri = storedCoverArtUri)

        } catch (e: Exception) {
            Timber.e(e, "Failed to update metadata for songId: $songId")
            SongMetadataEditResult(success = false, updatedAlbumArtUri = null)
        }
    }

    private fun updateFileMetadataWithTagLib(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate? = null
    ): Boolean {
        return try {
            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                Log.e(TAG, "TAGLIB: Audio file does not exist: $filePath")
                return false
            }
            Log.e(TAG, "TAGLIB: Opening file: $filePath")

            // Open file with read/write permissions
            ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
                // Get existing metadata or create empty map
                Log.e(TAG, "TAGLIB: Getting existing metadata...")
                val metadataFd = fd.dup()
                val existingMetadata = TagLib.getMetadata(metadataFd.detachFd())
                Log.e(TAG, "TAGLIB: Existing metadata: ${existingMetadata?.propertyMap?.keys}")
                val propertyMap = HashMap(existingMetadata?.propertyMap ?: emptyMap())

                // Update metadata fields
                propertyMap["TITLE"] = arrayOf(newTitle)
                propertyMap["ARTIST"] = arrayOf(newArtist)
                propertyMap["ALBUM"] = arrayOf(newAlbum)
                propertyMap.upsertOrRemove("GENRE", newGenre)
                propertyMap.upsertOrRemove("LYRICS", newLyrics)
                propertyMap["TRACKNUMBER"] = arrayOf(newTrackNumber.toString())
                propertyMap["ALBUMARTIST"] = arrayOf(newArtist)
                Log.e(TAG, "TAGLIB: Updated property map, saving...")

                // Save metadata
                val saveFd = fd.dup()
                val metadataSaved = TagLib.savePropertyMap(saveFd.detachFd(), propertyMap)
                Log.e(TAG, "TAGLIB: savePropertyMap result: $metadataSaved")
                if (!metadataSaved) {
                    Log.e(TAG, "TAGLIB: Failed to save metadata for file: $filePath")
                    return false
                }

                // Update cover art if provided
                coverArtUpdate?.let { update ->
                    val picture = Picture(
                        data = update.bytes,
                        description = "Front Cover",
                        pictureType = "Front Cover",
                        mimeType = update.mimeType
                    )
                    val pictureFd = fd.dup()
                    val coverSaved = TagLib.savePictures(pictureFd.detachFd(), arrayOf(picture))
                    if (!coverSaved) {
                        Log.w(TAG, "TAGLIB: Failed to save cover art, but metadata was saved")
                    } else {
                        Log.d(TAG, "TAGLIB: Successfully embedded cover art")
                    }
                }
            }

            // Force file system sync to ensure data is written to disk
            try {
                java.io.RandomAccessFile(audioFile, "rw").use { raf ->
                    raf.fd.sync()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync file, changes should still be persisted", e)
            }

            Log.e(TAG, "TAGLIB: SUCCESS - Updated file metadata: ${audioFile.path}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "TAGLIB ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun updateFileMetadataWithVorbisJava(
        filePath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int
    ): Boolean {
        val audioFile = File(filePath)
        val originalExtension = audioFile.extension
        var tempFile: File? = null
        var backupFile: File? = null
        
        return try {
            if (!audioFile.exists()) {
                Log.e(TAG, "VORBISJAVA: Audio file does not exist: $filePath")
                return false
            }

            Log.e(TAG, "VORBISJAVA: Reading Opus file: $filePath")
            
            // Read existing file
            val opusFile = OpusFile(audioFile)
            val tags = opusFile.tags ?: OpusTags()
            
            Log.e(TAG, "VORBISJAVA: Existing tags: ${tags.allComments}")
            
            // Clear existing tags and set new ones
            tags.removeComments("TITLE")
            tags.removeComments("ARTIST")
            tags.removeComments("ALBUM")
            tags.removeComments("GENRE")
            tags.removeComments("LYRICS")
            tags.removeComments("TRACKNUMBER")
            tags.removeComments("ALBUMARTIST")
            
            // Add new values (only if not blank)
            if (newTitle.isNotBlank()) tags.addComment("TITLE", newTitle)
            if (newArtist.isNotBlank()) {
                tags.addComment("ARTIST", newArtist)
                tags.addComment("ALBUMARTIST", newArtist)
            }
            if (newAlbum.isNotBlank()) tags.addComment("ALBUM", newAlbum)
            if (newGenre.isNotBlank()) tags.addComment("GENRE", newGenre)
            if (newLyrics.isNotBlank()) tags.addComment("LYRICS", newLyrics)
            if (newTrackNumber > 0) tags.addComment("TRACKNUMBER", newTrackNumber.toString())
            
            Log.e(TAG, "VORBISJAVA: Updated tags: ${tags.allComments}")
            
            // Create temp file with same extension as original
            tempFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}_temp.${originalExtension}")
            
            Log.e(TAG, "VORBISJAVA: Writing to temp file: ${tempFile.path}")
            FileOutputStream(tempFile).use { fos ->
                val newOpusFile = OpusFile(fos, opusFile.info, tags)
                
                // Copy audio packets
                var packet = opusFile.nextAudioPacket
                while (packet != null) {
                    newOpusFile.writeAudioData(packet)
                    packet = opusFile.nextAudioPacket
                }
                
                newOpusFile.close()
            }
            opusFile.close()
            
            // Verify temp file was created and has content
            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e(TAG, "VORBISJAVA: Temp file creation failed or is empty")
                return false
            }
            Log.e(TAG, "VORBISJAVA: Temp file size: ${tempFile.length()} bytes, original: ${audioFile.length()} bytes")
            
            // Create backup of original file before replacing
            backupFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}_backup.${originalExtension}")
            if (!audioFile.renameTo(backupFile)) {
                Log.e(TAG, "VORBISJAVA: Failed to create backup of original file")
                tempFile.delete()
                return false
            }
            Log.e(TAG, "VORBISJAVA: Created backup: ${backupFile.path}")
            
            // Rename temp file to original name
            if (!tempFile.renameTo(audioFile)) {
                Log.e(TAG, "VORBISJAVA: Failed to rename temp file to original")
                // Restore backup
                backupFile.renameTo(audioFile)
                return false
            }
            
            // Delete backup on success
            backupFile.delete()
            Log.e(TAG, "VORBISJAVA: SUCCESS - Updated file metadata: ${audioFile.path}")
            true

        } catch (e: Exception) {
            Log.e(TAG, "VORBISJAVA ERROR: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            
            // Cleanup on error
            tempFile?.delete()
            // Try to restore backup if it exists
            if (backupFile?.exists() == true && !audioFile.exists()) {
                backupFile.renameTo(audioFile)
            }
            false
        }
    }

    private fun updateMediaStoreMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        trackNumber: Int
    ): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.GENRE, genre)
                put(MediaStore.Audio.Media.TRACK, trackNumber)
                put(MediaStore.Audio.Media.DISPLAY_NAME, title)
                put(MediaStore.Audio.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                put(MediaStore.Audio.Media.ALBUM_ARTIST, artist)
            }

            val rowsUpdated = context.contentResolver.update(uri, values, null, null)
            val success = rowsUpdated > 0

            Timber.d("MediaStore update: $rowsUpdated row(s) affected")
            success

        } catch (e: Exception) {
            Timber.e(e, "Failed to update MediaStore for songId: $songId")
            false
        }
    }

    private fun forceMediaRescan(filePath: String) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                Log.e(TAG, "RESCAN: Starting MediaScanner for: $filePath")
                // Use MediaScannerConnection to force rescan
                val latch = java.util.concurrent.CountDownLatch(1)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    null
                ) { path, uri ->
                    Log.e(TAG, "RESCAN: Completed for: $path, new URI: $uri")
                    latch.countDown()
                }
                // Wait for scan to complete (max 5 seconds)
                val completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                if (!completed) {
                    Log.w(TAG, "RESCAN: MediaScanner timeout for: $filePath")
                }
            } else {
                Log.e(TAG, "RESCAN: File does not exist: $filePath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "RESCAN ERROR: ${e.message}")
        }
    }

    private fun getFilePathFromMediaStore(songId: Long): String? {
        Log.e(TAG, "getFilePathFromMediaStore: Looking up songId: $songId")
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.DATA),
                "${MediaStore.Audio.Media._ID} = ?",
                arrayOf(songId.toString()),
                null
            )?.use { cursor ->
                Log.e(TAG, "getFilePathFromMediaStore: Cursor count: ${cursor.count}")
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    Log.e(TAG, "getFilePathFromMediaStore: Found path: $path")
                    path
                } else {
                    Log.e(TAG, "getFilePathFromMediaStore: No file found for songId: $songId")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFilePathFromMediaStore: Error querying MediaStore: ${e.message}")
            null
        }
    }
    private fun saveCoverArtPreview(songId: Long, coverArtUpdate: CoverArtUpdate): String? {
        return try {
            val extension = imageExtensionFromMimeType(coverArtUpdate.mimeType) ?: "jpg"
            val directory = File(context.cacheDir, "").apply {
                if (!exists()) mkdirs()
            }

            // Clean up old cover art files for this song
            directory.listFiles { file ->
                file.name.startsWith("song_art_${songId}")
            }?.forEach { it.delete() }

            // Save new cover art
            val file = File(directory, "song_art_${songId}_${System.currentTimeMillis()}.$extension")
            FileOutputStream(file).use { outputStream ->
                outputStream.write(coverArtUpdate.bytes)
            }

            file.toUri().toString()
        } catch (e: Exception) {
            Timber.e(e, "Error saving cover art preview for songId: $songId")
            null
        }
    }

    private fun imageExtensionFromMimeType(mimeType: String): String? {
        return when (mimeType) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> null
        }
    }
}

private fun MutableMap<String, Array<String>>.upsertOrRemove(key: String, value: String) {
    if (value.isBlank()) {
        remove(key)
    } else {
        this[key] = arrayOf(value)
    }
}

// Data classes
data class SongMetadataEditResult(
    val success: Boolean,
    val updatedAlbumArtUri: String?,
)

data class CoverArtUpdate(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CoverArtUpdate

        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
