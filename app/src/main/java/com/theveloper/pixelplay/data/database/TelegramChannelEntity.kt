package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telegram_channels")
data class TelegramChannelEntity(
    @PrimaryKey
    @ColumnInfo(name = "chat_id") val chatId: Long,
    
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "username") val username: String? = null,
    
    @ColumnInfo(name = "song_count") val songCount: Int = 0,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long = 0,
    
    @ColumnInfo(name = "photo_path") val photoPath: String? = null // Local path to cached profile photo
)
