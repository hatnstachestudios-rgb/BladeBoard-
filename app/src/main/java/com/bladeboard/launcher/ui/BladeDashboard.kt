package com.bladeboard.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.media.MediaRepository
import com.bladeboard.launcher.update.UpdateManager
import com.bladeboard.launcher.widgets.WidgetHostManager
import com.bladeboard.launcher.ui.blades.AppsBlade
import com.bladeboard.launcher.ui.blades.HomeBlade
import com.bladeboard.launcher.ui.blades.MusicBlade
import com.bladeboard.launcher.ui.blades.ClockBlade
import com.bladeboard.launcher.clock.Stopwatch
import com.bladeboard.launcher.ui.blades.QuickBlade
import com.bladeboard.launcher.ui.blades.GamesBlade
import com.bladeboard.launcher.games.GameLibrary
import com.bladeboard.launcher.data.BladeId
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import com.bladeboard.launcher.ui.blades.StyleBlade


/**
 * The blade stack. Every blade is the same width; blades at or left of the selected one sit
 * at the left edge (only their colored tab peeking out), blades to its right are pushed to the
 * right edge. Changing the selection slides them all into their new positions.
 */
@Composable
fun BladeDashboard(
    repo: AppRepository,
    media: MediaRepository,
    widgets: WidgetHostManager,
    updates: UpdateManager,
    stopwatch: Stopwatch,
    games: GameLibrary,
    settings: LauncherSettings,
    homePresses: Int,
    controller: Flow<ControllerAction>,
) {
    val blades = settings.visibleBlades
    var selected by rememberSaveable { mutableIntStateOf(0) }
    if (selected > blades.lastIndex) selected = blades.lastIndex
    val go: (Int) -> Unit = { selected = it.coerceIn(0, settings.visibleBlades.lastIndex) }

    // Home button while already on the launcher → back to the Home blade.
    LaunchedEffect(homePresses) { if (homePresses > 0) selected = 0 }

    // LB / RB / Start. After the blade slides over, focus its first item so the D-pad
    // carries on from there.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(controller) {
        controller.collect { action ->
            when (action) {
                ControllerAction.PREVIOUS_BLADE -> go(selected - 1)
                ControllerAction.NEXT_BLADE -> go(selected + 1)
                ControllerAction.HOME -> selected = 0
            }
            focusManager.clearFocus(force = true)
            delay(420)
            focusManager.moveFocus(FocusDirection.Next)
        }
    }
    // Back never leaves the launcher; it returns to Home.
    BackHandler { selected = 0 }

    Box(Modifier.fillMaxSize().dashboardBackground(settings)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val count = blades.size
            val tabW = settings.tabWidthDp.dp
            val fullW = maxWidth
            // Edge curvature scales with screen height so tall phones get a gentle, long arc.
            // Signed: the Style slider can bend edges either way (0 = straight).
            val curve = (maxHeight * 0.024f).coerceAtMost(22.dp) * (settings.curveStrength / 100f)
            val curveSize = if (curve < 0.dp) -curve else curve
            val bladeW = fullW - tabW * (count - 1)
            val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
            var dragTotal by remember { mutableFloatStateOf(0f) }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(count) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragTotal < -swipeThreshold) go(selected + 1)
                                else if (dragTotal > swipeThreshold) go(selected - 1)
                                dragTotal = 0f
                            },
                            onDragCancel = { dragTotal = 0f },
                        ) { _, amount -> dragTotal += amount }
                    }
            ) {
                blades.forEachIndexed { i, blade ->
                    val title = blade.title
                    val slot = blade.slot
                    val active = i == selected
                    val targetX = if (i <= selected) tabW * i else fullW - tabW * (count - i)
                    val x by animateDpAsState(
                        targetValue = targetX,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                        label = "bladeX$i",
                    )
                    // Inactive blades lean back slightly for a sense of depth.
                    val lean by animateFloatAsState(
                        targetValue = when {
                            !settings.bladeDepth || active -> 0f
                            i < selected -> 10f
                            else -> -10f
                        },
                        label = "bladeLean$i",
                    )
                    val accent = settings.accentFor(slot)

                    Blade(
                        title = title,
                        accent = accent,
                        panel = settings.panelFor(slot),
                        active = active,
                        onRight = i > selected,
                        classic = settings.classicBlades,
                        tabWidth = tabW,
                        curve = curve,
                        finish = settings.tabFinish,
                        onTabClick = { go(i) },
                        modifier = Modifier
                            .offset(x = x)
                            .width(bladeW + curveSize)
                            .fillMaxHeight()
                            .zIndex(i.toFloat())
                            .graphicsLayer {
                                rotationY = lean
                                cameraDistance = 30f * density
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            },
                    ) {
                        CompositionLocalProvider(LocalWideBlade provides (bladeW >= 520.dp)) {
                            when (blade) {
                                BladeId.HOME -> HomeBlade(repo, settings, updates, accent)
                                BladeId.MUSIC -> MusicBlade(media, widgets, repo, settings, accent)
                                BladeId.GAMES -> GamesBlade(repo, settings, games, accent)
                                BladeId.APPS -> AppsBlade(repo, settings, accent)
                                BladeId.QUICK -> QuickBlade(repo, settings, accent)
                                BladeId.CLOCK -> ClockBlade(stopwatch, repo, settings, accent)
                                BladeId.STYLE -> StyleBlade(settings, repo, updates, accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
