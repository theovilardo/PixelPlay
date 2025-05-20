package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mohamedrejeb.compose.dnd.reorder.ReorderContainer
import com.mohamedrejeb.compose.dnd.reorder.ReorderableItem
import com.mohamedrejeb.compose.dnd.reorder.rememberReorderState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueBottomSheet(
    queue: List<Song>,
    currentSongId: String?,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme

    var items by remember { mutableStateOf(queue) }
    LaunchedEffect(queue) { items = queue }

    val reorderState = rememberReorderState<Song>()   // :contentReference[oaicite:2]{index=2}

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = colors.onPrimary,
        dragHandle       = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 0.dp)
        ) {
            Text(
                text     = "Next up",
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally)
            )

            //Divider()

            if (items.isEmpty()) {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("La cola está vacía", color = colors.onSurface)
                }
            } else {
                ReorderContainer(
                    state    = reorderState,
                    modifier = Modifier.weight(1f)
                ) {  // Habilita DnD :contentReference[oaicite:4]{index=4}
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
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

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            colors.onPrimary
                                        )
                                        .clickable { onPlaySong(song) }
                                        .shadow(elevation)
                                        .padding(vertical = 4.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Handle visual, pero no capturará drag aparte
                                    Icon(
                                        painter           = painterResource(R.drawable.rounded_drag_handle_24),
                                        contentDescription = "Arrastrar",
                                        tint              = colors.onSurface,
                                        modifier          = Modifier
                                            .padding(end = 8.dp)
                                            .size(24.dp)
                                    )
                                    SmartImage(
                                        model = song.albumArtUri ?: R.drawable.rounded_music_note_24,
                                        contentDescription = "Carátula",
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .size(40.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text      = song.title,
                                            maxLines  = 1,
                                            overflow  = TextOverflow.Ellipsis,
                                            color     = if (song.id == currentSongId) colors.primary else colors.secondary,
                                            fontWeight = if (song.id == currentSongId) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text      = song.artist,
                                            maxLines  = 1,
                                            overflow  = TextOverflow.Ellipsis,
                                            style     = MaterialTheme.typography.bodySmall,
                                            color     = colors.secondary.copy(alpha = 0.8f)
                                        )
                                    }
                                    IconButton(onClick = { onRemoveSong(song.id) }) {
                                        Icon(
                                            painter           = painterResource(R.drawable.rounded_close_24),
                                            contentDescription = "Eliminar",
                                            tint              = colors.tertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}