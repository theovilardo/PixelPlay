package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.QueuePlaylistSongItem
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by playlistViewModel.uiState.collectAsState()
    val playerStableState by playerViewModel.stablePlayerState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val currentPlaylist = uiState.currentPlaylistDetails
    val songsInPlaylist = uiState.currentPlaylistSongs

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var isRemoveModeEnabled by remember { mutableStateOf(false) }

    var localReorderableSongs by remember(songsInPlaylist) { mutableStateOf(songsInPlaylist) }

    val listState = rememberLazyListState()
    val view = LocalView.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            localReorderableSongs = localReorderableSongs.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = from.index
            }
            lastMovedTo = to.index
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            currentPlaylist?.let {
                playlistViewModel.reorderSongsInPlaylist(it.id, lastMovedFrom!!, lastMovedTo!!)
            }
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    scrolledContainerColor = Color.Transparent,
                    containerColor = Color.Transparent
                ),
                subtitle = {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = "${songsInPlaylist.size} songs • ${formatTotalDuration(songsInPlaylist)}",
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
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier
                    .height(if (playerStableState.isPlaying || playerStableState.currentSong != null) MiniPlayerHeight + 60.dp else 66.dp)
                    .padding(
                        bottom = if (playerStableState.isPlaying || playerStableState.currentSong != null) MiniPlayerHeight else 10.dp,
                        //end = 10.dp
                    ),
                shape = CircleShape,
                onClick = { showAddSongsSheet = true },
                icon = { Icon(Icons.Rounded.Add, "Añadir canciones") },
                text = { Text("Add Songs") },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading && currentPlaylist == null) {
            Box(Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()), Alignment.Center) { CircularProgressIndicator() }
        } else if (currentPlaylist == null) {
            Box(Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()), Alignment.Center) { Text("Playlist no encontrada.") }
        } else {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (localReorderableSongs.isNotEmpty()) {
                                playerViewModel.playSongs(localReorderableSongs, localReorderableSongs.first(), currentPlaylist.name)
                                if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp),
                        enabled = localReorderableSongs.isNotEmpty(),
                        shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 60.dp, smoothnessAsPercentTR = 60, cornerRadiusTR = 14.dp, smoothnessAsPercentTL = 60, cornerRadiusBL = 60.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 14.dp, smoothnessAsPercentBL = 60)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Play it")
                    }
                    FilledTonalButton(
                        onClick = {
                            if (localReorderableSongs.isNotEmpty()) {
                                if (!playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                playerViewModel.playSongs(localReorderableSongs, localReorderableSongs.random(), currentPlaylist.name)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp),
                        enabled = localReorderableSongs.isNotEmpty(),
                        shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 14.dp, smoothnessAsPercentTR = 60, cornerRadiusTR = 60.dp, smoothnessAsPercentTL = 60, cornerRadiusBL = 14.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 60.dp, smoothnessAsPercentBL = 60)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Shuffle")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 0.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val reorderCornerRadius by animateDpAsState(
                        targetValue = if (isReorderModeEnabled) 24.dp else 12.dp,
                        label = "reorderCornerRadius"
                    )
                    val reorderButtonColor by animateColorAsState(
                        targetValue = if (isReorderModeEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "reorderButtonColor"
                    )
                    val reorderIconColor by animateColorAsState(
                        targetValue = if (isReorderModeEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                        label = "reorderIconColor"
                    )

                    val removeCornerRadius by animateDpAsState(
                        targetValue = if (isRemoveModeEnabled) 24.dp else 12.dp,
                        label = "removeCornerRadius"
                    )
                    val removeButtonColor by animateColorAsState(
                        targetValue = if (isRemoveModeEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "removeButtonColor"
                    )
                    val removeIconColor by animateColorAsState(
                        targetValue = if (isRemoveModeEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                        label = "removeIconColor"
                    )

                    Button(
                        onClick = { isRemoveModeEnabled = !isRemoveModeEnabled },
                        shape = RoundedCornerShape(removeCornerRadius),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = removeButtonColor,
                            contentColor = removeIconColor
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .animateContentSize()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(removeCornerRadius))
                    ) {
                        Icon(
                            modifier = Modifier.size(18.dp),
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = "Remove songs",
                            tint = removeIconColor
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            modifier = Modifier.padding(end = 4.dp),
                            text = "Remove",
                            color = removeIconColor,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = { isReorderModeEnabled = !isReorderModeEnabled },
                        shape = RoundedCornerShape(reorderCornerRadius),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = reorderButtonColor,
                            contentColor = reorderIconColor
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .animateContentSize()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(reorderCornerRadius))
                    ) {
                        Icon(
                            modifier = Modifier.size(22.dp),
                            painter = painterResource(R.drawable.drag_order_icon),
                            contentDescription = "Reorder songs",
                            tint = reorderIconColor
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            modifier = Modifier.padding(end = 4.dp),
                            text = "Reorder",
                            color = reorderIconColor,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (localReorderableSongs.isEmpty()) {
                    Box(Modifier
                        .fillMaxSize()
                        .weight(1f), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.MusicOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("This playlist is empty.", style = MaterialTheme.typography.titleMedium)
                            Text("Tap on 'Add Songs' to begin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .clip(
                                AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 32.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusTL = 32.dp,
                                    smoothnessAsPercentTL = 60,
                                )
                            )
                            .background(color = MaterialTheme.colorScheme.surfaceContainerHigh),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(
                            top = 12.dp,
                            bottom = if (playerStableState.isPlaying || playerStableState.currentSong != null) MiniPlayerHeight + 32.dp + 104.dp else 10.dp + 104.dp
                        )
                    ) {
                        itemsIndexed(localReorderableSongs, key = { _, item -> item.id }) { _, song ->
                            ReorderableItem(
                                state = reorderableState,
                                key = song.id,
                            ) { isDragging ->
                                val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "scale")

                                QueuePlaylistSongItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                    onClick = {
                                        playerViewModel.playSongs(
                                            localReorderableSongs,
                                            song,
                                            currentPlaylist.name,
                                            currentPlaylist.id
                                        )
                                    },
                                    song = song,
                                    isCurrentSong = playerStableState.currentSong?.id == song.id,
                                    isPlaying = playerStableState.isPlaying,
                                    isDragging = isDragging,
                                    onRemoveClick = {
                                        currentPlaylist.let {
                                            playlistViewModel.removeSongFromPlaylist(it.id, song.id)
                                        }
                                    },
                                    isReorderModeEnabled = isReorderModeEnabled,
                                    isDragHandleVisible = isReorderModeEnabled,
                                    isRemoveButtonVisible = isRemoveModeEnabled,
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        ViewCompat.performHapticFeedback(
                                                            view,
                                                            HapticFeedbackConstantsCompat.GESTURE_START
                                                        )
                                                    },
                                                    onDragStopped = {
                                                        ViewCompat.performHapticFeedback(
                                                            view,
                                                            HapticFeedbackConstantsCompat.GESTURE_END
                                                        )
                                                    }
                                                )
                                                .size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = "Reorder song",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
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

    if (showAddSongsSheet && currentPlaylist != null) {
        SongPickerBottomSheet(
            allSongs = uiState.songSelectionForPlaylist,
            isLoading = uiState.isLoadingSongSelection,
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedIds ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIds.toList())
                showAddSongsSheet = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerBottomSheet(
    allSongs: List<Song>,
    isLoading: Boolean,
    initiallySelectedSongIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>().apply {
        initiallySelectedSongIds.forEach { put(it, true) }
    } }
    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) allSongs
        else allSongs.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
    }

    val animatedAlbumCornerRadius = 60.dp

    val albumShape = remember(animatedAlbumCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedAlbumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedAlbumCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedAlbumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedAlbumCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                topBar = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 26.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Add Songs", style = MaterialTheme.typography.displaySmall, fontFamily = GoogleSansRounded)
                        }
                        OutlinedTextField(
                            value = searchQuery,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                unfocusedTrailingIconColor = Color.Transparent,
                                focusedSupportingTextColor = Color.Transparent,
                            ),
                            onValueChange = { searchQuery = it },
                            label = { Text("Search for songs...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = CircleShape,
                            singleLine = true,
                            trailingIcon = { if(searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Clear, null) } }
                        )
                    }
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        modifier = Modifier.padding(bottom = 18.dp, end = 8.dp),
                        shape = CircleShape,
                        onClick = { onConfirm(selectedSongIds.filterValues { it }.keys) },
                        icon = { Icon(Icons.Rounded.Check, "Añadir canciones") },
                        text = { Text("Add") },
                    )
                }
            ) { innerPadding ->
                if (isLoading) {
                    Box(Modifier
                        .fillMaxSize()
                        .padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(bottom = 100.dp, top = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredSongs, key = { it.id }) { song ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .clickable {
                                        val currentSelection = selectedSongIds[song.id] ?: false
                                        selectedSongIds[song.id] = !currentSelection
                                    }
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedSongIds[song.id] ?: false,
                                    onCheckedChange = { isChecked -> selectedSongIds[song.id] = isChecked }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                ) {
                                    // Usando tu composable SmartImage
                                    SmartImage(
                                        model = song.albumArtUriString,
                                        contentDescription = song.title,
                                        shape = albumShape,
                                        targetSize = Size(168, 168), // 56dp * 3 (para densidad xxhdpi)
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
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
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {

            }
        }
    }
}

@Composable
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(TextFieldValue(currentName)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renamer Playlist") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                shape = CircleShape,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (newName.text.isNotBlank()) onRename(newName.text) },
                enabled = newName.text.isNotBlank() && newName.text != currentName
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}