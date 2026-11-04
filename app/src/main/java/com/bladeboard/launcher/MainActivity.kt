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
import com.bladeboard.launcher.ui.ControllerAction
import com.bladeboard.launcher.games.GameLibrary
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.bladeboard.launcher.ui.LocalControllerMode
import kotlinx.coroutines.flow.MutableSharedFlow
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
    private lateinit var games: GameLibrary

    // Dashboard-level controller buttons (LB/RB/Start), handed to the Compose UI.
    private val controller = MutableSharedFlow<ControllerAction>(extraBufferCapacity = 8)

    // Bumped each time the Home button is pressed while we're already the foreground app.
    private val homePresses = mutableIntStateOf(0)

    // Switches on with gamepad input and off with touch, so text fields stay out of the D-pad path.
    private val controllerMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = LauncherSettings(this)
        repo = AppRepository(this).also { it.start() }
        media = MediaRepository(this)
        widgets = WidgetHostManager(this, settings)
        updates = UpdateManager(this)
        stopwatch = Stopwatch(this)
        games = GameLibrary(this, settings)

        setContent {
            BladeboardTheme(settings) {
              CompositionLocalProvider(LocalControllerMode provides controllerMode.value) {
                BladeDashboard(
                    repo = repo,
                    media = media,
                    widgets = widgets,
                    updates = updates,
                    stopwatch = stopwatch,
                    games = games,
                    settings = settings,
                    homePresses = homePresses.intValue,
                    controller = controller,
                )
              }
            }
        }
    }

    /**
     * Gamepad mapping: LB/RB switch blades, Start returns Home, A selects, B goes back.
     * The D-pad and left stick move focus through Android's built-in handling; Y opens an
     * item's options (handled by the item itself).
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fromPad = event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            KeyEvent.isGamepadButton(event.keyCode)
        if (fromPad && !controllerMode.value) controllerMode.value = true
        val firstDown = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        fun emit(a: ControllerAction): Boolean { if (firstDown) controller.tryEmit(a); return true }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L1 -> emit(ControllerAction.PREVIOUS_BLADE)
            KeyEvent.KEYCODE_BUTTON_R1 -> emit(ControllerAction.NEXT_BLADE)
            KeyEvent.KEYCODE_BUTTON_START -> emit(ControllerAction.HOME)
            KeyEvent.KEYCODE_BUTTON_A -> super.dispatchKeyEvent(event.remapTo(KeyEvent.KEYCODE_DPAD_CENTER))
            KeyEvent.KEYCODE_BUTTON_B -> super.dispatchKeyEvent(event.remapTo(KeyEvent.KEYCODE_BACK))
            else -> super.dispatchKeyEvent(event)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (controllerMode.value) controllerMode.value = false
        return super.dispatchTouchEvent(ev)
    }

    private fun KeyEvent.remapTo(code: Int) =
        KeyEvent(downTime, eventTime, action, code, repeatCount, metaState, deviceId, scanCode, flags, source)

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
