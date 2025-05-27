package com.theveloper.pixelplay.presentation.components.subcomps

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.components.WavyMusicSlider
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.utils.formatDuration
import kotlinx.coroutines.flow.map
import kotlin.math.roundToLong

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun PlayerProgressSection(
    playerViewModel: PlayerViewModel,
    onSeek: (Long) -> Unit,
    // Necesitamos estos estados para pasarlos a WavyMusicSlider si su comportamiento depende de ellos
    isPlaying: Boolean,
    currentSheetState: PlayerSheetState,
    // Los colores también pueden ser parámetros si quieres que sean dinámicos
    // o puedes leerlos del LocalMaterialTheme.current como está haciendo WavyMusicSlider
    modifier: Modifier = Modifier // Para el Column que envuelve esta sección
) {
    // Leer currentPosition y totalDuration directamente del ViewModel
    val currentPositionFromVM by remember(playerViewModel.playerUiState) {
        playerViewModel.playerUiState.map { it.currentPosition }
    }.collectAsStateWithLifecycle(initialValue = playerViewModel.playerUiState.value.currentPosition)

    val totalDurationFromVM by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.totalDuration }
    }.collectAsStateWithLifecycle(initialValue = playerViewModel.stablePlayerState.value.totalDuration)

    val progressFraction = remember(currentPositionFromVM, totalDurationFromVM) {
        (currentPositionFromVM.coerceAtLeast(0).toFloat() /
                totalDurationFromVM.coerceAtLeast(1).toFloat())
    }.coerceIn(0f, 1f)

    // Log para verificar recomposición de esta sección específica
    Log.d("Recomposition", "PlayerProgressSection RECOMPOSED - Pos: $currentPositionFromVM, Progress: $progressFraction")

    Column(modifier = modifier) { // El modifier pasado se aplica aquí
        WavyMusicSlider(
            value = progressFraction,
            onValueChange = { frac ->
                onSeek((frac * totalDurationFromVM).roundToLong())
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            // Pasamos isPlaying y currentSheetState para la lógica interna del WavyMusicSlider
            isPlaying = (isPlaying && currentSheetState == PlayerSheetState.EXPANDED),
            // Puedes parametrizar estos colores o dejar que WavyMusicSlider los tome del tema
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            thumbColor = MaterialTheme.colorScheme.primary
            // ...otros parámetros estables de WavyMusicSlider...
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatDuration(currentPositionFromVM),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            Text(
                formatDuration(totalDurationFromVM),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}