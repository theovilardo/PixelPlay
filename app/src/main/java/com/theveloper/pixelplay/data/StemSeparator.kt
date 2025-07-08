package com.theveloper.pixelplay.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.MuxerException
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Muxer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Handles audio stem separation using a TFLite model and Media3 Transformer for audio processing.
 * This version replaces FFmpeg with modern AndroidX libraries.
 *
 * @param context The application context.
 * @param modelName The name of the TFLite model file in assets (without the .tflite extension).
 */
class StemSeparator(private val context: Context, private val modelName: String) {

    private var interpreter: Interpreter? = null

    fun init() {
        try {
            val model = loadModelFile(modelName)
            val options = Interpreter.Options()
            options.addDelegate(FlexDelegate())
            options.setNumThreads(Runtime.getRuntime().availableProcessors())
            interpreter = Interpreter(model, options)
            // Asignar tensores una sola vez al inicio. El modelo tiene una forma de entrada fija.
            interpreter?.allocateTensors()
        } catch (e: Exception) {
            Log.e("StemSeparator", "Error loading TFLite model or delegate.", e)
            throw e
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelName: String): MappedByteBuffer {
        context.assets.openFd("$modelName.tflite").use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                return fileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }

    suspend fun separate(inputAudioUriString: String): Map<String, Uri> {
        return withContext(Dispatchers.IO) {
            val interpreter = this@StemSeparator.interpreter ?: throw IllegalStateException("Interpreter not initialized. Call init() first.")
            decodeAndSeparateInChunks(interpreter, inputAudioUriString)
        }
    }

    private fun decodeAndSeparateInChunks(interpreter: Interpreter, inputUriString: String): Map<String, Uri> {
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(Uri.parse(inputUriString), "r")
            ?: throw IOException("Could not open file descriptor for URI: $inputUriString")

        try {
            // CORRECCIÓN: Usar pfd.statSize para obtener la longitud del archivo.
            extractor.setDataSource(pfd.fileDescriptor, 0, pfd.statSize)
        } finally {
            pfd.close()
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: throw IOException("No audio track found in the file.")

        extractor.selectTrack(trackIndex)
        val inputFormat = extractor.getTrackFormat(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val stemIndexMap = mapOf(0 to "bass", 1 to "drums", 2 to "other", 3 to "vocals", 4 to "piano")
        val outputFiles = stemIndexMap.values.associateWith { File(context.filesDir, "$it.pcm") }
        val outputStreams = outputFiles.mapValues { FileOutputStream(it.value) }
        val outputDataSizes = stemIndexMap.values.associateWith { 0L }.toMutableMap()

        val inputTensor = interpreter.getInputTensor(0)
        val requiredChunkSizeBytes = inputTensor.numBytes()
        val pcmChunkBuffer = ByteBuffer.allocateDirect(requiredChunkSizeBytes).order(ByteOrder.nativeOrder())

        val outputMap = HashMap<Int, Any>()
        for (i in 0 until interpreter.outputTensorCount) {
            val outputTensor = interpreter.getOutputTensor(i)
            outputMap[i] = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var isInputEos = false
        var isOutputEos = false
        val timeoutUs = 10000L

        while (!isOutputEos) {
            if (!isInputEos) {
                val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isInputEos = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (outputBufferIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!

                    while (outputBuffer.hasRemaining()) {
                        val amountToRead = min(outputBuffer.remaining(), pcmChunkBuffer.remaining())
                        val limit = outputBuffer.limit()
                        outputBuffer.limit(outputBuffer.position() + amountToRead)
                        pcmChunkBuffer.put(outputBuffer)
                        outputBuffer.limit(limit)

                        if (!pcmChunkBuffer.hasRemaining()) {
                            processChunk(interpreter, pcmChunkBuffer, outputMap, outputStreams, outputDataSizes)
                            pcmChunkBuffer.clear()
                        }
                    }
                }
                decoder.releaseOutputBuffer(outputBufferIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isOutputEos = true
                    if (pcmChunkBuffer.position() > 0) {
                        processChunk(interpreter, pcmChunkBuffer, outputMap, outputStreams, outputDataSizes, true)
                    }
                }
            }
        }

        outputStreams.values.forEach { it.close() }
        decoder.stop()
        decoder.release()
        extractor.release()

        val stemUris = mutableMapOf<String, Uri>()
        outputFiles.forEach { (name, pcmFile) ->
            val wavFile = File(context.filesDir, "$name.wav")
            val dataSize = outputDataSizes[name] ?: 0L
            addWavHeader(pcmFile, wavFile, dataSize, sampleRate, channelCount)
            stemUris[name] = Uri.fromFile(wavFile)
            pcmFile.delete()
        }
        return stemUris
    }

    private fun processChunk(
        interpreter: Interpreter,
        pcmChunkBuffer: ByteBuffer,
        outputMap: MutableMap<Int, Any>,
        outputStreams: Map<String, FileOutputStream>,
        outputDataSizes: MutableMap<String, Long>,
        isLastChunk: Boolean = false
    ) {
        val actualBytesInChunk = pcmChunkBuffer.position()

        if (isLastChunk) {
            while (pcmChunkBuffer.hasRemaining()) {
                pcmChunkBuffer.put(0.toByte())
            }
        }

        pcmChunkBuffer.flip()

        val inputs = arrayOf<Any>(pcmChunkBuffer)

        outputMap.values.forEach { (it as ByteBuffer).clear() }

        interpreter.runForMultipleInputsOutputs(inputs, outputMap)

        val stemIndexMap = mapOf(0 to "bass", 1 to "drums", 2 to "other", 3 to "vocals", 4 to "piano")
        for ((index, data) in outputMap) {
            val stemName = stemIndexMap[index] ?: continue
            val outputByteBuffer = data as ByteBuffer

            val bytesToWrite = if (isLastChunk) {
                val ratio = actualBytesInChunk.toFloat() / pcmChunkBuffer.capacity().toFloat()
                (outputByteBuffer.capacity() * ratio).toInt()
            } else {
                outputByteBuffer.capacity()
            }

            outputByteBuffer.limit(bytesToWrite)

            val chunk = ByteArray(outputByteBuffer.remaining())
            outputByteBuffer.get(chunk)
            outputStreams[stemName]?.write(chunk)
            outputDataSizes[stemName] = (outputDataSizes[stemName] ?: 0) + chunk.size
        }
    }

    private fun addWavHeader(pcmFile: File, wavFile: File, dataSize: Long, sampleRate: Int, channels: Int) {
        FileOutputStream(wavFile).use { out ->
            val bitsPerSample = 32
            val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
            writeWavHeader(out, dataSize, sampleRate.toLong(), channels, byteRate)
            FileInputStream(pcmFile).use { fis ->
                fis.copyTo(out)
            }
        }
    }

    @Throws(IOException::class)
    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long, longSampleRate: Long, channels: Int, byteRate: Long) {
        val totalDataLen = totalAudioLen + 36
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte(); header[5] = (totalDataLen shr 8 and 0xff).toByte(); header[6] = (totalDataLen shr 16 and 0xff).toByte(); header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 3; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte(); header[25] = (longSampleRate shr 8 and 0xff).toByte(); header[26] = (longSampleRate shr 16 and 0xff).toByte(); header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte(); header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 4).toByte(); header[33] = 0
        header[34] = 32; header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte(); header[41] = (totalAudioLen shr 8 and 0xff).toByte(); header[42] = (totalAudioLen shr 16 and 0xff).toByte(); header[43] = (totalAudioLen shr 24 and 0xff).toByte()
        out.write(header, 0, 44)
    }

    fun close() {
        interpreter?.close()
    }
}

//class StemSeparator(private val context: Context, private val modelName: String) {
//
//    private var interpreter: Interpreter? = null
//
//    fun init() {
//        try {
//            val model = loadModelFile(modelName)
//            val options = Interpreter.Options()
//            options.setNumThreads(Runtime.getRuntime().availableProcessors())
//            interpreter = Interpreter(model, options)
//        } catch (e: IOException) {
//            Log.e("StemSeparator", "Error loading TFLite model.", e)
//            throw e
//        }
//    }
//
//    @Throws(IOException::class)
//    private fun loadModelFile(modelName: String): MappedByteBuffer {
//        context.assets.openFd("$modelName.tflite").use { fileDescriptor ->
//            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
//                val fileChannel = inputStream.channel
//                return fileChannel.map(
//                    FileChannel.MapMode.READ_ONLY,
//                    fileDescriptor.startOffset,
//                    fileDescriptor.declaredLength
//                )
//            }
//        }
//    }
//
//    suspend fun separate(inputAudioUriString: String): Map<String, Uri> {
//        return withContext(Dispatchers.IO) {
//            val interpreter = this@StemSeparator.interpreter ?: throw IllegalStateException("Interpreter not initialized. Call init() first.")
//            decodeAndSeparateInChunks(interpreter, inputAudioUriString)
//        }
//    }
//
//    @OptIn(UnstableApi::class)
//    private fun decodeAndSeparateInChunks(interpreter: Interpreter, inputUriString: String): Map<String, Uri> {
//        val extractor = MediaExtractor()
//        val pfd = context.contentResolver.openFileDescriptor(Uri.parse(inputUriString), "r")
//            ?: throw IOException("Could not open file descriptor for URI: $inputUriString")
//
//        extractor.setDataSource(pfd.fileDescriptor)
//        pfd.close()
//
//        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
//            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
//        } ?: throw IOException("No audio track found in the file.")
//
//        extractor.selectTrack(trackIndex)
//        val inputFormat = extractor.getTrackFormat(trackIndex)
//        val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
//        val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
//        val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
//
//        val decoder = MediaCodec.createDecoderByType(mime)
//        decoder.configure(inputFormat, null, null, 0)
//        decoder.start()
//
//        val stemIndexMap = mapOf(0 to "bass", 1 to "drums", 2 to "other", 3 to "vocals", 4 to "piano")
//        val outputFiles = stemIndexMap.values.associateWith { File(context.filesDir, "$it.pcm") }
//        val outputStreams = outputFiles.mapValues { FileOutputStream(it.value) }
//        val outputDataSizes = stemIndexMap.values.associateWith { 0L }.toMutableMap()
//
//        val bufferInfo = MediaCodec.BufferInfo()
//        var isInputEos = false
//        var isOutputEos = false
//        val timeoutUs = 10000L
//
//        val chunkSizeBytes = sampleRate * channelCount * 4
//        val pcmChunkBuffer = ByteBuffer.allocate(chunkSizeBytes).order(ByteOrder.nativeOrder())
//
//        // CORRECCIÓN: Pre-asignar los buffers de salida una sola vez con el tamaño máximo.
//        val outputMap = HashMap<Int, Any>()
//        for (i in 0 until interpreter.outputTensorCount) {
//            outputMap[i] = ByteBuffer.allocateDirect(chunkSizeBytes).order(ByteOrder.nativeOrder())
//        }
//
//        while (!isOutputEos) {
//            if (!isInputEos) {
//                val inputBufferIndex = decoder.dequeueInputBuffer(timeoutUs)
//                if (inputBufferIndex >= 0) {
//                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
//                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
//                    if (sampleSize < 0) {
//                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
//                        isInputEos = true
//                    } else {
//                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
//                        extractor.advance()
//                    }
//                }
//            }
//
//            val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
//            if (outputBufferIndex >= 0) {
//                if (bufferInfo.size > 0) {
//                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
//
//                    while (outputBuffer.hasRemaining()) {
//                        val amountToRead = min(outputBuffer.remaining(), pcmChunkBuffer.remaining())
//                        val limit = outputBuffer.limit()
//                        outputBuffer.limit(outputBuffer.position() + amountToRead)
//                        pcmChunkBuffer.put(outputBuffer)
//                        outputBuffer.limit(limit)
//
//                        if (!pcmChunkBuffer.hasRemaining()) {
//                            processChunk(interpreter, pcmChunkBuffer, outputMap, outputStreams, outputDataSizes)
//                            pcmChunkBuffer.clear()
//                        }
//                    }
//                }
//                decoder.releaseOutputBuffer(outputBufferIndex, false)
//                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                    isOutputEos = true
//                    if (pcmChunkBuffer.position() > 0) {
//                        processChunk(interpreter, pcmChunkBuffer, outputMap, outputStreams, outputDataSizes)
//                    }
//                }
//            }
//        }
//
//        outputStreams.values.forEach { it.close() }
//        decoder.stop()
//        decoder.release()
//        extractor.release()
//
//        val stemUris = mutableMapOf<String, Uri>()
//        outputFiles.forEach { (name, pcmFile) ->
//            val wavFile = File(context.filesDir, "$name.wav")
//            val dataSize = outputDataSizes[name] ?: 0L
//            addWavHeader(pcmFile, wavFile, dataSize, sampleRate, channelCount)
//            stemUris[name] = Uri.fromFile(wavFile)
//            pcmFile.delete()
//        }
//        return stemUris
//    }
//
//    private fun processChunk(
//        interpreter: Interpreter,
//        pcmChunkBuffer: ByteBuffer,
//        outputMap: HashMap<Int, Any>,
//        outputStreams: Map<String, FileOutputStream>,
//        outputDataSizes: MutableMap<String, Long>
//    ) {
//        pcmChunkBuffer.flip()
//        val numSamples = pcmChunkBuffer.remaining() / (4 * 2)
//        if (numSamples == 0) return
//
//        val inputShape = intArrayOf(numSamples, 2)
//        interpreter.resizeInput(0, inputShape)
//        interpreter.allocateTensors()
//
//        // CORRECCIÓN: Rebobinar los buffers en lugar de re-asignarlos.
//        outputMap.values.forEach { (it as ByteBuffer).rewind() }
//
//        interpreter.runForMultipleInputsOutputs(arrayOf(pcmChunkBuffer), outputMap)
//
//        val stemIndexMap = mapOf(0 to "bass", 1 to "drums", 2 to "other", 3 to "vocals", 4 to "piano")
//        for ((index, data) in outputMap) {
//            val stemName = stemIndexMap[index] ?: continue
//            val outputByteBuffer = data as ByteBuffer
//
//            // CORRECCIÓN: Establecer el límite del buffer al tamaño real de los datos procesados
//            val outputByteCount = numSamples * 2 * 4
//            outputByteBuffer.limit(outputByteCount)
//
//            val chunk = ByteArray(outputByteBuffer.remaining())
//            outputByteBuffer.get(chunk)
//            outputStreams[stemName]?.write(chunk)
//            outputDataSizes[stemName] = (outputDataSizes[stemName] ?: 0) + chunk.size
//        }
//    }
//
//    private fun addWavHeader(pcmFile: File, wavFile: File, dataSize: Long, sampleRate: Int, channels: Int) {
//        FileOutputStream(wavFile).use { out ->
//            val bitsPerSample = 32
//            val byteRate = (sampleRate * channels * bitsPerSample / 8).toLong()
//            writeWavHeader(out, dataSize, sampleRate.toLong(), channels, byteRate)
//            FileInputStream(pcmFile).use { fis ->
//                fis.copyTo(out)
//            }
//        }
//    }
//
//    @Throws(IOException::class)
//    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long, longSampleRate: Long, channels: Int, byteRate: Long) {
//        val totalDataLen = totalAudioLen + 36
//        val header = ByteArray(44)
//        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
//        header[4] = (totalDataLen and 0xff).toByte(); header[5] = (totalDataLen shr 8 and 0xff).toByte(); header[6] = (totalDataLen shr 16 and 0xff).toByte(); header[7] = (totalDataLen shr 24 and 0xff).toByte()
//        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
//        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
//        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
//        header[20] = 3; header[21] = 0
//        header[22] = channels.toByte(); header[23] = 0
//        header[24] = (longSampleRate and 0xff).toByte(); header[25] = (longSampleRate shr 8 and 0xff).toByte(); header[26] = (longSampleRate shr 16 and 0xff).toByte(); header[27] = (longSampleRate shr 24 and 0xff).toByte()
//        header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8 and 0xff).toByte(); header[30] = (byteRate shr 16 and 0xff).toByte(); header[31] = (byteRate shr 24 and 0xff).toByte()
//        header[32] = (channels * 4).toByte(); header[33] = 0
//        header[34] = 32; header[35] = 0
//        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
//        header[40] = (totalAudioLen and 0xff).toByte(); header[41] = (totalAudioLen shr 8 and 0xff).toByte(); header[42] = (totalAudioLen shr 16 and 0xff).toByte(); header[43] = (totalAudioLen shr 24 and 0xff).toByte()
//        out.write(header, 0, 44)
//    }
//
//    fun close() {
//        interpreter?.close()
//    }
//}