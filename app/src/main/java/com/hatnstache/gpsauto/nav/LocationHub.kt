package com.hatnstache.gpsauto.nav

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Shares one GPS subscription between everything that needs it (the app screen, the
 * navigation service, Android Auto). Updates stop when the last user lets go.
 */
object LocationHub {
    private var client: FusedLocationProviderClient? = null
    private val users = mutableSetOf<String>()
    private var running = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { NavigationEngine.onLocation(it) }
        }
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun acquire(context: Context, who: String) {
        users += who
        if (running || !hasPermission(context)) return
        val c = client ?: LocationServices.getFusedLocationProviderClient(context.applicationContext).also { client = it }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        try {
            c.requestLocationUpdates(request, callback, Looper.getMainLooper())
            c.lastLocation.addOnSuccessListener { loc -> loc?.let { NavigationEngine.onLocation(it) } }
            running = true
        } catch (e: SecurityException) {
            running = false
        }
    }

    fun release(who: String) {
        users -= who
        if (users.isEmpty() && running) {
            client?.removeLocationUpdates(callback)
            running = false
        }
    }
}
