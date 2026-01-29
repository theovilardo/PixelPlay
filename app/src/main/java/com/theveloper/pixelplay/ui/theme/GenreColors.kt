package com.theveloper.pixelplay.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

data class GenreThemeColor(
    val container: Color,
    val onContainer: Color
)

object GenreThemeUtils {
    
    private val darkColors = listOf(
        GenreThemeColor(Color(0xFF004A77), Color(0xFFC2E7FF)), // Blue
        GenreThemeColor(Color(0xFF7D5260), Color(0xFFFFD8E4)), // Rose
        GenreThemeColor(Color(0xFF633B48), Color(0xFFFFD8EC)), // Pink
        GenreThemeColor(Color(0xFF004F58), Color(0xFF88FAFF)), // Cyan
        GenreThemeColor(Color(0xFF324F34), Color(0xFFCBEFD0)), // Green
        GenreThemeColor(Color(0xFF6E4E13), Color(0xFFFFDEAC)), // Gold/Orange
        GenreThemeColor(Color(0xFF3F474D), Color(0xFFDEE3EB)), // Slate
        GenreThemeColor(Color(0xFF4A4458), Color(0xFFE8DEF8)), // Purple
        GenreThemeColor(Color(0xFF7D2B2B), Color(0xFFFFB4AB)), // Red
        GenreThemeColor(Color(0xFF5B6300), Color(0xFFDDF669)), // Lime
        GenreThemeColor(Color(0xFF005047), Color(0xFF8CF4E6)), // Teal
        GenreThemeColor(Color(0xFF4F378B), Color(0xFFEADDFF)), // Indigo
        GenreThemeColor(Color(0xFF8B4A62), Color(0xFFFFD9E2)), // Maroon
        GenreThemeColor(Color(0xFF725C00), Color(0xFFFFE084)), // Yellow
        GenreThemeColor(Color(0xFF00213B), Color(0xFF99CBFF)), // Navy
        GenreThemeColor(Color(0xFF23507D), Color(0xFFD1E4FF)), // Steel Blue
        GenreThemeColor(Color(0xFF93000A), Color(0xFFFFDAD6)), // Brick Red
        GenreThemeColor(Color(0xFF45464F), Color(0xFFC4C6D0)), // Grey
        GenreThemeColor(Color(0xFF5D3F75), Color(0xFFE8B6FF)), // Violet
        GenreThemeColor(Color(0xFF7A5900), Color(0xFFFFDEA5))  // Amber
    )

    private val lightColors = listOf(
        GenreThemeColor(Color(0xFFD7E3FF), Color(0xFF005AC1)), // Blue
        GenreThemeColor(Color(0xFFFFD8E4), Color(0xFF631835)), // Rose
        GenreThemeColor(Color(0xFFFFD8EC), Color(0xFF631B4B)), // Pink
        GenreThemeColor(Color(0xFFCCE8EA), Color(0xFF004F58)), // Cyan
        GenreThemeColor(Color(0xFFCBEFD0), Color(0xFF042106)), // Green
        GenreThemeColor(Color(0xFFFFDEAC), Color(0xFF281900)), // Gold/Orange
        GenreThemeColor(Color(0xFFEFF1F7), Color(0xFF44474F)), // Slate
        GenreThemeColor(Color(0xFFE8DEF8), Color(0xFF1D192B)), // Purple
        GenreThemeColor(Color(0xFFFFB4AB), Color(0xFF690005)), // Red
        GenreThemeColor(Color(0xFFDDF669), Color(0xFF2F3300)), // Lime
        GenreThemeColor(Color(0xFF8CF4E6), Color(0xFF00201C)), // Teal
        GenreThemeColor(Color(0xFFEADDFF), Color(0xFF21005D)), // Indigo
        GenreThemeColor(Color(0xFFFFD9E2), Color(0xFF3B071D)), // Maroon
        GenreThemeColor(Color(0xFFFFE084), Color(0xFF231B00)), // Yellow
        GenreThemeColor(Color(0xFF99CBFF), Color(0xFF003258)), // Navy
        GenreThemeColor(Color(0xFFD1E4FF), Color(0xFF051C36)), // Steel Blue
        GenreThemeColor(Color(0xFFFFDAD6), Color(0xFF410002)), // Brick Red
        GenreThemeColor(Color(0xFFE2E2E9), Color(0xFF191C20)), // Grey
        GenreThemeColor(Color(0xFFF2DAFF), Color(0xFF2C004F)), // Violet
        GenreThemeColor(Color(0xFFFFDEA5), Color(0xFF261900))  // Amber
    )

    fun getGenreThemeColor(genreId: String, isDark: Boolean): GenreThemeColor {
        val hash = abs(genreId.hashCode())
        val index = hash % darkColors.size
        return if (isDark) darkColors[index] else lightColors[index]
    }

    private fun androidx.compose.ui.graphics.Color.shiftHue(amount: Float): androidx.compose.ui.graphics.Color {
        val argb = this.toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        hsv[0] = (hsv[0] + amount + 360) % 360
        return androidx.compose.ui.graphics.Color(android.graphics.Color.HSVToColor(hsv))
    }

    private fun androidx.compose.ui.graphics.Color.contrastColor(): androidx.compose.ui.graphics.Color {
        // Calculate luminance using standard formula
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue)
        return if (luminance > 0.5) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White
    }

    @androidx.compose.runtime.Composable
    fun getGenreColorScheme(
        genreId: String,
        isDark: Boolean,
        baseScheme: androidx.compose.material3.ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    ): androidx.compose.material3.ColorScheme {
        val themeColor = getGenreThemeColor(genreId, isDark)
        val primarySeed = themeColor.container
        
        // Generate Secondary (Analogous +25) and Tertiary (Triadic +120)
        val secondarySeed = primarySeed.shiftHue(25f)
        val tertiarySeed = primarySeed.shiftHue(120f)
        
        // We derive the tonal palettes simply by opacity or reuse for now to avoid full HCT engine overhead.
        
        return baseScheme.copy(
            primary = themeColor.onContainer,
            onPrimary = themeColor.container,
            primaryContainer = themeColor.container,
            onPrimaryContainer = themeColor.onContainer,
            
            secondary = secondarySeed,
            onSecondary = secondarySeed.contrastColor(), 
            secondaryContainer = secondarySeed, // Solid container
            onSecondaryContainer = secondarySeed.contrastColor(),
            
            tertiary = tertiarySeed,
            onTertiary = tertiarySeed.contrastColor(),
            tertiaryContainer = tertiarySeed, // Solid container
            onTertiaryContainer = tertiarySeed.contrastColor(),
            
            surface = themeColor.container // Tinted surface for contrast
        )
    }
}
