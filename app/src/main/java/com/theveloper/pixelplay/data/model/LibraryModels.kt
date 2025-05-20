package com.theveloper.pixelplay.data.model

import android.net.Uri

data class Album(
    val id: Long, // MediaStore.Audio.Albums._ID
    val title: String,
    val artist: String,
    val albumArtUri: Uri?,
    val songCount: Int
)

data class Artist(
    val id: Long, // MediaStore.Audio.Artists._ID
    val name: String,
    val songCount: Int
    // Podrías añadir una forma de obtener una imagen representativa del artista si es necesario
)