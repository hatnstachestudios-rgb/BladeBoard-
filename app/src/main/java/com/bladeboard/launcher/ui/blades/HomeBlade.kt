package com.bladeboard.launcher.ui.blades

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.ui.AppTile
import com.bladeboard.launcher.ui.BladeHeader
import com.bladeboard.launcher.ui.UpdateBanner
import com.bladeboard.launcher.update.UpdateManager
import com.bladeboard.launcher.update.UpdateState
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeBlade(repo: AppRepository, settings: LauncherSettings, updates: UpdateManager, accent: Color) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val timeFmt = remember(settings.use24h) { DateTimeFormatter.ofPattern(if (settings.use24h) "HH:mm" else "h:mm") }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }

    val pinned = settings.favorites.mapNotNull { key -> repo.apps.firstOrNull { it.key == key } }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        UpdateBanner(updates, accent, Modifier.padding(bottom = 16.dp))
        Column(
            Modifier.clickable { repo.openIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
        ) {
            Text(now.format(timeFmt), fontSize = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
            Text(now.format(dateFmt), fontSize = 16.sp, color = accent, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(32.dp))
        BladeHeader("Pinned", accent)
        Spacer(Modifier.height(8.dp))

        if (pinned.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Long-press any app on the Apps blade\nand choose \"Pin to Home\".",
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.gridColumns),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(pinned, key = { it.key }) { app -> AppTile(app, repo, settings, accent) }
            }
        }
    }
}
