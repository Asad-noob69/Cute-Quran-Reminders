package com.cutequran.app

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var reference: TextView
    private lateinit var arabic: TextView
    private lateinit var english: TextView
    private lateinit var meaning: TextView

    private lateinit var previewCard: View
    private lateinit var themeList: LinearLayout

    private lateinit var lockSwitch: MaterialSwitch
    private lateinit var bubbleSwitch: MaterialSwitch
    private lateinit var arabicSwitch: MaterialSwitch
    private lateinit var translationSwitch: MaterialSwitch

    private var bindingSwitches = false

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enableLockScreen()
            } else {
                bindSwitches()
                toast(getString(R.string.need_notifications))
            }
        }

    private val askOverlay =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (canDrawOverlays()) {
                prefs.bubbleEnabled = true
                VerseService.sync(this)
                VerseScheduler.scheduleNext(this)
            } else {
                toast(getString(R.string.need_overlay))
            }
            bindSwitches()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        reference = findViewById(R.id.verse_reference)
        arabic = findViewById(R.id.verse_arabic)
        english = findViewById(R.id.verse_english)
        meaning = findViewById(R.id.verse_meaning)

        previewCard = findViewById(R.id.widget_preview)
        themeList = findViewById(R.id.theme_list)
        buildThemeChips()

        lockSwitch = findViewById(R.id.switch_lock)
        bubbleSwitch = findViewById(R.id.switch_bubble)
        arabicSwitch = findViewById(R.id.switch_arabic)
        translationSwitch = findViewById(R.id.switch_translation)

        findViewById<MaterialButton>(R.id.button_shuffle).setOnClickListener {
            VerseRefresher.shuffleNow(this)
            showVerse(animate = true)
        }

        findViewById<MaterialButton>(R.id.button_share).setOnClickListener { shareVerse() }

        lockSwitch.setOnCheckedChangeListener { _, checked ->
            if (bindingSwitches) return@setOnCheckedChangeListener
            if (checked) requestLockScreen() else {
                prefs.lockScreenEnabled = false
                VerseNotifier.cancelVerse(this)
                VerseScheduler.scheduleNext(this)
            }
        }

        bubbleSwitch.setOnCheckedChangeListener { _, checked ->
            if (bindingSwitches) return@setOnCheckedChangeListener
            if (checked) requestBubble() else {
                prefs.bubbleEnabled = false
                VerseService.sync(this)
                VerseScheduler.scheduleNext(this)
            }
        }

        arabicSwitch.setOnCheckedChangeListener { _, checked ->
            if (bindingSwitches) return@setOnCheckedChangeListener
            if (!checked && !prefs.showTranslation) {
                bindSwitches()
                toast(getString(R.string.keep_one))
                return@setOnCheckedChangeListener
            }
            prefs.showArabic = checked
            showVerse(animate = false)
            pushEverywhere()
        }

        translationSwitch.setOnCheckedChangeListener { _, checked ->
            if (bindingSwitches) return@setOnCheckedChangeListener
            if (!checked && !prefs.showArabic) {
                bindSwitches()
                toast(getString(R.string.keep_one))
                return@setOnCheckedChangeListener
            }
            prefs.showTranslation = checked
            showVerse(animate = false)
            pushEverywhere()
        }

        val intervals = findViewById<MaterialButtonToggleGroup>(R.id.interval_group)
        intervals.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || bindingSwitches) return@addOnButtonCheckedListener
            prefs.intervalMinutes = when (checkedId) {
                R.id.interval_15 -> 15
                R.id.interval_30 -> 30
                R.id.interval_60 -> 60
                R.id.interval_180 -> 180
                else -> 360
            }
            VerseScheduler.scheduleNext(this)
            toast(getString(R.string.interval_saved))
        }

        findViewById<View>(R.id.battery_hint).setOnClickListener { openBatterySettings() }
        findViewById<View>(R.id.autostart_hint).setOnClickListener { openAutostartSettings() }
        findViewById<View>(R.id.lockscreen_hint).setOnClickListener { openLockScreenSettings() }
    }

    override fun onResume() {
        super.onResume()
        // The overlay permission (and Android 13 notification permission) can be revoked
        // from system settings while we're backgrounded, so re-read the truth each time.
        if (prefs.bubbleEnabled && !canDrawOverlays()) prefs.bubbleEnabled = false
        if (prefs.lockScreenEnabled && !hasNotificationPermission()) prefs.lockScreenEnabled = false
        VerseNotifier.createChannels(this)
        // If the ROM killed our timer while we were away, this both catches the ayah up
        // and puts the schedule back.
        if (!VerseRefresher.catchUp(this)) VerseNotifier.post(this)
        showVerse(animate = false)
        bindSwitches()
    }

    // ------------------------------------------------------------------ verse card

    private fun showVerse(animate: Boolean) {
        val verse = Quran.verseAt(this, prefs.currentIndex)
        reference.text = verse.reference
        meaning.text = "${verse.surahArabic}  ·  ${verse.surahMeaning}"
        arabic.text = verse.arabic
        english.text = "“${verse.english}”"
        arabic.visibility = if (prefs.showArabic) View.VISIBLE else View.GONE
        english.visibility = if (prefs.showTranslation) View.VISIBLE else View.GONE

        showPreview(verse)

        if (animate) {
            val pop = AnimationUtils.loadAnimation(this, R.anim.verse_pop)
            findViewById<View>(R.id.verse_card).startAnimation(pop)
        }
    }

    // ------------------------------------------------------------------ widget style

    /** The preview is the widget's own layout, so what you see is what lands on the home screen. */
    private fun showPreview(verse: Verse) {
        previewCard.findViewById<TextView>(R.id.widget_reference).text = verse.reference
        previewCard.findViewById<TextView>(R.id.widget_arabic).apply {
            text = verse.arabic
            visibility = if (prefs.showArabic) View.VISIBLE else View.GONE
        }
        previewCard.findViewById<TextView>(R.id.widget_english).apply {
            text = verse.english
            visibility = if (prefs.showTranslation) View.VISIBLE else View.GONE
        }
        prefs.widgetTheme.applyTo(previewCard)
    }

    private fun buildThemeChips() {
        WidgetTheme.values().forEach { theme ->
            val chip = layoutInflater.inflate(R.layout.theme_chip, themeList, false)
            chip.findViewById<View>(R.id.chip_swatch).setBackgroundResource(theme.background)
            chip.findViewById<TextView>(R.id.chip_sample).setTextColor(theme.arabicColor)
            chip.findViewById<TextView>(R.id.chip_label).setText(theme.label)
            chip.contentDescription = getString(theme.label)
            chip.setOnClickListener {
                if (prefs.widgetTheme == theme) return@setOnClickListener
                prefs.widgetTheme = theme
                showPreview(Quran.verseAt(this, prefs.currentIndex))
                markChosenTheme()
                VerseWidget.refreshAll(this)
                toast(getString(R.string.theme_saved))
            }
            themeList.addView(chip)
        }
    }

    private fun markChosenTheme() {
        WidgetTheme.values().forEachIndexed { index, theme ->
            val chip = themeList.getChildAt(index) ?: return
            val chosen = theme == prefs.widgetTheme
            chip.findViewById<View>(R.id.chip_ring).setBackgroundResource(
                if (chosen) R.drawable.theme_chip_ring else R.drawable.theme_chip_ring_off
            )
            chip.findViewById<TextView>(R.id.chip_label).setTextColor(
                ContextCompat.getColor(
                    this,
                    if (chosen) R.color.lavender_deep else R.color.ink_faint
                )
            )
        }
    }

    private fun shareVerse() {
        val verse = Quran.verseAt(this, prefs.currentIndex)
        val text = "${verse.arabic}\n\n“${verse.english}”\n\n— ${verse.reference}"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text),
                getString(R.string.share_verse)
            )
        )
    }

    private fun pushEverywhere() {
        VerseNotifier.post(this)
        VerseService.notifyRefresh(this)
        VerseWidget.refreshAll(this)
    }

    // ------------------------------------------------------------------ switches

    private fun bindSwitches() {
        bindingSwitches = true
        lockSwitch.isChecked = prefs.lockScreenEnabled
        bubbleSwitch.isChecked = prefs.bubbleEnabled
        arabicSwitch.isChecked = prefs.showArabic
        translationSwitch.isChecked = prefs.showTranslation
        findViewById<MaterialButtonToggleGroup>(R.id.interval_group).check(
            when (prefs.intervalMinutes) {
                15 -> R.id.interval_15
                30 -> R.id.interval_30
                60 -> R.id.interval_60
                180 -> R.id.interval_180
                else -> R.id.interval_360
            }
        )
        markChosenTheme()
        bindingSwitches = false
    }

    private fun requestLockScreen() {
        if (!hasNotificationPermission()) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        enableLockScreen()
    }

    private fun enableLockScreen() {
        prefs.lockScreenEnabled = true
        VerseNotifier.post(this)
        VerseScheduler.scheduleNext(this)
        bindSwitches()
    }

    private fun requestBubble() {
        if (!canDrawOverlays()) {
            toast(getString(R.string.grant_overlay))
            askOverlay.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            bindSwitches()
            return
        }
        prefs.bubbleEnabled = true
        VerseService.sync(this)
        VerseScheduler.scheduleNext(this)
        bindSwitches()
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    /** Deep-links to the system's lock-screen notification settings where possible. */
    private fun openLockScreenSettings() {
        val candidates = listOf(
            // One UI keeps the "view style / show content" switches on its own screen.
            Intent("com.samsung.android.settings.LOCKSCREEN_NOTIFICATION"),
            Intent("android.settings.LOCK_SCREEN_NOTIFICATION_SETTINGS"),
            Intent("android.settings.NOTIFICATION_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        toast(getString(R.string.no_settings))
    }

    /**
     * Ask to be left alone by Doze first — that dialog is one tap and it is what actually
     * keeps the timer alive. Once we already have it, fall through to the app's own
     * settings page so the user can set Battery to Unrestricted.
     */
    private fun openBatterySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !ignoringBatteryOptimizations()) {
            val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            if (runCatching { startActivity(ask) }.isSuccess) return
        }
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(details) }
            .onFailure { toast(getString(R.string.no_settings)) }
    }

    /**
     * Vivo, Oppo, Xiaomi and friends keep a separate "auto-start" list, and an app that
     * is not on it gets force-stopped in the background — which cancels its alarms. There
     * is no API for it, only these ROM-specific screens.
     */
    private fun openAutostartSettings() {
        val candidates = listOf(
            // Vivo (Funtouch / OriginOS)
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
            ),
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            ),
            // Xiaomi (MIUI / HyperOS)
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // Oppo / Realme (ColorOS)
            ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            ),
            // Huawei (EMUI)
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            // Letv / OnePlus-style fallbacks
            ComponentName(
                "com.letv.android.letvsafe",
                "com.letv.android.letvsafe.AutobootManageActivity"
            )
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageManager.resolveActivity(intent, 0) == null) continue
            if (runCatching { startActivity(intent) }.isSuccess) return
        }
        toast(getString(R.string.no_autostart))
        openBatterySettings()
    }

    private fun ignoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val power = getSystemService(PowerManager::class.java) ?: return true
        return power.isIgnoringBatteryOptimizations(packageName)
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
