package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReorderPresetsSheet(
    visible: Boolean,
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
            text = { Text("This will restore the default preset order and visibility. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        showResetDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    // Data class for preset items
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
            localItems = newList
        }
    }

    // Fullscreen dialog with animation
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
        AnimatedVisibility(
            visibleState = transitionState,
            enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
            label = "manage_presets_dialog"
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            title = {
                                Text(
                                    text = "Manage Presets",
                                    fontFamily = GoogleSansRounded,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 22.sp,
                                        textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            },
                            navigationIcon = {
                                FilledIconButton(
                                    modifier = Modifier.padding(start = 6.dp),
                                    onClick = onDismiss,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close"
                                    )
                                }
                            },
                            actions = {
                                FilledIconButton(
                                    modifier = Modifier.padding(end = 6.dp),
                                    onClick = { showResetDialog = true },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.outline_restart_alt_24),
                                        contentDescription = "Reset to default"
                                    )
                                }
                            }
                        )
                    },
                    contentWindowInsets = WindowInsets.systemBars,
                    floatingActionButton = {
                        MediumExtendedFloatingActionButton(
                            modifier = Modifier.padding(end = 12.dp),
                            onClick = {
                                scope.launch {
                                    val newPinnedNames = localItems.filter { it.isPinned }.map { it.preset.name }
                                    onSave(newPinnedNames)
                                    onDismiss()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = CircleShape,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Done",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "Done")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) { innerPadding ->
                    Box() {
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Description
                            Text(
                                text = "Drag to reorder • Tap eye to show/hide",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )

                            // Preset list
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = 100.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(localItems, key = { it.preset.name }) { item ->
                                    ReorderableItem(reorderableState, key = item.preset.name) { isDragging ->
                                        val elevation = if (isDragging) 8.dp else 0.dp
                                        val itemShape = AbsoluteSmoothCornerShape(
                                            cornerRadiusTR = 20.dp,
                                            smoothnessAsPercentTL = 60,
                                            cornerRadiusTL = 20.dp,
                                            smoothnessAsPercentTR = 60,
                                            cornerRadiusBR = 20.dp,
                                            smoothnessAsPercentBL = 60,
                                            cornerRadiusBL = 20.dp,
                                            smoothnessAsPercentBR = 60
                                        )

                                        val bgColor by animateColorAsState(
                                            targetValue = when {
                                                isDragging -> MaterialTheme.colorScheme.surfaceContainerHighest
                                                item.isPinned -> MaterialTheme.colorScheme.surfaceContainer
                                                else -> MaterialTheme.colorScheme.surfaceContainerLowest
                                            },
                                            label = "bgColor"
                                        )

                                        val contentColor by animateColorAsState(
                                            targetValue = if (item.isPinned)
                                                MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            label = "contentColor"
                                        )

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(itemShape)
                                                .clickable { togglePin(item) },
                                            shadowElevation = elevation,
                                            tonalElevation = if (item.isPinned) 1.dp else 0.dp,
                                            color = bgColor,
                                            shape = itemShape
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Drag Handle
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (item.isPinned)
                                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                                            else
                                                                MaterialTheme.colorScheme.surfaceContainerLow
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DragIndicator,
                                                        contentDescription = "Reorder",
                                                        modifier = if (item.isPinned) Modifier.draggableHandle() else Modifier,
                                                        tint = if (item.isPinned)
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        else
                                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(14.dp))

                                                Text(
                                                    text = item.preset.displayName,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (item.isPinned) FontWeight.Medium else FontWeight.Normal,
                                                    modifier = Modifier.weight(1f),
                                                    color = contentColor
                                                )

                                                // Visibility Toggle
                                                IconButton(
                                                    onClick = { togglePin(item) },
                                                ) {
                                                    Icon(
                                                        imageVector = if (item.isPinned)
                                                            Icons.Rounded.Visibility
                                                        else
                                                            Icons.Rounded.VisibilityOff,
                                                        contentDescription = if (item.isPinned) "Visible" else "Hidden",
                                                        tint = if (item.isPinned)
                                                            MaterialTheme.colorScheme.primary
                                                        else
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
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
                                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                                .height(90.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }

        }
    }
}
