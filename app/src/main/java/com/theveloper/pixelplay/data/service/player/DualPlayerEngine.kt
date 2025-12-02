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
            playerB.stop()
            playerB.clearMediaItems()
            playerB.setMediaItem(mediaItem)
            playerB.prepare()
            playerB.volume = 0f // Start silent
        } catch (e: Exception) {
            Timber.e(e, "[Transitions] Failed to prepare next player")
        }
    }

    /**
     * If a track was pre-buffered in Player B, this cancels it.
     */
    fun cancelNext() {
        if (playerB.mediaItemCount > 0) {
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
                when (settings.mode) {
                    com.theveloper.pixelplay.data.model.TransitionMode.FADE_IN_OUT -> performFadeInOutTransition(settings)
                    com.theveloper.pixelplay.data.model.TransitionMode.OVERLAP, com.theveloper.pixelplay.data.model.TransitionMode.SMOOTH -> performOverlapTransition(settings)
                    com.theveloper.pixelplay.data.model.TransitionMode.NONE -> {
                        // No transition logic needed, the default player behavior should suffice.
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[Transitions] Error performing transition")
                // Fallback: Restore volume
                playerA.volume = 1f
                playerB.stop()
            }
        }
    }

    private suspend fun performFadeInOutTransition(settings: TransitionSettings) {
        Timber.d("[Transitions] Starting Fade In/Out")
        if (playerB.mediaItemCount == 0) {
            Timber.d("[Transitions] Skipping fade in/out - next player not prepared")
            return
        }
        if (playerB.playbackState == Player.STATE_IDLE) {
            playerB.prepare()
        }
        val halfDuration = settings.durationMs.toLong() / 2
        if (halfDuration <= 0) return

        // 1. Fade Out Player A
        var elapsed = 0L
        while (elapsed < halfDuration) {
            val progress = elapsed.toFloat() / halfDuration
            playerA.volume = 1f - envelope(progress, settings.curveOut)
            delay(50L)
            elapsed += 50L
        }
        playerA.volume = 0f
        playerA.pause()

        // 2. Start Player B (already prepared) and fade it in.
        playerB.volume = 0f
        playerB.play()
        elapsed = 0L
        while (elapsed < halfDuration) {
            val progress = elapsed.toFloat() / halfDuration
            playerB.volume = envelope(progress, settings.curveIn)
            delay(50L)
            elapsed += 50L
        }
        playerB.volume = 1f

        finalizeTransition()
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        Timber.d("[Transitions] Starting Overlap. Duration: %d", settings.durationMs)

        if (playerB.mediaItemCount == 0) {
            Timber.d("[Transitions] Skipping overlap - next player not prepared")
            return
        }
        if (playerB.playbackState == Player.STATE_IDLE) {
            playerB.prepare()
        }

        // 1. Start Player B and ramp volumes
        playerB.volume = 0f
        playerB.play()

        val duration = settings.durationMs.toLong()
        var elapsed = 0L
        val startTime = System.currentTimeMillis()

        // Safety brake: Monitor playerA state. If it ends naturally, break loop.

        while (elapsed < duration) {
            // Recalculate elapsed to be precise
            elapsed = System.currentTimeMillis() - startTime

            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            playerA.volume = 1f - envelope(progress, settings.curveOut)
            playerB.volume = envelope(progress, settings.curveIn)

            if (!playerA.isPlaying) {
                 Timber.w("[Transitions] Player A stopped playing prematurely during transition!")
                 break
            }

            delay(32L) // ~30fps update for smooth volume
        }

        Timber.d("[Transitions] Overlap loop finished. Swapping.")
        playerA.volume = 0f
        playerB.volume = 1f

        finalizeTransition()
    }

    private fun finalizeTransition() {
         // 2. Handover to Player A keeping the queue intact
        if (playerA.hasNextMediaItem()) {
            val handoffPosition = playerB.currentPosition
            Timber.d("[Transitions] Handoff: Seek A to next item at %d ms", handoffPosition)

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
        } else {
             Timber.w("[Transitions] Player A has no next item?")
             playerA.volume = 1f // restore just in case
        }

        // 3. Clean up Player B
        playerB.stop()
        playerB.clearMediaItems()
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
