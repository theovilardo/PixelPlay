package com.theveloper.pixelplay.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import androidx.core.graphics.createBitmap

/**
 * Converts a hex color string to a Compose Color object.
 *
 * @param hex The hex color string (e.g., "#FF0000" or "FF0000").
 * @param defaultColor The color to return if the hex string is null or invalid.
 * @return The Compose Color object.
 */
fun hexToColor(hex: String?, defaultColor: Color = Color.Gray): Color {
    if (hex == null) return defaultColor
    val colorString = if (hex.startsWith("#")) hex.substring(1) else hex
    return try {
        Color(android.graphics.Color.parseColor("#$colorString"))
    } catch (e: IllegalArgumentException) {
        defaultColor
    }
}

/**
 * Genera un Bitmap escalable (9-patch) que imita el comportamiento de CornerBasedShape/RoundedCornerShape de Compose.
 *
 * Esta versión final utiliza un lienzo de referencia con el aspect ratio correcto y una lógica
 * de escalado completa para prevenir la deformación en formas rectangulares, produciendo
 * un resultado geométricamente correcto y visualmente consistente.
 *
 * @param context El Contexto de la aplicación.
 * @param color El color de fondo.
 * @param topLeft El radio de la esquina superior izquierda en Dp.
 * @param topRight El radio de la esquina superior derecha en Dp.
 * @param bottomLeft El radio de la esquina inferior izquierda en Dp.
 * @param bottomRight El radio de la esquina inferior derecha en Dp.
 * @param width El ancho opcional para guiar la proporción de la forma.
 * @param height La altura opcional para guiar la proporción de la forma.
 * @return Un Bitmap formateado como 9-patch, listo para ser usado como fondo escalable.
 */
fun createScalableBackgroundBitmap(
    context: Context,
    color: Color,
    topLeft: Dp,
    topRight: Dp,
    bottomLeft: Dp,
    bottomRight: Dp,
    width: Dp?,
    height: Dp?
): Bitmap {
    // 1. Convertir Dp a Píxeles usando el context para consistencia visual.
    val displayMetrics = context.resources.displayMetrics
    val tlPx = topLeft.value * displayMetrics.density
    val trPx = topRight.value * displayMetrics.density
    val blPx = bottomLeft.value * displayMetrics.density
    val brPx = bottomRight.value * displayMetrics.density

    // 2. Definir un lienzo de referencia para el cálculo del aspect ratio.
    val refWidth: Float
    val refHeight: Float
    val defaultSize = 200f // Un tamaño de referencia arbitrario si no se especifican dimensiones.

    when {
        width != null && height != null && width > 0.dp && height > 0.dp -> {
            refWidth = width.value * displayMetrics.density
            refHeight = height.value * displayMetrics.density
        }
        width != null && width > 0.dp -> {
            refWidth = width.value * displayMetrics.density
            refHeight = defaultSize // Usar un valor neutro si la altura es desconocida
        }
        height != null && height > 0.dp -> {
            refHeight = height.value * displayMetrics.density
            refWidth = defaultSize // Usar un valor neutro si el ancho es desconocido
        }
        else -> {
            refWidth = defaultSize
            refHeight = defaultSize
        }
    }

    // 3. Aplicar la lógica de escalado completa, verificando los 4 bordes.
    var scale = 1.0f
    val topSum = tlPx + trPx
    val bottomSum = blPx + brPx
    val leftSum = tlPx + blPx
    val rightSum = trPx + brPx

    if (topSum > refWidth && topSum > 0f) scale = min(scale, refWidth / topSum)
    if (bottomSum > refWidth && bottomSum > 0f) scale = min(scale, refWidth / bottomSum)
    if (leftSum > refHeight && leftSum > 0f) scale = min(scale, refHeight / leftSum)
    if (rightSum > refHeight && rightSum > 0f) scale = min(scale, refHeight / rightSum)

    // 4. Aplicar la escala para obtener los radios finales y geométricamente correctos.
    val finalTl = tlPx * scale
    val finalTr = trPx * scale
    val finalBl = blPx * scale
    val finalBr = brPx * scale

    // 5. Calcular el tamaño del bitmap 9-patch para que sea lo más pequeño posible.
    val leftUnstretchable = finalTl.coerceAtLeast(finalBl)
    val rightUnstretchable = finalTr.coerceAtLeast(finalBr)
    val topUnstretchable = finalTl.coerceAtLeast(finalTr)
    val bottomUnstretchable = finalBl.coerceAtLeast(finalBr)

    val stretch = 2f
    val border = 1f

    val bitmapWidth = (border + leftUnstretchable + stretch + rightUnstretchable + border).toInt()
    val bitmapHeight = (border + topUnstretchable + stretch + bottomUnstretchable + border).toInt()

    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // 6. Dibujar la forma redondeada dentro del bitmap.
    val rect = RectF(border, border, bitmapWidth - border, bitmapHeight - border)
    val radii = floatArrayOf(finalTl, finalTl, finalTr, finalTr, finalBr, finalBr, finalBl, finalBl)
    val path = Path().apply { addRoundRect(rect, radii, Path.Direction.CW) }
    val paint = Paint().apply { isAntiAlias = true; this.color = color.toArgb() }
    canvas.drawPath(path, paint)

    // 7. Dibujar los marcadores de la 9-patch para definir las áreas de estiramiento.
    val markerPaint = Paint().apply { this.color = android.graphics.Color.BLACK }
    canvas.drawRect(border + leftUnstretchable, 0f, bitmapWidth - border - rightUnstretchable, 1f, markerPaint)
    canvas.drawRect(0f, border + topUnstretchable, 1f, bitmapHeight - border - bottomUnstretchable, markerPaint)

    return bitmap
}

///**
// * Genera un Bitmap escalable (9-patch) que imita el comportamiento de CornerBasedShape/RoundedCornerShape de Compose.
// *
// * Esta versión final utiliza un lienzo de tamaño fijo como referencia para calcular un factor de
// * escala uniforme, previniendo la deformación de las esquinas y produciendo formas
// * geométricamente correctas y consistentes que se estiran para llenar cualquier contenedor.
// *
// * @param context El Contexto de la aplicación.
// * @param color El color de fondo.
// * @param topLeft El radio de la esquina superior izquierda en Dp.
// * @param topRight El radio de la esquina superior derecha en Dp.
// * @param bottomLeft El radio de la esquina inferior izquierda en Dp.
// * @param bottomRight El radio de la esquina inferior derecha en Dp.
// * @return Un Bitmap formateado como 9-patch, listo para ser usado como fondo escalable.
// */
//fun createScalableBackgroundBitmap(
//    context: Context,
//    color: Color,
//    topLeft: Dp,
//    topRight: Dp,
//    bottomLeft: Dp,
//    bottomRight: Dp
//): Bitmap {
//    // 1. Convertir Dp a Píxeles usando el context para consistencia visual.
//    val displayMetrics = context.resources.displayMetrics
//    val tlPx = topLeft.value * displayMetrics.density
//    val trPx = topRight.value * displayMetrics.density
//    val blPx = bottomLeft.value * displayMetrics.density
//    val brPx = bottomRight.value * displayMetrics.density
//
//    // 2. Definir un lienzo de referencia de tamaño fijo.
//    //    Esto proporciona la restricción de tamaño externa que faltaba,
//    //    emulando el parámetro `size` en `createOutline` de Compose.
//    val canvasSize = 200f
//
//    // 3. Calcular el factor de escala uniforme, imitando la lógica de RoundedCornerShape.
//    var scale = 1.0f
//    val topSum = tlPx + trPx
//    val bottomSum = blPx + brPx
//    val leftSum = tlPx + blPx
//    val rightSum = trPx + brPx
//
//    if (topSum > canvasSize && topSum != 0f) scale = min(scale, canvasSize / topSum)
//    if (bottomSum > canvasSize && bottomSum != 0f) scale = min(scale, canvasSize / bottomSum)
//    if (leftSum > canvasSize && leftSum != 0f) scale = min(scale, canvasSize / leftSum)
//    if (rightSum > canvasSize && rightSum != 0f) scale = min(scale, canvasSize / rightSum)
//
//    // 4. Aplicar la escala para obtener los radios finales y geométricamente correctos.
//    val finalTl = tlPx * scale
//    val finalTr = trPx * scale
//    val finalBl = blPx * scale
//    val finalBr = brPx * scale
//
//    // 5. Crear el bitmap. Añadimos 2 píxeles para el borde de la 9-patch.
//    val bitmapSize = (canvasSize + 2).toInt()
//    val bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)
//    val canvas = android.graphics.Canvas(bitmap)
//
//    // 6. Dibujar la forma redondeada dentro del lienzo.
//    val rect = RectF(1f, 1f, canvasSize + 1f, canvasSize + 1f)
//    val radii = floatArrayOf(finalTl, finalTl, finalTr, finalTr, finalBr, finalBr, finalBl, finalBl)
//    val path = Path().apply { addRoundRect(rect, radii, Path.Direction.CW) }
//    val paint = Paint().apply { isAntiAlias = true; this.color = color.toArgb() }
//    canvas.drawPath(path, paint)
//
//    // 7. Dibujar los marcadores de la 9-patch para definir las áreas de estiramiento.
//    val markerPaint = Paint().apply { this.color = android.graphics.Color.BLACK }
//    val leftUnstretchable = finalTl.coerceAtLeast(finalBl)
//    val rightUnstretchable = finalTr.coerceAtLeast(finalBr)
//    val topUnstretchable = finalTl.coerceAtLeast(finalTr)
//    val bottomUnstretchable = finalBl.coerceAtLeast(finalBr)
//
//    // Marcador de estiramiento horizontal (en el borde superior)
//    canvas.drawRect(
//        1f + leftUnstretchable,
//        0f,
//        bitmapSize - 1f - rightUnstretchable,
//        1f,
//        markerPaint
//    )
//    // Marcador de estiramiento vertical (en el borde izquierdo)
//    canvas.drawRect(
//        0f,
//        1f + topUnstretchable,
//        1f,
//        bitmapSize - 1f - bottomUnstretchable,
//        markerPaint
//    )
//
//    return bitmap
//}