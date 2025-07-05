package com.theveloper.pixelplay.presentation.components.subcomps

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Un Composable de Glance que ajusta automáticamente el tamaño de la fuente del texto
 * para llenar las dimensiones especificadas.
 *
 * NOTA: A diferencia de Jetpack Compose, Glance requiere que se especifiquen explícitamente
 * el ancho y el alto (`width` y `height`) para que el cálculo funcione.
 *
 * @param text El texto a mostrar.
 * @param modifier El GlanceModifier a aplicar al contenedor.
 * @param style El estilo base del texto. El tamaño de la fuente se sobrescribirá, pero se respetarán
 * propiedades como fontWeight.
 * @param color El color del texto.
 * @param width El ancho exacto del área disponible para el texto.
 * @param height La altura exacta del área disponible para el texto.
 * @param textAlign La alineación del texto.
 * @param minFontSize El tamaño de fuente más pequeño permitido.
 * @param maxFontSize El tamaño de fuente más grande permitido.
 */
@Composable
fun AutoSizingTextGlance(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    style: TextStyle,
    color: ColorProvider,
    width: Dp,
    height: Dp,
    textAlign: TextAlign = TextAlign.Start,
    minFontSize: TextUnit = 8.sp,
    maxFontSize: TextUnit = 100.sp
) {
    val context = LocalContext.current
    val textColor = color.getColor(context).toArgb()
    val density = context.resources.displayMetrics.density

    // Convertir dimensiones Dp a Píxeles
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()

    // Crear el bitmap que contendrá el texto renderizado
    val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Configurar TextPaint para medir y dibujar el texto
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = textColor
        // Aplicar fontWeight del estilo
        this.typeface = when (style.fontWeight) {
            FontWeight.Bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            FontWeight.Medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL) // Requiere API 21+
            else -> Typeface.DEFAULT
        }
    }

    // Mapear TextAlign de Glance a Layout.Alignment de Android
    val alignment = when (textAlign) {
        TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
        TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL // Start, Left
    }


    // --- Búsqueda binaria para el tamaño de fuente óptimo ---
    var lowerBound = minFontSize.value
    var upperBound = maxFontSize.value
    var bestSize = lowerBound

    // Realizar la búsqueda solo si el área es válida
    if (widthPx > 0 && heightPx > 0) {
        while (lowerBound <= upperBound) {
            val mid = (lowerBound + upperBound) / 2
            if (mid <= 0) break // Evitar tamaños de fuente no válidos

            textPaint.textSize = mid * density

            // StaticLayout es la herramienta de Android para manejar texto multilínea y con saltos de línea.
            val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, widthPx)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()

            // Si el texto cabe en la altura, es un candidato válido. Intentamos un tamaño mayor.
            if (staticLayout.height <= heightPx) {
                bestSize = mid
                lowerBound = mid + 0.1f
            } else {
                // Si no cabe, necesitamos un tamaño más pequeño.
                upperBound = mid - 0.1f
            }
        }
    }
    // --- Fin de la búsqueda binaria ---

    // Dibujar el texto final en el canvas con el mejor tamaño de fuente encontrado
    textPaint.textSize = bestSize * density
    val finalLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, widthPx)
        .setAlignment(alignment)
        .setLineSpacing(0f, 1f)
        .setIncludePad(false)
        .build()

    finalLayout.draw(canvas)

    // Mostrar el bitmap renderizado en un Composable Image
    Box(
        modifier = modifier.size(width, height),
        contentAlignment = Alignment.CenterStart
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = text, // Usar el texto como descripción de contenido
            modifier = GlanceModifier
                .fillMaxSize()
                //.size(width, height)
        )
    }
}