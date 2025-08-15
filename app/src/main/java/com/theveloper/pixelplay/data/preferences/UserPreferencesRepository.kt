package com.theveloper.pixelplay.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey // Added import
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SortOption // Added import
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemePreference {
    const val DEFAULT = "default"       // Tema PixelPlay predeterminado
    const val DYNAMIC = "dynamic"       // Tema dinámico del sistema (Android 12+)
    const val ALBUM_ART = "album_art"   // Tema basado en carátula
    const val GLOBAL = "global"         // Para que el reproductor siga el tema global
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json // Inyectar Json para serialización
) {

    private object PreferencesKeys {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val ALLOWED_DIRECTORIES = stringSetPreferencesKey("allowed_directories")
        val INITIAL_SETUP_DONE = stringSetPreferencesKey("initial_setup_done_directories")
        // val GLOBAL_THEME_PREFERENCE = stringPreferencesKey("global_theme_preference_v2") // Removed
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
        val FAVORITE_SONG_IDS = stringSetPreferencesKey("favorite_song_ids")
        val USER_PLAYLISTS = stringPreferencesKey("user_playlists_json_v1")

        // Sort Option Keys
        val SONGS_SORT_OPTION = stringPreferencesKey("songs_sort_option")
        val ALBUMS_SORT_OPTION = stringPreferencesKey("albums_sort_option")
        val ARTISTS_SORT_OPTION = stringPreferencesKey("artists_sort_option")
        val PLAYLISTS_SORT_OPTION = stringPreferencesKey("playlists_sort_option")
        val LIKED_SONGS_SORT_OPTION = stringPreferencesKey("liked_songs_sort_option")

        // UI State Keys
        val LAST_LIBRARY_TAB_INDEX = intPreferencesKey("last_library_tab_index") // Corrected: Add intPreferencesKey here
        val MOCK_GENRES_ENABLED = booleanPreferencesKey("mock_genres_enabled")
        val LAST_DAILY_MIX_UPDATE = longPreferencesKey("last_daily_mix_update")
        val DAILY_MIX_SONG_IDS = stringPreferencesKey("daily_mix_song_ids")
        val NAV_BAR_CORNER_RADIUS = intPreferencesKey("nav_bar_corner_radius")
    }

    val dailyMixSongIdsFlow: Flow<List<String>> = dataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.DAILY_MIX_SONG_IDS]
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<String>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    suspend fun saveDailyMixSongIds(songIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_MIX_SONG_IDS] = json.encodeToString(songIds)
        }
    }

    val lastDailyMixUpdateFlow: Flow<Long> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_DAILY_MIX_UPDATE] ?: 0L
        }

    suspend fun saveLastDailyMixUpdateTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_DAILY_MIX_UPDATE] = timestamp
        }
    }

    val allowedDirectoriesFlow: Flow<Set<String>> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ALLOWED_DIRECTORIES] ?: emptySet()
        }

    val initialSetupDoneFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            // Si INITIAL_SETUP_DONE existe (incluso vacío), significa que el setup se hizo.
            // Lo usamos para decidir si la primera vez debemos permitir todos los directorios encontrados.
            preferences.contains(PreferencesKeys.INITIAL_SETUP_DONE)
        }

    // Removed globalThemePreferenceFlow
    // val globalThemePreferenceFlow: Flow<String> = dataStore.data
    //     .map { preferences ->
    //         preferences[PreferencesKeys.GLOBAL_THEME_PREFERENCE] ?: ThemePreference.DYNAMIC
    //     }

    val playerThemePreferenceFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PLAYER_THEME_PREFERENCE] ?: ThemePreference.ALBUM_ART // Default to Album Art
        }

    val favoriteSongIdsFlow: Flow<Set<String>> = dataStore.data // Nuevo flujo para favoritos
        .map { preferences ->
            preferences[PreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet()
        }

    val userPlaylistsFlow: Flow<List<Playlist>> = dataStore.data
        .map { preferences ->
            val jsonString = preferences[PreferencesKeys.USER_PLAYLISTS]
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<Playlist>>(jsonString)
                } catch (e: Exception) {
                    // Error al deserializar, devolver lista vacía o manejar error
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    private suspend fun savePlaylists(playlists: List<Playlist>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_PLAYLISTS] = json.encodeToString(playlists)
        }
    }

    suspend fun createPlaylist(name: String): Playlist {
        val currentPlaylists = userPlaylistsFlow.first().toMutableList()
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            songIds = emptyList()
        )
        currentPlaylists.add(newPlaylist)
        savePlaylists(currentPlaylists)
        return newPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) {
        val currentPlaylists = userPlaylistsFlow.first().toMutableList()
        currentPlaylists.removeAll { it.id == playlistId }
        savePlaylists(currentPlaylists)
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        val currentPlaylists = userPlaylistsFlow.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            currentPlaylists[index] = currentPlaylists[index].copy(name = newName, lastModified = System.currentTimeMillis())
            savePlaylists(currentPlaylists)
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        val currentPlaylists = userPlaylistsFlow.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = currentPlaylists[index]
            // Evitar duplicados, añadir solo los nuevos
            val newSongIds = (playlist.songIds + songIdsToAdd).distinct()
            currentPlaylists[index] = playlist.copy(songIds = newSongIds, lastModified = System.currentTimeMillis())
            savePlaylists(currentPlaylists)
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
        val currentPlaylists = userPlaylistsFlow.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            val playlist = currentPlaylists[index]
            currentPlaylists[index] = playlist.copy(
                songIds = playlist.songIds.filterNot { it == songIdToRemove },
                lastModified = System.currentTimeMillis()
            )
            savePlaylists(currentPlaylists)
        }
    }

    suspend fun reorderSongsInPlaylist(playlistId: String, newSongOrderIds: List<String>) {
        val currentPlaylists = userPlaylistsFlow.first().toMutableList()
        val index = currentPlaylists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            currentPlaylists[index] = currentPlaylists[index].copy(songIds = newSongOrderIds, lastModified = System.currentTimeMillis())
            savePlaylists(currentPlaylists)
        }
    }

    suspend fun updateAllowedDirectories(allowedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALLOWED_DIRECTORIES] = allowedPaths
            // Aseguramos que al actualizar directorios, el setup se marque como hecho.
            if (!preferences.contains(PreferencesKeys.INITIAL_SETUP_DONE)) {
                preferences[PreferencesKeys.INITIAL_SETUP_DONE] = emptySet()
            }
        }
    }

    // Removed setGlobalThemePreference
    // suspend fun setGlobalThemePreference(themeMode: String) {
    //     dataStore.edit { preferences ->
    //         preferences[PreferencesKeys.GLOBAL_THEME_PREFERENCE] = themeMode
    //     }
    // }

    suspend fun setPlayerThemePreference(themeMode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYER_THEME_PREFERENCE] = themeMode
        }
    }

    suspend fun toggleFavoriteSong(songId: String) { // Nueva función para favoritos
        dataStore.edit { preferences ->
            val currentFavorites = preferences[PreferencesKeys.FAVORITE_SONG_IDS] ?: emptySet()
            if (currentFavorites.contains(songId)) {
                preferences[PreferencesKeys.FAVORITE_SONG_IDS] = currentFavorites - songId
            } else {
                preferences[PreferencesKeys.FAVORITE_SONG_IDS] = currentFavorites + songId
            }
        }
    }

    suspend fun setInitialSetupDone(isDone: Boolean) {
        dataStore.edit { preferences ->
            if (isDone) {
                // Si queremos marcarlo como hecho, nos aseguramos de que la clave exista.
                // Guardar un emptySet es consistente con tu lógica actual en updateAllowedDirectories.
                if (!preferences.contains(PreferencesKeys.INITIAL_SETUP_DONE)) {
                    preferences[PreferencesKeys.INITIAL_SETUP_DONE] = emptySet()
                }
            } else {
                // Si queremos marcarlo como NO hecho (por ejemplo, para un reset), eliminamos la clave.
                // Esto hará que initialSetupDoneFlow emita false.
                preferences.remove(PreferencesKeys.INITIAL_SETUP_DONE)
            }
        }
    }

    // Flows for Sort Options
    val songsSortOptionFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SONGS_SORT_OPTION] ?: SortOption.SongTitleAZ.displayName
        }

    val albumsSortOptionFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ALBUMS_SORT_OPTION] ?: SortOption.AlbumTitleAZ.displayName
        }

    val artistsSortOptionFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.ARTISTS_SORT_OPTION] ?: SortOption.ArtistNameAZ.displayName
        }

    val playlistsSortOptionFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PLAYLISTS_SORT_OPTION] ?: SortOption.PlaylistNameAZ.displayName
        }

    val likedSongsSortOptionFlow: Flow<String> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LIKED_SONGS_SORT_OPTION] ?: SortOption.LikedSongTitleAZ.displayName
        }

    // Functions to update Sort Options
    suspend fun setSongsSortOption(optionName: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SONGS_SORT_OPTION] = optionName
        }
    }

    suspend fun setAlbumsSortOption(optionName: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALBUMS_SORT_OPTION] = optionName
        }
    }

    suspend fun setArtistsSortOption(optionName: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ARTISTS_SORT_OPTION] = optionName
        }
    }

    suspend fun setPlaylistsSortOption(optionName: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYLISTS_SORT_OPTION] = optionName
        }
    }

    suspend fun setLikedSongsSortOption(optionName: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIKED_SONGS_SORT_OPTION] = optionName
        }
    }

    // --- Library UI State ---
    val lastLibraryTabIndexFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_LIBRARY_TAB_INDEX] ?: 0 // Default to 0 (Songs tab)
        }

    suspend fun saveLastLibraryTabIndex(tabIndex: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_LIBRARY_TAB_INDEX] = tabIndex
        }
    }

    val mockGenresEnabledFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.MOCK_GENRES_ENABLED] ?: false // Default to false
        }

    suspend fun setMockGenresEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MOCK_GENRES_ENABLED] = enabled
        }
    }

    val geminiApiKey: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GEMINI_API_KEY] ?: ""
    }

    suspend fun setGeminiApiKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_API_KEY] = apiKey
        }
    }

    val navBarCornerRadiusFlow: Flow<Int> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.NAV_BAR_CORNER_RADIUS] ?: 32
        }

    suspend fun setNavBarCornerRadius(radius: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAV_BAR_CORNER_RADIUS] = radius
        }
    }
}