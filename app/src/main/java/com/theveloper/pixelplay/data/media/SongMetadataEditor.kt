package com.theveloper.pixelplay.data.media

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.database.MusicDao
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.Artwork
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.reference.PictureTypes
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

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
            tempFile = createTempAudioFileFromUri(context, uri)
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
                val artwork: Artwork = ArtworkFactory.getNew().apply {
                    binaryData = update.bytes
                    mimeType = update.mimeType
                    pictureType = PictureTypes.DEFAULT_ID
                }
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
            val extension = imageExtensionFromMimeType(coverArtUpdate.mimeType) ?: "jpg"
            val directory = File(context.cacheDir, "").apply { if (!exists()) mkdirs() }
            directory.listFiles { file ->
                file.name.startsWith("song_art_${songId}")
            }?.forEach { it.delete() }
            val file = File(directory, "song_${songId}.$extension")
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

}

data class SongMetadataEditResult(
    val success: Boolean,
    val updatedAlbumArtUri: String?,
)
