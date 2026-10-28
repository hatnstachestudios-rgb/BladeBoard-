package com.bladeboard.launcher.ui.blades

import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bladeboard.launcher.clock.Stopwatch
import com.bladeboard.launcher.clock.formatStopwatch
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.ui.BladeHeader
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import com.bladeboard.launcher.ui.AutoSizeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.android.awaitFrame
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ClockBlade(stopwatch: Stopwatch, repo: AppRepository, settings: LauncherSettings, accent: Color) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BladeHeader("Alarms", accent)
        NextAlarmCard(repo, settings, accent)
        NewAlarmCard(repo, settings, accent)

        Spacer(Modifier.height(4.dp))
        BladeHeader("Stopwatch", accent)
        StopwatchCard(stopwatch, accent)
    }
}

@Composable
private fun NextAlarmCard(repo: AppRepository, settings: LauncherSettings, accent: Color) {
    val context = LocalContext.current
    // Re-read whenever the blade is shown, so new alarms appear after you set them.
    var next by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            next = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
            delay(5_000)
        }
    }
    val timeFmt = remember(settings.use24h) { DateTimeFormatter.ofPattern(if (settings.use24h) "HH:mm" else "h:mm a") }
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEEE") }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable { repo.openIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            .padding(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text("Next alarm", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            val t = next
            if (t == null) {
                Text("None set", fontSize = 26.sp, fontWeight = FontWeight.Light)
            } else {
                val at = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault())
                AutoSizeText(at.format(timeFmt), maxFontSize = 34.sp, fontWeight = FontWeight.Light)
                Text("${at.format(dayFmt)} · in ${untilText(t)}", fontSize = 13.sp, color = accent)
            }
        }
        Text("All alarms ›", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp))
    }
}

private fun untilText(trigger: Long): String {
    val mins = ((trigger - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    val d = mins / 1440
    val h = (mins % 1440) / 60
    val m = mins % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

@Composable
private fun NewAlarmCard(repo: AppRepository, settings: LauncherSettings, accent: Color) {
    val now = remember { LocalTime.now() }
    var hour by remember { mutableIntStateOf(now.hour) }
    var minute by remember { mutableIntStateOf((now.minute / 5 * 5 + 5) % 60) }
    var label by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(setOf<Int>()) }
    var saved by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("New alarm", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))

        // Hour : minute, each with up/down arrows (hold to scroll fast), plus AM/PM in 12-hour mode.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val shownHour = if (settings.use24h) hour else (hour % 12).let { if (it == 0) 12 else it }
            Stepper(
                value = if (settings.use24h) "%02d".format(shownHour) else "$shownHour",
                accent = accent,
                onUp = { hour = (hour + 1) % 24 },
                onDown = { hour = (hour + 23) % 24 },
                modifier = Modifier.weight(1f),
            )
            Text(":", fontSize = 32.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(horizontal = 2.dp))
            Stepper(
                value = "%02d".format(minute),
                accent = accent,
                onUp = { minute = (minute + 1) % 60 },
                onDown = { minute = (minute + 59) % 60 },
                modifier = Modifier.weight(1f),
            )
            if (!settings.use24h) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.5.dp, accent, RoundedCornerShape(10.dp))
                        .clickable { hour = (hour + 12) % 24 }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) { Text(if (hour < 12) "AM" else "PM", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        // Repeat days (Calendar.SUNDAY = 1 … SATURDAY = 7).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEachIndexed { i, d ->
                val day = i + 1
                val on = day in days
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (on) accent else Color.White.copy(alpha = 0.08f))
                        .clickable { days = if (on) days - day else days + day },
                    contentAlignment = Alignment.Center,
                ) { Text(d, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.Black else Color.White) }
            }
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(40) },
            placeholder = { Text("Label (optional)") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, cursorColor = accent, unfocusedBorderColor = Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, hour)
                    .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                if (label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label.trim())
                if (days.isNotEmpty()) intent.putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days.sorted()))
                repo.openIntent(intent)
                val fmt = DateTimeFormatter.ofPattern(if (settings.use24h) "HH:mm" else "h:mm a")
                saved = "Alarm set for ${LocalTime.of(hour, minute).format(fmt)}"
                label = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set alarm", color = Color.Black, fontWeight = FontWeight.SemiBold) }

        saved?.let { Text(it, fontSize = 12.sp, color = accent, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

@Composable
private fun Stepper(
    value: String,
    accent: Color,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RepeatArrow("▲", accent, onUp)
        AutoSizeText(
            value,
            maxFontSize = 52.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
        RepeatArrow("▼", accent, onDown)
    }
}

/** Arrow that steps once on tap and keeps stepping, faster and faster, while held. */
@Composable
private fun RepeatArrow(symbol: String, accent: Color, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    val repeater = scope.launch {
                        delay(400)
                        var wait = 120L
                        while (true) {
                            step()
                            delay(wait)
                            wait = (wait - 10).coerceAtLeast(40)
                        }
                    }
                    tryAwaitRelease()
                    repeater.cancel()
                })
            },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = accent, fontSize = 16.sp) }
}

@Composable
private fun StopwatchCard(stopwatch: Stopwatch, accent: Color) {
    // Redraw every frame while running.
    var elapsed by remember { mutableLongStateOf(stopwatch.elapsedMs()) }
    LaunchedEffect(stopwatch.isRunning, stopwatch.hasTime) {
        elapsed = stopwatch.elapsedMs()
        while (stopwatch.isRunning) {
            awaitFrame()
            elapsed = stopwatch.elapsedMs()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AutoSizeText(
            formatStopwatch(elapsed),
            maxFontSize = 48.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stopwatch.isRunning) {
                OutlinedButton(onClick = { stopwatch.lap() }) { Text("Lap", color = Color.White) }
                Button(onClick = { stopwatch.pause() }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Text("Pause", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            } else {
                if (stopwatch.hasTime) {
                    OutlinedButton(onClick = { stopwatch.reset() }) { Text("Reset", color = Color.White) }
                }
                Button(onClick = { stopwatch.start() }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Text(if (stopwatch.hasTime) "Resume" else "Start", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (stopwatch.laps.isNotEmpty()) {
            val laps = stopwatch.laps
            // Lap durations: each total minus the previous (older) total.
            val splits = laps.mapIndexed { i, total -> total - (laps.getOrNull(i + 1) ?: 0L) }
            val fastest = if (splits.size > 1) splits.min() else null
            val slowest = if (splits.size > 1) splits.max() else null
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                laps.forEachIndexed { i, total ->
                    val split = splits[i]
                    val color = when (split) {
                        fastest -> Color(0xFF4CC38A)
                        slowest -> Color(0xFFE8505B)
                        else -> Color.White
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("#${laps.size - i}", fontSize = 13.sp, maxLines = 1, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(0.6f))
                        Text(formatStopwatch(split), fontSize = 12.sp, maxLines = 1, softWrap = false, fontFamily = FontFamily.Monospace, color = color, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text(formatStopwatch(total), fontSize = 12.sp, maxLines = 1, softWrap = false, fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}
