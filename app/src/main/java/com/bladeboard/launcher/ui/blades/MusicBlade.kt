package com.bladeboard.launcher.ui.blades

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bladeboard.launcher.data.AppEntry
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.media.MediaListenerService
import com.bladeboard.launcher.media.MediaRepository
import com.bladeboard.launcher.media.NowPlaying
import com.bladeboard.launcher.ui.BladeHeader
import com.bladeboard.launcher.ui.MediaIcons
import com.bladeboard.launcher.widgets.WidgetHostManager
import kotlinx.coroutines.delay

@Composable
fun MusicBlade(
    media: MediaRepository,
    widgets: WidgetHostManager,
    repo: AppRepository,
    settings: LauncherSettings,
    accent: Color,
) {
    val defaultApp = repo.apps.firstOrNull { it.key == settings.musicAppKey }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // With widgets added, the built-in player card can be hidden so the app's own widgets take over.
        if (settings.showPlayerCard || settings.musicWidgets.isEmpty()) {
            BladeHeader("Now playing", accent)
            val np = media.nowPlaying
            when {
                !media.hasAccess -> AccessCard(repo, accent)
                np == null -> IdleCard(defaultApp, repo, accent)
                else -> PlayerCard(np, media, repo, accent)
            }
            Spacer(Modifier.height(8.dp))
        }

        WidgetSection(widgets, settings, defaultApp?.component?.packageName, accent)

        Spacer(Modifier.height(8.dp))
        BladeHeader("Default player", accent)
        PlayerPicker(repo, settings, defaultApp, accent)
    }
}

@Composable
private fun PlayerCard(np: NowPlaying, media: MediaRepository, repo: AppRepository, accent: Color) {
    // Tick twice a second while playing so the seek bar moves.
    var position by remember { mutableLongStateOf(np.currentPositionMs()) }
    LaunchedEffect(np) {
        while (true) {
            position = np.currentPositionMs()
            if (!np.isPlaying) break
            delay(500)
        }
    }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val sourceApp = repo.apps.firstOrNull { it.component.packageName == np.packageName }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.3f))))
                .clickable { sourceApp?.let(repo::launch) },
            contentAlignment = Alignment.Center,
        ) {
            if (np.art != null) {
                Image(np.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(MediaIcons.Note, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(72.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(np.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val sub = listOf(np.artist, np.album).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotEmpty()) {
            Text(sub, fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        if (np.durationMs > 0) {
            Spacer(Modifier.height(8.dp))
            Slider(
                value = if (dragging) dragValue else position.toFloat(),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { media.seekTo(dragValue.toLong()); dragging = false },
                valueRange = 0f..np.durationMs.toFloat(),
                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(if (dragging) dragValue.toLong() else position), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                Text(formatTime(np.durationMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            TransportButton(MediaIcons.Previous, "Previous", 52.dp, Color.White.copy(alpha = 0.08f)) { media.previous() }
            TransportButton(
                if (np.isPlaying) MediaIcons.Pause else MediaIcons.Play,
                if (np.isPlaying) "Pause" else "Play",
                72.dp, accent,
            ) { media.playPause() }
            TransportButton(MediaIcons.Next, "Next", 52.dp, Color.White.copy(alpha = 0.08f)) { media.next() }
        }

        if (sourceApp != null) {
            TextButton(onClick = { repo.launch(sourceApp) }) { Text("Open ${sourceApp.label}", color = accent) }
        }
    }
}

@Composable
private fun TransportButton(icon: ImageVector, label: String, size: Dp, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun IdleCard(defaultApp: AppEntry?, repo: AppRepository, accent: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(MediaIcons.Note, contentDescription = null, tint = accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(8.dp))
        Text("Nothing playing", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        if (defaultApp != null) {
            Button(onClick = { repo.launch(defaultApp) }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                Text("Open ${defaultApp.label}")
            }
        } else {
            Text("Pick a default player below.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}

@Composable
private fun AccessCard(repo: AppRepository, accent: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.06f)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Allow media control", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "To show and control what's playing in Musicolet, Spotify or any other player, " +
                "turn on Notification access for Bladeboard.",
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f),
        )
        Button(
            onClick = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        ComponentName("com.bladeboard.launcher", MediaListenerService::class.java.name).flattenToString(),
                    )
                } else {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }
                repo.openIntent(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Allow access") }
        Text(
            "Greyed out or \"Restricted setting\"? Android limits this for apps not installed from a store. " +
                "Open App info, tap ⋮ at the top right, choose \"Allow restricted settings\", then try again.",
            fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f),
        )
        OutlinedButton(
            onClick = {
                repo.openIntent(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.bladeboard.launcher"))
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open App info", color = Color.White) }
    }
}

@Composable
private fun PlayerPicker(repo: AppRepository, settings: LauncherSettings, current: AppEntry?, accent: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (current != null) Image(current.icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(current?.label ?: "Choose an app", fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("Change", color = accent, fontSize = 13.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            repo.apps.forEach { app ->
                DropdownMenuItem(
                    text = { Text(app.label) },
                    leadingIcon = { Image(app.icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
                    onClick = { settings.musicAppKey = app.key; open = false },
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
