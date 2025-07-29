package com.theveloper.pixelplay.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.theveloper.pixelplay.data.database.SongEntity

@Immutable
data class Song(
    val id: String, // MediaStore.Audio.Media._ID
    val title: String,
    val artist: String,
    val artistId: Long, // MediaStore.Audio.Media.ARTIST_ID para obtener foto de artista
    val album: String,
    val albumId: Long, // MediaStore.Audio.Media.ALBUM_ID para obtener carátula
    val contentUriString: String, // Uri para cargar la canción
    val albumArtUriString: String?, // Uri de la carátula del álbum
    val duration: Long, // en milisegundos
    val genre: String? = null, // Added genre field
    val isFavorite: Boolean = false
) {
    companion object {
        fun emptySong(): Song {
            return Song(
                id = "-1",
                title = "",
                artist = "",
                artistId = -1L,
                album = "",
                albumId = -1L,
                contentUriString = "",
                albumArtUriString = null,
                duration = 0L,
                genre = null,
                isFavorite = false
            )
        }
    }

    fun toSongEntity(): SongEntity {
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
            isFavorite = this.isFavorite,
            filePath = "", // These are not part of the domain model, so they are empty.
            parentDirectoryPath = "" // These are not part of the domain model, so they are empty.
        )
    }
}