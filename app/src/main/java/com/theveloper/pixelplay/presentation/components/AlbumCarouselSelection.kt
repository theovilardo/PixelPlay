package com.theveloper.pixelplay.presentation.components

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import kotlinx.collections.immutable.ImmutableList

// ====== TIPOS/STATE DEL CARRUSEL (wrapper para mantener compatibilidad) ======

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberRoundedParallaxCarouselState(
    initialPage: Int,
    pageCount: () -> Int
): CarouselState = rememberCarouselState(initialItem = initialPage, itemCount = pageCount)

// ====== TU SECCIÓN: ACOPLADA AL NUEVO API ======

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCarouselSection(
    currentSong: Song?,
    queue: ImmutableList<Song>,
    expansionFraction: Float,
    onSongSelected: (Song) -> Unit,
    modifier: Modifier = Modifier,
    preferredItemWidth: Dp = 280.dp,
    itemSpacing: Dp = 8.dp
) {
    if (queue.isEmpty()) return

    // Mantiene compatibilidad con tu llamada actual
    val carouselState = rememberRoundedParallaxCarouselState(
        initialPage = queue.indexOf(currentSong).coerceAtLeast(0),
        pageCount = { queue.size }
    )

    // Player -> Carousel
    val currentSongIndex = remember(currentSong, queue) {
        queue.indexOf(currentSong).coerceAtLeast(0)
    }
    LaunchedEffect(currentSongIndex) {
        if (carouselState.pagerState.currentPage != currentSongIndex) {
            // animateScrollToPage es del PagerState (foundation)
            carouselState.pagerState.animateScrollToPage(currentSongIndex)
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    // Carousel -> Player (cuando se detiene el scroll)
    LaunchedEffect(carouselState.pagerState, currentSongIndex, queue) {
        snapshotFlow { carouselState.pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                val settled = carouselState.pagerState.currentPage
                if (settled != currentSongIndex) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    queue.getOrNull(settled)?.let(onSongSelected)
                }
            }
    }

    // Radio animado (usa ui.util.lerp(Dp, Dp, Float))
    val corner = lerp(16.dp, 4.dp, expansionFraction.coerceIn(0f, 1f))

    // Carrusel con scope receiver en el contenido
    RoundedHorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = preferredItemWidth,
        modifier = modifier,
        itemSpacing = itemSpacing,
        contentPadding = PaddingValues(horizontal = 0.dp),
        itemCornerRadius = corner,
        //parallaxMaxOffsetPx = 36f
    ) { index ->
        // <<--- ESTA LAMBDA AHORA ES CON SCOPE (CarouselItemScope). Solo recibe index.
        val song = queue[index]

        // Tu contenido ocupando toda la tarjeta/slide
        Box(Modifier.fillMaxSize()) {
            OptimizedAlbumArt(
                uri = song.albumArtUriString,
                title = song.title,
                modifier = Modifier.fillMaxSize(),
                targetSize = Size(600, 600) // Float para Size de compose-ui
            )
        }
    }
}