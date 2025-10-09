package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    ) {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    TextButton(
                        onClick = {
                            onReset()
                            onDismiss()
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text("Reset")
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
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
            floatingActionButtonPosition = FabPosition.Center
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
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(localTabs, key = { it }) { tab ->
                            ReorderableItem(reorderableState, key = tab) { isDragging ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
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