package com.theveloper.pixelplay.ui.theme

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import kotlin.math.max
import androidx.core.graphics.scale

private fun Color.toHct(): Triple<Float, Float, Float> {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(red.toByteInt(), green.toByteInt(), blue.toByteInt(), hsl)
    return Triple(hsl[0], hsl[1], hsl[2])
}

private fun hctToColor(hue: Float, chroma: Float, tone: Float): Color {
    val hsl = floatArrayOf(hue.coerceIn(0f, 360f), chroma.coerceIn(0f, 1f), tone.coerceIn(0f, 1f))
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.tone(targetTone: Int): Color {
    val (_, chroma, _) = this.toHct()
    return hctToColor(this.toHct().first, chroma, targetTone / 100f)
}
private fun Color.withChroma(targetChroma: Float): Color {
    val (hue, _, tone) = this.toHct()
    return hctToColor(hue, targetChroma.coerceIn(0f,1f), tone)
}

private fun Color.withMinChroma(minChroma: Float, maxChroma: Float = 0.5f): Color {
    val (hue, chroma, tone) = this.toHct()
    val boostedChroma = max(chroma, minChroma).coerceAtMost(maxChroma)
    val balancedTone = tone.coerceIn(0.32f, 0.82f)
    return hctToColor(hue, boostedChroma, balancedTone)
}

private fun Float.toByteInt(): Int = (this * 255f).toInt()


private val extractedColorCache = LruCache<Int, Color>(20)

// --- Optimized Color Scheme Generation ---
fun extractSeedColor(bitmap: Bitmap): Color {
    val bitmapHash = bitmap.hashCode()

    extractedColorCache.get(bitmapHash)?.let { return it }

    val scaledBitmap = if (bitmap.width > 200 || bitmap.height > 200) {
        val scale = 200f / max(bitmap.width, bitmap.height)
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()
        bitmap.scale(scaledWidth, scaledHeight)
    } else {
        bitmap
    }

    val palette = Palette.Builder(scaledBitmap)
        .maximumColorCount(16)
        .resizeBitmapArea(0)
        .clearFilters()
        .generate()

    val color = palette.vibrantSwatch?.rgb?.let { Color(it) }
        ?: palette.mutedSwatch?.rgb?.let { Color(it) }
        ?: palette.dominantSwatch?.rgb?.let { Color(it) }
        ?: DarkColorScheme.primary // Fallback
    
    // Store in cache
    extractedColorCache.put(bitmapHash, color)

    if (scaledBitmap != bitmap) {
        scaledBitmap.recycle()
    }
    
    return color
}

fun generateColorSchemeFromSeed(seedColor: Color): ColorSchemePair {

    val tunedSeed = seedColor.withMinChroma(minChroma = 0.38f, maxChroma = 0.65f)

    // --- Tonal Palettes ---
    // Primary Tones
    val primary10 = tunedSeed.tone(10)
    val primary18 = tunedSeed.tone(18)
    val primary28 = tunedSeed.tone(28)
    val primary36 = tunedSeed.tone(36)
    val primary52 = tunedSeed.tone(52)
    val primary64 = tunedSeed.tone(64) // Primary Light (more luminous than dark theme)
    val primary78 = tunedSeed.tone(78)
    val primary86 = tunedSeed.tone(86)
    val primary92 = tunedSeed.tone(92)
    val primary96 = tunedSeed.tone(96)
    val primary100= tunedSeed.tone(100)


    // Secondary Tones (Shift hue, adjust chroma)
    val secondarySeed = hctToColor((tunedSeed.toHct().first + 45f) % 360f, 0.3f, tunedSeed.toHct().third).withMinChroma(0.26f, maxChroma = 0.5f)
    val secondary10 = secondarySeed.tone(10)
    val secondary18 = secondarySeed.tone(18)
    val secondary32 = secondarySeed.tone(32)
    val secondary40 = secondarySeed.tone(40)
    val secondary62 = secondarySeed.tone(62)
    val secondary78 = secondarySeed.tone(78)
    val secondary88 = secondarySeed.tone(88)
    val secondary94 = secondarySeed.tone(94)
    val secondary100= secondarySeed.tone(100)

    // Tertiary Tones (Shift hue differently, adjust chroma)
    val tertiarySeed = hctToColor((tunedSeed.toHct().first + 120f) % 360f, 0.36f, tunedSeed.toHct().third).withMinChroma(0.28f, maxChroma = 0.55f)
    val tertiary10 = tertiarySeed.tone(10)
    val tertiary18 = tertiarySeed.tone(18)
    val tertiary32 = tertiarySeed.tone(32)
    val tertiary40 = tertiarySeed.tone(40)
    val tertiary62 = tertiarySeed.tone(62)
    val tertiary78 = tertiarySeed.tone(78)
    val tertiary88 = tertiarySeed.tone(88)
    val tertiary94 = tertiarySeed.tone(94)
    val tertiary100= tertiarySeed.tone(100)

    // Neutral Tones (Very low chroma from seed)
    val neutralSeed = tunedSeed.withChroma(0.08f)
    val neutral6 = neutralSeed.tone(6) // deeper dark surface
    val neutral10 = neutralSeed.tone(10) // Surface Dark, Background Dark
    val neutral16 = neutralSeed.tone(16)
    val neutral22 = neutralSeed.tone(22)
    val neutral32 = neutralSeed.tone(32) // SurfaceVariant Dark
    val neutral78 = neutralSeed.tone(78)
    val neutral84 = neutralSeed.tone(84)
    val neutral88 = neutralSeed.tone(88)
    val neutral92 = neutralSeed.tone(92)
    val neutral96 = neutralSeed.tone(96)
    val neutral98 = neutralSeed.tone(98)
    val neutral100= neutralSeed.tone(100)


    // Light Color Scheme
    val lightScheme = lightColorScheme(
        primary = primary64,
        onPrimary = primary100,
        primaryContainer = primary92,
        onPrimaryContainer = primary18,
        secondary = secondary62,
        onSecondary = secondary100,
        secondaryContainer = secondary94,
        onSecondaryContainer = secondary18,
        tertiary = tertiary62,
        onTertiary = tertiary100,
        tertiaryContainer = tertiary94,
        onTertiaryContainer = tertiary18,
        error = Color(0xFFBA1A1A), // M3 Defaults
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = neutral98,
        onBackground = neutral10,
        surface = neutral96,
        onSurface = neutral10,
        surfaceVariant = neutral88,
        onSurfaceVariant = neutral32,
        outline = neutralSeed.tone(58),
        inverseOnSurface = neutral96,
        inverseSurface = neutral22,
        inversePrimary = primary78,
        surfaceTint = primary64,
        outlineVariant = neutral84,
        scrim = Color.Black
    )

    // Dark Color Scheme
    val darkScheme = darkColorScheme(
        primary = primary78,
        onPrimary = primary22,
        primaryContainer = primary28,
        onPrimaryContainer = primary92,
        secondary = secondary78,
        onSecondary = secondary22,
        secondaryContainer = secondary32,
        onSecondaryContainer = secondary88,
        tertiary = tertiary78,
        onTertiary = tertiary22,
        tertiaryContainer = tertiary32,
        onTertiaryContainer = tertiary88,
        error = Color(0xFFFFB4AB), // M3 Defaults
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = neutral6, // deeper background for stronger separation
        onBackground = neutral92,
        surface = neutral10.copy(alpha = (neutral10.toHct().third + 0.08f).coerceIn(0f,1f)), // Slightly lighter surface
        onSurface = neutral92,
        surfaceVariant = neutral32,
        onSurfaceVariant = neutralSeed.tone(82),
        outline = neutralSeed.tone(62),
        inverseOnSurface = neutral22,
        inverseSurface = neutral92,
        inversePrimary = primary52,
        surfaceTint = primary78,
        outlineVariant = neutral32,
        scrim = Color.Black
    )
    return ColorSchemePair(lightScheme, darkScheme)
}