package com.hatnstache.gpsauto.map

import com.hatnstache.gpsauto.data.Settings
import com.hatnstache.gpsauto.data.ThemeMode
import com.hatnstache.gpsauto.nav.NavigationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Light map by day, dark map by night, switching at local sunset/sunrise like the game's
 * minimap does with time of day. Can be forced either way in Settings.
 */
object MapTheme {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _night = MutableStateFlow(false)
    val night: StateFlow<Boolean> = _night

    fun init() {
        scope.launch { Settings.theme.collect { recompute() } }
        scope.launch {
            while (true) {
                recompute()
                delay(60_000)
            }
        }
        // Get it right as soon as the first GPS fix arrives.
        scope.launch {
            NavigationEngine.location.collect { if (it != null && lastLat == null) recompute() }
        }
    }

    private var lastLat: Double? = null

    private fun recompute() {
        _night.value = when (Settings.theme.value) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> {
                val loc = NavigationEngine.location.value
                if (loc == null) {
                    val h = ZonedDateTime.now().hour
                    h < 7 || h >= 19
                } else {
                    lastLat = loc.latitude
                    Sun.isNight(loc.latitude, loc.longitude, ZonedDateTime.now(ZoneOffset.UTC))
                }
            }
        }
    }
}

/** NOAA sunrise/sunset approximation, good to a few minutes. */
object Sun {
    private const val ZENITH = 90.833

    fun isNight(lat: Double, lon: Double, utc: ZonedDateTime): Boolean {
        val now = utc.hour + utc.minute / 60.0
        val rise = event(lat, lon, utc.dayOfYear, rising = true)
        val set = event(lat, lon, utc.dayOfYear, rising = false)
        return when {
            rise == null && set == null -> polarNight(lat, utc.dayOfYear)
            rise == null || set == null -> false
            rise < set -> now < rise || now > set
            else -> now > set && now < rise
        }
    }

    private fun polarNight(lat: Double, day: Int): Boolean {
        val northernWinter = day < 80 || day > 264
        return if (lat > 0) northernWinter else !northernWinter
    }

    /** Sunrise or sunset as a UTC hour, or null if the sun doesn't cross the horizon today. */
    private fun event(lat: Double, lon: Double, day: Int, rising: Boolean): Double? {
        val lngHour = lon / 15.0
        val t = day + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634
        l = norm(l, 360.0)
        var ra = norm(Math.toDegrees(atan(0.91764 * tanD(l))), 360.0)
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15.0
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(ZENITH) - sinDec * sinD(lat)) / (cosDec * cosD(lat))
        if (cosH > 1 || cosH < -1) return null
        var h = if (rising) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
        h /= 15.0
        val localT = h + ra - 0.06571 * t - 6.622
        return norm(localT - lngHour, 24.0)
    }

    private fun norm(v: Double, m: Double) = ((v % m) + m) % m
    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
}
