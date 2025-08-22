package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song

@Composable
fun PlaylistArtCollage(
    songs: List<Song>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (songs.isEmpty()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Playlist",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            val imageModifier = Modifier
                .clip(CircleShape)
                .background(Color.Gray)

            when (songs.size) {
                1 -> {
                    SmartImage(
                        model = songs[0].albumArtUriString ?: R.drawable.rounded_album_24,
                        contentDescription = songs[0].title,
                        contentScale = ContentScale.Crop,
                        targetSize = Size(256, 256),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SmartImage(
                            model = songs[0].albumArtUriString ?: R.drawable.rounded_album_24,
                            contentDescription = songs[0].title,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(128, 128),
                            modifier = imageModifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        SmartImage(
                            model = songs[1].albumArtUriString ?: R.drawable.rounded_album_24,
                            contentDescription = songs[1].title,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(128, 128),
                            modifier = imageModifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
                3 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SmartImage(
                            model = songs[0].albumArtUriString ?: R.drawable.rounded_album_24,
                            contentDescription = songs[0].title,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(128, 128),
                            modifier = imageModifier
                                .size(32.dp)
                                .align(Alignment.TopCenter)
                                .offset(y = 4.dp)
                        )
                        SmartImage(
                            model = songs[1].albumArtUriString ?: R.drawable.rounded_album_24,
                            contentDescription = songs[1].title,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(128, 128),
                            modifier = imageModifier
                                .size(32.dp)
                                .align(Alignment.BottomStart)
                                .offset(x = 8.dp, y = (-8).dp)
                        )
                        SmartImage(
                            model = songs[2].albumArtUriString ?: R.drawable.rounded_album_24,
                            contentDescription = songs[2].title,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(128, 128),
                            modifier = imageModifier
                                .size(32.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-8).dp, y = (-8).dp)
                        )
                    }
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
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
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
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
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
