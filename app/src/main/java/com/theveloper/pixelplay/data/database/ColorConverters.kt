package com.theveloper.pixelplay.data.database

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt

// No se necesitan TypeConverter ni ColorScheme aquí si no los usa Room.

// Helper para convertir Color a String y viceversa (usado en ViewModel)
fun Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}

fun String.toComposeColor(): Color {
    // Añadir manejo de errores para parseo de color
    return try {
        Color(this.toColorInt())
    } catch (e: IllegalArgumentException) {
        // Log error o devolver un color por defecto si el string no es válido
        Color.Black // Fallback color
    }
}

// La clase ColorConverters puede permanecer vacía o ser eliminada si no se usa
// para otros TypeConverters en el futuro.
class ColorConverters {
    // No se necesitan métodos @TypeConverter aquí para la estructura actual.
}