package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.io.File

@OptIn(ExperimentalAnimationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerBottomSheet(
    currentPath: File,
    directoryChildren: List<File>,
    allowedDirectories: Set<String>,
    isLoading: Boolean,
    isAtRoot: Boolean,
    rootDirectory: File,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onToggleAllowed: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Select music folders",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            FileExplorerHeader(
                currentPath = currentPath,
                rootDirectory = rootDirectory,
                isAtRoot = isAtRoot,
                onNavigateUp = onNavigateUp,
                onNavigateHome = onNavigateHome,
                onNavigateTo = onNavigateTo
            )

            AnimatedContent(
                targetState = Triple(currentPath, directoryChildren, isLoading),
                label = "directory_content",
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(200)))
                }
            ) { (_, children, loading) ->
                when {
                    loading -> {
                        ExplorerLoadingState()
                    }

                    children.isEmpty() -> {
                        ExplorerEmptyState(text = "No subfolders here")
                    }

                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(children, key = { it.absolutePath }) { file ->
                                val isAllowed = allowedDirectories.contains(file.absolutePath)
                                FileExplorerItem(
                                    file = file,
                                    isAllowed = isAllowed,
                                    onNavigate = { onNavigateTo(file) },
                                    onToggleAllowed = { onToggleAllowed(file) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(6.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileExplorerHeader(
    currentPath: File,
    rootDirectory: File,
    isAtRoot: Boolean,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateTo: (File) -> Unit
) {
    val scrollState = rememberScrollState()
    val breadcrumbs = remember(currentPath, rootDirectory) {
        val segments = mutableListOf<File>()
        var cursor: File? = currentPath
        val rootPath = rootDirectory.path
        while (cursor != null) {
            segments.add(cursor)
            if (cursor.path == rootPath) break
            cursor = cursor.parentFile
        }
        segments.reversed()
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isAtRoot) {
                IconButton(
                    onClick = onNavigateUp,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Navigate up",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            FilledTonalButton(
                onClick = onNavigateHome,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "Go to root",
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(text = rootDirectory.name.ifEmpty { "Storage" })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            breadcrumbs.forEachIndexed { index, file ->
                val isLast = index == breadcrumbs.lastIndex
                val label = file.name.ifEmpty { file.path }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal),
                        color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 70))
                            .clickable(enabled = !isLast) { onNavigateTo(file) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )

                    if (!isLast) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

@Composable
private fun ExplorerEmptyState(
    text: String,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ExplorerLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Loading folders…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FileExplorerItem(
    file: File,
    isAllowed: Boolean,
    onNavigate: () -> Unit,
    onToggleAllowed: () -> Unit
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 18.dp,
        smoothnessAsPercentBR = 90,
        cornerRadiusTR = 18.dp,
        smoothnessAsPercentBL = 90,
        cornerRadiusBL = 18.dp,
        smoothnessAsPercentTR = 90,
        cornerRadiusBR = 18.dp,
        smoothnessAsPercentTL = 90
    )

    val containerColor = if (isAllowed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = if (isAllowed) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .clickable { onNavigate() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = contentColor
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name.ifEmpty { file.path },
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = if (isAllowed) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        RadioButton(
            selected = isAllowed,
            onClick = onToggleAllowed,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
