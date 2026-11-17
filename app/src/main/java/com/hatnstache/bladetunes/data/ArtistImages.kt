package com.hatnstache.bladetunes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Photos you pick for artists. A copy of each photo is saved inside the app,
 * so it keeps working even if you delete the original from your gallery.
 */
class ArtistImages(context: Context) {

    private val dir = File(context.filesDir, "artist_photos").apply { mkdirs() }
    private val resolver = context.contentResolver

    /** file key → last changed time; changes whenever a photo is set or removed. */
    private val _versions = MutableStateFlow(scan())
    val versions: StateFlow<Map<String, Long>> = _versions.asStateFlow()

    fun keyFor(artistName: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(artistName.trim().lowercase().toByteArray())
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    fun fileFor(key: String) = File(dir, "$key.jpg")

    suspend fun set(artistName: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(resolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                // Keep it sharp but not huge: longest side up to 1200px.
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (longest > MAX_PX) {
                    val scale = MAX_PX.toFloat() / longest
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
            }
            val key = keyFor(artistName)
            val tmp = File(dir, "$key.tmp")
            tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val target = fileFor(key)
            target.delete()
            check(tmp.renameTo(target))
            _versions.value = scan()
        }.isSuccess
    }

    fun remove(artistName: String) {
        fileFor(keyFor(artistName)).delete()
        _versions.value = scan()
    }

    private fun scan(): Map<String, Long> =
        dir.listFiles { f -> f.extension == "jpg" }
            ?.associate { it.nameWithoutExtension to it.lastModified() }
            ?: emptyMap()

    private companion object {
        const val MAX_PX = 1200
    }
}
