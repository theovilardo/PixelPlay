package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.R
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalCoilApi::class)
@Composable
fun SmartImage(
    // Data source: could be Uri, URL string, @DrawableRes Int, etc.
    model: Any?,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    // Placeholder & error
    @DrawableRes placeholder: Int = R.drawable.rounded_imagesmode_24,
    @DrawableRes error: Int = R.drawable.rounded_broken_image_24,
    // Shape and size
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    // Animation & caching
    crossfadeDuration: Int = 300,
    useDiskCache: Boolean = true,
    useMemoryCache: Boolean = true,
    // Size hints for optimized loading
    size: Size? = null,
    // Color Filter, alpha, etc.
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    // Optional warm-up callback
    onSuccessWarmUp: Boolean = true
) {
    val context = LocalContext.current

    // 1) Build and remember the ImageRequest with optimizations
    val request = remember(model, size) {
        ImageRequest.Builder(context)
            .data(model)
            .placeholder(placeholder)
            .error(error)
            .crossfade(crossfadeDuration)
            .dispatcher(Dispatchers.IO)
            // Add size if provided to avoid decoding at full resolution
            .apply {
                // Set size hint if provided to avoid decoding at full resolution unnecessarily
                size?.let { this.size(it) }
                
                // Set cache policies
                if (!useDiskCache) diskCachePolicy(CachePolicy.DISABLED) else diskCachePolicy(CachePolicy.ENABLED)
                if (!useMemoryCache) memoryCachePolicy(CachePolicy.DISABLED) else memoryCachePolicy(CachePolicy.ENABLED)
                
                // Add bitmap pooling to reuse memory
                allowRgb565(true)  // Use RGB_565 format when possible (smaller memory footprint)
                allowHardware(false)  // Disable hardware bitmaps for consistent behavior with palette
                bitmapConfig(Bitmap.Config.RGB_565)  // Prefer RGB_565 when color accuracy isn't critical
            }
            .build()
    }

    // 2) Create and remember painter
    val painter = rememberAsyncImagePainter(
        model = request,
        // 3) Warm up bitmap for smoother draws but only if explicitly requested
        onSuccess = { state ->
            if (onSuccessWarmUp) {
                (state.result.drawable as? BitmapDrawable)
                    ?.bitmap
                    ?.asImageBitmap()
                    ?.prepareToDraw()
            }
        }
    )

    // 4) Display via Image
    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha,
        modifier = modifier
            .clip(shape)
    )
}
