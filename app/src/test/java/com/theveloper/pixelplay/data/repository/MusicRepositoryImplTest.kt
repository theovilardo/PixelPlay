package com.theveloper.pixelplay.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SearchHistoryEntity
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class MusicRepositoryImplTest {

    private lateinit var musicRepository: MusicRepositoryImpl
    private val mockContentResolver: ContentResolver = mockk()
    private val mockSearchHistoryDao: SearchHistoryDao = mockk()
    private val mockContext: Context = mockk()
    private val mockUserPreferencesRepository: UserPreferencesRepository = mockk()

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Mock default behavior for preferences needed in various repository methods
        coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)
        coEvery { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(emptySet()) // Default to empty, can be overridden

        musicRepository = MusicRepositoryImpl(mockContext, mockUserPreferencesRepository, mockSearchHistoryDao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSongCursor(songs: List<Song>): MatrixCursor {
        val cursor = MatrixCursor(arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        ))
        songs.forEach { song ->
            cursor.addRow(arrayOf(
                song.id.toLong(),
                song.title,
                song.artist,
                song.artistId,
                song.album,
                song.albumId,
                song.duration,
                // Construct a fake path that would be allowed by default if allowedDirs is empty during setup
                "/storage/emulated/0/Music/${song.title}.mp3"
            ))
        }
        return cursor
    }
     private fun createAlbumCursor(albums: List<Album>): MatrixCursor {
        val cursor = MatrixCursor(arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS
        ))
        albums.forEach { album ->
            cursor.addRow(arrayOf(album.id, album.title, album.artist, album.songCount))
        }
        return cursor
    }

    private fun createArtistCursor(artists: List<Artist>): MatrixCursor {
        val cursor = MatrixCursor(arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        ))
        artists.forEach { artist ->
            cursor.addRow(arrayOf(artist.id, artist.name, artist.trackCount))
        }
        return cursor
    }


    @Nested
    @DisplayName("Search Functions")
    inner class SearchFunctions {
        @Test
        fun `test_searchSongs_returnsCorrectSongs`() = runTest {
            val query = "test"
            val expectedSongs = listOf(Song("1", "Test Song", "Artist", 10L, "Album", 20L, "uri", "arturi", 30000L))
            val songCursor = createSongCursor(expectedSongs)

            every { mockContext.contentResolver } returns mockContentResolver
            coEvery {
                mockContentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    any(), // projection
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.TITLE} LIKE ?", // selection
                    arrayOf("%$query%"), // selectionArgs
                    MediaStore.Audio.Media.TITLE + " ASC", // sortOrder
                    null // cancellationSignal
                )
            } returns songCursor
             coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(false) // Allow all directories for simplicity

            val result = musicRepository.searchSongs(query)
            assertEquals(expectedSongs.size, result.size)
            assertEquals(expectedSongs[0].title, result[0].title)
        }

        @Test
        fun `test_searchAlbums_returnsCorrectAlbums`() = runTest {
            val query = "test album"
            val expectedAlbums = listOf(Album(1L, "Test Album", "Artist", "uri", 1))
            val albumCursor = createAlbumCursor(expectedAlbums)

            every { mockContext.contentResolver } returns mockContentResolver
            coEvery {
                mockContentResolver.query(
                    MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                    any(), // projection
                    "${MediaStore.Audio.Albums.ALBUM} LIKE ?", // selection
                    arrayOf("%$query%"), // selectionArgs
                    "${MediaStore.Audio.Albums.ALBUM} ASC" // sortOrder
                )
            } returns albumCursor
            coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(false)


            val result = musicRepository.searchAlbums(query)
            assertEquals(expectedAlbums.size, result.size)
            assertEquals(expectedAlbums[0].title, result[0].title)
        }

        @Test
        fun `test_searchArtists_returnsCorrectArtists`() = runTest {
            val query = "test artist"
            val expectedArtists = listOf(Artist(1L, "Test Artist", 1))
            val artistCursor = createArtistCursor(expectedArtists)

            every { mockContext.contentResolver } returns mockContentResolver
            coEvery {
                mockContentResolver.query(
                    MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
                    any(), // projection
                    "${MediaStore.Audio.Artists.ARTIST} LIKE ?", // selection
                    arrayOf("%$query%"), // selectionArgs
                    "${MediaStore.Audio.Artists.ARTIST} ASC" // sortOrder
                )
            } returns artistCursor
             coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(false)

            val result = musicRepository.searchArtists(query)
            assertEquals(expectedArtists.size, result.size)
            assertEquals(expectedArtists[0].name, result[0].name)
        }


        @Test
        fun `test_searchPlaylists_whenNotImplemented_returnsEmptyList`() = runTest {
            val result = musicRepository.searchPlaylists("any query")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `test_searchAll_callsCorrectMethods_forFilterAll`() = runTest {
            val query = "all"
            coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(false) // simplify filtering for this test

            // Mock individual search methods (simplified cursor mocking for brevity)
            every { mockContext.contentResolver } returns mockContentResolver
            coEvery { mockContentResolver.query(eq(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI), any(), any(), any(), any(), any()) } returns createSongCursor(listOf(Song("1", "S1", "A",1L,"Al",1L,"","",0L)))
            coEvery { mockContentResolver.query(eq(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI), any(), any(), any(), any()) } returns createAlbumCursor(listOf(Album(1L, "AL1", "A", "uri",1)))
            coEvery { mockContentResolver.query(eq(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI), any(), any(), any(), any()) } returns createArtistCursor(listOf(Artist(1L, "AR1", 1)))

            val results = musicRepository.searchAll(query, SearchFilterType.ALL)
            assertEquals(3, results.size) // Song, Album, Artist
        }

        @Test
        fun `test_searchAll_callsCorrectMethods_forFilterSongs`() = runTest {
             coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(false)
            every { mockContext.contentResolver } returns mockContentResolver
            coEvery { mockContentResolver.query(eq(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI), any(), any(), any(), any(), any()) } returns createSongCursor(listOf(Song("1", "S1", "A",1L,"Al",1L,"","",0L)))

            val results = musicRepository.searchAll("song query", SearchFilterType.SONGS)
            assertEquals(1, results.size)
            assertTrue(results[0] is SearchResultItem.SongItem)
        }
         @Test
        fun `test_searchAll_callsCorrectMethods_forFilterAlbums`() = runTest {
            coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(false)
            every { mockContext.contentResolver } returns mockContentResolver
            coEvery { mockContentResolver.query(eq(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI), any(), any(), any(), any()) } returns createAlbumCursor(listOf(Album(1L, "AL1", "A","uri", 1)))

            val results = musicRepository.searchAll("album query", SearchFilterType.ALBUMS)
            assertEquals(1, results.size)
            assertTrue(results[0] is SearchResultItem.AlbumItem)
        }

        @Test
        fun `test_searchAll_callsCorrectMethods_forFilterArtists`() = runTest {
            coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(false)
            every { mockContext.contentResolver } returns mockContentResolver
            coEvery { mockContentResolver.query(eq(MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI), any(), any(), any(), any()) } returns createArtistCursor(listOf(Artist(1L, "AR1", 1)))

            val results = musicRepository.searchAll("artist query", SearchFilterType.ARTISTS)
            assertEquals(1, results.size)
            assertTrue(results[0] is SearchResultItem.ArtistItem)
        }
    }

    @Nested
    @DisplayName("Search History Functions")
    inner class SearchHistoryFunctions {
        @Test
        fun `test_addSearchHistoryItem_deletesAndInserts`() = runTest {
            val query = "history query"
            val entitySlot = slot<SearchHistoryEntity>()

            coEvery { mockSearchHistoryDao.deleteByQuery(query) } just runs
            coEvery { mockSearchHistoryDao.insert(capture(entitySlot)) } just runs

            musicRepository.addSearchHistoryItem(query)

            coVerify(exactly = 1) { mockSearchHistoryDao.deleteByQuery(query) }
            coVerify(exactly = 1) { mockSearchHistoryDao.insert(any()) }
            assertEquals(query, entitySlot.captured.query)
            assertTrue(entitySlot.captured.timestamp > 0)
        }

        @Test
        fun `test_getRecentSearchHistory_returnsMappedItems`() = runTest {
            val limit = 5
            val timestamp = System.currentTimeMillis()
            val entities = listOf(SearchHistoryEntity(1, "q1", timestamp), SearchHistoryEntity(2, "q2", timestamp - 1000))
            coEvery { mockSearchHistoryDao.getRecentSearches(limit) } returns entities

            val result = musicRepository.getRecentSearchHistory(limit)

            assertEquals(entities.size, result.size)
            assertEquals(entities[0].query, result[0].query)
            assertEquals(entities[0].timestamp, result[0].timestamp)
        }

        @Test
        fun `test_clearSearchHistory_callsDaoClearAll`() = runTest {
            coEvery { mockSearchHistoryDao.clearAll() } just runs
            musicRepository.clearSearchHistory()
            coVerify(exactly = 1) { mockSearchHistoryDao.clearAll() }
        }

        @Test
        fun `test_deleteSearchHistoryItemByQuery_callsDaoDeleteByQuery`() = runTest {
            val query = "delete this"
            coEvery { mockSearchHistoryDao.deleteByQuery(query) } just runs
            musicRepository.deleteSearchHistoryItemByQuery(query)
            coVerify(exactly = 1) { mockSearchHistoryDao.deleteByQuery(query) }
        }
    }
}
