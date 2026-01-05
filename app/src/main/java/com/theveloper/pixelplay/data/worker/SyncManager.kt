package com.theveloper.pixelplay.data.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class representing the progress of the sync operation.
 */
data class SyncProgress(
    val isRunning: Boolean = false,
    val currentCount: Int = 0,
    val totalCount: Int = 0,
    val isCompleted: Boolean = false,
    val phase: SyncPhase = SyncPhase.IDLE
) {
    enum class SyncPhase {
        IDLE,
        FETCHING_MEDIASTORE,
        PROCESSING_FILES,
        SAVING_TO_DATABASE,
        COMPLETING
    }

    val progress: Float
        get() = if (totalCount > 0) currentCount.toFloat() / totalCount else 0f

    val hasProgress: Boolean
        get() = totalCount > 0
    }

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val workManager = WorkManager.getInstance(context)
    private val sharingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // EXPONE UN FLOW<BOOLEAN> SIMPLE
    val isSyncing: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING }
                val isEnqueued = workInfos.any { it.state == WorkInfo.State.ENQUEUED }
                isRunning || isEnqueued
            }
            .distinctUntilChanged()
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

    /**
     * Flow that exposes the detailed sync progress including song count.
     */
    val syncProgress: Flow<SyncProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME)
            .map { workInfos ->
                val runningWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                val succeededWork = workInfos.firstOrNull { it.state == WorkInfo.State.SUCCEEDED }
                val enqueuedWork = workInfos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }

                when {
                    runningWork != null -> {
                        val current = runningWork.progress.getInt(SyncWorker.PROGRESS_CURRENT, 0)
                        val total = runningWork.progress.getInt(SyncWorker.PROGRESS_TOTAL, 0)
                        val phaseOrdinal = runningWork.progress.getInt(SyncWorker.PROGRESS_PHASE, 0)
                        val phase = try {
                            SyncProgress.SyncPhase.entries[phaseOrdinal]
                        } catch (e: IndexOutOfBoundsException) {
                            SyncProgress.SyncPhase.IDLE
                        }
                        SyncProgress(
                            isRunning = true,
                            currentCount = current,
                            totalCount = total,
                            isCompleted = false,
                            phase = phase
                        )
                    }
                    succeededWork != null -> {
                        val total = succeededWork.outputData.getInt(SyncWorker.OUTPUT_TOTAL_SONGS, 0)
                        SyncProgress(
                            isRunning = false,
                            currentCount = total,
                            totalCount = total,
                            isCompleted = true,
                            phase = SyncProgress.SyncPhase.COMPLETING
                        )
                    }
                    enqueuedWork != null -> {
                        SyncProgress(isRunning = true, isCompleted = false, phase = SyncProgress.SyncPhase.IDLE)
                    }
                    else -> SyncProgress()
                }
            }
            .distinctUntilChanged()
            .shareIn(
                scope = sharingScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

    fun sync() {
        sharingScope.launch {
            val lastSyncTime = userPreferencesRepository.getLastSyncTimestamp()
            val currentTime = System.currentTimeMillis()

            if (lastSyncTime == 0L || (currentTime - lastSyncTime) >= MIN_SYNC_INTERVAL_MS) {
                Log.d(TAG, "Syncing library (last sync: ${(currentTime - lastSyncTime) / 1000}s ago)")
                val syncRequest = if (lastSyncTime == 0L) {
                    Log.d(TAG, "Initial sync detected. Using REBUILD mode for maximum speed.")
                    SyncWorker.rebuildDatabaseWork()
                } else {
                    SyncWorker.startUpSyncWork()
                }
                workManager.enqueueUniqueWork(
                    SyncWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
            } else {
                Log.d(TAG, "Skipping sync - last sync was ${(currentTime - lastSyncTime) / 1000}s ago (min: ${MIN_SYNC_INTERVAL_MS / 1000}s)")
            }
        }
    }

    /**
     * Performs an incremental sync, only processing files that have changed
     * since the last sync. Much faster for large libraries with few changes.
     * This is the recommended sync method for pull-to-refresh actions.
     */
    fun incrementalSync() {
        val syncRequest = SyncWorker.incrementalSyncWork()
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Performs a full library rescan, ignoring the last sync timestamp.
     * Use this when the user explicitly wants to force a complete rescan.
     */
    fun fullSync() {
        val syncRequest = SyncWorker.fullSyncWork(deepScan = false)
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Completely rebuilds the database from scratch.
     * Clears all existing data including user edits (lyrics, etc.) and rescans.
     * Use when database is corrupted or songs are missing.
     */
    fun rebuildDatabase() {
        val syncRequest = SyncWorker.rebuildDatabaseWork()
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Fuerza una nueva sincronización, reemplazando cualquier trabajo de sincronización
     * existente. Ideal para el botón de "Refrescar Biblioteca".
     */
    fun forceRefresh() {
        val syncRequest = SyncWorker.startUpSyncWork(true)
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    companion object {
        private const val TAG = "SyncManager"
        private const val MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }
}
