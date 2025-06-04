package com.theveloper.pixelplay.presentation.components

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
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
import com.theveloper.pixelplay.presentation.navigation.BottomNavItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Definir un CompositionLocal para el tema del álbum
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

private val LocalMaterialTheme = staticCompositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

val MiniPlayerHeight = 64.dp
val PlayerSheetExpandedCornerRadius = 32.dp // Totalmente expandido -> sin esquinas
val PlayerSheetCollapsedCornerRadius = 32.dp // Cuando está colapsado -> forma de píldora
val CollapsedPlayerContentSpacerHeight = 6.dp
const val ANIMATION_DURATION_MS = 255 // Duración para animaciones con tween

/**
 * ColorPalette generada desde la portada del álbum
 * Basada en principios de Material You pero personalizada para nuestra app
 */
data class AlbumColorPalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color, // Añadido para contraste en secundario
    val tertiary: Color,
    val onTertiary: Color, // Añadido para contraste en terciario
    val surface: Color,
    val onSurface: Color,    // Añadido para contraste en superficie
    val surfaceVariant: Color, // Para elementos ligeramente distintos de la superficie
    val onSurfaceVariant: Color,
    val outline: Color,
    val gradient: List<Color>
)

// --- UnifiedPlayerSheet (Versión mejorada con gestos más sensibles y animaciones suaves) ---

@Composable
fun UnifiedPlayerSheet(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    initialTargetTranslationY: Float,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    collapsedStateBottomMargin: Dp = 0.dp,
    hideNavBar: Boolean = false
    // isKeyboardVisible: Boolean // Removed
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val playerUiState by playerViewModel.playerUiState.collectAsState()
    val currentSheetContentState by playerViewModel.sheetState.collectAsState()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsState()

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val screenHeightPx = remember(configuration) { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val miniPlayerContentHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }
    val navBarHeightPx = remember(density, NavBarPersistentHeight) { with(density) { NavBarPersistentHeight.toPx() } }
    val miniPlayerAndSpacerHeightPx = remember(density, MiniPlayerHeight, CollapsedPlayerContentSpacerHeight) { with(density) { (MiniPlayerHeight + CollapsedPlayerContentSpacerHeight).toPx() } }
    remember { with(density) { CollapsedPlayerContentSpacerHeight.toPx() } }

    val showPlayerContentArea by remember { derivedStateOf { stablePlayerState.currentSong != null } }

    val playerContentExpansionFraction = remember { Animatable(0f) }
    LaunchedEffect(showPlayerContentArea, currentSheetContentState) {
        val targetFraction = if (showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) 1f else 0f
        playerContentExpansionFraction.animateTo(
            targetFraction,
            animationSpec = tween(durationMillis = ANIMATION_DURATION_MS, easing = FastOutSlowInEasing)
        )
    }

    val playerContentAreaActualHeightPx by remember(showPlayerContentArea, playerContentExpansionFraction, screenHeightPx, miniPlayerContentHeightPx) {
        derivedStateOf {
            if (showPlayerContentArea) {
                lerp(miniPlayerContentHeightPx, screenHeightPx, playerContentExpansionFraction.value)
            } else { 0f }
        }
    }
    val playerContentAreaActualHeightDp = with(density) { playerContentAreaActualHeightPx.toDp() }

    // MEJORADO: Cálculo de altura total considerando navbar oculta
    val totalSheetHeightWhenContentCollapsedPx = remember(showPlayerContentArea, hideNavBar, miniPlayerAndSpacerHeightPx, navBarHeightPx) {
        val playerHeight = if (showPlayerContentArea) miniPlayerAndSpacerHeightPx else 0f
        val navHeight = if (hideNavBar) 0f else navBarHeightPx
        playerHeight + navHeight
    }

    val animatedTotalSheetHeightPx by remember(showPlayerContentArea, playerContentExpansionFraction, screenHeightPx, totalSheetHeightWhenContentCollapsedPx) {
        derivedStateOf {
            if (showPlayerContentArea) {
                lerp(totalSheetHeightWhenContentCollapsedPx, screenHeightPx, playerContentExpansionFraction.value)
            } else { navBarHeightPx }
        }
    }

    // NUEVO: Altura con espacio extra para la sombra
    val navBarElevation = if (!hideNavBar) 3.dp else 0.dp
    val shadowSpacePx = remember(density, navBarElevation) {
        with(density) { (navBarElevation * 8).toPx() } // Espacio extra para la sombra
    }

    val animatedTotalSheetHeightWithShadowPx by remember(animatedTotalSheetHeightPx, hideNavBar, shadowSpacePx) {
        derivedStateOf {
            if (hideNavBar) {
                animatedTotalSheetHeightPx // Sin navbar, no necesitamos espacio extra
            } else {
                animatedTotalSheetHeightPx + shadowSpacePx // Con navbar, añadimos espacio para la sombra
            }
        }
    }
    val animatedTotalSheetHeightWithShadowDp = with(density) { animatedTotalSheetHeightWithShadowPx.toDp() }

    with(density) { animatedTotalSheetHeightPx.toDp() }

    val sheetExpandedTargetY = 0f
    val sheetCollapsedTargetY = remember(screenHeightPx, totalSheetHeightWhenContentCollapsedPx, collapsedStateBottomMargin) {
        screenHeightPx - totalSheetHeightWhenContentCollapsedPx - with(density) { collapsedStateBottomMargin.toPx() }
    }

    val currentSheetTranslationY = remember { Animatable(initialTargetTranslationY) }
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

    // CORREGIDO: Animación suave de esquinas redondeadas del sheet principal con spring
    val overallSheetTopCornerRadiusTargetValue by remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        hideNavBar,
        predictiveBackCollapseProgress,
        currentSheetContentState
    ) {
        derivedStateOf {
            if (showPlayerContentArea) {
                // Check if a predictive back gesture is in progress on an expanded sheet
                if (predictiveBackCollapseProgress > 0f && currentSheetContentState == PlayerSheetState.EXPANDED) {
                    val expandedCorner = 0.dp // Corner radius when fully expanded
                    val collapsedCornerTarget = if (hideNavBar) 32.dp else PlayerSheetCollapsedCornerRadius
                    lerp(expandedCorner, collapsedCornerTarget, predictiveBackCollapseProgress)
                } else {
                    // Original logic when not in predictive back or sheet not expanded
                    val fraction = playerContentExpansionFraction.value
                    val expandedTarget = 0.dp
                    val collapsedTarget = if (hideNavBar) 32.dp else PlayerSheetCollapsedCornerRadius
                    lerp(collapsedTarget, expandedTarget, fraction)
                }
            } else {
                // No player content
                if (hideNavBar) 32.dp else PlayerSheetCollapsedCornerRadius
            }
        }
    }

    val overallSheetTopCornerRadius by animateDpAsState(
        targetValue = overallSheetTopCornerRadiusTargetValue, // Use the new target
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "SheetTopCornerRadius"
    )

    val sheetShape = RoundedCornerShape(
        topStart = overallSheetTopCornerRadius,
        topEnd = overallSheetTopCornerRadius,
        bottomStart = PlayerSheetCollapsedCornerRadius,
        bottomEnd = PlayerSheetCollapsedCornerRadius
    )

    // CORREGIDO: Animación de esquinas redondeadas basada en la fracción de expansión
    val playerContentActualBottomRadiusTargetValue by remember(
        hideNavBar,
        showPlayerContentArea,
        playerContentExpansionFraction,
        stablePlayerState.isPlaying,
        stablePlayerState.currentSong,
        predictiveBackCollapseProgress,
        currentSheetContentState
    ) {
        derivedStateOf {
            // Check if a predictive back gesture is in progress on an expanded sheet
            if (predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
                val expandedRadius = if (hideNavBar) 32.dp else 26.dp // Value when playerContentExpansionFraction is 1f (fully expanded)
                val collapsedRadiusTarget = if (hideNavBar) 32.dp else 12.dp // Value when playerContentExpansionFraction is 0f (collapsed)
                lerp(expandedRadius, collapsedRadiusTarget, predictiveBackCollapseProgress)
            } else {
                // Original logic when not in predictive back or sheet not expanded
                if (hideNavBar) {
                    32.dp
                } else if (showPlayerContentArea) {
                    val fraction = playerContentExpansionFraction.value
                    if (fraction < 0.2f) {
                        lerp(12.dp, 26.dp, (fraction / 0.2f).coerceIn(0f, 1f))
                    } else { // After 20% expansion or fully expanded
                        26.dp
                    }
                } else { // Player area not shown (mini-player state or no song)
                    if (!stablePlayerState.isPlaying || stablePlayerState.currentSong == null) {
                        PlayerSheetCollapsedCornerRadius // Or a specific value like 32.dp or 12.dp if that's the design
                    } else {
                        12.dp // Default collapsed mini-player bottom radius
                    }
                }
            }
        }
    }

    val playerContentActualBottomRadius by animateDpAsState(
        targetValue = playerContentActualBottomRadiusTargetValue, // Use the new target
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PlayerContentBottomRadius"
    )

    val navBarActualTopRadius by animateDpAsState(
        targetValue = if (showPlayerContentArea) {
            // Usar interpolación suave basada en la fracción de expansión
            val fraction = playerContentExpansionFraction.value
            if (fraction < 0.2f) {
                // Interpolar de 12dp a 0dp en los primeros 20% de la expansión
                lerp(12.dp, 18.dp, (fraction / 0.2f).coerceIn(0f, 1f))
            } else { 18.dp }
        } else { 12.dp },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "NavBarTopRadius"
    )

    val currentHorizontalPadding by remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        collapsedStateHorizontalPadding,
        predictiveBackCollapseProgress // Add this dependency
    ) {
        derivedStateOf {
            if (predictiveBackCollapseProgress > 0f && showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED) {
                // During predictive back (from expanded state), padding goes from 0.dp to collapsedStateHorizontalPadding
                lerp(0.dp, collapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else if (showPlayerContentArea) {
                // Normal calculation based on expansion fraction
                lerp(collapsedStateHorizontalPadding, 0.dp, playerContentExpansionFraction.value)
            } else {
                // Default when no content or not in predictive back
                collapsedStateHorizontalPadding
            }
        }
    }

    val currentDimLayerAlpha by remember(
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        currentSheetContentState // Add this dependency
    ) {
        derivedStateOf {
            val baseAlpha = playerContentExpansionFraction.value // Alpha when fully expanded is 1f, collapsed is 0f.
            if (predictiveBackCollapseProgress > 0f && currentSheetContentState == PlayerSheetState.EXPANDED) {
                // During predictive back (from expanded state), alpha goes from baseAlpha (should be ~1f) towards 0f.
                lerp(baseAlpha, 0f, predictiveBackCollapseProgress)
            } else {
                // Normal alpha based on expansion fraction.
                baseAlpha
            }
        }
    }

    var showQueueSheet by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isDraggingPlayerArea by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    var accumulatedDragYSinceStart by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler( // Removed 'keys' parameter
        enabled = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED && !isDragging
    ) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                playerViewModel.updatePredictiveBackCollapseFraction(backEvent.progress)
            }
            // On completion (back gesture committed by user)
            scope.launch {
                // 1. Capture the gesture's progress at the point of release/commit.
                //    This value is crucial for determining the starting point of the ensuing animations.
                //    At this stage, playerViewModel.predictiveBackCollapseFraction.value holds the latest
                //    progress reported by the gesture before it's forced to 1f.
                val progressAtRelease = playerViewModel.predictiveBackCollapseFraction.value

                // 2. Set the *current values* of the main Animatables to reflect their visual state
                //    at the moment the gesture was released. This makes subsequent animations start smoothly.

                // For currentSheetTranslationY:
                // It should be at the position dictated by progressAtRelease.
                val currentVisualY = lerp(sheetExpandedTargetY, sheetCollapsedTargetY, progressAtRelease)
                currentSheetTranslationY.snapTo(currentVisualY)

                // For playerContentExpansionFraction:
                // Its visual equivalent is inversely related to gesture progress (1.0 when progress is 0, 0.0 when progress is 1.0).
                val currentVisualExpansionFraction = (1f - progressAtRelease).coerceIn(0f, 1f)
                playerContentExpansionFraction.snapTo(currentVisualExpansionFraction)

                // 3. Now, signal the ViewModel to update the overall predictive fraction to 1 (full commit)
                //    and then trigger the collapse by changing the sheet's logical state.
                playerViewModel.updatePredictiveBackCollapseFraction(1f) // Visually signals "target is now fully collapsed"
                playerViewModel.collapsePlayerSheet() // Changes currentSheetContentState to PlayerSheetState.COLLAPSED

                // When currentSheetContentState changes to COLLAPSED, the LaunchedEffects watching it will trigger:
                // - playerContentExpansionFraction.animateTo(0f, ...) will animate from currentVisualExpansionFraction to 0f.
                // - currentSheetTranslationY.animateTo(sheetCollapsedTargetY, ...) will animate from currentVisualY to sheetCollapsedTargetY.
                // This should produce the desired continuous animation from the release point.

                // 4. Reset the overall predictive back progress state for the next gesture.
                //    This is done after the collapse has been initiated.
                playerViewModel.updatePredictiveBackCollapseFraction(0f)
            }
        } catch (e: CancellationException) {
            // On cancellation (back gesture cancelled by user)
            scope.launch {
                // Corrected Animatable usage:
                Animatable(playerViewModel.predictiveBackCollapseFraction.value).animateTo(
                    targetValue = 0f,
                    animationSpec = tween(ANIMATION_DURATION_MS)
                ) { // `this` is Animatable.AnimationScope<Float, AnimationVector1D>
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

    // NUEVO: Solo mostrar el Surface si hay contenido para mostrar
    val shouldShowSheet by remember(showPlayerContentArea, hideNavBar) {
        derivedStateOf { showPlayerContentArea || !hideNavBar }
    }

    var internalIsKeyboardVisible by remember { mutableStateOf(false) }

    // Observe IME visibility using snapshotFlow (recommended)
    val imeInsets = WindowInsets.ime
    LaunchedEffect(imeInsets, density) { // Relaunch if imeInsets object or density changes
        snapshotFlow { imeInsets.getBottom(density) > 0 }
            .collectLatest { isVisible ->
                internalIsKeyboardVisible = isVisible
                Log.d("UnifiedPlayerSheet", "Internal Keyboard Visible: $isVisible")
            }
    }

    val actuallyShowSheetContent = shouldShowSheet && !internalIsKeyboardVisible

    val currentAlbumColorSchemePair by playerViewModel.currentAlbumArtColorSchemePair.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val albumColorScheme = remember(currentAlbumColorSchemePair, isDarkTheme) {
        if (isDarkTheme) currentAlbumColorSchemePair?.dark else currentAlbumColorSchemePair?.light
    }

    // NUEVO: Elevation animada para el área del player
    val playerAreaElevation by animateDpAsState(
        targetValue = if (showPlayerContentArea) {
            val fraction = playerContentExpansionFraction.value
            // Elevation más alta cuando está expandido, menor cuando está colapsado
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

    // NUEVO: Formas para las sombras del player
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

    // Main sheet content logic:
    // The AnimatedVisibility that was here (using slideInVertically/slideOutVertically) is REMOVED.
    // It is REPLACED by this 'if' statement:
    if (actuallyShowSheetContent) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = visualSheetTranslationY }
                .height(animatedTotalSheetHeightWithShadowDp),
            shadowElevation = 0.dp,
            color = Color.Transparent
        ) {
            // The original Column and its content (player area, spacer, PlayerInternalNavigationBar)
            // remain structurally the same inside this Surface.
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Example of internal structure (should match existing content):
                if (showPlayerContentArea) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = currentHorizontalPadding)
                            .height(playerContentAreaActualHeightDp)
                            // NUEVO: Aplicar shadow antes del background
                            .shadow(
                                elevation = playerAreaElevation,
                                shape = playerShadowShape,
                                clip = false // No recortar la sombra
                            )
                            .background(
                                color = albumColorScheme?.primaryContainer
                                    ?: MaterialTheme.colorScheme.primaryContainer,
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
                            // NUEVO: Gestos aplicados solo al área del player
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
                                            // La traslación Y sigue directamente al dedo
                                            val newY = (currentSheetTranslationY.value + dragAmount)
                                                .coerceIn(
                                                    sheetExpandedTargetY - miniPlayerContentHeightPx * 0.2f,
                                                    sheetCollapsedTargetY + miniPlayerContentHeightPx * 0.2f
                                                )
                                            currentSheetTranslationY.snapTo(newY)

                                            // La fracción de expansión del contenido se calcula basada en la nueva posición Y
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

                                        // CORREGIDO: Umbrales aún más sensibles para evitar rebotes
                                        val minDragThresholdPx =
                                            with(density) { 5.dp.toPx() } // Reducido de 8dp a 5dp
                                        val velocityThresholdForInstantTrigger =
                                            150f // Reducido de 200f a 150f

                                        // CORREGIDO: Lógica de decisión que respeta la intención del gesto
                                        val targetContentState = when {
                                            // 1. Prioridad absoluta: Si hay movimiento mínimo, respetarlo SIEMPRE
                                            abs(accumulatedDragYSinceStart) > minDragThresholdPx -> {
                                                if (accumulatedDragYSinceStart < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                            }
                                            // 2. Si hay velocidad pero poco movimiento, considerar la velocidad
                                            abs(verticalVelocity) > velocityThresholdForInstantTrigger -> {
                                                if (verticalVelocity < 0) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                            }
                                            // 3. Solo si NO hay movimiento ni velocidad significativa, usar posición actual
                                            else -> {
                                                // Usar un umbral más conservador solo para gestos muy pequeños
                                                if (currentExpansionFraction > 0.5f) PlayerSheetState.EXPANDED else PlayerSheetState.COLLAPSED
                                            }
                                        }

                                        // Actualiza el estado del ViewModel y coordina la animación
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
                                                launch {
                                                    currentSheetTranslationY.animateTo(
                                                        targetValue = sheetCollapsedTargetY,
                                                        animationSpec = tween(
                                                            durationMillis = ANIMATION_DURATION_MS,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    )
                                                }
                                                launch {
                                                    playerContentExpansionFraction.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = tween(
                                                            durationMillis = ANIMATION_DURATION_MS,
                                                            easing = FastOutSlowInEasing
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
                            // MEJORADO: Click solo en área del player y cuando no se está dragando
                            .clickable(
                                enabled = true,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                playerViewModel.togglePlayerSheetState()
                            }
                    ) {
                        if (showPlayerContentArea) {
                            val currentSong = stablePlayerState.currentSong!!
                            val miniPlayerAlpha by remember { derivedStateOf { (1f - playerContentExpansionFraction.value * 2f).coerceIn(0f, 1f) } }
                            if (miniPlayerAlpha > 0.01f) {
                                // Aplicar el tema del álbum al mini player
                                CompositionLocalProvider(
                                    LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .graphicsLayer { alpha = miniPlayerAlpha }
                                    ) {
                                        MiniPlayerContentInternal(
                                            song = currentSong, isPlaying = stablePlayerState.isPlaying,
                                            onPlayPause = { playerViewModel.playPause() }, onNext = { playerViewModel.nextSong() },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            val fullPlayerAlpha by remember { derivedStateOf { playerContentExpansionFraction.value.pow(2) } }
                            if (fullPlayerAlpha > 0.01f) {
                                // Aplicar el tema del álbum al full player
                                CompositionLocalProvider(
                                    LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
                                ) {
                                    Box(modifier = Modifier.graphicsLayer { alpha = fullPlayerAlpha }) {
                                        FullPlayerContentInternal(
                                            //currentPosition = playerUiState.currentPosition,
                                            currentSong = stablePlayerState.currentSong,
                                            isPlaying = stablePlayerState.isPlaying,
                                            isShuffleEnabled = stablePlayerState.isShuffleEnabled,
                                            repeatMode = stablePlayerState.repeatMode,
                                            isFavorite = stablePlayerState.isCurrentSongFavorite,
                                            //totalDuration = stablePlayerState.totalDuration,
                                            onPlayPause = { playerViewModel.playPause() }, onSeek = { playerViewModel.seekTo(it) },
                                            onNext = { playerViewModel.nextSong() }, onPrevious = { playerViewModel.previousSong() },
                                            onCollapse = { playerViewModel.collapsePlayerSheet() }, expansionFraction = playerContentExpansionFraction.value,
                                            currentSheetState = currentSheetContentState, onShowQueueClicked = { showQueueSheet = true },
                                            onShuffleToggle = { playerViewModel.toggleShuffle() },      // ADDED
                                            onRepeatToggle = { playerViewModel.cycleRepeatMode() },    // ADDED
                                            onFavoriteToggle = { playerViewModel.toggleFavorite() },   // ADDED
                                            playerViewModel = playerViewModel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Spacer entre player y navigation bar (solo visible cuando colapsado y hay contenido de player)
                // This Spacer is relevant only when the NavBar is potentially visible and there's content.
                if (showPlayerContentArea && !hideNavBar) {
                    val spacerTargetHeight = lerp(
                        start = CollapsedPlayerContentSpacerHeight,
                        stop = 0.dp,
                        fraction = (playerContentExpansionFraction.value / 0.4f).coerceIn(0f, 1f)
                    )
                    val animatedSpacerHeight by animateDpAsState(
                        targetValue = spacerTargetHeight,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium), // Using a spring
                        label = "SpacerHeightAnimation"
                    )

                    if (animatedSpacerHeight > 0.1.dp) { // Use a small threshold to avoid composing for negligible heights
                        Spacer(
                            Modifier
                                .height(animatedSpacerHeight)
                                .fillMaxWidth()
                                .background(Color.Transparent) // Or its original background
                        )
                    }
                }

                // MEJORADO: Navigation bar sin gestos de drag
                if (!hideNavBar) {
                    val navBarHideFraction = if (showPlayerContentArea) playerContentExpansionFraction.value.pow(2) else 0f
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRouteValue = navBackStackEntry?.destination?.route
                    val rememberedCurrentRoute = remember(currentRouteValue) {
                        Log.d("RouteDebug", "rememberedCurrentRoute recalculated. New value: $currentRouteValue")
                        currentRouteValue
                    }

                    val actualShape = remember(playerContentActualBottomRadius, showPlayerContentArea) {
                        AbsoluteSmoothCornerShape(
                            cornerRadiusTL = playerContentActualBottomRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusTR = playerContentActualBottomRadius,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBL = PlayerSheetCollapsedCornerRadius,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBR = PlayerSheetCollapsedCornerRadius,
                            smoothnessAsPercentBL = 60
                        )
                    }

                    val conditionalShape = if (showPlayerContentArea) {
                        actualShape
                    } else {
                        CircleShape
                    }

                    val playerInternalNavBarModifier = remember {
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = currentHorizontalPadding)
                            .pointerInput(Unit) {
                                detectTapGestures { /* Permitir taps normales en nav items */ }
                            }
                    }

                    PlayerInternalNavigationBar(
                        navController = navController,
                        navItems = navItems,
                        containerShape = conditionalShape,
                        navBarElevation = navBarElevation,
                        isPlayerVisible = showPlayerContentArea,
                        currentRoute = rememberedCurrentRoute,
                        navBarHideFraction = navBarHideFraction,
                        topCornersRadiusDp = playerContentActualBottomRadius,
                        bottomCornersRadiusDp = PlayerSheetCollapsedCornerRadius,
                        navBarHeightPx = navBarHeightPx,
                        modifier = playerInternalNavBarModifier
                    )
                }
            }
        }
    }

    // Queue bottom sheet
    if (showQueueSheet && !internalIsKeyboardVisible) { // Use internalIsKeyboardVisible
        CompositionLocalProvider(
            LocalMaterialTheme provides (albumColorScheme ?: MaterialTheme.colorScheme)
        ) {
            QueueBottomSheet(
                queue = playerUiState.currentPlaybackQueue,
                currentSongId = stablePlayerState.currentSong?.id,
                onDismiss = { showQueueSheet = false },
                onPlaySong = { song ->
                    playerViewModel.playSongs(playerUiState.currentPlaybackQueue, song, playerUiState.currentQueueSourceNname)
                },
                onRemoveSong = { songId -> playerViewModel.removeSongFromQueue(songId) },
                onReorder = { from, to -> playerViewModel.reorderQueueItem(from, to) }
            )
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
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(start = 11.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song.albumArtUriString ?: R.drawable.rounded_album_24,
            contentDescription = "Carátula de ${song.title}",
            shape = CircleShape,
            modifier = Modifier
                .size(44.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp
                ),
                color = LocalMaterialTheme.current.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    letterSpacing = 0.sp
                ),
                color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        // Play/Pause button with morphing animation
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

        // Next button
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

// Enum para identificar qué botón fue presionado
private enum class ButtonType {
    NONE, PREVIOUS, PLAY_PAUSE, NEXT
}


@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerContentInternal(
    currentSong: Song?,
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
    playerViewModel: PlayerViewModel
) {
    val song = currentSong ?: return

    // RECOLECTAR LOS VALORES QUE CAMBIAN FRECUENTEMENTE AQUÍ UNA SOLA VEZ
    val currentPositionValue by remember(playerViewModel.playerUiState) {
        playerViewModel.playerUiState.map { it.currentPosition }
    }.collectAsStateWithLifecycle(initialValue = playerViewModel.playerUiState.value.currentPosition)

    val totalDurationValue by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.totalDuration }
    }.collectAsStateWithLifecycle(initialValue = playerViewModel.stablePlayerState.value.totalDuration)

    // CALCULAR LA FRACCIÓN DE PROGRESO AQUÍ
    val progressFractionValue = remember(currentPositionValue, totalDurationValue) {
        (currentPositionValue.coerceAtLeast(0).toFloat() /
                totalDurationValue.coerceAtLeast(1).toFloat())
    }.coerceIn(0f, 1f)

    // Definir la AnimationSpec estable para los controles de reproducción
    val stableControlAnimationSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    // Leer colores del tema una vez si no cambian con el tema del álbum aquí
    // Si sí cambian con el tema del álbum, LocalMaterialTheme.current es correcto.
    val controlOtherButtonsColor = LocalMaterialTheme.current.primary.copy(alpha = 0.15f)
    val controlPlayPauseColor = LocalMaterialTheme.current.primary
    val controlTintPlayPauseIcon = LocalMaterialTheme.current.onPrimary
    val controlTintOtherIcons = LocalMaterialTheme.current.primary

    //Log.d("Recomposition", "FullPlayerContentInternal RECOMPOSED - Song: ${song.title}, IsPlaying: $isPlaying, ExpFrac: $expansionFraction")

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.alpha(expansionFraction.coerceIn(0f, 1f)),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                    actionIconContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                    navigationIconContentColor = LocalMaterialTheme.current.onPrimaryContainer
                ),
                title = {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.padding(start = 14.dp),
                        onClick = onCollapse
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                            contentDescription = "Colapsar"
                        )
                    }
                },
                actions = {
                    // Botón para mostrar la cola
                    IconButton(
                        modifier = Modifier.padding(end = 14.dp),
                        onClick = onShowQueueClicked
                    ) {
                        Icon(painterResource(R.drawable.rounded_queue_music_24), "Cola de reproducción")
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
            // Album Cover - Asegurando suficiente espacio
            song.albumArtUriString?.let {
                OptimizedAlbumArt(
                    uri = it,
                    title = song.title,
                    expansionFraction = expansionFraction
                )
            }

            // Song Info - Asegurando que no ocupe demasiado espacio
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(vertical = lerp(2.dp, 10.dp, expansionFraction))
                    .fillMaxWidth(0.9f) // Limita el ancho del texto
                    .graphicsLayer {
                        alpha = expansionFraction
                        translationY = (1f - expansionFraction) * 24f
                    }
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded
                        //letterSpacing = (-0.5).sp
                    ),
                    color = LocalMaterialTheme.current.onPrimaryContainer,
                    maxLines = 1, // Forzamos una sola línea
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.sp),
                    color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Progress Bar and Times - Asegurando altura fija
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = lerp(2.dp, 10.dp, expansionFraction))
                    .graphicsLayer {
                        alpha = expansionFraction
                    }
                    .heightIn(min = 70.dp) // Altura mínima fija para este componente
            ) {
                // IMPLEMENTACIÓN DE WAVY SLIDER CON ESTADO DE REPRODUCCIÓN
                val onSliderValueChange = remember(onSeek, totalDurationValue) { { frac: Float -> onSeek((frac * totalDurationValue).roundToLong()) } }
                WavyMusicSlider(
                    value = progressFractionValue,
                    onValueChange = onSliderValueChange,
                    onValueChangeFinished = {
                        // Opcional: acciones cuando el usuario termina de arrastrar
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    trackHeight = 6.dp,
                    thumbRadius = 8.dp,
                    activeTrackColor = LocalMaterialTheme.current.primary,
                    inactiveTrackColor = LocalMaterialTheme.current.primary.copy(alpha = 0.2f),
                    thumbColor = LocalMaterialTheme.current.primary,
                    waveFrequency = 0.08f,
                    isPlaying = (isPlaying && currentSheetState == PlayerSheetState.EXPANDED) // Pasamos el estado de reproducción
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(currentPositionValue),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        formatDuration(totalDurationValue),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Espaciador flexible para empujar los controles hacia abajo

            // Playback Controls - Contenedor con altura fija
            //Log.d("Recomposition", "FullPlayerContentInternal - Calling AnimatedPlaybackControls, isPlaying: $isPlaying")
            AnimatedPlaybackControls(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                isPlaying = isPlaying,
                onPrevious = onPrevious,     // Estas deben ser referencias estables
                onPlayPause = onPlayPause,   // o lambdas recordadas
                onNext = onNext,           //
                height = 80.dp,
                // Pasar la AnimationSpec estable
                pressAnimationSpec = stableControlAnimationSpec,
                releaseDelay = 220L, // Más corto
                // Pasar colores estables (si los defines fuera como hice arriba)
                // o dejar que AnimatedPlaybackControls los tome del tema si está bien
                colorOtherButtons = controlOtherButtonsColor,
                colorPlayPause = controlPlayPauseColor,
                tintPlayPauseIcon = controlTintPlayPauseIcon,
                tintOtherIcons = controlTintOtherIcons
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom toggle row con altura fija
            BottomToggleRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp, max = 88.dp) // Altura mínima fija
                    .padding(horizontal = 26.dp, vertical = 8.dp),
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                isFavorite = isFavorite,
                onShuffleToggle = onShuffleToggle,    // CHANGED
                onRepeatToggle = onRepeatToggle,      // CHANGED
                onFavoriteToggle = onFavoriteToggle   // CHANGED
            )
        }
    }
}

// Define una AnimationSpec estable que se pueda usar como valor por defecto.
// Puedes poner esto a nivel de archivo o como un objeto companion.
private val DefaultPlaybackControlAnimationSpec: AnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    // Parámetros configurables
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>, //= DefaultPlaybackControlAnimationSpec,//tween(durationMillis = 150, easing = FastOutSlowInEasing),
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 70.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = LocalMaterialTheme.current.primary.copy(alpha = 0.15f),
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    tintOtherIcons: Color = LocalMaterialTheme.current.primary,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp
) {
    // Estado interno: último botón tocado
    var lastClicked by remember { mutableStateOf<ButtonType?>(null) }

    // Efecto para resetear lastClicked tras la animación
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
            //.background(color = Color.Red)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Helper para calcular peso dinámico
            fun weightFor(button: ButtonType): Float = when (lastClicked) {
                button   -> expansionWeight
                null     -> baseWeight
                else     -> compressionWeight
            }

            // --- Previous Button ---
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

            // --- Play/Pause Button ---
            val playWeight by animateFloatAsState(
                targetValue = weightFor(ButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec
            )
            // Animar el corner radius según isPlaying
            val playCorner by animateDpAsState(
                targetValue = if (!isPlaying) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy, // O MediumBouncy si quieres un leve rebote
                    stiffness = Spring.StiffnessMedium // Ajusta según necesites
                ),
                label = "PlayCornerRadiusAnim" // Añadir label
            )
//            val playCorner by animateDpAsState(
//                targetValue = if (!isPlaying) playPauseCornerPlaying else playPauseCornerPaused,
//                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
//            )
            val playShape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = playCorner,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = playCorner,
                smoothnessAsPercentBL = 60,
                cornerRadiusTL = playCorner,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = playCorner,
                smoothnessAsPercentTL = 60
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

            // --- Next Button ---
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
    // Parámetros de estilo
    val rowCorners = 60.dp
    val inactiveBg = LocalMaterialTheme.current.primary.copy(alpha = 0.08f)

    // Fonde de la fila segmentada
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
            // Composable reutilizable
            val commonModifier = Modifier.weight(1f)

            // Shuffle
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
            // Repeat
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
            // Favorite
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
    // Animación de color de fondo
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 250)
    )
    // Animación de radio de esquina
    val cornerRadius by animateDpAsState(
        targetValue = if (active) activeCornerRadius else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            //.size(buttonSize)
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