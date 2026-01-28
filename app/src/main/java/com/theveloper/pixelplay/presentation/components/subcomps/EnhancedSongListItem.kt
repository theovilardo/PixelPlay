package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingTextOnDemand
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.components.SmartImage
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean = false,
    isLoading: Boolean = false,
    showAlbumArt: Boolean = true,
    customShape: androidx.compose.ui.graphics.Shape? = null,
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    // Animamos el radio de las esquinas basándonos en si la canción es la actual.
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong && !isLoading) 50.dp else 22.dp,
        animationSpec = tween(durationMillis = 400),
        label = "cornerRadiusAnimation"
    )

    val animatedAlbumCornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong && !isLoading) 50.dp else 12.dp,
        animationSpec = tween(durationMillis = 400),
        label = "cornerRadiusAnimation"
    )

    val surfaceShape = remember(animatedCornerRadius, customShape, isCurrentSong, isLoading) {
        if (customShape != null && (!isCurrentSong || isLoading)) {
            customShape
        } else {
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = animatedCornerRadius,
                smoothnessAsPercentTR = 60,
                cornerRadiusTR = animatedCornerRadius,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = animatedCornerRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = animatedCornerRadius,
                smoothnessAsPercentTL = 60
            )
        }
    }

    val albumShape = remember(animatedCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedAlbumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedAlbumCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedAlbumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedAlbumCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    val colors = MaterialTheme.colorScheme
    val containerColor = if ((isCurrentSong) && !isLoading) colors.primaryContainer else colors.surfaceContainerLow
    val contentColor = if ((isCurrentSong) && !isLoading) colors.onPrimaryContainer else colors.onSurface

    val mvContainerColor = if ((isCurrentSong) && !isLoading) colors.primaryContainer else colors.onSurface
    val mvContentColor = if ((isCurrentSong) && !isLoading) colors.onPrimaryContainer else colors.surfaceContainerHigh

    if (isLoading) {
        // Shimmer Placeholder Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape),
            shape = surfaceShape,
            color = colors.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if(showAlbumArt) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if(showAlbumArt) 0.dp else 4.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }
        }
    } else {
        // Actual Song Item Layout
        var applyTextMarquee by remember { mutableStateOf(false) }

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { applyTextMarquee = !applyTextMarquee },
                        onPress = {
                            try {
                                awaitRelease()
                            } finally {
                                applyTextMarquee = false
                            }
                        })
                },
            shape = surfaceShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAlbumArt) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        // Usando tu composable SmartImage
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = song.title,
                            shape = albumShape,
                            targetSize = Size(168, 168), // 56dp * 3 (para densidad xxhdpi)
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    if (applyTextMarquee) {
                        AutoScrollingTextOnDemand(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            gradientEdgeColor = containerColor,
                            expansionFractionProvider = { 1f },
                        )

                    } else {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = contentColor,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isCurrentSong) {
                     PlayingEqIcon(
                         modifier = Modifier
                             .padding(start = 8.dp)
                             .size(width = 18.dp, height = 16.dp),
                         color = contentColor,
                         isPlaying = isPlaying
                     )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledIconButton(
                    onClick = { onMoreOptionsClick(song) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = mvContentColor,
                        contentColor = mvContainerColor
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options for ${song.title}",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
