package com.theveloper.pixelplay.data.network.lyrics

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz de Retrofit para interactuar con la API de LRCLIB.
 */
interface LrcLibApiService {

    /**
     * Busca la letra de una canción utilizando sus metadatos.
     * @param trackName El nombre de la canción.
     * @param artistName El nombre del artista.
     * @param albumName El nombre del álbum.
     * @param duration La duración de la canción en segundos.
     * @return Una instancia de LrcLibResponse si se encuentra, o null.
     */
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String,
        @Query("duration") duration: Int
    ): LrcLibResponse?

    /**
     * Search for lyrics using flexible query parameters.
     * At least one of q or trackName should be provided.
     * @param query General search query (can include title, artist, or lyrics fragment).
     * @param trackName The name of the track.
     * @param artistName The name of the artist (optional filter).
     * @param albumName The name of the album (optional filter).
     * @return An array of LrcLibResponse objects.
     */
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") artistName: String? = null,
        @Query("album_name") albumName: String? = null
    ): Array<LrcLibResponse>?
}