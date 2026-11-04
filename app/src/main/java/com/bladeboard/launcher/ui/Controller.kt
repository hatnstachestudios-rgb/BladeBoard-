package com.bladeboard.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/** Controller buttons that act on the whole dashboard rather than the focused item. */
enum class ControllerAction { PREVIOUS_BLADE, NEXT_BLADE, HOME }

/**
 * True when the open blade is wide enough (landscape, tablets, external screens) for
 * side-by-side layouts.
 */
val LocalWideBlade = staticCompositionLocalOf { false }

/** True after a gamepad button is pressed, false again after the screen is touched. */
val LocalControllerMode = compositionLocalOf { false }

/**
 * Highlights an item while it has controller/keyboard focus: an accent ring and a slight lift.
 * Place it before the item's clickable so it can see that focus.
 */
fun Modifier.focusRing(color: Color, shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (focused) 1.05f else 1f, label = "focusLift")
    this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = lift; scaleY = lift }
        .border(if (focused) 3.dp else 0.dp, if (focused) color else Color.Transparent, shape)
}

/** Runs [action] when Y (or the keyboard Menu key) is pressed on the focused item. */
fun Modifier.onOptionsButton(action: () -> Unit): Modifier = onKeyEvent {
    if (it.type == KeyEventType.KeyDown && (it.key == Key.ButtonY || it.key == Key.Menu)) {
        action(); true
    } else false
}
