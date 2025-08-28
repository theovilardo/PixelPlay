package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.utils.LogUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncManager: SyncManager,
    musicRepository: MusicRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val isSetupComplete: StateFlow<Boolean> = userPreferencesRepository.initialSetupDoneFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Un Flow que emite `true` si el SyncWorker está encolado o en ejecución.
     * Ideal para mostrar un indicador de carga.
     */
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Asumimos que podría estar sincronizando al inicio
        )

    /**
     * Un Flow que emite `true` si la base de datos de Room no tiene canciones.
     * Nos ayuda a saber si es la primera vez que se abre la app.
     */
    val isLibraryEmpty: StateFlow<Boolean> = musicRepository
        // --- CORRECCIÓN AQUÍ ---
        // Llamamos al método correcto pidiendo solo la primera canción para verificar si la biblioteca está vacía.
        .getAudioFiles()
        .map { it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * Función para iniciar la sincronización de la biblioteca de música.
     * Se debe llamar después de que los permisos hayan sido concedidos.
     */
    fun startSync() {
        LogUtils.i(this, "startSync called")
        syncManager.sync()
    }
}