package com.theveloper.pixelplay.data.service.player

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
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

    private val onPlayerSwappedListeners = mutableListOf<(Player) -> Unit>()

    // Audio Focus Management
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isFocusLossPause = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS. Pausing.")
                isFocusLossPause = false
                playerA.playWhenReady = false
                playerB.playWhenReady = false
                abandonAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Timber.tag("TransitionDebug").d("AudioFocus LOSS_TRANSIENT. Pausing.")
                isFocusLossPause = true
                playerA.playWhenReady = false
                playerB.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Timber.tag("TransitionDebug").d("AudioFocus GAIN. Resuming if paused by loss.")
                if (isFocusLossPause) {
                    isFocusLossPause = false
                    playerA.playWhenReady = true
                    if (transitionRunning) playerB.playWhenReady = true
                }
            }
        }
    }

    // Listener to attach to the active master player (playerA)
    private val masterPlayerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                requestAudioFocus()
            } else {
                if (!isFocusLossPause) {
                    abandonAudioFocus()
                }
            }
        }
    }

    fun addPlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.add(listener)
    }

    fun removePlayerSwapListener(listener: (Player) -> Unit) {
        onPlayerSwappedListeners.remove(listener)
    }

    /** The master player instance that should be connected to the MediaSession. */
    val masterPlayer: Player
        get() = playerA

    fun isTransitionRunning(): Boolean = transitionRunning

    init {
        // We initialize BOTH players with USAGE_GAME and NO internal focus handling.
        // This prevents the system from pausing the active player when the auxiliary one starts
        // (the "System Kill" issue on Oppo/OnePlus).
        // We manage Audio Focus manually via AudioFocusManager.
        playerA = buildPlayer(handleAudioFocus = false, useGameUsage = true)
        playerB = buildPlayer(handleAudioFocus = false, useGameUsage = true)

        // Attach listener to initial master
        playerA.addListener(masterPlayerListener)
    }

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return // Already have or requested

        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusChangeListener)
            .build()

        val result = audioManager.requestAudioFocus(request)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        } else {
            Timber.tag("TransitionDebug").w("AudioFocus Request Failed: $result")
            playerA.playWhenReady = false
        }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    private fun buildPlayer(handleAudioFocus: Boolean, useGameUsage: Boolean): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val usage = if (useGameUsage) C.USAGE_GAME else C.USAGE_MEDIA

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(usage)
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

        // 1. Capture History and Future from Old A (now becoming B)
        val currentAIndex = playerA.currentMediaItemIndex

        // History: All songs up to and including the current one (Old Song)
        val historyToTransfer = mutableListOf<MediaItem>()
        for (i in 0..currentAIndex) {
            historyToTransfer.add(playerA.getMediaItemAt(i))
        }

        // Future: Songs AFTER the Next Song
        // We start from currentAIndex + 2 because:
        // currentAIndex is the Old Song (currently fading out on Old A).
        // currentAIndex + 1 is the Next Song (currently playing on New A).
        // We only want the songs AFTER the Next Song.
        val futureToTransfer = mutableListOf<MediaItem>()
        if (currentAIndex < playerA.mediaItemCount - 2) {
            for (i in (currentAIndex + 2) until playerA.mediaItemCount) {
                futureToTransfer.add(playerA.getMediaItemAt(i))
            }
        }

        // 2. Perform Swap
        val oldPlayer = playerA
        val newPlayer = playerB

        // Move manual focus management to the new master player
        oldPlayer.removeListener(masterPlayerListener)

        playerA = newPlayer
        playerB = oldPlayer

        playerA.addListener(masterPlayerListener)
        // Ensure we hold focus for the new master
        if (playerA.playWhenReady) {
             requestAudioFocus()
        }

        // 3. Transfer History to New A (Prepend)
        // New A currently has [NextSong] at index 0.
        // We want [History..., NextSong, Future...]
        if (historyToTransfer.isNotEmpty()) {
             // Inserting at 0 shifts existing items (NextSong) to the right.
             // ExoPlayer automatically updates the current item index so playback continues uninterrupted on NextSong.
             playerA.addMediaItems(0, historyToTransfer)
             Timber.tag("TransitionDebug").d("Transferred %d history items to new player.", historyToTransfer.size)
        }

        // 4. Transfer Future to New A (Append)
        if (futureToTransfer.isNotEmpty()) {
             playerA.addMediaItems(futureToTransfer)
             Timber.tag("TransitionDebug").d("Transferred %d future items to new player.", futureToTransfer.size)
        }

        // 4. Notify Service to update MediaSession
        onPlayerSwappedListeners.forEach { it(playerA) }
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

            if (elapsed - lastLog >= 250) {
                Timber.tag("TransitionDebug").v("Loop: Progress=%.2f, VolNew=%.2f (Act: %.2f), VolOld=%.2f (Act: %.2f)",
                    progress, volIn, playerA.volume, volOut, playerB.volume)
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
