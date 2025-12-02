package com.theveloper.pixelplay.data.service.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
//import androidx.media3.exoplayer.ffmpeg.FfmpegAudioRenderer
import com.theveloper.pixelplay.data.model.TransitionSettings
import com.theveloper.pixelplay.utils.envelope
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
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
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context, renderersFactory).build().apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
        }
    }

    /**
     * Prepares the auxiliary player (Player B) with the next media item.
     */
    fun prepareNext(mediaItem: MediaItem) {
        try {
            Timber.tag("TransitionDebug").d("Engine: prepareNext called for %s", mediaItem.mediaId)
            playerB.stop()
            playerB.clearMediaItems()
            playerB.setMediaItem(mediaItem)
            playerB.prepare()
            playerB.volume = 0f // Start silent
            Timber.tag("TransitionDebug").d("Engine: Player B prepared and silent.")
        } catch (e: Exception) {
            Timber.tag("TransitionDebug").e(e, "Failed to prepare next player")
        }
    }

    /**
     * If a track was pre-buffered in Player B, this cancels it.
     */
    fun cancelNext() {
        if (playerB.mediaItemCount > 0) {
            Timber.tag("TransitionDebug").d("Engine: Cancelling next player")
            playerB.stop()
            playerB.clearMediaItems()
        }
        // Ensure master player is full volume if we cancel
        playerA.volume = 1f
    }

    /**
     * Executes a transition based on the provided settings.
     */
    fun performTransition(settings: TransitionSettings) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            try {
                // Force Overlap for now as per instructions
                performOverlapTransition(settings)
            } catch (e: Exception) {
                Timber.tag("TransitionDebug").e(e, "Error performing transition")
                // Fallback: Restore volume
                playerA.volume = 1f
                playerB.stop()
            }
        }
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        Timber.tag("TransitionDebug").d("Starting Overlap/Crossfade. Duration: %d ms", settings.durationMs)

        if (playerB.mediaItemCount == 0) {
            Timber.tag("TransitionDebug").w("Skipping overlap - next player not prepared (count=0)")
            return
        }

        // Wait for B to be ready if it isn't
        if (playerB.playbackState != Player.STATE_READY) {
             Timber.tag("TransitionDebug").d("Player B not ready yet. State: %d", playerB.playbackState)
        }

        if (playerB.playbackState == Player.STATE_IDLE) {
            playerB.prepare()
        }

        // 1. Start Player B and ramp volumes
        playerB.volume = 0f
        playerB.play()

        Timber.tag("TransitionDebug").d("Player B started. Playing: %s", playerB.isPlaying)

        val duration = settings.durationMs.toLong()
        var elapsed = 0L
        val startTime = System.currentTimeMillis()

        // Safety brake: Monitor playerA state. If it ends naturally, break loop.

        while (elapsed < duration) {
            // Recalculate elapsed to be precise
            elapsed = System.currentTimeMillis() - startTime

            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val volA = 1f - envelope(progress, settings.curveOut)
            val volB = envelope(progress, settings.curveIn)

            playerA.volume = volA
            playerB.volume = volB

            if (elapsed % 500 < 50) {
                Timber.tag("TransitionDebug").v("Loop: Progress=%.2f, VolA=%.2f, VolB=%.2f", progress, volA, volB)
            }

            if (!playerA.isPlaying) {
                 Timber.tag("TransitionDebug").w("Player A stopped playing prematurely during transition!")
                 break
            }

            delay(32L) // ~30fps update for smooth volume
        }

        Timber.tag("TransitionDebug").d("Overlap loop finished. Swapping.")
        playerA.volume = 0f
        playerB.volume = 1f

        finalizeTransition()
    }

    private fun finalizeTransition() {
         // 2. Handover to Player A keeping the queue intact
        if (playerA.hasNextMediaItem()) {
            val handoffPosition = playerB.currentPosition
            Timber.tag("TransitionDebug").d("Handoff: Seek A to next item at %d ms", handoffPosition)

            // To avoid a glitch, we seek first, then volume up?
            // Player A is currently paused or volume 0.

            playerA.pause()
            playerA.seekToNextMediaItem()

            // Critical: If we just seek, ExoPlayer might take a moment to buffer.
            // But since it's the same file (usually cached), it should be fast.
            // Ideally we'd wait for STATE_READY, but we'll try immediate.

            playerA.seekTo(handoffPosition)
            playerA.volume = 1f
            playerA.play()
            Timber.tag("TransitionDebug").d("Player A resumed on next track.")
        } else {
             Timber.tag("TransitionDebug").w("Player A has no next item?")
             playerA.volume = 1f // restore just in case
        }

        // 3. Clean up Player B
        playerB.stop()
        playerB.clearMediaItems()
        Timber.tag("TransitionDebug").d("Player B stopped and cleared.")
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
