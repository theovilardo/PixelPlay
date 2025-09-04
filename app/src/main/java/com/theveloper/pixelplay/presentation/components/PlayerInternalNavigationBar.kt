package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.theveloper.pixelplay.BottomNavItem
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.components.scoped.CustomNavigationBarItem
import kotlinx.collections.immutable.ImmutableList

val NavBarContentHeight = 66.dp // Altura del contenido de la barra de navegación
val NavBarContentHeightFullWidth = 84.dp // Altura del contenido de la barra de navegación en modo completo

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String
) {
    val rowModifier = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute == item.screen.route
            val selectedColor = MaterialTheme.colorScheme.primary
            val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val indicatorColorFromTheme = MaterialTheme.colorScheme.secondaryContainer

            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId
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
            val selectedIconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
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
                selectedIcon = selectedIconLambda,
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
    isPlayerVisible: Boolean,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    topCornersRadiusDp: Dp,
    bottomCornersRadiusDp: Dp,
    navBarHideFraction: Float,
    navBarHeightPx: Float,
    navBarInset: Dp,
    navBarStyle: String
) {
    remember(navBarHideFraction) { derivedStateOf { 1f - navBarHideFraction } }
    val animatedTranslationY = remember(navBarHideFraction, navBarHeightPx) { derivedStateOf { navBarHeightPx * navBarHideFraction } }
    val boxAlignment = if (navBarStyle == NavBarStyle.FULL_WIDTH) Alignment.TopCenter else Alignment.Center

    val navHeight = if (navBarStyle == NavBarStyle.FULL_WIDTH) NavBarContentHeightFullWidth else NavBarContentHeight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(navHeight + navBarInset) // Explicit total height
            .graphicsLayer {
                translationY = animatedTranslationY.value
                alpha = 1f
            }
            .shadow(
                elevation = navBarElevation,
                shape = containerShape,
                clip = false
            )
            .background(
                color = NavigationBarDefaults.containerColor,
                shape = containerShape
            ),
        contentAlignment = boxAlignment
    ) {
        PlayerInternalNavigationItemsRow(
            navController = navController,
            navItems = navItems,
            currentRoute = currentRoute,
            modifier = Modifier.height(navHeight), // Content has fixed height
            navBarStyle = navBarStyle
        )
    }
}