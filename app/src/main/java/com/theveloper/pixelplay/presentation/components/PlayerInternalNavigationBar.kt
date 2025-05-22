package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.theveloper.pixelplay.presentation.navigation.BottomNavItem

val NavBarPersistentHeight = 84.dp // Altura estimada o fija para la PlayerInternalNavigationBar
// --- NUEVO: Barra de Navegación Interna del Player ---
// --- Barra de Navegación Interna del Player ---
@Composable
fun PlayerInternalNavigationBar(
    navController: NavController,
    navItems: List<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    topCornersRadius: Dp // NUEVO: para redondear esquinas superiores cuando el player está encima
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NavBarPersistentHeight)
            .background(
                color = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(topStart = topCornersRadius, topEnd = topCornersRadius) // Aplicar radios
            )
            .padding(horizontal = 0.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        navItems.forEach { item ->
            val isSelected = currentRoute == item.screen.route
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        // 1) Limpia TODO el back-stack (incluyendo la 'raíz')
                        popUpTo(navController.graph.id) {
                            inclusive = true     // elimina TODO lo anterior
                            saveState = false
                        }
                        // 2) Evita duplicados
                        launchSingleTop = true
                        // 3) No recuperes absolutamente ningún estado previo
                        restoreState = false
                    }
                },
//                onClick = {
//                    navController.navigate(item.screen.route) {
//                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
//                        launchSingleTop = true
//                        restoreState = true
//                    }
//                },
                icon = { Icon(if (isSelected) item.selectedIcon ?: item.icon else item.icon, item.label) },
                label = { Text(item.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}