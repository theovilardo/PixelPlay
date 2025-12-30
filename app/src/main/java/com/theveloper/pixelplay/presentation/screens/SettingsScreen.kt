package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.FileExplorerDialog
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.FailedSongInfo
import com.theveloper.pixelplay.presentation.viewmodel.LyricsRefreshProgress
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SettingsTopBar(
        collapseFraction: Float,
        headerHeight: Dp,
        onBackPressed: () -> Unit,
        title: String = "Settings",
        expandedTitleStartPadding: Dp = 0.dp
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val titleStartPadding = lerp(expandedTitleStartPadding, 0.dp, collapseFraction.coerceIn(0f, 1f))

    Box(
            modifier =
                    Modifier.fillMaxWidth()
                            .height(headerHeight)
                            .background(surfaceColor.copy(alpha = collapseFraction))
    ) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            FilledIconButton(
                    modifier =
                            Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp),
                    onClick = onBackPressed,
                    colors =
                            IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
            ) {
                Icon(painterResource(R.drawable.rounded_arrow_back_24), contentDescription = "Back")
            }

            ExpressiveTopBarContent(
                    title = title,
                    collapseFraction = collapseFraction,
                    modifier = Modifier.fillMaxSize().padding(start = titleStartPadding, end = 0.dp)
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
    // Recopilar el estado de la UI del ViewModel
    val uiState by settingsViewModel.uiState.collectAsState()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val currentPath by settingsViewModel.currentPath.collectAsState()
    val directoryChildren by settingsViewModel.currentDirectoryChildren.collectAsState()
    val availableStorages by settingsViewModel.availableStorages.collectAsState()
    val selectedStorageIndex by settingsViewModel.selectedStorageIndex.collectAsState()
    val isLoadingDirectories by settingsViewModel.isLoadingDirectories.collectAsState()
    val isExplorerPriming by settingsViewModel.isExplorerPriming.collectAsState()
    val isExplorerReady by settingsViewModel.isExplorerReady.collectAsState()
    val isSyncing by settingsViewModel.isSyncing.collectAsState()
    val syncProgress by settingsViewModel.syncProgress.collectAsState()
    val explorerRoot = settingsViewModel.explorerRoot()
    val isRefreshingLyrics by settingsViewModel.isRefreshingLyrics.collectAsState()
    val lyricsRefreshProgress by settingsViewModel.lyricsRefreshProgress.collectAsState()
    val lastFailedSongs by settingsViewModel.lastFailedSongs.collectAsState()

    val context = LocalContext.current

    var showExplorerWarmup by remember { mutableStateOf(false) }
    var showLyricsRefreshWarning by remember { mutableStateOf(false) }

    var showClearLyricsDialog by remember { mutableStateOf(false) }
    var showExplorerSheet by remember { mutableStateOf(false) }
    var refreshRequested by remember { mutableStateOf(false) }
    var showFailedSongsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { settingsViewModel.primeExplorer() }

    // Show completion toast when sync finishes
    LaunchedEffect(syncProgress.isCompleted, syncProgress.isRunning) {
        if (syncProgress.isCompleted && !syncProgress.isRunning && refreshRequested) {
            val songsFound = syncProgress.totalCount
            Toast.makeText(
                            context,
                            "Library refreshed: $songsFound songs found",
                            Toast.LENGTH_SHORT
                    )
                    .show()
            refreshRequested = false
        }
    }

    // Legacy fallback for isSyncing
    LaunchedEffect(isSyncing) {
        if (!isSyncing && refreshRequested && !syncProgress.isCompleted) {
            Toast.makeText(context, "Library refreshed", Toast.LENGTH_SHORT).show()
            refreshRequested = false
        }
    }

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    // Efecto para animaciones de transición
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
    val maxTopBarHeight = 180.dp // Adjusted for a less intrusive header

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

    if (isSyncing && refreshRequested) {
        RefreshingLibraryDialog(syncProgress = syncProgress)
    }

    if (isRefreshingLyrics || lyricsRefreshProgress.isComplete) {
        RefreshingLyricsDialog(
                progress = lyricsRefreshProgress,
                onDismiss = {
                    settingsViewModel.dismissLyricsRefreshDialog()
                    // Show failed songs dialog if there are failures
                    if (lyricsRefreshProgress.hasFailedSongs) {
                        showFailedSongsDialog = true
                    }
                },
                onRefreshAgain = {
                    settingsViewModel.dismissLyricsRefreshDialog()
                    settingsViewModel.refreshAllLyrics()
                }
        )
    }

    if (showFailedSongsDialog && lastFailedSongs.isNotEmpty()) {
        FailedSongsDialog(
                failedSongs = lastFailedSongs,
                onDismiss = { showFailedSongsDialog = false },
                onRefreshAgain = {
                    showFailedSongsDialog = false
                    settingsViewModel.refreshAllLyrics()
                }
        )
    }

    if (showLyricsRefreshWarning) {
        LyricsRefreshWarningDialog(
                onConfirm = {
                    showLyricsRefreshWarning = false
                    Toast.makeText(context, "Fetching lyrics…", Toast.LENGTH_SHORT).show()
                    settingsViewModel.refreshAllLyrics()
                },
                onDismiss = { showLyricsRefreshWarning = false }
        )
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
                contentPadding = PaddingValues(top = currentTopBarHeightDp),
                modifier = Modifier.fillMaxSize()
        ) {
            item(key = "music_management_section") {
                // Sección de gestión de música
                SettingsSection(
                        title = "Music Management",
                        icon = {
                            Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                ) {
                    Column(modifier = Modifier.clip(shape = RoundedCornerShape(24.dp))) {
                        SettingsItem(
                                title = "Excluded Directories",
                                subtitle =
                                        "Folders here will be skipped when scanning your library.",
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Abrir selector",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    val hasAllFilesPermission =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                Environment.isExternalStorageManager()
                                            } else true

                                    if (!hasAllFilesPermission &&
                                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                                    ) {
                                        val intent =
                                                Intent(
                                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                                )
                                        intent.data = "package:${context.packageName}".toUri()
                                        context.startActivity(intent)
                                        return@SettingsItem
                                    }

                                    showExplorerSheet = true
                                    if (!isExplorerReady && !isExplorerPriming) {
                                        settingsViewModel.primeExplorer()
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RefreshLibraryItem(
                                isSyncing = isSyncing,
                                syncProgress = syncProgress,
                                onRefresh = {
                                    if (isSyncing) {
                                        Toast.makeText(
                                                        context,
                                                        "Library is already refreshing",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        return@RefreshLibraryItem
                                    }
                                    refreshRequested = true
                                    Toast.makeText(
                                                    context,
                                                    "Refreshing library…",
                                                    Toast.LENGTH_SHORT
                                            )
                                            .show()
                                    settingsViewModel.refreshLibrary()
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RefreshLyricsItem(
                                isRefreshing = isRefreshingLyrics,
                                progress = lyricsRefreshProgress,
                                onRefresh = {
                                    if (isRefreshingLyrics) {
                                        Toast.makeText(
                                                        context,
                                                        "Lyrics refresh is already in progress",
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        return@RefreshLyricsItem
                                    }
                                    // Show warning dialog before starting refresh
                                    showLyricsRefreshWarning = true
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsItem(
                                title = "Reset Imported Lyrics",
                                subtitle = "Remove all imported lyrics from the database.",
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Outlined.ClearAll,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                onClick = { showClearLyricsDialog = true }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsItem(
                                title = "Artists",
                                subtitle = "Multi-artist parsing and organization options.",
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Outlined.Person,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Open",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = { navController.navigate(Screen.ArtistSettings.route) }
                        )
                    }
                }
            }

            item(key = "spacer_1") { Spacer(modifier = Modifier.height(16.dp)) }

            item(key = "appearance_section") {
                // Sección de apariencia
                SettingsSection(
                        title = "Appearance",
                        icon = {
                            Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                ) {
                    Column(
                            Modifier.background(
                                            color = Color.Transparent,
                                            shape = RoundedCornerShape(24.dp)
                                    )
                                    .clip(shape = RoundedCornerShape(24.dp))
                    ) {
                        ThemeSelectorItem(
                                label = "App Theme",
                                description =
                                        "Switch between light, dark, or follow system appearance.",
                                options =
                                        mapOf(
                                                AppThemeMode.LIGHT to "Light Theme",
                                                AppThemeMode.DARK to "Dark Theme",
                                                AppThemeMode.FOLLOW_SYSTEM to "Follow System"
                                        ),
                                selectedKey = uiState.appThemeMode,
                                onSelectionChanged = { settingsViewModel.setAppThemeMode(it) },
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Outlined.LightMode,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeSelectorItem(
                                label = "Player Theme",
                                description = "Choose the appearance for the floating player.",
                                options =
                                        mapOf(
                                                ThemePreference.ALBUM_ART to "Album Art",
                                                ThemePreference.DYNAMIC to "System Dynamic"
                                        ),
                                selectedKey = uiState.playerThemePreference,
                                onSelectionChanged = {
                                    settingsViewModel.setPlayerThemePreference(it)
                                },
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Outlined.PlayCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeSelectorItem(
                                label = "NavBar Style",
                                description = "Choose the appearance for the navigation bar.",
                                options =
                                        mapOf(
                                                NavBarStyle.DEFAULT to "Default",
                                                NavBarStyle.FULL_WIDTH to "Full Width"
                                        ),
                                selectedKey = uiState.navBarStyle,
                                onSelectionChanged = { settingsViewModel.setNavBarStyle(it) },
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Outlined.Style,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                        if (uiState.navBarStyle == NavBarStyle.DEFAULT) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SettingsItem(
                                    title = "NavBar Corner Radius",
                                    subtitle = "Adjust the corner radius of the navigation bar.",
                                    leadingIcon = {
                                        Icon(
                                                painter =
                                                        painterResource(
                                                                id =
                                                                        R.drawable
                                                                                .rounded_rounded_corner_24
                                                        ),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                                imageVector = Icons.Rounded.ChevronRight,
                                                contentDescription = "Adjust radius",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = { navController.navigate("nav_bar_corner_radius") }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeSelectorItem(
                                label = "Carousel Style",
                                description = "Choose the appearance for the album carousel.",
                                options =
                                        mapOf(
                                                CarouselStyle.NO_PEEK to "No Peek",
                                                CarouselStyle.ONE_PEEK to "One Peek",
                                                // CarouselStyle.TWO_PEEK to "Two Peeks"
                                                ),
                                selectedKey = uiState.carouselStyle,
                                onSelectionChanged = { settingsViewModel.setCarouselStyle(it) },
                                leadingIcon = {
                                    Icon(
                                            painter =
                                                    painterResource(
                                                            id = R.drawable.rounded_view_carousel_24
                                                    ),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeSelectorItem(
                                label = "Default Tab",
                                description = "Choose the Default launch tab.",
                                options =
                                        mapOf(
                                                LaunchTab.HOME to "Home",
                                                LaunchTab.SEARCH to "Search",
                                                LaunchTab.LIBRARY to "Library",
                                        ),
                                selectedKey = uiState.launchTab,
                                onSelectionChanged = { settingsViewModel.setLaunchTab(it) },
                                leadingIcon = {
                                    Icon(
                                            painter = painterResource(id = R.drawable.tab_24),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeSelectorItem(
                                label = "Library Navigation",
                                description = "Choose how to move between Library tabs.",
                                options =
                                        mapOf(
                                                LibraryNavigationMode.TAB_ROW to
                                                        "Tab row (default)",
                                                LibraryNavigationMode.COMPACT_PILL to
                                                        "Compact pill & grid"
                                        ),
                                selectedKey = uiState.libraryNavigationMode,
                                onSelectionChanged = {
                                    settingsViewModel.setLibraryNavigationMode(it)
                                },
                                leadingIcon = {
                                    Icon(
                                            painter =
                                                    painterResource(
                                                            id = R.drawable.rounded_library_music_24
                                                    ),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                    }
                }
            }

            item(key = "spacer_2") { Spacer(modifier = Modifier.height(16.dp)) }

            item(key = "playback_section") {
                SettingsSection(
                        title = "Playback",
                        icon = {
                            Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                ) {
                    Column(
                            Modifier.background(
                                            color = Color.Transparent,
                                            shape = RoundedCornerShape(24.dp)
                                    )
                                    .clip(shape = RoundedCornerShape(24.dp))
                    ) {
                        ThemeSelectorItem(
                                label = "Keep playing after closing",
                                description =
                                        "If off, removing the app from recents will stop playback.",
                                options = mapOf("true" to "On", "false" to "Off"),
                                selectedKey =
                                        if (uiState.keepPlayingInBackground) "true" else "false",
                                onSelectionChanged = { key ->
                                    settingsViewModel.setKeepPlayingInBackground(key.toBoolean())
                                },
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeSelectorItem(
                                label = "Auto-play on cast connect/disconnect",
                                description =
                                        "Start playing immediately after switching cast connections.",
                                options = mapOf("false" to "Enabled", "true" to "Disabled"),
                                selectedKey = if (uiState.disableCastAutoplay) "true" else "false",
                                onSelectionChanged = { key ->
                                    settingsViewModel.setDisableCastAutoplay(key.toBoolean())
                                },
                                leadingIcon = {
                                    Icon(
                                            painter =
                                                    painterResource(
                                                            id = R.drawable.rounded_cast_24
                                                    ),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeSelectorItem(
                                label = "Crossfade",
                                description = "Enable smooth transition between songs.",
                                options = mapOf("true" to "Enabled", "false" to "Disabled"),
                                selectedKey = if (uiState.isCrossfadeEnabled) "true" else "false",
                                onSelectionChanged = { key ->
                                    settingsViewModel.setCrossfadeEnabled(key.toBoolean())
                                },
                                leadingIcon = {
                                    Icon(
                                            painter =
                                                    painterResource(
                                                            id =
                                                                    R.drawable
                                                                            .rounded_align_justify_space_even_24
                                                    ),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                        )
                        if (uiState.isCrossfadeEnabled) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SliderSettingsItem(
                                    label = "Crossfade Duration",
                                    value = uiState.crossfadeDuration.toFloat(),
                                    valueRange = 2000f..12000f,
                                    onValueChange = {
                                        settingsViewModel.setCrossfadeDuration(it.toInt())
                                    },
                                    valueText = { value -> "${(value / 1000).toInt()}s" }
                            )
                        }
                    }
                }
            }

            item(key = "ai_section") {
                SettingsSection(
                        title = "AI Integration (Beta)",
                        icon = {
                            Icon(
                                    painter = painterResource(id = R.drawable.gemini_ai),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                ) {
                    GeminiApiKeyItem(
                            apiKey = geminiApiKey,
                            onApiKeyChange = { settingsViewModel.onGeminiApiKeyChange(it) },
                            title = "Gemini API Key",
                            subtitle = "Needed for AI-powered features."
                    )
                }
            }

            item(key = "spacer_3") { Spacer(modifier = Modifier.height(16.dp)) }

            item(key = "dev_options_section") {
                // Sección de Opciones de Desarrollador
                SettingsSection(
                        title = "Developer Options",
                        icon = {
                            Icon(
                                    imageVector = Icons.Outlined.Style,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                ) {
                    Column(
                            Modifier.background(
                                            color = Color.Transparent,
                                            shape = RoundedCornerShape(24.dp)
                                    )
                                    .clip(shape = RoundedCornerShape(24.dp))
                    ) {
                        SettingsItem(
                                title = "Experimental",
                                subtitle = "Player UI loading experiments and toggles.",
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Rounded.Science,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Open experimental settings",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = { navController.navigate(Screen.Experimental.route) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsItem(
                                title = "Force Daily Mix Regeneration",
                                subtitle = "Re-creates the daily mix playlist immediately.",
                                leadingIcon = {
                                    Icon(
                                            painter =
                                                    painterResource(
                                                            id = R.drawable.rounded_instant_mix_24
                                                    ),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                onClick = { playerViewModel.forceUpdateDailyMix() }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsItem(
                                title = "Trigger Test Crash",
                                subtitle = "Simulate a crash to test the crash reporting system.",
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Outlined.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = { settingsViewModel.triggerTestCrash() }
                        )
                    }
                }
            }

            item(key = "spacer_4") { Spacer(modifier = Modifier.height(16.dp)) }

            item(key = "about_section") {
                // About section
                SettingsSection(
                        title = "About",
                        icon = {
                            Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                ) {
                    Column(
                            Modifier.background(
                                            color = Color.Transparent,
                                            shape = RoundedCornerShape(24.dp)
                                    )
                                    .clip(shape = RoundedCornerShape(24.dp))
                    ) {
                        SettingsItem(
                                title = "About PixelPlayer",
                                subtitle = "App version, credits, and more.",
                                leadingIcon = {
                                    Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Navigate to about screen",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = { navController.navigate("about") }
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(MiniPlayerHeight + 36.dp))
            }
        }
        SettingsTopBar(
                collapseFraction = collapseFraction,
                headerHeight = currentTopBarHeightDp,
                onBackPressed = onNavigationIconClick
        )
    }

    FileExplorerDialog(
            visible = showExplorerSheet,
            currentPath = currentPath,
            directoryChildren = directoryChildren,
            availableStorages = availableStorages,
            selectedStorageIndex = selectedStorageIndex,
            isLoading = isLoadingDirectories,
            isAtRoot = settingsViewModel.isAtRoot(),
            rootDirectory = explorerRoot,
            onNavigateTo = settingsViewModel::loadDirectory,
            onNavigateUp = settingsViewModel::navigateUp,
            onNavigateHome = { settingsViewModel.loadDirectory(explorerRoot) },
            onToggleAllowed = settingsViewModel::toggleDirectoryAllowed,
            onRefresh = settingsViewModel::refreshExplorer,
            onStorageSelected = settingsViewModel::selectStorage,
            onDone = { showExplorerSheet = false },
            onDismiss = { showExplorerSheet = false }
    )

    // Reset lyrics dialog
    if (showClearLyricsDialog) {
        AlertDialog(
                icon = { Icon(imageVector = Icons.Outlined.Warning, contentDescription = null) },
                title = { Text(text = "Reset imported lyrics?") },
                text = { Text(text = "This action cannot be undone.") },
                onDismissRequest = { showClearLyricsDialog = false },
                confirmButton = {
                    TextButton(
                            onClick = {
                                showClearLyricsDialog = false
                                playerViewModel.resetAllLyrics()
                            }
                    ) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { showClearLyricsDialog = false }) { Text("Cancel") }
                }
        )
    }
}

@Composable
private fun ExplorerWarmupDialog(onCancel: () -> Unit) {
    AlertDialog(
            onDismissRequest = onCancel,
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancel) { Text(text = "Cancel") } },
            icon = { CircularProgressIndicator() },
            title = { Text(text = "Preparing folders") },
            text = { Text(text = "Loading your storage map so the explorer opens instantly.") }
    )
}

@Composable
private fun LyricsRefreshWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                        text = "Refresh All Lyrics?",
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                            text =
                                    "This operation will fetch lyrics for all songs in your library and may take a considerable amount of time.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                            text = "Please keep the app open during this process.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                            text = "Continue",
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel", fontFamily = GoogleSansRounded)
                }
            }
    )
}

@Composable
private fun RefreshingLibraryDialog(syncProgress: SyncProgress) {
    // Animate progress for smooth transitions
    val animatedProgress by
            androidx.compose.animation.core.animateFloatAsState(
                    targetValue = syncProgress.progress,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                    label = "libraryProgress"
            )

    AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            icon = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                    @OptIn(ExperimentalMaterial3ExpressiveApi::class) ContainedLoadingIndicator()
                }
            },
            title = { Text(text = "Refreshing library", fontFamily = GoogleSansRounded) },
            text = {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    if (syncProgress.hasProgress) {
                        Text(
                                text =
                                        "Scanned ${syncProgress.currentCount} of ${syncProgress.totalCount} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        LinearWavyProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth(),
                                amplitude = { 1f },
                                wavelength = 24.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                                text = "Rebuilding your music library. This may take a moment.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded
                        )
                    }
                }
            }
    )
}

@Composable
private fun RefreshLibraryItem(
        isSyncing: Boolean,
        syncProgress: SyncProgress,
        onRefresh: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = "Refresh Library",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = "Tap the sync button to rescan and update your library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(
                        onClick = onRefresh,
                        enabled = !isSyncing,
                        colors =
                                IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = "Refresh library",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (isSyncing) {
                Spacer(modifier = Modifier.height(12.dp))
                if (syncProgress.hasProgress) {
                    LinearProgressIndicator(
                            progress = { syncProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text =
                                    "Scanned ${syncProgress.currentCount} of ${syncProgress.totalCount} songs",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                            text = "Refreshing library…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshingLyricsDialog(
        progress: LyricsRefreshProgress,
        onDismiss: () -> Unit,
        onRefreshAgain: () -> Unit
) {
    val title = if (progress.isComplete) "Lyrics Refresh Complete" else "Fetching Lyrics"

    // Animate progress for smooth transitions
    val animatedProgress by
            androidx.compose.animation.core.animateFloatAsState(
                    targetValue = progress.progress,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
                    label = "lyricsProgress"
            )

    AlertDialog(
            onDismissRequest = { if (progress.isComplete) onDismiss() },
            confirmButton = {
                if (progress.isComplete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (progress.hasFailedSongs) {
                            TextButton(onClick = onRefreshAgain) {
                                Text(text = "Refresh Again", fontFamily = GoogleSansRounded)
                            }
                        }
                        TextButton(onClick = onDismiss) {
                            Text(
                                    text = if (progress.hasFailedSongs) "View Failed" else "Done",
                                    fontFamily = GoogleSansRounded
                            )
                        }
                    }
                }
            },
            icon = {
                if (!progress.isComplete) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                        ContainedLoadingIndicator()
                    }
                }
            },
            title = { Text(text = title, fontFamily = GoogleSansRounded) },
            text = {
                Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    if (progress.hasProgress) {
                        if (!progress.isComplete) {
                            Text(
                                    text =
                                            "Processing ${progress.currentCount} of ${progress.totalSongs} songs",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = GoogleSansRounded
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
                            LinearWavyProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    amplitude = { 1f },
                                    wavelength = 24.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                    text = "${(progress.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                    text = "✓ Saved: ${progress.savedCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                    text = "✗ Not found: ${progress.notFoundCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                    text = "→ Skipped: ${progress.skippedCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                                text = "Preparing to fetch lyrics...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded
                        )
                    }
                }
            }
    )
}

@Composable
private fun FailedSongsDialog(
        failedSongs: List<FailedSongInfo>,
        onDismiss: () -> Unit,
        onRefreshAgain: () -> Unit
) {
    AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRefreshAgain) {
                        Text(text = "Refresh Again", fontFamily = GoogleSansRounded)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = "Done", fontFamily = GoogleSansRounded)
                    }
                }
            },
            title = {
                Text(text = "Failed Songs (${failedSongs.size})", fontFamily = GoogleSansRounded)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                            text =
                                    "These songs couldn't be found. You can add lyrics manually from the Now Playing screen.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable list of failed songs
                    androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(250.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(failedSongs.size) { index ->
                            val song = failedSongs[index]
                            Surface(
                                    color =
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.5f
                                            ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                            text = song.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                            text = song.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = GoogleSansRounded,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
    )
}

@Composable
private fun RefreshLyricsItem(
        isRefreshing: Boolean,
        progress: LyricsRefreshProgress,
        onRefresh: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            painter = painterResource(id = R.drawable.rounded_lyrics_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = "Refresh Lyrics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = "Automatically fetch lyrics for all songs using lrclib.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledIconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        colors =
                                IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                ) {
                    Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = "Refresh lyrics",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            if (isRefreshing && progress.hasProgress) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text =
                                "Processing ${progress.currentCount} of ${progress.totalSongs} songs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}

@Composable
fun SettingsItem(
        title: String,
        subtitle: String,
        leadingIcon: @Composable () -> Unit,
        trailingIcon: @Composable () -> Unit = {},
        onClick: () -> Unit
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClick)
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center
            ) { leadingIcon() }

            Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                trailingIcon()
            }
        }
    }
}

@Composable
fun SwitchSettingItem(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        leadingIcon: @Composable (() -> Unit)? = null,
        enabled: Boolean = true
) {
    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Box(
                        modifier = Modifier.padding(end = 4.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) { leadingIcon() }
            }

            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                                if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Switch(
                    checked = checked,
                    onCheckedChange = { if (enabled) onCheckedChange(it) },
                    enabled = enabled,
                    colors =
                            SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorItem(
        label: String,
        description: String,
        options: Map<String, String>,
        selectedKey: String,
        onSelectionChanged: (String) -> Unit,
        leadingIcon: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options[selectedKey] ?: selectedKey

    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier =
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        expanded = true
                    }
    ) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Box(
                    modifier = Modifier.padding(end = 16.dp).size(24.dp),
                    contentAlignment = Alignment.Center
            ) { leadingIcon() }

            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                                text = selectedOption,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Abrir menú",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }

    if (expanded) {
        ModalBottomSheet(
                onDismissRequest = { expanded = false },
                sheetState = rememberModalBottomSheetState(),
                dragHandle = { BottomSheetDefaults.DragHandle() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                options.forEach { (key, name) ->
                    val isSelected = key == selectedKey

                    Surface(
                            color =
                                    if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                onSelectionChanged(key)
                                                expanded = false
                                            }
                    ) {
                        Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color =
                                            if (isSelected)
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                            )

                            if (isSelected) {
                                Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Seleccionado",
                                        tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SliderSettingsItem(
        label: String,
        value: Float,
        valueRange: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit,
        valueText: (Float) -> String
) {
    var sliderValue by remember(value) { mutableStateOf(value) }

    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()) {
            Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = valueText(sliderValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = { onValueChange(sliderValue) },
                    valueRange = valueRange,
                    steps = ((valueRange.endInclusive - valueRange.start) / 1000).toInt() - 1,
                    modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiApiKeyItem(
        apiKey: String,
        onApiKeyChange: (String) -> Unit,
        title: String,
        subtitle: String
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val uiState by settingsViewModel.uiState.collectAsState()
    val selectedModel by settingsViewModel.geminiModel.collectAsState()
    var isModelDropdownExpanded by remember { mutableStateOf(false) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // Fetch models when API key becomes available
    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank() && uiState.availableModels.isEmpty() && !uiState.isLoadingModels) {
            settingsViewModel.fetchAvailableModels(apiKey)
        }
    }

    Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                        modifier = Modifier.padding(end = 16.dp).size(24.dp),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            painter = painterResource(id = R.drawable.gemini_ai),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                                painter = painterResource(id = R.drawable.rounded_key_vertical_24),
                                contentDescription = null,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    visualTransformation =
                            if (isApiKeyVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                    imageVector =
                                            if (isApiKeyVisible) Icons.Rounded.VisibilityOff
                                            else Icons.Rounded.Visibility,
                                    contentDescription =
                                            if (isApiKeyVisible) "Ocultar API Key"
                                            else "Mostrar API Key"
                            )
                        }
                    }
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (apiKey.isBlank()) {
                val context = LocalContext.current
                val url = "https://aistudio.google.com/app/apikey"
                val annotatedString = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append("Get it here: ")
                    }
                    withStyle(
                            style =
                                    SpanStyle(
                                            color = MaterialTheme.colorScheme.primary,
                                            textDecoration = TextDecoration.Underline
                                    )
                    ) { append(url) }
                }

                Text(
                        text = annotatedString,
                        modifier =
                                Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                    context.startActivity(intent)
                                }
                )
            } else {
                // Show model selector when API key is present
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                        text = "Model",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                )

                ExposedDropdownMenuBox(
                        expanded = isModelDropdownExpanded,
                        onExpandedChange = {
                            if (!uiState.isLoadingModels && uiState.availableModels.isNotEmpty()) {
                                isModelDropdownExpanded = !isModelDropdownExpanded
                            }
                        }
                ) {
                    OutlinedTextField(
                            value =
                                    uiState.availableModels
                                            .find { it.name == selectedModel }
                                            ?.displayName
                                            ?: selectedModel.ifEmpty { "Select a model" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Gemini Model") },
                            trailingIcon = {
                                if (uiState.isLoadingModels) {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                    )
                                } else {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = isModelDropdownExpanded
                                    )
                                }
                            },
                            modifier = Modifier.menuAnchor(),
                            //
                            // .menuAnchor(ExposedDropdownMenuBoxScope.MenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(10.dp),
                            enabled =
                                    !uiState.isLoadingModels && uiState.availableModels.isNotEmpty()
                    )

                    ExposedDropdownMenu(
                            expanded = isModelDropdownExpanded,
                            onDismissRequest = { isModelDropdownExpanded = false }
                    ) {
                        uiState.availableModels.forEach { model ->
                            DropdownMenuItem(
                                    text = {
                                        Text(
                                                text = model.displayName,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    onClick = {
                                        settingsViewModel.onGeminiModelChange(model.name)
                                        isModelDropdownExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }

                if (uiState.isLoadingModels) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                            text = "Fetching available models...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (uiState.modelsFetchError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                            text = "Error: ${uiState.modelsFetchError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                    )
                }

                // System Prompt Section
                Spacer(modifier = Modifier.height(16.dp))

                val systemPrompt by settingsViewModel.geminiSystemPrompt.collectAsState()
                var showSystemPromptDialog by remember { mutableStateOf(false) }

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "System Prompt",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(onClick = { showSystemPromptDialog = true }) { Text("Modify") }
                }

                if (showSystemPromptDialog) {
                    SystemPromptDialog(
                            currentPrompt = systemPrompt,
                            onDismiss = { showSystemPromptDialog = false },
                            onSave = { newPrompt ->
                                settingsViewModel.onGeminiSystemPromptChange(newPrompt)
                                showSystemPromptDialog = false
                            },
                            onReset = { settingsViewModel.resetGeminiSystemPrompt() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptDialog(
        currentPrompt: String,
        onDismiss: () -> Unit,
        onSave: (String) -> Unit,
        onReset: () -> Unit
) {
    var editedPrompt by remember { mutableStateOf(currentPrompt) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(currentPrompt) { editedPrompt = currentPrompt }

    ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxHeight(1f),
            dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            // Header
            Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "System Prompt",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                        onClick = {
                            onReset()
                            // Reset the edited prompt to the default value immediately
                            editedPrompt =
                                    com.theveloper.pixelplay.data.preferences
                                            .UserPreferencesRepository.DEFAULT_SYSTEM_PROMPT
                        }
                ) { Text("Reset") }
            }

            Text(
                    text =
                            "Customize the AI's behavior and personality by modifying the system prompt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
            )

            // Text field for editing prompt with floating clear button
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                OutlinedTextField(
                        value = editedPrompt,
                        onValueChange = { editedPrompt = it },
                        modifier = Modifier.fillMaxSize(),
                        placeholder = { Text("Enter system prompt...") },
                        shape = RoundedCornerShape(16.dp),
                        minLines = 8,
                        maxLines = 20,
                        textStyle = MaterialTheme.typography.bodyMedium
                )

                // Floating clear button
                if (editedPrompt.isNotEmpty()) {
                    FloatingActionButton(
                            onClick = { editedPrompt = "" },
                            modifier =
                                    Modifier.align(Alignment.BottomEnd).padding(16.dp).size(48.dp),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Clear all text"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f)
                ) { Text("Cancel") }

                FilledIconButton(
                        onClick = {
                            scope.launch {
                                onSave(editedPrompt)
                                // Show toast
                                android.widget.Toast.makeText(
                                                context,
                                                "System prompt saved successfully",
                                                android.widget.Toast.LENGTH_SHORT
                                        )
                                        .show()
                                // Smooth dismiss animation
                                sheetState.hide()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors =
                                IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                )
                ) {
                    Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                                text = "Save",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
