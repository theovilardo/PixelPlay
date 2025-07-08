package com.theveloper.pixelplay.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.linc.amplituda.Amplituda
import com.linc.amplituda.AmplitudaResult
import com.linc.amplituda.Cache
import com.theveloper.pixelplay.data.StemSeparator
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.presentation.viewmodel.exts.DeckController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TrackStems(
    val vocals: Boolean = true,
    val instrumental: Boolean = true,
    val bass: Boolean = true,
    val drums: Boolean = true
)

data class DeckState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val volume: Float = 1f,
    val speed: Float = 1f,
    val stems: TrackStems = TrackStems(),
    val isLoading: Boolean = false,
    val loadingMessage: String = "",
    val stemWaveforms: Map<String, List<Int>> = emptyMap()
)

data class MashupUiState(
    val deck1: DeckState = DeckState(),
    val deck2: DeckState = DeckState(),
    val crossfaderValue: Float = 0f,
    val allSongs: List<Song> = emptyList(),
    val showSongPickerForDeck: Int? = null
)

@HiltViewModel
class MashupViewModel @Inject constructor(
    private val application: Application,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MashupUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var deck1Controller: DeckController
    private lateinit var deck2Controller: DeckController

    private var progressJob: Job? = null

    init {
        initializeDecks()
        loadAllSongs()
        startProgressUpdater()
    }

    private fun initializeDecks() {
        deck1Controller = DeckController(application) { isPlaying -> _uiState.update { it.copy(deck1 = it.deck1.copy(isPlaying = isPlaying)) } }
        deck2Controller = DeckController(application) { isPlaying -> _uiState.update { it.copy(deck2 = it.deck2.copy(isPlaying = isPlaying)) } }
    }

    private fun loadAllSongs() {
        viewModelScope.launch {
            musicRepository.getAudioFiles().collect { songs ->
                _uiState.update { it.copy(allSongs = songs) }
            }
        }
    }

    fun selectSong(song: Song, deck: Int) {
        closeSongPicker()
        viewModelScope.launch(Dispatchers.IO) {
            // Reemplaza "model_4stems" con el nombre de tu archivo de modelo en assets.
            val separator = StemSeparator(application, "5stems")
            try {
                updateDeckState(deck) { it.copy(isLoading = true, loadingMessage = "Initializing...", song = song, stemWaveforms = emptyMap()) }
                separator.init()

                updateDeckState(deck) { it.copy(loadingMessage = "Separating stems...") }
                val stemFiles = separator.separate(song.contentUriString)

                if (stemFiles.isNotEmpty()) {
                    updateDeckState(deck) { it.copy(loadingMessage = "Loading tracks...") }
                    if (deck == 1) deck1Controller.loadStems(stemFiles) else deck2Controller.loadStems(stemFiles)

                    updateDeckState(deck) { it.copy(loadingMessage = "Generating waveforms...") }
                    val waveforms = generateWaveformsForStems(stemFiles)
                    updateDeckState(deck) { it.copy(stemWaveforms = waveforms) }
                } else {
                    throw Exception("Stem separation failed.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(application, "Error processing song: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e("MashupViewModel", "Error processing song", e)
                }
            } finally {
                separator.close()
                updateDeckState(deck) { it.copy(isLoading = false, loadingMessage = "") }
            }
        }
    }

    private suspend fun generateWaveformsForStems(stems: Map<String, Uri>): Map<String, List<Int>> {
        val amplituda = Amplituda(application)
        val waveformMap = mutableMapOf<String, List<Int>>()
        stems.forEach { (name, uri) ->
            uri.path?.let { path ->
                val processingOutput = amplituda.processAudio(path, Cache.withParams(Cache.REUSE))
                // CORRECCIÓN: Llamar a .get() para obtener AmplitudaResult y luego a .amplitudesAsList()
                waveformMap[name] = processingOutput.get().amplitudesAsList()
            }
        }
        return waveformMap
    }

    private fun updateDeckState(deck: Int, update: (DeckState) -> DeckState) {
        if (deck == 1) _uiState.update { it.copy(deck1 = update(it.deck1)) }
        else _uiState.update { it.copy(deck2 = update(it.deck2)) }
    }

    fun playPause(deck: Int) { if (deck == 1) deck1Controller.playPause() else deck2Controller.playPause() }
    fun seek(deck: Int, progress: Float) { if (deck == 1) deck1Controller.seek(progress) else deck2Controller.seek(progress) }
    fun nudge(deck: Int, amountMs: Long) { if (deck == 1) deck1Controller.nudge(amountMs) else deck2Controller.nudge(amountMs) }

    fun setVolume(deck: Int, volume: Float) {
        updateDeckState(deck) { it.copy(volume = volume.coerceIn(0f, 1f)) }
        updateCrossfaderAndVolumes()
    }

    fun onCrossfaderChange(value: Float) {
        _uiState.update { it.copy(crossfaderValue = value) }
        updateCrossfaderAndVolumes()
    }

    fun setSpeed(deck: Int, speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        if (deck == 1) deck1Controller.setSpeed(safeSpeed) else deck2Controller.setSpeed(safeSpeed)
        updateDeckState(deck) { it.copy(speed = safeSpeed) }
    }

    fun toggleStem(deck: Int, stem: String) {
        val currentDeckState = if (deck == 1) uiState.value.deck1 else uiState.value.deck2
        val newStems = with(currentDeckState.stems) {
            when(stem) {
                "vocals" -> copy(vocals = !vocals)
                "other" -> copy(instrumental = !instrumental)
                "bass" -> copy(bass = !bass)
                "drums" -> copy(drums = !drums)
                else -> this
            }
        }
        updateDeckState(deck) { it.copy(stems = newStems) }
        updateCrossfaderAndVolumes()
    }

    private fun updateCrossfaderAndVolumes() {
        val state = _uiState.value
        val vol1Multiplier = (1f - ((state.crossfaderValue + 1f) / 2f)).coerceIn(0f, 1f)
        val vol2Multiplier = ((state.crossfaderValue + 1f) / 2f).coerceIn(0f, 1f)

        deck1Controller.setDeckVolume(state.deck1.volume * vol1Multiplier, state.deck1.stems)
        deck2Controller.setDeckVolume(state.deck2.volume * vol2Multiplier, state.deck2.stems)
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                updateDeckState(1) { it.copy(progress = deck1Controller.getProgress()) }
                updateDeckState(2) { it.copy(progress = deck2Controller.getProgress()) }
                delay(100)
            }
        }
    }

    fun openSongPicker(deck: Int) { _uiState.update { it.copy(showSongPickerForDeck = deck) } }
    fun closeSongPicker() { _uiState.update { it.copy(showSongPickerForDeck = null) } }

    override fun onCleared() {
        super.onCleared()
        deck1Controller.release()
        deck2Controller.release()
        progressJob?.cancel()
    }
}