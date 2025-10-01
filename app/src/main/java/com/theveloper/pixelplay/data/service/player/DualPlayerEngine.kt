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
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

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
        playerB.stop()
        playerB.clearMediaItems()
        playerB.setMediaItem(mediaItem)
        playerB.prepare()
    }

    /**
     * If a track was pre-buffered in Player B, this cancels it.
     */
    fun cancelNext() {
        if (playerB.mediaItemCount > 0) {
            playerB.stop()
            playerB.clearMediaItems()
        }
    }

    /**
     * Executes a transition based on the provided settings.
     */
    fun performTransition(settings: TransitionSettings) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            when (settings.mode) {
                com.theveloper.pixelplay.data.model.TransitionMode.FADE_IN_OUT -> performFadeInOutTransition(settings)
                com.theveloper.pixelplay.data.model.TransitionMode.OVERLAP, com.theveloper.pixelplay.data.model.TransitionMode.SMOOTH -> performOverlapTransition(settings)
                com.theveloper.pixelplay.data.model.TransitionMode.NONE -> {
                    // No transition logic needed, the default player behavior should suffice.
                }
            }
        }
    }

    private suspend fun performFadeInOutTransition(settings: TransitionSettings) {
        if (playerB.mediaItemCount == 0) return
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
        playerA.stop()

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

        // 3. Handover to Player A.
        // Player A is the master player and its timeline is managed by the MediaController.
        // We just need to tell it to move to the next item and sync its state with Player B.
        if (playerA.hasNextMediaItem()) {
            playerA.seekToNextMediaItem()
            playerA.seekTo(playerB.currentPosition)
            playerA.volume = 1f
            playerA.play()
        }

        // 4. Clean up Player B
        playerB.stop()
        playerB.clearMediaItems()
    }

    private suspend fun performOverlapTransition(settings: TransitionSettings) {
        if (playerB.mediaItemCount == 0) return

        // 1. Start Player B and ramp volumes
        playerB.volume = 0f
        playerB.play()

        val duration = settings.durationMs.toLong()
        var elapsed = 0L
        while (elapsed < duration) {
            val progress = elapsed.toFloat() / duration
            playerA.volume = 1f - envelope(progress, settings.curveOut)
            playerB.volume = envelope(progress, settings.curveIn)
            delay(50L)
            elapsed += 50L
        }
        playerA.volume = 0f
        playerB.volume = 1f

        // 2. Stop Player A after fade-out is complete
        playerA.stop()

        // 3. Handover to Player A.
        if (playerA.hasNextMediaItem()) {
            playerA.seekToNextMediaItem()
            playerA.seekTo(playerB.currentPosition)
            playerA.volume = 1f
            playerA.play()
        }

        // 4. Clean up Player B
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
