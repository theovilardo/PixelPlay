package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.presentation.components.InfiniteGridHandler
import com.theveloper.pixelplay.presentation.components.InfiniteListHandler
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarPersistentHeight
import com.theveloper.pixelplay.presentation.components.PlayerSheetCollapsedCornerRadius
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.subcomps.LibraryActionRow
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by playerViewModel.playerUiState.collectAsState()
    // val stablePlayerState by playerViewModel.stablePlayerState.collectAsState() // Uncomment if used
    val playlistUiState by playlistViewModel.uiState.collectAsState()
    // val sheetVisibility by playerViewModel.isSheetVisible.collectAsState() // Uncomment if used
    val favoriteSongs by playerViewModel.favoriteSongs.collectAsState() // Keep if used elsewhere, or remove if only favoriteIds is needed for this screen
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsState()
    val scope = rememberCoroutineScope()

    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }

    val tabTitles = listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "LIKED")
    val pagerState = rememberPagerState { tabTitles.size }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    // States for sort menu visibility and selected options for each tab
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedSortOptionForSongs by remember { mutableStateOf<SortOption>(SortOption.SongTitleAZ) }
    var selectedSortOptionForAlbums by remember { mutableStateOf<SortOption>(SortOption.AlbumTitleAZ) }
    var selectedSortOptionForArtists by remember { mutableStateOf<SortOption>(SortOption.ArtistNameAZ) }
    var selectedSortOptionForPlaylists by remember { mutableStateOf<SortOption>(SortOption.PlaylistNameAZ) }
    var selectedSortOptionForLiked by remember { mutableStateOf<SortOption>(SortOption.LikedSongTitleAZ) }


    LaunchedEffect(Unit) {
        if (uiState.allSongs.isEmpty() && uiState.canLoadMoreSongs) playerViewModel.loadMoreSongs()
        if (uiState.albums.isEmpty() && uiState.canLoadMoreAlbums) playerViewModel.loadMoreAlbums()
        if (uiState.artists.isEmpty() && uiState.canLoadMoreArtists) playerViewModel.loadMoreArtists()
    }

    val fabState by remember { derivedStateOf { pagerState.currentPage } }
    val transition = updateTransition(targetState = fabState, label = "Action Button Icon Transition")

    val bottomBarHeightDp = NavBarPersistentHeight

    val dm = isSystemInDarkTheme()

    val iconRotation by transition.animateFloat(
        label = "Action Button Icon Rotation",
        transitionSpec = {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        }
    ) { page ->
        when (page) {
            3 -> 0f // Playlist icon (PlaylistAdd) usually doesn't rotate
            else -> 360f // Shuffle icon animates
        }
    }

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }

    Scaffold(
        modifier = Modifier.background(brush = gradientBrush),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = "Library",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                },
                actions = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(end = 14.dp),
                        onClick = { /* TODO: User profile action */ },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "User Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = gradientColors[0]
                )
            )
        }
    ) { innerScaffoldPadding ->
        Column(
            modifier = Modifier
                .padding(innerScaffoldPadding)
                .background(brush = Brush.verticalGradient(gradientColors))
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = pagerState.currentPage == index
                    val onClick = remember(index, pagerState, scope) { { scope.launch { pagerState.animateScrollToPage(index) } } }
                    Tab(
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 12.dp) // Adjusted padding for better touch target
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(50) // Simpler shape for tabs
                            ),
                        selected = isSelected,
                        onClick = onClick,
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp), // Added vertical padding
                color = MaterialTheme.colorScheme.surface,
                // Using RoundedCornerShape as AbsoluteSmoothCornerShape is custom
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 34.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusTR = 34.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBL = 0.dp,
                    smoothnessAsPercentBL = 60,
                    cornerRadiusBR = 0.dp,
                    smoothnessAsPercentBR = 60
                )
                // shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 24.dp, smoothnessAsPercentTR = 60, /*...*/) // Your custom shape
            ) {
                Column(Modifier.fillMaxSize()) {
                    // Determine current sort option and handler based on selected page
                    val (currentSelectedSortOption, onSortOptionChanged) = when (pagerState.currentPage) {
                        0 -> selectedSortOptionForSongs to { option: SortOption ->
                            selectedSortOptionForSongs = option
                            playerViewModel.sortSongs(option)
                        }
                        1 -> selectedSortOptionForAlbums to { option: SortOption ->
                            selectedSortOptionForAlbums = option
                            playerViewModel.sortAlbums(option)
                        }
                        2 -> selectedSortOptionForArtists to { option: SortOption ->
                            selectedSortOptionForArtists = option
                            playerViewModel.sortArtists(option)
                        }
                        3 -> selectedSortOptionForPlaylists to { option: SortOption ->
                            selectedSortOptionForPlaylists = option
                            playlistViewModel.sortPlaylists(option)
                        }
                        4 -> selectedSortOptionForLiked to { option: SortOption ->
                            selectedSortOptionForLiked = option
                            playerViewModel.sortFavoriteSongs(option)
                        }
                        else -> selectedSortOptionForSongs to { _: SortOption -> } // Fallback
                    }

                    val availableSortOptions = remember(pagerState.currentPage) {
                        when (pagerState.currentPage) {
                            0 -> listOf(SortOption.SongTitleAZ, SortOption.SongTitleZA, SortOption.SongArtist, SortOption.SongAlbum, SortOption.SongDateAdded, SortOption.SongDuration)
                            1 -> listOf(SortOption.AlbumTitleAZ, SortOption.AlbumTitleZA, SortOption.AlbumArtist, SortOption.AlbumReleaseYear)
                            2 -> listOf(SortOption.ArtistNameAZ, SortOption.ArtistNameZA)
                            3 -> listOf(SortOption.PlaylistNameAZ, SortOption.PlaylistNameZA, SortOption.PlaylistDateCreated)
                            4 -> listOf(SortOption.LikedSongTitleAZ, SortOption.LikedSongTitleZA, SortOption.LikedSongArtist, SortOption.LikedSongAlbum, SortOption.LikedSongDateLiked)
                            else -> emptyList()
                        }
                    }

                    LibraryActionRow(
                        modifier = Modifier.padding(
                            top = 10.dp,
                            start = 10.dp,
                            end = 10.dp
                        ),
                        currentPage = pagerState.currentPage,
                        onMainActionClick = {
                            when (pagerState.currentPage) {
                                3 -> showCreatePlaylistDialog = true
                                else -> {
                                    playerViewModel.toggleShuffle()
                                    playerViewModel.playPause()
                                }
                            }
                        },
                        iconRotation = iconRotation,
                        showSortButton = availableSortOptions.isNotEmpty(),
                        onSortIconClick = { showSortMenu = !showSortMenu },
                        showSortMenu = showSortMenu,
                        onDismissSortMenu = { showSortMenu = false },
                        currentSortOptionsForTab = availableSortOptions,
                        selectedSortOption = currentSelectedSortOption,
                        onSortOptionSelected = { option ->
                            onSortOptionChanged(option)
                            showSortMenu = false // Dismiss menu on selection
                        }
                    )

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp), // Ensure content is below ActionRow
                        pageSpacing = 0.dp,
                    ) { page ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                            // .padding(top = 8.dp) // This padding is now on the HorizontalPager itself
                        ) {
                            when (page) {
                            0 -> LibrarySongsTab(uiState, playerViewModel, bottomBarHeightDp) { songFromItem ->
                                selectedSongForInfo = songFromItem
                                showSongInfoBottomSheet = true
                            }
                                1 -> LibraryAlbumsTab(uiState, playerViewModel, bottomBarHeightDp)
                                2 -> LibraryArtistsTab(uiState, playerViewModel, bottomBarHeightDp) // Assuming no bottom bar needed or handled internally
                                3 -> LibraryPlaylistsTab(playlistUiState, navController, bottomBarHeightDp)
                            4 -> LibraryFavoritesTab(favoriteSongs, playerViewModel, bottomBarHeightDp) { songFromItem ->
                                selectedSongForInfo = songFromItem
                                showSongInfoBottomSheet = true
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialogRedesigned(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                playlistViewModel.createPlaylist(name) // Pass the actual name
                showCreatePlaylistDialog = false
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo!! // Safe due to the check
        // val isFavorite by playerViewModel.favoriteSongs.collectAsState().value.any { it.id == currentSong.id } // This might be tricky for recomposition.
        // A better way to get isFavorite and handle toggle:
        // Collect favoriteSongIds directly from the viewModel or pass them down.
        // For simplicity in this step, we can use favoriteSongs flow and derive the state.
        // PlayerViewModel will need to expose favoriteSongIds or a way to check if a songId is a favorite.

        val isFavorite = remember(currentSong.id, favoriteIds) { derivedStateOf { favoriteIds.contains(currentSong.id) } }.value

        SongInfoBottomSheet(
            song = currentSong,
            isFavorite = isFavorite,
            onToggleFavorite = {
                // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                playerViewModel.toggleFavoriteSpecificSong(currentSong) // Assumes such a method exists or will be added to PlayerViewModel
            },
            onDismiss = { showSongInfoBottomSheet = false },
            onPlaySong = {
                playerViewModel.showAndPlaySong(currentSong)
                showSongInfoBottomSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                showSongInfoBottomSheet = false
                // Optionally, show a Snackbar/Toast message
            },
            onNavigateToAlbum = {
                // navController.navigate(Screen.AlbumDetail.createRoute(currentSong.albumId)) // Example
                showSongInfoBottomSheet = false
                // Actual navigation logic to be implemented if routes exist
            },
            onNavigateToArtist = {
                // navController.navigate(Screen.ArtistDetail.createRoute(currentSong.artistId)) // Example
                showSongInfoBottomSheet = false
                // Actual navigation logic to be implemented if routes exist
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialogRedesigned(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        //shape = RoundedCornerShape(28.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Nueva Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Nombre de la playlist") },
                    placeholder = { Text("Mi playlist") },
                    shape = RoundedCornerShape(16.dp),
//                    colors = TextFieldDefaults.t(
//                        focusedBorderColor = MaterialTheme.colorScheme.primary,
//                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
//                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = { onCreate(playlistName) },
                        enabled = playlistName.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("Crear")
                    }
                }
            }
        }
    }
}

// NUEVA Pestaña para Favoritos
@Composable
fun LibraryFavoritesTab(
    favoriteSongs: List<Song>, // This is already StateFlow<ImmutableList<Song>> from ViewModel
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val listState = rememberLazyListState()

    // No need to collect favoriteSongs again if it's passed directly as a list
    // However, if you need to react to its changes, ensure it's collected or passed as StateFlow's value

    if (favoriteSongs.isEmpty()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No tienes canciones favoritas.", style = MaterialTheme.typography.titleMedium)
                Text("Toca el corazón en el reproductor para añadir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 26.dp,
                        topEnd = 26.dp,
                        bottomStart = PlayerSheetCollapsedCornerRadius,
                        bottomEnd = PlayerSheetCollapsedCornerRadius
                    )
                ),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 10.dp)
        ) {
            items(favoriteSongs, key = { "fav_${it.id}" }) { song ->
                val isPlayingThisSong = song.id == stablePlayerState.currentSong?.id && stablePlayerState.isPlaying
                // Using EnhancedSongListItem for consistency, though it has more details than SongListItemFavs
                EnhancedSongListItem(
                    song = song,
                    isPlaying = isPlayingThisSong,
                    onMoreOptionsClick = { onMoreOptionsClick(song) },
                    onClick = { playerViewModel.showAndPlaySong(song) }
                )
            }
        }
    }
}

@Composable
fun LibrarySongsTab(
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val listState = rememberLazyListState()
    if (uiState.isLoadingInitialSongs && uiState.allSongs.isEmpty()) { /* ... Loading ... */ }
    else if (uiState.allSongs.isEmpty() && !uiState.canLoadMoreSongs) { /* ... No songs ... */ }
    else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    ),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 10.dp) // Espacio para el FAB
            ) {
                item {
                    Spacer(Modifier.height(0.dp))
                }
                items(uiState.allSongs, key = { it.id }) { song ->
                    val isPlayingThisSong = song.id == stablePlayerState.currentSong?.id
                    EnhancedSongListItem(
                        song = song,
                        isPlaying = isPlayingThisSong,
                        onMoreOptionsClick = { onMoreOptionsClick(song) }
                    ) {
                        playerViewModel.showAndPlaySong(song)
                    }
                }
                if (uiState.isLoadingMoreSongs) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            // Gradiente superior para el efecto de desvanecimiento
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )

            InfiniteListHandler(listState = listState) {
                if (uiState.canLoadMoreSongs && !uiState.isLoadingMoreSongs) {
                    playerViewModel.loadMoreSongs()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EnhancedSongListItem(
    song: Song,
    isPlaying: Boolean,
    onMoreOptionsClick: (Song) -> Unit, // Added parameter
    onClick: () -> Unit
) {
    val itemCornerRadius = 60.dp
    val coverCornerRadius = 60.dp

    val colors = MaterialTheme.colorScheme

    val containerColor = if (isPlaying) colors.primaryContainer.copy(alpha = 0.34f) else colors.surfaceContainerLow
    val contentColor = if (isPlaying) colors.primary else colors.onSurface

    val surfaceShape = AbsoluteSmoothCornerShape(
        cornerRadiusBL = itemCornerRadius,
        smoothnessAsPercentTL = 60,
        cornerRadiusTR = itemCornerRadius,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = itemCornerRadius,
        smoothnessAsPercentBR = 60,
        cornerRadiusTL = itemCornerRadius,
        smoothnessAsPercentBL = 60
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                surfaceShape
            )
            .clickable {
                onClick()
            },
            //.padding(end = 6.dp, start = 2.dp),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusBL = itemCornerRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusTR = itemCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusBR = itemCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusTL = itemCornerRadius,
            smoothnessAsPercentBL = 60
        ),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art con sombra y esquinas más suaves
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                SmartImage(
                    model = song.albumArtUriString ?: R.drawable.rounded_album_24,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Song info con mejor espaciado
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = contentColor,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = contentColor.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (song.duration > 0) {
                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = contentColor.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = formatDuration(song.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            IconButton(
                onClick = { onMoreOptionsClick(song) }, // Modified onClick
                modifier = Modifier
                    .size(26.dp)
                    .padding(end = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun LibraryAlbumsTab(uiState: PlayerUiState, playerViewModel: PlayerViewModel, bottomBarHeight: Dp) {
    val gridState = rememberLazyGridState()
    if (uiState.isLoadingLibraryCategories && uiState.albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (uiState.albums.isEmpty() && !uiState.canLoadMoreAlbums) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Album, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("No se encontraron álbumes.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                modifier = Modifier
                    .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    ),
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 14.dp), // Espacio para el FAB
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(4.dp))
                }
                items(uiState.albums, key = { "album_${it.id}" }) { album ->
                    val albumSpecificColorSchemeFlow = playerViewModel.getAlbumColorSchemeFlow(album.albumArtUriString)
                    AlbumGridItemRedesigned( // Usar el nuevo Composable
                        album = album,
                        albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                        onClick = { playerViewModel.playAlbum(album) }
                    )
                }
                if (uiState.isLoadingLibraryCategories && uiState.albums.isNotEmpty()) {
                    item { Box(Modifier
                        .fillMaxWidth()
                        .padding(8.dp), Alignment.Center) { CircularProgressIndicator() } }
                }
            }
            // Gradiente superior para el efecto de desvanecimiento
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )
            InfiniteGridHandler(gridState = gridState) {
                if (uiState.canLoadMoreAlbums && !uiState.isLoadingLibraryCategories) {
                    playerViewModel.loadMoreAlbums()
                }
            }
        }
    }
}

@Composable
fun AlbumGridItemRedesigned(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>, // Recibe el Flow
    onClick: () -> Unit
) {
    val albumColorSchemePair by albumColorSchemePairFlow.collectAsState()
    val systemIsDark = isSystemInDarkTheme()

    // Determinar el ColorScheme específico para este ítem.
    // Si no está listo, usa un fallback temporal basado en el tema actual de la app.
    val itemDesignColorScheme = albumColorSchemePair?.let { if (systemIsDark) it.dark else it.light }
        ?: MaterialTheme.colorScheme // Fallback al tema actual de la app mientras carga el específico

    val gradientBaseColor = itemDesignColorScheme.primaryContainer // Un color del tema específico del álbum
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 26.dp

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = cardCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = cardCornerRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusBR = cardCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = cardCornerRadius,
            smoothnessAsPercentBR = 60
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = itemDesignColorScheme.surfaceVariant.copy(alpha = 0.3f)) // Un fondo sutil
    ) {
        Column(
            modifier = Modifier.background(
                color = gradientBaseColor,
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = cardCornerRadius,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusTR = cardCornerRadius,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBR = cardCornerRadius,
                    smoothnessAsPercentBL = 60,
                    cornerRadiusBL = cardCornerRadius,
                    smoothnessAsPercentBR = 60
                )
            )
        ) {
            Box(contentAlignment = Alignment.BottomStart) {
                SmartImage(
                    model = album.albumArtUriString ?: R.drawable.rounded_album_24,
                    contentDescription = "Carátula de ${album.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(3f / 2f)
                        .fillMaxSize()
                )
                // Gradiente que permite ver ~70% de la carátula
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(3f / 2f)
                        //.height(90.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    gradientBaseColor
                                )
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onGradientColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(album.artist, style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${album.songCount} canciones", style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun LibraryArtistsTab(uiState: PlayerUiState, playerViewModel: PlayerViewModel, bottomBarHeight: Dp) {
    val listState = rememberLazyListState() // Artistas en una lista por ahora
    if (uiState.isLoadingLibraryCategories && uiState.artists.isEmpty()) { /* ... Loading ... */ }
    else if (uiState.artists.isEmpty() && !uiState.canLoadMoreArtists) { /* ... No artists ... */ }
    else {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    ),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 10.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                }
                items(uiState.artists, key = { it.id }) { artist -> ArtistListItem(artist = artist) { playerViewModel.playArtist(artist) } }
                if (uiState.isLoadingLibraryCategories && uiState.artists.isNotEmpty()) {
                    item { Box(Modifier
                        .fillMaxWidth()
                        .padding(8.dp), Alignment.Center) { CircularProgressIndicator() } }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )
            InfiniteListHandler(listState = listState) {
                if (uiState.canLoadMoreArtists && !uiState.isLoadingLibraryCategories) {
                    playerViewModel.loadMoreArtists()
                }
            }
        }
    }
}

@Composable
fun ArtistListItem(artist: Artist, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(R.drawable.rounded_artist_24), contentDescription = "Artista", modifier = Modifier
                .size(40.dp)
                .padding(end = 12.dp))
            Column {
                Text(artist.name, style = MaterialTheme.typography.titleMedium)
                Text("${artist.songCount} canciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LibraryPlaylistsTab(
    playlistUiState: PlaylistUiState, // Usar el estado de PlaylistViewModel
    navController: NavController,
    bottomBarHeight: Dp
) {
    val listState = rememberLazyListState()
    if (playlistUiState.isLoading && playlistUiState.playlists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (playlistUiState.playlists.isEmpty()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No has creado ninguna playlist.", style = MaterialTheme.typography.titleMedium)
                Text("Toca el botón '+' para empezar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    ),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 10.dp)
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                }
                items(playlistUiState.playlists, key = { it.id }) { playlist ->
                    PlaylistItem(playlist = playlist) {
                        navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
                    .align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun PlaylistItem(playlist: Playlist, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Playlist",
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 16.dp),
                tint = MaterialTheme.colorScheme.secondary
            )
            Column {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                Text("${playlist.songIds.size} canciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
