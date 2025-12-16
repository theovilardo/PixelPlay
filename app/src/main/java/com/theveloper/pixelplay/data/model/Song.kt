package com.theveloper.pixelplay.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String, // Display string (combined artists or primary artist)
    val artistId: Long, // Primary artist ID for backward compatibility
    val artists: List<ArtistRef> = emptyList(), // All artists for multi-artist support
    val album: String,
    val albumId: Long,
    val albumArtist: String? = null, // Album artist from metadata
    val path: String, // Added for direct file system access
    val contentUriString: String,
    val albumArtUriString: String?,
    val duration: Long,
    val genre: String? = null,
    val lyrics: String? = null,
    val isFavorite: Boolean = false,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
) {
    /**
     * Returns the display string for artists.
     * If multiple artists exist, joins them with ", ".
     * Falls back to the artist field if artists list is empty.
     */
    val displayArtist: String
        get() = if (artists.isNotEmpty()) {
            artists.sortedByDescending { it.isPrimary }.joinToString(", ") { it.name }
        } else {
            artist
        }

    /**
     * Returns the primary artist from the artists list,
     * or creates one from the legacy artist field.
     */
    val primaryArtist: ArtistRef
        get() = artists.find { it.isPrimary }
            ?: artists.firstOrNull()
            ?: ArtistRef(id = artistId, name = artist, isPrimary = true)

    companion object {
        fun emptySong(): Song {
            return Song(
                id = "-1",
                title = "",
                artist = "",
                artistId = -1L,
                artists = emptyList(),
                album = "",
                albumId = -1L,
                albumArtist = null,
                path = "",
                contentUriString = "",
                albumArtUriString = null,
                duration = 0L,
                genre = null,
                lyrics = null,
                isFavorite = false,
                trackNumber = 0,
                year = 0,
                dateAdded = 0,
                mimeType = "-",
                bitrate = 0,
                sampleRate = 0
            )
        }
    }
}