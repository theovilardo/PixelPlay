package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    textAlign: TextAlign? = null,
    gradientEdgeColor: Color,
    gradientWidth: Dp = 24.dp
) {
    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        val textPlaceable = subcompose("text") {
            Text(text = text, style = style, maxLines = 1)
        }[0].measure(constraints.copy(maxWidth = Int.MAX_VALUE))

        val isOverflowing = textPlaceable.width > constraints.maxWidth

        val content = @Composable {
            if (isOverflowing) {
                val initialDelayMillis = 1500
                val fadeAnimationDuration = 500
                val gradientAlpha = remember { Animatable(0f) }

                LaunchedEffect(Unit) {
                    delay(initialDelayMillis.toLong())
                    gradientAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = fadeAnimationDuration, easing = LinearEasing)
                    )
                }

                Box(
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val gradientWidthPx = gradientWidth.toPx()
                            // Left fade-in, drawn with animated alpha
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, gradientEdgeColor),
                                    startX = 0f,
                                    endX = gradientWidthPx
                                ),
                                blendMode = BlendMode.DstIn,
                                alpha = gradientAlpha.value
                            )
                            // Right fade-out, drawn with animated alpha
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(gradientEdgeColor, Color.Transparent),
                                    startX = size.width - gradientWidthPx,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn,
                                alpha = gradientAlpha.value
                            )
                        }
                ) {
                    Text(
                        text = text,
                        style = style,
                        textAlign = textAlign,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(gradientWidth + 6.dp),
                            velocity = 25.dp,
                            initialDelayMillis = initialDelayMillis
                        )
                    )
                }
            } else {
                Text(
                    text = text,
                    style = style,
                    textAlign = textAlign,
                    maxLines = 1,
                )
            }
        }

        val contentPlaceable = subcompose("content", content)[0].measure(constraints)
        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}