package com.theveloper.pixelplay.presentation.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.linc.audiowaveform.AudioWaveform
import com.linc.audiowaveform.model.WaveformAlignment
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.DeckState
import com.theveloper.pixelplay.presentation.viewmodel.MashupViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StemsUiState
import com.theveloper.pixelplay.presentation.viewmodel.StemsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MashupScreen(
    mashupViewModel: MashupViewModel = hiltViewModel(),
    stemsViewModel: StemsViewModel = hiltViewModel()
) {
    val mashupUiState by mashupViewModel.uiState.collectAsState()
    val stemsUiState by stemsViewModel.uiState
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var songBeingProcessed by remember { mutableStateOf<Song?>(null) }
    var isSavingStems by remember { mutableStateOf(false) }

    // Efecto lanzado cuando el estado de los stems cambia a Success
    LaunchedEffect(stemsUiState, songBeingProcessed) {
        val uiState = stemsUiState
        val song = songBeingProcessed
        if (uiState is StemsUiState.Success && song != null) {
            val deck = mashupUiState.showSongPickerForDeck ?: return@LaunchedEffect

            isSavingStems = true

            // Adaptado para trabajar con Map<String, String>
            // 1. Convierte el mapa de rutas de archivo a un mapa de nombre de stem -> Uri
            val stemUris = uiState.stemFiles.mapValues { (_, path) ->
                val file = File(path)
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            }

            // 2. Crea una lista de objetos File a partir de las rutas para generar las formas de onda
            val stemFilesForWaveform = uiState.stemFiles.values.map { File(it) }
            val stemWaveforms = generateWaveformsFromFiles(stemFilesForWaveform) // Asume que esta función existe

            // 3. Carga la canción y los stems en el ViewModel principal
            mashupViewModel.loadSongAndStems(
                deck = deck,
                song = song,
                stems = stemUris,
                waveforms = stemWaveforms
            )

            songBeingProcessed = null
            isSavingStems = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DJ Space") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val isLoading1 = (stemsUiState is StemsUiState.Loading && mashupUiState.showSongPickerForDeck == 1) || (isSavingStems && mashupUiState.showSongPickerForDeck == 1)
                    val isLoading2 = (stemsUiState is StemsUiState.Loading && mashupUiState.showSongPickerForDeck == 2) || (isSavingStems && mashupUiState.showSongPickerForDeck == 2)

                    // El resto de la UI (DeckUi, Crossfader) permanece igual
                    DeckUi(
                        deckNumber = 1,
                        deckState = mashupUiState.deck1,
                        isLoading = isLoading1,
                        loadingMessage = if (isSavingStems) "Processing stems..." else "Separating stems...",
                        onPlayPause = { mashupViewModel.playPause(1) },
                        onVolumeChange = { mashupViewModel.setVolume(1, it) },
                        onSelectSong = { mashupViewModel.openSongPicker(1) },
                        onSeek = { progress -> mashupViewModel.seek(1, progress) },
                        onSpeedChange = { speed -> mashupViewModel.setSpeed(1, speed) },
                        onNudge = { amount -> mashupViewModel.nudge(1, amount) },
                        onToggleStem = { stem -> mashupViewModel.toggleStem(1, stem) }
                    )
                    DeckUi(
                        deckNumber = 2,
                        deckState = mashupUiState.deck2,
                        isLoading = isLoading2,
                        loadingMessage = if (isSavingStems) "Processing stems..." else "Separating stems...",
                        onPlayPause = { mashupViewModel.playPause(2) },
                        onVolumeChange = { mashupViewModel.setVolume(2, it) },
                        onSelectSong = { mashupViewModel.openSongPicker(2) },
                        onSeek = { progress -> mashupViewModel.seek(2, progress) },
                        onSpeedChange = { speed -> mashupViewModel.setSpeed(2, speed) },
                        onNudge = { amount -> mashupViewModel.nudge(2, amount) },
                        onToggleStem = { stem -> mashupViewModel.toggleStem(2, stem) }
                    )
                }

                Crossfader(
                    value = mashupUiState.crossfaderValue,
                    onValueChange = { mashupViewModel.onCrossfaderChange(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            if (mashupUiState.showSongPickerForDeck != null) {
                ModalBottomSheet(
                    onDismissRequest = {
                        if (!isSavingStems && stemsUiState !is StemsUiState.Loading) {
                            mashupViewModel.closeSongPicker()
                        }
                    },
                    sheetState = sheetState
                ) {
                    SongPickerSheet(
                        songs = mashupUiState.allSongs,
                        onSongSelected = { song ->
                            scope.launch {
                                songBeingProcessed = song
                                stemsViewModel.startSeparation(Uri.parse(song.contentUriString))
                            }
                        }
                    )
                }
            }
        }
    }
}

//@androidx.annotation.OptIn(UnstableApi::class)
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun MashupScreen(
//    mashupViewModel: MashupViewModel = hiltViewModel(),
//    stemsViewModel: StemsViewModel = hiltViewModel()
//) {
//    val mashupUiState by mashupViewModel.uiState.collectAsState()
//    val stemsUiState by stemsViewModel.uiState
//    val sheetState = rememberModalBottomSheetState()
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//
//    var songBeingProcessed by remember { mutableStateOf<Song?>(null) }
//    var isSavingStems by remember { mutableStateOf(false) }
//
//    LaunchedEffect(stemsUiState, songBeingProcessed) {
//        val uiState = stemsUiState
//        val song = songBeingProcessed
//        if (uiState is StemsUiState.Success && song != null) {
//            val deck = mashupUiState.showSongPickerForDeck ?: return@LaunchedEffect
//
//            isSavingStems = true
//            // Convierte los archivos de stems a un mapa de nombre -> Uri
//            val stemUris = uiState.stemFiles.associate {
//                it.nameWithoutExtension to FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
//            }
//            // Genera las formas de onda desde los archivos
//            val stemWaveforms = generateWaveformsFromFiles(uiState.stemFiles)
//
//            mashupViewModel.loadSongAndStems(
//                deck = deck,
//                song = song,
//                stems = stemUris,
//                waveforms = stemWaveforms
//            )
//
//            songBeingProcessed = null
//            isSavingStems = false
//        }
//    }
//
//    Scaffold(
//        topBar = {
//            TopAppBar(
//                title = { Text("DJ Space") },
//                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
//            )
//        }
//    ) { paddingValues ->
//        Box(modifier = Modifier.fillMaxSize()) {
//            Column(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(paddingValues)
//                    .padding(horizontal = 16.dp)
//                    .verticalScroll(rememberScrollState()),
//                horizontalAlignment = Alignment.CenterHorizontally,
//                verticalArrangement = Arrangement.spacedBy(16.dp)
//            ) {
//                Spacer(Modifier.height(8.dp))
//                Column(
//                    modifier = Modifier.fillMaxWidth(),
//                    verticalArrangement = Arrangement.spacedBy(16.dp)
//                ) {
//                    val isLoading1 = (stemsUiState is StemsUiState.Loading && mashupUiState.showSongPickerForDeck == 1) || (isSavingStems && mashupUiState.showSongPickerForDeck == 1)
//                    val isLoading2 = (stemsUiState is StemsUiState.Loading && mashupUiState.showSongPickerForDeck == 2) || (isSavingStems && mashupUiState.showSongPickerForDeck == 2)
//
//                    DeckUi(
//                        deckNumber = 1,
//                        deckState = mashupUiState.deck1,
//                        isLoading = isLoading1,
//                        loadingMessage = if (isSavingStems) "Processing stems..." else "Separating stems...",
//                        onPlayPause = { mashupViewModel.playPause(1) },
//                        onVolumeChange = { mashupViewModel.setVolume(1, it) },
//                        onSelectSong = { mashupViewModel.openSongPicker(1) },
//                        onSeek = { progress -> mashupViewModel.seek(1, progress) },
//                        onSpeedChange = { speed -> mashupViewModel.setSpeed(1, speed) },
//                        onNudge = { amount -> mashupViewModel.nudge(1, amount) },
//                        onToggleStem = { stem -> mashupViewModel.toggleStem(1, stem) }
//                    )
//                    DeckUi(
//                        deckNumber = 2,
//                        deckState = mashupUiState.deck2,
//                        isLoading = isLoading2,
//                        loadingMessage = if (isSavingStems) "Processing stems..." else "Separating stems...",
//                        onPlayPause = { mashupViewModel.playPause(2) },
//                        onVolumeChange = { mashupViewModel.setVolume(2, it) },
//                        onSelectSong = { mashupViewModel.openSongPicker(2) },
//                        onSeek = { progress -> mashupViewModel.seek(2, progress) },
//                        onSpeedChange = { speed -> mashupViewModel.setSpeed(2, speed) },
//                        onNudge = { amount -> mashupViewModel.nudge(2, amount) },
//                        onToggleStem = { stem -> mashupViewModel.toggleStem(2, stem) }
//                    )
//                }
//
//                Crossfader(
//                    value = mashupUiState.crossfaderValue,
//                    onValueChange = { mashupViewModel.onCrossfaderChange(it) },
//                    modifier = Modifier.fillMaxWidth()
//                )
//                Spacer(Modifier.height(8.dp))
//            }
//
//            if (mashupUiState.showSongPickerForDeck != null) {
//                ModalBottomSheet(
//                    onDismissRequest = {
//                        if (!isSavingStems && stemsUiState !is StemsUiState.Loading) {
//                            mashupViewModel.closeSongPicker()
//                        }
//                    },
//                    sheetState = sheetState
//                ) {
//                    SongPickerSheet(
//                        songs = mashupUiState.allSongs,
//                        onSongSelected = { song ->
//                            scope.launch {
//                                songBeingProcessed = song
//                                stemsViewModel.startSeparation(Uri.parse(song.contentUriString))
//                            }
//                        }
//                    )
//                }
//            }
//        }
//    }
//}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckUi(
    deckNumber: Int,
    deckState: DeckState,
    isLoading: Boolean,
    loadingMessage: String,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSelectSong: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onNudge: (Long) -> Unit,
    onToggleStem: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Deck $deckNumber",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !isLoading) { onSelectSong() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (deckState.song != null) {
                            SmartImage(
                                model = deckState.song.albumArtUriString,
                                contentDescription = "Song Cover",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(painterResource(id = R.drawable.rounded_playlist_add_24), "Load Song", modifier = Modifier.size(40.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deckState.song?.title ?: "No song loaded", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(deckState.song?.artist ?: "...", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        AudioWaveform(
                            amplitudes = deckState.stemWaveforms["other"] ?: List(100) {0},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            progress = deckState.progress,
                            onProgressChange = { onSeek(it) },
                            waveformAlignment = WaveformAlignment.Center,
                            progressBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                AnimatedVisibility(deckState.song != null && !isLoading) {
                    Column {
                        Text("Stems", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StemChip(text = "Vocals", selected = deckState.stems.vocals, waveform = deckState.stemWaveforms["vocals"], onClick = { onToggleStem("vocals") })
                            StemChip(text = "Instrumental", selected = deckState.stems.instrumental, waveform = deckState.stemWaveforms["other"], onClick = { onToggleStem("other") })
                            StemChip(text = "Bass", selected = deckState.stems.bass, waveform = deckState.stemWaveforms["bass"], onClick = { onToggleStem("bass") })
                            StemChip(text = "Drums", selected = deckState.stems.drums, waveform = deckState.stemWaveforms["drums"], onClick = { onToggleStem("drums") })
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = { onNudge(-100) }, enabled = deckState.song != null) { Text("<<") }
                    IconButton(onClick = onPlayPause, enabled = deckState.song != null, modifier = Modifier.size(56.dp)) {
                        Icon(painter = painterResource(if (deckState.isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24), contentDescription = "Play/Pause", modifier = Modifier.fillMaxSize())
                    }
                    OutlinedButton(onClick = { onNudge(100) }, enabled = deckState.song != null) { Text(">>") }
                }

                Column(modifier = Modifier.padding(top = 8.dp)) {
                    SliderControl(label = "Volume", value = deckState.volume, onValueChange = onVolumeChange, valueRange = 0f..1f, enabled = deckState.song != null)
                    SliderControl(label = "Speed", value = deckState.speed, onValueChange = onSpeedChange, valueRange = 0.5f..2f, steps = 14, enabled = deckState.song != null) {
                        Text(text = "x${"%.2f".format(deckState.speed)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(Modifier.height(16.dp))
                    Text(loadingMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StemChip(text: String, selected: Boolean, waveform: List<Int>?, onClick: () -> Unit) {
    val hasWaveform = waveform != null && waveform.isNotEmpty()
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = hasWaveform,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text)
                if(hasWaveform) {
                    if (waveform != null) {
                        AudioWaveform(
                            modifier = Modifier
                                .height(20.dp)
                                .width(40.dp),
                            amplitudes = waveform,
                            onProgressChange = {},
                            waveformAlignment = WaveformAlignment.Center
                        )
                    }
                }
            }
        },
        leadingIcon = if (selected) { { Icon(painter = painterResource(id = R.drawable.rounded_check_circle_24), modifier = Modifier.size(18.dp), contentDescription = null) } } else { null }
    )
}

@Composable
private fun SliderControl(
    label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0, enabled: Boolean, endContent: @Composable (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp), color = if(enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f))
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, modifier = Modifier.weight(1f), enabled = enabled)
        if (endContent != null) {
            Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) { endContent() }
        }
    }
}

@Composable
private fun Crossfader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Crossfader", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Deck 1", style = MaterialTheme.typography.bodyMedium)
            Slider(value = value, onValueChange = onValueChange, valueRange = -1f..1f, modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp))
            Text("Deck 2", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SongPickerSheet(songs: List<Song>, onSongSelected: (Song) -> Unit) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Text("Select a Song", style = MaterialTheme.typography.titleLarge, modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), textAlign = TextAlign.Center)
        LazyColumn(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)) {
            items(songs) { song ->
                SongPickerItem(song = song, onClick = { onSongSelected(song) })
                Divider()
            }
        }
    }
}

@Composable
private fun SongPickerItem(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = "Song Cover",
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(text = song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// CORRECCIÓN: Funciones de ayuda añadidas para procesar formas de onda desde archivos.
private suspend fun generateWaveformsFromFiles(files: List<File>): Map<String, List<Int>> = withContext(Dispatchers.IO) {
    files.associate { file ->
        file.nameWithoutExtension to generateWaveformFromFile(file)
    }
}

private fun generateWaveformFromFile(file: File, samples: Int = 100): List<Int> {
    val bytes = file.readBytes()
    // La cabecera WAV tiene 44 bytes, nos aseguramos de que haya datos de audio.
    if (bytes.size <= 44) return List(samples) { 0 }

    val pcmData = bytes.copyOfRange(44, bytes.size)
    val floatArray = bytesToFloatArray(pcmData, pcmData.size)

    if (floatArray.isEmpty()) return List(samples) { 0 }

    val waveform = mutableListOf<Int>()
    val groupSize = floatArray.size / samples
    if (groupSize <= 0) return List(samples) { 0 }

    for (i in 0 until samples) {
        val start = i * groupSize
        val end = start + groupSize
        var max = 0f
        for (j in start until end) {
            if (abs(floatArray[j]) > max) {
                max = abs(floatArray[j])
            }
        }
        waveform.add((max * 255).toInt())
    }
    return waveform
}

private fun bytesToFloatArray(bytes: ByteArray, count: Int): FloatArray {
    val floatArray = FloatArray(count / 2)
    val shortBuffer = ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    for (i in floatArray.indices) {
        floatArray[i] = shortBuffer.get().toFloat() / Short.MAX_VALUE
    }
    return floatArray
}

// Note: The following helper functions are added to bridge the incompatibility between
// StemsViewModel (producing FloatArray) and MashupViewModel (consuming Uri).
// This logic would ideally live in a repository or a dedicated service, not in a UI file.

private suspend fun saveStemsToFiles(context: Context, stems: Map<String, FloatArray>): Map<String, Uri> = withContext(Dispatchers.IO) {
    val stemUris = mutableMapOf<String, Uri>()
    val authority = "${context.packageName}.provider"
    stems.forEach { (name, floatArray) ->
        val wavBytes = floatArrayToWav(floatArray)
        val file = File(context.cacheDir, "$name.wav")
        file.writeBytes(wavBytes)
        stemUris[name] = FileProvider.getUriForFile(context, authority, file)
    }
    stemUris
}

private fun generateWaveform(floatArray: FloatArray, samples: Int = 100): List<Int> {
    if (floatArray.isEmpty()) return List(samples) { 0 }

    val waveform = mutableListOf<Int>()
    val groupSize = floatArray.size / samples
    if (groupSize <= 0) return List(samples) { 0 }

    for (i in 0 until samples) {
        val start = i * groupSize
        val end = start + groupSize
        var max = 0f
        for (j in start until end) {
            if (abs(floatArray[j]) > max) {
                max = abs(floatArray[j])
            }
        }
        waveform.add((max * 255).toInt())
    }
    return waveform
}

private fun floatArrayToWav(floatArray: FloatArray, sampleRate: Int = 44100, channels: Int = 1, bitDepth: Int = 16): ByteArray {
    val byteBuffer = ByteBuffer.allocate(floatArray.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (value in floatArray) {
        byteBuffer.putShort((value * Short.MAX_VALUE).toInt().toShort())
    }
    val pcmData = byteBuffer.array()

    val header = ByteArrayOutputStream()
    header.write("RIFF".toByteArray())
    header.write(intToBytes(36 + pcmData.size))
    header.write("WAVE".toByteArray())
    header.write("fmt ".toByteArray())
    header.write(intToBytes(16))
    header.write(shortToBytes(1)) // Audio format 1=PCM
    header.write(shortToBytes(channels.toShort()))
    header.write(intToBytes(sampleRate))
    header.write(intToBytes(sampleRate * channels * bitDepth / 8))
    header.write(shortToBytes((channels * bitDepth / 8).toShort()))
    header.write(shortToBytes(bitDepth.toShort()))
    header.write("data".toByteArray())
    header.write(intToBytes(pcmData.size))

    return header.toByteArray() + pcmData
}

private fun intToBytes(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
private fun shortToBytes(value: Short): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
