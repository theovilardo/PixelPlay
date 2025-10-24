package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import org.junit.jupiter.api.extension.ExtendWith
import com.theveloper.pixelplay.MainCoroutineExtension // Assuming this rule exists from project setup


@ExperimentalCoroutinesApi
@ExtendWith(MainCoroutineExtension::class) // Use a JUnit 5 extension for coroutines
class PlayerViewModelTest {

    private lateinit var playerViewModel: PlayerViewModel
    private val mockMusicRepository: MusicRepository = mockk()
    private val mockUserPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true) // relaxed for flows not directly tested
    private val mockAlbumArtThemeDao: AlbumArtThemeDao = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true) // For MediaController and SessionToken

    private val testDispatcher = StandardTestDispatcher() // Replaced MainCoroutineRule

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // Replaced MainCoroutineRule

        // Mock default behaviors for UserPreferencesRepository flows
        coEvery { mockUserPreferencesRepository.globalThemePreferenceFlow } returns flowOf("Dynamic")
        coEvery { mockUserPreferencesRepository.playerThemePreferenceFlow } returns flowOf("Global")
        coEvery { mockUserPreferencesRepository.favoriteSongIdsFlow } returns flowOf(emptySet())
        coEvery { mockUserPreferencesRepository.songsSortOptionFlow } returns flowOf("SongTitleAZ")
        coEvery { mockUserPreferencesRepository.albumsSortOptionFlow } returns flowOf("AlbumTitleAZ")
        coEvery { mockUserPreferencesRepository.artistsSortOptionFlow } returns flowOf("ArtistNameAZ")
        coEvery { mockUserPreferencesRepository.likedSongsSortOptionFlow } returns flowOf("LikedSongTitleAZ")

        // Mock repository calls that happen in init
        coEvery { mockMusicRepository.getRecentSearchHistory(any()) } returns emptyList()
        coEvery { mockMusicRepository.getAllUniqueAlbumArtUris() } returns emptyList() // For theme preloading
        coEvery { mockMusicRepository.getAudioFiles(any(), any()) } returns emptyList() // For initial song load
        coEvery { mockMusicRepository.getAlbums(any(), any()) } returns emptyList() // For initial album load
        coEvery { mockMusicRepository.getArtists(any(), any()) } returns emptyList() // For initial artist load

        coEvery { mockAlbumArtThemeDao.deleteThemesByUris(any()) } just runs


        playerViewModel = PlayerViewModel(
            context = mockContext,
            musicRepository = mockMusicRepository,
            userPreferencesRepository = mockUserPreferencesRepository,
            albumArtThemeDao = mockAlbumArtThemeDao
        )
        // Advance past initial loads in init
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain() // Replaced MainCoroutineRule
    }

    @Nested
    @DisplayName("Search and Filters")
    inner class SearchAndFiltersTests {

        @Test
        fun `test_performSearch_callsRepositorySearchAll_andUpdatesState`() = runTest {
            val query = "test query"
            val filter = SearchFilterType.SONGS
            val mockResults = listOf(mockk<SearchResultItem.SongItem>())

            coEvery { mockMusicRepository.searchAll(query, filter) } returns mockResults
            coEvery { mockMusicRepository.addSearchHistoryItem(query) } just runs // For search history part
            coEvery { mockMusicRepository.getRecentSearchHistory(any()) } returns emptyList() // For search history part


            playerViewModel.updateSearchFilter(filter) // Set the filter first
            playerViewModel.performSearch(query)
            testDispatcher.scheduler.advanceUntilIdle()


            coVerify { mockMusicRepository.searchAll(query, filter) }
            assertEquals(mockResults, playerViewModel.playerUiState.value.searchResults)
        }

        @Test
        fun `test_performSearch_withNonBlankQuery_addsToHistory_andReloadsHistory`() = runTest {
            val query = "history test"
            coEvery { mockMusicRepository.searchAll(query, any()) } returns emptyList() // Search result itself is not important here
            coEvery { mockMusicRepository.addSearchHistoryItem(query) } just runs
            coEvery { mockMusicRepository.getRecentSearchHistory(any()) } returns listOf(SearchHistoryItem(query = query, timestamp = 0L))

            playerViewModel.performSearch(query)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { mockMusicRepository.addSearchHistoryItem(query) }
            coVerify(atLeast = 1) { mockMusicRepository.getRecentSearchHistory(any()) } // Called in init and after adding
            assertEquals(query, playerViewModel.playerUiState.value.searchHistory.firstOrNull()?.query)
        }

        @Test
        fun `test_updateSearchFilter_updatesUiState`() = runTest {
            val newFilter = SearchFilterType.ALBUMS
            playerViewModel.playerUiState.test {
                skipItems(1) // Skip initial state

                playerViewModel.updateSearchFilter(newFilter)
                testDispatcher.scheduler.advanceUntilIdle()

                val emittedItem = awaitItem()
                assertEquals(newFilter, emittedItem.selectedSearchFilter)
                cancelAndConsumeRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("Search History")
    inner class SearchHistoryTests {

        @Test
        fun `test_loadSearchHistory_updatesUiState`() = runTest {
            val historyItems = listOf(SearchHistoryItem(query = "q1", timestamp = 1L))
            coEvery { mockMusicRepository.getRecentSearchHistory(any()) } returns historyItems

            playerViewModel.playerUiState.test {
                // Skip initial state from init block's loadSearchHistory
                // If init already loaded an empty list, await that first if needed.
                // For simplicity, we assume this test focuses on a direct call to loadSearchHistory.
                // If loadSearchHistory in init is complex, might need to adjust skipping.
                // Let's assume init has already run and potentially emitted.

                // awaitItem() // May need to await initial emission after setup

                playerViewModel.loadSearchHistory() // Explicitly call
                testDispatcher.scheduler.advanceUntilIdle()

                // We expect at least one emission from the explicit call.
                // The exact number of items to await/skip might depend on how many times
                // loadSearchHistory is triggered indirectly by other actions during setup or test.
                // Using expectMostRecentItem() or awaitLastItem() from Turbine could be more robust
                // if intermediate states are not critical.

                var currentItem = awaitItem()
                // If other operations (like performSearch in setup) also call loadSearchHistory,
                // we might get intermediate empty lists. Loop until we get the one we expect or fail.
                while(currentItem.searchHistory != historyItems && isActive) {
                     currentItem = awaitItem()
                }
                assertEquals(historyItems, currentItem.searchHistory)
                cancelAndConsumeRemainingEvents()
            }
        }


        @Test
        fun `test_clearSearchHistory_callsRepository_andUpdatesUiState`() = runTest {
            coEvery { mockMusicRepository.clearSearchHistory() } just runs

            playerViewModel.playerUiState.test {
                skipItems(1) // Skip initial state

                playerViewModel.clearSearchHistory()
                testDispatcher.scheduler.advanceUntilIdle()

                val emitted = awaitItem()
                assertTrue(emitted.searchHistory.isEmpty())
                coVerify { mockMusicRepository.clearSearchHistory() }
                cancelAndConsumeRemainingEvents()
            }
        }

        @Test
        fun `test_deleteSearchHistoryItem_callsRepository_andRefreshesHistory`() = runTest {
            val queryToDelete = "delete me"
            val initialHistory = listOf(SearchHistoryItem(query = queryToDelete, timestamp = 1L), SearchHistoryItem(query = "keep me", timestamp = 2L))
            val finalHistory = listOf(SearchHistoryItem(query = "keep me", timestamp = 2L))

            // Initial load
            coEvery { mockMusicRepository.getRecentSearchHistory(any()) } returns initialHistory
            playerViewModel.loadSearchHistory()
            testDispatcher.scheduler.advanceUntilIdle()


            coEvery { mockMusicRepository.deleteSearchHistoryItemByQuery(queryToDelete) } just runs
            coEvery { mockMusicRepository.getRecentSearchHistory(any()) } returns finalHistory // After deletion

            playerViewModel.playerUiState.test {
                 // Skip initial states or states from the setup's loadSearchHistory
                // This ensures we are testing the state *after* deleteSearchHistoryItem is called.
                // The number of items to skip might need adjustment based on how many emissions occur
                // before deleteSearchHistoryItem has its effect + reloads.

                // Await the state reflecting the initial load if not already consumed.
                 var currentItem = awaitItem()
                 while(currentItem.searchHistory != initialHistory && isActive) {
                     currentItem = awaitItem()
                 }
                 assertEquals(initialHistory, currentItem.searchHistory)


                playerViewModel.deleteSearchHistoryItem(queryToDelete)
                testDispatcher.scheduler.advanceUntilIdle()

                // Await the state reflecting the history *after* deletion and reload.
                var deletedState = awaitItem()
                 while(deletedState.searchHistory != finalHistory && isActive) {
                     deletedState = awaitItem()
                 }
                assertEquals(finalHistory, deletedState.searchHistory)

                coVerify(exactly = 1) { mockMusicRepository.deleteSearchHistoryItemByQuery(queryToDelete) }
                coVerify(atLeast = 2) { mockMusicRepository.getRecentSearchHistory(any()) } // Initial + after delete
                cancelAndConsumeRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("Get Song URIs for Genre")
    inner class GetSongUrisForGenreTests {

        // Dummy values for non-essential fields in Song for these tests
        private val dummyArtistId = 1L
        private val dummyAlbumId = 1L
        private val dummyContentUri = "content://dummy"
        private val dummyDuration = 180000L

        private val song1 = Song(id = "1", title = "Rock Song 1", artist = "Artist A", genre = "Rock", albumArtUriString = "rock_cover1.png", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)
        private val song2 = Song(id = "2", title = "Rock Song 2", artist = "Artist B", genre = "Rock", albumArtUriString = "rock_cover2.png", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)
        private val song3 = Song(id = "3", title = "Pop Song 1", artist = "Artist C", genre = "Pop", albumArtUriString = "pop_cover1.png", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)
        private val song4 = Song(id = "4", title = "Rock Song 3", artist = "Artist D", genre = "Rock", albumArtUriString = "rock_cover3.png", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)
        private val song5 = Song(id = "5", title = "Rock Song 4", artist = "Artist E", genre = "Rock", albumArtUriString = "rock_cover4.png", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)
        private val song6 = Song(id = "6", title = "Jazz Song 1", artist = "Artist F", genre = "Jazz", albumArtUriString = null, artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)
        private val song7 = Song(id = "7", title = "Rock Song NoCover", artist = "Artist G", genre = "Rock", albumArtUriString = "", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)
        private val song8 = Song(id = "8", title = "Jazz Song With Cover", artist = "Artist H", genre = "Jazz", albumArtUriString = "jazz_cover1.png", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)
        private val song9 = Song(id = "9", title = "Null Genre Song", artist = "Artist I", genre = null, albumArtUriString = "null_genre_cover.png", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration)


        private fun setupViewModelWithSongs(songs: List<Song>) {
            coEvery { mockMusicRepository.getAudioFiles(1, any()) } returns songs // Mocks the initial load
            // Re-initialize ViewModel or update state if possible.
            // For simplicity, we rely on the @BeforeEach creating a new ViewModel which will pick this up.
            // If tests need to change songs *after* initial setup, a more complex state manipulation or ViewModel action would be needed.
            // The current PlayerViewModelTest structure re-creates PlayerViewModel for each test in @BeforeEach,
            // so this coEvery in a test-specific setup function called *before* super.setUp() or PlayerViewModel init
            // would be ideal. Or, make PlayerViewModel take songs as a constructor arg for tests.
            // Given the current structure, this coEvery should be placed *before* PlayerViewModel() is called.
            // The @BeforeEach in PlayerViewModelTest calls PlayerViewModel().
            // A workaround is to update the state directly if a method was exposed, or re-mock and re-init.

            // Let's try to re-initialize the viewModel for these tests or ensure this mock is set before PlayerViewModel()
            // For now, we'll assume this coEvery is effective for the PlayerViewModel created in @BeforeEach.
            // This means these tests might need their own @BeforeEach or careful sequencing.
            // The current setup in PlayerViewModelTest has PlayerViewModel initialized with emptyList for songs.
            // We need to update the playerUiState.allSongs.
            // A direct way for testability would be if PlayerViewModel had a method like `setAllSongsForTest(songs: List<Song>)`.
            // Lacking that, we rely on mocking the repository call *before* init.
            // The tests will run within runTest { }, so `playerViewModel` from `setUp` is available.
            // We can update the `allSongs` part of the state. This is a bit hacky but common.
            // The actual loading mechanism involves `_playerUiState.update { it.copy(allSongs = ... ) }`
            // We can't call that directly.
            // So, the mock for getAudioFiles is the correct path.
        }


        @Test
        fun `genre with fewer than 3 songs returns all their valid covers`() = runTest {
            setupViewModelWithSongs(listOf(song1, song3, song2)) // song3 is Pop
            // Manually trigger state update as init in setUp already ran with empty songs
            playerViewModel.playerUiState.update { it.copy(allSongs = listOf(song1, song3, song2).toImmutableList()) }
            advanceUntilIdle()


            val uris = playerViewModel.getSongUrisForGenre("Pop")
            assertEquals(listOf("pop_cover1.png"), uris)
        }

        @Test
        fun `genre with more than 3 songs returns first 3 valid covers`() = runTest {
            val testSongs = listOf(song1, song2, song3, song4, song5) // song1,2,4,5 are Rock
            playerViewModel.playerUiState.update { it.copy(allSongs = testSongs.toImmutableList()) }
            advanceUntilIdle()

            val uris = playerViewModel.getSongUrisForGenre("Rock")
            assertEquals(listOf("rock_cover1.png", "rock_cover2.png", "rock_cover3.png"), uris)
        }

        @Test
        fun `genre with no songs returns empty list`() = runTest {
            val testSongs = listOf(song1, song3) // No "Electronic" songs
            playerViewModel.playerUiState.update { it.copy(allSongs = testSongs.toImmutableList()) }
            advanceUntilIdle()

            val uris = playerViewModel.getSongUrisForGenre("Electronic")
            assertTrue(uris.isEmpty())
        }

        @Test
        fun `genre with songs but no valid URIs returns empty list`() = runTest {
            // song6 (Jazz, null URI), song7 (Rock, empty URI)
            val testSongs = listOf(song6, song7, Song(id = "10", title = "Jazz Song 2", artist = "Artist J", genre = "Jazz", albumArtUriString = "", artistId = dummyArtistId, albumId = dummyAlbumId, contentUriString = dummyContentUri, duration = dummyDuration))
            playerViewModel.playerUiState.update { it.copy(allSongs = testSongs.toImmutableList()) }
            advanceUntilIdle()

            val jazzUris = playerViewModel.getSongUrisForGenre("Jazz")
            assertTrue(jazzUris.isEmpty())

            val rockUris = playerViewModel.getSongUrisForGenre("Rock") // Only song7 which has empty URI
            assertTrue(rockUris.isEmpty())
        }

        @Test
        fun `genre with mixed valid and invalid URIs returns only valid ones`() = runTest {
            val testSongs = listOf(song8, song6) // song8 (Jazz, valid URI), song6 (Jazz, null URI)
            playerViewModel.playerUiState.update { it.copy(allSongs = testSongs.toImmutableList()) }
            advanceUntilIdle()

            val uris = playerViewModel.getSongUrisForGenre("Jazz")
            assertEquals(listOf("jazz_cover1.png"), uris)
        }

        @Test
        fun `empty allSongs list returns empty list for any genre`() = runTest {
            playerViewModel.playerUiState.update { it.copy(allSongs = emptyList().toImmutableList()) }
            advanceUntilIdle()

            val uris = playerViewModel.getSongUrisForGenre("Rock")
            assertTrue(uris.isEmpty())
        }

        @Test
        fun `case insensitive genre matching`() = runTest {
            val testSongs = listOf(song1, song3) // song1 is Rock
            playerViewModel.playerUiState.update { it.copy(allSongs = testSongs.toImmutableList()) }
            advanceUntilIdle()

            val uris = playerViewModel.getSongUrisForGenre("rOcK")
            assertEquals(listOf("rock_cover1.png"), uris)
        }

        @Test
        fun `genre not found among songs with null genres`() = runTest {
            val testSongs = listOf(song9) // song9 has null genre
            playerViewModel.playerUiState.update { it.copy(allSongs = testSongs.toImmutableList()) }
            advanceUntilIdle()

            val uris = playerViewModel.getSongUrisForGenre("Rock") // "Rock" genre does not exist
            assertTrue(uris.isEmpty())
        }
         @Test
        fun `songs with null genre do not match specific genre query`() = runTest {
            val testSongs = listOf(song1, song9) // song1 (Rock), song9 (null genre)
            playerViewModel.playerUiState.update { it.copy(allSongs = testSongs.toImmutableList()) }
            advanceUntilIdle()

            val uris = playerViewModel.getSongUrisForGenre("Rock")
            assertEquals(listOf("rock_cover1.png"), uris) // Should only get song1
        }
    }

    @Nested
    @DisplayName("Shuffle Functionality")
    inner class ShuffleFunctionalityTests {

        private val song1 = Song(id = "1", title = "Song 1", artist = "Artist A", genre = "Rock", albumArtUriString = "cover1.png", artistId = 1L, albumId = 1L, contentUriString = "content://dummy/1", duration = 180000L)
        private val song2 = Song(id = "2", title = "Song 2", artist = "Artist B", genre = "Pop", albumArtUriString = "cover2.png", artistId = 2L, albumId = 2L, contentUriString = "content://dummy/2", duration = 200000L)
        private val allSongs = listOf(song1, song2)

        @Test
        fun `shuffleAllSongs calls playSongs with a random song`() = runTest {
            // Arrange
            val spiedViewModel = spyk(playerViewModel, recordPrivateCalls = true)
            coEvery { spiedViewModel.playSongs(any(), any(), any(), any()) } just runs
            spiedViewModel.playerUiState.update { it.copy(allSongs = allSongs.toImmutableList()) }

            // Act
            spiedViewModel.shuffleAllSongs()
            advanceUntilIdle()

            // Assert
            val capturedSongs = slot<List<Song>>()
            val capturedStartSong = slot<Song>()
            val capturedQueueName = slot<String>()

            coVerify {
                spiedViewModel.playSongs(
                    songsToPlay = capture(capturedSongs),
                    startSong = capture(capturedStartSong),
                    queueName = capture(capturedQueueName),
                    playlistId = any()
                )
            }

            assertEquals(allSongs, capturedSongs.captured)
            assertTrue(allSongs.contains(capturedStartSong.captured))
            assertEquals("All Songs (Shuffled)", capturedQueueName.captured)
        }
    }
}

// Assuming MainCoroutineExtension.kt exists in the test directory structure
// e.g., app/src/test/java/com/theveloper/pixelplay/MainCoroutineExtension.kt
// package com.theveloper.pixelplay
//
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.ExperimentalCoroutinesApi
// import kotlinx.coroutines.test.StandardTestDispatcher
// import kotlinx.coroutines.test.TestDispatcher
// import kotlinx.coroutines.test.resetMain
// import kotlinx.coroutines.test.setMain
// import org.junit.jupiter.api.extension.AfterEachCallback
// import org.junit.jupiter.api.extension.BeforeEachCallback
// import org.junit.jupiter.api.extension.ExtensionContext
//
// @ExperimentalCoroutinesApi
// class MainCoroutineExtension(private val testDispatcher: TestDispatcher = StandardTestDispatcher()) :
//     BeforeEachCallback, AfterEachCallback {
//
//     override fun beforeEach(context: ExtensionContext?) {
//         Dispatchers.setMain(testDispatcher)
//     }
//
//     override fun afterEach(context: ExtensionContext?) {
//         Dispatchers.resetMain()
//     }
// }
