package com.theveloper.pixelplay.presentation.components

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
// import coil.size.Size // Not strictly needed if ORIGINAL is not used
import kotlinx.coroutines.Dispatchers
import com.theveloper.pixelplay.R // Import R explicitly for drawable resources

@OptIn(ExperimentalCoilApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OptimizedAlbumArt(
    uri: String,
    title: String,
    expansionFraction: Float,
    modifier: Modifier = Modifier // This modifier will be applied to the outer Box
) {
    val context = LocalContext.current

    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            // Keep placeholder for Coil internal use, it might show briefly before state is Loading
            .placeholder(R.drawable.rounded_album_24)
            .error(R.drawable.rounded_broken_image_24) // Define an error drawable in request
            .crossfade(true)
            .dispatcher(Dispatchers.IO)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    val painter = rememberAsyncImagePainter(
        model = request,
        onSuccess = { state ->
            (state.result.drawable as? BitmapDrawable)
                ?.bitmap
                ?.asImageBitmap()
                ?.prepareToDraw()
        }
        // onError and onLoading can also be handled here if needed,
        // but painter.state handles it more explicitly for UI switching.
    )

    Box(
        modifier = modifier // Apply the passed modifier to the Box
            .padding(vertical = lerp(4.dp, 16.dp, expansionFraction))
            .fillMaxWidth(lerp(0.5f, 0.8f, expansionFraction))
            .aspectRatio(1f)
            .clip(RoundedCornerShape(lerp(16.dp, 24.dp, expansionFraction)))
            .shadow(elevation = 16.dp * expansionFraction)
            .graphicsLayer { alpha = expansionFraction }
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Loading -> {
                ShimmerBox(modifier = Modifier.fillMaxSize())
            }
            is AsyncImagePainter.State.Error -> {
                Image(
                    painter = painterResource(id = R.drawable.rounded_broken_image_24), // Fallback error image
                    contentDescription = "Error loading album art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> { // AsyncImagePainter.State.Success or AsyncImagePainter.State.Empty
                Image(
                    painter = painter,
                    contentDescription = "Album art of $title",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
