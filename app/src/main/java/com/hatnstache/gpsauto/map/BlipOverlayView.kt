package com.hatnstache.gpsauto.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import com.hatnstache.gpsauto.R
import com.hatnstache.gpsauto.data.Place
import com.hatnstache.gpsauto.nav.Geo
import com.hatnstache.gpsauto.nav.NavigationEngine
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Draws blips on top of the map like GTA: flat icons that always face the screen. A place that
 * is off the map sticks to the frame edge, on the side it lies in, and slides as you turn.
 * Also draws the destination pip and the N (north) marker on the edge.
 */
class BlipOverlayView(context: Context) : View(context) {
    var map: MapLibreMap? = null
        set(value) { field = value; invalidate() }
    var places: List<Place> = emptyList()
        set(value) { field = value; invalidate() }
    var destination: LatLng? = null
        set(value) { field = value; invalidate() }
    var destinationColor: Int = ROUTE_PINK
        set(value) { field = value; invalidate() }

    /** Area the map is actually visible in (Android Auto covers some of the screen). */
    var visibleInsets = Rect()
        set(value) { field = value; invalidate() }

    /** Smaller blips for the floating minimap. */
    var compact = false

    private val density = resources.displayMetrics.density
    private val painter = BlipPainter(context)
    private val pip = Paint(Paint.ANTI_ALIAS_FLAG)
    private val northBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF121315.toInt() }
    private val northRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }
    private val northText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hits = mutableListOf<Pair<Place, PointF>>()

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
    }

    /** The saved place under a tap, if any. */
    fun hitTest(x: Float, y: Float): Place? =
        hits.firstOrNull { (_, p) -> hypot(p.x - x, p.y - y) < 26 * density }?.first

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        hits.clear()
        val m = map ?: return
        val scale = if (compact) 0.72f else 1f
        val r = 15 * density * scale
        val margin = r + 6 * density
        val frame = RectF(
            visibleInsets.left + margin,
            visibleInsets.top + margin,
            width - visibleInsets.right - margin,
            height - visibleInsets.bottom - margin,
        )
        if (frame.width() <= 0 || frame.height() <= 0) return

        val cam = m.cameraPosition
        val camBearing = cam.bearing
        val me = NavigationEngine.location.value?.let { LatLng(it.latitude, it.longitude) }
        val meScreen = me?.let { m.projection.toScreenLocation(it) }
        val origin = if (meScreen != null && frame.contains(meScreen.x, meScreen.y)) meScreen
        else PointF(frame.centerX(), frame.centerY())

        fun place(target: LatLng): Pair<PointF, Boolean> {
            val near = me == null || Geo.distance(me, target) < 4000
            if (near) {
                val p = m.projection.toScreenLocation(target)
                if (frame.contains(p.x, p.y)) return p to true
                if (me == null) return clampRay(origin, p, frame) to false
            }
            // Far away, or behind the tilted horizon: aim along the compass bearing instead.
            val angle = Math.toRadians(Geo.bearing(me!!, target) - camBearing)
            val far = PointF(origin.x + sin(angle).toFloat() * 10000f, origin.y - cos(angle).toFloat() * 10000f)
            return clampRay(origin, far, frame) to false
        }

        for (p in places) {
            val (pt, inside) = place(LatLng(p.lat, p.lon))
            painter.draw(canvas, pt.x, pt.y, if (inside) r else r * 1.08f, p.icon.res, p.color)
            hits += p to pt
        }

        destination?.let { d ->
            val (pt, inside) = place(d)
            val pr = r * if (inside) 0.95f else 0.8f
            painter.draw(canvas, pt.x, pt.y, pr, if (inside) R.drawable.blip_flag else null, destinationColor)
        }

        // North marker rides the frame edge in the direction of north.
        val nAngle = Math.toRadians(-camBearing)
        val center = PointF(frame.centerX(), frame.centerY())
        val nFar = PointF(center.x + sin(nAngle).toFloat() * 10000f, center.y - cos(nAngle).toFloat() * 10000f)
        val n = clampRay(center, nFar, frame)
        val nr = 12 * density * scale
        canvas.drawCircle(n.x, n.y, nr, northBg)
        northRing.strokeWidth = 1.5f * density
        canvas.drawCircle(n.x, n.y, nr, northRing)
        northText.textSize = 13 * density * scale
        canvas.drawText("N", n.x, n.y - (northText.descent() + northText.ascent()) / 2, northText)
    }

    /** Where the ray from [from] toward [to] leaves [rect]. */
    private fun clampRay(from: PointF, to: PointF, rect: RectF): PointF {
        val dx = to.x - from.x
        val dy = to.y - from.y
        if (abs(dx) < 1e-3 && abs(dy) < 1e-3) return from
        var t = Float.MAX_VALUE
        if (dx > 0) t = minOf(t, (rect.right - from.x) / dx)
        if (dx < 0) t = minOf(t, (rect.left - from.x) / dx)
        if (dy > 0) t = minOf(t, (rect.bottom - from.y) / dy)
        if (dy < 0) t = minOf(t, (rect.top - from.y) / dy)
        t = t.coerceIn(0f, 1f)
        return PointF(
            (from.x + dx * t).coerceIn(rect.left, rect.right),
            (from.y + dy * t).coerceIn(rect.top, rect.bottom),
        )
    }
}
