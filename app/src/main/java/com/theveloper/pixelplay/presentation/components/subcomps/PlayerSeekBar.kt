package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.presentation.components.WavyMusicSlider
import com.theveloper.pixelplay.utils.formatDuration
import kotlin.math.roundToLong

@Composable
fun PlayerSeekBar(
    backgroundColor: Color,
    onBackgroundColor: Color,
    primaryColor: Color,
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val progressFraction = remember(currentPosition, totalDuration) {
        if (totalDuration > 0) {
            (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,          // nivel de sombra
                shape = CircleShape,       // la misma forma de clip
                clip = false               // importante: NO recortar la sombra
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(0.15f),
            text = formatDuration(currentPosition),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = onBackgroundColor,
            fontSize = 12.sp
        )
        WavyMusicSlider(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f),
            value = progressFraction,
            onValueChange = { newFraction ->
                onSeek((newFraction * totalDuration).roundToLong())
            },
            trackHeight = 6.dp,
            thumbRadius = 8.dp,
            activeTrackColor = primaryColor,
            inactiveTrackColor = primaryColor.copy(alpha = 0.2f),
            thumbColor = primaryColor,
            waveLength = 30.dp,
            isPlaying = isPlaying
        )
        Text(
            modifier = Modifier.weight(0.15f),
            text = formatDuration(totalDuration),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = onBackgroundColor,
            fontSize = 12.sp
        )
    }
}
