package com.theveloper.pixelplay.presentation.components.external

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.OptimizedAlbumArt
import com.theveloper.pixelplay.presentation.components.WavyMusicSlider
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.utils.formatDuration
import kotlin.math.roundToLong

@Composable
fun ExternalPlayerOverlay(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val playerUiState by playerViewModel.playerUiState.collectAsState()
    val remotePosition by playerViewModel.remotePosition.collectAsState()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsState()
    val currentSong = stablePlayerState.currentSong

    var sheetVisible by remember { mutableStateOf(false) }

    LaunchedEffect(currentSong) {
        if (currentSong != null) {
            sheetVisible = true
        } else {
            sheetVisible = false
            onDismiss()
        }
    }

    BackHandler(enabled = sheetVisible) {
        onDismiss()
    }

    val backgroundScrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)
    val safePadding = WindowInsets.safeDrawing.asPaddingValues()
    val bottomPadding = safePadding.calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = sheetVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
        }

        AnimatedVisibility(
            visible = sheetVisible && currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                tonalElevation = 12.dp,
                shadowElevation = 18.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                if (currentSong == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    val totalDuration = stablePlayerState.totalDuration.coerceAtLeast(0L)
                    val rawPosition = if (isRemotePlaybackActive) remotePosition else playerUiState.currentPosition
                    val position = rawPosition.coerceIn(0L, totalDuration)
                    val progressFraction = if (totalDuration > 0) position.toFloat() / totalDuration else 0f

                    var sliderPosition by remember(currentSong.id) { mutableStateOf(progressFraction) }
                    var isUserScrubbing by remember { mutableStateOf(false) }

                    LaunchedEffect(progressFraction) {
                        if (!isUserScrubbing) {
                            sliderPosition = progressFraction
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp)
                            .padding(bottom = bottomPadding)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .width(48.dp)
                                    .height(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_close_24),
                                    contentDescription = stringResource(id = R.string.close_external_player),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                tonalElevation = 4.dp
                            ) {
                                OptimizedAlbumArt(
                                    uri = currentSong.albumArtUriString,
                                    title = currentSong.title,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentSong.title,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentSong.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentSong.album,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        WavyMusicSlider(
                            value = sliderPosition,
                            onValueChange = { newValue ->
                                isUserScrubbing = true
                                sliderPosition = newValue.coerceIn(0f, 1f)
                            },
                            onValueChangeFinished = {
                                val targetPosition = (sliderPosition * totalDuration).roundToLong()
                                playerViewModel.seekTo(targetPosition)
                                isUserScrubbing = false
                            },
                            isPlaying = stablePlayerState.isPlaying,
                            isWaveEligible = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(position),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(totalDuration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { playerViewModel.previousSong() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_skip_previous_24),
                                    contentDescription = stringResource(id = R.string.previous_track)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(
                                onClick = { playerViewModel.playPause() },
                                modifier = Modifier.size(68.dp)
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (stablePlayerState.isPlaying) {
                                            R.drawable.rounded_pause_24
                                        } else {
                                            R.drawable.rounded_play_arrow_24
                                        }
                                    ),
                                    contentDescription = if (stablePlayerState.isPlaying) {
                                        stringResource(id = R.string.pause_playback)
                                    } else {
                                        stringResource(id = R.string.play_playback)
                                    },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            IconButton(onClick = { playerViewModel.nextSong() }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_skip_next_24),
                                    contentDescription = stringResource(id = R.string.next_track)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onOpenFullPlayer,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = stringResource(id = R.string.open_full_player),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
