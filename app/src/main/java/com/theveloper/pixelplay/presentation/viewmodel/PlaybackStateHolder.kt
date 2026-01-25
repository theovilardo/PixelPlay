package com.theveloper.pixelplay.presentation.viewmodel

import androidx.media3.session.MediaController
import androidx.media3.common.Player
import androidx.media3.common.MediaMetadata
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.google.android.gms.cast.MediaStatus
import android.os.Bundle
import timber.log.Timber
import com.theveloper.pixelplay.utils.QueueUtils
import com.theveloper.pixelplay.utils.MediaItemBuilder
import kotlinx.collections.immutable.toImmutableList

@Singleton
class PlaybackStateHolder @Inject constructor(
    private val dualPlayerEngine: DualPlayerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val castStateHolder: CastStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val listeningStatsTracker: ListeningStatsTracker
) {
    private var scope: CoroutineScope? = null
    
    // MediaController
    var mediaController: MediaController? = null
        private set

    // Player State
    private val _stablePlayerState = MutableStateFlow(StablePlayerState())
    val stablePlayerState: StateFlow<StablePlayerState> = _stablePlayerState.asStateFlow()

    // Internal State
    private var isSeeking = false

    fun initialize(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
    }

    fun setMediaController(controller: MediaController?) {
        this.mediaController = controller
    }
    
    fun updateStablePlayerState(update: (StablePlayerState) -> StablePlayerState) {
        _stablePlayerState.update(update)
    }
    
    /* -------------------------------------------------------------------------- */
    /*                               Playback Controls                            */
    /* -------------------------------------------------------------------------- */

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
            } else {
                if (remoteMediaClient.mediaQueue != null && remoteMediaClient.mediaQueue.itemCount > 0) {
                    castStateHolder.castPlayer?.play()
                } else {
                    Timber.w("Remote queue empty, cannot resume.")
                }
            }
        } else {
            val controller = mediaController ?: return
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(position: Long) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.setRemotelySeeking(true)
            castStateHolder.setRemotePosition(position)
            castStateHolder.castPlayer?.seek(position)
        } else {
            mediaController?.seekTo(position)
            _stablePlayerState.update { it.copy(currentPosition = position) }
        }
    }

    fun previousSong() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.castPlayer?.previous()
        } else {
            val controller = mediaController ?: return
             if (controller.currentPosition > 10000) { // 10 seconds
                 controller.seekTo(0)
            } else {
                 controller.seekToPrevious()
            }
        }
    }

    fun nextSong() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.castPlayer?.next()
        } else {
             mediaController?.seekToNext()
        }
    }

    fun cycleRepeatMode() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            val currentRepeatMode = remoteMediaClient.mediaStatus?.getQueueRepeatMode() ?: MediaStatus.REPEAT_MODE_REPEAT_OFF
            val newMode = when (currentRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_OFF -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                MediaStatus.REPEAT_MODE_REPEAT_ALL -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castStateHolder.castPlayer?.setRepeatMode(newMode)
            
            // Map remote mode back to local constant for persistence/UI
            val mappedLocalMode = when (newMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            scope?.launch { userPreferencesRepository.setRepeatMode(mappedLocalMode) }
            _stablePlayerState.update { it.copy(repeatMode = mappedLocalMode) }
        } else {
            val currentMode = _stablePlayerState.value.repeatMode
            val newMode = when (currentMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            mediaController?.repeatMode = newMode
            scope?.launch { userPreferencesRepository.setRepeatMode(newMode) }
            _stablePlayerState.update { it.copy(repeatMode = newMode) }
        }
    }

    fun setRepeatMode(mode: Int) {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            val remoteMode = when (mode) {
                Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castStateHolder.castPlayer?.setRepeatMode(remoteMode)
        } else {
             mediaController?.repeatMode = mode
        }
        
        scope?.launch { userPreferencesRepository.setRepeatMode(mode) }
        _stablePlayerState.update { it.copy(repeatMode = mode) }
    }

    /* -------------------------------------------------------------------------- */
    /*                               Progress Updates                             */
    /* -------------------------------------------------------------------------- */
    
    private var progressJob: kotlinx.coroutines.Job? = null

    fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope?.launch {
            while (true) {
                val castSession = castStateHolder.castSession.value
                val isRemote = castSession?.remoteMediaClient != null
                
                if (isRemote) {
                    val remoteClient = castSession?.remoteMediaClient
                    if (remoteClient != null && remoteClient.isPlaying) {
                        val currentPosition = remoteClient.approximateStreamPosition
                        val duration = remoteClient.streamDuration
                        _stablePlayerState.update {
                             it.copy(currentPosition = currentPosition, totalDuration = duration)
                        }
                    }
                } else {
                     val controller = mediaController
                     // Media3: Check isPlaying or playbackState == READY/BUFFERING
                     if (controller != null && controller.isPlaying && !isSeeking) {
                         val currentPosition = controller.currentPosition
                         val duration = controller.duration
                         
                         listeningStatsTracker.onProgress(currentPosition, true)
                         
                         _stablePlayerState.update {
                             it.copy(currentPosition = currentPosition, totalDuration = duration)
                        }
                     }
                }
                delay(500) // 500ms ticker
            }
        }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    /* -------------------------------------------------------------------------- */
    /*                               Shuffle & Repeat                             */
    /* -------------------------------------------------------------------------- */

    fun toggleShuffle(
        currentSongs: List<Song>,
        currentSong: Song?,
        currentQueueSourceName: String,
        updateQueueCallback: (List<Song>) -> Unit
    ) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient
            val newRepeatMode = if (remoteMediaClient?.mediaStatus?.getQueueRepeatMode() == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE) {
                MediaStatus.REPEAT_MODE_REPEAT_ALL
            } else {
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
            }
            castStateHolder.castPlayer?.setRepeatMode(newRepeatMode)
        } else {
            scope?.launch {
                val player = mediaController ?: return@launch
                if (currentSongs.isEmpty()) return@launch

                val isCurrentlyShuffled = _stablePlayerState.value.isShuffleEnabled

                if (!isCurrentlyShuffled) {
                    // Enable Shuffle
                    if (!queueStateHolder.hasOriginalQueue()) {
                        queueStateHolder.setOriginalQueueOrder(currentSongs)
                        queueStateHolder.saveOriginalQueueState(currentSongs, currentQueueSourceName)
                    }

                    val currentIndex = player.currentMediaItemIndex.coerceIn(0, (currentSongs.size - 1).coerceAtLeast(0))
                    val currentPosition = player.currentPosition
                    val currentMediaId = player.currentMediaItem?.mediaId

                    val shuffledQueue = QueueUtils.buildAnchoredShuffleQueue(currentSongs, currentIndex)

                    val targetIndex = shuffledQueue.indexOfFirst { it.id == currentMediaId }
                        .takeIf { it != -1 } ?: currentIndex

                    // Prepare player for seamless transition - maintains playback state
                    val wasPlaying = player.isPlaying
                    
                    dualPlayerEngine.masterPlayer.setMediaItems(
                         shuffledQueue.map { MediaItemBuilder.build(it) },
                         targetIndex,
                         currentPosition
                    )
                    
                    // Resume if was playing - player should continue seamlessly
                    if (wasPlaying && !player.isPlaying) {
                        player.play()
                    }
                    
                    updateQueueCallback(shuffledQueue)
                    _stablePlayerState.update { it.copy(isShuffleEnabled = true) }
                    
                    scope?.launch {
                        if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                            userPreferencesRepository.setShuffleOn(true)
                        }
                    }
                } else {
                    // Disable Shuffle
                   scope?.launch {
                        if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                            userPreferencesRepository.setShuffleOn(false)
                        }
                    }

                    if (!queueStateHolder.hasOriginalQueue()) {
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        return@launch
                    }

                    val originalQueue = queueStateHolder.originalQueueOrder
                    val currentPosition = player.currentPosition
                    
                    // Find where the current song is in the original queue
                    val currentSongId = currentSong?.id
                    val originalIndex = originalQueue.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }

                    if (originalIndex == null) {
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        return@launch
                    }

                    // Preserve playback state during queue rebuild
                    val wasPlaying = player.isPlaying

                     dualPlayerEngine.masterPlayer.setMediaItems(
                         originalQueue.map { MediaItemBuilder.build(it) },
                         originalIndex,
                         currentPosition
                    )
                    
                    // Resume if was playing
                    if (wasPlaying && !player.isPlaying) {
                        player.play()
                    }

                    updateQueueCallback(originalQueue)
                    _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                }
            }
        }
    }

}
