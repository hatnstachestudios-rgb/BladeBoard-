package com.hatnstache.bladetunes.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.hatnstache.bladetunes.AppViewModel
import com.hatnstache.bladetunes.data.Song
import com.hatnstache.bladetunes.data.formatTime
import com.hatnstache.bladetunes.playback.PlayerState

private val IpodBlack = Color(0xFF111111)
private val IpodGrayText = Color(0xFF3B3B3B)
private const val ART_TILT = 16f
private val ART_SIZE = 300.dp

/**
 * Now Playing blade: a classic-iPod style screen stretched to a tall phone,
 * with the media controls sitting underneath it.
 */
@Composable
fun NowPlayingBlade(vm: AppViewModel) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val ratings by vm.ratings.ratings.collectAsStateWithLifecycle()
    val song = state.songId?.let { library.byId[it] }

    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 10.dp)) {
        IpodScreen(
            song = song,
            state = state,
            rating = song?.let { ratings[it.id] } ?: 0,
            onRate = { stars -> song?.let { vm.setRating(it, stars) } },
            onSeek = vm.player::seekTo,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        MediaControls(
            state = state,
            onShuffle = vm.player::toggleShuffle,
            onPrevious = vm.player::previous,
            onPlayPause = vm.player::togglePlay,
            onNext = vm.player::next,
            onRepeat = vm.player::cycleRepeat,
        )
        Spacer(Modifier.height(10.dp))
    }
}

// ---------------------------------------------------------------- the iPod screen

@Composable
private fun IpodScreen(
    song: Song?,
    state: PlayerState,
    rating: Int,
    onRate: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dark bezel around a white LCD, like the reference.
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1E1E1E))))
            .padding(7.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
        ) {
            IpodTitleBar(isPlaying = state.isPlaying)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 14.dp)
            ) {
                // Art gets the full screen width so it can reach 300×300.
                ReflectedArt(song, Modifier.weight(1f).fillMaxWidth())

                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(10.dp))
                    IpodText(song?.title ?: "Not Playing", 22.sp, FontWeight.Bold, IpodBlack)
                    IpodText(song?.artist ?: "Pick a song from any blade", 17.sp, FontWeight.SemiBold, IpodGrayText)
                    IpodText(song?.album ?: "", 17.sp, FontWeight.SemiBold, IpodGrayText)
                    Spacer(Modifier.height(6.dp))
                    StarRating(rating = rating, enabled = song != null, onRate = onRate)
                    Spacer(Modifier.height(4.dp))
                    IpodText(
                        if (state.count > 0) "${state.index + 1} of ${state.count}" else " ",
                        16.sp, FontWeight.SemiBold, IpodBlack,
                    )
                    Spacer(Modifier.height(14.dp))
                    IpodProgress(
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        onSeek = onSeek,
                    )
                }
            }
        }
    }
}

@Composable
private fun IpodText(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, color: Color) {
    // iPods clipped long names at the screen edge rather than adding "…"
    Text(
        text, fontSize = size, fontWeight = weight, color = color,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
        letterSpacing = (-0.2).sp,
    )
}

@Composable
private fun IpodTitleBar(isPlaying: Boolean) {
    val battery = rememberBatteryLevel()
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFFFFFFFF),
                    0.45f to Color(0xFFEDEDED),
                    0.5f to Color(0xFFD9D9D9),
                    1f to Color(0xFFC2C2C2),
                )
            )
            .drawBehind {
                drawLine(Color(0xFF8E8E8E), Offset(0f, size.height), Offset(size.width, size.height), 2f)
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Now Playing", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = IpodBlack,
            modifier = Modifier.weight(1f),
        )
        PlayStateGlyph(isPlaying)
        Spacer(Modifier.width(8.dp))
        BatteryGlyph(battery)
    }
}

/** The little blue ▶ (or ❚❚) in the title bar. */
@Composable
private fun PlayStateGlyph(isPlaying: Boolean) {
    val blue = Brush.verticalGradient(listOf(Color(0xFF9CC8F5), Color(0xFF3E86DB)))
    Canvas(Modifier.size(width = 16.dp, height = 18.dp)) {
        if (isPlaying) {
            val p = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(0f, size.height)
                close()
            }
            drawPath(p, blue)
        } else {
            val bar = size.width * 0.32f
            drawRect(blue, Offset(size.width * 0.08f, 0f), Size(bar, size.height))
            drawRect(blue, Offset(size.width * 0.6f, 0f), Size(bar, size.height))
        }
    }
}

@Composable
private fun BatteryGlyph(level: Int) {
    Canvas(Modifier.size(width = 34.dp, height = 16.dp)) {
        val nub = size.width * 0.08f
        val bodyW = size.width - nub
        val r = CornerRadius(3f, 3f)
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFFF5F5F5), Color(0xFFBDBDBD))),
            size = Size(bodyW, size.height), cornerRadius = r,
        )
        val inset = 3f
        val fillW = (bodyW - inset * 2) * (level.coerceIn(0, 100) / 100f)
        val fill = if (level <= 20) listOf(Color(0xFFFFA2A2), Color(0xFFD83A3A))
        else listOf(Color(0xFFC9F5A8), Color(0xFF58B92C))
        drawRoundRect(
            Brush.verticalGradient(fill),
            topLeft = Offset(inset, inset),
            size = Size(fillW, size.height - inset * 2),
            cornerRadius = CornerRadius(2f, 2f),
        )
        drawRoundRect(
            Color(0xFF7A7A7A), size = Size(bodyW, size.height), cornerRadius = r,
            style = Stroke(width = 1.5f),
        )
        drawRect(
            Color(0xFF8A8A8A),
            topLeft = Offset(bodyW, size.height * 0.3f),
            size = Size(nub, size.height * 0.4f),
        )
    }
}

@Composable
fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(100) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val l = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && s > 0) level = l * 100 / s
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { receiver.onReceive(context, it) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return level
}

/** Album art tilted in 3D with the fading mirror reflection under it. */
@Composable
private fun ReflectedArt(song: Song?, modifier: Modifier = Modifier) {
    val art = rememberArt(song, big = true)
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        val reflectionRatio = 0.22f
        // 300×300 target; only shrinks if the screen genuinely has less room.
        val side: Dp = min(ART_SIZE, min(maxWidth, maxHeight / (1f + reflectionRatio)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(side)
                    .graphicsLayer {
                        rotationY = ART_TILT
                        cameraDistance = 14f * density
                    }
            ) {
                AlbumArt(song, Modifier.fillMaxSize(), big = true, corner = 0.dp)
            }
            Canvas(
                Modifier
                    .size(width = side, height = side * reflectionRatio)
                    .graphicsLayer {
                        rotationY = ART_TILT
                        cameraDistance = 14f * density
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                if (art != null) drawReflection(art, this)
                else drawRect(Color(0xFF9AA39A))
                // Fade the reflection out toward the bottom.
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent)),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }
}

private fun drawReflection(img: ImageBitmap, scope: androidx.compose.ui.graphics.drawscope.DrawScope) = with(scope) {
    // Use the bottom slice of the (center-cropped, square) artwork, flipped upside down.
    val sq = minOf(img.width, img.height)
    val x0 = (img.width - sq) / 2
    val y0 = (img.height - sq) / 2
    val srcH = (sq * (size.height / size.width)).toInt().coerceIn(1, sq)
    withTransform({ scale(1f, -1f, pivot = Offset(size.width / 2f, size.height / 2f)) }) {
        drawImage(
            img,
            srcOffset = IntOffset(x0, y0 + sq - srcH),
            srcSize = IntSize(sq, srcH),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

@Composable
private fun StarRating(rating: Int, enabled: Boolean, onRate: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            Box(
                Modifier
                    .size(28.dp)
                    .clickable(enabled = enabled) { onRate(if (rating == i) 0 else i) },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (i <= rating) {
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFF4A4A4A), modifier = Modifier.size(24.dp))
                } else {
                    // iPods showed dots for the unrated spots
                    Box(
                        Modifier
                            .padding(start = 9.dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB5B5B5))
                    )
                }
            }
        }
    }
}

@Composable
private fun IpodProgress(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = when {
        dragging -> dragFraction
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownPos = if (dragging) (dragFraction * durationMs).toLong() else positionMs

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTime(shownPos), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IpodBlack)
        Spacer(Modifier.width(10.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(16.dp)
                .pointerInputSeek(
                    durationMs = durationMs,
                    onDragStart = { f -> dragging = true; dragFraction = f },
                    onDrag = { f -> dragFraction = f },
                    onDragEnd = { dragging = false; onSeek((dragFraction * durationMs).toLong()) },
                    onTap = { f -> onSeek((f * durationMs).toLong()) },
                )
        ) {
            val h = size.height
            // track
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFDFDFD), Color(0xFFDADADA), Color(0xFFEAEAEA))))
            // glossy blue fill
            val w = size.width * fraction
            drawRect(
                Brush.verticalGradient(
                    0f to Color(0xFFC3DBF7),
                    0.45f to Color(0xFF8DB6EE),
                    0.5f to Color(0xFF5B8FDD),
                    1f to Color(0xFF86B1EC),
                ),
                size = Size(w, h),
            )
            drawRect(Color(0xFFA9A9A9), style = Stroke(width = 1.5f))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "-" + formatTime((durationMs - shownPos).coerceAtLeast(0)),
            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = IpodBlack,
        )
    }
}

private fun Modifier.pointerInputSeek(
    durationMs: Long,
    onDragStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (Float) -> Unit,
): Modifier = this
    .pointerInput(durationMs, "tap") {
        detectTapGestures { pos -> if (durationMs > 0) onTap((pos.x / size.width).coerceIn(0f, 1f)) }
    }
    .pointerInput(durationMs, "drag") {
        detectHorizontalDragGestures(
            onDragStart = { pos -> if (durationMs > 0) onDragStart((pos.x / size.width).coerceIn(0f, 1f)) },
            onDragEnd = { if (durationMs > 0) onDragEnd() },
            onDragCancel = { if (durationMs > 0) onDragEnd() },
            onHorizontalDrag = { change, _ ->
                change.consume()
                if (durationMs > 0) onDrag((change.position.x / size.width).coerceIn(0f, 1f))
            },
        )
    }

// ---------------------------------------------------------------- controls under the screen

@Composable
private fun MediaControls(
    state: PlayerState,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleButton(Icons.Rounded.Shuffle, "Shuffle", active = state.shuffle, onClick = onShuffle)
        SilverButton(Icons.Rounded.SkipPrevious, "Previous", 58.dp, onPrevious)
        SilverButton(
            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (state.isPlaying) "Pause" else "Play",
            78.dp, onPlayPause,
        )
        SilverButton(Icons.Rounded.SkipNext, "Next", 58.dp, onNext)
        ToggleButton(
            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            "Repeat",
            active = state.repeatMode != Player.REPEAT_MODE_OFF,
            onClick = onRepeat,
        )
    }
}

@Composable
private fun SilverButton(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFFFAFAFA),
                    0.5f to Color(0xFFDCDCDC),
                    1f to Color(0xFFB9B9B9),
                )
            )
            .border(1.dp, Color(0xFF8C8C8C), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFF3A3A3A), modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (active) BladeColors.NowPlaying.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = label,
            tint = if (active) BladeColors.NowPlaying else BladeColors.TextDim,
            modifier = Modifier.size(26.dp),
        )
    }
}
