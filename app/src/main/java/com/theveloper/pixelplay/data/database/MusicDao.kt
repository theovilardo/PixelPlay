package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
        // Clear old data first to ensure consistency, especially if sync is destructive.
        // Alternatively, handle updates more granularly if needed.
        // For this phase, a full clear and re-insert is simpler for the initial sync.
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()

        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
    }

    // --- Clear Operations ---
    @Query("DELETE FROM songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM albums")
    suspend fun clearAllAlbums()

    @Query("DELETE FROM artists")
    suspend fun clearAllArtists()

    // --- Song Queries ---
    @Query("SELECT * FROM songs ORDER BY title ASC LIMIT :pageSize OFFSET :offset")
    fun getSongs(pageSize: Int, offset: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    fun getSongById(songId: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    fun getSongsByIds(songIds: List<Long>): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY title ASC")
    fun getSongsByAlbumId(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId ORDER BY title ASC")
    fun getSongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    // --- Album Queries ---
    @Query("SELECT * FROM albums ORDER BY title ASC LIMIT :pageSize OFFSET :offset")
    fun getAlbums(pageSize: Int, offset: Int): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId")
    fun getAlbumById(albumId: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchAlbums(query: String): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums")
    fun getAlbumCount(): Flow<Int>

    @Query("SELECT * FROM albums WHERE artist_id = :artistId ORDER BY title ASC")
    fun getAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>>


    // --- Artist Queries ---
    @Query("SELECT * FROM artists ORDER BY name ASC LIMIT :pageSize OFFSET :offset")
    fun getArtists(pageSize: Int, offset: Int): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :artistId")
    fun getArtistById(artistId: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchArtists(query: String): Flow<List<ArtistEntity>>

    @Query("SELECT COUNT(*) FROM artists")
    fun getArtistCount(): Flow<Int>

    // --- Genre Queries ---
    // Example: Get all songs for a specific genre
    @Query("SELECT * FROM songs WHERE genre LIKE :genreName ORDER BY title ASC")
    fun getSongsByGenre(genreName: String): Flow<List<SongEntity>>

    // Example: Get all unique genre names
    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    fun getUniqueGenres(): Flow<List<String>>

    // --- Combined Queries (Potentially useful for more complex scenarios) ---
    // E.g., Get all album art URIs from songs (could be useful for theme preloading from SSoT)
    @Query("SELECT DISTINCT album_art_uri_string FROM songs WHERE album_art_uri_string IS NOT NULL")
    fun getAllUniqueAlbumArtUrisFromSongs(): Flow<List<String>>
}
