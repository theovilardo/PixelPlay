package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp

@Composable
fun ExpressiveTopBarContent(
    title: String,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    containerHeightRange: Pair<Dp, Dp> = 88.dp to 56.dp,
    supportingContent: (@Composable () -> Unit)? = null
) {
    val clampedFraction = collapseFraction.coerceIn(0f, 1f)
    val titleScale = lerp(1.2f, 0.8f, clampedFraction)
    val titlePaddingStart = lerp(32.dp, 58.dp, clampedFraction)
    val titleVerticalBias = lerp(1f, -1f, clampedFraction)
    val animatedTitleAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val titleContainerHeight = lerp(containerHeightRange.first, containerHeightRange.second, clampedFraction)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(animatedTitleAlignment)
                .height(titleContainerHeight)
                .fillMaxWidth()
                .padding(start = titlePaddingStart, end = 24.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer {
                        scaleX = titleScale
                        scaleY = titleScale
                    }
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(1f - clampedFraction)
                    )
                }
                if (supportingContent != null) {
                    Box(modifier = Modifier.alpha(1f - clampedFraction)) {
                        supportingContent()
                    }
                }
            }
        }
    }
}
