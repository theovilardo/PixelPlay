package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song

@Composable
fun AiMetadataDialog(
    song: Song,
    onDismiss: () -> Unit,
    onGenerate: (List<String>) -> Unit
) {
    val missingFields = remember {
        val fields = mutableListOf<String>()
        if (song.title.isBlank()) fields.add("Title")
        if (song.displayArtist.isBlank()) fields.add("Artist")
        if (song.album.isBlank()) fields.add("Album")
        if (song.genre.isNullOrBlank()) fields.add("Genre")
        fields
    }

    var selectedFields by remember { mutableStateOf(missingFields.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate Metadata with AI") },
        text = {
            Column {
                Text("Select the fields you want to generate:")
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn {
                    items(
                        items = missingFields,
                        key = { it }
                    ) { field ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedFields.contains(field),
                                onCheckedChange = {
                                    selectedFields = if (it) {
                                        selectedFields + field
                                    } else {
                                        selectedFields - field
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(field)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(selectedFields.toList()) },
                enabled = selectedFields.isNotEmpty()
            ) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
