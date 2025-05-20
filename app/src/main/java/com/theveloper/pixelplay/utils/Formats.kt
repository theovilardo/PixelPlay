package com.theveloper.pixelplay.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.theveloper.pixelplay.data.model.Song
import java.util.concurrent.TimeUnit

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

// Función helper para formatear la duración total
fun formatTotalDuration(songs: List<Song>): String {
    val totalMillis = songs.sumOf { it.duration }
    val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
    // val seconds = TimeUnit.MILLISECONDS.toSeconds(totalMillis) % 60 // Podríamos omitir segundos para duración total
    return if (hours > 0) {
        String.format("%d h %02d min", hours, minutes)
    } else {
        String.format("%d min", minutes)
    }
}

// Helper para convertir Px a Dp
@Composable
fun pxToDp(px: Float): Dp = with(LocalDensity.current) { px.toDp() }