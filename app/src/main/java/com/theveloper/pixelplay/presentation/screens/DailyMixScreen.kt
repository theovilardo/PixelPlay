package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import android.os.Trace // Import Trace
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AiPlaylistSheet
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage
import com.theveloper.pixelplay.presentation.components.DailyMixHeader
import com.theveloper.pixelplay.presentation.components.DailyMixMenu
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.threeShapeSwitch
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.MainViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.utils.formatDuration
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun DailyMixScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    Trace.beginSection("DailyMixScreen.Composition")
    val dailyMixSongs: ImmutableList<Song> by playerViewModel.dailyMixSongs.collectAsState()
    val currentSongId by remember { playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged() }.collectAsState(initial = null)
    val isPlaying by remember { playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged() }.collectAsState(initial = false)
    val isShuffleEnabled by remember { playerViewModel.stablePlayerState.map { it.isShuffleEnabled }.distinctUntilChanged() }.collectAsState(initial = false)

    val playerSheetState by playerViewModel.sheetState.collectAsState() // This is a simple enum, less critical but fine
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()

    val showAiSheet by playerViewModel.showAiPlaylistSheet.collectAsState()
    val isGeneratingAiPlaylist by playerViewModel.isGeneratingAiPlaylist.collectAsState()
    val aiError by playerViewModel.aiError.collectAsState()
    val lazyListState = rememberLazyListState()

    var showSongInfoSheet by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }
    var showDailyMixMenu by remember { mutableStateOf(false) }

    if (showDailyMixMenu) {
        DailyMixMenu(onDismiss = { showDailyMixMenu = false })
    }

    if (showAiSheet) {
        AiPlaylistSheet(
            onDismiss = { playerViewModel.dismissAiPlaylistSheet() },
            onGenerateClick = { prompt, minLength, maxLength ->
                playerViewModel.generateAiPlaylist(prompt, minLength, maxLength, saveAsPlaylist = false)
            },
            isGenerating = isGeneratingAiPlaylist,
            error = aiError
        )
    }

    val surfaceContainer = MaterialTheme.colorScheme.surface
    val headerColor = MaterialTheme.colorScheme.primary
    val backgroundBrush = remember(surfaceContainer, headerColor) {
        Brush.verticalGradient(
            colors = listOf(
                headerColor.copy(alpha = 0.25f),
                surfaceContainer.copy(alpha = 0.5f),
                surfaceContainer
            ),
            endY = 1200f
        )
    }

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    if (showSongInfoSheet && selectedSongForInfo != null) {
        val song = selectedSongForInfo!!
        SongInfoBottomSheet(
            song = song,
            isFavorite = favoriteSongIds.contains(song.id),
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(song) },
            onDismiss = { showSongInfoSheet = false },
            onPlaySong = {
                playerViewModel.showAndPlaySong(song, dailyMixSongs, "Daily Mix", isVoluntaryPlay = false)
                showSongInfoSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(song)
                showSongInfoSheet = false
            },
            onNavigateToAlbum = {
                // Assuming Screen object has a method to create a route
                navController.navigate(Screen.AlbumDetail.createRoute(song.albumId))
                showSongInfoSheet = false
            },
            onNavigateToArtist = {
                // TODO: Implement navigation to artist screen. Might require finding artist by name.
                showSongInfoSheet = false
            },
            onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate ->
                playerViewModel.editSongMetadata(song, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate)
            },
            generateAiMetadata = { fields ->
                playerViewModel.generateAiMetadata(song, fields)
            }
        )
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (dailyMixSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = MiniPlayerHeight + 38.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "daily_mix_header") {
                    ExpressiveDailyMixHeader(
                        songs = dailyMixSongs,
                        scrollState = lazyListState,
                        onShowMenu = { playerViewModel.showAiPlaylistSheet() }
                    )
                }

                item(key = "play_shuffle_buttons") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (dailyMixSongs.isNotEmpty()) {
                                    playerViewModel.playSongs(dailyMixSongs, dailyMixSongs.first(), "Daily Mix")
                                    if (isShuffleEnabled) playerViewModel.toggleShuffle() // Desactivar shuffle si estaba activo
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            enabled = dailyMixSongs.isNotEmpty(),
                            shape = RoundedCornerShape(
                                topStart = 60.dp,
                                topEnd = 14.dp,
                                bottomStart = 60.dp,
                                bottomEnd = 14.dp
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(
                                ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Play it")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (dailyMixSongs.isNotEmpty()) {
                                    if (!isShuffleEnabled) playerViewModel.toggleShuffle() // Activar shuffle si no estaba activo
                                    playerViewModel.playSongs(dailyMixSongs, dailyMixSongs.random(), "Daily Mix")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            enabled = dailyMixSongs.isNotEmpty(),
                            shape = RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 60.dp,
                                bottomStart = 14.dp,
                                bottomEnd = 60.dp
                            )
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(
                                ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Shuffle")
                        }
                    }
                }

                items(dailyMixSongs, key = { it.id }) { song ->
                    EnhancedSongListItem(
                        modifier = Modifier
                            .padding(horizontal = 16.dp),
                        song = song,
                        isCurrentSong = dailyMixSongs.isNotEmpty() && stablePlayerState.currentSong == song,
                        isPlaying = currentSongId == song.id && isPlaying,
                        onClick = { playerViewModel.showAndPlaySong(song, dailyMixSongs, "Daily Mix", isVoluntaryPlay = false) },
                        onMoreOptionsClick = {
                            selectedSongForInfo = it
                            showSongInfoSheet = true
                        }
                    )
                }
            }
        }

        FilledIconButton(
            onClick = { navController.popBackStack() },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 10.dp, top = 8.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Volver"
            )
        }

        // Bottom Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(80.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(0.5f),
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {

        }

        //Top Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(50.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(0.5f),
                            Color.Transparent,
                        )
                    )
                )
        ) {

        }
    }
    Trace.endSection()
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveDailyMixHeader(
    songs: List<Song>,
    scrollState: LazyListState,
    onShowMenu: () -> Unit
) {
    Trace.beginSection("ExpressiveDailyMixHeader.Composition")
    val albumArts = remember(songs) { songs.map { it.albumArtUriString }.distinct().take(3) }
    val totalDuration = remember(songs) { songs.sumOf { it.duration } }

    val parallaxOffset by remember { derivedStateOf { if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset * 0.5f else 0f } }

    val headerAlpha by remember {
        derivedStateOf {
            (1f - (scrollState.firstVisibleItemScrollOffset / 600f)).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .graphicsLayer {
                translationY = parallaxOffset
                alpha = headerAlpha
            }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-80).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                albumArts.forEachIndexed { index, artUrl ->
                    val size = when (index) {
                        0 -> 180.dp
                        1 -> 220.dp
                        2 -> 180.dp
                        else -> 150.dp
                    }
                    val rotation = when (index) {
                        0 -> -15f
                        1 -> 0f
                        2 -> 15f
                        else -> 0f
                    }
                    val shape = threeShapeSwitch(index, thirdShapeCornerRadius = 30.dp)

                    // --- INICIO DE LA CORRECCIÓN ---
                    if (index == 2) {
                        // Para la 3ra imagen, usamos Modifier.layout para controlar la medición y el posicionamiento.
                        Box(
                            modifier = Modifier.layout { measurable, constraints ->
                                // 1. Medimos el contenido (la imagen) para que sea un cuadrado perfecto de `size` x `size`,
                                // ignorando las restricciones de ancho que puedan venir del padre (el Row).
                                val placeable = measurable.measure(
                                    Constraints.fixed(width = size.roundToPx(), height = size.roundToPx())
                                )

                                // 2. Le decimos al Row que nuestro layout ocupará el ancho que él nos dio (`constraints.maxWidth`),
                                // de esta forma no empujamos a los otros elementos. La altura será la de nuestro cuadrado.
                                layout(constraints.maxWidth, placeable.height) {
                                    // 3. Colocamos nuestro contenido cuadrado (`placeable`) dentro del espacio asignado.
                                    // Lo centramos horizontalmente para que se desborde por ambos lados si es necesario.
                                    val xOffset = (constraints.maxWidth - placeable.width) / 2
                                    placeable.placeRelative(xOffset, 0)
                                }
                            }
                        ) {
                            // Este es el contenido que se mide y se dibuja.
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = rotation }
                                    .clip(shape)
                            ) {
                                SmartImage(
                                    model = artUrl ?: R.drawable.rounded_album_24,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize() // Llena el tamaño cuadrado que le dimos.
                                )
                            }
                        }
                    } else {
                        // Lógica original para las otras dos imágenes
                        Box(
                            modifier = Modifier
                                .size(size)
                                .graphicsLayer { rotationZ = rotation }
                                .clip(shape)
                        ) {
                            SmartImage(
                                model = artUrl ?: R.drawable.rounded_album_24,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // --- FIN DE LA CORRECCIÓN ---
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = 900f
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 6.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Daily Mix", style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${songs.size} Songs • ${formatDuration(totalDuration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            LargeFloatingActionButton(
                modifier = Modifier,
                onClick = onShowMenu,
                shape = RoundedStarShape(
                    sides = 8,
                    curve = 0.05,
                    rotation = 0f
                )
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.gemini_ai),
                    contentDescription = "Play"
                )
            }
        }
    }
    Trace.endSection()
}