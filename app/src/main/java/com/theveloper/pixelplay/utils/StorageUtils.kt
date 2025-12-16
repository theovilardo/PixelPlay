package com.theveloper.pixelplay.utils

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

/**
 * Represents the type of storage device
 */
enum class StorageType {
    INTERNAL,
    SD_CARD,
    USB
}

/**
 * Data class representing storage information
 */
data class StorageInfo(
    val path: File,
    val displayName: String,
    val storageType: StorageType,
    val isRemovable: Boolean
)

/**
 * Utility object for detecting and managing storage devices
 */
object StorageUtils {

    /**
     * Get all available storage devices (Internal Storage, SD Card, USB OTG)
     * @param context Application context
     * @return List of available StorageInfo objects
     */
    fun getAvailableStorages(context: Context): List<StorageInfo> {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        val storages = mutableListOf<StorageInfo>()

        // Counter for multiple USB devices
        var usbCounter = 0

        for (volume in storageVolumes) {
            // Skip volumes that are not mounted
            if (volume.state != Environment.MEDIA_MOUNTED) continue

            val path = getVolumePath(volume) ?: continue

            val storageType = determineStorageType(volume)
            val displayName = when (storageType) {
                StorageType.INTERNAL -> "Internal Storage"
                StorageType.SD_CARD -> "SD Card"
                StorageType.USB -> {
                    usbCounter++
                    if (usbCounter > 1) "USB Storage $usbCounter" else "USB Storage"
                }
            }

            storages.add(
                StorageInfo(
                    path = path,
                    displayName = displayName,
                    storageType = storageType,
                    isRemovable = volume.isRemovable
                )
            )
        }

        // Sort: Internal first, then SD Card, then USB devices
        return storages.sortedBy { it.storageType.ordinal }
    }

    /**
     * Get the file path for a StorageVolume
     */
    private fun getVolumePath(volume: StorageVolume): File? {
        return try {
            // Use directory property (API 30+)
            volume.directory
        } catch (e: Exception) {
            // Fallback for older approach
            try {
                val getPath = volume.javaClass.getMethod("getPath")
                val path = getPath.invoke(volume) as? String
                path?.let { File(it) }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Determine the storage type based on StorageVolume properties
     */
    private fun determineStorageType(volume: StorageVolume): StorageType {
        // Primary storage is always internal
        if (volume.isPrimary) {
            return StorageType.INTERNAL
        }

        // Check if it's removable
        if (!volume.isRemovable) {
            return StorageType.INTERNAL
        }

        // Try to determine if it's SD card or USB
        // SD cards typically have specific descriptions or are emulated
        val description = volume.getDescription(null)?.lowercase() ?: ""
        
        return when {
            description.contains("sd") -> StorageType.SD_CARD
            description.contains("usb") -> StorageType.USB
            description.contains("otg") -> StorageType.USB
            // Default removable storage as SD card (most common case)
            else -> {
                // Additional heuristic: check volume UUID pattern
                // SD cards often have specific UUID formats
                val uuid = volume.uuid
                if (uuid != null && uuid.length <= 9) {
                    // Short UUID typically indicates SD card (e.g., "1234-5678")
                    StorageType.SD_CARD
                } else {
                    // Longer or null UUID might indicate USB
                    StorageType.USB
                }
            }
        }
    }

    /**
     * Check if any external storage (SD Card or USB) is available
     */
    fun hasExternalStorage(context: Context): Boolean {
        return getAvailableStorages(context).any { it.storageType != StorageType.INTERNAL }
    }

    /**
     * Get internal storage only
     */
    fun getInternalStorage(): StorageInfo {
        return StorageInfo(
            path = Environment.getExternalStorageDirectory(),
            displayName = "Internal Storage",
            storageType = StorageType.INTERNAL,
            isRemovable = false
        )
    }
}
