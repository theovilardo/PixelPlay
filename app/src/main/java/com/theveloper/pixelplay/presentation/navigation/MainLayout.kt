package com.theveloper.pixelplay.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.navigation.NavHostController

@Immutable
data class BottomNavItem(
    val label: String,
    @DrawableRes val iconResId: Int,
    @DrawableRes val selectedIconResId: Int? = null,
    val screen: Screen
)

// --- MainLayout Modificado ---
@Composable
fun MainLayout(
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        content(innerPadding) // El contenido principal de la app
    }
}