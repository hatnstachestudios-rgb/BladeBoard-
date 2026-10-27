package com.bladeboard.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    /** Average color of the icon, used to tint carousel cards. */
    val tint: Color,
)

/** Loads launchable apps and keeps the list fresh as apps are installed or removed. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadJob: Job? = null

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(p: Array<out String>?, u: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(p: Array<out String>?, u: UserHandle?, replacing: Boolean) = refresh()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
        scope.cancel()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch {
            apps = withContext(Dispatchers.IO) { load() }
        }
    }

    private fun load(): List<AppEntry> {
        val user = Process.myUserHandle()
        val iconPx = (96 * context.resources.displayMetrics.density).toInt()
        return launcherApps.getActivityList(null, user)
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                val bmp = info.getBadgedIcon(0).toBitmap(iconPx, iconPx)
                AppEntry(
                    key = info.componentName.flattenToString(),
                    label = info.label.toString(),
                    component = info.componentName,
                    user = info.user,
                    icon = bmp.asImageBitmap(),
                    tint = averageColor(bmp),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Mean of the opaque pixels, sampled from a tiny downscale; boosted a little so cards aren't muddy. */
    private fun averageColor(bmp: Bitmap): Color {
        val small = Bitmap.createScaledBitmap(bmp, 12, 12, true)
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (x in 0 until 12) for (y in 0 until 12) {
            val p = small.getPixel(x, y)
            if (android.graphics.Color.alpha(p) < 128) continue
            r += android.graphics.Color.red(p); g += android.graphics.Color.green(p); b += android.graphics.Color.blue(p); n++
        }
        if (n == 0) return Color(0xFF3A4250)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)
        hsv[2] = hsv[2].coerceIn(0.35f, 0.85f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    fun launch(app: AppEntry) {
        runCatching { launcherApps.startMainActivity(app.component, app.user, null, null) }
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openIntent(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
