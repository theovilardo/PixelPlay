package com.theveloper.pixelplay.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.SortOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val createdDirs = mutableListOf<java.nio.file.Path>()

    @AfterEach
    fun tearDown() {
        createdDirs.forEach { path ->
            path.toFile().deleteRecursively()
        }
        createdDirs.clear()
    }

    private fun createRepository(scope: CoroutineScope): UserPreferencesRepository {
        val dir = Files.createTempDirectory("user-prefs-test")
        createdDirs += dir
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            dir.resolve("settings.preferences_pb").toFile()
        }
        return UserPreferencesRepository(dataStore, json)
    }

    @Test
    fun ensureLibrarySortDefaults_sets_expected_defaults() = runTest {
        val repository = createRepository(this)

        repository.ensureLibrarySortDefaults()

        assertThat(repository.albumsSortOptionFlow.first()).isEqualTo(SortOption.AlbumTitleAZ.storageKey)
        assertThat(repository.playlistsSortOptionFlow.first()).isEqualTo(SortOption.PlaylistNameAZ.storageKey)
        assertThat(repository.likedSongsSortOptionFlow.first()).isEqualTo(SortOption.LikedSongDateLiked.storageKey)
    }

    @Test
    fun ensureLibrarySortDefaults_preserves_existing_values() = runTest {
        val repository = createRepository(this)

        repository.setAlbumsSortOption(SortOption.AlbumTitleZA.storageKey)
        repository.setPlaylistsSortOption(SortOption.PlaylistDateCreated.storageKey)
        repository.setLikedSongsSortOption(SortOption.LikedSongArtist.storageKey)

        repository.ensureLibrarySortDefaults()

        assertThat(repository.albumsSortOptionFlow.first()).isEqualTo(SortOption.AlbumTitleZA.storageKey)
        assertThat(repository.playlistsSortOptionFlow.first()).isEqualTo(SortOption.PlaylistDateCreated.storageKey)
        assertThat(repository.likedSongsSortOptionFlow.first()).isEqualTo(SortOption.LikedSongArtist.storageKey)
    }

    @Test
    fun ensureLibrarySortDefaults_migrates_legacy_display_names() = runTest {
        val repository = createRepository(this)

        repository.setAlbumsSortOption(SortOption.AlbumTitleZA.displayName)
        repository.setPlaylistsSortOption(SortOption.PlaylistDateCreated.displayName)
        repository.setLikedSongsSortOption(SortOption.LikedSongArtist.displayName)

        repository.ensureLibrarySortDefaults()

        assertThat(repository.albumsSortOptionFlow.first()).isEqualTo(SortOption.AlbumTitleZA.storageKey)
        assertThat(repository.playlistsSortOptionFlow.first()).isEqualTo(SortOption.PlaylistDateCreated.storageKey)
        assertThat(repository.likedSongsSortOptionFlow.first()).isEqualTo(SortOption.LikedSongArtist.storageKey)
    }
}
