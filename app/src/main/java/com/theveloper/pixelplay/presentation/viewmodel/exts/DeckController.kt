package com.theveloper.pixelplay.presentation.viewmodel.exts

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class DeckController(
    private val context: Context
) {
    var player: ExoPlayer? = null
        private set

    fun loadSong(songUri: Uri) {
        release()
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(songUri))
            prepare()
        }
    }

    fun playPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }

    fun seek(progress: Float) {
        val duration = player?.duration?.takeIf { it > 0 } ?: return
        val position = (duration * progress).toLong()
        player?.seekTo(position)
    }

    fun setSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed)
    }

    fun nudge(amountMs: Long) {
        val duration = player?.duration ?: return
        val currentPosition = player?.currentPosition ?: return
        val newPosition = (currentPosition + amountMs).coerceIn(0, duration)
        player?.seekTo(newPosition)
    }

    fun setDeckVolume(deckVolume: Float) {
        player?.volume = deckVolume
    }

    fun getProgress(): Float {
        val duration = player?.duration?.takeIf { it > 0 } ?: return 0f
        val position = player?.currentPosition ?: return 0f
        return (position.toFloat() / duration).coerceIn(0f, 1f)
    }

    fun release() {
        player?.release()
        player = null
    }
}