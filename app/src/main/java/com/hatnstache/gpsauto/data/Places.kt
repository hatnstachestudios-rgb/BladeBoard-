package com.hatnstache.gpsauto.data

import android.content.Context
import android.content.SharedPreferences
import com.hatnstache.gpsauto.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** The icon drawn inside a saved place's blip. */
enum class BlipIcon(val res: Int, val label: String) {
    HOME(R.drawable.blip_home, "Home"),
    WORK(R.drawable.blip_work, "Work"),
    GYM(R.drawable.blip_gym, "Gym"),
    FRIEND(R.drawable.blip_friend, "Friend"),
    FOOD(R.drawable.blip_food, "Food"),
    GAS(R.drawable.blip_gas, "Gas"),
    SHOP(R.drawable.blip_shop, "Shop"),
    STAR(R.drawable.blip_star, "Favorite"),
}

/** Blip fill colors. Each pairs with a white glyph, so they all keep enough contrast. */
val BlipColors: List<Int> = listOf(
    0xFF1F9D55, // green
    0xFF2563EB, // blue
    0xFFD97706, // orange
    0xFF7C3AED, // purple
    0xFFDC2626, // red
    0xFF374151, // graphite
    0xFF0E7490, // teal
    0xFFBE185D, // magenta
).map { it.toInt() }

fun defaultColorFor(icon: BlipIcon): Int = when (icon) {
    BlipIcon.HOME -> BlipColors[0]
    BlipIcon.WORK -> BlipColors[1]
    BlipIcon.GYM -> BlipColors[2]
    BlipIcon.FRIEND -> BlipColors[3]
    BlipIcon.FOOD -> BlipColors[4]
    BlipIcon.GAS -> BlipColors[5]
    BlipIcon.SHOP -> BlipColors[6]
    BlipIcon.STAR -> BlipColors[7]
}

data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val icon: BlipIcon,
    val color: Int,
)

/** Saved places, kept in SharedPreferences as JSON and exposed as a flow every screen can watch. */
object PlaceStore {
    private lateinit var prefs: SharedPreferences
    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places

    fun init(context: Context) {
        prefs = context.getSharedPreferences("places", Context.MODE_PRIVATE)
        _places.value = load()
    }

    fun add(name: String, lat: Double, lon: Double, icon: BlipIcon, color: Int) {
        val place = Place(UUID.randomUUID().toString(), name.trim().ifBlank { icon.label }, lat, lon, icon, color)
        save(_places.value + place)
    }

    fun remove(id: String) = save(_places.value.filterNot { it.id == id })

    private fun save(list: List<Place>) {
        _places.value = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name)
                    .put("lat", p.lat).put("lon", p.lon)
                    .put("icon", p.icon.name).put("color", p.color)
            )
        }
        prefs.edit().putString("list", arr.toString()).apply()
    }

    private fun load(): List<Place> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val icon = runCatching { BlipIcon.valueOf(o.getString("icon")) }.getOrDefault(BlipIcon.STAR)
                Place(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    icon = icon,
                    color = o.optInt("color", defaultColorFor(icon)),
                )
            }
        }.getOrDefault(emptyList())
    }
}
