package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import java.net.URLEncoder
import timber.log.Timber
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSongSheet(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, genre: String, lyrics: String) -> Unit,
    generateAiMetadata: suspend (List<String>) -> Result<com.theveloper.pixelplay.data.ai.SongMetadata>
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var genre by remember { mutableStateOf(song.genre ?: "") }
    var lyrics by remember { mutableStateOf(song.lyrics ?: "") }

    var showInfoDialog by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(song) {
        title = song.title
        artist = song.artist
        album = song.album
        genre = song.genre ?: ""
        lyrics = song.lyrics ?: ""
    }

    if (isGenerating) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Generating Metadata") },
            text = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {}
        )
    }

    if (showAiDialog) {
        AiMetadataDialog(
            song = song,
            onDismiss = { showAiDialog = false },
            onGenerate = { fields ->
                scope.launch {
                    isGenerating = true
                    val result = generateAiMetadata(fields)
                    result.onSuccess { metadata ->
                        Timber.d("AI metadata generated successfully: $metadata")
                        title = metadata.title ?: title
                        artist = metadata.artist ?: artist
                        album = metadata.album ?: album
                        genre = metadata.genre?.split(",")?.firstOrNull()?.trim() ?: genre
                        lyrics = metadata.lyrics ?: lyrics
                    }.onFailure { error ->
                        Timber.e(error, "Failed to generate AI metadata")
                    }
                    isGenerating = false
                }
                showAiDialog = false
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true } // Permite cerrar el BottomSheet
    )

    // Definición de colores para los TextFields
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    // Definición de la forma para los TextFields
    val textFieldShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 10.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusTR = 10.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusBL = 10.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusBR = 10.dp,
        smoothnessAsPercentTR = 60
    )

    // --- Diálogo de Información ---
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = "Information Icon") },
            title = { Text("Editing Song Metadata") },
            text = { Text("Editing a song's metadata can affect how it's displayed and organized in your library. Changes are permanent and may not be reversible.") },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets.ime.add(WindowInsets(bottom = 16.dp)) }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Cabecera con Título y Botón de Información ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Edit Song",
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.displaySmall
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
//                    Box(
//                        modifier = Modifier
//                            .size(40.dp)
//                            .clip(CircleShape)
//                            .background(
//                                brush = Brush.horizontalGradient(
//                                    colors = listOf(
//                                        MaterialTheme.colorScheme.primary,
//                                        MaterialTheme.colorScheme.secondary,
//                                        MaterialTheme.colorScheme.tertiary
//                                    )
//                                )
//                            )
//                    ) {
//                        IconButton(onClick = { showAiDialog = true }) {
//                            Icon(
//                                modifier = Modifier
//                                    .size(20.dp),
//                                painter = painterResource(id = R.drawable.gemini_ai),
//                                contentDescription = "Use Gemini AI",
//                                tint = MaterialTheme.colorScheme.onPrimary
//                            )
//                        }
//                    }
                    FilledTonalIconButton(
                        onClick = { showInfoDialog = true },
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = "Show info dialog")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- Campo de Título ---
            OutlinedTextField(
                value = title,
                shape = textFieldShape,
                colors = textFieldColors,
                onValueChange = { title = it },
                placeholder = { Text("Title") },
                leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = "Title Icon") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- Campo de Artista ---
            OutlinedTextField(
                value = artist,
                colors = textFieldColors,
                shape = textFieldShape,
                onValueChange = { artist = it },
                placeholder = { Text("Artist") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = "Artist Icon") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- Campo de Álbum ---
            OutlinedTextField(
                value = album,
                colors = textFieldColors,
                shape = textFieldShape,
                onValueChange = { album = it },
                placeholder = { Text("Album") },
                leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = "Album Icon") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- Campo de Género ---
            OutlinedTextField(
                value = genre,
                colors = textFieldColors,
                shape = textFieldShape,
                onValueChange = { genre = it },
                placeholder = { Text("Genre") },
                leadingIcon = { Icon(Icons.Rounded.Category, contentDescription = "Genre Icon") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- Campo de Letra ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = lyrics,
                    colors = textFieldColors,
                    shape = textFieldShape,
                    onValueChange = { lyrics = it },
                    placeholder = { Text("Lyrics") },
                    leadingIcon = { Icon(Icons.Rounded.Notes, contentDescription = "Lyrics Icon") },
                    modifier = Modifier
                        .weight(1f)
                        .height(150.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = {
                        val encodedTitle = URLEncoder.encode(title, "UTF-8")
                        val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                        val url = "https://lrclib.net/search/$encodedTitle%20$encodedArtist"
                        val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
                        context.startActivity(intent)
                    },
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_search_24),
                        contentDescription = "Search lyrics on lrclib.net"
                    )
                }
            }

            // --- Botones de acción ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(title, artist, album, genre, lyrics) },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
