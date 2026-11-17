package com.hatnstache.bladetunes.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hatnstache.bladetunes.AppViewModel
import com.hatnstache.bladetunes.data.Artist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ArtistPhotoCache {
    val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
}

@Composable
fun rememberHasArtistPhoto(vm: AppViewModel, artistName: String): Boolean {
    val versions by vm.artistImages.versions.collectAsStateWithLifecycle()
    val key = remember(artistName) { vm.artistImages.keyFor(artistName) }
    return versions.containsKey(key)
}

/**
 * Round artist picture: the photo you picked, or (until you pick one)
 * the cover of the artist's newest album, so the list isn't just text.
 */
@Composable
fun ArtistPhoto(vm: AppViewModel, artist: Artist, modifier: Modifier = Modifier) {
    val versions by vm.artistImages.versions.collectAsStateWithLifecycle()
    val key = remember(artist.name) { vm.artistImages.keyFor(artist.name) }
    val version = versions[key]

    val photo by produceState<ImageBitmap?>(null, key, version) {
        value = if (version == null) null else withContext(Dispatchers.IO) {
            val cacheKey = "$key@$version"
            val bmp = ArtistPhotoCache.cache.get(cacheKey)
                ?: BitmapFactory.decodeFile(vm.artistImages.fileFor(key).path)
                    ?.also { ArtistPhotoCache.cache.put(cacheKey, it) }
            bmp?.asImageBitmap()
        }
    }

    val shown = photo
    if (shown != null) {
        Image(
            shown, contentDescription = artist.name,
            contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
            modifier = modifier.clip(CircleShape),
        )
    } else {
        val cover = remember(artist) { artist.songs.maxByOrNull { it.year } ?: artist.songs.firstOrNull() }
        AlbumArt(cover, modifier.clip(CircleShape))
    }
}
