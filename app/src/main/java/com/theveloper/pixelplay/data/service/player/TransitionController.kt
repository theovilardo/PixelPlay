package com.theveloper.pixelplay.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.TransitionMode
import com.theveloper.pixelplay.data.repository.TransitionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
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
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var transitionListener: Player.Listener? = null
    private var transitionSchedulerJob: Job? = null

    /**
     * Attaches the controller to the player engine to start listening for state changes.
     */
    fun initialize() {
        if (transitionListener != null) return // Already initialized

        Timber.d("[Transitions] Initializing TransitionController...")

        transitionListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Timber.d("[Transitions] onMediaItemTransition: %s (reason=%d)", mediaItem?.mediaId, reason)
                if (mediaItem != null) {
                    scheduleTransitionFor(mediaItem)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val job = transitionSchedulerJob
                if (isPlaying && (job == null || job.isCompleted)) {
                    // If playback resumes and no transition is scheduled, schedule one.
                    Timber.d("[Transitions] Playback resumed. Checking if transition needs scheduling.")
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                } else if (!isPlaying) {
                    // If playback is paused, we keep the job but it will be paused in the loop effectively
                    // actually, we might want to keep it running to monitor position?
                    // The original code cancelled it. Let's keep it running but it will wait.
                    // If we cancel, we lose the transition state.
                    // Timber.d("[Transitions] Playback paused. Keeping scheduler alive.")
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    // The queue has changed (e.g., reordered, item removed).
                    Timber.d("[Transitions] Timeline changed (reason=%d). Cancelling pending transition.", reason)
                    transitionSchedulerJob?.cancel()
                    engine.cancelNext()

                    // Try to reschedule for the current item
                     engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                }
            }
        }.also {
            engine.masterPlayer.addListener(it)
        }
    }

    private fun scheduleTransitionFor(currentMediaItem: MediaItem) {
        // Cancel any existing job first
        transitionSchedulerJob?.cancel()

        transitionSchedulerJob = scope.launch {
            val player = engine.masterPlayer
            val nextIndex = player.currentMediaItemIndex + 1

            // If there is no next track, cancel any pending transition and stop.
            if (nextIndex >= player.mediaItemCount) {
                Timber.d("[Transitions] No next track (index=%d, count=%d). No transition.", nextIndex, player.mediaItemCount)
                engine.cancelNext()
                return@launch
            }

            val nextMediaItem = player.getMediaItemAt(nextIndex)
            Timber.d("[Transitions] Preparing next track: %s (Index: %d)", nextMediaItem.mediaId, nextIndex)
            engine.prepareNext(nextMediaItem)

            val playlistId = currentMediaItem.mediaMetadata.extras?.getString("playlistId")
            val fromTrackId = currentMediaItem.mediaId
            val toTrackId = nextMediaItem.mediaId

            Timber.d("[Transitions] Resolving settings for playlistId=%s, %s -> %s", playlistId, fromTrackId, toTrackId)

            // Use collectLatest to automatically cancel and restart the logic if settings change.
            val settingsFlow = if (playlistId != null) {
                transitionRepository.resolveTransitionSettings(playlistId, fromTrackId, toTrackId)
            } else {
                Timber.d("[Transitions] Missing playlistId. Using global settings.")
                transitionRepository.getGlobalSettings()
            }

            settingsFlow
                .distinctUntilChanged() // Crucial: prevents restarting the job if the same settings are emitted again
                .collectLatest { settings ->

                Timber.d("[Transitions] Settings resolved: Mode=%s, Duration=%dms", settings.mode, settings.durationMs)

                // If transition is disabled or has no duration, do nothing.
                if (settings.mode == TransitionMode.NONE || settings.durationMs <= 0) {
                    Timber.d("[Transitions] Transition disabled or zero duration.")
                    return@collectLatest
                }

                // Wait for the player to report a valid duration.
                var duration = player.duration
                while ((duration == C.TIME_UNSET || duration <= 0) && isActive) {
                    delay(500)
                    duration = player.duration
                    Timber.v("[Transitions] Waiting for duration... (%d)", duration)
                }

                if (!isActive) return@collectLatest

                // Calculate transition point
                // Ensure effective duration isn't longer than the song itself
                val effectiveDuration = settings.durationMs.coerceAtMost(duration.toInt()).coerceAtLeast(500)
                val transitionPoint = duration - effectiveDuration

                Timber.d(
                    "[Transitions] Scheduled %s at %d ms (Duration: %d, Effective: %d)",
                    settings.mode, transitionPoint, duration, effectiveDuration
                )

                if (transitionPoint <= player.currentPosition) {
                     // If we are already past the point, trigger immediately if it's reasonable
                     // But if we are TOO close to the end (e.g. < 500ms), we might just skip it to avoid glitch
                     val remaining = duration - player.currentPosition
                     if (remaining > 500) {
                         Timber.w("[Transitions] Already past transition point! Triggering immediately.")
                         engine.performTransition(settings.copy(durationMs = remaining.toInt()))
                     } else {
                         Timber.w("[Transitions] Too close to end (%d ms left). Skipping to avoid glitch.", remaining)
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
                    delay(sleep)
                }

                // Final check to ensure the job wasn't cancelled while waiting.
                if (isActive) {
                    Timber.d("[Transitions] FIRING TRANSITION NOW!")
                    engine.performTransition(settings.copy(durationMs = effectiveDuration))
                } else {
                    Timber.d("[Transitions] Job cancelled before firing.")
                }
            }
        }
    }

    /**
     * Cleans up resources and listeners.
     */
    fun release() {
        Timber.d("[Transitions] Releasing controller.")
        transitionSchedulerJob?.cancel()
        transitionListener?.let { engine.masterPlayer.removeListener(it) }
        transitionListener = null
        scope.cancel()
    }
}
