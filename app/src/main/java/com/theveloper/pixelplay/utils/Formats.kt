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

fun formatListeningDurationLong(milliseconds: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return when {
        hours > 0 && minutes > 0 -> String.format("%d h %02d m", hours, minutes)
        hours > 0 -> String.format("%d h", hours)
        minutes > 0 -> String.format("%d m", minutes)
        else -> String.format("%d s", seconds)
    }
}

fun formatListeningDurationCompact(milliseconds: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return when {
        hours > 0 && minutes > 0 -> String.format("%dh %02dm", hours, minutes)
        hours > 0 -> String.format("%dh", hours)
        minutes > 0 -> String.format("%dm", minutes)
        else -> String.format("%ds", seconds)
    }
}

