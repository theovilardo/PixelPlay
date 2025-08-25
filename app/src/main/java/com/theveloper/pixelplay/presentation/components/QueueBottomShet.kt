package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.trimmedLength
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.media3.common.Player
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentQueueSourceName: String,
    currentSongId: String?,
    repeatMode: Int,
    isShuffleOn: Boolean,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    activeTimerValueDisplay: String?,
    isEndOfTrackTimerActive: Boolean,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    var showTimerOptions by rememberSaveable { mutableStateOf(false) }

    var items by remember(queue) { mutableStateOf(queue) }

    val listState = rememberLazyListState()
    val view = LocalView.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            items = items.toMutableList().apply {
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
            onReorder(lastMovedFrom!!, lastMovedTo!!)
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = colors.surfaceContainer,
        dragHandle       = {
            BottomSheetDefaults.DragHandle(
                color = colors.primary
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Absolute.SpaceBetween
                ) {
                    Text(
                        text     = "Next Up",
                        style    = MaterialTheme.typography.displayMedium,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .align(Alignment.CenterVertically)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(end = 16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = CircleShape
                            )
                    ) {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            text = currentQueueSourceName,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
                        )
                    }
                }

                if (items.isEmpty()) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Queue is empty.", color = colors.onSurface)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 26.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusTL = 26.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusBR = 0.dp,
                                    smoothnessAsPercentBL = 60,
                                    cornerRadiusBL = 0.dp,
                                    smoothnessAsPercentBR = 60
                                )
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        itemsIndexed(items, key = { _, s -> s.id }) { _, song ->
                            ReorderableItem(
                                state = reorderableState,
                                key = song.id
                            ) { isDragging ->
                                val scale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.05f else 1f,
                                    label = "scaleAnimation"
                                )
                                val backgroundColor by animateColorAsState(
                                    targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                                    label = "backgroundColorAnimation"
                                )

                                QueuePlaylistSongItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clip(AbsoluteSmoothCornerShape(
                                            cornerRadiusTR = 22.dp,
                                            smoothnessAsPercentTL = 60,
                                            cornerRadiusTL = 22.dp,
                                            smoothnessAsPercentTR = 60,
                                            cornerRadiusBR = 22.dp,
                                            smoothnessAsPercentBL = 60,
                                            cornerRadiusBL = 22.dp,
                                            smoothnessAsPercentBR = 60
                                        ))
                                        .clickable { onPlaySong(song) },
                                    song = song,
                                    isCurrentSong = song.id == currentSongId,
                                    isPlaying = null,
                                    isDragging = isDragging,
                                    onRemoveClick = { onRemoveSong(song.id) },
                                    isReorderModeEnabled = false,
                                    isDragHandleVisible = true,
                                    isRemoveButtonVisible = true,
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.GESTURE_START)
                                                    },
                                                    onDragStopped = {
                                                        ViewCompat.performHapticFeedback(view, HapticFeedbackConstantsCompat.GESTURE_END)
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

            // Floating Toolbar remains the same
            HorizontalFloatingToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                expandedShadowElevation = 0.dp,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                expanded = true,
                scrollBehavior = scrollBehavior,
                floatingActionButton = {
                    LargeFloatingActionButton(
                        onClick = onDismiss,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close"
                        )
                    }
                },
                content = {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Toggle Shuffle",
                            tint = if (isShuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onToggleRepeat) {
                        val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
                        val repeatIcon = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        }
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Toggle Repeat",
                            tint = if (repeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showTimerOptions = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (activeTimerValueDisplay != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    )
            ) {

            }
        }

        if (showTimerOptions) {
            TimerOptionsBottomSheet(
                activeTimerValueDisplay = activeTimerValueDisplay,
                isEndOfTrackTimerActive = isEndOfTrackTimerActive,
                onDismiss = { showTimerOptions = false },
                onSetPredefinedTimer = onSetPredefinedTimer,
                onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                onOpenCustomTimePicker = onOpenCustomTimePicker,
                onCancelTimer = onCancelTimer
            )
        }
    }
}

@Composable
fun QueuePlaylistSongItem(
    modifier: Modifier = Modifier,
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean? = null,
    isDragging: Boolean,
    onRemoveClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
    isReorderModeEnabled: Boolean,
    isDragHandleVisible: Boolean,
    isRemoveButtonVisible: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val itemShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 22.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTL = 22.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = 22.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBL = 22.dp,
        smoothnessAsPercentBR = 60
    )

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 4.dp else 1.dp,
        label = "elevationAnimation"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isCurrentSong) colors.surfaceContainerLowest else colors.surfaceContainerLowest,
        label = "backgroundColorAnimation"
    )

    Surface(
        modifier = modifier.clip(itemShape),
        shape = itemShape,
        color = backgroundColor,
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = isDragHandleVisible) {
                dragHandle()
            }

            //val albumArtPadding = 12.dp

            val albumArtPadding by animateDpAsState(
                targetValue = if (isDragHandleVisible) 6.dp else 12.dp,
                label = "albumArtPadding"
            )
            Spacer(Modifier.width(albumArtPadding))

            SmartImage(
                model = song.albumArtUriString,
                shape = RoundedCornerShape(8.dp),
                contentDescription = "Carátula",
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSong) colors.primary else colors.onSurface,
                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrentSong) colors.primary.copy(alpha = 0.8f) else colors.onSurfaceVariant
                )
            }

            if (isCurrentSong) {
                if (isPlaying != null) {
                    PlayingEqIcon(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(width = 18.dp, height = 16.dp), // similar al tamaño del ícono
                        color = colors.primary,
                        isPlaying = isPlaying  // o conectalo a tu estado real de reproducción
                    )
                    Spacer(Modifier.width(4.dp))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }

            AnimatedVisibility(visible = isRemoveButtonVisible) {
                FilledIconButton(
                    onClick = onRemoveClick,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.surfaceContainer,
                        contentColor = colors.onSurface
                    ),
                    modifier = Modifier
                        .width(40.dp)
                        .padding(start = 4.dp, end = 8.dp)
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Quitar de la playlist",
                        //tint = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

//@Composable
//private fun PlaylistSongItem(
//    modifier: Modifier = Modifier,
//    song: Song,
//    isPlaying: Boolean,
//    onRemoveClick: () -> Unit,
//    dragHandle: @Composable () -> Unit
//) {
//    Row(
//        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        SmartImage(
//            modifier = Modifier.size(48.dp),
//            contentScale = ContentScale.Crop,
//            model = song.albumArtUriString,
//            contentDescription = "albumart",
//        )
//        Spacer(modifier = Modifier.width(16.dp))
//        Column(modifier = Modifier.weight(1f)) {
//            Text(
//                text = song.title,
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis,
//                style = MaterialTheme.typography.bodyLarge,
//                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
//            )
//            Text(
//                text = song.artist,
//                maxLines = 1,
//                overflow = TextOverflow.Ellipsis,
//                style = MaterialTheme.typography.bodyMedium,
//                color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
//            )
//        }
//        Spacer(modifier = Modifier.width(8.dp))
//        IconButton(onClick = onRemoveClick) {
//            Icon(
//                imageVector = Icons.Rounded.Close,
//                contentDescription = "Remove from queue",
//                tint = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//        }
//        dragHandle()
//    }
//}

//@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
//    ExperimentalMaterial3ExpressiveApi::class
//)
//@Composable
//fun QueueBottomSheet(
//    queue: List<Song>,
//    currentQueueSourceName: String,
//    currentSongId: String?,
//    repeatMode: Int, // Usar constantes de Player.REPEAT_MODE_*
//    isShuffleOn: Boolean,
//    onDismiss: () -> Unit,
//    onPlaySong: (Song) -> Unit,
//    onRemoveSong: (String) -> Unit,
//    onReorder: (from: Int, to: Int) -> Unit,
//    onToggleRepeat: () -> Unit,
//    onToggleShuffle: () -> Unit,
//    // onTimerClick: () -> Unit, // Removed
//    activeTimerValueDisplay: String?,
//    isEndOfTrackTimerActive: Boolean,
//    onSetPredefinedTimer: (minutes: Int) -> Unit,
//    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
//    onOpenCustomTimePicker: () -> Unit,
//    onCancelTimer: () -> Unit
//) {
//    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
//    val colors = MaterialTheme.colorScheme
//    var showTimerOptions by rememberSaveable { mutableStateOf(false) }
//
//    var items by remember { mutableStateOf(queue) }
//    LaunchedEffect(queue) { items = queue }
//
//    val reorderState = rememberReorderState<Song>()
//
//    val listState = rememberLazyListState()
//
//    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
//        exitDirection = FloatingToolbarExitDirection.Bottom
//    )
//
//    ModalBottomSheet(
//        onDismissRequest = onDismiss,
//        sheetState       = sheetState,
//        containerColor   = colors.surfaceContainer,
//        dragHandle       = {
//            BottomSheetDefaults.DragHandle(
//                color = colors.primary
//            )
//        }
//    ) {
//        Box(
//            modifier = Modifier.fillMaxSize()
//        ) {
//            Column(
//                modifier = Modifier
//                    .padding(bottom = 0.dp)
//            ) {
//                Row(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(12.dp),
//                    horizontalArrangement = Arrangement.Absolute.SpaceBetween
//                ) {
//                    Text(
//                        text     = "Next Up",
//                        style    = MaterialTheme.typography.displayMedium,
//                        modifier = Modifier
//                            .padding(horizontal = 12.dp, vertical = 8.dp)
//                            .align(Alignment.CenterVertically)
//                    )
//                    Spacer(modifier = Modifier.width(8.dp))
//                    Box(
//                        modifier = Modifier
//                            .align(Alignment.CenterVertically)
//                            .padding(end = 16.dp)
//                            .background(
//                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
//                                shape = CircleShape
//                            )
//                    ) {
//                        Text(
//                            modifier = Modifier
//                                .padding(horizontal = 10.dp, vertical = 6.dp),
//                            style = MaterialTheme.typography.labelLarge,
//                            text = currentQueueSourceName,
//                            overflow = TextOverflow.Ellipsis,
//                            maxLines = 1
//                        )
//                    }
//                }
//
//                if (items.isEmpty()) {
//                    Box(
//                        modifier         = Modifier
//                            .fillMaxWidth()
//                            .padding(32.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Text("Queue is empty.", color = colors.onSurface)
//                    }
//                } else {
//                    ReorderContainer(
//                        state    = reorderState,
//                        modifier = Modifier.weight(1f)
//                    ) {  // Habilita DnD :contentReference[oaicite:4]{index=4}
//                        LazyColumn(
//                            state = listState,
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(
//                                    horizontal = 16.dp
//                                )
//                                .clip(
//                                    shape = AbsoluteSmoothCornerShape(
//                                        cornerRadiusTR = 26.dp,
//                                        smoothnessAsPercentTL = 60,
//                                        cornerRadiusTL = 26.dp,
//                                        smoothnessAsPercentTR = 60,
//                                        cornerRadiusBR = 0.dp,
//                                        smoothnessAsPercentBL = 60,
//                                        cornerRadiusBL = 0.dp,
//                                        smoothnessAsPercentBR = 60
//
//                                    )
//                                ),
//                            verticalArrangement = Arrangement.spacedBy(8.dp),
//                            contentPadding = PaddingValues(
//                                bottom = 110.dp
//                            )
//                        ) {
//                            itemsIndexed(items, key = { _, s -> s.id }) { _, song ->
//                                ReorderableItem(
//                                    state             = reorderState,
//                                    key               = song.id,
//                                    data              = song,
//                                    dragAfterLongPress = true  // larga pulsación inicia drag
//                                    // **Omitimos `draggableContent`** para que `content` entero sea draggable
//                                    ,onDrop = { draggedState ->
//                                        // reconstruye índices con indexOfFirst porque DraggedItemState no expone índices :contentReference[oaicite:6]{index=6}
//                                        val from = items.indexOfFirst { it.id == draggedState.data.id }
//                                        val to   = items.indexOfFirst { it.id == song.id }
//                                        if (from != to && from >= 0 && to >= 0) {
//                                            items = items.toMutableList().apply {
//                                                add(to, removeAt(from))
//                                            }
//                                            onReorder(from, to)
//                                        }
//                                    }
//                                ) {
//                                    // `content`: TOd O el Row − se usará también como drag preview
//                                    val isDragging = this.isDragging
//                                    val elevation by animateDpAsState(
//                                        targetValue = if (isDragging) 8.dp else 0.dp
//                                    )
//
//                                    PlaylistSongItem(
//                                        modifier = Modifier
//                                            .fillMaxWidth()
//                                            //.then(compressionModifier)
//                                            //.padding(horizontal = 10.dp)
//                                            .clickable {
//                                                onPlaySong(song)
//                                            },
//                                        song = song,
//                                        isDragging = isDragging,
//                                        elevation = elevation,
//                                        isPlaying = song.id == currentSongId,
//                                        onRemoveClick = { onRemoveSong(song.id) },
//                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//
//            HorizontalFloatingToolbar(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .padding(bottom = 24.dp),
//                expandedShadowElevation = 0.dp,
//                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
//                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceVariant
//                ),
//                expanded = true, // Siempre expandida, el scrollBehavior maneja la visibilidad
//                scrollBehavior = scrollBehavior, // Conectar el comportamiento
//                floatingActionButton = {
//                    LargeFloatingActionButton(
//                        onClick = onDismiss,
//                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
//                    ) {
//                        Icon(
//                            imageVector = Icons.Rounded.Close,
//                            contentDescription = ""
//                        )
//                    }
//                },
//                content = {
//                    // Botón de Aleatorio (Shuffle)
//                    IconButton(onClick = onToggleShuffle) {
//                        Icon(
//                            imageVector = Icons.Rounded.Shuffle,
//                            contentDescription = "Toggle Shuffle",
//                            tint = if (isShuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    }
//
//                    // Botón de Repetir (Repeat)
//                    IconButton(onClick = onToggleRepeat) {
//                        val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
//                        val repeatIcon = when (repeatMode) {
//                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
//                            else -> Icons.Rounded.Repeat // El color diferencia entre OFF y ALL
//                        }
//                        Icon(
//                            imageVector = repeatIcon,
//                            contentDescription = "Toggle Repeat",
//                            tint = if (repeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    }
//
//                    // Botón de Temporizador (Timer)
//                    IconButton(onClick = { showTimerOptions = true }) {
//                        Icon(
//                            imageVector = Icons.Rounded.Timer,
//                            contentDescription = "Sleep Timer",
//                            tint = if (activeTimerValueDisplay != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    }
//                }
//            )
//            Box(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .fillMaxWidth()
//                    .height(30.dp)
//                    .background(
//                        brush = Brush.verticalGradient(
//                            listOf(
//                                Color.Transparent,
//                                MaterialTheme.colorScheme.surfaceContainer
//                            )
//                        )
//                    )
//            ) {
//
//            }
//        }
//
//        if (showTimerOptions) {
//            TimerOptionsBottomSheet(
//                activeTimerValueDisplay = activeTimerValueDisplay,
//                isEndOfTrackTimerActive = isEndOfTrackTimerActive,
//                onDismiss = { showTimerOptions = false },
//                onSetPredefinedTimer = { minutes ->
//                    onSetPredefinedTimer(minutes)
//                    // showTimerOptions = false // Optionally dismiss after setting
//                },
//                onSetEndOfTrackTimer = { enable ->
//                    onSetEndOfTrackTimer(enable)
//                    // showTimerOptions = false // Optionally dismiss after setting
//                },
//                onOpenCustomTimePicker = {
//                    onOpenCustomTimePicker()
//                    // showTimerOptions = false // Optionally dismiss after opening picker
//                },
//                onCancelTimer = {
//                    onCancelTimer()
//                    //showTimerOptions = false // Dismiss after cancelling
//                }
//            )
//        }
//    }
//}