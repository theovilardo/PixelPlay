package com.theveloper.pixelplay.data.model

import kotlinx.serialization.Serializable

@Serializable // Para poder convertirlo a/desde JSON
data class Playlist(
    val id: String, // UUID o timestamp para unicidad
    var name: String,
    var songIds: List<String>, // Lista de IDs de las canciones en MediaStore
    val createdAt: Long = System.currentTimeMillis(),
    var lastModified: Long = System.currentTimeMillis()
    // Podrías añadir una URI para una imagen de portada de la playlist si quieres
)