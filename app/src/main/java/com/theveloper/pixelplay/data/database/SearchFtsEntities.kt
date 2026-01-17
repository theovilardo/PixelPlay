package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = SongEntity::class)
@Entity(tableName = "songs_fts")
data class SongFtsEntity(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_name") val albumName: String
)

@Fts4(contentEntity = AlbumEntity::class)
@Entity(tableName = "albums_fts")
data class AlbumFtsEntity(
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String
)

@Fts4(contentEntity = ArtistEntity::class)
@Entity(tableName = "artists_fts")
data class ArtistFtsEntity(
    @ColumnInfo(name = "name") val name: String
)
