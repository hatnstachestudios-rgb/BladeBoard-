package com.hatnstache.gpsauto.map

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import com.hatnstache.gpsauto.data.Settings
import com.hatnstache.gpsauto.nav.Geo
import com.hatnstache.gpsauto.nav.NavigationEngine
import com.hatnstache.gpsauto.nav.Route
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Drives one map (phone screen, floating minimap, or car screen): the game-style look, the
 * pink route, the player arrow, and the camera modes.
 */
class GtaMap(
    private val context: Context,
    val mapView: MapView,
    private val blips: BlipOverlayView?,
    /** Floating minimap: closer, no gestures. */
    private val compact: Boolean = false,
) {
    enum class Mode { FOLLOW, FREE, PREVIEW, OVERVIEW }

    var map: MapLibreMap? = null
        private set
    var mode = Mode.FOLLOW
        private set(value) {
            if (field != value) {
                field = value
                onModeChanged?.invoke(value)
            }
        }
    var onModeChanged: ((Mode) -> Unit)? = null
    var onReady: ((MapLibreMap) -> Unit)? = null

    /** Parts of the view covered by other UI (car templates, phone cards). */
    var insets = Rect()
        set(value) {
            field = value
            blips?.visibleInsets = value
        }

    /** How far down the screen the player sits in follow mode (0 = center, 1 = bottom edge). */
    var followBias = 0.55f

    private var style: Style? = null
    private var night = false
    private var labels = true
    private var routeColor = ROUTE_PINK
    private var routeSource: GeoJsonSource? = null
    private var meSource: GeoJsonSource? = null
    private var styleReady = false
    private var pendingRoute: List<LatLng>? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoRecenter = Runnable { if (mode == Mode.FREE || mode == Mode.OVERVIEW) recenter() }

    init {
        mapView.getMapAsync { m -> setup(m) }
    }

    private fun setup(m: MapLibreMap) {
        map = m
        blips?.map = m
        m.uiSettings.apply {
            isCompassEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = !compact
            attributionGravity = Gravity.BOTTOM or Gravity.END
            if (compact) setAllGesturesEnabled(false)
        }
        m.setMinZoomPreference(3.0)
        m.setMaxZoomPreference(19.5)
        m.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(LatLng(39.5, -98.35)).zoom(3.5).build()
            )
        )

        val json = context.assets.open("gta_style.json").bufferedReader().use { it.readText() }
        m.setStyle(Style.Builder().fromJson(json)) { style -> onStyle(style) }

        m.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) userMoved()
        }
        m.addOnCameraMoveListener { blips?.invalidate() }
        m.addOnCameraIdleListener {
            blips?.invalidate()
            if (mode == Mode.FREE) flattenForZoom()
        }
        onReady?.invoke(m)
    }

    private fun onStyle(style: Style) {
        val density = context.resources.displayMetrics.density
        style.addImage("me", Bitmaps.playerArrow(((if (compact) 30 else 44) * density).toInt()))

        val route = GeoJsonSource("route")
        style.addSource(route)
        routeSource = route
        val pink = hex(routeColor)
        val glow = LineLayer("route-glow", "route").withProperties(
            PropertyFactory.lineColor(pink),
            PropertyFactory.lineWidth(if (compact) 12f else 20f),
            PropertyFactory.lineBlur(if (compact) 5f else 8f),
            PropertyFactory.lineOpacity(0.38f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        val core = LineLayer("route-core", "route").withProperties(
            PropertyFactory.lineColor(pink),
            PropertyFactory.lineWidth(if (compact) 5f else 8f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        val shine = LineLayer("route-shine", "route").withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(if (compact) 1.2f else 2f),
            PropertyFactory.lineOpacity(0.45f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        style.addLayerBelow(glow, "road-label")
        style.addLayerBelow(core, "road-label")
        style.addLayerBelow(shine, "road-label")

        val me = GeoJsonSource("me")
        style.addSource(me)
        meSource = me
        style.addLayer(
            SymbolLayer("me", "me").withProperties(
                PropertyFactory.iconImage("me"),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
        )
        this.style = style
        styleReady = true
        applyTheme()
        applyLabels()
        applyRouteColor()
        pendingRoute?.let { drawRouteLine(it) }
        NavigationEngine.location.value?.let { onLocation(it, NavigationEngine.heading.value, animate = false) }
    }

    /** Draws only the part of the route still ahead, so the line starts at the arrow like the game. */
    fun showRoute(route: Route?, along: Double?, snapped: LatLng?) {
        if (route == null) {
            pendingRoute = null
            if (styleReady) routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
            return
        }
        val pts = if (along != null && snapped != null) {
            val (_, seg) = Geo.pointAlong(route.points, route.cumulative, along)
            listOf(snapped) + route.points.drop(seg + 1)
        } else route.points
        if (!styleReady) {
            pendingRoute = pts
            return
        }
        drawRouteLine(pts)
    }

    private fun drawRouteLine(pts: List<LatLng>) {
        if (pts.size < 2) {
            routeSource?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
            return
        }
        val line = LineString.fromLngLats(pts.map { Point.fromLngLat(it.longitude, it.latitude) })
        routeSource?.setGeoJson(Feature.fromGeometry(line))
    }

    fun onLocation(loc: Location, heading: Float, animate: Boolean = true) {
        if (styleReady) {
            val f = Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude))
            f.addNumberProperty("bearing", heading)
            meSource?.setGeoJson(f)
        }
        blips?.invalidate()
        if (mode == Mode.FOLLOW) follow(loc, heading, animate)
    }

    private fun follow(loc: Location, heading: Float, animate: Boolean) {
        val m = map ?: return
        val h = (mapView.height - insets.top - insets.bottom).coerceAtLeast(1)
        // Push the focal point down so the road ahead fills the view.
        val topPad = (h * (followBias - 0.5f) * 2f).coerceAtLeast(0f).toDouble()
        val zoom = when {
            compact -> 16.4
            loc.speed > 25 -> 15.4
            loc.speed > 14 -> 16.0
            else -> 16.8
        }
        val pos = CameraPosition.Builder()
            .target(LatLng(loc.latitude, loc.longitude))
            .bearing(heading.toDouble())
            .tilt(if (compact) 50.0 else 56.0)
            .zoom(zoom)
            .padding(insets.left.toDouble(), insets.top + topPad, insets.right.toDouble(), insets.bottom.toDouble())
            .build()
        if (animate) m.easeCamera(CameraUpdateFactory.newCameraPosition(pos), 950)
        else m.moveCamera(CameraUpdateFactory.newCameraPosition(pos))
    }

    /** Snap back behind the arrow. */
    fun recenter() {
        handler.removeCallbacks(autoRecenter)
        mode = Mode.FOLLOW
        val loc = NavigationEngine.location.value ?: return
        val m = map ?: return
        m.cancelTransitions()
        follow(loc, NavigationEngine.heading.value, animate = true)
    }

    private fun userMoved() {
        if (mode != Mode.FREE) mode = Mode.FREE
        handler.removeCallbacks(autoRecenter)
        if (NavigationEngine.isNavigating) handler.postDelayed(autoRecenter, 10_000)
    }

    /** Zoomed far out, the tilt eases off into a flat, pause-menu style map. */
    private fun flattenForZoom() {
        val m = map ?: return
        val cam = m.cameraPosition
        val target = when {
            cam.zoom < 12.5 -> 0.0
            cam.zoom < 15.0 -> (cam.zoom - 12.5) / 2.5 * 56.0
            else -> return
        }
        if (abs(cam.tilt - target) > 3) {
            m.animateCamera(CameraUpdateFactory.tiltTo(target), 400)
        }
    }

    fun zoomBy(delta: Double, focus: PointF? = null) {
        val m = map ?: return
        userMoved()
        if (focus != null) m.animateCamera(CameraUpdateFactory.zoomBy(delta, android.graphics.Point(focus.x.toInt(), focus.y.toInt())), 250)
        else m.animateCamera(CameraUpdateFactory.zoomBy(delta), 250)
    }

    /** Pan by screen pixels (used by the car screen's drag gesture). */
    fun scrollBy(dx: Float, dy: Float) {
        val m = map ?: return
        userMoved()
        m.scrollBy(dx, dy)
    }

    /** Glide along the route: [fraction] 0 = start, 1 = destination. */
    fun previewAlong(route: Route, fraction: Float) {
        val m = map ?: return
        handler.removeCallbacks(autoRecenter)
        mode = Mode.PREVIEW
        val d = route.length * fraction.coerceIn(0f, 1f)
        val (p, _) = Geo.pointAlong(route.points, route.cumulative, d)
        val (ahead, _) = Geo.pointAlong(route.points, route.cumulative, d + 80)
        val bearing = if (Geo.distance(p, ahead) > 1) Geo.bearing(p, ahead) else m.cameraPosition.bearing
        val pos = CameraPosition.Builder()
            .target(p)
            .bearing(bearing)
            .tilt(50.0)
            .zoom(16.2)
            .padding(insets.left.toDouble(), insets.top.toDouble(), insets.right.toDouble(), insets.bottom.toDouble())
            .build()
        m.easeCamera(CameraUpdateFactory.newCameraPosition(pos), 300)
    }

    /** Whole route, top-down. */
    fun overview(route: Route) {
        val m = map ?: return
        mode = Mode.OVERVIEW
        handler.removeCallbacks(autoRecenter)
        val b = LatLngBounds.Builder()
        route.points.forEach { b.include(it) }
        NavigationEngine.location.value?.let { b.include(LatLng(it.latitude, it.longitude)) }
        val pad = (48 * context.resources.displayMetrics.density).toInt()
        val bounds = try { b.build() } catch (e: Exception) { return }
        val cam = m.getCameraForLatLngBounds(
            bounds,
            intArrayOf(insets.left + pad, insets.top + pad, insets.right + pad, insets.bottom + pad),
        ) ?: return
        val flat = CameraPosition.Builder(cam).tilt(0.0).bearing(0.0).build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(flat), 800)
        if (NavigationEngine.isNavigating) handler.postDelayed(autoRecenter, 12_000)
    }

    /** Follow the shared theme, label and waypoint-color state for as long as [scope] lives. */
    fun bindStyle(scope: CoroutineScope): List<Job> = listOf(
        scope.launch { MapTheme.night.collect { night = it; applyTheme() } },
        scope.launch { Settings.streetLabels.collect { labels = it; applyLabels() } },
        scope.launch {
            NavigationEngine.destination.collect { d ->
                routeColor = if (d?.waypoint == true) WAYPOINT_CYAN else ROUTE_PINK
                blips?.destinationColor = routeColor
                applyRouteColor()
            }
        },
    )

    private fun hex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    private fun applyRouteColor() {
        val s = style ?: return
        val c = hex(routeColor)
        s.getLayer("route-glow")?.setProperties(PropertyFactory.lineColor(c))
        s.getLayer("route-core")?.setProperties(PropertyFactory.lineColor(c))
    }

    private fun applyLabels() {
        val s = style ?: return
        // The game's minimap has no text on it; the full map can show street names.
        val v = if (labels && !compact) Property.VISIBLE else Property.NONE
        s.getLayer("road-label")?.setProperties(PropertyFactory.visibility(v))
        s.getLayer("place-label")?.setProperties(PropertyFactory.visibility(v))
    }

    /** Warm light-gray by day, slate dark by night. */
    private fun applyTheme() {
        val s = style ?: return
        val p = if (night) NIGHT else DAY
        s.getLayer("background")?.setProperties(PropertyFactory.backgroundColor(p.ground))
        s.getLayer("landcover")?.setProperties(PropertyFactory.fillColor(p.green))
        s.getLayer("park")?.setProperties(PropertyFactory.fillColor(p.park))
        s.getLayer("landuse")?.setProperties(PropertyFactory.fillColor(p.park))
        s.getLayer("water")?.setProperties(PropertyFactory.fillColor(p.water))
        s.getLayer("waterway")?.setProperties(PropertyFactory.lineColor(p.water))
        s.getLayer("aeroway")?.setProperties(PropertyFactory.fillColor(p.aeroway))
        s.getLayer("road-minor")?.setProperties(PropertyFactory.lineColor(p.minor))
        s.getLayer("road-casing")?.setProperties(PropertyFactory.lineColor(p.casing))
        s.getLayer("road-major")?.setProperties(PropertyFactory.lineColor(p.major))
        s.getLayer("rail")?.setProperties(PropertyFactory.lineColor(p.rail))
        s.getLayer("building-3d")?.setProperties(PropertyFactory.fillExtrusionColor(p.building))
        s.getLayer("poi-entrance")?.setProperties(PropertyFactory.circleStrokeColor(p.dotRing))
        s.getLayer("road-label")?.setProperties(PropertyFactory.textColor(p.label), PropertyFactory.textHaloColor(p.halo))
        s.getLayer("place-label")?.setProperties(PropertyFactory.textColor(p.label), PropertyFactory.textHaloColor(p.ground))
        onThemeApplied?.invoke(night)
    }

    /** Lets owners recolor their frame/background to match. */
    var onThemeApplied: ((Boolean) -> Unit)? = null

    private class Palette(
        val ground: String, val green: String, val park: String, val water: String, val aeroway: String,
        val minor: String, val casing: String, val major: String, val rail: String, val building: String,
        val dotRing: String, val label: String, val halo: String,
    )

    private companion object {
        private val DAY = Palette(
            ground = "#d6d3cb", green = "#ccd1c1", park = "#c4cbb9", water = "#a7b8c0", aeroway = "#cfccc4",
            minor = "#eeece6", casing = "#c9c5bc", major = "#f4f2ed", rail = "#bdb8ae", building = "#e3e0d9",
            dotRing = "#8a857b", label = "#7a766e", halo = "#f4f2ed",
        )
        private val NIGHT = Palette(
            ground = "#23262b", green = "#262b28", park = "#243026", water = "#17212a", aeroway = "#2a2d31",
            minor = "#383c43", casing = "#1a1c20", major = "#4b5059", rail = "#3a3d42", building = "#343840",
            dotRing = "#111214", label = "#9aa0a8", halo = "#23262b",
        )
    }

    fun release() {
        handler.removeCallbacks(autoRecenter)
    }
}
