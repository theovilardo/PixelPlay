package com.theveloper.pixelplay.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


/**
 * Clase para separar un archivo de audio en sus componentes (stems) usando un modelo TFLite.
 * Esta implementación utiliza Media3 para la decodificación de audio, eliminando la dependencia de FFmpeg.
 *
 * @param context El contexto de la aplicación.
 */
@UnstableApi // Media3 Transformer todavía es inestable.
class StemSeparator(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val targetSampleRate = 44100
    private val targetChannelCount = 2 // Estéreo
    private val pcmEncoding = androidx.media3.common.C.ENCODING_PCM_FLOAT // 32-bit float

    /**
     * Carga el modelo TFLite desde la carpeta de assets.
     * @param modelName El nombre del modelo sin la extensión .tflite.
     * @return Un MappedByteBuffer que contiene el modelo.
     */
    @Throws(IOException::class)
    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("$modelName.tflite")
        val fileChannel = FileInputStream(fileDescriptor.fileDescriptor).channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Decodifica un archivo de audio a formato PCM crudo (32-bit float, 44100Hz, estéreo) usando Media3 Transformer.
     * El modelo TFLite requiere este formato específico para funcionar.
     * @param sourceUri El URI del archivo de audio de origen.
     * @return Un par que contiene las dimensiones del audio y los datos del waveform, o null si falla.
     */
    private suspend fun decodeAudioToPcm(sourceUri: Uri): Pair<IntArray, Array<FloatArray>>? {
        val tempFile = withContext(Dispatchers.IO) {
            File.createTempFile("decoded_audio", ".pcm", context.cacheDir)
        }

        try {
            // 1. Configurar el Transformer de Media3 para exportar a PCM crudo.
            val mediaItem = MediaItem.fromUri(sourceUri)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true) // Nos aseguramos de procesar solo el audio
                .build()

            // Usamos una coroutine para esperar el resultado asíncrono del Transformer.
            val exportResult = suspendCancellableCoroutine<ExportResult> { continuation ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        Timber.tag("StemSeparator").d("Media3 decoding successful.")
                        continuation.resume(result)
                    }
                    override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                        Timber.tag("StemSeparator").e(exception, "Media3 decoding failed.")
                        continuation.resumeWithException(exception)
                    }
                }

                val transformer = Transformer.Builder(context)
                    // Establecemos el tipo MIME de audio directamente en el constructor.
                    .setAudioMimeType(MimeTypes.AUDIO_RAW) // Salida en PCM
                    .addListener(listener) // Se añade el listener al builder
                    .build()

                // La llamada a start ya no incluye el listener como argumento.
                transformer.start(editedMediaItem, tempFile.absolutePath)

                continuation.invokeOnCancellation {
                    transformer.cancel()
                }
            }

            // 2. Leer los datos PCM del archivo temporal.
            val dataList = ArrayList<FloatArray>()
            tempFile.inputStream().buffered().use { inputStream ->
                // 8 bytes por frame: 4 bytes por float (muestra) * 2 canales (estéreo)
                val buffer = ByteArray(8)
                while (inputStream.read(buffer) != -1) {
                    val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
                    val leftChannel = byteBuffer.float
                    val rightChannel = byteBuffer.float
                    dataList.add(floatArrayOf(leftChannel, rightChannel))
                }
            }

            if (dataList.isEmpty()) {
                Timber.tag("StemSeparator").e("Decoded PCM data is empty.")
                return null
            }

            val dims = intArrayOf(dataList.size, targetChannelCount) // [número de frames, número de canales]
            val data = dataList.toTypedArray()
            return Pair(dims, data)

        } catch (e: Exception) {
            Timber.tag("StemSeparator").e(e, "An error occurred during Media3 decoding.")
            return null
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Guarda un array de datos de audio PCM crudo en un archivo WAV.
     * Esta función escribe manualmente el encabezado WAV, eliminando la necesidad de FFmpeg o Media3 para la codificación.
     * @param data Los datos de audio del stem a guardar.
     * @param outputFilePath La ruta absoluta del archivo WAV de salida.
     */
    @Throws(IOException::class)
    private fun saveStemToWav(data: Array<FloatArray>, outputFilePath: String) {
        val numFrames = data.size
        val numChannels = targetChannelCount
        val sampleRate = targetSampleRate
        val bitsPerSample = 32 // Para PCM float

        val byteRate = sampleRate * numChannels * bitsPerSample / 8
        val blockAlign = numChannels * bitsPerSample / 8
        val dataSize = numFrames * numChannels * bitsPerSample / 8
        val fileSize = dataSize + 36 // 36 bytes para el encabezado sin la data

        FileOutputStream(outputFilePath).use { fileStream ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF chunk descriptor
            header.put("RIFF".toByteArray())
            header.putInt(fileSize)
            header.put("WAVE".toByteArray())

            // "fmt " sub-chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // Sub-chunk size for PCM
            header.putShort(3) // Audio format (3 for float PCM)
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())

            // "data" sub-chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fileStream.write(header.array())

            // Escribir los datos de audio
            val dataBuffer = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (frame in data) {
                dataBuffer.putFloat(frame.getOrElse(0) { 0f }) // Canal izquierdo
                dataBuffer.putFloat(frame.getOrElse(1) { 0f }) // Canal derecho
            }
            fileStream.write(dataBuffer.array())
        }
        Log.d("StemSeparator", "WAV file saved successfully to $outputFilePath")
    }

    /**
     * Realiza el proceso completo de separación de stems.
     * @param audioUri El URI del archivo de audio a separar.
     * @param modelName El nombre del modelo TFLite a utilizar (ej: "4stem").
     * @return Un mapa con los nombres de los stems (ej: "vocals") y las rutas a los archivos WAV generados.
     */
    suspend fun separate(audioUri: Uri, modelName: String): Map<String, String> = withContext(Dispatchers.IO) {
        val outputPaths = mutableMapOf<String, String>()
        try {
            // 1. Inicializar el intérprete de TFLite
            Log.d("StemSeparator", "Loading model: $modelName")
            interpreter = Interpreter(loadModelFile(modelName))
            Log.d("StemSeparator", "Model loaded successfully.")

            // 2. Decodificar el archivo de audio a datos PCM crudos usando Media3
            val waveform = decodeAudioToPcm(audioUri)
            if (waveform == null) {
                Log.e("StemSeparator", "Failed to decode audio to PCM using Media3.")
                return@withContext emptyMap()
            }
            val dims = waveform.first
            val data = waveform.second
            Log.d("StemSeparator", "Audio decoded. Frames: ${dims[0]}, Channels: ${dims[1]}")

            // 3. Preparar las entradas y salidas del modelo TFLite
            interpreter?.resizeInput(0, dims)
            interpreter?.allocateTensors()

            val modelOutputMap = HashMap<Int, Any>()
            val outputTensorCount = interpreter?.outputTensorCount ?: 0
            for (i in 0 until outputTensorCount) {
                modelOutputMap[i] = Array(dims[0]) { FloatArray(dims[1]) }
            }
            Log.d("StemSeparator", "TFLite inputs/outputs prepared. Output tensors: $outputTensorCount")

            // 4. Ejecutar la inferencia del modelo
            Log.d("StemSeparator", "Running model inference...")
            interpreter?.runForMultipleInputsOutputs(arrayOf(data), modelOutputMap)
            Log.d("StemSeparator", "Model inference finished.")

            // 5. Guardar cada stem de salida en un archivo WAV
            // El orden de los stems para el modelo spleeter:4stems es: vocals, drums, bass, other
            val stemNames = listOf("vocals", "drums", "bass", "other")
            for ((index, stemData) in modelOutputMap) {
                // Usar una conversión segura (safe cast) para evitar el warning de "Unchecked cast"
                val typedStemData = stemData as? Array<FloatArray>
                if (typedStemData != null) {
                    val stemName = stemNames.getOrElse(index) { "stem_$index" }
                    val outputFile = File(context.filesDir, "${System.currentTimeMillis()}_$stemName.wav")
                    saveStemToWav(typedStemData, outputFile.absolutePath)
                    outputPaths[stemName] = outputFile.absolutePath
                } else {
                    Log.w("StemSeparator", "Unexpected data type for stem $index. Skipping.")
                }
            }

        } catch (e: Exception) {
            Log.e("StemSeparator", "An error occurred during the separation process", e)
        } finally {
            // 6. Liberar recursos
            interpreter?.close()
            interpreter = null
            Log.d("StemSeparator", "Interpreter closed and resources released.")
        }
        outputPaths
    }
}


///**
// * Gestiona la separación de pistas de audio (stems) utilizando un modelo TFLite de Spleeter.
// * Esta clase se encarga de decodificar el audio de entrada, procesarlo con el modelo
// * y guardar las pistas resultantes como archivos WAV.
// *
// * @param context El contexto de la aplicación, necesario para acceder a los assets y al almacenamiento.
// */
//class StemSeparator(private val context: Context) {
//
//    private var interpreter: Interpreter? = null
//
//    /**
//     * Carga el modelo TFLite desde la carpeta de assets.
//     * @param modelName El nombre del modelo sin la extensión .tflite.
//     * @return Un MappedByteBuffer que contiene el modelo.
//     */
//    @Throws(IOException::class)
//    private fun loadModelFile(modelName: String): MappedByteBuffer {
//        val fileDescriptor = context.assets.openFd("$modelName.tflite")
//        val fileChannel = FileInputStream(fileDescriptor.fileDescriptor).channel
//        return fileChannel.map(
//            FileChannel.MapMode.READ_ONLY,
//            fileDescriptor.startOffset,
//            fileDescriptor.declaredLength
//        )
//    }
//
//    /**
//     * Decodifica un archivo de audio a formato PCM crudo (32-bit float, 44100Hz, estéreo) usando FFmpeg-Kit.
//     * El modelo TFLite requiere este formato específico para funcionar.
//     * @param sourcePath La ruta absoluta al archivo de audio de origen.
//     * @return Un par que contiene las dimensiones del audio y los datos del waveform, o null si falla.
//     */
//    private fun decodeAudioToPcm(sourcePath: String): Pair<IntArray, Array<FloatArray>>? {
//        val tempFile = File.createTempFile("decoded_audio", ".pcm", context.cacheDir)
//        // El comando -y sobrescribe el archivo de salida si ya existe.
//        val command = "-y -i \"$sourcePath\" -f f32le -acodec pcm_f32le -ac 2 -ar 44100 \"${tempFile.absolutePath}\""
//
//        Log.d("StemSeparator", "Executing FFmpeg decode command: $command")
//        val session = FFmpegKit.execute(command)
//
//        return if (ReturnCode.isSuccess(session.returnCode)) {
//            Log.d("StemSeparator", "FFmpeg decoding successful.")
//            val dataList = ArrayList<FloatArray>()
//            tempFile.inputStream().buffered().use { inputStream ->
//                // 8 bytes por frame: 4 bytes por float (muestra) * 2 canales (estéreo)
//                val buffer = ByteArray(8)
//                while (inputStream.read(buffer) != -1) {
//                    val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
//                    val leftChannel = byteBuffer.float
//                    val rightChannel = byteBuffer.float
//                    dataList.add(floatArrayOf(leftChannel, rightChannel))
//                }
//            }
//            tempFile.delete()
//
//            if (dataList.isEmpty()) {
//                Log.e("StemSeparator", "Decoded PCM data is empty.")
//                null
//            } else {
//                val dims = intArrayOf(dataList.size, 2) // [número de frames, número de canales]
//                val data = dataList.toTypedArray()
//                Pair(dims, data)
//            }
//        } else {
//            Log.e("StemSeparator", "FFmpeg decoding failed. RC: ${session.returnCode}. Log: ${session.allLogsAsString}")
//            tempFile.delete()
//            null
//        }
//    }
//
//    /**
//     * Guarda un array de datos de audio PCM crudo en un archivo WAV usando FFmpeg-Kit.
//     * @param data Los datos de audio del stem a guardar.
//     * @param outputFilePath La ruta absoluta del archivo WAV de salida.
//     */
//    private fun saveStemToWav(data: Array<FloatArray>, outputFilePath: String) {
//        val tempFile = File.createTempFile("encoded_stem", ".pcm", context.cacheDir)
//        tempFile.outputStream().buffered().use { outputStream ->
//            val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
//            for (frame in data) {
//                buffer.clear()
//                buffer.putFloat(frame[0]) // Canal izquierdo
//                buffer.putFloat(frame[1]) // Canal derecho
//                outputStream.write(buffer.array())
//            }
//        }
//
//        val command = "-y -f f32le -acodec pcm_f32le -ac 2 -ar 44100 -i \"${tempFile.absolutePath}\" \"$outputFilePath\""
//        Log.d("StemSeparator", "Executing FFmpeg encode command: $command")
//        val session = FFmpegKit.execute(command)
//
//        if (!ReturnCode.isSuccess(session.returnCode)) {
//            Log.e("StemSeparator", "FFmpeg encoding failed for $outputFilePath. RC: ${session.returnCode}. Log: ${session.allLogsAsString}")
//        } else {
//            Log.d("StemSeparator", "FFmpeg encoding successful for $outputFilePath")
//        }
//        tempFile.delete()
//    }
//
//    /**
//     * Realiza el proceso completo de separación de stems.
//     * @param audioUri El URI del archivo de audio a separar.
//     * @param modelName El nombre del modelo TFLite a utilizar (ej: "4stem").
//     * @return Un mapa con los nombres de los stems (ej: "vocals") y las rutas a los archivos WAV generados.
//     */
//    suspend fun separate(audioUri: Uri, modelName: String): Map<String, String> = withContext(
//        Dispatchers.IO) {
//        val outputPaths = mutableMapOf<String, String>()
//        try {
//            // 1. Inicializar el intérprete de TFLite
//            Log.d("StemSeparator", "Loading model: $modelName")
//            interpreter = Interpreter(loadModelFile(modelName))
//            Log.d("StemSeparator", "Model loaded successfully.")
//
//            // 2. Obtener una ruta de archivo real desde el URI
//            val audioPath = AudioFileProvider.getPathFromUri(context, audioUri)
//            if (audioPath == null) {
//                Log.e("StemSeparator", "Could not get a valid file path from URI: $audioUri")
//                return@withContext emptyMap()
//            }
//            Log.d("StemSeparator", "Audio path resolved to: $audioPath")
//
//            // 3. Decodificar el archivo de audio a datos PCM crudos
//            val waveform = decodeAudioToPcm(audioPath)
//            if (waveform == null) {
//                Log.e("StemSeparator", "Failed to decode audio to PCM.")
//                return@withContext emptyMap()
//            }
//            val dims = waveform.first
//            val data = waveform.second
//            Log.d("StemSeparator", "Audio decoded. Frames: ${dims[0]}, Channels: ${dims[1]}")
//
//            // 4. Preparar las entradas y salidas del modelo TFLite
//            interpreter?.resizeInput(0, dims)
//            interpreter?.allocateTensors()
//
//            val modelOutputMap = HashMap<Int, Any>()
//            val outputTensorCount = interpreter?.outputTensorCount ?: 0
//            for (i in 0 until outputTensorCount) {
//                modelOutputMap[i] = Array(dims[0]) { FloatArray(dims[1]) }
//            }
//            Log.d("StemSeparator", "TFLite inputs/outputs prepared. Output tensors: $outputTensorCount")
//
//            // 5. Ejecutar la inferencia del modelo
//            Log.d("StemSeparator", "Running model inference...")
//            interpreter?.runForMultipleInputsOutputs(arrayOf(data), modelOutputMap)
//            Log.d("StemSeparator", "Model inference finished.")
//
//            // 6. Guardar cada stem de salida en un archivo WAV
//            // El orden de los stems para el modelo spleeter:4stems es: vocals, drums, bass, other
//            val stemNames = listOf("vocals", "drums", "bass", "other")
//            for ((index, stemData) in modelOutputMap) {
//                val stemName = stemNames.getOrElse(index) { "stem_$index" }
//                val outputFile = File(context.filesDir, "${System.currentTimeMillis()}_$stemName.wav")
//                saveStemToWav(stemData as Array<FloatArray>, outputFile.absolutePath)
//                outputPaths[stemName] = outputFile.absolutePath
//            }
//
//        } catch (e: Exception) {
//            Log.e("StemSeparator", "An error occurred during the separation process", e)
//        } finally {
//            // 7. Liberar recursos
//            interpreter?.close()
//            interpreter = null
//            Log.d("StemSeparator", "Interpreter closed and resources released.")
//        }
//        outputPaths
//    }
//}

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