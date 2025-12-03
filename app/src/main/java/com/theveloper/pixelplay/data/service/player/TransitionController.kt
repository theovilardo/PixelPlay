package com.theveloper.pixelplay.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.TransitionMode
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.TransitionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates song transitions by observing the player state and
 * commanding the DualPlayerEngine.
 */
@OptIn(UnstableApi::class)
@Singleton
class TransitionController @Inject constructor(
    private val engine: DualPlayerEngine,
    private val transitionRepository: TransitionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionListener: Player.Listener? = null
    private var transitionSchedulerJob: Job? = null
    private var currentObservedPlayer: Player? = null

    private val swapListener: (Player) -> Unit = { newPlayer ->
        Timber.tag("TransitionDebug").d("Controller detected player swap. Moving listener.")
        transitionListener?.let { listener ->
            currentObservedPlayer?.removeListener(listener)
            currentObservedPlayer = newPlayer
            newPlayer.addListener(listener)

            // Trigger check for the new player immediately
            if (newPlayer.isPlaying) {
                newPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
            }
        }
    }

    /**
     * Attaches the controller to the player engine to start listening for state changes.
     */
    fun initialize() {
        if (transitionListener != null) return // Already initialized

        Timber.tag("TransitionDebug").d("Initializing TransitionController...")

        transitionListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Timber.tag("TransitionDebug").d("onMediaItemTransition: %s (reason=%d)", mediaItem?.mediaId, reason)
                // When we naturally move to a new song, ensure pauseAtEnd is OFF by default.
                engine.setPauseAtEndOfMediaItems(false)

                if (mediaItem != null) {
                    scheduleTransitionFor(mediaItem)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val job = transitionSchedulerJob
                if (isPlaying && (job == null || job.isCompleted)) {
                    // If playback resumes and no transition is scheduled, schedule one.
                    Timber.tag("TransitionDebug").d("Playback resumed. Checking if transition needs scheduling.")
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    // The queue has changed (e.g., reordered, item removed).
                    Timber.tag("TransitionDebug").d("Timeline changed (reason=%d). Cancelling pending transition.", reason)
                    transitionSchedulerJob?.cancel()
                    engine.cancelNext()

                    // Try to reschedule for the current item
                     engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }
        }

        // Initial setup
        currentObservedPlayer = engine.masterPlayer
        currentObservedPlayer?.addListener(transitionListener!!)
        engine.addPlayerSwapListener(swapListener)
    }

    private fun scheduleTransitionFor(currentMediaItem: MediaItem) {
        // Cancel any existing job first
        transitionSchedulerJob?.cancel()

        transitionSchedulerJob = scope.launch {
            val player = engine.masterPlayer
            val nextIndex = player.currentMediaItemIndex + 1

            // If there is no next track, cancel any pending transition and stop.
            if (nextIndex >= player.mediaItemCount) {
                Timber.tag("TransitionDebug").d("No next track (index=%d, count=%d). No transition.", nextIndex, player.mediaItemCount)
                engine.cancelNext()
                return@launch
            }

            val nextMediaItem = player.getMediaItemAt(nextIndex)
            Timber.tag("TransitionDebug").d("Preparing next track: %s (Index: %d)", nextMediaItem.mediaId, nextIndex)
            engine.prepareNext(nextMediaItem)

            val playlistId = currentMediaItem.mediaMetadata.extras?.getString("playlistId")
            val fromTrackId = currentMediaItem.mediaId
            val toTrackId = nextMediaItem.mediaId

            Timber.tag("TransitionDebug").d("Resolving settings for playlistId=%s, %s -> %s", playlistId, fromTrackId, toTrackId)

            // Check global crossfade toggle first
            val isCrossfadeEnabledFlow = userPreferencesRepository.isCrossfadeEnabledFlow

            // Use collectLatest to automatically cancel and restart the logic if settings change.
            val settingsFlow = if (playlistId != null) {
                transitionRepository.resolveTransitionSettings(playlistId, fromTrackId, toTrackId)
            } else {
                Timber.tag("TransitionDebug").d("Missing playlistId. Using global settings.")
                transitionRepository.getGlobalSettings()
            }

            kotlinx.coroutines.flow.combine(settingsFlow, isCrossfadeEnabledFlow) { settings, isEnabled ->
                Pair(settings, isEnabled)
            }.distinctUntilChanged() // Crucial: prevents restarting the job if the same settings are emitted again
            .collectLatest { (settings, isEnabled) ->

                Timber.tag("TransitionDebug").d("Settings resolved: Mode=%s, Duration=%dms, GlobalEnabled=%s", settings.mode, settings.durationMs, isEnabled)

                // If globally disabled, use no transition (or maybe we just let ExoPlayer handle it naturally)
                if (!isEnabled) {
                    Timber.tag("TransitionDebug").d("Crossfade globally disabled. Using default gap.")
                    engine.setPauseAtEndOfMediaItems(false)
                    // If we wanted to enforce a specific gap (e.g. 1ms), we would handle it here,
                    // but disabling pauseAtEnd allows ExoPlayer to proceed naturally which usually has a tiny gap.
                    return@collectLatest
                }

                // If transition is disabled or has no duration, do nothing.
                if (settings.mode == TransitionMode.NONE || settings.durationMs <= 0) {
                    Timber.tag("TransitionDebug").d("Transition disabled or zero duration.")
                    engine.setPauseAtEndOfMediaItems(false)
                    return@collectLatest
                }

                val effectiveSettings = settings

                // Wait for the player to report a valid duration.
                var duration = player.duration
                while ((duration == C.TIME_UNSET || duration <= 0) && isActive) {
                    delay(500)
                    duration = player.duration
                    Timber.tag("TransitionDebug").v("Waiting for duration... (%d)", duration)
                }

                if (!isActive) return@collectLatest

                // Calculate transition point

                // "Fade In Next" Logic:
                // We want the current song (A) to finish completely (or almost).
                // Then the next song (B) starts at Volume 0 and fades in.
                // We trigger the transition just before the end of A to ensure gapless start.
                val triggerBuffer = 500 // Trigger 500ms before end
                val transitionPoint = (duration - triggerBuffer).coerceAtLeast(0)

                // We pass the full user-configured duration to the engine for the Fade In ramp.
                val effectiveDuration = effectiveSettings.durationMs.coerceAtLeast(500)

                Timber.tag("TransitionDebug").d(
                    "Scheduled %s at %d ms (SongDur: %d). Next song fade-in: %d ms",
                    effectiveSettings.mode, transitionPoint, duration, effectiveDuration
                )

                // --- CRITICAL FIX: Enable Pause At End ---
                // We want to control the transition manually, so we prevent auto-advance.
                engine.setPauseAtEndOfMediaItems(true)
                Timber.tag("TransitionDebug").d("Enabled pauseAtEndOfMediaItems to prevent auto-skip.")

                if (transitionPoint <= player.currentPosition) {
                     val remaining = duration - player.currentPosition
                     // If we are slightly past the point but still have enough time to start the next player, fire it.
                     // We use a lower threshold (100ms) because we just want to start B before A dies.
                     if (remaining > 100) {
                         Timber.tag("TransitionDebug").w("Already past transition point! Triggering immediately.")
                         // We use full effectiveDuration for the fade-in of the next song, regardless of how little time is left for the current song.
                         engine.performTransition(effectiveSettings)
                     } else {
                         Timber.tag("TransitionDebug").w("Too close to end (%d ms left). Skipping to avoid glitch.", remaining)
                         engine.setPauseAtEndOfMediaItems(false)
                     }
                    return@collectLatest
                }

                // Wait loop with adaptive sleep
                while (player.currentPosition < transitionPoint && isActive) {
                    val remaining = transitionPoint - player.currentPosition
                    val sleep = when {
                        remaining > 5000 -> 1000L
                        remaining > 1000 -> 250L
                        else -> 50L // Tight loop near the end
                    }
                    if (remaining < 2000 && remaining % 500 < 50) {
                        Timber.tag("TransitionDebug").v("Countdown: %d ms to transition", remaining)
                    }
                    delay(sleep)
                }

                // Final check to ensure the job wasn't cancelled while waiting.
                if (isActive) {
                    Timber.tag("TransitionDebug").d("FIRING TRANSITION NOW!")
                    engine.performTransition(effectiveSettings.copy(durationMs = effectiveDuration))
                } else {
                    Timber.tag("TransitionDebug").d("Job cancelled before firing.")
                    engine.setPauseAtEndOfMediaItems(false)
                }
            }
        }
    }

    /**
     * Cleans up resources and listeners.
     */
    fun release() {
        Timber.tag("TransitionDebug").d("Releasing controller.")
        transitionSchedulerJob?.cancel()
        engine.removePlayerSwapListener(swapListener)
        transitionListener?.let { currentObservedPlayer?.removeListener(it) }
        transitionListener = null
        currentObservedPlayer = null
        scope.cancel()
    }
}
