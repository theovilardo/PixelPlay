package com.theveloper.pixelplay.presentation.components

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.trimmedLength
import coil.compose.AsyncImage
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.screens.PlaylistSongItem
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.media3.common.Player

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentQueueSourceName: String,
    currentSongId: String?,
    repeatMode: Int, // Usar constantes de Player.REPEAT_MODE_*
    isShuffleOn: Boolean,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onTimerClick: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme

    var items by remember { mutableStateOf(queue) }
    LaunchedEffect(queue) { items = queue }

    val reorderState = rememberReorderState<Song>()

    val listState = rememberLazyListState()

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
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = 0.dp)
            ) {
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
                    ReorderContainer(
                        state    = reorderState,
                        modifier = Modifier.weight(1f)
                    ) {  // Habilita DnD :contentReference[oaicite:4]{index=4}
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp
                                )
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
                            contentPadding = PaddingValues(
                                bottom = 110.dp
                            )
                        ) {
                            itemsIndexed(items, key = { _, s -> s.id }) { _, song ->
                                ReorderableItem(
                                    state             = reorderState,
                                    key               = song.id,
                                    data              = song,
                                    dragAfterLongPress = true  // larga pulsación inicia drag
                                    // **Omitimos `draggableContent`** para que `content` entero sea draggable
                                    ,onDrop = { draggedState ->
                                        // reconstruye índices con indexOfFirst porque DraggedItemState no expone índices :contentReference[oaicite:6]{index=6}
                                        val from = items.indexOfFirst { it.id == draggedState.data.id }
                                        val to   = items.indexOfFirst { it.id == song.id }
                                        if (from != to && from >= 0 && to >= 0) {
                                            items = items.toMutableList().apply {
                                                add(to, removeAt(from))
                                            }
                                            onReorder(from, to)
                                        }
                                    }
                                ) {
                                    // `content`: TOd O el Row − se usará también como drag preview
                                    val isDragging = this.isDragging
                                    val elevation by animateDpAsState(
                                        targetValue = if (isDragging) 8.dp else 0.dp
                                    )

                                    PlaylistSongItem(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            //.then(compressionModifier)
                                            //.padding(horizontal = 10.dp)
                                            .clickable {
                                                onPlaySong(song)
                                            },
                                        song = song,
                                        isDragging = isDragging,
                                        elevation = elevation,
                                        isPlaying = song.id == currentSongId,
                                        onRemoveClick = { onRemoveSong(song.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalFloatingToolbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                expandedShadowElevation = 0.dp,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                expanded = true, // Siempre expandida, el scrollBehavior maneja la visibilidad
                scrollBehavior = scrollBehavior, // Conectar el comportamiento
                floatingActionButton = {
                    LargeFloatingActionButton(
                        onClick = onDismiss,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = ""
                        )
                    }
                },
                content = {
                    // Botón de Aleatorio (Shuffle)
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Toggle Shuffle",
                            tint = if (isShuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Botón de Repetir (Repeat)
                    IconButton(onClick = onToggleRepeat) {
                        val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
                        val repeatIcon = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat // El color diferencia entre OFF y ALL
                        }
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = "Toggle Repeat",
                            tint = if (repeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Botón de Temporizador (Timer)
                    IconButton(onClick = onTimerClick) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Sleep Timer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}