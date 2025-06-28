package com.theveloper.pixelplay.presentation.navigation

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.theveloper.pixelplay.presentation.screens.AlbumDetailScreen
import com.theveloper.pixelplay.presentation.screens.DailyMixScreen
import com.theveloper.pixelplay.presentation.screens.GenreDetailScreen
import com.theveloper.pixelplay.presentation.screens.HomeScreen
import com.theveloper.pixelplay.presentation.screens.LibraryScreen
import com.theveloper.pixelplay.presentation.screens.PlaylistDetailScreen
import com.theveloper.pixelplay.presentation.screens.SearchScreen
import com.theveloper.pixelplay.presentation.screens.SettingsScreen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel

@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
) {
    MainLayout { paddingValues ->
        NavHost(navController = navController, startDestination = Screen.Home.route) {
            composable(Screen.Home.route) {
                HomeScreen(navController = navController, paddingValuesParent = paddingValues, playerViewModel = playerViewModel)
            }
            composable(Screen.Search.route) {
                SearchScreen(paddingValues = paddingValues, playerViewModel = playerViewModel, navController = navController)
            }
            composable(Screen.Library.route) {
                LibraryScreen(navController = navController, playerViewModel = playerViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    playerViewModel = playerViewModel,
                    onNavigationIconClick = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.DailyMixScreen.route) {
                // hiltViewModel() inyectará la instancia correcta de MainViewModel
                //val mainViewModel: MainViewModel = hiltViewModel()

                DailyMixScreen(
                    playerViewModel = playerViewModel,
                    navController = navController
                )
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
                        playerViewModel = playerViewModel,
                        playlistViewModel = playlistViewModel,
                        onBackClick = { navController.popBackStack() },
                        onDeletePlayListClick = { navController.popBackStack() }
                    )
                }
            }

            composable(
                route = Screen.GenreDetail.route,
                arguments = listOf(navArgument("genreId") { type = NavType.StringType })
            ) { backStackEntry ->
                val genreId = backStackEntry.arguments?.getString("genreId")
                // val playerViewModel: PlayerViewModel = hiltViewModel() // playerViewModel is already in AppNavigation's scope

                if (genreId != null) {
                    GenreDetailScreen(
                        navController = navController,
                        genreId = genreId,
                        playerViewModel = playerViewModel // Pass the existing playerViewModel
                    )
                } else {
                    Text("Error: Genre ID missing", modifier = Modifier.padding(paddingValues))
                }
            }


            // CORRECCIÓN: La definición de la ruta DEBE incluir el placeholder para el argumento.
            // Se usa StringType para el ID para que coincida con la firma de la pantalla de destino.
            composable(
                route = Screen.AlbumDetail.route + "/{albumId}",
                arguments = listOf(navArgument("albumId") { type = NavType.StringType })
            ) { backStackEntry ->
                // El viewModel se inyectará automáticamente en AlbumDetailScreen.
                // Aquí pasamos los parámetros requeridos por la firma de la pantalla.
                val albumId = backStackEntry.arguments?.getString("albumId")
                if (albumId != null) {
                    AlbumDetailScreen(
                        albumId = albumId,
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}