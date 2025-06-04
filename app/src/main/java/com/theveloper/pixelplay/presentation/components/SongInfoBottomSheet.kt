package com.theveloper.pixelplay.presentation.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.AutoSizingTextToFill
import com.theveloper.pixelplay.utils.formatDuration
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongInfoBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onPlaySong: () -> Unit,
    onAddToQueue: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToArtist: () -> Unit
) {
    val context = LocalContext.current
    val listItemShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusBR = 20.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTL = 20.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
//                    .background(
//                        Color.Red
//                    )
                    .align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Album Art
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = "Album Art",
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = 18.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusBR = 18.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTL = 18.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBL = 18.dp,
                        smoothnessAsPercentTR = 60
                    ),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(80.dp),
                        //.clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit,
                    // Assuming SmartImage has a placeholder parameter or handles it internally
                    // placeholder = painterResource(id = R.drawable.ic_default_album_art) // Example placeholder
                )
                // Song Title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterVertically)
                ) {
                    AutoSizingTextToFill(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(end = 8.dp),
                        text = song.title
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MediumExtendedFloatingActionButton(
                    modifier = Modifier.weight(0.5f),
                    onClick = onPlaySong,
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = 26.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusBR = 26.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTL = 26.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBL = 26.dp,
                        smoothnessAsPercentTR = 60
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                    Spacer(Modifier.width(14.dp))
                    Text("Play")
                    Spacer(Modifier.width(8.dp))
                }
                FilledIconButton(
                    modifier = Modifier
                        .weight(0.25f)
                        //.aspectRatio(1f)
                        .size(80.dp),
                    onClick = onToggleFavorite,
                    shape = CircleShape
                ) {
                    Icon(
                        modifier = Modifier
                            .size(
                                FloatingActionButtonDefaults.LargeIconSize
                            ),
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        //tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    modifier = Modifier
                        .weight(0.25f)
                        //.aspectRatio(1f)
                        .size(80.dp),
                    onClick = {
                        Toast.makeText(context, "Share not implemented yet", Toast.LENGTH_SHORT).show()
                    },
                    shape = CircleShape
                ) {
                    Icon(
                        modifier = Modifier
                            .size(
                                FloatingActionButtonDefaults.LargeIconSize
                            ),
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Toggle Favorite",
                        //tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add to Queue Button
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(68.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ),
                shape = CircleShape,
                onClick = onAddToQueue
            ) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add to Queue")
                Spacer(Modifier.width(8.dp))
                Text("Add to Queue")
            }

            HorizontalDivider(
                thickness = 3.dp,
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 10.dp)
                    .clip(shape = CircleShape)
            )

            // Details Section

            FlowRow {
                // Duration
                ListItem(
                    modifier = Modifier.clip(
                        shape = listItemShape
                    ),
                    headlineContent = { Text("Duration") },
                    supportingContent = { Text(formatDuration(song.duration)) },
                    leadingContent = { Icon(Icons.Rounded.Schedule, contentDescription = "Duration") }
                )

                // Genre
                if (!song.genre.isNullOrEmpty()) {
                    ListItem(
                        modifier = Modifier.clip(
                            shape = listItemShape
                        ),
                        headlineContent = { Text("Genre") },
                        supportingContent = { Text(song.genre) },
                        leadingContent = { Icon(Icons.Rounded.MusicNote, contentDescription = "Genre") }
                    )
                }

                // Album
                ListItem(
                    modifier = Modifier
                        .clip(
                            shape = listItemShape
                        )
                        .clickable(onClick = onNavigateToAlbum),
                    headlineContent = { Text("Album") },
                    supportingContent = { Text(song.album) },
                    leadingContent = { Icon(Icons.Rounded.Album, contentDescription = "Album") }
                )

                // Artist
                ListItem(
                    modifier = Modifier
                        .clip(
                            shape = listItemShape
                        )
                        .clickable(onClick = onNavigateToArtist),
                    headlineContent = { Text("Artist") },
                    supportingContent = { Text(song.artist) },
                    leadingContent = { Icon(Icons.Rounded.Person, contentDescription = "Artist") },
                )
            }
        }
    }
}