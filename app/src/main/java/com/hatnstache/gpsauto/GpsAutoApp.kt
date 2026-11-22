package com.hatnstache.gpsauto

import android.app.Application
import com.hatnstache.gpsauto.data.PlaceStore
import com.hatnstache.gpsauto.data.Settings
import com.hatnstache.gpsauto.map.MapTheme
import com.hatnstache.gpsauto.nav.NavigationEngine
import org.maplibre.android.MapLibre

class GpsAutoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        PlaceStore.init(this)
        Settings.init(this)
        NavigationEngine.init(this)
        MapTheme.init()
    }
}
