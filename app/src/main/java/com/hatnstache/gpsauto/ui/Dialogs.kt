package com.hatnstache.gpsauto.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hatnstache.gpsauto.data.BlipColors
import com.hatnstache.gpsauto.data.BlipIcon
import com.hatnstache.gpsauto.data.Place
import com.hatnstache.gpsauto.data.PlaceStore
import com.hatnstache.gpsauto.data.defaultColorFor
import com.hatnstache.gpsauto.nav.Geo
import com.hatnstache.gpsauto.nav.PlaceSearch
import com.hatnstache.gpsauto.nav.SearchResult
import com.hatnstache.gpsauto.nav.Units
import kotlinx.coroutines.delay
import org.maplibre.android.geometry.LatLng

data class NewPlace(val name: String, val lat: Double, val lon: Double)

@Composable
fun SearchSheet(
    near: LatLng?,
    places: List<Place>,
    onPick: (String, LatLng) -> Unit,
    onSave: (String, LatLng) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.length < 3) { results = emptyList(); failed = false; return@LaunchedEffect }
        delay(350)
        busy = true
        try {
            results = PlaceSearch.search(query, near)
            failed = false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        } finally {
            busy = false
        }
    }

    val matchingPlaces = places.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }

    Column(Modifier.fillMaxSize().background(Color(0xFF111214)).statusBarsPadding().imePadding()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search a place or address") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E2024),
                    unfocusedContainerColor = Color(0xFF1E2024),
                    focusedIndicatorColor = Pink,
                    cursorColor = Pink,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (busy) CircularProgressIndicator(color = Pink, strokeWidth = 2.dp, modifier = Modifier.padding(start = 10.dp).size(22.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (matchingPlaces.isNotEmpty()) {
                item { SectionLabel("Saved places") }
                items(matchingPlaces, key = { "p-" + it.id }) { p ->
                    ResultRow(
                        leading = { BlipBadge(p, 36.dp) },
                        title = p.name,
                        subtitle = near?.let { Units.distance(Geo.distance(it, LatLng(p.lat, p.lon))) } ?: "",
                        onClick = { onPick(p.name, LatLng(p.lat, p.lon)) },
                    )
                }
            }
            if (results.isNotEmpty()) {
                item { SectionLabel("Results") }
                items(results) { r ->
                    val ll = LatLng(r.lat, r.lon)
                    ResultRow(
                        leading = {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2A2C30)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Pink)
                            }
                        },
                        title = r.title,
                        subtitle = listOfNotNull(
                            near?.let { Units.distance(Geo.distance(it, ll)) },
                            r.subtitle.ifBlank { null },
                        ).joinToString(" · "),
                        onClick = { onPick(r.title, ll) },
                        trailing = {
                            IconButton(onClick = { onSave(r.title, ll) }) {
                                Icon(Icons.Default.BookmarkBorder, "Save as a place", tint = Color.White.copy(alpha = 0.8f))
                            }
                        },
                    )
                }
            }
            if (failed) item { SectionLabel("Search isn't reachable right now. Check your connection.") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp))
}

@Composable
private fun ResultRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
fun AddPlaceDialog(np: NewPlace, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(np.name) }
    var icon by remember { mutableStateOf(BlipIcon.STAR) }
    var color by remember { mutableStateOf(defaultColorFor(BlipIcon.STAR)) }
    var colorTouched by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this place") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Text("Icon", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BlipIcon.entries.toList()) { i ->
                        val selected = i == icon
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(Color(if (colorTouched) color else defaultColorFor(i)))
                                .border(if (selected) 3.dp else 1.5.dp, if (selected) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
                                .clickable {
                                    icon = i
                                    if (!colorTouched) color = defaultColorFor(i)
                                    if (name.isBlank() || BlipIcon.entries.any { it.label == name }) name = i.label
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(i.res), contentDescription = i.label, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Text("Color", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlipColors.forEach { c ->
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == color) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { color = c; colorTouched = true },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                PlaceStore.add(name, np.lat, np.lon, icon, color)
                onDismiss()
            }) { Text("Save", color = Pink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PlaceDialog(place: Place, onNavigate: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { BlipBadge(place, 44.dp) },
        title = { Text(place.name) },
        confirmButton = { TextButton(onClick = onNavigate) { Text("Navigate", color = Pink) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFF87171)) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
