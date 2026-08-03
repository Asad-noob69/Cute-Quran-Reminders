package com.cutequran.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms don't survive a reboot, so put the ayah and its timer back. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                VerseNotifier.createChannels(context)
                VerseNotifier.post(context)
                VerseScheduler.scheduleNext(context)
                VerseService.sync(context)
                VerseWidget.refreshAll(context)
            }
        }
    }
}
