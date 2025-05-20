package com.theveloper.pixelplay.ui.theme

import androidx.compose.ui.graphics.Color

// Paleta inspirada en las imágenes, con un toque "expresivo"
// Tonos púrpuras, rosas, naranjas y un fondo oscuro base.

val PurpleDark = Color(0xFF1A102C) // Fondo principal oscuro
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val PurplePrimary = Color(0xFF7B5AFF) // Un púrpura vibrante para acentos
val PinkAccent = Color(0xFFFF69B4)    // Rosa para elementos destacados
val OrangeAccent = Color(0xFFFFA500)  // Naranja para toques cálidos

val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)


val PixelPlayGray = Color(0xFF888888)        // Para texto secundario
val PixelPlayLightGray = Color(0xFFCCCCCC)   // Para divisores o elementos sutiles
val PixelPlayPurpleDark = Color(0xFF1E1234) // Fondo principal más oscuro y profundo
val PixelPlayPurplePrimary = Color(0xFFAB47BC) // Morado principal más vibrante (Material Purple 300)
val PixelPlayPink = Color(0xFFF06292)         // Rosa más suave y moderno (Material Pink 300)
val PixelPlayOrange = Color(0xFFFF8A65)       // Naranja coral (Material Deep Orange 300)
val PixelPlayLightPurple = Color(0xFFE1BEE7)   // Lavanda muy claro para superficies (Material Purple 100)
val PixelPlayWhite = Color(0xFFFFFFFF)
val PixelPlayBlack = Color(0xFF000000)
val PixelPlayGrayText = Color(0xFFB0BEC5)    // Gris azulado claro para texto secundario (Blue Grey 200)
val PixelPlaySurface = Color(0xFF2A1F40)    // Superficie ligeramente más clara que el fondo

// Colores para el degradado de fondo del reproductor (ajustados)
val PlayerGradientStart = PixelPlayOrange.copy(alpha = 0.7f)
val PlayerGradientCenter = PixelPlayPink.copy(alpha = 0.8f)
val PlayerGradientEnd = PixelPlayPurplePrimary.copy(alpha = 0.9f)

// Colores para tema claro (si se implementa)
val LightBackground = Color(0xFFFDF7FF) // Un blanco ligeramente teñido de púrpura
val LightPrimary = Color(0xFF6A1B9A)    // Púrpura oscuro para primario en tema claro
val LightAccent = PixelPlayPink