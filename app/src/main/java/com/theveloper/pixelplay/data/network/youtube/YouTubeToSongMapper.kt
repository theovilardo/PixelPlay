package com.theveloper.pixelplay.data.network.youtube

import com.theveloper.pixelplay.data.model.Song
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Mapper to convert NewPipe StreamInfoItem to Song objects
 */
object YouTubeToSongMapper {

    /**
     * Convert a YouTube StreamInfoItem to a Song object
     * @param videoItem YouTube video item
     * @return Song object with YouTube video data
     */
    fun mapToSong(videoItem: StreamInfoItem): Song {
        // Extract artist from uploader name or use "Unknown Artist"
        val artist = videoItem.uploaderName ?: "Unknown Artist"
        
        // Extract video ID from URL
        val videoId = extractVideoIdFromUrl(videoItem.url) ?: "unknown"
        
        // Create a unique ID for YouTube songs using video ID
        // Format: "youtube_{videoId}" - this allows us to extract the video ID later
        val songId = "youtube_$videoId"
        
        // Convert duration from seconds to milliseconds
        val durationMs = videoItem.duration * 1000L
        
        // Get the highest quality thumbnail
        val thumbnailUrl = videoItem.thumbnails
            .maxByOrNull { it.height }
            ?.url ?: ""
        
        return Song(
            id = songId,
            title = videoItem.name,
            artist = artist,
            artistId = -1L, // YouTube songs don't have artist IDs
            artists = emptyList(),
            album = "YouTube", // Default album name for YouTube songs
            albumId = -1L,
            albumArtist = null,
            path = "", // No local path for YouTube songs
            contentUriString = "", // Will be set when streaming
            albumArtUriString = thumbnailUrl,
            duration = durationMs,
            genre = null,
            lyrics = null,
            isFavorite = false,
            trackNumber = 0,
            year = 0,
            dateAdded = System.currentTimeMillis(),
            mimeType = null,
            bitrate = null,
            sampleRate = null
        )
    }

    /**
     * Convert a list of YouTube StreamInfoItems to Song objects
     * @param videoItems List of YouTube video items
     * @return List of Song objects
     */
    fun mapToSongs(videoItems: List<StreamInfoItem>): List<Song> {
        return videoItems.mapNotNull { item ->
            try {
                mapToSong(item)
            } catch (e: Exception) {
                null // Skip items that can't be mapped
            }
        }
    }

    /**
     * Extract video ID from a YouTube song ID
     * @param songId Song ID in format "youtube_{videoId}"
     * @return YouTube video ID, or null if format is invalid
     */
    fun extractVideoId(songId: String): String? {
        return if (songId.startsWith("youtube_")) {
            songId.removePrefix("youtube_")
        } else null
    }

    /**
     * Extract video ID from YouTube URL
     * @param url YouTube URL
     * @return YouTube video ID, or null if not found
     */
    private fun extractVideoIdFromUrl(url: String): String? {
        return try {
            when {
                url.contains("youtube.com/watch?v=") -> {
                    url.substringAfter("v=").substringBefore("&")
                }
                url.contains("youtu.be/") -> {
                    url.substringAfter("youtu.be/").substringBefore("?")
                }
                url.startsWith("/watch?v=") -> {
                    url.substringAfter("v=").substringBefore("&")
                }
                url.matches(Regex("[a-zA-Z0-9_-]{11}")) -> {
                    url // Direct video ID
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a song is from YouTube
     * @param song Song object
     * @return true if song is from YouTube
     */
    fun isYouTubeSong(song: Song): Boolean {
        return song.id.startsWith("youtube_")
    }
}
