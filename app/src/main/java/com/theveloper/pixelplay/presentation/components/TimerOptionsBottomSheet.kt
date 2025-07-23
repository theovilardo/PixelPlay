package com.theveloper.pixelplay.presentation.components

import android.app.TimePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.Calendar
import kotlin.math.roundToInt

val predefinedTimes = listOf(0, 5, 10, 15, 20, 30, 45, 60) // 0 represents 'Off'

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerOptionsBottomSheet(
    activeTimerValueDisplay: String?, // e.g., "15 minutes", "End of Track"
    isEndOfTrackTimerActive: Boolean,
    onDismiss: () -> Unit,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit, // This could be removed if not used elsewhere, but kept for now
    onCancelTimer: () -> Unit
) {
    var showCustomTimePicker by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var sliderPosition by remember { mutableStateOf(0f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isSwitchEnabled = isEndOfTrackTimerActive

    // Animate background color
    val boxBackgroundColor by animateColorAsState(
        targetValue = if (isSwitchEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "boxBackgroundColorAnimation"
    )

    // Animate corner radius
    val boxCornerRadius by animateDpAsState(
        targetValue = if (isSwitchEnabled) 18.dp else 50.dp, // Or your default CircleShape equivalent for "not rounded"
        // If your original was CircleShape, it implies 50% corners.
        // For a more controlled animation to 0dp from rounded:
        //  start with RoundedCornerShape(0.dp) for the 'else' case
        //  if you want to animate from fully sharp corners.
        //  Using CircleShape directly in 'else' might not animate smoothly to 18.dp.
        label = "boxCornerRadiusAnimation"
    )

    LaunchedEffect(activeTimerValueDisplay) {
        sliderPosition = when {
            activeTimerValueDisplay == null -> 0f // Off
            activeTimerValueDisplay == "End of Track" -> 0f // Slider shows 'Off' as EOT is a separate control
            activeTimerValueDisplay.startsWith("Custom:") -> 0f // Slider shows 'Off' if custom time is active via a different mechanism
            else -> {
                val minutesString = activeTimerValueDisplay.removeSuffix(" minutes")
                val minutesInt = minutesString.toIntOrNull()
                val index = minutesInt?.let { predefinedTimes.indexOf(it) } ?: -1
                if (index != -1) index.toFloat() else 0f
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    text = "Sleep Timer",
                    fontFamily = GoogleSansRounded,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Predefined times replaced by Slider
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val currentIndex = sliderPosition.roundToInt().coerceIn(0, predefinedTimes.size - 1)
                val currentMinutes = predefinedTimes[currentIndex]
                val displayText = if (currentMinutes == 0) "Off" else "$currentMinutes minutes"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(
                                topEnd = 18.dp,
                                topStart = 18.dp,
                                bottomStart = 6.dp,
                                bottomEnd = 6.dp
                            )
                        )
                ) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        valueRange = 0f..(predefinedTimes.size - 1).toFloat(),
                        steps = predefinedTimes.size - 2, // Number of discrete intervals
                        onValueChangeFinished = {
                            val selectedIndexOnFinish = sliderPosition.roundToInt().coerceIn(0, predefinedTimes.size - 1)
                            val selectedMinutesOnFinish = predefinedTimes[selectedIndexOnFinish]
                            if (selectedMinutesOnFinish == 0) {
                                if (activeTimerValueDisplay != null && activeTimerValueDisplay != "End of Track") { // Only cancel if a duration timer was set
                                    onCancelTimer()
                                }
                            } else {
                                onSetPredefinedTimer(selectedMinutesOnFinish)
                            }
                            // Consider dismissing the sheet: onDismiss()
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                // Le pasamos el estado que nos provee el Slider
                                sliderState = sliderState,
                                // El modifier para la altura sigue funcionando igual
                                modifier = Modifier
                                    .heightIn(min = 32.dp), // <-- ¡Aquí defines la altura!
                                // Los colores y el estado 'enabled' se infieren del Slider principal,
                                // por lo que no necesitas especificarlos a menos que quieras anularlos.
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(
                                bottomEnd = 18.dp,
                                bottomStart = 18.dp,
                                topEnd = 6.dp,
                                topStart = 6.dp
                            )
                        )
                ) {
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (displayText == "Off") MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(14.dp)
                            .padding(start = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // End of track option
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp)
                    .clip(
                        AbsoluteSmoothCornerShape(
                            cornerRadiusBL = boxCornerRadius,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusTR = boxCornerRadius,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusTL = boxCornerRadius,
                            smoothnessAsPercentBL = 60,
                            cornerRadiusBR = boxCornerRadius,
                            smoothnessAsPercentTR = 60
                        )
                    ) // Apply animated corner radius for clipping
                    .background(color = boxBackgroundColor)   // Apply animated background color
                    .clickable(
                        onClick = {
                            onSetEndOfTrackTimer(!isSwitchEnabled)
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 16.dp), // Adjusted padding for better spacing
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "End of current track",
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp), // Added end padding for spacing from switch
                        color = if (isSwitchEnabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface // Adjust text color for contrast
                    )
                    Switch(
                        checked = isSwitchEnabled,
                        onCheckedChange = {
                            onSetEndOfTrackTimer(it)
                            // onDismiss() // Optional: based on desired UX
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.tertiary,
                            checkedTrackColor = MaterialTheme.colorScheme.tertiaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        thumbContent = if (isSwitchEnabled) {
                            { // Composable lambda for thumb content
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Switch is on",
                                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            }
                        } else {
                            null // No icon when switch is off
                        }
                    )
                }
            }

            // Combined Custom Time and Cancel Timer buttons in a single Row
            val buttonHeight = 68.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 6.dp), // Add some vertical padding around the Row
                horizontalArrangement = Arrangement.spacedBy(6.dp), // Or SpaceEvenly, or use weights
                verticalAlignment = Alignment.CenterVertically
            ) {

                // Custom time picker Button
                Button(
                    onClick = {
                        // onOpenCustomTimePicker() // If you have other logic for this callback
                        showCustomTimePicker = true
                    },
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        bottomStart = 50.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp
                    ),
                    modifier = Modifier
                        .weight(1f) // Give buttons equal space if desired
                        .height(buttonHeight)
                ) {
                    Text("Custom Time")
                }

                // Cancel Timer Button
                Button(
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(
                        topStart = 8.dp,
                        bottomStart = 8.dp,
                        topEnd = 50.dp,
                        bottomEnd = 50.dp
                    ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    enabled = activeTimerValueDisplay != null,
                    modifier = Modifier
                        .weight(1f) // Give buttons equal space if desired
                        .height(buttonHeight)
                ) {
                    Text("Cancel Timer")
                }
            }
            Spacer(modifier = Modifier.height(16.dp)) // For bottom sheet handle spacing
        }
    }

    if (showCustomTimePicker) {
        // Initialize the TimePickerState for Material 3 TimePicker.
        // For a duration picker, default hours to 0.
        val initialHour = 0    // Default to 0 hours for a duration
        val initialMinute = 15 // Default to 15 minutes for a duration

        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true // Consistent with your previous setting (24-hour format)
        )

        AlertDialog(
            onDismissRequest = {
                showCustomTimePicker = false // Dismiss the M3 dialog
                // No need to call onDismiss() for the bottom sheet here,
                // as that's handled by the confirm button or if the user specifically dismisses the bottom sheet.
            },
            title = { Text("Set Custom Duration") },
            text = {
                // Place the Material 3 TimePicker in the dialog's content area.
                TimePicker(state = timePickerState)
                // Optionally, if you also want the text input fields along with the clock:
                // Column {
                //     TimePicker(state = timePickerState)
                //     Spacer(modifier = Modifier.height(16.dp))
                //     TimeInput(state = timePickerState, modifier = Modifier.fillMaxWidth())
                // }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hour = timePickerState.hour
                        val minute = timePickerState.minute
                        val totalMinutes = hour * 60 + minute

                        if (totalMinutes > 0) { // Ensure some time is set
                            onSetPredefinedTimer(totalMinutes) // Your existing callback
                        }
                        showCustomTimePicker = false // Dismiss the M3 dialog
                        onDismiss() // Dismiss the bottom sheet after setting time, as per original logic
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomTimePicker = false // Dismiss the M3 dialog
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
//    if (showCustomTimePicker) {
//        val calendar = Calendar.getInstance()
//        val initialHour = 0 // Default to 00 hours
//        val initialMinute = 15 // Default to 15 minutes
//
//        val timePickerDialog = remember(context) {
//            TimePickerDialog(
//                context,
//                { _, hourOfDay, minute ->
//                    val totalMinutes = hourOfDay * 60 + minute
//                    if (totalMinutes > 0) { // Ensure some time is set
//                        onSetPredefinedTimer(totalMinutes)
//                    }
//                    showCustomTimePicker = false
//                    onDismiss() // Dismiss the bottom sheet after setting time
//                },
//                initialHour,
//                initialMinute,
//                true // 24-hour format
//            ).apply {
//                setOnCancelListener {
//                    showCustomTimePicker = false
//                }
//            }
//        }
//
//        LaunchedEffect(Unit) {
//            timePickerDialog.show()
//        }
//    }
}
