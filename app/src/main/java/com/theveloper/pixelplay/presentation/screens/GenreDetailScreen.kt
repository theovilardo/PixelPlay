package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Shuffle // Import Shuffle icon
import androidx.compose.material3.FloatingActionButton // Import FAB
// Removed TopAppBar and TopAppBarDefaults as GradientTopBar will be used
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color // Added for default color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.data.model.Song // Import Song
// Attempt to import ExpressiveSongListItem. If this fails, a local one will be used.
// import com.theveloper.pixelplay.presentation.screens.ExpressiveSongListItem // Path might vary
import com.theveloper.pixelplay.presentation.components.GradientTopBar // Import GradientTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight // For MiniPlayerHeight if needed for padding
import com.theveloper.pixelplay.presentation.components.SmartImage // For a simple song item
import com.theveloper.pixelplay.presentation.viewmodel.GenreDetailViewModel
import com.theveloper.pixelplay.presentation.viewmodel.GroupedSongListItem // Import the new sealed interface
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel // Assuming PlayerViewModel might be needed
import com.theveloper.pixelplay.utils.hexToColor // Import hexToColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    navController: NavHostController,
    genreId: String, // genreId is passed directly as per AppNavigation setup
    playerViewModel: PlayerViewModel, // Keep if needed for playback controls from this screen
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    Scaffold(
        topBar = {
            val startColor = hexToColor(
                hex = uiState.genre?.colorHex,
                defaultColor = MaterialTheme.colorScheme.surfaceVariant // Fallback color
            )
            val endColor = MaterialTheme.colorScheme.background // End color for the gradient

            GradientTopBar(
                title = uiState.genre?.name ?: "Genre Details",
                startColor = startColor,
                endColor = endColor,
                onNavigationIconClick = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            if (uiState.songs.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        if (uiState.songs.isNotEmpty()) { // Double check, though FAB is conditional
                            val randomSong = uiState.songs.random()
                            playerViewModel.showAndPlaySong(randomSong, uiState.songs, uiState.genre?.name ?: "Genre Shuffle")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Play Random")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {

            if (uiState.isLoadingGenreName && uiState.genre == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null && uiState.genre == null) { // Show general error if genre name failed
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                    Text(
                        text = "Error: ${uiState.error}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else { // Genre name loaded or error is specific to songs
                // Display genre name even if songs are loading/failed
                uiState.genre?.name?.let {
                    // Text("Songs for ${it}:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
                    // Already in AppBar
                }

                if (uiState.isLoadingSongs) {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                        CircularProgressIndicator()
                    }
                } else if (uiState.songs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
                        Text(
                            if (uiState.error != null) "Error loading songs: ${uiState.error}" else "No songs found for this genre.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp) // Horizontal padding handled by items
                    ) {
                        items(uiState.groupedSongs, key = { item ->
                            when (item) {
                                is GroupedSongListItem.ArtistHeader -> "artist_${item.name}"
                                is GroupedSongListItem.AlbumHeader -> "album_${item.artistName}_${item.name}"
                                is GroupedSongListItem.SongItem -> "song_${item.song.id}"
                            }
                        }) { item ->
                            when (item) {
                                is GroupedSongListItem.ArtistHeader -> {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                is GroupedSongListItem.AlbumHeader -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                    ) {
                                        SmartImage(
                                            model = item.albumArtUri,
                                            contentDescription = "Album art for ${item.name}",
                                            modifier = Modifier
                                                .size(40.dp) // Smaller for album header
                                                .clip(CircleShape)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1
                                        )
                                    }
                                }
                                is GroupedSongListItem.SongItem -> {
                                    SimpleGenreSongItem(
                                        song = item.song,
                                        onClick = {
                                            // Important: Pass the original flat list (uiState.songs) for playback context
                                            playerViewModel.showAndPlaySong(item.song, uiState.songs, uiState.genre?.name ?: "Genre")
                                        },
                                        modifier = Modifier.padding(horizontal = 16.dp) // Add horizontal padding to song item
                                    )
                                }
                            }
                            // Add a small spacer after each item, except for the last one perhaps
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        // Add spacer for the mini player if visible
                        item { Spacer(modifier = Modifier.height(MiniPlayerHeight + 16.dp)) }
                    }
                }
            }
        }
    }
}

// Simple Song Item for GenreDetailScreen - to avoid complex dependencies for now
@Composable
private fun SimpleGenreSongItem(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier // Apply the modifier passed from the LazyColumn item
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp), // Keep vertical padding internal to the item
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = "Album art for ${song.title}",
            modifier = Modifier.size(48.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
