package com.hatnstache.bladetunes.data

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.resume

data class TagFields(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
    val disc: String = "",
)

/** Musicolet-style tag editing: writes straight into the audio file, then rescans it. */
object TagEditor {

    init {
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun hasFileAccess(): Boolean = Environment.isExternalStorageManager()

    suspend fun read(path: String): TagFields = withContext(Dispatchers.IO) {
        val tag = AudioFileIO.read(File(path)).tagOrCreateAndSetDefault
        TagFields(
            title = tag.first(FieldKey.TITLE),
            artist = tag.first(FieldKey.ARTIST),
            album = tag.first(FieldKey.ALBUM),
            albumArtist = tag.first(FieldKey.ALBUM_ARTIST),
            genre = tag.first(FieldKey.GENRE),
            year = tag.first(FieldKey.YEAR),
            track = tag.first(FieldKey.TRACK),
            disc = tag.first(FieldKey.DISC_NO),
        )
    }

    /** Writes [fields] to each file. Blank fields are left alone when editing several files. */
    suspend fun write(
        context: Context,
        paths: List<String>,
        fields: TagFields,
        skipBlank: Boolean,
    ): Int = withContext(Dispatchers.IO) {
        var ok = 0
        for (path in paths) {
            runCatching {
                val audio = AudioFileIO.read(File(path))
                val tag = audio.tagOrCreateAndSetDefault
                tag.put(FieldKey.TITLE, fields.title, skipBlank)
                tag.put(FieldKey.ARTIST, fields.artist, skipBlank)
                tag.put(FieldKey.ALBUM, fields.album, skipBlank)
                tag.put(FieldKey.ALBUM_ARTIST, fields.albumArtist, skipBlank)
                tag.put(FieldKey.GENRE, fields.genre, skipBlank)
                tag.put(FieldKey.YEAR, fields.year, skipBlank)
                tag.put(FieldKey.TRACK, fields.track, skipBlank)
                tag.put(FieldKey.DISC_NO, fields.disc, skipBlank)
                audio.commit()
                ok++
            }
        }
        scan(context, paths)
        ok
    }

    /** Best-effort: saves a 1–5 star rating into the file's own rating tag. */
    suspend fun writeRating(context: Context, path: String, stars: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val audio = AudioFileIO.read(File(path))
            val tag = audio.tagOrCreateAndSetDefault
            val isId3 = path.endsWith(".mp3", ignoreCase = true)
            // ID3 POPM uses 0–255 (Windows Media Player scale); Vorbis/FLAC use 0–100.
            val value = if (isId3) listOf(0, 1, 64, 128, 196, 255)[stars.coerceIn(0, 5)] else stars * 20
            if (stars == 0) tag.deleteField(FieldKey.RATING) else tag.setField(FieldKey.RATING, value.toString())
            audio.commit()
            scan(context, listOf(path))
        }
    }

    private fun Tag.first(key: FieldKey): String = runCatching { getFirst(key) }.getOrDefault("") ?: ""

    private fun Tag.put(key: FieldKey, value: String, skipBlank: Boolean) {
        val v = value.trim()
        if (v.isEmpty()) {
            if (!skipBlank) runCatching { deleteField(key) }
        } else {
            runCatching { setField(key, v) }
        }
    }

    private suspend fun scan(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            var left = paths.size
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                left--
                if (left == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }
}

/** Star ratings kept by the app (always works, even for files that can't be written). */
class RatingStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ratings", Context.MODE_PRIVATE)
    private val _ratings = MutableStateFlow(
        prefs.all.mapNotNull { (k, v) -> k.toLongOrNull()?.let { id -> id to ((v as? Int) ?: 0) } }.toMap()
    )
    val ratings: StateFlow<Map<Long, Int>> = _ratings.asStateFlow()

    fun set(songId: Long, stars: Int) {
        prefs.edit().apply {
            if (stars == 0) remove(songId.toString()) else putInt(songId.toString(), stars)
        }.apply()
        _ratings.value = _ratings.value.toMutableMap().apply {
            if (stars == 0) remove(songId) else put(songId, stars)
        }
    }
}
