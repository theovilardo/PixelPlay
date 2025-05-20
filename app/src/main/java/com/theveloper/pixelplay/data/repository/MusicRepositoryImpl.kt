package com.theveloper.pixelplay.data.repository

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : MusicRepository {

    // Proyección común para canciones
    private val songProjection = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA // Necesitamos DATA para obtener el path del archivo y su directorio padre
    )

    // Mutex para asegurar que el escaneo inicial de directorios sea seguro
    private val directoryScanMutex = Mutex()

    // Cache simple para los directorios encontrados durante el escaneo inicial
    private var cachedAudioDirectories: Set<String>? = null

    // Cache para almacenar todas las canciones (filtradas por directorio) después de la primera carga completa
    private var cachedAllSongs: List<Song>? = null

    /**
     * Consulta MediaStore para obtener canciones y aplica el filtrado por directorio.
     * **Esta función NO usa paginación en la consulta de MediaStore.**
     * @param selection La cláusula WHERE.
     * @param selectionArgs Los argumentos para la cláusula WHERE.
     * @param sortOrder La cláusula ORDER BY (sin LIMIT/OFFSET).
     * @return Lista de objetos Song, filtrada por directorios permitidos.
     */
    private suspend fun queryAndFilterSongs(
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String? // sortOrder sin LIMIT/OFFSET
    ): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        // Obtener las preferencias de directorios permitidos
        val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
        val allowedDirectories = userPreferencesRepository.allowedDirectoriesFlow.first()

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songProjection,
            selection, // Puede ser null
            selectionArgs, // Puede ser null
            sortOrder // Usamos el sortOrder SIN paginación
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val filePath = c.getString(dataColumn)
                val directoryPath = File(filePath).parent ?: ""

                // --- Lógica de Filtrado Mejorada ---
                // Aplicar el filtro de directorios permitidos *siempre* que el setup inicial esté hecho.
                // Si el setup inicial NO está hecho, permitimos *todas* las canciones de la consulta actual
                // para que el usuario pueda descubrir todos los directorios en la configuración.
                val isAllowed = if (!initialSetupDone) {
                    true // Permitir todas las canciones si el setup inicial no está hecho
                } else {
                    // Si el setup inicial está hecho, filtrar por directorios permitidos.
                    // Si allowedDirectories está vacío, significa que el usuario desmarcó todo,
                    // por lo tanto, ninguna canción será permitida.
                    allowedDirectories.contains(directoryPath)
                }
                // ----------------------------------

                if (isAllowed) {
                    val id = c.getLong(idColumn)
                    val title = c.getString(titleColumn)
                    val artist = c.getString(artistColumn) ?: "Artista Desconocido"
                    val album = c.getString(albumColumn) ?: "Álbum Desconocido"
                    val albumId = c.getLong(albumIdColumn)
                    val duration = c.getLong(durationColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                    )
                    val albumArtUri: Uri? = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), albumId
                    )
                    songs.add(
                        Song(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            contentUri = contentUri,
                            albumArtUri = albumArtUri,
                            duration = duration
                            // Considerar añadir filePath al modelo Song si es útil en otros lugares
                        )
                    )
                }
            }
        }
        return@withContext songs
    }

    // Implementación de getAudioFiles con paginación MANUAL
    override suspend fun getAudioFiles(page: Int, pageSize: Int): List<Song> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * pageSize
        if (offset < 0) throw IllegalArgumentException("Page number must be 1 or greater")

        // Si la caché de todas las canciones filtradas no existe, la cargamos
        if (cachedAllSongs == null) {
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
            val selectionArgs = arrayOf("30000") // Mínimo 30 segundos
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC" // Ordenar pero SIN paginación en la consulta

            // Consulta MediaStore para obtener *todas* las canciones válidas y filtrarlas
            cachedAllSongs = queryAndFilterSongs(selection, selectionArgs, sortOrder)
        }

        // Aplicar Paginación MANUAL a la lista cacheada
        return@withContext cachedAllSongs?.drop(offset)?.take(pageSize) ?: emptyList()
    }

    // Implementación de getAlbums con paginación MANUAL
    override suspend fun getAlbums(page: Int, pageSize: Int): List<Album> = withContext(Dispatchers.IO) {
        val allAlbums = mutableListOf<Album>()
        val offset = (page - 1) * pageSize
        if (offset < 0) throw IllegalArgumentException("Page number must be 1 or greater")

        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
            MediaStore.Audio.Albums.ALBUM_ART // Disponible a partir de API 29
        )

        // Consulta SIN paginación en MediaStore
        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC" // Solo ordenar, sin LIMIT/OFFSET

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            null, // Sin selección específica por ahora
            null,
            sortOrder // Usamos el sortOrder SIN paginación
        )
        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val songCountColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            // val albumArtColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART) // API 29+

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val title = c.getString(titleColumn) ?: "Álbum Desconocido"
                val artist = c.getString(artistColumn) ?: "Varios Artistas"
                val songCount = c.getInt(songCountColumn)
                val albumArtUri: Uri? = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), id
                )

                if (songCount > 0) { // Solo mostrar álbumes con canciones
                    // Añadir *todos* los álbumes válidos a la lista temporal
                    allAlbums.add(Album(id, title, artist, albumArtUri, songCount))
                }
            }
        }

        // --- Aplicar Paginación MANUAL a la lista completa ---
        return@withContext allAlbums.drop(offset).take(pageSize)
        // ----------------------------------------------------
    }

    // Implementación de getArtists con paginación MANUAL
    override suspend fun getArtists(page: Int, pageSize: Int): List<Artist> = withContext(Dispatchers.IO) {
        val allArtists = mutableListOf<Artist>()
        val offset = (page - 1) * pageSize
        if (offset < 0) throw IllegalArgumentException("Page number must be 1 or greater")

        val projection = arrayOf(
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
        )

        // Consulta SIN paginación en MediaStore
        val sortOrder = "${MediaStore.Audio.Artists.ARTIST} ASC" // Solo ordenar, sin LIMIT/OFFSET

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder // Usamos el sortOrder SIN paginación
        )
        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Artists._ID)
            val nameColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val trackCountColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val name = c.getString(nameColumn) ?: "Artista Desconocido"
                val trackCount = c.getInt(trackCountColumn)
                if (trackCount > 0) { // Solo mostrar artistas con canciones
                    // Añadir *todos* los artistas válidos a la lista temporal
                    allArtists.add(Artist(id, name, trackCount))
                }
            }
        }

        // --- Aplicar Paginación MANUAL a la lista completa ---
        return@withContext allArtists.drop(offset).take(pageSize)
        // ---------------------------------------------------
    }

    // Implementación de getSongsForAlbum (NO paginada en MediaStore)
    // Se usa para obtener TODAS las canciones de un álbum para la cola de reproducción.
    override suspend fun getSongsForAlbum(albumId: Long): List<Song> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ALBUM_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        // No aplicamos paginación en la consulta, obtenemos todas las canciones del álbum
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        // queryAndFilterSongs ya maneja el filtrado por directorio
        return@withContext queryAndFilterSongs(selection, selectionArgs, sortOrder)
    }

    // Implementación de getSongsForArtist (NO paginada en MediaStore)
    // Se usa para obtener TODAS las canciones de un artista para la cola de reproducción.
    override suspend fun getSongsForArtist(artistId: Long): List<Song> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.ARTIST_ID} = ?"
        val selectionArgs = arrayOf(artistId.toString())
        // No aplicamos paginación en la consulta, obtenemos todas las canciones del artista
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        // queryAndFilterSongs ya maneja el filtrado por directorio
        return@withContext queryAndFilterSongs(selection, selectionArgs, sortOrder)
    }

    // Implementación de getSongsByIds (NO paginada en MediaStore)
    override suspend fun getSongsByIds(songIds: List<String>): List<Song> = withContext(Dispatchers.IO) {
        if (songIds.isEmpty()) return@withContext emptyList()

        val songsMap = mutableMapOf<String, Song>()
        // Construir la cláusula IN para la selección
        // MediaStore._ID es un LONG, asegúrate de que tus songIds sean Strings que representen LONGs válidos
        val selection = "${MediaStore.Audio.Media._ID} IN (${songIds.joinToString(separator = ",") { "?" }})"
        val selectionArgs = songIds.toTypedArray()

        // No aplicamos paginación en la consulta, obtenemos todos los IDs solicitados
        // No hay orden específico de MediaStore, ordenaremos después
        val sortOrder: String? = null // No hay ORDER BY específico en la consulta

        // queryAndFilterSongs ya maneja el filtrado por directorio
        val filteredSongs = queryAndFilterSongs(selection, selectionArgs, sortOrder)

        // Devolver las canciones encontradas en el orden original de songIds
        val filteredSongsMap = filteredSongs.associateBy { it.id }
        return@withContext songIds.mapNotNull { filteredSongsMap[it] }
    }


    // Función para obtener todos los directorios únicos que contienen audio
    // Usado por SettingsViewModel
    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        // Usamos un Mutex para asegurar que solo un escaneo de directorios se ejecute a la vez
        directoryScanMutex.withLock {
            // Si ya hemos escaneado los directorios recientemente, devolvemos la caché.
            cachedAudioDirectories?.let { return@withContext it }

            // Si no hay caché, realizamos la consulta a MediaStore para *todos* los directorios.
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

            // Consulta SIN paginación para obtener TODOS los directorios
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null // No hay LIMIT/OFFSET ni ORDER BY aquí
            )
            cursor?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    val filePath = c.getString(dataColumn)
                    File(filePath).parent?.let { directories.add(it) }
                }
            }

            // Si el setup inicial no se ha hecho, guardar todos los directorios encontrados como permitidos.
            val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first()
            if (!initialSetupDone && directories.isNotEmpty()) {
                userPreferencesRepository.updateAllowedDirectories(directories)
                // Marcar el setup inicial como hecho
                userPreferencesRepository.setInitialSetupDone(true) // Asegúrate de que este método exista y funcione
            }

            // Cachear los directorios encontrados en este escaneo completo
            cachedAudioDirectories = directories
            return@withContext directories
        } // Fin del Mutex lock
    }

    override suspend fun getAllUniqueAlbumArtUris(): List<Uri> = withContext(Dispatchers.IO) {
        val uris = mutableSetOf<Uri>() // Usar Set para evitar duplicados
        val projection = arrayOf(MediaStore.Audio.Media.ALBUM_ID)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null // No necesitamos orden específico aquí
        )
        cursor?.use { c ->
            val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (c.moveToNext()) {
                val albumId = c.getLong(albumIdColumn)
                val albumArtUri: Uri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                // Verificar si la URI es válida o si la carátula existe podría ser una optimización,
                // pero Coil ya maneja errores de carga.
                uris.add(albumArtUri)
            }
        }
        return@withContext uris.toList()
    }

    // Método de ayuda para actualizar el estado de setup inicial en las preferencias
    // Asegúrate de implementar setInitialSetupDone en UserPreferencesRepository
    private suspend fun UserPreferencesRepository.setInitialSetupDone(isDone: Boolean) {
        // Implementación para guardar el booleano en DataStore/SharedPreferences
        // Ejemplo (asumiendo DataStore):
        // context.dataStore.edit { preferences ->
        //     preferences[booleanPreferencesKey("initial_setup_done")] = isDone
        // }
        // Reemplaza con tu lógica de guardado de preferencias
    }
}