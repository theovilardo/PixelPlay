package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private enum class PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
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
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
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
            fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
                button -> expansionWeight
                null -> baseWeight
                else -> compressionWeight
            }

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight"
            )
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorOtherButtons)
                    .clickable {
                        lastClicked = PlaybackButtonType.PREVIOUS
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
                targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight"
            )
            val playCorner by animateDpAsState(
                targetValue = if (!isPlaying) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "playCorner"
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
                        lastClicked = PlaybackButtonType.PLAY_PAUSE
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = if (isPlaying) {
                        painterResource(R.drawable.rounded_pause_24)
                    } else {
                        painterResource(R.drawable.rounded_play_arrow_24)
                    },
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = tintPlayPauseIcon,
                    modifier = Modifier.size(playPauseIconSize)
                )
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight"
            )
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorOtherButtons)
                    .clickable {
                        lastClicked = PlaybackButtonType.NEXT
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
