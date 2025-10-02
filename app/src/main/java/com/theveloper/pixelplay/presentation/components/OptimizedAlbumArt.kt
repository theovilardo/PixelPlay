package com.theveloper.pixelplay.presentation.components

import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.R
import timber.log.Timber

@OptIn(ExperimentalCoilApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OptimizedAlbumArt(
    uri: String?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = Size.ORIGINAL
) {
    val context = LocalContext.current

    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(uri)
            .crossfade(false)
            .placeholder(R.drawable.ic_music_placeholder)
            .error(R.drawable.rounded_broken_image_24)
            .size(targetSize)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    )

    Crossfade(
        targetState = painter.state,
        modifier = modifier, // el padre le dará fillMaxSize()
        animationSpec = tween(350),
        label = "AlbumArtCrossfade"
    ) { state ->
        when (state) {
            is AsyncImagePainter.State.Success -> Image(
                painter = state.painter,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            is AsyncImagePainter.State.Loading,
            is AsyncImagePainter.State.Empty -> Image(
                painter = painterResource(R.drawable.ic_music_placeholder),
                contentDescription = "$title placeholder",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            is AsyncImagePainter.State.Error -> Image(
                painter = painterResource(R.drawable.rounded_broken_image_24),
                contentDescription = "Error loading album art for $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}



//@Composable
//fun OptimizedAlbumArt(
//    uri: String?,
//    title: String,
//    expansionFraction: Float,
//    modifier: Modifier = Modifier,
//    targetSize: Size = Size.ORIGINAL
//) {
//    val context = LocalContext.current
//
//    val painter = rememberAsyncImagePainter(
//        model = ImageRequest.Builder(context)
//            .data(uri)
//            .crossfade(false)
//            .placeholder(R.drawable.ic_music_placeholder)
//            .error(R.drawable.rounded_broken_image_24)
//            .size(targetSize) // Usar el parámetro targetSize
//            .memoryCachePolicy(CachePolicy.ENABLED)
//            .diskCachePolicy(CachePolicy.ENABLED)
//            .build(),
//        onState = { state ->
//            Timber.tag("OptimizedAlbumArt")
//                .d("Painter State (Size: $targetSize): $state for URI: $uri")
//            if (state is AsyncImagePainter.State.Error) {
//                Timber.tag("OptimizedAlbumArt")
//                    .e(state.result.throwable, "Coil Error State for URI: $uri")
//            }
//        }
//    )
//
//    val imageContainerModifier = modifier
//        .padding(vertical = lerp(4.dp, 16.dp, expansionFraction))
//        .fillMaxWidth(lerp(0.5f, 0.8f, expansionFraction))
//        .aspectRatio(1f)
//        //.clip(RoundedCornerShape(lerp(16.dp, 24.dp, expansionFraction)))
//        .graphicsLayer {
//            clip = true
//            alpha = expansionFraction
//        }
//
//    Crossfade(
//        targetState = painter.state,
//        modifier = imageContainerModifier,
//        animationSpec = tween(durationMillis = 350),
//        label = "AlbumArtCrossfade"
//    ) { currentState ->
//        when (currentState) {
//            is AsyncImagePainter.State.Loading,
//            is AsyncImagePainter.State.Empty -> { // Show static placeholder for Loading and Empty states
//                Image(
//                    painter = painterResource(id = R.drawable.ic_music_placeholder),
//                    contentDescription = "$title placeholder", // Adjusted content description
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize(),
//                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
//                )
//            }
//            is AsyncImagePainter.State.Error -> {
//                Timber.tag("OptimizedAlbumArt")
//                    .e(currentState.result.throwable, "Displaying error placeholder for URI: $uri")
//                Image(
//                    painter = painterResource(id = R.drawable.rounded_broken_image_24),
//                    contentDescription = "Error loading album art for $title",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//            }
//            is AsyncImagePainter.State.Success -> {
//                Image(
//                    painter = currentState.painter,
//                    contentDescription = "Album art of $title",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//            }
//            // Note: AsyncImagePainter.State.Empty is now handled with Loading.
//            // If a distinct visual for Empty is needed and it's different from Loading,
//            // it would need its own branch. For now, grouped with Loading to show the static placeholder.
//        }
//    }
//}
