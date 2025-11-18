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

    // Keep a vivid seed for light and a slightly deeper variant for dark to preserve contrast
    val lightSeed = seedColor.withMinChroma(minChroma = 0.46f, maxChroma = 0.72f)
    val darkSeed = seedColor.withMinChroma(minChroma = 0.36f, maxChroma = 0.6f)

    // --- Tonal Palettes ---
    // Primary Tones
    val lightPrimary48 = lightSeed.tone(48)
    val lightPrimary56 = lightSeed.tone(56)
    val lightPrimary64 = lightSeed.tone(64)
    val lightPrimary76 = lightSeed.tone(76)
    val lightPrimary84 = lightSeed.tone(84)
    val lightPrimary92 = lightSeed.tone(92)

    val darkPrimary18 = darkSeed.tone(18)
    val darkPrimary26 = darkSeed.tone(26)
    val darkPrimary36 = darkSeed.tone(36)
    val darkPrimary52 = darkSeed.tone(52)
    val darkPrimary64 = darkSeed.tone(64)
    val darkPrimary78 = darkSeed.tone(78)
    val darkPrimary92 = darkSeed.tone(92)

    // Secondary Tones (Shift hue, adjust chroma)
    val secondarySeed = hctToColor((lightSeed.toHct().first + 38f) % 360f, 0.36f, lightSeed.toHct().third)
        .withMinChroma(0.32f, maxChroma = 0.54f)
    val lightSecondary46 = secondarySeed.tone(46)
    val lightSecondary54 = secondarySeed.tone(54)
    val lightSecondary64 = secondarySeed.tone(64)
    val lightSecondary76 = secondarySeed.tone(76)
    val lightSecondary86 = secondarySeed.tone(86)
    val lightSecondary94 = secondarySeed.tone(94)

    val darkSecondary22 = secondarySeed.tone(22)
    val darkSecondary30 = secondarySeed.tone(30)
    val darkSecondary42 = secondarySeed.tone(42)
    val darkSecondary58 = secondarySeed.tone(58)
    val darkSecondary70 = secondarySeed.tone(70)
    val darkSecondary82 = secondarySeed.tone(82)

    // Tertiary Tones (Shift hue differently, adjust chroma)
    val tertiarySeed = hctToColor((lightSeed.toHct().first + 115f) % 360f, 0.38f, lightSeed.toHct().third)
        .withMinChroma(0.3f, maxChroma = 0.56f)
    val lightTertiary48 = tertiarySeed.tone(48)
    val lightTertiary58 = tertiarySeed.tone(58)
    val lightTertiary70 = tertiarySeed.tone(70)
    val lightTertiary82 = tertiarySeed.tone(82)
    val lightTertiary90 = tertiarySeed.tone(90)
    val lightTertiary96 = tertiarySeed.tone(96)

    val darkTertiary22 = tertiarySeed.tone(22)
    val darkTertiary32 = tertiarySeed.tone(32)
    val darkTertiary44 = tertiarySeed.tone(44)
    val darkTertiary58 = tertiarySeed.tone(58)
    val darkTertiary72 = tertiarySeed.tone(72)
    val darkTertiary84 = tertiarySeed.tone(84)

    // Neutral Tones (Very low chroma from seed)
    val lightNeutralSeed = lightSeed.withChroma(0.1f)
    val lightNeutral4 = lightNeutralSeed.tone(4)
    val lightNeutral10 = lightNeutralSeed.tone(10)
    val lightNeutral16 = lightNeutralSeed.tone(16)
    val lightNeutral22 = lightNeutralSeed.tone(22)
    val lightNeutral30 = lightNeutralSeed.tone(30)
    val lightNeutral84 = lightNeutralSeed.tone(84)
    val lightNeutral88 = lightNeutralSeed.tone(88)
    val lightNeutral92 = lightNeutralSeed.tone(92)
    val lightNeutral95 = lightNeutralSeed.tone(95)
    val lightNeutral98 = lightNeutralSeed.tone(98)

    val darkNeutralSeed = darkSeed.withChroma(0.08f)
    val darkNeutral6 = darkNeutralSeed.tone(6) // deeper dark surface
    val darkNeutral10 = darkNeutralSeed.tone(10) // Surface Dark, Background Dark
    val darkNeutral16 = darkNeutralSeed.tone(16)
    val darkNeutral22 = darkNeutralSeed.tone(22)
    val darkNeutral32 = darkNeutralSeed.tone(32) // SurfaceVariant Dark
    val darkNeutral78 = darkNeutralSeed.tone(78)
    val darkNeutral84 = darkNeutralSeed.tone(84)
    val darkNeutral88 = darkNeutralSeed.tone(88)
    val darkNeutral92 = darkNeutralSeed.tone(92)


    // Light Color Scheme
    val lightScheme = lightColorScheme(
        primary = lightPrimary64,
        onPrimary = lightNeutral4,
        primaryContainer = lightPrimary92,
        onPrimaryContainer = lightPrimary56,
        secondary = lightSecondary64,
        onSecondary = lightNeutral4,
        secondaryContainer = lightSecondary94,
        onSecondaryContainer = lightSecondary54,
        tertiary = lightTertiary70,
        onTertiary = lightNeutral4,
        tertiaryContainer = lightTertiary96,
        onTertiaryContainer = lightTertiary58,
        error = Color(0xFFBA1A1A), // M3 Defaults
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = lightNeutral98,
        onBackground = lightNeutral22,
        surface = lightNeutral95,
        onSurface = lightNeutral22,
        surfaceVariant = lightNeutral84,
        onSurfaceVariant = lightNeutral30,
        outline = lightNeutral30,
        inverseOnSurface = lightNeutral95,
        inverseSurface = lightNeutral22,
        inversePrimary = lightPrimary76,
        surfaceTint = lightPrimary64,
        outlineVariant = lightNeutral88,
        scrim = Color.Black
    )

    // Dark Color Scheme
    val darkScheme = darkColorScheme(
        primary = darkPrimary78,
        onPrimary = darkPrimary18,
        primaryContainer = darkPrimary26,
        onPrimaryContainer = darkPrimary92,
        secondary = darkSecondary70,
        onSecondary = darkSecondary22,
        secondaryContainer = darkSecondary42,
        onSecondaryContainer = darkSecondary82,
        tertiary = darkTertiary72,
        onTertiary = darkTertiary22,
        tertiaryContainer = darkTertiary44,
        onTertiaryContainer = darkTertiary84,
        error = Color(0xFFFFB4AB), // M3 Defaults
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = darkNeutral6, // deeper background for stronger separation
        onBackground = darkNeutral92,
        surface = darkNeutral10,
        onSurface = darkNeutral92,
        surfaceVariant = darkNeutral32,
        onSurfaceVariant = darkNeutral78,
        outline = darkNeutral84,
        inverseOnSurface = darkNeutral22,
        inverseSurface = darkNeutral88,
        inversePrimary = darkPrimary52,
        surfaceTint = darkPrimary78,
        outlineVariant = darkNeutral32,
        scrim = Color.Black
    )
    return ColorSchemePair(lightScheme, darkScheme)
}
