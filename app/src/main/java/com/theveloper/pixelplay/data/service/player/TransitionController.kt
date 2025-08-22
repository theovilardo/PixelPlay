package com.theveloper.pixelplay.data.service.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.TransitionMode
import com.theveloper.pixelplay.data.repository.TransitionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
        }.also {
            engine.masterPlayer.addListener(it)
        }
    }

    private fun scheduleTransitionFor(currentMediaItem: MediaItem) {
        transitionSchedulerJob?.cancel()
        transitionSchedulerJob = scope.launch {
            val player = engine.masterPlayer
            val nextIndex = player.currentMediaItemIndex + 1

            // Ensure there is a next track to transition to.
            if (nextIndex >= player.mediaItemCount) {
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

            val settings = transitionRepository.resolveTransitionSettings(playlistId, fromTrackId, toTrackId).first()

            // If transition is disabled or has no duration, do nothing.
            if (settings.mode == TransitionMode.NONE || settings.durationMs <= 0) {
                return@launch
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
                return@launch
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
