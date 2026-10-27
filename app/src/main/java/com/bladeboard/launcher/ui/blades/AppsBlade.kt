package com.bladeboard.launcher.ui.blades

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.bladeboard.launcher.data.AppEntry
import com.bladeboard.launcher.data.AppRepository
import com.bladeboard.launcher.data.LauncherSettings
import com.bladeboard.launcher.ui.AppMenu
import com.bladeboard.launcher.ui.AppTile
import com.bladeboard.launcher.ui.BladeHeader
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

@Composable
fun AppsBlade(repo: AppRepository, settings: LauncherSettings, accent: Color) {
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) repo.apps
    else repo.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    // Lets the search box's Enter key launch whatever the carousel has focused.
    var focusedApp by remember { mutableStateOf<AppEntry?>(null) }

    Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            BladeHeader("All apps · ${repo.apps.size}", accent)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    (focusedApp.takeIf { settings.appsCarousel } ?: filtered.firstOrNull())?.let(repo::launch)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    cursorColor = accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (settings.appsCarousel) {
            AppCarousel(filtered, query, repo, settings, accent) { focusedApp = it }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.gridColumns),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.key }) { app -> AppTile(app, repo, settings, accent) }
            }
        }
    }
}

/**
 * Two-row cover carousel. The column nearest the center is full size; others shrink and dim
 * with distance. Tap a centered card to launch it, tap any other card to bring it to center.
 */
@Composable
private fun AppCarousel(
    apps: List<AppEntry>,
    query: String,
    repo: AppRepository,
    settings: LauncherSettings,
    accent: Color,
    onFocusChanged: (AppEntry?) -> Unit,
) {
    val columns = remember(apps) { apps.chunked(2) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedRow by remember { mutableIntStateOf(0) }

    // New search → jump back to the first result.
    LaunchedEffect(query) { listState.scrollToItem(0) }

    val centerColumn by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - center) }?.index ?: 0
        }
    }
    val focusedColumn = columns.getOrNull(centerColumn)
    val focusedRow = if (focusedColumn != null && selectedRow < focusedColumn.size) selectedRow else 0
    val focused = focusedColumn?.getOrNull(focusedRow)
    LaunchedEffect(focused) { onFocusChanged(focused) }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (columns.isEmpty()) {
                Text(
                    "No apps match \"$query\"",
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Center),
                )
                return@BoxWithConstraints
            }
            // Fit two cards plus a reflection vertically; keep box-art proportions.
            val rowGap = 14.dp
            val cardH = min((maxHeight - rowGap - 24.dp) / 2.3f, 260.dp)
            val cardW = min(cardH * 0.78f, maxWidth * 0.55f)
            val reflectionH = cardH * 0.3f
            val sidePad = (maxWidth - cardW) / 2
            val gap = 10.dp
            val stepPx = with(LocalDensity.current) { (cardW + gap).toPx() }

            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
                contentPadding = PaddingValues(horizontal = sidePad),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(columns, key = { _, col -> col.first().key }) { colIndex, col ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(rowGap),
                        modifier = Modifier
                            .width(cardW)
                            .graphicsLayer {
                                // 0 at center → 1 one full step away.
                                val info = listState.layoutInfo
                                val item = info.visibleItemsInfo.firstOrNull { it.index == colIndex }
                                val dist = if (item == null) 1f else {
                                    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                    (abs(item.offset + item.size / 2f - center) / stepPx).coerceAtMost(1f)
                                }
                                val s = lerp(1f, 0.8f, dist)
                                scaleX = s
                                scaleY = s
                                alpha = lerp(1f, 0.55f, dist)
                            },
                    ) {
                        col.forEachIndexed { row, app ->
                            val isFocused = colIndex == centerColumn && row == focusedRow
                            CoverCard(
                                app = app,
                                accent = accent,
                                focused = isFocused,
                                height = cardH,
                                repo = repo,
                                settings = settings,
                                onClick = {
                                    selectedRow = row
                                    if (colIndex == centerColumn) repo.launch(app)
                                    else scope.launch { listState.animateScrollToItem(colIndex) }
                                },
                            )
                        }
                        if (col.size == 1) Spacer(Modifier.height(cardH))
                        Reflection(col.last(), accent, cardH, reflectionH, isFocused = colIndex == centerColumn && focusedRow == col.lastIndex)
                    }
                }
            }
        }

        InfoBar(focused, apps, accent)
    }
}

/** Box-art style card: a light title strip over the icon on a gradient made from the icon's own color. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverCard(
    app: AppEntry,
    accent: Color,
    focused: Boolean,
    height: Dp,
    repo: AppRepository,
    settings: LauncherSettings,
    onClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        CardFace(
            app, accent, focused, height,
            Modifier.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        )
        AppMenu(app, repo, settings, menuOpen) { menuOpen = false }
    }
}

@Composable
private fun CardFace(app: AppEntry, accent: Color, focused: Boolean, height: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(app.tint, Color(0xFF0D1015))))
            .border(if (focused) 3.dp else 1.5.dp, if (focused) accent else accent.copy(alpha = 0.45f), shape)
            .then(modifier),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.8f))))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
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
            Image(app.icon, contentDescription = app.label, modifier = Modifier.size(min(maxWidth, maxHeight) * 0.62f))
        }
    }
}

/** Mirrored, fading copy of the bottom card, like a glossy floor. */
@Composable
private fun Reflection(app: AppEntry, accent: Color, cardH: Dp, reflectionH: Dp, isFocused: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(reflectionH)
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
            CardFace(app, accent, isFocused, cardH)
        }
    }
}

/** Dark band with the focused app's name and position, plus a gesture legend. */
@Composable
private fun InfoBar(focused: AppEntry?, apps: List<AppEntry>, accent: Color) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                focused?.label ?: "—",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (focused != null) {
                Text("${apps.indexOf(focused) + 1} of ${apps.size}", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f))
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Hint("Tap", "Launch", accent)
            Hint("Hold", "Options", accent)
            Hint("Swipe", "Browse", accent)
        }
    }
}

@Composable
private fun Hint(gesture: String, action: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .border(1.5.dp, accent, RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 1.dp)
        ) {
            Text(gesture, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1D22))
        }
        Spacer(Modifier.width(5.dp))
        Text(action, fontSize = 12.sp, color = Color(0xFF3A3F47))
    }
}
