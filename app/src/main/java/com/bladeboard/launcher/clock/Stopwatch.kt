package com.bladeboard.launcher.clock

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Stopwatch whose state is saved, so it keeps counting while you're in other apps or if
 * Android closes the launcher. Times come from the monotonic clock, so changing the
 * phone's time doesn't affect it. A reboot resets it.
 */
class Stopwatch(context: Context) {
    private val prefs = context.getSharedPreferences("bladeboard_stopwatch", Context.MODE_PRIVATE)

    /** elapsedRealtime when the current run started; -1 while paused or reset. */
    private var runStartedAt by mutableLongStateOf(-1L)
    /** Time banked from earlier runs before the latest pause. */
    private var banked by mutableLongStateOf(0L)
    var laps by mutableStateOf<List<Long>>(emptyList())
        private set

    val isRunning: Boolean get() = runStartedAt >= 0
    val hasTime: Boolean get() = isRunning || banked > 0

    init {
        val start = prefs.getLong("start", -1L)
        // A start time in the future means the phone rebooted since; that run can't be recovered.
        val rebooted = start > SystemClock.elapsedRealtime()
        if (!rebooted) {
            runStartedAt = start
            banked = prefs.getLong("banked", 0L)
            laps = prefs.getString("laps", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        } else {
            save()
        }
    }

    fun elapsedMs(): Long = banked + if (isRunning) SystemClock.elapsedRealtime() - runStartedAt else 0L

    fun start() {
        if (isRunning) return
        runStartedAt = SystemClock.elapsedRealtime()
        save()
    }

    fun pause() {
        if (!isRunning) return
        banked = elapsedMs()
        runStartedAt = -1L
        save()
    }

    /** Records the total time at this moment; newest lap first. */
    fun lap() {
        if (!isRunning) return
        laps = listOf(elapsedMs()) + laps
        save()
    }

    fun reset() {
        runStartedAt = -1L
        banked = 0L
        laps = emptyList()
        save()
    }

    private fun save() {
        prefs.edit()
            .putLong("start", runStartedAt)
            .putLong("banked", banked)
            .putString("laps", laps.joinToString(","))
            .apply()
    }
}

/** 1:02:03.45 or 02:03.45 */
fun formatStopwatch(ms: Long): String {
    val hundredths = (ms / 10) % 100
    val s = (ms / 1000) % 60
    val m = (ms / 60_000) % 60
    val h = ms / 3_600_000
    return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, hundredths) else "%02d:%02d.%02d".format(m, s, hundredths)
}
