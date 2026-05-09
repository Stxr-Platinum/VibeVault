package com.vibevault.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SyncScheduler — Manages WorkManager scheduling for the SyncWorker.
 *
 * Provides two sync modes:
 *   - Immediate (one-shot): Triggered after user actions (like toggle)
 *   - Periodic: Background sync every 15 minutes when connected
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Enqueue an immediate one-shot sync.
     * Called after local mutations (like toggles, playlist edits).
     */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .addTag(SyncWorker.TAG)
            .build()

        workManager.enqueueUniqueWork(
            "${SyncWorker.WORK_NAME}_immediate",
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d("SyncScheduler", "Immediate sync enqueued")
    }

    /**
     * Schedule periodic background sync (every 15 minutes).
     * Called once during app initialization.
     */
    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .addTag(SyncWorker.TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.d("SyncScheduler", "Periodic sync scheduled (15min)")
    }

    /**
     * Cancel all pending sync work.
     * Called on logout.
     */
    fun cancelAll() {
        workManager.cancelAllWorkByTag(SyncWorker.TAG)
    }
}
