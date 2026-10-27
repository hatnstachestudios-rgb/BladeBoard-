package com.bladeboard.launcher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

enum class BackgroundStyle { GRADIENT, WALLPAPER, SOLID }

/** A named color scheme: two background stops plus one accent per blade. */
data class Palette(
    val name: String,
    val bgTop: Color,
    val bgBottom: Color,
    val blades: List<Color>,
)

object Palettes {
    val all = listOf(
        Palette(
            "Aurora", Color(0xFF0B1A2B), Color(0xFF1E3A5F),
            listOf(Color(0xFF4CC38A), Color(0xFF3FA7D6), Color(0xFFF29E4C), Color(0xFFB86BD9)),
        ),
        Palette(
            "Ember", Color(0xFF1A0E0A), Color(0xFF4A1F12),
            listOf(Color(0xFFFF7A45), Color(0xFFFFC145), Color(0xFFE8505B), Color(0xFF9BC53D)),
        ),
        Palette(
            "Tide", Color(0xFF04161A), Color(0xFF0C3B44),
            listOf(Color(0xFF2EC4B6), Color(0xFF5BC0EB), Color(0xFFE9C46A), Color(0xFFF4A261)),
        ),
        Palette(
            "Neon", Color(0xFF0A0614), Color(0xFF241046),
            listOf(Color(0xFFFF4D9D), Color(0xFF7B5CFF), Color(0xFF00E0FF), Color(0xFFB6FF3B)),
        ),
        Palette(
            "Graphite", Color(0xFF121212), Color(0xFF2B2B2E),
            listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF64B5F6), Color(0xFFFFB74D)),
        ),
    )
}

/** Extra colors the user can assign to any single blade. */
val AccentSwatches = listOf(
    Color(0xFF4CC38A), Color(0xFF2EC4B6), Color(0xFF3FA7D6), Color(0xFF5BC0EB),
    Color(0xFF7B5CFF), Color(0xFFB86BD9), Color(0xFFFF4D9D), Color(0xFFE8505B),
    Color(0xFFFF7A45), Color(0xFFF29E4C), Color(0xFFFFC145), Color(0xFFB6FF3B),
    Color(0xFFE0E0E0),
)

/**
 * Every customization option, persisted in SharedPreferences and exposed as Compose state
 * so the UI updates the moment a value changes.
 */
class LauncherSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bladeboard", Context.MODE_PRIVATE)

    var paletteIndex by intPref("palette", 0)
    var iconSizeDp by intPref("icon_size", 52)
    var gridColumns by intPref("grid_columns", 4)
    var tabWidthDp by intPref("tab_width", 34)
    var showLabels by boolPref("show_labels", true)
    var use24h by boolPref("use_24h", false)
    var bladeDepth by boolPref("blade_depth", true)

    private var backgroundName by stringPref("background", BackgroundStyle.GRADIENT.name)
    var backgroundStyle: BackgroundStyle
        get() = runCatching { BackgroundStyle.valueOf(backgroundName) }.getOrDefault(BackgroundStyle.GRADIENT)
        set(value) { backgroundName = value.name }

    val palette: Palette get() = Palettes.all[paletteIndex.coerceIn(0, Palettes.all.lastIndex)]

    // Per-blade accent override: -1 means "use the palette's color".
    private val accentOverrides = List(MAX_BLADES) { i -> mutableIntStateOf(prefs.getInt("accent_$i", -1)) }

    fun accentOverride(blade: Int): Int = accentOverrides[blade].intValue

    fun setAccentOverride(blade: Int, swatch: Int) {
        accentOverrides[blade].intValue = swatch
        prefs.edit().putInt("accent_$blade", swatch).apply()
    }

    fun accentFor(blade: Int): Color {
        val o = accentOverride(blade)
        return if (o in AccentSwatches.indices) AccentSwatches[o] else palette.blades[blade % palette.blades.size]
    }

    // Pinned apps on the Home blade, in pin order. Keys are flattened ComponentNames.
    var favorites by mutableStateOf(
        prefs.getString("favorites", "").orEmpty().split('|').filter { it.isNotEmpty() }
    )
        private set

    fun isFavorite(key: String) = key in favorites

    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.edit().putString("favorites", favorites.joinToString("|")).apply()
    }

    fun resetLook() {
        paletteIndex = 0; iconSizeDp = 52; gridColumns = 4; tabWidthDp = 34
        showLabels = true; use24h = false; bladeDepth = true
        backgroundStyle = BackgroundStyle.GRADIENT
        repeat(MAX_BLADES) { setAccentOverride(it, -1) }
    }

    // --- tiny persisted-state delegates ---

    private fun intPref(key: String, default: Int) = object : ReadWriteProperty<Any?, Int> {
        val state = mutableIntStateOf(prefs.getInt(key, default))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.intValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            state.intValue = value
            prefs.edit().putInt(key, value).apply()
        }
    }

    private fun boolPref(key: String, default: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        val state = mutableStateOf(prefs.getBoolean(key, default))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            state.value = value
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    private fun stringPref(key: String, default: String) = object : ReadWriteProperty<Any?, String> {
        val state = mutableStateOf(prefs.getString(key, default) ?: default)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            state.value = value
            prefs.edit().putString(key, value).apply()
        }
    }

    companion object {
        const val MAX_BLADES = 4
    }
}
