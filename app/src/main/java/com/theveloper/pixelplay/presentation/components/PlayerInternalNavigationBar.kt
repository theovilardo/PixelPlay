package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.presentation.components.scoped.CustomNavigationBarItem
import com.theveloper.pixelplay.presentation.navigation.BottomNavItem
import kotlinx.collections.immutable.ImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

val NavBarPersistentHeight = 84.dp // Altura estimada o fija para la PlayerInternalNavigationBar

// --- NUEVO: Barra de Navegación Interna del Player ---

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    //Log.d("Recomposition", "PlayerInternalNavigationItemsRow - currentRoute: $currentRoute, navItemsHash: ${navItems.hashCode()}")

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
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    containerShape: Shape,
    navBarElevation: Dp,
    isPlayerVisible: Boolean = false,
    currentRoute: String?,
    modifier: Modifier = Modifier,      // For external adjustments
    topCornersRadiusDp: Dp = 12.dp,
    bottomCornersRadiusDp: Dp = 12.dp,
    navBarHideFraction: Float,
    navBarHeightPx: Float
) {
    remember(navBarHideFraction) { derivedStateOf { 1f - navBarHideFraction } }
    val animatedTranslationY = remember(navBarHideFraction, navBarHeightPx) { derivedStateOf { navBarHeightPx * navBarHideFraction } }
    val actualShape = remember(topCornersRadiusDp) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = topCornersRadiusDp,
            smoothnessAsPercentBR = 60,
            cornerRadiusTR = topCornersRadiusDp,
            smoothnessAsPercentTL = 60,
            cornerRadiusBL = bottomCornersRadiusDp,
            smoothnessAsPercentTR = 60,
            cornerRadiusBR = bottomCornersRadiusDp,
            smoothnessAsPercentBL = 60
        )
    }

//    val conditionalShape = if (isPlayerVisible) {
//        actualShape
//    } else {
//        CircleShape
//    }

    Box(
        modifier = modifier // Internal base modifier for the component's structure
            .fillMaxWidth()
            .height(NavBarPersistentHeight) // RESTORED: Use the Dp constant
            .graphicsLayer {
                translationY = animatedTranslationY.value
            }
            .shadow(
                elevation = navBarElevation,
                shape = containerShape,
                clip = false // No recortar la sombra
            )
            .background(
                color = NavigationBarDefaults.containerColor,
                shape = containerShape //conditionalShape
            )
    ) {
        PlayerInternalNavigationItemsRow(
            navController = navController,
            navItems = navItems,
            currentRoute = currentRoute
        )
    }
}