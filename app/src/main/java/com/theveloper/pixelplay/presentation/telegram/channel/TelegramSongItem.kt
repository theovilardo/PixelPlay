package com.theveloper.pixelplay.presentation.telegram.channel

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.SmartImage
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun TelegramSongItem(
    song: Song,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isDownloading: Boolean,
    modifier: Modifier = Modifier
) {
    val albumCornerRadius = 12.dp
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        albumShape
                    )
                    .clip(albumShape)
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
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    gradientEdgeColor = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.height(4.dp))
                AutoScrollingText(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    gradientEdgeColor = MaterialTheme.colorScheme.surface
                )
            }
            IconButton(
                onClick = onDownloadClick,
                enabled = !isDownloading
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    val icon = if (song.path.isNotEmpty()) Icons.Rounded.PlayArrow else Icons.Rounded.Download
                    Icon(
                        imageVector = icon,
                        contentDescription = "Download or Play",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
