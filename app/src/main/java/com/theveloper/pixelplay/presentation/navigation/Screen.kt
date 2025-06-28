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
    object GenreDetail : Screen("genre_detail/{genreId}") { // New screen
        fun createRoute(genreId: String) = "genre_detail/$genreId"
    }

    // La ruta base es "album_detail". La ruta completa con el argumento se define en AppNavigation.
    object AlbumDetail : Screen("album_detail/{albumId}") {
        // Función de ayuda para construir la ruta de navegación con el ID del álbum.
        fun createRoute(albumId: Long) = "$route/$albumId"
    }
}