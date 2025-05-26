package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.DirectoryItem
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.MusicRepositoryImpl
// Usar la implementación para acceder a getAllUniqueAudioDirectories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val directoryItems: List<DirectoryItem> = emptyList(),
    val isLoadingDirectories: Boolean = true,
    val globalThemePreference: String = ThemePreference.DYNAMIC, // Default a DYNAMIC
    val playerThemePreference: String = ThemePreference.GLOBAL
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository, // Inyectar la interfaz, no la implementación concreta
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Observar las preferencias de tema directamente
    init {
        viewModelScope.launch {
            userPreferencesRepository.globalThemePreferenceFlow.collect { preference ->
                _uiState.update { it.copy(globalThemePreference = preference) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.playerThemePreferenceFlow.collect { preference ->
                _uiState.update { it.copy(playerThemePreference = preference) }
            }
        }

        // Cargar los directorios y observar cambios en los directorios permitidos
        loadDirectoryPreferences()
    }

    private fun loadDirectoryPreferences() {
        viewModelScope.launch {
            // Combina el flujo de directorios permitidos con la lista de todos los directorios encontrados.
            // Usamos flow {} para envolver la llamada suspend a getAllUniqueAudioDirectories()
            // y onStart para emitir un estado de carga inicial.
            userPreferencesRepository.allowedDirectoriesFlow.combine(
                flow {
                    // Emitir true para isLoading antes de empezar la carga de directorios
                    emit(musicRepository.getAllUniqueAudioDirectories())
                }.onStart { _uiState.update { it.copy(isLoadingDirectories = true) } } // Mostrar indicador de carga al inicio del flujo
            ) { allowedDirs, allFoundDirs ->
                // Esta parte se ejecuta cada vez que allowedDirs o allFoundDirs cambian.
                // allFoundDirs solo cambiará la primera vez o si el repositorio lo re-escanea.
                val initialSetupDone = userPreferencesRepository.initialSetupDoneFlow.first() // Obtener el estado actual

                allFoundDirs.map { dirPath ->
                    val isAllowed = if (!initialSetupDone) true else allowedDirs.contains(dirPath)
                    DirectoryItem(path = dirPath, isAllowed = isAllowed)
                }.sortedBy { it.displayName } // Ordenar alfabéticamente
            }.catch { e ->
                // Manejar error durante la carga o procesamiento de directorios
                _uiState.update { it.copy(isLoadingDirectories = false, directoryItems = emptyList()) } // Indicar fallo y limpiar lista
                // Loggear el error
            }.collectLatest { directoryItems ->
                // Recopilar la lista de DirectoryItem generada y actualizar el estado de la UI
                _uiState.update { it.copy(directoryItems = directoryItems, isLoadingDirectories = false) } // Ocultar indicador de carga
            }
        }
    }

    // Método para alternar el estado de un directorio y guardar en preferencias
    fun toggleDirectoryAllowed(directoryItem: DirectoryItem) {
        viewModelScope.launch {
            val currentAllowed = userPreferencesRepository.allowedDirectoriesFlow.first().toMutableSet()
            if (directoryItem.isAllowed) {
                currentAllowed.remove(directoryItem.path)
            } else {
                currentAllowed.add(directoryItem.path)
            }
            userPreferencesRepository.updateAllowedDirectories(currentAllowed)

            // --- ¡AÑADIR ESTA LÍNEA! ---
            musicRepository.invalidateCachesDependentOnAllowedDirectories()
            // --------------------------

            // El flujo allowedDirectoriesFlow en loadDirectoryPreferences() se actualizará,
            // y también lo hará la lista de DirectoryItem en la UI de Ajustes.
            // La invalidación de caché asegura que PlayerViewModel, etc., obtengan datos frescos
            // la próxima vez que consulten el repositorio.
        }
    }

//    fun toggleDirectoryAllowed(directoryItem: DirectoryItem) {
//        viewModelScope.launch {
//            val currentAllowed = userPreferencesRepository.allowedDirectoriesFlow.first().toMutableSet()
//            if (directoryItem.isAllowed) {
//                currentAllowed.remove(directoryItem.path)
//            } else {
//                currentAllowed.add(directoryItem.path)
//            }
//            // Guardar el conjunto actualizado en preferencias
//            userPreferencesRepository.updateAllowedDirectories(currentAllowed)
//            // El flujo allowedDirectoriesFlow en loadDirectoryPreferences() detectará el cambio
//            // y regenerará la lista directoryItems automáticamente.
//        }
//    }

    // Método para guardar la preferencia de tema global
    fun setGlobalThemePreference(preference: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGlobalThemePreference(preference)
            // El flujo globalThemePreferenceFlow en init{} detectará el cambio y actualizará _uiState
        }
    }

    // Método para guardar la preferencia de tema del reproductor
    fun setPlayerThemePreference(preference: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPlayerThemePreference(preference)
            // El flujo playerThemePreferenceFlow en init{} detectará el cambio y actualizará _uiState
        }
    }
}