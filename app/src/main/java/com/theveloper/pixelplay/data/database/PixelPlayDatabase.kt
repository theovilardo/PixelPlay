package com.theveloper.pixelplay.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlbumArtThemeEntity::class,
        SearchHistoryEntity::class,
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        TransitionRuleEntity::class,
        SongArtistCrossRef::class,
        TelegramSongEntity::class,
        TelegramChannelEntity::class,
        SongEngagementEntity::class
    ],
    version = 15, // Incremented for combined updates
    exportSchema = false
)
abstract class PixelPlayDatabase : RoomDatabase() {
    abstract fun albumArtThemeDao(): AlbumArtThemeDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun musicDao(): MusicDao
    abstract fun transitionDao(): TransitionDao
    abstract fun telegramDao(): TelegramDao
    abstract fun engagementDao(): EngagementDao

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN mime_type TEXT")
                db.execSQL("ALTER TABLE songs ADD COLUMN bitrate INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN sample_rate INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN album_artist TEXT DEFAULT NULL")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS song_artist_cross_ref (
                        song_id INTEGER NOT NULL,
                        artist_id INTEGER NOT NULL,
                        is_primary INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (song_id, artist_id),
                        FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
                        FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_song_id ON song_artist_cross_ref(song_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_artist_id ON song_artist_cross_ref(artist_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_is_primary ON song_artist_cross_ref(is_primary)")
                
                db.execSQL("""
                    INSERT OR REPLACE INTO song_artist_cross_ref (song_id, artist_id, is_primary)
                    SELECT id, artist_id, 1 FROM songs WHERE artist_id IS NOT NULL
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artists ADD COLUMN image_url TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS telegram_songs (
                        id TEXT NOT NULL PRIMARY KEY,
                        chat_id INTEGER NOT NULL,
                        message_id INTEGER NOT NULL,
                        file_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        mime_type TEXT NOT NULL,
                        date_added INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telegram_songs ADD COLUMN album_art_uri TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS telegram_channels (
                        chat_id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        username TEXT,
                        song_count INTEGER NOT NULL DEFAULT 0,
                        last_sync_time INTEGER NOT NULL DEFAULT 0,
                        photo_path TEXT
                    )
                """.trimIndent())
            }
        }
        
        val MIGRATION_14_15 = object : Migration(14, 15) {
             override fun migrate(db: SupportSQLiteDatabase) {
                // Create song_engagements table for tracking play statistics
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS song_engagements (
                        song_id TEXT NOT NULL PRIMARY KEY,
                        play_count INTEGER NOT NULL DEFAULT 0,
                        total_play_duration_ms INTEGER NOT NULL DEFAULT 0,
                        last_played_timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
    }
}