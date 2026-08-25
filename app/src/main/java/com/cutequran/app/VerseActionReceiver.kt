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
        when (intent.action) {
            // The timer fired. Go through catchUp rather than shuffling blindly so the
            // alarm and the backup worker can't double-shuffle if they land together.
            ACTION_TICK -> VerseRefresher.catchUp(context)

            // The user asked for it, so always give them a new one.
            ACTION_SHUFFLE -> VerseRefresher.shuffleNow(context)

            ACTION_COPY -> VerseClipboard.copyCurrent(context)

            ACTION_TOGGLE_BUBBLE -> {
                val prefs = Prefs(context)
                prefs.bubbleEnabled = !prefs.bubbleEnabled
                VerseService.sync(context)
                VerseNotifier.post(context)
                VerseScheduler.scheduleNext(context)
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.cutequran.app.TICK"
        const val ACTION_SHUFFLE = "com.cutequran.app.SHUFFLE"
        const val ACTION_COPY = "com.cutequran.app.COPY"
        const val ACTION_TOGGLE_BUBBLE = "com.cutequran.app.TOGGLE_BUBBLE"
    }
}
