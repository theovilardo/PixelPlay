package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.components.subcomps.PlayerSeekBar
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.utils.BubblesLine
import com.theveloper.pixelplay.utils.ProviderText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playerUiStateFlow: StateFlow<PlayerUiState>,
    lyricsTextStyle: TextStyle,
    backgroundColor: Color,
    onBackgroundColor: Color,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    tertiaryColor: Color,
    onTertiaryColor: Color,
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

    val fabShapeCornerRadius by animateDpAsState(
        targetValue = if (isPlaying) 24.dp else 50.dp,
        label = "fabShapeAnimation"
    )

    var fabShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = fabShapeCornerRadius,
        smoothnessAsPercentBL = 60,
        cornerRadiusTR = fabShapeCornerRadius,
        smoothnessAsPercentBR = 60,
        cornerRadiusBL = fabShapeCornerRadius,
        smoothnessAsPercentTL = 60,
        cornerRadiusBR = fabShapeCornerRadius,
        smoothnessAsPercentTR = 60
    )

    val tabTitles = listOf("Synced", "Static")

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp)),
        containerColor = containerColor,
        contentColor = contentColor,
        topBar = {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .height(218.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    containerColor,
                                    containerColor,
                                    containerColor,
                                    containerColor,
                                    containerColor,
                                    containerColor,
                                    containerColor,
                                    Color.Transparent
                                )
                            )
                        )
                ) {

                }
                Column(
                    Modifier.align(Alignment.TopCenter)
                ) {
                    CenterAlignedTopAppBar(
                        title = { Text(text = "Lyrics", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            FilledIconButton(
                                modifier = Modifier.padding(start = 12.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = backgroundColor,
                                    contentColor = onBackgroundColor
                                ),
                                onClick = onBackClick
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowBack,
                                    contentDescription = context.resources.getString(R.string.close_lyrics_sheet)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                    if (lyrics?.synced != null && lyrics?.plain != null) {
                        val selectedTabIndex = if (showSyncedLyrics == true) 0 else 1

                        TabRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            indicator = { tabPositions ->
                                if (selectedTabIndex < tabPositions.size) {
                                    TabRowDefaults.PrimaryIndicator(
                                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        height = 3.dp,
                                        color = Color.Transparent
                                    )
                                }
                            },
                            divider = {}
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Spacer(modifier = Modifier.width(14.dp))
                                tabTitles.forEachIndexed { index, title ->
                                    TabAnimation(
                                        modifier = Modifier.weight(1f),
                                        selectedColor = accentColor,
                                        onSelectedColor = onAccentColor,
                                        unselectedColor = contentColor.copy(alpha = 0.15f),
                                        onUnselectedColor = contentColor,
                                        index = index,
                                        title = title,
                                        selectedIndex = selectedTabIndex,
                                        onClick = {
                                            showSyncedLyrics = (index == 0)
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            LargeFloatingActionButton(
                modifier = Modifier.padding(bottom = 64.dp),
                onClick = onPlayPause,
                shape = fabShape,
                containerColor = tertiaryColor,
                contentColor = onTertiaryColor
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    label = "playPauseIconAnimation"
                ) { playing ->
                    if (playing) {
                        Icon(
                            modifier = Modifier.size(36.dp),
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = "Pause"
                        )
                    } else {
                        Icon(
                            modifier = Modifier.size(36.dp),
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play"
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { paddingValues ->
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val playerUiState by playerUiStateFlow.collectAsState()
        val density = LocalDensity.current

        val currentItemIndex by remember {
            derivedStateOf {
                val position = playerUiState.currentPosition
                lyrics?.synced?.let { synced ->
                    var currentIndex = -1
                    for ((index, line) in synced.withIndex()) {
                        val nextTime = synced.getOrNull(index + 1)?.time?.toLong() ?: Long.MAX_VALUE
                        if (position in line.time.toLong()..nextTime) {
                            currentIndex = index
                            break
                        }
                    }
                    currentIndex
                } ?: -1
            }
        }

        LaunchedEffect(currentItemIndex) {
            if (currentItemIndex != -1 && !listState.isScrollInProgress) {
                val itemInfo = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == currentItemIndex }

                if (itemInfo != null) {
                    // Item is visible, use precise scrollBy
                    val viewportHeight = listState.layoutInfo.viewportSize.height
                    val itemHeight = itemInfo.size
                    val desiredOffset = ((viewportHeight * 0.35F) - (itemHeight / 2)).toInt()
                    val scrollAmount = itemInfo.offset - desiredOffset
                    if (abs(scrollAmount) > 1) {
                        coroutineScope.launch {
                            listState.animateScrollBy(
                                value = scrollAmount.toFloat(),
                                animationSpec = tween(durationMillis = 300)
                            )
                        }
                    }
                } else {
                    // Item is not visible, use animateScrollToItem with estimated height
                    val estimatedItemHeight = with(density) { 48.dp.toPx() }
                    val viewportHeight = listState.layoutInfo.viewportSize.height
                    val desiredOffset = ((viewportHeight * 0.35F) - (estimatedItemHeight / 2)).toInt()

                    coroutineScope.launch {
                        listState.animateScrollToItem(
                            index = currentItemIndex,
                            scrollOffset = desiredOffset
                        )
                    }
                }
            }
        }

        LaunchedEffect(lyrics) { listState.scrollToItem(0) }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 180.dp // Padding for FAB and seek bar
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
                            ) { index, syncedLine ->
                                val nextTime = synced.getOrNull(index + 1)?.time ?: Int.MAX_VALUE

                                if (syncedLine.line.isNotBlank()) {
                                    SyncedLyricsLine(
                                        positionFlow = playerUiStateFlow.map { it.currentPosition },
                                        syncedLine = syncedLine,
                                        nextTime = nextTime,
                                        accentColor = accentColor,
                                        style = lyricsTextStyle,
                                        onClick = { onSeekTo(syncedLine.time.toLong()) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    BubblesLine(
                                        positionFlow = playerUiStateFlow.map { it.currentPosition },
                                        time = syncedLine.time,
                                        color = contentColor,
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
                    .height(96.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                containerColor
                            )
                        )
                    )
            ) {

            }

            PlayerSeekBar(
                backgroundColor = backgroundColor,
                onBackgroundColor = onBackgroundColor,
                primaryColor = accentColor,
                currentPosition = playerUiState.currentPosition,
                totalDuration = stablePlayerState.totalDuration,
                onSeek = onSeekTo,
                isPlaying = isPlaying,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(78.dp)
                    .padding(bottom = paddingValues.calculateBottomPadding() + 10.dp)
                    .padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun SyncedLyricsLine(
    positionFlow: Flow<Long>,
    syncedLine: SyncedLine,
    accentColor: Color,
    nextTime: Int,
    style: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val position by positionFlow.collectAsState(0L)
    val isCurrentLine by remember(position, syncedLine.time, nextTime) {
        derivedStateOf { position in syncedLine.time.toLong()..<nextTime.toLong() }
    }

    val words = syncedLine.words
    if (words.isNullOrEmpty()) {
        // Fallback to line-by-line
        Text(
            text = syncedLine.line,
            style = style,
            color = if (isCurrentLine) accentColor else LocalContentColor.current.copy(alpha = 0.45f),
            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
            modifier = modifier.clickable { onClick() }
        )
    } else {
        // Word-by-word highlighting
        val unhighlightedColor = LocalContentColor.current.copy(alpha = 0.45f)
        val highlightedColor = accentColor

        Row(modifier = modifier.clickable { onClick() }) {
            for ((index, word) in words.withIndex()) {
                val nextWordTime = words.getOrNull(index + 1)?.time?.toLong() ?: nextTime.toLong()
                val isCurrentWord by remember(position, word.time, nextWordTime) {
                    derivedStateOf { position in word.time.toLong()..<nextWordTime }
                }

                val color by animateColorAsState(
                    targetValue = if (isCurrentWord) highlightedColor else unhighlightedColor,
                    animationSpec = tween(durationMillis = 300)
                )

                Text(
                    text = word.word,
                    style = style,
                    color = color,
                    fontWeight = if (isCurrentWord) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
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