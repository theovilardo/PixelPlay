package com.theveloper.pixelplay.presentation.components.player

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.presentation.components.AlbumCarouselSection
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.AutoScrollingTextOnDemand
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.LyricsSheet
import com.theveloper.pixelplay.presentation.components.WavyMusicSlider
import com.theveloper.pixelplay.presentation.components.scoped.DeferAt
import com.theveloper.pixelplay.presentation.components.scoped.PrefetchAlbumNeighbors
import com.theveloper.pixelplay.presentation.components.scoped.PrefetchAlbumNeighborsImg
import com.theveloper.pixelplay.presentation.components.scoped.rememberSmoothProgress
import com.theveloper.pixelplay.presentation.components.subcomps.FetchLyricsDialog
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    currentSheetState: com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState,
    carouselStyle: String,
    playerViewModel: PlayerViewModel, // For stable state like totalDuration and lyrics
    // State Providers
    currentPositionProvider: () -> Long,
    isPlayingProvider: () -> Boolean,
    isFavoriteProvider: () -> Boolean,
    // Event Handlers
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCollapse: () -> Unit,
    onShowQueueClicked: () -> Unit,
    onShowCastClicked: () -> Unit,
    onShowTrackVolumeClicked: () -> Unit,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val song = currentSong ?: return // Early exit if no song
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val lyricsSearchUiState by playerViewModel.lyricsSearchUiState.collectAsState()

    var showFetchLyricsDialog by remember { mutableStateOf(false) }

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
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    val controlOtherButtonsColor = LocalMaterialTheme.current.primary.copy(alpha = 0.15f)
    val controlPlayPauseColor = LocalMaterialTheme.current.primary
    val controlTintPlayPauseIcon = LocalMaterialTheme.current.onPrimary
    val controlTintOtherIcons = LocalMaterialTheme.current.primary

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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.alpha(expansionFraction.coerceIn(0f, 1f)),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                    actionIconContentColor = LocalMaterialTheme.current.onPrimaryContainer,
                    navigationIconContentColor = LocalMaterialTheme.current.onPrimaryContainer
                ),
                title = {
                    Text(
                        modifier = Modifier.padding(start = 18.dp),
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        fontWeight = FontWeight.SemiBold
                    )
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
                        // Cast Button
//                        Box(
//                            modifier = Modifier
//                                .size(height = 42.dp, width = 50.dp)
//                                .clip(
//                                    RoundedCornerShape(
//                                        topStart = 50.dp,
//                                        topEnd = 6.dp,
//                                        bottomStart = 50.dp,
//                                        bottomEnd = 6.dp
//                                    )
//                                )
//                                .background(LocalMaterialTheme.current.onPrimary)
//                                .clickable { onShowCastClicked() },
//                            contentAlignment = Alignment.Center
//                        ) {
//                            Icon(
//                                painter = painterResource(R.drawable.rounded_cast_24),
//                                contentDescription = "Cast",
//                                tint = LocalMaterialTheme.current.primary
//                            )
//                        }

                        // Track Volume Button
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
                                .clickable { onShowTrackVolumeClicked() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_volume_up_24),
                                contentDescription = "Track Volume",
                                tint = LocalMaterialTheme.current.primary
                            )
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
    ) { paddingValues ->
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
            DeferAt(expansionFraction, 0.08f) {
                PrefetchAlbumNeighborsImg(
                    current = currentSong,
                    queue = currentPlaybackQueue,
                    radius = 2 // prev/next 2
                )
            }


            // Album Cover section
            BoxWithConstraints(
                modifier = Modifier
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

                DeferAt(expansionFraction, 0.34f) {
                    key(currentSong.id) {
                        AlbumCarouselSection(
                            currentSong = currentSong,
                            queue = currentPlaybackQueue,
                            expansionFraction = expansionFraction,
                            onSongSelected = { newSong ->
                                if (newSong.id != currentSong.id) {
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
                }
            }

            // Song Info - uses new Composable
            SongMetadataDisplaySection(
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 0.dp),
                onClickLyrics = onLyricsClick,
                song = currentSong, // currentSong is from stablePlayerState
                expansionFraction = expansionFraction,
                textColor = LocalMaterialTheme.current.onPrimaryContainer,
                artistTextColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.8f),
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer
                // modifier for PlayerSongInfo is internal to SongMetadataDisplaySection if needed, or pass one
            )

            // Progress Bar and Times - this section *will* recompose with currentPosition
            DeferAt(expansionFraction, 0.32f) {
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
                    timeTextColor = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            DeferAt(expansionFraction, 0.42f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SongMetadataDisplaySection( // Renamed for clarity
    song: Song?, // Nullable, comes from stablePlayerState
    expansionFraction: Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    onClickLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Absolute.SpaceBetween
    ) {
        song?.let { currentSong ->
            DeferAt(expansionFraction, 0.20f) {
                PlayerSongInfo(
                    title = currentSong.title,
                    artist = currentSong.artist,
                    expansionFraction = expansionFraction,
                    textColor = textColor,
                    artistTextColor = artistTextColor,
                    gradientEdgeColor = gradientEdgeColor,
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

@Composable
fun PlayerProgressBarSection(
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
    modifier: Modifier = Modifier
) {
    val isExpanded = currentSheetState == PlayerSheetState.EXPANDED &&
            expansionFraction >= 0.995f

    val rawPosition = currentPositionProvider()
    val rawProgress = (rawPosition.coerceAtLeast(0) / totalDurationValue.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    val (smoothProgress, sampledPosition) = rememberSmoothProgress(
        isPlayingProvider = isPlayingProvider,
        currentPositionProvider = currentPositionProvider,
        totalDuration = totalDurationValue,
        sampleWhilePlayingMs = 200L,
        sampleWhilePausedMs = 800L
    )


    var sliderDragValue by remember { mutableStateOf<Float?>(null) }
    val interactionSource = remember { MutableInteractionSource() }

    val effectiveProgress = sliderDragValue ?: if (isExpanded) rawProgress else smoothProgress
    val effectivePosition = sliderDragValue?.let { (it * totalDurationValue).roundToLong() }
        ?: if (isExpanded) rawPosition else sampledPosition


    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = lerp(2.dp, 0.dp, expansionFraction))
            .graphicsLayer { alpha = expansionFraction }
            .heightIn(min = 70.dp)
    ) {
        DeferAt(expansionFraction = expansionFraction, threshold = 0.08f) {
            WavyMusicSlider(
                value = effectiveProgress,
                onValueChange = { newValue -> sliderDragValue = newValue },
                onValueChangeFinished = {
                    sliderDragValue?.let { finalValue ->
                        onSeek((finalValue * totalDurationValue).roundToLong())
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
                waveFrequency = 0.08f,
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

@Composable
private fun PlayerSongInfo(
    title: String,
    artist: String,
    expansionFraction: Float,
    textColor: Color,
    artistTextColor: Color,
    gradientEdgeColor: Color,
    modifier: Modifier = Modifier
) {
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
        AutoScrollingTextOnDemand(artist, artistStyle, gradientEdgeColor, expansionFraction)
    }
}

private enum class ButtonType {
    NONE, PREVIOUS, PLAY_PAUSE, NEXT
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = LocalMaterialTheme.current.primary.copy(alpha = 0.15f),
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    tintOtherIcons: Color = LocalMaterialTheme.current.primary,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<ButtonType?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(releaseDelay)
            lastClicked = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fun weightFor(button: ButtonType): Float = when (lastClicked) {
                button   -> expansionWeight
                null     -> baseWeight
                else     -> compressionWeight
            }

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(ButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = ""
            )
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorOtherButtons)
                    .clickable {
                        lastClicked = ButtonType.PREVIOUS
                        onPrevious()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_skip_previous_24),
                    contentDescription = "Anterior",
                    tint = tintOtherIcons,
                    modifier = Modifier.size(iconSize)
                )
            }

            val playWeight by animateFloatAsState(
                targetValue = weightFor(ButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = ""
            )
            val playCorner by animateDpAsState(
                targetValue = if (!isPlaying) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "PlayCornerRadiusAnim"
            )
            val playShape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = playCorner,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = playCorner,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = playCorner,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = playCorner,
                smoothnessAsPercentBR = 60
            )
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .clip(playShape)
                    .background(colorPlayPause)
                    .clickable {
                        lastClicked = ButtonType.PLAY_PAUSE
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = if (isPlaying)
                        painterResource(R.drawable.rounded_pause_24)
                    else
                        painterResource(R.drawable.rounded_play_arrow_24),
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = tintPlayPauseIcon,
                    modifier = Modifier.size(playPauseIconSize)
                )
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(ButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = ""
            )
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorOtherButtons)
                    .clickable {
                        lastClicked = ButtonType.NEXT
                        onNext()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_skip_next_24),
                    contentDescription = "Siguiente",
                    tint = tintOtherIcons,
                    modifier = Modifier.size(iconSize)
                )
            }
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
                .background(Color.Transparent)
            ,
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