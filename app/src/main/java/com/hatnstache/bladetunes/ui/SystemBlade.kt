package com.hatnstache.bladetunes.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hatnstache.bladetunes.AppViewModel
import com.hatnstache.bladetunes.data.TagEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

data class AppEntry(val label: String, val pkg: String, val activity: String, val icon: ImageBitmap)

private suspend fun loadApps(context: Context): List<AppEntry> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            AppEntry(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                activity = it.activityInfo.name,
                icon = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

private fun Context.safeStart(intent: Intent) {
    try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) {}
}

/**
 * System blade. In launcher mode it's your app drawer; in app mode it's just settings.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SystemBlade(vm: AppViewModel, isHome: Boolean) {
    val context = LocalContext.current
    val accent = Blade.SYSTEM.color
    val library by vm.library.collectAsStateWithLifecycle()
    var fileAccess by remember { mutableStateOf(TagEditor.hasFileAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { fileAccess = TagEditor.hasFileAccess() }

    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState(emptyList<AppEntry>(), isHome) {
        if (isHome) value = loadApps(context)
    }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, true) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(78.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { ClockCard() }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(
                    "${library.songs.size} songs • ${library.albums.size} albums • ${library.artists.size} artists",
                    color = BladeColors.TextDim, fontSize = 13.sp,
                )
                Text(
                    if (fileAccess) "Tag editing: ready" else "Tag editing: needs All files access",
                    color = BladeColors.TextDim, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PillButton("Rescan music", Icons.Rounded.Refresh, accent, {
                        vm.refresh(); vm.toast("Rescanning…")
                    })
                    if (!fileAccess) {
                        PillButton("Allow file access", Icons.Rounded.FolderOpen, accent, {
                            context.safeStart(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        })
                    }
                    PillButton(
                        if (isHome) "Change home app" else "Use as home screen",
                        Icons.Rounded.Home, accent,
                        { context.safeStart(Intent(Settings.ACTION_HOME_SETTINGS)) },
                    )
                    PillButton("Phone settings", Icons.Rounded.Settings, accent, {
                        context.safeStart(Intent(Settings.ACTION_SETTINGS))
                    })
                }
            }
        }

        if (isHome) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchField(query, { query = it }, "Search apps")
            }
            items(shown, key = { it.pkg + "/" + it.activity }) { app ->
                Column(
                    Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = {
                                context.safeStart(
                                    Intent(Intent.ACTION_MAIN)
                                        .addCategory(Intent.CATEGORY_LAUNCHER)
                                        .setComponent(ComponentName(app.pkg, app.activity))
                                        .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                                )
                            },
                            onLongClick = {
                                context.safeStart(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                                )
                            },
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(app.icon, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        app.label, fontSize = 12.sp, color = BladeColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockCard() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000) }
    }
    val battery = rememberBatteryLevel()
    val timeFmt = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    Column(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BladeColors.PanelLight)
            .padding(16.dp)
    ) {
        Text(
            DateFormat.format(timeFmt, now).toString(),
            fontSize = 52.sp, fontWeight = FontWeight.Light, color = BladeColors.Text,
        )
        Text(
            DateFormat.format("EEEE, MMMM d", now).toString() + "  •  $battery%",
            fontSize = 15.sp, color = BladeColors.TextDim,
        )
    }
}
