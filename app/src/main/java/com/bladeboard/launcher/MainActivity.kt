package com.bladeboard.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import com.bladeboard.launcher.clock.Stopwatch
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.media.MediaRepository
import com.bladeboard.launcher.ui.BladeDashboard
import com.bladeboard.launcher.ui.theme.BladeboardTheme
import com.bladeboard.launcher.update.UpdateManager
import com.bladeboard.launcher.widgets.WidgetHostManager

class MainActivity : ComponentActivity() {
    private lateinit var repo: AppRepository
    private lateinit var settings: LauncherSettings
    private lateinit var media: MediaRepository
    private lateinit var widgets: WidgetHostManager
    private lateinit var updates: UpdateManager
    private lateinit var stopwatch: Stopwatch

    // Bumped each time the Home button is pressed while we're already the foreground app.
    private val homePresses = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = LauncherSettings(this)
        repo = AppRepository(this).also { it.start() }
        media = MediaRepository(this)
        widgets = WidgetHostManager(this, settings)
        updates = UpdateManager(this)
        stopwatch = Stopwatch(this)

        setContent {
            BladeboardTheme(settings) {
                BladeDashboard(
                    repo = repo,
                    media = media,
                    widgets = widgets,
                    updates = updates,
                    stopwatch = stopwatch,
                    settings = settings,
                    homePresses = homePresses.intValue,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Only reset when Home is pressed while the launcher is already showing,
        // so returning from another app keeps you on the blade you left.
        val alreadyVisible = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        if (intent.hasCategory(Intent.CATEGORY_HOME) && alreadyVisible) homePresses.intValue++
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    @Deprecated("Widget configuration screens still report back through this callback.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        widgets.onActivityResult(requestCode, resultCode)
    }

    override fun onResume() {
        super.onResume()
        // Picks up newly granted notification access and the current player.
        media.refresh()
        updates.onResume()
        updates.checkIfDue(autoInstall = settings.autoUpdate)
    }

    override fun onDestroy() {
        media.stop()
        repo.stop()
        super.onDestroy()
    }
}
