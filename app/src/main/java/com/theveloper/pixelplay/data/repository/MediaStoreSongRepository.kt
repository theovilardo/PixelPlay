package com.theveloper.pixelplay.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.observer.MediaStoreObserver
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.DirectoryRuleResolver
import com.theveloper.pixelplay.utils.LogUtils
import com.theveloper.pixelplay.utils.normalizeMetadataText
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreSongRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreObserver: MediaStoreObserver,
    private val favoritesDao: FavoritesDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : SongRepository {

    init {
        mediaStoreObserver.register()
    }

    private fun getBaseSelection(): String {
        // Relaxed filter: Remove IS_MUSIC to include all audio strings (WhatsApp, Recs, etc.)
        // We filter by duration to skip extremely short clips (likely UI sounds).
        return "${MediaStore.Audio.Media.DURATION} >= 30000 AND ${MediaStore.Audio.Media.TITLE} != ''"
    }

    private suspend fun getFavoriteIds(): Set<Long> {
        return favoritesDao.getFavoriteSongIdsOnce().toSet()
    }

    private fun normalizePath(path: String): String = File(path).absolutePath

    private fun getExcludedPaths(): Set<String> {
        // This should come from a repository/store, not blocking flow preferably, 
        // but for query implementation we'll need to filter the cursor results.
        // For now, we will assume strict filtering logic inside mapCursorToSongs
        return emptySet() 
    }

    override fun getSongs(): Flow<List<Song>> = combine(
        mediaStoreObserver.mediaStoreChanges.onStart { emit(Unit) },
        favoritesDao.getFavoriteSongIds(),
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { _, favoriteIds, allowedDirs, blockedDirs ->
        // Triggered by mediaStore change or favorites change or directory config change
        fetchSongsFromMediaStore(favoriteIds.toSet(), allowedDirs.toList(), blockedDirs.toList())
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchSongsFromMediaStore(
        favoriteIds: Set<Long>,
        allowedDirs: List<String>,
        blockedDirs: List<String>
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.ALBUM_ARTIST, // Valid on API 30+, fallback needed if minSdk < 30
            // Genre is difficult in MediaStore.Audio.Media, usually requires separate query.
            // keeping it simple for now, maybe null or fetch separately.
        )
        
        // Handling API version differences for columns if necessary
        // Assuming minSdk is high enough or columns exist (ALBUM_ARTIST is API 30+, need check if app supports lower)
        
        val selection = getBaseSelection()

        val songIdToGenreMap = getSongIdToGenreMap(context.contentResolver)

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) // Can be -1

                val resolver = DirectoryRuleResolver(
                    allowedDirs.map(::normalizePath).toSet(),
                    blockedDirs.map(::normalizePath).toSet() 
                )
                val isFilterActive = blockedDirs.isNotEmpty()

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol)
                    
                    // Directory Filtering
                    if (isFilterActive) {
                        val lastSlashIndex = path.lastIndexOf('/')
                        val parentPath = if (lastSlashIndex != -1) path.substring(0, lastSlashIndex) else ""
                        if (resolver.isBlocked(parentPath)) {
                            continue
                        }
                    }

                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    
                    val song = Song(
                        id = id.toString(),
                        title = cursor.getString(titleCol).normalizeMetadataTextOrEmpty(),
                        artist = cursor.getString(artistCol).normalizeMetadataTextOrEmpty(),
                        artistId = cursor.getLong(artistIdCol),
                        artists = emptyList(), // TODO: Secondary query for Multi-Artist or split string
                        album = cursor.getString(albumCol).normalizeMetadataTextOrEmpty(),
                        albumId = albumId,
                        albumArtist = if (albumArtistCol != -1) cursor.getString(albumArtistCol).normalizeMetadataText() else null,
                        path = path,
                        contentUriString = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                        albumArtUriString = ContentUris.withAppendedId(
                            android.net.Uri.parse("content://media/external/audio/albumart"),
                            albumId
                        ).toString(),
                        duration = cursor.getLong(durationCol),
                        genre = songIdToGenreMap[id],
                        lyrics = null,
                        isFavorite = favoriteIds.contains(id),
                        trackNumber = cursor.getInt(trackCol),
                        year = cursor.getInt(yearCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModifiedCol),
                        mimeType = null, 
                        bitrate = null,
                        sampleRate = null
                    )
                    songs.add(song)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreSongRepository", "Error querying MediaStore", e)
        }
        songs
    }

    private fun getSongIdToGenreMap(contentResolver: android.content.ContentResolver): Map<Long, String> {
        val genreMap = mutableMapOf<Long, String>()
        try {
            val genresUri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            val genresProjection = arrayOf(
                MediaStore.Audio.Genres._ID,
                MediaStore.Audio.Genres.NAME
            )
            
            contentResolver.query(genresUri, genresProjection, null, null, null)?.use { genreCursor ->
                val genreIdCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val genreNameCol = genreCursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                
                while (genreCursor.moveToNext()) {
                    val genreId = genreCursor.getLong(genreIdCol)
                    val genreName = genreCursor.getString(genreNameCol).normalizeMetadataTextOrEmpty()
                    
                    if (genreName.isNotBlank() && genreName != "<unknown>") {
                        val membersUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                        val membersProjection = arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID)
                        
                        try {
                            contentResolver.query(membersUri, membersProjection, null, null, null)?.use { membersCursor ->
                                val audioIdCol = membersCursor.getColumnIndex(MediaStore.Audio.Genres.Members.AUDIO_ID)
                                if (audioIdCol != -1) {
                                    while (membersCursor.moveToNext()) {
                                        val songId = membersCursor.getLong(audioIdCol)
                                        // If a song has multiple genres, this simple map keeps the last one found.
                                        // Could be improved to join them if needed.
                                        genreMap[songId] = genreName 
                                    }
                                }
                            }
                        } catch (e: Exception) {
                             Log.w("MediaStoreSongRepository", "Error querying members for genreId=$genreId", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreSongRepository", "Error querying Genres", e)
        }
        return genreMap
    }

    override fun getSongsByAlbum(albumId: Long): Flow<List<Song>> {
         // Reusing getSongs() and filtering might be inefficient for one album, 
         // but consistent with the reactive source of truth.
         // Optimization: Create specific query flow if needed.
         return getSongs().flowOn(Dispatchers.IO).combine(kotlinx.coroutines.flow.flowOf(albumId)) { songs, id ->
             songs.filter { it.albumId == id }
         }
    }

    override fun getSongsByArtist(artistId: Long): Flow<List<Song>> {
        return getSongs().flowOn(Dispatchers.IO).combine(kotlinx.coroutines.flow.flowOf(artistId)) { songs, id ->
            songs.filter { it.artistId == id }
        }
    }

    override suspend fun searchSongs(query: String): List<Song> {
        val allSongs = getSongs().first() // Snapshot
        return allSongs.filter { 
            it.title.contains(query, true) || it.artist.contains(query, true) 
        }
    }

    override fun getSongById(songId: Long): Flow<Song?> {
        return getSongs().flowOn(Dispatchers.IO).combine(kotlinx.coroutines.flow.flowOf(songId)) { songs, id ->
            songs.find { it.id == id.toString() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(): Flow<androidx.paging.PagingData<Song>> {
        return combine(
            mediaStoreObserver.mediaStoreChanges.onStart { emit(Unit) },
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { _, allowedDirs, blockedDirs ->
            Triple(allowedDirs, blockedDirs, Unit)
        }.flatMapLatest { (allowedDirs, blockedDirs, _) ->
             val musicIds = getFilteredSongIds(allowedDirs.toList(), blockedDirs.toList())
             val genreMap = getSongIdToGenreMap(context.contentResolver) // Potentially expensive, optimize if needed
             
             androidx.paging.Pager(
                 config = androidx.paging.PagingConfig(
                     pageSize = 50,
                     enablePlaceholders = true,
                     initialLoadSize = 50
                 ),
                 pagingSourceFactory = {
                     com.theveloper.pixelplay.data.paging.MediaStorePagingSource(context, musicIds, genreMap)
                 }
             ).flow
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun getFilteredSongIds(allowedDirs: List<String>, blockedDirs: List<String>): List<Long> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val selection = getBaseSelection()
        
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                
                val resolver = DirectoryRuleResolver(
                    allowedDirs.map(::normalizePath).toSet(),
                    blockedDirs.map(::normalizePath).toSet()
                )
                val isFilterActive = blockedDirs.isNotEmpty()

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol)
                    if (isFilterActive) {
                    if (isFilterActive) {
                        val lastSlashIndex = path.lastIndexOf('/')
                        val parentPath = if (lastSlashIndex != -1) path.substring(0, lastSlashIndex) else ""
                        if (resolver.isBlocked(parentPath)) {
                            continue
                        }
                    }
                    }
                    ids.add(cursor.getLong(idCol))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreSongRepository", "Error getting IDs", e)
        }
        ids
    }
}
