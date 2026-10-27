package com.bladeboard.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bladeboard.launcher.data.AppEntry
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.BackgroundStyle
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.ui.theme.BladeSurface
import kotlin.math.cos
import kotlin.math.sin

/**
 * One blade: a tab strip on its left edge plus the content panel.
 *
 * Classic style: solid-color tab, curved left edge (bows right on the left stack and the open
 * blade, bows left on the right stack, so the open panel flares at its corners), and a full
 * color gradient panel with arched top and bottom. Glass style: straight dark translucent panel.
 */
@Composable
fun Blade(
    title: String,
    accent: Color,
    panel: Color,
    active: Boolean,
    onRight: Boolean,
    classic: Boolean,
    tabWidth: Dp,
    curve: Dp,
    onTabClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tabText = if (accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    // +1 = edge bows toward the right, -1 = toward the left. Animated so blades bend as they slide.
    val bulge by animateFloatAsState(if (onRight) -1f else 1f, label = "bladeBulge")
    val density = LocalDensity.current
    val curvePx = if (classic) with(density) { curve.toPx() } else 0f
    val shape = remember(bulge, curvePx) { BladeEdgeShape(bulge, curvePx) }
    // Shift the tab label so it stays centered on the visible part of the curved strip.
    val labelShift = if (classic) curve * ((1f + bulge) / 2f) else 0.dp

    Row(modifier.shadow(if (active) 28.dp else 10.dp, shape).clip(shape)) {
        Box(
            Modifier
                .width(tabWidth)
                .fillMaxHeight()
                .background(if (classic) Brush.horizontalGradient(listOf(accent, accent)) else Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.78f))))
                .clickable(onClick = onTabClick)
                .padding(start = labelShift),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.uppercase(),
                color = tabText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                maxLines = 1,
                softWrap = false,
                // Left stack reads top-to-bottom, right stack bottom-to-top, facing the open blade.
                modifier = Modifier.verticalText(clockwise = classic && !onRight),
            )
        }
        val body = if (classic) {
            val arch = with(density) { (curve * 1.6f).toPx() }
            Modifier
                .background(panel.darken(0.32f))
                .clip(remember(arch) { ArchShape(arch) })
                .background(
                    Brush.linearGradient(
                        listOf(panel.lighten(0.18f), panel, panel.darken(0.5f)),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    )
                )
                .drawBehind {
                    // Soft sheen in the upper left, plus a gentle scrim at the bottom for legibility.
                    drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.2f, size.height * 0.1f), radius = size.maxDimension * 0.7f))
                    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)))
                }
        } else {
            Modifier
                .background(BladeSurface)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent)))
        }
        Box(Modifier.weight(1f).fillMaxHeight().then(body)) {
            // Only compose content for the visible blade; fade it in and out.
            val alpha by animateFloatAsState(if (active) 1f else 0f, label = "bladeContent")
            if (active || alpha > 0.01f) {
                Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) { content() }
            }
        }
    }
}

/** Blade outline whose left edge is a quadratic curve; [bulge] in -1..1 picks the direction. */
private class BladeEdgeShape(private val bulge: Float, private val curve: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (curve <= 0f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val ends = curve * (1f - bulge) / 2f
        val middle = curve * (1f + bulge) / 2f
        val control = 2f * middle - ends
        val path = Path().apply {
            moveTo(ends, 0f)
            quadraticBezierTo(control, size.height / 2f, ends, size.height)
            lineTo(size.width, size.height)
            lineTo(size.width, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Panel whose top and bottom edges arch toward the center, leaving flared corners. */
private class ArchShape(private val arch: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            quadraticBezierTo(w / 2f, 2f * arch, w, 0f)
            lineTo(w, h)
            quadraticBezierTo(w / 2f, h - 2f * arch, 0f, h)
            close()
        }
        return Outline.Generic(path)
    }
}

private fun Color.darken(amount: Float) = Color(red * (1 - amount), green * (1 - amount), blue * (1 - amount), alpha)
private fun Color.lighten(amount: Float) = Color(red + (1 - red) * amount, green + (1 - green) * amount, blue + (1 - blue) * amount, alpha)

/** Rotates text 90° (clockwise = reads top-to-bottom) and swaps its measured width/height. */
fun Modifier.verticalText(clockwise: Boolean = false): Modifier = this
    .layout { measurable, constraints ->
        val maxW = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val p = measurable.measure(Constraints(maxWidth = maxW))
        layout(p.height, p.width) {
            p.place(x = -(p.width - p.height) / 2, y = (p.width - p.height) / 2)
        }
    }
    .rotate(if (clockwise) 90f else -90f)

/** Gradient / solid / wallpaper backdrop, with a slow drifting glow in gradient mode. */
fun Modifier.dashboardBackground(settings: LauncherSettings): Modifier = composed {
    val palette = settings.palette
    when (settings.backgroundStyle) {
        BackgroundStyle.WALLPAPER -> background(Color.Black.copy(alpha = 0.25f))
        BackgroundStyle.SOLID -> background(palette.bgTop)
        BackgroundStyle.GRADIENT -> {
            val t by rememberInfiniteTransition(label = "glow").animateFloat(
                initialValue = 0f,
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
                label = "glowAngle",
            )
            val glow = settings.accentFor(0)
            background(Brush.verticalGradient(listOf(palette.bgTop, palette.bgBottom)))
                .drawBehind {
                    val center = Offset(
                        size.width * (0.5f + 0.3f * cos(t)),
                        size.height * (0.45f + 0.2f * sin(t)),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.22f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.6f,
                        ),
                        radius = size.maxDimension * 0.6f,
                        center = center,
                    )
                }
        }
    }
}

/** App icon + label with a long-press menu (pin, info, uninstall). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(app: AppEntry, repo: AppRepository, settings: LauncherSettings, accent: Color) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { repo.launch(app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            Image(bitmap = app.icon, contentDescription = app.label, modifier = Modifier.size(settings.iconSizeDp.dp))
            if (settings.showLabels) {
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
        AppMenu(app, repo, settings, menuOpen) { menuOpen = false }
    }
}

/** Long-press options shared by every app surface. */
@Composable
fun AppMenu(app: AppEntry, repo: AppRepository, settings: LauncherSettings, expanded: Boolean, onDismiss: () -> Unit) {
    val pinned = settings.isFavorite(app.key)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (pinned) "Unpin from Home" else "Pin to Home") },
            onClick = { settings.toggleFavorite(app.key); onDismiss() },
        )
        DropdownMenuItem(text = { Text("App info") }, onClick = { repo.openAppInfo(app); onDismiss() })
        DropdownMenuItem(text = { Text("Uninstall") }, onClick = { repo.uninstall(app); onDismiss() })
    }
}

/** Small section header used across blades. */
@Composable
fun BladeHeader(text: String, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(width = 18.dp, height = 3.dp).background(accent))
        Text(text.uppercase(), fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
    }
}
