package com.theveloper.pixelplay.presentation.telegram.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

@HiltViewModel
class TelegramChannelSearchViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _foundChat = MutableStateFlow<TdApi.Chat?>(null)
    val foundChat = _foundChat.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    // Status message for errors or "Not Found"
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage = _statusMessage.asStateFlow()

    private val _playbackRequest = kotlinx.coroutines.flow.MutableSharedFlow<Song>(extraBufferCapacity = 1)
    val playbackRequest = _playbackRequest.asSharedFlow()

    fun onQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun searchChannel() {
        val query = _searchQuery.value
        if (query.isNotEmpty()) {
            _isLoading.value = true
            _statusMessage.value = null
            _foundChat.value = null
            _songs.value = emptyList()
            
            viewModelScope.launch {
                // If it starts with @, remove it for consistency if needed, but TDLib handles username search usually.
                val chat = telegramRepository.searchPublicChat(query)
                _isLoading.value = false
                
                if (chat != null) {
                    _foundChat.value = chat
                    fetchSongs(chat.id)
                } else {
                    _statusMessage.value = "Channel not found"
                }
            }
        }
    }

    private fun fetchSongs(chatId: Long) {
        _isLoading.value = true
        _statusMessage.value = "Syncing songs from channel..." // Improve with chat title if possible, but valid enough.
        
        viewModelScope.launch {
            val fetchedSongs = telegramRepository.getAudioMessages(chatId)
            
            if (fetchedSongs.isNotEmpty()) {
                musicRepository.saveTelegramSongs(fetchedSongs)
                
                // Save Channel Entity
                val chat = _foundChat.value
                val currentQuery = _searchQuery.value
                if (chat != null) {
                    var localPhotoPath: String? = null
                    val photoFileId = chat.photo?.small?.id
                    if (photoFileId != null) {
                         localPhotoPath = telegramRepository.downloadFileAwait(photoFileId)
                    }
                    
                    val entity = com.theveloper.pixelplay.data.database.TelegramChannelEntity(
                        chatId = chat.id,
                        title = chat.title,
                        username = if (currentQuery.startsWith("@")) currentQuery else null,
                        songCount = fetchedSongs.size,
                        lastSyncTime = System.currentTimeMillis(),
                        photoPath = localPhotoPath
                    )
                    musicRepository.saveTelegramChannel(entity)
                }

                _statusMessage.value = "Success! ${fetchedSongs.size} songs added to library. You can close this window."
            } else {
                _statusMessage.value = "No audio songs found in this channel."
            }
            // We do NOT update _songs to avoid showing the list
            _songs.value = emptyList()
            _isLoading.value = false
        }
    }

    fun downloadAndPlay(song: Song) {
        if (song.telegramFileId == null) return
        
        _isLoading.value = true
        _statusMessage.value = "Downloading ${song.title}..."
        
        viewModelScope.launch {
            val localPath = telegramRepository.downloadFileAwait(song.telegramFileId)
            _isLoading.value = false
            
            if (localPath != null) {
                // Create a new Song with the local path
                val playableSong = song.copy(path = localPath, contentUriString = localPath)
                musicRepository.saveTelegramSongs(listOf(playableSong)) // Update DB with path
                _playbackRequest.tryEmit(playableSong)
                _statusMessage.value = "Playing..."
            } else {
                _statusMessage.value = "Failed to download song"
            }
        }
    }
}
