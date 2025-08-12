package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.preferences.protobuf.InvalidProtocolBufferException
import androidx.glance.state.GlanceStateDefinition
import java.io.InputStream
import java.io.OutputStream
import com.theveloper.pixelplay.data.model.PlayerInfo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.IOException
import java.io.File

object PlayerInfoStateDefinition : GlanceStateDefinition<PlayerInfo> { // Changed to PlayerInfo
    private const val DATASTORE_FILE_NAME = "pixelPlayPlayerInfo_v1_json" // Changed filename suffix

    // Json instance for serialization. Could be injected if this object were a class.
    // For simplicity here, using a default configured instance.
    // Ideally, use the one provided by AppModule.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true // Important if some default values in PlayerInfo might be null initially
    }

    private val Context.playerInfoDataStore: DataStore<PlayerInfo> by dataStore(
        fileName = DATASTORE_FILE_NAME,
        serializer = PlayerInfoJsonSerializer(json) // Use new JSON serializer
    )

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<PlayerInfo> {
        return context.playerInfoDataStore
    }

    override fun getLocation(context: Context, fileKey: String): File {
        return File(context.filesDir, "datastore/$DATASTORE_FILE_NAME")
    }
}

class PlayerInfoJsonSerializer(private val json: Json) : Serializer<PlayerInfo> {
    override val defaultValue: PlayerInfo = PlayerInfo() // Default instance of the data class

    override suspend fun readFrom(input: InputStream): PlayerInfo {
        try {
            val string = input.bufferedReader().use { it.readText() }
            if (string.isBlank()) return defaultValue // Handle empty file case
            return json.decodeFromString<PlayerInfo>(string)
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read json.", exception)
        } catch (e: IOException) {
            throw CorruptionException("Cannot read json due to IO issue.", e)
        }
    }

    override suspend fun writeTo(t: PlayerInfo, output: OutputStream) {
        output.bufferedWriter().use {
            it.write(json.encodeToString(t))
        }
    }
}