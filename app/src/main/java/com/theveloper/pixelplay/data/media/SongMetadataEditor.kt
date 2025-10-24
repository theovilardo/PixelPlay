package com.theveloper.pixelplay.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.database.MusicDao
import org.jaudiotagger.tag.images.ArtworkFactory
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream

class SongMetadataEditor(private val context: Context, private val musicDao: MusicDao) {

    fun editSongMetadata(
        contentUri: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate? = null,
    ): SongMetadataEditResult {
        Timber.d("Editing metadata for URI: $contentUri")
        val uri = contentUri.toUri()
        var tempFile: File? = null
        try {
            // 1. Crear un archivo temporal a partir del URI de contenido
            tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                Timber.tag("SongMetadataEditor").e("Failed to create temp file from URI.")
                return SongMetadataEditResult(success = false, updatedAlbumArtUri = null)
            }

            // 2. Leer el archivo de audio y modificar los metadatos
            val audioFile: AudioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tagOrCreateDefault
            tag.setField(FieldKey.TITLE, newTitle)
            tag.setField(FieldKey.ARTIST, newArtist)
            tag.setField(FieldKey.ALBUM, newAlbum)
            tag.setField(FieldKey.GENRE, newGenre)
            tag.setField(FieldKey.LYRICS, newLyrics)
            tag.setField(FieldKey.TRACK, newTrackNumber.toString())

            coverArtUpdate?.let { update ->
                Timber.d("Updating embedded cover art for URI: $contentUri")
                try {
                    tag.deleteArtworkField()
                } catch (ignore: Exception) {
                    Timber.v(ignore, "No previous artwork to delete for URI: $contentUri")
                }
                val artwork = ArtworkFactory.createArtworkFromByteArray(update.bytes, update.mimeType)
                tag.setField(artwork)
            }
            Timber.d("Committing changes to temp file.")
            audioFile.commit() // Esto guarda los cambios en el archivo temporal

            // 3. Sobrescribir el archivo original con el archivo temporal modificado
            Timber.d("Overwriting original file.")
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                    FileInputStream(tempFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } ?: run {
                Timber.tag("SongMetadataEditor").e("Failed to open FileDescriptor for writing.")
                return SongMetadataEditResult(success = false, updatedAlbumArtUri = null)
            }

            val songId = uri.lastPathSegment?.toLongOrNull()
            var storedCoverArtUri: String? = null
            if (songId != null) {
                Timber.d("Updating database for songId: $songId")
                runBlocking {
                    musicDao.updateSongMetadata(songId, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber)
                    if (coverArtUpdate != null) {
                        storedCoverArtUri = saveCoverArtPreview(songId, coverArtUpdate)
                        storedCoverArtUri?.let { newUri ->
                            musicDao.updateSongAlbumArt(songId, newUri)
                        }
                    }
                }
            }

            Timber.tag("SongMetadataEditor")
                .d("Successfully edited and saved metadata for URI: $contentUri")
            return SongMetadataEditResult(success = true, updatedAlbumArtUri = storedCoverArtUri)

        } catch (e: Exception) {
            Timber.tag("SongMetadataEditor").e(e, "Error editing metadata for URI: $contentUri")
            return SongMetadataEditResult(success = false, updatedAlbumArtUri = null)
        } finally {
            // 4. Limpiar el archivo temporal
            tempFile?.delete()
        }
    }

    private fun saveCoverArtPreview(songId: Long, coverArtUpdate: CoverArtUpdate): String? {
        return try {
            val extension = when (coverArtUpdate.mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val directory = File(context.filesDir, "cover_art").apply { if (!exists()) mkdirs() }
            val file = File(directory, "song_$songId.$extension")
            if (file.exists()) {
                file.delete()
            }
            FileOutputStream(file).use { outputStream ->
                ByteArrayInputStream(coverArtUpdate.bytes).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            file.toUri().toString()
        } catch (e: Exception) {
            Timber.tag("SongMetadataEditor").e(e, "Error saving cover art preview for songId=$songId")
            null
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val fileExtension = getFileExtension(uri)
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("temp_audio", fileExtension, context.cacheDir)
            tempFile.deleteOnExit()
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Timber.tag("SongMetadataEditor").e(e, "Error creating temp file from URI")
            null
        }
    }

    private fun getFileExtension(uri: Uri): String {
        var extension = ".mp3" // Default extension
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    val displayName = cursor.getString(displayNameIndex)
                    val dotIndex = displayName.lastIndexOf('.')
                    if (dotIndex > 0) {
                        extension = displayName.substring(dotIndex)
                    }
                }
            }
        }
        return extension
    }
}

data class SongMetadataEditResult(
    val success: Boolean,
    val updatedAlbumArtUri: String?,
)
