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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    val syncWorkInfo: LiveData<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME)

    fun enqueueSyncWorker(replaceExisting: Boolean = false) {
        val constraints = Constraints.Builder()
            // Opcional: Definir restricciones, ej. NetworkType.UNMETERED si descarga mucho
            // .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            // Podrías añadir datos de entrada si fueran necesarios para el worker
            // .setInputData(workDataOf("KEY_EXAMPLE" to "VALUE_EXAMPLE"))
            .build()

        val policy = if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            policy,
            syncRequest
        )
    }

    // Opcional: un método para iniciar la sincronización solo si no se ha hecho antes (ej. primer inicio)
    // Esto requeriría una bandera en UserPreferences.
    // fun initialSyncIfNecessary() { ... }
}
