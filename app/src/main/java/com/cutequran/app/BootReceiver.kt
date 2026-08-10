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
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Vivo, Xiaomi and a few other ROMs send this instead of BOOT_COMPLETED
            // when the phone comes back from their "quick boot" / fast-start path.
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON -> {
                VerseNotifier.createChannels(context)
                // The clock kept running while we were off, so an ayah may already be due.
                if (!VerseRefresher.catchUp(context)) VerseNotifier.post(context)
                VerseService.sync(context)
                VerseWidget.refreshAll(context)
            }
        }
    }

    private companion object {
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
