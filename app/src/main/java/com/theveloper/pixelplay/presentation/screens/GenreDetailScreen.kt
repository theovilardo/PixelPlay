package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton // Import FAB
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
// Removed TopAppBar and TopAppBarDefaults as GradientTopBar will be used
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color // Added for default color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.data.model.Song // Import Song
import com.theveloper.pixelplay.presentation.components.CollapsibleGenreTopBar
import com.theveloper.pixelplay.presentation.components.GenreGradientTopBar
// Attempt to import ExpressiveSongListItem. If this fails, a local one will be used.
// import com.theveloper.pixelplay.presentation.screens.ExpressiveSongListItem // Path might vary
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight // For MiniPlayerHeight if needed for padding
import com.theveloper.pixelplay.presentation.components.SmartImage // For a simple song item
import com.theveloper.pixelplay.presentation.viewmodel.GenreDetailViewModel
import com.theveloper.pixelplay.presentation.viewmodel.GroupedSongListItem // Import the new sealed interface
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel // Assuming PlayerViewModel might be needed
import com.theveloper.pixelplay.utils.formatDuration
import com.theveloper.pixelplay.utils.hexToColor // Import hexToColor
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreDetailScreen(
    navController: NavHostController,
    genreId: String,
    decodedGenreId: String = java.net.URLDecoder.decode(genreId, "UTF-8"),
    playerViewModel: PlayerViewModel,
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()

    val darkMode = isSystemInDarkTheme()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    val isMiniPlayerVisible = stablePlayerState.isPlaying || stablePlayerState.currentSong != null

    val fabBottomPadding = animateDpAsState(
        targetValue = if (isMiniPlayerVisible) {
            MiniPlayerHeight + 8.dp
        } else {
            16.dp
        },
        label = "fabBottomPaddingAnimation"
    ).value

    val fabShape = AbsoluteSmoothCornerShape(
        cornerRadiusBL = 24.dp,
        smoothnessAsPercentBR = 70,
        cornerRadiusBR = 24.dp,
        smoothnessAsPercentBL = 70,
        cornerRadiusTL = 24.dp,
        smoothnessAsPercentTR = 70,
        cornerRadiusTR = 24.dp,
        smoothnessAsPercentTL = 70
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val startColor = hexToColor(
                hex = if (darkMode) uiState.genre?.darkColorHex else uiState.genre?.lightColorHex,
                defaultColor = MaterialTheme.colorScheme.surfaceVariant
            )
            val endColor = MaterialTheme.colorScheme.background

            val onColor = hexToColor(
                hex = if (darkMode) uiState.genre?.onDarkColorHex else uiState.genre?.onLightColorHex,
                defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GenreGradientTopBar(
                title = uiState.genre?.name ?: "Genre Details",
                startColor = startColor,
                endColor = endColor,
                contentColor = onColor,
                scrollBehavior = scrollBehavior,
                onNavigationIconClick = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            if (uiState.songs.isNotEmpty()) {
                MediumFloatingActionButton(
                    modifier = Modifier
                        .padding(
                            end = 10.dp,
                            bottom = fabBottomPadding
                        ),
                    shape = fabShape,
                    onClick = {
                        if (uiState.songs.isNotEmpty()) {
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
        // **LA CORRECCIÓN CLAVE ESTÁ AQUÍ**
        // Usamos un Box como contenedor principal en lugar de una Column.
        // Esto simplifica la jerarquía y soluciona el problema del scroll.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            if (uiState.isLoadingGenreName && uiState.genre == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null && uiState.genre == null) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                if (uiState.isLoadingSongs) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.songs.isEmpty()) {
                    Text(
                        if (uiState.error != null) "Error loading songs: ${uiState.error}" else "No songs found for this genre.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                } else {
                    // Ahora la LazyColumn llena el Box, lo que le permite ser scrollable.
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                            .padding(horizontal = 16.dp)
                            .clip(
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 28.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusTL = 28.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusBL = 0.dp,
                                    cornerRadiusBR = 0.dp
                                )
                            )
                            //.background(Color.Red)
                        ,
                        contentPadding = PaddingValues(bottom = MiniPlayerHeight + 36.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        val sections = buildSections(uiState.groupedSongs)

                        items(sections, key = { it.id }) { section ->
                            when (section) {
                                is SectionData.ArtistSection -> {
                                    ArtistSectionCard(
                                        artistName = section.artistName,
                                        albums = section.albums,
                                        onSongClick = { song ->
                                            playerViewModel.showAndPlaySong(
                                                song,
                                                uiState.songs,
                                                uiState.genre?.name ?: "Genre"
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(MiniPlayerHeight + 36.dp)) }
                    }
                }
            }
        }
    }
}

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun GenreDetailScreen(
//    navController: NavHostController,
//    genreId: String, // genreId is passed directly as per AppNavigation setup
//    playerViewModel: PlayerViewModel, // Keep if needed for playback controls from this screen
//    viewModel: GenreDetailViewModel = hiltViewModel()
//) {
//    val uiState by viewModel.uiState.collectAsState()
//    val playerSheetState by playerViewModel.sheetState.collectAsState()
//    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
//
//    val darkMode = isSystemInDarkTheme()
//
//    // Create scroll behavior for collapsible top bar
//    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
//        rememberTopAppBarState()
//    )
//
//    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
//        playerViewModel.collapsePlayerSheet()
//    }
//
//    val isMiniPlayerVisible = stablePlayerState.isPlaying || stablePlayerState.currentSong != null
//
//    // Animate FAB bottom padding
//    val fabBottomPadding = animateDpAsState(
//        targetValue = if (isMiniPlayerVisible) {
//            MiniPlayerHeight + 8.dp // Height of mini player + some extra spacing
//        } else {
//            16.dp // Default bottom padding when mini player is not visible (or fully expanded)
//        },
//        label = "fabBottomPaddingAnimation"
//    ).value // Don't forget .value to get the Dp
//
//    val fabShape = AbsoluteSmoothCornerShape(
//        cornerRadiusBL = 28.dp,
//        smoothnessAsPercentBR = 60,
//        cornerRadiusBR = 28.dp,
//        smoothnessAsPercentBL = 60,
//        cornerRadiusTL = 28.dp,
//        smoothnessAsPercentTR = 60,
//        cornerRadiusTR = 28.dp,
//        smoothnessAsPercentTL = 60
//    )
//
//    Scaffold(
//        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
//        topBar = {
//            val startColor = hexToColor(
//                hex = if (darkMode) uiState.genre?.darkColorHex else uiState.genre?.lightColorHex,
//                defaultColor = MaterialTheme.colorScheme.surfaceVariant // Fallback color
//            )
//            val endColor = MaterialTheme.colorScheme.background // End color for the gradient
//
//            val onColor = hexToColor(
//                hex = if (darkMode) uiState.genre?.onDarkColorHex else uiState.genre?.onLightColorHex,
//                defaultColor = MaterialTheme.colorScheme.onSurfaceVariant // Fallback color
//            )
//
//            // Usamos la nueva versión refactorizada de la TopBar
//            CollapsibleGenreTopBar(
//                title = uiState.genre?.name ?: "Genre Details",
//                startColor = startColor,
//                endColor = endColor,
//                contentColor = onColor,
//                scrollBehavior = scrollBehavior,
//                onNavigationIconClick = { navController.popBackStack() }
//            )
////            GenreGradientTopBar(
////                title = uiState.genre?.name ?: "Genre Details",
////                startColor = startColor,
////                endColor = endColor,
////                contentColor = onColor,
////                onNavigationIconClick = { navController.popBackStack() }
////            )
//        },
//        floatingActionButton = {
//            if (uiState.songs.isNotEmpty()) {
//                LargeFloatingActionButton(
//                    modifier = Modifier
//                        .padding(
//                            end = 10.dp,
//                            bottom = fabBottomPadding
//                        ),
//                    shape = fabShape,
//                    onClick = {
//                        if (uiState.songs.isNotEmpty()) { // Double check, though FAB is conditional
//                            val randomSong = uiState.songs.random()
//                            playerViewModel.showAndPlaySong(randomSong, uiState.songs, uiState.genre?.name ?: "Genre Shuffle")
//                        }
//                    },
//                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
//                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
//                ) {
//                    Icon(Icons.Filled.Shuffle, contentDescription = "Play Random")
//                }
//            }
//        },
//        containerColor = MaterialTheme.colorScheme.background
//    ) { paddingValues ->
//        Column(modifier = Modifier
//            .fillMaxSize()
//            .padding(paddingValues)) {
//
//            if (uiState.isLoadingGenreName && uiState.genre == null) {
//                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
//                    CircularProgressIndicator()
//                }
//            } else if (uiState.error != null && uiState.genre == null) { // Show general error if genre name failed
//                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
//                    Text(
//                        text = "Error: ${uiState.error}",
//                        color = MaterialTheme.colorScheme.error,
//                        modifier = Modifier.padding(16.dp)
//                    )
//                }
//            } else { // Genre name loaded or error is specific to songs
//                if (uiState.isLoadingSongs) {
//                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
//                        CircularProgressIndicator()
//                    }
//                } else if (uiState.songs.isEmpty()) {
//                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
//                        Text(
//                            if (uiState.error != null) "Error loading songs: ${uiState.error}" else "No songs found for this genre.",
//                            modifier = Modifier.padding(16.dp)
//                        )
//                    }
//                } else {
//                    LazyColumn(
//                        contentPadding = PaddingValues(vertical = 16.dp),
//                        verticalArrangement = Arrangement.spacedBy(24.dp)
//                    ) {
//                        // Group songs by artist and album for better organization
//                        val sections = buildSections(uiState.groupedSongs)
//
//                        items(sections, key = { it.id }) { section ->
//                            when (section) {
//                                is SectionData.ArtistSection -> {
//                                    ArtistSectionCard(
//                                        artistName = section.artistName,
//                                        albums = section.albums,
//                                        onSongClick = { song ->
//                                            playerViewModel.showAndPlaySong(
//                                                song,
//                                                uiState.songs,
//                                                uiState.genre?.name ?: "Genre"
//                                            )
//                                        }
//                                    )
//                                }
//                            }
//                        }
//
//                        // Add spacer for the mini player if visible
//                        item { Spacer(modifier = Modifier.height(MiniPlayerHeight + 16.dp)) }
//                    }
//                }
//            }
//        }
//    }
//}

// Data classes for better organization
private sealed class SectionData {
    abstract val id: String

    data class ArtistSection(
        override val id: String,
        val artistName: String,
        val albums: List<AlbumData>
    ) : SectionData()
}

private data class AlbumData(
    val name: String,
    val artUri: String?,
    val songs: List<Song>
)

// Helper function to build sections from grouped songs
private fun buildSections(groupedSongs: List<GroupedSongListItem>): List<SectionData> {
    val sections = mutableListOf<SectionData>()
    var currentArtist: String? = null
    var currentAlbums = mutableListOf<AlbumData>()
    var currentAlbumSongs = mutableListOf<Song>()
    var currentAlbumName: String? = null
    var currentAlbumArt: String? = null

    for (item in groupedSongs) {
        when (item) {
            is GroupedSongListItem.ArtistHeader -> {
                // Save previous artist section if exists
                if (currentArtist != null) {
                    // Save current album if exists
                    if (currentAlbumName != null && currentAlbumSongs.isNotEmpty()) {
                        currentAlbums.add(
                            AlbumData(currentAlbumName!!, currentAlbumArt, currentAlbumSongs.toList())
                        )
                    }
                    sections.add(
                        SectionData.ArtistSection(
                            id = "artist_${currentArtist}",
                            artistName = currentArtist!!,
                            albums = currentAlbums.toList()
                        )
                    )
                }

                // Start new artist
                currentArtist = item.name
                currentAlbums.clear()
                currentAlbumSongs.clear()
                currentAlbumName = null
                currentAlbumArt = null
            }

            is GroupedSongListItem.AlbumHeader -> {
                // Save previous album if exists
                if (currentAlbumName != null && currentAlbumSongs.isNotEmpty()) {
                    currentAlbums.add(
                        AlbumData(currentAlbumName!!, currentAlbumArt, currentAlbumSongs.toList())
                    )
                }

                // Start new album
                currentAlbumName = item.name
                currentAlbumArt = item.albumArtUri
                currentAlbumSongs.clear()
            }

            is GroupedSongListItem.SongItem -> {
                currentAlbumSongs.add(item.song)
            }
        }
    }

    // Save last artist section
    if (currentArtist != null) {
        if (currentAlbumName != null && currentAlbumSongs.isNotEmpty()) {
            currentAlbums.add(
                AlbumData(currentAlbumName!!, currentAlbumArt, currentAlbumSongs.toList())
            )
        }
        sections.add(
            SectionData.ArtistSection(
                id = "artist_${currentArtist}",
                artistName = currentArtist!!,
                albums = currentAlbums.toList()
            )
        )
    }

    return sections
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistSectionCard(
    artistName: String,
    albums: List<AlbumData>,
    onSongClick: (Song) -> Unit
) {
    val shadowShaoe = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 28.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTL = 28.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBL = 28.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusBR = 28.dp,
        smoothnessAsPercentBL = 60
    )
    Column(
        modifier = Modifier
//            .shadow(
//                elevation = 4.dp,
//                shape = shadowShaoe
//            )
    ) {
        // Artist Header with gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.padding(horizontal = 16.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset.Infinite
                    ),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = 28.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTL = 28.dp,
                        smoothnessAsPercentTR = 60
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Albums section with connected background
        Card(
            modifier = Modifier
                .fillMaxWidth()
                //.padding(horizontal = 16.dp)
            ,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBR = 28.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 28.dp,
                smoothnessAsPercentBR = 60
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                albums.forEach { album ->
                    AlbumSection(
                        album = album,
                        onSongClick = onSongClick
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSection(
    album: AlbumData,
    onSongClick: (Song) -> Unit
) {
    Column {
        // Album Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            SmartImage(
                model = album.artUri,
                contentDescription = "Album art for ${album.name}",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "ALBUM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal scrolling songs
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            //contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(album.songs, key = { it.id }) { song ->
                SquareSongCard(
                    song = song,
                    onClick = { onSongClick(song) }
                )
            }
        }
    }
}

@Composable
private fun SquareSongCard(
    song: Song,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Box {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Album Art
                Card(
                    shape = RoundedCornerShape(6.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = "Album art for ${song.title}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Song Info
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Duration or play indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    song.duration?.let { duration ->
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun GenreDetailScreen(
//    navController: NavHostController,
//    genreId: String, // genreId is passed directly as per AppNavigation setup
//    playerViewModel: PlayerViewModel, // Keep if needed for playback controls from this screen
//    viewModel: GenreDetailViewModel = hiltViewModel()
//) {
//    val uiState by viewModel.uiState.collectAsState()
//    val playerSheetState by playerViewModel.sheetState.collectAsState()
//    val playerUiState by playerViewModel.playerUiState.collectAsState()
//    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
//
//    val darkMode = isSystemInDarkTheme()
//
//    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
//        playerViewModel.collapsePlayerSheet()
//    }
//
//    val isMiniPlayerVisible = stablePlayerState.isPlaying || stablePlayerState.currentSong != null
//
//    // Animate FAB bottom padding
//    val fabBottomPadding = animateDpAsState(
//        targetValue = if (isMiniPlayerVisible) {
//            MiniPlayerHeight + 8.dp // Height of mini player + some extra spacing
//        } else {
//            16.dp // Default bottom padding when mini player is not visible (or fully expanded)
//        },
//        label = "fabBottomPaddingAnimation"
//    ).value // Don't forget .value to get the Dp
//
//    val fabShape = AbsoluteSmoothCornerShape(
//        cornerRadiusBL = 28.dp,
//        smoothnessAsPercentBR = 60,
//        cornerRadiusBR = 28.dp,
//        smoothnessAsPercentBL = 60,
//        cornerRadiusTL = 28.dp,
//        smoothnessAsPercentTR = 60,
//        cornerRadiusTR = 28.dp,
//        smoothnessAsPercentTL = 60
//    )
//
//    Scaffold(
//        topBar = {
//            val startColor = hexToColor(
//                hex = if (darkMode) uiState.genre?.darkColorHex else uiState.genre?.lightColorHex,
//                defaultColor = MaterialTheme.colorScheme.surfaceVariant // Fallback color
//            )
//            val endColor = MaterialTheme.colorScheme.background // End color for the gradient
//
//            val onColor = hexToColor(
//                hex = if (darkMode) uiState.genre?.onDarkColorHex else uiState.genre?.onLightColorHex,
//                defaultColor = MaterialTheme.colorScheme.onSurfaceVariant // Fallback color
//            )
//
//            GenreGradientTopBar(
//                title = uiState.genre?.name ?: "Genre Details",
//                startColor = startColor,
//                endColor = endColor,
//                contentColor = onColor,
//                onNavigationIconClick = { navController.popBackStack() }
//            )
//        },
//        floatingActionButton = {
//            if (uiState.songs.isNotEmpty()) {
//                LargeFloatingActionButton(
//                    modifier = Modifier
//                        .padding(
//                            end = 10.dp,
//                            bottom = fabBottomPadding
//                        ),
//                    shape = fabShape,
//                    onClick = {
//                        if (uiState.songs.isNotEmpty()) { // Double check, though FAB is conditional
//                            val randomSong = uiState.songs.random()
//                            playerViewModel.showAndPlaySong(randomSong, uiState.songs, uiState.genre?.name ?: "Genre Shuffle")
//                        }
//                    },
//                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
//                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
//                ) {
//                    Icon(Icons.Filled.Shuffle, contentDescription = "Play Random")
//                }
//            }
//        },
//        containerColor = MaterialTheme.colorScheme.background
//    ) { paddingValues ->
//        Column(modifier = Modifier
//            .fillMaxSize()
//            .padding(paddingValues)) {
//
//            if (uiState.isLoadingGenreName && uiState.genre == null) {
//                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
//                    CircularProgressIndicator()
//                }
//            } else if (uiState.error != null && uiState.genre == null) { // Show general error if genre name failed
//                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
//                    Text(
//                        text = "Error: ${uiState.error}",
//                        color = MaterialTheme.colorScheme.error,
//                        modifier = Modifier.padding(16.dp)
//                    )
//                }
//            } else { // Genre name loaded or error is specific to songs
//                // Display genre name even if songs are loading/failed
//                uiState.genre?.name?.let {
//                    // Text("Songs for ${it}:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
//                    // Already in AppBar
//                }
//
//                if (uiState.isLoadingSongs) {
//                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
//                        CircularProgressIndicator()
//                    }
//                } else if (uiState.songs.isEmpty()) {
//                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center){
//                        Text(
//                            if (uiState.error != null) "Error loading songs: ${uiState.error}" else "No songs found for this genre.",
//                            modifier = Modifier.padding(16.dp)
//                        )
//                    }
//                } else {
//                    LazyColumn(
//                        contentPadding = PaddingValues(vertical = 8.dp) // Horizontal padding handled by items
//                    ) {
//                        items(uiState.groupedSongs, key = { item ->
//                            when (item) {
//                                is GroupedSongListItem.ArtistHeader -> "artist_${item.name}"
//                                is GroupedSongListItem.AlbumHeader -> "album_${item.artistName}_${item.name}"
//                                is GroupedSongListItem.SongItem -> "song_${item.song.id}"
//                            }
//                        }) { item ->
//                            when (item) {
//                                is GroupedSongListItem.ArtistHeader -> {
//                                    Text(
//                                        text = item.name,
//                                        style = MaterialTheme.typography.titleMedium,
//                                        modifier = Modifier
//                                            .fillMaxWidth()
//                                            .padding(horizontal = 16.dp, vertical = 8.dp)
//                                    )
//                                }
//                                is GroupedSongListItem.AlbumHeader -> {
//                                    Row(
//                                        verticalAlignment = Alignment.CenterVertically,
//                                        modifier = Modifier
//                                            .fillMaxWidth()
//                                            .padding(horizontal = 16.dp, vertical = 4.dp)
//                                    ) {
//                                        SmartImage(
//                                            model = item.albumArtUri,
//                                            contentDescription = "Album art for ${item.name}",
//                                            modifier = Modifier
//                                                .size(40.dp) // Smaller for album header
//                                                .clip(CircleShape)
//                                        )
//                                        Spacer(Modifier.width(12.dp))
//                                        Text(
//                                            text = item.name,
//                                            style = MaterialTheme.typography.titleSmall,
//                                            maxLines = 1
//                                        )
//                                    }
//                                }
//                                is GroupedSongListItem.SongItem -> {
//                                    SimpleGenreSongItem(
//                                        song = item.song,
//                                        onClick = {
//                                            // Important: Pass the original flat list (uiState.songs) for playback context
//                                            playerViewModel.showAndPlaySong(item.song, uiState.songs, uiState.genre?.name ?: "Genre")
//                                        },
//                                        modifier = Modifier.padding(horizontal = 16.dp) // Add horizontal padding to song item
//                                    )
//                                }
//                            }
//                            // Add a small spacer after each item, except for the last one perhaps
//                            Spacer(modifier = Modifier.height(4.dp))
//                        }
//                        // Add spacer for the mini player if visible
//                        item { Spacer(modifier = Modifier.height(MiniPlayerHeight + 16.dp)) }
//                    }
//                }
//            }
//        }
//    }
//}
//
//// Simple Song Item for GenreDetailScreen - to avoid complex dependencies for now
//@Composable
//private fun SimpleGenreSongItem(song: Song, onClick: () -> Unit, modifier: Modifier = Modifier) {
//    Row(
//        modifier = modifier // Apply the modifier passed from the LazyColumn item
//            .fillMaxWidth()
//            .clickable(onClick = onClick)
//            .padding(vertical = 8.dp), // Keep vertical padding internal to the item
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        SmartImage(
//            model = song.albumArtUriString,
//            contentDescription = "Album art for ${song.title}",
//            modifier = Modifier.size(48.dp).clip(CircleShape)
//        )
//        Spacer(Modifier.width(16.dp))
//        Column(modifier = Modifier.weight(1f)) {
//            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
//            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
//        }
//    }
//}
