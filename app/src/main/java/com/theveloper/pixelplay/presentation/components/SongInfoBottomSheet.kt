package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album Art
            SmartImage(
                uri = song.albumArtUriString,
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                // Assuming SmartImage has a placeholder parameter or handles it internally
                // placeholder = painterResource(id = R.drawable.ic_default_album_art) // Example placeholder
            )

            // Song Title
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Artist and Album Name
            Text(
                text = "${song.artist} • ${song.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FilledTonalButton(onClick = onPlaySong) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    Spacer(Modifier.width(8.dp))
                    Text("Play")
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add to Queue Button
            OutlinedButton(onClick = onAddToQueue) {
                Icon(Icons.Filled.QueueMusic, contentDescription = "Add to Queue")
                Spacer(Modifier.width(8.dp))
                Text("Add to Queue")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Details Section
            // Duration
            ListItem(
                headlineContent = { Text("Duration") },
                supportingContent = { Text(formatDuration(song.duration)) },
                leadingContent = { Icon(Icons.Rounded.Schedule, contentDescription = "Duration") }
            )

            // Genre
            if (!song.genre.isNullOrEmpty()) {
                ListItem(
                    headlineContent = { Text("Genre") },
                    supportingContent = { Text(song.genre!!) },
                    leadingContent = { Icon(Icons.Rounded.MusicNote, contentDescription = "Genre") }
                )
            }

            // Album
            ListItem(
                headlineContent = { Text("Album") },
                supportingContent = { Text(song.album) },
                leadingContent = { Icon(Icons.Rounded.Album, contentDescription = "Album") },
                modifier = Modifier.clickable(onClick = onNavigateToAlbum)
            )

            // Artist
            ListItem(
                headlineContent = { Text("Artist") },
                supportingContent = { Text(song.artist) },
                leadingContent = { Icon(Icons.Rounded.Person, contentDescription = "Artist") },
                modifier = Modifier.clickable(onClick = onNavigateToArtist)
            )
        }
    }
}
