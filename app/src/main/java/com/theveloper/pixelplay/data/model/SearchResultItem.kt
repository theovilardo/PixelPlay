package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SearchResultItem {
    data class SongItem(val song: Song) : SearchResultItem
    data class AlbumItem(val album: Album) : SearchResultItem
    data class ArtistItem(val artist: Artist) : SearchResultItem
    data class PlaylistItem(val playlist: Playlist) : SearchResultItem
}
