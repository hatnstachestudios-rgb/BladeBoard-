package com.hatnstache.gpsauto.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.hatnstache.gpsauto.nav.LocationHub

/** Entry point for Android Auto. */
class GpsCarAppService : CarAppService() {
    // Personal sideloaded build: accept any Android Auto host (enable "Unknown sources" in AA developer settings).
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = GpsCarSession()
}

class GpsCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val renderer = CarMapRenderer(carContext, lifecycle)
        LocationHub.acquire(carContext, "car")
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                LocationHub.release("car")
            }
        })
        return NavScreen(carContext, renderer)
    }
}
