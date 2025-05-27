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
 * @param waveFrequency Frecuencia de la onda (mayor valor = más oscilaciones)
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
    waveFrequency: Float = 0.08f,
    waveAnimationDuration: Int = 2000,
    hideInactiveTrackPortion: Boolean = true,
    isPlaying: Boolean = true,
    thumbLineHeightWhenInteracting: Dp = 24.dp
) {
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    val shouldShowWave = isPlaying && !isInteracting

    val currentWaveAmplitudeTarget = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp
    val animatedWaveAmplitude by animateDpAsState(
        targetValue = currentWaveAmplitudeTarget,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "WavePhaseInfiniteAnim")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = waveAnimationDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhaseShiftAnim"
    )

    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
    val waveAmplitudePxInternal = with(LocalDensity.current) { animatedWaveAmplitude.toPx() }
    val thumbLineHeightPxInternal = with(LocalDensity.current) { thumbLineHeightWhenInteracting.toPx() }
    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }

    val normalizedValue = remember(value, valueRange) {
        if (valueRange.endInclusive == valueRange.start) 0f
        else ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    }

    val wavePath = remember { Path() }

    val sliderVisualHeight = remember(trackHeight, thumbRadius, thumbLineHeightWhenInteracting) {
        max(trackHeight * 2, max(thumbRadius * 2, thumbLineHeightWhenInteracting) + 8.dp)
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        Slider(
            value = value,
            onValueChange = onValueChange,
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
                    // Cálculos que dependen del tamaño del Canvas (size)
                    val canvasWidth = size.width
                    val localCenterY = size.height / 2f
                    val localTrackStart = thumbRadiusPx
                    val localTrackEnd = canvasWidth - thumbRadiusPx
                    val localTrackWidth = (localTrackEnd - localTrackStart).coerceAtLeast(0f)

                    // El lambda onDraw DEBE devolver un DrawResult.
                    // Lo hacemos llamando a onDrawWithContent al final,
                    // aunque aquí no dibujamos el contenido original del Spacer (es vacío).
                    // Esto simplemente satisface el tipo de retorno.
                    onDrawWithContent {
                        // this: DrawScope

                        // --- Dibujar Pista Inactiva ---
                        val currentProgressPxEndVisual = localTrackStart + localTrackWidth * normalizedValue
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
                            val activeTrackVisualEnd = currentProgressPxEndVisual - (thumbGapPx * thumbInteractionFraction)

                            if (waveAmplitudePxInternal > 0.01f) {
                                wavePath.reset()
                                val waveStartDrawX = localTrackStart
                                val waveEndDrawX = activeTrackVisualEnd.coerceAtLeast(waveStartDrawX)

                                if (waveEndDrawX > waveStartDrawX) {
                                    wavePath.moveTo(
                                        waveStartDrawX,
                                        localCenterY + waveAmplitudePxInternal * sin(waveFrequency * waveStartDrawX + phaseShift)
                                    )
                                    val waveStep = 1f
                                    var x = waveStartDrawX + waveStep
                                    while (x < waveEndDrawX) {
                                        val wavePhase = waveFrequency * x + phaseShift
                                        val waveY = localCenterY + waveAmplitudePxInternal * sin(wavePhase)
                                        val clampedY = waveY.coerceIn(
                                            localCenterY - waveAmplitudePxInternal - trackHeightPx / 2f,
                                            localCenterY + waveAmplitudePxInternal + trackHeightPx / 2f
                                        )
                                        wavePath.lineTo(x, clampedY)
                                        x += waveStep
                                    }
                                    wavePath.lineTo(
                                        waveEndDrawX,
                                        localCenterY + waveAmplitudePxInternal * sin(waveFrequency * waveEndDrawX + phaseShift)
                                    )
                                    drawPath(
                                        path = wavePath,
                                        color = activeTrackColor,
                                        style = Stroke(width = trackHeightPx, cap = StrokeCap.Round)
                                    )
                                }
                            } else { // Dibujar línea recta
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
                        val currentThumbCenterX = localTrackStart + localTrackWidth * normalizedValue
                        val thumbCurrentWidthPx = lerp(thumbRadiusPx * 2f, trackHeightPx * 1.2f, thumbInteractionFraction)
                        val thumbCurrentHeightPx = lerp(thumbRadiusPx * 2f, thumbLineHeightPxInternal, thumbInteractionFraction)

                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(
                                currentThumbCenterX - thumbCurrentWidthPx / 2f,
                                localCenterY - thumbCurrentHeightPx / 2f
                            ),
                            size = Size(thumbCurrentWidthPx, thumbCurrentHeightPx),
                            cornerRadius = CornerRadius(thumbCurrentWidthPx / 2f)
                        )
                        // No es necesario llamar a drawContent() aquí explícitamente porque
                        // estamos dibujando todo nosotros y no queremos que el Spacer (vacío) se dibuje.
                        // La llamada a onDrawWithContent {} al final del bloque drawWithCache es lo que
                        // devuelve el DrawResult.
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
//    waveAmplitude: Dp = 3.dp,
//    waveFrequency: Float = 0.08f,
//    animationDuration: Int = 2000,
//    hideInactiveTrack: Boolean = true,
//    isPlaying: Boolean = true,
//    thumbLineHeight: Dp = 24.dp
//) {
//    // Estados de interacción para aplicar efectos visuales
//    val isDragged by interactionSource.collectIsDraggedAsState()
//    val isPressed by interactionSource.collectIsPressedAsState()
//    val isInteracting = isDragged || isPressed
//
//    // Animamos la interacción para la transformación del thumb
//    val interactionTransition by animateFloatAsState(
//        targetValue = if (isInteracting) 1f else 0f,
//        animationSpec = tween(
//            durationMillis = 250,
//            easing = FastOutSlowInEasing
//        ),
//        label = "thumb_transition"
//    )
//
//    // Determinamos si debemos mostrar la onda o una línea recta
//    val shouldShowWave = isPlaying && !isInteracting
//
//    // Animamos la amplitud cuando cambia el estado de reproducción o interacción
//    val targetAmplitude = if (shouldShowWave) waveAmplitude else 0.dp
//    val animatedAmplitude by animateDpAsState(
//        targetValue = targetAmplitude,
//        animationSpec = tween(
//            durationMillis = 300,
//            easing = FastOutSlowInEasing
//        ),
//        label = "amplitude_animation"
//    )
//
//    // Animación de la fase de la onda
//    val infiniteTransition = rememberInfiniteTransition(label = "wavy_transition")
//    val phaseShift by infiniteTransition.animateFloat(
//        initialValue = 0f,
//        targetValue = 2 * PI.toFloat(),
//        animationSpec = infiniteRepeatable(
//            animation = tween(durationMillis = animationDuration, easing = LinearEasing),
//            repeatMode = RepeatMode.Restart
//        ),
//        label = "wavy_phase_animation"
//    )
//
//    // Conversión de unidades
//    val trackHeightPx = with(LocalDensity.current) { trackHeight.toPx() }
//    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
//    val waveAmplitudePx = with(LocalDensity.current) { animatedAmplitude.toPx() }
//    val thumbLineHeightPx = with(LocalDensity.current) { thumbLineHeight.toPx() }
//    val thumbGapPx = with(LocalDensity.current) { 4.dp.toPx() }
//
//    val activeTrackColorFilter = remember(activeTrackColor) { ColorFilter.tint(activeTrackColor) } // Ejemplo
//    val inactiveTrackColorFilter = remember(inactiveTrackColor) { ColorFilter.tint(inactiveTrackColor) }
//    val thumbColorFilter = remember(thumbColor) { ColorFilter.tint(thumbColor) }
//
//    // Recordar el Path fuera del onDraw si se usa drawWithCache
//    val wavePath = remember { Path() }
//
//    // Cálculo del valor normalizado para la pista
//    val normalizedValue = if (valueRange.endInclusive == valueRange.start) {
//        0f
//    } else {
//        (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
//    }.coerceIn(0f, 1f)
//
//    // Usar Slider interno para manejar la funcionalidad base y accesibilidad
//    BoxWithConstraints(
//        modifier = modifier
//            .clipToBounds()
//    ) {
//        val maxWidthPx = constraints.maxWidth.toFloat()
//        val maxHeightPx = constraints.maxHeight.toFloat()
//
//        // Calcular posición del thumb
//        val thumbPosition = maxWidthPx * normalizedValue
//
//        // Slider oculto para manejar la interacción y accesibilidad
//        Slider(
//            value = value,
//            onValueChange = onValueChange,
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(max(trackHeight * 2, thumbRadius * 2 + 6.dp)),
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
//        // Dibujar pista personalizada y thumb
//        Canvas(
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(max(trackHeight * 2, thumbLineHeight + 6.dp))
//        ) {
//            val centerY = size.height / 2
//            val trackStart = thumbRadiusPx
//            val trackEnd = size.width - thumbRadiusPx
//            val trackWidth = trackEnd - trackStart
//
//            // Dibujar pista inactiva (solo la parte no recorrida)
//            if (!hideInactiveTrack) {
//                // Dibujar toda la pista inactiva
//                drawLine(
//                    color = inactiveTrackColor,
//                    start = Offset(trackStart, centerY),
//                    end = Offset(trackEnd, centerY),
//                    strokeWidth = trackHeightPx,
//                    cap = StrokeCap.Round
//                )
//            } else {
//                // Dibujar solo la parte no recorrida de la pista inactiva
//                val progressEnd = trackStart + trackWidth * normalizedValue
//                if (progressEnd < trackEnd) {
//                    drawLine(
//                        color = inactiveTrackColor,
//                        start = Offset(progressEnd, centerY),
//                        end = Offset(trackEnd, centerY),
//                        strokeWidth = trackHeightPx,
//                        cap = StrokeCap.Round
//                    )
//                }
//            }
//
//            // Dibujar pista activa con efecto de onda o línea recta según el estado
//            if (normalizedValue > 0) {
//                val progressEnd = trackStart + trackWidth * normalizedValue
//
//                if (waveAmplitudePx > 0) {
//                    // Crear path para la pista de onda
//                    val wavePath = Path().apply {
//                        // Calcular la posición Y inicial con la misma fórmula de onda
//                        val startWavePhase = waveFrequency * trackStart + phaseShift
//                        val startY = centerY + waveAmplitudePx * sin(startWavePhase)
//
//                        // Iniciar en la posición con la forma de la onda
//                        moveTo(trackStart, startY)
//
//                        // Dibujar la onda punto por punto
//                        val step = 1f // Mejor densidad para una onda suave
//                        var x = trackStart + step
//                        while (x <= progressEnd - thumbGapPx * interactionTransition) {
//                            val wavePhase = waveFrequency * x + phaseShift
//
//                            // Factor de intensidad que disminuye hacia el thumb
//                            val intensityFactor = if (isInteracting) {
//                                // Reduce la amplitud cerca del thumb durante la interacción
//                                val distanceToThumb = abs(x - thumbPosition) / trackWidth
//                                min(distanceToThumb * 4, 1f)
//                            } else {
//                                1f
//                            }
//
//                            val waveY = centerY + waveAmplitudePx * sin(wavePhase) * intensityFactor
//
//                            // Asegurar que la onda permanezca dentro de los límites razonables
//                            val clampedY = waveY.coerceIn(
//                                centerY - waveAmplitudePx - trackHeightPx/2,
//                                centerY + waveAmplitudePx + trackHeightPx/2
//                            )
//
//                            lineTo(x, clampedY)
//                            x += step
//                        }
//
//                        // Asegurar que la onda termine en la posición correcta
//                        val endPosition = progressEnd - thumbGapPx * interactionTransition
//                        if (endPosition > trackStart) {
//                            val finalWavePhase = waveFrequency * endPosition + phaseShift
//                            val finalWaveY = centerY + waveAmplitudePx * sin(finalWavePhase)
//
//                            // Asegurar que la última posición esté en la onda
//                            lineTo(endPosition, finalWaveY)
//                        }
//                    }
//
//                    // Dibujar la onda
//                    drawPath(
//                        path = wavePath,
//                        color = activeTrackColor,
//                        style = Stroke(width = trackHeightPx, cap = StrokeCap.Round)
//                    )
//                } else {
//                    // Dibujar una línea recta cuando no hay onda (amplitud = 0)
//                    // Ajustamos el final de la línea activa para respetar el gap cuando hay interacción
//                    val adjustedProgressEnd = progressEnd - thumbGapPx * interactionTransition
//                    if (adjustedProgressEnd > trackStart) {
//                        drawLine(
//                            color = activeTrackColor,
//                            start = Offset(trackStart, centerY),
//                            end = Offset(adjustedProgressEnd, centerY),
//                            strokeWidth = trackHeightPx,
//                            cap = StrokeCap.Round
//                        )
//                    }
//                }
//            }
//
//            // Dibujar thumb con transformación de forma según la interacción
//            val thumbPosition = trackStart + trackWidth * normalizedValue
//
//            // Calculamos los valores para la animación de morphing
//            val thumbWidthPx = androidx.compose.ui.util.lerp(thumbRadiusPx * 2, trackHeightPx * 1.2f, interactionTransition)
//            val thumbHeightPx = androidx.compose.ui.util.lerp(thumbRadiusPx * 2, thumbLineHeightPx, interactionTransition)
//
//            // Dibujamos un rectángulo redondeado que puede morphear de círculo a línea
//            drawRoundRect(
//                color = thumbColor,
//                topLeft = Offset(
//                    thumbPosition - thumbWidthPx / 2,
//                    centerY - thumbHeightPx / 2
//                ),
//                size = Size(thumbWidthPx, thumbHeightPx),
//                cornerRadius = CornerRadius(thumbWidthPx / 2),
//            )
//        }
//    }
//}