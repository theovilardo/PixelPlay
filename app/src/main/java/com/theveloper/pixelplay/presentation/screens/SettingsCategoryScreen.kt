package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.FileExplorerDialog
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.LyricsRefreshProgress
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCategoryScreen(
    categoryId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    statsViewModel: com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val category = SettingsCategory.fromId(categoryId) ?: return
    val context = LocalContext.current
    
    // State Collection (Duplicated from SettingsScreen for now to ensure functionality)
    val uiState by settingsViewModel.uiState.collectAsState()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()
    val geminiModel by settingsViewModel.geminiModel.collectAsState()
    val geminiSystemPrompt by settingsViewModel.geminiSystemPrompt.collectAsState()
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

    // Local State
    var showExplorerSheet by remember { mutableStateOf(false) }
    var refreshRequested by remember { mutableStateOf(false) }
    var showClearLyricsDialog by remember { mutableStateOf(false) }
    var showRebuildDatabaseWarning by remember { mutableStateOf(false) }
    var showRegenerateDailyMixDialog by remember { mutableStateOf(false) }
    var showRegenerateStatsDialog by remember { mutableStateOf(false) }

    // Fetch models on page load when API key exists and models are not already loaded
    LaunchedEffect(category, geminiApiKey) {
        if (category == SettingsCategory.AI_INTEGRATION && 
            geminiApiKey.isNotBlank() && 
            uiState.availableModels.isEmpty() && 
            !uiState.isLoadingModels) {
            settingsViewModel.fetchAvailableModels(geminiApiKey)
        }
    }

    // TopBar Animations (identical to SettingsScreen)
    // TopBar Animations (identical to SettingsScreen)
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    val isLongTitle = category.title.length > 13
    
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = if (isLongTitle) 200.dp else 180.dp //for 2 lines use 220 and make text use \n

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    
    val titleMaxLines = if (isLongTitle) 2 else 1

    val topBarHeight = remember(maxTopBarHeightPx) { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value, maxTopBarHeightPx) {
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
            Modifier.nestedScroll(nestedScrollConnection).fillMaxSize()
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
        ) {
            item {
               // Use a simple Column for now, or ExpressiveSettingsGroup if preferred strictly for items
               Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Transparent) 
               ) {
                    when (category) {
                        SettingsCategory.LIBRARY -> {
                             SettingsItem(
                                title = "Excluded Directories",
                                subtitle = "Folders here will be skipped when scanning your library.",
                                leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary) },
                                trailingIcon = { Icon(Icons.Rounded.ChevronRight, "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = {
                                    val hasAllFilesPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        Environment.isExternalStorageManager()
                                    } else true

                                    if (!hasAllFilesPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
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
                            Spacer(Modifier.height(4.dp))
                            RefreshLibraryItem(
                                isSyncing = isSyncing,
                                syncProgress = syncProgress,
                                onFullSync = {
                                    if (isSyncing) return@RefreshLibraryItem
                                    refreshRequested = true
                                    Toast.makeText(context, "Full rescan started…", Toast.LENGTH_SHORT).show()
                                    settingsViewModel.fullSyncLibrary()
                                },
                                onRebuild = {
                                    if (isSyncing) return@RefreshLibraryItem
                                    showRebuildDatabaseWarning = true
                                }
                            )

                            Spacer(Modifier.height(4.dp))

                            SettingsItem(
                                title = "Reset Imported Lyrics",
                                subtitle = "Remove all imported lyrics from the database.",
                                leadingIcon = { Icon(Icons.Outlined.ClearAll, null, tint = MaterialTheme.colorScheme.secondary) },
                                onClick = { showClearLyricsDialog = true }
                            )
                            Spacer(Modifier.height(4.dp))
                            ThemeSelectorItem(
                                label = "Lyrics Source Priority",
                                description = "Choose which source to try first when fetching lyrics.",
                                options = mapOf(
                                    LyricsSourcePreference.EMBEDDED_FIRST.name to "Embedded First",
                                    LyricsSourcePreference.API_FIRST.name to "Online First",
                                    LyricsSourcePreference.LOCAL_FIRST.name to "Local (.lrc) First"
                                ),
                                selectedKey = uiState.lyricsSourcePreference.name,
                                onSelectionChanged = { key ->
                                    settingsViewModel.setLyricsSourcePreference(
                                        LyricsSourcePreference.fromName(key)
                                    )
                                },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            SwitchSettingItem(
                                title = "Auto-scan .lrc files",
                                subtitle = "Automatically scan and assign .lrc files in the same folder during library sync.",
                                checked = uiState.autoScanLrcFiles,
                                onCheckedChange = { settingsViewModel.setAutoScanLrcFiles(it) },
                                leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            SettingsItem(
                                title = "Artists",
                                subtitle = "Multi-artist parsing and organization options.",
                                leadingIcon = { Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.secondary) },
                                trailingIcon = { Icon(Icons.Rounded.ChevronRight, "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { navController.navigate(Screen.ArtistSettings.route) }
                            )
                        }
                        SettingsCategory.APPEARANCE -> {
                            val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsState()

                            SwitchSettingItem(
                                title = "Use Smooth Corners",
                                subtitle = "Use complex shaped corners effectively improving aesthetics but may affect performance on low-end devices",
                                checked = useSmoothCorners,
                                onCheckedChange = settingsViewModel::setUseSmoothCorners,
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_rounded_corner_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            
                            ThemeSelectorItem(
                                label = "App Theme",
                                description = "Switch between light, dark, or follow system appearance.",
                                options = mapOf(
                                    AppThemeMode.LIGHT to "Light Theme",
                                    AppThemeMode.DARK to "Dark Theme",
                                    AppThemeMode.FOLLOW_SYSTEM to "Follow System"
                                ),
                                selectedKey = uiState.appThemeMode,
                                onSelectionChanged = { settingsViewModel.setAppThemeMode(it) },
                                leadingIcon = { Icon(Icons.Outlined.LightMode, null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            ThemeSelectorItem(
                                label = "Player Theme",
                                description = "Choose the appearance for the floating player.",
                                options = mapOf(
                                    ThemePreference.ALBUM_ART to "Album Art",
                                    ThemePreference.DYNAMIC to "System Dynamic"
                                ),
                                selectedKey = uiState.playerThemePreference,
                                onSelectionChanged = { settingsViewModel.setPlayerThemePreference(it) },
                                leadingIcon = { Icon(Icons.Outlined.PlayCircle, null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            ThemeSelectorItem(
                                label = "NavBar Style",
                                description = "Choose the appearance for the navigation bar.",
                                options = mapOf(
                                    NavBarStyle.DEFAULT to "Default",
                                    NavBarStyle.FULL_WIDTH to "Full Width"
                                ),
                                selectedKey = uiState.navBarStyle,
                                onSelectionChanged = { settingsViewModel.setNavBarStyle(it) },
                                leadingIcon = { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            // if (uiState.navBarStyle == NavBarStyle.DEFAULT) { // Allow for both modes now
                                Spacer(Modifier.height(4.dp))
                                SettingsItem(
                                    title = "NavBar Corner Radius",
                                    subtitle = "Adjust the corner radius of the navigation bar.",
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_rounded_corner_24), null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { navController.navigate("nav_bar_corner_radius") }
                                )
                            //}
                            Spacer(Modifier.height(4.dp))
                            ThemeSelectorItem(
                                label = "Carousel Style",
                                description = "Choose the appearance for the album carousel.",
                                options = mapOf(
                                    CarouselStyle.NO_PEEK to "No Peek",
                                    CarouselStyle.ONE_PEEK to "One Peek"
                                ),
                                selectedKey = uiState.carouselStyle,
                                onSelectionChanged = { settingsViewModel.setCarouselStyle(it) },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_view_carousel_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            ThemeSelectorItem(
                                label = "Default Tab",
                                description = "Choose the Default launch tab.",
                                options = mapOf(
                                    LaunchTab.HOME to "Home",
                                    LaunchTab.SEARCH to "Search",
                                    LaunchTab.LIBRARY to "Library",
                                ),
                                selectedKey = uiState.launchTab,
                                onSelectionChanged = { settingsViewModel.setLaunchTab(it) },
                                leadingIcon = { Icon(painterResource(R.drawable.tab_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            ThemeSelectorItem(
                                label = "Library Navigation",
                                description = "Choose how to move between Library tabs.",
                                options = mapOf(
                                    LibraryNavigationMode.TAB_ROW to "Tab row (default)",
                                    LibraryNavigationMode.COMPACT_PILL to "Compact pill & grid"
                                ),
                                selectedKey = uiState.libraryNavigationMode,
                                onSelectionChanged = { settingsViewModel.setLibraryNavigationMode(it) },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_library_music_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                        }
                        SettingsCategory.PLAYBACK -> {
                            ThemeSelectorItem(
                                label = "Keep playing after closing",
                                description = "If off, removing the app from recents will stop playback.",
                                options = mapOf("true" to "On", "false" to "Off"),
                                selectedKey = if (uiState.keepPlayingInBackground) "true" else "false",
                                onSelectionChanged = { settingsViewModel.setKeepPlayingInBackground(it.toBoolean()) },
                                leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            ThemeSelectorItem(
                                label = "Auto-play on cast connect/disconnect",
                                description = "Start playing immediately after switching cast connections.",
                                options = mapOf("false" to "Enabled", "true" to "Disabled"),
                                selectedKey = if (uiState.disableCastAutoplay) "true" else "false",
                                onSelectionChanged = { settingsViewModel.setDisableCastAutoplay(it.toBoolean()) },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_cast_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            ThemeSelectorItem(
                                label = "Crossfade",
                                description = "Enable smooth transition between songs.",
                                options = mapOf("true" to "Enabled", "false" to "Disabled"),
                                selectedKey = if (uiState.isCrossfadeEnabled) "true" else "false",
                                onSelectionChanged = { settingsViewModel.setCrossfadeEnabled(it.toBoolean()) },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_align_justify_space_even_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            if (uiState.isCrossfadeEnabled) {
                                Spacer(Modifier.height(4.dp))
                                SliderSettingsItem(
                                    label = "Crossfade Duration",
                                    value = uiState.crossfadeDuration.toFloat(),
                                    valueRange = 2000f..12000f,
                                    onValueChange = { settingsViewModel.setCrossfadeDuration(it.toInt()) },
                                    valueText = { value -> "${(value / 1000).toInt()}s" }
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            SwitchSettingItem(
                                title = "Persistent Shuffle",
                                subtitle = "Remember shuffle setting even after closing the app.",
                                checked = uiState.persistentShuffleEnabled,
                                onCheckedChange = { settingsViewModel.setPersistentShuffleEnabled(it) },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_shuffle_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            SwitchSettingItem(
                                title = "Show queue history",
                                subtitle = "Show previously played songs in the queue.",
                                checked = uiState.showQueueHistory,
                                onCheckedChange = { settingsViewModel.setShowQueueHistory(it) },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_queue_music_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            SettingsItem(
                                title = "Battery Optimization",
                                subtitle = "Disable battery optimization to prevent playback interruptions.",
                                onClick = {
                                    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                    if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                                        Toast.makeText(context, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
                                        return@SettingsItem
                                    }
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = "package:${context.packageName}".toUri()
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        try {
                                            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            context.startActivity(fallbackIntent)
                                        } catch (e2: Exception) {
                                            Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_all_inclusive_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                            Spacer(Modifier.height(4.dp))
                            SwitchSettingItem(
                                title = "Tap background closes player",
                                subtitle = "Tap the blurred background to close the player sheet.",
                                checked = uiState.tapBackgroundClosesPlayer,
                                onCheckedChange = { settingsViewModel.setTapBackgroundClosesPlayer(it) },
                                leadingIcon = { Icon(painterResource(R.drawable.rounded_touch_app_24), null, tint = MaterialTheme.colorScheme.secondary) }
                            )
                        }
                        SettingsCategory.AI_INTEGRATION -> {
                            GeminiApiKeyItem(
                                apiKey = geminiApiKey,
                                onApiKeySave = { settingsViewModel.onGeminiApiKeyChange(it) },
                                title = "Gemini API Key",
                                subtitle = "Needed for AI-powered features."
                            )
                            
                            // Show loading, error, or model selector based on state
                            if (uiState.isLoadingModels) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Loading available models...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else if (uiState.modelsFetchError != null) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = uiState.modelsFetchError ?: "Error loading models",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else if (uiState.availableModels.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                ThemeSelectorItem(
                                    label = "AI Model",
                                    description = "Select the Gemini model to use.",
                                    options = uiState.availableModels.associate { it.name to it.displayName },
                                    selectedKey = geminiModel.ifEmpty { uiState.availableModels.firstOrNull()?.name ?: "" },
                                    onSelectionChanged = { settingsViewModel.onGeminiModelChange(it) },
                                    leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }
                            
                            // System Prompt
                            Spacer(Modifier.height(8.dp))
                            GeminiSystemPromptItem(
                                systemPrompt = geminiSystemPrompt,
                                defaultPrompt = com.theveloper.pixelplay.data.preferences.UserPreferencesRepository.DEFAULT_SYSTEM_PROMPT,
                                onSystemPromptSave = { settingsViewModel.onGeminiSystemPromptChange(it) },
                                onReset = { settingsViewModel.resetGeminiSystemPrompt() },
                                title = "System Prompt",
                                subtitle = "Customize how the AI behaves."
                            )
                        }
                        SettingsCategory.DEVELOPER -> {
                             SettingsItem(
                                title = "Experimental",
                                subtitle = "Player UI loading experiments and toggles.",
                                leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.secondary) },
                                trailingIcon = { Icon(Icons.Rounded.ChevronRight, "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { navController.navigate(Screen.Experimental.route) }
                            )
                            Spacer(Modifier.height(4.dp))
                            ActionSettingsItem(
                                title = "Force Daily Mix Regeneration",
                                subtitle = "Re-creates the daily mix playlist immediately.",
                                icon = { Icon(painterResource(R.drawable.rounded_instant_mix_24), null, tint = MaterialTheme.colorScheme.secondary) },
                                primaryActionLabel = "Regenerate Daily Mix",
                                onPrimaryAction = { showRegenerateDailyMixDialog = true }
                            )
                            Spacer(Modifier.height(4.dp))
                            ActionSettingsItem(
                                title = "Force Stats Regeneration",
                                subtitle = "Clears cache and recalculates playback statistics.",
                                icon = { Icon(painterResource(R.drawable.rounded_monitoring_24), null, tint = MaterialTheme.colorScheme.secondary) },
                                primaryActionLabel = "Regenerate Stats",
                                onPrimaryAction = { showRegenerateStatsDialog = true }
                            )
                            Spacer(Modifier.height(4.dp))
                            SettingsItem(
                                title = "Test Setup Flow",
                                subtitle = "Launch the onboarding setup screen for testing.",
                                leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.tertiary) },
                                onClick = {
                                    settingsViewModel.resetSetupFlow()
                                }
                            )
                            Spacer(Modifier.height(4.dp))
                            SettingsItem(
                                title = "Trigger Test Crash",
                                subtitle = "Simulate a crash to test the crash reporting system.",
                                leadingIcon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { settingsViewModel.triggerTestCrash() }
                            )
                        }
                        SettingsCategory.ABOUT -> {
                             SettingsItem(
                                title = "About PixelPlayer",
                                subtitle = "App version, credits, and more.",
                                leadingIcon = { Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary) },
                                trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { navController.navigate("about") }
                            )
                        }
                        SettingsCategory.EQUALIZER -> {
                             // Equalizer has its own screen, so this block is unreachable via standard navigation
                             // but required for exhaustiveness.
                        }

                    }
               }
            }

            item {
                // Spacer handled by contentPadding
                Spacer(Modifier.height(1.dp))
            }
        }

        SettingsTopBar(
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackPressed = onBackClick,
            title = category.title,
            expandedStartPadding = 20.dp,
            collapsedStartPadding = 68.dp,
            maxLines = titleMaxLines
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

    // Dialogs
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
    
     // Dialogs logic (copied)
    if (showClearLyricsDialog) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.Warning, null) },
            title = { Text("Reset imported lyrics?") },
            text = { Text("This action cannot be undone.") },
            onDismissRequest = { showClearLyricsDialog = false },
            confirmButton = { TextButton(onClick = { showClearLyricsDialog = false; playerViewModel.resetAllLyrics() }) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = { showClearLyricsDialog = false }) { Text("Cancel") } }
        )
    }

    
    if (showRebuildDatabaseWarning) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Rebuild database?") },
            text = { Text("This will completely rebuild your music library from scratch. All imported lyrics, favorites, and custom metadata will be lost. This action cannot be undone.") },
            onDismissRequest = { showRebuildDatabaseWarning = false },
            confirmButton = { 
                TextButton(
                    onClick = { 
                        showRebuildDatabaseWarning = false
                        refreshRequested = true
                        Toast.makeText(context, "Rebuilding database…", Toast.LENGTH_SHORT).show()
                        settingsViewModel.rebuildDatabase() 
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text("Rebuild") 
                } 
            },
            dismissButton = { TextButton(onClick = { showRebuildDatabaseWarning = false }) { Text("Cancel") } }
        )
    }

    if (showRegenerateDailyMixDialog) {
        AlertDialog(
            icon = { Icon(painterResource(R.drawable.rounded_instant_mix_24), null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Regenerate Daily Mix?") },
            text = { Text("This will discard the current mix and generate a new one based on recent listening habits.") },
            onDismissRequest = { showRegenerateDailyMixDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateDailyMixDialog = false
                        playerViewModel.forceUpdateDailyMix()
                        Toast.makeText(context, "Daily Mix regeneration started", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = { TextButton(onClick = { showRegenerateDailyMixDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRegenerateStatsDialog) {
        AlertDialog(
            icon = { Icon(painterResource(R.drawable.rounded_monitoring_24), null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Regenerate Stats?") },
            text = { Text("This will clear the statistics cache and force a recalculation from the database history.") },
            onDismissRequest = { showRegenerateStatsDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateStatsDialog = false
                        statsViewModel.forceRegenerateStats()
                        Toast.makeText(context, "Stats regeneration started", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = { TextButton(onClick = { showRegenerateStatsDialog = false }) { Text("Cancel") } }
        )
    }
}
