package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
// Importando EnhancedSongListItem de LibraryScreen
import com.theveloper.pixelplay.presentation.screens.EnhancedSongListItem
// import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel // Se añadirá
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.AlbumDetailViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()

    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor = if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f)

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    Scaffold(
        content = { innerPadding ->
            when {
                uiState.isLoading && uiState.album == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null && uiState.album == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                uiState.album != null -> {
                    val album = uiState.album!!
                    val songs = uiState.songs
                    val lazyListState = rememberLazyListState()
                    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                    val headerHeightPx = with(LocalDensity.current) { 300.dp.toPx() }
                    val collapsedState = remember {
                        derivedStateOf {
                            val firstItem = lazyListState.firstVisibleItemIndex == 0
                            val scrollOffset = lazyListState.firstVisibleItemScrollOffset
                            if (firstItem && scrollOffset < headerHeightPx) {
                                scrollOffset / headerHeightPx
                            } else {
                                1f
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Header con imagen y gradientes
                        CollapsingAlbumHeader(
                            album = album,
                            scrollFraction = collapsedState.value,
                            statusBarColor = statusBarColor,
                            surfaceColor = surfaceColor,
                            onPlayClick = { playerViewModel.playAlbum(album) }
                        )

                        // Lista de canciones
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(
                                bottom = MiniPlayerHeight + 16.dp + navBarInset,
                                end = 16.dp,
                                start = 16.dp
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Spacer para empujar el contenido debajo del header
                            item { Spacer(modifier = Modifier.height(300.dp)) }

                            items(songs, key = { song -> "album_song_${song.id}" }) { song ->
                                EnhancedSongListItem(
                                    song = song,
                                    isCurrentSong = songs.isNotEmpty() && stablePlayerState.currentSong == song,
                                    isPlaying = stablePlayerState.currentSong?.id == song.id,
                                    onMoreOptionsClick = { /* TODO */ },
                                    onClick = { playerViewModel.showAndPlaySong(song, songs) }
                                )
                            }
                        }

                        // Top App Bar transparente que se desvanece
                        CollapsingTopAppBar(
                            albumTitle = album.title,
                            scrollFraction = collapsedState.value,
                            onBackPressed = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollapsingAlbumHeader(
    album: Album,
    scrollFraction: Float,
    statusBarColor: Color,
    surfaceColor: Color,
    onPlayClick: () -> Unit
) {
    val imageAlpha = 1f - scrollFraction
    val textAlpha = (1f - scrollFraction * 2).coerceAtLeast(0f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .graphicsLayer {
                // Efecto Parallax
                translationY = -scrollFraction * 150.dp.toPx()
                alpha = 1f - scrollFraction
            }
    ) {
        // Imagen de fondo
        SmartImage(
            model = album.albumArtUriString ?: R.drawable.rounded_album_24,
            contentDescription = "Portada de ${album.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradiente superior para la status bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp) // Altura suficiente para cubrir la status bar y un poco más
                .background(
                    Brush.verticalGradient(
                        colors = listOf(statusBarColor, Color.Transparent)
                    )
                )
                .align(Alignment.TopCenter)
        )

        // Gradiente inferior para la transición a la lista
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, surfaceColor),
                        startY = 0f,
                        endY = 400f
                    )
                )
                .align(Alignment.BottomCenter)
        )

        // Información del Álbum
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .padding(start = 8.dp, end = 110.dp)
                .graphicsLayer { alpha = textAlpha }
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 30.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Botón de Play
        LargeFloatingActionButton(
            onClick = { onPlayClick() },
            shape = RoundedStarShape(
                sides = 8,
                curve = 0.05,
                rotation = 0f
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .graphicsLayer { alpha = imageAlpha } // Se desvanece con la imagen
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproducir álbum")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsingTopAppBar(
    albumTitle: String,
    scrollFraction: Float,
    onBackPressed: () -> Unit
) {
    val titleAlpha = (scrollFraction - 0.5f) * 2
    TopAppBar(
        title = {
            Text(
                text = albumTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .alpha(titleAlpha.coerceIn(0f, 1f))
                    //.padding(start = 12.dp)
            )
        },
        actions = {
            FilledIconButton(
                modifier = Modifier.padding(end = 8.dp),
                onClick = { /* TODO */ },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Icon(Icons.Rounded.Search, contentDescription = "Reproducir álbum")
            }
        },
        navigationIcon = {
            FilledIconButton(
                modifier = Modifier.padding(start = 8.dp),
                onClick = onBackPressed,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = (scrollFraction * 1.5f).coerceIn(0f, 1f)
            ),
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
