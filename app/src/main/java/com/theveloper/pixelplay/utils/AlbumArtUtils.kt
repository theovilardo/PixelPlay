package com.theveloper.pixelplay.utils

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

object AlbumArtUtils {

    /**
     * Main function to get album art - tries multiple methods
     */
    fun getAlbumArtUri(appContext:Context, path: String, albumId: Long): Uri? {
        // Method 1: Try embedded art from file
        getEmbeddedAlbumArtUri(appContext, path)?.let { return it }

        // Method 2: Try external album art files in directory
        getExternalAlbumArtUri(path)?.let { return it }

        // Method 3: Try MediaStore (even though it often fails)
        getMediaStoreAlbumArtUri(albumId)?.let { return it }

        return null
    }

    /**
     * Enhanced embedded art extraction with better error handling
     */
    fun getEmbeddedAlbumArtUri(appContext: Context, filePath: String): Uri? {
        if (!File(filePath).exists() || !File(filePath).canRead()) {
            return null
        }

        val retriever = MediaMetadataRetriever()
        return try {
            // Try different setDataSource methods
            try {
                retriever.setDataSource(filePath)
            } catch (e: IllegalArgumentException) {
                // Some files need FileDescriptor approach
                try {
                    FileInputStream(File(filePath)).use { fis ->
                        retriever.setDataSource(fis.fd)
                    }
                } catch (e2: Exception) {
                    return null
                }
            }

            val bytes = retriever.embeddedPicture
            bytes?.let { saveAlbumArtToCache(appContext,it) }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Look for external album art files in the same directory
     */
    fun getExternalAlbumArtUri(filePath: String): Uri? {
        return try {
            val audioFile = File(filePath)
            val parentDir = audioFile.parent ?: return null

            // Extended list of common album art file names
            val commonNames = listOf(
                "cover.jpg", "cover.png", "cover.jpeg",
                "folder.jpg", "folder.png", "folder.jpeg",
                "album.jpg", "album.png", "album.jpeg",
                "albumart.jpg", "albumart.png", "albumart.jpeg",
                "artwork.jpg", "artwork.png", "artwork.jpeg",
                "front.jpg", "front.png", "front.jpeg",
                ".folder.jpg", ".albumart.jpg",
                "thumb.jpg", "thumbnail.jpg",
                "scan.jpg", "scanned.jpg"
            )

            // Look for files in the directory
            val dir = File(parentDir)
            if (dir.exists() && dir.isDirectory) {
                // First, check exact common names
                for (name in commonNames) {
                    val artFile = File(parentDir, name)
                    if (artFile.exists() && artFile.isFile && artFile.length() > 1024) { // At least 1KB
                        return Uri.fromFile(artFile)
                    }
                }

                // Then, check any image files that might be album art
                val imageFiles = dir.listFiles { file ->
                    file.isFile && (
                            file.name.contains("cover", ignoreCase = true) ||
                                    file.name.contains("album", ignoreCase = true) ||
                                    file.name.contains("folder", ignoreCase = true) ||
                                    file.name.contains("art", ignoreCase = true) ||
                                    file.name.contains("front", ignoreCase = true)
                            ) && (
                            file.extension.lowercase() in setOf("jpg", "jpeg", "png", "bmp", "webp")
                            )
                }

                imageFiles?.firstOrNull()?.let { Uri.fromFile(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Try MediaStore as last resort
     */
    fun getMediaStoreAlbumArtUri(albumId: Long): Uri? {
        return try {
            // If song has albumId, try the standard MediaStore URI
            albumId.takeIf { it != -1L }?.let { albumId ->
                ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save embedded art to cache with unique naming
     */
    private fun saveAlbumArtToCache(appContext: Context, bytes: ByteArray): Uri {
        val timestamp = System.currentTimeMillis()
        val file = File(appContext.cacheDir, "album_art_${timestamp}.jpg")

        file.outputStream().use { outputStream ->
            outputStream.write(bytes)
        }

        return try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            // Fallback to file URI if FileProvider fails
            Uri.fromFile(file)
        }
    }
}