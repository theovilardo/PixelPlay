package com.theveloper.pixelplay.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
data class IconConfig(val size: Dp, val align: Alignment, val rot: Float, val shape: Shape, val offsetX: Dp, val offsetY: Dp)

@Composable
fun PermissionIconCollage(
    @DrawableRes icons: ImmutableList<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    padding: Dp = 0.dp,
) {
    val iconsToShow = remember(icons) {
        (icons.take(5) + List(5 - icons.size.coerceAtMost(5)) { null }).toImmutableList()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(padding)
    ) {
        val boxMaxHeight = maxHeight
        val iconConfigs by produceState<List<IconConfig>>(initialValue = emptyList(), iconsToShow, boxMaxHeight) {
            value = withContext(Dispatchers.Default) {
                val min = minOf(200.dp, height)
                listOf(
                    IconConfig(size = min * 0.8f, align = Alignment.Center, rot = -15f, shape = RoundedCornerShape(20.dp), offsetX = 0.dp, offsetY = 0.dp),
                    IconConfig(size = min * 0.4f, align = Alignment.TopStart, rot = 15f, shape = CircleShape, offsetX = (300.dp * 0.05f), offsetY = (boxMaxHeight * 0.05f)),
                    IconConfig(size = min * 0.4f, align = Alignment.BottomEnd, rot = 5f, shape = CircleShape, offsetX = -(300.dp * 0.05f), offsetY = -(boxMaxHeight * 0.05f)),
                    IconConfig(size = min * 0.5f, align = Alignment.TopEnd, rot = -20f, shape = RoundedCornerShape(20.dp), offsetX = -(300.dp * 0.1f), offsetY = (boxMaxHeight * 0.1f)),
                    IconConfig(size = min * 0.35f, align = Alignment.BottomStart, rot = 10f, shape = RoundedStarShape(sides = 8, curve = 0.1f), offsetX = (300.dp * 0.1f), offsetY = -(boxMaxHeight * 0.1f))
                )
            }
        }

        if (iconConfigs.isNotEmpty()) {
            iconsToShow.forEachIndexed { index, iconRes ->
                if (iconRes != null) {
                    val config = iconConfigs[index]
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier
                            .size(config.size)
                            .align(config.align)
                            .offset(config.offsetX, config.offsetY)
                            .graphicsLayer { rotationZ = config.rot }
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = config.shape
                            )
                            .clip(config.shape)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
