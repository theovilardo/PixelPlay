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

val DarkColorScheme = darkColorScheme(
    primary = PixelPlayPurplePrimary,
    secondary = PixelPlayPink,
    tertiary = PixelPlayOrange,
    background = PixelPlayPurpleDark,
    surface = PixelPlaySurface,
    onPrimary = PixelPlayWhite,
    onSecondary = PixelPlayWhite,
    onTertiary = PixelPlayWhite,
    onBackground = PixelPlayWhite,
    onSurface = PixelPlayLightPurple, // Texto sobre superficies
    error = Color(0xFFFF5252),
    onError = PixelPlayWhite
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = PixelPlayPink,
    tertiary = PixelPlayOrange,
    background = LightBackground,
    surface = PixelPlayWhite,
    onPrimary = PixelPlayWhite,
    onSecondary = PixelPlayBlack,
    onTertiary = PixelPlayBlack,
    onBackground = PixelPlayBlack,
    onSurface = PixelPlayBlack,
    error = Color(0xFFD32F2F),
    onError = PixelPlayWhite
)

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