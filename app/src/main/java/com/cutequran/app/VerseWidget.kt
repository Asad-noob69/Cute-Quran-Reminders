package com.cutequran.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.ceil
import kotlin.math.max

/** A little home-screen card showing the same verse as the lock screen. */
class VerseWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // The launcher pokes us on its own half-hourly schedule, which is one more timer
        // the OEM power manager can't take away — use it to catch up and re-arm ours.
        VerseRefresher.catchUp(context)
        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, buildViews(context, manager.getAppWidgetOptions(id)))
        }
    }

    /** The card was resized, so the verse needs re-fitting to the new box. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        manager.updateAppWidget(appWidgetId, buildViews(context, newOptions))
    }

    companion object {

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, VerseWidget::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id ->
                manager.updateAppWidget(id, buildViews(context, manager.getAppWidgetOptions(id)))
            }
        }

        private fun buildViews(context: Context, options: Bundle? = null): RemoteViews {
            val prefs = Prefs(context)
            val verse = Quran.verseAt(context, prefs.currentIndex)
            val views = RemoteViews(context.packageName, R.layout.widget_verse)

            prefs.widgetTheme.applyTo(views)

            views.setTextViewText(R.id.widget_reference, verse.reference)
            views.setTextViewText(R.id.widget_arabic, verse.arabic)
            views.setTextViewText(R.id.widget_english, verse.english)
            views.setViewVisibility(
                R.id.widget_arabic,
                if (prefs.showArabic) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.widget_english,
                if (prefs.showTranslation) View.VISIBLE else View.GONE
            )

            fitText(views, options, verse, prefs.showArabic, prefs.showTranslation)

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE
                } else {
                    0
                }

            views.setOnClickPendingIntent(
                R.id.widget_shuffle,
                PendingIntent.getBroadcast(
                    context, 11,
                    Intent(context, VerseActionReceiver::class.java)
                        .setAction(VerseActionReceiver.ACTION_SHUFFLE),
                    flags
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 12,
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    flags
                )
            )
            return views
        }

        // Padding, header row and margins the verse never gets to use, in dp.
        private const val CHROME_HEIGHT = 14f + 14f + 30f + 10f
        private const val CHROME_WIDTH = 16f + 16f

        /**
         * Short verses should read big, long ones should still fit. RemoteViews can't
         * auto-size, so we estimate the wrapped height ourselves and pick the largest
         * size that fits the card the launcher actually gave us.
         */
        private fun fitText(
            views: RemoteViews,
            options: Bundle?,
            verse: Verse,
            showArabic: Boolean,
            showEnglish: Boolean
        ) {
            // Launchers report the box in dp; fall back to the provider's declared minimums.
            val width = max(
                (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0), 250
            ) - CHROME_WIDTH
            val height = max(
                (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0), 110
            ) - CHROME_HEIGHT

            // Split the leftover space between the two blocks that are actually shown.
            val arabicShare = when {
                showArabic && showEnglish -> 0.55f
                showArabic -> 1f
                else -> 0f
            }
            val englishShare = when {
                showArabic && showEnglish -> 0.45f - 0.04f // the 8dp gap between them
                showEnglish -> 1f
                else -> 0f
            }

            if (showArabic) {
                fitOne(
                    views, R.id.widget_arabic, verse.arabic, width, height * arabicShare,
                    minSize = 15f, maxSize = 34f,
                    charWidthRatio = 0.46f, lineHeightRatio = 1.45f * 1.55f
                )
            }
            if (showEnglish) {
                fitOne(
                    views, R.id.widget_english, verse.english, width, height * englishShare,
                    minSize = 12f, maxSize = 22f,
                    charWidthRatio = 0.50f, lineHeightRatio = 1.25f * 1.30f
                )
            }
        }

        private fun fitOne(
            views: RemoteViews,
            viewId: Int,
            text: String,
            width: Float,
            height: Float,
            minSize: Float,
            maxSize: Float,
            charWidthRatio: Float,
            lineHeightRatio: Float
        ) {
            val chars = text.length.coerceAtLeast(1)
            // Walk down in half-point steps until the estimated block fits; if even the
            // floor size overflows the card, the TextView ellipsizes as before.
            var size = minSize
            var candidate = maxSize
            while (candidate >= minSize) {
                val perLine = max(width / (candidate * charWidthRatio), 1f)
                val needed = ceil(chars / perLine).toInt().coerceAtLeast(1)
                if (needed * candidate * lineHeightRatio <= height) {
                    size = candidate
                    break
                }
                candidate -= 0.5f
            }

            views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, size)
        }
    }
}
