package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing song engagement statistics.
 * This replaces the JSON-based storage in DailyMixManager for better performance
 * and structured querying.
 */
@Entity(tableName = "song_engagements")
data class SongEngagementEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,
    
    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,
    
    @ColumnInfo(name = "total_play_duration_ms")
    val totalPlayDurationMs: Long = 0L,
    
    @ColumnInfo(name = "last_played_timestamp")
    val lastPlayedTimestamp: Long = 0L
)
