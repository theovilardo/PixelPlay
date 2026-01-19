package com.theveloper.pixelplay.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.database.MusicDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

object AlbumArtUtils {

    /**
     * Main function to get album art - tries multiple methods
     */
    fun getAlbumArtUri(
        appContext: Context,
        musicDao: MusicDao,
        path: String,
        albumId: Long,
        songId: Long,
        deepScan: Boolean
    ): String? {
        // Method 1: Try MediaStore (even though it often fails)
//        getMediaStoreAlbumArtUri(appContext, albumId)?.let { return it.toString() }

        // Method 2: Try embedded art from file
        getEmbeddedAlbumArtUri(appContext, path, songId, deepScan)?.let { return it.toString() }
        // Method 3: try from db
//        musicDao.getAlbumArtUriById(songId)?.let {
//            return it
//        }
        // Method 4: Try external album art files in directory
//        getExternalAlbumArtUri(path)?.let { return it.toString() }

        return null
    }

    /**
     * Enhanced embedded art extraction with better error handling
     */
    fun getEmbeddedAlbumArtUri(
        appContext: Context,
        filePath: String,
        songId: Long,
        deepScan: Boolean
    ): Uri? {
        if (!File(filePath).exists() || !File(filePath).canRead()) {
            return null
        }
        if (!deepScan) {

            // 1. Check if art is already cached
            val cachedFile = File(appContext.cacheDir, "song_art_${songId}.jpg")
            if (cachedFile.exists()) {
                // Touch file for LRU tracking
                cachedFile.setLastModified(System.currentTimeMillis())
                return try {
                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.provider",
                        cachedFile
                    )
                } catch (e: Exception) {
                    Uri.fromFile(cachedFile)
                }
            }
        }

        // 2. Check if marked as "no art" to skip extraction
        val noArtFile = File(appContext.cacheDir, "song_art_${songId}_no.jpg")
        if (noArtFile.exists()) {
            if (deepScan)
                noArtFile.delete()
            else
                return null
        }

        // 3. Try to extract embedded art using pooled MediaMetadataRetriever
        return MediaMetadataRetrieverPool.withRetriever { retriever ->
            try {
                retriever.setDataSource(filePath)
            } catch (e: IllegalArgumentException) {
                // FileDescriptor fallback
                try {
                    FileInputStream(filePath).use { fis ->
                        retriever.setDataSource(fis.fd)
                    }
                } catch (e2: Exception) {
                    return@withRetriever null
                }
            }

            val bytes = retriever.embeddedPicture
            if (bytes != null) {
                saveAlbumArtToCache(appContext, bytes, songId)
            } else {
                // Mark "no art" to avoid trying again
                noArtFile.createNewFile()
                null
            }
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
    fun getMediaStoreAlbumArtUri(appContext: Context, albumId: Long): Uri? {
        if (albumId <= 0) return null

        val potentialUri = ContentUris.withAppendedId(
            "content://media/external/audio/albumart".toUri(),
            albumId
        )

        return try {
            appContext.contentResolver.openFileDescriptor(potentialUri, "r")?.use {
                potentialUri // only return if open succeeded
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save embedded art to cache with unique naming
     */
    fun saveAlbumArtToCache(appContext: Context, bytes: ByteArray, songId: Long): Uri {
        val file = File(appContext.cacheDir, "song_art_${songId}.jpg")

        file.outputStream().use { outputStream ->
            outputStream.write(bytes)
        }
        
        // Trigger async cache cleanup if needed
        CoroutineScope(Dispatchers.IO).launch {
            AlbumArtCacheManager.cleanCacheIfNeeded(appContext)
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