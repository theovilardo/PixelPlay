package com.theveloper.pixelplay.presentation.components

import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import kotlinx.coroutines.Dispatchers
import coil.size.Size // Import Coil's Size

@Composable
fun SmartImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderResId: Int = R.drawable.ic_music_placeholder,
    errorResId: Int = R.drawable.rounded_broken_image_24,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeDurationMillis: Int = 300,
    useDiskCache: Boolean = true,
    useMemoryCache: Boolean = true,
    allowHardware: Boolean = false,
    targetSize: Size = Size(300, 300),
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    onState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current

    val requestBuilder = ImageRequest.Builder(context)
        .data(model)
        .placeholder(placeholderResId)
        .error(errorResId)
        .crossfade(crossfadeDurationMillis)
        .diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
        .memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
        .allowHardware(allowHardware)
        .memoryCacheKey(model?.toString()?.plus("_${targetSize.width}x${targetSize.height}"))
        .diskCacheKey(model?.toString()?.plus("_${targetSize.width}x${targetSize.height}"))

    targetSize.let {
        requestBuilder.size(it)
    }

    AsyncImage(
        model = requestBuilder.build(),
        contentDescription = contentDescription,
        modifier = modifier.clip(shape),
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha,
        onState = onState,
    )
}
