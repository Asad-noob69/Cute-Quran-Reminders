package com.cutequran.app

import android.app.AlarmManager
import android.content.Context
import android.os.Build

/**
 * Drives the shuffle timer with a plain alarm rather than a live service, so the
 * lock-screen ayah keeps refreshing even when nothing of ours is running.
 */
object VerseScheduler {

    private const val REQUEST_CODE = 3

    fun scheduleNext(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.lockScreenEnabled && !prefs.bubbleEnabled) {
            cancel(context)
            return
        }
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + prefs.intervalMinutes * 60_000L
        val pending = VerseNotifier.receiverIntent(
            context, VerseActionReceiver.ACTION_TICK, REQUEST_CODE
        )
        alarms.cancel(pending)
        // setAndAllowWhileIdle survives Doze; our shortest interval (15 min) is well
        // above the once-per-9-minutes ceiling it imposes, so nothing gets dropped.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.set(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(
            VerseNotifier.receiverIntent(context, VerseActionReceiver.ACTION_TICK, REQUEST_CODE)
        )
    }
}
