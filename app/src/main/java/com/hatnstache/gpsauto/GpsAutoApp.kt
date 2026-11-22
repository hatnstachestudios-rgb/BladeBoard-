package com.hatnstache.gpsauto

import android.app.Application
import com.hatnstache.gpsauto.data.PlaceStore
import com.hatnstache.gpsauto.nav.NavigationEngine
import org.maplibre.android.MapLibre

class GpsAutoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        PlaceStore.init(this)
        NavigationEngine.init(this)
    }
}
