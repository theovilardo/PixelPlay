package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import com.theveloper.pixelplay.utils.formatListeningDurationCompact
import com.theveloper.pixelplay.utils.formatListeningDurationLong
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    statsViewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by statsViewModel.uiState.collectAsState()
    val summary = uiState.summary
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 62.dp + statusBarHeight
    val maxTopBarHeight = 176.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val scrollingDown = delta < 0

                if (!scrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsume = !(scrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsume) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val target = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != target) {
                coroutineScope.launch {
                    topBarHeight.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
    val tabsHeight = 62.dp
    val tabIndicatorExtraSpacing = 8.dp
    val tabContentSpacing = 20.dp
    var selectedTimelineMetric by rememberSaveable { mutableStateOf(TimelineMetric.ListeningTime) }
    var selectedCategoryDimension by rememberSaveable { mutableStateOf(CategoryDimension.Genre) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(nestedScrollConnection)
    ) {
        if (uiState.isLoading && summary == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val showDailyRhythm = summary?.range == StatsTimeRange.DAY || summary?.range == StatsTimeRange.WEEK

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = currentTopBarHeightDp + tabsHeight + tabIndicatorExtraSpacing + tabContentSpacing + 20.dp,
                    bottom = 32.dp + MiniPlayerHeight
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                if (showDailyRhythm) {
                    item {
                        DailyListeningDistributionSection(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
                item {
                    ListeningTimelineSection(
                        summary = summary,
                        selectedMetric = selectedTimelineMetric,
                        onMetricSelected = { selectedTimelineMetric = it },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                item {
                    CategoryMetricsSection(
                        summary = summary,
                        selectedDimension = selectedCategoryDimension,
                        onDimensionSelected = { selectedCategoryDimension = it },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                item {
                    StatsSummaryCard(
                        summary = summary,
                        isLoading = uiState.isLoading,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                item {
                    ListeningHabitsCard(
                        summary = summary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                item {
                    TopArtistsCard(
                        summary = summary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                item {
                    TopAlbumsCard(
                        summary = summary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                item {
                    SongStatsCard(
                        summary = summary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(currentTopBarHeightDp + tabsHeight + tabIndicatorExtraSpacing + tabContentSpacing)
        ) {
            Column {
                StatsTopBar(
                    collapseFraction = collapseFraction,
                    height = currentTopBarHeightDp + 8.dp,
                    onBackClick = { navController.popBackStack() }
                )

                RangeTabsHeader(
                    ranges = uiState.availableRanges,
                    selected = uiState.selectedRange,
                    onRangeSelected = statsViewModel::onRangeSelected,
                    indicatorSpacing = tabIndicatorExtraSpacing,
                    //modifier = Modifier.padding(bottom = 8.dp)
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tabContentSpacing)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsTopBar(
    collapseFraction: Float,
    height: Dp,
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
        ) {
            FilledIconButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 8.dp),
                onClick = onBackClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
            }

            ExpressiveTopBarContent(
                title = "Listening Stats",
                collapseFraction = collapseFraction,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 0.dp, end = 0.dp),
                containerHeightRange = 80.dp to 56.dp,
                titlePaddingRange = 28.dp to 44.dp,
                collapsedTitleVerticalBias = 0f
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsSummaryCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 20.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusTR = 20.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = 20.dp,
        smoothnessAsPercentTL = 60
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            //MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
                .padding(horizontal = 28.dp, vertical = 26.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = summary?.range?.displayName ?: "No listening yet",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatListeningDurationLong(summary?.totalDurationMs ?: 0L),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (summary != null) {
                        Text(
                            text = "Across ${summary.totalPlayCount} plays",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (summary != null) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
//                        SummaryHeroTile(
//                            title = "Total plays",
//                            value = summary.totalPlayCount.toString(),
//                            supporting = "Logged in this range"
//                        )
//                        SummaryHeroTile(
//                            title = "Unique songs",
//                            value = summary.uniqueSongs.toString(),
//                            supporting = "Different tracks"
//                        )
                    }

//                    SummaryProgressRow(
//                        title = "Peak day",
//                        label = summary.peakDayLabel,
//                        supporting = if (summary.peakDayDurationMs > 0L) {
//                            formatListeningDurationCompact(summary.peakDayDurationMs)
//                        } else {
//                            "No standout day yet"
//                        },
//                        progress = if (summary.totalDurationMs > 0L) {
//                            (summary.peakDayDurationMs.toFloat() / summary.totalDurationMs.toFloat()).coerceIn(0f, 1f)
//                        } else {
//                            0f
//                        }
//                    )

//                    FlowRow(
//                        horizontalArrangement = Arrangement.spacedBy(12.dp),
//                        verticalArrangement = Arrangement.spacedBy(12.dp)
//                    ) {
//                        SummaryPill(label = "Active days", value = summary.activeDays.toString())
//                        SummaryPill(
//                            label = "Avg per day",
//                            value = formatListeningDurationCompact(summary.averageDailyDurationMs)
//                        )
//                        SummaryPill(
//                            label = "Avg session",
//                            value = formatListeningDurationCompact(summary.averageSessionDurationMs)
//                        )
//                        SummaryPill(
//                            label = "Sessions/day",
//                            value = String.format(Locale.US, "%.1f", summary.averageSessionsPerDay)
//                        )
//                        SummaryPill(label = "Total sessions", value = summary.totalSessions.toString())
//                        SummaryPill(
//                            label = "Longest session",
//                            value = if (summary.longestSessionDurationMs > 0L) {
//                                formatListeningDurationCompact(summary.longestSessionDurationMs)
//                            } else {
//                                "—"
//                            }
//                        )
//                        SummaryPill(
//                            label = "Longest streak",
//                            value = if (summary.longestStreakDays > 0) "${summary.longestStreakDays} days" else "—"
//                        )
//                    }
                } else {
                    Text(
                        text = "Start playing music to unlock your insights.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SummaryHeroTile(
    title: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .widthIn(min = 160.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryProgressRow(
    title: String,
    label: String?,
    supporting: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val displayLabel = label ?: "—"
    val progressValue = progress.coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { progressValue },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RangeTabsHeader(
    ranges: List<StatsTimeRange>,
    selected: StatsTimeRange,
    onRangeSelected: (StatsTimeRange) -> Unit,
    indicatorSpacing: Dp,
    modifier: Modifier = Modifier
) {
    val selectedIndex = remember(ranges, selected) { ranges.indexOf(selected).coerceAtLeast(0) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(1f),
        color = MaterialTheme.colorScheme.surface,
        //shadowElevation = 6.dp
    ) {
        ScrollableTabRow(
            modifier = Modifier.padding(bottom = indicatorSpacing),
            selectedTabIndex = selectedIndex,
            edgePadding = 20.dp,
            divider = {},
            containerColor = Color.Transparent,
            indicator = { positions ->
                if (selectedIndex in positions.indices) {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[selectedIndex]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            }
        ) {
            ranges.forEachIndexed { index, range ->
                TabAnimation(
                    index = index,
                    selectedIndex = selectedIndex,
                    onClick = { onRangeSelected(range) },
                    selectedColor = MaterialTheme.colorScheme.primary,
                    onSelectedColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    onUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = range.displayName
                ) {
                    Text(
                        text = range.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningHabitsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Listening habits",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary == null) {
                Text(
                    text = "We will surface your habits once we know you better.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HabitMetric(
                        icon = Icons.Outlined.History,
                        label = "Total sessions",
                        value = summary.totalSessions.toString()
                    )
                    HabitMetric(
                        icon = Icons.Outlined.Hearing,
                        label = "Avg session",
                        value = formatListeningDurationCompact(summary.averageSessionDurationMs)
                    )
                    HabitMetric(
                        icon = Icons.Outlined.Bolt,
                        label = "Longest session",
                        value = if (summary.longestSessionDurationMs > 0L) {
                            formatListeningDurationCompact(summary.longestSessionDurationMs)
                        } else {
                            "—"
                        }
                    )
                    HabitMetric(
                        icon = Icons.Outlined.AutoGraph,
                        label = "Sessions/day",
                        value = String.format(Locale.US, "%.1f", summary.averageSessionsPerDay)
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                HighlightRow(
                    title = "Most active day",
                    value = summary.peakDayLabel ?: "—",
                    supporting = if (summary.peakDayDurationMs > 0L) {
                        formatListeningDurationCompact(summary.peakDayDurationMs)
                    } else {
                        "No playback yet"
                    },
                    icon = Icons.Outlined.CalendarMonth
                )
                summary.peakTimeline?.let { peak ->
                    HighlightRow(
                        title = "Peak timeline slot",
                        value = peak.label,
                        supporting = formatListeningDurationCompact(peak.totalDurationMs),
                        icon = Icons.Outlined.AutoGraph
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitMetric(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatMinutesWindowLabel(startMinute: Int, endMinute: Int): String {
    val safeStart = startMinute.coerceIn(0, 24 * 60)
    val safeEnd = endMinute.coerceIn(0, 24 * 60)
    return "${formatHourLabel(safeStart)} – ${formatHourLabel(safeEnd)}"
}

private fun formatHourLabel(minute: Int): String {
    val normalized = minute.coerceIn(0, 24 * 60)
    val hours = normalized / 60
    val mins = normalized % 60
    return String.format(Locale.getDefault(), "%02d:%02d", hours % 24, mins)
}

@Composable
private fun HighlightRow(
    title: String,
    value: String,
    supporting: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class TimelineMetric(
    val displayName: String,
    val description: String,
    val extractValue: (PlaybackStatsRepository.TimelineEntry) -> Double,
    val formatValue: (PlaybackStatsRepository.TimelineEntry) -> String
) {
    ListeningTime(
        displayName = "Listening time",
        description = "Total listening captured in the selected range.",
        extractValue = { it.totalDurationMs.toDouble() },
        formatValue = { formatListeningDurationCompact(it.totalDurationMs) }
    ),
    PlayCount(
        displayName = "Play count",
        description = "How many sessions you completed per segment.",
        extractValue = { it.playCount.toDouble() },
        formatValue = { "${it.playCount} plays" }
    ),
    AverageSession(
        displayName = "Avg. session",
        description = "Average listening length for each segment.",
        extractValue = { entry ->
            if (entry.playCount > 0) entry.totalDurationMs.toDouble() / entry.playCount.toDouble() else 0.0
        },
        formatValue = { entry ->
            val average = if (entry.playCount > 0) entry.totalDurationMs / entry.playCount else 0L
            formatListeningDurationCompact(average)
        }
    )
}

private enum class CategoryDimension(
    val displayName: String,
    val cardTitle: String,
    val highlightTitle: String
) {
    Genre(
        displayName = "Genre",
        cardTitle = "Listening by genre",
        highlightTitle = "Top genre"
    ),
    Artist(
        displayName = "Artist",
        cardTitle = "Listening by artist",
        highlightTitle = "Top artist"
    ),
    Album(
        displayName = "Album",
        cardTitle = "Listening by album",
        highlightTitle = "Top album"
    ),
    Song(
        displayName = "Song",
        cardTitle = "Listening by song",
        highlightTitle = "Top song"
    )
}

private data class CategoryMetricEntry(
    val label: String,
    val durationMs: Long,
    val supporting: String
)

@Composable
private fun DailyListeningDistributionSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Daily rhythm",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "See when you listen most across the day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val distribution = summary?.dayListeningDistribution
        val isWeeklyRange = summary?.range == StatsTimeRange.WEEK
        if (distribution == null || distribution.buckets.isEmpty()) {
            Text(
                text = "Press play to build your daily listening fingerprint.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val peakBucket = distribution.buckets.maxByOrNull { it.totalDurationMs }
            if (peakBucket != null) {
                HighlightRow(
                    title = "Peak window",
                    value = formatMinutesWindowLabel(peakBucket.startMinute, peakBucket.endMinuteExclusive),
                    supporting = formatListeningDurationCompact(peakBucket.totalDurationMs),
                    icon = Icons.Outlined.Bolt
                )
            }
            if (isWeeklyRange) {
                WeeklyDailyListeningTimeline(
                    distribution = distribution,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                DailyListeningTimeline(
                    buckets = distribution.buckets,
                    maxBucketDurationMs = distribution.maxBucketDurationMs,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HourMarkersRow()
        }
    }
}

@Composable
private fun WeeklyDailyListeningTimeline(
    distribution: PlaybackStatsRepository.DayListeningDistribution,
    modifier: Modifier = Modifier
) {
    val locale = Locale.getDefault()
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM d", locale) }
    val perDayMax = remember(distribution.days) {
        distribution.days.maxOfOrNull { day ->
            day.buckets.maxOfOrNull { it.totalDurationMs } ?: 0L
        }?.coerceAtLeast(1L) ?: 1L
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        distribution.days.forEach { day ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val dayOfWeek = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                val formattedDate = day.date.format(dateFormatter)
                Text(
                    text = "$dayOfWeek · $formattedDate",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DailyListeningTimeline(
                    buckets = day.buckets,
                    maxBucketDurationMs = perDayMax,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DailyListeningTimeline(
    buckets: List<PlaybackStatsRepository.DailyListeningBucket>,
    maxBucketDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val gradientStart = MaterialTheme.colorScheme.primary
    val gradientEnd = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalMinutes = 24f * 60f
            val maxDuration = maxBucketDurationMs.coerceAtLeast(1L).toFloat()
            buckets.forEach { bucket ->
                val startFraction = bucket.startMinute.coerceIn(0, 24 * 60).toFloat() / totalMinutes
                val endFraction = bucket.endMinuteExclusive.coerceIn(0, 24 * 60).toFloat() / totalMinutes
                val left = size.width * startFraction
                val right = size.width * endFraction
                if (right <= left) return@forEach
                val intensity = (bucket.totalDurationMs.toFloat() / maxDuration).coerceIn(0f, 1f)
                val color = lerp(
                    gradientStart.copy(alpha = 0.3f),
                    gradientEnd.copy(alpha = 0.9f),
                    intensity
                )
                drawRect(
                    color = color,
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, size.height)
                )
            }
        }
    }
}

@Composable
private fun HourMarkersRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(0, 6 * 60, 12 * 60, 18 * 60, 24 * 60).forEach { minute ->
            Text(
                text = formatHourLabel(minute),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ListeningTimelineSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    selectedMetric: TimelineMetric,
    onMetricSelected: (TimelineMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Listening timeline",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            TimelineMetric.entries.forEach { metric ->
                val isSelected = metric == selectedMetric
                FilterChip(
                    selected = isSelected,
                    onClick = { onMetricSelected(metric) },
                    label = {
                        Text(
                            text = metric.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }
        Text(
            text = selectedMetric.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val timeline = summary?.timeline.orEmpty()
        if (timeline.isEmpty() || timeline.all { it.totalDurationMs == 0L && it.playCount == 0 }) {
            Text(
                text = "Press play to build your listening timeline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                TimelineBarChart(entries = timeline, metric = selectedMetric)
            }
            summary?.peakTimeline?.let { peak ->
                HighlightRow(
                    title = "Peak segment",
                    value = peak.label,
                    supporting = when (selectedMetric) {
                        TimelineMetric.ListeningTime -> formatListeningDurationCompact(peak.totalDurationMs)
                        TimelineMetric.PlayCount -> "${peak.playCount} plays"
                        TimelineMetric.AverageSession -> {
                            val average = if (peak.playCount > 0) peak.totalDurationMs / peak.playCount else 0L
                            formatListeningDurationCompact(average)
                        }
                    },
                    icon = Icons.Outlined.AutoGraph
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryMetricsSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    selectedDimension: CategoryDimension,
    onDimensionSelected: (CategoryDimension) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Top categories",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Compare how you listen across genres, artists, albums, and songs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            CategoryDimension.entries.forEach { dimension ->
                val isSelected = dimension == selectedDimension
                FilterChip(
                    selected = isSelected,
                    onClick = { onDimensionSelected(dimension) },
                    label = {
                        Text(
                            text = dimension.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        val entries = remember(summary, selectedDimension) {
            val base = when (selectedDimension) {
                CategoryDimension.Genre -> summary?.topGenres.orEmpty().map {
                    CategoryMetricEntry(
                        label = it.genre,
                        durationMs = it.totalDurationMs,
                        supporting = "${it.playCount} plays • ${it.uniqueArtists} artists"
                    )
                }

                CategoryDimension.Artist -> summary?.topArtists.orEmpty().map {
                    CategoryMetricEntry(
                        label = it.artist,
                        durationMs = it.totalDurationMs,
                        supporting = "${it.playCount} plays • ${it.uniqueSongs} tracks"
                    )
                }

                CategoryDimension.Album -> summary?.topAlbums.orEmpty().map {
                    CategoryMetricEntry(
                        label = it.album,
                        durationMs = it.totalDurationMs,
                        supporting = "${it.playCount} plays • ${it.uniqueSongs} tracks"
                    )
                }

                CategoryDimension.Song -> summary?.topSongs.orEmpty().map {
                    val supportingParts = buildList {
                        add("${it.playCount} plays")
                        if (it.artist.isNotBlank()) {
                            add(it.artist)
                        }
                    }
                    CategoryMetricEntry(
                        label = it.title,
                        durationMs = it.totalDurationMs,
                        supporting = supportingParts.joinToString(separator = " • ")
                    )
                }
            }
            base.filter { it.durationMs > 0L }
        }

        if (entries.isEmpty()) {
            Text(
                text = "Press play to surface your listening highlights in this view.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = selectedDimension.cardTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    CategoryVerticalBarChart(entries = entries)

                    val topEntry = remember(entries) { entries.maxByOrNull { it.durationMs } }
                    if (topEntry != null) {
                        val supportingPieces = buildList {
                            add(formatListeningDurationCompact(topEntry.durationMs))
                            if (topEntry.supporting.isNotBlank()) {
                                add(topEntry.supporting)
                            }
                        }
                        HighlightRow(
                            title = selectedDimension.highlightTitle,
                            value = topEntry.label,
                            supporting = supportingPieces.joinToString(separator = " • "),
                            icon = Icons.Outlined.Hearing
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryVerticalBarChart(
    entries: List<CategoryMetricEntry>,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val maxDuration = entries.maxOf { it.durationMs }.coerceAtLeast(1L)
    val highlightDuration = entries.maxOf { it.durationMs }
    val highlightIndex = entries.indexOfFirst { it.durationMs == highlightDuration }.coerceAtLeast(0)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { index, entry ->
                val progress = (entry.durationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
                val isHighlight = entry.durationMs == highlightDuration
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatListeningDurationCompact(entry.durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(progress)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isHighlight) {
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                                MaterialTheme.colorScheme.primary
                                            )
                                        )
                                    }
                                )
                        )
                    }
                    CategoryMetricIndicator(
                        index = index,
                        highlighted = isHighlight
                    )
                }
            }
        }
        CategoryMetricsLegend(entries = entries, highlightIndex = highlightIndex)
    }
}

@Composable
private fun CategoryMetricIndicator(
    index: Int,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (highlighted) 4.dp else 0.dp
    ) {
        Text(
            text = "${index + 1}",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CategoryMetricsLegend(
    entries: List<CategoryMetricEntry>,
    highlightIndex: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryMetricIndicator(index = index, highlighted = index == highlightIndex)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.supporting.isNotBlank()) {
                        Text(
                            text = entry.supporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = formatListeningDurationCompact(entry.durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimelineBarChart(
    entries: List<PlaybackStatsRepository.TimelineEntry>,
    metric: TimelineMetric,
    modifier: Modifier = Modifier
) {
    val maxMetricValue = entries.maxOfOrNull { metric.extractValue(it) }?.coerceAtLeast(0.0) ?: 0.0
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        entries.forEach { entry ->
            val value = metric.extractValue(entry)
            val progress = if (maxMetricValue > 0) (value / maxMetricValue).toFloat().coerceIn(0f, 1f) else 0f
            val formattedValue = metric.formatValue(entry)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formattedValue,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TopArtistsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Top artists",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val artists = summary?.topArtists.orEmpty()
            if (artists.isEmpty()) {
                Text(
                    text = "Keep listening and your favorite artists will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxDuration = artists.maxOf { it.totalDurationMs }.coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    artists.forEachIndexed { index, artistSummary ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ArtistAvatar(name = artistSummary.artist)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${artistSummary.artist}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${artistSummary.playCount} plays • ${artistSummary.uniqueSongs} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatListeningDurationCompact(artistSummary.totalDurationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = (artistSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistAvatar(name: String) {
    val initials = remember(name) {
        name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TopAlbumsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Top albums",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val albums = summary?.topAlbums.orEmpty()
            if (albums.isEmpty()) {
                Text(
                    text = "Albums you revisit often will appear soon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxDuration = albums.maxOf { it.totalDurationMs }.coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    albums.forEachIndexed { index, albumSummary ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SmartImage(
                                    model = albumSummary.albumArtUri,
                                    contentDescription = albumSummary.album,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${albumSummary.album}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${albumSummary.playCount} plays • ${albumSummary.uniqueSongs} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatListeningDurationCompact(albumSummary.totalDurationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            LinearProgressIndicator(
                                progress = (albumSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongStatsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val songs = summary?.songs.orEmpty()
            var showAll by rememberSaveable(songs) { mutableStateOf(songs.size <= 8) }
            val displayedSongs = remember(songs, showAll) {
                if (showAll || songs.size <= 8) songs else songs.take(8)
            }
            val maxDuration = songs.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L
            val positions = remember(songs) { songs.mapIndexed { index, song -> song.songId to index }.toMap() }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tracks in this range",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (songs.size > 8) {
                    TextButton(onClick = { showAll = !showAll }) {
                        Text(
                            text = if (showAll || songs.size <= 8) "Show top" else "Show all",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (songs.isEmpty()) {
                Text(
                    text = "Listen to your favorites to see them highlighted here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    displayedSongs.forEach { songSummary ->
                        val position = positions[songSummary.songId] ?: songs.indexOf(songSummary)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SmartImage(
                                    model = songSummary.albumArtUri,
                                    contentDescription = songSummary.title,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${position + 1}. ${songSummary.title}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = songSummary.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${songSummary.playCount} plays • ${formatListeningDurationCompact(songSummary.totalDurationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = (songSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
