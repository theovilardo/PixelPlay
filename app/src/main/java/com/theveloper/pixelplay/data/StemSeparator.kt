package com.theveloper.pixelplay.data

import android.content.Context
import android.util.Log
import com.theveloper.pixelplay.utils.WavHeader
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class StemSeparator(context: Context, modelName: String = "5stems.tflite") : Closeable {

    private var interpreter: Interpreter?
    val outputStemNames = listOf("vocals", "drums", "bass", "piano", "other")
    // --- CAMBIO RADICAL 1: Reducimos drásticamente el tamaño del fragmento ---
    private val CHUNK_SIZE_IN_SAMPLES = 16384 // ~0.18s de audio mono. Es un último intento.

    init {
        val model = loadModelFile(context, modelName)
        val options = Interpreter.Options()
        // --- CAMBIO RADICAL 2: Forzamos el uso de un solo hilo para reducir el uso de memoria ---
        options.setNumThreads(1)
        interpreter = Interpreter(model, options)
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        return context.assets.openFd(modelName).use { fd ->
            FileInputStream(fd.fileDescriptor).use { fis ->
                fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    fun separate(inputFile: File, outputDir: File): Result<List<File>> = runCatching {
        val currentInterpreter = interpreter ?: error("Interpreter has been closed.")

        val outputFiles = outputStemNames.map { File(outputDir, "$it.wav") }
        val outputStreams = outputFiles.map { FileOutputStream(it) }
        val totalBytesWritten = LongArray(outputStreams.size)

        outputStreams.forEach { it.write(WavHeader(0, 0, 44100, 16, 1).asByteArray()) }

        val channels = 1
        val samplesPerChunk = CHUNK_SIZE_IN_SAMPLES / channels
        val inputShape = intArrayOf(1, samplesPerChunk, channels)
        currentInterpreter.resizeInput(0, inputShape)
        currentInterpreter.allocateTensors()

        val inputBuffer = ByteBuffer.allocateDirect(CHUNK_SIZE_IN_SAMPLES * 4).order(ByteOrder.nativeOrder())
        val outputBuffers = mutableMapOf<Int, Any>()
        for (i in outputStemNames.indices) {
            val outputArray = Array(inputShape[0]) { Array(inputShape[1]) { FloatArray(inputShape[2]) } }
            outputBuffers[i] = outputArray
        }
        val readBuffer = ByteArray(CHUNK_SIZE_IN_SAMPLES * 2)

        FileInputStream(inputFile).use { inputStream ->
            inputStream.skip(44)

            var bytesRead: Int
            var chunkCount = 0
            while (inputStream.read(readBuffer).also { bytesRead = it } != -1) {
                if (bytesRead == 0) continue
                chunkCount++
                Log.d("StemSeparator", "Processing chunk #$chunkCount")

                val floatChunk = bytesToFloatArray(readBuffer, bytesRead)
                val finalChunk = if (floatChunk.size < CHUNK_SIZE_IN_SAMPLES) {
                    floatChunk.copyOf(CHUNK_SIZE_IN_SAMPLES)
                } else {
                    floatChunk
                }

                inputBuffer.rewind()
                inputBuffer.asFloatBuffer().put(finalChunk)

                try {
                    currentInterpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputBuffers)
                } catch (e: Exception) {
                    Log.e("StemSeparator", "TFLite Interpreter run failed", e)
                    throw e
                }

                outputBuffers.forEach { (index, value) ->
                    @Suppress("UNCHECKED_CAST")
                    val outputArray = (value as Array<Array<FloatArray>>)[0].flatMap { it.asIterable() }.toFloatArray()

                    val finalOutputData = if(outputArray.size > floatChunk.size) {
                        outputArray.copyOf(floatChunk.size)
                    } else {
                        outputArray
                    }

                    val byteData = floatArrayToBytes(finalOutputData)
                    outputStreams[index].write(byteData)
                    totalBytesWritten[index] += byteData.size.toLong()
                }
            }
        }

        outputStreams.forEach { it.close() }
        outputFiles.forEachIndexed { index, file ->
            val finalHeader = WavHeader(
                fileSize = (totalBytesWritten[index] + 36).toInt(),
                subchunk2Size = totalBytesWritten[index].toInt(),
                sampleRate = 44100, bitsPerSample = 16, numChannels = 1
            )
            finalHeader.updateHeader(file)
        }

        outputFiles
    }

    private fun bytesToFloatArray(bytes: ByteArray, count: Int): FloatArray {
        val floatArray = FloatArray(count / 2)
        val shortBuffer = ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in floatArray.indices) {
            floatArray[i] = shortBuffer.get().toFloat() / Short.MAX_VALUE
        }
        return floatArray
    }

    private fun floatArrayToBytes(floats: FloatArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(floats.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = byteBuffer.asShortBuffer()
        for (f in floats) {
            shortBuffer.put((f * Short.MAX_VALUE).toInt().toShort())
        }
        return byteBuffer.array()
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }
}

// only has FlexRFFT issue
//class StemSeparator(context: Context, modelName: String = "5stems.tflite") : Closeable {
//
//    private var interpreter: Interpreter?
//    val outputStemNames = listOf("vocals", "drums", "bass", "piano", "other")
//    // Un tamaño de fragmento fijo y seguro para la memoria (~0.37s de audio estéreo)
//    private val CHUNK_SIZE_IN_SAMPLES = 32768
//
//    init {
//        val model = loadModelFile(context, modelName)
//        val options = Interpreter.Options()
//        interpreter = Interpreter(model, options)
//    }
//
//    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
//        return context.assets.openFd(modelName).use { fd ->
//            FileInputStream(fd.fileDescriptor).use { fis ->
//                fis.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
//            }
//        }
//    }
//
//    fun separate(inputFile: File, outputDir: File): Result<List<File>> = runCatching {
//        val currentInterpreter = interpreter ?: error("Interpreter has been closed.")
//
//        val outputFiles = outputStemNames.map { File(outputDir, "$it.wav") }
//        val outputStreams = outputFiles.map { FileOutputStream(it) }
//        val totalBytesWritten = LongArray(outputStreams.size)
//
//        // Escribimos cabeceras WAV temporales que actualizaremos al final
//        outputStreams.forEach { it.write(WavHeader(0, 0, 44100, 16, 2).asByteArray()) }
//
//        // --- INICIO DE LA ARQUITECTURA EFICIENTE ---
//
//        // 1. Redimensionamos y asignamos tensores UNA SOLA VEZ para nuestro tamaño de fragmento fijo.
//        val channels = 2
//        val samplesPerChunk = CHUNK_SIZE_IN_SAMPLES / channels
//        val inputShape = intArrayOf(1, samplesPerChunk, channels)
//        currentInterpreter.resizeInput(0, inputShape)
//        currentInterpreter.allocateTensors()
//
//        // 2. Creamos los buffers de entrada y salida UNA SOLA VEZ para reutilizarlos en el bucle.
//        val inputBuffer = ByteBuffer.allocateDirect(CHUNK_SIZE_IN_SAMPLES * 4).order(ByteOrder.nativeOrder())
//        val outputBuffers = mutableMapOf<Int, Any>()
//        for (i in outputStemNames.indices) {
//            val outputArray = Array(inputShape[0]) { Array(inputShape[1]) { FloatArray(inputShape[2]) } }
//            outputBuffers[i] = outputArray
//        }
//        val readBuffer = ByteArray(CHUNK_SIZE_IN_SAMPLES * 2) // 2 bytes por muestra (short)
//
//        // 3. Procesamos el archivo en un bucle eficiente
//        FileInputStream(inputFile).use { inputStream ->
//            inputStream.skip(44) // Saltamos la cabecera del archivo de entrada
//
//            var bytesRead: Int
//            var chunkCount = 0
//            while (inputStream.read(readBuffer).also { bytesRead = it } != -1) {
//                if (bytesRead == 0) continue
//                chunkCount++
//                Log.d("StemSeparator", "Processing chunk #$chunkCount")
//
//                val floatChunk = bytesToFloatArray(readBuffer, bytesRead)
//
//                // Rellenamos con ceros si es el último fragmento y es más corto
//                val finalChunk = if (floatChunk.size < CHUNK_SIZE_IN_SAMPLES) {
//                    floatChunk.copyOf(CHUNK_SIZE_IN_SAMPLES)
//                } else {
//                    floatChunk
//                }
//
//                inputBuffer.rewind()
//                inputBuffer.asFloatBuffer().put(finalChunk)
//
//                currentInterpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputBuffers)
//
//                outputBuffers.forEach { (index, value) ->
//                    @Suppress("UNCHECKED_CAST")
//                    val outputArray = (value as Array<Array<FloatArray>>)[0].flatMap { it.asIterable() }.toFloatArray()
//
//                    // Asegurarnos de escribir solo la cantidad original de datos, sin el relleno
//                    val finalOutputData = if(outputArray.size > floatChunk.size) {
//                        outputArray.copyOf(floatChunk.size)
//                    } else {
//                        outputArray
//                    }
//
//                    val byteData = floatArrayToBytes(finalOutputData)
//                    outputStreams[index].write(byteData)
//                    totalBytesWritten[index] += byteData.size.toLong()
//                }
//            }
//        }
//        // --- FIN DE LA ARQUITECTURA EFICIENTE ---
//
//        // Cerramos todos los streams y actualizamos las cabeceras WAV con los tamaños correctos
//        outputStreams.forEach { it.close() }
//        outputFiles.forEachIndexed { index, file ->
//            val finalHeader = WavHeader(
//                fileSize = (totalBytesWritten[index] + 36).toInt(),
//                subchunk2Size = totalBytesWritten[index].toInt(),
//                sampleRate = 44100, bitsPerSample = 16, numChannels = 2
//            )
//            finalHeader.updateHeader(file)
//        }
//
//        outputFiles
//    }
//
//    private fun bytesToFloatArray(bytes: ByteArray, count: Int): FloatArray {
//        val floatArray = FloatArray(count / 2)
//        val shortBuffer = ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
//        for (i in floatArray.indices) {
//            floatArray[i] = shortBuffer.get().toFloat() / Short.MAX_VALUE
//        }
//        return floatArray
//    }
//
//    private fun floatArrayToBytes(floats: FloatArray): ByteArray {
//        val byteBuffer = ByteBuffer.allocate(floats.size * 2).order(ByteOrder.LITTLE_ENDIAN)
//        val shortBuffer = byteBuffer.asShortBuffer()
//        for (f in floats) {
//            shortBuffer.put((f * Short.MAX_VALUE).toInt().toShort())
//        }
//        return byteBuffer.array()
//    }
//
//    override fun close() {
//        interpreter?.close()
//        interpreter = null
//    }
//}


//notworking but not crashing
//class StemSeparator(context: Context, modelName: String = "5stems.tflite") {
//
//    private val interpreter: Interpreter
//
//    // Nombres de los stems de salida según el modelo Spleeter de 5 stems
//    val outputStemNames = listOf("vocals", "drums", "bass", "piano", "other")
//
//    init {
//        val model = loadModelFile(context, modelName)
//        val options = Interpreter.Options()
//        // Opcional: Usar delegados para aceleración por hardware (GPU/NNAPI)
//        // options.addDelegate(GpuDelegate())
//        interpreter = Interpreter(model, options)
//    }
//
//    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
//        val fileDescriptor = context.assets.openFd(modelName)
//        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//        val fileChannel = inputStream.channel
//        val startOffset = fileDescriptor.startOffset
//        val declaredLength = fileDescriptor.declaredLength
//        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
//    }
//
//    /**
//     * Separa la forma de onda de audio en sus stems constituyentes.
//     * @param waveform El audio de entrada como un FloatArray estéreo.
//     * @return Un mapa que asocia el nombre de cada stem con su FloatArray de audio.
//     */
//    fun separate(waveform: FloatArray): Map<String, FloatArray> {
//        // El modelo de Spleeter espera una forma específica: [1, num_samples, 2]
//        // donde 1 es el batch size, num_samples la longitud, y 2 para estéreo.
//        val inputShape = interpreter.getInputTensor(0).shape()
//        val numSamples = inputShape[1]
//
//        // Preparamos el buffer de entrada con el tamaño esperado por el modelo
//        val inputBuffer = ByteBuffer.allocateDirect(1 * numSamples * 2 * 4).order(ByteOrder.nativeOrder())
//        inputBuffer.asFloatBuffer().put(waveform.copyOf(numSamples * 2))
//
//        // Preparamos los buffers de salida
//        val outputBuffers = mutableMapOf<Int, Any>()
//        for (i in outputStemNames.indices) {
//            val outputShape = interpreter.getOutputTensor(i).shape()
//            // La salida es [1, num_samples, 2]
//            val outputArray = Array(1) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
//            outputBuffers[i] = outputArray
//        }
//
//        // Ejecutamos la inferencia
//        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputBuffers)
//
//        // Procesamos los resultados
//        val stems = mutableMapOf<String, FloatArray>()
//        outputBuffers.forEach { (index, value) ->
//            val stemName = outputStemNames[index]
//            val outputArray = (value as Array<Array<FloatArray>>)[0] // Extraemos el batch
//            // Aplanamos el array estéreo [num_samples, 2] a un FloatArray de una dimensión
//            val flattenedStem = outputArray.flatMap { it.asIterable() }.toFloatArray()
//            stems[stemName] = flattenedStem
//        }
//
//        return stems
//    }
//}