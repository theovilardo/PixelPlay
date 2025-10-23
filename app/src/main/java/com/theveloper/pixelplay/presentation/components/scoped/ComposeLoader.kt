package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ------------------------------------------------------------
// 1) Phase loader: compose a subtree only after a threshold, then keep it alive
// ------------------------------------------------------------
@Composable
fun DeferAt(
    expansionFraction: Float,
    threshold: Float,
    keepAliveKey: Any? = "default",
    content: @Composable () -> Unit
) {
    var ready by rememberSaveable(keepAliveKey) { mutableStateOf(false) }
    LaunchedEffect(expansionFraction) {
        if (!ready && expansionFraction >= threshold) ready = true
    }
    if (ready) content()
}


@Composable
fun DeferUntil(
    condition: Boolean,
    keepAliveKey: Any? = "default",
    content: @Composable () -> Unit
) {
    var ready by rememberSaveable(keepAliveKey) { mutableStateOf(false) }
    LaunchedEffect(condition) { if (condition) ready = true }
    if (ready) content()
}

// ------------------------------------------------------------
// 2) Smooth progress sampler for long-running sliders/meters
// Cuts recompositions from ~50–60 FPS position updates down to ~5 FPS,
// while animating the UI in between so it still looks 60 FPS.
// ------------------------------------------------------------
@Composable
fun rememberSmoothProgress(
    isPlayingProvider: () -> Boolean,
    currentPositionProvider: () -> Long,
    totalDuration: Long,
    sampleWhilePlayingMs: Long = 200L,
    sampleWhilePausedMs: Long = 800L,
): Pair<Float, Long> {
    var sampledPosition by remember { mutableLongStateOf(0L) }
    var targetFraction by remember { mutableFloatStateOf(0f) }

    val latestPositionProvider by rememberUpdatedState(newValue = currentPositionProvider)
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)

    val safeDuration = totalDuration.coerceAtLeast(1L)

    LaunchedEffect(totalDuration, sampleWhilePlayingMs, sampleWhilePausedMs) {
        val initialPosition = latestPositionProvider().coerceIn(0L, totalDuration)
        sampledPosition = initialPosition
        targetFraction = (initialPosition / safeDuration.toFloat()).coerceIn(0f, 1f)

        snapshotFlow { latestIsPlayingProvider() to latestPositionProvider() }
            .distinctUntilChanged()
            .collectLatest { (isPlaying, rawPosition) ->
                val clampedPosition = rawPosition.coerceIn(0L, totalDuration)
                sampledPosition = clampedPosition
                targetFraction = (clampedPosition / safeDuration.toFloat()).coerceIn(0f, 1f)

                val delayMillis = if (isPlaying) sampleWhilePlayingMs else sampleWhilePausedMs
                val safeDelay = delayMillis.coerceAtLeast(1L)
                delay(safeDelay)
            }
    }

    val isPlaying = latestIsPlayingProvider()
    val animationDuration = ((if (isPlaying) sampleWhilePlayingMs else sampleWhilePausedMs) * 0.9f)
        .roundToInt()
        .coerceAtLeast(1)
    val smooth by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = animationDuration, easing = LinearEasing),
        label = "SmoothProgressAnim"
    )
    val animatedPosition = (smooth * safeDuration).roundToLong().coerceIn(0L, totalDuration)
    val displayedPosition = if (isPlaying) animatedPosition else sampledPosition
    return smooth to displayedPosition
}