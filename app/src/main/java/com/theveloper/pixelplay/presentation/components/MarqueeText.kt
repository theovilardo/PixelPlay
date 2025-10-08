package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    textAlign: TextAlign? = null,
    gradientEdgeColor: Color,
    gradientWidth: Dp = 16.dp
) {
    var isOverflowing by remember { mutableStateOf(false) }

    val textModifier = if (isOverflowing) {
        Modifier
            .basicMarquee(
                iterations = Int.MAX_VALUE,
                spacing = MarqueeSpacing(0.dp),
                velocity = 25.dp // Slower speed
            )
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val gradientWidthPx = gradientWidth.toPx()
                // Left fade
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, gradientEdgeColor),
                        startX = 0f,
                        endX = gradientWidthPx
                    ),
                    blendMode = BlendMode.DstIn
                )
                // Right fade
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(gradientEdgeColor, Color.Transparent),
                        startX = size.width - gradientWidthPx,
                        endX = size.width
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    } else {
        Modifier
    }

    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        // Subcompose with the actual text to measure it
        val textPlaceable = subcompose("text") {
            Text(text = text, style = style, maxLines = 1, onTextLayout = {
                // This is a common pattern, but we need to be careful.
                // The onTextLayout lambda is called after the layout phase,
                // but we need the overflow information during the layout phase.
                // SubcomposeLayout helps us here.
            })
        }[0].measure(constraints.copy(maxWidth = Int.MAX_VALUE))

        isOverflowing = textPlaceable.width > constraints.maxWidth

        // Subcompose with the (potentially) modified content
        val contentPlaceable = subcompose("content") {
            Box(modifier = textModifier) {
                Text(
                    text = text,
                    style = style,
                    textAlign = textAlign,
                    maxLines = 1,
                )
            }
        }[0].measure(constraints)

        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}