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
            .padding(horizontal = 18.dp)
            .clip(AbsoluteSmoothCornerShape(
                cornerRadiusTR = 24.dp,
                smoothnessAsPercentTR = 70,
                cornerRadiusTL = 24.dp,
                smoothnessAsPercentTL = 70,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentBR = 70,
                cornerRadiusBL = 0.dp,
                smoothnessAsPercentBL = 70
            )),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 28.dp + NavBarPersistentHeight + MiniPlayerHeight + systemNavBarHeight
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(genres, key = { it.id }) { genre ->
            // CORREGIDO: Obtener las URIs de manera más robusta
            GenreCard(
                genre = genre,
                onClick = { onGenreClick(genre) }
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    onClick: () -> Unit
) {
    val backgroundColor = getGenreBackgroundColor(genre.name)

    Card(
        modifier = Modifier
            .aspectRatio(1.2f)
            .clickable(onClick = onClick),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTR = 24.dp,
            smoothnessAsPercentTL = 70,
            cornerRadiusTL = 24.dp,
            smoothnessAsPercentTR = 70,
            cornerRadiusBR = 24.dp,
            smoothnessAsPercentBL = 70,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentBR = 70
        ),
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
                    .size(108.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp)
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
                    fontSize = 20.sp
                ),
                color = getOnGenreBackgroundColor(genre.name),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun getGenreBackgroundColor(genreName: String): Color {
    return when (genreName.lowercase()) {
        // Reds & Pinks - Muted and Tonal
        "rock" -> Color(0xFFC56262) // Muted Red (was 0xFF8B0000 - DarkRed)
        "pop" -> Color(0xFFF080AC) // Softer Pink (was 0xFFFF69B4 - HotPink)
        "punk" -> Color(0xFFD96676) // Muted Crimson (was 0xFFDC143C - Crimson)
        "reggaeton" -> Color(0xFFE97EA8) // Softer Deep Pink (was 0xFFFF1493 - DeepPink)
        "salsa" -> Color(0xFFF28C6C) // Muted Tomato/OrangeRed (was 0xFFFF6B35)
        "bachata" -> Color(0xFFDB7093) // Paler Violet Red (was 0xFFE91E63)

        // Oranges & Yellows - Tonal and Warm
        "country" -> Color(0xFFD8894E) // Muted Brownish Orange (was 0xFFD2691E - Chocolate)
        "indie" -> Color(0xFFFA8A5F) // Softer Coral (was 0xFFFF6347 - Tomato)
        "latino", "latin" -> Color(0xFFFFA040) // Softer Orange Red (was 0xFFFF4500 - OrangeRed)
        "merengue" -> Color(0xFFFFBB60) // Muted Orange (was 0xFFFFA726)

        // Greens - Natural and Tonal
        "hip hop", "hip-hop", "rap" -> Color(0xFF67C067) // Softer Lime Green (was 0xFF32CD32 - LimeGreen)
        "reggae" -> Color(0xFF58A058) // Muted Forest Green (was 0xFF228B22 - ForestGreen)
        "folk" -> Color(0xFF90C090) // Desaturated Dark Sea Green (was 0xFF8FBC8F - DarkSeaGreen)

        // Blues & Cyans - Calm and Tonal
        "jazz" -> Color(0xFF7358D4) // Muted Indigo/SlateBlue (was 0xFF4B0082 - Indigo)
        "electronic" -> Color(0xFF57B8BB) // Muted Dark Turquoise (was 0xFF00CED1 - DarkTurquoise)
        "blues" -> Color(0xFF5050A0) // Muted Midnight Blue (was 0xFF191970 - MidnightBlue)
        "alternative" -> Color(0xFF6A9EC2) // Muted Steel Blue (was 0xFF4682B4 - SteelBlue)

        // Purples - Tonal and Rich
        "classical" -> Color(0xFF9370DB) // Medium Purple (was 0xFF800080 - Purple) - Adjusted to be less dark
        "r&b", "rnb" -> Color(0xFFB366CF) // Muted Orchid (was 0xFF9932CC - DarkOrchid)

        // Darks/Greys - Tonal
        "metal" -> Color(0xFF607D8B) // Blue Grey (was 0xFF2F4F4F - DarkSlateGray) - More distinctly blue/grey, less green

        else -> MaterialTheme.colorScheme.surfaceVariant // A good Material You fallback
    }
}

@Composable
fun getOnGenreBackgroundColor(genreName: String): Color {
    return when (genreName.lowercase()) {
        // Background: Color(0xFFC56262) (Muted Red) - Dark-ish
        "rock" -> Color(0xFFFFDAD7) // Very light red/pink tone (like Material You's Tonal Spot 90 for a red accent)
        // Background: Color(0xFFF080AC) (Softer Pink) - Light
        "pop" -> Color(0xFF4F1D30)    // Very dark pink/magenta tone (like Tonal Spot 10-20)
        // Background: Color(0xFFD96676) (Muted Crimson) - Mid-dark
        "punk" -> Color(0xFFFFDADA)   // Very light crimson/pink tone
        // Background: Color(0xFFE97EA8) (Softer Deep Pink) - Light
        "reggaeton" -> Color(0xFF4A1A31) // Very dark magenta tone
        // Background: Color(0xFFF28C6C) (Muted Tomato/OrangeRed) - Light
        "salsa" -> Color(0xFF5B1A00)   // Very dark orange-red tone
        // Background: Color(0xFFDB7093) (Paler Violet Red) - Mid-dark
        "bachata" -> Color(0xFFFFD9E1) // Very light violet-pink tone

        // Background: Color(0xFFD8894E) (Muted Brownish Orange) - Mid-dark
        "country" -> Color(0xFFFFDCC3) // Very light brownish-orange tone
        // Background: Color(0xFFFA8A5F) (Softer Coral) - Light
        "indie" -> Color(0xFF56200C)   // Very dark coral/brown tone
        // Background: Color(0xFFFFA040) (Softer Orange Red) - Light
        "latino", "latin" -> Color(0xFF462A00) // Very dark orange tone
        // Background: Color(0xFFFFBB60) (Muted Orange) - Light
        "merengue" -> Color(0xFF3F2D00) // Very dark amber/brown tone

        // Background: Color(0xFF67C067) (Softer Lime Green) - Mid-light
        "hip hop", "hip-hop", "rap" -> Color(0xFF00390E) // Very dark green tone
        // Background: Color(0xFF58A058) (Muted Forest Green) - Dark
        "reggae" -> Color(0xFFD7F8D7)  // Very light green tone
        // Background: Color(0xFF90C090) (Desaturated Dark Sea Green) - Light
        "folk" -> Color(0xFF0F381F)    // Very dark desaturated green tone

        // Background: Color(0xFF7358D4) (Muted Indigo/SlateBlue) - Dark
        "jazz" -> Color(0xFFEADDFF)   // Very light indigo/purple tone
        // Background: Color(0xFF57B8BB) (Muted Dark Turquoise) - Mid-light
        "electronic" -> Color(0xFF00373A) // Very dark turquoise/cyan tone
        // Background: Color(0xFF5050A0) (Muted Midnight Blue) - Dark
        "blues" -> Color(0xFFDEE0FF)  // Very light blue/lavender tone
        // Background: Color(0xFF6A9EC2) (Muted Steel Blue) - Mid-dark
        "alternative" -> Color(0xFFDDEEFF) // Very light steel blue tone

        // Background: Color(0xFF9370DB) (Medium Purple) - Mid-tone
        "classical" -> Color(0xFFF0DBFF) // Very light purple tone
        // Background: Color(0xFFB366CF) (Muted Orchid) - Mid-light (Lighter than classical's BG)
        "r&b", "rnb" -> Color(0xFF3C004F)    // Very dark orchid/purple tone
        // Background: Color(0xFF607D8B) (Blue Grey) - Mid-dark
        "metal" -> Color(0xFFDCECF2)  // Very light blue-grey tone

        // Fallback for background: MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant // This already follows Material You principles
    }
}

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