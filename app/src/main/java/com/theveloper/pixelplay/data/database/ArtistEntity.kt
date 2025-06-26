package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Artist

@Entity(
    tableName = "artists",
    indices = [Index(value = ["name"], unique = false)] // Índice en el nombre para búsquedas rápidas
)
data class ArtistEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int
)

fun ArtistEntity.toArtist(): Artist {
    return Artist(
        id = this.id,
        name = this.name,
        songCount = this.trackCount // El modelo Artist usa songCount, MediaStore usa NUMBER_OF_TRACKS
    )
}

fun List<ArtistEntity>.toArtists(): List<Artist> {
    return this.map { it.toArtist() }
}

fun Artist.toEntity(): ArtistEntity {
    return ArtistEntity(
        id = this.id,
        name = this.name,
        trackCount = this.songCount
    )
}
