package com.theveloper.pixelplay.data.repository

import android.net.Uri
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    /**
     * Obtiene la lista de archivos de audio (canciones) filtrada por directorios permitidos.
     * @return Flow que emite una lista completa de objetos Song.
     */
    fun getAudioFiles(): Flow<List<Song>> // Existing Flow for reactive updates

    /**
     * Obtiene la lista de álbumes filtrada.
     * @return Flow que emite una lista completa de objetos Album.
     */
    fun getAlbums(): Flow<List<Album>> // Existing Flow for reactive updates

    /**
     * Obtiene la lista de artistas filtrada.
     * @return Flow que emite una lista completa de objetos Artist.
     */
    fun getArtists(): Flow<List<Artist>> // Existing Flow for reactive updates

    // New suspend functions for one-shot data loading as per performance report
    /**
     * Obtiene la lista completa de álbumes una sola vez.
     * @return Lista de objetos Album.
     */
    suspend fun getAllAlbumsOnce(): List<Album>

    /**
     * Obtiene la lista completa de artistas una sola vez.
     * @return Lista de objetos Artist.
     */
    suspend fun getAllArtistsOnce(): List<Artist>

    /**
     * Obtiene un álbum específico por su ID.
     * @param id El ID del álbum.
     * @return Flow que emite el objeto Album o null si no se encuentra.
     */
    fun getAlbumById(id: Long): Flow<Album?>

    /**
     * Obtiene la lista de artistas filtrada.
     * @return Flow que emite una lista completa de objetos Artist.
     */
    //fun getArtists(): Flow<List<Artist>>

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

    /**
     * Cambia el estado de favorito de una canción.
     * @param songId El ID de la canción.
     * @return El nuevo estado de favorito (true si es favorito, false si no).
     */
    suspend fun toggleFavoriteStatus(songId: String): Boolean

    /**
     * Obtiene una canción específica por su ID.
     * @param songId El ID de la canción.
     * @return Flow que emite el objeto Song o null si no se encuentra.
     */
    fun getSong(songId: String): Flow<Song?>
    fun getArtistById(artistId: Long): Flow<Artist?>

    /**
     * Obtiene la lista de géneros, ya sea mockeados o leídos de los metadatos.
     * @return Flow que emite una lista de objetos Genre.
     */
    fun getGenres(): Flow<List<com.theveloper.pixelplay.data.model.Genre>>

    suspend fun getLyrics(song: Song): Lyrics?

    suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>>

    suspend fun updateLyrics(songId: Long, lyrics: String)
}