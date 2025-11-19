package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.rememberSwipeableState
import androidx.compose.material.swipeable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.draggableHandle
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun QueueBottomSheet(
    viewModel: PlayerViewModel = hiltViewModel(),
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
    onClearQueue: () -> Unit,
    activeTimerValueDisplay: String?,
    playCount: Float,
    isEndOfTrackTimerActive: Boolean,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onPlayCounter: (count: Int) -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 10.dp,
    shape: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
    val colors = MaterialTheme.colorScheme
    var showTimerOptions by rememberSaveable { mutableStateOf(false) }
    var showClearQueueDialog by remember { mutableStateOf(false) }

    val stablePlayerState by viewModel.stablePlayerState.collectAsState()

    val isPlaying = stablePlayerState.isPlaying

    val currentSongIndex = remember(queue, currentSongId) {
        queue.indexOfFirst { it.id == currentSongId }
    }

    val displayStartIndex = remember(currentSongIndex) { if (currentSongIndex >= 0) currentSongIndex else 0 }
    val displayQueue = remember(queue, currentSongId, currentSongIndex) {
        queue.drop(displayStartIndex)
    }

    var items by remember { mutableStateOf(displayQueue) }
    LaunchedEffect(displayQueue) {
        items = displayQueue
    }

    val listState = rememberLazyListState()
    val canDragSheetFromList by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val updatedCanDragSheet by rememberUpdatedState(canDragSheetFromList)
    var draggingSheetFromList by remember { mutableStateOf(false) }
    var listDragAccumulated by remember { mutableStateOf(0f) }
    val view = LocalView.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }
    var pendingReorderSongId by remember { mutableStateOf<String?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }
    val updatedReorderHandleInUse by rememberUpdatedState(reorderHandleInUse)

    fun mapLazyListIndexToLocal(indexInfo: LazyListItemInfo?): Int? {
        val key = indexInfo?.key ?: return null
        val resolvedIndex = items.indexOfFirst { it.id == key }
        return resolvedIndex.takeIf { it != -1 }
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromLocalIndex = mapLazyListIndexToLocal(from) ?: return@rememberReorderableLazyListState
            val toLocalIndex = mapLazyListIndexToLocal(to) ?: return@rememberReorderableLazyListState
            val movingSongId = items.getOrNull(fromLocalIndex)?.id
            items = items.toMutableList().apply {
                add(toLocalIndex, removeAt(fromLocalIndex))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = fromLocalIndex
            }
            lastMovedTo = toLocalIndex
            if (movingSongId != null && pendingReorderSongId == null) {
                pendingReorderSongId = movingSongId
            }
        },
    )
    val isReordering by remember {
        derivedStateOf { reorderableState.isAnyItemDragging }
    }
    val updatedIsReordering by rememberUpdatedState(isReordering)
    val updatedOnQueueDragStart by rememberUpdatedState(onQueueDragStart)
    val updatedOnQueueDrag by rememberUpdatedState(onQueueDrag)
    val updatedOnQueueRelease by rememberUpdatedState(onQueueRelease)

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            val fromIndex = lastMovedFrom
            val toIndex = lastMovedTo
            val movedSongId = pendingReorderSongId

            lastMovedFrom = null
            lastMovedTo = null
            pendingReorderSongId = null

            if (fromIndex != null && toIndex != null && movedSongId != null) {
                val fromOriginalIndex = displayStartIndex + fromIndex
                val resolvedTargetLocalIndex = items.indexOfFirst { it.id == movedSongId }
                    .takeIf { it != -1 } ?: toIndex
                val toOriginalIndex = displayStartIndex + resolvedTargetLocalIndex

                val fromWithinQueue = fromOriginalIndex in queue.indices
                val toWithinQueue = toOriginalIndex in queue.indices

                if (fromWithinQueue && toWithinQueue && fromOriginalIndex != toOriginalIndex) {
                    onReorder(fromOriginalIndex, toOriginalIndex)
                    return@LaunchedEffect
                }
            }

            items = displayQueue
        }
    }

    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    val listDragConnection = remember(updatedCanDragSheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (updatedIsReordering || updatedReorderHandleInUse) return Offset.Zero

                if (draggingSheetFromList) {
                    listDragAccumulated += available.y
                    onQueueDrag(available.y)
                    return available
                }

                if (available.y > 0 && updatedCanDragSheet) {
                    if (!draggingSheetFromList) {
                        draggingSheetFromList = true
                        listDragAccumulated = 0f
                        onQueueDragStart()
                    }
                    listDragAccumulated += available.y
                    onQueueDrag(available.y)
                    return Offset(0f, available.y)
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (updatedIsReordering || updatedReorderHandleInUse) return Velocity.Zero

                if (available.y > 0 && updatedCanDragSheet) {
                    if (!draggingSheetFromList) {
                        draggingSheetFromList = true
                        listDragAccumulated = 0f
                        onQueueDragStart()
                    }
                    onQueueRelease(listDragAccumulated, available.y)
                    draggingSheetFromList = false
                    listDragAccumulated = 0f
                    return available
                }
                return Velocity.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (updatedIsReordering || updatedReorderHandleInUse) return Offset.Zero

                if (draggingSheetFromList && source == NestedScrollSource.Drag && available.y != 0f) {
                    listDragAccumulated += available.y
                    onQueueDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (updatedIsReordering || updatedReorderHandleInUse) return Velocity.Zero

                if (draggingSheetFromList) {
                    onQueueRelease(listDragAccumulated, available.y)
                    draggingSheetFromList = false
                    listDragAccumulated = 0f
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val directSheetDragModifier =
        if (updatedIsReordering || updatedReorderHandleInUse) {
            Modifier
        } else {
            Modifier.pointerInput(updatedOnQueueDragStart, updatedOnQueueDrag, updatedOnQueueRelease) {
                var dragTotal = 0f
                val dragVelocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        dragTotal = 0f
                        dragVelocityTracker.resetTracking()
                        updatedOnQueueDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                        dragVelocityTracker.addPosition(change.uptimeMillis, change.position)
                        updatedOnQueueDrag(dragAmount)
                    },
                    onDragEnd = {
                        val velocity = dragVelocityTracker.calculateVelocity().y
                        updatedOnQueueRelease(dragTotal, velocity)
                    },
                    onDragCancel = {
                        val velocity = dragVelocityTracker.calculateVelocity().y
                        updatedOnQueueRelease(dragTotal, velocity)
                    }
                )
            }
        }

    LaunchedEffect(listState.isScrollInProgress, draggingSheetFromList) {
        if (draggingSheetFromList && !listState.isScrollInProgress) {
            onQueueRelease(listDragAccumulated, 0f)
            draggingSheetFromList = false
            listDragAccumulated = 0f
        }
    }

    Surface(
        modifier = modifier,
        shape = shape,
        tonalElevation = tonalElevation,
        color = colors.surfaceContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .padding(top = 32.dp)
                        .then(directSheetDragModifier),
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
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusTL = 26.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusBR = 0.dp,
                                    smoothnessAsPercentBR = 60,
                                    cornerRadiusBL = 0.dp,
                                    smoothnessAsPercentBL = 60
                                )
                            )
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 26.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusTL = 26.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusBR = 0.dp,
                                    smoothnessAsPercentBR = 60,
                                    cornerRadiusBL = 0.dp,
                                    smoothnessAsPercentBL = 60
                                )
                            )
                            .then(
                                if (isReordering || reorderHandleInUse) {
                                    Modifier
                                } else {
                                    Modifier.nestedScroll(listDragConnection)
                                }
                            ),
                        userScrollEnabled = !(isReordering || reorderHandleInUse),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 110.dp)
                    ) {
                        item("queue_top_spacer") {
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        itemsIndexed(items, key = { _, s -> s.id }) { index, song ->
                            ReorderableItem(
                                state = reorderableState,
                                key = song.id,
                                enabled = index != 0
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
                                    ,
                                    onClick = { onPlaySong(song) },
                                    song = song,
                                    isCurrentSong = song.id == currentSongId,
                                    isPlaying = isPlaying,
                                    isDragging = isDragging,
                                    onRemoveClick = { onRemoveSong(song.id) },
                                    isReorderModeEnabled = false,
                                    isDragHandleVisible = index != 0,
                                    isRemoveButtonVisible = false,
                                    enableSwipeToDismiss = index != 0,
                                    onDismiss = { onRemoveSong(song.id) },
                                    dragHandle = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier
                                                .draggableHandle(
                                                        onDragStarted = {
                                                            draggingSheetFromList = false
                                                            reorderHandleInUse = true
                                                            ViewCompat.performHapticFeedback(
                                                                view,
                                                            HapticFeedbackConstantsCompat.GESTURE_START
                                                        )
                                                    },
                                                    onDragStopped = {
                                                        reorderHandleInUse = false
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

            HorizontalFloatingToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .then(directSheetDragModifier),
                expandedShadowElevation = 0.dp,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                expanded = true,
                scrollBehavior = scrollBehavior,
                floatingActionButton = {
                    LargeFloatingActionButton(
                        onClick = { showClearQueueDialog = true },
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_clear_all_24),
                            contentDescription = "Clear Queue"
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
                onPlayCounter = onPlayCounter,
                activeTimerValueDisplay = activeTimerValueDisplay,
                playCount = playCount,
                isEndOfTrackTimerActive = isEndOfTrackTimerActive,
                onDismiss = { showTimerOptions = false },
                onSetPredefinedTimer = onSetPredefinedTimer,
                onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                onOpenCustomTimePicker = onOpenCustomTimePicker,
                onCancelCountedPlay = onCancelCountedPlay,
                onCancelTimer = onCancelTimer
            )
        }

        if (showClearQueueDialog) {
            AlertDialog(
                onDismissRequest = { showClearQueueDialog = false },
                title = { Text("Clear Queue") },
                text = { Text("Are you sure you want to clear all songs from the queue except the current one?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearQueue()
                            showClearQueueDialog = false
                        }
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearQueueDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun QueuePlaylistSongItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean? = null,
    isDragging: Boolean,
    onRemoveClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
    isReorderModeEnabled: Boolean,
    isDragHandleVisible: Boolean,
    isRemoveButtonVisible: Boolean,
    enableSwipeToDismiss: Boolean = false,
    onDismiss: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme

    val cornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong) 60.dp else 22.dp,
        label = "cornerRadiusAnimation"
    )

    val itemShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = cornerRadius,
        smoothnessAsPercentTL = 60,
        cornerRadiusTL = cornerRadius,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = cornerRadius,
        smoothnessAsPercentBL = 60,
        cornerRadiusBL = cornerRadius,
        smoothnessAsPercentBR = 60
    )

    val albumCornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong) 60.dp else 8.dp,
        label = "cornerRadiusAnimation"
    )

    val albumShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = albumCornerRadius,
        smoothnessAsPercentTL = 60,
        cornerRadiusTL = albumCornerRadius,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = albumCornerRadius,
        smoothnessAsPercentBL = 60,
        cornerRadiusBL = albumCornerRadius,
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

    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = constraints.maxWidth.toFloat()
        val swipeAnchors = remember(maxWidthPx) {
            mapOf(0f to SwipeState.Resting, -maxWidthPx to SwipeState.Dismissed)
        }
        val capsuleGap = 4.dp

        val swipeableState = rememberSwipeableState(
            initialValue = SwipeState.Resting,
            confirmStateChange = { target ->
                if (target == SwipeState.Dismissed) {
                    onDismiss()
                }
                true
            }
        )

        val offsetX by remember { derivedStateOf { if (enableSwipeToDismiss) swipeableState.offset.value else 0f } }
        val dismissProgress by remember { derivedStateOf { (offsetX / -maxWidthPx).coerceIn(0f, 1f) } }

        val capsuleWidth by animateDpAsState(
            targetValue = with(density) { (maxWidthPx * dismissProgress).toDp() },
            label = "capsuleWidth"
        )
        val iconAlpha by animateFloatAsState(
            targetValue = if (dismissProgress > 0.2f) 1f else 0f,
            label = "dismissIconAlpha"
        )
        val iconScale by animateFloatAsState(
            targetValue = if (dismissProgress > 0.2f) 1f else 0.8f,
            label = "dismissIconScale"
        )

        val hapticView = LocalView.current
        var dismissHapticPlayed by remember { mutableStateOf(false) }

        LaunchedEffect(dismissProgress, enableSwipeToDismiss) {
            if (!enableSwipeToDismiss) return@LaunchedEffect

            if (dismissProgress > 0.5f && !dismissHapticPlayed) {
                dismissHapticPlayed = true
                ViewCompat.performHapticFeedback(
                    hapticView,
                    HapticFeedbackConstantsCompat.GESTURE_END
                )
            } else if (dismissProgress < 0.25f) {
                dismissHapticPlayed = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .swipeable(
                    enabled = enableSwipeToDismiss && !isDragging,
                    state = swipeableState,
                    anchors = swipeAnchors,
                    thresholds = { _, _ -> FractionalThreshold(0.6f) },
                    orientation = Orientation.Horizontal,
                    resistance = null,
                )
        ) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .clip(itemShape)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(capsuleGap))

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(capsuleWidth)
                        .clip(CircleShape)
                        .background(colors.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_close_24),
                        contentDescription = "Dismiss song",
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            alpha = iconAlpha
                        },
                        tint = colors.onErrorContainer
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp + capsuleGap)
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .clip(itemShape)
                    .clickable(enabled = offsetX == 0f) {
                        onClick()
                    },
                shape = itemShape,
                color = backgroundColor,
                tonalElevation = elevation,
                shadowElevation = elevation
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(visible = isDragHandleVisible) {
                        dragHandle()
                    }

                    val albumArtPadding by animateDpAsState(
                        targetValue = if (isDragHandleVisible) 6.dp else 12.dp,
                        label = "albumArtPadding"
                    )
                    Spacer(Modifier.width(albumArtPadding))

                    SmartImage(
                        model = song.albumArtUriString,
                        shape = albumShape,
                        contentDescription = "Carátula",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(albumShape),
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
                                    .size(width = 18.dp, height = 16.dp),
                                color = colors.primary,
                                isPlaying = isPlaying
                            )
                            Spacer(Modifier.width(4.dp))
                            if (!isRemoveButtonVisible){
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }

                    AnimatedVisibility(visible = isRemoveButtonVisible && !enableSwipeToDismiss) {
                        FilledIconButton(
                            onClick = onRemoveClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.surfaceContainer,
                                contentColor = colors.onSurface
                            ),
                            modifier = Modifier
                                .width(40.dp)
                                .height(40.dp)
                                .padding(start = 4.dp, end = 8.dp)
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                painter = painterResource(R.drawable.rounded_close_24),
                                contentDescription = "Remove from playlist",
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
private enum class SwipeState { Resting, Dismissed }