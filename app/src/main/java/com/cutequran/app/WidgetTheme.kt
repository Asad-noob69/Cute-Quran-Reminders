package com.cutequran.app

import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView

/**
 * The looks a home-screen widget card can wear. Colours live here rather than in
 * colors.xml because RemoteViews can only be handed plain ints, and the same values
 * drive the in-app preview and the little picker chips.
 */
enum class WidgetTheme(
    val id: String,
    val label: Int,
    val background: Int,
    val buttonBackground: Int,
    /** Reference line, moon and shuffle icon. */
    val accent: Int,
    val arabicColor: Int,
    val englishColor: Int
) {
    COTTON(
        "cotton", R.string.theme_cotton,
        R.drawable.widget_bg_cotton, R.drawable.widget_btn_bg,
        accent = 0xFF7C5CE0.toInt(), arabicColor = 0xFF3D3355.toInt(),
        englishColor = 0xFF6B6188.toInt()
    ),
    GLASS(
        "glass", R.string.theme_glass,
        R.drawable.widget_bg_glass, R.drawable.widget_btn_glass,
        accent = 0xFF5B41B8.toInt(), arabicColor = 0xFF241E38.toInt(),
        englishColor = 0xFF3F3760.toInt()
    ),
    GLASS_DARK(
        "glass_dark", R.string.theme_glass_dark,
        R.drawable.widget_bg_glass_dark, R.drawable.widget_btn_dark,
        accent = 0xFFD8CBFF.toInt(), arabicColor = 0xFFFFFFFF.toInt(),
        englishColor = 0xFFDCD6EC.toInt()
    ),
    MIDNIGHT(
        "midnight", R.string.theme_midnight,
        R.drawable.widget_bg_midnight, R.drawable.widget_btn_dark,
        accent = 0xFFB69CFF.toInt(), arabicColor = 0xFFF6F3FF.toInt(),
        englishColor = 0xFFC8C0E4.toInt()
    ),
    SUNSET(
        "sunset", R.string.theme_sunset,
        R.drawable.widget_bg_sunset, R.drawable.widget_btn_ink,
        accent = 0xFFB4406B.toInt(), arabicColor = 0xFF4A2436.toInt(),
        englishColor = 0xFF75455B.toInt()
    ),
    MINT(
        "mint", R.string.theme_mint,
        R.drawable.widget_bg_mint, R.drawable.widget_btn_ink,
        accent = 0xFF2F8F7A.toInt(), arabicColor = 0xFF1F3D3A.toInt(),
        englishColor = 0xFF4C6A67.toInt()
    ),
    PAPER(
        "paper", R.string.theme_paper,
        R.drawable.widget_bg_paper, R.drawable.widget_btn_ink,
        accent = 0xFF7C5CE0.toInt(), arabicColor = 0xFF3D3355.toInt(),
        englishColor = 0xFF6B6188.toInt()
    ),
    NEON(
        "neon", R.string.theme_neon,
        R.drawable.widget_bg_neon, R.drawable.widget_btn_neon,
        accent = 0xFF63E6FF.toInt(), arabicColor = 0xFFEAFBFF.toInt(),
        englishColor = 0xFFA9C9E8.toInt()
    );

    /** Dress a widget card that is about to be pushed to the launcher. */
    fun applyTo(views: RemoteViews) {
        views.setInt(R.id.widget_root, "setBackgroundResource", background)
        views.setInt(R.id.widget_shuffle, "setBackgroundResource", buttonBackground)
        views.setInt(R.id.widget_shuffle, "setColorFilter", accent)
        views.setInt(R.id.widget_icon, "setColorFilter", accent)
        views.setTextColor(R.id.widget_reference, accent)
        views.setTextColor(R.id.widget_arabic, arabicColor)
        views.setTextColor(R.id.widget_english, englishColor)
    }

    /** Dress the live preview inside the app, which inflates the very same layout. */
    fun applyTo(card: View) {
        card.setBackgroundResource(background)
        card.findViewById<ImageView>(R.id.widget_shuffle).apply {
            setBackgroundResource(buttonBackground)
            setColorFilter(accent)
        }
        card.findViewById<ImageView>(R.id.widget_icon).setColorFilter(accent)
        card.findViewById<TextView>(R.id.widget_reference).setTextColor(accent)
        card.findViewById<TextView>(R.id.widget_arabic).setTextColor(arabicColor)
        card.findViewById<TextView>(R.id.widget_english).setTextColor(englishColor)
    }

    companion object {
        val DEFAULT = COTTON

        fun from(id: String?): WidgetTheme = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}
