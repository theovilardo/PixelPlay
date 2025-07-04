package com.theveloper.pixelplay.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair

// Define neutral base colors for fallback themes
// These replace the previous violet-centric "PixelPlay Default" theme.
val NeutralDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7FCDCF), // Example: A muted teal/cyan
    secondary = PixelPlayPink,      // Kept as per user feedback (general accent)
    tertiary = PixelPlayOrange,     // Kept as per user feedback (general accent)
    background = Color(0xFF121212), // Standard dark theme background
    surface = Color(0xFF1E1E1E),    // Standard dark theme surface (slightly lighter)
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color(0xFFE0E0E0),  // Light grey for text on dark surfaces
    error = Color(0xFFCF6679),      // Standard M3 dark error
    onError = Color.Black
)

val NeutralLightColorScheme = lightColorScheme(
    primary = Color(0xFF6200EE), // Example: A standard purple/blue
    secondary = PixelPlayPink,      // Kept
    tertiary = PixelPlayOrange,     // Kept
    background = Color(0xFFFFFBFB), // Common M3 light background
    surface = Color(0xFFFFFBFB),    // Common M3 light surface
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    error = Color(0xFFB00020),      // Standard M3 light error
    onError = Color.White
)

// Renamed DarkColorScheme and LightColorScheme to avoid confusion if they are imported elsewhere.
// The PixelPlayTheme will now use NeutralDarkColorScheme and NeutralLightColorScheme as fallbacks.
val DarkColorScheme = NeutralDarkColorScheme
val LightColorScheme = NeutralLightColorScheme


@Composable
fun PixelPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val finalColorScheme = when {
        colorSchemePairOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Tema dinámico del sistema como prioridad si no hay override
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (e: Exception) {
                // Fallback a los defaults si dynamic colors falla (raro, pero posible en algunos dispositivos)
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        colorSchemePairOverride != null -> {
            // Usar el esquema del álbum si se proporciona
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        // Fallback final a los defaults si no hay override ni dynamic colors aplicables
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = finalColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

//@Composable
//fun PixelPlayTheme(
//    darkTheme: Boolean = isSystemInDarkTheme(),
//    // Dynamic color is available on Android 12+
//    colorSchemePairOverride: ColorSchemePair? = null, // Puede ser null para tema dinámico
//    dynamicColor: Boolean = true,
//    content: @Composable () -> Unit
//) {
//    val context = LocalContext.current
//    val finalColorScheme = when {
//        colorSchemePairOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
//            // Indicador de tema dinámico del sistema
//            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
//        }
//        colorSchemePairOverride != null -> {
//            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
//        }
//        darkTheme -> DarkColorScheme
//        else -> LightColorScheme
//    }
//
//    val view = LocalView.current
//    if (!view.isInEditMode) {
//        SideEffect {
//            val window = (view.context as Activity).window
//            window.statusBarColor = finalColorScheme.background.toArgb()
//            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
//        }
//    }
//
//    MaterialTheme(
//        colorScheme = finalColorScheme,
//        typography = Typography,
//        shapes = Shapes,
//        content = content
//    )
////    val colorScheme = when {
////        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
////            val context = LocalContext.current
////            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
////        }
////
////        darkTheme -> DarkColorScheme
////        else -> LightColorScheme
////    }
////
////    MaterialTheme(
////        colorScheme = colorScheme,
////        typography = Typography,
////        shapes = Shapes,
////        content = content
////    )
//}