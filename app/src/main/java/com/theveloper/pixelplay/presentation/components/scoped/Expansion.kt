package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.updateTransition
import androidx.compose.runtime.Composable


@Composable
fun rememberExpansionTransition(expansionFraction: Float) =
    updateTransition(targetState = expansionFraction, label = "expansionTransition")