package com.theveloper.pixelplay.data.service.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.utils.envelope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages two ExoPlayer instances (A and B) to enable seamless transitions.
 *
 * Player A is the designated "master" player, which is exposed to the MediaSession.
 * Player B is the auxiliary player used to pre-buffer and fade in the next track.
 * After a transition, Player A adopts the state of Player B, ensuring continuity.
 */
@OptIn(UnstableApi::class)
@Singleton
class DualPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionJob: Job? = null

    private val playerA: ExoPlayer
    private val playerB: ExoPlayer

    /** The master player instance that should be connected to the MediaSession. */
    val masterPlayer: Player
        get() = playerA

    init {
        playerA = buildPlayer()
        playerB = buildPlayer()
    }

    private fun buildPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }
    }

    /**
     * Prepares the auxiliary player (Player B) with the next media item.
     */
    fun prepareNext(mediaItem: MediaItem) {
        playerB.setMediaItem(mediaItem)
        playerB.prepare()
    }

    /**
     * Executes a transition from Player A to Player B based on the provided settings.
     */
    fun performTransition(settings: TransitionSettings) {
        // Cancel any ongoing transition to start the new one.
        transitionJob?.cancel()

        // Ensure Player B has a media item ready to play.
        if (playerB.mediaItemCount == 0) return

        transitionJob = scope.launch {
            // Start Player B muted and playing in the background.
            playerB.volume = 0f
            playerB.play()

            val duration = settings.durationMs.toLong()
            val interval = 50L // Update volume every 50ms for a smooth ramp.
            var elapsed = 0L

            // Animate volume levels over the transition duration.
            while (elapsed < duration) {
                val progress = elapsed.toFloat() / duration
                playerA.volume = 1f - envelope(progress, settings.curveOut)
                playerB.volume = envelope(progress, settings.curveIn)
                delay(interval)
                elapsed += interval
            }

            // Ensure the final volume levels are set correctly.
            playerA.volume = 0f
            playerB.volume = 1f

            // --- The Handover ---
            // Player A takes over the media item and state from Player B.
            val nextMediaItem = playerB.currentMediaItem!!
            val nextPosition = playerB.currentPosition

            playerA.setMediaItem(nextMediaItem, nextPosition)
            playerA.volume = 1f // Restore master player volume.
            playerA.prepare()
            playerA.play()

            // Reset Player B to be ready for the next cycle.
            playerB.stop()
            playerB.clearMediaItems()
        }
    }

    /**
     * Cleans up resources when the engine is no longer needed.
     */
    fun release() {
        transitionJob?.cancel()
        playerA.release()
        playerB.release()
    }
}
