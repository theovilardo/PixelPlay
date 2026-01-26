package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.max

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySliderExpressive(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true, 
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(), 
    activeTrackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    
    isPlaying: Boolean = true,
    strokeWidth: Dp = 5.dp,
    thumbRadius: Dp = 8.dp,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 2f, // Slower wave as requested
    
    waveAmplitudeWhenPlaying: Dp = 4.dp, 
    thumbLineHeightWhenInteracting: Dp = 24.dp 
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val thumbLineHeightPx = with(density) { thumbLineHeightWhenInteracting.toPx() }
    
    val stroke = remember(strokeWidthPx) { 
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round) 
    }
    
    val normalizedValue = if(valueRange.endInclusive == valueRange.start) 0f 
        else ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(normalizedValue) }
    
    val interactionSource = remember { MutableInteractionSource() }
    // We can use interactionSource if we want, but we are doing manual gesture detection.
    // However, for the Thumb Morph animation, we need to know if we are interacting.
    // The previous WavyMusicSlider used interactionSource logic or isDragging state.
    // Here we have isDragging. Let's use that for simple logic.
    
    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    val displayValue = if (isDragging) dragValue else normalizedValue
    
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging) 1f else 0f, // Flatten when interacting, like WavyMusicSlider
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude"
    )
    
    val containerHeight = max(WavyProgressIndicatorDefaults.LinearContainerHeight, max(thumbRadius * 2, thumbLineHeightWhenInteracting))
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(containerHeight)
            .pointerInput(valueRange, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                    val mappedValue = valueRange.start + newValue * (valueRange.endInclusive - valueRange.start)
                    onValueChange(mappedValue)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(valueRange, enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        val mappedValue = valueRange.start + dragValue * (valueRange.endInclusive - valueRange.start)
                        onValueChange(mappedValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished?.invoke() 
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragValue = (dragValue + dragAmount / size.width).coerceIn(0f, 1f)
                        val mappedValue = valueRange.start + dragValue * (valueRange.endInclusive - valueRange.start)
                        onValueChange(mappedValue)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        
        // Thumb Dimensions Interpolation
        // We can't use 'lerp' with Dp easily outside of density context without explicit conversion.
        // We'll calculate Px values for standard thumb vs line.
        // Thumb width: radius*2 -> radius*2 (circle) OR trackHeight*1.2 (pill width?)
        // WavyMusicSlider logic:
        // width: lerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, fraction)
        // height: lerp(thumbRadiusPx * 2f, thumbLineHeightPx, fraction)
        // trackHeightPx here is 'strokeWidthPx'.
        
        // Wait, WavyMusicSlider's thumb becomes THINNER (width) and TALLER (height)?
        // width: thumbRadius*2 (16dp) -> strokeWidth*1.2 (4.8dp)
        // height: thumbRadius*2 (16dp) -> thumbLineHeight (24dp)
        
        // Gap size calculation
        // LinearWavyProgressIndicator gapSize is the total gap.
        // We want the gap to accommodate the thumb.
        // If the thumb changes size, the gap should technically change, but LinearWavyProgressIndicator might NOT animate gapSize smoothly.
        // Let's keep gapSize strictly related to the MAXIMUM thumb width (radius*2) + padding to avoid clipping.
        // Or if we want strict visual sync, simple radius*2 + padding is safe.
        
        LinearWavyProgressIndicator(
            progress = { displayValue },
            modifier = Modifier.fillMaxWidth(),
            color = activeTrackColor,
            trackColor = inactiveTrackColor,
            stroke = stroke,
            trackStroke = stroke,
            gapSize = thumbRadius + 4.dp, // Slightly larger gap to be safe
            stopSize = 3.dp, //WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
            amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f },
            wavelength = wavelength,
            waveSpeed = waveSpeed
        )
        
        // Draw Thumb (Pill Morph)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thumbX = size.width * displayValue
            val thumbY = size.height / 2
            
            // Interpolate dimensions
            // WavyMusicSlider logic:
            // val thumbCurrentWidthPx = lerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
            // val thumbCurrentHeightPx = lerp(thumbRadiusPx * 2f, thumbLineHeightPxInternal, thumbInteractionFraction)

            // Manual Lerp for Float
            fun lerp(start: Float, stop: Float, fraction: Float): Float {
                return start + (stop - start) * fraction
            }

            val currentWidth = lerp(thumbRadiusPx * 2f, strokeWidthPx * 1.2f, thumbInteractionFraction)
            val currentHeight = lerp(thumbRadiusPx * 2f, thumbLineHeightPx, thumbInteractionFraction)
            
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(
                    thumbX - currentWidth / 2f,
                    thumbY - currentHeight / 2f
                ),
                size = Size(currentWidth, currentHeight),
                cornerRadius = CornerRadius(currentWidth / 2f)
            )
        }
    }
}
