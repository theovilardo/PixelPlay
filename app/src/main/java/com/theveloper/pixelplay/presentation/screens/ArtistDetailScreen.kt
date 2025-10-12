
package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.ArtistDetailViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val lazyListState = rememberLazyListState()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsState()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsState()

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // --- Lógica del Header Colapsable ---
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 300.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                // Si estamos scrolleando hacia arriba y no estamos en el tope de la lista,
                // el scroll es para la lista, no para la TopBar.
                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                // Si estamos en el tope y scrolleamos hacia arriba, la lista no debe moverse.
                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return super.onPostFling(consumed, available)
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue = if (shouldExpand && canExpand) {
                maxTopBarHeightPx
            } else {
                minTopBarHeightPx
            }

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }
    // --- Fin de la lógica del Header ---

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface // Asegura que el fondo coincida con el gradiente
    ) {
        Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
            when {
                uiState.isLoading && uiState.artist == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.artist == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                uiState.artist != null -> {
                    val artist = uiState.artist!!
                    val songs = uiState.songs
                    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(top = currentTopBarHeightDp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        items(songs, key = { song -> "artist_song_${song.id}" }) { song ->
                            EnhancedSongListItem(
                                song = song,
                                isCurrentSong = songs.isNotEmpty() && stablePlayerState.currentSong == song,
                                isPlaying = stablePlayerState.isPlaying,
                                onMoreOptionsClick = {
                                    playerViewModel.selectSongForInfo(song)
                                    showSongInfoBottomSheet = true
                                },
                                onClick = { playerViewModel.showAndPlaySong(song, songs) }
                            )
                        }

                        item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(MiniPlayerHeight + 16.dp)) }
                    }

                    CustomCollapsingTopBar(
                        artist = artist,
                        songsCount = songs.size,
                        collapseFraction = collapseFraction,
                        headerHeight = currentTopBarHeightDp,
                        onBackPressed = { navController.popBackStack() },
                        onPlayClick = {
                            if (songs.isNotEmpty()) {
                                val randomSong = songs.random()
                                playerViewModel.showAndPlaySong(randomSong, songs) }
                        }
                    )
                }
            }
        }
    }
    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            derivedStateOf { currentSong?.let { favoriteIds.contains(it.id) } }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onNavigateToAlbum = {
                    navController.navigate(Screen.AlbumDetail.createRoute(currentSong.albumId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigate(Screen.ArtistDetail.createRoute(currentSong.artistId))
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber ->
                    playerViewModel.editSongMetadata(currentSong, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber)
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                }
            )
        }
    }
}

@Composable
private fun CustomCollapsingTopBar(
    artist: Artist,
    songsCount: Int,
    collapseFraction: Float, // 0.0 = expandido, 1.0 = colapsado
    headerHeight: Dp,
    onBackPressed: () -> Unit,
    onPlayClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor = if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f)

    // --- Animation Values ---
    val fabScale = 1f - collapseFraction
    val backgroundAlpha = collapseFraction
    val headerContentAlpha = 1f - (collapseFraction * 2).coerceAtMost(1f)

    // Title animation
    val titleScale = lerp(1f, 0.75f, collapseFraction)
    val titlePaddingStart = lerp(24.dp, 58.dp, collapseFraction)
    val titleMaxLines = if(collapseFraction < 0.5f) 2 else 1
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val animatedTitleAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val titleContainerHeight = lerp(88.dp, 56.dp, collapseFraction)
    val yOffsetCorrection = lerp( (titleContainerHeight / 2) - 64.dp, 0.dp, collapseFraction)


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(surfaceColor.copy(alpha = backgroundAlpha))
    ) {
        // --- Contenido del Header (visible cuando está expandido) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = headerContentAlpha }
        ) {
            MusicIconPattern(
                modifier = Modifier.fillMaxSize(),
                collapseFraction = collapseFraction
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.4f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent)))
                .align(Alignment.TopCenter)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            FilledIconButton(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp),
                onClick = onBackPressed,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }

            // Box contenedor para el título
            Box(
                modifier = Modifier
                    .align(animatedTitleAlignment)
                    .height(titleContainerHeight)
                    .fillMaxWidth()
                    .offset(y = yOffsetCorrection)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = titlePaddingStart, end = 120.dp)
                        .graphicsLayer {
                            scaleX = titleScale
                            scaleY = titleScale
                        },
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "$songsCount songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Botón de Play
            LargeFloatingActionButton(
                onClick = onPlayClick,
                shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                        alpha = fabScale
                    }
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle play album")
            }
        }
    }
}

@Composable
private fun MusicIconPattern(modifier: Modifier = Modifier, collapseFraction: Float) {
    val color1 = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
    val color2 = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null, tint = color1,
            modifier = Modifier.align(Alignment.TopStart).offset(x = lerp(60.dp, 100.dp, collapseFraction), y = lerp(100.dp, 10.dp, collapseFraction)).size(60.dp).graphicsLayer { rotationZ = lerp(-15f, 30f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null, tint = color1,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = lerp(20.dp,
                (-40).dp, collapseFraction), y = lerp(50.dp, 90.dp, collapseFraction)).size(50.dp).graphicsLayer { rotationZ = lerp(5f, 45f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.Album,
            contentDescription = null, tint = color2,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = lerp((-40).dp, 20.dp, collapseFraction), y = lerp(-50.dp, -90.dp, collapseFraction)).size(70.dp).graphicsLayer { rotationZ = lerp(20f, -10f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null, tint = color1,
            modifier = Modifier.align(Alignment.BottomCenter).offset(x = lerp(20.dp, 10.dp, collapseFraction),y = lerp((-40).dp, 20.dp, collapseFraction)).size(60.dp).graphicsLayer { rotationZ = lerp(-5f, 35f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.SurroundSound,
            contentDescription = null, tint = color2,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = lerp(60.dp, 10.dp, collapseFraction), x = lerp(0.dp, -50.dp, collapseFraction)).size(80.dp).graphicsLayer { rotationZ = lerp(-10f, 20f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null, tint = color1,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = lerp((-30).dp, (-10).dp, collapseFraction), y = lerp(-120.dp, -150.dp, collapseFraction)).size(45.dp).graphicsLayer { rotationZ = lerp(15f, -30f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null, tint = color2,
            modifier = Modifier.align(Alignment.Center).offset(x = lerp(60.dp, 80.dp, collapseFraction), y = lerp(20.dp, 60.dp, collapseFraction)).size(45.dp).graphicsLayer { rotationZ = lerp(-25f, 15f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
    }
}
