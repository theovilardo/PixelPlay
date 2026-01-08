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
import kotlinx.coroutines.flow.asStateFlow // Added
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
    
    // Active Audio Session ID Flow
    private val _activeAudioSessionId = kotlinx.coroutines.flow.MutableStateFlow(0)
    val activeAudioSessionId: kotlinx.coroutines.flow.StateFlow<Int> = _activeAudioSessionId.asStateFlow()

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

    /**
     * Returns the audio session ID of the master player.
     * Use this to attach audio effects like Equalizer.
     */
    /**
     * Returns the audio session ID of the master player.
     * Use this to attach audio effects like Equalizer.
     */
    fun getAudioSessionId(): Int = playerA.audioSessionId

    init {
        // We initialize BOTH players with NO internal focus handling.
        // We manage Audio Focus manually via AudioFocusManager.
        playerA = buildPlayer(handleAudioFocus = false)
        playerB = buildPlayer(handleAudioFocus = false)

        // Attach listener to initial master
        playerA.addListener(masterPlayerListener)
        
        // Initialize active session ID
        _activeAudioSessionId.value = playerA.audioSessionId
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
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
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
            playerA.volume = 1f
            setPauseAtEndOfMediaItems(false)
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

        // Small warmup to guarantee audible overlap
        delay(75)

        // --- SWAP PLAYERS EARLY (Before Fade) ---
        // This ensures the UI updates to show the "Next Song" immediately when the transition starts.

        // 1. Identify Outgoing (Old A) and Incoming (Old B / New A)
        val outgoingPlayer = playerA
        val incomingPlayer = playerB

        val isSelfTransition = outgoingPlayer.currentMediaItem?.mediaId == incomingPlayer.currentMediaItem?.mediaId

        val currentOutgoingIndex = outgoingPlayer.currentMediaItemIndex

        // History: All songs up to and including the current one (Old Song)
        val historyToTransfer = mutableListOf<MediaItem>()
        val historyEndIndex = if (isSelfTransition) currentOutgoingIndex else currentOutgoingIndex + 1
        for (i in 0 until historyEndIndex) {
            historyToTransfer.add(outgoingPlayer.getMediaItemAt(i))
        }

        // Future: Songs AFTER the Next Song
        // We skip the immediate next one because incomingPlayer already has it.
        val futureToTransfer = mutableListOf<MediaItem>()
        val futureStartIndex = if (isSelfTransition) currentOutgoingIndex + 1 else currentOutgoingIndex + 2
        for (i in futureStartIndex until outgoingPlayer.mediaItemCount) {
            futureToTransfer.add(outgoingPlayer.getMediaItemAt(i))
        }

        // 2. Transfer playback settings (repeat mode, shuffle mode) before swap
        val repeatModeToTransfer = outgoingPlayer.repeatMode
        val shuffleModeToTransfer = outgoingPlayer.shuffleModeEnabled
        incomingPlayer.repeatMode = repeatModeToTransfer
        incomingPlayer.shuffleModeEnabled = shuffleModeToTransfer
        Timber.tag("TransitionDebug").d("Transferred playback settings: repeatMode=%d, shuffle=%s", repeatModeToTransfer, shuffleModeToTransfer)

        // 3. Move manual focus management to the new master player
        outgoingPlayer.removeListener(masterPlayerListener)

        // 4. Swap References
        playerA = incomingPlayer
        playerB = outgoingPlayer
        
        // Critical: Reset pauseAtEndOfMediaItems on both players after swap.
        // The outgoing player (now B) had pauseAtEndOfMediaItems=true set before the transition started.
        // If we don't disable it, the outgoing player will pause itself when it reaches the end,
        // causing the "stops then restarts" glitch during crossfade.
        playerB.pauseAtEndOfMediaItems = false
        playerA.pauseAtEndOfMediaItems = false

        playerA.addListener(masterPlayerListener)
        // Ensure we hold focus for the new master
        if (playerA.playWhenReady) {
             requestAudioFocus()
        }

        // 4. Transfer History to New A (Prepend)
        if (historyToTransfer.isNotEmpty()) {
             playerA.addMediaItems(0, historyToTransfer)
             Timber.tag("TransitionDebug").d("Transferred %d history items to new player.", historyToTransfer.size)
        }

        // 5. Transfer Future to New A (Append)
        if (futureToTransfer.isNotEmpty()) {
             playerA.addMediaItems(futureToTransfer)
             Timber.tag("TransitionDebug").d("Transferred %d future items to new player.", futureToTransfer.size)
        }

        // 6. Notify Service to update MediaSession
        onPlayerSwappedListeners.forEach { it(playerA) }
        
        // Update Session ID for Equalizer
        _activeAudioSessionId.value = playerA.audioSessionId
        
        Timber.tag("TransitionDebug").d("Players swapped EARLY. UI should now show next song.")

        // *** FADE LOOP ***
        // playerA is now the Incoming/New Master.
        // playerB is now the Outgoing/Aux.

        val duration = settings.durationMs.toLong().coerceAtLeast(500L)
        val stepMs = 16L
        var elapsed = 0L
        var lastLog = 0L

        while (elapsed <= duration) {
            val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            val volIn = envelope(progress, settings.curveIn)  // Incoming (Now A)
            val volOut = 1f - envelope(progress, settings.curveOut) // Outgoing (Now B)

            playerA.volume = volIn
            playerB.volume = volOut.coerceIn(0f, 1f)

            if (elapsed - lastLog >= 250) {
                Timber.tag("TransitionDebug").v("Loop: Progress=%.2f, VolNew=%.2f (Act: %.2f), VolOld=%.2f (Act: %.2f)",
                    progress, volIn, playerA.volume, volOut, playerB.volume)
                lastLog = elapsed
            }

            // Break early if either player stops in a non-ready state to avoid stuck fades.
            if (playerA.playbackState == Player.STATE_ENDED || playerB.playbackState == Player.STATE_ENDED) {
                Timber.tag("TransitionDebug").w("One of the players ended during crossfade (A=%d, B=%d)", playerA.playbackState, playerB.playbackState)
                break
            }

            delay(stepMs)
            elapsed += stepMs
        }

        Timber.tag("TransitionDebug").d("Overlap loop finished.")
        playerB.volume = 0f
        playerA.volume = 1f

        // Clean up Old Player (now B)
        playerB.pause()
        playerB.stop()
        playerB.clearMediaItems()

        // Fresh Player Strategy: Release and recreate playerB to avoid OEM "stale session" tracking
        playerB.release()
        playerB = buildPlayer(handleAudioFocus = false)
        Timber.tag("TransitionDebug").d("Old Player (B) released and recreated fresh.")

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
