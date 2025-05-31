package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mohamedrejeb.compose.dnd.annotation.ExperimentalDndApi
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.abs

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class, ExperimentalDndApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by playlistViewModel.uiState.collectAsState()
    val playerStableState by playerViewModel.stablePlayerState.collectAsState() // Para saber qué canción se reproduce
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val currentPlaylist = uiState.currentPlaylistDetails
    val songsInPlaylist = uiState.currentPlaylistSongs

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    var showAddSongsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Estado local para la lista reordenable
    var localReorderableSongs by remember { mutableStateOf(songsInPlaylist) }
    LaunchedEffect(songsInPlaylist) {
        localReorderableSongs = songsInPlaylist
    }

    // Implementación de reordenamiento similar a QueueBottomSheet
    val reorderState = rememberReorderState<Song>()

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = currentPlaylist?.name ?: "Playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                subtitle = {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = "${songsInPlaylist.size} canciones • ${formatTotalDuration(songsInPlaylist)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(start = 10.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onBackClick
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = { showRenameDialog = true }
                    ) { Icon(Icons.Filled.Edit, "Renombrar") }
                    FilledTonalIconButton(
                        modifier = Modifier.padding(end = 10.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = {
                            currentPlaylist?.let { playlistViewModel.deletePlaylist(it.id) }
                            onDeletePlayListClick()
                        }
                    ) {
                        Icon(Icons.Filled.DeleteOutline, "Eliminar Playlist")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier
                    .height(if (playerStableState.isPlaying || playerStableState.currentSong != null) MiniPlayerHeight + 60.dp else 66.dp)
                    .padding(
                        bottom = if (playerStableState.isPlaying || playerStableState.currentSong != null) MiniPlayerHeight else 10.dp,
                        end = 10.dp
                ),
                onClick = { showAddSongsDialog = true },
                icon = { Icon(Icons.Filled.Add, "Añadir canciones") },
                text = { Text("Add Songs") },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading && currentPlaylist == null) { // Mostrar carga solo si no hay datos previos
            Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()), Alignment.Center) { CircularProgressIndicator() }
        } else if (currentPlaylist == null) {
            Box(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()), Alignment.Center) { Text("Playlist no encontrada.") }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
                // Botones de Play All / Shuffle All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (localReorderableSongs.isNotEmpty()) {
                                playerViewModel.playSongs(localReorderableSongs, localReorderableSongs.first(), currentPlaylist.name)
                                playerStableState.isShuffleEnabled.let { if(it) playerViewModel.toggleShuffle() } // Desactivar shuffle si estaba activo
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp),
                        enabled = localReorderableSongs.isNotEmpty(),
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 60.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusTR = 14.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBL = 60.dp,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBR = 14.dp,
                            smoothnessAsPercentBL = 60
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Play it")
                    }
                    FilledTonalButton(
                        onClick = {
                            if (localReorderableSongs.isNotEmpty()) {
                                playerStableState.isShuffleEnabled.let { if(!it) playerViewModel.toggleShuffle() } // Activar shuffle si no estaba activo
                                playerViewModel.playSongs(localReorderableSongs, localReorderableSongs.random(), currentPlaylist.name)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp),
                        enabled = localReorderableSongs.isNotEmpty(),
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 14.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusTR = 60.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBL = 14.dp,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBR = 60.dp,
                            smoothnessAsPercentBL = 60
                        )
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Shuffle")
                    }
                }

                if (localReorderableSongs.isEmpty()) {
                    Box(Modifier.fillMaxSize().weight(1f), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.MusicOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("Esta playlist está vacía.", style = MaterialTheme.typography.titleMedium)
                            Text("Toca 'Añadir Canciones' para empezar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    // Usamos ReorderContainer similar a QueueBottomSheet
                    ReorderContainer(
                        state = reorderState,
                        modifier = Modifier.fillMaxSize().weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .clip(
                                    shape = RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomEnd = 0.dp,
                                        bottomStart = 0.dp
                                    )
                                ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = if (playerStableState.isPlaying || playerStableState.currentSong != null) MiniPlayerHeight + 32.dp + 104.dp else 10.dp + 104.dp
                            )
                        ) {
                            itemsIndexed(localReorderableSongs, key = { _, item -> item.id }) { _, song ->
                                ReorderableItem(
                                    state = reorderState,
                                    key = song.id,
                                    data = song,
                                    dragAfterLongPress = true,
                                    onDrop = { draggedState ->
                                        // Reconstruir índices al soltar
                                        val from = localReorderableSongs.indexOfFirst { it.id == draggedState.data.id }
                                        val to = localReorderableSongs.indexOfFirst { it.id == song.id }

                                        if (from != to && from >= 0 && to >= 0) {
                                            // Actualizar la lista local
                                            localReorderableSongs = localReorderableSongs.toMutableList().apply {
                                                add(to, removeAt(from))
                                            }

                                            // Notificar al ViewModel para persistir el cambio
                                            currentPlaylist?.let {
                                                playlistViewModel.reorderSongsInPlaylist(it.id, from, to)
                                            }
                                        }
                                    }
                                ) {
                                    // Añadimos animación
                                    val isDragging = this.isDragging

                                    // Animación para elevation
                                    val elevation by animateDpAsState(
                                        targetValue = if (isDragging) 8.dp else 1.dp,
                                        label = "elevation"
                                    )

                                    // Animación para compresión vertical cuando un elemento está siendo movido
                                    // Usando una combinada de TransitionState y animación
                                    val draggedItem = reorderState.hoveredDropTargetKey
                                    val itemPosition = localReorderableSongs.indexOfFirst { it.id == song.id }
                                    val draggedPosition = draggedItem?.let { draggedKey ->
                                        localReorderableSongs.indexOfFirst { it.id == draggedKey }
                                    } ?: -1

                                    // Solo aplicamos el efecto si hay un elemento siendo arrastrado y no es este mismo
                                    val compressionModifier = if (draggedItem != null && draggedItem != song.id && draggedPosition != -1) {
                                        val isBelow = itemPosition > draggedPosition
                                        val isNextTo = abs(itemPosition - draggedPosition) == 1

                                        // Solo aplicamos efecto a elementos contiguos
                                        if (isNextTo) {
                                            if (isBelow) {
                                                // Este elemento está justo debajo del hueco
                                                Modifier.graphicsLayer {
                                                    scaleY = 0.95f
                                                    translationY = -2f
                                                }
                                            } else {
                                                // Este elemento está justo arriba del hueco
                                                Modifier.graphicsLayer {
                                                    scaleY = 0.95f
                                                    translationY = 2f
                                                }
                                            }
                                        } else {
                                            Modifier
                                        }
                                    } else {
                                        Modifier
                                    }

                                    PlaylistSongItem(
                                        song = song,
                                        isPlaying = playerStableState.currentSong?.id == song.id && playerStableState.isPlaying,
                                        isDragging = isDragging,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(compressionModifier)
                                            .clickable {
                                                playerViewModel.playSongs(localReorderableSongs, song, currentPlaylist.name)
                                            },
                                        elevation = elevation,
                                        onRemoveClick = {
                                            currentPlaylist.let {
                                                playlistViewModel.removeSongFromPlaylist(it.id, song.id)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSongsDialog && currentPlaylist != null) {
        SongPickerDialog(
            allSongs = uiState.songSelectionForPlaylist, // Usar la lista cargada
            isLoading = uiState.isLoadingSongSelection,
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsDialog = false },
            onConfirm = { selectedIds ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIds.toList())
                showAddSongsDialog = false
            }
        )
    }
    if (showRenameDialog && currentPlaylist != null) {
        RenamePlaylistDialog(
            currentName = currentPlaylist.name,
            onDismiss = { showRenameDialog = false },
            onRename = { newName ->
                playlistViewModel.renamePlaylist(currentPlaylist.id, newName)
                showRenameDialog = false
            }
        )
    }
}

@Composable
fun PlaylistSongItem(
    song: Song,
    isPlaying: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    elevation: Dp = 1.dp,
    onRemoveClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val itemShape = RoundedCornerShape(16.dp)

    // Efecto de sombra y elevación para el elemento arrastrado
    val backgroundColor = if (isPlaying) {
        colors.onPrimary
    } else {
        colors.surfaceContainerLowest
    }

    Surface(
        modifier = modifier,
        shape = itemShape,
        color = backgroundColor,
        tonalElevation = elevation,
        shadowElevation = if (isDragging) elevation else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Arrastrar para reordenar",
                tint = if (isPlaying) colors.primary else colors.onSurface,
                modifier = Modifier.padding(end = 12.dp)
            )
            SmartImage(
                model = song.albumArtUriString,
                shape = RoundedCornerShape(8.dp),
                contentDescription = "Carátula",
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (isPlaying) colors.primary else colors.onSurface,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = (if (isPlaying) colors.primary else colors.onSurface).copy(alpha = 0.7f)
                )
            }
            if (isPlaying) {
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = "Reproduciendo",
                    tint = colors.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier
                    .padding(start = 8.dp)
            ) {
                Icon(
                    Icons.Filled.RemoveCircleOutline,
                    "Quitar de la playlist",
                    tint = if (isPlaying) colors.primary else colors.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerDialog(
    allSongs: List<Song>,
    isLoading: Boolean,
    initiallySelectedSongIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>().apply {
        initiallySelectedSongIds.forEach { put(it, true) }
    } }
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) allSongs
        else allSongs.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir Canciones a la Playlist") },
        text = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) { // Limitar altura
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar canciones") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    trailingIcon = { if(searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Clear, null) } }
                )
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn {
                        items(filteredSongs, key = { it.id }) { song ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val currentSelection = selectedSongIds[song.id] ?: false
                                    selectedSongIds[song.id] = !currentSelection
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedSongIds[song.id] ?: false,
                                    onCheckedChange = { isChecked -> selectedSongIds[song.id] = isChecked }
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedSongIds.filterValues { it }.keys) }) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(TextFieldValue(currentName)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar Playlist") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Nuevo nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (newName.text.isNotBlank()) onRename(newName.text) },
                enabled = newName.text.isNotBlank() && newName.text != currentName
            ) { Text("Renombrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}