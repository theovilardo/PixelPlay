package com.theveloper.pixelplay.data.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.theveloper.pixelplay.utils.AudioMeta
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

    // --- Incremental Sync Operations ---
    @Query("SELECT id FROM songs")
    suspend fun getAllSongIds(): List<Long>

    @Query("DELETE FROM songs WHERE id IN (:songIds)")
    suspend fun deleteSongsByIds(songIds: List<Long>)

    @Query("DELETE FROM song_artist_cross_ref WHERE song_id IN (:songIds)")
    suspend fun deleteCrossRefsBySongIds(songIds: List<Long>)

    /**
     * Incrementally sync music data: upsert new/modified songs and remove deleted ones.
     * More efficient than clear-and-replace for large libraries with few changes.
     */
    @Transaction
    suspend fun incrementalSyncMusicData(
        songs: List<SongEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>,
        deletedSongIds: List<Long>
    ) {
        // Delete removed songs and their cross-refs
        if (deletedSongIds.isNotEmpty()) {
            deletedSongIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
                deleteCrossRefsBySongIds(chunk)
                deleteSongsByIds(chunk)
            }
        }
        
        // Upsert artists, albums, and songs (REPLACE strategy handles updates)
        insertArtists(artists)
        insertAlbums(albums)
        
        // Insert songs in chunks to allow concurrent reads
        songs.chunked(SONG_BATCH_SIZE).forEach { chunk ->
            insertSongs(chunk)
        }
        
        // Delete old cross-refs for updated songs and insert new ones
        val updatedSongIds = songs.map { it.id }
        updatedSongIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            deleteCrossRefsBySongIds(chunk)
        }
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertSongArtistCrossRefs(chunk)
        }
        
        // Clean up orphaned albums and artists
        deleteOrphanedAlbums()
        deleteOrphanedArtists()
    }

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

    @Query("SELECT * FROM songs WHERE file_path = :path LIMIT 1")
    suspend fun getSongByPath(path: String): SongEntity?

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

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCountOnce(): Int

    /**
     * Returns random songs for efficient shuffle without loading all songs into memory.
     * Uses SQLite RANDOM() for true randomness.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomSongs(
        limit: Int,
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): List<SongEntity>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getAllSongs(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): Flow<List<SongEntity>>
    
    // --- Paginated Queries for Large Libraries ---
    /**
     * Returns a PagingSource for songs, enabling efficient pagination for large libraries.
     * Room auto-generates the PagingSource implementation.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getSongsPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): PagingSource<Int, SongEntity>

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

    /**
     * Unfiltered list of all artists (including those only reachable via cross-refs).
     */
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtistsRaw(): Flow<List<ArtistEntity>>

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

    /**
     * Unfiltered list of all artists (one-shot).
     */
    @Query("SELECT * FROM artists ORDER BY name ASC")
    suspend fun getAllArtistsListRaw(): List<ArtistEntity>

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

    // --- Artist Image Operations ---
    @Query("SELECT image_url FROM artists WHERE id = :artistId")
    suspend fun getArtistImageUrl(artistId: Long): String?

    @Query("UPDATE artists SET image_url = :imageUrl WHERE id = :artistId")
    suspend fun updateArtistImageUrl(artistId: Long, imageUrl: String)

    @Query("SELECT id FROM artists WHERE name = :name LIMIT 1")
    suspend fun getArtistIdByName(name: String): Long?

    @Query("SELECT MAX(id) FROM artists")
    suspend fun getMaxArtistId(): Long?

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
    suspend fun updateSongMetadata(
        songId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        lyrics: String?,
        trackNumber: Int
    )

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

    @Query("SELECT album_art_uri_string FROM songs WHERE id=:id")
    suspend fun getAlbumArtUriById(id: Long) : String?

    @Query("DELETE FROM songs WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("""
    SELECT mime_type AS mimeType,
           bitrate,
           sample_rate AS sampleRate
    FROM songs
    WHERE id = :id
    """)
    suspend fun getAudioMetadataById(id: Long): AudioMeta?

    // ===== Song-Artist Cross Reference (Junction Table) Operations =====

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongArtistCrossRefs(crossRefs: List<SongArtistCrossRef>)

    @Query("SELECT * FROM song_artist_cross_ref")
    fun getAllSongArtistCrossRefs(): Flow<List<SongArtistCrossRef>>

    @Query("SELECT * FROM song_artist_cross_ref")
    suspend fun getAllSongArtistCrossRefsList(): List<SongArtistCrossRef>

    @Query("DELETE FROM song_artist_cross_ref")
    suspend fun clearAllSongArtistCrossRefs()

    @Query("DELETE FROM song_artist_cross_ref WHERE song_id = :songId")
    suspend fun deleteCrossRefsForSong(songId: Long)

    @Query("DELETE FROM song_artist_cross_ref WHERE artist_id = :artistId")
    suspend fun deleteCrossRefsForArtist(artistId: Long)

    /**
     * Get all artists for a specific song using the junction table.
     */
    @Query("""
        SELECT artists.* FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId
        ORDER BY song_artist_cross_ref.is_primary DESC, artists.name ASC
    """)
    fun getArtistsForSong(songId: Long): Flow<List<ArtistEntity>>

    /**
     * Get all artists for a specific song (one-shot).
     */
    @Query("""
        SELECT artists.* FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId
        ORDER BY song_artist_cross_ref.is_primary DESC, artists.name ASC
    """)
    suspend fun getArtistsForSongList(songId: Long): List<ArtistEntity>

    /**
     * Get all songs for a specific artist using the junction table.
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN song_artist_cross_ref ON songs.id = song_artist_cross_ref.song_id
        WHERE song_artist_cross_ref.artist_id = :artistId
        ORDER BY songs.title ASC
    """)
    fun getSongsForArtist(artistId: Long): Flow<List<SongEntity>>

    /**
     * Get all songs for a specific artist (one-shot).
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN song_artist_cross_ref ON songs.id = song_artist_cross_ref.song_id
        WHERE song_artist_cross_ref.artist_id = :artistId
        ORDER BY songs.title ASC
    """)
    suspend fun getSongsForArtistList(artistId: Long): List<SongEntity>

    /**
     * Get the cross-references for a specific song.
     */
    @Query("SELECT * FROM song_artist_cross_ref WHERE song_id = :songId")
    suspend fun getCrossRefsForSong(songId: Long): List<SongArtistCrossRef>

    /**
     * Get the primary artist for a song.
     */
    @Query("""
        SELECT artists.id AS artist_id, artists.name FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId AND song_artist_cross_ref.is_primary = 1
        LIMIT 1
    """)
    suspend fun getPrimaryArtistForSong(songId: Long): PrimaryArtistInfo?

    /**
     * Get song count for an artist from the junction table.
     */
    @Query("SELECT COUNT(*) FROM song_artist_cross_ref WHERE artist_id = :artistId")
    suspend fun getSongCountForArtist(artistId: Long): Int

    /**
     * Get all artists with their song counts computed from the junction table.
     */
    @Query("""
        SELECT artists.id, artists.name, artists.image_url,
               (SELECT COUNT(*) FROM song_artist_cross_ref WHERE song_artist_cross_ref.artist_id = artists.id) AS track_count
        FROM artists
        ORDER BY artists.name ASC
    """)
    fun getArtistsWithSongCounts(): Flow<List<ArtistEntity>>

    /**
     * Get all artists with song counts, filtered by allowed directories.
     */
    @Query("""
        SELECT DISTINCT artists.id, artists.name, artists.image_url,
               (SELECT COUNT(*) FROM song_artist_cross_ref 
                INNER JOIN songs ON song_artist_cross_ref.song_id = songs.id
                WHERE song_artist_cross_ref.artist_id = artists.id
                AND (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))) AS track_count
        FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        INNER JOIN songs ON song_artist_cross_ref.song_id = songs.id
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY artists.name ASC
    """)
    fun getArtistsWithSongCountsFiltered(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<ArtistEntity>>

    /**
     * Clear all music data including cross-references.
     */
    @Transaction
    suspend fun clearAllMusicDataWithCrossRefs() {
        clearAllSongArtistCrossRefs()
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()
    }

    /**
     * Insert music data with cross-references in a single transaction.
     * Uses chunked inserts for cross-refs to avoid SQLite variable limits.
     */
    @Transaction
    suspend fun insertMusicDataWithCrossRefs(
        songs: List<SongEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>
    ) {
        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
        // Insert cross-refs in chunks to avoid SQLite variable limit.
        // Each SongArtistCrossRef has 3 fields, so batch size is calculated accordingly.
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertSongArtistCrossRefs(chunk)
        }
    }

    companion object {
        /**
         * SQLite has a limit on the number of variables per statement (default 999, higher in newer versions).
         * Each SongArtistCrossRef insert uses 3 variables (songId, artistId, isPrimary).
         * The batch size is calculated so that batchSize * 3 <= SQLITE_MAX_VARIABLE_NUMBER.
         */
        private const val SQLITE_MAX_VARIABLE_NUMBER = 999 // Increase if you know your SQLite version supports more
        private const val CROSS_REF_FIELDS_PER_OBJECT = 3
        val CROSS_REF_BATCH_SIZE: Int = SQLITE_MAX_VARIABLE_NUMBER / CROSS_REF_FIELDS_PER_OBJECT
        
        /**
         * Batch size for song inserts during incremental sync.
         * Allows database reads to interleave with writes for better UX.
         */
        const val SONG_BATCH_SIZE = 500
    }
}
