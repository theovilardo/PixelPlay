package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.InfiniteGridHandler
import com.theveloper.pixelplay.presentation.components.InfiniteListHandler
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarPersistentHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.internal.toImmutableList
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalAnimationApi::class
)
@Composable
fun LibraryScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by playerViewModel.playerUiState.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val playlistUiState by playlistViewModel.uiState.collectAsState()
    val sheetVisibility by playerViewModel.isSheetVisible.collectAsState()
    val favoriteSongs by playerViewModel.favoriteSongs.collectAsState() // Observar canciones favoritas
    val scope = rememberCoroutineScope()

    val tabTitles = listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "LIKED")
    val pagerState = rememberPagerState { tabTitles.size }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (uiState.allSongs.isEmpty() && uiState.canLoadMoreSongs) playerViewModel.loadMoreSongs()
        if (uiState.albums.isEmpty() && uiState.canLoadMoreAlbums) playerViewModel.loadMoreAlbums()
        if (uiState.artists.isEmpty() && uiState.canLoadMoreArtists) playerViewModel.loadMoreArtists()
    }

    // Animación para cambio de FAB
    val fabState by remember { derivedStateOf { pagerState.currentPage } }
    val transition = updateTransition(targetState = fabState, label = "FAB Transition")

    val bottomBarHeightDp = NavBarPersistentHeight

    val dm = isSystemInDarkTheme()

    val fabIconRotation by transition.animateFloat(
        label = "FAB Icon Rotation",
        transitionSpec = {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        }
    ) { page ->
        when (page) {
            3 -> 0f
            else -> 360f
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
        modifier = Modifier
            .background(
                brush = gradientBrush
            ),
        topBar = {
            // TopBar al estilo de la imagen de referencia
            TopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = "Library",
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                },
                actions = {
                    // Avatar del usuario
                    FilledTonalIconButton(
                        modifier = Modifier.padding(end = 14.dp),
                        onClick = {},
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        )
                    ) { // Aquí iría el avatar del usuario
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = gradientColors[0]//MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            )
        }
    ) { innerScaffoldPadding ->
        Column(
            modifier = Modifier
                .padding(innerScaffoldPadding)
                .background(
                    brush = Brush.verticalGradient(
                        gradientColors
                    )
                )
                .fillMaxSize()
        ) {
            // TabRow mejorada con animaciones
            // TabRow mejorada
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent, // Hacer transparente
                edgePadding = 12.dp, // Padding en los bordes
                indicator = { tabPositions ->
                    if (pagerState.currentPage < tabPositions.size) {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                            height = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {} // Sin divisor por defecto
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        modifier = Modifier
                            .padding(8.dp)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(50.dp)
                            ),
                        selected = isSelected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge, // Estilo más expresivo
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )
                }
            }

            // Contenedor principal con fondo y esquinas redondeadas
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 24.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusTR = 24.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBR = 24.dp,
                    smoothnessAsPercentBL = 60,
                    cornerRadiusBL = 24.dp,
                    smoothnessAsPercentBR = 60
                )
            ) {
                Column(
                    Modifier.fillMaxSize()
                ) {
                    SmallFloatingActionButton(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp, bottom = 2.dp)
                        ,
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTL = 26.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusTR = 26.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBL = 26.dp,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBR = 26.dp,
                            smoothnessAsPercentBL = 60
                        ),
                        onClick = {
                            when (pagerState.currentPage) {
                                3 -> showCreatePlaylistDialog = true
                                else -> playerViewModel.toggleShuffle().also {
                                    playerViewModel.playPause()
                                }
                            }
                        },
                        //shape = fabShape,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 6.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            // Contenido del FAB según la tab
                            when (pagerState.currentPage) {
                                3 -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.rounded_playlist_play_24),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .rotate(fabIconRotation)
                                        )
                                        Text(
                                            "New Playlist",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                                else -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.rounded_shuffle_24),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .rotate(fabIconRotation)
                                        )
                                        Text(
                                            "Shuffle",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 0.dp,
                        pageContent = { page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp)
                            ) {
                                when (page) {
                                    0 -> LibrarySongsTab(uiState, playerViewModel, bottomBarHeightDp)
                                    1 -> LibraryAlbumsTab(uiState, playerViewModel, bottomBarHeightDp)
                                    2 -> LibraryArtistsTab(uiState, playerViewModel)
                                    3 -> LibraryPlaylistsTab(playlistUiState, navController)
                                    4 -> LibraryFavoritesTab(
                                        favoriteSongs,
                                        playerViewModel
                                    ) // Nueva Tab
                                }
                            }
                        }
                    )
                }
                // El contenido del pager con animación suave entre tabs
            }
        }
    }

    // Diálogo de creación de playlist con diseño Material 3
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialogRedesigned(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                playlistViewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
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
    favoriteSongs: List<Song>,
    playerViewModel: PlayerViewModel
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()

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
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(favoriteSongs, key = { "fav_${it.id}" }) { song ->
                val isPlayingThisSong = song.id == stablePlayerState.currentSong?.id && stablePlayerState.isPlaying
                SongListItemFavs(
                    title = song.title,
                    artist = song.artist,
                    albumArtUrl = song.albumArtUri,
                    isPlaying = isPlayingThisSong,
                    onClick = { playerViewModel.showAndPlaySong(song) }
                )
            }
        }
    }
}

@Composable
fun LibrarySongsTab(uiState: PlayerUiState, playerViewModel: PlayerViewModel, bottomBarHeight: Dp) {
    val listState = rememberLazyListState()
    if (uiState.isLoadingInitialSongs && uiState.allSongs.isEmpty()) { /* ... Loading ... */ }
    else if (uiState.allSongs.isEmpty() && !uiState.canLoadMoreSongs) { /* ... No songs ... */ }
    else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.padding(bottom = bottomBarHeight - 6.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = MiniPlayerHeight + 16.dp) // Espacio para el FAB
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                }
                items(uiState.allSongs, key = { it.id }) { song ->
                    EnhancedSongListItem(song = song) {
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

            InfiniteListHandler(listState = listState) {
                if (uiState.canLoadMoreSongs && !uiState.isLoadingMoreSongs) {
                    playerViewModel.loadMoreSongs()
                }
            }
        }
    }
}

@Composable
fun EnhancedSongListItem(
    song: Song,
    onClick: () -> Unit
) {
    val itemCornerRadius = 60.dp
    val coverCornerRadius = 60.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(end = 6.dp, start = 2.dp),
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
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art con sombra y esquinas más suaves
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusBL = coverCornerRadius,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTR = coverCornerRadius,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBR = coverCornerRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusTL = coverCornerRadius,
                            smoothnessAsPercentBL = 60
                        )
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                SmartImage(
                    model = song.albumArtUri ?: R.drawable.rounded_album_24,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Song info con mejor espaciado
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
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
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = formatDuration(song.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Play button con animación
//            IconButton(
//                onClick = onClick,
//                colors = IconButtonDefaults.iconButtonColors(
//                    containerColor = MaterialTheme.colorScheme.secondary,
//                    contentColor = MaterialTheme.colorScheme.onSecondary
//                ),
//                modifier = Modifier
//                    .size(30.dp)
//                    .aspectRatio(2f / 3f)
//            ) {
//                Icon(
//                    painter = painterResource(id = R.drawable.rounded_play_arrow_24),
//                    contentDescription = "Reproducir",
//                    tint = MaterialTheme.colorScheme.onSecondary
//                )
//            }
        }
    }
}

@Composable
fun LibraryAlbumsTab(uiState: PlayerUiState, playerViewModel: PlayerViewModel, bottomBarHeight: Dp) {
    val gridState = rememberLazyGridState()
    if (uiState.isLoadingLibraryCategories && uiState.albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (uiState.albums.isEmpty() && !uiState.canLoadMoreAlbums) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Album, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("No se encontraron álbumes.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                modifier = Modifier.padding(bottom = bottomBarHeight - 6.dp),
                state = gridState,
                columns = GridCells.Fixed(2), // O GridCells.Adaptive(minSize = 160.dp)
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = MiniPlayerHeight + bottomBarHeight + 28.dp), // Espacio para el FAB
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                }
                items(uiState.albums, key = { "album_${it.id}" }) { album ->
                    val albumSpecificColorSchemeFlow = playerViewModel.getAlbumColorSchemeFlow(album.albumArtUri)
                    AlbumGridItemRedesigned( // Usar el nuevo Composable
                        album = album,
                        albumColorSchemePairFlow = albumSpecificColorSchemeFlow,
                        onClick = { playerViewModel.playAlbum(album) }
                    )
                }
                if (uiState.isLoadingLibraryCategories && uiState.albums.isNotEmpty()) {
                    item { Box(Modifier.fillMaxWidth().padding(8.dp), Alignment.Center) { CircularProgressIndicator() } }
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
                    model = album.albumArtUri ?: R.drawable.rounded_album_24,
                    contentDescription = "Carátula de ${album.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(3f/2f).fillMaxSize()
                )
                // Gradiente que permite ver ~70% de la carátula
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(3f/2f)
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
                modifier = Modifier.fillMaxWidth().padding(12.dp)
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

//@Composable
//fun LibraryAlbumsTab(uiState: PlayerUiState, playerViewModel: PlayerViewModel) {
//    val gridState = rememberLazyGridState()
//    if (uiState.isLoadingLibraryCategories && uiState.albums.isEmpty()) { /* ... Loading ... */ }
//    else if (uiState.albums.isEmpty() && !uiState.canLoadMoreAlbums) { /* ... No albums ... */ }
//    else {
//        LazyVerticalGrid(
//            state = gridState,
//            columns = GridCells.Fixed(2),
//            contentPadding = PaddingValues(16.dp),
//            verticalArrangement = Arrangement.spacedBy(16.dp),
//            horizontalArrangement = Arrangement.spacedBy(16.dp)
//        ) {
//            items(uiState.albums, key = { it.id }) { album -> AlbumGridItem(album = album) { playerViewModel.playAlbum(album) } }
//            if (uiState.isLoadingLibraryCategories && uiState.albums.isNotEmpty()) { // Mostrar loader al final si está cargando más
//                item { Box(Modifier
//                    .fillMaxWidth()
//                    .padding(8.dp), Alignment.Center) { CircularProgressIndicator() } }
//            }
//        }
//        InfiniteGridHandler(gridState = gridState) {
//            if (uiState.canLoadMoreAlbums && !uiState.isLoadingLibraryCategories) {
//                playerViewModel.loadMoreAlbums()
//            }
//        }
//    }
//}

@Composable
fun LibraryArtistsTab(uiState: PlayerUiState, playerViewModel: PlayerViewModel) {
    val listState = rememberLazyListState() // Artistas en una lista por ahora
    if (uiState.isLoadingLibraryCategories && uiState.artists.isEmpty()) { /* ... Loading ... */ }
    else if (uiState.artists.isEmpty() && !uiState.canLoadMoreArtists) { /* ... No artists ... */ }
    else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.artists, key = { it.id }) { artist -> ArtistListItem(artist = artist) { playerViewModel.playArtist(artist) } }
            if (uiState.isLoadingLibraryCategories && uiState.artists.isNotEmpty()) {
                item { Box(Modifier
                    .fillMaxWidth()
                    .padding(8.dp), Alignment.Center) { CircularProgressIndicator() } }
            }
        }
        InfiniteListHandler(listState = listState) {
            if (uiState.canLoadMoreArtists && !uiState.isLoadingLibraryCategories) {
                playerViewModel.loadMoreArtists()
            }
        }
    }
}

@Composable
fun AlbumGridItem(album: Album, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            SmartImage(
                model = album.albumArtUri ?: R.drawable.rounded_album_24,
                contentDescription = "Carátula de ${album.title}",
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(12.dp)) {
                Text(album.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(album.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${album.songCount} canciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
    navController: NavController
) {
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
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlistUiState.playlists, key = { it.id }) { playlist ->
                PlaylistItem(playlist = playlist) {
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id))
                }
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear Nueva Playlist") },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Nombre de la playlist") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playlistName.text.isNotBlank()) {
                        onCreate(playlistName.text)
                    }
                },
                enabled = playlistName.text.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}