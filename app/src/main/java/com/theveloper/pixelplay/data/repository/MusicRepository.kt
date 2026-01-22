package com.theveloper.pixelplay.data.repository

import android.net.Uri
import androidx.paging.PagingData
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
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
     * Returns paginated songs for efficient display of large libraries.
     * @return Flow of PagingData<Song> for use with LazyPagingItems.
     */
    fun getPaginatedSongs(): Flow<PagingData<Song>>

    /**
     * Returns the count of songs in the library.
     * @return Flow emitting the current song count.
     */
    fun getSongCountFlow(): Flow<Int>

    /**
     * Returns a random selection of songs for efficient shuffle.
     * Uses database-level RANDOM() for performance.
     * @param limit Maximum number of songs to return.
     * @return List of randomly selected songs.
     */
    suspend fun getRandomSongs(limit: Int): List<Song>

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

    /**
     * Obtiene la lista completa de canciones una sola vez.
     * @return Lista de objetos Song.
     */
    suspend fun getAllSongsOnce(): List<Song>

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
     * Obtiene una canción por su ruta de archivo.
     * @param path Ruta del archivo.
     * @return El objeto Song o null si no se encuentra.
     */
    suspend fun getSongByPath(path: String): Song?

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
    fun getArtistsForSong(songId: Long): Flow<List<Artist>>

    /**
     * Obtiene la lista de géneros, ya sea mockeados o leídos de los metadatos.
     * @return Flow que emite una lista de objetos Genre.
     */
    fun getGenres(): Flow<List<com.theveloper.pixelplay.data.model.Genre>>

    suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
        forceRefresh: Boolean = false
    ): Lyrics?

    suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>>

    /**
     * Search for lyrics remotely, less specific than `getLyricsFromRemote` but more lenient
     * @param song The song to search lyrics for
     * @return The search query and the results
     */
    suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>>

    /**
     * Search for lyrics remotely using query provided, and not use song metadata
     * @param query The query for searching, typically song title and artist name
     * @return The search query and the results
     */
    suspend fun searchRemoteLyricsByQuery(title: String, artist: String? = null): Result<Pair<String, List<LyricsSearchResult>>>

    suspend fun updateLyrics(songId: Long, lyrics: String)

    suspend fun resetLyrics(songId: Long)

    suspend fun resetAllLyrics()

    fun getMusicFolders(): Flow<List<com.theveloper.pixelplay.data.model.MusicFolder>>

    suspend fun deleteById(id: Long)
}