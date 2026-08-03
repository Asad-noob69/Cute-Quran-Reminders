package com.cutequran.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Single entry point for everything that happens outside the app UI: the shuffle alarm,
 * the notification buttons and the widget button. It works whether or not the floating
 * card service is alive.
 */
class VerseActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)

        when (intent.action) {
            ACTION_TICK, ACTION_SHUFFLE -> {
                prefs.shuffle()
                VerseNotifier.post(context)
                VerseWidget.refreshAll(context)
                VerseService.notifyRefresh(context)
                VerseScheduler.scheduleNext(context)
            }

            ACTION_TOGGLE_BUBBLE -> {
                prefs.bubbleEnabled = !prefs.bubbleEnabled
                VerseService.sync(context)
                VerseNotifier.post(context)
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.cutequran.app.TICK"
        const val ACTION_SHUFFLE = "com.cutequran.app.SHUFFLE"
        const val ACTION_TOGGLE_BUBBLE = "com.cutequran.app.TOGGLE_BUBBLE"
    }
}
