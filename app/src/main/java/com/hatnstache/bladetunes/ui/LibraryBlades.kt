package com.hatnstache.bladetunes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hatnstache.bladetunes.AppViewModel
import com.hatnstache.bladetunes.data.Album
import com.hatnstache.bladetunes.data.Artist
import com.hatnstache.bladetunes.data.Song
import com.hatnstache.bladetunes.data.formatTime

/** Things any song list can ask the app shell to do (open dialogs). */
class SongActions(
    val onEditTags: (List<Song>) -> Unit,
    val onAddToPlaylist: (List<Song>) -> Unit,
)

// ---------------------------------------------------------------- song row + menu

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    vm: AppViewModel,
    actions: SongActions,
    isCurrent: Boolean,
    accent: Color,
    onClick: () -> Unit,
    leading: String? = null,
    extraMenu: @Composable ColumnScope.(close: () -> Unit) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) LeadingNumber(leading)
            else AlbumArt(song, Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, fontSize = 16.sp,
                    color = if (isCurrent) accent else BladeColors.Text,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${song.artist} • ${song.album}", fontSize = 13.sp, color = BladeColors.TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(formatTime(song.durationMs), fontSize = 12.sp, color = BladeColors.TextDim)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = {
                vm.player.playNext(song); vm.toast("Playing next"); menu = false
            })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = {
                vm.player.addToQueue(listOf(song)); vm.toast("Added to queue"); menu = false
            })
            DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = {
                actions.onAddToPlaylist(listOf(song)); menu = false
            })
            DropdownMenuItem(text = { Text("Edit tags…") }, onClick = {
                actions.onEditTags(listOf(song)); menu = false
            })
            extraMenu { menu = false }
        }
    }
}

/** Play / Shuffle / ⋮ buttons used at the top of albums, artists and playlists. */
@Composable
fun GroupButtons(
    accent: Color,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    menuItems: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillButton("Play", Icons.Rounded.PlayArrow, accent, onPlay)
        PillButton("Shuffle", Icons.Rounded.Shuffle, accent, onShuffle)
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = BladeColors.TextDim)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                menuItems { menu = false }
            }
        }
    }
}

@Composable
fun DetailTitle(title: String, subtitle: String, onBack: () -> Unit, art: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = BladeColors.Text)
        }
        art()
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = BladeColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = BladeColors.TextDim, maxLines = 1)
        }
    }
}

private fun totalTime(songs: List<Song>) = formatTime(songs.sumOf { it.durationMs })
private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

// ---------------------------------------------------------------- songs blade

@Composable
fun SongsBlade(vm: AppViewModel, actions: SongActions) {
    val accent = Blade.SONGS.color
    val library by vm.library.collectAsStateWithLifecycle()
    val state by vm.player.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val songs = remember(library, query) {
        if (query.isBlank()) library.songs
        else library.songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }

    Column(Modifier.fillMaxSize()) {
        BladeHeader(plural(songs.size, "song")) {
            PillButton("Shuffle all", Icons.Rounded.Shuffle, accent, { vm.shuffle(songs) })
        }
        SearchField(query, { query = it }, "Search songs, artists, albums")
        when {
            !library.loaded -> CenterMessage("Reading your music…", loading = true)
            songs.isEmpty() -> CenterMessage(if (query.isBlank()) "No music found on this device" else "No matches")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, vm, actions, s.id == state.songId, accent, onClick = { vm.play(songs, i) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- albums blade

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsBlade(vm: AppViewModel, actions: SongActions) {
    val accent = Blade.ALBUMS.color
    val library by vm.library.collectAsStateWithLifecycle()
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val open = openKey?.let { k -> library.albums.firstOrNull { it.key == k } }

    if (open != null) {
        BackHandler { openKey = null }
        AlbumDetail(open, vm, actions, accent, onBack = { openKey = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        BladeHeader(plural(library.albums.size, "album"))
        if (!library.loaded) { CenterMessage("Reading your music…", loading = true); return@Column }
        if (library.albums.isEmpty()) { CenterMessage("No albums yet"); return@Column }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(library.albums, key = { it.key }) { album ->
                var menu by remember { mutableStateOf(false) }
                Box {
                    Column(
                        Modifier.combinedClickable(
                            onClick = { openKey = album.key },
                            onLongClick = { menu = true },
                        )
                    ) {
                        AlbumArt(album.cover, Modifier.fillMaxWidth().aspectRatio(1f), corner = 6.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(album.title, fontSize = 14.sp, color = BladeColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.artist, fontSize = 12.sp, color = BladeColors.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        AlbumMenuItems(album, vm, actions) { menu = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.AlbumMenuItems(album: Album, vm: AppViewModel, actions: SongActions, close: () -> Unit) {
    DropdownMenuItem(text = { Text("Play") }, onClick = { vm.play(album.songs, 0); close() })
    DropdownMenuItem(text = { Text("Shuffle") }, onClick = { vm.shuffle(album.songs); close() })
    DropdownMenuItem(text = { Text("Add to queue") }, onClick = { vm.player.addToQueue(album.songs); vm.toast("Added to queue"); close() })
    DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = { actions.onAddToPlaylist(album.songs); close() })
    DropdownMenuItem(text = { Text("Edit album tags…") }, onClick = { actions.onEditTags(album.songs); close() })
}

@Composable
private fun AlbumDetail(album: Album, vm: AppViewModel, actions: SongActions, accent: Color, onBack: () -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DetailTitle(
            album.title,
            listOfNotNull(
                album.artist,
                album.year.takeIf { it > 0 }?.toString(),
                plural(album.songs.size, "song"),
                totalTime(album.songs),
            ).joinToString(" • "),
            onBack,
        ) { AlbumArt(album.cover, Modifier.size(72.dp), corner = 6.dp) }
        GroupButtons(accent, { vm.play(album.songs, 0) }, { vm.shuffle(album.songs) }) { close ->
            AlbumMenuItems(album, vm, actions, close)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(album.songs, key = { _, s -> s.id }) { i, s ->
                SongRow(
                    s, vm, actions, s.id == state.songId, accent,
                    onClick = { vm.play(album.songs, i) },
                    leading = if (s.track > 0) s.track.toString() else "–",
                )
            }
        }
    }
}

// ---------------------------------------------------------------- artists blade

@Composable
fun ArtistsBlade(vm: AppViewModel, actions: SongActions) {
    val accent = Blade.ARTISTS.color
    val library by vm.library.collectAsStateWithLifecycle()
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val open = openName?.let { n -> library.artists.firstOrNull { it.name == n } }

    if (open != null) {
        BackHandler { openName = null }
        ArtistDetail(open, vm, actions, accent, onBack = { openName = null })
        return
    }

    val artists = remember(library, query) {
        if (query.isBlank()) library.artists else library.artists.filter { it.name.contains(query, true) }
    }
    Column(Modifier.fillMaxSize()) {
        BladeHeader(plural(artists.size, "artist"))
        SearchField(query, { query = it }, "Search artists")
        if (!library.loaded) { CenterMessage("Reading your music…", loading = true); return@Column }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(artists, key = { it.name }) { artist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openName = artist.name }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(accent.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            artist.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BladeColors.Text,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, fontSize = 16.sp, color = BladeColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${plural(artist.albumCount, "album")} • ${plural(artist.songs.size, "song")}",
                            fontSize = 13.sp, color = BladeColors.TextDim,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistDetail(artist: Artist, vm: AppViewModel, actions: SongActions, accent: Color, onBack: () -> Unit) {
    val state by vm.player.state.collectAsStateWithLifecycle()
    val songs = artist.songs
    Column(Modifier.fillMaxSize()) {
        DetailTitle(
            artist.name,
            "${plural(artist.albumCount, "album")} • ${plural(songs.size, "song")} • ${totalTime(songs)}",
            onBack,
        )
        GroupButtons(accent, { vm.play(songs, 0) }, { vm.shuffle(songs) }) { close ->
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { vm.player.addToQueue(songs); vm.toast("Added to queue"); close() })
            DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = { actions.onAddToPlaylist(songs); close() })
            DropdownMenuItem(text = { Text("Edit artist tags…") }, onClick = { actions.onEditTags(songs); close() })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            var lastAlbum: String? = null
            songs.forEachIndexed { i, s ->
                if (s.albumKey != lastAlbum) {
                    lastAlbum = s.albumKey
                    item(key = "album-${s.albumKey}") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AlbumArt(s, Modifier.size(34.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(s.album, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item(key = s.id) {
                    SongRow(
                        s, vm, actions, s.id == state.songId, accent,
                        onClick = { vm.play(songs, i) },
                        leading = if (s.track > 0) s.track.toString() else "–",
                    )
                }
            }
        }
    }
}
