package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

// En un nuevo archivo o junto a PlayerInternalNavigationItemsRow.kt

@Composable
fun RowScope.CustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    // Pasa los colores directamente como parámetros estables en lugar de un objeto NavigationBarItemColors
    // si sospechas que el objeto colors es el problema. O, si NavigationBarItemColors es estable y bien implementado, puedes seguir usándolo.
    // Para este ejemplo, simplificaremos y asumiremos que los colores se manejan de forma más directa o con less animación.
    selectedIconColor: Color,
    unselectedIconColor: Color,
    selectedTextColor: Color,
    unselectedTextColor: Color,
    indicatorColor: Color, // Si quieres un indicador
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    // Log para depurar recomposiciones de este ítem personalizado
    // Log.d("Recomposition", "CustomNavigationBarItem for (label) RECOMPOSED, selected: $selected") // Necesitarías una forma de obtener el label aquí para el log

    val targetIconColor = if (selected) selectedIconColor else unselectedIconColor
    val targetTextColor = if (selected) selectedTextColor else unselectedTextColor

    // Animaciones de color (puedes hacerlas más simples o quitarlas si son la fuente del problema)
    // Si las mantienes, asegúrate de que solo se disparen cuando 'selected' realmente cambie.
    val animatedIconColor by animateColorAsState(targetIconColor, animationSpec = tween(durationMillis = MotionConstants.DefaultFadeInDuration)) // Usar constantes de Material o propias
    val animatedTextColor by animateColorAsState(targetTextColor, animationSpec = tween(durationMillis = MotionConstants.DefaultFadeInDuration))

    // Determinar si se muestra la etiqueta
    val showLabel = label != null && (alwaysShowLabel || selected)

    // Semántica para accesibilidad
    val clearIconSemantics = showLabel
    val iconModifier = if (clearIconSemantics) Modifier.clearAndSetSemantics {} else Modifier

    // Estructura básica similar al NavigationBarItemLayout
    // Tendrás que recrear la lógica de layout si quieres el indicador animado,
    // o puedes simplificarlo.

    Column(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = ripple() // Ripple por defecto
            )
            .weight(1f)
            .padding(vertical = 8.dp), // Ajusta el padding según necesites
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.layoutId("icon")) { // Para posible alineación personalizada
            CompositionLocalProvider(LocalContentColor provides animatedIconColor) {
                Box(modifier = iconModifier) {
                    icon()
                }
            }
        }

        if (showLabel) {
            Spacer(Modifier.height(4.dp)) // Espacio entre icono y etiqueta
            Box(modifier = Modifier.layoutId("label")) {
                ProvideTextStyle(value = MaterialTheme.typography.labelSmall.copy(color = animatedTextColor)) { // Ajusta el estilo
                    label!!()
                }
            }
        }

        // Indicador (simplificado, sin animación de tamaño compleja por ahora)
        // Puedes añadir una animación de alpha o altura/offset si es necesario.
        val indicatorAlpha by animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = tween(MotionConstants.DefaultFadeInDuration)
        )
        if (indicatorAlpha > 0.01f) {
            Box(
                Modifier
                    .padding(top = 4.dp) // Ajusta la posición del indicador
                    .height(3.dp) // Altura del indicador
                    .fillMaxWidth(0.6f) // Ancho del indicador
                    .alpha(indicatorAlpha)
                    .background(indicatorColor, RoundedCornerShape(50))
            )
        }
    }
}

// Necesitarás importar MotionConstants o definir tus propias duraciones
object MotionConstants {
    const val DefaultFadeInDuration = 150 // ms
    // ... otras constantes de animación que puedas necesitar de los tokens de Material ...
}