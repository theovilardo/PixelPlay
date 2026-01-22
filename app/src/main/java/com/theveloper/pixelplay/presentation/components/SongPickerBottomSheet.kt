package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.filter
import androidx.paging.LoadState
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPickerBottomSheet(
    songs: Flow<PagingData<Song>>,
    initiallySelectedSongIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedSongIds = remember {
        mutableStateMapOf<String, Boolean>().apply {
            initiallySelectedSongIds.forEach { put(it, true) }
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredPagingFlow = remember(searchQuery, songs) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.map { pagingData ->
                pagingData.filter { song ->
                    song.title.contains(searchQuery, true) ||
                        song.artist.contains(searchQuery, true)
                }
            }
        }
    }
    val paginatedSongs = filteredPagingFlow.collectAsLazyPagingItems()

    val animatedAlbumCornerRadius = 60.dp

    val albumShape = remember(animatedAlbumCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedAlbumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedAlbumCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedAlbumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedAlbumCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        SongPickerContent(
            paginatedSongs = paginatedSongs,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            selectedSongIds = selectedSongIds,
            albumShape = albumShape,
            onConfirm = onConfirm
        )
    }
}

@Composable
fun SongPickerContent(
    paginatedSongs: LazyPagingItems<Song>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedSongIds: MutableMap<String, Boolean>,
    albumShape: androidx.compose.ui.graphics.Shape,
    onConfirm: (Set<String>) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Add Songs",
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = GoogleSansRounded
                        )
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            unfocusedTrailingIconColor = Color.Transparent,
                            focusedSupportingTextColor = Color.Transparent,
                        ),
                        onValueChange = onSearchQueryChange,
                        label = { Text("Search for songs...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = CircleShape,
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = {
                                onSearchQueryChange("")
                            }) { Icon(Icons.Filled.Clear, null) }
                        }
                    )
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = 18.dp, end = 8.dp),
                    shape = CircleShape,
                    onClick = { onConfirm(selectedSongIds.filterValues { it }.keys) },
                    icon = { Icon(Icons.Rounded.Check, "Añadir canciones") },
                    text = { Text("Add") },
                )
            }
        ) { innerPadding ->
            SongPickerList(
                paginatedSongs = paginatedSongs,
                searchQuery = searchQuery,
                selectedSongIds = selectedSongIds,
                albumShape = albumShape,
                modifier = Modifier.padding(innerPadding)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {

        }
    }
}

@Composable
fun SongPickerList(
    paginatedSongs: LazyPagingItems<Song>,
    searchQuery: String,
    selectedSongIds: MutableMap<String, Boolean>,
    albumShape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp, top = 20.dp)
) {
    val isInitialLoading =
        paginatedSongs.loadState.refresh is LoadState.Loading && paginatedSongs.itemCount == 0
    if (isInitialLoading) {
        Box(
            modifier
                .fillMaxSize(), Alignment.Center
        ) { CircularProgressIndicator() }
    } else if (paginatedSongs.itemCount == 0 && paginatedSongs.loadState.refresh is LoadState.NotLoading) {
        Box(
            modifier
                .fillMaxSize(), Alignment.Center
        ) {
            Text(
                text = if (searchQuery.isBlank()) "No songs found." else "No songs match \"$searchQuery\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .padding(horizontal = 14.dp),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = paginatedSongs.itemCount,
                key = { index ->
                    paginatedSongs.peek(index)?.id ?: "song_placeholder_$index"
                }
            ) { index ->
                val song = paginatedSongs[index]
                if (song != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable {
                                val currentSelection = selectedSongIds[song.id] ?: false
                                selectedSongIds[song.id] = !currentSelection
                            }
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selectedSongIds[song.id] ?: false,
                            onCheckedChange = { isChecked ->
                                selectedSongIds[song.id] = isChecked
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = song.title,
                                shape = albumShape,
                                targetSize = Size(
                                    168,
                                    168
                                ), // 56dp * 3 (para densidad xxhdpi)
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                song.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = false, onCheckedChange = null)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(" ", maxLines = 1)
                            Text(" ", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (paginatedSongs.loadState.append is LoadState.Loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
