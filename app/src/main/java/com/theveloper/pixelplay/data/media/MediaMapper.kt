package com.theveloper.pixelplay.data.media

import android.content.Context
import androidx.media3.common.MediaItem
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper to map MediaItem to Song.
 * Note: This does NOT have access to the full song library master list,
 * so it should be used for strictly metadata-based mapping or fallback.
 * The ViewModel should try lookup by ID first.
 */
@Singleton
class MediaMapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveSongFromMediaItem(mediaItem: MediaItem): Song? {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        // extras are lazily populated in some cases, or we rely on localConfiguration
        val contentUri = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
            ?: mediaItem.localConfiguration?.uri?.toString()
            ?: return null

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_song_title)
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_artist)
        val album = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM)?.takeIf { it.isNotBlank() }
            ?: metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_album)
        val albumId = -1L
        val duration = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION) ?: 0L
        val dateAdded = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DATE_ADDED) ?: System.currentTimeMillis()
        val id = mediaItem.mediaId

        // Note: This creates a partial Song object. 
        // Some fields like path, genre, year might be missing if not in extras.
        return Song(
            id = id,
            title = title,
            artist = artist,
            artistId = -1L, // unknown from just MediaItem typically
            album = album,
            albumId = albumId,
            path = "", // local path unknown from URI usually
            contentUriString = contentUri,
            albumArtUriString = metadata.artworkUri?.toString(),
            duration = duration,
            dateAdded = dateAdded,
            mimeType = null, 
            bitrate = null,
            sampleRate = null
        )
    }
}
