package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R

sealed class DrawerDestination(val route: String, val title: String) {
    object Home : DrawerDestination("home", "Home")
    object Equalizer : DrawerDestination("equalizer", "Equalizer")
    object Settings : DrawerDestination("settings", "Settings")
}

@Composable
fun AppSidebarDrawer(
    drawerState: DrawerState,
    selectedRoute: String,
    onDestinationSelected: (DrawerDestination) -> Unit,
    content: @Composable () -> Unit
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                DrawerContent(
                    selectedRoute = selectedRoute,
                    onDestinationSelected = onDestinationSelected
                )
            }
        },
        content = content
    )
}

@Composable
private fun DrawerContent(
    selectedRoute: String,
    onDestinationSelected: (DrawerDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // App Header
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp)
        ) {
            Text(
                text = "PixelPlay",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Music Player",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Navigation Items
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = "Home"
                )
            },
            label = {
                Text(
                    text = "Home",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            selected = selectedRoute == DrawerDestination.Home.route,
            onClick = { onDestinationSelected(DrawerDestination.Home) },
            modifier = Modifier.padding(vertical = 4.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        )
        
        NavigationDrawerItem(
            icon = {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Equalizer"
                )
            },
            label = {
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            selected = selectedRoute == DrawerDestination.Equalizer.route,
            onClick = { onDestinationSelected(DrawerDestination.Equalizer) },
            modifier = Modifier.padding(vertical = 4.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        
        // Settings at bottom
        NavigationDrawerItem(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.rounded_settings_24),
                    contentDescription = "Settings"
                )
            },
            label = {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            selected = selectedRoute == DrawerDestination.Settings.route,
            onClick = { onDestinationSelected(DrawerDestination.Settings) },
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 0.dp),
            colors = NavigationDrawerItemDefaults.colors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
