package com.bladeboard.launcher.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val build: Int) : UpdateState
    data class Downloading(val build: Int, val progress: Float) : UpdateState
    data object Installing : UpdateState
    /** Android needs "Install unknown apps" turned on for Bladeboard. */
    data class NeedsPermission(val build: Int) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks the repo's "latest" GitHub release (titled "Build N"), downloads its APK and installs
 * it with PackageInstaller. After the first update, Android 12+ installs later ones without asking.
 */
class UpdateManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = context.getSharedPreferences("bladeboard_update", Context.MODE_PRIVATE)

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    val currentBuild: Int = runCatching {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)).toInt()
    }.getOrDefault(0)

    private var latestUrl: String? = null

    init {
        installStatusListener = { message -> state = if (message == null) UpdateState.Idle else UpdateState.Failed(message) }
    }

    /** Called on every resume; only hits the network every [CHECK_INTERVAL_MS]. */
    fun checkIfDue(autoInstall: Boolean) {
        if (state is UpdateState.Downloading || state is UpdateState.Installing) return
        val last = prefs.getLong("last_check", 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(autoInstall)
    }

    fun check(autoInstall: Boolean) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing) return
        scope.launch {
            state = UpdateState.Checking
            val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
            prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
            result.fold(
                onSuccess = { (build, url) ->
                    latestUrl = url
                    if (build > currentBuild) {
                        state = UpdateState.Available(build)
                        if (autoInstall) install()
                    } else {
                        state = UpdateState.UpToDate
                    }
                },
                onFailure = { state = UpdateState.Failed("Couldn't check for updates (${it.message ?: "network error"})") },
            )
        }
    }

    /** Downloads and installs the available build. */
    fun install() {
        val build = when (val s = state) {
            is UpdateState.Available -> s.build
            is UpdateState.NeedsPermission -> s.build
            else -> return
        }
        val url = latestUrl ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedsPermission(build)
            return
        }
        scope.launch {
            state = UpdateState.Downloading(build, 0f)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val apk = File(context.cacheDir, "update.apk")
                    download(url, apk) { p -> state = UpdateState.Downloading(build, p) }
                    commitInstall(apk)
                }
            }
            state = result.fold(
                onSuccess = { UpdateState.Installing },
                onFailure = { UpdateState.Failed("Update failed (${it.message ?: "download error"})") },
            )
        }
    }

    fun openInstallPermissionSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** After the user returns from the permission screen, continue if it was granted. */
    fun onResume() {
        val s = state
        if (s is UpdateState.NeedsPermission && context.packageManager.canRequestPackageInstalls()) install()
    }

    private fun fetchLatest(): Pair<Int, String> {
        val conn = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val build = Regex("""Build (\d+)""").find(json.optString("name"))?.groupValues?.get(1)?.toInt()
                ?: error("release has no build number")
            val assets = json.getJSONArray("assets")
            val url = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url") ?: error("release has no APK")
            return build to url
        } finally {
            conn.disconnect()
        }
    }

    private fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            if (p - lastReported >= 0.02f) { lastReported = p; onProgress(p) }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun commitInstall(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Android 12+: skip the confirmation once Bladeboard is the app that installed itself.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    companion object {
        private const val RELEASE_API =
            "https://api.github.com/repos/hatnstachestudios-rgb/BladeBoard-/releases/tags/latest"
        private const val CHECK_INTERVAL_MS = 30 * 60 * 1000L

        /** Set by the live UpdateManager; the receiver reports install failures through it. */
        internal var installStatusListener: ((String?) -> Unit)? = null
    }
}
