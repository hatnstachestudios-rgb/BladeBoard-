package com.hatnstache.bladetunes.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hatnstache.bladetunes.AppViewModel
import com.hatnstache.bladetunes.data.PlaylistNode
import com.hatnstache.bladetunes.data.formatTime

private sealed interface PlaylistDialog {
    data class New(val folder: Boolean) : PlaylistDialog
    data class Rename(val node: PlaylistNode) : PlaylistDialog
    data class Move(val node: PlaylistNode) : PlaylistDialog
    data class Delete(val node: PlaylistNode) : PlaylistDialog
}

/** Playlists organised into folders (folders can hold folders), Spotify-style. */
@Composable
fun PlaylistsBlade(vm: AppViewModel, actions: SongActions) {
    val accent = Blade.PLAYLISTS.color
    val nodes by vm.playlists.nodes.collectAsStateWithLifecycle()
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importPlaylist(uri, folderId)
    }

    // If the folder we're in was deleted, fall back to the top level.
    LaunchedEffect(nodes) {
        if (folderId != null && nodes.none { it.id == folderId }) folderId = null
    }

    val open = openId?.let { id -> nodes.firstOrNull { it.id == id && !it.isFolder } }
    if (open != null) {
        BackHandler { openId = null }
        PlaylistDetail(open, vm, actions, accent, onBack = { openId = null }, onDialog = { dialog = it })
    } else {
        BackHandler(enabled = folderId != null) {
            folderId = folderId?.let { vm.playlists.get(it)?.parentId }
        }
        val children = remember(nodes, folderId) { vm.playlists.children(folderId) }
        val path = remember(nodes, folderId) { vm.playlists.pathTo(folderId) }

        Column(Modifier.fillMaxSize()) {
            // Breadcrumb: Playlists › Folder › Subfolder
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (folderId != null) {
                    IconButton(onClick = { folderId = path.dropLast(1).lastOrNull()?.id }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Up", tint = BladeColors.Text)
                    }
                } else {
                    Spacer(Modifier.width(10.dp))
                }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    Crumb("Playlists", current = folderId == null) { folderId = null }
                    path.forEach { f ->
                        Text("  ›  ", color = BladeColors.TextDim, fontSize = 16.sp)
                        Crumb(f.name, current = f.id == folderId) { folderId = f.id }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton("Folder", Icons.Rounded.CreateNewFolder, accent, { dialog = PlaylistDialog.New(folder = true) })
                PillButton("Playlist", Icons.AutoMirrored.Rounded.PlaylistAdd, accent, { dialog = PlaylistDialog.New(folder = false) })
                PillButton("Import M3U", Icons.Rounded.FileOpen, accent, { importer.launch(arrayOf("*/*")) })
            }

            if (children.isEmpty()) {
                CenterMessage(if (folderId == null) "No playlists yet — make a folder or playlist above" else "This folder is empty")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(children, key = { it.id }) { node ->
                        NodeRow(
                            node = node,
                            itemCount = if (node.isFolder) nodes.count { it.parentId == node.id } else node.songIds.size,
                            accent = accent,
                            onOpen = { if (node.isFolder) folderId = node.id else openId = node.id },
                            vm = vm,
                            onDialog = { dialog = it },
                        )
                    }
                }
            }
        }
    }

    // ---- dialogs ----
    when (val d = dialog) {
        is PlaylistDialog.New -> TextInputDialog(
            title = if (d.folder) "New folder" else "New playlist",
            initial = "",
            confirm = "Create",
            onConfirm = { name ->
                if (d.folder) vm.playlists.createFolder(name, folderId)
                else vm.playlists.createPlaylist(name, folderId)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is PlaylistDialog.Rename -> TextInputDialog(
            title = "Rename",
            initial = d.node.name,
            confirm = "Save",
            onConfirm = { vm.playlists.rename(d.node.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is PlaylistDialog.Move -> MoveDialog(
            node = d.node,
            store = vm.playlists,
            onMove = { parent -> vm.playlists.move(d.node.id, parent); dialog = null },
            onDismiss = { dialog = null },
        )
        is PlaylistDialog.Delete -> ConfirmDialog(
            title = "Delete ${d.node.name}?",
            text = if (d.node.isFolder) "The folder and everything inside it will be deleted. Your music files are not touched."
            else "The playlist will be deleted. Your music files are not touched.",
            confirm = "Delete",
            onConfirm = {
                if (openId == d.node.id) openId = null
                vm.playlists.delete(d.node.id); dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> {}
    }
}

@Composable
private fun Crumb(text: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
        color = if (current) BladeColors.Text else BladeColors.TextDim,
        maxLines = 1,
        modifier = Modifier.padding(vertical = 10.dp).then(
            if (current) Modifier else Modifier.clickable(onClick = onClick)
        ),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeRow(
    node: PlaylistNode,
    itemCount: Int,
    accent: Color,
    onOpen: () -> Unit,
    vm: AppViewModel,
    onDialog: (PlaylistDialog) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (node.isFolder) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = if (node.isFolder) accent else BladeColors.TextDim,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name, fontSize = 16.sp, color = BladeColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (node.isFolder) "Folder • $itemCount item${if (itemCount == 1) "" else "s"}"
                    else "$itemCount song${if (itemCount == 1) "" else "s"}",
                    fontSize = 13.sp, color = BladeColors.TextDim,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!node.isFolder) {
                DropdownMenuItem(text = { Text("Play") }, onClick = { vm.playPlaylist(node); menu = false })
                DropdownMenuItem(text = { Text("Shuffle") }, onClick = { vm.playPlaylist(node, shuffle = true); menu = false })
            }
            DropdownMenuItem(text = { Text("Rename…") }, onClick = { onDialog(PlaylistDialog.Rename(node)); menu = false })
            DropdownMenuItem(text = { Text("Move to…") }, onClick = { onDialog(PlaylistDialog.Move(node)); menu = false })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { onDialog(PlaylistDialog.Delete(node)); menu = false })
        }
    }
}

@Composable
private fun PlaylistDetail(
    node: PlaylistNode,
    vm: AppViewModel,
    actions: SongActions,
    accent: Color,
    onBack: () -> Unit,
    onDialog: (PlaylistDialog) -> Unit,
) {
    val library by vm.library.collectAsStateWithLifecycle()
    val state by vm.player.state.collectAsStateWithLifecycle()
    // Keep each song's position in the playlist so remove/move hit the right entry.
    val entries = remember(node, library) {
        node.songIds.mapIndexedNotNull { i, id -> library.byId[id]?.let { i to it } }
    }
    val songs = entries.map { it.second }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) vm.exportPlaylist(uri, node)
    }

    Column(Modifier.fillMaxSize()) {
        DetailTitle(
            node.name,
            "${songs.size} song${if (songs.size == 1) "" else "s"} • ${formatTime(songs.sumOf { it.durationMs })}",
            onBack,
        )
        GroupButtons(accent, { vm.play(songs, 0) }, { vm.shuffle(songs) }) { close ->
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { vm.player.addToQueue(songs); vm.toast("Added to queue"); close() })
            DropdownMenuItem(text = { Text("Export as M3U…") }, onClick = { exporter.launch("${node.name}.m3u"); close() })
            DropdownMenuItem(text = { Text("Edit tags of all songs…") }, onClick = { actions.onEditTags(songs); close() })
            DropdownMenuItem(text = { Text("Rename…") }, onClick = { onDialog(PlaylistDialog.Rename(node)); close() })
            DropdownMenuItem(text = { Text("Move to…") }, onClick = { onDialog(PlaylistDialog.Move(node)); close() })
            DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { onDialog(PlaylistDialog.Delete(node)); close() })
        }
        if (songs.isEmpty()) {
            CenterMessage("Empty playlist — long-press any song and pick \"Add to playlist\"")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(entries.size, key = { "${entries[it].first}-${entries[it].second.id}" }) { pos ->
                    val (index, song) = entries[pos]
                    SongRow(
                        song, vm, actions, song.id == state.songId, accent,
                        onClick = { vm.play(songs, pos) },
                        leading = "${pos + 1}",
                    ) { close ->
                        if (index > 0) DropdownMenuItem(text = { Text("Move up") }, onClick = {
                            vm.playlists.moveSong(node.id, index, index - 1); close()
                        })
                        if (index < node.songIds.lastIndex) DropdownMenuItem(text = { Text("Move down") }, onClick = {
                            vm.playlists.moveSong(node.id, index, index + 1); close()
                        })
                        DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = {
                            vm.playlists.removeAt(node.id, index); close()
                        })
                    }
                }
            }
        }
    }
}
