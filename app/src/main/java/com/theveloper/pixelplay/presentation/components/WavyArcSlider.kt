package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun WavyArcSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackThickness: Dp = 4.dp,
    thumbSize: Dp = 16.dp, 
    waveAmplitude: Dp = 2.dp,
    waveLength: Dp = 20.dp,
    startAngle: Float = 135f,
    sweepAngle: Float = 270f
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val view = LocalView.current
    
    val trackThicknessPx = with(density) { trackThickness.toPx() }
    val thumbSizePx = with(density) { thumbSize.toPx() }
    val waveAmplitudePx = with(density) { waveAmplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }
    val thumbRadius = thumbSizePx / 2
    
    // Normalize value
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    
    // Animation for wave phase - REMOVED per user request (static wave)
    val phaseShift = 0f
    
    // Interaction state for thumb scaling
    var isInteracting by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.2f else 1f,
        label = "ThumbScale"
    )

    // Store integer value for haptic feedback
    var lastHapticValue by remember { mutableIntStateOf(value.roundToInt()) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val minDim = min(width, height)
        
        // Calculate arc geometry
        // Ensure strictly square bounds for the arc to be circular
        // Center the arc in the available space
        val arcDiameter = minDim - thumbSizePx * 2 - waveAmplitudePx * 2
        val arcRadius = arcDiameter / 2
        val arcCenter = Offset(width / 2, height / 2)
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, view) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        // Wait for the first down event, consuming it if needed, or just observing
                        // We set requireUnconsumed = false to ensure we see it even if another modifier sees it
                        val down = awaitFirstDown(requireUnconsumed = false)
                        
                        // Request interception immediately
                        view.parent.requestDisallowInterceptTouchEvent(true)
                        
                        // Wait for the gesture to finish (up or cancel)
                        waitForUpOrCancellation()
                        
                        // Allow interception again
                        view.parent.requestDisallowInterceptTouchEvent(false)
                    }
                }
                .pointerInput(enabled, valueRange) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { 
                            isInteracting = true 
                        },
                        onDragEnd = { 
                            isInteracting = false 
                        },
                        onDragCancel = { 
                            isInteracting = false 
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            
                            // Calculate angle from center to touch point
                            val touchPoint = change.position
                            val dx = touchPoint.x - arcCenter.x
                            val dy = touchPoint.y - arcCenter.y
                            var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                            
                            // Normalize angle to 0..360
                            if (angle < 0) angle += 360f
                            
                            // Map angle to progress relative to startAngle
                            // StartAngle is usually 135 (bottom-left)
                            // We need to handle the wrap-around at 360/0 if necessary
                            // but for 135 start + 270 sweep, the range is 135 -> 405 (45)
                            
                            var relativeAngle = angle - startAngle
                            if (relativeAngle < 0) relativeAngle += 360f
                            
                            // If touch is in the "dead zone" (gap between end and start), clamp to nearest
                            if (relativeAngle > sweepAngle) {
                                // Clamp to 0 or 1 based on which side is closer
                                val halfDeadZone = (360f - sweepAngle) / 2
                                if (relativeAngle < sweepAngle + halfDeadZone) {
                                    relativeAngle = sweepAngle // effectively 100%
                                } else {
                                    relativeAngle = 0f // effectively 0%
                                }
                            }
                            
                            val newProgress = (relativeAngle / sweepAngle).coerceIn(0f, 1f)
                            val newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newValue)
                            
                            // Haptics
                            val newInt = newValue.roundToInt()
                            if (newInt != lastHapticValue) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastHapticValue = newInt
                            }
                        }
                    )
                }
        ) {
            val activeSweep = sweepAngle * normalizedValue
            
            // 1. Draw Inactive Track
            // Only draw from the end of the active part to the end of the sweep
            // This prevents the straight line from showing behind the wavy active part
            if (activeSweep < sweepAngle) {
                drawArc(
                    color = inactiveTrackColor,
                    startAngle = startAngle + activeSweep,
                    sweepAngle = sweepAngle - activeSweep,
                    useCenter = false,
                    style = Stroke(width = trackThicknessPx, cap = StrokeCap.Round),
                    topLeft = Offset(arcCenter.x - arcRadius, arcCenter.y - arcRadius),
                    size = Size(arcDiameter, arcDiameter)
                )
            }
            
            // 2. Draw Active Track (Wavy)
            
            if (activeSweep > 0) {
                val wavePath = Path()
                
                // We will iterate along the arc angle
                // Steps need to be small enough for smoothness
                val steps = (activeSweep * 2).toInt().coerceAtLeast(10) // ~0.5 degree steps
                val angleStep = activeSweep / steps
                
                // Starting point
                var firstPointSet = false
                
                for (i in 0..steps) {
                    val currentAngleDeg = startAngle + (i * angleStep)
                    val currentAngleRad = Math.toRadians(currentAngleDeg.toDouble())
                    
                    // Distance along the arc from start
                    // Arc length = radius * angle_in_radians (relative to start)
                    val angleFromStartRad = Math.toRadians((i * angleStep).toDouble())
                    val distanceAlongArc = arcRadius * angleFromStartRad
                    
                    // Calculate wave offset
                    // h = A * sin(k * x + phase)
                    // k = 2pi / wavelength
                    val k = (2 * PI) / waveLengthPx
                    val h = if (enabled) waveAmplitudePx * sin(k * distanceAlongArc + phaseShift) else 0.0
                    
                    // Radius at this point
                    val r = arcRadius + h
                    
                    // Convert polar to cartesian
                    val x = arcCenter.x + r * cos(currentAngleRad)
                    val y = arcCenter.y + r * sin(currentAngleRad)
                    
                    if (!firstPointSet) {
                        wavePath.moveTo(x.toFloat(), y.toFloat())
                        firstPointSet = true
                    } else {
                        wavePath.lineTo(x.toFloat(), y.toFloat())
                    }
                }
                
                drawPath(
                    path = wavePath,
                    color = activeTrackColor,
                    style = Stroke(width = trackThicknessPx, cap = StrokeCap.Round)
                )
            }
            
            // 3. Draw Thumb
            val thumbAngleDeg = startAngle + activeSweep
            val thumbAngleRad = Math.toRadians(thumbAngleDeg.toDouble())
            val thumbX = arcCenter.x + arcRadius * cos(thumbAngleRad)
            val thumbY = arcCenter.y + arcRadius * sin(thumbAngleRad)
            
            drawCircle(
                color = thumbColor,
                radius = thumbRadius * thumbScale,
                center = Offset(thumbX.toFloat(), thumbY.toFloat())
            )
        }
    }
}
