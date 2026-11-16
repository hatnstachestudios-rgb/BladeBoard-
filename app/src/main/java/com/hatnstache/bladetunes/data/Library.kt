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
)

data class Album(
    val id: Long,
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
    val albumCount: Int get() = songs.map { it.albumId }.distinct().size
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

        songs.sortBy { it.title.lowercase() }

        val albums = songs.groupBy { it.albumId }.map { (id, list) ->
            val sorted = list.sortedWith(compareBy({ it.disc }, { it.track }, { it.title.lowercase() }))
            val first = sorted.first()
            Album(
                id = id,
                title = first.album,
                artist = first.albumArtist.ifBlank { first.artist },
                year = sorted.maxOf { it.year },
                songs = sorted,
            )
        }.sortedBy { it.title.lowercase() }

        val artists = songs.groupBy { it.albumArtist.ifBlank { it.artist } }.map { (name, list) ->
            Artist(
                name = name,
                songs = list.sortedWith(
                    compareBy({ it.album.lowercase() }, { it.albumId }, { it.disc }, { it.track })
                ),
            )
        }.sortedBy { it.name.lowercase() }

        LibraryData(songs, albums, artists, loaded = true)
    }

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
