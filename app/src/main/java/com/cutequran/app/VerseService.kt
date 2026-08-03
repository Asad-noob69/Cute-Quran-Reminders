package com.cutequran.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Exists purely to hold the floating card on screen — the lock-screen ayah is handled by
 * [VerseNotifier] and [VerseScheduler], which need nothing running. That keeps the
 * unavoidable "service is running" notification out of the way when the user only wants
 * the lock screen.
 */
class VerseService : Service() {

    private lateinit var prefs: Prefs
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_REFRESH) renderBubble()
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        VerseNotifier.createChannels(this)
        ContextCompat.registerReceiver(
            this,
            actionReceiver,
            IntentFilter(ACTION_REFRESH),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android gives us only a few seconds to publish a notification, so do it first.
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this, VerseNotifier.SERVICE_NOTIF_ID, VerseNotifier.buildServiceNotification(this), type
        )

        if (!prefs.bubbleEnabled || !canOverlay()) {
            stopEverything()
            return START_NOT_STICKY
        }

        addBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(actionReceiver) }
        removeBubble()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- floating card

    private fun canOverlay(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun addBubble() {
        if (bubbleView != null) {
            renderBubble()
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.bubble_card, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.bubbleX
            y = prefs.bubbleY
        }

        view.findViewById<ImageButton>(R.id.bubble_shuffle).setOnClickListener {
            sendBroadcast(
                Intent(this, VerseActionReceiver::class.java)
                    .setAction(VerseActionReceiver.ACTION_SHUFFLE)
            )
        }
        view.findViewById<ImageButton>(R.id.bubble_close).setOnClickListener {
            prefs.bubbleEnabled = false
            VerseNotifier.post(this)
            stopEverything()
        }
        view.findViewById<View>(R.id.bubble_collapsed).setOnClickListener {
            prefs.bubbleExpanded = true
            renderBubble()
        }
        view.findViewById<ImageButton>(R.id.bubble_minimise).setOnClickListener {
            prefs.bubbleExpanded = false
            renderBubble()
        }
        attachDrag(view.findViewById(R.id.bubble_drag_handle), params)
        attachDrag(view.findViewById(R.id.bubble_collapsed), params)

        runCatching { windowManager?.addView(view, params) }.onFailure { return }

        bubbleView = view
        renderBubble()
    }

    private fun attachDrag(handle: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var dragged = false
        handle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > 12 || abs(dy) > 12) dragged = true
                    params.x = startX + dx.toInt()
                    params.y = startY + dy.toInt()
                    bubbleView?.let { runCatching { windowManager?.updateViewLayout(it, params) } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.bubbleX = params.x
                    prefs.bubbleY = params.y
                    if (!dragged) v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    private fun renderBubble() {
        val view = bubbleView ?: return
        val verse = Quran.verseAt(this, prefs.currentIndex)
        val expanded = prefs.bubbleExpanded

        view.findViewById<View>(R.id.bubble_expanded).visibility =
            if (expanded) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.bubble_collapsed).visibility =
            if (expanded) View.GONE else View.VISIBLE

        if (!expanded) return

        view.findViewById<TextView>(R.id.bubble_reference).text = verse.reference
        view.findViewById<TextView>(R.id.bubble_arabic).apply {
            text = verse.arabic
            visibility = if (prefs.showArabic) View.VISIBLE else View.GONE
        }
        view.findViewById<TextView>(R.id.bubble_english).apply {
            text = verse.english
            visibility = if (prefs.showTranslation) View.VISIBLE else View.GONE
        }
    }

    private fun removeBubble() {
        bubbleView?.let { view -> runCatching { windowManager?.removeView(view) } }
        bubbleView = null
    }

    private fun stopEverything() {
        removeBubble()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_REFRESH = "com.cutequran.app.REFRESH"

        /** Start or stop the floating card to match the prefs. */
        fun sync(context: Context) {
            val intent = Intent(context, VerseService::class.java)
            if (Prefs(context).bubbleEnabled) {
                runCatching { ContextCompat.startForegroundService(context, intent) }
            } else {
                context.stopService(intent)
            }
            VerseWidget.refreshAll(context)
        }

        /** Nudge a running service to redraw the card. */
        fun notifyRefresh(context: Context) {
            context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
        }
    }
}
