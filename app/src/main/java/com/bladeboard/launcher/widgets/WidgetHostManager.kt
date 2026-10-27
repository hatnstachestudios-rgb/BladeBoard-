package com.bladeboard.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.bladeboard.launcher.data.LauncherSettings

/**
 * Hosts other apps' home-screen widgets (e.g. Musicolet's player and queue) inside Bladeboard.
 * Handles the Android bind-permission and widget-configuration flows.
 * Create it in Activity.onCreate (it registers an activity-result launcher).
 */
class WidgetHostManager(private val activity: ComponentActivity, private val settings: LauncherSettings) {
    private val host = AppWidgetHost(activity.applicationContext, HOST_ID)
    private val manager = AppWidgetManager.getInstance(activity)

    private var pendingId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

    private val bindLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrFinish() else cancelPending()
    }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    fun providers(): List<AppWidgetProviderInfo> =
        manager.getInstalledProvidersForProfile(Process.myUserHandle())

    /** Starts adding a widget; may show the system "allow Bladeboard to create widgets" dialog. */
    fun add(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        pendingInfo = info
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrFinish()
        } else {
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            )
        }
    }

    private fun configureOrFinish() {
        val info = pendingInfo ?: return
        val configOptional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (info.configure != null && !configOptional) {
            try {
                host.startAppWidgetConfigureActivityForResult(activity, pendingId, 0, REQ_CONFIGURE, null)
            } catch (e: Exception) {
                finish()
            }
        } else {
            finish()
        }
    }

    /** Forward from Activity.onActivityResult. */
    fun onActivityResult(requestCode: Int, resultCode: Int) {
        if (requestCode != REQ_CONFIGURE) return
        if (resultCode == Activity.RESULT_OK) finish() else cancelPending()
    }

    private fun finish() {
        val info = pendingInfo ?: return
        val density = activity.resources.displayMetrics.density
        val heightDp = (info.minHeight / density).toInt().coerceIn(110, 420)
        settings.addWidget(pendingId, heightDp)
        pendingId = -1
        pendingInfo = null
    }

    private fun cancelPending() {
        if (pendingId != -1) host.deleteAppWidgetId(pendingId)
        pendingId = -1
        pendingInfo = null
    }

    fun remove(id: Int) {
        host.deleteAppWidgetId(id)
        settings.removeWidget(id)
    }

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = manager.getAppWidgetInfo(id) ?: return null
        return host.createView(context, id, info).apply { setPadding(0, 0, 0, 0) }
    }

    fun providerLabel(id: Int): String? =
        manager.getAppWidgetInfo(id)?.loadLabel(activity.packageManager)

    companion object {
        private const val HOST_ID = 0x0B1A
        private const val REQ_CONFIGURE = 0x0C0F
    }
}
