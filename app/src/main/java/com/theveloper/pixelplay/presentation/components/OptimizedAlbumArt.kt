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
import coil.size.Size
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalCoilApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OptimizedAlbumArt(
    uri: String,
    title: String,
    expansionFraction: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 1) Build an ImageRequest that uses Dispatchers.IO for decoding & caches aggressively
    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            // .size(Size.ORIGINAL) // Removed to allow Coil to determine size dynamically
            .crossfade(true)                           // subtle fade-in
            .dispatcher(Dispatchers.IO)                // offload decode to IO
            .memoryCachePolicy(CachePolicy.ENABLED)    // keep in memory
            .diskCachePolicy(CachePolicy.ENABLED)      // disk cache for faster repeat loads
            .build()
    }

    val painter = rememberAsyncImagePainter(
        model = request,
        onSuccess = { state: AsyncImagePainter.State.Success ->
            // state contains the drawable
            (state.result.drawable as? BitmapDrawable)
                ?.bitmap
                ?.asImageBitmap()
                ?.prepareToDraw()
        }
    )


    // 4) Lift alpha & shadow out of graphicsLayer into separate modifiers
    Image(
        painter = painter,
        contentDescription = "Album art of $title",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .padding(vertical = lerp(4.dp, 16.dp, expansionFraction))
            .fillMaxWidth(lerp(0.5f, 0.8f, expansionFraction))
            .aspectRatio(1f)
            .clip(RoundedCornerShape(lerp(16.dp, 24.dp, expansionFraction)))
            .shadow(elevation = 16.dp * expansionFraction)  // use Modifier.shadow()
            .graphicsLayer { alpha = expansionFraction }    // only alpha remains in layer
    )
}
