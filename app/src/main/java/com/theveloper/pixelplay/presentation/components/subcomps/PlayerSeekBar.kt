package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.presentation.components.WavyMusicSlider
import com.theveloper.pixelplay.utils.formatDuration
import kotlin.math.roundToLong

@Composable
fun PlayerSeekBar(
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        WavyMusicSlider(
            valueProvider = { progressFraction },
            onValueChange = { newFraction ->
                onSeek((newFraction * totalDuration).roundToLong())
            },
            modifier = Modifier.fillMaxWidth(),
            trackHeight = 6.dp,
            thumbRadius = 8.dp,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            thumbColor = MaterialTheme.colorScheme.primary,
            waveFrequency = 0.08f,
            isPlaying = isPlaying
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentPosition),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = formatDuration(totalDuration),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}
