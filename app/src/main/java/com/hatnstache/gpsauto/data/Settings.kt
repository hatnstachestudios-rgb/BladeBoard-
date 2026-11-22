package com.hatnstache.gpsauto.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode(val label: String) { AUTO("Auto (sunset)"), LIGHT("Light"), DARK("Dark") }

/** User options, persisted and observable from every screen. */
object Settings {
    private lateinit var prefs: SharedPreferences

    private val _theme = MutableStateFlow(ThemeMode.AUTO)
    val theme: StateFlow<ThemeMode> = _theme

    /** Floating minimap fades away when you're parked with no route, like the game's dynamic HUD. */
    private val _dynamicOverlay = MutableStateFlow(true)
    val dynamicOverlay: StateFlow<Boolean> = _dynamicOverlay

    /** Street names on the full-screen map. The minimaps never show them, like the game. */
    private val _streetLabels = MutableStateFlow(true)
    val streetLabels: StateFlow<Boolean> = _streetLabels

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _theme.value = runCatching { ThemeMode.valueOf(prefs.getString("theme", "AUTO")!!) }.getOrDefault(ThemeMode.AUTO)
        _dynamicOverlay.value = prefs.getBoolean("dynamic_overlay", true)
        _streetLabels.value = prefs.getBoolean("street_labels", true)
    }

    fun setTheme(t: ThemeMode) { _theme.value = t; prefs.edit().putString("theme", t.name).apply() }
    fun setDynamicOverlay(on: Boolean) { _dynamicOverlay.value = on; prefs.edit().putBoolean("dynamic_overlay", on).apply() }
    fun setStreetLabels(on: Boolean) { _streetLabels.value = on; prefs.edit().putBoolean("street_labels", on).apply() }
}
