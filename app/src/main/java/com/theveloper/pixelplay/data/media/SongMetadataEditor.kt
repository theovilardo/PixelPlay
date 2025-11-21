package com.theveloper.pixelplay.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.database.MusicDao
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.audio.exceptions.CannotWriteException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class SongMetadataEditor(private val context: Context, private val musicDao: MusicDao) {

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
            // 1. FIRST: Update the actual file with ALL metadata using JAudioTagger
            val fileUpdateSuccess = updateFileMetadataWithJAudioTagger(
                songId = songId,
                newTitle = newTitle,
                newArtist = newArtist,
                newAlbum = newAlbum,
                newGenre = newGenre,
                newLyrics = newLyrics,
                newTrackNumber = newTrackNumber,
                coverArtUpdate = coverArtUpdate
            )

            if (!fileUpdateSuccess) {
                Timber.e("Failed to update file metadata for songId: $songId")
                return SongMetadataEditResult(success = false, updatedAlbumArtUri = null)
            }

            // 2. SECOND: Update MediaStore to reflect the changes
            val mediaStoreSuccess = updateMediaStoreMetadata(
                songId = songId,
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = newGenre,
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
                    songId, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber
                )

                coverArtUpdate?.let { update ->
                    storedCoverArtUri = saveCoverArtPreview(songId, update)
                    storedCoverArtUri?.let { coverUri ->
                        musicDao.updateSongAlbumArt(songId, coverUri)
                    }
                }
            }

            // 4. Force media rescan
            forceMediaRescan(songId)

            Timber.d("Successfully updated metadata for songId: $songId")
            SongMetadataEditResult(success = true, updatedAlbumArtUri = storedCoverArtUri)

        } catch (e: Exception) {
            Timber.e(e, "Failed to update metadata for songId: $songId")
            SongMetadataEditResult(success = false, updatedAlbumArtUri = null)
        }
    }

    private fun updateFileMetadataWithJAudioTagger(
        songId: Long,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate? = null
    ): Boolean {
        return try {
            val filePath = getFilePathFromMediaStore(songId)
            if (filePath == null) {
                Timber.e("Could not get file path for songId: $songId")
                return false
            }

            val audioFile = File(filePath)
            if (!audioFile.exists()) {
                Timber.e("Audio file does not exist: $filePath")
                return false
            }

            // Read and update the audio file
            val audioFileIO: AudioFile = AudioFileIO.read(audioFile)
            val tag: Tag = audioFileIO.tagOrCreateAndSetDefault

            // Update ALL metadata fields
            tag.setField(FieldKey.TITLE, newTitle)
            tag.setField(FieldKey.ARTIST, newArtist)
            tag.setField(FieldKey.ALBUM, newAlbum)
            tag.setField(FieldKey.GENRE, newGenre)
            tag.setField(FieldKey.LYRICS, newLyrics)
            tag.setField(FieldKey.TRACK, newTrackNumber.toString())

            // Also set album artist for better compatibility
            tag.setField(FieldKey.ALBUM_ARTIST, newArtist)

            // Update cover art if provided
            coverArtUpdate?.let { update ->
                updateEmbeddedCoverArt(tag, update)
            }

            // Save changes
            audioFileIO.commit()
            Timber.d("Successfully updated file metadata: ${audioFile.path}")
            true

        } catch (e: CannotReadException) {
            Timber.e(e, "Cannot read audio file for songId: $songId")
            false
        } catch (e: CannotWriteException) {
            Timber.e(e, "Cannot write to audio file for songId: $songId")
            false
        } catch (e: Exception) {
            Timber.e(e, "Error updating file metadata for songId: $songId")
            false
        }
    }

    private fun updateEmbeddedCoverArt(tag: Tag, coverArtUpdate: CoverArtUpdate) {
        try {
            // Remove existing artwork
            try {
                tag.deleteArtworkField()
                Timber.d("Removed existing artwork")
            } catch (e: Exception) {
                Timber.v("No existing artwork to remove")
            }

            // Create and set new artwork
            val artwork: Artwork = ArtworkFactory.getNew().apply {
                binaryData = coverArtUpdate.bytes
                mimeType = coverArtUpdate.mimeType
                description = "Cover Art"
                pictureType = PictureTypes.DEFAULT_ID
            }

            tag.setField(artwork)
            Timber.d("Successfully embedded cover art")

        } catch (e: Exception) {
            Timber.e(e, "Failed to embed cover art")
            throw e
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

    private fun forceMediaRescan(songId: Long) {
        try {
            val filePath = getFilePathFromMediaStore(songId)
            if (filePath != null && File(filePath).exists()) {
                // Use MediaScannerConnection to force rescan
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(filePath),
                    null
                ) { path, uri ->
                    Timber.d("Media scan completed for: $path")
                }
                Timber.d("Triggered media scan for songId: $songId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to force media rescan for songId: $songId")
        }
    }

    private fun getFilePathFromMediaStore(songId: Long): String? {
        return context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            "${MediaStore.Audio.Media._ID} = ?",
            arrayOf(songId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
            } else {
                Timber.e("No file found for songId: $songId")
                null
            }
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