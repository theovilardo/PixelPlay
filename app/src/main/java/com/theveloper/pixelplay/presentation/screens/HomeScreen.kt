package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage
import com.theveloper.pixelplay.presentation.components.DailyMixSection
import com.theveloper.pixelplay.presentation.components.HomeGradientTopBar
import com.theveloper.pixelplay.presentation.components.HomeOptionsBottomSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarPersistentHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

// Modern HomeScreen with collapsible top bar and staggered grid layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    // 1) Observar sólo la lista de canciones, que cambia con poca frecuencia
    val allSongs by playerViewModel.allSongsFlow.collectAsState(initial = emptyList())

    val dailyMixSongs = remember(allSongs) {
        val list: List<Song> = allSongs.take(10)
        val immutable: ImmutableList<Song> = list.toImmutableList() // Now this should work
        immutable
    }

    val yourMixSong: String = "Today's Mix for you"

    // 2) Observar sólo el currentSong (o null) para saber si mostrar padding
    val currentSong by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong }
    }.collectAsState(initial = null)

    // 3) Calcular y recordar los URIs para el header
    val recentUrisForHeader: ImmutableList<String?> = remember(allSongs) {
        allSongs
            .take(6)
            .map { it.albumArtUriString }
            .toImmutableList() // Convierte la List resultante a ImmutableList
    }

    // Padding inferior si hay canción en reproducción
    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                HomeGradientTopBar(
                    onNavigationIconClick = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onMoreOptionsClick = {
                        showOptionsBottomSheet = true
                    }
//                    onMoreOptionsClick = {
//                        scope.launch {
//                            showOptionsBottomSheet = true
//                        }
//                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = paddingValuesParent.calculateBottomPadding()
                            + 18.dp + NavBarPersistentHeight + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Your Mix
                item {
                    YourMixHeader(
                        song = yourMixSong,
                        onPlayRandomSong = { playerViewModel.playPause() }
                    )
                }

                // Collage
                if (recentUrisForHeader.isNotEmpty()) {
                    item {
                        AlbumArtCollage(
                            modifier = Modifier.fillMaxWidth(),
                            albumArts = recentUrisForHeader,
                            padding = 14.dp,
                            height = 400.dp
                        )
                    }
                }

                // Daily Mix
                if (allSongs.isNotEmpty()) {
                    item {
                        DailyMixSection(
                            songs = dailyMixSongs,
                            onClickOpen = {
                                navController.navigate(Screen.DailyMixScreen.route)
                            },
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
        }
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
    }
    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showOptionsBottomSheet = false
                            navController.navigate(Screen.DJSpace.route)
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHeader(
    song: String,
    onPlayRandomSong: () -> Unit
) {

    val buttonCorners = 68.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(216.dp)
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
                    letterSpacing = (1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
            )

            // Artist/Song subtitle
            Text(
                text = song,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
            )
        }
        // Play Button
        LargeFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp),
            onClick = onPlayRandomSong,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = buttonCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = buttonCorners,
                smoothnessAsPercentTL = 60,
                cornerRadiusBL = buttonCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = buttonCorners,
                smoothnessAsPercentBL = 60,
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_play_arrow_24),
                contentDescription = "Reproducir",
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
            .padding(start = 4.dp)
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
                    "See more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
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


// SongListItem (modificado para aceptar parámetros individuales)
@Composable
fun SongListItemFavs(
    modifier: Modifier = Modifier,
    cardCorners: Dp = 12.dp,
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isPlaying) colors.primaryContainer.copy(alpha = 0.46f) else colors.surfaceContainer
    val contentColor = if (isPlaying) colors.primary else colors.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cardCorners),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = albumArtUrl ?: Icons.Filled.MusicNote,
                contentDescription = "Carátula de $title",
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(48.dp)
            )
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
                    tint = Color.Transparent//contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Composable presentacional para un ítem de canción en el carrusel, diseñado como una tarjeta vertical.
 */
@Composable
fun SongCardCarouselItem(
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    itemWidth: Dp // Ancho esperado para el ítem, usualmente el preferredItemWidth del carrusel
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(itemWidth), // La tarjeta toma el ancho especificado
        shape = RoundedCornerShape(12.dp), // Esquinas redondeadas
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp // Elevación sutil
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth() // La columna interna llena el ancho de la tarjeta
        ) {
            // Sección de la carátula del álbum con indicador de reproducción superpuesto
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f) // Mantiene la carátula cuadrada (ej: 90dp x 90dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(albumArtUrl)
                        .crossfade(true) // Animación suave al cargar la imagen
                        .build(),
                    contentDescription = "Carátula de $title", // Descripción para accesibilidad
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)), // Redondea solo las esquinas superiores de la imagen
                    contentScale = ContentScale.Crop, // Escala la imagen para llenar el espacio cortando si es necesario
                    // Reemplaza con tu propio placeholder:
                    placeholder = painterResource(id = android.R.drawable.ic_menu_gallery), // Placeholder genérico de Android
                    error = painterResource(id = android.R.drawable.ic_menu_report_image) // Error placeholder genérico
                )
                if (isPlaying) {
                    // Superposición semi-transparente para indicar reproducción
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                // Usa el color scrim del tema para la superposición
                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                // Aplica la misma forma que la imagen para que la superposición también esté redondeada
                                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            )
                    )
                    Icon(
                        imageVector = Icons.Filled.GraphicEq, // Ícono de ecualizador
                        contentDescription = "Reproduciendo",
                        tint = Color.White, // Ícono blanco sobre la superposición oscura
                        modifier = Modifier
                            .align(Alignment.Center) // Centra el ícono
                            .size(32.dp) // Tamaño del ícono
                    )
                }
            }

            // Sección de texto (título y artista)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp) // Padding para el texto
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge, // Estilo de Material 3 para títulos en tarjetas
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, // Limita a una línea para mantener la UI compacta
                    overflow = TextOverflow.Ellipsis // Añade "..." si el texto es muy largo
                )
                Spacer(modifier = Modifier.height(2.dp)) // Pequeño espacio entre título y artista
                Text(
                    text = artist,
                    style = MaterialTheme.typography.labelMedium, // Estilo para texto secundario
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Color más sutil para el artista
                )
            }
        }
    }
}

// Wrapper Composable for SongListItemFavs to isolate state observation
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect the stablePlayerState once
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()

    // Derive isThisSongPlaying using remember
    val isThisSongPlaying = remember(song.id, stablePlayerState.currentSong?.id, stablePlayerState.isPlaying) {
        song.id == stablePlayerState.currentSong?.id && stablePlayerState.isPlaying
    }

    // Call the presentational composable
    SongListItemFavs(
        modifier = modifier,
        cardCorners = 0.dp,
        title = song.title,
        artist = song.artist,
        albumArtUrl = song.albumArtUriString,
        isPlaying = isThisSongPlaying,
        onClick = onClick
    )
}