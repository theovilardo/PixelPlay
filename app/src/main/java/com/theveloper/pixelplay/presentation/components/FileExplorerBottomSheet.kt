package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FileExplorerBottomSheet(
    currentPath: File,
    children: List<File>,
    allowedDirectories: Set<String>,
    isLoading: Boolean,
    canNavigateUp: Boolean,
    onNavigateUp: () -> Unit,
    onDirectoryClick: (File) -> Unit,
    onToggleAllowed: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            FileExplorerNavigationHeader(
                currentPath = currentPath,
                canNavigateUp = canNavigateUp,
                onNavigateUp = onNavigateUp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedContent(
                targetState = currentPath.absolutePath,
                transitionSpec = {
                    slideInHorizontally(animationSpec = tween(300)) { it } togetherWith
                        slideOutHorizontally(animationSpec = tween(250)) { -it } using
                        SizeTransform(clip = false)
                },
                label = "DirectoryTransition"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(AbsoluteSmoothCornerShape(18.dp, 70))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        children.isEmpty() -> {
                            EmptyDirectoryState()
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = BottomSheetDefaults.windowInsets.asPaddingValues()
                            ) {
                                items(children, key = { it.absolutePath }) { child ->
                                    val isAllowed = allowedDirectories.contains(child.absolutePath)
                                    FileExplorerItem(
                                        file = child,
                                        isAllowed = isAllowed,
                                        onNavigate = onDirectoryClick,
                                        onToggle = onToggleAllowed
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

@Composable
private fun FileExplorerNavigationHeader(
    currentPath: File,
    canNavigateUp: Boolean,
    onNavigateUp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconButton(
            onClick = onNavigateUp,
            enabled = canNavigateUp,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.clip(CircleShape)
        ) {
            Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = "Navigate up")
        }

        Column(modifier = Modifier.weight(1f)) {
            val name = currentPath.name.ifEmpty { currentPath.absolutePath }
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentPath.absolutePath,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FileExplorerItem(
    file: File,
    isAllowed: Boolean,
    onNavigate: (File) -> Unit,
    onToggle: (File) -> Unit,
) {
    val containerColor = if (isAllowed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isAllowed) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val shape = AbsoluteSmoothCornerShape(20.dp, 65)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .clickable { onNavigate(file) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = contentColor.copy(alpha = 0.16f),
            contentColor = contentColor,
            shape = AbsoluteSmoothCornerShape(14.dp, 70),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = Icons.Rounded.Folder, contentDescription = null)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            val name = file.name.ifEmpty { file.absolutePath }
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = isAllowed,
            onCheckedChange = { onToggle(file) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            )
        )
    }
}

@Composable
private fun EmptyDirectoryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = AbsoluteSmoothCornerShape(26.dp, 70),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(96.dp),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = Icons.Rounded.FolderOff, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No subfolders found here",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Try a different folder or create one to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
