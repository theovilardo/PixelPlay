package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.GenreSortBottomSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.viewmodel.GenreDetailViewModel
import com.theveloper.pixelplay.presentation.viewmodel.GroupedSongListItem
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import com.theveloper.pixelplay.utils.formatDuration
import com.theveloper.pixelplay.utils.hexToColor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreDetailScreen(
    navController: NavHostController,
    genreId: String,
    decodedGenreId: String = java.net.URLDecoder.decode(genreId, "UTF-8"),
    playerViewModel: PlayerViewModel,
    viewModel: GenreDetailViewModel = hiltViewModel(),
    playlistViewModel: com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val playlistUiState by playlistViewModel.uiState.collectAsState()
    
    // Get artists to resolve images
    val artists by playerViewModel.playerUiState.map { it.artists }.collectAsState(initial = persistentListOf())

    val darkMode = LocalPixelPlayDarkTheme.current

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    // Scroll & Collapsing Top Bar State
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 58.dp + statusBarHeight // Reduced by 6dp from 64.dp
    val maxTopBarHeight = 200.dp
    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                // If scrolling up (content going down) and list is not at top, don't expand yet
                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                // Make sure we consume scroll only if we actually resized the bar
                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    // Colors
    val defaultContainer = MaterialTheme.colorScheme.surfaceVariant
    val defaultOnContainer = MaterialTheme.colorScheme.onSurfaceVariant
    val themeColor = remember(uiState.genre?.id, darkMode, defaultContainer, defaultOnContainer) {
        if (uiState.genre != null) {
            com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(uiState.genre!!.id, darkMode)
        } else {
            com.theveloper.pixelplay.ui.theme.GenreThemeColor(
                defaultContainer,
                defaultOnContainer
            )
        }
    }
    
    val startColor = themeColor.container
    val contentColor = themeColor.onContainer
    
    // FAB Logic
    var showSortSheet by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.ARTIST) }
    var showSongOptionsSheet by remember { mutableStateOf<Song?>(null) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    
    val isMiniPlayerVisible = stablePlayerState.currentSong != null
    val fabBottomPadding by animateDpAsState(
        targetValue = if (isMiniPlayerVisible) MiniPlayerHeight + 16.dp else 16.dp, 
        label = "fabPadding"
    )

    val sortedSongs = remember(uiState.songs, sortOption) {
        when (sortOption) {
            SortOption.ARTIST -> uiState.songs.sortedBy { it.artist }
            SortOption.ALBUM -> uiState.songs.sortedBy { it.album }
            SortOption.TITLE -> uiState.songs.sortedBy { it.title }
        }
    }
    
    val displaySections = remember(sortedSongs, sortOption) {
        if (sortOption == SortOption.ARTIST) {
            buildSectionsByArtist(sortedSongs)
        } else if (sortOption == SortOption.ALBUM) {
             buildSectionsByAlbum(sortedSongs)
        } else {
            listOf(SectionData.FlatList(sortedSongs))
        }
    }

    // Dynamic Theme
    val genreColorScheme = com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreColorScheme(
        genreId = uiState.genre?.id ?: "unknown",
        isDark = darkMode
    )

    // Capture Neutral Colors from the App Theme (before overriding)
    val baseColorScheme = MaterialTheme.colorScheme
    val neutralOnSurface = MaterialTheme.colorScheme.onSurface
    val neutralSurfaceContainer = MaterialTheme.colorScheme.surfaceContainer

    MaterialTheme(colorScheme = genreColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .background(MaterialTheme.colorScheme.background) // Uses new theme background
        ) {
            val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

                // Content
            CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides neutralOnSurface
            ) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = currentTopBarHeightDp + 8.dp, // Push content down initially
                        start = 16.dp,
                        end = 16.dp,
                        bottom = fabBottomPadding + 148.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                     items(displaySections, key = { it.id }) { section ->
                        when (section) {
                            is SectionData.ArtistSection -> {
                                val artistImage = remember(section.artistName, artists) {
                                    artists.find { it.name.equals(section.artistName, ignoreCase = true) }?.imageUrl
                                }

                                ArtistSectionItem(
                                    section = section,
                                    artistImageUrl = artistImage,
                                    onSongClick = { song ->
                                        playerViewModel.showAndPlaySong(song, sortedSongs, uiState.genre?.name ?: "Genre")
                                    },
                                    stablePlayerState = stablePlayerState,
                                    onMoreOptionsClick = { song -> showSongOptionsSheet = song }
                                )
                            }
                            is SectionData.AlbumSection -> {
                                AlbumSectionItem(
                                    album = section.album,
                                    onSongClick = { song ->
                                         playerViewModel.showAndPlaySong(song, sortedSongs, uiState.genre?.name ?: "Genre")
                                    },
                                    stablePlayerState = stablePlayerState,
                                    onMoreOptionsClick = { song -> showSongOptionsSheet = song }
                                )
                            }
                            is SectionData.FlatList -> {
                                 Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                     section.songs.forEach { song ->
                                         EnhancedSongListItem(
                                             song = song,
                                             isPlaying = stablePlayerState.isPlaying,
                                             isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                                             onClick = {
                                                 playerViewModel.showAndPlaySong(song, sortedSongs, uiState.genre?.name ?: "Genre")
                                             },
                                             onMoreOptionsClick = { showSongOptionsSheet = it }
                                         )
                                     }
                                 }
                            }
                        }
                    }
                }
            }

            // Collapsible Top Bar with Gradient (On Top of List, High Z-Index)
            // This ensures the gradient is ON TOP of the scrolling content, so content scrolls BEHIND it.
            GenreCollapsibleTopBar(
                title = uiState.genre?.name ?: "Genre",
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackPressed = { navController.popBackStack() },
                startColor = startColor,
                contentColor = contentColor,
                containerColor = neutralSurfaceContainer, // Collapsed Background is Neutral
                collapsedContentColor = neutralOnSurface // Collapsed Content is Neutral
            )
        
            // FAB
            Box(
                 modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = fabBottomPadding + 16.dp, end = 16.dp)
                    .zIndex(10f) // Ensure FAB is above everything
            ) {
                 LargeFloatingActionButton(
                    onClick = { showSortSheet = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = AbsoluteSmoothCornerShape(24.dp, 60)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Options",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        
            // Sorting Bottom Sheet
            if (showSortSheet) {
                GenreSortBottomSheet(
                    onDismiss = { showSortSheet = false },
                    currentSort = sortOption,
                    onSortSelected = {
                        sortOption = it
                        showSortSheet = false
                    },
                    onShuffle = {
                        if (uiState.songs.isNotEmpty()) {
                            playerViewModel.showAndPlaySong(uiState.songs.random(), uiState.songs, uiState.genre?.name ?: "Genre Shuffle")
                            showSortSheet = false
                        }
                    }
                )
            }
        
            // Song Options Bottom Sheet
            showSongOptionsSheet?.let { song ->
                val isFavorite = favoriteSongIds.contains(song.id)

                MaterialTheme(colorScheme = baseColorScheme) {
                    SongInfoBottomSheet(
                        song = song,
                        isFavorite = isFavorite,
                        onToggleFavorite = {
                            playerViewModel.toggleFavoriteSpecificSong(song)
                        },
                        onDismiss = { showSongOptionsSheet = null },
                        onPlaySong = {
                            playerViewModel.showAndPlaySong(song, sortedSongs, uiState.genre?.name ?: "Genre")
                            showSongOptionsSheet = null
                        },
                        onAddToQueue = {
                            playerViewModel.addSongToQueue(song)
                            showSongOptionsSheet = null
                            playerViewModel.sendToast("Added to the queue")
                        },
                        onAddNextToQueue = {
                            playerViewModel.addSongNextToQueue(song)
                            showSongOptionsSheet = null
                            playerViewModel.sendToast("Will play next")
                        },
                        onAddToPlayList = {
                            showPlaylistBottomSheet = true
                        },
                        onDeleteFromDevice = playerViewModel::deleteFromDevice,
                        onNavigateToAlbum = {
                            com.theveloper.pixelplay.presentation.navigation.Screen.AlbumDetail.createRoute(song.albumId).let { route ->
                                navController.navigate(route)
                            }
                            showSongOptionsSheet = null
                        },
                        onNavigateToArtist = {
                            com.theveloper.pixelplay.presentation.navigation.Screen.ArtistDetail.createRoute(song.artistId).let { route ->
                                navController.navigate(route)
                            }
                            showSongOptionsSheet = null
                        },
                        onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate ->
                            playerViewModel.editSongMetadata(song, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate)
                        },
                        generateAiMetadata = { fields ->
                            playerViewModel.generateAiMetadata(song, fields)
                        },
                        removeFromListTrigger = {}
                    )
                }

                if (showPlaylistBottomSheet) {
                    com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet(
                        playlistUiState = playlistUiState,
                        song = song,
                        onDismiss = { showPlaylistBottomSheet = false },
                        bottomBarHeight = 0.dp, // Or calculate if needed
                        playerViewModel = playerViewModel
                    )
                }
            }
        
            // Loading/Error States
            if (uiState.isLoadingSongs) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

// --- Top Bar Component ---
@Composable
fun GenreCollapsibleTopBar(
    title: String,
    collapseFraction: Float,
    headerHeight: Dp,
    onBackPressed: () -> Unit,
    startColor: Color,
    containerColor: Color,
    contentColor: Color,
    collapsedContentColor: Color
) {
    val density = LocalDensity.current
    
    // Calculate alpha for the solid background layer based on collapse fraction
    // It should become fully opaque as we approach the collapsed state
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f) // Becomes opaque halfway through collapse
    
    // Interpolate content color from Vibrant to Neutral
    val animatedContentColor = androidx.compose.ui.graphics.lerp(
        start = contentColor,
        stop = collapsedContentColor,
        fraction = solidAlpha
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .zIndex(5f)
    ) {
        // Layer 1: The solid background that fades in (Neutral Surface)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor.copy(alpha = solidAlpha)) 
        )
        
        // Layer 2: The gradient (visible when expanded, fades out when collapsed to avoid double darkening?)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            startColor.copy(alpha = 0.8f * (1f - solidAlpha)), // Fade out top slightly
                            startColor.copy(alpha = 0f) // Keep bottom transparent
                        )
                    )
                )
        )

        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
             FilledIconButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 4.dp)
                    .zIndex(10f),
                onClick = onBackPressed,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = animatedContentColor.copy(alpha = 0.1f), // Tonal style 
                    contentColor = animatedContentColor
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = animatedContentColor)
            }

            ExpressiveTopBarContent(
                title = title,
                collapseFraction = collapseFraction,
                modifier = Modifier.fillMaxSize(),
                collapsedTitleStartPadding = 68.dp,
                expandedTitleStartPadding = 20.dp,
                maxLines = 1,
                contentColor = animatedContentColor
            )
        }
    }
}

// --- Data Models & Helpers ---

enum class SortOption { ARTIST, ALBUM, TITLE }

sealed class SectionData {
    abstract val id: String
    
    data class ArtistSection(
        override val id: String,
        val artistName: String,
        val albums: List<AlbumData>
    ) : SectionData()

    data class AlbumSection(
        override val id: String,
        val album: AlbumData
    ) : SectionData()
    
    data class FlatList(
        val songs: List<Song>
    ) : SectionData() {
        override val id = "flat_list"
    }
}

data class AlbumData(
    val name: String,
    val artUri: String?,
    val songs: List<Song>
)

// Helper functions (simplified from original to match strictly the needed logic)
private fun buildSectionsByArtist(songs: List<Song>): List<SectionData> {
    val grouped = songs.groupBy { it.artist ?: "Unknown Artist" }
    return grouped.map { (artist, artistSongs) ->
        val albums = artistSongs.groupBy { it.album ?: "Unknown Album" }.map { (albumName, albumSongs) ->
             AlbumData(albumName, albumSongs.firstOrNull()?.albumArtUriString, albumSongs)
        }
        SectionData.ArtistSection("artist_$artist", artist, albums)
    }
}

private fun buildSectionsByAlbum(songs: List<Song>): List<SectionData> {
     val grouped = songs.groupBy { it.album ?: "Unknown Album" }
     return grouped.map { (album, albumSongs) ->
         SectionData.AlbumSection(
             "album_$album",
             AlbumData(album, albumSongs.firstOrNull()?.albumArtUriString, albumSongs)
         )
     }
}

// --- Section Composables ---

// --- Section Composables ---

@Composable
fun ArtistSectionItem(
    section: SectionData.ArtistSection,
    artistImageUrl: String?,
    onSongClick: (Song) -> Unit,
    stablePlayerState: com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState, 
    onMoreOptionsClick: (Song) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha=0.5f)),
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
            ) {
                 Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artistImageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artistImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = section.artistName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "Generic Artist",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp).fillMaxSize()
                        )
                    }
                }
                
                Spacer(Modifier.width(12.dp))
                Text(
                    text = section.artistName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            //Spacer(Modifier.height(8.dp))
            
            section.albums.forEachIndexed { index, album ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).alpha(0.3f))
                AlbumSectionItemInArtist(album, onSongClick, stablePlayerState, onMoreOptionsClick)
            }
        }
    }
}

@Composable
fun AlbumSectionItem(
    album: AlbumData,
    onSongClick: (Song) -> Unit,
    stablePlayerState: com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState,
    onMoreOptionsClick: (Song) -> Unit
) {
     Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = AbsoluteSmoothCornerShape(24.dp, 60),
        modifier = Modifier.fillMaxWidth()
    ) {
        AlbumSectionItemInArtist(album, onSongClick, stablePlayerState, onMoreOptionsClick)
    }
}

@Composable
fun AlbumSectionItemInArtist(
    album: AlbumData,
    onSongClick: (Song) -> Unit,
    stablePlayerState: com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState,
    onMoreOptionsClick: (Song) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
             SmartImage(
                model = album.artUri,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                 Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleMedium,
                     maxLines = 1,
                     overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                 Text(
                    text = "${album.songs.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
             IconButton(
                onClick = { 
                    if(album.songs.isNotEmpty()) onSongClick(album.songs.first()) // Simple play first of album
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Album")
            }
        }

        Spacer(Modifier.height(10.dp))
        
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .padding(top = 10.dp)
                .padding(horizontal = 8.dp)
        ) {
            album.songs.forEach { song ->
                EnhancedSongListItem(
                     song = song,
                     isPlaying = stablePlayerState.isPlaying,
                     isCurrentSong = stablePlayerState.currentSong?.id == song.id,
                     showAlbumArt = false, // Hide album art as it is redundant
                     onClick = {
                         onSongClick(song)
                     },
                     onMoreOptionsClick = onMoreOptionsClick
                 )
            }
        }
    }
}
