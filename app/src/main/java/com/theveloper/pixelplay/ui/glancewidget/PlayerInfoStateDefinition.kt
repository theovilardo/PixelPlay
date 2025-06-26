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
import com.theveloper.pixelplay.PlayerInfoProto
import java.io.File

// La definición del mensaje PlayerInfoProto se encuentra en:
// app/src/main/proto/player_info.proto
//
// Su contenido es:
// syntax = "proto3";
// option java_package = "com.theveloper.pixelplay";
// option java_multiple_files = true;
//
// message PlayerInfoProto {
//   string song_title = 1;
//   string artist_name = 2;
//   bool is_playing = 3;
//   string album_art_uri = 4;
//   bytes album_art_bitmap_data = 5;
//   int64 current_position_ms = 6;
//   int64 total_duration_ms = 7;
// }

object PlayerInfoStateDefinition : GlanceStateDefinition<PlayerInfoProto> {
    private const val DATASTORE_FILE_NAME = "pixelPlayPlayerInfo_v1" // Nombre de archivo único
    private val Context.playerInfoDataStore: DataStore<PlayerInfoProto> by dataStore(
        fileName = DATASTORE_FILE_NAME,
        serializer = PlayerInfoSerializer
    )

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<PlayerInfoProto> {
        return context.playerInfoDataStore
    }

    override fun getLocation(context: Context, fileKey: String): File {
        // Devuelve la ruta al archivo DataStore dentro del directorio de archivos de la app.
        return File(context.filesDir, "datastore/$DATASTORE_FILE_NAME")
    }
}

object PlayerInfoSerializer : Serializer<PlayerInfoProto> {
    override val defaultValue: PlayerInfoProto = PlayerInfoProto.getDefaultInstance()
    override suspend fun readFrom(input: InputStream): PlayerInfoProto {
        try {
            return PlayerInfoProto.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }
    override suspend fun writeTo(t: PlayerInfoProto, output: OutputStream) = t.writeTo(output)
}