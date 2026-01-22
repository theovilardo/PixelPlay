package com.theveloper.pixelplay.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.Lyrics

@Immutable
data class StablePlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isLoadingLyrics: Boolean = false,
    val lyrics: Lyrics? = null
)
