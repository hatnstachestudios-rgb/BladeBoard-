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

private data class QuickAction(val label: String, val sub: String, val intent: () -> Intent)

private val actions = listOf(
    QuickAction("Wi-Fi", "Networks") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_WIFI)
        else Intent(Settings.ACTION_WIFI_SETTINGS)
    },
    QuickAction("Bluetooth", "Devices") { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) },
    QuickAction("Display", "Brightness, dark mode") { Intent(Settings.ACTION_DISPLAY_SETTINGS) },
    QuickAction("Sound", "Volume, ringtones") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(Settings.Panel.ACTION_VOLUME)
        else Intent(Settings.ACTION_SOUND_SETTINGS)
    },
    QuickAction("Battery", "Saver, usage") { Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS) },
    QuickAction("Wallpaper", "Change background") {
        Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Set wallpaper")
    },
    QuickAction("Default home", "Pick launcher") { Intent(Settings.ACTION_HOME_SETTINGS) },
    QuickAction("All settings", "Everything else") { Intent(Settings.ACTION_SETTINGS) },
)

@Composable
fun QuickBlade(repo: AppRepository, accent: Color) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
        BladeHeader("Quick settings", accent)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(actions) { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { repo.openIntent(action.intent()) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
                    Column(Modifier.padding(horizontal = 14.dp)) {
                        Text(action.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(action.sub, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}
