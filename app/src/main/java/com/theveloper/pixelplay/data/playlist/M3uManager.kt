package com.theveloper.pixelplay.data.playlist

import android.content.Context
import android.net.Uri
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository
) {

    suspend fun parseM3u(uri: Uri): Pair<String, List<String>> {
        val songIds = mutableListOf<String>()
        var playlistName = "Imported Playlist"

        // Pre-load all songs once for efficient lookup (fixes performance issue with large M3U files)
        val allSongs = musicRepository.getAudioFiles().first()
        
        // Build lookup maps for fast matching
        val songsByPath = allSongs.associateBy { it.path }
        val songsByFileName = allSongs.groupBy { it.path.substringAfterLast("/") }
        val songsByContentUriFileName = allSongs.groupBy { it.contentUriString.substringAfterLast("/") }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmedLine = line?.trim() ?: continue
                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                        // Handle metadata if needed, e.g., #EXTINF
                        continue
                    }
                    
                    // trimmedLine is likely a file path or URI
                    // We need to find a song in our database that matches this path
                    
                    // First try exact path match from pre-loaded map
                    val songByPath = songsByPath[trimmedLine]
                    if (songByPath != null) {
                        songIds.add(songByPath.id)
                    } else {
                        // Try to match by filename if path doesn't match exactly
                        val fileName = trimmedLine.substringAfterLast("/")
                        val matchedSong = songsByFileName[fileName]?.firstOrNull()
                            ?: songsByContentUriFileName[fileName]?.firstOrNull()
                        if (matchedSong != null) {
                            songIds.add(matchedSong.id)
                        }
                    }
                }
            }
        }

        // Try to get the filename as playlist name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                playlistName = cursor.getString(nameIndex).removeSuffix(".m3u").removeSuffix(".m3u8")
            }
        }

        return Pair(playlistName, songIds)
    }

    fun generateM3u(playlist: Playlist, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        for (song in songs) {
            sb.append("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}\n")
            sb.append("${song.path}\n")
        }
        return sb.toString()
    }
}
