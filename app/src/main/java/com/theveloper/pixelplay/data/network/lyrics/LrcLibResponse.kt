package com.theveloper.pixelplay.data.network.lyrics

import com.google.gson.annotations.SerializedName

/**
 * Representa la respuesta de la API de LRCLIB.
 * Contiene la letra de la canción, tanto en formato simple como sincronizado.
 */
data class LrcLibResponse(
    val id: Int,
    val name: String,
    val artistName: String,
    val albumName: String,
    val duration: Int,
    @SerializedName("plainLyrics")
    val plainLyrics: String?,
    @SerializedName("syncedLyrics")
    val syncedLyrics: String?
)