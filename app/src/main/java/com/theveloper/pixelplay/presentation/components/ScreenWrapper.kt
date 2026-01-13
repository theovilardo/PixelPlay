package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


@Composable
fun ScreenWrapper(
    navController: androidx.navigation.NavController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Lifecycle State
    var isResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Initial Check
    val currentState = lifecycleOwner.lifecycle.currentState
    if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        isResumed = true
    }

    // Stack Check (for Dimming)
    // We compare indices to determine if we are strictly BEHIND the active screen.
    val backStack by navController.currentBackStack.collectAsState()
    val myEntry = lifecycleOwner as? androidx.navigation.NavBackStackEntry
    val myIndex = backStack.indexOfFirst { it.id == myEntry?.id }
    val topIndex = backStack.lastIndex
    
    // Dim Logic:
    // If I am BACKGROUND (myIndex < topIndex) -> Dim.
    // If I am TOP (myIndex == topIndex) -> Clear.
    // If I am EXITING (myIndex > topIndex, effectively in front during pop) -> Clear.
    val shouldDim = myIndex != -1 && topIndex != -1 && myIndex < topIndex

    // Declarative Animations
    // Radius: If NOT Resumed -> 32dp. (Background OR Popped)
    val targetRadius = if (isResumed) 0f else 32f
    val cornerRadius by animateFloatAsState(
        targetValue = targetRadius,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "cornerRadius"
    )

    // Dim: If strictly behind Top -> 0.4f. Else -> 0f.
    val targetDim = if (shouldDim) 0.4f else 0f
    val dimAlpha by animateFloatAsState(
        targetValue = targetDim,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "dimAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius.dp)) // Clip first
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
        
        // Dim Layer Overlay
        if (dimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }
    }
}
