package com.theveloper.pixelplay.data.repository

import android.net.Uri
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song

interface MusicRepository {
    /**
     * Obtiene la lista de archivos de audio (canciones) paginada y filtrada por directorios permitidos.
     * @param page El número de página (empezando en 1).
     * @param pageSize El número de elementos por página.
     * @return Lista de objetos Song para la página solicitada.
     */
    suspend fun getAudioFiles(page: Int, pageSize: Int): List<Song>

    /**
     * Obtiene la lista de álbumes paginada.
     * @param page El número de página (empezando en 1).
     * @param pageSize El número de elementos por página.
     * @return Lista de objetos Album para la página solicitada.
     */
    suspend fun getAlbums(page: Int, pageSize: Int): List<Album>

    /**
     * Obtiene la lista de artistas paginada.
     * @param page El número de página (empezando en 1).
     * @param pageSize El número de elementos por página.
     * @return Lista de objetos Artist para la página solicitada.
     */
    suspend fun getArtists(page: Int, pageSize: Int): List<Artist>

    /**
     * Obtiene la lista de canciones para un álbum específico (NO paginada para la cola de reproducción).
     * @param albumId El ID del álbum.
     * @return Lista de objetos Song pertenecientes al álbum.
     */
    suspend fun getSongsForAlbum(albumId: Long): List<Song>

    /**
     * Obtiene la lista de canciones para un artista específico (NO paginada para la cola de reproducción).
     * @param artistId El ID del artista.
     * @return Lista de objetos Song pertenecientes al artista.
     */
    suspend fun getSongsForArtist(artistId: Long): List<Song>

    /**
     * Obtiene una lista de canciones por sus IDs.
     * @param songIds Lista de IDs de canciones.
     * @return Lista de objetos Song correspondientes a los IDs, en el mismo orden.
     */
    suspend fun getSongsByIds(songIds: List<String>): List<Song>

    /**
     * Obtiene todos los directorios únicos que contienen archivos de audio.
     * Esto se usa principalmente para la configuración inicial de directorios.
     * También gestiona el guardado inicial de directorios permitidos si es la primera vez.
     * @return Conjunto de rutas de directorios únicas.
     */
    suspend fun getAllUniqueAudioDirectories(): Set<String>

    suspend fun getAllUniqueAlbumArtUris(): List<Uri> // Nuevo para precarga de temas

    suspend fun invalidateCachesDependentOnAllowedDirectories() // Nuevo para precarga de temas

    suspend fun searchSongs(query: String): List<Song>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun searchArtists(query: String): List<Artist>
    suspend fun searchPlaylists(query: String): List<Playlist>
    suspend fun searchAll(query: String, filterType: SearchFilterType): List<SearchResultItem>

    // Search History
    suspend fun addSearchHistoryItem(query: String)
    suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem>
    suspend fun deleteSearchHistoryItemByQuery(query: String)
    suspend fun clearSearchHistory()

    /**
     * Obtiene la lista de canciones para un género específico (placeholder implementation).
     * @param genreId El ID del género (e.g., "pop", "rock").
     * @return Lista de objetos Song (simulada para este género).
     */
    suspend fun getMusicByGenre(genreId: String): List<Song> // Added this line
}