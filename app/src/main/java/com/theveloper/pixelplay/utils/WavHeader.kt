package com.theveloper.pixelplay.utils

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Clase de ayuda para crear y actualizar la cabecera de un archivo WAV.
class WavHeader(
    private var fileSize: Int,
    private var subchunk2Size: Int,
    private val sampleRate: Int,
    private val bitsPerSample: Int,
    private val numChannels: Int
) {
    fun asByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(44)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk descriptor
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize) // fileSize - 8
        buffer.put("WAVE".toByteArray())

        // "fmt " sub-chunk
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16) // Subchunk1Size
        buffer.putShort(1) // AudioFormat (PCM)
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * numChannels * bitsPerSample / 8) // byteRate
        buffer.putShort((numChannels * bitsPerSample / 8).toShort()) // blockAlign
        buffer.putShort(bitsPerSample.toShort())

        // "data" sub-chunk
        buffer.put("data".toByteArray())
        buffer.putInt(subchunk2Size)

        return buffer.array()
    }

    fun updateHeader(file: File) {
        val headerBytes = asByteArray()
        RandomAccessFile(file, "rw").use {
            it.seek(0)
            it.write(headerBytes)
        }
    }
}