package com.example.kotozvonok

import android.content.Context

object Prefs {
    private const val FILE = "koto_prefs"
    private const val KEY_FULLSCREEN = "fullscreen_calls"
    private const val KEY_CATS_ENABLED = "cats_enabled"
    private const val KEY_CUSTOM_CATS = "custom_cats"

    // Ключи настраиваемых иконок интерфейса
    val ICON_KEYS = listOf("answer", "decline", "search", "tab_dialer", "tab_contacts", "tab_calls", "tab_settings")

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
}
