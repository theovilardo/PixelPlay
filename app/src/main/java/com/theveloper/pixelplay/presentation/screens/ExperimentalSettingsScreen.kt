package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LinearScale
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.rounded.LinearScale
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Rectangle
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun ExperimentalSettingsScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { transitionState.targetState = true }

    val transition = rememberTransition(transitionState, label = "ExperimentalSettingsAppearTransition")
    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) }
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
    ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            }
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = currentTopBarHeightDp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "player_ui_tweaks_section") {
                SettingsSection(
                    title = "PlayerUI loading tweaks",
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Style,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        modifier = Modifier.padding(horizontal = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val delayAllEnabled = uiState.fullPlayerLoadingTweaks.delayAll
                            val appearThresholdPercent = uiState.fullPlayerLoadingTweaks.contentAppearThresholdPercent
                            val isAnyDelayEnabled = uiState.fullPlayerLoadingTweaks.let {
                                it.delayAll || it.delayAlbumCarousel || it.delaySongMetadata || it.delayProgressBar || it.delayControls
                            }

                            SwitchSettingItem(
                                title = "Delay everything",
                                subtitle = "Hold the full player content until the sheet background is fully expanded.",
                                checked = delayAllEnabled,
                                onCheckedChange = settingsViewModel::setDelayAllFullPlayerContent,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )

                            SwitchSettingItem(
                                title = "Album carousel",
                                subtitle = "Delay album art and carousel until the sheet is expanded.",
                                checked = uiState.fullPlayerLoadingTweaks.delayAlbumCarousel,
                                onCheckedChange = settingsViewModel::setDelayAlbumCarousel,
                                enabled = !delayAllEnabled,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.ViewCarousel,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )

                            SwitchSettingItem(
                                title = "Song metadata",
                                subtitle = "Delay title, artist, and lyrics/queue actions.",
                                checked = uiState.fullPlayerLoadingTweaks.delaySongMetadata,
                                onCheckedChange = settingsViewModel::setDelaySongMetadata,
                                enabled = !delayAllEnabled,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.LinearScale,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )

                            SwitchSettingItem(
                                title = "Progress bar",
                                subtitle = "Delay the timeline and time labels until expansion completes.",
                                checked = uiState.fullPlayerLoadingTweaks.delayProgressBar,
                                onCheckedChange = settingsViewModel::setDelayProgressBar,
                                enabled = !delayAllEnabled,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.LinearScale,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )

                            SwitchSettingItem(
                                title = "Playback controls",
                                subtitle = "Delay play/pause, seek, and favorite controls.",
                                checked = uiState.fullPlayerLoadingTweaks.delayControls,
                                onCheckedChange = settingsViewModel::setDelayControls,
                                enabled = !delayAllEnabled,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.PlayCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.LinearScale,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Full player content appear threshold",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Control when delayed full player components become visible during expansion.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Slider(
                                        value = appearThresholdPercent.toFloat(),
                                        onValueChange = { settingsViewModel.setFullPlayerAppearThreshold(it.roundToInt()) },
                                        valueRange = 50f..100f,
                                        steps = 50,
                                        enabled = isAnyDelayEnabled
                                    )

                                    Text(
                                        text = "Content appears at ${appearThresholdPercent}% of expansion",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            SwitchSettingItem(
                                title = "Use placeholders for delayed items",
                                subtitle = "Keep layout stable by rendering lightweight placeholders while components wait for expansion.",
                                checked = uiState.fullPlayerLoadingTweaks.showPlaceholders,
                                onCheckedChange = settingsViewModel::setFullPlayerPlaceholders,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Rectangle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )

                            SwitchSettingItem(
                                title = "Make placeholders transparent",
                                subtitle = "Placeholders keep their layout space but become invisible.",
                                checked = uiState.fullPlayerLoadingTweaks.transparentPlaceholders,
                                onCheckedChange = settingsViewModel::setTransparentPlaceholders,
                                enabled = uiState.fullPlayerLoadingTweaks.showPlaceholders,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            // Divider for new section
            item(key = "divider_visuals") { 
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                     Text(
                        text = "Visual Quality",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                     androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier
                            .weight(3f)
                            .padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            item(key = "visual_tweaks_section") {
                val albumArtQuality = uiState.albumArtQuality
                
                 SettingsSection(
                    title = "Album Art Resolution",
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote, // Or Image/Photo icon
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent,
                        modifier = Modifier.padding(horizontal = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                           // Quality Selector using a Dialog or a custom Picker?
                           // Using a series of Radio Buttons or a clickable list item that opens a dialog is common.
                           // For simplicity and quick access as requested ("selector or slider"), let's use a segmented style or a simple list of options.
                           
                           // Using a loop to create selectable items for each enum value
                           com.theveloper.pixelplay.data.preferences.AlbumArtQuality.entries.forEach { quality ->
                               val isSelected = quality == albumArtQuality
                               
                               Surface(
                                   color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                   shape = RoundedCornerShape(12.dp),
                                   modifier = Modifier.fillMaxWidth(),
                                   onClick = { settingsViewModel.setAlbumArtQuality(quality) }
                               ) {
                                   Row(
                                       modifier = Modifier
                                           .padding(horizontal = 16.dp, vertical = 12.dp)
                                           .fillMaxWidth(),
                                       verticalAlignment = Alignment.CenterVertically,
                                       horizontalArrangement = Arrangement.SpaceBetween
                                   ) {
                                       Column(modifier = Modifier.weight(1f)) {
                                           Text(
                                               text = quality.label.substringBefore(" - "),
                                               style = MaterialTheme.typography.bodyLarge,
                                               color = MaterialTheme.colorScheme.onSurface,
                                               fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                           )
                                           quality.label.substringAfter(" - ", "").takeIf { it.isNotEmpty() }?.let { desc ->
                                                Text(
                                                   text = desc,
                                                   style = MaterialTheme.typography.bodySmall,
                                                   color = MaterialTheme.colorScheme.onSurfaceVariant
                                               )
                                           }
                                       }
                                       
                                       if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.LinearScale, // Check icon
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                       }
                                   }
                               }
                           }
                        }
                    }
                }
            }

            item(key = "experimental_bottom_spacer") {
                Spacer(modifier = Modifier.height(MiniPlayerHeight + 36.dp))
            }
        }

        SettingsTopBar(
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackPressed = onNavigationIconClick,
            title = "Experimental"
        )
    }
}
