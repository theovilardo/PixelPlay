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
@Singleton
class DualPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionJob: Job? = null
    private var transitionRunning = false

    private val playerA: ExoPlayer
    private val playerB: ExoPlayer

    /** The master player instance that should be connected to the MediaSession. */
    val masterPlayer: Player
        get() = playerA

    fun isTransitionRunning(): Boolean = transitionRunning

    init {
        // Player A must handle audio focus to be the "master"
        playerA = buildPlayer(handleAudioFocus = true)
        // Player B must NOT handle audio focus, otherwise it would pause Player A when starting
        playerB = buildPlayer(handleAudioFocus = false)
    }

    private fun buildPlayer(handleAudioFocus: Boolean): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(context, renderersFactory).build().apply {
            setAudioAttributes(audioAttributes, handleAudioFocus)
            setHandleAudioBecomingNoisy(handleAudioFocus)
            // Explicitly keep both players live so they can overlap without affecting each other
            playWhenReady = false
        }
    }

    /**
     * Enables or disables pausing at the end of media items for the master player.
     * This is crucial for controlling the transition manually.
     */
    fun setPauseAtEndOfMediaItems(shouldPause: Boolean) {
        playerA.pauseAtEndOfMediaItems = shouldPause
    }

    /**
     * Prepares the auxiliary player (Player B) with the next media item.
     */
    fun prepareNext(mediaItem: MediaItem, startPositionMs: Long = 0L) {
        try {
            Timber.tag("TransitionDebug").d("Engine: prepareNext called for %s", mediaItem.mediaId)
            playerB.stop()
            playerB.clearMediaItems()
            playerB.playWhenReady = false
            playerB.setMediaItem(mediaItem)
            playerB.prepare()
            playerB.volume = 0f // Start silent
            if (startPositionMs > 0) {
                playerB.seekTo(startPositionMs)
            } else {
                playerB.seekTo(0)
            }
            // Critical: leave B paused so it can start instantly when asked
            playerB.pause()
            Timber.tag("TransitionDebug").d("Engine: Player B prepared, paused, volume=0f")
        } catch (e: Exception) {
            Timber.tag("TransitionDebug").e(e, "Failed to prepare next player")
        }
    }

    /**
     * If a track was pre-buffered in Player B, this cancels it.
     */
    fun cancelNext() {
        transitionJob?.cancel()
        transitionRunning = false
        if (playerB.mediaItemCount > 0) {
            Timber.tag("TransitionDebug").d("Engine: Cancelling next player")
            playerB.stop()
            playerB.clearMediaItems()
        }
        // Ensure master player is full volume if we cancel and reset focus logic
        playerA.volume = 1f
        setPauseAtEndOfMediaItems(false)
    }

    /**
     * Executes a transition based on the provided settings.
     */
    fun performTransition(settings: TransitionSettings) {
        transitionJob?.cancel()
        transitionRunning = true
        transitionJob = scope.launch {
            try {
                // Force Overlap for now as per instructions
                performOverlapTransition(settings)
            } catch (e: Exception) {
                Timber.tag("TransitionDebug").e(e, "Error performing transition")
                // Fallback: Restore volume and reset logic
                playerA.volume = 1f
                setPauseAtEndOfMediaItems(false)
                playerB.stop()
            } finally {
                transitionRunning = false
            }
        }
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        Timber.tag("TransitionDebug").d("Starting Overlap/Crossfade. Duration: %d ms", settings.durationMs)

        if (playerB.mediaItemCount == 0) {
            Timber.tag("TransitionDebug").w("Skipping overlap - next player not prepared (count=0)")
            return
        }

        // Ensure B is fully buffered and paused at the starting position
        if (playerB.playbackState == Player.STATE_IDLE) {
            Timber.tag("TransitionDebug").d("Player B idle. Preparing now.")
            playerB.prepare()
        }

        // Wait until READY (or until it is clearly failing) to guarantee instant start
        var readinessChecks = 0
        while (playerB.playbackState == Player.STATE_BUFFERING && readinessChecks < 120) {
            Timber.tag("TransitionDebug").v("Waiting for Player B to buffer (state=%d)", playerB.playbackState)
            delay(25)
            readinessChecks++
        }

        if (playerB.playbackState != Player.STATE_READY) {
            Timber.tag("TransitionDebug").w("Player B not ready for overlap. State=%d", playerB.playbackState)
            return
        }

        // 1. Start Player B paused with volume=0 then immediately request play so overlap is audible
        playerB.volume = 0f
        playerA.volume = 1f
        if (!playerA.isPlaying && playerA.playbackState == Player.STATE_READY) {
            // Ensure the outgoing track keeps rendering during the crossfade window
            playerA.play()
        }

        // Make sure PlayWhenReady is honored even if we had paused earlier
        playerB.playWhenReady = true
        playerB.play()

        Timber.tag("TransitionDebug").d("Player B started for overlap. Playing=%s state=%d", playerB.isPlaying, playerB.playbackState)

        // Ensure Player B is actually outputting audio before we begin the fade
        var playChecks = 0
        while (!playerB.isPlaying && playChecks < 80) {
            Timber.tag("TransitionDebug").v("Waiting for Player B to start rendering audio (state=%d)", playerB.playbackState)
            delay(25)
            playChecks++
        }

        if (!playerB.isPlaying) {
            Timber.tag("TransitionDebug").e("Player B failed to start in time. Aborting crossfade.")
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
            return
        }

        // Small warmup to guarantee audible overlap (some devices output a few ms late)
        delay(75)

        val duration = settings.durationMs.toLong()
        val stepMs = 16L
        var elapsed = 0L
        var lastLog = 0L

        while (elapsed <= duration) {
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val volA = 1f - envelope(progress, settings.curveOut)
            val volB = envelope(progress, settings.curveIn)

            playerA.volume = volA
            playerB.volume = volB

            if (elapsed - lastLog >= 500) {
                Timber.tag("TransitionDebug").v("Loop: Progress=%.2f, VolA=%.2f, VolB=%.2f", progress, volA, volB)
                lastLog = elapsed
            }

            if (!playerA.isPlaying && playerA.playbackState !in listOf(Player.STATE_READY, Player.STATE_BUFFERING, Player.STATE_ENDED)) {
                Timber.tag("TransitionDebug").w("Player A stopped unexpectedly (state=%d) during transition", playerA.playbackState)
                break
            }

            delay(stepMs)
            elapsed += stepMs
        }

        Timber.tag("TransitionDebug").d("Overlap loop finished. Swapping.")
        playerA.volume = 0f
        playerB.volume = 1f

        finalizeTransition()
    }

    private suspend fun finalizeTransition() {
         // 2. Handover to Player A keeping the queue intact
        if (playerA.hasNextMediaItem()) {
            val handoffPosition = playerB.currentPosition
            Timber.tag("TransitionDebug").d("Handoff: Seek A to next item at %d ms", handoffPosition)

            // Unpause the auto-pause lock
            setPauseAtEndOfMediaItems(false)

            playerA.pause() // Should be redundant if it auto-paused, but safe
            playerA.seekToNextMediaItem()

            // Critical: If we just seek, ExoPlayer might take a moment to buffer.
            // But since it's the same file (usually cached), it should be fast.

            playerA.seekTo(handoffPosition)
            playerA.volume = 1f
            playerA.play()

            // Keep B alive until A actually starts rendering audio to avoid gaps
            var safetyChecks = 0
            while (playerA.playbackState == Player.STATE_BUFFERING && safetyChecks < 40) {
                Timber.tag("TransitionDebug").v("Waiting for Player A to start after handoff (state=%d)", playerA.playbackState)
                delay(25)
                safetyChecks++
            }
            safetyChecks = 0
            while (!playerA.isPlaying && playerA.playbackState != Player.STATE_ENDED && safetyChecks < 80) {
                Timber.tag("TransitionDebug").v("Player A not playing yet after handoff (state=%d)", playerA.playbackState)
                delay(25)
                safetyChecks++
            }

            Timber.tag("TransitionDebug").d("Player A resumed on next track. State=%d, playing=%s", playerA.playbackState, playerA.isPlaying)
        } else {
             Timber.tag("TransitionDebug").w("Player A has no next item?")
             playerA.volume = 1f // restore just in case
             setPauseAtEndOfMediaItems(false)
        }

        // 3. Clean up Player B
        playerB.pause()
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
