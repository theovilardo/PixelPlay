package com.theveloper.pixelplay.presentation.components.subcomps

import android.os.Environment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.io.File

val defaultShape = RoundedCornerShape(26.dp) // Fallback shape

@Composable
fun LibraryActionRow(
    currentPage: Int,
    onMainActionClick: () -> Unit,
    iconRotation: Float,
    showSortButton: Boolean,
    onSortIconClick: () -> Unit,
    showSortMenu: Boolean,
    onDismissSortMenu: () -> Unit,
    currentSortOptionsForTab: List<SortOption>,
    selectedSortOption: SortOption,
    onSortOptionSelected: (SortOption) -> Unit,
    isPlaylistTab: Boolean,
    onGenerateWithAiClick: () -> Unit,
    onFilterClick: () -> Unit,
    isFoldersTab: Boolean,
    modifier: Modifier = Modifier,
    // Breadcrumb parameters
    currentFolder: MusicFolder?,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = isFoldersTab,
            label = "ActionRowContent",
            transitionSpec = {
                if (targetState) { // Transition to Folders (Breadcrumbs)
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                } else { // Transition to other tabs (Buttons)
                    slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                }
            },
            modifier = Modifier.weight(1f)
        ) { isFolders ->
            if (isFolders) {
                Breadcrumbs(
                    currentFolder = currentFolder,
                    onFolderClick = onFolderClick,
                    onNavigateBack = onNavigateBack
                )
            } else {
                val newButtonEndCorner by animateDpAsState(
                    targetValue = if (isPlaylistTab) 6.dp else 26.dp,
                    label = "NewButtonEndCorner"
                )
                val generateButtonStartCorner by animateDpAsState(
                    targetValue = if (isPlaylistTab) 6.dp else 26.dp,
                    label = "GenerateButtonStartCorner"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(
                        onClick = onMainActionClick,
                        shape = RoundedCornerShape(
                            topStart = 26.dp, bottomStart = 26.dp,
                            topEnd = newButtonEndCorner, bottomEnd = newButtonEndCorner
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        val icon = if (isPlaylistTab) Icons.Rounded.PlaylistAdd else Icons.Rounded.Shuffle
                        val text = if (isPlaylistTab) "New" else "Shuffle"
                        val contentDesc = if (isPlaylistTab) "Create New Playlist" else "Shuffle Play"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = contentDesc,
                                modifier = Modifier.size(20.dp).rotate(iconRotation)
                            )
                            Text(
                                modifier = Modifier.animateContentSize(),
                                text = text,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isPlaylistTab,
                        enter = fadeIn() + expandHorizontally(
                            expandFrom = Alignment.Start,
                            clip = false, // <— evita el “corte” durante la expansión
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        ),
                        exit = fadeOut() + shrinkHorizontally(
                            shrinkTowards = Alignment.Start,
                            clip = false, // <— evita el “corte” durante la expansión
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = onGenerateWithAiClick,
                                shape = RoundedCornerShape(
                                    topStart = generateButtonStartCorner, bottomStart = generateButtonStartCorner,
                                    topEnd = 26.dp, bottomEnd = 26.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.generate_playlist_ai),
                                        contentDescription = "Generate with AI",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Generate",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }


        Spacer(modifier = Modifier.width(8.dp))

        if (showSortButton) {
            Box {
                FilledTonalIconButton(onClick = onSortIconClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Sort Options",
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = onDismissSortMenu,
                    properties = PopupProperties(
                        clippingEnabled = true
                    ),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 22.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = 22.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusBL = 22.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = 22.dp,
                        smoothnessAsPercentBL = 60
                    ),
                    containerColor = Color.Transparent,
                    shadowElevation = 0.dp,
                    modifier = Modifier.background(
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) // Custom background for dropdown
                ) {
                    currentSortOptionsForTab.forEach { option ->
                        val enabled = option == selectedSortOption
                        DropdownMenuItem(
                            modifier = Modifier
                                .padding(4.dp)
                                .padding(horizontal = 8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow, //if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                    shape = if (enabled) CircleShape else AbsoluteSmoothCornerShape(
                                        cornerRadiusTL = 12.dp,
                                        smoothnessAsPercentBR = 60,
                                        cornerRadiusTR = 12.dp,
                                        smoothnessAsPercentTL = 60,
                                        cornerRadiusBL = 12.dp,
                                        smoothnessAsPercentTR = 60,
                                        cornerRadiusBR = 12.dp,
                                        smoothnessAsPercentBL = 60
                                    )
                                )
                                .clip(if (enabled) CircleShape else RoundedCornerShape(12.dp)),
                            text = { Text(option.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                onSortOptionSelected(option)
                                // onDismissSortMenu() // Already called in LibraryScreen's onSortOptionSelected lambda
                            },
                            leadingIcon = if (enabled) { // Check if it's the selected one
                                {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Breadcrumbs(
    currentFolder: MusicFolder?,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val rowState = rememberLazyListState()
    val storageRootPath = Environment.getExternalStorageDirectory().path
    val pathSegments = remember(currentFolder?.path) {
        val path = currentFolder?.path ?: storageRootPath
        val relativePath = path.removePrefix(storageRootPath).removePrefix("/")
        if (relativePath.isEmpty() || path == storageRootPath) {
            listOf("Internal Storage" to storageRootPath)
        } else {
            listOf("Internal Storage" to storageRootPath) + relativePath.split("/").scan("") { acc, segment ->
                "$acc/$segment"
            }.drop(1).map {
                val file = File(storageRootPath, it)
                file.name to file.path
            }
        }
    }

    val showStartFade by remember { derivedStateOf { rowState.canScrollBackward } }
    val showEndFade by remember { derivedStateOf { rowState.canScrollForward } }

    LaunchedEffect(pathSegments.size) {
        if (pathSegments.isNotEmpty()) {
            rowState.animateScrollToItem(pathSegments.lastIndex + 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(36.dp),
            enabled = currentFolder != null
        ) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.width(8.dp))

        LazyRow(
            state = rowState,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                // 1. Forzamos que el contenido se dibuje en una capa separada.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // 2. Dibujamos el contenido original (el LazyRow).
                    drawContent()

                    // 3. Dibujamos los gradientes que actúan como "máscaras de borrado".
                    val gradientWidth = 24.dp.toPx()

                    // Máscara para el borde IZQUIERDO
                    if (showStartFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                // Gradiente de transparente a opaco (negro)
                                colors = listOf(Color.Transparent, Color.Black),
                                endX = gradientWidth
                            ),
                            // DstIn mantiene el contenido del LazyRow solo donde esta capa es opaca.
                            blendMode = BlendMode.DstIn
                        )
                    }

                    // Máscara para el borde DERECHO
                    if (showEndFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                // Gradiente de opaco (negro) a transparente
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = this.size.width - gradientWidth
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            item { Spacer(modifier = Modifier.width(12.dp)) }

            items(pathSegments.size) { index ->
                val (name, path) = pathSegments[index]
                val isLast = index == pathSegments.lastIndex
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = GoogleSansRounded,
                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isLast) { onFolderClick(path) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                if (!isLast) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.width(12.dp)) }
        }
    }
}

//@Composable
//fun Breadcrumbs(
//    currentFolder: MusicFolder?,
//    onFolderClick: (String) -> Unit,
//    onNavigateBack: () -> Unit
//) {
//    val rowState = rememberLazyListState()
//    val storageRootPath = Environment.getExternalStorageDirectory().path
//    val pathSegments = remember(currentFolder?.path) {
//        val path = currentFolder?.path ?: storageRootPath
//        val relativePath = path.removePrefix(storageRootPath).removePrefix("/")
//        if (relativePath.isEmpty() || path == storageRootPath) {
//            listOf("Internal Storage" to storageRootPath)
//        } else {
//            listOf("Internal Storage" to storageRootPath) + relativePath.split("/").scan("") { acc, segment ->
//                "$acc/$segment"
//            }.drop(1).map {
//                val file = File(storageRootPath, it)
//                file.name to file.path
//            }
//        }
//    }
//
//    LaunchedEffect(pathSegments.size) {
//        if (pathSegments.isNotEmpty()) {
//            rowState.animateScrollToItem(pathSegments.lastIndex)
//        }
//    }
//
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(end = 8.dp),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        FilledTonalIconButton(
//            onClick = onNavigateBack,
//            modifier = Modifier.size(36.dp),
//            enabled = currentFolder != null
//        ) {
//            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
//        }
//        Spacer(Modifier.width(8.dp))
//        LazyRow(
//            state = rowState,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            items(pathSegments.size) { index ->
//                val (name, path) = pathSegments[index]
//                val isLast = index == pathSegments.lastIndex
//                Text(
//                    text = name,
//                    style = MaterialTheme.typography.titleSmall,
//                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
//                    fontFamily = GoogleSansRounded,
//                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
//                    modifier = Modifier
//                        .clip(RoundedCornerShape(8.dp))
//                        .clickable(enabled = !isLast) {
//                            onFolderClick(path)
//                        }
//                        .padding(horizontal = 8.dp, vertical = 4.dp)
//                )
//                if (!isLast) {
//                    Icon(
//                        imageVector = Icons.Rounded.ChevronRight,
//                        contentDescription = null,
//                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
//                        modifier = Modifier.size(20.dp)
//                    )
//                }
//            }
//        }
//    }
//}