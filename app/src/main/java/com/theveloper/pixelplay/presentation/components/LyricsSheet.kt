package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.components.subcomps.PlayerSeekBar
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.BubblesLine
import com.theveloper.pixelplay.utils.ProviderText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.max
import kotlin.math.roundToInt

private const val LYRICS_HIGHLIGHT_FRACTION = 0f
private val LYRICS_BOTTOM_PADDING = 180.dp
private val DEFAULT_LINE_HEIGHT = 56.dp
private val VERSE_MARKER_INLINE_REGEX = Regex("(?i)\\bv\\d+:\\s*")
private val VERSE_MARKER_WORD_REGEX = Regex("(?i)^v\\d+:$")

private fun String.removeVerseMarkers(): String {
    val stripped = VERSE_MARKER_INLINE_REGEX.replace(this, "")
    return stripped.replace(Regex("\\s+"), " ").trim()
}

private fun String.isVerseMarkerWord(): Boolean = VERSE_MARKER_WORD_REGEX.matches(trim())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playerUiStateFlow: StateFlow<PlayerUiState>,
    lyricsSearchUiState: LyricsSearchUiState,
    resetLyricsForCurrentSong: () -> Unit,
    onSearchLyrics: () -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onImportLyrics: () -> Unit,
    onDismissLyricsSearch: () -> Unit,
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
    val currentSong by remember { derivedStateOf { stablePlayerState.currentSong } }

    val context = LocalContext.current

    var showFetchLyricsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentSong, lyrics, isLoadingLyrics) {
        if (currentSong != null && lyrics == null && !isLoadingLyrics) {
            showFetchLyricsDialog = true
        } else if (lyrics != null || isLoadingLyrics) {
            showFetchLyricsDialog = false
        }
    }

    if (showFetchLyricsDialog) {
        FetchLyricsDialog(
            uiState = lyricsSearchUiState,
            onConfirm = onSearchLyrics,
            onPickResult = onPickResult,
            onDismiss = {
                showFetchLyricsDialog = false
                onDismissLyricsSearch()
             },
            onImport = onImportLyrics
        )
    }

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
                        title = {
                            Text(
                                text = "Lyrics",
                                fontWeight = FontWeight.Bold,
                                color = onBackgroundColor
                            )
                        },
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
                        actions = {
                            var expanded by remember { mutableStateOf(false) }
                            IconButton(
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = onBackgroundColor
                                ),
                                onClick = { expanded = !expanded }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "Lyrics options",
                                    tint = onBackgroundColor
                                )
                                DropdownMenu(
                                    shape = AbsoluteSmoothCornerShape(
                                        cornerRadiusBL = 20.dp,
                                        smoothnessAsPercentTL = 60,
                                        cornerRadiusBR = 20.dp,
                                        smoothnessAsPercentTR = 60,
                                        cornerRadiusTL = 20.dp,
                                        smoothnessAsPercentBL = 60,
                                        cornerRadiusTR = 20.dp,
                                        smoothnessAsPercentBR = 60
                                    ),
                                    containerColor = backgroundColor,
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(painter = painterResource(R.drawable.outline_restart_alt_24), contentDescription = null) },
                                        text = { Text(text = "Reset imported lyrics") },
                                        onClick = {
                                            expanded = false
                                            resetLyricsForCurrentSong()
                                        }
                                    )
                                }
                            }
                        }
                        ,
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
                                        },
                                        content = {
                                            Text(
                                                text = title,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = GoogleSansRounded
                                            )
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
        val syncedListState = rememberLazyListState()
        val staticListState = rememberLazyListState()
        val listState = when (showSyncedLyrics) {
            false -> staticListState
            else -> syncedListState
        }
        val playerUiState by playerUiStateFlow.collectAsState()
        val density = LocalDensity.current
        val lineHeights = remember { mutableStateMapOf<Int, Int>() }

        val topContentPadding = paddingValues.calculateTopPadding()
        val bottomContentPadding = paddingValues.calculateBottomPadding() + LYRICS_BOTTOM_PADDING
        val horizontalContentPadding = 24.dp

        val defaultLineHeightPx = remember(density) { with(density) { DEFAULT_LINE_HEIGHT.roundToPx() } }

        val currentItemIndex by remember {
            derivedStateOf {
                val position = playerUiState.currentPosition
                lyrics?.synced?.let { synced ->
                    var currentIndex = -1
                    for ((index, line) in synced.withIndex()) {
                        val nextTime = synced.getOrNull(index + 1)?.time?.toLong() ?: Long.MAX_VALUE
                        if (position in line.time.toLong()..<nextTime) {
                            currentIndex = index
                            break
                        }
                    }
                    currentIndex
                } ?: -1
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val highlightBandHeight = remember(maxHeight) {
                val desired = DEFAULT_LINE_HEIGHT + 24.dp
                if (maxHeight <= 0.dp) desired else desired.coerceAtMost(maxHeight / 2)
            }
            val highlightCenter = remember(maxHeight, topContentPadding, bottomContentPadding, highlightBandHeight) {
                val available = (maxHeight - topContentPadding - bottomContentPadding).coerceAtLeast(0.dp)
                val adjustableSpace = (available - highlightBandHeight).coerceAtLeast(0.dp)
                topContentPadding + (highlightBandHeight / 2f) + (adjustableSpace * LYRICS_HIGHLIGHT_FRACTION)
            }
            val highlightCenterPx = remember(density, highlightCenter) {
                with(density) { highlightCenter.roundToPx() }
            }
            val beforePaddingPx = remember(density, topContentPadding) {
                with(density) { topContentPadding.roundToPx() }
            }
            val afterPaddingPx = remember(density, bottomContentPadding) {
                with(density) { bottomContentPadding.roundToPx() }
            }

            LaunchedEffect(currentItemIndex, showSyncedLyrics, highlightCenterPx, syncedListState.isScrollInProgress) {
                if (syncedListState.isScrollInProgress) return@LaunchedEffect
                if (showSyncedLyrics == true && currentItemIndex != -1) {
                    val layoutInfo = syncedListState.layoutInfo
                    val viewportHeight = layoutInfo.viewportSize.height
                    if (viewportHeight <= 0) return@LaunchedEffect

                    val measuredHeight = lineHeights[currentItemIndex]
                        ?: layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentItemIndex }?.size
                        ?: defaultLineHeightPx

                    val desiredTop = highlightCenterPx - (measuredHeight / 2)
                    val visibleInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentItemIndex }
                    if (visibleInfo != null) {
                        val delta = visibleInfo.offset - desiredTop
                        if (kotlin.math.abs(delta) > 1) {
                            syncedListState.animateScrollBy(delta.toFloat())
                        }
                    } else {
                        val minOffset = -beforePaddingPx
                        val maxOffset = (viewportHeight - afterPaddingPx - measuredHeight).coerceAtLeast(minOffset)
                        val desiredOffset = (desiredTop - beforePaddingPx).coerceIn(minOffset, maxOffset)
                        syncedListState.animateScrollToItem(currentItemIndex, desiredOffset)
                    }
                }
            }

            LaunchedEffect(lyrics?.synced) {
                lineHeights.clear()
                syncedListState.scrollToItem(0)
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = horizontalContentPadding,
                    end = horizontalContentPadding,
                    top = topContentPadding,
                    bottom = bottomContentPadding
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

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { size ->
                                            lineHeights[index] = size.height
                                        }
                                ) {
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
                            }

                            if (lyrics!!.areFromRemote) {
                                item(key = "provider_text") {
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

            if (showSyncedLyrics == true) {
                val highlightTop = (highlightCenter - (highlightBandHeight / 2f))
                    .coerceAtLeast(topContentPadding)
                val maxTop = (maxHeight - bottomContentPadding - highlightBandHeight)
                    .coerceAtLeast(topContentPadding)
                val indicatorTop = highlightTop.coerceAtMost(maxTop)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalContentPadding)
                        .offset(y = indicatorTop)
                        .height(highlightBandHeight)
                        .drawBehind {
                            val strokeWidth = 1.5.dp.toPx()
                            val color = accentColor.copy(alpha = 0.35f)
                            drawLine(
                                color = color,
                                start = androidx.compose.ui.geometry.Offset(0f, strokeWidth / 2f),
                                end = androidx.compose.ui.geometry.Offset(size.width, strokeWidth / 2f),
                                strokeWidth = strokeWidth
                            )
                            drawLine(
                                color = color,
                                start = androidx.compose.ui.geometry.Offset(0f, size.height - strokeWidth / 2f),
                                end = androidx.compose.ui.geometry.Offset(size.width, size.height - strokeWidth / 2f),
                                strokeWidth = strokeWidth
                            )
                        }
                )
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

@OptIn(ExperimentalLayoutApi::class)
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
    val sanitizedLine = remember(syncedLine.line) { syncedLine.line.removeVerseMarkers() }
    val displayWords = remember(words) {
        words?.filterNot { it.word.isVerseMarkerWord() }
    }

    if (displayWords.isNullOrEmpty()) {
        // Fallback to line-by-line
        Text(
            text = sanitizedLine,
            style = style,
            color = if (isCurrentLine) accentColor else LocalContentColor.current.copy(alpha = 0.45f),
            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
            modifier = modifier.clickable { onClick() }
        )
    } else {
        // Word-by-word highlighting
        val unhighlightedColor = LocalContentColor.current.copy(alpha = 0.45f)
        val highlightedColor = accentColor

        FlowRow(
            modifier = modifier.clickable { onClick() },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            displayWords.forEachIndexed { index, word ->
                val nextWordTime = displayWords.getOrNull(index + 1)?.time?.toLong() ?: nextTime.toLong()
                val wordStart = if (index == 0) syncedLine.time.toLong() else word.time.toLong()
                val isCurrentWord = position in wordStart..<nextWordTime

                val color by animateColorAsState(
                    targetValue = if (isCurrentWord) highlightedColor else unhighlightedColor,
                    animationSpec = tween(durationMillis = 300),
                    label = "wordColor-$index"
                )

                Text(
                    text = word.word.removeVerseMarkers(),
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
        text = line.removeVerseMarkers(),
        style = style,
        color = LocalContentColor.current.copy(alpha = 0.7f),
        modifier = modifier
    )
}