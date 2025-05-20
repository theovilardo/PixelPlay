package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.GhostFAB
import com.theveloper.pixelplay.presentation.components.PillCornerRadius
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

data class BottomNavItem(
    val label: String,
    val icon: Painter,
    val selectedIcon: Painter? = null,
    val screen: Screen
)

// Updated MainLayout with improved floating navigation and animations
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(
    navController: NavController,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    content: @Composable (PaddingValues) -> Unit
) {
    val navItems = listOf(
        BottomNavItem(
            "Home",
            painterResource(R.drawable.rounded_home_24),
            painterResource(R.drawable.rounded_home_24),
            Screen.Home
        ),
        BottomNavItem(
            "Search",
            painterResource(R.drawable.rounded_search_24),
            painterResource(R.drawable.rounded_search_24),
            Screen.Search
        ),
        BottomNavItem(
            "Library",
            painterResource(R.drawable.rounded_library_music_24),
            painterResource(R.drawable.rounded_library_music_24),
            Screen.Library
        )
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState() // Para el estado del reproductor
    val showBottomBar = currentRoute != null
    val bottomBarBlackList = listOf(
        Screen.PlaylistDetail.route,
        Screen.Settings.route
    )
    val isSheetVisible by playerViewModel.isSheetVisible.collectAsState()

    val topPlayerEnabledCorners by animateDpAsState(
        targetValue = if ((isSheetVisible || stablePlayerState.isPlaying) || navController.currentDestination?.route == Screen.Library.route) 12.dp else 60.dp,
        label = "navBarCornerAnimation" // Rótulo opcional para ferramentas de depuração
    )

    // Actualizar la altura de la bottom bar en el ViewModel cuando cambie
    LaunchedEffect(showBottomBar) {
        if (!showBottomBar) {
            playerViewModel.updateBottomBarHeight(0)
        }
        // Si showBottomBar se vuelve true, onSizeChanged de la NavigationBar lo actualizará.
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = (!bottomBarBlackList.contains(navController.currentDestination?.route)) && showBottomBar,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    val barCorners = 70.dp
                    //val topPlayerEnabledCorners = 12.dp // if (stablePlayerState.isPlaying) 12.dp else 70.dp
                    Box(
                        modifier = Modifier
                            .padding(end = 22.dp, start = 22.dp, bottom = 0.dp)
                            .navigationBarsPadding()
                            .shadow(
                                elevation = 3.0.dp,
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTL = topPlayerEnabledCorners,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusBL = barCorners,
                                    smoothnessAsPercentBL = 60,
                                    cornerRadiusBR = barCorners,
                                    smoothnessAsPercentBR = 60,
                                    cornerRadiusTR = topPlayerEnabledCorners,
                                    smoothnessAsPercentTR = 60
                                )
                            )
                            .background(
                                color = NavigationBarDefaults.containerColor,
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTL = topPlayerEnabledCorners,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusBL = barCorners,
                                    smoothnessAsPercentBL = 60,
                                    cornerRadiusBR = barCorners,
                                    smoothnessAsPercentBR = 60,
                                    cornerRadiusTR = topPlayerEnabledCorners,
                                    smoothnessAsPercentTR = 60
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    playerViewModel.updateBottomBarHeight(size.height) // Reportar altura en Px
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            navItems.forEach { item ->
                                val isSelected = currentRoute == item.screen.route
                                val icon = if (isSelected) item.selectedIcon ?: item.icon else item.icon
                                NavigationBarItem(
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(top = 2.dp),
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
                                    icon = {
                                        Icon(
                                            painter = item.icon,
                                            contentDescription = item.label
                                        )
                                    },
                                    enabled = true,
                                    label = {
                                        Text(item.label)
                                    },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center, // Posición estándar del FAB
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->

            val contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
                end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
                //bottom = bottomPadding
            )

            // Render main content
            content(contentPadding)
        }
    }
}