package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import android.util.Log
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
// import androidx.compose.runtime.derivedStateOf // Already imported
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.navigation.Screen // Required for Screen.GenreDetail.createRoute
import com.theveloper.pixelplay.presentation.screens.search.components.GenreCategoriesGrid
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = NavBarContentHeight + systemNavBarInset
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val uiState by playerViewModel.playerUiState.collectAsState()
    val currentFilter by remember { derivedStateOf { uiState.selectedSearchFilter } }
    val searchHistory = uiState.searchHistory
    val genres by playerViewModel.genres.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var selectedSongForInfo by remember { mutableStateOf<Song?>(null) }

    // Perform search whenever searchQuery, active state, or filter changes
    LaunchedEffect(searchQuery, active, currentFilter) {
        if (searchQuery.isNotBlank()) {
            playerViewModel.performSearch(searchQuery)
        } else if (active) {
            playerViewModel.performSearch("")
        }
    }
    val searchResults = uiState.searchResults
    val handleSongMoreOptionsClick: (Song) -> Unit = { song ->
        selectedSongForInfo = song
        playerViewModel.selectSongForInfo(song)
        showSongInfoBottomSheet = true
    }

    val searchbarHorizontalPadding by animateDpAsState(
        targetValue = if (!active) 24.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium), // Ajusta la animación si es necesario
        label = "searchbarHorizontalPadding"
    )

    val searchbarCornerRadius = 28.dp

    val dm = LocalPixelPlayDarkTheme.current

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }

    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(active) {
        onSearchBarActiveChange(active)
    }

    DisposableEffect(Unit) {
        onDispose { onSearchBarActiveChange(false) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    gradientBrush
                )
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // CORREGIDO: Agregamos un padding mínimo para evitar crashes
            val safePadding = maxOf(0.dp, searchbarHorizontalPadding)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = safePadding) // Usar padding seguro
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            playerViewModel.onSearchQuerySubmitted(searchQuery)
                        }
                        active = false
                    },
                    active = active,
                    onActiveChange = {
                        if (!it) {
                            if (searchQuery.isNotBlank()) {
                                playerViewModel.onSearchQuerySubmitted(searchQuery)
                            }
                        }
                        active = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clip(RoundedCornerShape(searchbarCornerRadius)),
                    placeholder = {
                        Text(
                            "Search...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(end = 10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Limpiar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        inputFieldColors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    ),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Filter chips
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                //verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                                SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel)
                                SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel)
                                SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel)
                                SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel)
                            }

                            if (searchQuery.isBlank() && active && searchHistory.isNotEmpty()) {
                                val rememberedOnHistoryClick: (String) -> Unit = remember(playerViewModel) {
                                    { query -> searchQuery = query }
                                }
                                val rememberedOnHistoryDelete: (String) -> Unit = remember(playerViewModel) {
                                    { query -> playerViewModel.deleteSearchHistoryItem(query) }
                                }
                                val rememberedOnClearAllHistory: () -> Unit = remember(playerViewModel) {
                                    { playerViewModel.clearSearchHistory() }
                                }

                                SearchHistoryList(
                                    historyItems = searchHistory,
                                    onHistoryClick = rememberedOnHistoryClick,
                                    onHistoryDelete = rememberedOnHistoryDelete,
                                    onClearAllHistory = rememberedOnClearAllHistory
                                )
                            } else if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
                                EmptySearchResults(
                                    searchQuery = searchQuery,
                                    colorScheme = colorScheme
                                )
                            } else if (searchResults.isNotEmpty()) {
                                val rememberedOnItemSelected = remember(searchQuery, playerViewModel) {
                                    {
                                        if (searchQuery.isNotBlank()) {
                                            playerViewModel.onSearchQuerySubmitted(searchQuery)
                                        }
                                        active = false
                                    }
                                }
                                SearchResultsList(
                                    results = searchResults,
                                    playerViewModel = playerViewModel,
                                    onItemSelected = rememberedOnItemSelected,
                                    currentPlayingSongId = stablePlayerState.currentSong?.id,
                                    isPlaying = stablePlayerState.isPlaying,
                                    onSongMoreOptionsClick = handleSongMoreOptionsClick,
                                    navController = navController
                                )
                            } else if (searchQuery.isBlank() && active && searchHistory.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No recent searches", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                )
            }

            // Content to show when SearchBar is not active
            if (!active) {
                if (searchQuery.isBlank()) {
                    Box {
                        GenreCategoriesGrid(
                            genres = genres,
                            onGenreClick = { genre ->
                                Timber.tag("SearchScreen")
                                    .d("Genre clicked: ${genre.name} (ID: ${genre.id})")
                                val encodedGenreId = java.net.URLEncoder.encode(genre.id, "UTF-8")
                                navController.navigate(Screen.GenreDetail.createRoute(encodedGenreId))
                            },
                            playerViewModel = playerViewModel,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .height(80.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(
                                                0.5f
                                            ),
                                            MaterialTheme.colorScheme.surfaceContainerLowest
                                        )
                                    )
                                )
                        ) {

                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.SONGS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.ALBUMS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.ARTISTS, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.PLAYLISTS, currentFilter, playerViewModel)
                        }
                        SearchResultsList(
                            results = searchResults,
                            playerViewModel = playerViewModel,
                            onItemSelected = { },
                            currentPlayingSongId = stablePlayerState.currentSong?.id,
                            isPlaying = stablePlayerState.isPlaying,
                            onSongMoreOptionsClick = handleSongMoreOptionsClick,
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteSongIds) {
            derivedStateOf {
                currentSong?.let { favoriteSongIds.contains(it.id) }
            }
        }.value ?: false
        val removeFromListTrigger = remember(currentSong) {
            {
                searchQuery = "$searchQuery "
            }
        }

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentSong)
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigate(Screen.AlbumDetail.createRoute(currentSong.albumId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigate(Screen.ArtistDetail.createRoute(currentSong.artistId))
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                },
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsState()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    song = currentSong,
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SearchHistoryList(
    historyItems: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent Searches",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text("Clear All")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
            )
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = "History Icon",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = "Delete history item",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search, // More generic icon
            contentDescription = "No results",
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank()) "No results for \"$searchQuery\"" else "Nothing found",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Try a different search term or check your filters.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: List<SearchResultItem>,
    playerViewModel: PlayerViewModel,
    onItemSelected: () -> Unit,
    currentPlayingSongId: String?,
    isPlaying: Boolean,
    onSongMoreOptionsClick: (Song) -> Unit,
    navController: NavHostController
) {
    val localDensity = LocalDensity.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsState()

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No results found.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = results.groupBy { item ->
        when (item) {
            is SearchResultItem.SongItem -> SearchFilterType.SONGS
            is SearchResultItem.AlbumItem -> SearchFilterType.ALBUMS
            is SearchResultItem.ArtistItem -> SearchFilterType.ARTISTS
            is SearchResultItem.PlaylistItem -> SearchFilterType.PLAYLISTS
        }
    }

    val sectionOrder = listOf(
        SearchFilterType.SONGS,
        SearchFilterType.ALBUMS,
        SearchFilterType.ARTISTS,
        SearchFilterType.PLAYLISTS
    )

    val imePadding = WindowInsets.ime.getBottom(localDensity).dp
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
        )
    ) {
        sectionOrder.forEach { filterType ->
            val itemsForSection = groupedResults[filterType] ?: emptyList()

            if (itemsForSection.isNotEmpty()) {
                item(key = "header_${filterType.name}") {
                    SearchResultSectionHeader(
                        title = when (filterType) {
                            SearchFilterType.SONGS -> "Songs"
                            SearchFilterType.ALBUMS -> "Albums"
                            SearchFilterType.ARTISTS -> "Artists"
                            SearchFilterType.PLAYLISTS -> "Playlists"
                            else -> "Results"
                        }
                    )
                }

                items(
                    count = itemsForSection.size,
                    key = { index ->
                        val item = itemsForSection[index]
                        when (item) {
                            is SearchResultItem.SongItem -> "song_${item.song.id}"
                            is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                            is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                            is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}_${index}"
                        }
                    }
                ) { index ->
                    val item = itemsForSection[index]
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        when (item) {
                            is SearchResultItem.SongItem -> {
                                val rememberedOnClick = remember(item.song, playerViewModel, onItemSelected) {
                                    {
                                        playerViewModel.showAndPlaySong(item.song)
                                        onItemSelected()
                                    }
                                }
                                EnhancedSongListItem(
                                    song = item.song,
                                    isPlaying = isPlaying,
                                    isCurrentSong = currentPlayingSongId == item.song.id,
                                    onMoreOptionsClick = onSongMoreOptionsClick,
                                    onClick = rememberedOnClick
                                )
                            }

                            is SearchResultItem.AlbumItem -> {
                                val onPlayClick = remember(item.album, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Album clicked: ${item.album.title}")
                                        playerViewModel.playAlbum(item.album)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember (
                                    item.album,
                                    playerViewModel, onItemSelected ) {
                                    {
                                        navController.navigate(Screen.AlbumDetail.createRoute(item.album.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultAlbumItem(
                                    album = item.album,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.ArtistItem -> {
                                val onPlayClick = remember(item.artist, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Artist clicked: ${item.artist.name}")
                                        playerViewModel.playArtist(item.artist)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember (
                                    item.artist,
                                    playerViewModel, onItemSelected ) {
                                    {
                                        navController.navigate(Screen.ArtistDetail.createRoute(item.artist.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultArtistItem(
                                    artist = item.artist,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.PlaylistItem -> {
                                var songsInPlaylist by remember { mutableStateOf<List<Song>>(emptyList()) }
                                var fetchSongs by remember { mutableStateOf(false) }
                                LaunchedEffect(fetchSongs) {
                                    songsInPlaylist = playerViewModel.getSongs( item.playlist.songIds)
                                }
                                val onPlayClick = remember(item.playlist, playerViewModel, onItemSelected) {
                                    {
                                        fetchSongs = true
                                        if (songsInPlaylist.isNotEmpty()) {
                                            playerViewModel.playSongs(
                                                songsInPlaylist,
                                                songsInPlaylist.first(),
                                                item.playlist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        } else
                                            playerViewModel.sendToast("Empty playlist")
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember (
                                    item.playlist,
                                    playerViewModel, onItemSelected ) {
                                    {
                                        navController.navigate(Screen.PlaylistDetail.createRoute(item.playlist.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultPlaylistItem(
                                    playlist = item.playlist,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = album.albumArtUriString,
                contentDescription = "Album Art: ${album.title}",
                modifier = Modifier
                    .size(56.dp)
                    .clip(itemShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Album", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.rounded_artist_24),
                contentDescription = "Artist",
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                    .padding(12.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.songCount} Songs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Artist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.PlaylistPlay,
                contentDescription = "Playlist",
                modifier = Modifier
                    .size(56.dp)
                    .clip(itemShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Playlist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType, // Este valor debería provenir del estado de tu PlayerViewModel
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selected = filterType == currentFilter

    FilterChip(
        selected = selected, // FilterChip tiene un parámetro 'selected'
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        label = { Text(filterType.name.lowercase().replaceFirstChar { it.titlecase() }) },
        modifier = modifier,
        shape = CircleShape, // Expressive shape
        border = BorderStroke(
            width = 0.dp,
            color = Color.Transparent
        ),
        colors = FilterChipDefaults.filterChipColors(
            // Expressive colors for unselected state
            containerColor =  MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            // Expressive colors for selected state
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
         leadingIcon = if (selected) {
             {
                 Icon(
                     painter = painterResource(R.drawable.rounded_check_circle_24),
                     contentDescription = "Selected",
                     tint = MaterialTheme.colorScheme.onPrimary,
                     modifier = Modifier.size(FilterChipDefaults.IconSize)
                 )
             }
         } else {
             null
         }
    )
}
