package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

// Sealed class for Sort Options
@Immutable
sealed class SortOption(val storageKey: String, val displayName: String) {
    // Song Sort Options
    object SongDefaultOrder : SortOption("song_default_order", "Default Order")
    object SongTitle : SortOption("song_title", "Title")
    object SongArtist : SortOption("song_artist", "Artist")
    object SongAlbum : SortOption("song_album", "Album")
    object SongDateAdded : SortOption("song_date_added", "Date Added")
    object SongDuration : SortOption("song_duration", "Duration")
    object SongReleaseYear : SortOption("song_release_year", "Release Year")

    // Album Sort Options
    object AlbumTitle : SortOption("album_title", "Title")
    object AlbumArtist : SortOption("album_artist", "Artist")
    object AlbumReleaseYear : SortOption("album_release_year", "Release Year")
    object AlbumSize : SortOption("album_size", "Song Count")

    // Artist Sort Options
    object ArtistName : SortOption("artist_name", "Name")
    object ArtistSongCount : SortOption("artist_song_count", "Song Count")

    // Playlist Sort Options
    object PlaylistName : SortOption("playlist_name", "Name")
    object PlaylistDateCreated : SortOption("playlist_date_created", "Date Created")

    // Liked Sort Options (similar to Songs)
    object LikedSongTitle : SortOption("liked_title", "Title")
    object LikedSongArtist : SortOption("liked_artist", "Artist")
    object LikedSongAlbum : SortOption("liked_album", "Album")
    object LikedSongDateLiked : SortOption("liked_date_liked", "Date Liked")
    object LikedSongReleaseYear : SortOption("liked_release_year", "Release Year")

    // Folder Sort Options
    object FolderName : SortOption("folder_name", "Name")
    object FolderDateModified : SortOption("folder_date_modified", "Date Modified")

    companion object {
        val SONGS: List<SortOption> get() = listOf(
            SongDefaultOrder,
            SongTitle,
            SongArtist,
            SongAlbum,
            SongDateAdded,
            SongDuration,
            SongReleaseYear
        )
        val ALBUMS: List<SortOption> get() = listOf(
            AlbumTitle,
            AlbumArtist,
            AlbumReleaseYear,
            AlbumSize
        )
        val ARTISTS: List<SortOption> get() = listOf(
            ArtistName,
            ArtistSongCount
        )
        val PLAYLISTS: List<SortOption> get() = listOf(
            PlaylistName,
            PlaylistDateCreated
        )
        val FOLDERS: List<SortOption> get() = listOf(
            FolderName,
            FolderDateModified
        )
        val LIKED: List<SortOption> get() = listOf(
            LikedSongTitle,
            LikedSongArtist,
            LikedSongAlbum,
            LikedSongDateLiked,
            LikedSongReleaseYear
        )

        fun fromStorageKey(
            rawValue: String?,
            allowed: Collection<SortOption>,
            fallback: SortOption
        ): Pair<SortOption, SortOrder> {
            if (rawValue.isNullOrBlank()) {
                return fallback to SortOrder.ASCENDING
            }

            // check exact match first (for new keys)
            val exactMatch = allowed.firstOrNull { it.storageKey == rawValue }
            if (exactMatch != null) {
                return exactMatch to SortOrder.ASCENDING // Default to ASC if only key is present
            }

            // Migration logic for old keys
            return when (rawValue) {
                "song_title_az" -> SongTitle to SortOrder.ASCENDING
                "song_title_za" -> SongTitle to SortOrder.DESCENDING
                "song_artist" -> SongArtist to SortOrder.ASCENDING 
                "song_album" -> SongAlbum to SortOrder.ASCENDING
                "song_date_added" -> SongDateAdded to SortOrder.DESCENDING 
                "song_duration" -> SongDuration to SortOrder.DESCENDING 
                
                "album_title_az" -> AlbumTitle to SortOrder.ASCENDING
                "album_title_za" -> AlbumTitle to SortOrder.DESCENDING
                "album_size_asc" -> AlbumSize to SortOrder.ASCENDING
                "album_size_desc" -> AlbumSize to SortOrder.DESCENDING
                
                "artist_name_az" -> ArtistName to SortOrder.ASCENDING
                "artist_name_za" -> ArtistName to SortOrder.DESCENDING
                
                "playlist_name_az" -> PlaylistName to SortOrder.ASCENDING
                "playlist_name_za" -> PlaylistName to SortOrder.DESCENDING
                "playlist_date_created" -> PlaylistDateCreated to SortOrder.DESCENDING
                
                "liked_title_az" -> LikedSongTitle to SortOrder.ASCENDING
                "liked_title_za" -> LikedSongTitle to SortOrder.DESCENDING
                "liked_date_liked" -> LikedSongDateLiked to SortOrder.DESCENDING
                
                "folder_name_az" -> FolderName to SortOrder.ASCENDING
                "folder_name_za" -> FolderName to SortOrder.DESCENDING
                
                else -> {
                     // Try to match by display name as fallback
                    val byName = allowed.firstOrNull { it.displayName == rawValue }
                    if (byName != null) byName to SortOrder.ASCENDING else fallback to SortOrder.ASCENDING
                }
            }
        }
    }
}