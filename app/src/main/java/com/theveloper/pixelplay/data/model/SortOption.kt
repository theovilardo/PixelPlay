package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

// Sealed class for Sort Options
@Immutable
sealed class SortOption(val displayName: String) {
    // Song Sort Options
    object SongTitleAZ : SortOption("Title (A-Z)")
    object SongTitleZA : SortOption("Title (Z-A)")
    object SongArtist : SortOption("Artist")
    object SongAlbum : SortOption("Album")
    object SongDateAdded : SortOption("Date Added")
    object SongDuration : SortOption("Duration")

    // Album Sort Options
    object AlbumTitleAZ : SortOption("Title (A-Z)")
    object AlbumTitleZA : SortOption("Title (Z-A)")
    object AlbumArtist : SortOption("Artist")
    object AlbumReleaseYear : SortOption("Release Year")

    // Artist Sort Options
    object ArtistNameAZ : SortOption("Name (A-Z)")
    object ArtistNameZA : SortOption("Name (Z-A)")
    // object ArtistNumSongs : SortOption("Number of Songs") // Requires ViewModel change & data

    // Playlist Sort Options
    object PlaylistNameAZ : SortOption("Name (A-Z)")
    object PlaylistNameZA : SortOption("Name (Z-A)")
    object PlaylistDateCreated : SortOption("Date Created")
    // object PlaylistNumSongs : SortOption("Number of Songs") // Requires ViewModel change & data

    // Liked Sort Options (similar to Songs)
    object LikedSongTitleAZ : SortOption("Title (A-Z)")
    object LikedSongTitleZA : SortOption("Title (Z-A)")
    object LikedSongArtist : SortOption("Artist")
    object LikedSongAlbum : SortOption("Album")
    object LikedSongDateLiked : SortOption("Date Liked")

    // Folder Sort Options
    object FolderNameAZ : SortOption("Name (A-Z)")
    object FolderNameZA : SortOption("Name (Z-A)")
}