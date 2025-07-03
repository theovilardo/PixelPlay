package com.theveloper.pixelplay.presentation.screens

import android.os.Trace
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.snapshotFlow
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
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.ShimmerBox // Added import for ShimmerBox
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
// import com.theveloper.pixelplay.presentation.components.InfiniteGridHandler // Removed
// import com.theveloper.pixelplay.presentation.components.InfiniteListHandler // Removed
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    // La recolección de estados de alto nivel se mantiene mínima.
    val lastTabIndex by playerViewModel.lastLibraryTabIndexFlow.collectAsState()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsState() // Reintroducir favoriteIds aquí
    val scope = rememberCoroutineScope() // Mantener si se usa para acciones de UI

    // Estados locales para dialogs/bottom sheets, etc.
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }
    val tabTitles = listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "LIKED")
    val pagerState = rememberPagerState(initialPage = lastTabIndex) { tabTitles.size }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) } // Mantener para la visibilidad del menú

    // La lógica de carga diferida (lazy loading) se mantiene.
    LaunchedEffect(Unit) {
        Trace.beginSection("LibraryScreen.InitialTabLoad")
        playerViewModel.onLibraryTabSelected(lastTabIndex)
        Trace.endSection()
    }
    LaunchedEffect(pagerState.currentPage) {
        Trace.beginSection("LibraryScreen.PageChangeTabLoad")
        playerViewModel.onLibraryTabSelected(pagerState.currentPage)
        Trace.endSection()
    }

    val fabState by remember { derivedStateOf { pagerState.currentPage } } // UI sin cambios
    val transition = updateTransition(targetState = fabState, label = "Action Button Icon Transition") // UI sin cambios

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
        Box( // Box para permitir superposición del indicador de carga
            modifier = Modifier
                .padding(innerScaffoldPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    // .padding(innerScaffoldPadding) // El padding ya está en el Box contenedor
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
                        val onClick = remember(
                            index,
                            pagerState,
                            scope
                        ) { { scope.launch { pagerState.animateScrollToPage(index) } } }
                        Tab(
                            modifier = Modifier
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = 12.dp
                                ) // Adjusted padding for better touch target
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(50) // Simpler shape for tabs
                                ),
                            selected = isSelected,
                            onClick = { onClick() },
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
                    shape = RoundedCornerShape(
                        topStart = 34.dp,
                        topEnd = 34.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                    // shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 24.dp, smoothnessAsPercentTR = 60, /*...*/) // Your custom shape
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // OPTIMIZACIÓN: La lógica de ordenamiento ahora es más eficiente.
                        val availableSortOptions by playerViewModel.availableSortOptions.collectAsState()

                        // Recolectamos el estado de ordenación de forma más inteligente.
                        // `distinctUntilChanged()` evita recomposiciones si la opción de ordenación no ha cambiado.
                        val currentSelectedSortOption by remember(playerViewModel, pagerState.currentPage) { // pagerState.currentPage es clave aquí
                            playerViewModel.playerUiState.map {
                                when (pagerState.currentPage) {
                                    0 -> it.currentSongSortOption
                                    1 -> it.currentAlbumSortOption
                                    2 -> it.currentArtistSortOption
                                    4 -> it.currentFavoriteSortOption // Este es de PlayerViewModel
                                    else -> SortOption.SongTitleAZ // Fallback para pestañas no cubiertas por PlayerViewModel directamente
                                }
                            }.distinctUntilChanged()
                        }.collectAsState(initial = SortOption.SongTitleAZ) // Un initialValue razonable

                        // Playlist sort option se maneja por separado ya que viene de otro ViewModel
                        val playlistSortOption by remember(playlistViewModel) { // Solo depende de playlistViewModel
                            playlistViewModel.uiState.map { it.currentPlaylistSortOption }.distinctUntilChanged()
                        }.collectAsState(initial = SortOption.PlaylistNameAZ) // Un initialValue razonable

                        val finalSortOption = if (pagerState.currentPage == 3) playlistSortOption else currentSelectedSortOption

                        val onSortOptionChanged: (SortOption) -> Unit = remember(playerViewModel, playlistViewModel, pagerState.currentPage) {
                            { option ->
                                when (pagerState.currentPage) {
                                    0 -> playerViewModel.sortSongs(option)
                                    1 -> playerViewModel.sortAlbums(option)
                                    2 -> playerViewModel.sortArtists(option)
                                    3 -> playlistViewModel.sortPlaylists(option)
                                    4 -> playerViewModel.sortFavoriteSongs(option)
                                }
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
                            pageSpacing = 0.dp, // No pageSpacing as per original
                        ) { page ->
                            // El contenido de cada página ahora es más independiente.
                            when (page) {
                                0 -> {
                                    // OPTIMIZACIÓN: Cada pestaña recolecta solo los datos que necesita.
                                    // Usamos `map` para extraer solo la lista de canciones.
                                    // `distinctUntilChanged` previene recomposiciones si la lista no cambia.
                                    val songs by remember {
                                        playerViewModel.playerUiState
                                            .map { it.allSongs }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = persistentListOf())

                                    val isLoading by remember {
                                        playerViewModel.playerUiState
                                            .map { it.isLoadingInitialSongs }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = songs.isEmpty())

                                    val stableOnMoreOptionsClickForSongs = remember<(Song) -> Unit> {
                                        { songClicked ->
                                            selectedSongForInfo = songClicked
                                            showSongInfoBottomSheet = true
                                        }
                                    }
                                    LibrarySongsTab(
                                        songs = songs,
                                        isLoadingInitial = isLoading,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp,
                                        onMoreOptionsClick = stableOnMoreOptionsClickForSongs
                                    )
                                }

                                1 -> {
                                    val albums by remember {
                                        playerViewModel.playerUiState
                                            .map { it.albums }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = persistentListOf())

                                    val isLoading by remember {
                                        playerViewModel.playerUiState
                                            .map { it.isLoadingLibraryCategories }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = albums.isEmpty())

                                    val stableOnAlbumClick: (Long) -> Unit = remember(navController) { // (1)
                                        // Esta es la lambda que `remember` ejecutará (solo una vez si navController no cambia)
                                        // Su trabajo es DEVOLVER la lambda que realmente quieres usar.
                                        { albumId: Long -> // (2) Esta es la lambda (Long) -> Unit que se recuerda
                                            navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                                        }
                                    }
                                    LibraryAlbumsTab(
                                        albums = albums,
                                        isLoading = isLoading,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp,
                                        onAlbumClick = stableOnAlbumClick
                                    )
                                }

                                2 -> {
                                    val artists by remember {
                                        playerViewModel.playerUiState
                                            .map { it.artists }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = persistentListOf())

                                    val isLoading by remember {
                                        playerViewModel.playerUiState
                                            .map { it.isLoadingLibraryCategories }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = artists.isEmpty())

                                    LibraryArtistsTab(
                                        artists = artists,
                                        isLoading = isLoading,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp
                                    )
                                }
                                3 -> {
                                    // playlistUiState ya es granular y se recolecta una vez fuera del Pager
                                    // si se va a usar para el `currentSelectedSortOption`.
                                    // Si no, se puede recolectar aquí. Para consistencia con el prompt,
                                    // asumimos que `playlistUiState` ya está disponible.
                                    val currentPlaylistUiState by playlistViewModel.uiState.collectAsState()
                                    LibraryPlaylistsTab(
                                        playlistUiState = currentPlaylistUiState,
                                        navController = navController,
                                        bottomBarHeight = bottomBarHeightDp
                                    )
                                }

                                4 -> {
                                    // favoriteSongs ya es un Flow separado, lo cual es perfecto.
                                    val favoriteSongs by playerViewModel.favoriteSongs.collectAsState()
                                    LibraryFavoritesTab(
                                        favoriteSongs = favoriteSongs,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp
                                    ) { song ->
                                        selectedSongForInfo = song
                                        showSongInfoBottomSheet = true
                                    }
                                }
                            }
                        }
                    }
                }

                // El indicador de carga global puede permanecer, ya que es una superposición.
                // Recolectar playerUiState aquí solo para el indicador de carga global.
                val globalLoadingState by playerViewModel.playerUiState.collectAsState()
                if (globalLoadingState.isSyncingLibrary || ((globalLoadingState.isLoadingInitialSongs || globalLoadingState.isLoadingLibraryCategories) && (globalLoadingState.allSongs.isEmpty() && globalLoadingState.albums.isEmpty() && globalLoadingState.artists.isEmpty()))) {
                    Surface( // Fondo semitransparente para el indicador
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Sincronizando biblioteca...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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
    songs: ImmutableList<Song>,
    isLoadingInitial: Boolean,
    // isLoadingMore: Boolean, // Removed
    // canLoadMore: Boolean, // Removed
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Song) -> Unit
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageLoader = context.imageLoader

    // Prefetching logic for LibrarySongsTab
    LaunchedEffect(songs, listState) {
        snapshotFlow { listState.layoutInfo }
            .distinctUntilChanged()
            .collect { layoutInfo ->
                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                if (visibleItemsInfo.isNotEmpty() && songs.isNotEmpty()) {
                    val lastVisibleItemIndex = visibleItemsInfo.last().index
                    val totalItemsCount = songs.size
                    val prefetchThreshold = 10 // Start prefetching when 10 items are left
                    val prefetchCount = 20    // Prefetch next 20 items

                    if (totalItemsCount > lastVisibleItemIndex + 1 && lastVisibleItemIndex + prefetchThreshold >= totalItemsCount - prefetchCount ) {
                         val startIndexToPrefetch = lastVisibleItemIndex + 1
                         val endIndexToPrefetch = (startIndexToPrefetch + prefetchCount).coerceAtMost(totalItemsCount)

                        (startIndexToPrefetch until endIndexToPrefetch).forEach { indexToPrefetch ->
                            val song = songs.getOrNull(indexToPrefetch)
                            song?.albumArtUriString?.let { uri ->
                                val request = ImageRequest.Builder(context)
                                    .data(uri)
                                    .size(Size(168, 168)) // Same size as in EnhancedSongListItem
                                    .build()
                                imageLoader.enqueue(request)
                            }
                        }
                    }
                }
            }
    }

    if (isLoadingInitial && songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator() // O Shimmer para la lista completa
        }
    } else {
        // Determine content based on loading state and data availability
        when {
            isLoadingInitial && songs.isEmpty() -> { // Este caso ya está cubierto arriba, pero es bueno para claridad
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
                        )
                        .fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 10.dp)
                ) {
                    items(15) {
                        EnhancedSongListItem(
                            song = Song.emptySong(), isPlaying = false, isLoading = true,
                            onMoreOptionsClick = {}, onClick = {}
                        )
                    }
                }
            }
            songs.isEmpty() && !isLoadingInitial -> { // canLoadMore removed from condition
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_music_off_24),
                            contentDescription = "No songs found",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No songs found in your library.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Try rescanning your library in settings if you have music on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
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
                        contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 10.dp)
                    ) {
                        item { Spacer(Modifier.height(0.dp)) }
                        items(songs, key = { "song_${it.id}" }) { song ->
                            val isPlayingThisSong =
                                song.id == stablePlayerState.currentSong?.id && stablePlayerState.isPlaying

                            // Estabilizar lambdas
                            val rememberedOnMoreOptionsClick: (Song) -> Unit = remember(onMoreOptionsClick) {
                                // Esta es la lambda que `remember` ejecutará para producir el valor recordado.
                                // El valor recordado es la propia función `onMoreOptionsClick` (o una lambda que la llama).
                                { songFromListItem -> // Esta es la lambda (Song) -> Unit que se recuerda
                                    onMoreOptionsClick(songFromListItem)
                                }
                            }
                            val rememberedOnClick: () -> Unit = remember(song) {
                                { playerViewModel.showAndPlaySong(song) }
                            }

                            EnhancedSongListItem(
                                song = song,
                                isPlaying = isPlayingThisSong,
                                isLoading = false,
                                onMoreOptionsClick = rememberedOnMoreOptionsClick,
                                onClick = rememberedOnClick
                            )
                        }
                        // isLoadingMore indicator removed as all songs are loaded at once.
                        // if (isLoadingMore) {
                        //     item {
                        //         Box(
                        //             Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        //             contentAlignment = Alignment.Center
                        //         ) { CircularProgressIndicator() }
                        //     }
                        // }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                            )
                        )
                )
                // InfiniteListHandler removed as all songs are loaded at once.
                // InfiniteListHandler(listState = listState) {
                //     if (canLoadMore && !isLoadingMore) {
                //         playerViewModel.loadMoreSongs()
                //     }
                // }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EnhancedSongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean = false, // New parameter for shimmer state
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val itemCornerRadius = 26.dp // Fixed for performance testing

    val colors = MaterialTheme.colorScheme
    val containerColor = if (isPlaying && !isLoading) colors.primaryContainer.copy(alpha = 0.34f) else colors.surfaceContainerLow
    val contentColor = if (isPlaying && !isLoading) colors.primary else colors.onSurface

    val surfaceShape = remember { RoundedCornerShape(itemCornerRadius) }

    if (isLoading) {
        // Shimmer Placeholder Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape),
            shape = surfaceShape,
            color = colors.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp) // Approx height of title
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp) // Approx height of artist
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp) // Approx height of duration
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape) // MoreVert button placeholder
                )
            }
        }
    } else {
        // Actual Song Item Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape)
                .clickable { onClick() },
            shape = surfaceShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        targetSize = Size(100, 100), // 56dp * 3 (densidad asumida) approx
                        modifier = Modifier.fillMaxSize()
                    )
                }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    onClick = { onMoreOptionsClick(song) },
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options for ${song.title}",
                        modifier = Modifier.size(24.dp),
                        tint = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryAlbumsTab(
    albums: ImmutableList<Album>,
    isLoading: Boolean, // This now represents the loading state for all albums
    // canLoadMore: Boolean, // Removed
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onAlbumClick: (Long) -> Unit
) {
    val gridState = rememberLazyGridState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageLoader = context.imageLoader

    // Prefetching logic for LibraryAlbumsTab
    LaunchedEffect(albums, gridState) {
        snapshotFlow { gridState.layoutInfo }
            .distinctUntilChanged()
            .collect { layoutInfo ->
                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                if (visibleItemsInfo.isNotEmpty() && albums.isNotEmpty()) {
                    val lastVisibleItemIndex = visibleItemsInfo.last().index
                    val totalItemsCount = albums.size
                    val prefetchThreshold = 5 // Start prefetching when 5 items are left to be displayed from current visible ones
                    val prefetchCount = 10 // Prefetch next 10 items

                    if (totalItemsCount > lastVisibleItemIndex + 1 && lastVisibleItemIndex + prefetchThreshold >= totalItemsCount - prefetchCount) {
                        val startIndexToPrefetch = lastVisibleItemIndex + 1
                        val endIndexToPrefetch = (startIndexToPrefetch + prefetchCount).coerceAtMost(totalItemsCount)

                        (startIndexToPrefetch until endIndexToPrefetch).forEach { indexToPrefetch ->
                            val album = albums.getOrNull(indexToPrefetch)
                            album?.albumArtUriString?.let { uri ->
                                val request = ImageRequest.Builder(context)
                                    .data(uri)
                                    .size(Size(256,256)) // Same size as in AlbumGridItemRedesigned
                                    .build()
                                imageLoader.enqueue(request)
                            }
                        }
                    }
                }
            }
    }

    if (isLoading && albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (albums.isEmpty() && !isLoading) { // canLoadMore removed
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
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(4.dp))
                }
                items(albums, key = { "album_${it.id}" }) { album ->
                    val albumSpecificColorSchemeFlow = playerViewModel.getAlbumColorSchemeFlow(album.albumArtUriString)
                    val rememberedOnClick = remember(album.id) { { onAlbumClick(album.id) } }
                    AlbumGridItemRedesigned(
                        album = album,
                        albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                        onClick = rememberedOnClick,
                        isLoading = isLoading && albums.isEmpty() // Shimmer solo si está cargando Y la lista está vacía
                    )
                }
                // "Load more" indicator removed as all albums are loaded at once
                // if (isLoading && albums.isNotEmpty()) {
                //     item(span = { GridItemSpan(maxLineSpan) }) {
                //         Box(Modifier
                //             .fillMaxWidth()
                //             .padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                //     }
                // }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                        )
                    )
                    .align(Alignment.TopCenter)
            )
            // InfiniteGridHandler removed as all albums are loaded at once
            // InfiniteGridHandler(gridState = gridState) {
            //     if (canLoadMore && !isLoading) {
            //         playerViewModel.loadMoreAlbums()
            //     }
            // }
        }
    }
}

@Composable
fun AlbumGridItemRedesigned(
    album: Album,
    albumColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    val albumColorSchemePair by albumColorSchemePairFlow.collectAsState()
    val systemIsDark = isSystemInDarkTheme()

    // 1. Obtén el colorScheme del tema actual aquí, en el scope Composable.
    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val itemDesignColorScheme = remember(albumColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        // 2. Ahora, currentMaterialColorScheme es una variable estable que puedes usar.
        albumColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme // Usa la variable capturada
    }

    val gradientBaseColor = itemDesignColorScheme.primaryContainer
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 26.dp

    if (isLoading) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(cardCornerRadius),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(cardCornerRadius)
                )
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(3f / 2f)
                        .fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(cardCornerRadius),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = itemDesignColorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.background(
                    color = gradientBaseColor,
                    shape = RoundedCornerShape(cardCornerRadius)
                )
            ) {
                Box(contentAlignment = Alignment.BottomStart) {
                    var isLoadingImage by remember { mutableStateOf(true) }
                    SmartImage(
                        model = album.albumArtUriString ?: R.drawable.rounded_album_24,
                        contentDescription = "Carátula de ${album.title}",
                        contentScale = ContentScale.Crop,
                            // Reducido el tamaño para mejorar el rendimiento del scroll, como se sugiere en el informe.
                            // ContentScale.Crop se encargará de ajustar la imagen al aspect ratio.
                            targetSize = Size(256, 256),
                        modifier = Modifier
                            .aspectRatio(3f / 2f)
                            .fillMaxSize(),
                        onState = { state ->
                            isLoadingImage = state is AsyncImagePainter.State.Loading
                        }
                    )
                    if (isLoadingImage) {
                        ShimmerBox(
                            modifier = Modifier
                                .aspectRatio(3f / 2f)
                                .fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(3f / 2f)
                            .background(
                                remember(gradientBaseColor) { // Recordar el Brush
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            gradientBaseColor
                                        )
                                    )
                                }
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
}

@Composable
fun LibraryArtistsTab(
    artists: ImmutableList<Artist>,
    isLoading: Boolean, // This now represents the loading state for all artists
    // canLoadMore: Boolean, // Removed
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp
) {
    val listState = rememberLazyListState()
    if (isLoading && artists.isEmpty()) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
    else if (artists.isEmpty() && !isLoading) { /* ... No artists ... */ } // canLoadMore removed
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
                items(artists, key = { "artist_${it.id}" }) { artist ->
                    val rememberedOnClick = remember(artist) { { playerViewModel.playArtist(artist) } }
                    ArtistListItem(artist = artist, onClick = rememberedOnClick)
                }
                // "Load more" indicator removed as all artists are loaded at once
                // if (isLoading && artists.isNotEmpty()) {
                //     item { Box(Modifier
                //         .fillMaxWidth()
                //         .padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
                // }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                        )
                    )
                    .align(Alignment.TopCenter)
            )
            // InfiniteListHandler removed as all artists are loaded at once
            // InfiniteListHandler(listState = listState) {
            //     if (canLoadMore && !isLoading) {
            //         playerViewModel.loadMoreArtists()
            //     }
            // }
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
                    val rememberedOnClick = remember(playlist.id) {
                        { navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) }
                    }
                    PlaylistItem(playlist = playlist, onClick = rememberedOnClick)
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
