package com.theveloper.pixelplay.presentation.navigation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.theveloper.pixelplay.presentation.screens.HomeScreen
import com.theveloper.pixelplay.presentation.screens.LibraryScreen
import com.theveloper.pixelplay.presentation.screens.PlaylistDetailScreen
import com.theveloper.pixelplay.presentation.screens.SearchScreen
import com.theveloper.pixelplay.presentation.screens.SettingsScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(playerViewModel: PlayerViewModel, navController: NavHostController) {
    //val navController = rememberNavController()
    MainLayout(navController = navController) { paddingValues -> // paddingValues del Scaffold principal
        NavHost(navController = navController, startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {
                // Compartir mismo ViewModel entre pantallas
                //val parentEntry = remember(navController) { navController.getBackStackEntry(Screen.Home.route) }
                //val playerViewModel: PlayerViewModel = hiltViewModel(parentEntry)
                HomeScreen(navController = navController, paddingValuesParent = paddingValues, playerViewModel = playerViewModel)
            }
            composable(Screen.Search.route) {
                SearchScreen(navController = navController, paddingValues = paddingValues)
            }
            composable(Screen.Library.route) {
                LibraryScreen(navController = navController, paddingValues = paddingValues, playerViewModel = playerViewModel)
            }
            composable(Screen.Settings.route) { // Ruta para Settings
                SettingsScreen(navController = navController, paddingValues = paddingValues)
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")
                val playlistViewModel: PlaylistViewModel = hiltViewModel()
                if (playlistId != null) {
                    PlaylistDetailScreen(
                        playlistId = playlistId,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        playlistViewModel = playlistViewModel,
                        paddingValues = paddingValues
                    )
                }
            }
        }
    }
}