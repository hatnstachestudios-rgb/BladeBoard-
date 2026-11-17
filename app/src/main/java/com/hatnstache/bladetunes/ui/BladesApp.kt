package com.hatnstache.bladetunes.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hatnstache.bladetunes.AppViewModel
import com.hatnstache.bladetunes.MainActivity
import com.hatnstache.bladetunes.data.Song
import kotlinx.coroutines.launch

enum class Blade(val title: String, val color: Color) {
    NOW_PLAYING("now playing", BladeColors.NowPlaying),
    SONGS("songs", BladeColors.Songs),
    ALBUMS("albums", BladeColors.Albums),
    ARTISTS("artists", BladeColors.Artists),
    PLAYLISTS("playlists", BladeColors.Playlists),
    SYSTEM("system", BladeColors.System),
}

fun isDefaultHome(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return info?.activityInfo?.packageName == context.packageName
}

private val TAB_WIDTH = 16.dp

@Composable
fun BladesApp(vm: AppViewModel, activity: MainActivity) {
    BladesTheme {
        var hasPermission by remember { mutableStateOf(activity.hasAudioPermission()) }
        var isHome by remember { mutableStateOf(isDefaultHome(activity)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            hasPermission = activity.hasAudioPermission()
            isHome = isDefaultHome(activity)
            if (hasPermission && !vm.library.value.loaded) vm.refresh()
        }

        val blades = Blade.entries
        val pager = rememberPagerState { blades.size }
        val scope = rememberCoroutineScope()

        // Home button (while we're the launcher) and Back at the top level both go to Now Playing.
        LaunchedEffect(Unit) {
            activity.homePresses.collect { pager.animateScrollToPage(0) }
        }
        BackHandler(enabled = isHome || pager.currentPage != 0) {
            if (pager.currentPage != 0) scope.launch { pager.animateScrollToPage(0) }
        }

        var editSongs by remember { mutableStateOf<List<Song>?>(null) }
        var addSongs by remember { mutableStateOf<List<Song>?>(null) }
        val actions = remember { SongActions(onEditTags = { editSongs = it }, onAddToPlaylist = { addSongs = it }) }

        val snackbar = remember { SnackbarHostState() }
        val message by vm.message.collectAsStateWithLifecycle()
        LaunchedEffect(message) {
            message?.let {
                vm.clearMessage()
                snackbar.showSnackbar(it)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF16210F), BladeColors.Background)))
        ) {
            if (!hasPermission) {
                PermissionScreen(onGrant = activity::requestAudioPermission)
            } else {
                val current = pager.currentPage
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    // Big lowercase title, like the 360 dashboard
                    Text(
                        blades[current].title,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Light,
                        color = BladeColors.Text,
                        modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 4.dp),
                    )
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        // Blades already passed stack on the left…
                        blades.forEachIndexed { i, b ->
                            if (i < current) BladeTab(b, onClick = { scope.launch { pager.animateScrollToPage(i) } })
                        }
                        HorizontalPager(
                            state = pager,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            beyondViewportPageCount = 1,
                            key = { blades[it].name },
                        ) { page ->
                            BladePanel(blades[page]) {
                                when (blades[page]) {
                                    Blade.NOW_PLAYING -> NowPlayingBlade(vm)
                                    Blade.SONGS -> SongsBlade(vm, actions)
                                    Blade.ALBUMS -> AlbumsBlade(vm, actions)
                                    Blade.ARTISTS -> ArtistsBlade(vm, actions)
                                    Blade.PLAYLISTS -> PlaylistsBlade(vm, actions)
                                    Blade.SYSTEM -> SystemBlade(vm, isHome)
                                }
                            }
                        }
                        // …and the ones still ahead stack on the right.
                        blades.forEachIndexed { i, b ->
                            if (i > current) BladeTab(b, onClick = { scope.launch { pager.animateScrollToPage(i) } })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }

        editSongs?.let { songs -> TagEditorDialog(songs, vm, onDismiss = { editSongs = null }) }
        addSongs?.let { songs -> AddToPlaylistDialog(songs, vm, onDismiss = { addSongs = null }) }
    }
}

/** A blade's colored edge, with its name running vertically. */
@Composable
private fun BladeTab(blade: Blade, onClick: () -> Unit) {
    Box(
        Modifier
            .width(TAB_WIDTH)
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.horizontalGradient(listOf(blade.color, blade.color.copy(alpha = 0.7f)))
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(Modifier.padding(top = 60.dp)) {
            Text(
                blade.title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .rotate(90f),
            )
        }
    }
}

/** The open blade: a panel tinted with the blade's color at the top. */
@Composable
private fun BladePanel(blade: Blade, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    0f to blade.color.copy(alpha = 0.55f),
                    0.12f to blade.color.copy(alpha = 0.18f),
                    0.4f to BladeColors.Panel,
                    1f to BladeColors.Panel,
                )
            )
    ) {
        content()
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Music Blades", fontSize = 34.sp, fontWeight = FontWeight.Light, color = BladeColors.Text)
        Spacer(Modifier.height(12.dp))
        Text(
            "Allow access to your music so Music Blades can find the songs on this device. Nothing ever leaves your phone.",
            color = BladeColors.TextDim, fontSize = 15.sp,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGrant) { Text("Allow music access") }
    }
}
