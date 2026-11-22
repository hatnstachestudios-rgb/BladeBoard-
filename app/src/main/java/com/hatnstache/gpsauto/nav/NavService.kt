package com.hatnstache.gpsauto.nav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.hatnstache.gpsauto.MainActivity
import com.hatnstache.gpsauto.R
import com.hatnstache.gpsauto.overlay.MinimapOverlay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps GPS and voice directions going while the app is in the
 * background (or the phone is on Android Auto), and hosts the floating minimap.
 */
class NavService : LifecycleService() {
    private var navActive = false
    private var overlay: MinimapOverlay? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Navigation", NotificationManager.IMPORTANCE_LOW)
        )
        lifecycleScope.launch {
            combine(NavigationEngine.progress, NavigationEngine.destination) { p, d -> p to d }
                .collect { (p, d) ->
                    if (started) nm.notify(NOTIFICATION_ID, buildNotification(p, d))
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!started) {
            try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID,
                    buildNotification(NavigationEngine.progress.value, NavigationEngine.destination.value),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
                )
                started = true
                LocationHub.acquire(this, "service")
            } catch (e: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        when (intent?.action) {
            ACTION_NAV_START -> navActive = true
            ACTION_NAV_STOP -> navActive = false
            ACTION_OVERLAY_SHOW -> showOverlay()
            ACTION_OVERLAY_HIDE -> hideOverlay()
            ACTION_END -> NavigationEngine.stop()
        }
        if (!navActive && overlay == null) stopSelf()
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (overlay != null || !Settings.canDrawOverlays(this)) return
        overlay = MinimapOverlay(this).also { it.show() }
        overlayShowing = true
    }

    private fun hideOverlay() {
        overlay?.hide()
        overlay = null
        overlayShowing = false
    }

    override fun onDestroy() {
        hideOverlay()
        LocationHub.release("service")
        super.onDestroy()
    }

    private fun buildNotification(p: NavProgress?, d: Destination?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val end = PendingIntent.getService(
            this, 1, Intent(this, NavService::class.java).setAction(ACTION_END),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val step = p?.nextStep
        val title = when {
            d == null -> "GPS Auto"
            p == null -> "Finding a route to ${d.name}…"
            p.arrived -> "Arrived at ${d.name}"
            step != null -> "${Units.distance(p.distanceToNext)} · ${step.instruction}"
            else -> d.name
        }
        val text = if (d != null && p != null) "${Units.duration(p.remainingSeconds)} · ${Units.distance(p.remaining)} to ${d.name}"
        else "Minimap is running"
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(step?.let { Instructions.iconRes(it) } ?: R.drawable.act_recenter)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        if (d != null) b.addAction(0, "End route", end)
        return b.build()
    }

    companion object {
        private const val CHANNEL = "nav"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_NAV_START = "nav_start"
        private const val ACTION_NAV_STOP = "nav_stop"
        private const val ACTION_OVERLAY_SHOW = "overlay_show"
        private const val ACTION_OVERLAY_HIDE = "overlay_hide"
        private const val ACTION_END = "end"

        @Volatile var overlayShowing = false
            private set

        fun startNavigation(context: Context) = send(context, ACTION_NAV_START, foreground = true)

        fun stopNavigation(context: Context) = send(context, ACTION_NAV_STOP, foreground = false)

        fun showOverlay(context: Context) = send(context, ACTION_OVERLAY_SHOW, foreground = true)

        fun hideOverlay(context: Context) {
            if (overlayShowing) send(context, ACTION_OVERLAY_HIDE, foreground = false)
        }

        private fun send(context: Context, action: String, foreground: Boolean) {
            val i = Intent(context, NavService::class.java).setAction(action)
            try {
                if (foreground) ContextCompat.startForegroundService(context, i) else context.startService(i)
            } catch (e: Exception) {
                // Starting from the background can be refused; GPS still runs while a screen is open.
            }
        }
    }
}
