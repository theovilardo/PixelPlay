package com.theveloper.pixelplay.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlbumArtThemeEntity::class,
        SearchHistoryEntity::class,
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class
    ],
    version = 5, // Incremented version for adding lyrics
    exportSchema = false
)
//@TypeConverters(ColorConverters::class) // Necesitaremos conversores para los ColorScheme
abstract class PixelPlayDatabase : RoomDatabase() {
    abstract fun albumArtThemeDao(): AlbumArtThemeDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun musicDao(): MusicDao // Added MusicDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN parent_directory_path TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN lyrics TEXT")
            }
        }

        // Example of a simple migration if needed.
        // For adding a new table, Room often handles it if schema validation passes.
        // However, explicit migrations are best practice for production.

        // MIGRATION_2_3 would be needed if not using fallbackToDestructiveMigration
        // val MIGRATION_2_3 = object : Migration(2, 3) {
        //     override fun migrate(database: SupportSQLiteDatabase) {
        //         // Room will create these tables automatically if they don't exist.
        //         // For explicit schema changes (e.g. ALTER TABLE), you'd add SQL here.
        //         // Since we are adding new tables, Room handles this.
        //         // If exportSchema = true, Room would validate this against the schema file.
        //     }
        // }

        // In a real app, this would be added to the Room.databaseBuilder() call:
        // .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        // We will assume fallbackToDestructiveMigration or manual addition of migrations
        // is handled at the DI / DB instantiation level for now.
        // For example, in AppModule:
        // Room.databaseBuilder(context, PixelPlayDatabase::class.java, "pixelplay_db")
        //     .addMigrations(PixelPlayDatabase.MIGRATION_3_4) // Add other migrations as needed
        //     .build()
    }
}