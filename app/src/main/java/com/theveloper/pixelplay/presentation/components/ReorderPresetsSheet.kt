package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReorderPresetsSheet(
    allAvailablePresets: List<EqualizerPreset>,
    pinnedPresetsNames: List<String>,
    onSave: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Presets") },
            text = { Text("Are you sure you want to reset to default preset order?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showResetDialog = false
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton( onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Convert to mutable state for local manipulation
    // We need a list that represents the order.
    // Pinned items come first (in their order), then Unpinned items (alphabetical or standard order).
    // The UI allows reordering Pinned items, and Toggling Unpinned items to Pinned (adding to bottom).
    
    // Easier approach: Just keep a list of Pinned Names. The Sheet shows TWO lists?
    // Or one list where unpinned are at bottom?
    // "Reorderable" usually implies a single list.
    // Let's stick to the "Library Tabs" model:
    // Show ALL items. Pinned items are sortable. Unpinned items are "disabled" or just hidden?
    // Actually, LibraryTabsSheet just reorders the *Active* tabs.
    // Here we probably want to manage *Visibility* (Pinning) AND *Order*.
    
    // Let's create a local data structure for the list
    data class PresetItem(val preset: EqualizerPreset, val isPinned: Boolean)

    // Initial state: Pinned presets (in order) + Unpinned presets (rest)
    val initialList = remember(allAvailablePresets, pinnedPresetsNames) {
        val pinned = pinnedPresetsNames.mapNotNull { name -> 
            allAvailablePresets.find { it.name == name }?.let { PresetItem(it, true) }
        }
        val unpinned = allAvailablePresets.filter { !pinnedPresetsNames.contains(it.name) }
            .map { PresetItem(it, false) }
        pinned + unpinned
    }

    var localItems by remember { mutableStateOf(initialList) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            // Only allow reordering if BOTH are pinned? 
            // Or allow reordering anywhere, and top N are pinned?
            // User requirement: "reordenar o 'despinnear'"
            
            // Let's allow full reordering using the library. 
            // The result passed back will be the list of NAMES of items where isPinned == true, in valid order.
            
            localItems = localItems.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
        lazyListState = listState
    )
    
    // Helper to toggle pin
    fun togglePin(item: PresetItem) {
        val newItem = item.copy(isPinned = !item.isPinned)
        val index = localItems.indexOf(item)
        if (index != -1) {
            val newList = localItems.toMutableList()
            newList[index] = newItem
            
            // If pinning, maybe move to bottom of pinned section?
            // If unpinning, maybe move to top of unpinned section?
            // For now just toggle in place.
            localItems = newList
        }
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Manage Presets", style = MaterialTheme.typography.displaySmall, fontFamily = GoogleSansRounded)
                }
            },
            floatingActionButton = {
                FloatingToolBar(
                    modifier = Modifier,
                    onReset = { showResetDialog = true },
                    onDismiss = onDismiss,
                    onClick = {
                        scope.launch {
                            // Extract just the pinned names in order
                            val newPinnedNames = localItems.filter { it.isPinned }.map { it.preset.name }
                            onSave(newPinnedNames)
                            onDismiss()
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
             LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                contentPadding = PaddingValues(bottom = 100.dp, top = paddingValues.calculateTopPadding() + 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(localItems, key = { it.preset.name }) { item ->
                    ReorderableItem(reorderableState, key = item.preset.name) { isDragging ->
                        val elevation = if (isDragging) 4.dp else 0.dp
                        val bgColor = if (item.isPinned) 
                                        MaterialTheme.colorScheme.surfaceContainer
                                      else 
                                        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha=0.6f)
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CircleShape)
                                .clickable { togglePin(item) }, // Allow tapping row to toggle? Or specific button
                            shadowElevation = elevation,
                            color = bgColor 
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Drag Handle (Only if Pinned?)
                                if (item.isPinned) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragIndicator,
                                        contentDescription = "Reorder",
                                        modifier = Modifier.draggableHandle(),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(24.dp)) // Placeholder
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Text(
                                    text = item.preset.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    color = if (item.isPinned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                // Pin/Sync Toggle
                                IconButton(onClick = { togglePin(item) }) {
                                    Icon(
                                        imageVector = if (item.isPinned) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                        contentDescription = if (item.isPinned) "Visible" else "Hidden",
                                        tint = if (item.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
