package com.theveloper.pixelplay.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    small = RoundedCornerShape(8.dp), // Botones pequeños, chips
    medium = RoundedCornerShape(16.dp), // Tarjetas, diálogos
    large = RoundedCornerShape(24.dp)  // Componentes grandes, hojas inferiores
)