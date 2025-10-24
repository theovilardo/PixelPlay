package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.data.model.Song

enum class StatsTimeframe {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL
}

data class PlaybackTrendEntry(
    val label: String,
    val durationMs: Long,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isCurrentPeriod: Boolean,
    val isPeak: Boolean
)

data class SongPlaybackSummary(
    val song: Song,
    val playCount: Int,
    val totalDurationMs: Long
)

data class PlaybackStatsOverview(
    val timeframe: StatsTimeframe,
    val totalDurationMs: Long,
    val totalPlayCount: Int,
    val averageDurationPerBucketMs: Long,
    val chartEntries: List<PlaybackTrendEntry>,
    val topSongs: List<SongPlaybackSummary>
)
