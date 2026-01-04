package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.LibraryActionRow
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistBottomSheet(
    playlistUiState: PlaylistUiState,
    song: Song,
    onDismiss: () -> Unit,
    bottomBarHeight: Dp,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    currentPlaylistId: String? = null
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    var searchQuery by remember { mutableStateOf("") }
    val filteredPlaylists = remember(searchQuery, playlistUiState.playlists) {
        if (searchQuery.isBlank()) playlistUiState.playlists
        else playlistUiState.playlists.filter { it.name.contains(searchQuery, true) }
    }

    val selectedPlaylists = remember {
        mutableStateMapOf<String, Boolean>().apply {
            filteredPlaylists.forEach {
                put(
                    it.id,
                    it.songIds.contains(song.id)
                )
            }
        }
    }


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.windowInsets } // Manejo de insets como el teclado
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Select Playlists",
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
                    onValueChange = { searchQuery = it },
                    label = { Text("Search for playlists...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = CircleShape,
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) IconButton(onClick = {
                            searchQuery = ""
                        }) { Icon(Icons.Filled.Clear, null) }
                    }
                )




                LibraryActionRow(
                    modifier = Modifier.padding(
                        top = 10.dp,
                        start = 10.dp,
                        end = 10.dp
                    ),
                    //currentPage = pagerState.currentPage,
                    onMainActionClick = {
                        showCreatePlaylistDialog = true
                    },
                    iconRotation = 0f,
                    showSortButton = false,
                    showGenerateButton = false,
                    onSortClick = { },
                    isPlaylistTab = true,
                    isFoldersTab = false,
                    onGenerateWithAiClick = { },
                    currentFolder = null,
                    onFolderClick = { },
                    onNavigateBack = { }
                )

                Spacer(modifier = Modifier.height(8.dp))

                PlaylistContainer(
                    playlistUiState = playlistUiState,
                    isRefreshing = false,
                    onRefresh = { },
                    bottomBarHeight = bottomBarHeight,
                    navController = null,
                    playerViewModel = playerViewModel,
                    isAddingToPlaylist = true,
                    currentSong = song,
                    filteredPlaylists = filteredPlaylists,
                    selectedPlaylists = selectedPlaylists
                )

                if (showCreatePlaylistDialog) {
                    CreatePlaylistDialogRedesigned(
                        onDismiss = { showCreatePlaylistDialog = false },
                        onCreate = { name ->
                            playlistViewModel.createPlaylist(name) // Pass the actual name
                            showCreatePlaylistDialog = false
                        },
                        onGenerateClick = {
                            showCreatePlaylistDialog = false
                            playerViewModel.showAiPlaylistSheet()
                        }
                    )
                }
            }

            MediumExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 18.dp, end = 8.dp),
                shape = CircleShape,
                onClick = {
                    playlistViewModel.addOrRemoveSongFromPlaylists(
                        song.id,
                        selectedPlaylists.filter { it.value }.keys.toList(),
                        currentPlaylistId
                    )
                    onDismiss()
                    playerViewModel.sendToast("Saved")
                },
                icon = { Icon(Icons.Rounded.Save, "Save") },
                text = { Text("Save") },
            )
        }
    }
}