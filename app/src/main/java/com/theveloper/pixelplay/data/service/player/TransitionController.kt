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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

        transitionListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    scheduleTransitionFor(mediaItem)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val job = transitionSchedulerJob
                if (isPlaying && (job == null || job.isCompleted)) {
                    // If playback resumes and no transition is scheduled, schedule one.
                    engine.masterPlayer.currentMediaItem?.let { scheduleTransitionFor(it) }
                } else if (!isPlaying) {
                    // If playback is paused, cancel any pending transition.
                    job?.cancel()
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    // The queue has changed (e.g., reordered, item removed).
                    // We cancel any pending transition to let the player simply move to the
                    // next track in the new order. The transition will be rescheduled in
                    // onMediaItemTransition.
                    transitionSchedulerJob?.cancel()
                    engine.cancelNext()
                }
            }
        }.also {
            engine.masterPlayer.addListener(it)
        }
    }

    private fun scheduleTransitionFor(currentMediaItem: MediaItem) {
        transitionSchedulerJob?.cancel()
        transitionSchedulerJob = scope.launch {
            val player = engine.masterPlayer
            val nextIndex = player.currentMediaItemIndex + 1

            // If there is no next track, cancel any pending transition and stop.
            if (nextIndex >= player.mediaItemCount) {
                engine.cancelNext()
                return@launch
            }

            val nextMediaItem = player.getMediaItemAt(nextIndex)
            engine.prepareNext(nextMediaItem)

            // Resolve transition settings. Assumes playlistId is stored in media metadata extras.
            val playlistId = currentMediaItem.mediaMetadata.extras?.getString("playlistId")
            val fromTrackId = currentMediaItem.mediaId
            val toTrackId = nextMediaItem.mediaId

            // If no playlist context, we can't resolve rules.
            if (playlistId == null) return@launch

            // Use collectLatest to automatically cancel and restart the logic if settings change.
            transitionRepository.resolveTransitionSettings(playlistId, fromTrackId, toTrackId).collectLatest { settings ->
                // If transition is disabled or has no duration, do nothing.
                if (settings.mode == TransitionMode.NONE || settings.durationMs <= 0) {
                    return@collectLatest
                }

                // Wait for the player to report a valid duration.
                var duration = player.duration
                while (duration == C.TIME_UNSET && isActive) {
                    delay(100)
                    duration = player.duration
                }

                val transitionPoint = duration - settings.durationMs
                if (transitionPoint <= player.currentPosition) {
                    // Already past the transition point, so don't attempt transition.
                    return@collectLatest
                }

                // Wait until the playback position reaches the transition point.
                while (player.currentPosition < transitionPoint && isActive) {
                    delay(250) // Check periodically.
                }

                // Final check to ensure the job wasn't cancelled while waiting.
                if (isActive) {
                    engine.performTransition(settings)
                }
            }
        }
    }

    /**
     * Cleans up resources and listeners.
     */
    fun release() {
        transitionSchedulerJob?.cancel()
        transitionListener?.let { engine.masterPlayer.removeListener(it) }
        transitionListener = null
        scope.cancel()
    }
}
