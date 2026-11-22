package com.hatnstache.gpsauto.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hatnstache.gpsauto.data.Place
import com.hatnstache.gpsauto.data.PlaceStore
import com.hatnstache.gpsauto.map.BlipOverlayView
import com.hatnstache.gpsauto.map.GtaMap
import com.hatnstache.gpsauto.map.MapTheme
import com.hatnstache.gpsauto.media.MediaRepository
import com.hatnstache.gpsauto.nav.Instructions
import com.hatnstache.gpsauto.nav.NavProgress
import com.hatnstache.gpsauto.nav.NavigationEngine
import com.hatnstache.gpsauto.nav.RouteStep
import com.hatnstache.gpsauto.nav.Units
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val Pink = Color(0xFFFF4FA8)
val HudCard = Color(0xE6141518)
private val Ground = Color(0xFF15171A)

/** The phone's map view: MapLibre plus the blip layer, owned for the life of the screen. */
class PhoneMap(context: Context) {
    val mapView = MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
    val blips = BlipOverlayView(context)
    val gta = GtaMap(context, mapView, blips)
    val container = FrameLayout(context).apply {
        addView(mapView, FrameLayout.LayoutParams(-1, -1))
        addView(blips, FrameLayout.LayoutParams(-1, -1))
    }
    var onLongPress: ((LatLng) -> Unit)? = null
    var onBlipTap: ((Place) -> Unit)? = null

    init {
        mapView.onCreate(null)
        gta.onReady = { m ->
            m.addOnMapLongClickListener { ll -> onLongPress?.invoke(ll); true }
            m.addOnMapClickListener { ll ->
                val pt = m.projection.toScreenLocation(ll)
                val hit = blips.hitTest(pt.x, pt.y)
                if (hit != null) { onBlipTap?.invoke(hit); true } else false
            }
        }
    }
}

@Composable
fun GpsScreen(media: MediaRepository, onFloat: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val phoneMap = remember { PhoneMap(context) }

    val location by NavigationEngine.location.collectAsStateWithLifecycle()
    val heading by NavigationEngine.heading.collectAsStateWithLifecycle()
    val route by NavigationEngine.route.collectAsStateWithLifecycle()
    val progress by NavigationEngine.progress.collectAsStateWithLifecycle()
    val destination by NavigationEngine.destination.collectAsStateWithLifecycle()
    val loading by NavigationEngine.loading.collectAsStateWithLifecycle()
    val error by NavigationEngine.error.collectAsStateWithLifecycle()
    val muted by NavigationEngine.muted.collectAsStateWithLifecycle()
    val places by PlaceStore.places.collectAsStateWithLifecycle()

    var mode by remember { mutableStateOf(GtaMap.Mode.FOLLOW) }
    var minimap by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var previewFraction by remember { mutableFloatStateOf(0f) }
    var addPlaceAt by remember { mutableStateOf<NewPlace?>(null) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    val night by MapTheme.night.collectAsStateWithLifecycle()
    val navigating = destination != null

    // MapView lifecycle follows the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        phoneMap.gta.onModeChanged = { mode = it }
        phoneMap.onLongPress = { ll -> addPlaceAt = NewPlace("", ll.latitude, ll.longitude, fromPin = true) }
        phoneMap.onBlipTap = { selectedPlace = it }
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> phoneMap.mapView.onStart()
                Lifecycle.Event.ON_RESUME -> phoneMap.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> phoneMap.mapView.onPause()
                Lifecycle.Event.ON_STOP -> phoneMap.mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            phoneMap.gta.release()
            phoneMap.mapView.onDestroy()
        }
    }

    LaunchedEffect(phoneMap) { phoneMap.gta.bindStyle(this) }
    LaunchedEffect(location, heading) { location?.let { phoneMap.gta.onLocation(it, heading) } }
    LaunchedEffect(location != null) { if (location != null) phoneMap.gta.recenter() }
    LaunchedEffect(route, progress) { phoneMap.gta.showRoute(route, progress?.along, progress?.snapped) }
    LaunchedEffect(route != null) {
        if (route != null) phoneMap.gta.recenter() else previewing = false
    }
    LaunchedEffect(places) { phoneMap.blips.places = places }
    LaunchedEffect(destination) { phoneMap.blips.destination = destination?.point }
    LaunchedEffect(minimap, navigating) {
        // Keep the arrow and edge blips clear of the turn card and trip bar in full-screen mode.
        val top = if (!minimap && navigating) with(density) { 150.dp.roundToPx() } else 0
        val bottom = if (!minimap) with(density) { 110.dp.roundToPx() } else 0
        phoneMap.gta.insets = Rect(0, top, 0, bottom)
        phoneMap.gta.followBias = if (minimap) 0.62f else 0.55f
        phoneMap.gta.recenter()
    }
    LaunchedEffect(navigating) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        if (navigating) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    Box(Modifier.fillMaxSize().background(if (night) Color(0xFF0D0E10) else Ground)) {
        // ---- Map: full screen, or a rounded GTA minimap in the corner ----
        val frame = RoundedCornerShape(28.dp)
        val mapModifier = if (!minimap) Modifier.fillMaxSize() else Modifier
            .align(Alignment.BottomStart)
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, bottom = 92.dp)
            .fillMaxWidth()
            .height(320.dp)
            .clip(frame)
            .border(2.dp, Color.White.copy(alpha = 0.85f), frame)
        Box(mapModifier) {
            AndroidView(
                factory = {
                    (phoneMap.container.parent as? ViewGroup)?.removeView(phoneMap.container)
                    phoneMap.container
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (minimap && navigating && progress != null) {
                Text(
                    Units.distancePrecise(progress!!.remaining),
                    color = Color(0xFF1C1D20),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                )
            }
        }

        // GTA-style next-turn box sitting right on top of the minimap frame.
        val pm = progress
        if (minimap && navigating && pm != null && pm.nextStep != null && !pm.arrived && pm.distanceToNext < 480) {
            Box(
                Modifier.align(Alignment.BottomStart).navigationBarsPadding()
                    .padding(start = 14.dp, bottom = 92.dp + 320.dp + 8.dp)
            ) { GtaTurnBox(pm.nextStep, pm.distanceToNext) }
        }

        // ---- Top: search, or the turn card ----
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val p = progress
            when {
                navigating && minimap -> {}
                navigating && p != null && p.nextStep != null -> TurnCard(p.nextStep, p)
                navigating -> StatusCard(if (loading) "Finding a route…" else "Waiting for GPS…")
                else -> SearchPill { searchOpen = true }
            }
            error?.let { msg ->
                StatusCard(msg, action = "Retry", onAction = { NavigationEngine.retry() }, onClose = { NavigationEngine.clearError() })
            }
            NowPlayingStrip(media, context)
        }

        // ---- Right edge: map controls ----
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (mode != GtaMap.Mode.FOLLOW) {
                HudButton(Icons.Default.Navigation, "Re-center", tint = Pink) {
                    previewing = false
                    phoneMap.gta.recenter()
                }
            }
            route?.let { r ->
                HudButton(Icons.Default.Map, "Route overview") {
                    previewing = false
                    phoneMap.gta.overview(r)
                }
                HudButton(Icons.Default.Timeline, "Preview the route", tint = if (previewing) Pink else Color.White) {
                    previewing = !previewing
                    if (previewing) {
                        previewFraction = ((progress?.along ?: 0.0) / r.length.coerceAtLeast(1.0)).toFloat()
                        phoneMap.gta.previewAlong(r, previewFraction)
                    } else phoneMap.gta.recenter()
                }
            }
            if (navigating) {
                HudButton(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Unmute" else "Mute") {
                    NavigationEngine.setMuted(!muted)
                }
            }
            HudButton(if (minimap) Icons.Default.Fullscreen else Icons.Default.PictureInPictureAlt, if (minimap) "Full map" else "Minimap mode") {
                minimap = !minimap
            }
            HudButton(Icons.Default.OpenInNew, "Float the minimap over other apps", onClick = onFloat)
            if (!navigating) HudButton(Icons.Default.Settings, "Settings") { settingsOpen = true }
        }

        // ---- Bottom: trip bar, route scrubber, or saved places ----
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth()) {
            val r = route
            when {
                previewing && r != null -> PreviewBar(
                    fraction = previewFraction,
                    onChange = { previewFraction = it; phoneMap.gta.previewAlong(r, it) },
                    onDone = { previewing = false; phoneMap.gta.recenter() },
                )
                navigating -> TripBar(progress, onEnd = { NavigationEngine.stop() })
                else -> SavedPlacesBar(places) { NavigationEngine.navigateTo(it.name, LatLng(it.lat, it.lon)) }
            }
        }

        if (searchOpen) {
            SearchSheet(
                near = location?.let { LatLng(it.latitude, it.longitude) },
                places = places,
                onPick = { name, ll ->
                    searchOpen = false
                    NavigationEngine.navigateTo(name, ll)
                },
                onSave = { name, ll -> addPlaceAt = NewPlace(name, ll.latitude, ll.longitude) },
                onClose = { searchOpen = false },
            )
        }
        addPlaceAt?.let { np ->
            AddPlaceDialog(
                np,
                onDismiss = { addPlaceAt = null },
                onWaypoint = {
                    addPlaceAt = null
                    NavigationEngine.navigateTo("Waypoint", LatLng(np.lat, np.lon), waypoint = true)
                },
            )
        }
        if (settingsOpen) SettingsDialog(onDismiss = { settingsOpen = false })
        selectedPlace?.let { p ->
            PlaceDialog(
                place = p,
                onNavigate = {
                    selectedPlace = null
                    NavigationEngine.navigateTo(p.name, LatLng(p.lat, p.lon))
                },
                onDelete = { PlaceStore.remove(p.id); selectedPlace = null },
                onDismiss = { selectedPlace = null },
            )
        }
    }
}

@Composable
private fun TurnCard(step: RouteStep, p: NavProgress) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HudCard).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (p.arrived) "Arrived" else Instructions.shortLabel(step),
                    color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold,
                )
                if (!p.arrived) {
                    Text(
                        "  " + Units.distance(p.distanceToNext),
                        color = Color.White.copy(alpha = 0.75f), fontSize = 20.sp,
                    )
                }
            }
            val sub = if (p.arrived) "You're here" else step.name
            if (sub.isNotBlank()) {
                Text(sub, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            painterResource(Instructions.iconRes(step)), contentDescription = null,
            tint = Pink, modifier = Modifier.padding(start = 12.dp).size(44.dp),
        )
    }
}

/** The game's next-turn box: "Turn Left  46 ft" with a pink arrow on the right. */
@Composable
private fun GtaTurnBox(step: RouteStep, distance: Double) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(HudCard).padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Instructions.shortLabel(step), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("  " + Units.distance(distance), color = Color.White.copy(alpha = 0.8f), fontSize = 17.sp)
        Icon(
            painterResource(Instructions.iconRes(step)), contentDescription = null,
            tint = Pink, modifier = Modifier.padding(start = 10.dp).size(26.dp),
        )
    }
}

@Composable
private fun StatusCard(text: String, action: String? = null, onAction: () -> Unit = {}, onClose: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HudCard).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = Pink) }
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Dismiss", tint = Color.White) }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(HudCard).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Pink)
        Text("Where to?", color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
fun HudButton(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Surface(shape = CircleShape, color = HudCard, modifier = Modifier.size(52.dp)) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint) }
    }
}

@Composable
private fun TripBar(p: NavProgress?, onEnd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(HudCard).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (p != null) {
                val eta = LocalTime.now().plusSeconds(p.remainingSeconds.toLong())
                Text(eta.format(DateTimeFormatter.ofPattern("h:mm a")), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${Units.duration(p.remainingSeconds)} · ${Units.distance(p.remaining)}", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            } else {
                Text("Starting…", color = Color.White, fontSize = 20.sp)
            }
        }
        Surface(shape = CircleShape, color = Color(0xFFDC2626), modifier = Modifier.clickable(onClick = onEnd)) {
            Text("End", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun PreviewBar(fraction: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(HudCard).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Slide to scroll along the route", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Done", color = Pink) }
        }
        Slider(
            value = fraction,
            onValueChange = onChange,
            colors = SliderDefaults.colors(thumbColor = Pink, activeTrackColor = Pink),
        )
    }
}

@Composable
private fun SavedPlacesBar(places: List<Place>, onTap: (Place) -> Unit) {
    if (places.isEmpty()) {
        Text(
            "Long-press the map to save a place, or search an address and tap the bookmark.",
            color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HudCard).padding(14.dp),
        )
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(places, key = { it.id }) { p ->
            Row(
                Modifier.clip(CircleShape).background(HudCard).clickable { onTap(p) }.padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BlipBadge(p, 34.dp)
                Text(p.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
fun BlipBadge(p: Place, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(p.color)).border(2.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(p.icon.res), contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun NowPlayingStrip(media: MediaRepository, context: Context) {
    if (!media.hasAccess) {
        Row(
            Modifier.clip(CircleShape).background(HudCard)
                .clickable { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Pink, modifier = Modifier.size(18.dp))
            Text("Turn on music controls", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
        }
        return
    }
    val np = media.nowPlaying ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(HudCard).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = np.art
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)))
        } else {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2C30)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = Pink)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(np.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (np.artist.isNotBlank()) Text(np.artist, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = media::previous) { Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White) }
        IconButton(onClick = media::playPause) {
            Icon(if (np.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (np.isPlaying) "Pause" else "Play", tint = Pink)
        }
        IconButton(onClick = media::next) { Icon(Icons.Default.SkipNext, "Next", tint = Color.White) }
        Spacer(Modifier.width(2.dp))
    }
}
