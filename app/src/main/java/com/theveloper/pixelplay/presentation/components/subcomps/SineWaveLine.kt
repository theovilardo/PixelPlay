package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.sin

/**
 * Composable que dibuja una línea horizontal con ondulación senoidal.
 *
 * @param modifier Modificador para el Composable
 * @param color Color de la línea
 * @param alpha Opacidad (0f..1f)
 * @param strokeWidth Grosor de la línea (Dp)
 * @param amplitude Amplitud de la onda (Dp) — la altura máxima desde el centro
 * @param waves Número de ondas completas a lo largo del ancho (ej: 1f = una onda)
 * @param phase Desplazamiento de fase (radianes). Útil para animación.
 * @param samples Cantidad de puntos usados para dibujar la curva (más = más suave)
 * @param cap Tipo de extremo de la línea (Round, Butt, Square)
 */
@Composable
fun SineWaveLine(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    alpha: Float = 1f,
    strokeWidth: Dp = 2.dp,
    amplitude: Dp = 8.dp,
    waves: Float = 2f,
    phase: Float = 0f,
    samples: Int = 400,
    cap: StrokeCap = StrokeCap.Round
) {
    val density = LocalDensity.current

    // Convertimos dp a px
    val strokePx = with(density) { strokeWidth.toPx() }
    val ampPx = with(density) { amplitude.toPx() }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        if (w <= 0f || samples < 2) return@Canvas

        // Construimos el path senoidal
        val path = Path().apply {
            var x = 0f
            val step = w / (samples - 1)
            moveTo(0f, centerY + (ampPx * sin(phase))) // inicio
            var i = 0
            while (i < samples) {
                x = i * step
                // theta recorre 0..(2π * waves)
                val theta = (x / w) * (2f * PI.toFloat() * waves) + phase
                val y = centerY + ampPx * sin(theta)
                lineTo(x, y)
                i++
            }
        }

        drawPath(
            path = path,
            color = color.copy(alpha = alpha),
            style = Stroke(
                width = strokePx,
                cap = cap,
                join = StrokeJoin.Round
            ),
            alpha = 1f // ya aplicamos alpha al color
        )
    }
}

/**
 * Ejemplo de uso estático:
 *
 * SineWaveLine(
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .height(28.dp),
 *     color = Color(0xFF00AEEF),
 *     alpha = 0.95f,
 *     strokeWidth = 3.dp,
 *     amplitude = 10.dp,
 *     waves = 1.6f
 * )
 */