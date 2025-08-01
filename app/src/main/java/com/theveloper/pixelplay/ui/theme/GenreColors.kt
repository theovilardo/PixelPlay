package com.theveloper.pixelplay.ui.theme

import androidx.compose.ui.graphics.Color

data class GenreColor(
    val lightColor: Color,
    val onLightColor: Color,
    val darkColor: Color,
    val onDarkColor: Color
)

object GenreColors {
    val colors = listOf(
        GenreColor(Color(0xFFE0BBE4), Color(0xFF3B2942), Color(0xFF6D4A7A), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFF957DAD), Color(0xFF2C1B3E), Color(0xFF503D6E), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFFD291BC), Color(0xFF422139), Color(0xFF7A4E6F), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFFFEC8D8), Color(0xFF59323C), Color(0xFF996D7A), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFFFFF2CC), Color(0xFF4D4223), Color(0xFF8C7A4F), Color(0xFF000000)),
        GenreColor(Color(0xFFA2D2FF), Color(0xFF283E4D), Color(0xFF587A99), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFFBDE0FE), Color(0xFF344452), Color(0xFF6A88A1), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFFA2D2FF), Color(0xFF283E4D), Color(0xFF587A99), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFFCDB4DB), Color(0xFF3E3142), Color(0xFF74607A), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFFFFC8DD), Color(0xFF593241), Color(0xFF996D81), Color(0xFFFFFFFF)),
        GenreColor(Color(0xFFBDE0FE), Color(0xFF344452), Color(0xFF6A88A1), Color(0xFFFFFFFF))
    )
}
