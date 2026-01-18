package com.theveloper.pixelplay.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

object AudioDecoder {

    private const val TIMEOUT_US = 1000L
    private const val ENCODING_PCM_16BIT = 2
    private const val ENCODING_PCM_FLOAT = 4

    suspend fun decodeToFloatArray(context: Context, uri: Uri, requiredSamples: Int): Result<FloatArray> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex = findAudioTrack(extractor)
            if (trackIndex == -1) {
                extractor.release()
                error("No audio track found in the file.")
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("MIME type not found.")
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmData = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEndOfStream = false

            while (!isEndOfStream && pcmData.size < requiredSamples) { // --- MODIFICADO: Condición de parada ---
                val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                    pcmData.addAll(byteBufferToFloatArray(outputBuffer, format).asList())
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    // Si ya tenemos suficientes muestras, salimos del bucle interno
                    if (pcmData.size >= requiredSamples) break

                    outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            Timber.tag("AudioDecoder").d("Successfully decoded ${pcmData.size} samples.")

            // --- MODIFICADO: Rellenamos con silencio si la canción es más corta que lo requerido ---
            if (pcmData.size < requiredSamples) {
                val padding = FloatArray(requiredSamples - pcmData.size) { 0f }
                pcmData.addAll(padding.asList())
            }

            // Devolvemos el array con el tamaño exacto
            pcmData.toFloatArray().copyOf(requiredSamples)
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    private fun byteBufferToFloatArray(buffer: ByteBuffer, format: MediaFormat): FloatArray {
        val pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, ENCODING_PCM_16BIT)
        buffer.rewind()

        return when (pcmEncoding) {
            ENCODING_PCM_16BIT -> {
                val shortBuffer = buffer.asShortBuffer()
                FloatArray(shortBuffer.remaining()) {
                    shortBuffer.get().toFloat() / Short.MAX_VALUE
                }
            }
            ENCODING_PCM_FLOAT -> {
                val floatBuffer = buffer.asFloatBuffer()
                FloatArray(floatBuffer.remaining()) { floatBuffer.get() }
            }
            else -> throw UnsupportedOperationException("Unsupported PCM encoding: $pcmEncoding")
        }
    }
}