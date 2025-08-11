package com.theveloper.pixelplay.data.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
        playlistLength: Int
    ): Result<List<Song>> {
        return try {
            val apiKey = userPreferencesRepository.geminiApiKey.first()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API Key not configured."))
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
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

            val systemPrompt = """
            You are a world-class DJ and music expert. Your task is to create a playlist for a user based on their prompt.
            You will be given a user's request, a desired playlist length, and a list of available songs with their metadata, including a relevance score.

            Instructions:
            1. Analyze the user's prompt to understand the desired mood, genre, or theme.
            2. Select songs from the provided list that best match the user's request.
            3. Prioritize songs with a higher relevance_score if they fit the prompt.
            4. The final playlist should have exactly the number of songs specified by `playlist_length`.
            5. Your response MUST be ONLY a valid JSON array of song IDs. Do not include any other text, explanations, or markdown formatting.

            Example response for a playlist of 3 songs:
            ["song_id_1", "song_id_2", "song_id_3"]
            """.trimIndent()

            val fullPrompt = """
            $systemPrompt

            User's request: "$userPrompt"
            Desired playlist length: $playlistLength
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
