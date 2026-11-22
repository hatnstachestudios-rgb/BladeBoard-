package com.hatnstache.gpsauto

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.hatnstache.gpsauto.media.MediaRepository
import com.hatnstache.gpsauto.nav.LocationHub
import com.hatnstache.gpsauto.nav.NavService
import com.hatnstache.gpsauto.ui.GpsScreen

class MainActivity : ComponentActivity() {
    private lateinit var media: MediaRepository

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (LocationHub.hasPermission(this)) LocationHub.acquire(this, "activity")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        media = MediaRepository(this)

        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!LocationHub.hasPermission(this)) permissions.launch(wanted.toTypedArray())

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFF4FA8),
                    surface = Color(0xFF17181B),
                    background = Color(0xFF111214),
                )
            ) {
                GpsScreen(media = media, onFloat = ::floatMinimap)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocationHub.acquire(this, "activity")
        // Back in the app: the full map takes over from the floating one.
        NavService.hideOverlay(this)
    }

    override fun onResume() {
        super.onResume()
        media.refresh()
    }

    override fun onStop() {
        LocationHub.release("activity")
        super.onStop()
    }

    override fun onDestroy() {
        media.stop()
        super.onDestroy()
    }

    private fun floatMinimap() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        NavService.showOverlay(this)
        moveTaskToBack(true)
    }
}
