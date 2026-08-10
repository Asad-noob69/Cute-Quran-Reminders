package com.cutequran.app

import android.content.Context

/**
 * The one place that decides "is a new ayah due?" and puts it everywhere.
 *
 * The timer used to be a single chain of self-rescheduling alarms: each tick armed the
 * next one. On lenient ROMs that is fine, but aggressive OEM power managers (Vivo's
 * Funtouch/OriginOS especially, also Xiaomi/Oppo/Realme) force-stop background apps —
 * and a force-stopped app has *all* of its alarms cancelled and receives no broadcasts
 * until it is opened again. One dropped tick killed the chain forever, so the verse
 * simply froze.
 *
 * So instead of trusting any single timer, every path back into the app — alarm, backup
 * worker, widget update, boot, opening the app — calls [catchUp]. It shuffles if the
 * interval has actually elapsed (using the wall clock, not a timer that must survive)
 * and re-arms the schedule. Whatever the ROM kills, the next event heals it.
 */
object VerseRefresher {

    /** Ticks can land a little early; don't skip a refresh over a few seconds. */
    private const val TOLERANCE_MS = 60_000L

    fun intervalMillis(prefs: Prefs): Long = prefs.intervalMinutes * 60_000L

    /** When the next ayah is owed, in wall-clock time. */
    fun dueAt(prefs: Prefs): Long = prefs.lastShuffleAt + intervalMillis(prefs)

    fun isDue(prefs: Prefs, now: Long = System.currentTimeMillis()): Boolean {
        val last = prefs.lastShuffleAt
        // Never shuffled yet, or the user moved the clock backwards.
        if (last <= 0L || last > now) return true
        return now - last >= intervalMillis(prefs) - TOLERANCE_MS
    }

    /**
     * Shuffle if the interval has elapsed, push the result out, and always re-arm the
     * schedule. Cheap and idempotent — safe to call from any entry point.
     *
     * @return true if a new ayah was picked.
     */
    fun catchUp(context: Context): Boolean {
        val prefs = Prefs(context)
        val wanted = prefs.lockScreenEnabled || prefs.bubbleEnabled
        var shuffled = false

        if (wanted && isDue(prefs)) {
            prefs.shuffle()
            shuffled = true
        }
        if (shuffled) push(context)
        VerseScheduler.scheduleNext(context)
        return shuffled
    }

    /** Force a new ayah right now (the shuffle buttons). */
    fun shuffleNow(context: Context) {
        Prefs(context).shuffle()
        push(context)
        VerseScheduler.scheduleNext(context)
    }

    /** Redraw the ayah wherever it is currently shown. */
    fun push(context: Context) {
        VerseNotifier.post(context)
        VerseWidget.refreshAll(context)
        VerseService.notifyRefresh(context)
    }
}
