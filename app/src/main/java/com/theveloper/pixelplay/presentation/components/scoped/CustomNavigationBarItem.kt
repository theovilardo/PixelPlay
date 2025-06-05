package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
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
    selectedIconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    // Colores animados - Solo se recomponen cuando 'selected' cambia
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedIconColor,
        animationSpec = tween(durationMillis = 150),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = tween(durationMillis = 150),
        label = "textColor"
    )

    // Determinar si mostrar la etiqueta
    val showLabel = label != null && (alwaysShowLabel || selected)

    // Layout principal
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                onClick = { if (!selected) onClick() else null },
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null //ripple(bounded = true, radius = 24.dp) // Ripple contenido
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Container para el ícono con indicador
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp, 32.dp) // Tamaño específico para el área clicable
        ) {
            // Indicador de fondo (pill shape para Material 3 Expressive)
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = fadeIn(animationSpec = tween(100)) + // Un fade in más rápido
                        scaleIn(
                            animationSpec = spring( // Usamos spring para el scaleIn
                                dampingRatio = Spring.DampingRatioMediumBouncy, // Proporciona un rebote moderado
                                stiffness = Spring.StiffnessLow // Puedes ajustar la rigidez
                                // initialScale para que empiece un poco más pequeño si quieres más impacto
                                // initialScale = 0.8f // (Opcional)
                            ),
                            // También puedes ajustar initialScale dentro de scaleIn si es necesario
                            // initialScale = 0.8f // Este es el valor por defecto de scaleIn si no se especifica dentro de spring
                        ),
                exit = fadeOut(animationSpec = tween(100)) +
                        scaleOut(animationSpec = tween(100, easing = EaseInQuart)) // Mantenemos el exit como estaba o lo ajustamos según se necesite
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                        .background(
                            color = indicatorColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }
//            androidx.compose.animation.AnimatedVisibility(
//                visible = selected,
//                enter = fadeIn(animationSpec = tween(250)) +
//                        scaleIn(animationSpec = tween(250, easing = EaseOutQuart)),
//                exit = fadeOut(animationSpec = tween(100)) +
//                        scaleOut(animationSpec = tween(100, easing = EaseInQuart))
//            ) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(horizontal = 4.dp)
//                        .background(
//                            color = indicatorColor,
//                            shape = RoundedCornerShape(16.dp) // Pill shape
//                        )
//                )
//            }

            // Área clicable del ícono (más pequeña que el container)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp, 24.dp) // Área clicable reducida
                    .clip(RoundedCornerShape(12.dp))

            ) {
                // Ícono
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    Box(
                        modifier = Modifier.clearAndSetSemantics {
                            if (showLabel) {
                                // La semántica se maneja en el nivel superior
                            }
                        }
                    ) {
                        icon()
                    }
                }
            }
        }

        // Etiqueta con animación
        androidx.compose.animation.AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(animationSpec = tween(200, delayMillis = 50)),
            exit = fadeOut(animationSpec = tween(100))
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier.padding(top = 4.dp)
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelMedium.copy(
                        color = textColor,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                ) {
                    label?.invoke()
                }
            }
        }
    }
}

// Versión alternativa más minimalista (si prefieres menos animaciones)
@Composable
fun RowScope.SimpleCustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val iconColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val textColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val showLabel = label != null && (alwaysShowLabel || selected)

    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Container del ícono
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp, 32.dp)
        ) {
            // Indicador simple
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        )
                )
            }

            // Ícono clicable
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp, 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        onClick = onClick,
                        enabled = enabled,
                        role = Role.Tab,
                        interactionSource = interactionSource,
                        indication = null //ripple(bounded = true, radius = 20.dp)
                    )
            ) {
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    icon()
                }
            }
        }

        // Etiqueta
        if (showLabel) {
            Spacer(
                modifier = Modifier
                    .height(4.dp)
                    .fillMaxWidth()
            )
            ProvideTextStyle(
                value = MaterialTheme.typography.labelMedium.copy(
                    color = textColor,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
            ) {
                label?.invoke()
            }
        }
    }
}

// Easing curves para animaciones más suaves (Material 3 Expressive)
private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
private val EaseInQuart = CubicBezierEasing(0.5f, 0f, 0.75f, 0f)

//@Composable
//fun RowScope.CustomNavigationBarItem(
//    selected: Boolean,
//    onClick: () -> Unit,
//    icon: @Composable () -> Unit,
//    modifier: Modifier = Modifier,
//    enabled: Boolean = true,
//    label: @Composable (() -> Unit)? = null,
//    alwaysShowLabel: Boolean = true,
//    // Pasa los colores directamente como parámetros estables en lugar de un objeto NavigationBarItemColors
//    // si sospechas que el objeto colors es el problema. O, si NavigationBarItemColors es estable y bien implementado, puedes seguir usándolo.
//    // Para este ejemplo, simplificaremos y asumiremos que los colores se manejan de forma más directa o con less animación.
//    selectedIconColor: Color,
//    unselectedIconColor: Color,
//    selectedTextColor: Color,
//    unselectedTextColor: Color,
//    indicatorColor: Color, // Si quieres un indicador
//    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
//) {
//    // Log para depurar recomposiciones de este ítem personalizado
//    // Log.d("Recomposition", "CustomNavigationBarItem for (label) RECOMPOSED, selected: $selected") // Necesitarías una forma de obtener el label aquí para el log
//
//    val targetIconColor = if (selected) selectedIconColor else unselectedIconColor
//    val targetTextColor = if (selected) selectedTextColor else unselectedTextColor
//
//    // Animaciones de color (puedes hacerlas más simples o quitarlas si son la fuente del problema)
//    // Si las mantienes, asegúrate de que solo se disparen cuando 'selected' realmente cambie.
//    val animatedIconColor by animateColorAsState(targetIconColor, animationSpec = tween(durationMillis = MotionConstants.DefaultFadeInDuration)) // Usar constantes de Material o propias
//    val animatedTextColor by animateColorAsState(targetTextColor, animationSpec = tween(durationMillis = MotionConstants.DefaultFadeInDuration))
//
//    // Determinar si se muestra la etiqueta
//    val showLabel = label != null && (alwaysShowLabel || selected)
//
//    // Semántica para accesibilidad
//    val clearIconSemantics = showLabel
//    val iconModifier = if (clearIconSemantics) Modifier.clearAndSetSemantics {} else Modifier
//
//    // Estructura básica similar al NavigationBarItemLayout
//    // Tendrás que recrear la lógica de layout si quieres el indicador animado,
//    // o puedes simplificarlo.
//
//    Column(
//        modifier = modifier
//            .selectable(
//                selected = selected,
//                onClick = onClick,
//                enabled = enabled,
//                role = Role.Tab,
//                interactionSource = interactionSource,
//                indication = ripple() // Ripple por defecto
//            )
//            .weight(1f)
//            .padding(vertical = 8.dp), // Ajusta el padding según necesites
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Box(modifier = Modifier.layoutId("icon")) { // Para posible alineación personalizada
//            CompositionLocalProvider(LocalContentColor provides animatedIconColor) {
//                Box(modifier = iconModifier) {
//                    icon()
//                }
//            }
//        }
//
//        if (showLabel) {
//            Spacer(Modifier.height(4.dp)) // Espacio entre icono y etiqueta
//            Box(modifier = Modifier.layoutId("label")) {
//                ProvideTextStyle(value = MaterialTheme.typography.labelSmall.copy(color = animatedTextColor)) { // Ajusta el estilo
//                    label!!()
//                }
//            }
//        }
//
//        // Indicador (simplificado, sin animación de tamaño compleja por ahora)
//        // Puedes añadir una animación de alpha o altura/offset si es necesario.
//        val indicatorAlpha by animateFloatAsState(
//            targetValue = if (selected) 1f else 0f,
//            animationSpec = tween(MotionConstants.DefaultFadeInDuration)
//        )
//        if (indicatorAlpha > 0.01f) {
//            Box(
//                Modifier
//                    .padding(top = 4.dp) // Ajusta la posición del indicador
//                    .height(3.dp) // Altura del indicador
//                    .fillMaxWidth(0.6f) // Ancho del indicador
//                    .alpha(indicatorAlpha)
//                    .background(indicatorColor, RoundedCornerShape(50))
//            )
//        }
//    }
//}
//
//// Necesitarás importar MotionConstants o definir tus propias duraciones
//object MotionConstants {
//    const val DefaultFadeInDuration = 150 // ms
//    // ... otras constantes de animación que puedas necesitar de los tokens de Material ...
//}