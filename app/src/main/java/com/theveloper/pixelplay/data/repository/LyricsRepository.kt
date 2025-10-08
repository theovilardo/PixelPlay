package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song

interface LyricsRepository {
    suspend fun getLyrics(song: Song): Lyrics?
    suspend fun fetchFromRemote(song: Song): Result<Pair<Lyrics, String>>
    suspend fun searchRemote(song: Song): Result<List<LyricsSearchResult>>
    suspend fun updateLyrics(songId: Long, lyricsContent: String)
    suspend fun resetLyrics(songId: Long)
    fun clearCache()
}