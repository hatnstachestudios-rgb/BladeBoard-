package com.hatnstache.bladetunes.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * One entry in the playlist tree. Folders hold playlists and other folders (any depth);
 * playlists hold song ids. Everything is a flat list linked by [parentId] (null = top level).
 */
data class PlaylistNode(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val parentId: String?,
    val songIds: List<Long> = emptyList(),
)

/** Spotify-style playlist folders, saved as JSON in the app's private storage. */
class PlaylistStore(private val context: Context) {

    private val file = File(context.filesDir, "playlists.json")
    private val _nodes = MutableStateFlow(load())
    val nodes: StateFlow<List<PlaylistNode>> = _nodes.asStateFlow()

    fun children(parentId: String?): List<PlaylistNode> =
        _nodes.value.filter { it.parentId == parentId }
            .sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() }))

    fun get(id: String): PlaylistNode? = _nodes.value.firstOrNull { it.id == id }

    /** Folder chain from the top level down to [id], for the breadcrumb. */
    fun pathTo(id: String?): List<PlaylistNode> {
        val out = mutableListOf<PlaylistNode>()
        var cur = id?.let { get(it) }
        while (cur != null) {
            out.add(0, cur)
            cur = cur.parentId?.let { get(it) }
        }
        return out
    }

    /** Every folder with its depth, for "Move to…" pickers. */
    fun folderTree(): List<Pair<PlaylistNode, Int>> {
        val out = mutableListOf<Pair<PlaylistNode, Int>>()
        fun walk(parent: String?, depth: Int) {
            children(parent).filter { it.isFolder }.forEach {
                out += it to depth
                walk(it.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    /** Every playlist with its depth (folders included as headers), for "Add to playlist". */
    fun fullTree(): List<Pair<PlaylistNode, Int>> {
        val out = mutableListOf<Pair<PlaylistNode, Int>>()
        fun walk(parent: String?, depth: Int) {
            children(parent).forEach {
                out += it to depth
                if (it.isFolder) walk(it.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    fun descendantsOf(id: String): Set<String> {
        val out = mutableSetOf<String>()
        fun walk(p: String) {
            _nodes.value.filter { it.parentId == p }.forEach { out += it.id; walk(it.id) }
        }
        walk(id)
        return out
    }

    fun createFolder(name: String, parentId: String?): PlaylistNode =
        add(PlaylistNode(newId(), name.trim().ifBlank { "New folder" }, true, parentId))

    fun createPlaylist(name: String, parentId: String?, songIds: List<Long> = emptyList()): PlaylistNode =
        add(PlaylistNode(newId(), name.trim().ifBlank { "New playlist" }, false, parentId, songIds))

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim().ifBlank { it.name }) }

    fun move(id: String, newParentId: String?) {
        if (newParentId == id || (newParentId != null && newParentId in descendantsOf(id))) return
        update(id) { it.copy(parentId = newParentId) }
    }

    fun delete(id: String) {
        val gone = descendantsOf(id) + id
        save(_nodes.value.filterNot { it.id in gone })
    }

    fun addSongs(playlistId: String, ids: List<Long>) =
        update(playlistId) { it.copy(songIds = it.songIds + ids) }

    fun removeAt(playlistId: String, index: Int) =
        update(playlistId) { n -> n.copy(songIds = n.songIds.filterIndexed { i, _ -> i != index }) }

    fun moveSong(playlistId: String, from: Int, to: Int) = update(playlistId) { n ->
        if (from !in n.songIds.indices || to !in n.songIds.indices) return@update n
        val list = n.songIds.toMutableList()
        list.add(to, list.removeAt(from))
        n.copy(songIds = list)
    }

    // ---- M3U import / export (so playlists aren't locked inside the app) ----

    fun exportM3u(uri: Uri, node: PlaylistNode, library: LibraryData) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { w ->
            w.write("#EXTM3U\n")
            w.write("#PLAYLIST:${node.name}\n")
            node.songIds.mapNotNull { library.byId[it] }.forEach { s ->
                w.write("#EXTINF:${s.durationMs / 1000},${s.artist} - ${s.title}\n")
                w.write(s.path + "\n")
            }
        }
    }

    /** Returns the new playlist and how many lines couldn't be matched to a song. */
    fun importM3u(uri: Uri, parentId: String?, library: LibraryData): Pair<PlaylistNode, Int> {
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readLines().orEmpty()
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        var name = (displayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Imported").substringBeforeLast('.')
        val byPath = library.songs.associateBy { it.path.lowercase() }
        val byFile = library.songs.groupBy { it.path.substringAfterLast('/').lowercase() }
        val ids = mutableListOf<Long>()
        var missed = 0
        for (raw in lines) {
            val line = raw.trim().removePrefix("﻿")
            if (line.startsWith("#PLAYLIST:")) { name = line.removePrefix("#PLAYLIST:").trim(); continue }
            if (line.isEmpty() || line.startsWith("#")) continue
            val norm = line.replace('\\', '/')
            val song = byPath[norm.lowercase()]
                ?: library.songs.firstOrNull { it.path.endsWith(norm.trimStart('.', '/'), ignoreCase = true) }
                ?: byFile[norm.substringAfterLast('/').lowercase()]?.firstOrNull()
            if (song != null) ids += song.id else missed++
        }
        return createPlaylist(name, parentId, ids) to missed
    }

    // ---- storage ----

    private fun add(n: PlaylistNode): PlaylistNode { save(_nodes.value + n); return n }

    private fun update(id: String, f: (PlaylistNode) -> PlaylistNode) =
        save(_nodes.value.map { if (it.id == id) f(it) else it })

    private fun newId() = UUID.randomUUID().toString()

    private fun save(list: List<PlaylistNode>) {
        _nodes.value = list
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id)
                put("name", n.name)
                put("folder", n.isFolder)
                put("parent", n.parentId ?: JSONObject.NULL)
                put("songs", JSONArray().apply { n.songIds.forEach { put(it) } })
            })
        }
        val tmp = File(file.parentFile, "playlists.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun load(): List<PlaylistNode> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val songs = o.getJSONArray("songs")
            PlaylistNode(
                id = o.getString("id"),
                name = o.getString("name"),
                isFolder = o.getBoolean("folder"),
                parentId = if (o.isNull("parent")) null else o.getString("parent"),
                songIds = (0 until songs.length()).map { songs.getLong(it) },
            )
        }
    }.getOrDefault(emptyList())
}
