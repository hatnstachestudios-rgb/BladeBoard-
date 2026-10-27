package com.bladeboard.launcher.ui.blades

import android.appwidget.AppWidgetProviderInfo
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.bladeboard.launcher.data.HostedWidget
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.ui.BladeHeader
import com.bladeboard.launcher.widgets.WidgetHostManager

/** Musicolet's package; its widgets are listed first in the picker. */
private const val MUSICOLET_PACKAGE = "in.krosbits.musicolet"

@Composable
fun WidgetSection(
    widgets: WidgetHostManager,
    settings: LauncherSettings,
    defaultPlayerPackage: String?,
    accent: Color,
) {
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BladeHeader("Widgets", accent, Modifier.weight(1f))
        if (settings.musicWidgets.isNotEmpty()) {
            TextButton(onClick = { editing = !editing }) { Text(if (editing) "Done" else "Edit", color = accent) }
        }
    }

    settings.musicWidgets.forEachIndexed { index, w ->
        HostedWidgetView(widgets, w)
        if (editing) {
            WidgetEditBar(
                label = widgets.providerLabel(w.id) ?: "Widget",
                canMoveUp = index > 0,
                canMoveDown = index < settings.musicWidgets.lastIndex,
                accent = accent,
                onShorter = { settings.resizeWidget(w.id, -40) },
                onTaller = { settings.resizeWidget(w.id, 40) },
                onUp = { settings.moveWidget(w.id, -1) },
                onDown = { settings.moveWidget(w.id, 1) },
                onRemove = { widgets.remove(w.id) },
            )
        }
    }

    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text("+ Add widget", color = Color.White)
    }
    if (settings.musicWidgets.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Show Bladeboard player card", fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.showPlayerCard,
                onCheckedChange = { settings.showPlayerCard = it },
                colors = SwitchDefaults.colors(checkedTrackColor = accent),
            )
        }
    }

    if (picking) {
        WidgetPicker(
            widgets = widgets,
            preferredPackage = defaultPlayerPackage,
            accent = accent,
            onPick = { info -> picking = false; widgets.add(info) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun HostedWidgetView(widgets: WidgetHostManager, w: HostedWidget) {
    val context = LocalContext.current
    val view = remember(w.id) { widgets.createView(context, w.id) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(w.heightDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center,
    ) {
        if (view == null) {
            Text("Widget unavailable — the app may have been uninstalled.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        } else {
            val widthDp = maxWidth.value.toInt()
            AndroidView(
                factory = { view },
                update = { v ->
                    // Tell the widget how much room it has so it can pick a fitting layout.
                    @Suppress("DEPRECATION")
                    v.updateAppWidgetSize(Bundle(), widthDp, w.heightDp, widthDp, w.heightDp)
                },
                modifier = Modifier.fillMaxWidth().height(w.heightDp.dp),
            )
        }
    }
}

@Composable
private fun WidgetEditBar(
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    accent: Color,
    onShorter: () -> Unit,
    onTaller: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EditChip("Shorter", true, onShorter)
            EditChip("Taller", true, onTaller)
            EditChip("Up", canMoveUp, onUp)
            EditChip("Down", canMoveDown, onDown)
            EditChip("Remove", true, onRemove, Color(0xFFE8505B))
        }
    }
}

@Composable
private fun EditChip(text: String, enabled: Boolean, onClick: () -> Unit, color: Color = Color.White) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = if (enabled) 0.1f else 0.03f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 12.sp, color = color.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

private data class ProviderItem(val info: AppWidgetProviderInfo, val label: String, val appLabel: String, val preview: ImageBitmap?)

@Composable
private fun WidgetPicker(
    widgets: WidgetHostManager,
    preferredPackage: String?,
    accent: Color,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val density = context.resources.displayMetrics.density
    val focusPackages = setOfNotNull(MUSICOLET_PACKAGE, preferredPackage)
    var showAll by remember { mutableStateOf(false) }

    val all = remember {
        widgets.providers().map { info ->
            val preview = runCatching {
                val d = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
                val maxPx = (220 * density).toInt()
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: maxPx
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: maxPx
                val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
                d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
            }.getOrNull()
            val appLabel = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString()
            }.getOrDefault(info.provider.packageName)
            ProviderItem(info, info.loadLabel(pm), appLabel, preview)
        }.sortedWith(compareBy({ it.info.provider.packageName !in focusPackages }, { it.appLabel.lowercase() }, { it.label.lowercase() }))
    }
    val focused = all.filter { it.info.provider.packageName in focusPackages }
    val shown = if (showAll || focused.isEmpty()) all else focused

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showAll || focused.isEmpty()) "Add a widget" else "Add a ${focused.first().appLabel} widget") },
        text = {
            Column {
                if (focused.isEmpty() && !showAll) {
                    Text(
                        "Musicolet's widgets weren't found. Showing every app's widgets instead.",
                        fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown) { item ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .clickable { onPick(item.info) }
                                .padding(10.dp),
                        ) {
                            if (item.preview != null) {
                                Image(
                                    item.preview, contentDescription = null,
                                    modifier = Modifier.heightIn(max = 120.dp).align(Alignment.CenterHorizontally),
                                )
                            }
                            Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                            Text(item.appLabel, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (focused.isNotEmpty()) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Music widgets only" else "Show all apps", color = accent)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}
