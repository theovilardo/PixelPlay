package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.copy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreGradientTopBar(
    title: String,
    startColor: Color,
    endColor: Color,
    contentColor: Color,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationIconClick: () -> Unit,
) {
    val gradientBrush = remember(startColor, endColor) {
        Brush.verticalGradient(colors = listOf(startColor, endColor))
    }

    LargeFlexibleTopAppBar(
        scrollBehavior = scrollBehavior,
        title = {
            Text(
                modifier = Modifier.padding(start = 6.dp),
                text = title,
                color = contentColor,
                fontFamily = GoogleSansRounded
            )
        },
        expandedHeight = 160.dp,
        modifier = Modifier.background(brush = gradientBrush),
        navigationIcon = {
            IconButton(
                modifier = Modifier.padding(start = 10.dp),
                onClick = onNavigationIconClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = contentColor
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = startColor
                )
            }
        },
        colors = topAppBarColors(
            containerColor = Color.Transparent, // Background is handled by the gradient brush
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer, // Or a color that contrasts well with your typical gradient
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer // Same as title
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsibleGenreTopBar(
    title: String,
    startColor: Color,
    endColor: Color,
    contentColor: Color,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigationIconClick: () -> Unit,
) {
    // Esta fracción nos dice qué tan colapsada está la barra (0.0 = expandida, 1.0 = colapsada)
    val collapseFraction = scrollBehavior.state.collapsedFraction

    // Interpolamos la altura de la barra entre su estado expandido y colapsado.
    val expandedHeight = 180.dp // Le damos un poco más de altura para que el título tenga espacio
    val collapsedHeight = 64.dp // Altura estándar de una TopAppBar
    val currentHeight = lerp(expandedHeight, collapsedHeight, collapseFraction)

    // Interpolamos el tamaño de la fuente del título.
    val titleFontSize = lerp(32.sp, 20.sp, collapseFraction)

    // Esta es la parte clave para la animación de posición:
    // Calculamos el padding horizontal y vertical del título.
    // Expandido: El título estará más abajo y más a la izquierda.
    // Colapsado: El título estará más arriba y a la derecha del ícono de "atrás".
    val titleStartPadding = lerp(16.dp, 60.dp, collapseFraction)
    val titleTopPadding = lerp(expandedHeight - 64.dp, 0.dp, collapseFraction)
    val titleBottomPadding = lerp(16.dp, 0.dp, collapseFraction)


    // Creamos el fondo con el gradiente.
    val gradientBrush = remember(startColor, endColor) {
        Brush.verticalGradient(colors = listOf(startColor, endColor))
    }

    // Usamos un Box para tener control total sobre el posicionamiento de los elementos.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(currentHeight)
            .background(brush = gradientBrush)
    ) {
        // --- Ícono de Navegación ---
        // Se mantiene fijo en la esquina superior izquierda.
        IconButton(
            modifier = Modifier
                .padding(top = 4.dp, start = 4.dp)
                .align(Alignment.TopStart),
            onClick = onNavigationIconClick
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = contentColor
            )
        }

        // --- Título ---
        // Usamos un Box contenedor para aplicar los paddings animados.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart) // Lo anclamos abajo a la izquierda del Box principal
                .padding(
                    start = titleStartPadding,
                    bottom = titleBottomPadding,
                    top = titleTopPadding // Este padding lo empujará hacia arriba al colapsar
                )
        ) {
            Text(
                text = title,
                color = contentColor,
                fontSize = titleFontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}


private fun Brush.copy(alpha: Float): Brush {
    return when (this) {
        is SolidColor -> {
            // For a SolidColor brush, we copy its underlying color and apply the new alpha.
            // Note: Color.copy(alpha = newAlphaValue) sets the alpha directly,
            // while color.copy(alpha = color.alpha * newAlphaFactor) multiplies.
            // Assuming the input 'alpha' is intended as a direct new alpha value for the brush.
            // If it's a factor, it should be color.value.copy(alpha = color.value.alpha * alpha)
            SolidColor(this.value.copy(alpha = alpha))
        }
        // For any other type of Brush (typically ShaderBrush for gradients):
        // We cannot reliably access its internal list of colors and other
        // construction parameters (start/end offsets, radius, tileMode, etc.)
        // to reconstruct it with only the color alphas changed.
        // Attempting to do so with assumptions about internal structure would be fragile.
        else -> {
            // Log.w("BrushCopy", "Alpha modification is not supported for brush type: ${this::class.simpleName}. Returning original brush.")
            // Return the original brush as a fallback.
            // The alpha change will not be applied to this brush instance.
            this
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeGradientTopBar(
    onNavigationIconClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 14.dp)
            ) {
                FilledIconButton(
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = onMoreOptionsClick
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = painterResource(R.drawable.round_newspaper_24),
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
