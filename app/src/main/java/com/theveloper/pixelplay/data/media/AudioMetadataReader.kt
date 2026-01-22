package com.theveloper.pixelplay.data.media

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.kyant.taglib.TagLib
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

data class AudioMetadata(
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val genre: String?,
    val durationMs: Long?,
    val trackNumber: Int?,
    val year: Int?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val artwork: AudioMetadataArtwork?
)

data class AudioMetadataArtwork(
    val bytes: ByteArray,
    val mimeType: String?
)

object AudioMetadataReader {

    fun read(context: Context, uri: Uri): AudioMetadata? {
        val tempFile = createTempAudioFileFromUri(context, uri) ?: run {
            Timber.tag("AudioMetadataReader").w("Unable to create temp file for uri: $uri")
            return null
        }

        return try {
            read(tempFile)
        } finally {
            try {
                tempFile.delete()
            } catch (e: Exception) {
                Timber.tag("AudioMetadataReader").w(e, "Failed to delete temp file")
            }
        }
    }

    fun read(file: File): AudioMetadata? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                // Get audio properties for duration
                val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd())
                val durationMs = audioProperties?.length?.takeIf { it > 0 }?.let { it * 1000L }
                val bitrate = audioProperties?.bitrate?.takeIf { it > 0 }
                val sampleRate = audioProperties?.sampleRate?.takeIf { it > 0 }

                // Get metadata
                val metadata = TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)
                val propertyMap = metadata?.propertyMap ?: emptyMap()

                val title = propertyMap["TITLE"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val artist = propertyMap["ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val albumArtist = propertyMap["ALBUMARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["ALBUM ARTIST"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["BAND"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val album = propertyMap["ALBUM"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val genre = propertyMap["GENRE"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val trackString = propertyMap["TRACKNUMBER"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                    ?: propertyMap["TRACK"]?.firstOrNull()?.takeIf { it.isNotBlank() }
                val trackNumber = trackString?.substringBefore('/')?.toIntOrNull()
                val year = propertyMap["DATE"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.take(4)?.toIntOrNull()
                    ?: propertyMap["YEAR"]?.firstOrNull()?.takeIf { it.isNotBlank() }?.toIntOrNull()

                // Get artwork
                val pictures = TagLib.getPictures(fd.detachFd())
                val artwork = pictures.firstOrNull()?.let { picture ->
                    picture.data.takeIf { it.isNotEmpty() && isValidImageData(it) }?.let { data ->
                        AudioMetadataArtwork(
                            bytes = data,
                            mimeType = picture.mimeType.takeIf { it.isNotBlank() } ?: guessImageMimeType(data)
                        )
                    }
                }

                AudioMetadata(
                    title = title,
                    artist = artist,
                    albumArtist = albumArtist,
                    album = album,
                    genre = genre,
                    durationMs = durationMs,
                    trackNumber = trackNumber,
                    year = year,
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    artwork = artwork
                )
            }
        } catch (error: Exception) {
            Timber.tag("AudioMetadataReader").e(error, "Unable to read metadata from file: ${file.absolutePath}")
            null
        }
    }
}
