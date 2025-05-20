package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LinearWavyPlayerProgress(
    progress: Float
) {
    // Animamos suavemente los cambios de progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec
    )

    LinearWavyProgressIndicator(
        progress    = { animatedProgress },
        modifier    = Modifier
            .fillMaxWidth()
            .height(4.dp),
        color       = WavyProgressIndicatorDefaults.indicatorColor,
        trackColor  = WavyProgressIndicatorDefaults.trackColor,
        stroke      = WavyProgressIndicatorDefaults.linearIndicatorStroke,
        trackStroke = WavyProgressIndicatorDefaults.linearTrackStroke,
        gapSize     = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
        stopSize    = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
        amplitude   = { it },                         // amplitud = función de progress :contentReference[oaicite:3]{index=3}
        wavelength  = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
        waveSpeed   = WavyProgressIndicatorDefaults.LinearDeterminateWavelength
    )
}
