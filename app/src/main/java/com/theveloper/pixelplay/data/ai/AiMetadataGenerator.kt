package com.theveloper.pixelplay.data.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.google.ai.client.generativeai.type.SerializationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import kotlin.Result

@Serializable
data class SongMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null
)

class AiMetadataGenerator @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val json: Json
) {
    private fun cleanJson(jsonString: String): String {
        return jsonString.replace("```json", "").replace("```", "").trim()
    }

    suspend fun generate(
        song: Song,
        fieldsToComplete: List<String>
    ): Result<SongMetadata> {
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

            val fieldsJson = fieldsToComplete.joinToString(separator = ", ") { "\"$it\"" }

            val systemPrompt = """
            You are a music metadata expert. Your task is to find and complete missing metadata for a given song.
            You will be given the song's title and artist, and a list of fields to complete.
            Your response MUST be a raw JSON object, without any markdown, backticks or other formatting.
            The JSON keys MUST be lowercase and match the requested fields (e.g., "title", "artist", "album", "genre").
            For the genre, you must provide only one, the most accurate, single genre for the song.
            If you cannot find a specific piece of information, you should return an empty string for that field.

            Example response for a request to complete "album" and "genre":
            {
                "album": "Some Album",
                "genre": "Indie Pop"
            }
            """.trimIndent()

            val albumInfo = if (song.album.isNotBlank()) "Album: \"${song.album}\"" else ""

            val fullPrompt = """
            $systemPrompt

            Song title: "${song.title}"
            Song artist: "${song.displayArtist}"
            $albumInfo
            Fields to complete: [$fieldsJson]
            """.trimIndent()

            val response = generativeModel.generateContent(fullPrompt)
            val responseText = response.text
            if (responseText.isNullOrBlank()) {
                Timber.e("AI returned an empty or null response.")
                return Result.failure(Exception("AI returned an empty response."))
            }

            Timber.d("AI Response: $responseText")
            val cleanedJson = cleanJson(responseText)
            val metadata = json.decodeFromString<SongMetadata>(cleanedJson)

            Result.success(metadata)
        } catch (e: SerializationException) {
            Timber.e(e, "Error deserializing AI response.")
            Result.failure(Exception("Failed to parse AI response: ${e.message}"))
        } catch (e: Exception) {
            Timber.e(e, "Generic error in AiMetadataGenerator.")
            Result.failure(Exception("AI Error: ${e.message}"))
        }
    }
}
