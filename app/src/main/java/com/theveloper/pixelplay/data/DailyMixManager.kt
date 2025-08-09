package com.theveloper.pixelplay.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailyMixManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val gson = Gson()
    private val scoresFile = File(context.filesDir, "song_scores.json")

    private fun getScores(): MutableMap<String, Int> {
        if (!scoresFile.exists()) {
            return mutableMapOf()
        }
        val type = object : TypeToken<MutableMap<String, Int>>() {}.type
        return gson.fromJson(scoresFile.readText(), type)
    }

    private fun saveScores(scores: Map<String, Int>) {
        scoresFile.writeText(gson.toJson(scores))
    }

    fun incrementScore(songId: String) {
        val scores = getScores()
        scores[songId] = scores.getOrDefault(songId, 0) + 1
        saveScores(scores)
    }

    fun generateDailyMix(
        allSongs: List<Song>,
        favoriteSongs: List<Song>,
        limit: Int = 30
    ): List<Song> {
        if (allSongs.isEmpty()) {
            return emptyList()
        }

        val scores = getScores()
        val dailyMix = mutableListOf<Song>()

        // Add favorite songs to the mix
        dailyMix.addAll(favoriteSongs)

        // Get 5 most played songs
        val mostPlayedSongs = scores.entries
            .sortedByDescending { it.value }
            .take(5)
            .mapNotNull { entry -> allSongs.find { it.id == entry.key } }
        dailyMix.addAll(mostPlayedSongs)


        // Add songs from the same artists as the favorites
        val favoriteArtists = favoriteSongs.map { it.artist }.toSet()
        val artistSongs = allSongs.filter { it.artist in favoriteArtists }
        dailyMix.addAll(artistSongs)

        // Add songs from the same genres as the favorites
        val favoriteGenres = favoriteSongs.mapNotNull { it.genre }.toSet()
        val genreSongs = allSongs.filter { it.genre in favoriteGenres }
        dailyMix.addAll(genreSongs)

        // Get a seed based on the current date
        val calendar = Calendar.getInstance()
        val seed = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)

        // Shuffle and take the limit
        return dailyMix.distinctBy { it.id }.shuffled(java.util.Random(seed.toLong())).take(limit)
    }
}
