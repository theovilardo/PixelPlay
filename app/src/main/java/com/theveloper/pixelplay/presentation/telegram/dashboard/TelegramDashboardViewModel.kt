package com.theveloper.pixelplay.presentation.telegram.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.TelegramChannelEntity
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class TelegramDashboardViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    // Expose channels flow directly
    val channels = musicRepository.getAllTelegramChannels()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isRefreshing = MutableStateFlow<Long?>(null) // ChatId being refreshed, or null
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    fun refreshChannel(channel: TelegramChannelEntity) {
        if (_isRefreshing.value != null) return

        viewModelScope.launch {
            _isRefreshing.value = channel.chatId
            _statusMessage.value = "Syncing ${channel.title}..."

            try {
                // Fetch latest songs (getAudioMessages implements pagination and fetches ALL)
                val songs = telegramRepository.getAudioMessages(channel.chatId)
                
                if (songs.isNotEmpty()) {
                    musicRepository.saveTelegramSongs(songs)
                    
                    // Update metadata
                    val updatedChannel = channel.copy(
                        songCount = songs.size,
                        lastSyncTime = System.currentTimeMillis()
                    )
                    musicRepository.saveTelegramChannel(updatedChannel)
                    
                    _statusMessage.value = "Synced ${songs.size} songs from ${channel.title}"
                } else {
                    _statusMessage.value = "No songs found in ${channel.title}"
                }
            } catch (e: Exception) {
                _statusMessage.value = "Sync failed: ${e.message}"
            } finally {
                _isRefreshing.value = null
            }
        }
    }

    fun removeChannel(chatId: Long) {
        viewModelScope.launch {
            musicRepository.deleteTelegramChannel(chatId)
            _statusMessage.value = "Channel removed"
        }
    }
    
    fun clearStatus() {
        _statusMessage.value = null
    }
}
