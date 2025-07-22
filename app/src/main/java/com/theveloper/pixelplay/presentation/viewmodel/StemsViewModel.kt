package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linc.amplituda.Amplituda
import com.linc.amplituda.Cache
import com.theveloper.pixelplay.data.StemSeparator
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.AudioDecoder
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.compose.runtime.State
import com.theveloper.pixelplay.utils.AudioFileProvider
import java.io.File

sealed class StemsUiState {
    object Idle : StemsUiState()
    object Loading : StemsUiState()
    // El estado de éxito ahora contiene una lista de archivos, no FloatArrays
    data class Success(val stemFiles: List<File>) : StemsUiState()
    data class Error(val message: String) : StemsUiState()
}

@HiltViewModel
class StemsViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _uiState = mutableStateOf<StemsUiState>(StemsUiState.Idle)
    val uiState: State<StemsUiState> = _uiState

    // El StemSeparator es seguro crearlo una vez gracias a 'lazy'
    private val stemSeparator by lazy { StemSeparator(application) }

    override fun onCleared() {
        super.onCleared()
        stemSeparator.close()
    }

    fun startSeparation(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = StemsUiState.Loading

            // Todo el trabajo pesado se mueve a un hilo de fondo (IO o Default)
            val separationResult = withContext(Dispatchers.IO) {
                runCatching {
                    // 1. Crear directorio de salida temporal y limpiarlo
                    val outputDir = File(application.cacheDir, "stems_output").apply { mkdirs() }
                    outputDir.listFiles()?.forEach { it.delete() }

                    // 2. Convertir el audio de entrada a un archivo WAV temporal
                    val wavInputFile = AudioFileProvider.getWavFile(application, uri).getOrThrow()

                    // 3. Procesar el archivo WAV por fragmentos y obtener los archivos de stems
                    val stemFiles = stemSeparator.separate(wavInputFile, outputDir).getOrThrow()

                    // 4. Limpiar el archivo WAV de entrada que ya no necesitamos
                    wavInputFile.delete()

                    stemFiles
                }
            }

            // Actualizar la UI de vuelta en el hilo principal
            separationResult.onSuccess { files ->
                _uiState.value = StemsUiState.Success(files)
            }.onFailure { e ->
                e.printStackTrace()
                _uiState.value = StemsUiState.Error("Separation failed: ${e.message}")
            }
        }
    }
}

// Define los estados de la UI para la separación
//sealed class StemsUiState {
//    object Idle : StemsUiState()
//    object Loading : StemsUiState()
//    data class Success(val stems: Map<String, FloatArray>) : StemsUiState()
//    data class Error(val message: String) : StemsUiState()
//}
//
//@HiltViewModel
//class StemsViewModel @Inject constructor(
//    private val application: Application
//) : ViewModel() {
//
//    private val _uiState = mutableStateOf<StemsUiState>(StemsUiState.Idle) // Declara como MutableState
//    val uiState: State<StemsUiState> = _uiState // Expone como State (inmutable desde fuera)
//
//    private val stemSeparator by lazy {
//        StemSeparator(getApplication(application))
//    }
//
//    fun startSeparation(uri: Uri) {
//        viewModelScope.launch {
//            _uiState.value = StemsUiState.Loading // Actualiza usando .value
//
//            // 1. Decodificar el archivo de audio
//            val decodeResult = AudioDecoder.decodeToFloatArray(getApplication(application), uri)
//
//            decodeResult.onSuccess { waveform ->
//                // 2. Separar los stems usando el modelo
//                try {
//                    val stems = stemSeparator.separate(waveform)
//                    _uiState.value = StemsUiState.Success(stems) // Actualiza usando .value
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                    _uiState.value = StemsUiState.Error("Failed to run model inference: ${e.message}") // Actualiza usando .value
//                }
//            }.onFailure {
//                _uiState.value = StemsUiState.Error("Failed to decode audio: ${it.message}") // Actualiza usando .value
//            }
//        }
//    }
//}