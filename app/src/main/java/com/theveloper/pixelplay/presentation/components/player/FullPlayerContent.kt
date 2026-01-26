package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import com.theveloper.pixelplay.data.model.Lyrics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SheetState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.presentation.components.AlbumCarouselSection
import com.theveloper.pixelplay.presentation.components.AutoScrollingTextOnDemand
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.LyricsSheet
import com.theveloper.pixelplay.presentation.components.WavyMusicSlider
import com.theveloper.pixelplay.presentation.components.scoped.DeferAt
import com.theveloper.pixelplay.presentation.components.scoped.PrefetchAlbumNeighborsImg
import com.theveloper.pixelplay.presentation.components.scoped.rememberSmoothProgress
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.AudioMetaUtils.mimeTypeToFormat
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import kotlin.math.roundToLong
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.theveloper.pixelplay.presentation.components.WavySliderExpressive

@androidx.annotation.OptIn(UnstableApi::class)
@SuppressLint("StateFlowValueCalledInComposition")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullPlayerContent(
    currentSong: Song?,
    currentPlaybackQueue: ImmutableList<Song>,
    currentQueueSourceName: String,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    expansionFractionProvider: () -> Float,
    currentSheetState: PlayerSheetState,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    playerViewModel: PlayerViewModel, // For stable state like totalDuration and lyrics
    // State Providers
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    isFavoriteProvider: () -> Boolean,
    repeatModeProvider: () -> Int,
    isShuffleEnabledProvider: () -> Boolean,
    totalDurationProvider: () -> Long,
    lyricsProvider: () -> Lyrics? = { null }, 
    // State
    isCastConnecting: Boolean = false,
    // Event Handlers
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueueClicked: () -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    onShowCastClicked: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    var retainedSong by remember { mutableStateOf(currentSong) }
    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            retainedSong = currentSong
        }
    }

    val song = currentSong ?: retainedSong ?: return // Keep the player visible while transitioning
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showArtistPicker by rememberSaveable { mutableStateOf(false) }
    
    // REMOVED: val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    
    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsState()
    val currentSongArtists by playerViewModel.currentSongArtists.collectAsState()
    val lyricsSyncOffset by playerViewModel.currentSongLyricsSyncOffset.collectAsState()
    val albumArtQuality by playerViewModel.albumArtQuality.collectAsState()

    var showFetchLyricsDialog by remember { mutableStateOf(false) }
    var totalDrag by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { inputStream ->
                        val lyricsContent = inputStream.bufferedReader().use { reader -> reader.readText() }
                        currentSong?.id?.toLong()?.let { songId ->
                            playerViewModel.importLyricsFromFile(songId, lyricsContent)
                        }
                    }
                    showFetchLyricsDialog = false
                } catch (e: Exception) {
                    Timber.e(e, "Error reading imported lyrics file")
                    playerViewModel.sendToast("Error reading file.")
                }
            }
        }
    )

    // totalDurationValue is derived from stablePlayerState, so it's fine.
    // OPTIMIZATION: Use passed provider instead of collecting flow
    val totalDurationValue = totalDurationProvider()

    val stableControlAnimationSpec = remember {
        tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
    }

    val controlOtherButtonsColor = LocalMaterialTheme.current.primary.copy(alpha = 0.15f)
    val controlPlayPauseColor = LocalMaterialTheme.current.primary
    val controlTintPlayPauseIcon = LocalMaterialTheme.current.onPrimary
    val controlTintOtherIcons = LocalMaterialTheme.current.primary

    val placeholderColor = LocalMaterialTheme.current.primary.copy(alpha = 0.08f)
    val placeholderOnColor = LocalMaterialTheme.current.primary.copy(alpha = 0.04f)

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE


    // Lógica para el botón de Lyrics en el reproductor expandido
    val onLyricsClick = {
        val lyrics = lyricsProvider()
        if (lyrics?.synced.isNullOrEmpty() && lyrics?.plain.isNullOrEmpty()) {
            // Si no hay letra, mostramos el diálogo para buscar
            showFetchLyricsDialog = true
        } else {
            // Si hay letra, mostramos el sheet directamente
            showLyricsSheet = true
        }
    }

    if (showFetchLyricsDialog) {
        FetchLyricsDialog(
            uiState = lyricsSearchUiState,
            currentSong = song, // Use 'song' which is derived from args/retained
            onConfirm = { forcePick ->
                // El usuario confirma, iniciamos la búsqueda
                playerViewModel.fetchLyricsForCurrentSong(forcePick)
            },
            onPickResult = { result ->
                playerViewModel.acceptLyricsSearchResultForCurrentSong(result)
            },
            onManualSearch = { title, artist ->
                playerViewModel.searchLyricsManually(title, artist)
            },
            onDismiss = {
                // El usuario cancela o cierra el diálogo
                showFetchLyricsDialog = false
                playerViewModel.resetLyricsSearchState()
            },
            onImport = {
                filePickerLauncher.launch("*/*")
            }
        )
    }

    // Observador para reaccionar al resultado de la búsqueda de letras
    LaunchedEffect(lyricsSearchUiState) {
        when (val state = lyricsSearchUiState) {
            is LyricsSearchUiState.Success -> {
                if (showFetchLyricsDialog) {
                    showFetchLyricsDialog = false
                    showLyricsSheet = true
                    playerViewModel.resetLyricsSearchState()
                }
            }
            is LyricsSearchUiState.Error -> {
            }
            else -> Unit
        }
    }

    val gestureScope = rememberCoroutineScope()
    val isCastConnecting by playerViewModel.isCastConnecting.collectAsState()

    // Sub sections , to be reused in different layout modes

    @SuppressLint("UnusedBoxWithConstraintsScope")
    @Composable
    fun AlbumCoverSection(modifier: Modifier = Modifier) {
        val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayAlbumCarousel

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            val carouselHeight = when (carouselStyle) {
                CarouselStyle.NO_PEEK -> maxWidth
                CarouselStyle.ONE_PEEK -> maxWidth * 0.8f
                CarouselStyle.TWO_PEEK -> maxWidth * 0.6f
                else -> maxWidth * 0.8f
            }

            DelayedContent(
                shouldDelay = shouldDelay,
                showPlaceholders = loadingTweaks.showPlaceholders,
                expansionFractionProvider = expansionFractionProvider,
                isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
                normalStartThreshold = 0.08f,
                delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
                placeholder = {
                    if (loadingTweaks.transparentPlaceholders) {
                        Box(Modifier.height(carouselHeight).fillMaxWidth())
                    } else {
                        AlbumPlaceholder(height = carouselHeight, placeholderColor, placeholderOnColor)
                    }
                }
            ) {
                 AlbumCarouselSection(
                    currentSong = song,
                    queue = currentPlaybackQueue,
                    expansionFraction = 1f, // Static layout
                    onSongSelected = { newSong ->
                        if (newSong.id != song.id) {
                            playerViewModel.showAndPlaySong(
                                song = newSong,
                                contextSongs = currentPlaybackQueue,
                                queueName = currentQueueSourceName
                            )
                        }
                    },
                    carouselStyle = carouselStyle,
                    modifier = Modifier.height(carouselHeight),
                    albumArtQuality = albumArtQuality
                )
            }
        }
    }

    @Composable
    fun ControlsSection() {
        val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delayControls

        DelayedContent(
            shouldDelay = shouldDelay,
            showPlaceholders = loadingTweaks.showPlaceholders,
            expansionFractionProvider = expansionFractionProvider,
            isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
            normalStartThreshold = 0.42f,
            delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
            placeholder = {
                if (loadingTweaks.transparentPlaceholders) {
                    Box(Modifier.fillMaxWidth().height(174.dp))
                } else {
                    ControlsPlaceholder(placeholderColor, placeholderOnColor)
                }
            }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedPlaybackControls(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    isPlayingProvider = isPlayingProvider,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    height = 80.dp,
                    pressAnimationSpec = stableControlAnimationSpec,
                    releaseDelay = 220L,
                    colorOtherButtons = controlOtherButtonsColor,
                    colorPlayPause = controlPlayPauseColor,
                    tintPlayPauseIcon = controlTintPlayPauseIcon,
                    tintOtherIcons = controlTintOtherIcons
                )

                Spacer(modifier = Modifier.height(14.dp))

                BottomToggleRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp, max = 78.dp)
                        .padding(horizontal = 26.dp, vertical = 0.dp)
                        .padding(bottom = 6.dp),
                    isShuffleEnabled = isShuffleEnabledProvider(),
                    repeatMode = repeatModeProvider(),
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    }

    @Composable
    fun PlayerProgressSection() {
        PlayerProgressBarSection(
            currentPositionProvider = currentPositionProvider,
            totalDurationValue = totalDurationValue,
            onSeek = onSeek,
            expansionFractionProvider = expansionFractionProvider,
            isPlayingProvider = isPlayingProvider,
            currentSheetState = currentSheetState,
            activeTrackColor = LocalMaterialTheme.current.primary,
            inactiveTrackColor = LocalMaterialTheme.current.primary.copy(alpha = 0.2f),
            thumbColor = LocalMaterialTheme.current.primary,
            timeTextColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f),
            loadingTweaks = loadingTweaks
        )
    }

    @Composable
    fun SongMetadataSection() {
        val shouldDelay = loadingTweaks.delayAll || loadingTweaks.delaySongMetadata

        DelayedContent(
            shouldDelay = shouldDelay,
            showPlaceholders = loadingTweaks.showPlaceholders,
            expansionFractionProvider = expansionFractionProvider,
            isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
            normalStartThreshold = 0.20f,
            delayAppearThreshold = loadingTweaks.contentAppearThresholdPercent / 100f,
            placeholder = {
                if (loadingTweaks.transparentPlaceholders) {
                    Box(Modifier.fillMaxWidth().height(70.dp))
                } else {
                    MetadataPlaceholder(expansionFractionProvider(), placeholderColor, placeholderOnColor)
                }
            }
        ) {
            SongMetadataDisplaySection(
                modifier = Modifier
                    .padding(start = 0.dp),
                onClickLyrics = onLyricsClick,
                song = song,
                currentSongArtists = currentSongArtists,
                expansionFractionProvider = expansionFractionProvider,
                textColor = LocalMaterialTheme.current.onPrimaryContainer,
                artistTextColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.8f),
                playerViewModel = playerViewModel,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer,
                showQueueButton = isLandscape,
                onClickQueue = {
                    showSongInfoBottomSheet = true
                    onShowQueueClicked()
                },
                onClickArtist = {
                    if (currentSongArtists.size > 1) {
                        showArtistPicker = true
                    } else {
                        playerViewModel.triggerArtistNavigationFromPlayer(song.artistId)
                    }
                }
            )
        }
    }

    @Composable
    fun FullPlayerPortraitContent(paddingValues: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = 24.dp,
                    vertical = 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            // Removed PrefetchAlbumNeighborsImg DeferAt wrapper - implicitly prefetching if composed?
            // Actually Prefetch likely needs to be kept but maybe without DeferAt if it's lightweight
            // For now, let's keep it simple.

            AlbumCoverSection()

            Box(Modifier.align(Alignment.Start)) {
                SongMetadataSection()
            }

            PlayerProgressSection()

            ControlsSection()
        }
    }

    @Composable
    fun FullPlayerLandscapeContent(paddingValues: PaddingValues) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = 24.dp,
                    vertical = 0.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumCoverSection(
                Modifier
                    .fillMaxHeight()
                    .weight(1f)
            )
            Spacer(Modifier.width(9.dp))
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(
                        horizontal = 0.dp,
                        vertical = 0.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                SongMetadataSection()

                PlayerProgressSection()

                ControlsSection()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.pointerInput(currentSheetState) {
            val queueDragActivationThresholdPx = 6.dp.toPx()

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // Check condition AFTER the down event occurs
                val isFullyExpanded = currentSheetState == PlayerSheetState.EXPANDED && expansionFractionProvider() >= 0.99f

                if (!isFullyExpanded) {
                    return@awaitEachGesture
                }

                // Proceed with gesture logic
                var dragConsumedByQueue = false
                val velocityTracker = VelocityTracker()
                var totalDrag = 0f

                drag(down.id) { change ->
                    val dragAmount = change.positionChange().y
                    totalDrag += dragAmount
                    val isDraggingUp = totalDrag < -queueDragActivationThresholdPx

                    if (isDraggingUp && !dragConsumedByQueue) {
                        dragConsumedByQueue = true
                        onQueueDragStart()
                    }

                    if (dragConsumedByQueue) {
                        change.consume()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        onQueueDrag(dragAmount)
                    }
                }

                if (dragConsumedByQueue) {
                    val velocity = velocityTracker.calculateVelocity().y
                    onQueueRelease(totalDrag, velocity)
                }
            }
        },
        topBar = {
            if (!isLandscape) {
                TopAppBar(
                    modifier = Modifier.graphicsLayer {
                        val fraction = expansionFractionProvider()
                        // TopBar should always fade in smoothly, ignoring delayAll to avoid empty UI
                        val startThreshold = 0f
                        val endThreshold = 1f
                        alpha = ((fraction - startThreshold) / (endThreshold - startThreshold)).coerceIn(0f, 1f)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                        actionIconContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                        navigationIconContentColor = LocalMaterialTheme.current.onPrimaryContainer
                    ),
                    title = {
                        val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsState()
                        if (!isCastConnecting) {
                            AnimatedVisibility(visible = (!isRemotePlaybackActive)) {
                                Text(
                                    modifier = Modifier.padding(start = 18.dp),
                                    text = "Now Playing",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        Box(
                            modifier = Modifier
                                // Ancho total = 14dp de padding + 42dp del botón
                                .width(56.dp)
                                .height(42.dp),
                            // 2. Alinea el contenido (el botón) al final (derecha) y centrado verticalmente
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            // 3. Tu botón circular original, sin cambios
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(LocalMaterialTheme.current.onPrimary)
                                    .clickable(onClick = onCollapse),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                                    contentDescription = "Colapsar",
                                    tint = LocalMaterialTheme.current.primary
                                )
                            }
                        }
                    },
                    actions = {
                        Row(
                            modifier = Modifier
                                .padding(end = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsState()
                            val selectedRouteName by playerViewModel.selectedRoute.map { it?.name }.collectAsState(initial = null)
                            val isBluetoothEnabled by playerViewModel.isBluetoothEnabled.collectAsState()
                            val bluetoothName by playerViewModel.bluetoothName.collectAsState()
                            val showCastLabel = isCastConnecting || (isRemotePlaybackActive && selectedRouteName != null)
                            val isBluetoothActive =
                                isBluetoothEnabled && !bluetoothName.isNullOrEmpty() && !isRemotePlaybackActive && !isCastConnecting
                            val castIconPainter = when {
                                isCastConnecting || isRemotePlaybackActive -> painterResource(R.drawable.rounded_cast_24)
                                isBluetoothActive -> painterResource(R.drawable.rounded_bluetooth_24)
                                else -> painterResource(R.drawable.rounded_mobile_speaker_24)
                            }
                            val castCornersExpanded = 50.dp
                            val castCornersCompact = 6.dp
                            val castTopStart by animateDpAsState(
                                targetValue = if (showCastLabel) castCornersExpanded else castCornersExpanded,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val castTopEnd by animateDpAsState(
                                targetValue = if (showCastLabel) castCornersExpanded else castCornersCompact,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val castBottomStart by animateDpAsState(
                                targetValue = if (showCastLabel) castCornersExpanded else castCornersExpanded,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val castBottomEnd by animateDpAsState(
                                targetValue = if (showCastLabel) castCornersExpanded else castCornersCompact,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val castContainerColor by animateColorAsState(
                                targetValue = LocalMaterialTheme.current.onPrimary,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            )
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .align(Alignment.CenterVertically)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    .widthIn(
                                        min = 50.dp,
                                        max = if (showCastLabel) 190.dp else 58.dp
                                    )
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = castTopStart.coerceAtLeast(0.dp),
                                            topEnd = castTopEnd.coerceAtLeast(0.dp),
                                            bottomStart = castBottomStart.coerceAtLeast(0.dp),
                                            bottomEnd = castBottomEnd.coerceAtLeast(0.dp)
                                        )
                                    )
                                    .background(castContainerColor)
                                    .clickable { onShowCastClicked() },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Icon(
                                        painter = castIconPainter,
                                        contentDescription = when {
                                            isCastConnecting || isRemotePlaybackActive -> "Cast"
                                            isBluetoothActive -> "Bluetooth"
                                            else -> "Local playback"
                                        },
                                        tint = LocalMaterialTheme.current.primary
                                    )
                                    AnimatedVisibility(visible = showCastLabel) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Spacer(Modifier.width(8.dp))
                                            AnimatedContent(
                                                targetState = when {
                                                    isCastConnecting -> "Connecting…"
                                                    isRemotePlaybackActive && selectedRouteName != null -> selectedRouteName ?: ""
                                                    else -> ""
                                                },
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(120))
                                                },
                                                label = "castButtonLabel"
                                            ) { label ->
                                                Row(
                                                    modifier = Modifier.padding(end = 16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = LocalMaterialTheme.current.primary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false)
                                                    )
                                                    AnimatedVisibility(visible = isCastConnecting) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier
                                                                .size(14.dp),
                                                            strokeWidth = 2.dp,
                                                            color = LocalMaterialTheme.current.primary
                                                        )
                                                    }
                                                    if (isRemotePlaybackActive && !isCastConnecting) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF38C450))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Queue Button
                            Box(
                                modifier = Modifier
                                    .size(height = 42.dp, width = 50.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 6.dp,
                                            topEnd = 50.dp,
                                            bottomStart = 6.dp,
                                            bottomEnd = 50.dp
                                        )
                                    )
                                    .background(LocalMaterialTheme.current.onPrimary)
                                    .clickable {
                                        showSongInfoBottomSheet = true
                                        onShowQueueClicked()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.rounded_queue_music_24),
                                    contentDescription = "Song options",
                                    tint = LocalMaterialTheme.current.primary
                                )
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        if (isLandscape) {
            FullPlayerLandscapeContent(paddingValues)
        } else {
            FullPlayerPortraitContent(paddingValues)
        }
    }
    AnimatedVisibility(
        visible = showLyricsSheet,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
    ) {
        // We can create a temporary StablePlayerState for LyricsSheet if needed, or update LyricsSheet to take Granular args.
        // For now, let's keep LyricsSheet collecting stablePlayerState internally IF it must, OR better:
        // Pass the subset we have.
        // LyricsSheet signature: stablePlayerStateFlow: StateFlow<StablePlayerState>
        // We can't change that easily without refactoring LyricsSheet too.
        // For now, pass the flow but LyricsSheet is only visible when sheet is open.
        // Ideally we should refactor LyricsSheet too, but let's stick to FullPlayerContent optimizations first.
        LyricsSheet(
            stablePlayerStateFlow = playerViewModel.stablePlayerState,
            playerUiStateFlow = playerViewModel.playerUiState,
            lyricsSearchUiState = lyricsSearchUiState,
            resetLyricsForCurrentSong = {
                showLyricsSheet = false
                playerViewModel.resetLyricsForCurrentSong()
            },
            onSearchLyrics = { forcePick -> playerViewModel.fetchLyricsForCurrentSong(forcePick) },
            onPickResult = { playerViewModel.acceptLyricsSearchResultForCurrentSong(it) },
            onManualSearch = { title, artist -> playerViewModel.searchLyricsManually(title, artist) },
            onImportLyrics = { filePickerLauncher.launch("*/*") },
            onDismissLyricsSearch = { playerViewModel.resetLyricsSearchState() },
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = { currentSong?.id?.let { songId -> playerViewModel.setLyricsSyncOffset(songId, it) } },
            lyricsTextStyle = MaterialTheme.typography.titleLarge,
            backgroundColor = LocalMaterialTheme.current.background,
            onBackgroundColor = LocalMaterialTheme.current.onBackground,
            containerColor = LocalMaterialTheme.current.primaryContainer,
            contentColor = LocalMaterialTheme.current.onPrimaryContainer,
            accentColor = LocalMaterialTheme.current.primary,
            onAccentColor = LocalMaterialTheme.current.onPrimary,
            tertiaryColor = LocalMaterialTheme.current.tertiary,
            onTertiaryColor = LocalMaterialTheme.current.onTertiary,
            onBackClick = { showLyricsSheet = false },
            onSeekTo = { playerViewModel.seekTo(it) },
            onPlayPause = {
                playerViewModel.playPause()
            }
        )
    }

    val artistPickerSheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && currentSongArtists.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showArtistPicker = false },
            sheetState = artistPickerSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.artist_picker_title), // short label; keep UI minimal
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalMaterialTheme.current.onPrimaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                currentSongArtists.forEachIndexed { index, artistItem ->
                    Text(
                        text = artistItem.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalMaterialTheme.current.onPrimaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clickable {
                                playerViewModel.triggerArtistNavigationFromPlayer(artistItem.id)
                                showArtistPicker = false
                            }
                    )
                    if (index != currentSongArtists.lastIndex) {
                        HorizontalDivider(color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SongMetadataDisplaySection(
    song: Song?,
    currentSongArtists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    onClickLyrics: () -> Unit,
    showQueueButton: Boolean,
    onClickQueue: () -> Unit,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        song?.let { currentSong ->
            PlayerSongInfo(
                title = currentSong.title,
                artist = currentSong.displayArtist,
                artistId = currentSong.artistId,
                artists = currentSongArtists,
                expansionFractionProvider = expansionFractionProvider,
                textColor = textColor,
                artistTextColor = artistTextColor,
                gradientEdgeColor = gradientEdgeColor,
                playerViewModel = playerViewModel,
                onClickArtist = onClickArtist,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            )
        }

        if (showQueueButton) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(height = 42.dp, width = 50.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 50.dp,
                                topEnd = 6.dp,
                                bottomStart = 50.dp,
                                bottomEnd = 6.dp
                            )
                        )
                        .background(LocalMaterialTheme.current.onPrimary)
                        .clickable { onClickLyrics() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_lyrics_24),
                        contentDescription = "Lyrics",
                        tint = LocalMaterialTheme.current.primary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(height = 42.dp, width = 50.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 6.dp,
                                topEnd = 50.dp,
                                bottomStart = 6.dp,
                                bottomEnd = 50.dp
                            )
                        )
                        .background(LocalMaterialTheme.current.onPrimary)
                        .clickable { onClickQueue() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_queue_music_24),
                        contentDescription = "Queue",
                        tint = LocalMaterialTheme.current.primary
                    )
                }
            }
        } else {
            // Portrait Mode: Just the Lyrics button (Queue is in TopBar)
            FilledIconButton(
                modifier = Modifier
                    .size(width = 48.dp, height = 48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = LocalMaterialTheme.current.onPrimary,
                    contentColor = LocalMaterialTheme.current.primary
                ),
                onClick = onClickLyrics,
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_lyrics_24),
                    contentDescription = "Lyrics"
                )
            }
        }
    }
}

fun formatAudioMetaString(mimeType: String?, bitrate: Int?, sampleRate: Int?): String {
    val bitrate = bitrate?.div(1000) ?: 0       // convert to kb/s
    val sampleRate = sampleRate ?: 0           // in Hz

    return "${mimeTypeToFormat(mimeType)} \u25CF $bitrate kb/s \u25CF ${sampleRate / 1000.0} kHz"
}

@Composable
private fun PlayerProgressBarSection(
    currentPositionProvider: () -> Long,
    totalDurationValue: Long,
    onSeek: (Long) -> Unit,
    expansionFractionProvider: () -> Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    thumbColor: Color,
    timeTextColor: Color,
    loadingTweaks: FullPlayerLoadingTweaks? = null,
    modifier: Modifier = Modifier
) {
    val expansionFraction by remember { derivedStateOf { expansionFractionProvider() } }
    
    val isVisible by remember { derivedStateOf { expansionFraction > 0.01f } }

    val isExpanded by remember { 
        derivedStateOf { 
            currentSheetState == PlayerSheetState.EXPANDED && expansionFraction >= 0.995f 
        } 
    }

    val durationForCalc = totalDurationValue.coerceAtLeast(1L)
    
    // Pass isVisible to rememberSmoothProgress
    val (smoothProgressState, _) = rememberSmoothProgress(
        isPlayingProvider = isPlayingProvider,
        currentPositionProvider = currentPositionProvider,
        totalDuration = totalDurationValue,
        sampleWhilePlayingMs = 200L,
        sampleWhilePausedMs = 800L,
        isVisible = isVisible
    )

    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
    // Optimistic Seek: Holds the target position immediately after seek to prevent snap-back
    var optimisticPosition by remember { mutableStateOf<Long?>(null) }
    
    // Clear optimistic position ONLY when the SMOOTH (visual) progress catches up
    // using raw position causes a jump because smooth progress might lag behind raw.
    LaunchedEffect(optimisticPosition) {
        val target = optimisticPosition
        if (target != null) {
            val start = System.currentTimeMillis()
            val targetFl = target.toFloat() / durationForCalc.toFloat()
            
            while (optimisticPosition != null) {
                // Check if the current VISUAL progress (smoothState) corresponds to the target
                // We use the derived state value which falls back to smoothProgressState
                val currentVisual = smoothProgressState.value
                val currentVisualMs = (currentVisual * durationForCalc).toLong()
                
                // If visual is close enough (within 500ms visual distance)
                if (kotlin.math.abs(currentVisualMs - target) < 500 || (System.currentTimeMillis() - start) > 2000) {
                     optimisticPosition = null
                }
                kotlinx.coroutines.delay(50)
            }
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    // Logic to determine target progress without reading values
    val rawPositionProvider = remember(currentPositionProvider, isVisible) {
        { if (isVisible) currentPositionProvider() else 0L }
    }
    
    // DIRECT State derivation - No intermediate Animatable (fixes "stepping" lag)
    val animatedProgressState = remember(isExpanded, sliderDragValue, optimisticPosition, smoothProgressState, durationForCalc, rawPositionProvider) {
        derivedStateOf {
             if (sliderDragValue != null) {
                 sliderDragValue!!
             } else if (optimisticPosition != null) {
                 (optimisticPosition!!.toFloat() / durationForCalc.toFloat()).coerceIn(0f, 1f)
             } else if (isExpanded) {
                 val rawPos = rawPositionProvider()
                 (rawPos.coerceAtLeast(0) / durationForCalc.toFloat()).coerceIn(0f, 1f)
             } else {
                 smoothProgressState.value
             }
        }
    }

    // No LaunchedEffect/snapshotFlow needed anymore. 
    // smoothProgressState is already 60fps animated.

    val effectivePositionState = remember(durationForCalc, animatedProgressState, isVisible, totalDurationValue) {
        derivedStateOf {
             val progress = animatedProgressState.value
             (progress * durationForCalc).roundToLong().coerceIn(0L, totalDurationValue.coerceAtLeast(0L))
        }
    }

    val shouldDelay = loadingTweaks?.let { it.delayAll || it.delayProgressBar } ?: false

    val placeholderColor = LocalMaterialTheme.current.primary.copy(alpha = 0.08f)
    val placeholderOnColor = LocalMaterialTheme.current.primary.copy(alpha = 0.04f)

    DelayedContent(
        shouldDelay = shouldDelay,
        showPlaceholders = loadingTweaks?.showPlaceholders ?: false,
        expansionFractionProvider = expansionFractionProvider,
        isExpandedOverride = currentSheetState == PlayerSheetState.EXPANDED,
        normalStartThreshold = 0.08f,
        delayAppearThreshold = (loadingTweaks?.contentAppearThresholdPercent ?: 100) / 100f,
        placeholder = {
             if (loadingTweaks?.transparentPlaceholders == true) {
                 Box(Modifier.fillMaxWidth().heightIn(min = 70.dp))
             } else {
                 ProgressPlaceholder(expansionFraction, placeholderColor, placeholderOnColor)
             }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer { 
                    // No reads here
                }
                .padding(vertical = lerp(2.dp, 0.dp, expansionFraction))
                .heightIn(min = 70.dp)
        ) {
            
            // Isolated Slider Component
            EfficientSlider(
                valueState = animatedProgressState,
                onValueChange = { sliderDragValue = it },
                onValueChangeFinished = {
                    sliderDragValue?.let { finalValue ->
                        val targetMs = (finalValue * durationForCalc).roundToLong()
                        optimisticPosition = targetMs
                        onSeek(targetMs)
                    }
                    sliderDragValue = null
                },
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = inactiveTrackColor,
                interactionSource = interactionSource,
                isPlaying = isPlayingProvider()
            )

            // Isolated Time Labels
            EfficientTimeLabels(
                positionState = effectivePositionState,
                duration = totalDurationValue,
                isVisible = isVisible,
                textColor = timeTextColor
            )
        }
    }
}

@Composable
private fun EfficientSlider(
    valueState: androidx.compose.runtime.State<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    thumbColor: Color,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    interactionSource: MutableInteractionSource,
    isPlaying: Boolean // Added parameter
) {
    WavySliderExpressive(
        value = valueState.value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
        thumbColor = thumbColor,
        isPlaying = isPlaying,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 6.dp)
    )
}

@Composable
private fun EfficientTimeLabels(
    positionState: androidx.compose.runtime.State<Long>,
    duration: Long,
    isVisible: Boolean,
    textColor: Color
) {
    // Move state derivation inside the component but remember it based on inputs
    // Actually, we can just use derivedStateOf here.
    val posStr by remember(isVisible) { 
        derivedStateOf { if (isVisible) formatDuration(positionState.value) else "--:--" } 
    }
    val durStr by remember(isVisible, duration) { 
        derivedStateOf { if (isVisible) formatDuration(duration) else "--:--" } 
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reads happen here, but now we read the derived String state.
        // If the String doesn't change, Text *might* skip recomposition if it's smart,
        // but the Row body will still execute?
        // No, if we read `posStr` (delegated property), we read the State<String>. 
        // If the State<String> didn't change (because derivedStateOf result equality check), 
        // this scope won't recompose!
        
        Text(
            posStr,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = textColor
        )
        Text(
            durStr,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = textColor
        )
    }
}

@Composable
private fun DelayedContent(
    shouldDelay: Boolean,
    showPlaceholders: Boolean,
    expansionFractionProvider: () -> Float,
    isExpandedOverride: Boolean = false,
    normalStartThreshold: Float,
    delayAppearThreshold: Float,
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    // Some carousel styles (e.g., one-peek) can leave the sheet fraction just shy of 1f when reopening
    // the player, which kept delayed sections stuck on placeholders. Treat near-complete expansion as
    // fully expanded to ensure content becomes visible without needing an extra interaction.
    val expansionFraction by remember {
        derivedStateOf {
            val raw = expansionFractionProvider().coerceIn(0f, 1f)
            if (isExpandedOverride) 1f else raw
        }
    }
    val easedExpansionFraction by remember {
        derivedStateOf { if (expansionFraction >= 0.985f || isExpandedOverride) 1f else expansionFraction }
    }

    val isDelayGateOpen by remember(shouldDelay, delayAppearThreshold, isExpandedOverride) {
        derivedStateOf {
            !shouldDelay || isExpandedOverride || easedExpansionFraction >= delayAppearThreshold.coerceIn(0f, 1f)
        }
    }

    val baseAlpha by remember(normalStartThreshold, isExpandedOverride) {
        derivedStateOf {
            val effectiveFraction = if (isExpandedOverride) 1f else easedExpansionFraction
            ((effectiveFraction - normalStartThreshold) / (1f - normalStartThreshold)).coerceIn(0f, 1f)
        }
    }

    if (shouldDelay) {
        Crossfade(
            targetState = isDelayGateOpen,
            label = "DelayedContentCrossfade"
        ) { gateOpen ->
            if (gateOpen) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = baseAlpha
                    }
                ) {
                    content()
                }
            } else if (showPlaceholders) {
                placeholder()
            }
        }
    } else {
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = baseAlpha
            }
        ) {
            content()
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerSongInfo(
    title: String,
    artist: String,
    artistId: Long,
    artists: List<Artist>,
    expansionFractionProvider: () -> Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isNavigatingToArtist by remember { mutableStateOf(false) }
    val titleStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontFamily = GoogleSansRounded,
        color = textColor
    )

    val artistStyle = MaterialTheme.typography.titleMedium.copy(
        letterSpacing = 0.sp,
        color = artistTextColor
    )

    Column(
        horizontalAlignment = Alignment.Start,
            modifier = modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
            .graphicsLayer {
                val fraction = expansionFractionProvider()
                alpha = fraction // Or apply specific fade logic if desired
                translationY = (1f - fraction) * 24f
            }
    ) {
        // We pass 1f to AutoScrollingTextOnDemand because the alpha/translation is now handled by the parent Column graphicsLayer
        // and we want it "fully rendered" but hidden/moved by the layer.
        // Actually, AutoScrollingTextOnDemand uses expansionFraction to start scrolling only when fully expanded?
        // Let's check AutoScrollingTextOnDemand. Assuming it uses it for scrolling trigger.
        // If we want to avoid recomposition, we might need to pass the provider or just 1f if scrolling logic handles itself.
        // For now, let's pass the current value from provider for logic correctness, but ideally this component should be optimized too.
        AutoScrollingTextOnDemand(
            title,
            titleStyle,
            gradientEdgeColor,
            expansionFractionProvider,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))

        AutoScrollingTextOnDemand(
            text = artist,
            style = artistStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFractionProvider = expansionFractionProvider,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (isNavigatingToArtist) return@combinedClickable
                    coroutineScope.launch {
                        isNavigatingToArtist = true
                        try {
                            onClickArtist()
                        } finally {
                            isNavigatingToArtist = false
                        }
                    }
                },
                onLongClick = {
                    if (isNavigatingToArtist) return@combinedClickable
                    coroutineScope.launch {
                        isNavigatingToArtist = true
                        try {
                            playerViewModel.triggerArtistNavigationFromPlayer(artistId)
                        } finally {
                            isNavigatingToArtist = false
                        }
                    }
                }
            )
        )
    }
}

@Composable
private fun PlaceholderBox(
    modifier: Modifier,
    cornerRadius: Dp = 12.dp,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = color,
        tonalElevation = 0.dp
    ) {}
}

@Composable
private fun AlbumPlaceholder(height: Dp, color: Color, onColor: Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(18.dp),
        color = color,
        tonalElevation = 0.dp
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                modifier = Modifier.size(86.dp),
                painter = painterResource(R.drawable.pixelplay_base_monochrome),
                contentDescription = null,
                tint = onColor
            )
        }
    }
}

@Composable
private fun MetadataPlaceholder(expansionFraction: Float, color: Color, onColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            // Removed vertical padding lerp to match real content's 70dp heightIn exactly
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxWidth(0.9f)
                .align(Alignment.CenterVertically),
            verticalArrangement = Arrangement.spacedBy(10.dp) // Adjusted spacing to match visual density
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.7f) // Simulate title length
                    .height(24.dp),
                cornerRadius = 4.dp,
                color = color
            )
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth(0.4f) // Simulate artist length
                    .height(16.dp),
                cornerRadius = 4.dp,
                color = onColor
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        PlaceholderBox(
            modifier = Modifier
                .size(42.dp),
            cornerRadius = 50.dp,
            color = onColor
        )
    }
}

@Composable
private fun ProgressPlaceholder(expansionFraction: Float, color: Color, onColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = expansionFraction }
            .heightIn(min = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp), // Match WavySlider track height
                cornerRadius = 3.dp,
                color = color
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PlaceholderBox(modifier = Modifier.width(30.dp).height(12.dp), cornerRadius = 2.dp, color = onColor)
            PlaceholderBox(modifier = Modifier.width(30.dp).height(12.dp), cornerRadius = 2.dp, color = onColor)
        }
    }
}

@Composable
private fun ControlsPlaceholder(color: Color, onColor: Color) {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 // 5 buttons: Prev, Play/Pause, Next + 2 smaller extras
                 // Size order: 42, 42, 64, 42, 42
                 val sizes = listOf(74.dp, 74.dp, 74.dp)
                 sizes.forEach { size ->
                     PlaceholderBox(
                         modifier = Modifier.size(size),
                         cornerRadius = size / 2, // Circle
                         color = if (size == 64.dp) color else onColor
                     )
                 }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Toggles Row
            PlaceholderBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp) // Avg between min 58 and max 78
                    .padding(horizontal = 26.dp)
                    .padding(bottom = 6.dp),
                cornerRadius = 30.dp,
                color = onColor
            )
        }
    }
}

@Composable
private fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val isFavorite = isFavoriteProvider()
    val rowCorners = 60.dp
    val inactiveBg = LocalMaterialTheme.current.primary.copy(alpha = 0.08f)

    Box(
        modifier = modifier.background(
            color = LocalMaterialTheme.current.onPrimary,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = rowCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = rowCorners,
                smoothnessAsPercentBL = 60,
                cornerRadiusTL = rowCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = rowCorners,
                smoothnessAsPercentTL = 60
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTL = 60
                    )
                )
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = isShuffleEnabled,
                activeColor = LocalMaterialTheme.current.primary,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onPrimary,
                inactiveColor = inactiveBg,
                onClick = onShuffleToggle,
                iconId = R.drawable.rounded_shuffle_24,
                contentDesc = "Aleatorio"
            )
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_24
                Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_24
                else -> R.drawable.rounded_repeat_24
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = LocalMaterialTheme.current.secondary,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onSecondary,
                inactiveColor = inactiveBg,
                onClick = onRepeatToggle,
                iconId = repeatIcon,
                contentDesc = "Repetir"
            )
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = LocalMaterialTheme.current.tertiary,
                activeCornerRadius = rowCorners,
                activeContentColor = LocalMaterialTheme.current.onTertiary,
                inactiveColor = inactiveBg,
                onClick = onFavoriteToggle,
                iconId = R.drawable.round_favorite_24,
                contentDesc = "Favorito"
            )
        }
    }
}

@Composable
fun ToggleSegmentButton(
    modifier: Modifier,
    active: Boolean,
    activeColor: Color,
    inactiveColor: Color = Color.Gray,
    activeContentColor: Color = LocalMaterialTheme.current.onPrimary,
    activeCornerRadius: Dp = 8.dp,
    onClick: () -> Unit,
    iconId: Int,
    contentDesc: String
) {
    val bgColor by animateColorAsState(
        targetValue = if (active) activeColor else inactiveColor,
        animationSpec = tween(durationMillis = 250),
        label = ""
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (active) activeCornerRadius else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = ""
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconId),
            contentDescription = contentDesc,
            tint = if (active) activeContentColor else LocalMaterialTheme.current.primary,
            modifier = Modifier.size(24.dp)
        )
    }
}
