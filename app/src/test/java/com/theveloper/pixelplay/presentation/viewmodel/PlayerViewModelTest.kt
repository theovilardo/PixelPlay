package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
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
