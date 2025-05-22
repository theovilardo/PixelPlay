package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
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
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage3
import com.theveloper.pixelplay.presentation.components.DailyMixSection
import com.theveloper.pixelplay.presentation.components.GradientTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarPersistentHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.map
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

// Modern HomeScreen with collapsible top bar and staggered grid layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    updateScrollState: (Boolean) -> Unit = {}
) {
    val currentUiState by playerViewModel.stablePlayerState.collectAsState()
    val playerUiState by playerViewModel.playerUiState.collectAsState()
    val scrollState = rememberLazyListState()

    // Calculate recentUris for YourMixHeader, remembered against changes in STABLE allSongs list
    val recentUrisForHeader = remember(playerUiState.allSongs) {
        playerUiState.allSongs.take(6).map { it.albumArtUri }
    }

    //testing padding inferior
    val isPlayerVsisibleBottomPadding = if (currentUiState.currentSong != null) MiniPlayerHeight else 0.dp

    if (playerUiState.allSongs.isEmpty()) {
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
    } else if (playerUiState.allSongs.isEmpty()) {
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
                    bottom = paddingValuesParent.calculateBottomPadding() + 18.dp + NavBarPersistentHeight + isPlayerVsisibleBottomPadding//+ bottomBarHeigh.value.dp + 48.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Large "Your Mix" header with play button
                item {
                    YourMixHeader(
                        albumArtUris = recentUrisForHeader,
                        isPlatyingAndCsNotNull = false,
                        onPlayRandomSong = {
                            //make it grab song from Your Mix
                            playerViewModel.playPause()
                        }
                    )
                }

                // Artistic album displays with various shapes
                item {
                    AlbumArtCollage3(
                        modifier = Modifier
                            .fillMaxWidth(),
                        albumArts = recentUrisForHeader,
                        padding = 14.dp,
                        height = 400.dp,
                    )
                }

//                item {
//                    FeaturedPlaylistsSection()
//                }

                item {
                    MoodBasedSection(
                        songs = playerUiState.allSongs.take(5),
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }

                item {
                    DailyMixSection(
                        songs = playerUiState.allSongs.take(10),
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHeader(
    albumArtUris: List<Uri?>,
    isPlatyingAndCsNotNull : Boolean = false,
    onPlayRandomSong: () -> Unit
) {
    val recentUris = albumArtUris

    val buttonCorners = 68.dp

    val smallButtonCorners = 14.dp

    val playPauseIcon = if (isPlatyingAndCsNotNull) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                //.width(160.dp)
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
                text = "Traveler, Water Houses",
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodBasedSection(
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
    navController: NavController // navController no se usa directamente en la lógica del Carousel aquí, pero se mantiene por si se necesita
) {
    // El estado del carrusel ahora solo necesita itemCount.
    // initialItem por defecto es 0.
    val carouselState = rememberCarouselState(
        itemCount = { songs.size }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp) // Padding para toda la sección
    ) {
        SectionHeader(
            title = "Para Relajarse",
            showViewAll = true,
            onViewAllClick = { /* Lógica para navegar a la sección del mood */ }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Es una buena práctica verificar si la lista de canciones no está vacía
        // antes de mostrar el carrusel para evitar errores con itemCount = 0.
        if (songs.isNotEmpty()) {
            HorizontalMultiBrowseCarousel(
                state = carouselState,
                //itemCount = songs.size, // El número total de ítems en el carrusel
                modifier = Modifier.fillMaxWidth(), // El carrusel ocupará todo el ancho disponible
                preferredItemWidth = 120.dp, // Ancho del ítem principal (enfocado)
                itemSpacing = 10.dp, // Espacio entre ítems
                flingBehavior = CarouselDefaults.multiBrowseFlingBehavior(state = carouselState), // Comportamiento de snapping
                // minSmallItemWidth y maxSmallItemWidth definen el tamaño de los ítems "pequeños"
                // a los lados del ítem principal en HorizontalMultiBrowseCarousel.
                // Ajusta estos valores según el aspecto deseado.
                minSmallItemWidth = 70.dp,
                maxSmallItemWidth = 70.dp,
                // contentPadding es el padding *dentro* del área de contenido del carrusel.
                // Si tus ítems ya tienen padding o si el padding de la Column es suficiente,
                // puedes usar PaddingValues() o un valor pequeño.
                contentPadding = PaddingValues(horizontal = 0.dp) // Sin padding extra dentro del carrusel
            ) { index ->
                // Obtenemos la canción para el índice actual
                val song = songs[index]

                // Aquí va el composable de tu ítem.
                // SongListItemFavsWrapper debería estar diseñado para ocupar el preferredItemWidth
                // o ser flexible para que el carrusel lo escale/maneje adecuadamente.
                SongListItemFavsWrapper(
                    song = song,
                    playerViewModel = playerViewModel,
                    onClick = { /* tu lógica de click */ },
                    itemWidth = 120.dp // Pasa el ancho aquí
                    // modifier = Modifier // Puedes añadir modifiers adicionales si el carrusel los necesita para cada ítem
                )
            }
        } else {
            // Opcional: Mostrar un mensaje si no hay canciones
            Text(
                text = "No hay canciones para relajarse en este momento.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }

        // La LazyRow ha sido reemplazada por HorizontalMultiBrowseCarousel.
        // Ya no necesitas el siguiente bloque:
        /*
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(songs, key = { song -> song.id }) { song ->
                SongListItemFavsWrapper(
                    song = song,
                    playerViewModel = playerViewModel,
                    onClick = {
                        playerViewModel.playSongs(
                            startSong = song,
                            songsToPlay = songs,
                            queueName = "SongListItemFavsWrapper"
                        )
                        //navController.navigate(Screen.Player.createRoute(song.id))
                    }
                )
            }
        }
        */
    }
}

//// Daily Mix section example
//@Composable
//fun DailyMixSection(
//    songs: List<Song>,
//    playerViewModel: PlayerViewModel,
//    navController: NavController
//) {
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp)
//    ) {
//        SectionHeader(
//            title = "Your Daily Mix",
//            showViewAll = true,
//            onViewAllClick = { /* Navigate to daily mix */ }
//        )
//
//        Spacer(modifier = Modifier.height(16.dp))
//
//        Card(
//            modifier = Modifier.fillMaxWidth(),
//            shape = RoundedCornerShape(16.dp),
//            colors = CardDefaults.cardColors(
//                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
//            ),
//            elevation = CardDefaults.elevatedCardElevation(0.dp)
//        ) {
//            Column(modifier = Modifier.fillMaxWidth()) {
//                // Header with visual elements
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .height(80.dp)
//                        .background(
//                            brush = Brush.horizontalGradient(
//                                colors = listOf(
//                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
//                                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
//                                )
//                            )
//                        ),
//                    contentAlignment = Alignment.CenterStart
//                ) {
//                    // Album art collage effect
//                    Row(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(start = 16.dp),
//                        verticalAlignment = Alignment.CenterVertically
//                    ) {
//                        // Daily Mix icon/text
//                        Column(
//                            modifier = Modifier.weight(1f)
//                        ) {
//                            Text(
//                                text = "DAILY MIX",
//                                style = MaterialTheme.typography.titleMedium,
//                                fontWeight = FontWeight.Bold,
//                                color = MaterialTheme.colorScheme.onPrimary
//                            )
//
//                            Text(
//                                text = "Basado en tu historial",
//                                style = MaterialTheme.typography.bodySmall,
//                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
//                            )
//                        }
//
//                        // Album art thumbnails with overlap effect
//                        Row(modifier = Modifier.padding(end = 16.dp)) {
//                            songs.take(3).forEachIndexed { index, song ->
//                                Box(
//                                    modifier = Modifier
//                                        .offset(x = (-16 * index).dp)
//                                        .size(48.dp)
//                                        .clip(CircleShape)
//                                        .border(
//                                            2.dp,
//                                            MaterialTheme.colorScheme.surface,
//                                            CircleShape
//                                        )
//                                ) {
//                                    SmartImage(
//                                        model = song.albumArtUri ?: R.drawable.rounded_album_24,
//                                        contentDescription = null,
//                                        contentScale = ContentScale.Crop,
//                                        modifier = Modifier.fillMaxSize()
//                                    )
////                                    AsyncImage(
////                                        model = song.albumArtUri ?: R.drawable.rounded_album_24,
////                                        contentDescription = null,
////                                        contentScale = ContentScale.Crop,
////                                        modifier = Modifier.fillMaxSize()
////                                    )
//                                }
//                            }
//                        }
//                    }
//                }
//
//                // Song list
//                Column(modifier = Modifier.fillMaxWidth()) {
//                    songs.take(4).forEach { song -> // Limiting for example, ideally use LazyColumn
//                        SongListItemFavsWrapper(
//                            song = song,
//                            playerViewModel = playerViewModel,
//                            onClick = {
//                                playerViewModel.playSongs(
//                                    songsToPlay = songs,
//                                    startSong = song,
//                                    queueName = "SongListItemFavsWrapper2"
//                                )
//                                // Example: navController.navigate("player_screen/${song.id}")
//                            },
//                            modifier = Modifier.padding(vertical = 4.dp) // Add some padding between items
//                        )
//                    }
//
//                    // "Ver más" button
//                    TextButton(
//                        onClick = { /* TODO: Navigate to a screen showing all daily mix songs */ },
//                        modifier = Modifier.align(Alignment.CenterHorizontally)
//                    ) {
//                        Text(
//                            text = "Ver todo el Daily Mix",
//                            style = MaterialTheme.typography.bodyMedium,
//                            fontWeight = FontWeight.Medium
//                        )
//
//                        Spacer(modifier = Modifier.width(4.dp))
//
//                        Icon(
//                            painter = painterResource(R.drawable.rounded_keyboard_arrow_right_24),
//                            contentDescription = null,
//                            modifier = Modifier.size(20.dp)
//                        )
//                    }
//                }
//            }
//        }
//    }
//}

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
    modifier: Modifier = Modifier,
    title: String,
    artist: String,
    albumArtUrl: Uri?,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isPlaying) colors.primaryContainer.copy(alpha = 0.6f) else colors.surfaceColorAtElevation(2.dp)
    val contentColor = if (isPlaying) colors.onPrimaryContainer else colors.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 4.dp else 1.dp)
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

/**
 * Wrapper Composable para SongCardCarouselItem que aísla la observación del estado.
 * Este wrapper no cambia su lógica, solo llama al nuevo composable presentacional.
 */
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 90.dp // Ancho que se espera para el ítem en el carrusel (ej: preferredItemWidth)
) {
    // 1) Observamos sólo el ID de la canción que está sonando globalmente:
    val currentPlayingSongId by playerViewModel
        .stablePlayerState
        .map { it.currentSong?.id }
        .collectAsState(initial = null)

    // 2) Observamos sólo si está sonando o no:
    val isGlobalPlaying by playerViewModel
        .stablePlayerState
        .map { it.isPlaying }
        .collectAsState(initial = false)

    // 3) Derivamos si esta tarjeta corresponde a la canción en reproducción:
    val isThisSongPlaying = song.id == currentPlayingSongId && isGlobalPlaying

    // 4) Llamamos al nuevo composable presentacional pasándole sólo lo que necesita:
    SongCardCarouselItem(
        modifier = modifier, // El modifier se aplica a la Card
        title = song.title,
        artist = song.artist, // Usamos song.artist
        albumArtUrl = song.albumArtUri.toString(), // Usamos song.albumArtUri
        isPlaying = isThisSongPlaying,
        onClick = onClick,
        itemWidth = itemWidth // Pasamos el ancho al composable de la tarjeta
    )
}

// Wrapper Composable for SongListItemFavs to isolate state observation
// Wrapper Composable for SongListItemFavs to isolate state observation
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 1) Observamos sólo el ID de la canción que está sonando globalmente:
    val currentPlayingSongId by playerViewModel
        .stablePlayerState           // StateFlow<PlayerUiState>
        .map { it.currentSong?.id }  // Flow<String?>
        .collectAsState(initial = null)

    // 2) Observamos sólo si está sonando o no:
    val isGlobalPlaying by playerViewModel
        .stablePlayerState
        .map { it.isPlaying }    // Flow<Boolean>
        .collectAsState(initial = false)

    // 3) Derivamos si esta fila corresponde a la canción en reproducción:
    val isThisSongPlaying = song.id == currentPlayingSongId && isGlobalPlaying

    // 4) Llamamos al presentacional pasándole sólo lo que necesita:
    SongListItemFavs(
        modifier      = modifier,
        title         = song.title,
        artist        = song.artist,
        albumArtUrl   = song.albumArtUri,
        isPlaying     = isThisSongPlaying,
        onClick       = onClick
    )
}

//@Composable
//fun SongListItemFavsWrapper(
//    song: Song,
//    playerViewModel: PlayerViewModel,
//    onClick: () -> Unit,
//    modifier: Modifier = Modifier
//) {
//    // Observe only the necessary parts of playerUiState for *this specific song*
//    val currentPlayingSongId by playerViewModel.playerUiState.map { it.currentSong?.id }.collectAsState(initial = null)
//    val isGlobalPlaying by playerViewModel.playerUiState.map { it.isPlaying }.collectAsState(initial = false)
//
//    val isThisSongPlaying = song.id == currentPlayingSongId && isGlobalPlaying
//
//    // Call the presentational Composable with the derived state
//    SongListItemFavs(
//        modifier = modifier,
//        title = song.title,
//        artist = song.artist,
//        albumArtUrl = song.albumArtUri,
//        isPlaying = isThisSongPlaying,
//        onClick = onClick
//    )
//}