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

    private var playerA: ExoPlayer
    private var playerB: ExoPlayer

    var onPlayerSwapped: ((Player) -> Unit)? = null

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

        // 1. Start Player B (Next Song) paused with volume=0 then immediately request play so overlap is audible
        // NOTE: playerA is currently playing "Old Song". playerB is "Next Song".
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

        // --- SWAP PLAYERS IMMEDIATELY ---
        // We want the UI to see "Next Song" (which is on playerB) as the current item immediately.
        // So we swap the references. Now 'playerA' will point to the one playing 'Next Song'.
        // 'playerB' will point to the one playing 'Old Song'.

        // 1. Capture the rest of the queue from Old A (now becoming B)
        val currentAIndex = playerA.currentMediaItemIndex
        val queueToTransfer = mutableListOf<MediaItem>()
        // We start from currentAIndex + 2 because:
        // currentAIndex is the Old Song (currently fading out on Old A).
        // currentAIndex + 1 is the Next Song (currently playing on New A).
        // We only want the songs AFTER the Next Song.
        if (currentAIndex < playerA.mediaItemCount - 2) {
            for (i in (currentAIndex + 2) until playerA.mediaItemCount) {
                queueToTransfer.add(playerA.getMediaItemAt(i))
            }
        }

        // 2. Perform Swap
        val oldPlayer = playerA
        val newPlayer = playerB
        playerA = newPlayer
        playerB = oldPlayer

        // 3. Transfer Queue to New A
        if (queueToTransfer.isNotEmpty()) {
             // Note: playerA (new) already has "Next Song" at index 0 (because we prepared it with setMediaItem).
             // We append the rest of the queue.
             playerA.addMediaItems(queueToTransfer)
             // If we wanted to be super precise, we'd verify indexes, but simple append is usually correct for sequential play.
        }

        // 4. Notify Service to update MediaSession
        onPlayerSwapped?.invoke(playerA)
        Timber.tag("TransitionDebug").d("Players swapped. UI should now show next song.")

        // Unpause the auto-pause lock on the OLD player (now B) if it was set, although it doesn't matter much as we control volume
        // Actually, we want B to finish playing so we can leave it alone.

        // Small warmup to guarantee audible overlap
        delay(75)

        val duration = settings.durationMs.toLong()
        val stepMs = 16L
        var elapsed = 0L
        var lastLog = 0L

        while (elapsed <= duration) {
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            // CAREFUL: Logic flipped because references flipped.
            // playerA is NEW (Fading IN). playerB is OLD (Fading OUT).
            val volIn = envelope(progress, settings.curveIn)  // A (New)
            val volOut = 1f - envelope(progress, settings.curveOut) // B (Old)

            playerA.volume = volIn
            playerB.volume = volOut

            if (elapsed - lastLog >= 500) {
                Timber.tag("TransitionDebug").v("Loop: Progress=%.2f, VolNew=%.2f, VolOld=%.2f", progress, volIn, volOut)
                lastLog = elapsed
            }

            // Check if OLD player stopped unexpectedly
            if (!playerB.isPlaying && playerB.playbackState !in listOf(Player.STATE_READY, Player.STATE_BUFFERING, Player.STATE_ENDED)) {
                Timber.tag("TransitionDebug").w("Old Player (B) stopped unexpectedly (state=%d) during transition", playerB.playbackState)
                break
            }

            delay(stepMs)
            elapsed += stepMs
        }

        Timber.tag("TransitionDebug").d("Overlap loop finished.")
        playerB.volume = 0f
        playerA.volume = 1f

        // 5. Clean up Old Player (B)
        playerB.pause()
        playerB.stop()
        playerB.clearMediaItems()
        Timber.tag("TransitionDebug").d("Old Player (B) stopped and cleared.")

        // Ensure New Player (A) is fully active and unrestricted
        setPauseAtEndOfMediaItems(false)
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
