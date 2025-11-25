package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMixMenu(
    onDismiss: () -> Unit,
    onApplyPrompt: (String) -> Unit,
    isLoading: Boolean
) {
    val sheetState = rememberModalBottomSheetState()
    var prompt by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Como se crea tu Daily Mix",
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tu Daily Mix se crea a partir de tus canciones favoritas y las que más escuchas. También añadimos canciones de artistas y géneros que te gustan para que descubras nueva música.",
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Dile a la IA qué quieres escuchar hoy") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Usamos una muestra pequeña para mantener los costos bajos") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onApplyPrompt(prompt)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = prompt.isNotBlank() && !isLoading
            ) {
                Text(if (isLoading) "Actualizando..." else "Actualizar Daily Mix")
            }
        }
    }
}
