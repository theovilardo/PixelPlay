package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.DailyMixHeader
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMixScreen(
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    paddingValuesParent: PaddingValues // Padding from MainLayout (for bottom nav bar)
) {
    val songs = playerViewModel.allSongsFlow.collectAsState() // uso todas pero deberia tener su propio mix
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    // Observe current song to adjust bottom padding for mini player
    val currentSong by playerViewModel.stablePlayerState
        .map { it.currentSong }
        .collectAsState(initial = null)

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    val bottomPaddingForMiniPlayer = if (currentSong != null) MiniPlayerHeight else 0.dp

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Daily Mix",
                        // Material 3 LargeTopAppBar handles animating the text style (size, position)
                        // as it collapses/expands. This should provide the smooth transition.
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onBackClick()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, // Or your desired color
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant, // Color when collapsed
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPaddingScaffold -> // Padding provided by Scaffold (for TopAppBar)
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = innerPaddingScaffold.calculateTopPadding()), // Apply Scaffold's top padding
            contentPadding = PaddingValues(
                // Horizontal padding can be applied per item or globally here
                start = 16.dp,
                end = 16.dp,
                // Bottom padding considers MainLayout's bottom bar AND the mini player
                bottom = paddingValuesParent.calculateBottomPadding() + bottomPaddingForMiniPlayer + 16.dp // Extra space at the end
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp) // Space between items
        ) {
            item {
                // The DailyMixHeader as defined in your DailyMixCard, showing a few thumbnails
                DailyMixHeader(
                    thumbnails = songs.value.take(3).toImmutableList()
                )
                // Spacer(Modifier.height(16.dp)) // Add space if DailyMixHeader doesn't have bottom margin
            }

            items(songs.value, key = { song -> song.id }) { song ->
                SongListItemFavsWrapper(
                    song = song,
                    playerViewModel = playerViewModel,
                    onClick = {
                        playerViewModel.playSongs(
                            songsToPlay = songs.value,
                            startSong = song,
                            queueName = "DailyMixScreenQueue" // Or a more descriptive queue name
                        )
                    },
                    modifier = Modifier.fillMaxWidth() // SongListItemFavsWrapper might have its own padding
                )
            }
        }
    }
}