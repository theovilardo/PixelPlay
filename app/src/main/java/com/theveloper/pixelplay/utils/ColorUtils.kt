package com.theveloper.pixelplay.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.theveloper.pixelplay.presentation.components.AlbumColorPalette
import com.theveloper.pixelplay.ui.theme.PixelPlayOrange
import com.theveloper.pixelplay.ui.theme.PixelPlayPink
import com.theveloper.pixelplay.ui.theme.PixelPlayPurpleDark
import kotlin.math.abs
import kotlin.math.pow

/**
 * Genera una paleta de colores completa basada en el color del álbum
 * Usa principios de teoría del color para crear una paleta armónica
 */
fun generateAlbumColorPalette(bitmap: Bitmap): AlbumColorPalette {
    val palette = Palette.from(bitmap).generate()

    // Helper para obtener un color o un default
    fun getColor(swatch: Palette.Swatch?, default: Color): Color {
        return swatch?.rgb?.let { Color(it) } ?: default
    }

    // Helper para determinar el color del texto/icono basado en la luminancia del fondo
    fun getOnColor(backgroundColor: Color, lightColor: Color = Color.White, darkColor: Color = Color.Black): Color {
        val r = backgroundColor.red * 255
        val g = backgroundColor.green * 255
        val b = backgroundColor.blue * 255
        val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
        return if (luminance > 0.5) darkColor else lightColor
    }

    // Extracción de colores base
    val dominantColor = getColor(palette.dominantSwatch, Color(0xFF6200EE)) // Default Purple
    val vibrantColor = getColor(palette.vibrantSwatch, dominantColor)
    val mutedColor = getColor(palette.mutedSwatch, dominantColor)

    // Definición de roles de color (simplificado, se puede expandir mucho más)
    val primary = vibrantColor
    val onPrimary = getOnColor(primary)

    // Para secundario, intentar un color diferente pero armonioso
    var secondary = getColor(palette.lightVibrantSwatch, getColor(palette.darkMutedSwatch, primary.copy(alpha = 0.7f)))
    if (abs(primary.toArgb() - secondary.toArgb()) < 10000) { // Si son muy parecidos
        secondary = mutedColor.copy(alpha = 0.8f)
    }
    val onSecondary = getOnColor(secondary)

    // Terciario para acentos
    var tertiary = getColor(palette.darkVibrantSwatch, getColor(palette.lightMutedSwatch, primary.copy(alpha = 0.5f)))
    if (abs(primary.toArgb() - tertiary.toArgb()) < 10000 || abs(secondary.toArgb() - tertiary.toArgb()) < 10000) {
        tertiary = dominantColor.copy(alpha = 0.6f) // Fallback más distintivo
    }
    val onTertiary = getOnColor(tertiary)

    // Superficies
    val surface = primary.copy(alpha = 0.08f).compositeOver(Color.White) // Tono claro del primario para superficie
    val onSurface = getOnColor(surface, darkColor = Color.Black.copy(alpha = 0.87f), lightColor = Color.White.copy(alpha = 0.87f))

    val surfaceVariant = primary.copy(alpha = 0.12f).compositeOver(Color.White)
    val onSurfaceVariant = getOnColor(surfaceVariant, darkColor = Color.Black.copy(alpha = 0.6f), lightColor = Color.White.copy(alpha = 0.6f))

    val outline = onSurface.copy(alpha = 0.12f)


    // Gradiente: usar primario, secundario y terciario si son suficientemente diferentes
    val gradientColors = mutableListOf(primary)
    if (abs(primary.toArgb() - secondary.toArgb()) > 20000) gradientColors.add(secondary) else gradientColors.add(primary.copy(alpha = 0.7f))
    if (abs(gradientColors.last().toArgb() - tertiary.toArgb()) > 20000 && abs(primary.toArgb() - tertiary.toArgb()) > 20000) gradientColors.add(tertiary) else gradientColors.add(secondary.copy(alpha = 0.5f))


    return AlbumColorPalette(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        tertiary = tertiary,
        onTertiary = onTertiary,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        gradient = gradientColors.distinct().take(3).ifEmpty { listOf(PixelPlayPink, PixelPlayOrange, PixelPlayPurpleDark) } // Asegurar al menos 3 colores
    )
}
//fun generateAlbumColorPalette(baseColor: Color): AlbumColorPalette {
//    // Convertir a HSL para manipulaciones de color
//    val hsl = baseColor.toHsl()
//    val hue = hsl[0]
//    val saturation = hsl[1]
//    val lightness = hsl[2]
//
//    // Ajustar saturación y luminosidad para mejor visibilidad
//    val adjustedSaturation = (saturation * 1.2f).coerceIn(0.4f, 0.95f)
//    val adjustedLightness = (lightness * 0.9f).coerceIn(0.15f, 0.85f)
//
//    // Calcular color principal con saturación y luminosidad ajustadas
//    val primary = Color.hsl(
//        hue = hue,
//        saturation = adjustedSaturation,
//        lightness = adjustedLightness
//    )
//
//    // Determinar si se necesita texto claro u oscuro sobre el color principal
//    val isLightPrimary = primary.luminance() > 0.5f
//    val onPrimary = if (isLightPrimary) Color.Black else Color.White
//
//    // Color secundario (análogo +30°)
//    val secondary = Color.hsl(
//        hue = (hue + 30f) % 360f,
//        saturation = (adjustedSaturation * 0.85f).coerceIn(0.3f, 0.9f),
//        lightness = (adjustedLightness * 1.1f).coerceIn(0.25f, 0.75f)
//    )
//
//    // Color terciario (análogo -30°)
//    val tertiary = Color.hsl(
//        hue = (hue - 30f + 360f) % 360f,
//        saturation = (adjustedSaturation * 1.1f).coerceIn(0.4f, 0.95f),
//        lightness = (adjustedLightness * 0.9f).coerceIn(0.2f, 0.7f)
//    )
//
//    // Color de superficie (versión más oscura y menos saturada para fondos)
//    val surface = Color.hsl(
//        hue = hue,
//        saturation = (adjustedSaturation * 0.8f).coerceIn(0.1f, 0.6f),
//        lightness = (adjustedLightness * 0.5f).coerceIn(0.05f, 0.3f)
//    )
//
//    // Generar colores para gradiente
//    val gradientColors = listOf(
//        primary.copy(alpha = 1f),
//        secondary.copy(alpha = 1f),
//        tertiary.copy(alpha = 1f),
//        // Añadir variaciones con luminosidad para más riqueza
//        Color.hsl(
//            hue = (hue + 15f) % 360f,
//            saturation = adjustedSaturation * 0.9f,
//            lightness = adjustedLightness * 0.7f
//        ),
//        Color.hsl(
//            hue = (hue - 15f + 360f) % 360f,
//            saturation = adjustedSaturation * 1.1f,
//            lightness = adjustedLightness * 0.6f
//        )
//    )
//
//    return AlbumColorPalette(
//        primary = primary,
//        onPrimary = onPrimary,
//        secondary = secondary,
//        tertiary = tertiary,
//        surface = surface,
//        gradient = gradientColors
//    )
//}

/**
 * Convierte un Color a valores HSL (Hue, Saturation, Lightness)
 * @return Array de floats con [hue (0-360), saturation (0-1), lightness (0-1)]
 */
fun Color.toHsl(): FloatArray {
    val r = red
    val g = green
    val b = blue

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)

    var h: Float
    val s: Float
    val l = (max + min) / 2f

    if (max == min) {
        h = 0f
        s = 0f
    } else {
        val d = max - min
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)

        h = when (max) {
            r -> (g - b) / d + (if (g < b) 6 else 0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        h /= 6f
    }

    return floatArrayOf(h * 360f, s, l)
}

/**
 * Crea un color HSL
 */
fun Color.Companion.hsl(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1.0f): Color {
    // Implementación de conversión HSL a RGB
    val h = hue / 360f
    val s = saturation
    val l = lightness

    fun hueToRgb(p: Float, q: Float, t: Float): Float {
        var tT = t
        if (tT < 0f) tT += 1f
        if (tT > 1f) tT -= 1f
        if (tT < 1f/6f) return p + (q - p) * 6f * tT
        if (tT < 1f/2f) return q
        if (tT < 2f/3f) return p + (q - p) * (2f/3f - tT) * 6f
        return p
    }

    if (s == 0f) {
        // Achromatic
        return Color(l, l, l, alpha)
    } else {
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        val r = hueToRgb(p, q, h + 1f/3f)
        val g = hueToRgb(p, q, h)
        val b = hueToRgb(p, q, h - 1f/3f)
        return Color(r, g, b, alpha)
    }
}

/**
 * Calcula la luminancia de un color (utilizado para determinar si usar texto claro u oscuro)
 */
fun Color.luminance(): Float {
    // Calcular luminancia según fórmula estándar W3C
    val r = if (red <= 0.03928f) red / 12.92f else ((red + 0.055f) / 1.055f).pow(2.4f)
    val g = if (green <= 0.03928f) green / 12.92f else ((green + 0.055f) / 1.055f).pow(2.4f)
    val b = if (blue <= 0.03928f) blue / 12.92f else ((blue + 0.055f) / 1.055f).pow(2.4f)

    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

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