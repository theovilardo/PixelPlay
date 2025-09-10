package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DismissUndoBar(
    modifier: Modifier = Modifier,
    onUndo: () -> Unit,
    onClose: () -> Unit,
    durationMillis: Long
) {
    var progress by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(key1 = onUndo) {
        progress = 1f // Reset progress when the bar appears
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() < startTime + durationMillis && progress > 0f) {
            progress = 1f - (System.currentTimeMillis() - startTime).toFloat() / durationMillis
            delay(16)
        }
        progress = 0f
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier.padding(start = 10.dp),
                    text = "Playlist Dismissed",
                    style = MaterialTheme.typography.titleSmallEmphasized
                        .copy(
                            fontFamily = GoogleSansRounded
                        ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onUndo
                    ) {
                        Text("Undo", color = MaterialTheme.colorScheme.primary)
                    }
                    FilledIconButton(
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        onClick = onClose
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTR = 12.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTL = 12.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusBR = 12.dp,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBL = 12.dp,
                            smoothnessAsPercentBR = 60
                        )
                    )
            )
        }
    }
}
