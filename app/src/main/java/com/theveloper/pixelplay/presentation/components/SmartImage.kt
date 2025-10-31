package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size // Import Coil's Size
import com.theveloper.pixelplay.R

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
    val clippedModifier = modifier.clip(shape)

    @Suppress("NAME_SHADOWING")
    val model = when (model) {
        is ImageRequest -> handleDirectModel(
            data = model.data,
            modifier = clippedModifier,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha
        ) ?: model
        else -> handleDirectModel(
            data = model,
            modifier = clippedModifier,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha
        ) ?: model
    }

    if (model is ImageVector || model is Painter || model is ImageBitmap || model is Bitmap) {
        // Already rendered inside handleDirectModel.
        return
    }

    if (model is ImageRequest) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = clippedModifier,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha,
            onState = onState,
        )
        return
    }

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
        modifier = clippedModifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha,
        onState = onState,
    )
}

@Composable
private fun handleDirectModel(
    data: Any?,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    alpha: Float
): Any? {
    return when (data) {
        is ImageVector -> {
            Image(
                imageVector = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Painter -> {
            Image(
                painter = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is ImageBitmap -> {
            Image(
                bitmap = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Bitmap -> {
            Image(
                bitmap = data.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        else -> null
    }
}
