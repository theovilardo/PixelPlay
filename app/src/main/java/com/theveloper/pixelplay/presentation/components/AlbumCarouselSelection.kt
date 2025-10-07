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
import com.theveloper.pixelplay.data.preferences.CarouselStyle
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
    carouselStyle: String = CarouselStyle.ONE_PEEK,
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

    val corner = lerp(16.dp, 4.dp, expansionFraction.coerceIn(0f, 1f))

    BoxWithConstraints(modifier = modifier) {
        val availableWidth = this.maxWidth
        val preferredItemWidth: Dp
        val contentPadding: PaddingValues

        when (carouselStyle) {
            CarouselStyle.NO_PEEK -> {
                preferredItemWidth = availableWidth
                contentPadding = PaddingValues(0.dp)
            }
            CarouselStyle.ONE_PEEK -> {
                preferredItemWidth = availableWidth * 0.8f
                contentPadding = PaddingValues(horizontal = (availableWidth * 0.1f))
            }
            CarouselStyle.TWO_PEEK -> {
                preferredItemWidth = availableWidth * 0.7f
                contentPadding = PaddingValues(horizontal = (availableWidth * 0.15f))
            }
            else -> {
                preferredItemWidth = availableWidth * 0.8f
                contentPadding = PaddingValues(horizontal = (availableWidth * 0.1f))
            }
        }

        val carouselHeight = preferredItemWidth

        RoundedHorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = preferredItemWidth,
            modifier = Modifier.height(carouselHeight),
            itemSpacing = itemSpacing,
            contentPadding = contentPadding,
            itemCornerRadius = corner
        ) { index ->
            val song = queue[index]
            Box(Modifier.fillMaxSize()) {
                OptimizedAlbumArt(
                    uri = song.albumArtUriString,
                    title = song.title,
                    modifier = Modifier.fillMaxSize().aspectRatio(1f),
                    targetSize = Size(600, 600)
                )
            }
        }
    }
}