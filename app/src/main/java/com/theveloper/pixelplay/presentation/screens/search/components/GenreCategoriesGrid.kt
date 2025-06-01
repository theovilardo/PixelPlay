package com.theveloper.pixelplay.presentation.screens.search.components

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun GenreCategoriesGrid(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
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
            .padding(horizontal = 12.dp)
            .clip(
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTR = 22.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusTL = 22.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusBR = 0.dp,
                    smoothnessAsPercentBL = 60,
                    cornerRadiusBL = 0.dp,
                    smoothnessAsPercentBR = 60
                )
            ),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 28.dp + NavBarPersistentHeight + MiniPlayerHeight + systemNavBarHeight
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(genres, key = { it.id }) { genre ->
            GenreCard(genre = genre, onClick = { onGenreClick(genre) })
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    onClick: () -> Unit
) {
    val backgroundColor = getGenreBackgroundColor(genre.name)
    // Simulamos algunas canciones para mostrar los cover arts
    val sampleSongCovers = getSampleSongCovers(genre.name)

    Card(
        modifier = Modifier
            .aspectRatio(1.2f)
            .shadow(
                elevation = 6.dp, // Expressive elevation
                shape = RoundedCornerShape(24.dp),
                spotColor = backgroundColor.copy(alpha = 0.5f) // Spot color based on genre bg
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp), // Standard M3 rounded corners
        colors = CardDefaults.cardColors(containerColor = Color.Transparent), // Container made transparent, bg handled by Box
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Elevation handled by shadow modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp)) // Clip content to the card shape
                .background(backgroundColor) // Set the base background color for the Box
        ) {
            // Gradiente de fondo sutil
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.6f), // Darker at the top
                                backgroundColor.copy(alpha = 0.8f),
                                backgroundColor // More opaque at the bottom
                            ),
                            // startY = 0.0f, endY = Float.POSITIVE_INFINITY (default like)
                        )
                    )
            )

            // Imagen del género en esquina inferior derecha (cortada)
            Box(
                modifier = Modifier
                    .size(120.dp) // Tamaño mayor para que se corte
                    .align(Alignment.BottomEnd)
                    .offset(x = 30.dp, y = 30.dp) // Offset para que se corte
            ) {
                SmartImage(
                    model = getGenreImageResource(genre.name),
                    contentDescription = "Genre illustration",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f), // Increased alpha for better visibility
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

            // Cover arts de canciones en esquina inferior izquierda
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .alpha(1f), // Explicitly set alpha to ensure visibility
                horizontalArrangement = Arrangement.spacedBy((-8).dp) // Overlap negativo
            ) {
                sampleSongCovers.take(3).forEachIndexed { index, coverUrl ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .zIndex((3 - index).toFloat()) // Z-index para el orden de apilamiento
                            .clip(CircleShape) // Clip the Box
                            .border( // Apply border to the Box
                                width = 2.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = CircleShape
                            )
                    ) {
                        SmartImage(
                            model = coverUrl,
                            contentDescription = "Song cover",
                            modifier = Modifier.fillMaxSize(), // SmartImage fills the Box
                            contentScale = ContentScale.Crop
                            // Removed .clip and .border from SmartImage's own modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getGenreBackgroundColor(genreName: String): Color {
    return when (genreName.lowercase()) {
        "rock" -> Color(0xFF8B0000) // Rojo oscuro
        "pop" -> Color(0xFFFF69B4) // Rosa
        "jazz" -> Color(0xFF4B0082) // Índigo
        "classical" -> Color(0xFF800080) // Púrpura
        "electronic" -> Color(0xFF00CED1) // Turquesa
        "hip hop", "hip-hop", "rap" -> Color(0xFF32CD32) // Verde lima
        "country" -> Color(0xFFD2691E) // Chocolate
        "blues" -> Color(0xFF191970) // Azul medianoche
        "reggae" -> Color(0xFF228B22) // Verde bosque
        "metal" -> Color(0xFF2F4F4F) // Gris pizarra oscuro
        "folk" -> Color(0xFF8FBC8F) // Verde mar oscuro
        "r&b", "rnb" -> Color(0xFF9932CC) // Orquídea oscuro
        "punk" -> Color(0xFFDC143C) // Carmesí
        "indie" -> Color(0xFFFF6347) // Tomate
        "alternative" -> Color(0xFF4682B4) // Azul acero
        "latino", "latin" -> Color(0xFFFF4500) // Rojo naranja
        "reggaeton" -> Color(0xFFFF1493) // Rosa profundo
        "salsa" -> Color(0xFFFF6B35) // Naranja vibrante
        "bachata" -> Color(0xFFE91E63) // Rosa
        "merengue" -> Color(0xFFFFA726) // Naranja
        else -> MaterialTheme.colorScheme.primary
    }
}

// Función para mapear géneros a recursos de imagen
private fun getGenreImageResource(genreName: String): Any {
    return when (genreName.lowercase()) {
        "rock" -> R.drawable.rock
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

// Función temporal para simular covers de canciones
// En tu implementación real, esto vendría de tu base de datos
private fun getSampleSongCovers(genreName: String): List<String> {
    // Aquí deberías obtener los covers reales de las canciones de ese género
    // Por ahora retorno una lista de ejemplo
    return listOf(
        "https://example.com/cover1.jpg",
        "https://example.com/cover2.jpg",
        "https://example.com/cover3.jpg"
    )
}

//@Composable
//fun GenreCategoriesGrid(
//    genres: List<Genre>,
//    onGenreClick: (Genre) -> Unit,
//    modifier: Modifier = Modifier
//) {
//    if (genres.isEmpty()) {
//        Box(
//            modifier = modifier.fillMaxSize().padding(16.dp),
//            contentAlignment = Alignment.Center
//        ) {
//            Text("No genres available.", style = MaterialTheme.typography.bodyLarge)
//        }
//        return
//    }
//
//    val systemNavBarHeight = getNavigationBarHeight()
//
//    LazyVerticalGrid(
//        columns = GridCells.Fixed(2), // Display two columns
//        modifier = modifier
//            .fillMaxSize()
//            .padding(horizontal = 12.dp)
//            .clip(
//                shape = AbsoluteSmoothCornerShape(
//                    cornerRadiusTR = 22.dp,
//                    smoothnessAsPercentTL = 60,
//                    cornerRadiusTL = 22.dp,
//                    smoothnessAsPercentTR = 60,
//                    cornerRadiusBR = 0.dp,
//                    smoothnessAsPercentBL = 60,
//                    cornerRadiusBL = 0.dp,
//                    smoothnessAsPercentBR = 60
//                )
//            )
//        ,
//        contentPadding = PaddingValues(
//            top = 8.dp,
//            bottom = 28.dp + NavBarPersistentHeight + MiniPlayerHeight + systemNavBarHeight
//        ),
//        verticalArrangement = Arrangement.spacedBy(14.dp),
//        horizontalArrangement = Arrangement.spacedBy(14.dp)
//    ) {
//        items(genres, key = { it.id }) { genre ->
//            GenreCard(genre = genre, onClick = { onGenreClick(genre) })
//        }
//    }
//}
//
//@Composable
//private fun GenreCard(
//    genre: Genre,
//    onClick: () -> Unit
//) {
//    Card(
//        modifier = Modifier
//            .aspectRatio(1.5f) // Aspect ratio for the card
//            .clickable(onClick = onClick),
//        //elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
//        shape = MaterialTheme.shapes.medium,
//        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
//    ) {
//        Box(
//            modifier = Modifier.fillMaxSize(),
//            contentAlignment = Alignment.Center
//        ) {
//            // Optional: Add SmartImage here if genre.imageUrl is available and valid
//             SmartImage(
//                 model = genre.imageUrl,
//                 contentDescription = "Image for ${genre.name}",
//                 modifier = Modifier.fillMaxSize(),
//                 contentScale = ContentScale.Crop
//             )
//
//            // For now, just text. If using an image, you might overlay text or place it below.
//            Text(
//                text = genre.name,
//                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
//                textAlign = TextAlign.Center,
//                modifier = Modifier.padding(16.dp)
//            )
//        }
//    }
//}
