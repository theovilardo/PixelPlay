package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- Insert Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Transaction
    suspend fun insertMusicData(songs: List<SongEntity>, albums: List<AlbumEntity>, artists: List<ArtistEntity>) {
        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
    }

    @Transaction
    suspend fun clearAllMusicData() {
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()
    }

    // --- Clear Operations ---
    @Query("DELETE FROM songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM albums")
    suspend fun clearAllAlbums()

    @Query("DELETE FROM artists")
    suspend fun clearAllArtists()

    // --- Song Queries ---
    // Updated getSongs to potentially filter by parent_directory_path
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getSongs(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    fun getSongById(songId: Long): Flow<SongEntity?>

    //@Query("SELECT * FROM songs WHERE id IN (:songIds)")
    @Query("""
        SELECT * FROM songs
        WHERE id IN (:songIds)
        AND (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getSongsByIds(
        songIds: List<Long>,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY title ASC")
    fun getSongsByAlbumId(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId ORDER BY title ASC")
    fun getSongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR artist_name LIKE '%' || :query || '%')
        ORDER BY title ASC
    """)
    fun searchSongs(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getAllSongs(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): Flow<List<SongEntity>>

    // --- Album Queries ---
    @Query("""
        SELECT DISTINCT albums.* FROM albums
        INNER JOIN songs ON albums.id = songs.album_id
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY albums.title ASC
    """)
    fun getAlbums(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    fun getAlbumById(albumId: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchAlbums(query: String): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums")
    fun getAlbumCount(): Flow<Int>

    // Version of getAlbums that returns a List for one-shot reads
    @Query("""
        SELECT DISTINCT albums.* FROM albums
        INNER JOIN songs ON albums.id = songs.album_id
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY albums.title ASC
    """)
    suspend fun getAllAlbumsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE artist_id = :artistId ORDER BY title ASC")
    fun getAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>>

    @Query("""
        SELECT DISTINCT albums.* FROM albums
        INNER JOIN songs ON albums.id = songs.album_id
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (albums.title LIKE '%' || :query || '%' OR albums.artist_name LIKE '%' || :query || '%')
        ORDER BY albums.title ASC
    """)
    fun searchAlbums(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AlbumEntity>>

    // --- Artist Queries ---
    @Query("""
        SELECT DISTINCT artists.* FROM artists
        INNER JOIN songs ON artists.id = songs.artist_id
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY artists.name ASC
    """)
    fun getArtists(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :artistId")
    fun getArtistById(artistId: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchArtists(query: String): Flow<List<ArtistEntity>>

    @Query("SELECT COUNT(*) FROM artists")
    fun getArtistCount(): Flow<Int>

    // Version of getArtists that returns a List for one-shot reads
    @Query("""
        SELECT DISTINCT artists.* FROM artists
        INNER JOIN songs ON artists.id = songs.artist_id
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY artists.name ASC
    """)
    suspend fun getAllArtistsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<ArtistEntity>

    @Query("""
        SELECT DISTINCT artists.* FROM artists
        INNER JOIN songs ON artists.id = songs.artist_id
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND artists.name LIKE '%' || :query || '%'
        ORDER BY artists.name ASC
    """)
    fun searchArtists(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<ArtistEntity>>

    // --- Genre Queries ---
    // Example: Get all songs for a specific genre
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND genre LIKE :genreName
        ORDER BY title ASC
    """)
    fun getSongsByGenre(
        genreName: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (genre IS NULL OR genre = '')
        ORDER BY title ASC
    """)
    fun getSongsWithNullGenre(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    // Example: Get all unique genre names
    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    fun getUniqueGenres(): Flow<List<String>>

    // --- Combined Queries (Potentially useful for more complex scenarios) ---
    // E.g., Get all album art URIs from songs (could be useful for theme preloading from SSoT)
    @Query("SELECT DISTINCT album_art_uri_string FROM songs WHERE album_art_uri_string IS NOT NULL")
    fun getAllUniqueAlbumArtUrisFromSongs(): Flow<List<String>>

    @Query("DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT album_id FROM songs)")
    suspend fun deleteOrphanedAlbums()

    @Query("DELETE FROM artists WHERE id NOT IN (SELECT DISTINCT artist_id FROM songs)")
    suspend fun deleteOrphanedArtists()

    // --- Favorite Operations ---
    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :songId")
    suspend fun setFavoriteStatus(songId: Long, isFavorite: Boolean)

    @Query("SELECT is_favorite FROM songs WHERE id = :songId")
    suspend fun getFavoriteStatus(songId: Long): Boolean?

    // Transaction to toggle favorite status
    @Transaction
    suspend fun toggleFavoriteStatus(songId: Long): Boolean {
        val currentStatus = getFavoriteStatus(songId) ?: false // Default to false if not found (should not happen for existing song)
        val newStatus = !currentStatus
        setFavoriteStatus(songId, newStatus)
        return newStatus
    }

    @Query("UPDATE songs SET title = :title, artist_name = :artist, album_name = :album, genre = :genre, lyrics = :lyrics, track_number = :trackNumber WHERE id = :songId")
    suspend fun updateSongMetadata(songId: Long, title: String, artist: String, album: String, genre: String, lyrics: String, trackNumber: Int)

    @Query("UPDATE songs SET album_art_uri_string = :albumArtUri WHERE id = :songId")
    suspend fun updateSongAlbumArt(songId: Long, albumArtUri: String?)

    @Query("UPDATE songs SET lyrics = :lyrics WHERE id = :songId")
    suspend fun updateLyrics(songId: Long, lyrics: String)

    @Query("UPDATE songs SET lyrics = NULL WHERE id = :songId")
    suspend fun resetLyrics(songId: Long)

    @Query("UPDATE songs SET lyrics = NULL")
    suspend fun resetAllLyrics()

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsList(): List<SongEntity>
}
