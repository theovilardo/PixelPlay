package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    // EXPONE UN FLOW<BOOLEAN> SIMPLE
    val isSyncing: Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME)
            .map { workInfos ->
                val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING }
                val isEnqueued = workInfos.any { it.state == WorkInfo.State.ENQUEUED }
                isRunning || isEnqueued
            }
            .distinctUntilChanged()

    fun sync() {
        val syncRequest = SyncWorker.startUpSyncWork()
        workManager.enqueueUniqueWork(
            SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            syncRequest
        )
    }

    /**
     * Fuerza una nueva sincronización, reemplazando cualquier trabajo de sincronización
     * existente. Ideal para el botón de "Refrescar Biblioteca".
     */
    fun forceRefresh() {
        val syncRequest = SyncWorker.startUpSyncWork()
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE, // La política clave para el refresco
            syncRequest
        )
    }

    companion object {
        const val SYNC_WORK_NAME = "com.theveloper.pixelplay.SYNC_WORK"
    }
}
