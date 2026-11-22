package com.hatnstache.gpsauto.car

import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Trip
import androidx.lifecycle.lifecycleScope
import com.hatnstache.gpsauto.R
import com.hatnstache.gpsauto.map.GtaMap
import com.hatnstache.gpsauto.nav.LocationHub
import com.hatnstache.gpsauto.nav.NavigationEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.car.app.navigation.model.Destination as CarDestination

/** The main car screen: our map underneath, Google's navigation template on top. */
class NavScreen(carContext: CarContext, private val renderer: CarMapRenderer) : Screen(carContext) {
    private val navManager = carContext.getCarService(NavigationManager::class.java)
    private var navStarted = false
    private var lastInvalidate = 0L
    private var pending: Job? = null

    init {
        navManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                NavigationEngine.stop()
            }

            override fun onAutoDriveEnabled() {}
        })
        renderer.onModeChanged = { refresh() }
        lifecycleScope.launch {
            combine(
                NavigationEngine.progress,
                NavigationEngine.destination,
                NavigationEngine.loading,
                NavigationEngine.muted,
            ) { _, _, _, _ -> Unit }.collect {
                syncNavigationState()
                refresh()
            }
        }
    }

    /** Template updates are rate limited by the host, so batch them to about once a second. */
    private fun refresh() {
        val wait = 1000 - (SystemClock.elapsedRealtime() - lastInvalidate)
        if (wait <= 0) {
            lastInvalidate = SystemClock.elapsedRealtime()
            invalidate()
        } else if (pending?.isActive != true) {
            pending = lifecycleScope.launch {
                delay(wait)
                lastInvalidate = SystemClock.elapsedRealtime()
                invalidate()
            }
        }
    }

    /** Tells Android Auto when we're navigating (so it can stop other nav apps) and feeds the cluster. */
    private fun syncNavigationState() {
        val dest = NavigationEngine.destination.value
        try {
            if (dest != null && !navStarted) {
                navManager.navigationStarted()
                navStarted = true
            } else if (dest == null && navStarted) {
                navManager.navigationEnded()
                navStarted = false
            }
            val p = NavigationEngine.progress.value
            val step = p?.nextStep
            if (navStarted && dest != null && p != null && step != null) {
                val trip = Trip.Builder()
                    .addStep(CarMappers.step(carContext, step), CarMappers.estimate(p.distanceToNext, p.remainingSeconds * (p.distanceToNext / p.remaining.coerceAtLeast(1.0))))
                    .addDestination(CarDestination.Builder().setName(dest.name).build(), CarMappers.estimate(p.remaining, p.remainingSeconds))
                    .build()
                navManager.updateTrip(trip)
            }
        } catch (e: Exception) {
            // The host can refuse these (e.g. another nav app took over); the map keeps working.
        }
    }

    override fun onGetTemplate(): Template {
        if (!LocationHub.hasPermission(carContext)) {
            return MessageTemplate.Builder("Open GPS Auto on your phone once and allow location access.")
                .setTitle("GPS Auto")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        val dest = NavigationEngine.destination.value
        val p = NavigationEngine.progress.value
        val muted = NavigationEngine.muted.value

        val strip = ActionStrip.Builder()
        if (dest == null) {
            strip.addAction(Action.Builder().setTitle("Places").setOnClickListener {
                screenManager.push(PlacesScreen(carContext))
            }.build())
            strip.addAction(Action.Builder().setTitle("Search").setOnClickListener {
                screenManager.push(SearchScreen(carContext))
            }.build())
        } else {
            strip.addAction(Action.Builder().setTitle(if (muted) "Unmute" else "Mute").setOnClickListener {
                NavigationEngine.setMuted(!muted)
            }.build())
        }
        strip.addAction(Action.Builder().setTitle(if (renderer.minimap) "Full map" else "Minimap").setOnClickListener {
            renderer.minimap = !renderer.minimap
            invalidate()
        }.build())
        if (dest != null) {
            strip.addAction(Action.Builder().setTitle("End").setOnClickListener { NavigationEngine.stop() }.build())
        }

        val mapStrip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(Action.Builder().setIcon(CarMappers.icon(carContext, R.drawable.act_zoom_in)).setOnClickListener { renderer.zoomIn() }.build())
            .addAction(Action.Builder().setIcon(CarMappers.icon(carContext, R.drawable.act_zoom_out)).setOnClickListener { renderer.zoomOut() }.build())
        if (renderer.mode != GtaMap.Mode.FOLLOW) {
            mapStrip.addAction(Action.Builder().setIcon(CarMappers.icon(carContext, R.drawable.act_recenter)).setOnClickListener { renderer.recenter() }.build())
        } else if (NavigationEngine.route.value != null) {
            mapStrip.addAction(Action.Builder().setIcon(CarMappers.icon(carContext, R.drawable.act_overview)).setOnClickListener { renderer.overview() }.build())
        }

        val b = NavigationTemplate.Builder()
            .setActionStrip(strip.build())
            .setMapActionStrip(mapStrip.build())
            .setPanModeListener { }

        if (dest != null) {
            val step = p?.nextStep
            when {
                p?.arrived == true -> b.setNavigationInfo(MessageInfo.Builder("Arrived at ${dest.name}").build())
                p != null && step != null -> {
                    b.setNavigationInfo(
                        RoutingInfo.Builder()
                            .setCurrentStep(CarMappers.step(carContext, step), CarMappers.distance(p.distanceToNext))
                            .build()
                    )
                    b.setDestinationTravelEstimate(CarMappers.estimate(p.remaining, p.remainingSeconds))
                }
                else -> b.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
            }
        }
        return b.build()
    }
}
