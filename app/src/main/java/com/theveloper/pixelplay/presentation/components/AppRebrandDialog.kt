package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRebrandDialog(
    onDismiss: () -> Unit,
    onDoNotShowAgain: (Boolean) -> Unit
) {
    var doNotShowAgain by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.app_name_change_title)) },
        text = {
            Column {
                Text(text = stringResource(id = R.string.app_name_change_message))
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = { doNotShowAgain = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(id = R.string.do_not_show_again))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDoNotShowAgain(doNotShowAgain)
                    onDismiss()
                }
            ) {
                Text(text = stringResource(id = R.string.dismiss))
            }
        }
    )
}