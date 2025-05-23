package com.theveloper.pixelplay.presentation.components

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
import com.theveloper.pixelplay.presentation.navigation.BottomNavItem

val NavBarPersistentHeight = 84.dp // Altura estimada o fija para la PlayerInternalNavigationBar

// --- NUEVO: Barra de Navegación Interna del Player ---


@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: List<BottomNavItem>, // CLAVE: Asegúrate de que esta lista sea ESTABLE.
    // Si se recrea innecesariamente, causará recomposiciones.
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    val rowModifier = remember { // Este remember está bien para el Modifier
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // navItems.forEach es eficiente si navItems es una lista estable.
        navItems.forEach { item ->
            val isSelected = currentRoute == item.screen.route

            // CORRECTO: Llama a NavigationBarItemDefaults.colors() directamente.
            // Es una función @Composable que maneja su propia memoización interna.
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                // Puedes omitir parámetros si los defaults de M3 son suficientes para tu caso.
                // La función usará los colores del tema y el estado 'selected' para
                // determinar los colores apropiados.
            )

            val itemModifier = remember { Modifier.weight(1f) }

            // Tus lambdas recordadas para onClick, icon y label están perfectas.
            val onClickLambda = remember(navController, item.screen.route) {
                {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                            saveState = false // Considera true si quieres guardar el estado de la pantalla de destino
                        }
                        launchSingleTop = true
                        restoreState = false // Considera true si quieres restaurar el estado
                    }
                }
            }

            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId!! // Asumiendo que selectedIconResId es Int? y 0 no es un ID válido
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

            NavigationBarItem(
                modifier = itemModifier,
                selected = isSelected,
                onClick = onClickLambda,
                icon = iconLambda,
                label = labelLambda,
                alwaysShowLabel = true, // O según tu preferencia
                colors = itemColors // Pasas el resultado de NavigationBarItemDefaults.colors()
            )
        }
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: List<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,      // For external adjustments
    topCornersRadiusDp: Dp,
    navBarHideFraction: Float,
    navBarHeightPx: Float
) {
    val animatedAlpha = remember(navBarHideFraction) { derivedStateOf { 1f - navBarHideFraction } }
    val animatedTranslationY = remember(navBarHideFraction, navBarHeightPx) { derivedStateOf { navBarHeightPx * navBarHideFraction } }
    val actualShape = remember(topCornersRadiusDp) {
        RoundedCornerShape(topStart = topCornersRadiusDp, topEnd = topCornersRadiusDp)
    }

    Box(
        modifier = Modifier // Internal base modifier for the component's structure
            .fillMaxWidth()
            .height(NavBarPersistentHeight) // RESTORED: Use the Dp constant
            .graphicsLayer {
                translationY = animatedTranslationY.value
                //alpha = animatedAlpha.value // RESTORED: Alpha for fade animation
            }
            .shadow(
                elevation = 8.dp, // Consider if this shadow needs to animate
                shape = actualShape
            )
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