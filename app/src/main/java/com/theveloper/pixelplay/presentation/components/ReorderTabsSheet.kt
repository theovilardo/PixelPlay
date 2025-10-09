package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.draggableHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderTabsSheet(
    tabs: List<String>,
    onReorder: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var localTabs by remember { mutableStateOf(tabs) }
    val scope = rememberCoroutineScope()
    val reorderableState = rememberReorderableLazyListState(onMove = { from, to ->
        localTabs = localTabs.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    })
    var isLoading by remember { mutableStateOf(false) }

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
                    Text("Reorder Library Tabs", style = MaterialTheme.typography.displaySmall, fontFamily = GoogleSansRounded)
                    TextButton(
                        onClick = {
                            onReset()
                            onDismiss()
                        }
                    ) {
                        Text("Reset")
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = 18.dp, end = 8.dp),
                    shape = CircleShape,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(1000) // Simulate network/db operation
                            onReorder(localTabs)
                            isLoading = false
                            onDismiss()
                        }
                    },
                    icon = { Icon(Icons.Rounded.Check, contentDescription = "Done") },
                    text = { Text("Done") }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Reordering tabs...")
                    }
                } else {
                    LazyColumn(
                        state = reorderableState.listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(bottom = 100.dp, top = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localTabs, key = { it }) { tab ->
                            ReorderableItem(reorderableState, key = tab) { isDragging ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape),
                                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = "Drag handle",
                                            modifier = Modifier.draggableHandle()
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(text = tab, style = MaterialTheme.typography.bodyLarge)
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