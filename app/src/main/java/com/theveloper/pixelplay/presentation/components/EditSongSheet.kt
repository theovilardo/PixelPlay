package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSongSheet(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, genre: String, lyrics: String) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var genre by remember { mutableStateOf(song.genre ?: "") }
    var lyrics by remember { mutableStateOf(song.lyrics ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = WindowInsets.ime.add(WindowInsets(bottom = 16.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Edit Song", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = album,
                onValueChange = { album = it },
                label = { Text("Album") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = genre,
                onValueChange = { genre = it },
                label = { Text("Genre") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lyrics,
                onValueChange = { lyrics = it },
                label = { Text("Lyrics") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { onSave(title, artist, album, genre, lyrics) }) {
                    Text("Save")
                }
            }
        }
    }
}
