package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["album_id"], unique = false),
        Index(value = ["artist_id"], unique = false),
        Index(value = ["genre"], unique = false)
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE // Si un álbum se borra, sus canciones también
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.SET_NULL // Si un artista se borra, el artist_id de la canción se pone a null
                                          // o podrías elegir CASCADE si las canciones no deben existir sin artista.
                                          // SET_NULL es más flexible si las canciones pueden ser de "Artista Desconocido".
        )
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String, // Nombre del artista de la canción
    @ColumnInfo(name = "artist_id", index = true) val artistId: Long,
    @ColumnInfo(name = "album_name") val albumName: String, // Nombre del álbum de la canción
    @ColumnInfo(name = "album_id", index = true) val albumId: Long,
    @ColumnInfo(name = "content_uri_string") val contentUriString: String,
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "genre") val genre: String?,
    @ColumnInfo(name = "file_path") val filePath: String // Added filePath
)

fun SongEntity.toSong(): Song {
    return Song(
        id = this.id.toString(), // El modelo Song usa ID como String
        title = this.title,
        artist = this.artistName,
        artistId = this.artistId,
        album = this.albumName,
        albumId = this.albumId,
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre
        // filePath no está en el modelo Song, se usa internamente en el repo o SSoT
    )
}

fun List<SongEntity>.toSongs(): List<Song> {
    return this.map { it.toSong() }
}

// El modelo Song usa id como String, pero la entidad lo necesita como Long (de MediaStore)
// El modelo Song no tiene filePath, así que no se puede mapear desde ahí directamente.
// filePath se poblará desde MediaStore en el SyncWorker.
fun Song.toEntity(filePathFromMediaStore: String): SongEntity {
    return SongEntity(
        id = this.id.toLong(), // Asumiendo que el ID del modelo Song puede convertirse a Long
        title = this.title,
        artistName = this.artist,
        artistId = this.artistId,
        albumName = this.album,
        albumId = this.albumId,
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre,
        filePath = filePathFromMediaStore
    )
}

// Sobrecarga o alternativa si el path no está disponible o no es necesario al convertir de Modelo a Entidad
// (menos probable que se use si la entidad siempre requiere el path)
fun Song.toEntityWithoutPath(): SongEntity {
    return SongEntity(
        id = this.id.toLong(),
        title = this.title,
        artistName = this.artist,
        artistId = this.artistId,
        albumName = this.album,
        albumId = this.albumId,
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre,
        filePath = "" // Default o manejar como no disponible
    )
}
