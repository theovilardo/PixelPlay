package com.theveloper.pixelplay.ui.glancewidget.subcomponents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Un LinearProgressIndicator para Glance que simula un efecto de onda generando un Bitmap.
 *
 * IMPORTANTE: La animación de la onda requiere un mecanismo externo (ej. CoroutineWorker)
 * que llame a `GlanceAppWidget.update` periódicamente con un `phaseShift` actualizado.
 *
 * @param progress El progreso actual (entre 0.0f y 1.0f).
 * @param isPlaying Si el contenido asociado se está reproduciendo (para mostrar la onda).
 * @param modifier El GlanceModifier a aplicar.
 * @param height La altura total del componente. Es importante para que ocupe el espacio correcto.
 * @param phaseShift El desplazamiento de fase para la animación de la onda (de 0 a 2*PI).
 * Debe ser actualizado externamente para crear la ilusión de movimiento.
 * @param activeTrackColor Color de la parte activa de la pista (la onda/parte recorrida).
 * @param trackBackgroundColor Color del fondo de la pista (la parte no recorrida).
 * @param thumbColor Color del círculo al final del progreso.
 * @param hideInactiveTrackPortion Si es `true`, la parte del fondo del track que ya ha sido
 * recorrida no se dibujará.
 * @param trackHeight Altura de la línea de la pista.
 * @param thumbRadius Radio del círculo.
 * @param waveAmplitude Amplitud de la onda cuando `isPlaying` es true.
 * @param waveFrequency Frecuencia de la onda (más alto = más ondulaciones).
 */
@Composable
fun WavyLinearProgressIndicator(
    progress: Float,
    isPlaying: Boolean,
    modifier: GlanceModifier = GlanceModifier,
    height: Dp = 24.dp, // Parámetro para controlar la altura
    phaseShift: Float = 0f,
    activeTrackColor: Color = Color(0xFF6200EE),
    trackBackgroundColor: Color = Color(0xFF6200EE).copy(alpha = 0.24f),
    thumbColor: Color = Color(0xFF6200EE),
    hideInactiveTrackPortion: Boolean = true, // Nuevo parámetro
    trackHeight: Dp = 6.dp,
    thumbRadius: Dp = 8.dp,
    waveAmplitude: Dp = 3.dp,
    waveFrequency: Float = 0.08f
) {
    val context = LocalContext.current
    val widgetSize = LocalSize.current
    val density = context.resources.displayMetrics.density
    val bitmapWidthPx = (widgetSize.width.value * density).toInt().coerceAtLeast(1)
    // El componente en Glance es simplemente una Imagen. El Bitmap se genera bajo demanda.
    Image(
        provider = ImageProvider(
            createWavyProgressBitmap(
                context = context, // Se pasa el contexto para la densidad
                progress = progress,
                isPlaying = isPlaying,
                phaseShift = phaseShift,
                activeTrackColor = activeTrackColor,
                trackBackgroundColor = trackBackgroundColor,
                thumbColor = thumbColor,
                hideInactiveTrackPortion = hideInactiveTrackPortion,
                trackHeight = trackHeight,
                thumbRadius = thumbRadius,
                waveAmplitude = waveAmplitude,
                waveFrequency = waveFrequency,
                bitmapWidth = bitmapWidthPx,
                bitmapHeight = (height.value * context.resources.displayMetrics.density).toInt()
            )
        ),
        contentDescription = "Barra de progreso con valor ${"%.0f".format(progress * 100)}%",
        modifier = modifier.height(height) // Se aplica la altura al modifier
    )
}

/**
 * Función de utilidad que genera un Bitmap con el dibujo de la barra de progreso ondulada.
 * Esta función adapta la lógica del WavyMusicSlider original para dibujar en un Canvas de Android.
 */
private fun createWavyProgressBitmap(
    context: Context,
    progress: Float,
    isPlaying: Boolean,
    phaseShift: Float,
    activeTrackColor: Color,
    trackBackgroundColor: Color,
    thumbColor: Color,
    hideInactiveTrackPortion: Boolean,
    trackHeight: Dp,
    thumbRadius: Dp,
    waveAmplitude: Dp,
    waveFrequency: Float,
    bitmapWidth: Int = 1000, // Ancho fijo para la resolución del bitmap
    bitmapHeight: Int
): Bitmap {
    val bmp = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Usar la densidad real del dispositivo para convertir Dp a Px.
    val density = context.resources.displayMetrics.density
    val trackHeightPx = trackHeight.value * density
    val thumbRadiusPx = thumbRadius.value * density
    val waveAmplitudePx = if (isPlaying) waveAmplitude.value * density else 0f

    val centerY = canvas.height / 2f
    val trackStart = thumbRadiusPx
    val trackEnd = canvas.width - thumbRadiusPx
    val trackWidth = trackEnd - trackStart

    // Configurar Paints
    val activePaint = Paint().apply {
        color = activeTrackColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = trackHeightPx
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    val inactivePaint = Paint().apply {
        color = trackBackgroundColor.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = trackHeightPx
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    val thumbPaint = Paint().apply {
        color = thumbColor.toArgb()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val activeTrackEndPx = trackStart + trackWidth * progress

    // Dibujar pista de fondo
    if (hideInactiveTrackPortion) {
        // Solo dibujar la parte que queda por recorrer
        canvas.drawLine(activeTrackEndPx, centerY, trackEnd, centerY, inactivePaint)
    } else {
        // Dibujar toda la pista de fondo
        canvas.drawLine(trackStart, centerY, trackEnd, centerY, inactivePaint)
    }

    // Dibujar pista activa (onda o línea)
    if (progress > 0) {
        if (waveAmplitudePx > 0.1f) {
            val wavePath = Path()
            wavePath.moveTo(
                trackStart,
                centerY + waveAmplitudePx * sin(waveFrequency * trackStart + phaseShift)
            )
            val step = 5f // Aumentar el paso para mejorar el rendimiento
            var x = trackStart + step
            while (x < activeTrackEndPx) {
                val waveY = centerY + waveAmplitudePx * sin(waveFrequency * x + phaseShift)
                wavePath.lineTo(x, waveY)
                x += step
            }
            wavePath.lineTo(
                activeTrackEndPx,
                centerY + waveAmplitudePx * sin(waveFrequency * activeTrackEndPx + phaseShift)
            )
            canvas.drawPath(wavePath, activePaint)
        } else {
            canvas.drawLine(trackStart, centerY, activeTrackEndPx, centerY, activePaint)
        }
    }

    // Dibujar Thumb
    val thumbX = activeTrackEndPx
    canvas.drawCircle(thumbX, centerY, thumbRadiusPx, thumbPaint)

    return bmp
}
