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

    fun getScore(songId: String): Int {
        val scores = getScores()
        return scores.getOrDefault(songId, 0)
    }

    fun generateDailyMix(
        allSongs: List<Song>,
        limit: Int = 30
    ): List<Song> {
        if (allSongs.isEmpty()) {
            return emptyList()
        }

        val scores = getScores()
        val calendar = Calendar.getInstance()
        val seed = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        val random = java.util.Random(seed.toLong())

        // If there are no scores (e.g., first-time user), return a random playlist of 15 songs.
        if (scores.isEmpty()) {
            return allSongs.shuffled(random).take(15)
        }

        // Create a list of songs with their scores, filtering out songs not in the library.
        val scoredSongs = scores.entries
            .mapNotNull { entry ->
                val song = allSongs.find { it.id == entry.key }
                song?.let { Pair(it, entry.value) }
            }
            .sortedByDescending { it.second } // Sort by score
            .map { it.first } // Get the Song object

        // If for some reason no scored songs are found in the library, fallback to random.
        if (scoredSongs.isEmpty()) {
            return allSongs.shuffled(random).take(15)
        }

        // Take the top 'limit' songs based on score.
        return scoredSongs.take(limit)
    }
}
