package com.theveloper.pixelplay.data.local.cue

import android.util.Log
import java.io.File

/**
 * Data class representing a single track from a CUE sheet.
 *
 * @property trackNumber The track number (1-based)
 * @property title The track title, or null if not specified
 * @property performer The track performer/artist, or null if not specified
 * @property startMs The start time of this track in milliseconds
 * @property endMs The end time of this track in milliseconds, or null if this is the last track
 */
data class CueTrack(
    val trackNumber: Int,
    val title: String?,
    val performer: String?,
    val startMs: Long,
    val endMs: Long?
)

/**
 * Data class representing a parsed CUE sheet.
 *
 * @property audioFileName The name of the audio file referenced in the CUE sheet
 * @property albumPerformer The album-level performer/artist, if specified
 * @property albumTitle The album title, if specified
 * @property tracks List of tracks defined in the CUE sheet
 */
data class CueSheet(
    val audioFileName: String,
    val albumPerformer: String?,
    val albumTitle: String?,
    val tracks: List<CueTrack>
)

/**
 * Parser for CUE sheet files commonly used with large audio files (FLAC, APE, etc.)
 * to define multiple tracks within a single file.
 *
 * This parser handles basic CUE sheet format including:
 * - FILE command to specify the audio file
 * - TRACK sections with number and type
 * - TITLE, PERFORMER commands
 * - INDEX 01 timestamps in mm:ss:ff format (where ff is frames at 75 fps)
 */
object CueParser {
    private const val TAG = "CueParser"

    /**
     * Parses a CUE file from the given file path.
     *
     * @param cueFilePath Absolute path to the .cue file
     * @return A [CueSheet] object if parsing succeeds, or null if parsing fails
     */
    fun parseCueFile(cueFilePath: String): CueSheet? {
        return try {
            val cueFile = File(cueFilePath)
            if (!cueFile.exists() || !cueFile.canRead()) {
                Log.w(TAG, "CUE file does not exist or cannot be read: $cueFilePath")
                return null
            }

            val content = cueFile.readText(Charsets.UTF_8)
            parseCueContent(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CUE file: $cueFilePath", e)
            null
        }
    }

    /**
     * Parses CUE content from a string.
     *
     * @param content The CUE file content as a string
     * @return A [CueSheet] object if parsing succeeds, or null if parsing fails
     */
    fun parseCueContent(content: String): CueSheet? {
        return try {
            val lines = content.lines()
            
            var audioFileName: String? = null
            var albumPerformer: String? = null
            var albumTitle: String? = null
            val tracks = mutableListOf<CueTrack>()
            
            var currentTrackNumber: Int? = null
            var currentTrackTitle: String? = null
            var currentTrackPerformer: String? = null
            var currentTrackStartMs: Long? = null
            
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("REM")) {
                    continue
                }
                
                when {
                    // FILE command
                    trimmedLine.startsWith("FILE", ignoreCase = true) -> {
                        audioFileName = extractQuotedString(trimmedLine, "FILE")
                    }
                    
                    // PERFORMER command (album-level or track-level)
                    trimmedLine.startsWith("PERFORMER", ignoreCase = true) -> {
                        val performer = extractQuotedString(trimmedLine, "PERFORMER")
                        if (currentTrackNumber == null) {
                            // Album-level performer
                            albumPerformer = performer
                        } else {
                            // Track-level performer
                            currentTrackPerformer = performer
                        }
                    }
                    
                    // TITLE command (album-level or track-level)
                    trimmedLine.startsWith("TITLE", ignoreCase = true) -> {
                        val title = extractQuotedString(trimmedLine, "TITLE")
                        if (currentTrackNumber == null) {
                            // Album-level title
                            albumTitle = title
                        } else {
                            // Track-level title
                            currentTrackTitle = title
                        }
                    }
                    
                    // TRACK command
                    trimmedLine.startsWith("TRACK", ignoreCase = true) -> {
                        // Save previous track if it exists
                        if (currentTrackNumber != null && currentTrackStartMs != null) {
                            tracks.add(
                                CueTrack(
                                    trackNumber = currentTrackNumber,
                                    title = currentTrackTitle,
                                    performer = currentTrackPerformer ?: albumPerformer,
                                    startMs = currentTrackStartMs,
                                    endMs = null // Will be set later
                                )
                            )
                        }
                        
                        // Start new track
                        val trackNumber = extractTrackNumber(trimmedLine)
                        if (trackNumber != null) {
                            currentTrackNumber = trackNumber
                            currentTrackTitle = null
                            currentTrackPerformer = null
                            currentTrackStartMs = null
                        }
                    }
                    
                    // INDEX command
                    trimmedLine.startsWith("INDEX", ignoreCase = true) -> {
                        // We only care about INDEX 01 (the start of the track)
                        if (trimmedLine.contains("INDEX 01", ignoreCase = true) ||
                            trimmedLine.matches(Regex("INDEX\\s+01\\s+.*", RegexOption.IGNORE_CASE))) {
                            val timestamp = extractTimestamp(trimmedLine)
                            if (timestamp != null) {
                                currentTrackStartMs = timestamp
                            }
                        }
                    }
                }
            }
            
            // Save the last track
            if (currentTrackNumber != null && currentTrackStartMs != null) {
                tracks.add(
                    CueTrack(
                        trackNumber = currentTrackNumber,
                        title = currentTrackTitle,
                        performer = currentTrackPerformer ?: albumPerformer,
                        startMs = currentTrackStartMs,
                        endMs = null
                    )
                )
            }
            
            // Set end times for all tracks except the last one
            for (i in 0 until tracks.size - 1) {
                tracks[i] = tracks[i].copy(endMs = tracks[i + 1].startMs)
            }
            
            if (audioFileName == null || tracks.isEmpty()) {
                Log.w(TAG, "CUE parsing resulted in no audio file or no tracks")
                return null
            }
            
            CueSheet(
                audioFileName = audioFileName,
                albumPerformer = albumPerformer,
                albumTitle = albumTitle,
                tracks = tracks
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CUE content", e)
            null
        }
    }

    /**
     * Extracts a quoted string from a CUE command line.
     * Example: 'TITLE "My Song"' returns "My Song"
     */
    private fun extractQuotedString(line: String, command: String): String? {
        val pattern = Regex("""$command\s+"([^"]+)"""", RegexOption.IGNORE_CASE)
        val match = pattern.find(line)
        return match?.groupValues?.getOrNull(1)
    }

    /**
     * Extracts the track number from a TRACK command line.
     * Example: 'TRACK 01 AUDIO' returns 1
     */
    private fun extractTrackNumber(line: String): Int? {
        val pattern = Regex("""TRACK\s+(\d+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(line)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    /**
     * Extracts and converts a timestamp from an INDEX command line.
     * Format: mm:ss:ff (minutes:seconds:frames, where frames are at 75 fps)
     * Example: 'INDEX 01 03:45:23' returns time in milliseconds
     */
    private fun extractTimestamp(line: String): Long? {
        val pattern = Regex("""INDEX\s+\d+\s+(\d+):(\d+):(\d+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(line)
        
        if (match != null) {
            val minutes = match.groupValues[1].toIntOrNull() ?: return null
            val seconds = match.groupValues[2].toIntOrNull() ?: return null
            val frames = match.groupValues[3].toIntOrNull() ?: return null
            
            return convertToMilliseconds(minutes, seconds, frames)
        }
        
        return null
    }

    /**
     * Converts CUE timestamp (mm:ss:ff) to milliseconds.
     * Frames are at 75 fps (CD audio standard).
     */
    private fun convertToMilliseconds(minutes: Int, seconds: Int, frames: Int): Long {
        val totalSeconds = minutes * 60 + seconds
        val frameMs = (frames * 1000.0 / 75.0).toLong()
        return totalSeconds * 1000L + frameMs
    }
}
