package com.theveloper.pixelplay.presentation.telegram.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.telegram.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

@HiltViewModel
class TelegramLoginViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository,
    private val musicRepository: com.theveloper.pixelplay.data.repository.MusicRepository
) : ViewModel() {

    val authorizationState = telegramRepository.authorizationState

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber = _phoneNumber.asStateFlow()

    private val _code = MutableStateFlow("")
    val code = _code.asStateFlow()
    
    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun onPhoneNumberChanged(number: String) {
        _phoneNumber.value = number
    }

    fun onCodeChanged(c: String) {
        _code.value = c
    }
    
    fun onPasswordChanged(p: String) {
        _password.value = p
    }

    fun sendPhoneNumber() {
        if (_phoneNumber.value.isNotEmpty()) {
            _isLoading.value = true
            viewModelScope.launch {
                telegramRepository.sendPhoneNumber(_phoneNumber.value)
                _isLoading.value = false
            }
        }
    }

    fun checkCode() {
        if (_code.value.isNotEmpty()) {
            _isLoading.value = true
            viewModelScope.launch {
                telegramRepository.checkAuthenticationCode(_code.value)
                _isLoading.value = false
            }
        }
    }
    
    fun checkPassword() {
        if (_password.value.isNotEmpty()) {
            _isLoading.value = true
            viewModelScope.launch {
                telegramRepository.checkAuthenticationPassword(_password.value)
                _isLoading.value = false
            }
        }
    }

    private val _playbackRequest = kotlinx.coroutines.flow.MutableSharedFlow<com.theveloper.pixelplay.data.model.Song>(extraBufferCapacity = 1)
    val playbackRequest = _playbackRequest.asSharedFlow()

    fun downloadAndPlay(song: com.theveloper.pixelplay.data.model.Song) {
         if (song.telegramFileId == null) return
        _isLoading.value = true
        viewModelScope.launch {
            val localPath = telegramRepository.downloadFileAwait(song.telegramFileId)
            _isLoading.value = false
            if (localPath != null) {
                val playableSong = song.copy(path = localPath, contentUriString = localPath)
                _playbackRequest.tryEmit(playableSong)
            }
        }
    }

    fun clearData() {
        viewModelScope.launch {
            musicRepository.clearTelegramData()
             // Optional: Show toast or feedback via a state flow if needed, 
            // but the button might just be a fire-and-forget or handled by UI state.
             // For now, no return value.
        }
    }
}
