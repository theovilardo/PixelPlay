package com.theveloper.pixelplay.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AlbumArtThemeEntity::class, SearchHistoryEntity::class], version = 2, exportSchema = false)
//@TypeConverters(ColorConverters::class) // Necesitaremos conversores para los ColorScheme
abstract class PixelPlayDatabase : RoomDatabase() {
    abstract fun albumArtThemeDao(): AlbumArtThemeDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        // Example of a simple migration if needed.
        // For adding a new table, Room often handles it if schema validation passes.
        // However, explicit migrations are best practice for production.
        // val MIGRATION_1_2 = object : Migration(1, 2) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         database.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
        //     }
        // }
        // In a real app, this would be added to the Room.databaseBuilder() call:
        // .addMigrations(MIGRATION_1_2)
        // For this exercise, if fallbackToDestructiveMigration is used elsewhere, this explicit migration might not be strictly necessary
        // for the new table to be created, but it's good practice.
        // We will assume fallbackToDestructiveMigration is handled at the DI / DB instantiation level for now.
    }
}