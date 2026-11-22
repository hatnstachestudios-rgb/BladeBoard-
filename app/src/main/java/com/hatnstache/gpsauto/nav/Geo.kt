package com.hatnstache.gpsauto.nav

import org.maplibre.android.geometry.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_R = 6_371_000.0

    fun distance(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val la1 = Math.toRadians(a.latitude)
        val la2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_R * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Initial compass bearing from a to b, 0..360. */
    fun bearing(a: LatLng, b: LatLng): Double {
        val la1 = Math.toRadians(a.latitude)
        val la2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun lerp(a: LatLng, b: LatLng, t: Double) =
        LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)

    /** Cumulative distance (m) at each vertex of a polyline. */
    fun cumulative(points: List<LatLng>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + distance(points[i - 1], points[i])
        return out
    }

    /** The point [along] meters into the polyline, plus the index of the segment it sits on. */
    fun pointAlong(points: List<LatLng>, cum: DoubleArray, along: Double): Pair<LatLng, Int> {
        if (points.size < 2) return (points.firstOrNull() ?: LatLng(0.0, 0.0)) to 0
        val d = along.coerceIn(0.0, cum.last())
        var i = 1
        while (i < cum.size - 1 && cum[i] < d) i++
        val segLen = cum[i] - cum[i - 1]
        val t = if (segLen > 0) (d - cum[i - 1]) / segLen else 0.0
        return lerp(points[i - 1], points[i], t) to (i - 1)
    }
}

/** Where a GPS fix lands on the route. */
data class Snap(val segment: Int, val along: Double, val offset: Double, val point: LatLng)

/**
 * Projects [p] onto the route, searching segments near [hint] first so a route that doubles back
 * doesn't make the position jump. Uses a flat-earth projection, which is accurate at these scales.
 */
fun snapToRoute(points: List<LatLng>, cum: DoubleArray, p: LatLng, hint: Int): Snap? {
    if (points.size < 2) return null
    val from = (hint - 5).coerceAtLeast(0)
    val to = (hint + 60).coerceAtMost(points.size - 2)
    var best = bestIn(points, cum, p, from, to)
    if (best == null || best.offset > 60) {
        val all = bestIn(points, cum, p, 0, points.size - 2)
        if (all != null && (best == null || all.offset < best.offset)) best = all
    }
    return best
}

private fun bestIn(points: List<LatLng>, cum: DoubleArray, p: LatLng, from: Int, to: Int): Snap? {
    if (from > to) return null
    val kx = 111_320.0 * cos(Math.toRadians(p.latitude))
    val ky = 110_540.0
    var best: Snap? = null
    for (i in from..to) {
        val a = points[i]
        val b = points[i + 1]
        val ax = (a.longitude - p.longitude) * kx
        val ay = (a.latitude - p.latitude) * ky
        val bx = (b.longitude - p.longitude) * kx
        val by = (b.latitude - p.latitude) * ky
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 > 0) (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0) else 0.0
        val cx = ax + dx * t
        val cy = ay + dy * t
        val off = sqrt(cx * cx + cy * cy)
        if (best == null || off < best.offset) {
            best = Snap(i, cum[i] + (cum[i + 1] - cum[i]) * t, off, Geo.lerp(a, b, t))
        }
    }
    return best
}

/** US units, matching how a nav voice talks. */
object Units {
    private const val M_PER_MILE = 1609.344
    private const val FT_PER_M = 3.28084

    /** Short label for banners: "450 ft", "0.3 mi", "12 mi". */
    fun distance(m: Double): String {
        val miles = m / M_PER_MILE
        return when {
            miles < 0.1 -> "${roundFeet(m)} ft"
            miles < 10 -> String.format("%.1f mi", miles)
            else -> "${miles.roundToInt()} mi"
        }
    }

    /** GTA-style readout with two decimals: "1.63 mi". */
    fun distancePrecise(m: Double): String {
        val miles = m / M_PER_MILE
        return if (miles < 100) String.format("%.2f mi", miles) else "${miles.roundToInt()} mi"
    }

    fun spoken(m: Double): String {
        val miles = m / M_PER_MILE
        return when {
            miles < 0.15 -> "${roundFeet(m)} feet"
            miles < 0.35 -> "a quarter mile"
            miles < 0.65 -> "half a mile"
            miles < 0.9 -> "three quarters of a mile"
            miles < 1.5 -> "1 mile"
            else -> "${miles.roundToInt()} miles"
        }
    }

    fun duration(seconds: Double): String {
        val min = (seconds / 60).roundToInt().coerceAtLeast(1)
        return if (min < 60) "$min min" else "${min / 60} hr ${min % 60} min"
    }

    private fun roundFeet(m: Double): Int {
        val ft = m * FT_PER_M
        return if (ft < 100) ((ft / 10).roundToInt() * 10).coerceAtLeast(10) else (ft / 50).roundToInt() * 50
    }
}
