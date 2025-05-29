package com.theveloper.pixelplay.presentation.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.components.scoped.CustomNavigationBarItem
import com.theveloper.pixelplay.presentation.navigation.BottomNavItem
import kotlinx.collections.immutable.ImmutableList

val NavBarPersistentHeight = 84.dp // Altura estimada o fija para la PlayerInternalNavigationBar

// --- NUEVO: Barra de Navegación Interna del Player ---

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    Log.d("Recomposition", "PlayerInternalNavigationItemsRow - currentRoute: $currentRoute, navItemsHash: ${navItems.hashCode()}")

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute == item.screen.route

            // Obtener colores del tema una vez
            val selectedColor = MaterialTheme.colorScheme.primary
            val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val indicatorColorFromTheme = MaterialTheme.colorScheme.secondaryContainer

            // Las lambdas de icono y etiqueta ya están bien recordadas
            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId!!
            } else {
                item.iconResId
            }
            val iconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = item.label
                    )
                }
            }
            val labelLambda: @Composable () -> Unit = remember(item.label) {
                { Text(item.label) }
            }
            val onClickLambda = remember(navController, item.screen.route) {
                {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.id) { inclusive = true; saveState = false }
                        launchSingleTop = true
                        restoreState = false
                    }
                }
            }

//            NavigationBarItem(
//                modifier = Modifier.weight(1f),
//                selected = isSelected,
//                onClick = onClickLambda,
//                icon = iconLambda,
//                label = labelLambda,
//            )

            CustomNavigationBarItem(
                modifier = Modifier.weight(1f),
                selected = isSelected,
                onClick = onClickLambda,
                icon = iconLambda,
                label = labelLambda,
                alwaysShowLabel = true,
                selectedIconColor = selectedColor,
                unselectedIconColor = unselectedColor,
                selectedTextColor = selectedColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = indicatorColorFromTheme
            )
        }
    }
}

@Composable
private fun PlayerNavigationItem( // Nota: Renombrado para claridad, o puedes mantener el nombre
    navController: NavHostController,
    item: BottomNavItem, // BottomNavItem debe ser @Immutable
    isCurrentlySelected: Boolean, // Pasar 'isSelected' directamente
    modifier: Modifier = Modifier // Modifier para el NavigationBarItem
) {
    // Log para ver cuándo se recompone este ítem específico
    Log.d("Recomposition", "PlayerNavigationItem for ${item.label} RECOMPOSED, isSelected: $isCurrentlySelected")

    // Los colores se calculan aquí, dependiendo solo de isCurrentlySelected y el tema.
    // El tema (MaterialTheme.colorScheme) se asume estable a menos que todo el tema de la app cambie.
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    )

    // El modificador para el NavigationBarItem se define una vez aquí.
    // val individualItemModifier = remember { Modifier.weight(1f) } // Esto estaba en el bucle, ahora es parte del 'modifier' pasado o se define aquí si es fijo.

    val onClickLambda = remember(navController, item.screen.route) { // Claves estables
        {
            navController.navigate(item.screen.route) {
                popUpTo(navController.graph.id) {
                    inclusive = true
                    saveState = false
                }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    val iconPainterResId = if (isCurrentlySelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
        item.selectedIconResId!!
    } else {
        item.iconResId
    }

    // Estas lambdas se recordarán correctamente si isCurrentlySelected (usada indirectamente vía iconPainterResId) y item.label son estables.
    val iconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
        {
            Icon(
                painter = painterResource(id = iconPainterResId),
                contentDescription = item.label
            )
        }
    }

    val labelLambda: @Composable () -> Unit = remember(item.label) {
        { Text(item.label) }
    }

    Row(
        modifier = modifier
    ) {
        NavigationBarItem(
            modifier = modifier, // Usar el modifier pasado (que podría incluir .weight(1f))
            selected = isCurrentlySelected,
            onClick = onClickLambda,
            icon = iconLambda,
            label = labelLambda,
            alwaysShowLabel = true,
            colors = itemColors
        )
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,      // For external adjustments
    topCornersRadiusDp: Dp = 12.dp,
    bottomCornersRadiusDp: Dp = 12.dp,
    navBarHideFraction: Float,
    navBarHeightPx: Float
) {
    val animatedAlpha = remember(navBarHideFraction) { derivedStateOf { 1f - navBarHideFraction } }
    val animatedTranslationY = remember(navBarHideFraction, navBarHeightPx) { derivedStateOf { navBarHeightPx * navBarHideFraction } }
    val actualShape = remember(topCornersRadiusDp) {
        RoundedCornerShape(
            topStart = topCornersRadiusDp,
            topEnd = topCornersRadiusDp,
            bottomStart = bottomCornersRadiusDp,
            bottomEnd = bottomCornersRadiusDp
        )
    }

    Box(
        modifier = Modifier // Internal base modifier for the component's structure
            .fillMaxWidth()
            .height(NavBarPersistentHeight) // RESTORED: Use the Dp constant
            .graphicsLayer {
                translationY = animatedTranslationY.value
            }
            .background(
                color = NavigationBarDefaults.containerColor,
                shape = actualShape
            )
            .then(modifier) // Apply the passed-in modifier last
    ) {
        PlayerInternalNavigationItemsRow(
            navController = navController,
            navItems = navItems,
            currentRoute = currentRoute
        )
    }
}

//@Composable
//private fun PlayerInternalNavigationItemsRow(
//    navController: NavHostController,
//    navItems: ImmutableList<BottomNavItem>, // CLAVE: Asegúrate de que esta lista sea ESTABLE.
//    // Si se recrea innecesariamente, causará recomposiciones.
//    currentRoute: String?,
//    modifier: Modifier = Modifier
//) {
//    val rowModifier = remember { // Este remember está bien para el Modifier
//        Modifier
//            .fillMaxSize()
//            .padding(horizontal = 12.dp)
//    }
//    Row(
//        modifier = rowModifier,
//        horizontalArrangement = Arrangement.SpaceAround,
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        // navItems.forEach es eficiente si navItems es una lista estable.
//        navItems.forEach { item ->
//            val isSelected = currentRoute == item.screen.route
//
//            // CORRECTO: Llama a NavigationBarItemDefaults.colors() directamente.
//            // Es una función @Composable que maneja su propia memoización interna.
//            val itemColors = NavigationBarItemDefaults.colors(
//                selectedIconColor = MaterialTheme.colorScheme.primary,
//                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
//                selectedTextColor = MaterialTheme.colorScheme.primary,
//                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
//                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
//                // Puedes omitir parámetros si los defaults de M3 son suficientes para tu caso.
//                // La función usará los colores del tema y el estado 'selected' para
//                // determinar los colores apropiados.
//            )
//
//            val itemModifier = remember { Modifier.weight(1f) }
//
//            // Tus lambdas recordadas para onClick, icon y label están perfectas.
//            val onClickLambda = remember(navController, item.screen.route) {
//                {
//                    navController.navigate(item.screen.route) {
//                        popUpTo(navController.graph.id) {
//                            inclusive = true
//                            saveState = false // Considera true si quieres guardar el estado de la pantalla de destino
//                        }
//                        launchSingleTop = true
//                        restoreState = false // Considera true si quieres restaurar el estado
//                    }
//                }
//            }
//
//            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
//                item.selectedIconResId!! // Asumiendo que selectedIconResId es Int? y 0 no es un ID válido
//            } else {
//                item.iconResId
//            }
//
//            val iconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
//                {
//                    Icon(
//                        painter = painterResource(id = iconPainterResId),
//                        contentDescription = item.label
//                    )
//                }
//            }
//
//            val labelLambda: @Composable () -> Unit = remember(item.label) {
//                { Text(item.label) }
//            }
//
//            NavigationBarItem(
//                modifier = itemModifier,
//                selected = isSelected,
//                onClick = onClickLambda,
//                icon = iconLambda,
//                label = labelLambda,
//                alwaysShowLabel = true, // O según tu preferencia
//                colors = itemColors // Pasas el resultado de NavigationBarItemDefaults.colors()
//            )
//        }
//    }
//}