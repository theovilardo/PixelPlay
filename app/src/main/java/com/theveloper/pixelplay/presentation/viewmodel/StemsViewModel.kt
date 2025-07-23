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
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.StemSeparator
import com.theveloper.pixelplay.utils.AudioFileProvider
import java.io.File

/**
 * Define los posibles estados de la UI para la pantalla de separación de stems.
 */
sealed class StemsUiState {
    object Idle : StemsUiState()
    object Loading : StemsUiState()
    /**
     * Estado de éxito que contiene un mapa con los nombres de los stems y las rutas a sus archivos.
     * @param stemFiles Un mapa donde la clave es el nombre del stem (ej: "vocals") y el valor es la ruta al archivo .wav.
     */
    data class Success(val stemFiles: Map<String, String>) : StemsUiState()
    data class Error(val message: String) : StemsUiState()
}

/**
 * ViewModel para gestionar la lógica de separación de stems.
 * Se integra con la clase StemSeparator para realizar la operación.
 */
@UnstableApi
@HiltViewModel
class StemsViewModel @Inject constructor(
    private val application: Application
) : ViewModel() {

    private val _uiState = mutableStateOf<StemsUiState>(StemsUiState.Idle)
    val uiState: State<StemsUiState> = _uiState

    // El StemSeparator se crea de forma perezosa y se reutiliza en la vida del ViewModel.
    private val stemSeparator by lazy { StemSeparator(application) }

    /**
     * Inicia el proceso de separación de stems para un archivo de audio dado.
     *
     * @param uri El URI del archivo de audio que se va a procesar.
     * @param modelName El nombre del modelo TFLite a utilizar (por ejemplo, "4stem").
     */
    fun startSeparation(uri: Uri, modelName: String = "4stem") {
        viewModelScope.launch {
            _uiState.value = StemsUiState.Loading
            try {
                // La función 'separate' ya se ejecuta en un hilo de fondo (Dispatchers.IO)
                // y maneja toda la creación y limpieza de archivos.
                val resultPaths = stemSeparator.separate(uri, modelName)

                // La separación se considera exitosa si el mapa de resultados no está vacío.
                if (resultPaths.isNotEmpty()) {
                    _uiState.value = StemsUiState.Success(resultPaths)
                } else {
                    // Si el mapa está vacío, significa que hubo un error manejado internamente en StemSeparator.
                    _uiState.value = StemsUiState.Error("La separación falló. Revisa los logs para más detalles.")
                }
            } catch (e: Exception) {
                // Este bloque catch captura excepciones inesperadas, como problemas al cargar el modelo.
                Log.e("StemsViewModel", "Ocurrió una excepción durante el proceso de separación", e)
                _uiState.value = StemsUiState.Error("Ocurrió un error inesperado: ${e.message}")
            }
        }
    }
}