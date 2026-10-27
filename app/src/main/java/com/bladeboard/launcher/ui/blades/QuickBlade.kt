package com.bladeboard.launcher.ui.blades

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.ui.BladeHeader
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.ui.QuickIcons

private data class QuickAction(val label: String, val sub: String, val icon: ImageVector, val intent: () -> Intent)

private val actions = listOf(
    QuickAction("Wi-Fi", "Networks", QuickIcons.Wifi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
        else Intent(Settings.ACTION_WIFI_SETTINGS)
    },
    QuickAction("Bluetooth", "Devices", QuickIcons.Bluetooth) { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) },
    QuickAction("Display", "Brightness, dark mode", QuickIcons.Brightness) { Intent(Settings.ACTION_DISPLAY_SETTINGS) },
    QuickAction("Sound", "Volume, ringtones", QuickIcons.Volume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_VOLUME)
        else Intent(Settings.ACTION_SOUND_SETTINGS)
    },
    QuickAction("Battery", "Saver, usage", QuickIcons.Battery) { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) },
    QuickAction("Wallpaper", "Change background", QuickIcons.Wallpaper) {
        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")
    },
    QuickAction("Default home", "Pick launcher", QuickIcons.Home) { Intent(Settings.ACTION_HOME_SETTINGS) },
    QuickAction("All settings", "Everything else", QuickIcons.Settings) { Intent(Settings.ACTION_SETTINGS) },
)

@Composable
fun QuickBlade(repo: AppRepository, settings: LauncherSettings, accent: Color) {
    val palette = settings.palette.blades
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
        BladeHeader("Quick settings", accent)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(actions) { i, action ->
                val color = if (settings.colorfulQuickIcons) palette[i % palette.size] else accent
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))))
                        .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                        .clickable { repo.openIntent(action.intent()) }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconBadge(action.icon, color)
                    Column {
                        Text(action.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(action.sub, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Glossy rounded badge: color gradient, soft top highlight, white glyph with a drop shadow. */
@Composable
private fun IconBadge(icon: ImageVector, color: Color) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(44.dp)
            .shadow(6.dp, shape, ambientColor = color, spotColor = color)
            .clip(shape)
            .background(Brush.linearGradient(listOf(color, color.copy(red = color.red * 0.55f, green = color.green * 0.55f, blue = color.blue * 0.55f))))
            .drawWithContent {
                drawContent()
                // Glass highlight across the top half.
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.35f),
                        0.5f to Color.White.copy(alpha = 0.05f),
                        0.5f to Color.Transparent,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.25f), modifier = Modifier.size(24.dp).offset(y = 1.dp))
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}
