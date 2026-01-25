package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.http.MediaFileHttpServerService
import com.theveloper.pixelplay.data.service.player.CastPlayer
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine

import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastTransferStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val castStateHolder: CastStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val dualPlayerEngine: DualPlayerEngine, // For local player control during transfer
    private val listeningStatsTracker: ListeningStatsTracker
) {
    private val CAST_LOG_TAG = "PlayerCastTransfer"

    private var scope: CoroutineScope? = null
    
    // Callbacks for interacting with PlayerViewModel
    // Provides current queue from UI state
    private var getCurrentQueue: (() -> List<Song>)? = null
    // Syncs queue updates back to UI
    private var updateQueue: ((List<Song>) -> Unit)? = null
    // Provides master song list for resolution
    private var getMasterAllSongs: (() -> List<Song>)? = null
    // Callback when transfer is finished
    private var onTransferBackComplete: (() -> Unit)? = null
    // Callback to ensure UI sheet is visible
    private var onSheetVisible: (() -> Unit)? = null
    // Callback to handle disconnection/errors
    private var onDisconnect: (() -> Unit)? = null
    // Callback to update color scheme
    private var onSongChanged: ((String?) -> Unit)? = null

    // Session Management
    private val sessionManager: SessionManager by lazy {
        CastContext.getSharedInstance(context).sessionManager
    }
    
    // We retain MediaRouter reference if needed, but managing routes is usually done via callbacks
    // in PlayerViewModel. We'll assume route selection logic remains there or is migrated separately.

    // State tracking variables
    private var lastRemoteMediaStatus: MediaStatus? = null
    var lastRemoteQueue: List<Song> = emptyList()
        private set
    var lastRemoteSongId: String? = null
        private set
    private var lastRemoteStreamPosition: Long = 0L
    private var lastRemoteRepeatMode: Int = MediaStatus.REPEAT_MODE_REPEAT_OFF
    private var lastKnownRemoteIsPlaying: Boolean = false
    private var lastRemoteItemId: Int? = null

    private var pendingRemoteSongId: String? = null
    private var pendingRemoteSongMarkedAt: Long = 0L

    // Listeners
    private var remoteMediaClientCallback: RemoteMediaClient.Callback? = null
    private var remoteProgressListener: RemoteMediaClient.ProgressListener? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var remoteProgressObserverJob: Job? = null

    fun initialize(
        scope: CoroutineScope,
        getCurrentQueue: () -> List<Song>,
        updateQueue: (List<Song>) -> Unit,
        getMasterAllSongs: () -> List<Song>,
        onTransferBackComplete: () -> Unit,
        onSheetVisible: () -> Unit,
        onDisconnect: () -> Unit,
        onSongChanged: (String?) -> Unit
    ) {
        this.scope = scope
        this.getCurrentQueue = getCurrentQueue
        this.updateQueue = updateQueue
        this.getMasterAllSongs = getMasterAllSongs
        this.onTransferBackComplete = onTransferBackComplete
        this.onSheetVisible = onSheetVisible
        this.onDisconnect = onDisconnect
        this.onSongChanged = onSongChanged

        setupListeners()
    }

    private fun setupListeners() {
        remoteProgressListener = RemoteMediaClient.ProgressListener { progress, _ ->
            val isSeeking = castStateHolder.isRemotelySeeking.value
            if (!isSeeking) {
                val pendingId = pendingRemoteSongId
                if (pendingId != null && SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000) {
                    val status = castStateHolder.castSession.value?.remoteMediaClient?.mediaStatus
                    val activeId = status
                        ?.getQueueItemById(status.getCurrentItemId())
                        ?.customData
                        ?.optString("songId")
                    if (activeId == null || activeId != pendingId) {
                         Timber.tag(CAST_LOG_TAG).d("Ignoring remote progress %d while pending target %s", progress, pendingId)
                        return@ProgressListener
                    }
                }
                castStateHolder.setRemotePosition(progress)
                lastRemoteStreamPosition = progress
                listeningStatsTracker.onProgress(progress, lastKnownRemoteIsPlaying)
            }
        }

        remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                handleRemoteStatusUpdate()
            }
        }

        castSessionManagerListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                transferPlayback(session)
            }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                transferPlayback(session)
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                scope?.launch { stopServerAndTransferBack() }
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                scope?.launch { stopServerAndTransferBack() }
            }
            override fun onSessionStarting(session: CastSession) {
                castStateHolder.setCastConnecting(true)
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                castStateHolder.setPendingCastRouteId(null)
                castStateHolder.setCastConnecting(false)
            }
            override fun onSessionEnding(session: CastSession) { }
            override fun onSessionResuming(session: CastSession, sessionId: String) {
                castStateHolder.setCastConnecting(true)
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                castStateHolder.setPendingCastRouteId(null)
                castStateHolder.setCastConnecting(false)
            }
        }
        
        sessionManager.addSessionManagerListener(castSessionManagerListener as SessionManagerListener<CastSession>, CastSession::class.java)
        
        // Sync initial state if session exists
        val currentSession = sessionManager.currentCastSession
        castStateHolder.setCastSession(currentSession)
        castStateHolder.setRemotePlaybackActive(currentSession != null)
        
        if (currentSession != null) {
            currentSession.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
            currentSession.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)
        }
    }

    private fun handleRemoteStatusUpdate() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient ?: return
        val mediaStatus = remoteMediaClient.mediaStatus ?: return

        lastRemoteMediaStatus = mediaStatus
        
        val songMap = getMasterAllSongs?.invoke()?.associateBy { it.id } ?: emptyMap()
        
        val newQueue = mediaStatus.queueItems.mapNotNull { item ->
            item.customData?.optString("songId")?.let { songId -> songMap[songId] }
        }.toImmutableList()

        val currentItemId = mediaStatus.getCurrentItemId()
        val currentRemoteItem = mediaStatus.getQueueItemById(currentItemId)
        val currentSongId = currentRemoteItem?.customData?.optString("songId")
        val streamPosition = mediaStatus.streamPosition

        val pendingId = pendingRemoteSongId
        val pendingIsFresh = pendingId != null &&
            SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000
        if (pendingIsFresh && currentSongId != null && currentSongId != pendingId) {
             remoteMediaClient.requestStatus()
             return
        }

        val itemChanged = lastRemoteItemId != currentItemId
        if (itemChanged) {
             lastRemoteItemId = currentItemId
             if (pendingRemoteSongId != null && pendingRemoteSongId != currentSongId) {
                 pendingRemoteSongId = null
             }
             castStateHolder.setRemotelySeeking(false)
             castStateHolder.setRemotePosition(streamPosition)
             playbackStateHolder.updateStablePlayerState { it.copy(currentPosition = streamPosition) } // UI sync
        }

        if (newQueue.isNotEmpty()) {
             val isShrunkSubset = newQueue.size < lastRemoteQueue.size && newQueue.all { song ->
                 lastRemoteQueue.any { it.id == song.id }
             }
             if (!isShrunkSubset || lastRemoteQueue.isEmpty()) {
                 lastRemoteQueue = newQueue
             }
        }
        
        // Update current song
        val reportedSong = currentSongId?.let { songMap[it] }
        val effectiveSong = resolvePendingRemoteSong(reportedSong, currentSongId, songMap)
        val effectiveSongId = effectiveSong?.id ?: currentSongId ?: lastRemoteSongId
        
        if (effectiveSongId != null) {
            lastRemoteSongId = effectiveSongId
        }
        
        val currentSongFallback = effectiveSong 
            ?: run {
                val pId = pendingRemoteSongId
                val stableSong = playbackStateHolder.stablePlayerState.value.currentSong
                if (pId != null && pId == stableSong?.id && SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000) {
                    stableSong
                } else {
                    playbackStateHolder.stablePlayerState.value.currentSong
                }
            }
            ?: lastRemoteQueue.firstOrNull { it.id == lastRemoteSongId }
            
        if (currentSongFallback?.id != playbackStateHolder.stablePlayerState.value.currentSong?.id) {
             // Pass callback to generate colors or handle it in PlayerViewModel observing stablePlayerState
             onSongChanged?.invoke(currentSongFallback?.albumArtUriString)
        }

        val isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING
        lastKnownRemoteIsPlaying = isPlaying
        lastRemoteStreamPosition = streamPosition
        lastRemoteRepeatMode = mediaStatus.queueRepeatMode

        if (!castStateHolder.isRemotelySeeking.value) {
            castStateHolder.setRemotePosition(streamPosition)
             playbackStateHolder.updateStablePlayerState {
                 it.copy(
                     currentPosition = streamPosition,
                     totalDuration = remoteMediaClient.streamDuration,
                     currentSong = currentSongFallback,
                     isPlaying = isPlaying,
                     repeatMode = if (mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_SINGLE) Player.REPEAT_MODE_ONE
                                  else if (mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL || mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE) Player.REPEAT_MODE_ALL
                                  else Player.REPEAT_MODE_OFF,
                     isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
                 )
             }
        }
        
        // Update Queue if changed
        val previousQueue = getCurrentQueue?.invoke() ?: emptyList()
        val queueForUi = if (newQueue.isNotEmpty()) newQueue else previousQueue
        
        if (newQueue.isNotEmpty() && newQueue != previousQueue) {
             updateQueue?.invoke(newQueue)
        }
        
        if (castSession != null && (newQueue.isNotEmpty() || previousQueue.isNotEmpty())) {
            onSheetVisible?.invoke()
        }
    }
    
    private fun transferPlayback(session: CastSession) {
        scope?.launch {
            castStateHolder.setPendingCastRouteId(null)
            castStateHolder.setCastConnecting(true)
            
            if (!ensureHttpServerRunning()) {
                castStateHolder.setCastConnecting(false)
                onDisconnect?.invoke()
                return@launch
            }
            
            // Ensure no local transition is messing with the player references
            dualPlayerEngine.cancelNext()

            val serverAddress = MediaFileHttpServerService.serverAddress
            val localPlayer = dualPlayerEngine.masterPlayer // Safe now as we are on Main and cancelled transitions

            val currentQueue = getCurrentQueue?.invoke() ?: emptyList()
            
            if (serverAddress == null || currentQueue.isEmpty()) {
                castStateHolder.setCastConnecting(false)
                return@launch
            }

            val wasPlaying = localPlayer.isPlaying
            val currentSongIndex = localPlayer.currentMediaItemIndex
            val currentPosition = localPlayer.currentPosition
            
             val castRepeatMode = if (localPlayer.shuffleModeEnabled) {
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
            } else {
                when (localPlayer.repeatMode) {
                    Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                    Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                    else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                }
            }

            lastRemoteMediaStatus = null
            lastRemoteQueue = currentQueue
            lastRemoteSongId = currentQueue.getOrNull(currentSongIndex)?.id
            lastRemoteStreamPosition = currentPosition
            lastRemoteRepeatMode = castRepeatMode

            onSheetVisible?.invoke()
            localPlayer.pause()
            
            playbackStateHolder.stopProgressUpdates()

            castStateHolder.setCastPlayer(CastPlayer(session))
            castStateHolder.setCastSession(session)
            castStateHolder.setRemotePlaybackActive(false)

            castStateHolder.castPlayer?.loadQueue(
                songs = currentQueue,
                startIndex = currentSongIndex,
                startPosition = currentPosition,
                repeatMode = castRepeatMode,
                serverAddress = serverAddress,
                autoPlay = wasPlaying, // Simplification
                onComplete = { success ->
                    if (!success) {
                       onDisconnect?.invoke()
                       castStateHolder.setCastConnecting(false)
                    }
                    castStateHolder.setRemotePlaybackActive(success)
                    castStateHolder.setCastConnecting(false)
                }
            )

            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
            session.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)
            
            startRemoteProgressObserver()
        }
    }
    
    private fun startRemoteProgressObserver() {
         remoteProgressObserverJob?.cancel()
         remoteProgressObserverJob = scope?.launch {
             castStateHolder.remotePosition.collect { position ->
                 playbackStateHolder.updateStablePlayerState { it.copy(currentPosition = position) }
             }
         }
    }
    
    suspend fun stopServerAndTransferBack() {
        val startMs = SystemClock.elapsedRealtime()
        val session = castStateHolder.castSession.value ?: return
        val remoteMediaClient = session.remoteMediaClient
         
        // Cleanup callbacks
        remoteMediaClient?.removeProgressListener(remoteProgressListener!!)
        remoteMediaClient?.unregisterCallback(remoteMediaClientCallback!!)
        remoteProgressObserverJob?.cancel()
        
        val liveStatus = remoteMediaClient?.mediaStatus
        val lastKnownStatus = liveStatus ?: lastRemoteMediaStatus
        val lastPosition = (liveStatus?.streamPosition ?: lastKnownStatus?.streamPosition ?: lastRemoteStreamPosition)
            .takeIf { it > 0 } ?: castStateHolder.remotePosition.value
            
        val wasPlaying = (liveStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING) 
            || (lastKnownStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING)
            || lastKnownRemoteIsPlaying
            
        val lastItemId = liveStatus?.currentItemId ?: lastKnownStatus?.currentItemId
        val lastRepeatMode = liveStatus?.queueRepeatMode ?: lastKnownStatus?.queueRepeatMode ?: lastRemoteRepeatMode
        val isShuffleEnabled = lastRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
        
        val transferSnapshot = TransferSnapshot(
            lastKnownStatus = lastKnownStatus,
            lastRemoteQueue = lastRemoteQueue,
            lastRemoteSongId = lastRemoteSongId,
            lastRemoteStreamPosition = lastRemoteStreamPosition,
            lastRemoteRepeatMode = lastRemoteRepeatMode,
            wasPlaying = wasPlaying,
            lastPosition = lastPosition,
            isShuffleEnabled = isShuffleEnabled
        )

        castStateHolder.setCastPlayer(null)
        castStateHolder.setCastSession(null)
        castStateHolder.setRemotePlaybackActive(false)
        
        if (castStateHolder.pendingCastRouteId == null) {
            context.stopService(Intent(context, MediaFileHttpServerService::class.java))
            // Signal disconnect to PlayerViewModel if needed, or rely on state holder
            onDisconnect?.invoke() 
        } else {
            castStateHolder.setCastConnecting(true)
        }
        
        // We defer getting the player until AFTER the suspension to avoid race conditions
        // where a transition might have released the reference we held.
        
        val queueData = withContext(Dispatchers.Default) {
            val fallbackQueue = if (transferSnapshot.lastKnownStatus?.queueItems?.isNotEmpty() == true) {
                transferSnapshot.lastKnownStatus.queueItems.mapNotNull { item ->
                    item.customData?.optString("songId")?.let { songId ->
                         getMasterAllSongs?.invoke()?.firstOrNull { it.id == songId }
                    }
                }.toImmutableList()
            } else {
                transferSnapshot.lastRemoteQueue
            }
            val chosenQueue = if (fallbackQueue.isEmpty()) transferSnapshot.lastRemoteQueue else fallbackQueue
             val songMap = getMasterAllSongs?.invoke()?.associateBy { it.id } ?: emptyMap()
            val finalQueue = chosenQueue.mapNotNull { song -> songMap[song.id] }
            
            val targetSongId = transferSnapshot.lastKnownStatus?.getQueueItemById(lastItemId ?: 0)?.customData?.optString("songId")
                ?: transferSnapshot.lastRemoteSongId
                
            QueueTransferData(
                finalQueue = finalQueue,
                targetSongId = targetSongId,
                isShuffleEnabled = transferSnapshot.isShuffleEnabled
            )
        }
        
        if (queueData.finalQueue.isNotEmpty() && queueData.targetSongId != null) {
             val desiredRepeatMode = when (lastRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            
            // Reusing local queue logic simplification: always rebuild for safety/completeness
            val rebuildResult = withContext(Dispatchers.Default) {
                val startIndex = queueData.finalQueue.indexOfFirst { it.id == queueData.targetSongId }.coerceAtLeast(0)
                val mediaItems = queueData.finalQueue.map { song -> MediaItemBuilder.build(song) }
                RebuildArtifacts(startIndex, mediaItems, queueData.finalQueue.getOrNull(startIndex))
            }
            
            // Now retrieve the FRESH local player reference
            val localPlayer = dualPlayerEngine.masterPlayer

            localPlayer.shuffleModeEnabled = queueData.isShuffleEnabled
            localPlayer.repeatMode = desiredRepeatMode
            localPlayer.setMediaItems(rebuildResult.mediaItems, rebuildResult.startIndex, transferSnapshot.lastPosition)
            localPlayer.prepare()
            
            if (wasPlaying) localPlayer.play() else localPlayer.pause()
            
            // Sync UI
            updateQueue?.invoke(queueData.finalQueue)
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = rebuildResult.targetSong,
                    isPlaying = wasPlaying,
                    totalDuration = rebuildResult.targetSong?.duration ?: it.totalDuration,
                    isShuffleEnabled = queueData.isShuffleEnabled,
                    repeatMode = desiredRepeatMode
                )
            }
            
            if (wasPlaying) {
                playbackStateHolder.startProgressUpdates()
            } else {
                playbackStateHolder.updateStablePlayerState { it.copy(currentPosition = transferSnapshot.lastPosition) }
            }
        }
        
        lastRemoteMediaStatus = null
        lastRemoteQueue = emptyList()
        lastRemoteSongId = null
        lastRemoteStreamPosition = 0L
        
        onTransferBackComplete?.invoke()
    }

    suspend fun ensureHttpServerRunning(): Boolean {
        if (MediaFileHttpServerService.isServerRunning) return true
        
        val intent = Intent(context, MediaFileHttpServerService::class.java)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.tag(CAST_LOG_TAG).e(e, "Failed to start media server service")
            return false
        }

        for (i in 0..20) {
            if (MediaFileHttpServerService.isServerRunning && MediaFileHttpServerService.serverAddress != null) {
                return true
            }
            delay(100)
        }
        return false
    }

    suspend fun playRemoteQueue(
        songsToPlay: List<Song>,
        startSong: Song,
        isShuffleEnabled: Boolean
    ): Boolean {
        if (!ensureHttpServerRunning()) return false

        val serverAddress = MediaFileHttpServerService.serverAddress ?: return false
        val startIndex = songsToPlay.indexOf(startSong).coerceAtLeast(0)

        val repeatMode = playbackStateHolder.stablePlayerState.value.repeatMode
        val castRepeatMode = if (isShuffleEnabled) {
             MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
        } else {
             when (repeatMode) {
                 Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                 Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                 else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
             }
        }

        markPendingRemoteSong(startSong)
        lastRemoteQueue = songsToPlay
        lastRemoteSongId = startSong.id
        lastRemoteStreamPosition = 0L
        lastRemoteRepeatMode = castRepeatMode

        val castPlayer = castStateHolder.castPlayer
        if (castPlayer != null) {
            val completionDeferred = CompletableDeferred<Boolean>()
            castPlayer.loadQueue(
                songs = songsToPlay,
                startIndex = startIndex,
                startPosition = 0L,
                repeatMode = castRepeatMode,
                serverAddress = serverAddress,
                autoPlay = true,
                onComplete = { success ->
                    if (!success) {
                        onDisconnect?.invoke()
                    } else {
                        castStateHolder.setRemotePlaybackActive(true)
                    }
                    completionDeferred.complete(success)
                }
            )
            return completionDeferred.await()
        }
        return false
    }

     fun markPendingRemoteSong(song: Song) {
        pendingRemoteSongId = song.id
        pendingRemoteSongMarkedAt = SystemClock.elapsedRealtime()
        lastRemoteSongId = song.id
        lastRemoteItemId = null
        Timber.tag(CAST_LOG_TAG).d("Marked pending remote song: %s", song.id)
        
        playbackStateHolder.updateStablePlayerState { state -> state.copy(currentSong = song) }
        onSheetVisible?.invoke()
        
        onSongChanged?.invoke(song.albumArtUriString)

        val queue = getCurrentQueue?.invoke() ?: lastRemoteQueue
        val updatedQueue = if (queue.any { it.id == song.id } || queue.isEmpty()) {
            queue
        } else {
            queue + song
        }
        
        if (updatedQueue != queue) {
             updateQueue?.invoke(updatedQueue)
        }
        
        castStateHolder.setRemotePosition(0L)
        playbackStateHolder.updateStablePlayerState { it.copy(currentPosition = 0L) }
    }

    private fun resolvePendingRemoteSong(
        reportedSong: Song?,
        currentSongId: String?,
        songMap: Map<String, Song>
    ): Song? {
        if (reportedSong != null) return reportedSong

        val pendingId = pendingRemoteSongId
        if (pendingId == null) return null

        val pendingIsFresh = SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000
        if (!pendingIsFresh) return null

        if (currentSongId != pendingId) {
             return null
        }

        return songMap[pendingId]
    }
    
    // Helper Data Classes
    private data class TransferSnapshot(
        val lastKnownStatus: MediaStatus?,
        val lastRemoteQueue: List<Song>,
        val lastRemoteSongId: String?,
        val lastRemoteStreamPosition: Long,
        val lastRemoteRepeatMode: Int,
        val wasPlaying: Boolean,
        val lastPosition: Long,
        val isShuffleEnabled: Boolean
    )
    
     private data class QueueTransferData(
        val finalQueue: List<Song>,
        val targetSongId: String?,
        val isShuffleEnabled: Boolean
    )

    private data class RebuildArtifacts(
        val startIndex: Int,
        val mediaItems: List<MediaItem>,
        val targetSong: Song?
    )
}
