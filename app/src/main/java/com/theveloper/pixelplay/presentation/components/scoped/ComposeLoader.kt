package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

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

    val latestPositionProvider by rememberUpdatedState(newValue = currentPositionProvider)
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)

    LaunchedEffect(totalDuration, sampleWhilePlayingMs, sampleWhilePausedMs) {
        while (isActive) {
            sampledPosition = latestPositionProvider()
            val nextDelay = if (latestIsPlayingProvider()) sampleWhilePlayingMs else sampleWhilePausedMs
            val safeDelay = nextDelay.coerceAtLeast(1L)
            delay(safeDelay)
        }
    }

    val isPlaying = latestIsPlayingProvider()
    val target = (sampledPosition.coerceAtLeast(0) / totalDuration.coerceAtLeast(1).toFloat())
        .coerceIn(0f, 1f)
    val animationDuration = ((if (isPlaying) sampleWhilePlayingMs else sampleWhilePausedMs) * 0.9f)
        .roundToInt()
        .coerceAtLeast(1)
    val smooth by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = animationDuration, easing = LinearEasing),
        label = "SmoothProgressAnim"
    )
    return smooth to sampledPosition
}