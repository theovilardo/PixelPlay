package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController

@Composable
fun SettingsTopBar(
        collapseFraction: Float,
        headerHeight: Dp,
        onBackPressed: () -> Unit,
        title: String = "Settings",
        expandedStartPadding: Dp = 20.dp,
        collapsedStartPadding: Dp = 68.dp,
        maxLines: Int = 1
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .height(headerHeight)
                            .background(surfaceColor.copy(alpha = collapseFraction))
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            FilledIconButton(
                    modifier =
                            Modifier.align(Alignment.TopStart)
                                .padding(start = 12.dp, top = 4.dp)
                                .zIndex(1f), // Ensure icon stays on top of animated text
                    onClick = onBackPressed,
                    colors =
                            IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
            ) {
                Icon(painterResource(R.drawable.rounded_arrow_back_24), contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }

            ExpressiveTopBarContent(
                    title = title,
                    collapseFraction = collapseFraction,
                    modifier = Modifier.fillMaxSize(),
                    collapsedTitleStartPadding = collapsedStartPadding,
                    expandedTitleStartPadding = expandedStartPadding,
                    maxLines = maxLines
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navController: NavController,
        playerViewModel: PlayerViewModel,
        onNavigationIconClick: () -> Unit,
        settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val playerSheetState by playerViewModel.sheetState.collectAsState()

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    // Animation effects
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) { transitionState.targetState = true }

    val transition = rememberTransition(transitionState, label = "SettingsAppearTransition")

    val contentAlpha by
            transition.animateFloat(
                    label = "ContentAlpha",
                    transitionSpec = { tween(durationMillis = 500) }
            ) { if (it) 1f else 0f }

    val contentOffset by
            transition.animateDp(
                    label = "ContentOffset",
                    transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) }
            ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp 

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction =
                1f -
                        ((topBarHeight.value - minTopBarHeightPx) /
                                        (maxTopBarHeightPx - minTopBarHeightPx))
                                .coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                                (lazyListState.firstVisibleItemIndex > 0 ||
                                        lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight =
                        (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                    lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue =
                    if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
            modifier =
                    Modifier.nestedScroll(nestedScrollConnection).fillMaxSize().graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffset.toPx()
                    }
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = currentTopBarHeightDp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
        ) {
            item {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                ExpressiveSettingsGroup {
                    val mainCategories = SettingsCategory.entries.filter { it != SettingsCategory.ABOUT && it != SettingsCategory.EQUALIZER }
                    
                    mainCategories.forEachIndexed { index, category ->
                        val colors = getCategoryColors(category, isDark)
                        
                        ExpressiveCategoryItem(
                            category = category,
                            customColors = colors,
                            onClick = {
                                navController.navigate(Screen.SettingsCategory.createRoute(category.id))
                            },
                            shape = when {
                                mainCategories.size == 1 -> RoundedCornerShape(24.dp)
                                index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                index == mainCategories.lastIndex -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                else -> RoundedCornerShape(4.dp)
                            }
                        )
                        if (index < mainCategories.lastIndex) {
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Equalizer Category (Standalone)
                ExpressiveCategoryItem(
                    category = SettingsCategory.EQUALIZER,
                    customColors = getCategoryColors(SettingsCategory.EQUALIZER, isDark),
                    onClick = { navController.navigate(Screen.Equalizer.route) }, // Direct navigation
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
                
                // About Category (Standalone)
                ExpressiveCategoryItem(
                    category = SettingsCategory.ABOUT,
                    customColors = getCategoryColors(SettingsCategory.ABOUT, isDark),
                    onClick = { navController.navigate("about") }, // Direct navigation
                    shape = RoundedCornerShape(24.dp)
                )

                // for player active:
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
        SettingsTopBar(
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackPressed = onNavigationIconClick
        )

        // Block interaction during transition
        var isTransitioning by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(com.theveloper.pixelplay.presentation.navigation.TRANSITION_DURATION.toLong())
            isTransitioning = false
        }

        if (isTransitioning) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                     awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ExpressiveCategoryItem(
    category: SettingsCategory,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(24.dp),
    customColors: Pair<Color, Color>? = null
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(88.dp) 
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp).fillMaxSize()
        ) {
            // Icon Container
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(customColors?.first ?: MaterialTheme.colorScheme.primaryContainer)
            ) {
                if (category.icon != null) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = customColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (category.iconRes != null) {
                    Icon(
                        painter = painterResource(id = category.iconRes),
                        contentDescription = null,
                        tint = customColors?.second ?: MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = category.subtitle,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Chevron or indicator
             Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                 Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ExpressiveSettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent)
    ) {
        content()
    }
}

private fun getCategoryColors(category: SettingsCategory, isDark: Boolean): Pair<Color, Color> {
    return if (isDark) {
        when (category) {
            SettingsCategory.LIBRARY -> Color(0xFF004A77) to Color(0xFFC2E7FF) 
            SettingsCategory.APPEARANCE -> Color(0xFF7D5260) to Color(0xFFFFD8E4) 
            SettingsCategory.PLAYBACK -> Color(0xFF633B48) to Color(0xFFFFD8EC) 
            SettingsCategory.AI_INTEGRATION -> Color(0xFF004F58) to Color(0xFF88FAFF) 
            SettingsCategory.DEVELOPER -> Color(0xFF324F34) to Color(0xFFCBEFD0) 
            SettingsCategory.EQUALIZER -> Color(0xFF6E4E13) to Color(0xFFFFDEAC) 
            SettingsCategory.ABOUT -> Color(0xFF3F474D) to Color(0xFFDEE3EB) 
        }
    } else {
        when (category) {
            SettingsCategory.LIBRARY -> Color(0xFFD7E3FF) to Color(0xFF005AC1)
            SettingsCategory.APPEARANCE -> Color(0xFFFFD8E4) to Color(0xFF631835)
            SettingsCategory.PLAYBACK -> Color(0xFFFFD8EC) to Color(0xFF631B4B)
            SettingsCategory.AI_INTEGRATION -> Color(0xFFCCE8EA) to Color(0xFF004F58)
            SettingsCategory.DEVELOPER -> Color(0xFFCBEFD0) to Color(0xFF042106)
            SettingsCategory.EQUALIZER -> Color(0xFFFFDEAC) to Color(0xFF281900)
            SettingsCategory.ABOUT -> Color(0xFFEFF1F7) to Color(0xFF44474F)
        }
    }
}
