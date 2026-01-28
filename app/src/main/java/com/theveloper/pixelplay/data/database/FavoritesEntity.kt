package com.theveloper.pixelplay.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoritesEntity(
    @PrimaryKey val songId: Long,
    val isFavorite: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
