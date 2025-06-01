package com.theveloper.pixelplay.presentation.screens.search.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarPersistentHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun GenreCategoriesGrid(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No genres available.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val systemNavBarHeight = getNavigationBarHeight()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 28.dp + NavBarPersistentHeight + MiniPlayerHeight + systemNavBarHeight
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(genres, key = { it.id }) { genre ->
            // CORREGIDO: Obtener las URIs de manera más robusta
            val songCoverUris by remember(genre.name) {
                derivedStateOf {
                    try {
                        val uris = playerViewModel.getSongUrisForGenre(genre.name)
                        // Tomar máximo 3 covers y filtrar nulls/vacíos
                        uris.filterNot { it.isNullOrBlank() }.take(3)
                    } catch (e: Exception) {
                        Log.e("GenreCategoriesGrid", "Error getting covers for ${genre.name}: ${e.message}")
                        emptyList<String>()
                    }
                }
            }

            GenreCard(
                genre = genre,
                songCoverUris = songCoverUris,
                onClick = { onGenreClick(genre) }
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    songCoverUris: List<String>,
    onClick: () -> Unit
) {
    val backgroundColor = getGenreBackgroundColor(genre.name)

    Card(
        modifier = Modifier
            .aspectRatio(1.2f)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = backgroundColor.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(backgroundColor)
        ) {
            // Gradiente de fondo sutil
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.6f),
                                backgroundColor.copy(alpha = 0.8f),
                                backgroundColor
                            )
                        )
                    )
            )

            // Imagen del género en esquina inferior derecha
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 30.dp)
            ) {
                SmartImage(
                    model = getGenreImageResource(genre.name),
                    contentDescription = "Genre illustration",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f),
                    contentScale = ContentScale.Crop
                )
            }

            // Nombre del género en esquina superior izquierda
            Text(
                text = genre.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )

            // CORREGIDO: Cover arts de canciones en esquina inferior izquierda
            if (songCoverUris.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)
                ) {
                    songCoverUris.take(3).forEachIndexed { index, coverUri ->
                        if (coverUri.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .zIndex((3 - index).toFloat())
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.dp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        shape = CircleShape
                                    )
                                    .background(Color.White.copy(alpha = 0.1f)) // Fondo de respaldo
                            ) {
                                SmartImage(
                                    model = coverUri,
                                    contentDescription = "Song cover $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
//                                    onError = {
//                                        Log.w("GenreCard", "Error loading cover: $coverUri")
//                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Mostrar placeholders si no hay covers disponibles
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy((-8).dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .zIndex((3 - index).toFloat())
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = Color.White.copy(alpha = 0.6f),
                                    shape = CircleShape
                                )
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Music placeholder",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getGenreBackgroundColor(genreName: String): Color {
    return when (genreName.lowercase()) {
        "rock" -> Color(0xFF8B0000)
        "pop" -> Color(0xFFFF69B4)
        "jazz" -> Color(0xFF4B0082)
        "classical" -> Color(0xFF800080)
        "electronic" -> Color(0xFF00CED1)
        "hip hop", "hip-hop", "rap" -> Color(0xFF32CD32)
        "country" -> Color(0xFFD2691E)
        "blues" -> Color(0xFF191970)
        "reggae" -> Color(0xFF228B22)
        "metal" -> Color(0xFF2F4F4F)
        "folk" -> Color(0xFF8FBC8F)
        "r&b", "rnb" -> Color(0xFF9932CC)
        "punk" -> Color(0xFFDC143C)
        "indie" -> Color(0xFFFF6347)
        "alternative" -> Color(0xFF4682B4)
        "latino", "latin" -> Color(0xFFFF4500)
        "reggaeton" -> Color(0xFFFF1493)
        "salsa" -> Color(0xFFFF6B35)
        "bachata" -> Color(0xFFE91E63)
        "merengue" -> Color(0xFFFFA726)
        else -> MaterialTheme.colorScheme.primary
    }
}

private fun getGenreImageResource(genreName: String): Any {
    return when (genreName.lowercase()) {
        "rock" -> R.drawable.rock_guitar_test
        "pop" -> R.drawable.pop_mic
        "jazz" -> R.drawable.sax
        "classical" -> R.drawable.clasic_piano
        "electronic" -> R.drawable.electronic_sound
        "hip hop", "hip-hop", "rap" -> R.drawable.rapper
        "country" -> R.drawable.banjo
        "blues" -> R.drawable.harmonica
        "reggae" -> R.drawable.maracas
        "metal" -> R.drawable.metal_guitar
        "folk" -> R.drawable.accordion
        "r&b / soul", "rnb" -> R.drawable.synth_piano
        "punk" -> R.drawable.punk
        "indie" -> R.drawable.idk_indie_ig
        "folk & acoustic" -> R.drawable.acoustic_guitar
        "alternative" -> R.drawable.alt_video
        "latino", "latin" -> R.drawable.star_angle
        "reggaeton" -> R.drawable.rapper
        "salsa" -> R.drawable.conga
        "bachata" -> R.drawable.bongos
        "merengue" -> R.drawable.drum
        else -> R.drawable.genre_default
    }
}