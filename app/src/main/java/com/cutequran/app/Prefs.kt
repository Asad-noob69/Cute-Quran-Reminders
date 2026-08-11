package com.cutequran.app

import android.content.Context
import kotlin.random.Random

/**
 * One shared store so the app, the lock-screen notification, the floating bubble and the
 * widget all show the *same* verse until it's time to shuffle.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("cute_quran", Context.MODE_PRIVATE)

    var currentIndex: Int
        get() {
            if (!sp.contains(KEY_INDEX)) {
                val fresh = Random.nextInt(Quran.TOTAL_VERSES)
                sp.edit().putInt(KEY_INDEX, fresh).apply()
                return fresh
            }
            return sp.getInt(KEY_INDEX, 0)
        }
        set(value) = sp.edit().putInt(KEY_INDEX, value).apply()

    /** Minutes between automatic shuffles. */
    var intervalMinutes: Int
        get() = sp.getInt(KEY_INTERVAL, 60)
        set(value) = sp.edit().putInt(KEY_INTERVAL, value).apply()

    /** Pin the verse to the lock screen / notification shade. */
    var lockScreenEnabled: Boolean
        get() = sp.getBoolean(KEY_LOCK, true)
        set(value) = sp.edit().putBoolean(KEY_LOCK, value).apply()

    /** Float a card on top of whatever app is open. */
    var bubbleEnabled: Boolean
        get() = sp.getBoolean(KEY_BUBBLE, false)
        set(value) = sp.edit().putBoolean(KEY_BUBBLE, value).apply()

    var showArabic: Boolean
        get() = sp.getBoolean(KEY_ARABIC, true)
        set(value) = sp.edit().putBoolean(KEY_ARABIC, value).apply()

    var showTranslation: Boolean
        get() = sp.getBoolean(KEY_TRANSLATION, true)
        set(value) = sp.edit().putBoolean(KEY_TRANSLATION, value).apply()

    /** Which card look the home-screen widget wears. */
    var widgetTheme: WidgetTheme
        get() = WidgetTheme.from(sp.getString(KEY_WIDGET_THEME, null))
        set(value) = sp.edit().putString(KEY_WIDGET_THEME, value.id).apply()

    var lastShuffleAt: Long
        get() = sp.getLong(KEY_LAST_SHUFFLE, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_SHUFFLE, value).apply()

    /** Remembered drag position of the floating bubble. */
    var bubbleX: Int
        get() = sp.getInt(KEY_BUBBLE_X, 0)
        set(value) = sp.edit().putInt(KEY_BUBBLE_X, value).apply()

    var bubbleY: Int
        get() = sp.getInt(KEY_BUBBLE_Y, 240)
        set(value) = sp.edit().putInt(KEY_BUBBLE_Y, value).apply()

    var bubbleExpanded: Boolean
        get() = sp.getBoolean(KEY_BUBBLE_OPEN, true)
        set(value) = sp.edit().putBoolean(KEY_BUBBLE_OPEN, value).apply()

    fun shuffle(): Int {
        var next = Random.nextInt(Quran.TOTAL_VERSES)
        if (next == currentIndex) next = (next + 1) % Quran.TOTAL_VERSES
        currentIndex = next
        lastShuffleAt = System.currentTimeMillis()
        return next
    }

    private companion object {
        const val KEY_INDEX = "index"
        const val KEY_INTERVAL = "interval"
        const val KEY_LOCK = "lock_screen"
        const val KEY_BUBBLE = "bubble"
        const val KEY_ARABIC = "arabic"
        const val KEY_TRANSLATION = "translation"
        const val KEY_WIDGET_THEME = "widget_theme"
        const val KEY_LAST_SHUFFLE = "last_shuffle"
        const val KEY_BUBBLE_X = "bubble_x"
        const val KEY_BUBBLE_Y = "bubble_y"
        const val KEY_BUBBLE_OPEN = "bubble_open"
    }
}
