package com.theveloper.pixelplay.data.repository

import android.content.Context
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SongEntity // Necesario para datos de prueba
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.model.Song // Para verificar el mapeo
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
// import org.junit.jupiter.api.Assertions.assertEquals // Usar Truth para aserciones más ricas
// import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.google.common.truth.Truth.assertThat


@ExperimentalCoroutinesApi
class MusicRepositoryImplTest {

    private lateinit var musicRepository: MusicRepositoryImpl
    private val mockMusicDao: MusicDao = mockk()
    private val mockSearchHistoryDao: SearchHistoryDao = mockk(relaxed = true) // relaxed para evitar mockear todos los métodos de historial
    private val mockContext: Context = mockk(relaxed = true) // relaxed para getAllUniqueAudioDirectories si no se testea a fondo aquí
    private val mockUserPreferencesRepository: UserPreferencesRepository = mockk()

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // Usar el dispatcher de prueba para Main
        // Mockear los flows de preferencias por defecto, pueden ser sobrescritos por test
        coEvery { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(emptySet())
        coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)

        musicRepository = MusicRepositoryImpl(
            context = mockContext,
            userPreferencesRepository = mockUserPreferencesRepository,
            searchHistoryDao = mockSearchHistoryDao,
            musicDao = mockMusicDao
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain() // Limpiar el dispatcher de Main
    }

    // --- Pruebas para getAudioFiles ---
    @Test
    fun `getAudioFiles returns songs from DAO, filtered by allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            SongEntity(1L, "Song A", "Artist 1", 101L, "Album X", 201L, "uri_a", "art_a", 180, "Pop", "/allowed/path/songA.mp3"),
            SongEntity(2L, "Song B", "Artist 1", 101L, "Album X", 201L, "uri_b", "art_b", 200, "Pop", "/forbidden/path/songB.mp3"),
            SongEntity(3L, "Song C", "Artist 2", 102L, "Album Y", 202L, "uri_c", "art_c", 220, "Rock", "/allowed/path/songC.mp3")
        )
        val allowedDirs = setOf("/allowed/path")

        every { mockMusicDao.getSongs(any(), any()) } returns flowOf(songEntities) // No es suspend
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs) // No es suspend
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true) // No es suspend

        val result: List<Song> = musicRepository.getAudioFiles(page = 1, pageSize = 10).first()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly("1", "3").inOrder()
        verify { mockMusicDao.getSongs(10, 0) } // Verificar llamada al DAO
    }

    @Test
    fun `getAudioFiles returns empty list if no allowed directories even before initial setup`() = runTest(testDispatcher) {
        val songEntities = listOf(
            SongEntity(1L, "Song A", "Artist 1", 101L, "Album X", 201L, "uri_a", "art_a", 180, "Pop", "/any/path/songA.mp3"),
            SongEntity(2L, "Song B", "Artist 1", 101L, "Album X", 201L, "uri_b", "art_b", 200, "Pop", "/other/path/songB.mp3")
        )
        every { mockMusicDao.getSongs(any(), any()) } returns flowOf(songEntities)
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(emptySet())
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(false) // Setup no completado

        val result = musicRepository.getAudioFiles(page = 1, pageSize = 10).first()
        assertThat(result).isEmpty()
    }

    @Test
    fun `getAudioFiles returns empty list if initial setup done and no allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            SongEntity(1L, "Song A", "Artist 1", 101L, "Album X", 201L, "uri_a", "art_a", 180, "Pop", "/allowed/path/songA.mp3")
        )
        every { mockMusicDao.getSongs(any(), any()) } returns flowOf(songEntities)
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(emptySet()) // No hay directorios permitidos
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true) // Setup completado

        val result = musicRepository.getAudioFiles(page = 1, pageSize = 10).first()
        assertThat(result).isEmpty()
    }

    // --- Pruebas para getAlbums ---
    @Test
    fun `getAlbums returns albums from DAO, filtered by songs in allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            SongEntity(1L, "S1", "A1", 101L, "Album1", 201L, "u1", "au1", 100, "G", "/allowed/s1.mp3"),
            SongEntity(2L, "S2", "A1", 101L, "Album1", 201L, "u2", "au2", 100, "G", "/allowed/s2.mp3"),
            SongEntity(3L, "S3", "A2", 102L, "Album2", 202L, "u3", "au3", 100, "G", "/forbidden/s3.mp3"),
            SongEntity(4L, "S4", "A3", 103L, "Album3", 203L, "u4", "au4", 100, "G", "/allowed/s4.mp3")
        )
        val allAlbumEntities = listOf(
            AlbumEntity(201L, "Album1", "ArtistName1", 101L, "art_uri1", 10), // El songCount original del DAO
            AlbumEntity(202L, "Album2", "ArtistName2", 102L, "art_uri2", 5),
            AlbumEntity(203L, "Album3", "ArtistName3", 103L, "art_uri3", 3)
        )
        val allowedDirs = setOf("/allowed")

        every { mockMusicDao.getSongs(Int.MAX_VALUE, 0) } returns flowOf(songEntities)
        every { mockMusicDao.getAlbums(any(), any()) } returns flowOf(allAlbumEntities)
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs)
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)

        val result = musicRepository.getAlbums(page = 1, pageSize = 10).first()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly(201L, 203L).inOrder() // Asumiendo que el DAO los devuelve ordenados
        assertThat(result.find { it.id == 201L }?.songCount).isEqualTo(2)
        assertThat(result.find { it.id == 203L }?.songCount).isEqualTo(1)
    }

    // --- Pruebas para getArtists ---
    @Test
    fun `getArtists returns artists from DAO, filtered by songs in allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            SongEntity(1L, "S1", "Artist1Name", 101L, "Album1", 201L, "u1", "au1", 100, "G", "/allowed/s1.mp3"),
            SongEntity(2L, "S2", "Artist2Name", 102L, "Album2", 202L, "u2", "au2", 100, "G", "/forbidden/s2.mp3"),
            SongEntity(3L, "S3", "Artist1Name", 101L, "Album3", 203L, "u3", "au3", 100, "G", "/allowed/s3.mp3")
        )
        val allArtistEntities = listOf(
            ArtistEntity(101L, "Artist1Name", 20), // El trackCount original del DAO
            ArtistEntity(102L, "Artist2Name", 10)
        )
        val allowedDirs = setOf("/allowed")

        every { mockMusicDao.getSongs(Int.MAX_VALUE, 0) } returns flowOf(songEntities)
        every { mockMusicDao.getArtists(any(), any()) } returns flowOf(allArtistEntities)
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs)
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)

        val result = musicRepository.getArtists(page = 1, pageSize = 10).first()

        assertThat(result).hasSize(1)
        assertThat(result.first().id).isEqualTo(101L)
        assertThat(result.first().songCount).isEqualTo(2) // El modelo Artist usa songCount
    }

    @Nested
    @DisplayName("Search History Functions")
    inner class SearchHistoryFunctions {
        @Test
        fun `addSearchHistoryItem calls dao methods`() = runTest {
            val query = "test query"
            coEvery { mockSearchHistoryDao.deleteByQuery(query) } just runs
            coEvery { mockSearchHistoryDao.insert(any()) } just runs

            musicRepository.addSearchHistoryItem(query)

            coVerifyOrder {
                mockSearchHistoryDao.deleteByQuery(query)
                mockSearchHistoryDao.insert(any())
            }
        }
        // TODO: Añadir más tests para el historial si es necesario
    }

    // TODO: Añadir tests para:
    // - getSongsForAlbum, getSongsForArtist, getSongsByIds
    // - searchSongs, searchAlbums, searchArtists, searchAll (verificando la lógica de combine y filtrado)
    // - getAllUniqueAlbumArtUris
    // - getMusicByGenre
    // - getAllUniqueAudioDirectories (si se mantiene la lógica de MediaStore, necesitará mockear ContentResolver)
    // - invalidateCachesDependentOnAllowedDirectories (verificar que hace lo esperado, o nada si es obsoleta)
}
