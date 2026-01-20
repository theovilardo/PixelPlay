package com.theveloper.pixelplay.presentation.viewmodel

import android.os.SystemClock
import androidx.media3.common.C
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Tracks listening statistics for songs.
 * Extracted from PlayerViewModel to reduce its size and improve modularity.
 *
 * Responsibilities:
 * - Track active listening sessions
 * - Record play statistics when session ends
 * - Handle voluntary vs automatic plays
 */
class ListeningStatsTracker @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository
) {
    private var currentSession: ActiveSession? = null
    private var pendingVoluntarySongId: String? = null
    private var scope: CoroutineScope? = null

    /**
     * Must be called to set the coroutine scope for async operations.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    fun onVoluntarySelection(songId: String) {
        pendingVoluntarySongId = songId
    }

    fun onSongChanged(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        finalizeCurrentSession()
        if (song == null) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val normalizedDuration = when {
            durationMs > 0 && durationMs != C.TIME_UNSET -> durationMs
            song.duration > 0 -> song.duration
            else -> 0L
        }

        currentSession = ActiveSession(
            songId = song.id,
            totalDurationMs = normalizedDuration,
            startedAtEpochMs = nowEpoch,
            lastKnownPositionMs = positionMs.coerceAtLeast(0L),
            accumulatedListeningMs = 0L,
            lastRealtimeMs = nowRealtime,
            lastUpdateEpochMs = nowEpoch,
            isPlaying = isPlaying,
            isVoluntary = pendingVoluntarySongId == song.id
        )
        if (pendingVoluntarySongId == song.id) {
            pendingVoluntarySongId = null
        }
    }

    fun onPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        if (session.isPlaying) {
            session.accumulatedListeningMs += (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
        }
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()
    }

    fun onProgress(positionMs: Long, isPlaying: Boolean) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        if (session.isPlaying) {
            val delta = (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
            if (delta > 0) {
                session.accumulatedListeningMs += delta
            }
        }
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()
    }

    fun ensureSession(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        if (song == null) {
            finalizeCurrentSession()
            return
        }
        val existing = currentSession
        if (existing?.songId == song.id) {
            updateDuration(durationMs)
            val nowRealtime = SystemClock.elapsedRealtime()
            if (existing.isPlaying) {
                existing.accumulatedListeningMs += (nowRealtime - existing.lastRealtimeMs).coerceAtLeast(0L)
            }
            existing.isPlaying = isPlaying
            existing.lastRealtimeMs = nowRealtime
            existing.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
            existing.lastUpdateEpochMs = System.currentTimeMillis()
            return
        }
        onSongChanged(song, positionMs, durationMs, isPlaying)
    }

    fun updateDuration(durationMs: Long) {
        val session = currentSession ?: return
        if (durationMs > 0 && durationMs != C.TIME_UNSET) {
            session.totalDurationMs = durationMs
        }
    }

    fun finalizeCurrentSession() {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        if (session.isPlaying) {
            session.accumulatedListeningMs += (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
        }
        val totalCap = if (session.totalDurationMs > 0) session.totalDurationMs else Long.MAX_VALUE
        val listened = session.accumulatedListeningMs.coerceAtMost(totalCap).coerceAtLeast(0L)
        if (listened >= MIN_SESSION_LISTEN_MS) {
            val rawEndTimestamp = session.lastUpdateEpochMs.takeIf { it > 0L }
                ?: (session.startedAtEpochMs + listened)
            val timestamp = rawEndTimestamp
                .coerceAtLeast(session.startedAtEpochMs.coerceAtLeast(0L))
                .coerceAtMost(System.currentTimeMillis())
            val songId = session.songId
            scope?.launch(Dispatchers.IO) {
                dailyMixManager.recordPlay(
                    songId = songId,
                    songDurationMs = listened,
                    timestamp = timestamp
                )
                playbackStatsRepository.recordPlayback(
                    songId = songId,
                    durationMs = listened,
                    timestamp = timestamp
                )
            }
        }
        currentSession = null
        if (pendingVoluntarySongId == session.songId) {
            pendingVoluntarySongId = null
        }
    }

    fun onPlaybackStopped() {
        finalizeCurrentSession()
    }

    fun onCleared() {
        finalizeCurrentSession()
        scope = null
    }

    companion object {
        private val MIN_SESSION_LISTEN_MS = TimeUnit.SECONDS.toMillis(5)
    }
}

/**
 * Represents an active listening session for a song.
 */
data class ActiveSession(
    val songId: String,
    var totalDurationMs: Long,
    val startedAtEpochMs: Long,
    var lastKnownPositionMs: Long,
    var accumulatedListeningMs: Long,
    var lastRealtimeMs: Long,
    var lastUpdateEpochMs: Long,
    var isPlaying: Boolean,
    val isVoluntary: Boolean
)
