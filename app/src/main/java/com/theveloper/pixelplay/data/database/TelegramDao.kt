package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramDao {
    @Query("SELECT * FROM telegram_songs ORDER BY date_added DESC")
    fun getAllTelegramSongs(): Flow<List<TelegramSongEntity>>

    @Query("SELECT * FROM telegram_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY date_added DESC")
    fun searchSongs(query: String): Flow<List<TelegramSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<TelegramSongEntity>)
    
    @Query("DELETE FROM telegram_songs WHERE id = :id")
    suspend fun deleteSong(id: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: TelegramChannelEntity)

    @Query("SELECT * FROM telegram_channels ORDER BY title ASC")
    fun getAllChannels(): Flow<List<TelegramChannelEntity>>

    @Query("DELETE FROM telegram_channels WHERE chat_id = :chatId")
    suspend fun deleteChannel(chatId: Long)

    @Query("DELETE FROM telegram_songs WHERE chat_id = :chatId")
    suspend fun deleteSongsByChatId(chatId: Long)

    @Query("DELETE FROM telegram_songs")
    suspend fun clearAll()
}
