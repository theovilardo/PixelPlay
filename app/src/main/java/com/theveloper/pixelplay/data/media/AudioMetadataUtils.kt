package com.theveloper.pixelplay.data.media

import android.content.Context
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
    val extensionFromDisplayName = runCatching {
        var extension: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    val displayName = cursor.getString(displayNameIndex)
                    val dotIndex = displayName.lastIndexOf('.')
                    if (dotIndex > 0 && dotIndex < displayName.lastIndex) {
                        extension = displayName.substring(dotIndex)
                    }
                }
            }
        }
        extension
    }.getOrNull()

    if (!extensionFromDisplayName.isNullOrBlank()) {
        return extensionFromDisplayName
    }

    val extensionFromMimeType = runCatching {
        context.contentResolver.getType(uri)?.let { mimeType ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        }
    }.getOrNull()

    if (!extensionFromMimeType.isNullOrBlank()) {
        return ".${extensionFromMimeType}"
    }

    return ".mp3"
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
