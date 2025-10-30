package com.theveloper.pixelplay.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection
import java.util.Locale
import timber.log.Timber

internal fun createTempAudioFileFromUri(context: Context, uri: Uri): File? {
    return try {
        val fileExtension = resolveAudioFileExtension(context, uri)
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("pixelplay_audio_", fileExtension, context.cacheDir)
        tempFile.deleteOnExit()
        val outputStream = FileOutputStream(tempFile)
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        tempFile
    } catch (error: Exception) {
        Timber.tag("AudioMetadataUtils").e(error, "Error creating temp file from URI: $uri")
        null
    }
}

internal fun resolveAudioFileExtension(context: Context, uri: Uri): String {
    fun normalizeExtension(extension: String): String {
        val normalized = extension.trim().lowercase(Locale.ROOT)
        return if (normalized.startsWith('.')) normalized else ".${normalized}"
    }

    val extensionFromDisplayName = runCatching {
        var extension: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    val displayName = cursor.getString(displayNameIndex)
                    val dotIndex = displayName.lastIndexOf('.')
                    if (dotIndex > 0 && dotIndex < displayName.lastIndex) {
                        extension = displayName.substring(dotIndex + 1)
                    }
                }
            }
        }
        extension
    }.getOrNull()

    if (!extensionFromDisplayName.isNullOrBlank()) {
        return normalizeExtension(extensionFromDisplayName)
    }

    uri.lastPathSegment
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() }
        ?.let { return normalizeExtension(it) }

    val mimeType = runCatching { context.contentResolver.getType(uri) }
        .getOrNull()
        ?.lowercase(Locale.ROOT)

    val extensionFromMimeType = mimeType
        ?.let { AUDIO_MIME_OVERRIDES[it] ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        ?.takeIf { it.isNotBlank() }

    if (!extensionFromMimeType.isNullOrBlank()) {
        return normalizeExtension(extensionFromMimeType)
    }

    val streamMimeType = runCatching {
        context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            URLConnection.guessContentTypeFromStream(input)
        }
    }.getOrNull()?.lowercase(Locale.ROOT)

    val extensionFromStream = streamMimeType
        ?.let { AUDIO_MIME_OVERRIDES[it] ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        ?.takeIf { it.isNotBlank() }

    if (!extensionFromStream.isNullOrBlank()) {
        return normalizeExtension(extensionFromStream)
    }

    return ".mp3"
}

private val AUDIO_MIME_OVERRIDES = mapOf(
    "audio/mp4" to "m4a",
    "audio/x-m4a" to "m4a",
    "audio/aac" to "aac",
    "audio/x-aac" to "aac",
    "audio/flac" to "flac",
    "audio/x-flac" to "flac",
    "audio/ogg" to "ogg",
    "audio/vorbis" to "ogg",
    "audio/opus" to "opus",
    "audio/wav" to "wav",
    "audio/x-wav" to "wav",
    "audio/vnd.wave" to "wav",
    "audio/mpeg" to "mp3",
    "audio/3gpp" to "3gp",
    "audio/3gpp2" to "3g2",
    "audio/amr" to "amr",
    "audio/x-ms-wma" to "wma"
)

internal fun isValidImageData(data: ByteArray): Boolean {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    return options.outWidth > 0 && options.outHeight > 0
}

internal fun imageExtensionFromMimeType(mimeType: String?): String? {
    return when (mimeType?.lowercase(Locale.ROOT)) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> null
    }
}

internal fun guessImageMimeType(data: ByteArray): String? {
    return runCatching {
        ByteArrayInputStream(data).use { input ->
            URLConnection.guessContentTypeFromStream(input)
        }
    }.getOrNull()
}
