package com.theveloper.pixelplay.data.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.LogUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import kotlin.Result

class AiPlaylistGenerator @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dailyMixManager: DailyMixManager,
    private val json: Json
) {
    suspend fun generate(
        userPrompt: String,
        allSongs: List<Song>,
        minLength: Int,
        maxLength: Int
    ): Result<List<Song>> {
        return try {
            val apiKey = userPreferencesRepository.geminiApiKey.first()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API Key not configured."))
            }

            val selectedModel = userPreferencesRepository.geminiModel.first()
            val modelName = selectedModel.ifEmpty { "" }

            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )

            // To optimize, send a random sample of songs instead of the whole library
            val songSample = allSongs.shuffled().take(200)

            val availableSongsJson = songSample.joinToString(separator = ",\n") { song ->
                // Calculate score for each song. This might be slow if it's a real-time calculation.
                val score = dailyMixManager.getScore(song.id)
                """
                {
                    "id": "${song.id}",
                    "title": "${song.title.replace("\"", "'")}",
                    "artist": "${song.artist.replace("\"", "'")}",
                    "genre": "${song.genre?.replace("\"", "'") ?: "unknown"}",
                    "relevance_score": $score
                }
                """.trimIndent()
            }

            // Get the custom system prompt from user preferences
            val customSystemPrompt = userPreferencesRepository.geminiSystemPrompt.first()

            // Build the task-specific instructions
            val taskInstructions = """
            Your task is to create a playlist for a user based on their prompt.
            You will be given a user's request, a desired playlist length range, and a list of available songs with their metadata.

            Instructions:
            1. Analyze the user's prompt to understand the desired mood, genre, or theme. This is the MOST IMPORTANT factor.
            2. Select songs from the provided list that best match the user's request.
            3. The `relevance_score` is a secondary factor. Use it to break ties or to choose between songs that equally match the prompt. Do NOT prioritize it over the prompt match.
            4. The final playlist should have a number of songs between `min_length` and `max_length`. It does not have to be the maximum.
            5. Your response MUST be ONLY a valid JSON array of song IDs. Do not include any other text, explanations, or markdown formatting.

            Example response for a playlist of 3 songs:
            ["song_id_1", "song_id_2", "song_id_3"]
            """.trimIndent()

            val fullPrompt = """
            
            $taskInstructions
            
            $customSystemPrompt
            
            User's request: "$userPrompt"
            Minimum playlist length: $minLength
            Maximum playlist length: $maxLength
            Available songs:
            [
            $availableSongsJson
            ]
            """.trimIndent()

            val response = generativeModel.generateContent(fullPrompt)
            val responseText = response.text ?: return Result.failure(Exception("AI returned an empty response."))

            // Clean the response to ensure it's valid JSON
            val cleanedJson = responseText.substringAfter("[").substringBeforeLast("]")
            val songIds = json.decodeFromString<List<String>>("[$cleanedJson]")

            // Map the returned IDs to the actual Song objects
            val songMap = allSongs.associateBy { it.id }
            val generatedPlaylist = songIds.mapNotNull { songMap[it] }

            Result.success(generatedPlaylist)

        } catch (e: Exception) {
            Result.failure(Exception("AI Error: ${e.message}"))
        }
    }
}
