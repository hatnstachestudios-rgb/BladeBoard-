package com.hatnstache.gpsauto.car

import android.app.Presentation
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.hatnstache.gpsauto.data.PlaceStore
import com.hatnstache.gpsauto.map.BlipOverlayView
import com.hatnstache.gpsauto.map.GtaMap
import com.hatnstache.gpsauto.nav.NavigationEngine
import com.hatnstache.gpsauto.nav.Units
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import kotlin.math.ln

/**
 * Draws the GTA-style map onto the car screen. Android Auto gives us a Surface; we point a
 * private virtual display at it and show a normal MapView there through a Presentation.
 */
class CarMapRenderer(private val carContext: CarContext, private val lifecycle: Lifecycle) :
    SurfaceCallback, DefaultLifecycleObserver {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var mapView: MapView? = null
    private var gta: GtaMap? = null
    private var blips: BlipOverlayView? = null
    private var frame: FrameLayout? = null
    private var readout: TextView? = null
    private var root: FrameLayout? = null
    private val jobs = mutableListOf<Job>()
    private var visibleArea: Rect? = null
    private var density = 1f
    private var surfaceW = 0
    private var surfaceH = 0

    var mode = GtaMap.Mode.FOLLOW
        private set
    var onModeChanged: ((GtaMap.Mode) -> Unit)? = null

    /** GTA minimap frame inside the car screen instead of a full-screen map. */
    var minimap = false
        set(value) {
            field = value
            applyLayout()
        }

    init {
        lifecycle.addObserver(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        teardown()
        val dm = carContext.getSystemService(DisplayManager::class.java)
        val vd = dm.createVirtualDisplay(
            "GPSAuto", container.width, container.height, container.dpi, surface, 0,
        ) ?: return
        virtualDisplay = vd
        surfaceW = container.width
        surfaceH = container.height
        val pres = Presentation(carContext, vd.display)
        presentation = pres
        val ctx = pres.context
        density = ctx.resources.displayMetrics.density

        val mv = MapView(ctx, MapLibreMapOptions.createFromAttributes(ctx).textureMode(true))
        val bv = BlipOverlayView(ctx)
        val fr = FrameLayout(ctx)
        fr.addView(mv, FrameLayout.LayoutParams(-1, -1))
        fr.addView(bv, FrameLayout.LayoutParams(-1, -1))
        val ro = TextView(ctx).apply {
            setTextColor(0xFF1C1D20.toInt())
            textSize = 20f
            setShadowLayer(4f, 0f, 0f, 0xCCF4F2ED.toInt())
            visibility = View.GONE
        }
        fr.addView(ro, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = (18 * density).toInt(); topMargin = (12 * density).toInt()
        })
        val rt = FrameLayout(ctx).apply { setBackgroundColor(0xFF24272B.toInt()) }
        rt.addView(fr, FrameLayout.LayoutParams(-1, -1))
        pres.setContentView(rt)
        try {
            pres.show()
        } catch (e: Exception) {
            teardown()
            return
        }

        mv.onCreate(null)
        mv.onStart()
        mv.onResume()
        val g = GtaMap(ctx, mv, bv)
        g.onModeChanged = { m -> mode = m; onModeChanged?.invoke(m) }
        mapView = mv; blips = bv; frame = fr; readout = ro; root = rt; gta = g
        applyLayout()
        observe(g, bv, ro)
    }

    private fun observe(g: GtaMap, bv: BlipOverlayView, ro: TextView) {
        val scope = lifecycle.coroutineScope
        jobs += scope.launch {
            combine(NavigationEngine.location, NavigationEngine.heading) { l, h -> l to h }.collect { (l, h) ->
                if (l != null) g.onLocation(l, h)
            }
        }
        jobs += scope.launch {
            combine(NavigationEngine.route, NavigationEngine.progress) { r, p -> r to p }.collect { (r, p) ->
                g.showRoute(r, p?.along, p?.snapped)
                ro.text = if (p != null && NavigationEngine.isNavigating) Units.distancePrecise(p.remaining) else ""
            }
        }
        jobs += scope.launch {
            NavigationEngine.route.collect { r -> if (r != null) g.recenter() }
        }
        jobs += scope.launch { PlaceStore.places.collect { bv.places = it } }
        g.onThemeApplied = { night ->
            root?.setBackgroundColor(if (night) 0xFF101113.toInt() else 0xFF24272B.toInt())
            ro.setTextColor(if (night) Color.WHITE else 0xFF1C1D20.toInt())
            ro.setShadowLayer(4f, 0f, 0f, if (night) 0xCC000000.toInt() else 0xCCF4F2ED.toInt())
        }
        jobs += g.bindStyle(scope)
        jobs += scope.launch { NavigationEngine.destination.collect { bv.destination = it?.point } }
    }

    private fun applyLayout() {
        val fr = frame ?: return
        if (root == null) return
        val g = gta ?: return
        val w = surfaceW
        val h = surfaceH
        if (w <= 0 || h <= 0) return
        val area = visibleArea ?: Rect(0, 0, w, h)

        if (!minimap) {
            fr.layoutParams = FrameLayout.LayoutParams(-1, -1)
            fr.clipToOutline = false
            fr.foreground = null
            readout?.visibility = View.GONE
            g.insets = Rect(area.left, area.top, (w - area.right).coerceAtLeast(0), (h - area.bottom).coerceAtLeast(0))
            g.followBias = 0.62f
        } else {
            val margin = (16 * density).toInt()
            val fw = ((area.width() - margin * 2) * 0.9f).toInt().coerceAtLeast(100)
            val fh = ((area.height() - margin * 2) * 0.86f).toInt().coerceAtLeast(80)
            fr.layoutParams = FrameLayout.LayoutParams(fw, fh).apply {
                leftMargin = area.left + (area.width() - fw) / 2
                topMargin = area.top + (area.height() - fh) / 2
            }
            val radius = 26 * density
            fr.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            fr.clipToOutline = true
            fr.foreground = GradientDrawable().apply {
                cornerRadius = radius
                setStroke((2 * density).toInt(), 0xD9FFFFFF.toInt())
                setColor(Color.TRANSPARENT)
            }
            readout?.visibility = View.VISIBLE
            g.insets = Rect()
            g.followBias = 0.64f
        }
        fr.requestLayout()
        fr.post { g.recenter() }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = Rect(visibleArea)
        applyLayout()
    }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onScroll(distanceX: Float, distanceY: Float) {
        gta?.scrollBy(-distanceX, -distanceY)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (scaleFactor <= 0f) return
        val delta = ln(scaleFactor.toDouble()) / ln(2.0)
        val focus = if (focusX >= 0 && focusY >= 0) PointF(focusX, focusY) else null
        gta?.zoomBy(delta, focus)
    }

    fun zoomIn() = gta?.zoomBy(1.0)
    fun zoomOut() = gta?.zoomBy(-1.0)
    fun recenter() = gta?.recenter()
    fun overview() {
        val r = NavigationEngine.route.value ?: return
        gta?.overview(r)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = teardown()

    override fun onDestroy(owner: LifecycleOwner) = teardown()

    private fun teardown() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        gta?.release()
        mapView?.let {
            runCatching { it.onPause(); it.onStop(); it.onDestroy() }
        }
        runCatching { presentation?.dismiss() }
        virtualDisplay?.release()
        presentation = null; virtualDisplay = null; mapView = null; gta = null
        blips = null; frame = null; readout = null; root = null
    }
}
