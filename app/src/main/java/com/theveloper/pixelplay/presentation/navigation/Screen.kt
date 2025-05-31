package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.runtime.Immutable


@Immutable
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Library : Screen("library")
    object Settings : Screen("settings") // Nueva pantalla,
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") { // Nueva pantalla
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }
    object  DailyMixScreen : Screen("daily_mix")
}