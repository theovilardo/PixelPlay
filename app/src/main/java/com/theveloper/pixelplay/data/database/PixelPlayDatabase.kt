package com.theveloper.pixelplay.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [AlbumArtThemeEntity::class], version = 1, exportSchema = false)
//@TypeConverters(ColorConverters::class) // Necesitaremos conversores para los ColorScheme
abstract class PixelPlayDatabase : RoomDatabase() {
    abstract fun albumArtThemeDao(): AlbumArtThemeDao
}