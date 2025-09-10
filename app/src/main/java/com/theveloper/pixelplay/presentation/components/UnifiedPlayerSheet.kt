package com.theveloper.pixelplay.presentation.components

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
// Coil imports for FullPlayerContentInternal

import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import android.os.Trace // Import Trace
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.BottomNavItem
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeightFullWidth
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign

private val LocalMaterialTheme = staticCompositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

private enum class DragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

val MiniPlayerHeight = 64.dp
val PlayerSheetExpandedCornerRadius = 32.dp
const val ANIMATION_DURATION_MS = 255

val MiniPlayerBottomSpacer = 8.dp

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun UnifiedPlayerSheet(
    playerViewModel: PlayerViewModel,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    hideMiniPlayer: Boolean = false,
    isNavBarHidden: Boolean = false
) {
    Trace.beginSection("UnifiedPlayerSheet.Composition")
    val context = LocalContext.current
    LaunchedEffect(key1 = Unit) {
        playerViewModel.toastEvents.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    // Granular collection for playerUiState fields used directly by UnifiedPlayerSheet or its main sub-components
    val currentPosition by remember {
        playerViewModel.playerUiState.map { it.currentPosition }.distinctUntilChanged()
    }.collectAsState(initial = 0L)
    val currentPlaybackQueue by remember {
        playerViewModel.playerUiState.map { it.currentPlaybackQueue }.distinctUntilChanged()
    }.collectAsState(initial = persistentListOf())
    val currentQueueSourceName by remember {
        playerViewModel.playerUiState.map { it.currentQueueSourceName }.distinctUntilChanged()
    }.collectAsState(initial = "")
    val showDismissUndoBar by remember {
        playerViewModel.playerUiState.map { it.showDismissUndoBar }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val dismissedSong by remember {
        playerViewModel.playerUiState.map { it.dismissedSong }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val dismissedQueue by remember {
        playerViewModel.playerUiState.map { it.dismissedQueue }.distinctUntilChanged()
    }.collectAsState(initial = persistentListOf())
    val dismissedQueueName by remember {
        playerViewModel.playerUiState.map { it.dismissedQueueName }.distinctUntilChanged()
    }.collectAsState(initial = "")
    val dismissedPosition by remember {
        playerViewModel.playerUiState.map { it.dismissedPosition }.distinctUntilChanged()
    }.collectAsState(initial = 0L)
    val undoBarVisibleDuration by remember { // Assuming this doesn't change often, mapping for consistency
        playerViewModel.playerUiState.map { it.undoBarVisibleDuration }.distinctUntilChanged()
    }.collectAsState(initial = 4000L)

    val isPlayerVisible = stablePlayerState.isPlaying


    val currentSheetContentState by playerViewModel.sheetState.collectAsState()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsState()

    val navBarCornerRadius by playerViewModel.navBarCornerRadius.collectAsState()
    val navBarStyle by playerViewModel.navBarStyle.collectAsState()

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val offsetAnimatable = remember { Animatable(0f) }

    val screenWidthPx = remember(configuration, density) { with(density) { configuration.screenWidthDp.dp.toPx() } }
    val dismissThresholdPx = remember(screenWidthPx) { screenWidthPx * 0.4f }

    val swipeDismissProgress = remember(offsetAnimatable.value, dismissThresholdPx) {
        derivedStateOf {
            if (dismissThresholdPx == 0f) 0f
            else (abs(offsetAnimatable.value) / dismissThresholdPx).coerceIn(0f, 1f)
        }
    }

    val screenHeightPx = remember(configuration) { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val miniPlayerContentHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }
    val miniPlayerAndSpacerHeightPx = remember(density, MiniPlayerHeight) { with(density) { MiniPlayerHeight.toPx() } }

    val showPlayerContentArea by remember { derivedStateOf { stablePlayerState.currentSong != null } }

    // Use the granular showDismissUndoBar here
    val isPlayerSlotOccupied by remember(showPlayerContentArea, showDismissUndoBar) {
        derivedStateOf {
            showPlayerContentArea || showDismissUndoBar
        }
    }

    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction
    val visualOvershootScaleY = remember { Animatable(1f) }
    var shouldRenderFullPlayer by remember { mutableStateOf(false) }
    val fullPlayerContentAlpha = remember { Animatable(0f) }
    val initialFullPlayerOffsetY = remember(density) { with(density) { 24.dp.toPx() } }
    val fullPlayerTranslationY = remember { Animatable(initialFullPlayerOffsetY) }

    LaunchedEffect(showPlayerContentArea, currentSheetContentState) {
        val targetFraction = if (showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) 1f else 0f

        if (targetFraction == 0f) {
            shouldRenderFullPlayer = false
            fullPlayerContentAlpha.snapTo(0f)
            fullPlayerTranslationY.snapTo(initialFullPlayerOffsetY)
        }

        playerContentExpansionFraction.animateTo(
            targetFraction,
            animationSpec = tween(durationMillis = ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)
        ) {
            if (targetFraction == 1f && this.value == 1f) {
                shouldRenderFullPlayer = true
                scope.launch {
                    launch {
                        fullPlayerContentAlpha.animateTo(
                            1f,
                            animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
                        )
                    }
                    launch {
                        fullPlayerTranslationY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            }
        }

        if (showPlayerContentArea) {
            scope.launch {
                visualOvershootScaleY.snapTo(1f)
                if (targetFraction == 1f) {
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = keyframes {
                            durationMillis = 50
                            1.0f at 0
                            1.05f at 125
                            1.0f at 250
                        }
                    )
                } else {
                    // A default bounce for tap-to-collapse
                    launch {
                        visualOvershootScaleY.snapTo(0.96f) //controls how much it can reduce vertically
                        visualOvershootScaleY.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                }
            }
        } else {
            scope.launch {
                visualOvershootScaleY.snapTo(1f)
            }
            if(shouldRenderFullPlayer) { // Check if it was true before to avoid unnecessary snaps
                shouldRenderFullPlayer = false
                fullPlayerContentAlpha.snapTo(0f)
                fullPlayerTranslationY.snapTo(initialFullPlayerOffsetY)
            }
        }
    }

    val currentBottomPadding by remember(
        showPlayerContentArea,
        collapsedStateHorizontalPadding,
        predictiveBackCollapseProgress,
        currentSheetContentState
    ) {
        derivedStateOf {
            if (predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
                lerp(0.dp, collapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else {
                0.dp
            }
        }
    }

    val playerContentAreaActualHeightPx by remember(showPlayerContentArea, playerContentExpansionFraction, containerHeight, miniPlayerContentHeightPx) {
        derivedStateOf {
            if (showPlayerContentArea) {
                val containerHeightPx = with(density) { containerHeight.toPx() }
                lerp(miniPlayerContentHeightPx, containerHeightPx, playerContentExpansionFraction.value)
            } else { 0f }
        }
    }
    val playerContentAreaActualHeightDp = with(density) { playerContentAreaActualHeightPx.toDp() }

    val totalSheetHeightWhenContentCollapsedPx = remember(
        isPlayerSlotOccupied,
        hideMiniPlayer,
        miniPlayerAndSpacerHeightPx
    ) {
        if (isPlayerSlotOccupied && !hideMiniPlayer) miniPlayerAndSpacerHeightPx else 0f
    }

    val animatedTotalSheetHeightPx by remember(
        isPlayerSlotOccupied,
        playerContentExpansionFraction,
        screenHeightPx,
        totalSheetHeightWhenContentCollapsedPx
    ) {
        derivedStateOf {
            if (isPlayerSlotOccupied) {
                lerp(totalSheetHeightWhenContentCollapsedPx, screenHeightPx, playerContentExpansionFraction.value)
            } else {
                0f
            }
        }
    }

    val navBarElevation = 3.dp
    val shadowSpacePx = remember(density, navBarElevation) {
        with(density) { (navBarElevation * 8).toPx() }
    }

    val animatedTotalSheetHeightWithShadowPx by remember(animatedTotalSheetHeightPx, shadowSpacePx) {
        derivedStateOf {
            animatedTotalSheetHeightPx + shadowSpacePx
        }
    }
    val animatedTotalSheetHeightWithShadowDp = with(density) { animatedTotalSheetHeightWithShadowPx.toDp() }

    with(density) { animatedTotalSheetHeightPx.toDp() }

    val sheetExpandedTargetY = 0f

    val initialY = if (currentSheetContentState == PlayerSheetState.COLLAPSED) sheetCollapsedTargetY else sheetExpandedTargetY
    val currentSheetTranslationY = remember { Animatable(initialY) }

    LaunchedEffect(showPlayerContentArea, currentSheetContentState, sheetCollapsedTargetY, sheetExpandedTargetY) {
        val targetY = if (showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
            sheetExpandedTargetY
        } else { sheetCollapsedTargetY }
        currentSheetTranslationY.animateTo(
            targetValue = targetY,
            animationSpec = tween(durationMillis = ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)
        )
    }

    val visualSheetTranslationY by remember {
        derivedStateOf {
            currentSheetTranslationY.value * (1f - predictiveBackCollapseProgress) +
                    (sheetCollapsedTargetY * predictiveBackCollapseProgress)
        }
    }

    val overallSheetTopCornerRadiusTargetValue by remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        currentSheetContentState,
        navBarStyle,
        navBarCornerRadius,
        isNavBarHidden
    ) {
        derivedStateOf {
            if (showPlayerContentArea) {
                val collapsedCornerTarget = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    32.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    navBarCornerRadius.dp
                }

                if (predictiveBackCollapseProgress > 0f && currentSheetContentState == PlayerSheetState.EXPANDED) {
                    val expandedCorner = 0.dp
                    lerp(expandedCorner, collapsedCornerTarget, predictiveBackCollapseProgress)
                } else {
                    val fraction = playerContentExpansionFraction.value
                    val expandedTarget = 0.dp
                    lerp(collapsedCornerTarget, expandedTarget, fraction)
                }
            } else {
                if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    0.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    navBarCornerRadius.dp
                }
            }
        }
    }

    val overallSheetTopCornerRadius by animateDpAsState(
        targetValue = overallSheetTopCornerRadiusTargetValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "SheetTopCornerRadius"
    )

    val sheetShape = RoundedCornerShape(
        topStart = overallSheetTopCornerRadius,
        topEnd = overallSheetTopCornerRadius,
        bottomStart = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else navBarCornerRadius.dp,
        bottomEnd = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else navBarCornerRadius.dp
    )

    val playerContentActualBottomRadiusTargetValue by remember(
        navBarStyle,
        showPlayerContentArea,
        playerContentExpansionFraction,
        stablePlayerState.isPlaying,
        stablePlayerState.currentSong,
        predictiveBackCollapseProgress,
        currentSheetContentState,
        swipeDismissProgress.value,
        isNavBarHidden,
        navBarCornerRadius
    ) {
        derivedStateOf {
            if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                val fraction = playerContentExpansionFraction.value
                return@derivedStateOf lerp(32.dp, 26.dp, fraction)
            }

            val calculatedNormally = if (predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
                val expandedRadius = 26.dp
                val collapsedRadiusTarget = if (isNavBarHidden) 60.dp else 12.dp
                lerp(expandedRadius, collapsedRadiusTarget, predictiveBackCollapseProgress)
            } else {
                if (showPlayerContentArea) {
                    val fraction = playerContentExpansionFraction.value
                    val collapsedRadius = if (isNavBarHidden) 60.dp else 12.dp
                    if (fraction < 0.2f) {
                        lerp(collapsedRadius, 26.dp, (fraction / 0.2f).coerceIn(0f, 1f))
                    } else {
                        26.dp
                    }
                } else {
                    if (!stablePlayerState.isPlaying || stablePlayerState.currentSong == null) {
                        if (isNavBarHidden) 32.dp else navBarCornerRadius.dp
                    } else {
                        if (isNavBarHidden) 32.dp else 12.dp
                    }
                }
            }

            if (currentSheetContentState == PlayerSheetState.COLLAPSED &&
                swipeDismissProgress.value > 0f &&
                showPlayerContentArea &&
                playerContentExpansionFraction.value < 0.01f
            ) {
                val baseCollapsedRadius = if (isNavBarHidden) 32.dp else 12.dp
                lerp(baseCollapsedRadius, navBarCornerRadius.dp, swipeDismissProgress.value)
            } else {
                calculatedNormally
            }
        }
    }

    val playerContentActualBottomRadius by animateDpAsState(
        targetValue = playerContentActualBottomRadiusTargetValue,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlayerContentBottomRadius"
    )

    val navBarActualTopRadiusTarget by remember(
        showPlayerContentArea, playerContentExpansionFraction,
        currentSheetContentState, swipeDismissProgress.value
    ) {
        derivedStateOf {
            val calculatedNormally = if (showPlayerContentArea) {
                val fraction = playerContentExpansionFraction.value
                if (fraction < 0.2f) {
                    lerp(12.dp, 18.dp, (fraction / 0.2f).coerceIn(0f, 1f))
                } else {
                    18.dp
                }
            } else {
                12.dp
            }

            if (currentSheetContentState == PlayerSheetState.COLLAPSED &&
                swipeDismissProgress.value > 0f &&
                showPlayerContentArea &&
                playerContentExpansionFraction.value < 0.01f
            ) {
                val baseCollapsedRadius = 12.dp
                lerp(baseCollapsedRadius, navBarCornerRadius.dp, swipeDismissProgress.value)
            } else {
                calculatedNormally
            }
        }
    }

    val navBarActualTopRadius by animateDpAsState(
        targetValue = navBarActualTopRadiusTarget.value.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "NavBarTopRadius"
    )

    val actualCollapsedStateHorizontalPadding = if (navBarStyle == NavBarStyle.FULL_WIDTH) 14.dp else collapsedStateHorizontalPadding

    val currentHorizontalPadding by remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        actualCollapsedStateHorizontalPadding,
        predictiveBackCollapseProgress,
        navBarStyle
    ) {
        derivedStateOf {
            if (predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
                lerp(0.dp, actualCollapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else if (showPlayerContentArea) {
                lerp(actualCollapsedStateHorizontalPadding, 0.dp, playerContentExpansionFraction.value)
            } else {
                actualCollapsedStateHorizontalPadding
            }
        }
    }

    val currentDimLayerAlpha by remember(
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        currentSheetContentState
    ) {
        derivedStateOf {
            val baseAlpha = playerContentExpansionFraction.value
            if (predictiveBackCollapseProgress > 0f && currentSheetContentState == PlayerSheetState.EXPANDED) {
                lerp(baseAlpha, 0f, predictiveBackCollapseProgress)
            } else {
                baseAlpha
            }
        }
    }

    var showQueueSheet by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isDraggingPlayerArea by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    var accumulatedDragYSinceStart by remember { mutableFloatStateOf(0f) }

    val hapticFeedback = LocalHapticFeedback.current

    PredictiveBackHandler(
        enabled = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED && !isDragging
    ) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                playerViewModel.updatePredictiveBackCollapseFraction(backEvent.progress)
            }
            scope.launch {
                val progressAtRelease = playerViewModel.predictiveBackCollapseFraction.value
                val currentVisualY = lerp(sheetExpandedTargetY, sheetCollapsedTargetY, progressAtRelease)
                currentSheetTranslationY.snapTo(currentVisualY)
                val currentVisualExpansionFraction = (1f - progressAtRelease).coerceIn(0f, 1f)
                playerContentExpansionFraction.snapTo(currentVisualExpansionFraction)
                playerViewModel.updatePredictiveBackCollapseFraction(1f)
                playerViewModel.collapsePlayerSheet()
                playerViewModel.updatePredictiveBackCollapseFraction(0f)
            }
        } catch (e: CancellationException) {
            scope.launch {
                Animatable(playerViewModel.predictiveBackCollapseFraction.value).animateTo(
                    targetValue = 0f,
                    animationSpec = tween(ANIMATION_DURATION_MS)
                ) {
                    playerViewModel.updatePredictiveBackCollapseFraction(this.value)
                }

                if (playerViewModel.sheetState.value == PlayerSheetState.EXPANDED) {
                    playerViewModel.expandPlayerSheet()
                } else {
                    playerViewModel.collapsePlayerSheet()
                }
            }
        }
    }

    val shouldShowSheet by remember(showPlayerContentArea, hideMiniPlayer) {
        derivedStateOf { showPlayerContentArea && !hideMiniPlayer }
    }

    var internalIsKeyboardVisible by remember { mutableStateOf(false) }

    val imeInsets = WindowInsets.ime
    LaunchedEffect(imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .collectLatest { isVisible ->
                internalIsKeyboardVisible = isVisible
                Timber.tag("UnifiedPlayerSheet").d("Internal Keyboard Visible: $isVisible")
            }
    }

    val actuallyShowSheetContent = shouldShowSheet && !internalIsKeyboardVisible

    // val currentAlbumColorSchemePair by playerViewModel.currentAlbumArtColorSchemePair.collectAsState() // Replaced by activePlayerColorSchemePair
    val activePlayerSchemePair by playerViewModel.activePlayerColorSchemePair.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val systemColorScheme = MaterialTheme.colorScheme // This is the standard M3 theme

    val targetColorScheme = remember(activePlayerSchemePair, isDarkTheme, systemColorScheme) {
        val schemeFromPair = activePlayerSchemePair?.let { if (isDarkTheme) it.dark else it.light }
        schemeFromPair ?: systemColorScheme // If activePlayerSchemePair is null (i.e. System Dynamic selected) OR the selected scheme from pair is somehow null, use systemColorScheme
    }

    val colorAnimationSpec = remember { tween<Color>(durationMillis = 700, easing = FastOutSlowInEasing) }

    val animPrimary by animateColorAsState(targetColorScheme.primary, colorAnimationSpec, label = "animPrimary")
    val animOnPrimary by animateColorAsState(targetColorScheme.onPrimary, colorAnimationSpec, label = "animOnPrimary")
    val animPrimaryContainer by animateColorAsState(targetColorScheme.primaryContainer, colorAnimationSpec, label = "animPrimaryContainer")
    val animOnPrimaryContainer by animateColorAsState(targetColorScheme.onPrimaryContainer, colorAnimationSpec, label = "animOnPrimaryContainer")
    val animSecondary by animateColorAsState(targetColorScheme.secondary, colorAnimationSpec, label = "animSecondary")
    val animOnSecondary by animateColorAsState(targetColorScheme.onSecondary, colorAnimationSpec, label = "animOnSecondary")
    val animTertiary by animateColorAsState(targetColorScheme.tertiary, colorAnimationSpec, label = "animTertiary")
    val animOnTertiary by animateColorAsState(targetColorScheme.onTertiary, colorAnimationSpec, label = "animOnTertiary")
    val animSurface by animateColorAsState(targetColorScheme.surface, colorAnimationSpec, label = "animSurface")
    val animOnSurface by animateColorAsState(targetColorScheme.onSurface, colorAnimationSpec, label = "animOnSurface")

    val animatedAlbumColorScheme = remember(
        animPrimary, animOnPrimary, animPrimaryContainer, animOnPrimaryContainer,
        animSecondary, animOnSecondary, animTertiary, animOnTertiary, animSurface, animOnSurface, targetColorScheme
    ) {
        targetColorScheme.copy(
            primary = animPrimary,
            onPrimary = animOnPrimary,
            primaryContainer = animPrimaryContainer,
            onPrimaryContainer = animOnPrimaryContainer,
            secondary = animSecondary,
            onSecondary = animOnSecondary,
            tertiary = animTertiary,
            onTertiary = animOnTertiary,
            surface = animSurface,
            onSurface = animOnSurface
        )
    }
    val albumColorScheme = animatedAlbumColorScheme


    val playerAreaElevation by animateDpAsState(
        targetValue = if (showPlayerContentArea) {
            val fraction = playerContentExpansionFraction.value
            lerp(2.dp, 12.dp, fraction)
        } else {
            0.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlayerAreaElevation"
    )

    val playerShadowShape = remember(overallSheetTopCornerRadius, playerContentActualBottomRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = overallSheetTopCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusTR = overallSheetTopCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = playerContentActualBottomRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusBL = playerContentActualBottomRadius,
            smoothnessAsPercentTR = 60
        )
    }

    AnimatedVisibility(
        visible = showPlayerContentArea && playerContentExpansionFraction.value > 0f && !internalIsKeyboardVisible,
        enter = fadeIn(animationSpec = tween(ANIMATION_DURATION_MS)),
        exit = fadeOut(animationSpec = tween(ANIMATION_DURATION_MS))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = if (isSystemInDarkTheme()) Color.Black.copy(alpha = currentDimLayerAlpha) else Color.White.copy(alpha = currentDimLayerAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (currentSheetContentState == PlayerSheetState.EXPANDED) {
                        playerViewModel.collapsePlayerSheet()
                    }
                }
        )
    }

    if (actuallyShowSheetContent) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = visualSheetTranslationY }
                .height(animatedTotalSheetHeightWithShadowDp),
            shadowElevation = 0.dp,
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = currentBottomPadding.value.dp)
            ) {
            // Use granular showDismissUndoBar and undoBarVisibleDuration
            if (showPlayerContentArea) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(playerViewModel, showPlayerContentArea, currentSheetContentState, configuration, density, scope) {
                                if (!showPlayerContentArea || currentSheetContentState != PlayerSheetState.COLLAPSED) {
                                    scope.launch { offsetAnimatable.snapTo(0f) }
                                    return@pointerInput
                                }
                                var accumulatedDragX by mutableFloatStateOf(0f)
                                var dragPhase by mutableStateOf(DragPhase.IDLE)

                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        dragPhase = DragPhase.TENSION
                                        accumulatedDragX = 0f
                                        scope.launch { offsetAnimatable.stop() }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedDragX += dragAmount

                                        when (dragPhase) {
                                            DragPhase.TENSION -> {
                                                val snapThresholdPx = with(density) { 100.dp.toPx() }
                                                if (abs(accumulatedDragX) < snapThresholdPx) {
                                                    val maxTensionOffsetPx = with(density) { 30.dp.toPx() }
                                                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                                                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                                                    scope.launch { offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign) }
                                                } else {
                                                    // Threshold crossed, transition to the snap phase
                                                    dragPhase = DragPhase.SNAPPING
                                                }
                                            }
                                            DragPhase.SNAPPING -> {
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                // On the first frame of snapping, launch the soft spring animation
                                                scope.launch {
                                                    offsetAnimatable.animateTo(
                                                        targetValue = accumulatedDragX,
                                                        animationSpec = spring(
                                                            dampingRatio = 0.8f,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                                // Immediately transition to free drag so subsequent events are handled there
                                                dragPhase = DragPhase.FREE_DRAG
                                            }
                                            DragPhase.FREE_DRAG -> {
                                                // After the initial snap, track the finger with a very stiff spring to feel 1-to-1
                                                scope.launch {
                                                    offsetAnimatable.animateTo(
                                                        targetValue = accumulatedDragX,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                                            stiffness = Spring.StiffnessHigh
                                                        )
                                                    )
                                                }
                                            }
                                            else -> {}
                                        }
                                    },
                                    onDragEnd = {
                                        dragPhase = DragPhase.IDLE
                                        val dismissThreshold = screenWidthPx * 0.4f
                                        if (abs(accumulatedDragX) > dismissThreshold) {
                                            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
                                            scope.launch {
                                                offsetAnimatable.animateTo(
                                                    targetValue = targetDismissOffset,
                                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                                )
                                                playerViewModel.dismissPlaylistAndShowUndo()
                                                offsetAnimatable.snapTo(0f)
                                            }
                                        } else {
                                            scope.launch {
                                                offsetAnimatable.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMedium
                                                    )
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = currentHorizontalPadding)
                            .height(playerContentAreaActualHeightDp)
                            .graphicsLayer {
                                translationX = offsetAnimatable.value
                                scaleY = visualOvershootScaleY.value
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .shadow(
                                elevation = playerAreaElevation,
                                shape = playerShadowShape,
                                clip = false
                            )
                            .background(
                                color = albumColorScheme.primaryContainer,
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTL = overallSheetTopCornerRadius,
                                    smoothnessAsPercentBL = 60,
                                    cornerRadiusTR = overallSheetTopCornerRadius,
                                    smoothnessAsPercentBR = 60,
                                    cornerRadiusBR = playerContentActualBottomRadius,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusBL = playerContentActualBottomRadius,
                                    smoothnessAsPercentTR = 60
                                )
                            )
                            .clipToBounds()
                            .pointerInput(
                                showPlayerContentArea,
                                sheetCollapsedTargetY,
                                sheetExpandedTargetY,
                                currentSheetContentState
                            ) {
                                if (!showPlayerContentArea) return@pointerInput
                                var initialFractionOnDragStart = 0f
                                var initialYOnDragStart = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { offset ->
                                        scope.launch {
                                            currentSheetTranslationY.stop()
                                            playerContentExpansionFraction.stop()
                                        }
                                        isDragging = true
                                        isDraggingPlayerArea = true
                                        velocityTracker.resetTracking()
                                        initialFractionOnDragStart = playerContentExpansionFraction.value
                                        initialYOnDragStart = currentSheetTranslationY.value
                                        accumulatedDragYSinceStart = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedDragYSinceStart += dragAmount
                                        scope.launch {
                                            val newY = (currentSheetTranslationY.value + dragAmount)
                                                .coerceIn(
                                                    sheetExpandedTargetY - miniPlayerContentHeightPx * 0.2f,
                                                    sheetCollapsedTargetY + miniPlayerContentHeightPx * 0.2f
                                                )
                                            currentSheetTranslationY.snapTo(newY)
                                            val dragRatio =
                                                (initialYOnDragStart - newY) / (sheetCollapsedTargetY - sheetExpandedTargetY).coerceAtLeast(
                                                    1f
                                                )
                                            val newFraction =
                                                (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
                                            playerContentExpansionFraction.snapTo(newFraction)
                                        }
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        isDraggingPlayerArea = false
                                        val verticalVelocity = velocityTracker.calculateVelocity().y
                                        val currentExpansionFraction = playerContentExpansionFraction.value
                                        val minDragThresholdPx =
                                            with(density) { 5.dp.toPx() }
                                        val velocityThresholdForInstantTrigger =
                                            150f
                                        val targetContentState = when {
                                            abs(accumulatedDragYSinceStart) > minDragThresholdPx -> {
                                                if (accumulatedDragYSinceStart < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                            }
                                            abs(verticalVelocity) > velocityThresholdForInstantTrigger -> {
                                                if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                            }
                                            else -> {
                                                if (currentExpansionFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                            }
                                        }
                                        scope.launch {
                                            if (targetContentState == PlayerSheetState.EXPANDED) {
                                                launch {
                                                    currentSheetTranslationY.animateTo(
                                                        targetValue = sheetExpandedTargetY,
                                                        animationSpec = tween(
                                                            durationMillis = ANIMATION_DURATION_MS,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    )
                                                }
                                                launch {
                                                    playerContentExpansionFraction.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = tween(
                                                            durationMillis = ANIMATION_DURATION_MS,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    )
                                                }
                                                playerViewModel.expandPlayerSheet()
                                            } else {
                                                val dynamicDampingRatio = lerp(
                                                    start = Spring.DampingRatioNoBouncy,
                                                    stop = Spring.DampingRatioLowBouncy,
                                                    fraction = currentExpansionFraction
                                                )
                                                // New logic for scale animation
                                                launch {
                                                    val initialSquash = lerp(1.0f, 0.97f, currentExpansionFraction)
                                                    visualOvershootScaleY.snapTo(initialSquash)
                                                    visualOvershootScaleY.animateTo(
                                                        targetValue = 1f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessVeryLow
                                                        )
                                                    )
                                                }
                                                launch {
                                                    currentSheetTranslationY.animateTo(
                                                        targetValue = sheetCollapsedTargetY,
                                                        initialVelocity = verticalVelocity,
                                                        animationSpec = spring(
                                                            dampingRatio = dynamicDampingRatio,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                                launch {
                                                    playerContentExpansionFraction.animateTo(
                                                        targetValue = 0f,
                                                        initialVelocity = verticalVelocity / (sheetCollapsedTargetY - sheetExpandedTargetY).coerceAtLeast(1f),
                                                        animationSpec = spring(
                                                            dampingRatio = dynamicDampingRatio,
                                                            stiffness = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                                playerViewModel.collapsePlayerSheet()
                                            }
                                        }
                                        accumulatedDragYSinceStart = 0f
                                    }
                                )
                            }
                            .clickable(
                                enabled = true,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                playerViewModel.togglePlayerSheetState()
                            }
                    ) {
                        if (showPlayerContentArea) {
                            // stablePlayerState.currentSong is already available from the top-level collection
                            stablePlayerState.currentSong?.let { currentSongNonNull ->
                                val miniPlayerAlpha by remember { derivedStateOf { (1f - playerContentExpansionFraction.value * 2f).coerceIn(0f, 1f) } }
                                if (miniPlayerAlpha > 0.01f) {
                                    CompositionLocalProvider(
                                        LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .graphicsLayer { alpha = miniPlayerAlpha }
                                        ) {
                                            MiniPlayerContentInternal(
                                                song = currentSongNonNull, // Use non-null version
                                                cornerRadiusAlb = (overallSheetTopCornerRadius.value * 0.5).dp,
                                                isPlaying = stablePlayerState.isPlaying, // from top-level stablePlayerState
                                                onPlayPause = { playerViewModel.playPause() },
                                                onNext = { playerViewModel.nextSong() },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }

                                if (shouldRenderFullPlayer) {
                                    CompositionLocalProvider(
                                        LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
                                    ) {
                                        Box(modifier = Modifier.graphicsLayer {
                                            alpha = fullPlayerContentAlpha.value
                                            translationY = fullPlayerTranslationY.value
                                        }) {
                                            FullPlayerContentInternal(
                                                currentSong = currentSongNonNull, // Use non-null version
                                                currentPosition = currentPosition, // Pass granular currentPosition
                                                isPlaying = stablePlayerState.isPlaying,
                                                isShuffleEnabled = stablePlayerState.isShuffleEnabled,
                                                repeatMode = stablePlayerState.repeatMode,
                                                isFavorite = playerViewModel.isCurrentSongFavorite.collectAsState().value,
                                                onPlayPause = { playerViewModel.playPause() },
                                                onSeek = { playerViewModel.seekTo(it) },
                                                onNext = { playerViewModel.nextSong() },
                                                onPrevious = { playerViewModel.previousSong() },
                                                onCollapse = { playerViewModel.collapsePlayerSheet() },
                                                expansionFraction = playerContentExpansionFraction.value,
                                                currentSheetState = currentSheetContentState,
                                                onShowQueueClicked = { showQueueSheet = true },
                                                onShuffleToggle = { playerViewModel.toggleShuffle() },
                                                onRepeatToggle = { playerViewModel.cycleRepeatMode() },
                                                onFavoriteToggle = { playerViewModel.toggleFavorite() },
                                                playerViewModel = playerViewModel // Keep passing ViewModel if FullPlayerContentInternal needs other parts of it
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Use granular showDismissUndoBar
                val isPlayerOrUndoBarVisible = showPlayerContentArea || showDismissUndoBar
                if (isPlayerOrUndoBarVisible) {
                    // Spacer removed
                }
            }
        }
    }

    if (showQueueSheet && !internalIsKeyboardVisible) {
        CompositionLocalProvider(
            LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
        ) {
            QueueBottomSheet(
                queue = currentPlaybackQueue, // Use granular state
                currentQueueSourceName = currentQueueSourceName, // Use granular state
                currentSongId = stablePlayerState.currentSong?.id, // stablePlayerState is fine here
                onDismiss = { showQueueSheet = false },
                onPlaySong = { song ->
                    playerViewModel.playSongs(
                        currentPlaybackQueue, // Use granular state
                        song,
                        currentQueueSourceName // Use granular state
                    )
                },
                onRemoveSong = { songId -> playerViewModel.removeSongFromQueue(songId) },
                onReorder = { from, to -> playerViewModel.reorderQueueItem(from, to) },
                repeatMode = stablePlayerState.repeatMode,
                isShuffleOn = stablePlayerState.isShuffleEnabled,
                onToggleRepeat = { playerViewModel.cycleRepeatMode() },
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onClearQueue = { playerViewModel.clearQueueExceptCurrent() },
                activeTimerValueDisplay = playerViewModel.activeTimerValueDisplay.collectAsState().value,
                isEndOfTrackTimerActive = playerViewModel.isEndOfTrackTimerActive.collectAsState().value,
                onSetPredefinedTimer = { minutes -> playerViewModel.setSleepTimer(minutes) },
                onSetEndOfTrackTimer = { enable -> playerViewModel.setEndOfTrackTimer(enable) },
                onOpenCustomTimePicker = {
                    Log.d("TimerOptions", "OpenCustomTimePicker clicked")
                },
                onCancelTimer = { playerViewModel.cancelSleepTimer() }
            )
        }
    }
    Trace.endSection() // End UnifiedPlayerSheet.Composition
}


@Composable
private fun AlbumArtDisplaySection( // Renamed for clarity and to avoid conflict if OptimizedAlbumArt is used directly
    song: Song?, // Nullable, comes from stablePlayerState
    expansionFraction: Float,
    modifier: Modifier = Modifier
) {
    song?.let { currentSong ->
        OptimizedAlbumArt(
            uri = currentSong.albumArtUriString,
            title = currentSong.title,
            expansionFraction = expansionFraction,
            modifier = modifier,
            targetSize = coil.size.Size(600, 600) // Tamaño específico para el reproductor expandido
        )
    }
}

@Composable
private fun SongMetadataDisplaySection( // Renamed for clarity
    song: Song?, // Nullable, comes from stablePlayerState
    expansionFraction: Float,
    textColor: Color,
    artistTextColor: Color,
    modifier: Modifier = Modifier
) {
    song?.let { currentSong ->
        PlayerSongInfo(
            title = currentSong.title,
            artist = currentSong.artist,
            expansionFraction = expansionFraction,
            textColor = textColor,
            artistTextColor = artistTextColor,
            modifier = modifier
        )
    }
}

@Composable
private fun PlayerProgressBarSection(
    currentPosition: Long, // Changed from currentPositionValue
    totalDurationValue: Long,
    progressFractionValue: Float,
    onSeek: (Long) -> Unit,
    expansionFraction: Float,
    isPlaying: Boolean,
    currentSheetState: PlayerSheetState,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    thumbColor: Color,
    timeTextColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = lerp(2.dp, 10.dp, expansionFraction))
            .graphicsLayer {
                alpha = expansionFraction
            }
            .heightIn(min = 70.dp)
    ) {
        val onSliderValueChange = remember(onSeek, totalDurationValue) {
            { frac: Float -> onSeek((frac * totalDurationValue).roundToLong()) }
        }
        WavyMusicSlider(
            valueProvider = { progressFractionValue },
            onValueChange = onSliderValueChange,
            onValueChangeFinished = { /* No specific action on finish needed for now */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            trackHeight = 6.dp,
            thumbRadius = 8.dp,
            activeTrackColor = activeTrackColor,
            inactiveTrackColor = inactiveTrackColor,
            thumbColor = thumbColor,
            waveFrequency = 0.08f,
            isPlaying = (isPlaying && currentSheetState == PlayerSheetState.EXPANDED) // Wave animation only when expanded and playing
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(currentPosition), // Use currentPosition
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = timeTextColor,
                fontSize = 12.sp
            )
            Text(
                formatDuration(totalDurationValue),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = timeTextColor,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlayerSongInfo(
    title: String,
    artist: String,
    expansionFraction: Float,
    textColor: Color,
    artistTextColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(vertical = lerp(2.dp, 10.dp, expansionFraction))
            .fillMaxWidth(0.9f)
            .graphicsLayer {
                alpha = expansionFraction
                translationY = (1f - expansionFraction) * 24f
            }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.sp),
            color = artistTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun getNavigationBarHeight(): Dp {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return insets.calculateBottomPadding()
}

@Composable
private fun MiniPlayerContentInternal(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    cornerRadiusAlb: Dp,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val albumShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = cornerRadiusAlb,
        smoothnessAsPercentBL = 60,
        cornerRadiusTR = cornerRadiusAlb,
        smoothnessAsPercentBR = 60,
        cornerRadiusBR = cornerRadiusAlb,
        smoothnessAsPercentTL = 60,
        cornerRadiusBL = cornerRadiusAlb,
        smoothnessAsPercentTR = 60
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            contentDescription = "Carátula de ${song.title}",
            shape = CircleShape,
            targetSize = coil.size.Size(150, 150),
            modifier = Modifier
                .size(44.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    letterSpacing = 0.sp
                ),
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.primary)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(
                        bounded = false,
                        color = LocalMaterialTheme.current.onPrimary
                    )
                ) { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = if (isPlaying) painterResource(R.drawable.rounded_pause_24) else painterResource(R.drawable.rounded_play_arrow_24),
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = LocalMaterialTheme.current.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.primary.copy(alpha = 0.2f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(
                        bounded = false,
                        color = LocalMaterialTheme.current.onPrimary
                    )
                ) { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_skip_next_24),
                contentDescription = "Siguiente",
                tint = LocalMaterialTheme.current.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private enum class ButtonType {
    NONE, PREVIOUS, PLAY_PAUSE, NEXT
}


@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerContentInternal(
    currentSong: Song?,
    currentPosition: Long, // Added currentPosition
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    expansionFraction: Float,
    currentSheetState: PlayerSheetState,
    onShowQueueClicked: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    playerViewModel: PlayerViewModel // Kept for stablePlayerState access for totalDuration, or could pass totalDuration too
) {
    val song = currentSong ?: return // Early exit if no song
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsState()

    var showFetchLyricsDialog by remember { mutableStateOf(false) }


    // totalDurationValue is derived from stablePlayerState, so it's fine.
    val totalDurationValue by remember {
        playerViewModel.stablePlayerState.map { it.totalDuration }.distinctUntilChanged()
    }.collectAsState(initial = 0L)

    // progressFractionValue depends on currentPosition, so it will change frequently.
    val progressFractionValue = remember(currentPosition, totalDurationValue) {
        (currentPosition.coerceAtLeast(0).toFloat() /
                totalDurationValue.coerceAtLeast(1).toFloat())
    }.coerceIn(0f, 1f)

    val stableControlAnimationSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    val controlOtherButtonsColor = LocalMaterialTheme.current.primary.copy(alpha = 0.15f)
    val controlPlayPauseColor = LocalMaterialTheme.current.primary
    val controlTintPlayPauseIcon = LocalMaterialTheme.current.onPrimary
    val controlTintOtherIcons = LocalMaterialTheme.current.primary

    // Lógica para el botón de Lyrics en el reproductor expandido
    val onLyricsClick = {
        val lyrics = stablePlayerState.lyrics
        if (lyrics?.synced.isNullOrEmpty() && lyrics?.plain.isNullOrEmpty()) {
            // Si no hay letra, mostramos el diálogo para buscar
            showFetchLyricsDialog = true
        } else {
            // Si hay letra, mostramos el sheet directamente
            showLyricsSheet = true
        }
    }

    if (showFetchLyricsDialog) {
        FetchLyricsDialog(
            uiState = lyricsSearchUiState,
            onConfirm = {
                // El usuario confirma, iniciamos la búsqueda
                playerViewModel.fetchLyricsForCurrentSong()
            },
            onDismiss = {
                // El usuario cancela o cierra el diálogo
                showFetchLyricsDialog = false
                playerViewModel.resetLyricsSearchState()
            }
        )
    }

    // Observador para reaccionar al resultado de la búsqueda de letras
    LaunchedEffect(lyricsSearchUiState) {
        when (val state = lyricsSearchUiState) {
            is LyricsSearchUiState.Success -> {
                if (showFetchLyricsDialog) {
                    showFetchLyricsDialog = false
                    showLyricsSheet = true
                    playerViewModel.resetLyricsSearchState()
                }
            }
            is LyricsSearchUiState.Error -> {
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.alpha(expansionFraction.coerceIn(0f, 1f)),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                    actionIconContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                    navigationIconContentColor = LocalMaterialTheme.current.onPrimaryContainer
                ),
                title = {
                    Text(
                        modifier = Modifier.padding(start = 18.dp),
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            // Ancho total = 14dp de padding + 42dp del botón
                            .width(56.dp)
                            .height(42.dp),
                        // 2. Alinea el contenido (el botón) al final (derecha) y centrado verticalmente
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        // 3. Tu botón circular original, sin cambios
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(LocalMaterialTheme.current.onPrimary)
                                .clickable(onClick = onCollapse),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                                contentDescription = "Colapsar",
                                tint = LocalMaterialTheme.current.primary
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .padding(end = 14.dp)
                            .width(104.dp),
                        // Ahora puedes controlar el espaciado exacto entre los elementos.
                        // Prueba a cambiar 0.dp por el valor que necesites, por ejemplo: 2.dp
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Primer botón (Lyrics)
                        Box(
                            modifier = Modifier
                                .size(height = 42.dp, width = 50.dp) // Define un tamaño fijo para el área de clic
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 50.dp,
                                        topEnd = 6.dp,
                                        bottomStart = 50.dp,
                                        bottomEnd = 6.dp
                                    )
                                )
                                .background(LocalMaterialTheme.current.onPrimary)
                                .clickable {
                                    onLyricsClick()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_lyrics_24),
                                contentDescription = "Lyrics",
                                tint = LocalMaterialTheme.current.primary
                            )
                        }

                        // Segundo botón (Queue)
                        Box(
                            modifier = Modifier
                                .size(height = 42.dp, width = 50.dp) // Usa el mismo tamaño para mantener la consistencia
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 6.dp,
                                        topEnd = 50.dp,
                                        bottomStart = 6.dp,
                                        bottomEnd = 50.dp
                                    )
                                )
                                .background(LocalMaterialTheme.current.onPrimary)
                                .clickable {
                                    showSongInfoBottomSheet = true
                                    onShowQueueClicked()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_queue_music_24),
                                contentDescription = "Song options",
                                tint = LocalMaterialTheme.current.primary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = lerp(8.dp, 24.dp, expansionFraction),
                    vertical = lerp(0.dp, 16.dp, expansionFraction)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Album Cover section
            val albumArtContainerModifier = Modifier
                .padding(vertical = lerp(4.dp, 8.dp, expansionFraction))
                .fillMaxWidth(lerp(0.5f, 0.8f, expansionFraction))
                .aspectRatio(1f)
                .clip(RoundedCornerShape(lerp(16.dp, 24.dp, expansionFraction)))
                //.shadow(elevation = 16.dp * expansionFraction)
                .graphicsLayer { alpha = expansionFraction }

            // Album Cover section - uses new Composable
            AlbumArtDisplaySection(
                song = currentSong,
                expansionFraction = expansionFraction,
                modifier = albumArtContainerModifier
            )

            // Song Info - uses new Composable
            SongMetadataDisplaySection(
                song = currentSong, // currentSong is from stablePlayerState
                expansionFraction = expansionFraction,
                textColor = LocalMaterialTheme.current.onPrimaryContainer,
                artistTextColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.8f)
                // modifier for PlayerSongInfo is internal to SongMetadataDisplaySection if needed, or pass one
            )

            // Progress Bar and Times - this section *will* recompose with currentPosition
            PlayerProgressBarSection(
                currentPosition = currentPosition, // Pass granular currentPosition
                totalDurationValue = totalDurationValue,
                progressFractionValue = progressFractionValue,
                onSeek = onSeek,
                expansionFraction = expansionFraction,
                isPlaying = isPlaying,
                currentSheetState = currentSheetState,
                activeTrackColor = LocalMaterialTheme.current.primary,
                inactiveTrackColor = LocalMaterialTheme.current.primary.copy(alpha = 0.2f),
                thumbColor = LocalMaterialTheme.current.primary,
                timeTextColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.weight(1f))

            AnimatedPlaybackControls(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                isPlaying = isPlaying,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                height = 80.dp,
                pressAnimationSpec = stableControlAnimationSpec,
                releaseDelay = 220L,
                colorOtherButtons = controlOtherButtonsColor,
                colorPlayPause = controlPlayPauseColor,
                tintPlayPauseIcon = controlTintPlayPauseIcon,
                tintOtherIcons = controlTintOtherIcons
            )

            Spacer(modifier = Modifier.height(14.dp))

            BottomToggleRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp, max = 88.dp)
                    .padding(horizontal = 26.dp, vertical = 8.dp),
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                isFavorite = isFavorite,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle
            )
        }
    }
    AnimatedVisibility(
        visible = showLyricsSheet,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
    ) {
        LyricsSheet(
            stablePlayerStateFlow = playerViewModel.stablePlayerState,
            playerUiStateFlow = playerViewModel.playerUiState,
            lyricsTextStyle = MaterialTheme.typography.titleLarge,
            backgroundColor = LocalMaterialTheme.current.background,
            onBackgroundColor = LocalMaterialTheme.current.onBackground,
            containerColor = LocalMaterialTheme.current.primaryContainer,
            contentColor = LocalMaterialTheme.current.onPrimaryContainer,
            accentColor = LocalMaterialTheme.current.primary,
            onAccentColor = LocalMaterialTheme.current.onPrimary,
            tertiaryColor = LocalMaterialTheme.current.tertiary,
            onTertiaryColor = LocalMaterialTheme.current.onTertiary,
            onBackClick = { showLyricsSheet = false },
            onSeekTo = { playerViewModel.seekTo(it) },
            onPlayPause = {
                playerViewModel.playPause()
            }
        )
    }
}

private val DefaultPlaybackControlAnimationSpec: AnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = LocalMaterialTheme.current.primary.copy(alpha = 0.15f),
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    tintOtherIcons: Color = LocalMaterialTheme.current.primary,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp
) {
    var lastClicked by remember { mutableStateOf<ButtonType?>(null) }

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(releaseDelay)
            lastClicked = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fun weightFor(button: ButtonType): Float = when (lastClicked) {
                button   -> expansionWeight
                null     -> baseWeight
                else     -> compressionWeight
            }

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(ButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec
            )
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorOtherButtons)
                    .clickable {
                        lastClicked = ButtonType.PREVIOUS
                        onPrevious()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_skip_previous_24),
                    contentDescription = "Anterior",
                    tint = tintOtherIcons,
                    modifier = Modifier.size(iconSize)
                )
            }

            val playWeight by animateFloatAsState(
                targetValue = weightFor(ButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec
            )
            val playCorner by animateDpAsState(
                targetValue = if (!isPlaying) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "PlayCornerRadiusAnim"
            )
            val playShape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = playCorner,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = playCorner,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = playCorner,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = playCorner,
                smoothnessAsPercentBR = 60
            )
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .clip(playShape)
                    .background(colorPlayPause)
                    .clickable {
                        lastClicked = ButtonType.PLAY_PAUSE
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = if (isPlaying)
                        painterResource(R.drawable.rounded_pause_24)
                    else
                        painterResource(R.drawable.rounded_play_arrow_24),
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = tintPlayPauseIcon,
                    modifier = Modifier.size(playPauseIconSize)
                )
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(ButtonType.NEXT),
                animationSpec = pressAnimationSpec
            )
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorOtherButtons)
                    .clickable {
                        lastClicked = ButtonType.NEXT
                        onNext()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_skip_next_24),
                    contentDescription = "Siguiente",
                    tint = tintOtherIcons,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val rowCorners = 60.dp
    val inactiveBg = LocalMaterialTheme.current.primary.copy(alpha = 0.08f)

    Box(
        modifier = modifier.background(
            color = LocalMaterialTheme.current.onPrimary,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = rowCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = rowCorners,
                smoothnessAsPercentBL = 60,
                cornerRadiusTL = rowCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = rowCorners,
                smoothnessAsPercentTL = 60
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTL = 60
                    )
                )
                .background(Color.Transparent)
            ,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = isShuffleEnabled,
                activeColor = LocalMaterialTheme.current.primary,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onPrimary,
                inactiveColor = inactiveBg,
                onClick = onShuffleToggle,
                iconId = R.drawable.rounded_shuffle_24,
                contentDesc = "Aleatorio"
            )
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_on_24
                Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_on_24
                else -> R.drawable.rounded_repeat_24
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = LocalMaterialTheme.current.secondary,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onSecondary,
                inactiveColor = inactiveBg,
                onClick = onRepeatToggle,
                iconId = repeatIcon,
                contentDesc = "Repetir"
            )
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = LocalMaterialTheme.current.tertiary,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onTertiary,
                inactiveColor = inactiveBg,
                onClick = onFavoriteToggle,
                iconId = R.drawable.round_favorite_24,
                contentDesc = "Favorito"
            )
        }
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    iconId: Int,
    contentDesc: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 250)
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (active) activeCornerRadius else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else LocalMaterialTheme.current.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}
