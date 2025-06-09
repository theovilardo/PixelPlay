package com.theveloper.pixelplay.presentation.components

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Sleep Timer", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            // Predefined times
            Text("Predefined Times", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                val predefinedTimes = listOf(5, 10, 15, 20, 30, 45, 60)
                predefinedTimes.forEach { time ->
                    Button(onClick = {
                        onSetPredefinedTimer(time)
                        onDismiss()
                    }) {
                        Text("$time min")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // End of track option
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("End of current track", modifier = Modifier.weight(1f))
                Switch(
                    checked = isEndOfTrackTimerActive,
                    onCheckedChange = {
                        onSetEndOfTrackTimer(it)
                        // Assuming the sheet might dismiss or update UI based on this
                        // onDismiss() // Or not, depending on desired UX
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Custom time picker
            Button(onClick = {
                // onOpenCustomTimePicker() // Call the passed lambda if it has other responsibilities
                showCustomTimePicker = true
            }) {
                Text("Set Custom Time")
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    },
                    enabled = activeTimerValueDisplay != null
                ) {
                    Text("Cancel Timer")
                }
                // As per instruction, direct selection sets timer, so no explicit "Set Timer" button for now.
                // If one was needed, it would go here.
                // Button(onClick = { /* Potentially call a general apply function if needed */ onDismiss() }) {
                // Text("Done")
                // }
            }
            Spacer(modifier = Modifier.height(16.dp)) // For bottom sheet handle spacing
        }
    }

    if (showCustomTimePicker) {
        val calendar = Calendar.getInstance()
        val initialHour = 0 // Default to 00 hours
        val initialMinute = 15 // Default to 15 minutes

        val timePickerDialog = remember(context) {
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    val totalMinutes = hourOfDay * 60 + minute
                    if (totalMinutes > 0) { // Ensure some time is set
                        onSetPredefinedTimer(totalMinutes)
                    }
                    showCustomTimePicker = false
                    onDismiss() // Dismiss the bottom sheet after setting time
                },
                initialHour,
                initialMinute,
                true // 24-hour format
            ).apply {
                setOnCancelListener {
                    showCustomTimePicker = false
                }
            }
        }

        LaunchedEffect(Unit) {
            timePickerDialog.show()
        }
    }
}
