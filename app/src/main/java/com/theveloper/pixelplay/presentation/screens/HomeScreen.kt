package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage3
import com.theveloper.pixelplay.presentation.components.GradientTopBar
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel


// Modern HomeScreen with collapsible top bar and staggered grid layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    updateScrollState: (Boolean) -> Unit = {}
) {
    val uiState by playerViewModel.playerUiState.collectAsState()
    val scrollState = rememberLazyListState()

    // Extraer las URIs de los últimos 4 reproducidos
    val recentUris = uiState.allSongs.take(6).map { it.albumArtUri }

    val bottomBarHeigh = playerViewModel.bottomBarHeight.collectAsState()

    if (uiState.allSongs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValuesParent),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Cargando tu música...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
        }
    } else if (uiState.allSongs.isEmpty()) {
        EmptyMusicView(paddingValues = paddingValuesParent)
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            topBar = {
                GradientTopBar(
                    navController = navController
                )
            }
        ) { paddingValues ->
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValuesParent.calculateBottomPadding() + bottomBarHeigh.value.dp + 48.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Large "Your Mix" header with play button
                item {
                    YourMixHeader(
                        playerViewModel = playerViewModel,
                        onPlayRandomSong = {
                            // Play a random song when the big play button is clicked
                            val randomSong = uiState.allSongs.firstOrNull()
                            randomSong?.let {
                                //playerViewModel.playSong(it)
                            }
                        }
                    )
                }

                // Artistic album displays with various shapes
                item {
                    AlbumArtCollage3(
                        modifier = Modifier
                            .fillMaxWidth(),
                        albumArts = recentUris,
                        padding = 14.dp,
                        height = 400.dp,
                    )
                }

                item {
                    FeaturedPlaylistsSection()
                }

//                item {
//                    MoodBasedSection(
//                        songs = uiState.allSongs.take(5),
//                        playerViewModel = playerViewModel,
//                        navController = navController
//                    )
//                }

                item {
                    DailyMixSection(
                        songs = uiState.allSongs.take(10),
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
        }
    }
}

@Composable
fun YourMixHeader(
    playerViewModel: PlayerViewModel,
    onPlayRandomSong: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            //.background(Color.Black)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp)
        ) {
            // Your Mix Title
            Text(
                text = "Your\nMix",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 56.sp,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
            )

            // Artist/Song subtitle
            Text(
                text = "Traveler, Water Houses",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
            )
        }

        // Play Button
        Box(
            modifier = Modifier
                .size(80.dp)
                .aspectRatio(1f)
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp)
                .clip(CircleShape)
                .background(Color(0xFF9EC8FF))
                .clickable { onPlayRandomSong() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_play_arrow_24),
                contentDescription = "Reproducir",
                tint = Color.Black,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun EmptyMusicView(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.rounded_music_off_24),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "No se encontraron canciones.\nAsegúrate de otorgar el permiso de almacenamiento.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { /* Request permission */ },
                shape = RoundedCornerShape(24.dp)
            ) {
                Text("Otorgar Permisos")
            }
        }
    }
}

// Updated section header with better design
@Composable
fun SectionHeader(
    title: String,
    showViewAll: Boolean = true,
    onViewAllClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )

        if (showViewAll) {
            TextButton(
                onClick = onViewAllClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "Ver todo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Icon(
                    painter = painterResource(R.drawable.rounded_chevron_right_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Placeholder for featured playlists section
@Composable
fun FeaturedPlaylistsSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        SectionHeader(
            title = "Destacados",
            showViewAll = true,
            onViewAllClick = { /* Navigate to featured */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Featured card with "Material You" design principles
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Descubre Nueva Música",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Explora nuestra selección de canciones recomendadas para ti",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { /* Action */ },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("Explorar")
                        }
                    }

                    // Right decorative elements - abstract music visuals
                    Box(
                        modifier = Modifier
                            .weight(0.7f)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                    )
                                )
                            )
                    ) {
                        // Decorative music elements
                        repeat(5) { index ->
                            val yOffset = remember { 20 + (index * 30) }
                            val width = remember { 8 + (index % 3) * 8 }

                            Box(
                                modifier = Modifier
                                    .offset(x = (60 + index * 20).dp, y = yOffset.dp)
                                    .width(width.dp)
                                    .height((40 + (index % 4) * 20).dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

// Mood based section example
@Composable
fun MoodBasedSection(
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        SectionHeader(
            title = "Para Relajarse",
            showViewAll = true,
            onViewAllClick = { /* Navigate to mood section */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(songs) { song ->
                MoodSongCard(
                    song = song,
                    onClick = {
                        //playerViewModel.loadAndPlaySong(song)
                        playerViewModel.showAndPlaySong(song) // Llama a la nueva función
                        //navController.navigate(Screen.Player.createRoute(song.id))
                    }
                )
            }
        }
    }
}

@Composable
fun MoodSongCard(
    song: Song,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .height(240.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Album art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                SmartImage(
                    model = song.albumArtUri ?: R.drawable.rounded_album_24,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
//                AsyncImage(
//                    model = song.albumArtUri ?: R.drawable.rounded_album_24,
//                    contentDescription = song.title,
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )

                // Play button overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            CircleShape
                        )
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_play_arrow_24),
                        contentDescription = "Reproducir",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Song info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Daily Mix section example
@Composable
fun DailyMixSection(
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        SectionHeader(
            title = "Your Daily Mix",
            showViewAll = true,
            onViewAllClick = { /* Navigate to daily mix */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with visual elements
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Album art collage effect
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Daily Mix icon/text
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "DAILY MIX",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )

                            Text(
                                text = "Basado en tu historial",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            )
                        }

                        // Album art thumbnails with overlap effect
                        Row(modifier = Modifier.padding(end = 16.dp)) {
                            songs.take(3).forEachIndexed { index, song ->
                                Box(
                                    modifier = Modifier
                                        .offset(x = (-16 * index).dp)
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                ) {
                                    SmartImage(
                                        model = song.albumArtUri ?: R.drawable.rounded_album_24,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
//                                    AsyncImage(
//                                        model = song.albumArtUri ?: R.drawable.rounded_album_24,
//                                        contentDescription = null,
//                                        contentScale = ContentScale.Crop,
//                                        modifier = Modifier.fillMaxSize()
//                                    )
                                }
                            }
                        }
                    }
                }

                // Song list
                Column(modifier = Modifier.fillMaxWidth()) {
                    songs.take(4).forEach { song ->
                        SongListItem(
                            song = song,
                            onClick = {
                                //playerViewModel.loadAndPlaySong(song)
                                playerViewModel.showAndPlaySong(song) // Llama a la nueva función
                                //navController.navigate(Screen.Player.createRoute(song.id))
                            }
                        )
                    }

                    // "Ver más" button
                    TextButton(
                        onClick = { /* View more */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Ver todo el Daily Mix",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Icon(
                            painter = painterResource(R.drawable.rounded_keyboard_arrow_right_24),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            SmartImage(
                model = song.albumArtUri ?: R.drawable.rounded_album_24,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
//            AsyncImage(
//                model = song.albumArtUri ?: R.drawable.rounded_album_24,
//                contentDescription = song.title,
//                contentScale = ContentScale.Crop,
//                modifier = Modifier.fillMaxSize()
//            )
        }

        // Song info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play button
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.rounded_play_arrow_24),
                contentDescription = "Reproducir",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// SongListItem (modificado para aceptar parámetros individuales)
@Composable
fun SongListItemFavs(
    title: String,
    artist: String,
    albumArtUrl: Uri?,
    isPlaying: Boolean, // Nuevo parámetro para indicar si esta canción se está reproduciendo
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isPlaying) colors.primaryContainer.copy(alpha = 0.6f) else colors.surfaceColorAtElevation(2.dp)
    val contentColor = if (isPlaying) colors.onPrimaryContainer else colors.onSurface

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = albumArtUrl ?: Icons.Filled.MusicNote,
                contentDescription = "Carátula de $title",
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            )
//            AsyncImage(
//                model = albumArtUrl ?: Icons.Filled.MusicNote,
//                contentDescription = "Carátula de $title",
//                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
//                    .background(colors.onSurface.copy(alpha = 0.1f)),
//                contentScale = ContentScale.Crop,
//                //error = { Icon(Icons.Filled.MusicNote, "Carátula", modifier = Modifier.size(48.dp), tint = contentColor.copy(alpha = 0.7f)) }
//            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist, style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(16.dp))
            if (isPlaying) {
                Icon(
                    Icons.Filled.GraphicEq, // O algún icono de "reproduciendo"
                    contentDescription = "Reproduciendo",
                    tint = colors.primary // O contentColor
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}