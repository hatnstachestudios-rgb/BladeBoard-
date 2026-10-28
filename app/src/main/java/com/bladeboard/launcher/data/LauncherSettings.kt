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

enum class TabFinish { FLAT, METALLIC, GLOSS, SPARKLE }

/** A named color scheme: two background stops plus one accent per blade. */
data class HostedWidget(val id: Int, val heightDp: Int)

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
            listOf(Color(0xFF4CC38A), Color(0xFF3FA7D6), Color(0xFFF29E4C), Color(0xFFB86BD9), Color(0xFFE8505B)),
        ),
        Palette(
            "Ember", Color(0xFF1A0E0A), Color(0xFF4A1F12),
            listOf(Color(0xFFFF7A45), Color(0xFFFFC145), Color(0xFFE8505B), Color(0xFF9BC53D), Color(0xFF3FA7D6)),
        ),
        Palette(
            "Tide", Color(0xFF04161A), Color(0xFF0C3B44),
            listOf(Color(0xFF2EC4B6), Color(0xFF5BC0EB), Color(0xFFE9C46A), Color(0xFFF4A261), Color(0xFFE76F51)),
        ),
        Palette(
            "Neon", Color(0xFF0A0614), Color(0xFF241046),
            listOf(Color(0xFFFF4D9D), Color(0xFF7B5CFF), Color(0xFF00E0FF), Color(0xFFB6FF3B), Color(0xFFFFC145)),
        ),
        Palette(
            "Graphite", Color(0xFF121212), Color(0xFF2B2B2E),
            listOf(Color(0xFFE0E0E0), Color(0xFF9E9E9E), Color(0xFF64B5F6), Color(0xFFFFB74D), Color(0xFF81C784)),
        ),
    )
}

/** Extra colors the user can assign to any single blade. */
val AccentSwatches = listOf(
    Color(0xFF4CC38A), Color(0xFF2EC4B6), Color(0xFF3FA7D6), Color(0xFF5BC0EB),
    Color(0xFF7B5CFF), Color(0xFFB86BD9), Color(0xFFFF4D9D), Color(0xFFE8505B),
    Color(0xFFFF7A45), Color(0xFFF29E4C), Color(0xFFFFC145), Color(0xFFB6FF3B),
    Color(0xFFE0E0E0),
    // Added later; appended so saved swatch choices keep their meaning.
    Color(0xFFF7931E), Color(0xFFB8BCC2), Color(0xFF8A9099), Color(0xFF4A4F57), Color(0xFF1C1F24),
    Color(0xFF2B6CB0), Color(0xFF1B7F5B), Color(0xFF8E2DE2),
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
    var musicAppKey by stringPref("music_app", "")
    var appsCarousel by boolPref("apps_carousel", true)
    var showPlayerCard by boolPref("show_player_card", true)
    var autoUpdate by boolPref("auto_update", true)
    var colorfulQuickIcons by boolPref("colorful_quick_icons", true)
    var classicBlades by boolPref("classic_blades", true)
    /** -100..100: strength of the blade edge curve; negative bends the other way. */
    var curveStrength by intPref("curve_strength", 50)
    var pinnedIconSizeDp by intPref("pinned_icon_size", 52)
    var pinnedColumns by intPref("pinned_columns", 4)

    private var tabFinishName by stringPref("tab_finish", TabFinish.FLAT.name)
    var tabFinish: TabFinish
        get() = runCatching { TabFinish.valueOf(tabFinishName) }.getOrDefault(TabFinish.FLAT)
        set(value) { tabFinishName = value.name }

    // Widgets hosted on the Music blade, top to bottom. Stored as "id:heightDp|id:heightDp".
    var musicWidgets by mutableStateOf(
        prefs.getString("music_widgets", "").orEmpty().split('|').mapNotNull { entry ->
            val parts = entry.split(':')
            val id = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            HostedWidget(id, parts.getOrNull(1)?.toIntOrNull() ?: 180)
        }
    )
        private set

    private fun saveWidgets(list: List<HostedWidget>) {
        musicWidgets = list
        prefs.edit().putString("music_widgets", list.joinToString("|") { "${it.id}:${it.heightDp}" }).apply()
    }

    fun addWidget(id: Int, heightDp: Int) = saveWidgets(musicWidgets + HostedWidget(id, heightDp))
    fun removeWidget(id: Int) = saveWidgets(musicWidgets.filterNot { it.id == id })

    fun resizeWidget(id: Int, deltaDp: Int) = saveWidgets(
        musicWidgets.map { if (it.id == id) it.copy(heightDp = (it.heightDp + deltaDp).coerceIn(80, 640)) else it }
    )

    fun moveWidget(id: Int, direction: Int) {
        val i = musicWidgets.indexOfFirst { it.id == id }
        val j = i + direction
        if (i < 0 || j !in musicWidgets.indices) return
        saveWidgets(musicWidgets.toMutableList().also { java.util.Collections.swap(it, i, j) })
    }

    private var backgroundName by stringPref("background", BackgroundStyle.GRADIENT.name)
    var backgroundStyle: BackgroundStyle
        get() = runCatching { BackgroundStyle.valueOf(backgroundName) }.getOrDefault(BackgroundStyle.GRADIENT)
        set(value) { backgroundName = value.name }

    val palette: Palette get() = Palettes.all[paletteIndex.coerceIn(0, Palettes.all.lastIndex)]

    // The Clock blade took slot 4, so Style moved to 5: carry its saved colors over once.
    init {
        if (prefs.getInt("blade_layout", 1) < 2) {
            val e = prefs.edit()
            listOf("accent_", "panel_").forEach { k ->
                if (prefs.contains("${k}4")) e.putInt("${k}5", prefs.getInt("${k}4", -1)).remove("${k}4")
            }
            e.putInt("blade_layout", 2).commit()
        }
    }

    // Per-blade accent override: -1 means "use the palette's color".
    private val accentOverrides = List(MAX_BLADES) { i -> mutableIntStateOf(prefs.getInt("accent_$i", -1)) }

    fun accentOverride(blade: Int): Int = accentOverrides[blade].intValue

    fun setAccentOverride(blade: Int, swatch: Int) {
        accentOverrides[blade].intValue = swatch
        prefs.edit().putInt("accent_$blade", swatch).apply()
    }

    // Per-blade inner panel color: -1 means "same as the tab".
    private val panelOverrides = List(MAX_BLADES) { i -> mutableIntStateOf(prefs.getInt("panel_$i", -1)) }

    fun panelOverride(blade: Int): Int = panelOverrides[blade].intValue

    fun setPanelOverride(blade: Int, swatch: Int) {
        panelOverrides[blade].intValue = swatch
        prefs.edit().putInt("panel_$blade", swatch).apply()
    }

    fun panelFor(blade: Int): Color {
        val o = panelOverride(blade)
        return if (o in AccentSwatches.indices) AccentSwatches[o] else accentFor(blade)
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
        showLabels = true; use24h = false; bladeDepth = true; appsCarousel = true; classicBlades = true
        curveStrength = 50; pinnedIconSizeDp = 52; pinnedColumns = 4; tabFinish = TabFinish.FLAT
        backgroundStyle = BackgroundStyle.GRADIENT
        repeat(MAX_BLADES) { setAccentOverride(it, -1); setPanelOverride(it, -1) }
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
        const val MAX_BLADES = 6
    }
}
