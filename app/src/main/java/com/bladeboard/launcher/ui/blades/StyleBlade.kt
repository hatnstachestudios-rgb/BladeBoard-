package com.bladeboard.launcher.ui.blades

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bladeboard.launcher.data.AccentSwatches
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.BackgroundStyle
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.data.Palettes
import com.bladeboard.launcher.data.TabFinish
import com.bladeboard.launcher.ui.BladeHeader
import com.bladeboard.launcher.ui.UpdateBanner
import com.bladeboard.launcher.update.UpdateManager
import com.bladeboard.launcher.update.UpdateState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleBlade(
    settings: LauncherSettings,
    repo: AppRepository,
    updates: UpdateManager,
    bladeTitles: List<String>,
    accent: Color,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // --- Theme palettes ---
        BladeHeader("Theme", accent)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Palettes.all.forEachIndexed { i, p ->
                val selected = settings.paletteIndex == i
                Column(
                    Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.verticalGradient(listOf(p.bgTop, p.bgBottom)))
                        .border(
                            BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.15f)),
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { settings.paletteIndex = i }
                        .padding(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        p.blades.forEach { c -> Box(Modifier.size(width = 14.dp, height = 28.dp).background(c)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // --- Background ---
        BladeHeader("Background", accent)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val styles = BackgroundStyle.entries
            styles.forEachIndexed { i, style ->
                SegmentedButton(
                    selected = settings.backgroundStyle == style,
                    onClick = { settings.backgroundStyle = style },
                    shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = accent.copy(alpha = 0.35f)),
                ) { Text(style.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }

        // --- Per-blade accents ---
        BladeHeader("Blade colors", accent)
        Text(
            "Tab is the outer bar; Panel is the inner menu. \"A\" = automatic (palette for tabs, match tab for panels).",
            fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
        )
        bladeTitles.forEachIndexed { blade, title ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title.replaceFirstChar { it.uppercase() }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                SwatchRow(
                    label = "Tab",
                    autoColor = settings.palette.blades[blade % settings.palette.blades.size],
                    selected = settings.accentOverride(blade),
                ) { settings.setAccentOverride(blade, it) }
                SwatchRow(
                    label = "Panel",
                    autoColor = settings.accentFor(blade),
                    selected = settings.panelOverride(blade),
                ) { settings.setPanelOverride(blade, it) }
            }
        }

        // --- Blade shape & finish ---
        BladeHeader("Blade shape", accent)
        LabeledSlider("Edge curve (← bends one way · bends other way →)", settings.curveStrength, -100..100, accent) { settings.curveStrength = it }
        Text("Tab finish", fontSize = 14.sp)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val finishes = TabFinish.entries
            finishes.forEachIndexed { i, f ->
                SegmentedButton(
                    selected = settings.tabFinish == f,
                    onClick = { settings.tabFinish = f },
                    shape = SegmentedButtonDefaults.itemShape(i, finishes.size),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = accent.copy(alpha = 0.35f)),
                ) { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp, maxLines = 1) }
            }
        }

        // --- Home ---
        BladeHeader("Home", accent)
        LabeledSlider("Pinned icon size", settings.pinnedIconSizeDp, 36..96, accent) { settings.pinnedIconSizeDp = it }
        LabeledSlider("Pinned columns", settings.pinnedColumns, 2..6, accent) { settings.pinnedColumns = it }

        // --- Layout ---
        BladeHeader("Layout", accent)
        LabeledSlider("App grid icon size", settings.iconSizeDp, 40..64, accent) { settings.iconSizeDp = it }
        LabeledSlider("App grid columns", settings.gridColumns, 3..6, accent) { settings.gridColumns = it }
        LabeledSlider("Tab width", settings.tabWidthDp, 24..48, accent) { settings.tabWidthDp = it }
        LabeledSwitch("Carousel app drawer", settings.appsCarousel, accent) { settings.appsCarousel = it }
        LabeledSwitch("Curved classic blades", settings.classicBlades, accent) { settings.classicBlades = it }
        LabeledSwitch("Colorful Quick icons", settings.colorfulQuickIcons, accent) { settings.colorfulQuickIcons = it }
        LabeledSwitch("Show app labels", settings.showLabels, accent) { settings.showLabels = it }
        LabeledSwitch("Blade depth effect", settings.bladeDepth, accent) { settings.bladeDepth = it }
        LabeledSwitch("24-hour clock", settings.use24h, accent) { settings.use24h = it }

        // --- Updates ---
        BladeHeader("Updates", accent)
        UpdateBanner(updates, accent)
        LabeledSwitch("Install updates automatically", settings.autoUpdate, accent) { settings.autoUpdate = it }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val status = when (updates.state) {
                is UpdateState.Checking -> "Checking…"
                is UpdateState.UpToDate -> "Up to date"
                else -> ""
            }
            Text("Build ${updates.currentBuild}" + if (status.isNotEmpty()) " · $status" else "", fontSize = 14.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { updates.check(autoInstall = settings.autoUpdate) }) {
                Text("Check now", color = Color.White)
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { repo.openIntent(Intent(Settings.ACTION_HOME_SETTINGS)) },
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Make Bladeboard my home screen") }
        OutlinedButton(onClick = { settings.resetLook() }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset look to defaults", color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SwatchRow(label: String, autoColor: Color, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(48.dp), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Swatch(autoColor, selected == -1, label = "A") { onSelect(-1) }
            AccentSwatches.forEachIndexed { s, c -> Swatch(c, selected == s) { onSelect(s) } }
        }
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, label: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 0.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.7f))
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, accent: Color, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp)
            Text("$value", fontSize = 14.sp, color = accent)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
        )
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent),
        )
    }
}
