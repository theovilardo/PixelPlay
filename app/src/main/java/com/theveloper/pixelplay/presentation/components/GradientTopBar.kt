package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import okhttp3.internal.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientTopBar(
    onNavigationIconClick: () -> Unit,
) {
    // 1) Pinta la status bar con el color surface
    val surfaceColor = MaterialTheme.colorScheme.surface

    val gradientColors = listOf(
        surfaceColor,
        Color.Transparent
    ).toImmutableList()

    // Recordar el Brush basado en la lista de colores recordada
    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }



    // 3) TopAppBar con fondo degradado
    TopAppBar(
        modifier = Modifier
            .background(brush = gradientBrush),
        title = { /* nada, usamos solo acciones */ },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(end = 14.dp)
            ) {
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { /* Más opciones */ }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_more_vert_24),
                        contentDescription = "Más opciones"
                    )
                }
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = onNavigationIconClick
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = "Ajustes"
                    )
                }
            }
        },
        colors = topAppBarColors(
            containerColor = Color.Transparent // ya pintamos el fondo con el Brush
        )
    )
}
