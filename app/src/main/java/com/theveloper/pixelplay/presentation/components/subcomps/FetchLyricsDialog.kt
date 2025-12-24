package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DoneOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.utils.ProviderText
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape

@Composable
fun FetchLyricsDialog(
    uiState: LyricsSearchUiState,
    currentSong: Song?,
    onConfirm: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit
) {
    if (uiState is LyricsSearchUiState.Success) return

    var forcePickResults by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(32.dp), // Forma muy redondeada (Expressive)
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (uiState) {
                    LyricsSearchUiState.Idle -> {
                        IdleContent(
                            currentSong = currentSong,
                            forcePickResults = forcePickResults,
                            onToggleForcePickResults = { forcePickResults = it },
                            onSearch = { onConfirm(forcePickResults) },
                            onImport = onImport,
                            onCancel = onDismiss
                        )
                    }
                    LyricsSearchUiState.Loading -> {
                        LoadingContent()
                    }
                    is LyricsSearchUiState.PickResult -> {
                        PickResultContent(
                            results = uiState.results,
                            onPickResult = onPickResult,
                            onCancel = onDismiss // Usamos botón cancelar en lugar de X
                        )
                    }
                    is LyricsSearchUiState.Error -> {
                        ErrorContent(
                            message = uiState.message,
                            onDismiss = onDismiss
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

// --------------------------------------------------------------------------
// Sub-componentes de Estados (Internal)
// --------------------------------------------------------------------------

@Composable
private fun IdleContent(
    currentSong: Song?,
    forcePickResults: Boolean,
    onToggleForcePickResults: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit
) {
    // Icono Decorativo Grande
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedStarShape(
                sides = 8,
                curve = 0.1,
                rotation = 0f,
                //iterations = 45
            )) // Forma "Squircle" agradable
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Título y Canción
    if (currentSong != null) {
        Text(
            text = currentSong.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = currentSong.displayArtist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            text = stringResource(R.string.lyrics_not_found),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.search_lyrics_online_prompt),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(32.dp))

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fetch_lyrics_show_options_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.fetch_lyrics_show_options_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
            Switch(
                checked = forcePickResults,
                onCheckedChange = onToggleForcePickResults,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Botones de Acción (Vertical para mejor touch target)
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.search))
        }

        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            )
        ) {
            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.import_file))
        }

        // Botón Cancelar (Reemplaza a la X)
        TextButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 4.dp,
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.searching_lyrics),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PickResultContent(
    results: List<LyricsSearchResult>,
    onPickResult: (LyricsSearchResult) -> Unit,
    onCancel: () -> Unit
) {
    Text(
        text = stringResource(R.string.found_n_matches_format).format(results.size),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Lista Scrollable Optimizada
    LazyColumn(
        modifier = Modifier.heightIn(max = 350.dp), // Altura máxima dinámica
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(results) { result ->
            ResultItemCard(result = result, onClick = { onPickResult(result) })
        }

        item {
            ProviderText(
                providerText = stringResource(R.string.lyrics_provided_by),
                uri = stringResource(R.string.lrclib_uri),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Botón Cancelar al final de la lista
    TextButton(
        onClick = onCancel,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
    ) {
        Text(stringResource(R.string.cancel))
    }
}

@Composable
private fun ResultItemCard(
    result: LyricsSearchResult,
    onClick: () -> Unit
) {
    val hasSyncedLyrics = !result.record.syncedLyrics.isNullOrEmpty()
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasSyncedLyrics) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasSyncedLyrics) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.record.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasSyncedLyrics) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = "SYNCED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${result.record.artistName} • ${result.record.albumName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = stringResource(R.string.error),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.error
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(stringResource(R.string.ok))
    }
}

///**
// * Diálogo que gestiona la búsqueda de letras para una canción.
// * Muestra diferentes contenidos según el estado de la búsqueda.
// */
//@Composable
//fun FetchLyricsDialog(
//    uiState: LyricsSearchUiState,
//    currentSong: Song?,
//    onConfirm: () -> Unit,
//    onPickResult: (LyricsSearchResult) -> Unit,
//    onDismiss: () -> Unit,
//    onImport: () -> Unit
//) {
//    if (uiState is LyricsSearchUiState.Success) {
//        // Do nothing and don't show the dialog if the state is success
//        return
//    }
//
//    Dialog(onDismissRequest = onDismiss) {
//        Surface(
//            shape = RoundedCornerShape(28.dp),
//            color = MaterialTheme.colorScheme.surfaceContainerHigh,
//            contentColor = MaterialTheme.colorScheme.onSurface
//        ) {
//            Box {
//                when (uiState) {
//                    LyricsSearchUiState.Idle -> {
//                        Column(
//                            modifier = Modifier.padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            DialogHeader(currentSong = currentSong)
//                            Spacer(modifier = Modifier.height(8.dp))
//                            Text(
//                                text = stringResource(R.string.lyrics_not_found),
//                                style = MaterialTheme.typography.headlineSmall,
//                                textAlign = TextAlign.Center
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = stringResource(R.string.search_lyrics_online_prompt),
//                                style = MaterialTheme.typography.bodyMedium,
//                                textAlign = TextAlign.Center,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                            Spacer(modifier = Modifier.height(24.dp))
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.spacedBy(8.dp)
//                            ) {
//                                OutlinedButton(
//                                    onClick = onImport,
//                                    modifier = Modifier.weight(1f)
//                                ) {
//                                    Icon(painter = painterResource(R.drawable.rounded_upload_file_24), contentDescription = null, modifier = Modifier.size(18.dp))
//                                    Spacer(modifier = Modifier.width(8.dp))
//                                    Text(stringResource(R.string.import_file))
//                                }
//                                Button(
//                                    onClick = onConfirm,
//                                    modifier = Modifier.weight(1f)
//                                ) {
//                                    Icon(painter = painterResource(R.drawable.rounded_manage_search_24), contentDescription = null, modifier = Modifier.size(18.dp))
//                                    Spacer(modifier = Modifier.width(8.dp))
//                                    Text(stringResource(R.string.search))
//                                }
//                            }
//                        }
//                    }
//                    LyricsSearchUiState.Loading -> {
//                        Column(
//                            modifier = Modifier
//                                .padding(horizontal = 24.dp, vertical = 48.dp)
//                                .fillMaxWidth(),
//                            horizontalAlignment = Alignment.CenterHorizontally,
//                            verticalArrangement = Arrangement.Center
//                        ) {
//                            CircularProgressIndicator()
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = stringResource(R.string.searching_lyrics),
//                                style = MaterialTheme.typography.bodyMedium,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                        }
//                    }
//                    is LyricsSearchUiState.PickResult -> {
//                        Column(
//                            modifier = Modifier.padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            DialogHeader(currentSong = currentSong)
//                            Spacer(modifier = Modifier.height(16.dp))
//                            ResultContextChip(query = uiState.query)
//                            Spacer(modifier = Modifier.height(10.dp))
//                            Text(
//                                text = stringResource(R.string.found_n_matches_format).format(uiState.results.size),
//                                style = MaterialTheme.typography.headlineSmall,
//                                textAlign = TextAlign.Center
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//
//                            val scrollState = rememberScrollState()
//                            Column(
//                                modifier = Modifier.verticalScroll(scrollState),
//                                horizontalAlignment = Alignment.CenterHorizontally
//                            ) {
//                                uiState.results.forEach { result ->
//                                    if (result != uiState.results.first()) {
//                                        Spacer(modifier = Modifier.height(12.dp))
//                                    }
//
//                                    OutlinedButton (
//                                        modifier = Modifier.fillMaxWidth(),
//                                        shape = RoundedCornerShape(12.dp),
//                                        contentPadding = PaddingValues(12.dp),
//                                        onClick = { onPickResult(result) },
//                                    ) {
//                                        Column(
//                                            modifier = Modifier.fillMaxWidth(),
//                                            horizontalAlignment = Alignment.Start
//                                        ) {
//                                            Text(
//                                                text = result.record.name,
//                                                style = MaterialTheme.typography.bodyMedium,
//                                                fontWeight = FontWeight.Bold,
//                                                color = MaterialTheme.colorScheme.onSurface
//                                            )
//                                            Spacer(modifier = Modifier.height(4.dp))
//                                            Text(
//                                                text = "${result.record.artistName} - ${result.record.albumName}",
//                                                style = MaterialTheme.typography.bodyMedium,
//                                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                                            )
//                                        }
//                                    }
//                                }
//                            }
//
//                            ProviderText(
//                                providerText = stringResource(R.string.lyrics_provided_by),
//                                uri = stringResource(R.string.lrclib_uri),
//                                textAlign = TextAlign.Center,
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .padding(vertical = 16.dp)
//                            )
//                        }
//                    }
//                    is LyricsSearchUiState.Error -> {
//                        Column(
//                            modifier = Modifier.padding(24.dp),
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
//                            Icon(
//                                imageVector = Icons.Rounded.ErrorOutline,
//                                contentDescription = null,
//                                modifier = Modifier.size(28.dp),
//                                tint = MaterialTheme.colorScheme.error
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = stringResource(R.string.error),
//                                style = MaterialTheme.typography.headlineSmall
//                            )
//                            Spacer(modifier = Modifier.height(16.dp))
//                            Text(
//                                text = uiState.message,
//                                style = MaterialTheme.typography.bodyMedium,
//                                textAlign = TextAlign.Center,
//                                color = MaterialTheme.colorScheme.onSurfaceVariant
//                            )
//                            if (uiState.query != null) {
//                                Spacer(modifier = Modifier.height(16.dp))
//                                Text(
//                                    text = stringResource(R.string.searched_for_x_format).format(uiState.query),
//                                    style = MaterialTheme.typography.bodyMedium,
//                                    textAlign = TextAlign.Center,
//                                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                                )
//                            }
//                            Spacer(modifier = Modifier.height(24.dp))
//                            Row(
//                                modifier = Modifier.fillMaxWidth(),
//                                horizontalArrangement = Arrangement.End
//                            ) {
//                                TextButton(onClick = onDismiss) {
//                                    Text(stringResource(R.string.ok))
//                                }
//                            }
//                        }
//                    }
//                    is LyricsSearchUiState.Success -> {
//                        // Handled outside, this case should not render the dialog
//                    }
//                }
//
//                // Close button for Idle, PickResult and Error states
//                if (uiState is LyricsSearchUiState.Idle
//                    || uiState is LyricsSearchUiState.PickResult
//                    || uiState is LyricsSearchUiState.Error) {
//                     IconButton(
//                         onClick = onDismiss,
//                         modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
//                     ) {
//                         Icon(
//                             imageVector = Icons.Rounded.Close,
//                             contentDescription = stringResource(R.string.cancel)
//                         )
//                     }
//                }
//            }
//        }
//    }
//}
//
//@Composable
//private fun DialogHeader(currentSong: Song?) {
//    val title = currentSong?.title.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.unknown_song_title)
//    val artist = currentSong?.displayArtist.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.unknown_artist)
//    val album = currentSong?.album.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.unknown_album)
//
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .clip(RoundedCornerShape(18.dp))
//            .padding(horizontal = 4.dp, vertical = 2.dp)
//            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Box(
//            modifier = Modifier
//                .size(48.dp)
//                .clip(RoundedCornerShape(16.dp)),
//            contentAlignment = Alignment.Center
//        ) {
//            Icon(
//                imageVector = Icons.Rounded.MusicNote,
//                contentDescription = null,
//                modifier = Modifier.size(24.dp),
//                tint = MaterialTheme.colorScheme.onSecondaryContainer
//            )
//        }
//        Spacer(modifier = Modifier.width(12.dp))
//        Column(
//            modifier = Modifier.weight(1f)
//        ) {
//            Text(
//                text = title,
//                style = MaterialTheme.typography.titleMedium,
//                color = MaterialTheme.colorScheme.onSurface
//            )
//            Spacer(modifier = Modifier.height(2.dp))
//            Text(
//                text = artist,
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//            Spacer(modifier = Modifier.height(2.dp))
//            Text(
//                text = album,
//                style = MaterialTheme.typography.bodySmall,
//                color = MaterialTheme.colorScheme.onSurfaceVariant
//            )
//        }
//    }
//}
//
//@Composable
//private fun ResultContextChip(query: String) {
//    SuggestionChip(
//        onClick = {},
//        label = { Text(text = query) },
//        icon = {
//            Icon(
//                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
//                contentDescription = null
//            )
//        }
//    )
//}
