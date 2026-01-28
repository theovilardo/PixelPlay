package com.theveloper.pixelplay.data.database

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

fun String.toComposeColor(): Color {
    // Añadir manejo de errores para parseo de color
    return try {
        Color(this.toColorInt())
    } catch (e: IllegalArgumentException) {
        // Log error o devolver un color por defecto si el string no es válido
        Color.Black // Fallback color
    }
}