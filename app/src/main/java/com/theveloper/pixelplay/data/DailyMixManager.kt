package com.theveloper.pixelplay.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
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
    private val scoresType = object : TypeToken<MutableMap<String, Int>>() {}.type

    private fun getScores(): MutableMap<String, Int> {
        if (!scoresFile.exists()) {
            return mutableMapOf()
        }

        val raw = scoresFile.readText()
        if (raw.isBlank()) {
            return mutableMapOf()
        }

        return runCatching {
            val element = gson.fromJson(raw, JsonElement::class.java)

            if (element == null || element.isJsonNull) {
                mutableMapOf()
            } else if (element.isJsonObject) {
                val result = mutableMapOf<String, Int>()
                for ((key, value) in element.asJsonObject.entrySet()) {
                    val score = extractScore(value)
                    if (score != null) {
                        result[key] = score
                    } else {
                        Log.w(TAG, "Skipping song score entry for \"$key\" because it does not contain a numeric score: $value")
                    }
                }
                result
            } else {
                gson.fromJson<MutableMap<String, Int>>(raw, scoresType)
            }
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to parse song scores file, ignoring its contents", throwable)
            mutableMapOf()
        }
    }

    private fun extractScore(value: JsonElement): Int? {
        if (value.isJsonPrimitive) {
            val primitive = value.asJsonPrimitive
            if (primitive.isNumber) {
                return primitive.asNumber.toInt()
            }
            return null
        }

        if (value.isJsonObject) {
            val obj = value.asJsonObject
            for (key in SCORE_KEY_CANDIDATES) {
                val candidate = obj.get(key)
                if (candidate != null && candidate.isJsonPrimitive) {
                    val primitive = candidate.asJsonPrimitive
                    if (primitive.isNumber) {
                        return primitive.asNumber.toInt()
                    }
                }
            }
        }

        return null
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

    companion object {
        private const val TAG = "DailyMixManager"
        private val SCORE_KEY_CANDIDATES = listOf("score", "count", "value")
    }
}
