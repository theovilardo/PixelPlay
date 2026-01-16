package com.theveloper.pixelplay.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class LibraryTabId(
    val storageKey: String,
    val title: String,
    val defaultSort: SortOption
) {
    SONGS("SONGS", "SONGS", SortOption.SongTitle),
    ALBUMS("ALBUMS", "ALBUMS", SortOption.AlbumTitle),
    ARTISTS("ARTIST", "ARTIST", SortOption.ArtistName),
    PLAYLISTS("PLAYLISTS", "PLAYLISTS", SortOption.PlaylistName),
    FOLDERS("FOLDERS", "FOLDERS", SortOption.FolderName),
    LIKED("LIKED", "LIKED", SortOption.LikedSongDateLiked);

    companion object {
        fun fromStorageKey(key: String): LibraryTabId =
            entries.firstOrNull { it.storageKey == key } ?: SONGS
    }
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull { it.storageKey == this }