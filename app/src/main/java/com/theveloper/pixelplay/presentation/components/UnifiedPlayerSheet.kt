package com.theveloper.pixelplay.presentation.components

import android.app.Activity
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.utils.formatDuration
import com.theveloper.pixelplay.utils.luminance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

val MiniPlayerHeight = 64.dp
val PlayerSheetCornerRadius = 38.dp // Radio para estado expandido
val PillCornerRadius = MiniPlayerHeight / 2 // Radio para estado colapsado (píldora)

// Constantes para umbrales de deslizamiento - ajustadas para mayor sensibilidad
private const val EXPANDED_DRAG_THRESHOLD_PERCENTAGE = 0f
private const val COLLAPSED_DRAG_THRESHOLD_PERCENTAGE = 0f
private const val VELOCITY_THRESHOLD = 250f  // Umbral de velocidad para gestos rápidos

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPlayerSheet(
    playerViewModel: PlayerViewModel,
    initialTargetTranslationY: Float,
    collapsedStateHorizontalPadding: Dp = 12.dp, // Tu valor
    collapsedStateBottomMargin: Dp // Cambiado a Dp
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val playerUiState by playerViewModel.playerUiState.collectAsState() // Para currentPosition y lavaLampColors
    val currentPosition by remember { derivedStateOf { playerUiState.currentPosition } }
    val lavaLampColorsFromVM by remember { derivedStateOf { playerUiState.lavaLampColors } }

    val currentSheetState by playerViewModel.sheetState.collectAsState()
    val bottomBarHeightPx by playerViewModel.bottomBarHeight.collectAsState()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsState()

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val currentPlayersTheme = MaterialTheme.colorScheme // Este es el tema específico del reproductor
    val view = LocalView.current
    val context = LocalContext.current
    val activity = LocalActivity.current as ComponentActivity
    val window = activity.window

    val screenHeightPx = remember(configuration) { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val miniPlayerHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }

    val collapsedTargetY = remember(screenHeightPx, miniPlayerHeightPx, bottomBarHeightPx, collapsedStateBottomMargin) {
        screenHeightPx - miniPlayerHeightPx - bottomBarHeightPx - with(density) { collapsedStateBottomMargin.toPx() }
    }
    val expandedTargetY = 0f

    val currentTranslationY = remember { Animatable(initialTargetTranslationY) }

    // Efecto para animar a la posición de reposo cuando el estado cambia
    LaunchedEffect(currentSheetState, collapsedTargetY, expandedTargetY) {
        val targetY = if (currentSheetState == PlayerSheetState.EXPANDED) expandedTargetY else collapsedTargetY
        currentTranslationY.animateTo(
            targetValue = targetY,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy, // Sin rebote para el snap
                stiffness = Spring.StiffnessMedium // Velocidad moderada
            )
        )
    }

    val dm = isSystemInDarkTheme()

    DisposableEffect(currentSheetState, currentPlayersTheme.surface, dm) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            when (currentSheetState) {
                PlayerSheetState.EXPANDED -> {
                    // Player is expanded, status bar icons should contrast with player's surface
                    val isPlayerSurfaceLight = currentPlayersTheme.surface.luminance() > 0.5f
                    insetsController.isAppearanceLightStatusBars = !isPlayerSurfaceLight
                }
                PlayerSheetState.COLLAPSED -> {
                    // Player is collapsed, status bar icons should contrast with main app's theme (which is system theme here)
                    // This will be effectively controlled by the main PixelPlayTheme's SideEffect
                    // We might need to explicitly set it to the system's preference if the main theme doesn't cover it when this is visible.
                    // For now, let's assume the main theme handles the collapsed state appearance.
                    // Or, more robustly:
                    insetsController.isAppearanceLightStatusBars = !dm
                }
            }
        }
        onDispose {
            // Cuando el sheet se va, la MainActivity (o el tema global) debería restaurar el color de la status bar.
            // Si no, podríamos necesitar resetearlo aquí al estado del tema del sistema.
            // (context as? Activity)?.window?.let { window ->
            //     WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !systemIsDarkTheme
            // }
        }
    }

    // `visualTranslationY` tiene en cuenta el progreso del gesto predictivo de retroceso
    val visualTranslationY by remember {
        derivedStateOf {
            currentTranslationY.value * (1f - predictiveBackCollapseProgress) +
                    (collapsedTargetY * predictiveBackCollapseProgress)
        }
    }

    // `expansionFraction` (0f = colapsado, 1f = expandido) se deriva de la posición visual
    val expansionFraction by remember {
        derivedStateOf {
            ((collapsedTargetY - visualTranslationY) / (collapsedTargetY - expandedTargetY).coerceAtLeast(1f))
                .coerceIn(0f, 1f)
        }
    }

    // Altura animada basada en la fracción de expansión
    val animatedHeight by remember {
        derivedStateOf { lerp(MiniPlayerHeight, configuration.screenHeightDp.dp, expansionFraction) }
    }

    // Animación de las esquinas y padding horizontal
    val cornerRadius by remember { derivedStateOf { lerp(PillCornerRadius, PlayerSheetCornerRadius, expansionFraction) } }
    val bottomCornerRadiusValue by remember { derivedStateOf { lerp(12.dp, PlayerSheetCornerRadius, expansionFraction) } } // Fondo plano cuando está expandido
    val horizontalPadding by remember { derivedStateOf { lerp(collapsedStateHorizontalPadding, 0.dp, expansionFraction) } }


    var showQueueSheet by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) } // Tu estado de dragging
    var dragVelocity by remember { mutableFloatStateOf(0f) } // Tu estado de velocidad

    val screenSizePx = with(LocalDensity.current) { Size(configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx()) }


    PredictiveBackHandler(enabled = currentSheetState == PlayerSheetState.EXPANDED && !isDragging) { progressFlow: Flow<BackEventCompat> ->
        try {
            var currentVisualYBeforeCommitOrCancel = visualTranslationY // Usar la visual actual
            progressFlow.collect { backEvent ->
                playerViewModel.updatePredictiveBackCollapseFraction(backEvent.progress)
                // Actualizar la posición visual "preview" durante el gesto
                currentVisualYBeforeCommitOrCancel = currentTranslationY.value * (1f - backEvent.progress) +
                        (collapsedTargetY * backEvent.progress)
            }
            // Gesture committed
            scope.launch {
                currentTranslationY.snapTo(currentVisualYBeforeCommitOrCancel) // Snap a donde estaba visualmente
                playerViewModel.collapsePlayerSheet() // Esto cambiará el estado y activará el LaunchedEffect
            }
        } catch (e: CancellationException) {
            // Gesture cancelled
            val currentVisualYAtCancel = currentTranslationY.value * (1f - playerViewModel.predictiveBackCollapseFraction.value) +
                    (collapsedTargetY * playerViewModel.predictiveBackCollapseFraction.value)
            scope.launch {
                currentTranslationY.snapTo(currentVisualYAtCancel)
                playerViewModel.expandPlayerSheet()
                Animatable(playerViewModel.predictiveBackCollapseFraction.value).animateTo(0f, spring()) {
                    playerViewModel.updatePredictiveBackCollapseFraction(this.value)
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = visualTranslationY } // Aplicar la traslación animada
            .padding(horizontal = horizontalPadding) // Padding horizontal animado
            .height(animatedHeight) // Altura animada
            .pointerInput(currentSheetState, collapsedTargetY, expandedTargetY) { // Claves correctas
                var dragStartOffsetY = 0f // Offset del puntero relativo al inicio del drag
                var initialSheetYOnDragStart = 0f // Posición Y del sheet al inicio del drag

                detectVerticalDragGestures(
                    onDragStart = {
                        scope.launch { currentTranslationY.stop() } // Detener animación en curso
                        isDragging = true
                        initialSheetYOnDragStart = currentTranslationY.value
                        dragStartOffsetY = 0f // Resetear
                        dragVelocity = 0f
                        // lastDragPosition y lastDragTime se pueden manejar dentro de onVerticalDrag
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragStartOffsetY += dragAmount // Acumular el delta del puntero

                        // Calcular la nueva posición Y del sheet basada en su posición inicial + el arrastre acumulado
                        var newSheetY = initialSheetYOnDragStart + dragStartOffsetY

                        // Aplicar resistencia (tu lógica)
                        if (currentSheetState == PlayerSheetState.EXPANDED && dragStartOffsetY > 0) { // Arrastrando hacia abajo
                            val resistance = 0.75f - (dragStartOffsetY / screenSizePx.height * 0.35f).coerceIn(0f, 0.5f)
                            newSheetY = initialSheetYOnDragStart + (dragStartOffsetY * resistance)
                        } else if (currentSheetState == PlayerSheetState.COLLAPSED && dragStartOffsetY < 0) { // Arrastrando hacia arriba
                            val resistance = 0.65f
                            newSheetY = initialSheetYOnDragStart + (dragStartOffsetY * resistance)
                        }

                        scope.launch {
                            currentTranslationY.snapTo(
                                newSheetY.coerceIn(
                                    expandedTargetY - miniPlayerHeightPx * 0.1f, // Límite de overdrag
                                    collapsedTargetY + miniPlayerHeightPx * 0.1f
                                )
                            )
                        }
                        // Cálculo de velocidad (opcional, si la librería no lo da o necesitas más control)
                        // dragVelocity = ... (tu cálculo de velocidad)
                    },
                    onDragEnd = {
                        isDragging = false
                        val currentY = currentTranslationY.value
                        // Determinar si se debe cambiar de estado basado en la posición y velocidad
                        val targetState = if (currentSheetState == PlayerSheetState.EXPANDED) {
                            if (currentY > expandedTargetY + (screenHeightPx * EXPANDED_DRAG_THRESHOLD_PERCENTAGE) || dragVelocity > VELOCITY_THRESHOLD) {
                                PlayerSheetState.COLLAPSED
                            } else {
                                PlayerSheetState.EXPANDED
                            }
                        } else { // COLLAPSED
                            if (currentY < collapsedTargetY - (screenHeightPx * COLLAPSED_DRAG_THRESHOLD_PERCENTAGE) || dragVelocity < -VELOCITY_THRESHOLD) {
                                PlayerSheetState.EXPANDED
                            } else {
                                PlayerSheetState.COLLAPSED
                            }
                        }
                        if (targetState == PlayerSheetState.EXPANDED) playerViewModel.expandPlayerSheet()
                        else playerViewModel.collapsePlayerSheet()
                        // El LaunchedEffect se encargará de animar a la posición de reposo.
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isDragging) { // Solo alternar si no se está arrastrando
                    playerViewModel.togglePlayerSheetState()
                }
            },
        // Usar tu AbsoluteSmoothCornerShape o RoundedCornerShape estándar
        shape = AbsoluteSmoothCornerShape( // Tu forma
            cornerRadiusTR = cornerRadius, smoothnessAsPercentTL = 60,
            cornerRadiusTL = cornerRadius, smoothnessAsPercentTR = 60,
            cornerRadiusBR = bottomCornerRadiusValue, smoothnessAsPercentBR = 60,
            cornerRadiusBL = bottomCornerRadiusValue, smoothnessAsPercentBL = 60
        ),
        color = Color.Transparent, // El fondo lo da el Box interno
        tonalElevation = lerp(2.dp, 8.dp, expansionFraction),
        shadowElevation = lerp(4.dp, 14.dp, expansionFraction) // Tu valor
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip( // Clip interno
                AbsoluteSmoothCornerShape(
                    cornerRadiusTR = cornerRadius, smoothnessAsPercentTL = 60,
                    cornerRadiusTL = cornerRadius, smoothnessAsPercentTR = 60,
                    cornerRadiusBR = bottomCornerRadiusValue, smoothnessAsPercentBR = 60,
                    cornerRadiusBL = bottomCornerRadiusValue, smoothnessAsPercentBL = 60
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.primary)
            )


            // Contenido
            if (stablePlayerState.currentSong != null) {
                val miniPlayerAlpha by remember { derivedStateOf { (1f - expansionFraction * 1.5f).coerceIn(0f, 1f) } }
                if (miniPlayerAlpha > 0.01f) {
                    Box(modifier = Modifier.alpha(miniPlayerAlpha)) {
                        MiniPlayerContentInternal(
                            song = stablePlayerState.currentSong!!,
                            isPlaying = stablePlayerState.isPlaying,
                            onPlayPause = { playerViewModel.playPause() },
                            onNext = { playerViewModel.nextSong() },
                            //colorPalette = albumColorPalette, // Pasar tu paleta
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                val fullPlayerAlpha by remember { derivedStateOf { expansionFraction.pow(0.6f) } }
                if (fullPlayerAlpha > 0.01f) {
                    Box(modifier = Modifier.alpha(fullPlayerAlpha)) {
                        FullPlayerContentInternal(
                            stablePlayerState = stablePlayerState,
                            playerViewModel = playerViewModel,
                            expansionFraction = expansionFraction,
                            onShowQueueClicked = { showQueueSheet = true },
                            uiState = playerUiState,
                            onPlayPause = { playerViewModel.playPause() },
                            onSeek = { playerViewModel.seekTo(it) },
                            onNext = { playerViewModel.nextSong() },
                            onPrevious = { playerViewModel.previousSong() },
                            onCollapse = { playerViewModel.collapsePlayerSheet() },
                            currentSheetState = currentSheetState
                            //colorPalette = albumColorPalette // Pasar tu paleta
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay canción seleccionada",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    if (showQueueSheet) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedPlayerSheetOld(
    playerViewModel: PlayerViewModel,
    initialTargetTranslationY: Float,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    collapsedStateBottomMargin: Float
) {
    val uiState by playerViewModel.playerUiState.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val currentPosition by remember { derivedStateOf { playerViewModel.playerUiState.value.currentPosition } }
    val currentSheetState by playerViewModel.sheetState.collectAsState()
    val bottomBarHeightPx by playerViewModel.bottomBarHeight.collectAsState()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsState()

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val view = LocalView.current
    val activity = LocalActivity.current as ComponentActivity
    val window = activity.window

    val screenHeightPx by remember(configuration) {
        mutableFloatStateOf(density.run { configuration.screenHeightDp.dp.toPx() })
    }
//    val screenHeightPx = remember(configuration) { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val miniPlayerHeightPx by remember {
        mutableFloatStateOf(density.run { MiniPlayerHeight.toPx() })
    }
//    val miniPlayerHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }

    val collapsedY by remember(screenHeightPx, miniPlayerHeightPx, bottomBarHeightPx) {
        mutableStateOf(screenHeightPx - miniPlayerHeightPx - bottomBarHeightPx - collapsedStateBottomMargin)
    }
    //val collapsedTranslationY = screenHeightPx - miniPlayerHeightPx - bottomBarHeightPx - collapsedStateBottomMargin
    val expandedTranslationY = 0f

    // 1) Single transition for sheet state
    val transition = updateTransition(targetState = currentSheetState, label = "sheetTransition")

    // 2) Animated vertical offset
    val offsetY by transition.animateFloat(
        transitionSpec = { spring(stiffness = 600f, dampingRatio = Spring.DampingRatioNoBouncy) },
        label = "offsetY"
    ) { state ->
        if (state == PlayerSheetState.EXPANDED) 0f else collapsedY
    }

    // 3) Rounded corners animation
    val cornerRadius by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) },
        label = "cornerRadius"
    ) { state ->
        if (state == PlayerSheetState.EXPANDED) 34.dp else MiniPlayerHeight / 2
    }

    // 4) Horizontal padding animation
    val horizontalPadding by transition.animateDp(
        transitionSpec = { tween(durationMillis = 200) },
        label = "horizontalPadding"
    ) { state ->
        if (state == PlayerSheetState.EXPANDED) 0.dp else collapsedStateHorizontalPadding
    }

    // 5) Derived expansion fraction
    val expansionFraction by remember(offsetY, collapsedY) {
        derivedStateOf {
            ((collapsedY - offsetY) / collapsedY).coerceIn(0f, 1f)
        }
    }

    // 7) Animated height derived from offset
    val animatedHeight by remember(offsetY) {
        derivedStateOf {
            with(density) {
                lerp(
                    start = screenHeightPx.toDp(),
                    stop = MiniPlayerHeight,
                    fraction = ((collapsedY - offsetY) / collapsedY).coerceIn(0f,1f)
                )
            }
        }
    }

    val currentTranslationYAnimatable = remember { Animatable(initialTargetTranslationY) }

    var showQueueSheet by remember { mutableStateOf(false) } // Estado para el BottomSheet de la cola

    // Update the resting position of currentTranslationYAnimatable
    LaunchedEffect(currentSheetState, collapsedY, expandedTranslationY) {
//    LaunchedEffect(currentSheetState, collapsedTranslationY, expandedTranslationY) {
        val targetY = if (currentSheetState == PlayerSheetState.EXPANDED) expandedTranslationY else collapsedY
//        val targetY = if (currentSheetState == PlayerSheetState.EXPANDED) expandedTranslationY else collapsedTranslationY
        if (abs(currentTranslationYAnimatable.value - targetY) > 0.5f) {
            currentTranslationYAnimatable.animateTo(
                targetValue = targetY,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = 600f//Spring.StiffnessMediumLow
                )
            )
        } else if (!currentTranslationYAnimatable.isRunning) {
            currentTranslationYAnimatable.snapTo(targetY)
        }
    }

    val dm = isSystemInDarkTheme()

    DisposableEffect(currentSheetState) {
        // true → iconos oscuros (sobre fondo claro)
        // false → iconos claros (sobre fondo oscuro)
        val themeIsDark = dm
        val darkIcons = when (currentSheetState) {
            PlayerSheetState.EXPANDED -> themeIsDark   // invertimos: si sistema oscuro, usamos íconos oscuros → fondo claro
            else                       -> !themeIsDark // al colapsar: íconos normales según tema
        }
        WindowCompat.getInsetsController(window, view)
            .isAppearanceLightStatusBars = darkIcons
        onDispose { /* opcional: aquí podrías forzar otro valor si lo necesitas */ }
    }

//    val visualTranslationY by remember {
//        derivedStateOf {
//            currentTranslationYAnimatable.value * (1f - predictiveBackCollapseProgress) +
//                    (collapsedTranslationY * predictiveBackCollapseProgress)
//        }
//    }

//    val animatedHeightPx by remember {
//        derivedStateOf {
//            lerp(
//                start = screenHeightPx, stop = miniPlayerHeightPx,
//                fraction = ((visualTranslationY - expandedTranslationY) / (collapsedTranslationY - expandedTranslationY).coerceAtLeast(1f)).coerceIn(0f, 1f)
//            )
//        }
//    }
    //val animatedHeight = with(density) { animatedHeightPx.toDp() }

    // 4. Simplifica el cálculo de expansionFraction con remember y derivedStateOf
//    val expansionFraction by remember {
//        derivedStateOf {
//            ((collapsedTranslationY - visualTranslationY) / (collapsedTranslationY - expandedTranslationY).coerceAtLeast(1f))
//                .coerceIn(0f, 1f)
//        }
//    }

    var isDragging by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }

    // More rounded corners for all states, matching the design in the images
    val topCornerTarget = if (currentSheetState == PlayerSheetState.EXPANDED && predictiveBackCollapseProgress == 0f) 34.dp else MiniPlayerHeight / 2
    val bottomCornerTarget = if (currentSheetState == PlayerSheetState.EXPANDED && predictiveBackCollapseProgress == 0f) 34.dp else MiniPlayerHeight / 2

    val animatedTopCorner = topCornerTarget
    val animatedBottomCorner = bottomCornerTarget

    val dynamicHorizontalPadding = lerp(0.dp, collapsedStateHorizontalPadding, 1f - expansionFraction)

    // Usar los colores del tema activo para el gradiente del LavaLamp
    val currentColorScheme = MaterialTheme.colorScheme
    val lavaLampGradientColors = remember(currentColorScheme, uiState.lavaLampColors) {
        uiState.lavaLampColors.ifEmpty { listOf(currentColorScheme.primary, currentColorScheme.secondary, currentColorScheme.tertiary) }
    }

    val gradientColors = listOf(
        Color.Transparent,
        Color.Transparent,
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
    )

    //val infiniteTransition = rememberInfiniteTransition(label = "gradientTransitionUnified")
//    val gradientPosition by infiniteTransition.animateFloat(
//        initialValue = 0f, targetValue = 1f,
//        animationSpec = infiniteRepeatable(animation = tween(24000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
//        label = "gradientMovementUnified"
//    )
    val screenSizePx = with(LocalDensity.current) { Size(configuration.screenWidthDp.dp.toPx(), configuration.screenHeightDp.dp.toPx()) }

    PredictiveBackHandler(enabled = currentSheetState == PlayerSheetState.EXPANDED && !isDragging) { progressFlow: Flow<BackEventCompat> ->
        try {
            var currentVisualYBeforeCommitOrCancel = 0f
            progressFlow.collect { backEvent ->
                playerViewModel.updatePredictiveBackCollapseFraction(backEvent.progress)
                currentVisualYBeforeCommitOrCancel = currentTranslationYAnimatable.value * (1f - backEvent.progress) +
                        (collapsedY * backEvent.progress)
//                        (collapsedTranslationY * backEvent.progress)
            }
            // Gesture committed
            scope.launch {
                currentTranslationYAnimatable.snapTo(currentVisualYBeforeCommitOrCancel)
                playerViewModel.collapsePlayerSheet()
            }
        } catch (e: CancellationException) {
            // Gesture cancelled
            val currentVisualYAtCancel = currentTranslationYAnimatable.value * (1f - playerViewModel.predictiveBackCollapseFraction.value) +
                    (collapsedY * playerViewModel.predictiveBackCollapseFraction.value)
//                    (collapsedTranslationY * playerViewModel.predictiveBackCollapseFraction.value)
            scope.launch {
                currentTranslationYAnimatable.snapTo(currentVisualYAtCancel)
                playerViewModel.expandPlayerSheet()
                // Animate predictive back fraction back to 0
                Animatable(playerViewModel.predictiveBackCollapseFraction.value).animateTo(0f, spring()) {
                    playerViewModel.updatePredictiveBackCollapseFraction(this.value)
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(x = 0, y = offsetY.roundToInt()) }  // uses one layout modifier :contentReference[oaicite:2]{index=2}
            .padding(horizontal = horizontalPadding)
            .height(animatedHeight)
            .graphicsLayer { alpha = expansionFraction }
//            .graphicsLayer {
//                translationY = visualTranslationY
//            }
//            .padding(
//                start = dynamicHorizontalPadding,
//                end = dynamicHorizontalPadding
//            )
            .pointerInput(currentSheetState, collapsedY, expandedTranslationY) {
//            .pointerInput(currentSheetState, collapsedTranslationY, expandedTranslationY) {
                var dragStartTime = 0L
                var dragStartAbsoluteY = 0f
                var pointerCurrentDragAccumulator = 0f
                var dragInProgress = false

                detectVerticalDragGestures(
                    onDragStart = { touch ->
                        scope.launch { currentTranslationYAnimatable.stop() }
                        isDragging = true
                        dragStartTime = System.currentTimeMillis()
                        dragStartAbsoluteY = currentTranslationYAnimatable.value
                        pointerCurrentDragAccumulator = 0f
                        dragVelocity = 0f

                        // Determinar si estamos en un estado intermedio (arrastre en progreso)
                        val currentY = currentTranslationYAnimatable.value
                        dragInProgress = when (currentSheetState) {
                            PlayerSheetState.EXPANDED -> currentY > expandedTranslationY + 1f
                            PlayerSheetState.COLLAPSED -> currentY < collapsedY - 1f
//                            PlayerSheetState.COLLAPSED -> currentY < collapsedTranslationY - 1f
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()

                        // Filtrar gestos según el estado actual, pero solo si no hay un arrastre en progreso
                        val isValidDirection = if (dragInProgress) {
                            // Si ya hay un arrastre en progreso, aceptar gestos en ambas direcciones
                            true
                        } else {
                            when (currentSheetState) {
                                PlayerSheetState.EXPANDED -> dragAmount > 0  // Solo permitir drag hacia abajo cuando está expandido
                                PlayerSheetState.COLLAPSED -> dragAmount < 0  // Solo permitir drag hacia arriba cuando está colapsado
                            }
                        }

                        // Solo procesar el gesto si es en la dirección válida
                        if (isValidDirection) {
                            pointerCurrentDragAccumulator += dragAmount

                            // Una vez que hemos procesado un gesto válido, consideramos que hay un arrastre en progreso
                            if (Math.abs(pointerCurrentDragAccumulator) > 5f) {
                                dragInProgress = true
                            }

                            val timeDelta = (System.currentTimeMillis() - dragStartTime).coerceAtLeast(1)
                            dragVelocity = (pointerCurrentDragAccumulator / timeDelta) * 1000f

                            // Aplicar resistencia para una sensación natural
                            var resistedDragAmount = dragAmount
                            if (currentSheetState == PlayerSheetState.EXPANDED && pointerCurrentDragAccumulator > 0) {
                                val resistance = 0.75f - (pointerCurrentDragAccumulator / screenSizePx.height * 0.35f).coerceIn(0f, 0.5f)
                                resistedDragAmount *= resistance
                            } else if (currentSheetState == PlayerSheetState.COLLAPSED && pointerCurrentDragAccumulator < 0) {
                                val resistance = 0.65f
                                resistedDragAmount *= resistance
                            }

                            scope.launch {
                                val newY = (currentTranslationYAnimatable.value + resistedDragAmount).coerceIn(
                                    expandedTranslationY - miniPlayerHeightPx * 0.1f,
                                    collapsedY + miniPlayerHeightPx * 0.1f
//                                    collapsedTranslationY + miniPlayerHeightPx * 0.1f
                                )
                                currentTranslationYAnimatable.snapTo(newY)
                            }
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        dragInProgress = false

                        // Procesar el final del gesto
                        val currentY = currentTranslationYAnimatable.value
                        val targetState: PlayerSheetState

                        val expandedThresholdPx = screenSizePx.height * EXPANDED_DRAG_THRESHOLD_PERCENTAGE
                        val collapsedThresholdPx = screenSizePx.height * COLLAPSED_DRAG_THRESHOLD_PERCENTAGE

                        if (currentSheetState == PlayerSheetState.EXPANDED) {
                            targetState = if (currentY > expandedTranslationY + expandedThresholdPx || dragVelocity > VELOCITY_THRESHOLD) {
                                PlayerSheetState.COLLAPSED
                            } else {
                                PlayerSheetState.EXPANDED
                            }
                        } else { // COLLAPSED
                            targetState = if (currentY < collapsedY - collapsedThresholdPx || dragVelocity < -VELOCITY_THRESHOLD) {
//                            targetState = if (currentY < collapsedTranslationY - collapsedThresholdPx || dragVelocity < -VELOCITY_THRESHOLD) {
                                PlayerSheetState.EXPANDED
                            } else {
                                PlayerSheetState.COLLAPSED
                            }
                        }

                        if (targetState == PlayerSheetState.EXPANDED) playerViewModel.expandPlayerSheet()
                        else playerViewModel.collapsePlayerSheet()
                    }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isDragging && (currentSheetState != PlayerSheetState.EXPANDED)) {
                    playerViewModel.togglePlayerSheetState()
                }
            },
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTR = animatedTopCorner,
            smoothnessAsPercentTL = 60,
            cornerRadiusTL = animatedTopCorner,
            smoothnessAsPercentTR = 60,
            cornerRadiusBR = animatedBottomCorner,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedBottomCorner,
            smoothnessAsPercentBL = 60
        ),
        color = Color.Transparent,
        tonalElevation = lerp(2.dp, 8.dp, expansionFraction),
        shadowElevation = lerp(6.dp, 14.dp, expansionFraction)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    RoundedCornerShape(
                        topStart = animatedTopCorner,
                        topEnd = animatedTopCorner,
                        bottomStart = animatedBottomCorner,
                        bottomEnd = animatedBottomCorner
                    )
                )
        ) {
            // Background with gradient
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary))

            // Content
            if (stablePlayerState.currentSong != null) {
                //Box(modifier = Modifier.alpha((1f - expansionFraction * 1.5f).coerceIn(0f, 1f))) {
                Box(modifier = Modifier.alpha((1f - expansionFraction * 1.3f).coerceIn(0f, 1f))) {
                    MiniPlayerContentInternal(
                        song = stablePlayerState.currentSong!!,
                        isPlaying = stablePlayerState.isPlaying,
                        onPlayPause = { playerViewModel.playPause() },
                        onNext = { playerViewModel.nextSong() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (expansionFraction > 0.05f) {
                    Box(modifier = Modifier.alpha(expansionFraction.pow(0.5f))) {
                        FullPlayerContentInternal(
                            playerViewModel = playerViewModel,
                            stablePlayerState = stablePlayerState, // Pasar estado estable
                            //currentPosition = currentPosition,     // Pasar posición actual
                            uiState = uiState,
                            onPlayPause = { playerViewModel.playPause() },
                            onSeek = { playerViewModel.seekTo(it) },
                            onNext = { playerViewModel.nextSong() },
                            onPrevious = { playerViewModel.previousSong() },
                            onCollapse = { playerViewModel.collapsePlayerSheet() },
                            expansionFraction = expansionFraction,
                            currentSheetState = currentSheetState,
                            onShowQueueClicked = { showQueueSheet = true },
                            //totalDuration = stablePlayerState.totalDuration, // De stablePlayerState
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay canción seleccionada",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queue = uiState.currentPlaybackQueue,
            currentSongId = stablePlayerState.currentSong?.id,
            onDismiss = { showQueueSheet = false },
            onPlaySong = { song ->
                playerViewModel.playSongs(uiState.currentPlaybackQueue, song, uiState.currentQueueSourceNname)
            },
            onRemoveSong = { songId -> playerViewModel.removeSongFromQueue(songId) },
            onReorder = { from, to -> playerViewModel.reorderQueueItem(from, to) }
        )
    }
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
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song.albumArtUri ?: R.drawable.rounded_album_24,
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
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
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
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, color = MaterialTheme.colorScheme.onPrimary)
                ) { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = if (isPlaying) painterResource(R.drawable.rounded_pause_24) else painterResource(R.drawable.rounded_play_arrow_24),
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Next button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, color = MaterialTheme.colorScheme.onPrimary)
                ) { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_skip_next_24),
                contentDescription = "Siguiente",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// Enum para identificar qué botón fue presionado
private enum class ButtonType {
    NONE, PREVIOUS, PLAY_PAUSE, NEXT
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FullPlayerContentInternal(
    playerViewModel: PlayerViewModel, // Recibe el ViewModel completo
    uiState: PlayerUiState,
    stablePlayerState: StablePlayerState, // Usar StablePlayerState
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    expansionFraction: Float,
    currentSheetState: PlayerSheetState,
    onShowQueueClicked: () -> Unit // Callback para mostrar la cola
) {
    val song = stablePlayerState.currentSong ?: return // Obtener canción de stablePlayerState

    // Acceder a los estados de control directamente desde uiState
    val isShuffleEnabled = stablePlayerState.isShuffleEnabled
    val repeatMode = stablePlayerState.repeatMode
    val isFavorite = stablePlayerState.isCurrentSongFavorite

    // Cálculo de la fracción de progreso
    val progressFraction = remember(uiState.currentPosition, stablePlayerState.totalDuration) {
        (uiState.currentPosition.coerceAtLeast(0).toFloat() /
                stablePlayerState.totalDuration.coerceAtLeast(1).toFloat())
    }.coerceIn(0f, 1f)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.alpha(expansionFraction.coerceIn(0f, 1f)),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
            song.albumArtUri?.let {
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
                    song.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        //letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1, // Forzamos una sola línea
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    song.artist,
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.sp),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
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
                WavyMusicSlider(
                    value = progressFraction,
                    onValueChange = { frac ->
                        onSeek((frac * stablePlayerState.totalDuration).roundToLong())
                    },
                    onValueChangeFinished = {
                        // Opcional: acciones cuando el usuario termina de arrastrar
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    trackHeight = 6.dp,
                    thumbRadius = 8.dp,
                    activeTrackColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                    thumbColor = MaterialTheme.colorScheme.onPrimary,
                    waveAmplitude = 3.dp,
                    waveFrequency = 0.08f,
                    isPlaying = (stablePlayerState.isPlaying && currentSheetState == PlayerSheetState.EXPANDED) // Pasamos el estado de reproducción
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(uiState.currentPosition),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        formatDuration(stablePlayerState.totalDuration),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f)) // Espaciador flexible para empujar los controles hacia abajo

            // Playback Controls - Contenedor con altura fija
            AnimatedPlaybackControls(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                isPlaying = stablePlayerState.isPlaying,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                // Configurables (opcional)
                height = 80.dp,
                baseWeight = 1f,
                expansionWeight = 1.5f,
                compressionWeight = 0.75f
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
                onShuffleToggle = { playerViewModel.toggleShuffle() },
                onRepeatToggle = { playerViewModel.cycleRepeatMode() },
                onFavoriteToggle = { playerViewModel.toggleFavorite() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    // Parámetros configurables
    modifier: Modifier = Modifier,
    height: Dp = 100.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.5f,
    compressionWeight: Float = 0.55f,
    pressAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 150, easing = FastOutSlowInEasing),
    releaseDelay: Long = 300L,
    playPauseCornerPlaying: Dp = 70.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
    colorPlayPause: Color = MaterialTheme.colorScheme.onPrimary,
    tintPlayPauseIcon: Color = MaterialTheme.colorScheme.primary,
    tintOtherIcons: Color = MaterialTheme.colorScheme.onPrimary,
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
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
            val playShape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = playCorner,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = playCorner,
                smoothnessAsPercentTL = 60,
                cornerRadiusTL = playCorner,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = playCorner,
                smoothnessAsPercentBL = 60
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
    val inactiveBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    // Fonde de la fila segmentada
    Box(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.onPrimary,
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
                activeColor = MaterialTheme.colorScheme.primary,
                activeCornerRadius = rowCorners,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveColor = inactiveBg,
                onClick = onShuffleToggle,
                icon = painterResource(R.drawable.rounded_shuffle_24),
                contentDesc = "Aleatorio"
            )
            // Repeat
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> painterResource(R.drawable.rounded_repeat_one_on_24)
                Player.REPEAT_MODE_ALL -> painterResource(R.drawable.rounded_repeat_on_24)
                else -> painterResource(R.drawable.rounded_repeat_24)
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = MaterialTheme.colorScheme.secondary,
                activeCornerRadius = rowCorners,
                activeContentColor = MaterialTheme.colorScheme.onSecondary,
                inactiveColor = inactiveBg,
                onClick = onRepeatToggle,
                icon = repeatIcon,
                contentDesc = "Repetir"
            )
            // Favorite
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = MaterialTheme.colorScheme.tertiary,
                activeCornerRadius = rowCorners,
                activeContentColor = MaterialTheme.colorScheme.onTertiary,
                inactiveColor = inactiveBg,
                onClick = onFavoriteToggle,
                icon = painterResource(R.drawable.round_favorite_24),
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
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    icon: Painter,
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
            painter = icon,
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}