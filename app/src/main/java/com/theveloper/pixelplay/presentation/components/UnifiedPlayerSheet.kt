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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.player.FullPlayerContent
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
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

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    // Granular collection for playerUiState fields used directly by UnifiedPlayerSheet or its main sub-components
    val currentPosition by remember {
        playerViewModel.playerUiState.map { it.currentPosition }.distinctUntilChanged()
    }.collectAsState(initial = 0L)
    val remotePosition by playerViewModel.remotePosition.collectAsState()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsState()
    val positionToDisplay = if (isRemotePlaybackActive) remotePosition else currentPosition
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
    LaunchedEffect(stablePlayerState.currentSong?.id) {
        if (stablePlayerState.currentSong != null) {
            prewarmFullPlayer = true
        }
    }
    LaunchedEffect(stablePlayerState.currentSong?.id, prewarmFullPlayer) {
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

    val showPlayerContentArea by remember {
        derivedStateOf { stablePlayerState.currentSong != null || isCastConnecting }
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

    data class PlayerSheetExpansionSnapshot(
        val fraction: Float,
        val fullPlayerContentAlpha: Float,
        val fullPlayerTranslationY: Float,
        val playerAreaElevation: Dp,
        val miniAlpha: Float,
        val dimLayerAlpha: Float,
        val bottomPadding: Dp,
        val contentAreaHeightDp: Dp,
        val totalSheetHeightWithShadowDp: Dp,
        val visualSheetTranslationY: Float,
        val topCornerRadius: Dp,
        val bottomCornerRadius: Dp,
        val horizontalPadding: Dp
    )

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

    val totalSheetHeightWhenContentCollapsedPx = remember(
        isPlayerSlotOccupied,
        hideMiniPlayer,
        miniPlayerAndSpacerHeightPx
    ) {
        if (isPlayerSlotOccupied && !hideMiniPlayer) miniPlayerAndSpacerHeightPx else 0f
    }

    val navBarElevation = 3.dp
    val shadowSpacePx = remember(density, navBarElevation) {
        with(density) { (navBarElevation * 8).toPx() }
    }

    val expansionSnapshot by remember(
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        currentSheetContentState,
        initialFullPlayerOffsetY,
        showPlayerContentArea,
        collapsedStateHorizontalPadding,
        containerHeight,
        density,
        isPlayerSlotOccupied,
        screenHeightPx,
        totalSheetHeightWhenContentCollapsedPx,
        shadowSpacePx,
        navBarStyle,
        navBarCornerRadius,
        isNavBarHidden,
        stablePlayerState.isPlaying,
        stablePlayerState.currentSong,
        swipeDismissProgress.value,
        sheetCollapsedTargetY,
        currentSheetTranslationY.value
    ) {
        derivedStateOf {
            val fraction = playerContentExpansionFraction.value
            val fullContentAlpha = (fraction - 0.25f).coerceIn(0f, 0.75f) / 0.75f
            val dimAlpha = if (predictiveBackCollapseProgress > 0f && currentSheetContentState == PlayerSheetState.EXPANDED) {
                lerp(fraction, 0f, predictiveBackCollapseProgress)
            } else {
                fraction
            }

            val actualCollapsedPadding = if (navBarStyle == NavBarStyle.FULL_WIDTH) 14.dp else collapsedStateHorizontalPadding
            val bottomPadding = if (predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
                lerp(0.dp, collapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else {
                0.dp
            }

            val contentHeightDp = if (showPlayerContentArea) {
                lerp(MiniPlayerHeight, containerHeight, fraction)
            } else {
                0.dp
            }

            val totalSheetHeightPx = if (isPlayerSlotOccupied) {
                lerp(
                    totalSheetHeightWhenContentCollapsedPx,
                    screenHeightPx,
                    fraction
                )
            } else {
                0f
            }
            val totalSheetHeightWithShadowDp = with(density) { (totalSheetHeightPx + shadowSpacePx).toDp() }

            val visualSheetTranslationY = currentSheetTranslationY.value * (1f - predictiveBackCollapseProgress) +
                    (sheetCollapsedTargetY * predictiveBackCollapseProgress)

            val collapsedCornerTarget = when {
                navBarStyle == NavBarStyle.FULL_WIDTH -> 32.dp
                isNavBarHidden -> 60.dp
                else -> navBarCornerRadius.dp
            }

            val topCornerRadius = if (showPlayerContentArea) {
                if (predictiveBackCollapseProgress > 0f && currentSheetContentState == PlayerSheetState.EXPANDED) {
                    lerp(0.dp, collapsedCornerTarget, predictiveBackCollapseProgress)
                } else {
                    lerp(collapsedCornerTarget, 0.dp, fraction)
                }
            } else {
                when {
                    navBarStyle == NavBarStyle.FULL_WIDTH -> 0.dp
                    isNavBarHidden -> 60.dp
                    else -> navBarCornerRadius.dp
                }
            }

            val collapsedRadius = if (isNavBarHidden) 60.dp else 12.dp
            val bottomCornerRadius = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                lerp(32.dp, 26.dp, fraction)
            } else {
                val calculatedNormally =
                    if (predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
                        lerp(26.dp, collapsedRadius, predictiveBackCollapseProgress)
                    } else {
                        if (showPlayerContentArea) {
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
                    fraction < 0.01f
                ) {
                    val baseCollapsedRadius = if (isNavBarHidden) 32.dp else 12.dp
                    lerp(baseCollapsedRadius, navBarCornerRadius.dp, swipeDismissProgress.value)
                } else {
                    calculatedNormally
                }
            }

            val horizontalPadding = when {
                predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED ->
                    lerp(0.dp, actualCollapsedPadding, predictiveBackCollapseProgress)

                showPlayerContentArea -> lerp(actualCollapsedPadding, 0.dp, fraction)
                else -> actualCollapsedPadding
            }

            PlayerSheetExpansionSnapshot(
                fraction = fraction,
                fullPlayerContentAlpha = fullContentAlpha,
                fullPlayerTranslationY = lerp(initialFullPlayerOffsetY, 0f, fullContentAlpha),
                playerAreaElevation = lerp(2.dp, 12.dp, fraction),
                miniAlpha = (1f - fraction * 2f).coerceIn(0f, 1f),
                dimLayerAlpha = dimAlpha,
                bottomPadding = bottomPadding,
                contentAreaHeightDp = contentHeightDp,
                totalSheetHeightWithShadowDp = totalSheetHeightWithShadowDp,
                visualSheetTranslationY = visualSheetTranslationY,
                topCornerRadius = topCornerRadius,
                bottomCornerRadius = bottomCornerRadius,
                horizontalPadding = horizontalPadding
            )
        }
    }

    var showQueueSheet by remember { mutableStateOf(false) }
    val queueSheetOffset = remember(screenHeightPx) { Animatable(screenHeightPx) }
    var queueSheetHeightPx by remember { mutableFloatStateOf(0f) }
    val queueHiddenOffsetPx by remember(expansionSnapshot.bottomPadding, queueSheetHeightPx, density) {
        derivedStateOf {
            val basePadding = with(density) { expansionSnapshot.bottomPadding.toPx() }
            if (queueSheetHeightPx == 0f) 0f else queueSheetHeightPx + basePadding
        }
    }
    val queueDragThresholdPx by remember(queueHiddenOffsetPx) {
        derivedStateOf { queueHiddenOffsetPx * 0.08f }
    }
    var pendingSaveQueueOverlay by remember { mutableStateOf<SaveQueueOverlayData?>(null) }
    var showCastSheet by remember { mutableStateOf(false) }
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
        scope.launch { animateQueueSheetInternal(targetExpanded) }
    }

    fun beginQueueDrag() {
        if (queueHiddenOffsetPx == 0f) return
        showQueueSheet = true
        scope.launch { queueSheetOffset.stop() }
    }

    fun dragQueueBy(dragAmount: Float) {
        if (queueHiddenOffsetPx == 0f) return
        val newOffset = (queueSheetOffset.value + dragAmount).coerceIn(0f, queueHiddenOffsetPx)
        scope.launch { queueSheetOffset.snapTo(newOffset) }
    }

    fun endQueueDrag(totalDrag: Float, velocity: Float) {
        if (queueHiddenOffsetPx == 0f) return
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

    val actuallyShowSheetContent = shouldShowSheet && (!internalIsKeyboardVisible || pendingSaveQueueOverlay != null)

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

    val playerShadowShape = remember(expansionSnapshot.topCornerRadius, expansionSnapshot.bottomCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = expansionSnapshot.topCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusTR = expansionSnapshot.topCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = expansionSnapshot.bottomCornerRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusBL = expansionSnapshot.bottomCornerRadius,
            smoothnessAsPercentTR = 60
        )
    }

    val isCollapsedState =
        rememberUpdatedState(currentSheetContentState == PlayerSheetState.COLLAPSED)

    val collapsedY = rememberUpdatedState(sheetCollapsedTargetY)
    val expandedY = rememberUpdatedState(sheetExpandedTargetY)
    val canShow = rememberUpdatedState(showPlayerContentArea)
    val miniH = rememberUpdatedState(miniPlayerContentHeightPx)
    val dens = rememberUpdatedState(LocalDensity.current) // opcional; útil para thresholds

    AnimatedVisibility(
        visible = showPlayerContentArea && playerContentExpansionFraction.value > 0f && (!internalIsKeyboardVisible || pendingSaveQueueOverlay != null),
        enter = fadeIn(animationSpec = tween(ANIMATION_DURATION_MS)),
        exit = fadeOut(animationSpec = tween(ANIMATION_DURATION_MS))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (isDarkTheme) Color.Black.copy(alpha = expansionSnapshot.dimLayerAlpha) else Color.White.copy(
                        alpha = expansionSnapshot.dimLayerAlpha
                    )
                )
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
                    translationY = expansionSnapshot.visualSheetTranslationY
                }
                .height(expansionSnapshot.totalSheetHeightWithShadowDp),
            shadowElevation = 0.dp,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = expansionSnapshot.bottomPadding)
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
                                .padding(horizontal = expansionSnapshot.horizontalPadding)
                                .height(expansionSnapshot.contentAreaHeightDp)
                                .graphicsLayer {
                                    translationX = offsetAnimatable.value
                                    scaleY = visualOvershootScaleY.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .shadow(
                                    elevation = expansionSnapshot.playerAreaElevation,
                                    shape = playerShadowShape,
                                    clip = false
                                )
                                .background(
                                    color = albumColorScheme.primaryContainer,
                                    shape = AbsoluteSmoothCornerShape(
                                        cornerRadiusTL = expansionSnapshot.topCornerRadius,
                                        smoothnessAsPercentBL = 60,
                                        cornerRadiusTR = expansionSnapshot.topCornerRadius,
                                        smoothnessAsPercentBR = 60,
                                        cornerRadiusBR = expansionSnapshot.bottomCornerRadius,
                                        smoothnessAsPercentTL = 60,
                                        cornerRadiusBL = expansionSnapshot.bottomCornerRadius,
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
                                    if (expansionSnapshot.miniAlpha > 0.01f) {
                                        Crossfade(
                                            targetState = albumColorScheme,
                                            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
                                            label = "miniPlayerColorScheme"
                                        ) { scheme ->
                                            CompositionLocalProvider(
                                                LocalMaterialTheme provides (scheme ?: MaterialTheme.colorScheme)
                                            ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.TopCenter)
                                                            .graphicsLayer {
                                                                alpha = expansionSnapshot.miniAlpha//miniPlayerAlpha
                                                            }
                                                    ) {
                                                    MiniPlayerContentInternal(
                                                        song = currentSongNonNull, // Use non-null version
                                                        cornerRadiusAlb = (expansionSnapshot.topCornerRadius.value * 0.5).dp,
                                                        isPlaying = stablePlayerState.isPlaying, // from top-level stablePlayerState
                                                        isCastConnecting = isCastConnecting,
                                                        onPlayPause = { playerViewModel.playPause() },
                                                        onPrevious = { playerViewModel.previousSong() },
                                                        onNext = { playerViewModel.nextSong() },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (expansionSnapshot.fullPlayerContentAlpha > 0f) {
                                        CompositionLocalProvider(
                                            LocalMaterialTheme provides (albumColorScheme
                                                ?: MaterialTheme.colorScheme)
                                        ) {
                                            val fullPlayerScale by remember(queueOpenFraction) {
                                                derivedStateOf {
                                                    lerp(
                                                        1f,
                                                        0.95f,
                                                        queueOpenFraction
                                                    )
                                                }
                                            }
                                            Box(modifier = Modifier.graphicsLayer {
                                                alpha = expansionSnapshot.fullPlayerContentAlpha
                                                translationY = expansionSnapshot.fullPlayerTranslationY
                                                scaleX = fullPlayerScale
                                                scaleY = fullPlayerScale
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
                        }
                    }

                    // Prewarm full player once per track to reduce first-open jank.
                    if (prewarmFullPlayer && stablePlayerState.currentSong != null) {
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
                                    currentSong = stablePlayerState.currentSong!!,
                                    currentPlaybackQueue = currentPlaybackQueue,
                                    currentQueueSourceName = currentQueueSourceName,
                                    isShuffleEnabled = stablePlayerState.isShuffleEnabled,
                                    repeatMode = stablePlayerState.repeatMode,
                                    expansionFraction = 1f,
                                    currentSheetState = PlayerSheetState.EXPANDED,
                                    carouselStyle = carouselStyle,
                                    playerViewModel = playerViewModel,
                                    currentPositionProvider = { positionToDisplay },
                                    isPlayingProvider = { stablePlayerState.isPlaying },
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
//                                queueSheetState = queueSheetState,
//                                isQueueSheetVisible = false,
                                    onPlayPause = playerViewModel::playPause,
                                    onSeek = playerViewModel::seekTo,
                                    onNext = playerViewModel::nextSong,
                                    onPrevious = playerViewModel::previousSong,
                                    onCollapse = {},
//                                onQueueSheetVisibilityChange = {},
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

                if (!internalIsKeyboardVisible) {
                    CompositionLocalProvider(
                        LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
                    ) {
                        Box {
                            QueueBottomSheet(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
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
                                queue = currentPlaybackQueue, // Use granular state
                                currentQueueSourceName = currentQueueSourceName, // Use granular state
                                currentSongId = stablePlayerState.currentSong?.id, // stablePlayerState is fine here
                                onDismiss = { animateQueueSheet(false) },
                                onPlaySong = { song ->
                                    playerViewModel.playSongs(
                                        currentPlaybackQueue, // Use granular state
                                        song,
                                        currentQueueSourceName // Use granular state
                                    )
                                },
                                onRemoveSong = { songId ->
                                    playerViewModel.removeSongFromQueue(
                                        songId
                                    )
                                },
                                onReorder = { from, to ->
                                    playerViewModel.reorderQueueItem(
                                        from,
                                        to
                                    )
                                },
                                repeatMode = stablePlayerState.repeatMode,
                                isShuffleOn = stablePlayerState.isShuffleEnabled,
                                onToggleRepeat = { playerViewModel.cycleRepeatMode() },
                                onToggleShuffle = { playerViewModel.toggleShuffle() },
                                onClearQueue = { playerViewModel.clearQueueExceptCurrent() },
                                activeTimerValueDisplay = playerViewModel.activeTimerValueDisplay.collectAsState().value,
                                playCount = playerViewModel.playCount.collectAsState().value,
                                isEndOfTrackTimerActive = playerViewModel.isEndOfTrackTimerActive.collectAsState().value,
                                onSetPredefinedTimer = { minutes ->
                                    playerViewModel.setSleepTimer(
                                        minutes
                                    )
                                },
                                onSetEndOfTrackTimer = { enable ->
                                    playerViewModel.setEndOfTrackTimer(
                                        enable
                                    )
                                },
                                onOpenCustomTimePicker = {
                                    Log.d("TimerOptions", "OpenCustomTimePicker clicked")
                                },
                                onCancelTimer = { playerViewModel.cancelSleepTimer() },
                                onCancelCountedPlay = playerViewModel::cancelCountedPlay,
                                onPlayCounter = playerViewModel::playCounted,
                                onRequestSaveAsPlaylist = { songs, defaultName, onConfirm ->
                                    launchSaveQueueOverlay(songs, defaultName, onConfirm)
                                },
                                onQueueDragStart = { beginQueueDrag() },
                                onQueueDrag = { dragQueueBy(it) },
                                onQueueRelease = { drag, velocity -> endQueueDrag(drag, velocity) }
                            )
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
                    onDismiss = { showCastSheet = false }
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
