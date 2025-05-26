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

@Immutable // Explicitly mark as immutable for Compose compiler, good practice
data class BottomNavItem(
    val label: String,
    @DrawableRes val iconResId: Int, // Changed from Painter to Int ResId
    @DrawableRes val selectedIconResId: Int? = null, // Changed from Painter to Int ResId
    val screen: Screen
)

// Updated MainLayout with improved floating navigation and animations
// --- MainLayout Modificado ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainLayout(
    navController: NavHostController,
    navItems: List<BottomNavItem>,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        content(innerPadding) // El contenido principal de la app
    }
}

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun MainLayout(
//    navController: NavController,
//    playerViewModel: PlayerViewModel = hiltViewModel(),
//    content: @Composable (PaddingValues) -> Unit
//) {
//    val navItems = listOf(
//        BottomNavItem(
//            "Home",
//            R.drawable.rounded_home_24,
//            R.drawable.rounded_home_24,
//            Screen.Home
//        ),
//        BottomNavItem(
//            "Search",
//            R.drawable.rounded_search_24,
//            R.drawable.rounded_search_24,
//            Screen.Search
//        ),
//        BottomNavItem(
//            "Library",
//            R.drawable.rounded_library_music_24,
//            R.drawable.rounded_library_music_24,
//            Screen.Library
//        )
//    )
//
//    val navBackStackEntry by navController.currentBackStackEntryAsState()
//    val currentRoute = navBackStackEntry?.destination?.route
//    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState() // Para el estado del reproductor
//    val showBottomBar = currentRoute != null
//    val bottomBarBlackList = listOf(
//        Screen.PlaylistDetail.route,
//        Screen.Settings.route
//    )
//    val isSheetVisible by playerViewModel.isSheetVisible.collectAsState()
//
//    val topPlayerEnabledCorners by animateDpAsState(
//        targetValue = if ((isSheetVisible || stablePlayerState.isPlaying) || navController.currentDestination?.route == Screen.Library.route) 12.dp else 60.dp,
//        label = "navBarCornerAnimation" // Rótulo opcional para ferramentas de depuração
//    )
//
//    // Actualizar la altura de la bottom bar en el ViewModel cuando cambie
//    LaunchedEffect(showBottomBar) {
//        if (!showBottomBar) {
//            playerViewModel.updateBottomBarHeight(0)
//        }
//        // Si showBottomBar se vuelve true, onSizeChanged de la NavigationBar lo actualizará.
//    }
//
//    Box(modifier = Modifier.fillMaxSize()) {
//        // Main content
//        Scaffold(
//            bottomBar = {
//                AnimatedVisibility(
//                    visible = (!bottomBarBlackList.contains(navController.currentDestination?.route)) && showBottomBar,
//                    enter = fadeIn() + slideInVertically { it },
//                    exit = fadeOut() + slideOutVertically { it }
//                ) {
//                    val barCorners = 70.dp
//                    Box(
//                        modifier = Modifier
//                            .padding(end = 22.dp, start = 22.dp, bottom = 0.dp)
//                            .navigationBarsPadding()
//                            .shadow(
//                                elevation = 3.0.dp,
//                                shape = AbsoluteSmoothCornerShape(
//                                    cornerRadiusTL = topPlayerEnabledCorners,
//                                    smoothnessAsPercentTL = 60,
//                                    cornerRadiusBL = barCorners,
//                                    smoothnessAsPercentBL = 60,
//                                    cornerRadiusBR = barCorners,
//                                    smoothnessAsPercentBR = 60,
//                                    cornerRadiusTR = topPlayerEnabledCorners,
//                                    smoothnessAsPercentTR = 60
//                                )
//                            )
//                            .background(
//                                color = NavigationBarDefaults.containerColor,
//                                shape = AbsoluteSmoothCornerShape(
//                                    cornerRadiusTL = topPlayerEnabledCorners,
//                                    smoothnessAsPercentTL = 60,
//                                    cornerRadiusBL = barCorners,
//                                    smoothnessAsPercentBL = 60,
//                                    cornerRadiusBR = barCorners,
//                                    smoothnessAsPercentBR = 60,
//                                    cornerRadiusTR = topPlayerEnabledCorners,
//                                    smoothnessAsPercentTR = 60
//                                )
//                            )
//                    ) {
//                        Row(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .onSizeChanged { size ->
//                                    playerViewModel.updateBottomBarHeight(size.height) // Reportar altura en Px
//                                },
//                            horizontalArrangement = Arrangement.SpaceBetween,
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            navItems.forEach { item ->
//                                val isSelected = currentRoute == item.screen.route
//                                val icon = if (isSelected) item.selectedIconResId ?: item.iconResId else item.iconResId
//                                NavigationBarItem(
//                                    modifier = Modifier
//                                        .align(Alignment.CenterVertically)
//                                        .padding(top = 2.dp),
//                                    selected = isSelected,
//                                    onClick = {
//                                        navController.navigate(item.screen.route) {
//                                            // 1) Limpia TODO el back-stack (incluyendo la 'raíz')
//                                            popUpTo(navController.graph.id) {
//                                                inclusive = true     // elimina TODO lo anterior
//                                                saveState = false
//                                            }
//                                            // 2) Evita duplicados
//                                            launchSingleTop = true
//                                            // 3) No recuperes absolutamente ningún estado previo
//                                            restoreState = false
//                                        }
//                                    },
//                                    icon = {
//                                        Icon(
//                                            painter = painterResource(icon),
//                                            contentDescription = item.label
//                                        )
//                                    },
//                                    enabled = true,
//                                    label = {
//                                        Text(item.label)
//                                    },
//                                    alwaysShowLabel = true
//                                )
//                            }
//                        }
//                    }
//                }
//            },
//            floatingActionButtonPosition = FabPosition.Center, // Posición estándar del FAB
//            containerColor = MaterialTheme.colorScheme.background
//        ) { innerPadding ->
//
//            val contentPadding = PaddingValues(
//                top = innerPadding.calculateTopPadding(),
//                start = innerPadding.calculateStartPadding(LayoutDirection.Ltr),
//                end = innerPadding.calculateEndPadding(LayoutDirection.Ltr),
//                //bottom = bottomPadding
//            )
//
//            // Render main content
//            content(contentPadding)
//        }
//    }
//}