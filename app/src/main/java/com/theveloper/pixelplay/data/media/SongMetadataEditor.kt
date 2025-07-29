package com.theveloper.pixelplay.data.media

import android.content.Context
import android.net.Uri
import android.util.Log
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class SongMetadataEditor(private val context: Context) {

    fun editSongMetadata(
        contentUri: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String
    ): Boolean {
        val uri = Uri.parse(contentUri)
        var tempFile: File? = null
        try {
            // 1. Crear un archivo temporal a partir del URI de contenido
            tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                Log.e("SongMetadataEditor", "Failed to create temp file from URI.")
                return false
            }

            // 2. Leer el archivo de audio y modificar los metadatos
            val audioFile: AudioFile = AudioFileIO.read(tempFile)
            val tag = audioFile.tagOrCreateDefault
            tag.setField(FieldKey.TITLE, newTitle)
            tag.setField(FieldKey.ARTIST, newArtist)
            tag.setField(FieldKey.ALBUM, newAlbum)
            audioFile.commit() // Esto guarda los cambios en el archivo temporal

            // 3. Sobrescribir el archivo original con el archivo temporal modificado
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                    FileInputStream(tempFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } ?: run {
                Log.e("SongMetadataEditor", "Failed to open FileDescriptor for writing.")
                return false
            }

            Log.d("SongMetadataEditor", "Successfully edited and saved metadata for URI: $contentUri")
            return true

        } catch (e: Exception) {
            Log.e("SongMetadataEditor", "Error editing metadata for URI: $contentUri", e)
            return false
        } finally {
            // 4. Limpiar el archivo temporal
            tempFile?.delete()
        }
    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("temp_audio", ".mp3", context.cacheDir)
            tempFile.deleteOnExit()
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e("SongMetadataEditor", "Error creating temp file from URI", e)
            null
        }
    }
}
