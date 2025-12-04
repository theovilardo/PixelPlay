package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import kotlin.math.floor
import kotlin.math.sqrt

@Composable
fun PlaylistArtCollage(
    songs: List<Song>,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Playlist",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    } else {
        Surface(
            modifier = modifier
                .aspectRatio(1f),
            shape = RoundedCornerShape(0.dp),
            color = Color.Transparent
        ) {
            val imageModifier = Modifier

            when (songs.size) {
                1 -> {
                    SmartImage(
                        model = songs[0].albumArtUriString,
                        contentDescription = songs[0].title,
                        contentScale = ContentScale.Crop,
                        targetSize = Size(256, 256),
                        modifier = imageModifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
                2 -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SmartImage(
                            model = songs[0].albumArtUriString,
                            contentDescription = songs[0].title,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(128, 128),
                            modifier = imageModifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                        )
                        SmartImage(
                            model = songs[1].albumArtUriString,
                            contentDescription = songs[1].title,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(128, 128),
                            modifier = imageModifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                        )
                    }
                }
                3 -> {
                    Layout(
                        content = {
                            songs.take(3).forEach { song ->
                                SmartImage(
                                    model = song.albumArtUriString,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    targetSize = Size(128, 128),
                                    modifier = imageModifier.clip(CircleShape)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { measurables, constraints ->
                        val separation = 2.dp.toPx()
                        // Recalculate itemSize based on the total width required for the triangle base
                        val itemSize = floor((constraints.maxWidth * 2f / (2f + sqrt(3f))) - separation).toInt()


                        val placeables = measurables.map {
                            it.measure(
                                constraints.copy(
                                    minWidth = itemSize, maxWidth = itemSize,
                                    minHeight = itemSize, maxHeight = itemSize
                                )
                            )
                        }

                        // The side length of the triangle formed by the centers
                        val L = itemSize + separation
                        // The height of the triangle formed by the centers
                        val h = L * sqrt(3f) / 2f

                        val collageHeight = h + itemSize
                        val collageWidth = L + itemSize

                        val offsetX = ((constraints.maxWidth - collageWidth) / 2f).toInt()
                        val offsetY = ((constraints.maxHeight - collageHeight) / 2f).toInt()


                        layout(constraints.maxWidth, constraints.maxHeight) {
                            // Place top circle
                            placeables[0].placeRelative(
                                x = (offsetX + (collageWidth - itemSize) / 2f).toInt(),
                                y = offsetY
                            )
                            // Place bottom-left circle
                            placeables[1].placeRelative(
                                x = offsetX,
                                y = (offsetY + h).toInt()
                            )
                            // Place bottom-right circle
                            placeables[2].placeRelative(
                                x = (offsetX + L).toInt(),
                                y = (offsetY + h).toInt()
                            )
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            SmartImage(
                                model = songs[0].albumArtUriString,
                                contentDescription = songs[0].title,
                                contentScale = ContentScale.Crop,
                                targetSize = Size(128, 128),
                                modifier = imageModifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                            )
                            SmartImage(
                                model = songs[1].albumArtUriString,
                                contentDescription = songs[1].title,
                                contentScale = ContentScale.Crop,
                                targetSize = Size(128, 128),
                                modifier = imageModifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            SmartImage(
                                model = songs[2].albumArtUriString,
                                contentDescription = songs[2].title,
                                contentScale = ContentScale.Crop,
                                targetSize = Size(128, 128),
                                modifier = imageModifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                            )
                            SmartImage(
                                model = songs[3].albumArtUriString,
                                contentDescription = songs[3].title,
                                contentScale = ContentScale.Crop,
                                targetSize = Size(128, 128),
                                modifier = imageModifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
