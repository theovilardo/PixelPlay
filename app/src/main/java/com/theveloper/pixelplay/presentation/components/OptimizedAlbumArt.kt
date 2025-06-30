package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.ExperimentalComposeUiApi

// OptimizedAlbumArt is now a simpler composable that just displays a Painter.
// The responsibility for loading the image and handling states (loading, error)
// has been moved to the caller (FullPlayerContentInternal).
// The modifier passed to this composable is expected to handle all transformations
// like padding, size, aspect ratio, clipping, shadow, and alpha,
// which are typically controlled by the 'expansionFraction' in the calling composable.

@OptIn(ExperimentalComposeUiApi::class) // Kept in case any Modifier methods used by parent are experimental
@Composable
fun OptimizedAlbumArt(
    painter: Painter,
    title: String, // Used for contentDescription
    modifier: Modifier = Modifier // This modifier is expected to be fully configured by the caller
) {
    Image(
        painter = painter,
        contentDescription = "Album art of $title",
        contentScale = ContentScale.Crop, // Assuming Crop is the desired scale
        modifier = modifier // The passed modifier handles all visual aspects (size, shape, alpha, etc.)
    )
}
