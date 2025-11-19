package com.theveloper.pixelplay.presentation.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.*
import androidx.compose.ui.draw.drawWithCache // Importación necesaria
import androidx.compose.ui.graphics.drawscope.DrawScope // Para el tipo de onDraw
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.util.lerp

/**
 * Un slider personalizado con un efecto de onda que se mueve a lo largo de la pista de progreso.
 * La onda se aplana cuando no se está reproduciendo música o cuando el usuario interactúa con el slider.
 * El thumb se transforma de un círculo a una línea vertical cuando el usuario interactúa con él.
 *
 * @param value El valor actual del slider (entre 0f y 1f)
 * @param onValueChange Callback invocado cuando el valor cambia
 * @param modifier Modificador a aplicar a este composable
 * @param enabled Si el slider está habilitado o no
 * @param valueRange Rango de valores permitidos
 * @param onValueChangeFinished Callback invocado cuando la interacción con el slider termina
 * @param interactionSource Fuente de interacción para este slider
 * @param trackHeight Altura de la pista del slider
 * @param thumbRadius Radio del thumb
 * @param activeTrackColor Color de la parte activa de la pista
 * @param inactiveTrackColor Color de la parte inactiva de la pista
 * @param thumbColor Color del thumb
 * @param waveAmplitude Amplitud de la onda
 * @param waveLength Longitud de la onda expresada en Dp. Controla la distancia entre los picos
 *                   de la onda a lo largo de la pista.
 * @param animationDuration Duración de la animación de la onda en milisegundos
 * @param hideInactiveTrack Si se debe ocultar la parte inactiva del track que ya ha sido recorrida
 * @param isPlaying Si el contenido asociado está reproduciéndose actualmente
 * @param thumbLineHeight Alto de la línea vertical del thumb cuando está en estado de interacción
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun WavyMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    trackHeight: Dp = 6.dp,
    thumbRadius: Dp = 8.dp,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    waveAmplitudeWhenPlaying: Dp = 3.dp,
    waveLength: Dp = 80.dp,
    waveAnimationDuration: Int = 2000,
    hideInactiveTrackPortion: Boolean = true,
    isPlaying: Boolean = true,
    thumbLineHeightWhenInteracting: Dp = 24.dp,
    // NUEVO: permite desactivar la onda si el sheet no está expandido
    isWaveEligible: Boolean = true
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    // La onda sólo si: el track está reproduciendo, no hay interacción y el contexto lo permite
    val shouldShowWave = isWaveEligible && isPlaying && !isInteracting

    val animatedWaveAmplitude by animateDpAsState(
        targetValue = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim"
    )

    // FASE CONDICIONAL: si la onda no se muestra, no hay transición infinita ni invalidaciones.
    val phaseShiftAnim = remember { Animatable(0f) }
    val phaseShift = phaseShiftAnim.value

    LaunchedEffect(shouldShowWave, waveAnimationDuration) {
        if (shouldShowWave && waveAnimationDuration > 0) {
            val fullRotation = (2 * PI).toFloat()
            while (shouldShowWave) {
                val start = (phaseShiftAnim.value % fullRotation).let { if (it < 0f) it + fullRotation else it }
                phaseShiftAnim.snapTo(start)
                phaseShiftAnim.animateTo(
                    targetValue = start + fullRotation,
                    animationSpec = tween(durationMillis = waveAnimationDuration, easing = LinearEasing)
                )
            }
        }
    }

    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
    val waveAmplitudePxInternal = with(LocalDensity.current) { animatedWaveAmplitude.toPx() }
    val waveLengthPx = with(LocalDensity.current) { waveLength.toPx() }
    val waveFrequency = if (waveLengthPx > 0f) {
        ((2 * PI) / waveLengthPx).toFloat()
    } else {
        0f
    }
    val thumbLineHeightPxInternal = with(LocalDensity.current) { thumbLineHeightWhenInteracting.toPx() }
    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }

    val wavePath = remember { Path() }

    val sliderVisualHeight = remember(trackHeight, thumbRadius, thumbLineHeightWhenInteracting) {
        max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeightWhenInteracting) + 8.dp)
    }

    val hapticFeedback = LocalHapticFeedback.current

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val lastHapticStep = remember { mutableIntStateOf(-1) }

        Slider(
            value = value,
            onValueChange = { newValue ->
                val currentStep = (newValue * 100 / (valueRange.endInclusive - valueRange.start)).toInt()
                if (currentStep != lastHapticStep.intValue) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticStep.intValue = currentStep
                }
                onValueChange(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight),
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .drawWithCache {
                    val canvasWidth = size.width
                    val localCenterY = size.height / 2f
                    val localTrackStart = thumbRadiusPx
                    val localTrackEnd = canvasWidth - thumbRadiusPx
                    val localTrackWidth = (localTrackEnd - localTrackStart).coerceAtLeast(0f)

                    val normalizedValue = value.let { v ->
                        if (valueRange.endInclusive == valueRange.start) 0f
                        else ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(
                            0f,
                            1f
                        )
                    }
                    onDrawWithContent {
                        // --- Dibujar Pista Inactiva ---
                        val currentProgressPxEndVisual =
                            localTrackStart + localTrackWidth * normalizedValue
                        if (hideInactiveTrackPortion) {
                            if (currentProgressPxEndVisual < localTrackEnd) {
                                drawLine(
                                    color = inactiveTrackColor,
                                    start = Offset(currentProgressPxEndVisual, localCenterY),
                                    end = Offset(localTrackEnd, localCenterY),
                                    strokeWidth = trackHeightPx,
                                    cap = StrokeCap.Round
                                )
                            }
                        } else {
                            drawLine(
                                color = inactiveTrackColor,
                                start = Offset(localTrackStart, localCenterY),
                                end = Offset(localTrackEnd, localCenterY),
                                strokeWidth = trackHeightPx,
                                cap = StrokeCap.Round
                            )
                        }

                        // --- Dibujar Pista Activa (Onda o Línea) ---
                        if (normalizedValue > 0f) {
                            val activeTrackVisualEnd =
                                currentProgressPxEndVisual - (thumbGapPx * thumbInteractionFraction)

                            if (waveAmplitudePxInternal > 0.01f && waveFrequency > 0f) {
                                wavePath.reset()
                                val waveStartDrawX = localTrackStart
                                val waveEndDrawX = activeTrackVisualEnd.coerceAtLeast(waveStartDrawX)
                                if (waveEndDrawX > waveStartDrawX) {
                                    val periodPx = ((2 * PI) / waveFrequency).toFloat()
                                    val samplesPerCycle = 20f
                                    val waveStep = (periodPx / samplesPerCycle)
                                        .coerceAtLeast(1.2f)
                                        .coerceAtMost(trackHeightPx)

                                    fun yAt(x: Float): Float {
                                        val s = sin(waveFrequency * x + phaseShift)
                                        return (localCenterY + waveAmplitudePxInternal * s)
                                            .coerceIn(
                                                localCenterY - waveAmplitudePxInternal - trackHeightPx / 2f,
                                                localCenterY + waveAmplitudePxInternal + trackHeightPx / 2f
                                            )
                                    }

                                    var prevX = waveStartDrawX
                                    var prevY = yAt(prevX)
                                    wavePath.moveTo(prevX, prevY)

                                    var x = prevX + waveStep
                                    while (x < waveEndDrawX) {
                                        val y = yAt(x)
                                        val midX = (prevX + x) * 0.5f
                                        val midY = (prevY + y) * 0.5f
                                        // Compose Path: quadraticBezierTo(controlX, controlY, endX, endY)
                                        wavePath.quadraticBezierTo(prevX, prevY, midX, midY)
                                        prevX = x
                                        prevY = y
                                        x += waveStep
                                    }
                                    val endY = yAt(waveEndDrawX)
                                    wavePath.quadraticBezierTo(prevX, prevY, waveEndDrawX, endY)

                                    drawPath(
                                        path = wavePath,
                                        color = activeTrackColor,
                                        style = Stroke(
                                            width = trackHeightPx,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round, // <- importante para suavizar uniones
                                            miter = 1f
                                        )
                                    )
                                }
                            } else {
                                if (activeTrackVisualEnd > localTrackStart) {
                                    drawLine(
                                        color = activeTrackColor,
                                        start = Offset(localTrackStart, localCenterY),
                                        end = Offset(activeTrackVisualEnd, localCenterY),
                                        strokeWidth = trackHeightPx,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }


                        // --- Dibujar Thumb ---
                        val currentThumbCenterX =
                            localTrackStart + localTrackWidth * normalizedValue
                        val thumbCurrentWidthPx =
                            lerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
                        val thumbCurrentHeightPx = lerp(
                            thumbRadiusPx * 2f,
                            thumbLineHeightPxInternal,
                            thumbInteractionFraction
                        )

                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(
                                currentThumbCenterX - thumbCurrentWidthPx / 2f,
                                localCenterY - thumbCurrentHeightPx / 2f
                            ),
                            size = Size(thumbCurrentWidthPx, thumbCurrentHeightPx),
                            cornerRadius = CornerRadius(thumbCurrentWidthPx / 2f)
                        )
                    }
                }
        )
    }
}

//@Composable
//fun WavyMusicSlider(
//    value: Float,
//    onValueChange: (Float) -> Unit,
//    modifier: Modifier = Modifier,
//    enabled: Boolean = true,
//    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
//    onValueChangeFinished: (() -> Unit)? = null,
//    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
//    trackHeight: Dp = 6.dp,
//    thumbRadius: Dp = 8.dp,
//    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
//    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
//    thumbColor: Color = MaterialTheme.colorScheme.primary,
//    waveAmplitudeWhenPlaying: Dp = 3.dp,
//    waveLength: Dp = 80.dp,
//    waveAnimationDuration: Int = 2000,
//    hideInactiveTrackPortion: Boolean = true,
//    isPlaying: Boolean = true,
//    thumbLineHeightWhenInteracting: Dp = 24.dp
//) {
//    val isDragged by interactionSource.collectIsDraggedAsState()
//    val isPressed by interactionSource.collectIsPressedAsState()
//    val isInteracting = isDragged || isPressed
//
//    val thumbInteractionFraction by animateFloatAsState(
//        targetValue = if (isInteracting) 1f else 0f,
//        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
//        label = "ThumbInteractionAnim"
//    )
//
//    val shouldShowWave = isPlaying && !isInteracting
//
//    val currentWaveAmplitudeTarget = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp
//    val animatedWaveAmplitude by animateDpAsState(
//        targetValue = currentWaveAmplitudeTarget,
//        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
//        label = "WaveAmplitudeAnim"
//    )
//
//    val infiniteTransition = rememberInfiniteTransition(label = "WavePhaseInfiniteAnim")
//    val phaseShift by infiniteTransition.animateFloat(
//        initialValue = 0f,
//        targetValue = 2 * PI.toFloat(),
//        animationSpec = infiniteRepeatable(
//            animation = tween(durationMillis = waveAnimationDuration, easing = LinearEasing),
//            repeatMode = RepeatMode.Restart
//        ),
//        label = "WavePhaseShiftAnim"
//    )
//
//    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
//    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
//    val waveAmplitudePxInternal = with(LocalDensity.current) { animatedWaveAmplitude.toPx() }
//    val thumbLineHeightPxInternal = with(LocalDensity.current) { thumbLineHeightWhenInteracting.toPx() }
//    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }
//    val waveLengthPx = with(LocalDensity.current) { waveLength.toPx() }
//    val waveFrequency = if (waveLengthPx > 0f) ((2 * PI) / waveLengthPx).toFloat() else 0f
//
//    val wavePath = remember { Path() }
//
//    val sliderVisualHeight = remember(trackHeight, thumbRadius, thumbLineHeightWhenInteracting) {
//        max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeightWhenInteracting) + 8.dp)
//    }
//
//    val hapticFeedback = LocalHapticFeedback.current
//
//    BoxWithConstraints(modifier = modifier.clipToBounds()) {
//        val lastHapticStep = remember { mutableIntStateOf(-1) }
//
//        Slider(
//            value = value,
//            onValueChange = { newValue ->
//                val currentStep = (newValue * 100 / (valueRange.endInclusive - valueRange.start)).toInt()
//                if (currentStep != lastHapticStep.intValue) {
//                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
//                    lastHapticStep.intValue = currentStep
//                }
//                onValueChange(newValue)
//            },
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(sliderVisualHeight),
//            enabled = enabled,
//            valueRange = valueRange,
//            onValueChangeFinished = onValueChangeFinished,
//            interactionSource = interactionSource,
//            colors = SliderDefaults.colors(
//                thumbColor = Color.Transparent,
//                activeTrackColor = Color.Transparent,
//                inactiveTrackColor = Color.Transparent
//            )
//        )
//
//        Spacer(
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(sliderVisualHeight)
//                .drawWithCache {
//                    val canvasWidth = size.width
//                    val localCenterY = size.height / 2f
//                    val localTrackStart = thumbRadiusPx
//                    val localTrackEnd = canvasWidth - thumbRadiusPx
//                    val localTrackWidth = (localTrackEnd - localTrackStart).coerceAtLeast(0f)
//
//                    val normalizedValue = value.let { v ->
//                        if (valueRange.endInclusive == valueRange.start) 0f
//                        else ((v - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(
//                            0f,
//                            1f
//                        )
//                    }
//                    onDrawWithContent {
//                        // --- Dibujar Pista Inactiva ---
//                        val currentProgressPxEndVisual =
//                            localTrackStart + localTrackWidth * normalizedValue
//                        if (hideInactiveTrackPortion) {
//                            if (currentProgressPxEndVisual < localTrackEnd) {
//                                drawLine(
//                                    color = inactiveTrackColor,
//                                    start = Offset(currentProgressPxEndVisual, localCenterY),
//                                    end = Offset(localTrackEnd, localCenterY),
//                                    strokeWidth = trackHeightPx,
//                                    cap = StrokeCap.Round
//                                )
//                            }
//                        } else {
//                            drawLine(
//                                color = inactiveTrackColor,
//                                start = Offset(localTrackStart, localCenterY),
//                                end = Offset(localTrackEnd, localCenterY),
//                                strokeWidth = trackHeightPx,
//                                cap = StrokeCap.Round
//                            )
//                        }
//
//                        // --- Dibujar Pista Activa (Onda o Línea) ---
//                        if (normalizedValue > 0f) {
//                            val activeTrackVisualEnd =
//                                currentProgressPxEndVisual - (thumbGapPx * thumbInteractionFraction)
//
//                            if (waveAmplitudePxInternal > 0.01f) {
//                                wavePath.reset()
//                                val waveStartDrawX = localTrackStart
//                                val waveEndDrawX =
//                                    activeTrackVisualEnd.coerceAtLeast(waveStartDrawX)
//
//                                if (waveEndDrawX > waveStartDrawX) {
//                                    wavePath.moveTo(
//                                        waveStartDrawX,
//                                        localCenterY + waveAmplitudePxInternal * sin(waveFrequency * waveStartDrawX + phaseShift)
//                                    )
//                                    val waveStep = 2f // Aumentado de 1f a 2f para reducir cálculos
//                                    var x = waveStartDrawX + waveStep
//                                    while (x < waveEndDrawX) {
//                                        val wavePhase = waveFrequency * x + phaseShift
//                                        val waveY =
//                                            localCenterY + waveAmplitudePxInternal * sin(wavePhase)
//                                        val clampedY = waveY.coerceIn(
//                                            localCenterY - waveAmplitudePxInternal - trackHeightPx / 2f,
//                                            localCenterY + waveAmplitudePxInternal + trackHeightPx / 2f
//                                        )
//                                        wavePath.lineTo(x, clampedY)
//                                        x += waveStep
//                                    }
//                                    wavePath.lineTo(
//                                        waveEndDrawX,
//                                        localCenterY + waveAmplitudePxInternal * sin(waveFrequency * waveEndDrawX + phaseShift)
//                                    )
//                                    drawPath(
//                                        path = wavePath,
//                                        color = activeTrackColor,
//                                        style = Stroke(width = trackHeightPx, cap = StrokeCap.Round)
//                                    )
//                                }
//                            } else { // Dibujar línea recta
//                                if (activeTrackVisualEnd > localTrackStart) {
//                                    drawLine(
//                                        color = activeTrackColor,
//                                        start = Offset(localTrackStart, localCenterY),
//                                        end = Offset(activeTrackVisualEnd, localCenterY),
//                                        strokeWidth = trackHeightPx,
//                                        cap = StrokeCap.Round
//                                    )
//                                }
//                            }
//                        }
//
//                        // --- Dibujar Thumb ---
//                        val currentThumbCenterX =
//                            localTrackStart + localTrackWidth * normalizedValue
//                        val thumbCurrentWidthPx =
//                            lerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
//                        val thumbCurrentHeightPx = lerp(
//                            thumbRadiusPx * 2f,
//                            thumbLineHeightPxInternal,
//                            thumbInteractionFraction
//                        )
//
//                        drawRoundRect(
//                            color = thumbColor,
//                            topLeft = Offset(
//                                currentThumbCenterX - thumbCurrentWidthPx / 2f,
//                                localCenterY - thumbCurrentHeightPx / 2f
//                            ),
//                            size = Size(thumbCurrentWidthPx, thumbCurrentHeightPx),
//                            cornerRadius = CornerRadius(thumbCurrentWidthPx / 2f)
//                        )
//                    }
//                }
//        )
//    }
//}