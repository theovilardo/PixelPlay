package com.theveloper.pixelplay.data.media

import android.content.Context
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import timber.log.Timber

internal data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val genre: String?,
    val durationMs: Long?,
    val trackNumber: Int?,
    val year: Int?,
    val artwork: AudioMetadataArtwork?
)

internal data class AudioMetadataArtwork(
    val bytes: ByteArray,
    val mimeType: String?
)

object AudioMetadataReader {

    fun read(context: Context, uri: Uri): AudioMetadata? {
        val tempFile = createTempAudioFileFromUri(context, uri)
        if (tempFile == null) {
            Timber.tag("AudioMetadataReader").w("Unable to create temp file for uri: $uri")
            return null
        }

        return try {
            val audioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tagOrCreateDefault

            val title = tag.getFirst(FieldKey.TITLE).takeIf { it.isNotBlank() }
            val artist = tag.getFirst(FieldKey.ARTIST).takeIf { it.isNotBlank() }
            val album = tag.getFirst(FieldKey.ALBUM).takeIf { it.isNotBlank() }
            val genre = tag.getFirst(FieldKey.GENRE).takeIf { it.isNotBlank() }
            val trackString = tag.getFirst(FieldKey.TRACK).takeIf { it.isNotBlank() }
            val trackNumber = trackString
                ?.substringBefore('/')
                ?.toIntOrNull()
            val year = tag.getFirst(FieldKey.YEAR).takeIf { it.isNotBlank() }?.toIntOrNull()
            val durationSeconds = runCatching { audioFile.audioHeader.trackLength }.getOrNull()
            val durationMs = durationSeconds?.takeIf { it > 0 }?.let { it * 1000L }

            val artworkTag = tag.firstArtwork
            val artworkData = artworkTag?.binaryData?.takeIf { it.isNotEmpty() }
            val artwork = artworkData
                ?.takeIf { isValidImageData(it) }
                ?.let { data ->
                    val mimeType = artworkTag.mimeType?.takeIf { it.isNotBlank() }
                        ?: guessImageMimeType(data)
                    AudioMetadataArtwork(bytes = data, mimeType = mimeType)
                }

            AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                durationMs = durationMs,
                trackNumber = trackNumber,
                year = year,
                artwork = artwork
            )
        } catch (error: Exception) {
            Timber.tag("AudioMetadataReader").e(error, "Unable to read metadata from uri: $uri")
            null
        } finally {
            tempFile?.delete()
        }
    }
}
