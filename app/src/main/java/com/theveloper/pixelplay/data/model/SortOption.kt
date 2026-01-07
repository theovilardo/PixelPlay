package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

// Sealed class for Sort Options
@Immutable
sealed class SortOption(val storageKey: String, val displayName: String) {
    // Song Sort Options
    object SongDefaultOrder : SortOption("song_default_order", "Default Order")
    object SongTitleAZ : SortOption("song_title_az", "Title (A-Z)")
    object SongTitleZA : SortOption("song_title_za", "Title (Z-A)")
    object SongArtist : SortOption("song_artist", "Artist")
    object SongAlbum : SortOption("song_album", "Album")
    object SongDateAdded : SortOption("song_date_added", "Date Added")
    object SongDuration : SortOption("song_duration", "Duration")

    // Album Sort Options
    object AlbumTitleAZ : SortOption("album_title_az", "Title (A-Z)")
    object AlbumTitleZA : SortOption("album_title_za", "Title (Z-A)")
    object AlbumArtist : SortOption("album_artist", "Artist")
    object AlbumReleaseYear : SortOption("album_release_year", "Release Year")
    object AlbumSizeAsc : SortOption("album_size_asc", "Fewest Songs")
    object AlbumSizeDesc : SortOption("album_size_desc", "Most Songs")

    // Artist Sort Options
    object ArtistNameAZ : SortOption("artist_name_az", "Name (A-Z)")
    object ArtistNameZA : SortOption("artist_name_za", "Name (Z-A)")
    // object ArtistNumSongs : SortOption("artist_num_songs", "Number of Songs") // Requires ViewModel change & data

    // Playlist Sort Options
    object PlaylistNameAZ : SortOption("playlist_name_az", "Name (A-Z)")
    object PlaylistNameZA : SortOption("playlist_name_za", "Name (Z-A)")
    object PlaylistDateCreated : SortOption("playlist_date_created", "Date Created")
    // object PlaylistNumSongs : SortOption("playlist_num_songs", "Number of Songs") // Requires ViewModel change & data

    // Liked Sort Options (similar to Songs)
    object LikedSongTitleAZ : SortOption("liked_title_az", "Title (A-Z)")
    object LikedSongTitleZA : SortOption("liked_title_za", "Title (Z-A)")
    object LikedSongArtist : SortOption("liked_artist", "Artist")
    object LikedSongAlbum : SortOption("liked_album", "Album")
    object LikedSongDateLiked : SortOption("liked_date_liked", "Date Liked")

    // Folder Sort Options
    object FolderNameAZ : SortOption("folder_name_az", "Name (A-Z)")
    object FolderNameZA : SortOption("folder_name_za", "Name (Z-A)")

    companion object {
        val SONGS: List<SortOption> = listOf(
            SongDefaultOrder,
            SongTitleAZ,
            SongTitleZA,
            SongArtist,
            SongAlbum,
            SongDateAdded,
            SongDuration
        )
        val ALBUMS: List<SortOption> = listOf(
            AlbumTitleAZ,
            AlbumTitleZA,
            AlbumArtist,
            AlbumReleaseYear,
            AlbumSizeAsc,
            AlbumSizeDesc
        )
        val ARTISTS: List<SortOption> = listOf(
            ArtistNameAZ,
            ArtistNameZA
        )
        val PLAYLISTS: List<SortOption> = listOf(
            PlaylistNameAZ,
            PlaylistNameZA,
            PlaylistDateCreated
        )
        val FOLDERS: List<SortOption> = listOf(
            FolderNameAZ,
            FolderNameZA
        )
        val LIKED: List<SortOption> = listOf(
            LikedSongTitleAZ,
            LikedSongTitleZA,
            LikedSongArtist,
            LikedSongAlbum,
            LikedSongDateLiked
        )

        fun fromStorageKey(
            rawValue: String?,
            allowed: Collection<SortOption>,
            fallback: SortOption
        ): SortOption {
            if (rawValue.isNullOrBlank()) {
                return fallback
            }

            val sanitized = allowed.filterIsInstance<SortOption>()
            if (sanitized.isEmpty()) {
                return fallback
            }

            sanitized.firstOrNull { option -> option.storageKey == rawValue }?.let { matched ->
                return matched
            }

            // Legacy values used display names; fall back to matching within the allowed group.
            return sanitized.firstOrNull { option -> option.displayName == rawValue } ?: fallback
        }
    }
}