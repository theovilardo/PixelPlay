package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.LyricsSearchUiState

/**
 * Diálogo que gestiona la búsqueda de letras para una canción.
 * Muestra diferentes contenidos según el estado de la búsqueda.
 */
@Composable
fun FetchLyricsDialog(
    uiState: LyricsSearchUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    when (uiState) {
        LyricsSearchUiState.Idle -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(text = stringResource(R.string.lyrics_not_found)) },
                text = { Text(text = stringResource(R.string.search_lyrics_online_prompt)) },
                confirmButton = {
                    TextButton(onClick = onConfirm) {
                        Text(stringResource(R.string.search))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        LyricsSearchUiState.Loading -> {
            AlertDialog(
                onDismissRequest = { /* No se puede cerrar mientras carga */ },
                title = { Text(text = stringResource(R.string.searching_lyrics)) },
                text = { CircularProgressIndicator() },
                confirmButton = {}
            )
        }
        is LyricsSearchUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(text = stringResource(R.string.error)) },
                text = { Text(text = uiState.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
        is LyricsSearchUiState.Success -> {
            // El éxito se maneja cerrando el diálogo y mostrando la LyricsSheet,
            // por lo que no se necesita un diálogo específico aquí.
            // El onDismiss se llamará desde el colector del estado.
        }
    }
}