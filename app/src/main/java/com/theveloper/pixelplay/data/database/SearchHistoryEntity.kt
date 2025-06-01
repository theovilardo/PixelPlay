package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.SearchHistoryItem

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "query") val query: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long
)

fun SearchHistoryEntity.toSearchHistoryItem(): SearchHistoryItem {
    return SearchHistoryItem(
        id = this.id,
        query = this.query,
        timestamp = this.timestamp
    )
}

fun SearchHistoryItem.toEntity(): SearchHistoryEntity {
    return SearchHistoryEntity(
        id = this.id ?: 0, // Room will auto-generate if id is 0 and not set
        query = this.query,
        timestamp = this.timestamp
    )
}
