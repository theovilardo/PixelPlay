package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.R

@OptIn(ExperimentalCoilApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OptimizedAlbumArt(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = Size.ORIGINAL
) {
    val context = LocalContext.current

    if (renderDirectAlbumArt(
            model = uri,
            title = title,
            modifier = modifier
        )
    ) {
        return
    }

    val painter = rememberAsyncImagePainter(
        model = when (uri) {
            is ImageRequest -> uri
            else -> ImageRequest.Builder(context)
                .data(uri)
                .crossfade(false)
                .placeholder(R.drawable.ic_music_placeholder)
                .error(R.drawable.rounded_broken_image_24)
                .size(targetSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        }
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

@Composable
private fun renderDirectAlbumArt(
    model: Any?,
    title: String,
    modifier: Modifier
): Boolean {
    return when (model) {
        is ImageRequest -> renderDirectAlbumArt(model.data, title, modifier)
        is ImageVector -> {
            Image(
                imageVector = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Painter -> {
            Image(
                painter = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is ImageBitmap -> {
            Image(
                bitmap = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Bitmap -> {
            Image(
                bitmap = model.asImageBitmap(),
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        else -> false
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
