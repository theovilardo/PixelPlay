package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.viewmodel.DirectoryEntry
import java.io.File
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerBottomSheet(
    currentPath: File,
    directoryChildren: List<DirectoryEntry>,
    allowedDirectories: Set<String>,
    smartViewEnabled: Boolean,
    isLoading: Boolean,
    isAtRoot: Boolean,
    rootDirectory: File,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onToggleAllowed: (File) -> Unit,
    onRefresh: () -> Unit,
    onSmartViewToggle: (Boolean) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    isDirectorySelected: (File) -> Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        FileExplorerContent(
            currentPath = currentPath,
            directoryChildren = directoryChildren,
            allowedDirectories = allowedDirectories,
            smartViewEnabled = smartViewEnabled,
            isLoading = isLoading,
            isAtRoot = isAtRoot,
            rootDirectory = rootDirectory,
            onNavigateTo = onNavigateTo,
            onNavigateUp = onNavigateUp,
            onNavigateHome = onNavigateHome,
            onToggleAllowed = onToggleAllowed,
            onRefresh = onRefresh,
            onSmartViewToggle = onSmartViewToggle,
            onDone = onDone,
            isDirectorySelected = isDirectorySelected,
            modifier = Modifier.fillMaxHeight(0.9f)
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun FileExplorerContent(
    currentPath: File,
    directoryChildren: List<DirectoryEntry>,
    allowedDirectories: Set<String>,
    smartViewEnabled: Boolean,
    isLoading: Boolean,
    isAtRoot: Boolean,
    rootDirectory: File,
    onNavigateTo: (File) -> Unit,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onToggleAllowed: (File) -> Unit,
    onRefresh: () -> Unit,
    onSmartViewToggle: (Boolean) -> Unit,
    onDone: () -> Unit,
    isDirectorySelected: (File) -> Boolean,
    title: String = "Select music folders",
    leadingContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingContent?.invoke()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !smartViewEnabled,
                onClick = { onSmartViewToggle(false) },
                label = { Text("All folders") }
            )
            FilterChip(
                selected = smartViewEnabled,
                onClick = { onSmartViewToggle(true) },
                label = { Text("Smart View") }
            )
        }

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
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(200))
            }
        ) { (_, children, loading) ->
            when {
                loading -> ExplorerLoadingState()

                children.isEmpty() -> ExplorerEmptyState(text = "No subfolders here")

                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(children, key = { it.file.absolutePath }) { directoryEntry ->
                            val isAllowed = remember(allowedDirectories, smartViewEnabled, directoryEntry) {
                                isDirectorySelected(directoryEntry.file)
                            }
                            val displayCount = if (smartViewEnabled) {
                                directoryEntry.directAudioCount
                            } else {
                                directoryEntry.totalAudioCount
                            }

                            FileExplorerItem(
                                file = directoryEntry.file,
                                audioCount = displayCount,
                                displayName = directoryEntry.displayName,
                                isAllowed = isAllowed,
                                onNavigate = { onNavigateTo(directoryEntry.file) },
                                onToggleAllowed = { onToggleAllowed(directoryEntry.file) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(6.dp)) }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ExtendedFloatingActionButton(
            onClick = onDone,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Done,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Done")
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
    val coroutineScope = rememberCoroutineScope()
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

    val rootLabel = remember(rootDirectory) {
        when (rootDirectory.name) {
            "0", "" -> "Internal storage"
            else -> rootDirectory.name
        }
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

            if (!isAtRoot) {
                LaunchedEffect(currentPath) {
                    coroutineScope.launch {
                        scrollState.scrollTo(scrollState.maxValue)
                        scrollState.scrollTo(scrollState.maxValue)
                    }
                }

                CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        breadcrumbs.forEachIndexed { index, file ->
                            val isRoot = file.path == rootDirectory.path
                            val isLast = index == breadcrumbs.lastIndex
                            val label = if (isRoot) rootLabel else file.name.ifEmpty { file.path }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(AbsoluteSmoothCornerShape(cornerRadius = 12.dp, smoothnessAsPercent = 70))
                                        .clickable(enabled = !isLast) {
                                            if (isRoot) onNavigateHome() else onNavigateTo(file)
                                        }
                                        .background(
                                            color = if (isLast) {
                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    if (isRoot) {
                                        Icon(
                                            imageVector = Icons.Rounded.Home,
                                            contentDescription = "Go to root",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (isLast) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

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
    audioCount: Int,
    displayName: String?,
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

    val badgeColor = if (isAllowed) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.secondary
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
                text = displayName ?: file.name.ifEmpty { file.path },
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = if (isAllowed) contentColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(
                modifier = Modifier
                    .height(6.dp)
                    .fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .clip(AbsoluteSmoothCornerShape(cornerRadius = 10.dp, smoothnessAsPercent = 70))
                    .background(badgeColor.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                val displayCount = if (audioCount > 99) "99+" else audioCount.toString()
                Text(
                    text = if (audioCount == 1) "1 song" else "$displayCount songs",
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = contentColor
        )

        RadioButton(
            selected = isAllowed,
            onClick = onToggleAllowed,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
