package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

const val TRANSITION_DURATION = 400

fun enterTransition() = slideInVertically(
    animationSpec = tween(TRANSITION_DURATION),
    initialOffsetY = { it / 10 }
) + slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION),
    initialOffsetX = { -it / 10 }
) + fadeIn(animationSpec = tween(TRANSITION_DURATION))

fun exitTransition() = slideOutVertically(
    animationSpec = tween(TRANSITION_DURATION),
    targetOffsetY = { it / 10 }
) + slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION),
    targetOffsetX = { -it / 10 }
) + fadeOut(animationSpec = tween(TRANSITION_DURATION))
