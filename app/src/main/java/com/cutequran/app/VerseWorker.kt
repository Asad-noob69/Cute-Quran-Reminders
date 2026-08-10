package com.cutequran.app

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Backup timer. Alarms are the fast path; this is the one that survives.
 *
 * WorkManager persists its schedule in its own database and drives it through
 * JobScheduler, which OEM power managers treat far more gently than a chain of
 * app-owned alarms — and it re-registers itself after a reboot or a force-stop the next
 * time the app runs. It fires every 15 minutes and simply asks [VerseRefresher] whether
 * an ayah is owed, so a 6-hour interval still only changes every 6 hours.
 */
class VerseWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        runCatching { VerseRefresher.catchUp(applicationContext) }
        return Result.success()
    }

    companion object {
        const val NAME = "ayah_refresh"

        /** Fixed cadence: the worker checks, the prefs decide. 15 min is WorkManager's floor. */
        const val PERIOD_MINUTES = 15L
    }
}
