package com.theveloper.pixelplay.data.repository

import android.net.Uri
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    /**
     * Obtiene la lista de archivos de audio (canciones) paginada y filtrada por directorios permitidos.
     * @param page El número de página (empezando en 1).
     * @param pageSize El número de elementos por página.
     * @return Flow que emite una lista de objetos Song para la página solicitada.
     */
    fun getAudioFiles(page: Int, pageSize: Int): Flow<List<Song>>

    /**
     * Obtiene la lista de álbumes paginada y filtrada.
     * @param page El número de página (empezando en 1).
     * @param pageSize El número de elementos por página.
     * @return Flow que emite una lista de objetos Album para la página solicitada.
     */
    fun getAlbums(page: Int, pageSize: Int): Flow<List<Album>>

    /**
     * Obtiene la lista de artistas paginada y filtrada.
     * @param page El número de página (empezando en 1).
     * @param pageSize El número de elementos por página.
     * @return Flow que emite una lista de objetos Artist para la página solicitada.
     */
    fun getArtists(page: Int, pageSize: Int): Flow<List<Artist>>

    /**
     * Obtiene la lista de canciones para un álbum específico (NO paginada para la cola de reproducción).
     * @param albumId El ID del álbum.
     * @return Flow que emite una lista de objetos Song pertenecientes al álbum.
     */
    fun getSongsForAlbum(albumId: Long): Flow<List<Song>>

    /**
     * Obtiene la lista de canciones para un artista específico (NO paginada para la cola de reproducción).
     * @param artistId El ID del artista.
     * @return Flow que emite una lista de objetos Song pertenecientes al artista.
     */
    fun getSongsForArtist(artistId: Long): Flow<List<Song>>

    /**
     * Obtiene una lista de canciones por sus IDs.
     * @param songIds Lista de IDs de canciones.
     * @return Flow que emite una lista de objetos Song correspondientes a los IDs, en el mismo orden.
     */
    fun getSongsByIds(songIds: List<String>): Flow<List<Song>>

    /**
     * Obtiene todos los directorios únicos que contienen archivos de audio.
     * Esto se usa principalmente para la configuración inicial de directorios.
     * También gestiona el guardado inicial de directorios permitidos si es la primera vez.
     * @return Conjunto de rutas de directorios únicas.
     */
    suspend fun getAllUniqueAudioDirectories(): Set<String>

    fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> // Nuevo para precarga de temas

    suspend fun invalidateCachesDependentOnAllowedDirectories() // Nuevo para precarga de temas

    fun searchSongs(query: String): Flow<List<Song>>
    fun searchAlbums(query: String): Flow<List<Album>>
    fun searchArtists(query: String): Flow<List<Artist>>
    suspend fun searchPlaylists(query: String): List<Playlist> // Mantener suspend, ya que no hay Flow aún
    fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>>

    // Search History
    suspend fun addSearchHistoryItem(query: String)
    suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem>
    suspend fun deleteSearchHistoryItemByQuery(query: String)
    suspend fun clearSearchHistory()

    /**
     * Obtiene la lista de canciones para un género específico (placeholder implementation).
     * @param genreId El ID del género (e.g., "pop", "rock").
     * @return Flow que emite una lista de objetos Song (simulada para este género).
     */
    fun getMusicByGenre(genreId: String): Flow<List<Song>> // Changed to Flow
}