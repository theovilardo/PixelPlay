package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackVolumeBottomSheet(
    theme: ProvidableCompositionLocal<ColorScheme>,
    initialVolume: Float,
    onDismiss: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sliderPosition by remember { mutableFloatStateOf(initialVolume) }
    val hapticFeedback = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }

    ModalBottomSheet(
        containerColor = theme.current.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = theme.current.primary) },
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = theme.current.secondaryContainer,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = "Volume",
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.current.secondary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            Slider(
                value = sliderPosition,
                onValueChange = { newValue ->
                    sliderPosition = newValue
                    onVolumeChange(newValue)
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onValueChangeFinished = {
                    onVolumeChange(sliderPosition)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = theme.current.primary
                ),
//                thumb = SliderDefaults.Thumb(
//                    interactionSource = interactionSource,
//                    colors = SliderDefaults.colors(
//                        thumbColor = theme.current.primary,
//                        activeTrackColor = theme.current.primary
//                    ),
//                    enabled = true,
//                ),
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(38.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = theme.current.primary,
                            inactiveTrackColor = theme.current.secondaryContainer,
                            activeTickColor = theme.current.primary
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .background(
                        color = theme.current.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${(sliderPosition * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded
                    ),
                    color = theme.current.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}