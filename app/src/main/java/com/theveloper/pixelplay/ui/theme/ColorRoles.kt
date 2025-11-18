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

private fun Color.withMinChroma(minChroma: Float): Color {
    val (hue, chroma, tone) = this.toHct()
    val boostedChroma = max(chroma, minChroma).coerceAtMost(0.5f)
    val balancedTone = tone.coerceIn(0.32f, 0.8f)
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

    val tunedSeed = seedColor.withMinChroma(0.26f)

    // --- Tonal Palettes ---
    // Primary Tones
    val primary10 = tunedSeed.tone(10)
    val primary20 = tunedSeed.tone(20)
    val primary30 = tunedSeed.tone(30)
    val primary45 = tunedSeed.tone(45) // Primary Light
    val primary80 = tunedSeed.tone(80) // Primary Dark
    val primary90 = tunedSeed.tone(90)
    val primary100= tunedSeed.tone(100)


    // Secondary Tones (Shift hue, adjust chroma)
    val secondarySeed = hctToColor((tunedSeed.toHct().first + 45f) % 360f, 0.24f, tunedSeed.toHct().third).withMinChroma(0.22f)
    val secondary10 = secondarySeed.tone(10)
    val secondary20 = secondarySeed.tone(20)
    val secondary30 = secondarySeed.tone(30)
    val secondary45 = secondarySeed.tone(45)
    val secondary80 = secondarySeed.tone(80)
    val secondary90 = secondarySeed.tone(90)
    val secondary100= secondarySeed.tone(100)

    // Tertiary Tones (Shift hue differently, adjust chroma)
    val tertiarySeed = hctToColor((tunedSeed.toHct().first + 120f) % 360f, 0.32f, tunedSeed.toHct().third).withMinChroma(0.24f)
    val tertiary10 = tertiarySeed.tone(10)
    val tertiary20 = tertiarySeed.tone(20)
    val tertiary30 = tertiarySeed.tone(30)
    val tertiary45 = tertiarySeed.tone(45)
    val tertiary80 = tertiarySeed.tone(80)
    val tertiary90 = tertiarySeed.tone(90)
    val tertiary100= tertiarySeed.tone(100)

    // Neutral Tones (Very low chroma from seed)
    val neutralSeed = tunedSeed.withChroma(0.06f)
    val neutral10 = neutralSeed.tone(10) // Surface Dark, Background Dark
    val neutral20 = neutralSeed.tone(20)
    val neutral30 = neutralSeed.tone(30) // SurfaceVariant Dark
    val neutral88 = neutralSeed.tone(88)
    val neutral90 = neutralSeed.tone(90)
    val neutral94 = neutralSeed.tone(94)
    val neutral97 = neutralSeed.tone(97)
    val neutral98 = neutralSeed.tone(98)
    val neutral100= neutralSeed.tone(100)


    // Light Color Scheme
    val lightScheme = lightColorScheme(
        primary = primary45,
        onPrimary = primary100,
        primaryContainer = primary90,
        onPrimaryContainer = primary10,
        secondary = secondary45,
        onSecondary = secondary100,
        secondaryContainer = secondary90,
        onSecondaryContainer = secondary10,
        tertiary = tertiary45,
        onTertiary = tertiary100,
        tertiaryContainer = tertiary90,
        onTertiaryContainer = tertiary10,
        error = Color(0xFFBA1A1A), // M3 Defaults
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = neutral98,
        onBackground = neutral10,
        surface = neutral97,
        onSurface = neutral10,
        surfaceVariant = neutral88,
        onSurfaceVariant = neutral30,
        outline = neutralSeed.tone(52),
        inverseOnSurface = neutral94,
        inverseSurface = neutral20,
        inversePrimary = primary80,
        surfaceTint = primary45,
        outlineVariant = neutralSeed.tone(78),
        scrim = Color.Black
    )

    // Dark Color Scheme
    val darkScheme = darkColorScheme(
        primary = primary80,
        onPrimary = primary20,
        primaryContainer = primary30,
        onPrimaryContainer = primary90,
        secondary = secondary80,
        onSecondary = secondary20,
        secondaryContainer = secondary30,
        onSecondaryContainer = secondary90,
        tertiary = tertiary80,
        onTertiary = tertiary20,
        tertiaryContainer = tertiary30,
        onTertiaryContainer = tertiary90,
        error = Color(0xFFFFB4AB), // M3 Defaults
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = neutral10, // Darker background
        onBackground = neutral90,
        surface = neutral10.copy(alpha = (neutral10.toHct().third + 0.05f).coerceIn(0f,1f)), // Slightly lighter surface
        onSurface = neutral90,
        surfaceVariant = neutral30,
        onSurfaceVariant = neutralSeed.tone(80),
        outline = neutralSeed.tone(60),
        inverseOnSurface = neutral20,
        inverseSurface = neutral90,
        inversePrimary = primary45,
        surfaceTint = primary80,
        outlineVariant = neutral30,
        scrim = Color.Black
    )
    return ColorSchemePair(lightScheme, darkScheme)
}