@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.telegram.channel

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TelegramSongItem(
    song: Song,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isDownloading: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Spring-based scale animation with overshoot effect
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val albumCornerRadius = 16.dp
    val albumShape = remember(albumCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = albumCornerRadius,
            smoothnessAsPercentTL = 60,
            cornerRadiusTL = albumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusBR = albumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = albumCornerRadius,
            smoothnessAsPercentBR = 60
        )
    }

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
        shape = cardShape,
        color = if (isPressed) 
            MaterialTheme.colorScheme.surfaceContainerHigh 
        else 
            MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (isPressed) 4.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Album Art with gradient fallback
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(albumShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = song.albumArtUriString ?: R.drawable.rounded_album_24,
                    contentDescription = song.title,
                    shape = albumShape,
                    targetSize = Size(168, 168),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                AutoScrollingText(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    gradientEdgeColor = if (isPressed)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else
                        MaterialTheme.colorScheme.surfaceContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                AutoScrollingText(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    gradientEdgeColor = if (isPressed)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else
                        MaterialTheme.colorScheme.surfaceContainer
                )
            }
            
            // Action Button with loading state
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloading) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    val isDownloaded = song.path.isNotEmpty()
                    
                    FilledTonalIconButton(
                        onClick = onDownloadClick,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (isDownloaded)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isDownloaded)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isDownloaded) 
                                Icons.Rounded.PlayArrow 
                            else 
                                Icons.Rounded.Download,
                            contentDescription = if (isDownloaded) "Play" else "Download"
                        )
                    }
                }
            }
        }
    }
}
