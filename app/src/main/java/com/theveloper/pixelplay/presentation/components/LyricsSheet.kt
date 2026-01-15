package com.theveloper.pixelplay.presentation.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
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
import com.theveloper.pixelplay.presentation.components.snapping.ExperimentalSnapperApi
import com.theveloper.pixelplay.presentation.components.snapping.SnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberLazyListSnapperLayoutInfo
import com.theveloper.pixelplay.presentation.components.snapping.rememberSnapperFlingBehavior
import com.theveloper.pixelplay.utils.LyricsUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsSheet(
    stablePlayerStateFlow: StateFlow<StablePlayerState>,
    playerUiStateFlow: StateFlow<PlayerUiState>,
    lyricsSearchUiState: LyricsSearchUiState,
    resetLyricsForCurrentSong: () -> Unit,
    onSearchLyrics: (Boolean) -> Unit,
    onPickResult: (LyricsSearchResult) -> Unit,
    onManualSearch: (String, String?) -> Unit,
    onImportLyrics: () -> Unit,
    onDismissLyricsSearch: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
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
    modifier: Modifier = Modifier,
    highlightZoneFraction: Float = 0.08f, // Reduced from 0.22 for less padding
    highlightOffsetDp: Dp = 32.dp,
    autoscrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 450, easing = FastOutSlowInEasing)
) {
    BackHandler { onBackClick() }
    val stablePlayerState by stablePlayerStateFlow.collectAsState()

    val isLoadingLyrics by remember { derivedStateOf { stablePlayerState.isLoadingLyrics } }
    val lyrics by remember { derivedStateOf { stablePlayerState.lyrics } }
    val isPlaying by remember { derivedStateOf { stablePlayerState.isPlaying } }
    val currentSong by remember { derivedStateOf { stablePlayerState.currentSong } }

    val context = LocalContext.current

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    // Flag to prevent dialog from showing briefly after reset
    var wasResetTriggered by remember { mutableStateOf(false) }
    // Save lyrics dialog state
    var showSaveLyricsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentSong, lyrics, isLoadingLyrics) {
        if (currentSong != null && lyrics == null && !isLoadingLyrics) {
            // Only show dialog if reset was not just triggered
            if (!wasResetTriggered) {
                showFetchLyricsDialog = true
            }
        } else if (lyrics != null || isLoadingLyrics) {
            showFetchLyricsDialog = false
            wasResetTriggered = false // Reset the flag when lyrics are loaded
        }
    }

    if (showFetchLyricsDialog) {
        FetchLyricsDialog(
            uiState = lyricsSearchUiState,
            currentSong = currentSong,
            onConfirm = onSearchLyrics,
            onPickResult = onPickResult,
            onManualSearch = onManualSearch,
            onDismiss = {
                showFetchLyricsDialog = false
                onDismissLyricsSearch()
                if (lyrics == null && !isLoadingLyrics) {
                    onBackClick()
                }
            },
            onImport = onImportLyrics
        )
    }

    // Save Lyrics Dialog
    if (showSaveLyricsDialog && lyrics != null && currentSong != null) {
        val hasSynced = !lyrics?.synced.isNullOrEmpty()
        val hasPlain = !lyrics?.plain.isNullOrEmpty()
        
        AlertDialog(
            onDismissRequest = { showSaveLyricsDialog = false },
            title = { Text(stringResource(R.string.save_lyrics_dialog_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.save_lyrics_dialog_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    if (hasSynced) {
                        FilledTonalButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                saveLyricsToFile(
                                    context = context,
                                    song = currentSong!!,
                                    lyrics = lyrics!!,
                                    preferSynced = true
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save_synced_lyrics))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (hasPlain) {
                        OutlinedButton(
                            onClick = {
                                showSaveLyricsDialog = false
                                saveLyricsToFile(
                                    context = context,
                                    song = currentSong!!,
                                    lyrics = lyrics!!,
                                    preferSynced = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.save_plain_lyrics))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveLyricsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
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
                                    // Save lyrics as .lrc option
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.outline_save_24),
                                                contentDescription = null
                                            )
                                        },
                                        text = { Text(text = stringResource(R.string.save_lyrics_as_lrc)) },
                                        enabled = lyrics != null,
                                        onClick = {
                                            expanded = false
                                            if (lyrics != null) {
                                                showSaveLyricsDialog = true
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.no_lyrics_to_save),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    )
                                    // Reset imported lyrics option
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.outline_restart_alt_24),
                                                contentDescription = null
                                            )
                                        },
                                        text = { Text(text = stringResource(R.string.reset_imported_lyrics)) },
                                        onClick = {
                                            expanded = false
                                            wasResetTriggered = true
                                            resetLyricsForCurrentSong()
                                        }
                                    )
                                }
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

                    // Quick offset controls for synced lyrics
                    if (lyrics?.synced != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                //.padding(top = 6.dp)
//                                .background(
//                                    color = containerColor,
//                                    shape = CircleShape
//                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .background(
                                        color = backgroundColor,
                                        shape = CircleShape
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Row(
                                    modifier = Modifier.padding(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // -0.5s button
                                    FilledTonalIconButton(
                                        onClick = { onLyricsSyncOffsetChange(lyricsSyncOffset - 500) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = onAccentColor, //.copy(alpha = 0.5f),
                                            contentColor = accentColor
                                        )
                                    ) {
                                        Text("−.5", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                    }
                                    // -0.1s button
                                    FilledTonalIconButton(
                                        onClick = { onLyricsSyncOffsetChange(lyricsSyncOffset - 100) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = onAccentColor, //.copy(alpha = 0.5f),
                                            contentColor = accentColor
                                        )
                                    ) {
                                        Text("−.1", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                    }
                                    // Current offset display / Reset button
                                    FilledTonalIconButton(
                                        onClick = { onLyricsSyncOffsetChange(0) },
                                        modifier = Modifier.width(48.dp).height(32.dp),
                                        enabled = lyricsSyncOffset != 0,
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = if (lyricsSyncOffset != 0) accentColor.copy(alpha = 0.3f) else backgroundColor.copy(alpha = 0.7f),
                                            contentColor = onBackgroundColor
                                        )
                                    ) {
                                        Text(
                                            text = if (lyricsSyncOffset == 0) "0s" else String.format("%+.1fs", lyricsSyncOffset / 1000f),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    // +0.1s button
                                    FilledTonalIconButton(
                                        onClick = { onLyricsSyncOffsetChange(lyricsSyncOffset + 100) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = onAccentColor, //.copy(alpha = 0.5f),
                                            contentColor = accentColor
                                        )
                                    ) {
                                        Text("+.1", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                    }
                                    // +0.5s button
                                    FilledTonalIconButton(
                                        onClick = { onLyricsSyncOffsetChange(lyricsSyncOffset + 500) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = onAccentColor, //.copy(alpha = 0.5f),
                                            contentColor = accentColor
                                        )
                                    ) {
                                        Text("+.5", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        },
        floatingActionButton = {
            LargeExtendedFloatingActionButton(
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
        val playerUiState by playerUiStateFlow.collectAsState()
        // Apply lyrics sync offset to the position flow
        val positionFlow = remember(playerUiStateFlow, lyricsSyncOffset) {
            playerUiStateFlow.map { (it.currentPosition + lyricsSyncOffset).coerceAtLeast(0L) }
        }

        LaunchedEffect(lyrics) {
            syncedListState.scrollToItem(0)
            staticListState.scrollToItem(0)
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            when (showSyncedLyrics) {
                null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding() + 180.dp,
                            start = 24.dp,
                            end = 24.dp
                        )
                    ) {
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
                                        LinearWavyProgressIndicator(
                                            trackColor = accentColor.copy(alpha = .5f),
                                            modifier = Modifier.width(100.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                true -> {
                    lyrics?.synced?.let { synced ->
                        SyncedLyricsList(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = 24.dp,
                                    end = 24.dp,
                                    //top = paddingValues.calculateTopPadding(),
                                    //bottom = paddingValues.calculateBottomPadding() //+ 180.dp
                                ),
                            lines = synced,
                            listState = syncedListState,
                            positionFlow = positionFlow,
                            accentColor = accentColor,
                            textStyle = lyricsTextStyle,
                            onLineClick = { syncedLine -> onSeekTo(syncedLine.time.toLong()) },
                            highlightZoneFraction = highlightZoneFraction,
                            highlightOffsetDp = highlightOffsetDp,
                            autoscrollAnimationSpec = autoscrollAnimationSpec,
                            footer = {
                                if (lyrics?.areFromRemote == true) {
                                    item(key = "provider_text") {
                                        ProviderText(
                                            providerText = context.resources.getString(R.string.lyrics_provided_by),
                                            uri = context.resources.getString(R.string.lrclib_uri),
                                            textAlign = TextAlign.Center,
                                            accentColor = accentColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                false -> {
                    lyrics?.plain?.let { plain ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = staticListState,
                            contentPadding = PaddingValues(
                                start = 24.dp,
                                end = 24.dp,
                                top = paddingValues.calculateTopPadding(),
                                bottom = paddingValues.calculateBottomPadding() + 180.dp
                            )
                        ) {
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

            val bottomPadding = paddingValues.calculateBottomPadding() + 10.dp
            val footerBaseHeight = 76.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = footerBaseHeight + bottomPadding)
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

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomPadding)
                    .padding(horizontal = 24.dp)
            ) {
                PlayerSeekBar(
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    primaryColor = accentColor,
                    currentPosition = playerUiState.currentPosition,
                    totalDuration = stablePlayerState.totalDuration,
                    onSeek = onSeekTo,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalSnapperApi::class)
@Composable
fun SyncedLyricsList(
    lines: List<SyncedLine>,
    listState: LazyListState,
    positionFlow: Flow<Long>,
    accentColor: Color,
    textStyle: TextStyle,
    onLineClick: (SyncedLine) -> Unit,
    highlightZoneFraction: Float,
    highlightOffsetDp: Dp,
    autoscrollAnimationSpec: AnimationSpec<Float>,
    modifier: Modifier = Modifier,
    footer: LazyListScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    val position by positionFlow.collectAsState(initial = 0L)
    val currentLineIndex by remember(position, lines) {
        derivedStateOf {
            if (lines.isEmpty()) return@derivedStateOf -1
            val currentPosition = position
            lines.withIndex().lastOrNull { (index, line) ->
                val nextTime = lines.getOrNull(index + 1)?.time?.toLong() ?: Long.MAX_VALUE
                currentPosition in line.time.toLong()..<nextTime
            }?.index ?: -1
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(maxHeight, highlightZoneFraction, highlightOffsetDp) {
            calculateHighlightMetrics(maxHeight, highlightZoneFraction, highlightOffsetDp)
        }
        val highlightOffsetPx = remember(highlightOffsetDp, density) { with(density) { highlightOffsetDp.toPx() } }

        val snapperLayoutInfo = rememberLazyListSnapperLayoutInfo(
            lazyListState = listState,
            snapOffsetForItem = { layoutInfo, item ->
                val viewportHeight = layoutInfo.endScrollOffset - layoutInfo.startScrollOffset
                highlightSnapOffsetPx(viewportHeight, item.size, highlightOffsetPx)
            }
        )
        val flingBehavior = rememberSnapperFlingBehavior(layoutInfo = snapperLayoutInfo)

        LaunchedEffect(currentLineIndex, lines.size, metrics) {
            if (lines.isEmpty()) return@LaunchedEffect
            if (currentLineIndex !in lines.indices) return@LaunchedEffect
            if (listState.isScrollInProgress) return@LaunchedEffect
            if (listState.layoutInfo.totalItemsCount == 0) return@LaunchedEffect

            animateToSnapIndex(
                listState = listState,
                layoutInfo = snapperLayoutInfo,
                targetIndex = currentLineIndex,
                animationSpec = autoscrollAnimationSpec
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(
                    top = 220.dp, // Slightly increased top padding
                    bottom = 200.dp // Bottom padding for FAB and seek bar
                )
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, item -> "${item.time}_$index" }
                ) { index, line ->
                    val nextTime = lines.getOrNull(index + 1)?.time ?: Int.MAX_VALUE
                    if (line.line.isNotBlank()) {
                        LyricLineRow(
                            line = line,
                            nextTime = nextTime,
                            position = position,
                            accentColor = accentColor,
                            style = textStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("synced_line_${line.time}"),
                            onClick = { onLineClick(line) }
                        )
                    } else {
                        BubblesLine(
                            positionFlow = positionFlow,
                            time = line.time,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                            nextTime = nextTime,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                footer()
            }

//            if (metrics.zoneHeight > 0.dp) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .offset(y = metrics.topPadding)
//                        .height(metrics.zoneHeight)
//                        .align(Alignment.TopCenter)
//                        .clip(RoundedCornerShape(18.dp))
//                        .background(accentColor.copy(alpha = 0.12f))
//                        .testTag("synced_highlight_zone")
//                )
//            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricLineRow(
    line: SyncedLine,
    nextTime: Int,
    position: Long,
    accentColor: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sanitizedLine = remember(line.line) { sanitizeLyricLineText(line.line) }
    val sanitizedWords = remember(line.words) {
        line.words?.let(::sanitizeSyncedWords)
    }
    val isCurrentLine by remember(position, line.time, nextTime) {
        derivedStateOf { position in line.time.toLong()..<nextTime.toLong() }
    }
    val unhighlightedColor = LocalContentColor.current.copy(alpha = 0.45f)
    val lineColor by animateColorAsState(
        targetValue = if (isCurrentLine) accentColor else unhighlightedColor,
        animationSpec = tween(durationMillis = 250),
        label = "lineColor"
    )

    if (sanitizedWords.isNullOrEmpty()) {
        Text(
            text = sanitizedLine,
            style = style,
            color = lineColor,
            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        )
    } else {
        FlowRow(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sanitizedWords.forEachIndexed { wordIndex, word ->
                key("${line.time}_${word.time}_${word.word}") {
                    val nextWordTime = sanitizedWords.getOrNull(wordIndex + 1)?.time?.toLong() ?: nextTime.toLong()
                    val isCurrentWord by remember(position, word.time, nextWordTime) {
                        derivedStateOf { position in word.time.toLong()..<nextWordTime }
                    }
                    LyricWordSpan(
                        word = word,
                        isHighlighted = isCurrentLine && isCurrentWord,
                        style = style,
                        highlightedColor = accentColor,
                        unhighlightedColor = unhighlightedColor
                    )
                }
            }
        }
    }
}

@Composable
fun LyricWordSpan(
    word: SyncedWord,
    isHighlighted: Boolean,
    style: TextStyle,
    highlightedColor: Color,
    unhighlightedColor: Color,
    modifier: Modifier = Modifier
) {
    val color by animateColorAsState(
        targetValue = if (isHighlighted) highlightedColor else unhighlightedColor,
        animationSpec = tween(durationMillis = 200),
        label = "wordColor"
    )

    Text(
        text = word.word,
        style = style,
        color = color,
        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
        modifier = modifier
    )
}

@Composable
fun PlainLyricsLine(
    line: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val sanitizedLine = remember(line) { sanitizeLyricLineText(line) }
    Text(
        text = sanitizedLine,
        style = style,
        color = LocalContentColor.current.copy(alpha = 0.7f),
        modifier = modifier
    )
}

private val LeadingTagRegex = Regex("^v\\d+:\\s*", RegexOption.IGNORE_CASE)

internal fun sanitizeLyricLineText(raw: String): String =
    LyricsUtils.stripLrcTimestamps(raw).replace(LeadingTagRegex, "").trimStart()

internal fun sanitizeSyncedWords(words: List<SyncedWord>): List<SyncedWord> =
    words.mapIndexedNotNull { index, word ->
        val sanitized = if (index == 0) LeadingTagRegex.replace(word.word, "") else word.word
        val trimmed = sanitized.trim()
        if (trimmed.isEmpty()) null else word.copy(word = trimmed)
    }

internal data class HighlightZoneMetrics(
    val topPadding: Dp,
    val bottomPadding: Dp,
    val zoneHeight: Dp,
    val centerFromTop: Dp
)

internal fun calculateHighlightMetrics(
    containerHeight: Dp,
    highlightZoneFraction: Float,
    highlightOffset: Dp
): HighlightZoneMetrics {
    val container = containerHeight.value
    val zoneHeight = (containerHeight * highlightZoneFraction).value.coerceAtLeast(0f)
    val offset = highlightOffset.value
    val minCenter = zoneHeight / 2f
    val maxCenter = (container - zoneHeight / 2f).coerceAtLeast(minCenter)
    val unclampedCenter = container / 2f - offset
    val center = unclampedCenter.coerceIn(minCenter, maxCenter)
    val topPadding = (center - zoneHeight / 2f).coerceAtLeast(0f)
    val bottomPadding = (container - center - zoneHeight / 2f).coerceAtLeast(0f)

    return HighlightZoneMetrics(
        topPadding = topPadding.dp,
        bottomPadding = bottomPadding.dp,
        zoneHeight = zoneHeight.dp,
        centerFromTop = center.dp
    )
}

internal fun highlightSnapOffsetPx(
    viewportHeight: Int,
    itemSize: Int,
    highlightOffsetPx: Float
): Int {
    if (viewportHeight <= 0 || itemSize <= 0) return 0
    if (itemSize >= viewportHeight) return 0
    val viewport = viewportHeight.toFloat()
    val halfItem = itemSize / 2f
    val targetCenter = (viewport / 2f) - highlightOffsetPx
    val clampedCenter = targetCenter.coerceIn(halfItem, viewport - halfItem)
    return (clampedCenter - halfItem).roundToInt()
}

internal suspend fun animateToSnapIndex(
    listState: LazyListState,
    layoutInfo: SnapperLayoutInfo,
    targetIndex: Int,
    animationSpec: AnimationSpec<Float>
) {
    val distance = layoutInfo.distanceToIndexSnap(targetIndex)
    if (distance == 0) return

    listState.scroll {
        var previous = 0f
        AnimationState(initialValue = 0f).animateTo(
            targetValue = distance.toFloat(),
            animationSpec = animationSpec
        ) {
            val delta = value - previous
            val consumed = scrollBy(delta)
            previous = value
            if (abs(delta - consumed) > 0.5f) cancelAnimation()
        }
    }
}

/**
 * Saves lyrics to a .lrc file in the same directory as the song.
 * @param context The Android context.
 * @param song The song whose lyrics are being saved.
 * @param lyrics The lyrics to save.
 * @param preferSynced Whether to prefer synced lyrics over plain.
 */
private fun saveLyricsToFile(
    context: android.content.Context,
    song: com.theveloper.pixelplay.data.model.Song,
    lyrics: Lyrics,
    preferSynced: Boolean
) {
    try {
        val songFile = File(song.path)
        val songDir = songFile.parentFile
        
        if (songDir == null || !songDir.exists()) {
            Toast.makeText(
                context,
                context.getString(R.string.lyrics_save_failed),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Create .lrc filename based on song filename
        val songNameWithoutExtension = songFile.nameWithoutExtension
        val lrcFileName = "$songNameWithoutExtension.lrc"
        val lrcFile = File(songDir, lrcFileName)
        
        // Convert lyrics to LRC format
        val lrcContent = LyricsUtils.toLrcString(lyrics, preferSynced)
        
        if (lrcContent.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.no_lyrics_to_save),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Write to file
        lrcFile.writeText(lrcContent, Charsets.UTF_8)
        
        Toast.makeText(
            context,
            context.getString(R.string.lyrics_saved_successfully),
            Toast.LENGTH_SHORT
        ).show()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(
            context,
            context.getString(R.string.lyrics_save_failed),
            Toast.LENGTH_SHORT
        ).show()
    }
}
