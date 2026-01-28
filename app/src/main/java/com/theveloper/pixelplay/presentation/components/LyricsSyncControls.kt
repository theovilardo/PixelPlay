package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LyricsSyncControls(
    modifier: Modifier = Modifier,
    offsetMillis: Int,
    onOffsetChange: (Int) -> Unit,
    backgroundColor: Color,
    accentColor: Color,
    onAccentColor: Color,
    onBackgroundColor: Color
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // -0.5s
        SyncButton(
            text = "−.5",
            onClick = { onOffsetChange(offsetMillis - 500) },
            weight = 1f,
            containerColor = onAccentColor,
            contentColor = accentColor
        )
        // -0.1s
        SyncButton(
            text = "−.1",
            onClick = { onOffsetChange(offsetMillis - 100) },
            weight = 1f,
            containerColor = onAccentColor,
            contentColor = accentColor
        )
        // Center Display / Reset
        SyncButton(
            text = if (offsetMillis == 0) "0s" else String.format("%+.1fs", offsetMillis / 1000f),
            onClick = { onOffsetChange(0) },
            weight = 1.3f, // Slightly wider
            containerColor = if (offsetMillis != 0) accentColor.copy(alpha = 0.3f) else backgroundColor.copy(alpha = 0.7f),
            contentColor = onBackgroundColor,
            enabled = offsetMillis != 0,
            fontSize = 12.sp
        )
        // +0.1s
        SyncButton(
            text = "+.1",
            onClick = { onOffsetChange(offsetMillis + 100) },
            weight = 1f,
            containerColor = onAccentColor,
            contentColor = accentColor
        )
        // +0.5s
        SyncButton(
            text = "+.5",
            onClick = { onOffsetChange(offsetMillis + 500) },
            weight = 1f,
            containerColor = onAccentColor,
            contentColor = accentColor
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SyncButton(
    text: String,
    onClick: () -> Unit,
    weight: Float,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight(),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.5f),
            disabledContentColor = contentColor.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(0.dp) // Tight padding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
