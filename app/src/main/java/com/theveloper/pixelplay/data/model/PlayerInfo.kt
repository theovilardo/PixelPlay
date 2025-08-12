package com.theveloper.pixelplay.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient // Para campos que no queremos serializar

@Serializable
data class QueueItem(
    val id: Long, // ID único de la canción
    val albumArtBitmapData: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueueItem

        if (id != other.id) return false
        if (albumArtBitmapData != null) {
            if (other.albumArtBitmapData == null) return false
            if (!albumArtBitmapData.contentEquals(other.albumArtBitmapData)) return false
        } else if (other.albumArtBitmapData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (albumArtBitmapData?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class PlayerInfo(
    val songTitle: String = "",
    val artistName: String = "",
    val isPlaying: Boolean = false,
    val albumArtUri: String? = null,
    val albumArtBitmapData: ByteArray? = null,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val lyrics: Lyrics? = null,
    val isLoadingLyrics: Boolean = false,
    val queue: List<QueueItem> = emptyList()
) {
    // equals y hashCode para ByteArray, ya que el por defecto no es comparando contenido
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerInfo

        if (songTitle != other.songTitle) return false
        if (artistName != other.artistName) return false
        if (isPlaying != other.isPlaying) return false
        if (albumArtUri != other.albumArtUri) return false
        if (albumArtBitmapData != null) {
            if (other.albumArtBitmapData == null) return false
            if (!albumArtBitmapData.contentEquals(other.albumArtBitmapData)) return false
        } else if (other.albumArtBitmapData != null) return false
        if (currentPositionMs != other.currentPositionMs) return false
        if (totalDurationMs != other.totalDurationMs) return false
        if (queue != other.queue) return false
        if (lyrics != other.lyrics) return false
        if (isLoadingLyrics != other.isLoadingLyrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = songTitle.hashCode()
        result = 31 * result + artistName.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + (albumArtUri?.hashCode() ?: 0)
        result = 31 * result + (albumArtBitmapData?.contentHashCode() ?: 0)
        result = 31 * result + currentPositionMs.hashCode()
        result = 31 * result + totalDurationMs.hashCode()
        result = 31 * result + queue.hashCode()
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + isLoadingLyrics.hashCode()
        return result
    }
}