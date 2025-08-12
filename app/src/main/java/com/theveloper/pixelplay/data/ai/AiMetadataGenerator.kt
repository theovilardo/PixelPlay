package com.theveloper.pixelplay.data.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.Result

data class SongMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val lyrics: String
)

class AiMetadataGenerator @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val json: Json
) {
    suspend fun generate(
        song: Song,
        fieldsToComplete: List<String>
    ): Result<SongMetadata> {
        return try {
            val apiKey = userPreferencesRepository.geminiApiKey.first()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API Key not configured."))
            }

            val generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )

            val fieldsJson = fieldsToComplete.joinToString(separator = ", ") { "\"$it\"" }

            val systemPrompt = """
            You are a music metadata expert. Your task is to find and complete missing metadata for a given song.
            You will be given the song's title and artist, and a list of fields to complete.
            Your response MUST be ONLY a valid JSON object with the requested fields.
            The lyrics must be in LRC format with timestamps like [mm:ss.xx].
            If you cannot find a specific piece of information, you should return an empty string for that field.
            Do not include any other text, explanations, or markdown formatting.

            Example response for a request to complete "album" and "lyrics":
            {
                "album": "Some Album",
                "lyrics": "[00:12.34] Some lyrics\n[00:15.67] More lyrics"
            }
            """.trimIndent()

            val fullPrompt = """
            $systemPrompt

            Song title: "${song.title}"
            Song artist: "${song.artist}"
            Fields to complete: [$fieldsJson]
            """.trimIndent()

            val response = generativeModel.generateContent(fullPrompt)
            val responseText = response.text ?: return Result.failure(Exception("AI returned an empty response."))

            val metadata = json.decodeFromString<SongMetadata>(responseText)

            Result.success(metadata)

        } catch (e: Exception) {
            Result.failure(Exception("AI Error: ${e.message}"))
        }
    }
}
