package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import com.theveloper.pixelplay.R

/**
 * Muestra el vector de res/drawable/material_you_vector.xml
 * - No se aplica tint; respeta los 3 colores definidos por atributos del tema.
 * - ContentScale.FillBounds para abarcar todo el contenedor.
 */
@Composable
fun MaterialYouVectorDrawable(
    modifier: Modifier = Modifier,
    painter: Painter
) {
    Image(
        painter = painter,
        contentDescription = null,
        //contentScale = ContentScale.FillBounds,
        modifier = modifier
    )
}