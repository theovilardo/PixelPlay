package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Song
import java.util.concurrent.TimeUnit

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun formatTotalDuration(songs: List<Song>): String {
    val totalMillis = songs.sumOf { it.duration }
    val hours = TimeUnit.MILLISECONDS.toHours(totalMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(totalMillis) % 60
    return if (hours > 0) {
        String.format("%d h %02d min", hours, minutes)
    } else {
        String.format("%d min", minutes)
    }
}

