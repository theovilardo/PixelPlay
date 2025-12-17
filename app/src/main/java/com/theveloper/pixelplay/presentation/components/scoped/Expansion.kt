package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp


@Composable
fun rememberExpansionTransition(expansionFraction: Float): ExpansionTransition =
    remember(expansionFraction) {
        ExpansionTransition(expansionFraction)
    }

class ExpansionTransition(private val expansionFraction: Float) {
    
    @Composable
    fun animateFloat(
        label: String = "",
        transitionSpec: @Composable ExpansionTransition.() -> AnimationSpec<Float> = { 
            TweenSpec(durationMillis = 300, easing = FastOutSlowInEasing) 
        },
        targetValueByState: (Float) -> Float
    ): State<Float> {
        return remember(expansionFraction) {
            derivedStateOf { targetValueByState(expansionFraction) }
        }
    }
    
    @Composable
    fun animateDp(
        label: String = "",
        transitionSpec: @Composable ExpansionTransition.() -> AnimationSpec<Dp> = { 
            TweenSpec(durationMillis = 300, easing = FastOutSlowInEasing) 
        },
        targetValueByState: (Float) -> Dp
    ): State<Dp> {
        return remember(expansionFraction) {
            derivedStateOf { targetValueByState(expansionFraction) }
        }
    }
}
