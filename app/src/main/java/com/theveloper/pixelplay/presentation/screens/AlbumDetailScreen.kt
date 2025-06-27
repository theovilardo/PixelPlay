package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
// import com.theveloper.pixelplay.presentation.viewmodel.AlbumDetailViewModel // Se creará después


// Placeholder para MiniPlayerHeight si no está definido globalmente
// val MiniPlayerHeight = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String, // albumId se sigue pasando para posible uso directo si es necesario, o para el ViewModel
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val album = uiState.album
    val songs = uiState.songs
    val isLoading = uiState.isLoading
    val error = uiState.error

    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState() // Para el estado isPlaying

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior() // Pinned para que no se oculte

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { /* El título se manejará en la sección de la imagen */ },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White // Asumiendo que el texto sobre la imagen será blanco
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent, // Hacemos transparente el TopAppBar
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f) // Color cuando hay scroll
                ),
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (songs.isNotEmpty() && album != null) {
                LargeFloatingActionButton(
                    onClick = { playerViewModel.playAlbum(album) }, // Acción conectada
                    modifier = Modifier.padding(bottom = MiniPlayerHeight + 16.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, "Reproducir álbum")
                }
            }
        },
        content = { innerPadding ->
            if (isLoading && album == null) { // Mostrar loading principal si el album aún no carga
                Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (error != null && album == null) { // Mostrar error principal si el album no cargó
                Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()), contentAlignment = Alignment.Center) {
                    Text(text = error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                }
            } else if (album != null) { // Si el álbum está cargado (incluso si las canciones aún no o hay error de canciones)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = MiniPlayerHeight + 80.dp)
                ) {
                    // 1. Cabecera con Imagen del Álbum y Degradado
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f) // Imagen cuadrada, o la proporción que desees
                        ) {
                            SmartImage(
                                model = album.albumArtUriString ?: R.drawable.rounded_album_24,
                                contentDescription = "Portada de ${album.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Degradado inferior
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                            startY = 0.6f * 1000f // Ajustar para que el degradado empiece más abajo
                                        )
                                    )
                            )
                            // Textos del álbum superpuestos
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = album.title,
                                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = album.artist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${album.songCount} canciones", // Podríamos añadir duración total si la tenemos
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        // Separador entre la cabecera y la lista de canciones
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 2. Lista de Canciones
                    if (songs.isEmpty()) {
                        item {
                            Text(
                                "No hay canciones en este álbum.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(songs, key = { song -> "album_song_${song.id}" }) { song ->
                            EnhancedSongListItem(
                                song = song,
                                isPlaying = false, // TODO: playerViewModel.stablePlayerState.currentSong?.id == song.id,
                                onMoreOptionsClick = { /* TODO: Mostrar menú de opciones */ },
                                onClick = {playerViewModel.showAndPlaySong(song, songs)  }
                            )
                        }
                    }

                    // Espaciador final
                    item {
                        Spacer(Modifier.height(16.dp)) // Espacio adicional al final de la lista
                    }
                }
            }
        }
    )
}
