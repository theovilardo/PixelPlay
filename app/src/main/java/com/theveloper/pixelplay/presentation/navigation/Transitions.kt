package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin

const val TRANSITION_DURATION = 500

fun enterTransition() = slideInVertically(
    animationSpec = tween(TRANSITION_DURATION),
    initialOffsetY = { it / 20 }
) + slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION),
    initialOffsetX = { -it / 20 }
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION)
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION),
    initialScale = 0.95f
)

fun exitTransition() = slideOutVertically(
    animationSpec = tween(TRANSITION_DURATION),
    targetOffsetY = { it / 20 }
) + slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION),
    targetOffsetX = { -it / 20 }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION)
) + scaleOut(
    animationSpec = tween(TRANSITION_DURATION),
    transformOrigin = TransformOrigin(0.5f, 0.5f),
    targetScale = 0.95f
)
