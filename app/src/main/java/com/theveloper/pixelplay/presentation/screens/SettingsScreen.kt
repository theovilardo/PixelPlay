package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
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
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.DirectoryItem
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

@Composable
private fun SettingsTopBar(
    collapseFraction: Float,
    headerHeight: Dp,
    onBackPressed: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val titleScale = lerp(1.2f, 0.8f, collapseFraction)
    val titlePaddingStart = lerp(32.dp, 58.dp, collapseFraction)
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val animatedTitleAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val titleContainerHeight = lerp(88.dp, 56.dp, collapseFraction)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(surfaceColor.copy(alpha = collapseFraction))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            FilledIconButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 4.dp),
                onClick = onBackPressed,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Icon(painterResource(R.drawable.rounded_arrow_back_24), contentDescription = "Back")
            }

            Box(
                modifier = Modifier
                    .align(animatedTitleAlignment)
                    .height(titleContainerHeight)
                    .fillMaxWidth()
                    .padding(start = titlePaddingStart, end = 24.dp)
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .graphicsLayer {
                            scaleX = titleScale
                            scaleY = titleScale
                        }
                )
            }
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
    // Estado para controlar la visibilidad del diálogo de directorios
    var showDirectoryDialog by remember { mutableStateOf(false) }
    var showClearLyricsDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    val directoryItems: ImmutableList<DirectoryItem> = remember(uiState.directoryItems) {
        val list: List<DirectoryItem> = uiState.directoryItems
        val immutable: ImmutableList<DirectoryItem> = list.toImmutableList() // Now this should work
        immutable
    }

    // Efecto para animaciones de transición
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(true) {
        transitionState.targetState = true
    }

    val transition = rememberTransition(transitionState, label = "SettingsAppearTransition")

    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) }
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
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
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
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

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
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
                            title = "Allowed Directories",
                            subtitle = "Choose the directories you want to get the music files from.",
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
                            onClick = { showDirectoryDialog = true }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SettingsItem(
                            title = "Refresh Library",
                            subtitle = "Rescan MediaStore and update the local database.",
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            onClick = { settingsViewModel.refreshLibrary() }
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
                        Modifier
                            .background(color = Color.Transparent, shape = RoundedCornerShape(24.dp))
                            .clip(shape = RoundedCornerShape(24.dp))
                    ){
                        ThemeSelectorItem(
                            label = "Player Theme",
                            description = "Choose the appearance for the floating player.",
                            options = mapOf(
                                ThemePreference.ALBUM_ART to "Album Art",
                                ThemePreference.DYNAMIC to "System Dynamic"
                            ),
                            selectedKey = uiState.playerThemePreference,
                            onSelectionChanged = { settingsViewModel.setPlayerThemePreference(it) },
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
                            options = mapOf(
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
                                        painter = painterResource(id = R.drawable.rounded_rounded_corner_24),
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
                            options = mapOf(
                                CarouselStyle.NO_PEEK to "No Peek",
                                CarouselStyle.ONE_PEEK to "One Peek",
                                //CarouselStyle.TWO_PEEK to "Two Peeks"
                            ),
                            selectedKey = uiState.carouselStyle,
                            onSelectionChanged = { settingsViewModel.setCarouselStyle(it) },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_view_carousel_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        )
                    }
                }
            }

            item(key = "spacer_2") { Spacer(modifier = Modifier.height(16.dp)) }

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
                        Modifier
                            .background(color = Color.Transparent, shape = RoundedCornerShape(24.dp))
                            .clip(shape = RoundedCornerShape(24.dp))
                    ) {
                        SettingsItem(
                            title = "Force Daily Mix Regeneration",
                            subtitle = "Re-creates the daily mix playlist immediately.",
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_instant_mix_24),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            onClick = { playerViewModel.forceUpdateDailyMix() }
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
                        Modifier
                            .background(color = Color.Transparent, shape = RoundedCornerShape(24.dp))
                            .clip(shape = RoundedCornerShape(24.dp))
                    ) {
                        SettingsItem(
                            title = "About Pixel Play",
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

            item(key = "bottom_spacer") { Spacer(modifier = Modifier.height(MiniPlayerHeight + 36.dp)) }
        }
        SettingsTopBar(
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackPressed = onNavigationIconClick
        )
    }

    // Diálogo para seleccionar directorios
    if (showDirectoryDialog) {
        DirectoryPickerBottomSheet(
            directoryItems = directoryItems,
            isLoading = uiState.isLoadingDirectories,
            onDismiss = { showDirectoryDialog = false },
            onItemToggle = { directoryItem ->
                settingsViewModel.toggleDirectoryAllowed(directoryItem)
            }
        )
    }

    // Reset lyrics dialog
    if (showClearLyricsDialog) {
        AlertDialog(
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Warning,
                    contentDescription = null
                )
            },
            title = {
                Text(text = "Reset imported lyrics?")
            },
            text = {
                Text(text = "This action cannot be undone.")
            },
            onDismissRequest = {
                showClearLyricsDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLyricsDialog = false
                        playerViewModel.resetAllLyrics()
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearLyricsDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                leadingIcon()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
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

            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                trailingIcon()
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { expanded = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                leadingIcon()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
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
                        modifier = Modifier
                            .fillMaxWidth()
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
                        color = if (isSelected)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryPickerBottomSheet(
    directoryItems: ImmutableList<DirectoryItem>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onItemToggle: (DirectoryItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    //.padding(bottom = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 16.dp, bottom = 22.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Music Folders",
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 4.dp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Scanning folders...",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        directoryItems.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No folders with audio files found",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 106.dp) // Space for the button
                            ) {
                                items(directoryItems, key = { it.path }) { item ->
                                    DirectoryItemCard(
                                        directoryItem = item,
                                        onToggle = { onItemToggle(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onDismiss,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp)
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 26.dp, vertical = 0.dp),
                    text = "Accept"
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    )
            ) {

            }
        }
    }
}


@Composable
fun DirectoryItemCard(
    directoryItem: DirectoryItem,
    onToggle: () -> Unit
) {
    val checkedState = remember { mutableStateOf(directoryItem.isAllowed) }

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

    LaunchedEffect(directoryItem) {
        checkedState.value = directoryItem.isAllowed
    }

    Surface(
        shape = shape,
        color = if (checkedState.value)
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(
            width = 2.dp,
            color = if (checkedState.value)
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            else
                Color.Transparent//MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                checkedState.value = !checkedState.value
                onToggle()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (checkedState.value) Icons.Filled.Folder else Icons.Outlined.Folder,
                contentDescription = null,
                tint = if (checkedState.value)
                    MaterialTheme.colorScheme.secondary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = directoryItem.displayName,
                    lineHeight = 18.sp,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checkedState.value)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = directoryItem.path,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                    color = if (checkedState.value)
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Checkbox
            Checkbox(
                checked = checkedState.value,
                onCheckedChange = {
                    checkedState.value = it
                    onToggle()
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                )
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

    // Fetch models when API key becomes available
    LaunchedEffect(apiKey) {
        if (apiKey.isNotBlank() && uiState.availableModels.isEmpty() && !uiState.isLoadingModels) {
            settingsViewModel.fetchAvailableModels(apiKey)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.gemini_ai),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
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
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (apiKey.isBlank()) {
                val context = LocalContext.current
                val url = "https://aistudio.google.com/app/apikey"
                val annotatedString = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append("Get it here: ")
                    }
                    withStyle(style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline)
                    ) {
                        append(url)
                    }
                }

                Text(
                    text = annotatedString,
                    modifier = Modifier.clickable {
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
                        value = uiState.availableModels.find { it.name == selectedModel }?.displayName
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
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isModelDropdownExpanded)
                            }
                        },
                        modifier = Modifier
                            .menuAnchor(),
//                            .menuAnchor(ExposedDropdownMenuBoxScope.MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !uiState.isLoadingModels && uiState.availableModels.isNotEmpty()
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

                    TextButton(
                        onClick = { showSystemPromptDialog = true }
                    ) {
                        Text("Modify")
                    }
                }

                if (showSystemPromptDialog) {
                    SystemPromptDialog(
                        currentPrompt = systemPrompt,
                        onDismiss = { showSystemPromptDialog = false },
                        onSave = { newPrompt ->
                            settingsViewModel.onGeminiSystemPromptChange(newPrompt)
                            showSystemPromptDialog = false
                        },
                        onReset = {
                            settingsViewModel.resetGeminiSystemPrompt()
                        }
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

    LaunchedEffect(currentPrompt) {
        editedPrompt = currentPrompt
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxHeight(1f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
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
                        editedPrompt = com.theveloper.pixelplay.data.preferences.UserPreferencesRepository.DEFAULT_SYSTEM_PROMPT
                    }
                ) {
                    Text("Reset")
                }
            }

            Text(
                text = "Customize the AI's behavior and personality by modifying the system prompt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Text field for editing prompt with floating clear button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
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
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(48.dp),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
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
                ) {
                    Text("Cancel")
                }

                FilledIconButton(
                    onClick = {
                        scope.launch {
                            onSave(editedPrompt)
                            // Show toast
                            android.widget.Toast.makeText(
                                context,
                                "System prompt saved successfully",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            // Smooth dismiss animation
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = IconButtonDefaults.filledIconButtonColors(
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
