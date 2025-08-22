package com.theveloper.pixelplay.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.TransitionSettings

@Entity(
    tableName = "transition_rules",
    indices = [Index(value = ["playlistId", "fromTrackId", "toTrackId"], unique = true)]
)
data class TransitionRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: String,
    val fromTrackId: String?,
    val toTrackId: String?,
    @Embedded val settings: TransitionSettings
)
