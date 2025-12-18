package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Song
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
    expansionFraction: Float,
    currentSheetState: PlayerSheetState,
    carouselStyle: String,
    loadingTweaks: FullPlayerLoadingTweaks,
    playerViewModel: PlayerViewModel, // For stable state like totalDuration and lyrics
    // State Providers
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    isFavoriteProvider: () -> Boolean,
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
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsState()
    val currentSongArtists by playerViewModel.currentSongArtists.collectAsState()

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
    val totalDurationValue by remember {
        playerViewModel.stablePlayerState.map { it.totalDuration }.distinctUntilChanged()
    }.collectAsState(initial = 0L)

    val stableControlAnimationSpec = remember {
        tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
    }

    val controlOtherButtonsColor = LocalMaterialTheme.current.primary.copy(alpha = 0.15f)
    val controlPlayPauseColor = LocalMaterialTheme.current.primary
    val controlTintPlayPauseIcon = LocalMaterialTheme.current.onPrimary
    val controlTintOtherIcons = LocalMaterialTheme.current.primary

    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val fullyExpandedThreshold = 0.995f
    val isBackgroundFullyExpanded = currentSheetState == PlayerSheetState.EXPANDED && expansionFraction >= fullyExpandedThreshold
    val placeholdersEnabled = loadingTweaks.showPlaceholders

    fun shouldDelay(toggle: Boolean) = loadingTweaks.delayAll || toggle
    fun delayThreshold(base: Float, toggle: Boolean) = if (shouldDelay(toggle)) fullyExpandedThreshold else base
    fun isComponentReady(toggle: Boolean) = !shouldDelay(toggle) || isBackgroundFullyExpanded

    val placeholdersTransparent = placeholdersEnabled && loadingTweaks.transparentPlaceholders
    val basePlaceholderColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.28f)
    val basePlaceholderOnColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.16f)
    val placeholderColor = if (placeholdersTransparent) basePlaceholderColor.copy(alpha = 0f) else basePlaceholderColor
    val placeholderOnColor = if (placeholdersTransparent) basePlaceholderOnColor.copy(alpha = 0f) else basePlaceholderOnColor

    @Composable
    fun PlaceholderBox(
        modifier: Modifier,
        cornerRadius: Dp = 12.dp,
        color: Color = placeholderColor
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(cornerRadius),
            color = color,
            tonalElevation = 0.dp
        ) {}
    }

    @Composable
    fun AlbumPlaceholder(height: Dp) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            shape = RoundedCornerShape(18.dp),
            color = placeholderColor,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    modifier = Modifier.size(86.dp),
                    painter = painterResource(R.drawable.pixelplay_base_monochrome),
                    contentDescription = null,
                    tint = placeholderOnColor
                )
            }
        }
    }

    @Composable
    fun MetadataPlaceholder() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .padding(vertical = lerp(2.dp, 10.dp, expansionFraction)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxWidth(0.9f)
                    .align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PlaceholderBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .padding(end = 50.dp),
                    cornerRadius = 60.dp
                )
                PlaceholderBox(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(16.dp),
                    cornerRadius = 60.dp,
                    color = placeholderOnColor
                )
            }
            PlaceholderBox(
                modifier = Modifier
                    .weight(0.15f)
                    .size(height = 48.dp, width = 42.dp),
                cornerRadius = 60.dp,
                color = placeholderOnColor
            )
        }
    }

    @Composable
    fun ProgressPlaceholder() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                //.padding(vertical = lerp(2.dp, 0.dp, expansionFraction))
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
                        .height(18.dp),
                    cornerRadius = 60.dp
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PlaceholderBox(modifier = Modifier.width(40.dp).height(12.dp), cornerRadius = 6.dp, color = placeholderOnColor)
                PlaceholderBox(modifier = Modifier.width(40.dp).height(12.dp), cornerRadius = 6.dp, color = placeholderOnColor)
            }
        }
    }

    @Composable
    fun ControlsPlaceholder() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                //.height(174.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(Triple(80.dp, 94.dp, 60.dp), Triple(80.dp, 94.dp, 28.dp), Triple(80.dp, 94.dp, 60.dp)).forEach { size ->
                        PlaceholderBox(
                            modifier = Modifier
                                .weight(1f)
                                .size(width = size.second, height = size.first),
                            cornerRadius = size.third
                        )
                    }
                }
                PlaceholderBox(
                    modifier = Modifier
                        .width(260.dp)
                        .height(68.dp),
                    cornerRadius = 60.dp,
                    color = placeholderOnColor
                )
            }
        }
    }

    // Lógica para el botón de Lyrics en el reproductor expandido
    val onLyricsClick = {
        val lyrics = stablePlayerState.lyrics
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
            onConfirm = {
                // El usuario confirma, iniciamos la búsqueda
                playerViewModel.fetchLyricsForCurrentSong()
            },
            onPickResult = { result ->
                playerViewModel.acceptLyricsSearchResultForCurrentSong(result)
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
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = lerp(4.dp, 8.dp, expansionFraction))
                .graphicsLayer {
                    alpha = expansionFraction
                }
        ) {
            val carouselHeight = when (carouselStyle) {
                CarouselStyle.NO_PEEK -> maxWidth
                CarouselStyle.ONE_PEEK -> maxWidth * 0.8f
                CarouselStyle.TWO_PEEK -> maxWidth * 0.6f // Main item is 60% of width
                else -> maxWidth * 0.8f
            }

            val albumThreshold = delayThreshold(0.34f, loadingTweaks.delayAlbumCarousel)
            val albumReady = expansionFraction >= albumThreshold

            Crossfade(targetState = albumReady, label = "AlbumSectionCrossfade") { ready ->
                if (ready) {
                    DeferAt(expansionFraction, albumThreshold) {
                        AlbumCarouselSection(
                            currentSong = song,
                            queue = currentPlaybackQueue,
                            expansionFraction = expansionFraction,
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
                            modifier = Modifier.height(carouselHeight) // Apply calculated height
                        )
                    }
                } else if (placeholdersEnabled && shouldDelay(loadingTweaks.delayAlbumCarousel)) {
                    AlbumPlaceholder(height = carouselHeight)
                }
            }
        }
    }

    @Composable
    fun ControlsSection() {
        Column(
            //modifier = Modifier.height(194.dp),
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
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                isFavoriteProvider = isFavoriteProvider,
                onShuffleToggle = onShuffleToggle,
                onRepeatToggle = onRepeatToggle,
                onFavoriteToggle = onFavoriteToggle
            )
        }
    }

    @Composable
    fun ControlsSectionContainer(threshold: Float) {
        val controlsReady = expansionFraction >= threshold || !shouldDelay(loadingTweaks.delayControls)
        Crossfade(targetState = controlsReady, label = "ControlsSectionCrossfade") { ready ->
            if (ready) {
                DeferAt(expansionFraction, threshold) {
                    ControlsSection()
                }
            } else if (placeholdersEnabled && shouldDelay(loadingTweaks.delayControls)) {
                ControlsPlaceholder()
            }
        }
    }

    @Composable
    fun PlayerProgressSection(progressThreshold: Float) {
        val progressReady = expansionFraction >= progressThreshold || !shouldDelay(loadingTweaks.delayProgressBar)

        Crossfade(targetState = progressReady, label = "ProgressSectionCrossfade") { ready ->
            if (ready) {
                PlayerProgressBarSection(
                    currentPositionProvider = currentPositionProvider,
                    totalDurationValue = totalDurationValue,
                    onSeek = onSeek,
                    expansionFraction = expansionFraction,
                    isPlayingProvider = isPlayingProvider,
                    currentSheetState = currentSheetState,
                    activeTrackColor = LocalMaterialTheme.current.primary,
                    inactiveTrackColor = LocalMaterialTheme.current.primary.copy(alpha = 0.2f),
                    thumbColor = LocalMaterialTheme.current.primary,
                    timeTextColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f),
                    progressThreshold = progressThreshold
                )
            } else if (placeholdersEnabled && shouldDelay(loadingTweaks.delayProgressBar)) {
                ProgressPlaceholder()
            }
        }
    }

    @Composable
    fun SongMetadataSection() {
        val metadataThreshold = delayThreshold(0.20f, loadingTweaks.delaySongMetadata)
        val metadataReady = expansionFraction >= metadataThreshold || !shouldDelay(loadingTweaks.delaySongMetadata)

            Crossfade(targetState = metadataReady, label = "MetadataSectionCrossfade") { ready ->
                if (ready) {
                    SongMetadataDisplaySection(
                    modifier = Modifier
                        .padding(start = 0.dp),
                    onClickLyrics = onLyricsClick,
                    song = song,
                    currentSongArtists = currentSongArtists,
                    expansionFraction = expansionFraction,
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
                    },
                    metadataThreshold = metadataThreshold
                )
            } else if (placeholdersEnabled && shouldDelay(loadingTweaks.delaySongMetadata)) {
                MetadataPlaceholder()
            }
        }
    }

    @Composable
    fun FullPlayerPortraitContent(paddingValues: PaddingValues) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = lerp(8.dp, 24.dp, expansionFraction),
                    vertical = lerp(0.dp, 0.dp, expansionFraction)
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            DeferAt(expansionFraction, delayThreshold(0.08f, loadingTweaks.delayAlbumCarousel)) {
                PrefetchAlbumNeighborsImg(
                    current = currentSong,
                    queue = currentPlaybackQueue,
                    radius = 2 // prev/next 2
                )
            }

            AlbumCoverSection()

            Box(Modifier.align(Alignment.Start)) {
                SongMetadataSection()
            }

            val controlsThreshold = delayThreshold(0.42f, loadingTweaks.delayControls)

            PlayerProgressSection(delayThreshold(0.08f, loadingTweaks.delayProgressBar))

            ControlsSectionContainer(controlsThreshold)
        }
    }

    @Composable
    fun FullPlayerLandscapeContent(paddingValues: PaddingValues) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(
                    horizontal = lerp(8.dp, 24.dp, expansionFraction),
                    vertical = lerp(0.dp, 0.dp, expansionFraction)
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
                        vertical = lerp(0.dp, 0.dp, expansionFraction)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                SongMetadataSection()
                val landscapeControlsThreshold = delayThreshold(0.05f, loadingTweaks.delayControls)

                PlayerProgressSection(delayThreshold(0.08f, loadingTweaks.delayProgressBar))

                ControlsSectionContainer(landscapeControlsThreshold)
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.pointerInput(currentSheetState, expansionFraction) {
            val isFullyExpanded =
                currentSheetState == PlayerSheetState.EXPANDED && expansionFraction >= 0.99f
            if (!isFullyExpanded) return@pointerInput

            val queueDragActivationThresholdPx = with(this) { 6.dp.toPx() }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var dragConsumedByQueue = false
                val velocityTracker = VelocityTracker()
                totalDrag = 0f

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

                totalDrag = 0f
            }
        },
        topBar = {
            if (!isLandscape) {
                TopAppBar(
                    modifier = Modifier.alpha(expansionFraction.coerceIn(0f, 1f)),
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                            val castButtonWidth by animateDpAsState(
                                targetValue = if (showCastLabel) 176.dp else 50.dp,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                            )
                            val castContainerColor by animateColorAsState(
                                targetValue = LocalMaterialTheme.current.onPrimary,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            )
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .width(castButtonWidth)
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
                                        .fillMaxWidth()
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
                                                        overflow = TextOverflow.Ellipsis
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
        LyricsSheet(
            stablePlayerStateFlow = playerViewModel.stablePlayerState,
            playerUiStateFlow = playerViewModel.playerUiState,
            lyricsSearchUiState = lyricsSearchUiState,
            resetLyricsForCurrentSong = {
                showLyricsSheet = false
                playerViewModel.resetLyricsForCurrentSong()
            },
            onSearchLyrics = { playerViewModel.fetchLyricsForCurrentSong() },
            onPickResult = { playerViewModel.acceptLyricsSearchResultForCurrentSong(it) },
            onImportLyrics = { filePickerLauncher.launch("*/*") },
            onDismissLyricsSearch = { playerViewModel.resetLyricsSearchState() },
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
    expansionFraction: Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    playerViewModel: PlayerViewModel,
    onClickLyrics: () -> Unit,
    showQueueButton: Boolean,
    onClickQueue: () -> Unit,
    metadataThreshold: Float,
    onClickArtist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Absolute.SpaceBetween
    ) {
        song?.let { currentSong ->
            DeferAt(expansionFraction, metadataThreshold) {
                PlayerSongInfo(
                    title = currentSong.title,
                    artist = currentSong.artist,
                    artistId = currentSong.artistId,
                    artists = currentSongArtists,
                    expansionFraction = expansionFraction,
                    textColor = textColor,
                    artistTextColor = artistTextColor,
                    gradientEdgeColor = gradientEdgeColor,
                    playerViewModel = playerViewModel,
                    onClickArtist = onClickArtist,
                    modifier = Modifier
                        .weight(0.85f)
                        .align(Alignment.CenterVertically)
                )
            }
        }
        Spacer(
            modifier = Modifier
                .width(8.dp)
        )

        if (showQueueButton) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                    .weight(0.15f)
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
    expansionFraction: Float,
    isPlayingProvider: () -> Boolean,
    currentSheetState: PlayerSheetState,
    activeTrackColor: Color,
    inactiveTrackColor: Color,
    thumbColor: Color,
    timeTextColor: Color,
    progressThreshold: Float,
    modifier: Modifier = Modifier
) {
    val isExpanded = currentSheetState == PlayerSheetState.EXPANDED &&
            expansionFraction >= 0.995f

    val durationForCalc = totalDurationValue.coerceAtLeast(1L)
    val rawPosition = currentPositionProvider()
    val rawProgress = (rawPosition.coerceAtLeast(0) / durationForCalc.toFloat()).coerceIn(0f, 1f)

    val (smoothProgress, _) = rememberSmoothProgress(
        isPlayingProvider = isPlayingProvider,
        currentPositionProvider = currentPositionProvider,
        totalDuration = totalDurationValue,
        sampleWhilePlayingMs = 200L,
        sampleWhilePausedMs = 800L
    )

    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    val targetProgress = sliderDragValue ?: if (isExpanded) rawProgress else smoothProgress

    val animatedProgress = remember {
        Animatable(targetProgress)
    }

    LaunchedEffect(targetProgress, sliderDragValue != null) {
        val clampedTarget = targetProgress.coerceIn(0f, 1f)
        if (sliderDragValue != null) {
            animatedProgress.snapTo(clampedTarget)
        } else {
            animatedProgress.animateTo(
                targetValue = clampedTarget,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
        }
    }

    val effectiveProgress = animatedProgress.value
    val effectivePosition = (effectiveProgress * durationForCalc).roundToLong().coerceIn(0L, totalDurationValue.coerceAtLeast(0L))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = lerp(2.dp, 0.dp, expansionFraction))
            .graphicsLayer { alpha = expansionFraction }
            .heightIn(min = 70.dp)
    ) {
        DeferAt(expansionFraction = expansionFraction, threshold = progressThreshold) {
            WavyMusicSlider(
                value = effectiveProgress,
                onValueChange = { newValue -> sliderDragValue = newValue },
                onValueChangeFinished = {
                    sliderDragValue?.let { finalValue ->
                        onSeek((finalValue * durationForCalc).roundToLong())
                    }
                    sliderDragValue = null
                },
                interactionSource = interactionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                trackHeight = 6.dp,
                thumbRadius = 8.dp,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = inactiveTrackColor,
                thumbColor = thumbColor,
                waveLength = 30.dp,
                isPlaying = (isPlayingProvider() && isExpanded),
                isWaveEligible = isExpanded
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                formatDuration(effectivePosition),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = timeTextColor
            )
            Text(
                formatDuration(totalDurationValue),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = timeTextColor
            )
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
    expansionFraction: Float,
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
                .padding(vertical = lerp(2.dp, 10.dp, expansionFraction))
                .fillMaxWidth(0.9f)
            .graphicsLayer {
                alpha = expansionFraction
                translationY = (1f - expansionFraction) * 24f
            }
    ) {
        AutoScrollingTextOnDemand(title, titleStyle, gradientEdgeColor, expansionFraction)
        Spacer(modifier = Modifier.height(4.dp))
        
        AutoScrollingTextOnDemand(
            text = artist,
            style = artistStyle,
            gradientEdgeColor = gradientEdgeColor,
            expansionFraction = expansionFraction,
            modifier = Modifier.combinedClickable(
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
                Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_on_24
                Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_on_24
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
