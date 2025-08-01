package com.theveloper.pixelplay.utils

import androidx.compose.ui.unit.Dp

// Función utilitaria para usar en animaciones
//private fun lerp(start: Dp, end: Dp, fraction: Float): Dp {
//    return start + ((end - start) * fraction)
//}

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Extiende Float con un método de potencia para efectos de animación
private fun Float.pow(power: Float, power1: Float): Float {
    return pow(this, power)
}

fun Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}