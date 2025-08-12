package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.utils.BubblesLine
import com.theveloper.pixelplay.utils.ProviderText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playerUiStateFlow: StateFlow<PlayerUiState>,
    lyricsTextStyle: TextStyle,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit, // New parameter
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }
    val stablePlayerState by stablePlayerStateFlow.collectAsState()

    val isLoadingLyrics by remember { derivedStateOf { stablePlayerState.isLoadingLyrics } }
    val lyrics by remember { derivedStateOf { stablePlayerState.lyrics } }
    val isPlaying by remember { derivedStateOf { stablePlayerState.isPlaying } }

    val context = LocalContext.current

    var showSyncedLyrics by remember(lyrics) {
        mutableStateOf(
            when {
                lyrics?.synced != null -> true
                lyrics?.plain != null -> false
                else -> null
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = containerColor,
        contentColor = contentColor,
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(text = "Lyrics", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = context.resources.getString(R.string.close_lyrics_sheet)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                if (lyrics?.synced != null && lyrics?.plain != null) {
                    ExpressiveLyricsTypeSwitch(
                        selectedIndex = if (showSyncedLyrics == true) 0 else 1,
                        onSelectedIndexChange = { index ->
                            showSyncedLyrics = index == 0
                        },
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        floatingActionButton = {
            val fabShapeCornerRadius by animateDpAsState(
                targetValue = if (isPlaying) 24.dp else 50.dp,
                label = "fabShapeAnimation"
            )

            LargeFloatingActionButton(
                onClick = onPlayPause,
                shape = RoundedCornerShape(fabShapeCornerRadius),
                containerColor = accentColor,
                contentColor = contentColor
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    label = "playPauseIconAnimation"
                ) { playing ->
                    if (playing) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            tint = containerColor.copy(alpha = 0.45f),
                            contentDescription = "Pause"
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            tint = containerColor.copy(alpha = 0.45f),
                            contentDescription = "Play"
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(lyrics) { listState.scrollToItem(0) }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = paddingValues.calculateTopPadding() + 16.dp,
                    bottom = paddingValues.calculateBottomPadding() + 80.dp // Padding for FAB
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                when (showSyncedLyrics) {
                    null -> {
                        item(key = "loader_or_empty") {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxSize()
                                    .padding(bottom = 160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingLyrics) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = context.resources.getString(R.string.loading_lyrics),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        LinearProgressIndicator(
                                            trackColor = accentColor.copy(alpha = .5f),
                                            modifier = Modifier.width(100.dp)
                                        )
                                    }
                                } else {
                                    Text(text = context.resources.getString(R.string.cant_find_lyrics))
                                }
                            }
                        }
                    }
                    true -> {
                        lyrics?.synced?.let { synced ->
                            itemsIndexed(
                                items = synced,
                                key = { index, item -> "$index-${item.time}" }
                            ) { index, (time, line) ->
                                val nextTime = synced.getOrNull(index + 1)?.time ?: Int.MAX_VALUE

                                if (line.isNotBlank()) {
                                    SyncedLyricsLine(
                                        positionFlow = playerUiStateFlow.map { it.currentPosition },
                                        time = time,
                                        nextTime = nextTime,
                                        line = line,
                                        accentColor = accentColor,
                                        style = lyricsTextStyle,
                                        onClick = { onSeekTo(time.toLong()) },
                                        onBecomeCurrent = {
                                            val isItemVisible = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.index == index } != null
                                            if (isItemVisible && !listState.isScrollInProgress) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(
                                                        index = index,
                                                        scrollOffset = -listState.layoutInfo.viewportSize.height / 3
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    BubblesLine(
                                        positionFlow = playerUiStateFlow.map { it.currentPosition },
                                        time = time,
                                        nextTime = nextTime,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            if (lyrics!!.areFromRemote) {
                                item {
                                    ProviderText(
                                        providerText = context.resources.getString(R.string.lyrics_provided_by),
                                        uri = context.resources.getString(R.string.lrclib_uri),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                    false -> {
                        lyrics?.plain?.let { plain ->
                            itemsIndexed(
                                items = plain,
                                key = { index, line -> "$index-$line" }
                            ) { _, line ->
                                PlainLyricsLine(
                                    line = line,
                                    style = lyricsTextStyle,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(80.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                containerColor.copy(0.5f),
                                containerColor
                            )
                        )
                    )
            ) {

            }
        }
    }
}

@Composable
fun ExpressiveLyricsTypeSwitch(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val items = listOf("Synced", "Static")
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        indicator = {
            TabRowDefaults.PrimaryIndicator(
                modifier = Modifier
                    .tabIndicatorOffset(selectedIndex)
                    .clip(RoundedCornerShape(100)),
                height = 4.dp,
                color = accentColor
            )
        },
        divider = {},
        modifier = modifier
    ) {
        items.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelectedIndexChange(index) },
                text = {
                    Text(
                        text = title,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                selectedContentColor = accentColor,
                unselectedContentColor = LocalContentColor.current.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SyncedLyricsLine(
    positionFlow: Flow<Long>,
    time: Int,
    accentColor: Color,
    nextTime: Int,
    line: String,
    style: TextStyle,
    onClick: () -> Unit,
    onBecomeCurrent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val position by positionFlow.collectAsState(0)
    val isCurrentLine by remember(position, time, nextTime) {
        derivedStateOf { position in time.toLong()..nextTime.toLong() }
    }

    LaunchedEffect(isCurrentLine) {
        if (isCurrentLine) {
            onBecomeCurrent()
        }
    }

    Text(
        text = line,
        style = style,
        color = if (isCurrentLine) accentColor else LocalContentColor.current.copy(alpha = 0.45f),
        fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier.clickable { onClick() }
    )
}

@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Text(
        text = line,
        style = style,
        color = LocalContentColor.current.copy(alpha = 0.7f),
        modifier = modifier
    )
}