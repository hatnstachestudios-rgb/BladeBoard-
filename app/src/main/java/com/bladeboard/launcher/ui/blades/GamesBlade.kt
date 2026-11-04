package com.bladeboard.launcher.ui.blades

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.bladeboard.launcher.data.AppEntry
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.games.GameLibrary
import com.bladeboard.launcher.games.rememberCover
import com.bladeboard.launcher.ui.BladeHeader
import com.bladeboard.launcher.ui.focusRing
import com.bladeboard.launcher.ui.onOptionsButton
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BOX_ART_RATIO = 0.72f

@Composable
fun GamesBlade(repo: AppRepository, settings: LauncherSettings, library: GameLibrary, accent: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val games = library.games(repo.apps)

    var editingList by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf<AppEntry?>(null) }
    var pickingFor by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = pickingFor
        pickingFor = null
        if (uri != null && key != null) {
            scope.launch {
                if (!library.saveCover(key, uri)) Toast.makeText(context, "Couldn't use that image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pickCover: (AppEntry) -> Unit = { app ->
        pickingFor = app.key
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BladeHeader("Games · ${games.size}", accent, Modifier.weight(1f))
            TextButton(onClick = { editingList = true }, modifier = Modifier.focusRing(accent)) {
                Text("Edit list", color = accent)
            }
        }

        if (games.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No games found", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tap Edit list to choose which apps show up here.",
                    fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f),
                )
            }
        } else {
            GameFlow(
                games = games,
                repo = repo,
                settings = settings,
                library = library,
                accent = accent,
                onChangeCover = pickCover,
                onResize = { resizing = it },
                onRemove = { library.setInGames(it, false) },
            )
        }
    }

    if (editingList) {
        GameListEditor(repo, library, accent) { editingList = false }
    }
    resizing?.let { app ->
        CoverSizeDialog(app, settings, library, accent, onChangeCover = { resizing = null; pickCover(app) }) { resizing = null }
    }
}

/** Single-row cover flow: the centered cover is full size, neighbours shrink and dim. */
@Composable
private fun GameFlow(
    games: List<AppEntry>,
    repo: AppRepository,
    settings: LauncherSettings,
    library: GameLibrary,
    accent: Color,
    onChangeCover: (AppEntry) -> Unit,
    onResize: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val centered by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: 0
        }
    }
    val focused = games.getOrNull(centered)

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Leave room for the reflection; the Style slider scales everything.
            val scale = settings.gameCardScale / 100f
            val cardH = min(maxHeight / 1.35f, 380.dp) * scale
            val cardW = min(cardH * BOX_ART_RATIO, maxWidth * 0.7f)
            val sidePad = ((maxWidth - cardW) / 2).coerceAtLeast(0.dp)
            val gap = 14.dp
            val stepPx = with(LocalDensity.current) { (cardW + gap).toPx() }

            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
                contentPadding = PaddingValues(horizontal = sidePad),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(games, key = { _, g -> g.key }) { index, game ->
                    Column(
                        Modifier
                            .width(cardW)
                            .graphicsLayer {
                                val info = listState.layoutInfo
                                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                                val dist = if (item == null) 1f else {
                                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                    (abs(item.offset + item.size / 2f - center) / stepPx).coerceAtMost(1f)
                                }
                                val s = 1f - 0.18f * dist
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.45f * dist
                            },
                    ) {
                        GameCard(
                            app = game,
                            height = cardH,
                            centered = index == centered,
                            repo = repo,
                            settings = settings,
                            library = library,
                            accent = accent,
                            onFocused = { scope.launch { listState.animateScrollToItem(index) } },
                            onClick = {
                                if (index == centered) repo.launch(game)
                                else scope.launch { listState.animateScrollToItem(index) }
                            },
                            onChangeCover = { onChangeCover(game) },
                            onResize = { onResize(game) },
                            onRemove = { onRemove(game) },
                        )
                        CoverReflection(game, library, settings, accent, cardH)
                    }
                }
            }
        }

        // Name and position, like a console's game shelf.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                focused?.label ?: "",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Text("${centered + 1} of ${games.size}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    app: AppEntry,
    height: Dp,
    centered: Boolean,
    repo: AppRepository,
    settings: LauncherSettings,
    library: GameLibrary,
    accent: Color,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onChangeCover: () -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box {
        CoverFace(
            app, library, settings, accent, height, highlighted = centered,
            modifier = Modifier
                .focusRing(accent, shape)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .onOptionsButton { menuOpen = true }
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Change cover image") }, onClick = { menuOpen = false; onChangeCover() })
            DropdownMenuItem(text = { Text("Adjust size") }, onClick = { menuOpen = false; onResize() })
            if (library.hasCover(app.key)) {
                DropdownMenuItem(text = { Text("Use default cover") }, onClick = { menuOpen = false; library.removeCover(app.key) })
            }
            DropdownMenuItem(
                text = { Text(if (settings.isFavorite(app.key)) "Unpin from Home" else "Pin to Home") },
                onClick = { menuOpen = false; settings.toggleFavorite(app.key) },
            )
            DropdownMenuItem(text = { Text("Remove from Games") }, onClick = { menuOpen = false; onRemove() })
            DropdownMenuItem(text = { Text("App info") }, onClick = { menuOpen = false; repo.openAppInfo(app) })
        }
    }
}

/**
 * The box art itself: the user's custom image if set, otherwise a generated cover with the
 * game's icon on a backdrop tinted from it. [zoomOverride] previews unsaved sizes.
 */
@Composable
private fun CoverFace(
    app: AppEntry,
    library: GameLibrary,
    settings: LauncherSettings,
    accent: Color,
    height: Dp,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    zoomOverride: Float? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    val cover = rememberCover(library, app.key)
    val zoom = zoomOverride ?: settings.gameZoom(app.key)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(app.tint, Color(0xFF0D1015))))
            .border(if (highlighted) 3.dp else 1.5.dp, if (highlighted) accent else Color.White.copy(alpha = 0.25f), shape)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(
                cover,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = zoom; scaleY = zoom },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.8f))))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 14.dp, height = 8.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        app.label.uppercase(),
                        color = Color(0xFF1A1D22),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(min(maxWidth, maxHeight) * 0.55f * zoom),
                    )
                }
            }
        }
        // Subtle shine across the top, like a plastic game case.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.12f), 0.35f to Color.Transparent))
        )
    }
}

/** Mirrored, fading copy of the cover, like a glossy floor. */
@Composable
private fun CoverReflection(app: AppEntry, library: GameLibrary, settings: LauncherSettings, accent: Color, cardH: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(cardH * 0.25f)
            .clipToBounds()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)),
                    blendMode = BlendMode.DstIn,
                )
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(cardH)
                .graphicsLayer { scaleY = -1f }
        ) {
            CoverFace(app, library, settings, accent, cardH, highlighted = false)
        }
    }
}

/** Live preview with a zoom slider for one game's cover or icon. */
@Composable
private fun CoverSizeDialog(
    app: AppEntry,
    settings: LauncherSettings,
    library: GameLibrary,
    accent: Color,
    onChangeCover: () -> Unit,
    onDismiss: () -> Unit,
) {
    var zoom by remember { mutableFloatStateOf(settings.gameZoom(app.key)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(150.dp)) {
                    CoverFace(app, library, settings, accent, 150.dp / BOX_ART_RATIO, highlighted = true, zoomOverride = zoom)
                }
                Spacer(Modifier.height(12.dp))
                Text("Size ${(zoom * 100).roundToInt()}%", fontSize = 14.sp)
                Slider(
                    value = zoom,
                    onValueChange = { zoom = (it * 20).roundToInt() / 20f },
                    valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { zoom = 1f }) { Text("Reset", color = Color.White) }
                    OutlinedButton(onClick = onChangeCover) { Text("Change image", color = Color.White) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { settings.setGameZoom(app.key, zoom); onDismiss() }) { Text("Save", color = accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

/** Checklist of every app; ticked apps appear on the Games blade. */
@Composable
private fun GameListEditor(repo: AppRepository, library: GameLibrary, accent: Color, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    // Games first, then everything else. The order is fixed when the list opens so rows
    // don't jump around as you tick them.
    val startedAsGames = remember { library.games(repo.apps).map { it.key }.toSet() }
    val apps = repo.apps.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareBy({ it.key !in startedAsGames }, { it.label.lowercase() }))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose games") },
        text = {
            Column {
                Text(
                    "Ticked apps show on the Games blade. \"Auto\" means Bladeboard detected it as a game.",
                    fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, cursorColor = accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(apps, key = { it.key }) { app ->
                        val checked = library.isGame(app)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .focusRing(accent, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { library.setInGames(app, !checked) }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (library.autoDetected(app)) {
                                    Text("Auto", fontSize = 11.sp, color = accent)
                                }
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { library.setInGames(app, it) },
                                colors = CheckboxDefaults.colors(checkedColor = accent),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = accent) } },
    )
}
