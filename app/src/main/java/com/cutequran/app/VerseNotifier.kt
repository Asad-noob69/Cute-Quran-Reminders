package com.cutequran.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Posts the ayah to the lock screen.
 *
 * Deliberately *not* the foreground-service notification: One UI (and stock Android) push
 * ongoing service notifications and anything on a low-importance channel down into the
 * silent section, which the lock screen doesn't render as a card. So the verse gets its
 * own default-importance channel with the sound and vibration stripped out — it reads as
 * a normal alerting notification, so it shows up properly, but it never makes a noise.
 */
object VerseNotifier {

    const val VERSE_CHANNEL = "ayah_lockscreen"
    const val SERVICE_CHANNEL = "ayah_floating_service"
    const val VERSE_NOTIF_ID = 4242
    const val SERVICE_NOTIF_ID = 4243

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val verse = NotificationChannel(
            VERSE_CHANNEL,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.channel_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

        val service = NotificationChannel(
            SERVICE_CHANNEL,
            context.getString(R.string.channel_service_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.channel_service_desc)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            setSound(null, null)
            enableVibration(false)
        }

        manager.createNotificationChannel(verse)
        manager.createNotificationChannel(service)
    }

    fun buildVerseNotification(context: Context): Notification {
        val prefs = Prefs(context)
        val verse = Quran.verseAt(context, prefs.currentIndex)

        val body = buildString {
            if (prefs.showArabic) append(verse.arabic)
            if (prefs.showArabic && prefs.showTranslation) append("\n\n")
            if (prefs.showTranslation) append("“").append(verse.english).append("”")
        }.ifBlank { verse.english }

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            flags()
        )

        return NotificationCompat.Builder(context, VERSE_CHANNEL)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(verse.reference)
            .setContentText(if (prefs.showTranslation) verse.english else verse.arabic)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(body)
                    .setSummaryText(verse.surahMeaning)
            )
            .setContentIntent(open)
            .addAction(
                R.drawable.ic_shuffle,
                context.getString(R.string.new_verse),
                receiverIntent(context, VerseActionReceiver.ACTION_SHUFFLE, 1)
            )
            .addAction(
                R.drawable.ic_bubble,
                context.getString(
                    if (prefs.bubbleEnabled) R.string.hide_bubble else R.string.show_bubble
                ),
                receiverIntent(context, VerseActionReceiver.ACTION_TOGGLE_BUBBLE, 2)
            )
            // Not ongoing: Samsung treats ongoing notifications as service chrome and
            // tucks them away. Dismissible is the price of showing on the lock screen.
            .setOngoing(false)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(ContextCompat.getColor(context, R.color.lavender))
            .build()
    }

    /** The near-invisible notification that keeps the floating-card service alive. */
    fun buildServiceNotification(context: Context): Notification =
        NotificationCompat.Builder(context, SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.floating_only))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setColor(ContextCompat.getColor(context, R.color.lavender))
            .build()

    fun post(context: Context) {
        val prefs = Prefs(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!prefs.lockScreenEnabled) {
            manager.cancel(VERSE_NOTIF_ID)
            return
        }
        createChannels(context)
        runCatching { manager.notify(VERSE_NOTIF_ID, buildVerseNotification(context)) }
    }

    fun cancelVerse(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(VERSE_NOTIF_ID)
    }

    fun receiverIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, VerseActionReceiver::class.java).setAction(action),
            flags()
        )

    fun flags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
