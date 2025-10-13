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
        Index(value = ["artist_name"], unique = false), // Nuevo índice para búsquedas por nombre de artista
        Index(value = ["genre"], unique = false),
        Index(value = ["parent_directory_path"], unique = false) // Índice para filtrado por directorio
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
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "artist_id") val artistId: Long, // index = true eliminado
    @ColumnInfo(name = "album_name") val albumName: String,
    @ColumnInfo(name = "album_id") val albumId: Long, // index = true eliminado
    @ColumnInfo(name = "content_uri_string") val contentUriString: String,
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "genre") val genre: String?,
    @ColumnInfo(name = "file_path") val filePath: String, // Added filePath
    @ColumnInfo(name = "parent_directory_path") val parentDirectoryPath: String, // Added for directory filtering
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "lyrics", defaultValue = "null") val lyrics: String? = null,
    @ColumnInfo(name = "track_number", defaultValue = "0") val trackNumber: Int = 0,
    @ColumnInfo(name = "year", defaultValue = "0") val year: Int = 0,
    @ColumnInfo(name = "date_added", defaultValue = "0") val dateAdded: Long = System.currentTimeMillis()
)

fun SongEntity.toSong(): Song {
    return Song(
        id = this.id.toString(),
        title = this.title,
        artist = this.artistName,
        artistId = this.artistId,
        album = this.albumName,
        albumId = this.albumId,
        path = this.filePath, // Map the file path
        contentUriString = this.contentUriString,
        albumArtUriString = this.albumArtUriString,
        duration = this.duration,
        genre = this.genre,
        lyrics = this.lyrics,
        isFavorite = this.isFavorite,
        trackNumber = this.trackNumber,
        dateAdded = this.dateAdded,
        year = this.year
        // filePath no está en el modelo Song, se usa internamente en el repo o SSoT
    )
}

fun List<SongEntity>.toSongs(): List<Song> {
    return this.map { it.toSong() }
}

// El modelo Song usa id como String, pero la entidad lo necesita como Long (de MediaStore)
// El modelo Song no tiene filePath, así que no se puede mapear desde ahí directamente.
// filePath y parentDirectoryPath se poblarán desde MediaStore en el SyncWorker.
fun Song.toEntity(filePathFromMediaStore: String, parentDirFromMediaStore: String): SongEntity {
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
        lyrics = this.lyrics,
        filePath = filePathFromMediaStore,
        parentDirectoryPath = parentDirFromMediaStore,
        dateAdded = this.dateAdded,
        year = this.year
    )
}

// Sobrecarga o alternativa si los paths no están disponibles o no son necesarios al convertir de Modelo a Entidad
// (menos probable que se use si la entidad siempre requiere los paths)
fun Song.toEntityWithoutPaths(): SongEntity {
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
        lyrics = this.lyrics,
        filePath = "", // Default o manejar como no disponible
        parentDirectoryPath = "", // Default o manejar como no disponible
        dateAdded = this.dateAdded,
        year = this.year
    )
}
