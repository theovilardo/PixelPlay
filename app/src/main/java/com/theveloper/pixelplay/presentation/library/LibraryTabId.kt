package com.theveloper.pixelplay.presentation.library

import com.theveloper.pixelplay.data.model.SortOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Stable identifiers for each library tab. The [stableKey] value is persisted so it must not
 * change between app versions.
 */
enum class LibraryTabId(
    val stableKey: String,
    val label: String,
    val sortOptions: List<SortOption>
) {
    Songs(
        stableKey = "SONGS",
        label = "SONGS",
        sortOptions = listOf(
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongAlbum,
            SortOption.SongDateAdded,
            SortOption.SongDuration
        )
    ),
    Albums(
        stableKey = "ALBUMS",
        label = "ALBUMS",
        sortOptions = listOf(
            SortOption.AlbumTitleAZ,
            SortOption.AlbumTitleZA,
            SortOption.AlbumArtist,
            SortOption.AlbumReleaseYear
        )
    ),
    Artists(
        stableKey = "ARTIST",
        label = "ARTIST",
        sortOptions = listOf(
            SortOption.ArtistNameAZ,
            SortOption.ArtistNameZA
        )
    ),
    Playlists(
        stableKey = "PLAYLISTS",
        label = "PLAYLISTS",
        sortOptions = listOf(
            SortOption.PlaylistNameAZ,
            SortOption.PlaylistNameZA,
            SortOption.PlaylistDateCreated
        )
    ),
    Folders(
        stableKey = "FOLDERS",
        label = "FOLDERS",
        sortOptions = listOf(
            SortOption.FolderNameAZ,
            SortOption.FolderNameZA
        )
    ),
    Liked(
        stableKey = "LIKED",
        label = "LIKED",
        sortOptions = listOf(
            SortOption.LikedSongTitleAZ,
            SortOption.LikedSongTitleZA,
            SortOption.LikedSongArtist,
            SortOption.LikedSongAlbum,
            SortOption.LikedSongDateLiked
        )
    );

    companion object {
        val defaultOrder: List<LibraryTabId> = entries.toList()

        fun fromStableKey(key: String): LibraryTabId? = entries.firstOrNull { it.stableKey == key }
    }
}

internal fun decodeLibraryTabOrder(orderJson: String?): List<LibraryTabId> {
    val storedKeys = orderJson?.let {
        runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull()
    } ?: emptyList()

    val ordered = LinkedHashSet<LibraryTabId>()
    storedKeys.mapNotNull { LibraryTabId.fromStableKey(it) }.forEach { ordered.add(it) }
    LibraryTabId.defaultOrder.forEach { ordered.add(it) }
    return ordered.toList()
}
