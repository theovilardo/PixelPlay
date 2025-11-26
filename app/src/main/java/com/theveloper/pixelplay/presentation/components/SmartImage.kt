package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size // Import Coil's Size
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.theveloper.pixelplay.R
import coil.Coil

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
    val cacheKeyData = when (model) {
        is ImageRequest -> model.data
        else -> model
    }

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

    val request = when (model) {
        is ImageRequest -> model.newBuilder()
            .memoryCacheKey(buildCacheKey(cacheKeyData, targetSize))
            .diskCacheKey(buildCacheKey(cacheKeyData, targetSize))
            .build()
        else -> ImageRequest.Builder(context)
            .data(model)
            .crossfade(crossfadeDurationMillis)
            .diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(allowHardware)
            .memoryCacheKey(buildCacheKey(cacheKeyData, targetSize))
            .diskCacheKey(buildCacheKey(cacheKeyData, targetSize))
            .apply {
                size(targetSize)
            }
            .build()
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = clippedModifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha,
        imageLoader = Coil.imageLoader(context)
    ) {
        val state = painter.state

        LaunchedEffect(state) {
            onState?.invoke(state)
        }

        when (state) {
            is AsyncImagePainter.State.Success -> {
                SubcomposeAsyncImageContent()
            }
            AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> {
                Placeholder(
                    modifier = Modifier.fillMaxSize(),
                    drawableResId = placeholderResId,
                    contentDescription = contentDescription,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    alpha = alpha
                )
            }
            is AsyncImagePainter.State.Error -> {
                Placeholder(
                    modifier = Modifier.fillMaxSize(),
                    drawableResId = errorResId,
                    contentDescription = contentDescription,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    iconColor = MaterialTheme.colorScheme.onErrorContainer,
                    alpha = alpha
                )
            }
        }
    }
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

@Composable
private fun Placeholder(
    modifier: Modifier,
    @DrawableRes drawableResId: Int,
    contentDescription: String?,
    containerColor: Color,
    iconColor: Color,
    alpha: Float,
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(drawableResId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit
        )
    }
}
