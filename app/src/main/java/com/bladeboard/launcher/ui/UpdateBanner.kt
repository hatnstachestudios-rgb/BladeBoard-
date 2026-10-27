package com.bladeboard.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bladeboard.launcher.update.UpdateManager
import com.bladeboard.launcher.update.UpdateState

/** Shows only while an update needs attention or is in progress. */
@Composable
fun UpdateBanner(updates: UpdateManager, accent: Color, modifier: Modifier = Modifier) {
    val s = updates.state
    val (title, detail) = when (s) {
        is UpdateState.Available -> "Update ready" to "Build ${s.build} is available."
        is UpdateState.Downloading -> "Updating…" to "Downloading build ${s.build}"
        is UpdateState.Installing -> "Installing update…" to "Bladeboard will restart in a moment."
        is UpdateState.NeedsPermission -> "Allow updates" to "Turn on \"Allow from this source\" so Bladeboard can update itself."
        is UpdateState.Failed -> "Update problem" to s.message
        else -> return
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.18f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
            when (s) {
                is UpdateState.Available -> SmallButton("Install", accent) { updates.install() }
                is UpdateState.NeedsPermission -> SmallButton("Allow", accent) { updates.openInstallPermissionSettings() }
                is UpdateState.Failed -> SmallButton("Retry", accent) { updates.check(autoInstall = true) }
                else -> Unit
            }
        }
        if (s is UpdateState.Downloading) {
            LinearProgressIndicator(
                progress = { s.progress },
                color = accent,
                trackColor = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SmallButton(text: String, accent: Color, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
        Text(text, color = Color.Black)
    }
}
