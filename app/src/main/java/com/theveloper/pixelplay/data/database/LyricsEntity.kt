package com.theveloper.pixelplay.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val content: String,
    val isSynced: Boolean = false,
    val source: String? = null // "local", "remote", "embedded" - optional
)
