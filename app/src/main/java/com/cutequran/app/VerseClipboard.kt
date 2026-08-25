package com.cutequran.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast

/**
 * Puts an ayah on the clipboard. It lives outside the activity because the floating card
 * and the widget button copy the very same text, and neither of them has an activity to
 * lean on.
 */
object VerseClipboard {

    /** Copies whichever ayah is showing right now. */
    fun copyCurrent(context: Context) {
        copy(context, Quran.verseAt(context, Prefs(context).currentIndex))
    }

    fun copy(context: Context, verse: Verse) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(context.getString(R.string.clip_label), verse.shareText)
        runCatching { clipboard.setPrimaryClip(clip) }.onFailure { return }
        // Android 13 pops up its own copy confirmation, so a toast on top would double it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }
}
