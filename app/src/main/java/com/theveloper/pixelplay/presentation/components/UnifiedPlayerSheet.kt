package com.theveloper.pixelplay.presentation.components

import android.os.Trace
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.util.UnstableApi
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.player.FullPlayerContent
import com.theveloper.pixelplay.presentation.components.scoped.rememberExpansionTransition
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sign

internal val LocalMaterialTheme = staticCompositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

private enum class DragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

val MiniPlayerHeight = 64.dp
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
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    // Granular collection for playerUiState fields used directly by UnifiedPlayerSheet or its main sub-components
    val currentPosition by remember {
        playerViewModel.playerUiState.map { it.currentPosition }.distinctUntilChanged()
    }.collectAsState(initial = 0L)
    val remotePosition by playerViewModel.remotePosition.collectAsState()
    val selectedRoute by playerViewModel.selectedRoute.collectAsState()
    val isCasting = selectedRoute?.isDefault == false
    val positionToDisplay = if (isCasting) remotePosition else currentPosition
    val isFavorite by playerViewModel.isCurrentSongFavorite.collectAsState()

    val currentPlaybackQueue by remember {
        playerViewModel.playerUiState.map { it.currentPlaybackQueue }.distinctUntilChanged()
    }.collectAsState(initial = persistentListOf())
    val currentQueueSourceName by remember {
        playerViewModel.playerUiState.map { it.currentQueueSourceName }.distinctUntilChanged()
    }.collectAsState(initial = "")
    val showDismissUndoBar by remember {
        playerViewModel.playerUiState.map { it.showDismissUndoBar }.distinctUntilChanged()
    }.collectAsState(initial = false)


    val currentSheetContentState by playerViewModel.sheetState.collectAsState()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsState()

    val navBarCornerRadius by playerViewModel.navBarCornerRadius.collectAsState()
    val navBarStyle by playerViewModel.navBarStyle.collectAsState()
    val carouselStyle by playerViewModel.carouselStyle.collectAsState()

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

    val screenHeightPx = remember(configuration, density) { with(density) { configuration.screenHeightDp.dp.toPx() } }
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
    val initialFullPlayerOffsetY = remember(density) { with(density) { 24.dp.toPx() } }

    val fullPlayerContentAlpha by remember {
        derivedStateOf {
            (playerContentExpansionFraction.value - 0.25f).coerceIn(0f, 0.75f) / 0.75f
        }
    }

    val fullPlayerTranslationY by remember {
        derivedStateOf {
            lerp(initialFullPlayerOffsetY, 0f, fullPlayerContentAlpha)
        }
    }

    LaunchedEffect(showPlayerContentArea, currentSheetContentState) {
        val targetFraction = if (showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) 1f else 0f

        playerContentExpansionFraction.animateTo(
            targetFraction,
            animationSpec = tween(durationMillis = ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)
        )

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
                    launch {
                        visualOvershootScaleY.snapTo(0.96f)
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
    val playerContentAreaHeightDp by remember(showPlayerContentArea, playerContentExpansionFraction, containerHeight) {
        derivedStateOf {
            if (showPlayerContentArea) lerp(MiniPlayerHeight, containerHeight, playerContentExpansionFraction.value)
            else 0.dp
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

    //with(density) { animatedTotalSheetHeightPx.toDp() }

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
    var showCastSheet by remember { mutableStateOf(false) }
    var showTrackVolumeSheet by remember { mutableStateOf(false) }
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

    val t = rememberExpansionTransition(playerContentExpansionFraction.value)

    val playerAreaElevation by t.animateDp(label = "elev") { f -> lerp(2.dp, 12.dp, f) }
//    val playerAreaElevation by animateDpAsState(
//        targetValue = if (showPlayerContentArea) {
//            val fraction = playerContentExpansionFraction.value
//            lerp(2.dp, 12.dp, fraction)
//        } else {
//            0.dp
//        },
//        animationSpec = spring(
//            dampingRatio = Spring.DampingRatioNoBouncy,
//            stiffness = Spring.StiffnessMedium
//        ),
//        label = "PlayerAreaElevation"
//    )

    val miniAlpha by t.animateFloat(label = "miniAlpha") { f -> (1f - f*2f).coerceIn(0f,1f) }

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

    val isCollapsedState = rememberUpdatedState(currentSheetContentState == PlayerSheetState.COLLAPSED)

    val collapsedY  = rememberUpdatedState(sheetCollapsedTargetY)
    val expandedY   = rememberUpdatedState(sheetExpandedTargetY)
    val canShow     = rememberUpdatedState(showPlayerContentArea)
    val miniH       = rememberUpdatedState(miniPlayerContentHeightPx)
    val dens        = rememberUpdatedState(LocalDensity.current) // opcional; útil para thresholds

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
                .graphicsLayer {
                    translationY = visualSheetTranslationY
                }
                .height(animatedTotalSheetHeightWithShadowDp),
            shadowElevation = 0.dp,
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = currentBottomPadding)
            ) {
            // Use granular showDismissUndoBar and undoBarVisibleDuration
            if (showPlayerContentArea) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit){
                                if (!isCollapsedState.value) return@pointerInput
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
                            .height(playerContentAreaHeightDp)
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
                            .pointerInput(Unit) {
                                if (!canShow.value) return@pointerInput

                                var initialFractionOnDragStart = 0f
                                var initialYOnDragStart = 0f

                                detectVerticalDragGestures(
                                    onDragStart = {
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
                                                    expandedY.value - miniH.value * 0.2f,
                                                    collapsedY.value + miniH.value * 0.2f
                                                )
                                            currentSheetTranslationY.snapTo(newY)

                                            val denom = (collapsedY.value - expandedY.value).coerceAtLeast(1f)
                                            val dragRatio = (initialYOnDragStart - newY) / denom
                                            val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
                                            playerContentExpansionFraction.snapTo(newFraction)
                                        }
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        isDraggingPlayerArea = false

                                        val verticalVelocity = velocityTracker.calculateVelocity().y
                                        val currentFraction = playerContentExpansionFraction.value
                                        val minDragThresholdPx = with(dens.value) { 5.dp.toPx() }
                                        val velocityThreshold = 150f

                                        val targetState =
                                            when {
                                                abs(accumulatedDragYSinceStart) > minDragThresholdPx ->
                                                    if (accumulatedDragYSinceStart < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                                abs(verticalVelocity) > velocityThreshold ->
                                                    if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                                else ->
                                                    if (currentFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                            }

                                        scope.launch {
                                            if (targetState == PlayerSheetState.EXPANDED) {
                                                launch {
                                                    currentSheetTranslationY.animateTo(
                                                        targetValue = expandedY.value,
                                                        animationSpec = tween(ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)
                                                    )
                                                }
                                                launch {
                                                    playerContentExpansionFraction.animateTo(
                                                        1f,
                                                        animationSpec = tween(ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)
                                                    )
                                                }
                                                playerViewModel.expandPlayerSheet()
                                            } else {
                                                val dynamicDamping = lerp(
                                                    start = Spring.DampingRatioNoBouncy,
                                                    stop  = Spring.DampingRatioLowBouncy,
                                                    fraction = currentFraction
                                                )
                                                launch {
                                                    val initialSquash = lerp(1.0f, 0.97f, currentFraction)
                                                    visualOvershootScaleY.snapTo(initialSquash)
                                                    visualOvershootScaleY.animateTo(
                                                        1f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness   = Spring.StiffnessVeryLow
                                                        )
                                                    )
                                                }
                                                launch {
                                                    currentSheetTranslationY.animateTo(
                                                        targetValue = collapsedY.value,
                                                        initialVelocity = verticalVelocity,
                                                        animationSpec = spring(
                                                            dampingRatio = dynamicDamping,
                                                            stiffness   = Spring.StiffnessLow
                                                        )
                                                    )
                                                }
                                                launch {
                                                    val denom = (collapsedY.value - expandedY.value).coerceAtLeast(1f)
                                                    playerContentExpansionFraction.animateTo(
                                                        0f,
                                                        initialVelocity = verticalVelocity / denom,
                                                        animationSpec = spring(
                                                            dampingRatio = dynamicDamping,
                                                            stiffness   = Spring.StiffnessLow
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
                            //.pointerInput(showPlayerContentArea, sheetCollapsedTargetY, sheetExpandedTargetY, currentSheetContentState)
//                            .pointerInput(Unit)
//                            {
//                                //if (!showPlayerContentArea) return@pointerInput
//                                if (!canShow.value) return@pointerInput
//                                var initialFractionOnDragStart = 0f
//                                var initialYOnDragStart = 0f
//                                detectVerticalDragGestures(
//                                    onDragStart = { offset ->
//                                        scope.launch {
//                                            currentSheetTranslationY.stop()
//                                            playerContentExpansionFraction.stop()
//                                        }
//                                        isDragging = true
//                                        isDraggingPlayerArea = true
//                                        velocityTracker.resetTracking()
//                                        initialFractionOnDragStart = playerContentExpansionFraction.value
//                                        initialYOnDragStart = currentSheetTranslationY.value
//                                        accumulatedDragYSinceStart = 0f
//                                    },
//                                    onVerticalDrag = { change, dragAmount ->
//                                        change.consume()
//                                        accumulatedDragYSinceStart += dragAmount
//                                        scope.launch {
//                                            val newY = (currentSheetTranslationY.value + dragAmount)
//                                                .coerceIn(
//                                                    sheetExpandedTargetY - miniPlayerContentHeightPx * 0.2f,
//                                                    sheetCollapsedTargetY + miniPlayerContentHeightPx * 0.2f
//                                                )
//                                            currentSheetTranslationY.snapTo(newY)
//                                            val dragRatio =
//                                                (initialYOnDragStart - newY) / (sheetCollapsedTargetY - sheetExpandedTargetY).coerceAtLeast(
//                                                    1f
//                                                )
//                                            val newFraction =
//                                                (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
//                                            playerContentExpansionFraction.snapTo(newFraction)
//                                        }
//                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
//                                    },
//                                    onDragEnd = {
//                                        isDragging = false
//                                        isDraggingPlayerArea = false
//                                        val verticalVelocity = velocityTracker.calculateVelocity().y
//                                        val currentExpansionFraction = playerContentExpansionFraction.value
//                                        val minDragThresholdPx =
//                                            with(density) { 5.dp.toPx() }
//                                        val velocityThresholdForInstantTrigger =
//                                            150f
//                                        val targetContentState = when {
//                                            abs(accumulatedDragYSinceStart) > minDragThresholdPx -> {
//                                                if (accumulatedDragYSinceStart < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
//                                            }
//                                            abs(verticalVelocity) > velocityThresholdForInstantTrigger -> {
//                                                if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
//                                            }
//                                            else -> {
//                                                if (currentExpansionFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
//                                            }
//                                        }
//                                        scope.launch {
//                                            if (targetContentState == PlayerSheetState.EXPANDED) {
//                                                launch {
//                                                    currentSheetTranslationY.animateTo(
//                                                        targetValue = sheetExpandedTargetY,
//                                                        animationSpec = tween(
//                                                            durationMillis = ANIMATION_DURATION_MS,
//                                                            easing = FastOutSlowInEasing
//                                                        )
//                                                    )
//                                                }
//                                                launch {
//                                                    playerContentExpansionFraction.animateTo(
//                                                        targetValue = 1f,
//                                                        animationSpec = tween(
//                                                            durationMillis = ANIMATION_DURATION_MS,
//                                                            easing = FastOutSlowInEasing
//                                                        )
//                                                    )
//                                                }
//                                                playerViewModel.expandPlayerSheet()
//                                            } else {
//                                                val dynamicDampingRatio = lerp(
//                                                    start = Spring.DampingRatioNoBouncy,
//                                                    stop = Spring.DampingRatioLowBouncy,
//                                                    fraction = currentExpansionFraction
//                                                )
//                                                // New logic for scale animation
//                                                launch {
//                                                    val initialSquash = lerp(1.0f, 0.97f, currentExpansionFraction)
//                                                    visualOvershootScaleY.snapTo(initialSquash)
//                                                    visualOvershootScaleY.animateTo(
//                                                        targetValue = 1f,
//                                                        animationSpec = spring(
//                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
//                                                            stiffness = Spring.StiffnessVeryLow
//                                                        )
//                                                    )
//                                                }
//                                                launch {
//                                                    currentSheetTranslationY.animateTo(
//                                                        targetValue = sheetCollapsedTargetY,
//                                                        initialVelocity = verticalVelocity,
//                                                        animationSpec = spring(
//                                                            dampingRatio = dynamicDampingRatio,
//                                                            stiffness = Spring.StiffnessLow
//                                                        )
//                                                    )
//                                                }
//                                                launch {
//                                                    playerContentExpansionFraction.animateTo(
//                                                        targetValue = 0f,
//                                                        initialVelocity = verticalVelocity / (sheetCollapsedTargetY - sheetExpandedTargetY).coerceAtLeast(1f),
//                                                        animationSpec = spring(
//                                                            dampingRatio = dynamicDampingRatio,
//                                                            stiffness = Spring.StiffnessLow
//                                                        )
//                                                    )
//                                                }
//                                                playerViewModel.collapsePlayerSheet()
//                                            }
//                                        }
//                                        accumulatedDragYSinceStart = 0f
//                                    }
//                                )
//                            }
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
                                if (miniAlpha > 0.01f) {
                                    CompositionLocalProvider(
                                        LocalMaterialTheme provides albumColorScheme
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .graphicsLayer {
                                                    alpha = miniAlpha//miniPlayerAlpha
                                                }
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

                                if (fullPlayerContentAlpha > 0f) {
                                    CompositionLocalProvider(
                                        LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
                                    ) {
                                        Box(modifier = Modifier.graphicsLayer {
                                            alpha = fullPlayerContentAlpha
                                            translationY = fullPlayerTranslationY
                                        }) {
                                            FullPlayerContent(
                                                currentSong = currentSongNonNull,
                                                currentPlaybackQueue = currentPlaybackQueue,
                                                currentQueueSourceName = currentQueueSourceName,
                                                isShuffleEnabled = stablePlayerState.isShuffleEnabled,
                                                repeatMode = stablePlayerState.repeatMode,
                                                expansionFraction = playerContentExpansionFraction.value,
                                                currentSheetState = currentSheetContentState,
                                                carouselStyle = carouselStyle,
                                                playerViewModel = playerViewModel,
                                                // State Providers
                                                currentPositionProvider = { positionToDisplay },
                                                isPlayingProvider = { stablePlayerState.isPlaying },
                                                isFavoriteProvider = { isFavorite },
                                                // Event Handlers
                                                onPlayPause = playerViewModel::playPause,
                                                onSeek = playerViewModel::seekTo,
                                                onNext = playerViewModel::nextSong,
                                                onPrevious = playerViewModel::previousSong,
                                                onCollapse = playerViewModel::collapsePlayerSheet,
                                                onShowQueueClicked = { showQueueSheet = true },
                                                onShowCastClicked = { showCastSheet = true },
                                                onShowTrackVolumeClicked = { showTrackVolumeSheet = true },
                                                onShuffleToggle = playerViewModel::toggleShuffle,
                                                onRepeatToggle = playerViewModel::cycleRepeatMode,
                                                onFavoriteToggle = playerViewModel::toggleFavorite
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

    if (showCastSheet && !internalIsKeyboardVisible) {
        CompositionLocalProvider(
            LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
        ) {
            CastBottomSheet(
                playerViewModel = playerViewModel,
                onDismiss = { showCastSheet = false }
            )
        }
    }

    if (showTrackVolumeSheet) {
        val trackVolume by playerViewModel.trackVolume.collectAsState()
        CompositionLocalProvider(
            LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
        ) {
            TrackVolumeBottomSheet(
                theme = LocalMaterialTheme,
                initialVolume = trackVolume,
                onDismiss = { showTrackVolumeSheet = false },
                onVolumeChange = { newVolume ->
                    playerViewModel.setTrackVolume(newVolume)
                }
            )
        }
    }
    Trace.endSection() // End UnifiedPlayerSheet.Composition
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
    val hapticFeedback = LocalHapticFeedback.current

    val interaction = remember { MutableInteractionSource() }
    val indication: Indication = ripple(bounded = false)
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
            targetSize = Size(150, 150),
            modifier = Modifier
                .size(44.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val titleStyle = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer
            )
            val artistStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                letterSpacing = 0.sp,
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f)
            )

            AutoScrollingText(
                text = song.title,
                style = titleStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer
            )
            AutoScrollingText(
                text = song.artist,
                style = artistStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.primary)
                .clickable(
                    interactionSource = interaction,
                    indication = indication
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayPause()
                },
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
                    interactionSource = interaction,
                    indication = indication
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
