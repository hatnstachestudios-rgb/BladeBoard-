package com.hatnstache.gpsauto.nav

import com.hatnstache.gpsauto.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One maneuver. It happens at [startAlong] meters into the route. */
data class RouteStep(
    val type: String,
    val modifier: String?,
    val name: String,
    val exit: Int?,
    val location: LatLng,
    val distance: Double,
    val startAlong: Double,
) {
    val instruction: String get() = Instructions.text(this)
}

class Route(
    val points: List<LatLng>,
    val distance: Double,
    val duration: Double,
    val steps: List<RouteStep>,
) {
    val cumulative: DoubleArray = Geo.cumulative(points)
    val length: Double get() = cumulative.lastOrNull() ?: 0.0
}

data class SearchResult(val title: String, val subtitle: String, val lat: Double, val lon: Double)

private fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 15_000
    conn.setRequestProperty("User-Agent", "GPSAuto/0.1 (personal Android navigation app)")
    try {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw RuntimeException("HTTP $code")
        return body
    } finally {
        conn.disconnect()
    }
}

/** Driving routes from the public OSRM server (OpenStreetMap data, no key needed). */
object Osrm {
    suspend fun route(from: LatLng, to: LatLng, headingDeg: Float? = null): Route = withContext(Dispatchers.IO) {
        val coords = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
        val bearings = headingDeg?.let { "&bearings=${it.toInt().mod(360)},60;" } ?: ""
        val url = "https://router.project-osrm.org/route/v1/driving/$coords" +
            "?overview=full&geometries=geojson&steps=true$bearings"
        val json = JSONObject(httpGet(url))
        if (json.optString("code") != "Ok") throw RuntimeException(json.optString("message", "No route found"))
        val r = json.getJSONArray("routes").getJSONObject(0)

        val coordsArr = r.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coordsArr.length()).map { i ->
            val c = coordsArr.getJSONArray(i)
            LatLng(c.getDouble(1), c.getDouble(0))
        }

        val steps = mutableListOf<RouteStep>()
        var along = 0.0
        val legs = r.getJSONArray("legs")
        for (l in 0 until legs.length()) {
            val arr = legs.getJSONObject(l).getJSONArray("steps")
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val m = s.getJSONObject("maneuver")
                val loc = m.getJSONArray("location")
                val name = s.optString("name").ifBlank { s.optString("ref") }
                val dist = s.optDouble("distance", 0.0)
                steps += RouteStep(
                    type = m.optString("type"),
                    modifier = m.optString("modifier").ifBlank { null },
                    name = name,
                    exit = if (m.has("exit")) m.optInt("exit") else null,
                    location = LatLng(loc.getDouble(1), loc.getDouble(0)),
                    distance = dist,
                    startAlong = along,
                )
                along += dist
            }
        }
        Route(points, r.optDouble("distance"), r.optDouble("duration"), steps)
    }
}

/** Place and address search from Photon (OpenStreetMap data, no key needed). */
object PlaceSearch {
    suspend fun search(query: String, near: LatLng?): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val bias = near?.let { "&lat=${it.latitude}&lon=${it.longitude}" } ?: ""
        val json = JSONObject(httpGet("https://photon.komoot.io/api/?q=$q&limit=10&lang=en$bias"))
        val features = json.getJSONArray("features")
        (0 until features.length()).mapNotNull { i ->
            val f = features.getJSONObject(i)
            val c = f.getJSONObject("geometry").getJSONArray("coordinates")
            val p = f.getJSONObject("properties")
            val street = listOf(p.optString("housenumber"), p.optString("street")).filter { it.isNotBlank() }.joinToString(" ")
            val name = p.optString("name")
            val title = name.ifBlank { street }.ifBlank { return@mapNotNull null }
            val subtitle = listOf(
                if (name.isNotBlank()) street else "",
                p.optString("city"), p.optString("state"),
            ).filter { it.isNotBlank() }.distinct().joinToString(", ")
            SearchResult(title, subtitle, c.getDouble(1), c.getDouble(0))
        }
    }
}

/** Turns OSRM maneuvers into spoken/printed instructions and icons. */
object Instructions {
    fun text(s: RouteStep): String {
        val onto = if (s.name.isNotBlank()) " onto ${s.name}" else ""
        val m = s.modifier ?: ""
        return when (s.type) {
            "depart" -> if (s.name.isNotBlank()) "Head out on ${s.name}" else "Start driving"
            "arrive" -> "Arrive at your destination"
            "merge" -> "Merge$onto"
            "on ramp" -> "Take the ramp$onto"
            "off ramp" -> "Take the exit" + if (s.name.isNotBlank()) " toward ${s.name}" else ""
            "fork" -> "Keep ${side(m)}$onto"
            "roundabout", "rotary", "roundabout turn" ->
                "At the roundabout, take the ${ordinal(s.exit ?: 1)} exit$onto"
            "exit roundabout", "exit rotary" -> "Exit the roundabout$onto"
            else -> when {
                m == "uturn" -> "Make a U-turn"
                m == "straight" || m.isBlank() -> "Continue$onto"
                m.startsWith("slight") -> "Bear ${side(m)}$onto"
                m.startsWith("sharp") -> "Turn sharp ${side(m)}$onto"
                else -> "Turn ${side(m)}$onto"
            }
        }
    }

    /** Big line on the turn card: the street if we have one. */
    fun headline(s: RouteStep): String = when {
        s.type == "arrive" -> "Destination"
        s.name.isNotBlank() -> s.name
        else -> text(s)
    }

    fun iconRes(s: RouteStep): Int {
        val m = s.modifier ?: ""
        return when (s.type) {
            "arrive" -> R.drawable.man_arrive
            "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> R.drawable.man_roundabout
            "merge" -> R.drawable.man_merge
            else -> when (m) {
                "uturn" -> R.drawable.man_uturn
                "sharp right" -> R.drawable.man_sharp_right
                "right" -> R.drawable.man_right
                "slight right" -> R.drawable.man_slight_right
                "slight left" -> R.drawable.man_slight_left
                "left" -> R.drawable.man_left
                "sharp left" -> R.drawable.man_sharp_left
                else -> R.drawable.man_straight
            }
        }
    }

    private fun side(m: String) = if (m.contains("left")) "left" else "right"

    private fun ordinal(n: Int) = when (n) {
        1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"
        else -> "${n}th"
    }
}
