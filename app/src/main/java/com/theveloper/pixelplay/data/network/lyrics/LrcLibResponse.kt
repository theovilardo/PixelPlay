package com.theveloper.pixelplay.data.network.lyrics

import com.google.gson.annotations.SerializedName

/**
 * Representa la respuesta de la API de LRCLIB.
 * Contiene la letra de la canción, tanto en formato simple como sincronizado.
 */
data class LrcLibResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("artistName") val artistName: String,
    @SerializedName("albumName") val albumName: String,
    @SerializedName("duration") val duration: Double,
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?
)