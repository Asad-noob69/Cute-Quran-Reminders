package com.cutequran.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews

/** A little home-screen card showing the same verse as the lock screen. */
class VerseWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, VerseWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = Prefs(context)
            val verse = Quran.verseAt(context, prefs.currentIndex)
            val views = RemoteViews(context.packageName, R.layout.widget_verse)

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
    }
}
