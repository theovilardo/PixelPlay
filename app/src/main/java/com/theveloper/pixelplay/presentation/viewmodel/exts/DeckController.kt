package com.theveloper.pixelplay.presentation.viewmodel.exts

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.theveloper.pixelplay.presentation.viewmodel.TrackStems

class DeckController(
    private val context: Context,
    private val onIsPlayingChanged: (Boolean) -> Unit
) {
    private val players = mutableMapOf<String, ExoPlayer>()
    private var primaryPlayer: ExoPlayer? = null

    fun loadStems(stemUris: Map<String, Uri>) {
        release()
        stemUris.forEach { (name, uri) ->
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
            players[name] = player
        }
        primaryPlayer = players["other"] ?: players.values.firstOrNull()
        primaryPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onIsPlayingChanged(isPlaying)
            }
        })
    }

    fun playPause() {
        val isPlaying = primaryPlayer?.isPlaying ?: false
        players.values.forEach { if (isPlaying) it.pause() else it.play() }
    }

    fun seek(progress: Float) {
        val duration = primaryPlayer?.duration?.takeIf { it > 0 } ?: return
        val position = (duration * progress).toLong()
        players.values.forEach { it.seekTo(position) }
    }

    fun setSpeed(speed: Float) {
        players.values.forEach { it.playbackParameters = PlaybackParameters(speed) }
    }

    fun nudge(amountMs: Long) {
        val duration = primaryPlayer?.duration ?: return
        val currentPosition = primaryPlayer?.currentPosition ?: return
        val newPosition = (currentPosition + amountMs).coerceIn(0, duration)
        players.values.forEach { it.seekTo(newPosition) }
    }

    fun setDeckVolume(deckVolume: Float, stemsState: TrackStems) {
        players["vocals"]?.volume = if (stemsState.vocals) deckVolume else 0f
        players["other"]?.volume = if (stemsState.instrumental) deckVolume else 0f
        players["bass"]?.volume = if (stemsState.bass) deckVolume else 0f
        players["drums"]?.volume = if (stemsState.drums) deckVolume else 0f
    }

    fun getProgress(): Float {
        val duration = primaryPlayer?.duration?.takeIf { it > 0 } ?: return 0f
        val position = primaryPlayer?.currentPosition ?: return 0f
        return (position.toFloat() / duration).coerceIn(0f, 1f)
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
        primaryPlayer = null
    }
}