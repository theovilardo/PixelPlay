package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.linc.audiowaveform.AudioWaveform
import com.linc.audiowaveform.model.WaveformAlignment
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.OptimizedAlbumArt
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.DeckState
import com.theveloper.pixelplay.presentation.viewmodel.MashupViewModel
import com.theveloper.pixelplay.presentation.viewmodel.MashupUiState
import com.theveloper.pixelplay.presentation.viewmodel.TrackStems
import com.theveloper.pixelplay.utils.formatDuration
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DJSpaceScreen(
    viewModel: MashupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()

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
                    DeckUi(
                        deckNumber = 1,
                        deckState = uiState.deck1,
                        onPlayPause = { viewModel.playPause(1) },
                        onVolumeChange = { viewModel.setVolume(1, it) },
                        onSelectSong = { viewModel.openSongPicker(1) },
                        onSeek = { progress -> viewModel.seek(1, progress) },
                        onSpeedChange = { speed -> viewModel.setSpeed(1, speed) },
                        onNudge = { amount -> viewModel.nudge(1, amount) },
                        onToggleStem = { stem -> viewModel.toggleStem(1, stem) }
                    )
                    DeckUi(
                        deckNumber = 2,
                        deckState = uiState.deck2,
                        onPlayPause = { viewModel.playPause(2) },
                        onVolumeChange = { viewModel.setVolume(2, it) },
                        onSelectSong = { viewModel.openSongPicker(2) },
                        onSeek = { progress -> viewModel.seek(2, progress) },
                        onSpeedChange = { speed -> viewModel.setSpeed(2, speed) },
                        onNudge = { amount -> viewModel.nudge(2, amount) },
                        onToggleStem = { stem -> viewModel.toggleStem(2, stem) }
                    )
                }

                Crossfader(
                    value = uiState.crossfaderValue,
                    onValueChange = { viewModel.onCrossfaderChange(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.showSongPickerForDeck != null) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.closeSongPicker() },
                    sheetState = sheetState
                ) {
                    SongPickerSheet(
                        songs = uiState.allSongs,
                        onSongSelected = { song -> viewModel.selectSong(song, uiState.showSongPickerForDeck!!) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DeckUi(
    deckNumber: Int,
    deckState: DeckState,
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
                            .clickable(enabled = !deckState.isLoading) { onSelectSong() },
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
                            //spikeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            progressBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                AnimatedVisibility(deckState.song != null && !deckState.isLoading) {
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

            if (deckState.isLoading) {
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(Modifier.height(16.dp))
                    Text(deckState.loadingMessage, style = MaterialTheme.typography.bodyMedium)
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
                            //spikeColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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