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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            val imageModifier = Modifier

            when (songs.size) {
                1 -> {
                    SmartImage(
                        model = songs[0].albumArtUriString ?: R.drawable.rounded_album_24,
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
                            model = songs[0].albumArtUriString ?: R.drawable.rounded_album_24,
                            contentDescription = songs[0].title,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(128, 128),
                            modifier = imageModifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                        )
                        SmartImage(
                            model = songs[1].albumArtUriString ?: R.drawable.rounded_album_24,
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
                                    model = song.albumArtUriString ?: R.drawable.rounded_album_24,
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
                        val itemSize = (constraints.maxWidth / 2) - separation.toInt()
                        val placeables = measurables.map {
                            it.measure(
                                constraints.copy(
                                    minWidth = itemSize,
                                    maxWidth = itemSize,
                                    minHeight = itemSize,
                                    maxHeight = itemSize,
                                )
                            )
                        }

                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val h = (sqrt(3.0) / 2 * itemSize).toInt()
                            val topY = (constraints.maxHeight - h - itemSize / 2) / 2
                            val topX = (constraints.maxWidth - itemSize) / 2
                            placeables[0].placeRelative(topX, topY)

                            val bottomY = topY + h
                            val bottomLeftX = (constraints.maxWidth / 2) - itemSize - (separation / 2).toInt()
                            placeables[1].placeRelative(bottomLeftX, bottomY)

                            val bottomRightX = (constraints.maxWidth / 2) + (separation / 2).toInt()
                            placeables[2].placeRelative(bottomRightX, bottomY)
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
                                model = songs[0].albumArtUriString
                                    ?: R.drawable.rounded_album_24,
                                contentDescription = songs[0].title,
                                contentScale = ContentScale.Crop,
                                targetSize = Size(128, 128),
                                modifier = imageModifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                            )
                            SmartImage(
                                model = songs[1].albumArtUriString
                                    ?: R.drawable.rounded_album_24,
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
                                model = songs[2].albumArtUriString
                                    ?: R.drawable.rounded_album_24,
                                contentDescription = songs[2].title,
                                contentScale = ContentScale.Crop,
                                targetSize = Size(128, 128),
                                modifier = imageModifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                            )
                            SmartImage(
                                model = songs[3].albumArtUriString
                                    ?: R.drawable.rounded_album_24,
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
