package com.cutequran.app

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Drives the shuffle timer with a plain alarm rather than a live service, so the
 * lock-screen ayah keeps refreshing even when nothing of ours is running.
 *
 * The alarm is deliberately *not* the only timer — see [VerseWorker] — because OEM power
 * managers cancel alarms wholesale when they force-stop an app.
 */
object VerseScheduler {

    private const val REQUEST_CODE = 3

    /** Never arm an alarm in the past; give the system a moment to settle. */
    private const val MIN_DELAY_MS = 5_000L

    fun scheduleNext(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.lockScreenEnabled && !prefs.bubbleEnabled) {
            cancel(context)
            return
        }
        scheduleWorker(context)

        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        // Anchor on when the ayah last changed, not on "now". Anchoring on now meant
        // every app open, widget redraw or settings change pushed the next tick a full
        // interval into the future — a user who opens the app often would never see it
        // refresh on its own.
        val now = System.currentTimeMillis()
        val at = VerseRefresher.dueAt(prefs).coerceAtLeast(now + MIN_DELAY_MS)

        val pending = VerseNotifier.receiverIntent(
            context, VerseActionReceiver.ACTION_TICK, REQUEST_CODE
        )
        alarms.cancel(pending)

        runCatching {
            when {
                // Exact where the platform still hands it out freely (API < 33): OEM
                // ROMs batch inexact alarms into deep sleep and the ayah drifts by hours.
                canScheduleExact(alarms) ->
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                // setAndAllowWhileIdle survives Doze; our shortest interval (15 min) is
                // above the once-per-9-minutes ceiling it imposes, so nothing is dropped.
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else -> alarms.set(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }.onFailure {
            // Some ROMs cap the number of alarms per app and throw; the worker still ticks.
            runCatching { alarms.set(AlarmManager.RTC_WAKEUP, at, pending) }
        }
    }

    private fun canScheduleExact(alarms: AlarmManager): Boolean = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> alarms.canScheduleExactAlarms()
        else -> true
    }

    /** The redundant timer that outlives a force-stop. Cheap to re-declare. */
    private fun scheduleWorker(context: Context) {
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                VerseWorker.NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<VerseWorker>(
                    VerseWorker.PERIOD_MINUTES, TimeUnit.MINUTES
                ).build()
            )
        }
    }

    fun cancel(context: Context) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(VerseWorker.NAME) }
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(
            VerseNotifier.receiverIntent(context, VerseActionReceiver.ACTION_TICK, REQUEST_CODE)
        )
    }
}
