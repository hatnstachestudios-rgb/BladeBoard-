package com.hatnstache.gpsauto.nav

import android.content.Context
import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

data class Destination(val name: String, val point: LatLng)

data class NavProgress(
    val nextStep: RouteStep?,
    val distanceToNext: Double,
    val remaining: Double,
    val remainingSeconds: Double,
    val along: Double,
    val snapped: LatLng?,
    val arrived: Boolean,
)

/**
 * The single source of truth for navigation. The phone UI, the floating minimap and the
 * Android Auto screen all watch these flows, so they always agree.
 */
object NavigationEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private lateinit var voice: VoiceGuide

    private val _location = MutableStateFlow<Location?>(null)
    val location: StateFlow<Location?> = _location

    /** Direction of travel used to rotate the camera and the arrow. */
    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route

    private val _destination = MutableStateFlow<Destination?>(null)
    val destination: StateFlow<Destination?> = _destination

    private val _progress = MutableStateFlow<NavProgress?>(null)
    val progress: StateFlow<NavProgress?> = _progress

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted

    val isNavigating: Boolean get() = _destination.value != null

    private var routeJob: Job? = null
    private var lastSegment = 0
    private var offRouteCount = 0
    private var lastRerouteAt = 0L
    private val announced = mutableSetOf<String>()
    private var arrivedAnnounced = false

    fun init(context: Context) {
        app = context.applicationContext
        voice = VoiceGuide(app)
    }

    fun navigateTo(name: String, point: LatLng) {
        _destination.value = Destination(name, point)
        _error.value = null
        arrivedAnnounced = false
        NavService.startNavigation(app)
        requestRoute(firstTime = true)
    }

    fun stop() {
        routeJob?.cancel()
        _destination.value = null
        _route.value = null
        _progress.value = null
        _loading.value = false
        announced.clear()
        voice.stop()
        NavService.stopNavigation(app)
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        if (muted) voice.stop()
    }

    fun clearError() { _error.value = null }

    fun retry() {
        _error.value = null
        requestRoute(firstTime = _route.value == null)
    }

    private fun requestRoute(firstTime: Boolean) {
        val dest = _destination.value ?: return
        val here = _location.value
        if (here == null) {
            // No fix yet: the first location update will retry.
            _loading.value = true
            return
        }
        routeJob?.cancel()
        _loading.value = true
        lastRerouteAt = SystemClock.elapsedRealtime()
        routeJob = scope.launch {
            try {
                val heading = if (here.hasBearing() && here.speed > 2f) here.bearing else null
                val r = Osrm.route(LatLng(here.latitude, here.longitude), dest.point, heading)
                _route.value = r
                lastSegment = 0
                offRouteCount = 0
                announced.clear()
                _error.value = null
                updateProgress(here)
                if (firstTime) {
                    val first = r.steps.firstOrNull { it.type != "depart" }
                    val p = _progress.value
                    if (first != null && p != null) {
                        say("Starting route to ${dest.name}. In ${Units.spoken(p.distanceToNext)}, ${first.instruction.lowerFirst()}")
                    } else say("Starting route to ${dest.name}")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Couldn't get a route. Check your connection."
            } finally {
                _loading.value = false
            }
        }
    }

    fun onLocation(loc: Location) {
        _location.value = loc
        if (loc.hasBearing() && loc.speed > 1.5f) _heading.value = loc.bearing

        if (_destination.value != null && _route.value == null && routeJob?.isActive != true && _error.value == null) {
            requestRoute(firstTime = true)
            return
        }
        updateProgress(loc)
    }

    private fun updateProgress(loc: Location) {
        val r = _route.value ?: return
        val here = LatLng(loc.latitude, loc.longitude)
        val snap = snapToRoute(r.points, r.cumulative, here, lastSegment) ?: return
        lastSegment = snap.segment

        // When stopped, aim the arrow along the road instead of at GPS noise.
        if (!(loc.hasBearing() && loc.speed > 1.5f) && snap.segment + 1 < r.points.size) {
            _heading.value = Geo.bearing(r.points[snap.segment], r.points[snap.segment + 1]).toFloat()
        }

        // Off-route detection: several bad fixes in a row, then fetch a new route.
        val accurate = !loc.hasAccuracy() || loc.accuracy < 50f
        offRouteCount = if (snap.offset > 50 && accurate) offRouteCount + 1 else 0
        if (offRouteCount >= 3 && SystemClock.elapsedRealtime() - lastRerouteAt > 8_000) {
            offRouteCount = 0
            say("Rerouting")
            requestRoute(firstTime = false)
        }

        val remaining = (r.length - snap.along).coerceAtLeast(0.0)
        val next = r.steps.firstOrNull { it.startAlong > snap.along + 3 && it.type != "depart" }
            ?: r.steps.lastOrNull()
        val toNext = next?.let { (it.startAlong - snap.along).coerceAtLeast(0.0) } ?: remaining
        val arrived = remaining < 30
        val ratio = if (r.length > 0) remaining / r.length else 0.0
        _progress.value = NavProgress(
            nextStep = next,
            distanceToNext = toNext,
            remaining = remaining,
            remainingSeconds = r.duration * ratio,
            along = snap.along,
            snapped = snap.point,
            arrived = arrived,
        )

        if (arrived) {
            if (!arrivedAnnounced) {
                arrivedAnnounced = true
                say("You have arrived")
                scope.launch {
                    delay(15_000)
                    if (_progress.value?.arrived == true) stop()
                }
            }
            return
        }
        if (next != null) announce(r.steps.indexOf(next), next, toNext, loc.speed)
    }

    /** Speaks each upcoming turn a few times as it approaches: far, near, and "now". */
    private fun announce(index: Int, step: RouteStep, distance: Double, speed: Float) {
        val thresholds = if (speed > 22f) listOf(2000.0, 800.0, 300.0, 80.0) else listOf(800.0, 250.0, 45.0)
        val hit = thresholds.lastOrNull { distance <= it } ?: return
        val key = "$index@$hit"
        if (key in announced) return
        // Mark this and every farther threshold so we never announce out of order.
        thresholds.filter { it >= hit }.forEach { announced += "$index@$it" }
        val text = if (hit == thresholds.last()) step.instruction
        else "In ${Units.spoken(distance)}, ${step.instruction.lowerFirst()}"
        say(text)
    }

    private fun say(text: String) {
        if (!_muted.value) voice.speak(text)
    }

    private fun String.lowerFirst() = replaceFirstChar { it.lowercase() }
}
