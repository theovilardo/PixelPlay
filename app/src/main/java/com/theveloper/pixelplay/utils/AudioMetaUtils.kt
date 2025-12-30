package com.theveloper.pixelplay.utils

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import com.theveloper.pixelplay.data.database.MusicDao
import java.io.File

data class AudioMeta(
    val mimeType: String?,
    val bitrate: Int?,      // bits per second
    val sampleRate: Int?   // Hz
)

object AudioMetaUtils {

    /**
     * Returns audio metadata for a given file path.
     * Tries MediaMetadataRetriever first, then falls back to MediaExtractor.
     */
    suspend fun getAudioMetadata(musicDao: MusicDao, id: Long, filePath: String, deepScan: Boolean): AudioMeta {
        val cached = musicDao.getAudioMetadataById(id)
        if (!deepScan && cached != null &&
            cached.mimeType != null &&
            cached.bitrate != null &&
            cached.sampleRate != null
        )
            return cached

        val file = File(filePath)
        if (!file.exists() || !file.canRead()) return AudioMeta(null, null, null)

        var mimeType: String? = null
        var bitrate: Int? = null
        var sampleRate: Int? = null

        // Try MediaMetadataRetriever
        try {
            MediaMetadataRetriever().apply {
                setDataSource(filePath)
                mimeType = extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                bitrate =
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                sampleRate =
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
                release()
            }
        } catch (e: Exception) {
            Log.w("AudioMetaUtils", "Retriever failed for $filePath: ${e.message}")
        }

        // Fallback with MediaExtractor
        try {
            MediaExtractor().apply {
                setDataSource(filePath)
                for (i in 0 until trackCount) {
                    val format: MediaFormat = getTrackFormat(i)
                    val trackMime = format.getString(MediaFormat.KEY_MIME)
                    if (trackMime?.startsWith("audio/") == true) {
                        mimeType = mimeType ?: trackMime
                        sampleRate =
                            sampleRate ?: format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        bitrate = bitrate ?: if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            format.getInteger(MediaFormat.KEY_BIT_RATE)
                        } else null
                        break
                    }
                }
                release()
            }
        } catch (e: Exception) {
            Log.w("AudioMetaUtils", "Extractor failed for $filePath: ${e.message}")
        }

        return AudioMeta(mimeType, bitrate, sampleRate)

    }

    fun mimeTypeToFormat(mimeType: String?): String {
        return when (mimeType?.lowercase()) {
            "audio/mpeg" -> "mp3"
            "audio/flac" -> "flac"
            "audio/x-wav", "audio/wav" -> "wav"
            "audio/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/mp4", "audio/m4a" -> "m4a"
            "audio/aac" -> "aac"
            "audio/amr" -> "amr"
            else -> "-"
        }
    }
}
