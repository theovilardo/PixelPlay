@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.theveloper.pixelplay.presentation.components

import android.os.Trace
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.MutatorMutex
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.player.FullPlayerContent
import com.theveloper.pixelplay.presentation.components.scoped.rememberExpansionTransition
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

internal val LocalMaterialTheme = staticCompositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

private enum class DragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

val MiniPlayerHeight = 64.dp
const val ANIMATION_DURATION_MS = 255

private data class SaveQueueOverlayData(
    val songs: List<Song>,
    val defaultName: String,
    val onConfirm: (String, Set<String>) -> Unit,
)

val MiniPlayerBottomSpacer = 8.dp

@OptIn(UnstableApi::class)
@Composable
fun UnifiedPlayerSheet(
    playerViewModel: PlayerViewModel,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    navController: NavHostController,
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

    val infrequentPlayerStateReference = remember {
        playerViewModel.stablePlayerState
            .map { it.copy(currentPosition = 0L) } // Keep totalDuration, only mask volatile position
            .distinctUntilChanged()
    }.collectAsState(initial = StablePlayerState())
    val infrequentPlayerState = infrequentPlayerStateReference.value

    val currentPositionState = remember {
        playerViewModel.playerUiState.map { it.currentPosition }.distinctUntilChanged()
    }.collectAsState(initial = 0L)

    val remotePositionState = playerViewModel.remotePosition.collectAsState()
    // We observe isRemotePlaybackActive directly as switching modes is a major event
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsState()
    
    // Position Provider: Reads state inside the lambda to prevent recomposition of UnifiedPlayerSheet
    val positionToDisplayProvider = remember(isRemotePlaybackActive) {
        {
            if (isRemotePlaybackActive) remotePositionState.value
            else currentPositionState.value
        }
    }
    
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
    var prewarmFullPlayer by remember { mutableStateOf(false) }

    val navBarCornerRadius by playerViewModel.navBarCornerRadius.collectAsState()
    val navBarStyle by playerViewModel.navBarStyle.collectAsState()
    val carouselStyle by playerViewModel.carouselStyle.collectAsState()
    val fullPlayerLoadingTweaks by playerViewModel.fullPlayerLoadingTweaks.collectAsState()
    val tapBackgroundClosesPlayer by playerViewModel.tapBackgroundClosesPlayer.collectAsState()
    val useSmoothCorners by playerViewModel.useSmoothCorners.collectAsState()

    LaunchedEffect(infrequentPlayerState.currentSong?.id) {
        if (infrequentPlayerState.currentSong != null) {
            prewarmFullPlayer = true
        }
    }
    LaunchedEffect(infrequentPlayerState.currentSong?.id, prewarmFullPlayer) {
        if (prewarmFullPlayer) {
            delay(32)
            prewarmFullPlayer = false
        }
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val offsetAnimatable = remember { Animatable(0f) }

    val screenWidthPx =
        remember(configuration, density) { with(density) { configuration.screenWidthDp.dp.toPx() } }
    val dismissThresholdPx = remember(screenWidthPx) { screenWidthPx * 0.4f }

    val swipeDismissProgress = remember(offsetAnimatable.value, dismissThresholdPx) {
        derivedStateOf {
            if (dismissThresholdPx == 0f) 0f
            else (abs(offsetAnimatable.value) / dismissThresholdPx).coerceIn(0f, 1f)
        }
    }

    val screenHeightPx = remember(
        configuration,
        density
    ) { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val miniPlayerContentHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }
    val miniPlayerAndSpacerHeightPx =
        remember(density, MiniPlayerHeight) { with(density) { MiniPlayerHeight.toPx() } }

    val isCastConnecting by playerViewModel.isCastConnecting.collectAsState()

    val showPlayerContentArea by remember(infrequentPlayerState.currentSong, isCastConnecting) {
        derivedStateOf { infrequentPlayerState.currentSong != null || isCastConnecting }
    }

    // Use the granular showDismissUndoBar here
    val isPlayerSlotOccupied by remember(showPlayerContentArea, showDismissUndoBar) {
        derivedStateOf {
            showPlayerContentArea || showDismissUndoBar
        }
    }

    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction
    val visualOvershootScaleY = remember { Animatable(1f) }
    val initialFullPlayerOffsetY = remember(density) { with(density) { 24.dp.toPx() } }
    val sheetAnimationSpec = remember {
        tween<Float>(
            durationMillis = ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        )
    }
    val sheetAnimationMutex = remember { MutatorMutex() }

    val sheetExpandedTargetY = 0f

    val initialY =
        if (currentSheetContentState == PlayerSheetState.COLLAPSED) sheetCollapsedTargetY else sheetExpandedTargetY
    val currentSheetTranslationY = remember { Animatable(initialY) }

    LaunchedEffect(
        navController,
        sheetAnimationMutex,
        sheetCollapsedTargetY
    ) {
        playerViewModel.artistNavigationRequests.collectLatest { artistId ->
            sheetAnimationMutex.mutate {
                currentSheetTranslationY.snapTo(sheetCollapsedTargetY)
                playerContentExpansionFraction.snapTo(0f)
            }
            playerViewModel.collapsePlayerSheet()

            navController.navigate(Screen.ArtistDetail.createRoute(artistId)) {
                launchSingleTop = true
            }
        }
    }

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

    suspend fun animatePlayerSheet(
        targetExpanded: Boolean,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = sheetAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        val targetFraction = if (showPlayerContentArea && targetExpanded) 1f else 0f
        val targetY = if (targetExpanded) sheetExpandedTargetY else sheetCollapsedTargetY
        val velocityScale = (sheetCollapsedTargetY - sheetExpandedTargetY).coerceAtLeast(1f)

        sheetAnimationMutex.mutate {
            coroutineScope {
                launch {
                    currentSheetTranslationY.animateTo(
                        targetValue = targetY,
                        initialVelocity = initialVelocity,
                        animationSpec = animationSpec
                    )
                }
                launch {
                    playerContentExpansionFraction.animateTo(
                        targetValue = targetFraction,
                        initialVelocity = initialVelocity / velocityScale,
                        animationSpec = animationSpec
                    )
                }
            }
        }
    }

    LaunchedEffect(sheetCollapsedTargetY) {
        val adjustedY = lerp(
            sheetCollapsedTargetY,
            sheetExpandedTargetY,
            playerContentExpansionFraction.value
        )

        sheetAnimationMutex.mutate {
            currentSheetTranslationY.snapTo(adjustedY)
        }
    }

    LaunchedEffect(showPlayerContentArea, currentSheetContentState) {
        val targetExpanded = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED

        animatePlayerSheet(targetExpanded = targetExpanded)

        if (showPlayerContentArea) {
            scope.launch {
                visualOvershootScaleY.snapTo(1f)
                if (targetExpanded) {
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
            scope.launch { visualOvershootScaleY.snapTo(1f) }
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

    val playerContentAreaActualHeightPx by remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        containerHeight,
        miniPlayerContentHeightPx
    ) {
        derivedStateOf {
            if (showPlayerContentArea) {
                val containerHeightPx = with(density) { containerHeight.toPx() }
                lerp(
                    miniPlayerContentHeightPx,
                    containerHeightPx,
                    playerContentExpansionFraction.value
                )
            } else {
                0f
            }
        }
    }
    val playerContentAreaHeightDp by remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        containerHeight
    ) {
        derivedStateOf {
            if (showPlayerContentArea) lerp(
                MiniPlayerHeight,
                containerHeight,
                playerContentExpansionFraction.value
            )
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
                lerp(
                    totalSheetHeightWhenContentCollapsedPx,
                    screenHeightPx,
                    playerContentExpansionFraction.value
                )
            } else {
                0f
            }
        }
    }

    val navBarElevation = 3.dp
    val shadowSpacePx = remember(density, navBarElevation) {
        with(density) { (navBarElevation * 8).toPx() }
    }

    val animatedTotalSheetHeightWithShadowPx by remember(
        animatedTotalSheetHeightPx,
        shadowSpacePx
    ) {
        derivedStateOf {
            animatedTotalSheetHeightPx + shadowSpacePx
        }
    }
    val animatedTotalSheetHeightWithShadowDp =
        with(density) { animatedTotalSheetHeightWithShadowPx.toDp() }

    //with(density) { animatedTotalSheetHeightPx.toDp() }

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

    val overallSheetTopCornerRadius = overallSheetTopCornerRadiusTargetValue

    val playerContentActualBottomRadiusTargetValue by remember(
        navBarStyle,
        showPlayerContentArea,
        playerContentExpansionFraction,
        infrequentPlayerState.isPlaying,
        infrequentPlayerState.currentSong,
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

            val calculatedNormally =
                if (predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
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
                        if (!infrequentPlayerState.isPlaying || infrequentPlayerState.currentSong == null) {
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

    val playerContentActualBottomRadius = playerContentActualBottomRadiusTargetValue

    val actualCollapsedStateHorizontalPadding =
        if (navBarStyle == NavBarStyle.FULL_WIDTH) 14.dp else collapsedStateHorizontalPadding

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
                lerp(
                    actualCollapsedStateHorizontalPadding,
                    0.dp,
                    playerContentExpansionFraction.value
                )
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
    val allowQueueSheetInteraction by remember(showPlayerContentArea, currentSheetContentState) {
        derivedStateOf {
            showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED
        }
    }
    val queueSheetOffset = remember(screenHeightPx) { Animatable(screenHeightPx) }
    var queueSheetHeightPx by remember { mutableFloatStateOf(0f) }
    val queueHiddenOffsetPx by remember(currentBottomPadding, queueSheetHeightPx, density) {
        derivedStateOf {
            val basePadding = with(density) { currentBottomPadding.toPx() }
            if (queueSheetHeightPx == 0f) 0f else queueSheetHeightPx + basePadding
        }
    }
    val queueDragThresholdPx by remember(queueHiddenOffsetPx) {
        derivedStateOf { queueHiddenOffsetPx * 0.08f }
    }
    var pendingSaveQueueOverlay by remember { mutableStateOf<SaveQueueOverlayData?>(null) }
    var showCastSheet by remember { mutableStateOf(false) }
    var castSheetOpenFraction by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var isDraggingPlayerArea by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    var accumulatedDragYSinceStart by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(queueHiddenOffsetPx) {
        if (queueHiddenOffsetPx <= 0f) return@LaunchedEffect
        val targetOffset = if (showQueueSheet) {
            queueSheetOffset.value.coerceIn(0f, queueHiddenOffsetPx)
        } else {
            queueHiddenOffsetPx
        }
        queueSheetOffset.snapTo(targetOffset)
    }

    suspend fun animateQueueSheetInternal(targetExpanded: Boolean) {
        if (queueHiddenOffsetPx == 0f) {
            showQueueSheet = targetExpanded
            return
        }
        val target = if (targetExpanded) 0f else queueHiddenOffsetPx
        showQueueSheet = true
        queueSheetOffset.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = ANIMATION_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        )
        showQueueSheet = targetExpanded
    }

    fun animateQueueSheet(targetExpanded: Boolean) {
        if (!allowQueueSheetInteraction && targetExpanded) return
        scope.launch { animateQueueSheetInternal(targetExpanded && allowQueueSheetInteraction) }
    }

    fun beginQueueDrag() {
        if (queueHiddenOffsetPx == 0f || !allowQueueSheetInteraction) return
        showQueueSheet = true
        scope.launch { queueSheetOffset.stop() }
    }

    fun dragQueueBy(dragAmount: Float) {
        if (queueHiddenOffsetPx == 0f || !allowQueueSheetInteraction) return
        val newOffset = (queueSheetOffset.value + dragAmount).coerceIn(0f, queueHiddenOffsetPx)
        scope.launch { queueSheetOffset.snapTo(newOffset) }
    }

    fun endQueueDrag(totalDrag: Float, velocity: Float) {
        if (queueHiddenOffsetPx == 0f || !allowQueueSheetInteraction) return
        val isFastUpward = velocity < -650f
        val isFastDownward = velocity > 650f
        val shouldExpand =
            isFastUpward || (!isFastDownward && (queueSheetOffset.value < queueHiddenOffsetPx - queueDragThresholdPx || totalDrag < -queueDragThresholdPx))
        animateQueueSheet(shouldExpand)
    }

    val hapticFeedback = LocalHapticFeedback.current
    val updatedQueueImpactHaptics by rememberUpdatedState(hapticFeedback)

    LaunchedEffect(queueHiddenOffsetPx, showQueueSheet) {
        if (queueHiddenOffsetPx == 0f) return@LaunchedEffect
        var hasHitTopEdge = showQueueSheet && queueSheetOffset.value <= 0.5f
        snapshotFlow { queueSheetOffset.value to showQueueSheet }
            .collectLatest { (offset, isShown) ->
                val isFullyOpen = isShown && offset <= 0.5f
                if (isFullyOpen && !hasHitTopEdge) {
                    updatedQueueImpactHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    hasHitTopEdge = true
                } else if (!isFullyOpen) {
                    hasHitTopEdge = false
                }
            }
    }

    LaunchedEffect(allowQueueSheetInteraction, queueHiddenOffsetPx) {
        if (allowQueueSheetInteraction) return@LaunchedEffect
        showQueueSheet = false
        if (queueHiddenOffsetPx > 0f) {
            queueSheetOffset.snapTo(queueHiddenOffsetPx)
        }
    }

    PredictiveBackHandler(
        enabled = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED && !isDragging
    ) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                playerViewModel.updatePredictiveBackCollapseFraction(backEvent.progress)
            }
            scope.launch {
                val progressAtRelease = playerViewModel.predictiveBackCollapseFraction.value
                val currentVisualY =
                    lerp(sheetExpandedTargetY, sheetCollapsedTargetY, progressAtRelease)
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

    val isQueueVisible by remember(showQueueSheet, queueHiddenOffsetPx) {
        derivedStateOf { showQueueSheet && queueHiddenOffsetPx > 0f && queueSheetOffset.value < queueHiddenOffsetPx }
    }

    val queueOpenFraction by remember(queueSheetOffset, queueHiddenOffsetPx) {
        derivedStateOf {
            if (queueHiddenOffsetPx == 0f) 0f else (1f - (queueSheetOffset.value / queueHiddenOffsetPx)).coerceIn(
                0f,
                1f
            )
        }
    }
    val effectiveQueueOpenFraction by remember(queueOpenFraction, showQueueSheet, queueHiddenOffsetPx) {
        derivedStateOf {
            if (queueHiddenOffsetPx == 0f && showQueueSheet) 1f else queueOpenFraction
        }
    }
    val bottomSheetOpenFraction by remember(effectiveQueueOpenFraction, castSheetOpenFraction) {
        derivedStateOf { max(effectiveQueueOpenFraction, castSheetOpenFraction) }
    }
    val queueScrimAlpha by remember(effectiveQueueOpenFraction) {
        derivedStateOf { (effectiveQueueOpenFraction * 0.45f).coerceIn(0f, 0.45f) }
    }

    val updatedPendingSaveOverlay = rememberUpdatedState(pendingSaveQueueOverlay)
    fun launchSaveQueueOverlay(
        songs: List<Song>,
        defaultName: String,
        onConfirm: (String, Set<String>) -> Unit
    ) {
        if (updatedPendingSaveOverlay.value != null) return
        scope.launch {
            animateQueueSheetInternal(false)
            playerViewModel.collapsePlayerSheet()
            delay(ANIMATION_DURATION_MS.toLong())
            pendingSaveQueueOverlay = SaveQueueOverlayData(songs, defaultName, onConfirm)
        }
    }

    var internalIsKeyboardVisible by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) } // State for the selected song info

    val imeInsets = WindowInsets.ime
    LaunchedEffect(imeInsets, density) {
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .distinctUntilChanged()
            .collectLatest { isVisible ->
                if (internalIsKeyboardVisible != isVisible) {
                    internalIsKeyboardVisible = isVisible
                }
            }
    }

    val actuallyShowSheetContent = shouldShowSheet && (
            !internalIsKeyboardVisible ||
            currentSheetContentState == PlayerSheetState.EXPANDED ||
            pendingSaveQueueOverlay != null ||
            selectedSongForInfo != null
    )

    // val currentAlbumColorSchemePair by playerViewModel.currentAlbumArtColorSchemePair.collectAsState() // Replaced by activePlayerColorSchemePair
    val activePlayerSchemePair by playerViewModel.activePlayerColorSchemePair.collectAsState()
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val systemColorScheme = MaterialTheme.colorScheme // This is the standard M3 theme

    val targetColorScheme = remember(activePlayerSchemePair, isDarkTheme, systemColorScheme) {
        val schemeFromPair = activePlayerSchemePair?.let { if (isDarkTheme) it.dark else it.light }
        schemeFromPair
            ?: systemColorScheme // If activePlayerSchemePair is null (i.e. System Dynamic selected) OR the selected scheme from pair is somehow null, use systemColorScheme
    }

    val albumColorScheme = targetColorScheme

    val t = rememberExpansionTransition(playerContentExpansionFraction.value)

    val playerAreaElevation by t.animateDp(label = "elev") { f -> lerp(2.dp, 12.dp, f) }

    val miniAlpha by t.animateFloat(label = "miniAlpha") { f -> (1f - f * 2f).coerceIn(0f, 1f) }

    val useSmoothShape by remember(useSmoothCorners, isDragging, playerContentExpansionFraction.isRunning) {
        derivedStateOf {
            useSmoothCorners && !isDragging && !playerContentExpansionFraction.isRunning
        }
    }

    val playerShadowShape = remember(overallSheetTopCornerRadius, playerContentActualBottomRadius, useSmoothShape) {
        if (useSmoothShape) {
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
        } else {
            RoundedCornerShape(
                topStart = overallSheetTopCornerRadius,
                topEnd = overallSheetTopCornerRadius,
                bottomStart = playerContentActualBottomRadius,
                bottomEnd = playerContentActualBottomRadius
            )
        }
    }

    val isCollapsedState =
        rememberUpdatedState(currentSheetContentState == PlayerSheetState.COLLAPSED)

    val collapsedY = rememberUpdatedState(sheetCollapsedTargetY)
    val expandedY = rememberUpdatedState(sheetExpandedTargetY)
    val canShow = rememberUpdatedState(showPlayerContentArea)
    val miniH = rememberUpdatedState(miniPlayerContentHeightPx)
    val dens = rememberUpdatedState(LocalDensity.current) // opcional; útil para thresholds

    if (actuallyShowSheetContent) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, visualSheetTranslationY.roundToInt()) }
                .height(containerHeight),
            shadowElevation = 0.dp,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = currentBottomPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Use granular showDismissUndoBar and undoBarVisibleDuration
                    if (showPlayerContentArea) {
                        val dismissGestureModifier =
                            if (currentSheetContentState == PlayerSheetState.COLLAPSED) {
                                Modifier.pointerInput(Unit) {
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
                                                    val snapThresholdPx =
                                                        with(density) { 100.dp.toPx() }
                                                    if (abs(accumulatedDragX) < snapThresholdPx) {
                                                        val maxTensionOffsetPx =
                                                            with(density) { 30.dp.toPx() }
                                                        val dragFraction =
                                                            (abs(accumulatedDragX) / snapThresholdPx).coerceIn(
                                                                0f,
                                                                1f
                                                            )
                                                        val tensionOffset =
                                                            lerp(
                                                                0f,
                                                                maxTensionOffsetPx,
                                                                dragFraction
                                                            )
                                                        scope.launch {
                                                            offsetAnimatable.snapTo(
                                                                tensionOffset * accumulatedDragX.sign
                                                            )
                                                        }
                                                    } else {
                                                        // Threshold crossed, transition to the snap phase
                                                        dragPhase = DragPhase.SNAPPING
                                                    }
                                                }

                                                DragPhase.SNAPPING -> {
                                                    hapticFeedback.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
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
                                                val targetDismissOffset =
                                                    if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
                                                scope.launch {
                                                    offsetAnimatable.animateTo(
                                                        targetValue = targetDismissOffset,
                                                        animationSpec = tween(
                                                            durationMillis = 200,
                                                            easing = FastOutSlowInEasing
                                                        )
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
                            } else {
                                Modifier
                            }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(dismissGestureModifier)
                                .padding(horizontal = currentHorizontalPadding)
                                .height(playerContentAreaHeightDp)
                                .graphicsLayer {
                                    translationX = offsetAnimatable.value
                                    scaleY = visualOvershootScaleY.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .shadow(
                                    elevation = playerAreaElevation,
                                    shape = RoundedCornerShape(
                                        topStart = overallSheetTopCornerRadius,
                                        topEnd = overallSheetTopCornerRadius,
                                        bottomStart = playerContentActualBottomRadius,
                                        bottomEnd = playerContentActualBottomRadius
                                    ),
                                    clip = false
                                )
                                .background(
                                    color = albumColorScheme.primaryContainer,
                                    shape = playerShadowShape
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
                                            initialFractionOnDragStart =
                                                playerContentExpansionFraction.value
                                            initialYOnDragStart = currentSheetTranslationY.value
                                            accumulatedDragYSinceStart = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            accumulatedDragYSinceStart += dragAmount
                                            scope.launch {
                                                val newY =
                                                    (currentSheetTranslationY.value + dragAmount)
                                                        .coerceIn(
                                                            expandedY.value - miniH.value * 0.2f,
                                                            collapsedY.value + miniH.value * 0.2f
                                                        )
                                                currentSheetTranslationY.snapTo(newY)

                                                val denom =
                                                    (collapsedY.value - expandedY.value).coerceAtLeast(
                                                        1f
                                                    )
                                                val dragRatio = (initialYOnDragStart - newY) / denom
                                                val newFraction =
                                                    (initialFractionOnDragStart + dragRatio).coerceIn(
                                                        0f,
                                                        1f
                                                    )
                                                playerContentExpansionFraction.snapTo(newFraction)
                                            }
                                            velocityTracker.addPosition(
                                                change.uptimeMillis,
                                                change.position
                                            )
                                        },
                                        onDragEnd = {
                                            isDragging = false
                                            isDraggingPlayerArea = false

                                            val verticalVelocity =
                                                velocityTracker.calculateVelocity().y
                                            val currentFraction =
                                                playerContentExpansionFraction.value
                                            val minDragThresholdPx =
                                                with(dens.value) { 5.dp.toPx() }
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
                                                    launch { animatePlayerSheet(targetExpanded = true) }
                                                    playerViewModel.expandPlayerSheet()
                                                } else {
                                                    val dynamicDamping = lerp(
                                                        start = Spring.DampingRatioNoBouncy,
                                                        stop = Spring.DampingRatioLowBouncy,
                                                        fraction = currentFraction
                                                    )
                                                    launch {
                                                        val initialSquash =
                                                            lerp(1.0f, 0.97f, currentFraction)
                                                        visualOvershootScaleY.snapTo(initialSquash)
                                                        visualOvershootScaleY.animateTo(
                                                            1f,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessVeryLow
                                                            )
                                                        )
                                                    }
                                                    launch {
                                                        animatePlayerSheet(
                                                            targetExpanded = false,
                                                            animationSpec = spring(
                                                                dampingRatio = dynamicDamping,
                                                                stiffness = Spring.StiffnessLow
                                                            ),
                                                            initialVelocity = verticalVelocity
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
                                    enabled = tapBackgroundClosesPlayer || currentSheetContentState == PlayerSheetState.COLLAPSED,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    playerViewModel.togglePlayerSheetState()
                                }
                        ) {
                                    // MiniPlayerContentInternal
                                    // stablePlayerState.currentSong is already available from the top-level collection
                                    // Use infrequentPlayerState
                                    infrequentPlayerState.currentSong?.let { currentSongNonNull ->
                                    // MiniPlayer
                                    Crossfade(
                                        targetState = albumColorScheme,
                                        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                                        label = "miniPlayerColorScheme"
                                    ) { scheme ->
                                        CompositionLocalProvider(
                                            LocalMaterialTheme provides (scheme ?: MaterialTheme.colorScheme)
                                        ) {
                                            val miniPlayerZIndex by remember { derivedStateOf { if (playerContentExpansionFraction.value < 0.5f) 1f else 0f } }
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .graphicsLayer {
                                                        alpha = miniAlpha
                                                    }
                                                    .zIndex(miniPlayerZIndex)
                                            ) {
                                                MiniPlayerContentInternal(
                                                    song = currentSongNonNull, // Use non-null version
                                                    cornerRadiusAlb = (overallSheetTopCornerRadius.value * 0.5).dp,
                                                    isPlaying = infrequentPlayerState.isPlaying, // from top-level stablePlayerState
                                                    isCastConnecting = isCastConnecting,
                                                    onPlayPause = { playerViewModel.playPause() },
                                                    onPrevious = { playerViewModel.previousSong() },
                                                    onNext = { playerViewModel.nextSong() },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }

                                    // FullPlayer
                                    CompositionLocalProvider(
                                        LocalMaterialTheme provides (albumColorScheme
                                            ?: MaterialTheme.colorScheme)
                                    ) {
                                        val fullPlayerScale by remember(bottomSheetOpenFraction) {
                                            derivedStateOf {
                                                lerp(
                                                    1f,
                                                    0.95f,
                                                    bottomSheetOpenFraction
                                                )
                                            }
                                        }
                                        
                                        val fullPlayerZIndex by remember { derivedStateOf { if (playerContentExpansionFraction.value >= 0.5f) 1f else 0f } }
                                        val fullPlayerOffset by remember { derivedStateOf { if (playerContentExpansionFraction.value <= 0.01f) IntOffset(0, 10000) else IntOffset.Zero } }
                                        
                                        Box(
                                            modifier = Modifier
                                                .graphicsLayer {
                                                    alpha = fullPlayerContentAlpha
                                                    translationY = fullPlayerTranslationY
                                                    scaleX = fullPlayerScale
                                                    scaleY = fullPlayerScale
                                                }
                                                .zIndex(fullPlayerZIndex)
                                                .offset { fullPlayerOffset }
                                        ) {
                                            FullPlayerContent(
                                                currentSong = currentSongNonNull,
                                                currentPlaybackQueue = currentPlaybackQueue,
                                                currentQueueSourceName = currentQueueSourceName,
                                                isShuffleEnabled = infrequentPlayerState.isShuffleEnabled,
                                                repeatMode = infrequentPlayerState.repeatMode,
                                                expansionFractionProvider = { playerContentExpansionFraction.value },
                                                currentSheetState = currentSheetContentState,
                                                carouselStyle = carouselStyle,
                                                loadingTweaks = fullPlayerLoadingTweaks,
                                                playerViewModel = playerViewModel,
                                                // State Providers
                                                currentPositionProvider = positionToDisplayProvider,
                                                isPlayingProvider = { infrequentPlayerState.isPlaying },
                                                repeatModeProvider = { infrequentPlayerState.repeatMode },
                                                isShuffleEnabledProvider = { infrequentPlayerState.isShuffleEnabled },
                                                totalDurationProvider = { infrequentPlayerState.totalDuration },
                                                lyricsProvider = { infrequentPlayerState.lyrics },
                                                isFavoriteProvider = { isFavorite },
                                                // Event Handlers
                                                onPlayPause = playerViewModel::playPause,
                                                onSeek = playerViewModel::seekTo,
                                                onNext = playerViewModel::nextSong,
                                                onPrevious = playerViewModel::previousSong,
                                                onCollapse = playerViewModel::collapsePlayerSheet,
                                                onShowQueueClicked = { animateQueueSheet(true) },
                                                onQueueDragStart = { beginQueueDrag() },
                                                onQueueDrag = { dragQueueBy(it) },
                                                onQueueRelease = { totalDrag, velocity ->
                                                    endQueueDrag(
                                                        totalDrag,
                                                        velocity
                                                    )
                                                },
                                                onShowCastClicked = { showCastSheet = true },
                                                onShuffleToggle = playerViewModel::toggleShuffle,
                                                onRepeatToggle = playerViewModel::cycleRepeatMode,
                                                onFavoriteToggle = playerViewModel::toggleFavorite,
                                            )
                                        }
                                    }
                                }
                        }
                    }

                    // Prewarm full player once per track to reduce first-open jank.
                    if (prewarmFullPlayer && infrequentPlayerState.currentSong != null) {
                        CompositionLocalProvider(
                            LocalMaterialTheme provides (albumColorScheme
                                ?: MaterialTheme.colorScheme)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(containerHeight)
                                    .fillMaxWidth()
                                    .alpha(0f)
                                    .clipToBounds()
                            ) {
                                FullPlayerContent(
                                    currentSong = infrequentPlayerState.currentSong!!,
                                    currentPlaybackQueue = currentPlaybackQueue,
                                    currentQueueSourceName = currentQueueSourceName,
                                    isShuffleEnabled = infrequentPlayerState.isShuffleEnabled,
                                    repeatMode = infrequentPlayerState.repeatMode,
                                    expansionFractionProvider = { 1f },
                                    currentSheetState = PlayerSheetState.EXPANDED,
                                    carouselStyle = carouselStyle,
                                    loadingTweaks = fullPlayerLoadingTweaks,
                                    playerViewModel = playerViewModel,
                                    currentPositionProvider = positionToDisplayProvider,
                                    isPlayingProvider = { infrequentPlayerState.isPlaying },
                                    repeatModeProvider = { infrequentPlayerState.repeatMode },
                                    isShuffleEnabledProvider = { infrequentPlayerState.isShuffleEnabled },
                                    totalDurationProvider = { infrequentPlayerState.totalDuration },
                                    lyricsProvider = { infrequentPlayerState.lyrics },
                                    isFavoriteProvider = { isFavorite },
                                    onShowQueueClicked = { animateQueueSheet(true) },
                                    onQueueDragStart = { beginQueueDrag() },
                                    onQueueDrag = { dragQueueBy(it) },
                                    onQueueRelease = { totalDrag, velocity ->
                                        endQueueDrag(
                                            totalDrag,
                                            velocity
                                        )
                                    },
                                    onPlayPause = playerViewModel::playPause,
                                    onSeek = playerViewModel::seekTo,
                                    onNext = playerViewModel::nextSong,
                                    onPrevious = playerViewModel::previousSong,
                                    onCollapse = {},
                                    onShowCastClicked = {},
                                    onShuffleToggle = playerViewModel::toggleShuffle,
                                    onRepeatToggle = playerViewModel::cycleRepeatMode,
                                    onFavoriteToggle = playerViewModel::toggleFavorite,
                                )
                            }
                        }
                    }

                    // Use granular showDismissUndoBar
                    val isPlayerOrUndoBarVisible = showPlayerContentArea || showDismissUndoBar
                    if (isPlayerOrUndoBarVisible) {
                        // Spacer removed
                    }
                }

                BackHandler(enabled = isQueueVisible && !internalIsKeyboardVisible) {
                    animateQueueSheet(false)
                }


                if (!internalIsKeyboardVisible || selectedSongForInfo != null) {
                    CompositionLocalProvider(
                        LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(
                                modifier = Modifier
                                    .matchParentSize()
                                    .zIndex(0f),
                                visible = queueScrimAlpha > 0f,
                                enter = fadeIn(animationSpec = tween(ANIMATION_DURATION_MS)),
                                exit = fadeOut(animationSpec = tween(ANIMATION_DURATION_MS))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            color = MaterialTheme.colorScheme.scrim.copy(alpha = queueScrimAlpha)
                                        )
                                )
                            }

                                val onDimissQueueRequest = remember { { animateQueueSheet(false) } }
                                val onQueueSongInfoClick = remember { { song: Song -> selectedSongForInfo = song } }
                                val onPlayQueueSong = remember(currentPlaybackQueue, currentQueueSourceName) {
                                    { song: Song -> 
                                        playerViewModel.playSongs(currentPlaybackQueue, song, currentQueueSourceName)
                                    }
                                }
                                val onRemoveQueueSong = remember { { id: String -> playerViewModel.removeSongFromQueue(id) } }
                                val onReorderQueue = remember { { from: Int, to: Int -> playerViewModel.reorderQueueItem(from, to) } }
                                val onToggleRepeat = remember { { playerViewModel.cycleRepeatMode() } }
                                val onToggleShuffle = remember { { playerViewModel.toggleShuffle() } }
                                val onClearQueue = remember { { playerViewModel.clearQueueExceptCurrent() } }
                                val onSetPredefinedTimer = remember { { minutes: Int -> playerViewModel.setSleepTimer(minutes) } }
                                val onSetEndOfTrackTimer = remember { { enable: Boolean -> playerViewModel.setEndOfTrackTimer(enable) } }
                                val onOpenCustomTimePicker: () -> Unit = remember { { Log.d("TimerOptions", "OpenCustomTimePicker clicked") } }
                                val onCancelTimer = remember { { playerViewModel.cancelSleepTimer() } }
                                val onCancelCountedPlay = remember { playerViewModel::cancelCountedPlay }
                                val onPlayCounter = remember { playerViewModel::playCounted }
                                val onRequestSavePlaylist = remember {
                                    { songs: List<Song>, defName: String, onConf: (String, Set<String>) -> Unit ->
                                        launchSaveQueueOverlay(songs, defName, onConf)
                                    }
                                }
                                val onQueueStartDrag = remember { { beginQueueDrag() } }
                                val onQueueDrag = remember { { drag: Float -> dragQueueBy(drag) } }
                                val onQueueRelease = remember { { drag: Float, vel: Float -> endQueueDrag(drag, vel) } }

                                val shouldRenderQueueSheet by remember(showQueueSheet, queueSheetOffset.value, queueHiddenOffsetPx, queueSheetHeightPx) {
                                  derivedStateOf {
                                    showQueueSheet || queueSheetHeightPx == 0f || queueSheetOffset.value < queueHiddenOffsetPx
                                  }
                                }
                                
                                // Force re-measure on configuration change
                                LaunchedEffect(configuration) {
                                    queueSheetHeightPx = 0f
                                }

                                if (shouldRenderQueueSheet) {
                                  QueueBottomSheet(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset {
                                             IntOffset(
                                                x = 0,
                                                y = queueSheetOffset.value.roundToInt()
                                            )
                                        }
                                        .graphicsLayer {
                                            alpha =
                                                if (queueHiddenOffsetPx == 0f || !showQueueSheet) 0f else 1f
                                        }
                                        .onGloballyPositioned { coordinates ->
                                            queueSheetHeightPx = coordinates.size.height.toFloat()
                                        },
                                    queue = currentPlaybackQueue,
                                    currentQueueSourceName = currentQueueSourceName,
                                    currentSongId = infrequentPlayerState.currentSong?.id,
                                    onDismiss = onDimissQueueRequest,
                                    onSongInfoClick = onQueueSongInfoClick,
                                    onPlaySong = onPlayQueueSong, // Correct lambda reference
                                    onRemoveSong = onRemoveQueueSong,
                                    onReorder = onReorderQueue,
                                    repeatMode = infrequentPlayerState.repeatMode,
                                    isShuffleOn = infrequentPlayerState.isShuffleEnabled,
                                    onToggleRepeat = onToggleRepeat,
                                    onToggleShuffle = onToggleShuffle,
                                    onClearQueue = onClearQueue,
                                    activeTimerValueDisplay = playerViewModel.activeTimerValueDisplay.collectAsState(),
                                    playCount = playerViewModel.playCount.collectAsState(),
                                    isEndOfTrackTimerActive = playerViewModel.isEndOfTrackTimerActive.collectAsState(),
                                    onSetPredefinedTimer = onSetPredefinedTimer,
                                    onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                                    onOpenCustomTimePicker = onOpenCustomTimePicker,
                                    onCancelTimer = onCancelTimer,
                                    onCancelCountedPlay = onCancelCountedPlay,
                                    onPlayCounter = onPlayCounter,
                                    onRequestSaveAsPlaylist = onRequestSavePlaylist,
                                    onQueueDragStart = onQueueStartDrag,
                                    onQueueDrag = onQueueDrag,
                                    onQueueRelease = onQueueRelease
                                )
                              }

                            // Show SongInfoBottomSheet when a song is selected
                            selectedSongForInfo?.let { staticSong ->
                                // Observar cambios en la canción (metadata o favorite status) reactivamente
                                val liveSongState by remember(staticSong.id) {
                                    playerViewModel.observeSong(staticSong.id)
                                        .map { it ?: staticSong } // Si no está en la librería, usar la estática como fallback
                                }.collectAsState(initial = staticSong)

                                val liveSong = liveSongState ?: staticSong

                                SongInfoBottomSheet(
                                    song = liveSong,
                                    isFavorite = liveSong.isFavorite,
                                    
                                    onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(liveSong) },
                                    onDismiss = { selectedSongForInfo = null },
                                    onPlaySong = { 
                                        playerViewModel.playSongs(currentPlaybackQueue, liveSong, currentQueueSourceName)
                                        selectedSongForInfo = null
                                    },
                                    onAddToQueue = { 
                                        playerViewModel.addSongToQueue(liveSong)
                                        selectedSongForInfo = null
                                        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                                    },
                                    onAddNextToQueue = { 
                                        playerViewModel.addSongNextToQueue(liveSong)
                                        selectedSongForInfo = null
                                         Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                                    },
                                    onAddToPlayList = { 
                                        // Trigger playlist selection dialog (if implemented in ViewModel or UI)
                                        // For now we might need a placeholder or check how it is implemented elsewhere.
                                        // playerViewModel doesn't seem to have 'openAddToPlaylistDialog'.
                                        // Maybe we can skip this or implement if simple.
                                        // SongInfoBottomSheet usually handles the UI for it? No, it has onAddToPlayList callback.
                                        // Let's leave it empty or log for now if we don't have a ready handler
                                        Log.d("UnifiedPlayerSheet", "Add to playlist clicked for ${liveSong.title}")
                                         selectedSongForInfo = null
                                    },
                                    onDeleteFromDevice = { activity, songToDelete, onResult ->
                                         playerViewModel.deleteFromDevice(activity, songToDelete, onResult)
                                         selectedSongForInfo = null
                                    },
                                    onNavigateToAlbum = {
                                        scope.launch {
                                            sheetAnimationMutex.mutate {
                                                currentSheetTranslationY.snapTo(sheetCollapsedTargetY)
                                                playerContentExpansionFraction.snapTo(0f)
                                            }
                                        }
                                        playerViewModel.collapsePlayerSheet()
                                        animateQueueSheet(false)
                                        selectedSongForInfo = null

                                         if (liveSong.albumId != -1L) {
                                            navController.navigate(Screen.AlbumDetail.createRoute(liveSong.albumId))
                                         }
                                    },
                                    onNavigateToArtist = {
                                        scope.launch {
                                            sheetAnimationMutex.mutate {
                                                currentSheetTranslationY.snapTo(sheetCollapsedTargetY)
                                                playerContentExpansionFraction.snapTo(0f)
                                            }
                                        }
                                        playerViewModel.collapsePlayerSheet()
                                        animateQueueSheet(false)
                                        selectedSongForInfo = null
                                        if (liveSong.artistId != -1L) {
                                            navController.navigate(Screen.ArtistDetail.createRoute(liveSong.artistId))
                                        }
                                    },
                                    onEditSong = { title, artist, album, genre, lyrics, trackNumber, coverArtUpdate ->
                                        playerViewModel.editSongMetadata(liveSong, title, artist, album, genre, lyrics, trackNumber, coverArtUpdate)
                                         selectedSongForInfo = null
                                    },
                                    generateAiMetadata = { fields -> playerViewModel.generateAiMetadata(liveSong, fields) },
                                    removeFromListTrigger = {
                                         // This is usually used to remove from a specific list (like 'Favorites').
                                         // In Queue, we have specific 'Remove' button. 
                                         // But maybe the user wants to remove from queue via this menu?
                                         playerViewModel.removeSongFromQueue(liveSong.id)
                                         selectedSongForInfo = null
                                    }
                                )
                            }
                        }
                    }
                }

            }
        }

        if (showCastSheet && !internalIsKeyboardVisible) {
            CompositionLocalProvider(
                LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
            ) {
                CastBottomSheet(
                    playerViewModel = playerViewModel,
                    onDismiss = {
                        castSheetOpenFraction = 0f
                        showCastSheet = false
                    },
                    onExpansionChanged = { fraction -> castSheetOpenFraction = fraction }
                )
            }
        }

        pendingSaveQueueOverlay?.let { overlay ->
            SaveQueueAsPlaylistSheet(
                songs = overlay.songs,
                defaultName = overlay.defaultName,
                onDismiss = { pendingSaveQueueOverlay = null },
                onConfirm = { name, selectedIds ->
                    overlay.onConfirm(name, selectedIds)
                    pendingSaveQueueOverlay = null
                }
            )
        }
        Trace.endSection() // End UnifiedPlayerSheet.Composition
    }
}

@Composable
private fun CastConnectingDialog() {
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(min = 220.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Mantén la app abierta",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Estamos transfiriendo la reproducción. Puede tardar unos segundos en desconectarse o reconectarse.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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
    isCastConnecting: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
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
        Box(contentAlignment = Alignment.Center) {
            SmartImage(
                model = song.albumArtUriString,
                contentDescription = "Carátula de ${song.title}",
                shape = CircleShape,
                targetSize = Size(150, 150),
                modifier = Modifier
                    .size(44.dp)
                    .alpha(if (isCastConnecting) 0.5f else 1f)
            )
            if (isCastConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = LocalMaterialTheme.current.onPrimaryContainer
                )
            }
        }
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
                text = if (isCastConnecting) "Connecting to device…" else song.title,
                style = titleStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer
            )
            AutoScrollingText(
                text = song.displayArtist,
                style = artistStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer
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
                    indication = indication,
                    enabled = !isCastConnecting
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPrevious()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_skip_previous_24),
                contentDescription = "Anterior",
                tint = LocalMaterialTheme.current.primary,
                modifier = Modifier.size(22.dp)
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
                    indication = indication,
                    enabled = !isCastConnecting
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
                    indication = indication,
                    enabled = !isCastConnecting
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
