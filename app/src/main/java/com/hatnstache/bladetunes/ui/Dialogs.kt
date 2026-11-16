package com.hatnstache.bladetunes.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hatnstache.bladetunes.AppViewModel
import com.hatnstache.bladetunes.data.PlaylistNode
import com.hatnstache.bladetunes.data.PlaylistStore
import com.hatnstache.bladetunes.data.Song
import com.hatnstache.bladetunes.data.TagEditor
import com.hatnstache.bladetunes.data.TagFields

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PickRow(icon: ImageVector, label: String, depth: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = (8 + depth * 18).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
            tint = if (enabled) BladeColors.Playlists else BladeColors.TextDim)
        Spacer(Modifier.width(12.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (enabled) BladeColors.Text else BladeColors.TextDim)
    }
}

/** Pick a folder (or the top level) to move a playlist/folder into. */
@Composable
fun MoveDialog(node: PlaylistNode, store: PlaylistStore, onMove: (String?) -> Unit, onDismiss: () -> Unit) {
    val blocked = remember(node) { store.descendantsOf(node.id) + node.id }
    val folders = remember(node) { store.folderTree() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${node.name} to…") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { PickRow(Icons.Rounded.Home, "Top level", 0, enabled = node.parentId != null) { onMove(null) } }
                items(folders, key = { it.first.id }) { (folder, depth) ->
                    PickRow(
                        Icons.Rounded.Folder, folder.name, depth + 1,
                        enabled = folder.id !in blocked && folder.id != node.parentId,
                    ) { onMove(folder.id) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Add songs to an existing playlist anywhere in the folder tree, or to a new one. */
@Composable
fun AddToPlaylistDialog(songs: List<Song>, vm: AppViewModel, onDismiss: () -> Unit) {
    val nodes by vm.playlists.nodes.collectAsStateWithLifecycle()
    val tree = remember(nodes) { vm.playlists.fullTree() }
    var naming by remember { mutableStateOf(false) }

    if (naming) {
        TextInputDialog("New playlist", "", "Create", onConfirm = { name ->
            val p = vm.playlists.createPlaylist(name, null)
            vm.addToPlaylist(p.id, songs)
            onDismiss()
        }, onDismiss = { naming = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (songs.size == 1) "Add “${songs.first().title}” to…" else "Add ${songs.size} songs to…") },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                item { PickRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "New playlist…", 0) { naming = true } }
                items(tree, key = { it.first.id }) { (node, depth) ->
                    if (node.isFolder) {
                        PickRow(Icons.Rounded.Folder, node.name, depth, enabled = false) {}
                    } else {
                        PickRow(Icons.AutoMirrored.Rounded.QueueMusic, node.name, depth) {
                            vm.addToPlaylist(node.id, songs)
                            onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Musicolet-style tag editor. One song: every field. Several songs: shared fields only. */
@Composable
fun TagEditorDialog(songs: List<Song>, vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(TagEditor.hasFileAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = TagEditor.hasFileAccess() }

    if (!hasAccess) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Allow file access") },
            text = {
                Text(
                    "To save tags into your music files, BladeTunes needs \"All files access\". " +
                        "Turn it on for BladeTunes on the next screen, then come back."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        return
    }

    val single = songs.size == 1
    var fields by remember { mutableStateOf<TagFields?>(null) }
    LaunchedEffect(songs) {
        fields = if (single) {
            vm.readTags(songs.first()) ?: songs.first().toFields()
        } else {
            // Prefill only what every selected song has in common.
            fun common(f: (Song) -> String) = (songs.map(f).distinct().singleOrNull() ?: "")
                .takeUnless { it == "Unknown artist" || it == "Unknown album" } ?: ""
            TagFields(
                artist = common { it.artist },
                album = common { it.album },
                albumArtist = common { it.albumArtist },
                genre = common { it.genre },
                year = common { if (it.year > 0) it.year.toString() else "" },
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (single) "Edit tags" else "Edit ${songs.size} songs") },
        text = {
            val f = fields
            if (f == null) {
                CircularProgressIndicator()
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (!single) {
                        Text("Blank fields are left unchanged.", fontSize = 13.sp, color = BladeColors.TextDim)
                        Spacer(Modifier.size(6.dp))
                    }
                    if (single) TagField("Title", f.title) { fields = f.copy(title = it) }
                    TagField("Artist", f.artist) { fields = f.copy(artist = it) }
                    TagField("Album", f.album) { fields = f.copy(album = it) }
                    TagField("Album artist", f.albumArtist) { fields = f.copy(albumArtist = it) }
                    TagField("Genre", f.genre) { fields = f.copy(genre = it) }
                    TagField("Year", f.year) { fields = f.copy(year = it) }
                    if (single) TagField("Track #", f.track) { fields = f.copy(track = it) }
                    TagField("Disc #", f.disc) { fields = f.copy(disc = it) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = fields != null, onClick = {
                fields?.let { vm.saveTags(songs, it) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TagField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    )
}

private fun Song.toFields() = TagFields(
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    genre = genre,
    year = if (year > 0) year.toString() else "",
    track = if (track > 0) track.toString() else "",
    disc = if (disc > 0) disc.toString() else "",
)
