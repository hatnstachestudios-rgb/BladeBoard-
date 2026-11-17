package com.hatnstache.bladetunes.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Song(
    val id: Long,
    val uri: Uri,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val genre: String,
    val durationMs: Long,
    val track: Int,
    val disc: Int,
    val year: Int,
    val dateAdded: Long,
    /** Which album this song is grouped into (see [MusicScanner.groupAlbums]). */
    val albumKey: String = "",
)

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val cover: Song get() = songs.first()
}

data class Artist(
    val name: String,
    val songs: List<Song>,
) {
    val albumCount: Int get() = songs.map { it.albumKey }.distinct().size
}

data class LibraryData(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val loaded: Boolean = false,
) {
    val byId: Map<Long, Song> by lazy { songs.associateBy { it.id } }
}

/** Reads the on-device music library from Android's MediaStore. No network involved. */
object MusicScanner {

    private const val UNKNOWN = "<unknown>"

    suspend fun scan(context: Context): LibraryData = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albumArtistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val genreC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val dataC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val addedC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val rawTrack = c.getInt(trackC) // MediaStore packs disc*1000 + track
                songs += Song(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    path = c.getString(dataC) ?: "",
                    title = c.getString(titleC)?.takeIf { it.isNotBlank() } ?: "Untitled",
                    artist = clean(c.getString(artistC)) ?: "Unknown artist",
                    album = clean(c.getString(albumC)) ?: "Unknown album",
                    albumId = c.getLong(albumIdC),
                    albumArtist = clean(c.getString(albumArtistC)) ?: "",
                    genre = clean(c.getString(genreC)) ?: "",
                    durationMs = c.getLong(durC),
                    track = rawTrack % 1000,
                    disc = rawTrack / 1000,
                    year = c.getInt(yearC),
                    dateAdded = c.getLong(addedC),
                )
            }
        }

        val grouped = groupAlbums(songs).sortedBy { it.title.lowercase() }

        val albums = grouped.groupBy { it.albumKey }.map { (key, list) ->
            val sorted = list.sortedWith(compareBy({ it.disc }, { it.track }, { it.title.lowercase() }))
            Album(
                key = key,
                title = sorted.first().album,
                artist = albumArtistOf(sorted),
                year = sorted.maxOf { it.year },
                songs = sorted,
            )
        }.sortedBy { it.title.lowercase() }

        // A song shows under every artist in a multi-artist tag ("BigXthaPlug; Snoop Dogg").
        val artistNames = mutableMapOf<String, String>()
        val artistSongs = mutableMapOf<String, MutableList<Song>>()
        for (song in grouped) {
            for (name in splitArtists(song.albumArtist.ifBlank { song.artist })) {
                val k = norm(name)
                artistNames.getOrPut(k) { name }
                artistSongs.getOrPut(k) { mutableListOf() } += song
            }
        }
        val artists = artistSongs.map { (k, list) ->
            Artist(
                name = artistNames.getValue(k),
                songs = list.distinctBy { it.id }.sortedWith(
                    compareBy({ it.album.lowercase() }, { it.albumKey }, { it.disc }, { it.track })
                ),
            )
        }.sortedBy { it.name.lowercase() }

        LibraryData(grouped, albums, artists, loaded = true)
    }

    /**
     * Groups songs into albums by album name, like Musicolet, instead of Android's album id.
     *
     * Songs with the same album name stay together when they're in the same folder OR their
     * album-artist tags share any artist (so "BigXthaPlug; Ro$ama" and "BigXthaPlug; Snoop Dogg"
     * are one album). Only same-named albums with nothing in common — different folders and
     * no shared artist, like two different "Greatest Hits" — are kept apart.
     */
    fun groupAlbums(songs: List<Song>): List<Song> =
        songs.groupBy { norm(it.album) }.flatMap { (name, list) ->
            if (list.size == 1) return@flatMap list.map { it.copy(albumKey = name) }

            // Union-find over the songs in this name group.
            val parent = IntArray(list.size) { it }
            fun find(i: Int): Int {
                var x = i
                while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }
                return x
            }
            fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

            val byFolder = mutableMapOf<String, Int>()
            val byArtist = mutableMapOf<String, Int>()
            list.forEachIndexed { i, s ->
                val folder = s.path.substringBeforeLast('/', "").lowercase()
                if (folder.isNotEmpty()) byFolder[folder]?.let { union(i, it) } ?: run { byFolder[folder] = i }
                for (artist in splitArtists(s.albumArtist).map(::norm)) {
                    byArtist[artist]?.let { union(i, it) } ?: run { byArtist[artist] = i }
                }
            }

            // Songs with no album artist that ended up alone join the biggest group.
            val clusters = list.indices.groupBy { find(it) }
            val biggest = clusters.maxBy { it.value.size }.key
            for ((root, members) in clusters) {
                if (root != biggest && members.all { list[it].albumArtist.isBlank() }) union(root, biggest)
            }

            // Biggest group keeps the plain key; any genuinely separate album gets a suffix.
            val roots = list.indices.groupBy { find(it) }.entries.sortedByDescending { it.value.size }
            val keyOf = roots.mapIndexed { n, e -> e.key to if (n == 0) name else "$name#$n" }.toMap()
            list.mapIndexed { i, s -> s.copy(albumKey = keyOf.getValue(find(i))) }
        }

    /** Splits multi-artist tags like "BigXthaPlug; Ro$ama" (only on ';' so "AC/DC" survives). */
    fun splitArtists(tag: String): List<String> =
        tag.split(';', '\u0000').map { it.trim() }.filter { it.isNotEmpty() }

    /** The artist most of the album's songs share, or "Various artists". */
    private fun albumArtistOf(songs: List<Song>): String {
        val names = mutableMapOf<String, String>()
        val counts = mutableMapOf<String, Int>()
        for (s in songs) {
            for (a in splitArtists(s.albumArtist.ifBlank { s.artist })) {
                val k = norm(a)
                names.getOrPut(k) { a }
                counts[k] = (counts[k] ?: 0) + 1
            }
        }
        val top = counts.maxByOrNull { it.value } ?: return "Unknown artist"
        return if (counts.size == 1 || top.value * 2 >= songs.size) names.getValue(top.key) else "Various artists"
    }

    private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun clean(s: String?): String? =
        s?.takeIf { it.isNotBlank() && it != UNKNOWN }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
