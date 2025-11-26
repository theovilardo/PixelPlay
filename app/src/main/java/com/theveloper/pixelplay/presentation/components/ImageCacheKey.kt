package com.theveloper.pixelplay.presentation.components

import coil.size.Dimension
import coil.size.Size

fun buildCacheKey(data: Any?, targetSize: Size, prefix: String = "img"): String? {
    val base = data?.toString() ?: return null

    fun Dimension.asPixels(): Int = when (this) {
        is Dimension.Pixels -> px
        Dimension.Undefined -> -1
    }

    val sizePart = when (targetSize) {
        Size.ORIGINAL -> "original"
        else -> "${targetSize.width.asPixels()}x${targetSize.height.asPixels()}"
    }

    return "$prefix:$base:$sizePart"
}
