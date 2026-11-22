package com.hatnstache.gpsauto.overlay

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hatnstache.gpsauto.MainActivity
import com.hatnstache.gpsauto.data.PlaceStore
import com.hatnstache.gpsauto.map.BlipOverlayView
import com.hatnstache.gpsauto.map.GtaMap
import com.hatnstache.gpsauto.map.ROUTE_PINK
import com.hatnstache.gpsauto.nav.Instructions
import com.hatnstache.gpsauto.nav.NavigationEngine
import com.hatnstache.gpsauto.nav.Units
import android.os.SystemClock
import com.hatnstache.gpsauto.data.Settings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import kotlin.math.abs

/** The floating GTA minimap that sits over other apps. Drag to move, tap to open the app. */
class MinimapOverlay(private val service: LifecycleService) {
    private val ctx = ContextThemeWrapper(service, android.R.style.Theme_Material)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    private val mapView = MapView(ctx, MapLibreMapOptions.createFromAttributes(ctx).textureMode(true))
    private val blips = BlipOverlayView(ctx).apply { compact = true }
    private val gta = GtaMap(ctx, mapView, blips, compact = true).apply { followBias = 0.62f }
    private val distance = TextView(ctx)
    private val turnIcon = ImageView(ctx)
    private val turnText = TextView(ctx)
    private val turnChip = LinearLayout(ctx)
    private val root = FrameLayout(ctx)
    private val container = LinearLayout(ctx)
    private val params = WindowManager.LayoutParams(
        dp(230), dp(150 + 46),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(12)
        y = service.resources.displayMetrics.heightPixels - dp(196) - dp(120)
    }
    private val jobs = mutableListOf<Job>()

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        val radius = dp(22).toFloat()
        root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        root.clipToOutline = true
        root.setBackgroundColor(0xFFD6D3CB.toInt())
        root.foreground = GradientDrawable().apply {
            cornerRadius = radius
            setStroke(dp(2), 0xCCFFFFFF.toInt())
        }

        mapView.onCreate(null)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        root.addView(blips, FrameLayout.LayoutParams(-1, -1))

        distance.setTextColor(0xFF1C1D20.toInt())
        distance.textSize = 15f
        distance.setShadowLayer(4f, 0f, 0f, 0xCCF4F2ED.toInt())
        root.addView(distance, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = dp(12); topMargin = dp(8)
        })

        // Transparent layer on top: the minimap itself doesn't pan, the whole window drags.
        val shield = View(ctx)
        root.addView(shield, FrameLayout.LayoutParams(-1, -1))
        val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        shield.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (dragging || abs(dx) > slop || abs(dy) > slop) {
                        dragging = true
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) openApp()
            }
            true
        }

        // GTA next-turn box above the minimap: "Turn Left  46 ft" + pink arrow.
        turnChip.orientation = LinearLayout.HORIZONTAL
        turnChip.gravity = Gravity.CENTER_VERTICAL
        turnChip.setPadding(dp(11), dp(6), dp(8), dp(6))
        turnChip.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(0xE6141518.toInt())
        }
        turnText.setTextColor(Color.WHITE)
        turnText.textSize = 13f
        turnChip.addView(turnText)
        turnIcon.setColorFilter(ROUTE_PINK)
        turnChip.addView(turnIcon, LinearLayout.LayoutParams(dp(20), dp(20)).apply { leftMargin = dp(8) })
        turnChip.visibility = View.INVISIBLE

        container.orientation = LinearLayout.VERTICAL
        container.addView(turnChip, LinearLayout.LayoutParams(-2, dp(34)).apply { bottomMargin = dp(6) })
        container.addView(root, LinearLayout.LayoutParams(-1, dp(150)))
        mapView.alpha = 0.95f

        wm.addView(container, params)
        mapView.onStart()
        mapView.onResume()

        jobs += service.lifecycleScope.launch {
            combine(NavigationEngine.location, NavigationEngine.heading) { l, h -> l to h }.collect { (l, h) ->
                if (l != null) gta.onLocation(l, h)
            }
        }
        jobs += service.lifecycleScope.launch {
            combine(NavigationEngine.route, NavigationEngine.progress) { r, p -> r to p }.collect { (r, p) ->
                gta.showRoute(r, p?.along, p?.snapped)
                if (p != null && NavigationEngine.isNavigating) {
                    distance.text = Units.distancePrecise(p.remaining)
                    val step = p.nextStep
                    if (step != null && !p.arrived && p.distanceToNext < 480) {
                        turnChip.visibility = View.VISIBLE
                        turnIcon.setImageResource(Instructions.iconRes(step))
                        turnText.text = "${Instructions.shortLabel(step)}  ${Units.distance(p.distanceToNext)}"
                    } else turnChip.visibility = View.INVISIBLE
                } else {
                    distance.text = ""
                    turnChip.visibility = View.INVISIBLE
                }
            }
        }
        jobs += service.lifecycleScope.launch { PlaceStore.places.collect { blips.places = it } }
        gta.onThemeApplied = { night ->
            root.setBackgroundColor(if (night) 0xFF23262B.toInt() else 0xFFD6D3CB.toInt())
            distance.setTextColor(if (night) Color.WHITE else 0xFF1C1D20.toInt())
            distance.setShadowLayer(4f, 0f, 0f, if (night) 0xCC000000.toInt() else 0xCCF4F2ED.toInt())
        }
        jobs += gta.bindStyle(service.lifecycleScope)
        // Dynamic mode: fade out when parked with no route, back in when you drive or start a route.
        jobs += service.lifecycleScope.launch {
            var lastActive = SystemClock.elapsedRealtime()
            while (true) {
                val active = !Settings.dynamicOverlay.value ||
                    NavigationEngine.destination.value != null ||
                    (NavigationEngine.location.value?.speed ?: 0f) > 2f
                val now = SystemClock.elapsedRealtime()
                if (active) lastActive = now
                val target = if (now - lastActive > 15_000) 0.15f else 1f
                if (container.alpha != target) container.animate().alpha(target).setDuration(600).start()
                delay(1000)
            }
        }
        jobs += service.lifecycleScope.launch {
            NavigationEngine.destination.collect { blips.destination = it?.point }
        }
    }

    private fun openApp() {
        service.startActivity(
            Intent(service, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    fun hide() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        gta.release()
        runCatching {
            mapView.onPause()
            mapView.onStop()
            wm.removeView(container)
            mapView.onDestroy()
        }
    }
}
