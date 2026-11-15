package com.example.kotozvonok

import android.content.Context

object Prefs {
    private const val FILE = "koto_prefs"
    private const val KEY_FULLSCREEN = "fullscreen_calls"
    private const val KEY_CATS_ENABLED = "cats_enabled"
    private const val KEY_CUSTOM_CATS = "custom_cats"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_FONT = "font_family"
    private const val KEY_OVERLAY_SCALE = "overlay_scale"
    private const val KEY_RINGTONE = "ringtone_uri"
    private const val KEY_COLLAPSE_TO_ISLAND = "collapse_to_island"

    // Ключи настраиваемых иконок интерфейса
    val ICON_KEYS = listOf("answer", "decline", "search", "tab_home", "tab_dialer")

    // Готовые цветовые темы: имя -> цвет
    val ACCENT_PRESETS = linkedMapOf(
        "Синий" to 0xFF4F6DF5.toInt(),
        "Фиолетовый" to 0xFF9C6DF5.toInt(),
        "Зелёный" to 0xFF2FBF71.toInt(),
        "Розовый" to 0xFFE55C9C.toInt(),
        "Оранжевый" to 0xFFE89A3C.toInt()
    )

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isFullScreenEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_FULLSCREEN, false)
    fun setFullScreenEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FULLSCREEN, enabled).apply()
    }

    fun isCatsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_CATS_ENABLED, true)
    fun setCatsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CATS_ENABLED, enabled).apply()
    }

    fun getCustomCats(context: Context): Set<String> = prefs(context).getStringSet(KEY_CUSTOM_CATS, emptySet()) ?: emptySet()
    fun addCustomCat(context: Context, uri: String) {
        val set = getCustomCats(context).toMutableSet()
        set.add(uri)
        prefs(context).edit().putStringSet(KEY_CUSTOM_CATS, set).apply()
    }
    fun clearCustomCats(context: Context) {
        prefs(context).edit().putStringSet(KEY_CUSTOM_CATS, emptySet()).apply()
    }

    fun getIcon(context: Context, key: String): String? = prefs(context).getString("icon_$key", null)
    fun setIcon(context: Context, key: String, uri: String?) {
        prefs(context).edit().putString("icon_$key", uri).apply()
    }
    fun clearAllIcons(context: Context) {
        val e = prefs(context).edit()
        ICON_KEYS.forEach { e.remove("icon_$it") }
        e.apply()
    }

    fun accentColor(context: Context): Int = prefs(context).getInt(KEY_ACCENT, ACCENT_PRESETS.values.first())
    fun setAccentColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_ACCENT, color).apply()
    }

    // "default" | "sans" | "serif" | "mono"
    fun fontFamily(context: Context): String = prefs(context).getString(KEY_FONT, "default") ?: "default"
    fun setFontFamily(context: Context, family: String) {
        prefs(context).edit().putString(KEY_FONT, family).apply()
    }

    // 0.85 / 1.0 / 1.25
    fun overlayScale(context: Context): Float = prefs(context).getFloat(KEY_OVERLAY_SCALE, 1.0f)
    fun setOverlayScale(context: Context, scale: Float) {
        prefs(context).edit().putFloat(KEY_OVERLAY_SCALE, scale).apply()
    }

    fun ringtoneUri(context: Context): String? = prefs(context).getString(KEY_RINGTONE, null)
    fun setRingtoneUri(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_RINGTONE, uri).apply()
    }

    fun collapseToIslandOnAnswer(context: Context): Boolean = prefs(context).getBoolean(KEY_COLLAPSE_TO_ISLAND, true)
    fun setCollapseToIslandOnAnswer(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_COLLAPSE_TO_ISLAND, enabled).apply()
    }
}
