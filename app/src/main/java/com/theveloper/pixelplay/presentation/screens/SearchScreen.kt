package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

    val uiState by playerViewModel.playerUiState.collectAsState()
    val allSongs = uiState.allSongs

    val filteredSongs = remember(searchQuery, allSongs) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            playerViewModel.searchSongs(searchQuery)
        }
    }

    // Efectos de animación para el encabezado

    val searchbarPadding by animateDpAsState(
        targetValue = if (!active) 24.dp else 0.dp,
        label = "searchbarPadding"
    )

    // Colores con estilo "Expressive"
    val gradientColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.background
    )
    
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 70.dp)
    ) {
        // Fondo con gradiente dinámico
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = gradientColors
                    )
                )
                .padding(top = paddingValues.calculateTopPadding())
//                .padding(top = 60.dp)
        )

        // Contenido principal
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // SearchBar con estilo Material 3 Expressive
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { active = false },
                active = active,
                onActiveChange = { active = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = searchbarPadding)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .animateContentSize()
                    .clip(RoundedCornerShape(28.dp)), // Más redondeado para estilo expresivo
                placeholder = {
                    Text(
                        "Search...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Limpiar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    inputFieldColors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                ),
                content = {
                    // Resultados de búsqueda con animación
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (searchQuery.isNotBlank() && filteredSongs.isEmpty()) {
                            EmptySearchResults(
                                searchQuery,
                                colorScheme = colorScheme
                            )
                        } else if (filteredSongs.isNotEmpty()) {
                            SearchResults(filteredSongs, playerViewModel) {
                                active = false
                            }
                        }
                    }
                }
            )

            // Estado inicial cuando no hay búsqueda activa
            if (!active && searchQuery.isBlank()) {
                InitialSearchState(
                    colorScheme = colorScheme
                )
            }
        }
    }
}

@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ilustración con estilo más expresivo
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 16.dp)
        ) {
            val radius = size.minDimension / 4

            // Dibujar un círculo decorativo
            drawCircle(
                color = colorScheme.tertiary.copy(alpha = 0.2f),
                radius = radius * 1.5f
            )

            // Dibujar una cara triste estilizada
            drawCircle(
                color = colorScheme.secondary.copy(alpha = 0.6f),
                radius = radius,
                center = center
            )
            // Ojos
            drawCircle(
                color = colorScheme.onSecondary,
                radius = radius / 5,
                center = Offset(center.x - radius / 2, center.y - radius / 5)
            )
            drawCircle(
                color = colorScheme.onSecondary,
                radius = radius / 5,
                center = Offset(center.x + radius / 2, center.y - radius / 5)
            )
            // Boca triste
            val mouthPath = Path().apply {
                moveTo(center.x - radius / 2, center.y + radius / 3)
                quadraticBezierTo(
                    center.x, center.y + radius / 1.2f,
                    center.x + radius / 2, center.y + radius / 3
                )
            }
            drawPath(
                path = mouthPath,
                color = colorScheme.onSecondary,
                style = Stroke(width = radius / 10)
            )
        }

        Text(
            text = "No hay resultados para \"$searchQuery\"",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "¿Quieres probar con otra búsqueda?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SearchResults(
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
    onSongSelected: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(songs, key = { it.id }) { song ->
            ExpressiveSongListItem(
                song = song,
                onClick = {
                    playerViewModel.showAndPlaySong(song)
                    onSongSelected()
                }
            )
        }
    }
}

@Composable
fun ExpressiveSongListItem(
    song: Song,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    // Animaciones más expresivas
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 6.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        label = "backgroundColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(24.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Imagen del álbum con bordes muy redondeados y efecto
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .shadow(4.dp, RoundedCornerShape(18.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            )
                        )
                    )
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = "Album Art",
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxSize()
                )
//                AsyncImage(
//                    model = song.albumArtUri ?: painterResource(R.drawable.rounded_album_24),
//                    contentDescription = "Album Art",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .clip(RoundedCornerShape(18.dp))
//                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Información de la canción
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
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
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Botón de play más expresivo
            FilledIconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun InitialSearchState(colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Elemento decorativo expresivo
        Box(
            modifier = Modifier
                .size(180.dp)
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Círculos concéntricos con un efecto de ondas
            for (i in 3 downTo 0) {
                val alpha = 0.2f - (i * 0.05f)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding((i * 12).dp)
                ) {
                    drawCircle(
                        color = colorScheme.primary.copy(alpha = alpha),
                        radius = size.minDimension / 2
                    )
                }
            }

            // Icono central
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }

        Text(
            text = "Tu biblioteca musical",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Escribe para descubrir canciones, artistas o álbumes que te inspiren",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Sugerencias rápidas de búsqueda
        Text(
            text = "Prueba buscar:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.Start).padding(start = 8.dp, bottom = 12.dp)
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { },
                label = { Text("Pop") },
                shape = RoundedCornerShape(24.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                )
            )

            SuggestionChip(
                onClick = { },
                label = { Text("Rock clásico") },
                shape = RoundedCornerShape(24.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                )
            )

            SuggestionChip(
                onClick = { },
                label = { Text("Música latina") },
                shape = RoundedCornerShape(24.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                )
            )

            SuggestionChip(
                onClick = { },
                label = { Text("Indie") },
                shape = RoundedCornerShape(24.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                )
            )
        }
    }
}