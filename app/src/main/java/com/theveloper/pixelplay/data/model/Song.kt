package com.theveloper.pixelplay.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

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
    val genre: String? = null // Added genre field
)