package com.hatnstache.bladetunes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hatnstache.bladetunes.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------- colors / theme

object BladeColors {
    val NowPlaying = Color(0xFF5DC21E)
    val Songs = Color(0xFF2E9CE6)
    val Albums = Color(0xFFF28C1E)
    val Artists = Color(0xFFB04FD6)
    val Playlists = Color(0xFFE0B81B)
    val System = Color(0xFF8E9AA3)

    val Background = Color(0xFF070A07)
    val Panel = Color(0xFF161B16)
    val PanelLight = Color(0xFF232A23)
    val Text = Color(0xFFF2F5F2)
    val TextDim = Color(0xFFA9B3A9)
}

@Composable
fun BladesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BladeColors.NowPlaying,
            onPrimary = Color.Black,
            secondary = BladeColors.Songs,
            background = BladeColors.Background,
            surface = BladeColors.Panel,
            surfaceVariant = BladeColors.PanelLight,
            onSurface = BladeColors.Text,
            onBackground = BladeColors.Text,
        ),
        content = content,
    )
}

// ---------------------------------------------------------------- album art

/** Small in-memory cache so scrolling lists don't reload art from disk. */
object ArtCache {
    private val cache = object : LruCache<Long, Bitmap>(96 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
    }
    private val missing: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    fun get(key: Long): Bitmap? = cache.get(key)
    fun put(key: Long, bmp: Bitmap) { cache.put(key, bmp) }
    fun isMissing(key: Long) = key in missing
    fun markMissing(key: Long) { missing += key }
    fun clear() { cache.evictAll(); missing.clear() }
}

/**
 * Loads the cover for [song]'s album (cached per album).
 * big = true: the original embedded artwork at its native resolution (Now Playing).
 * big = false: a small thumbnail for lists and grids.
 */
@Composable
fun rememberArt(song: Song?, big: Boolean = false): ImageBitmap? {
    if (song == null) return null
    val context = LocalContext.current
    val key = song.albumId * 2 + if (big) 1 else 0
    var bmp by remember(key) { mutableStateOf(ArtCache.get(key)?.asImageBitmap()) }
    LaunchedEffect(key) {
        if (bmp == null && !ArtCache.isMissing(key)) {
            val loaded = withContext(Dispatchers.IO) {
                if (big) loadNativeArt(context, song)
                else runCatching { context.contentResolver.loadThumbnail(song.uri, Size(240, 240), null) }.getOrNull()
            }
            if (loaded != null) {
                ArtCache.put(key, loaded)
                bmp = loaded.asImageBitmap()
            } else {
                ArtCache.markMissing(key)
            }
        }
    }
    return bmp
}

/** Largest side we'll decode. Only giant (8K+) covers get scaled; normal art stays untouched. */
private const val MAX_ART_PX = 4096

private val COVER_NAMES = setOf("cover", "folder", "front", "album", "albumart", "albumartlarge")
private val COVER_EXTS = setOf("jpg", "jpeg", "png", "webp")

/**
 * Full-quality artwork: the picture embedded in the file, byte for byte;
 * if there isn't one, a cover image sitting in the album's folder;
 * last resort, Android's thumbnail.
 */
private fun loadNativeArt(context: Context, song: Song): Bitmap? {
    val embedded = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, song.uri)
            r.embeddedPicture
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()
    embedded?.let { bytes -> decodeFull { opts -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) } }
        ?.let { return it }

    val folderCover = runCatching {
        File(song.path).parentFile?.listFiles()?.firstOrNull {
            it.isFile && it.nameWithoutExtension.lowercase() in COVER_NAMES && it.extension.lowercase() in COVER_EXTS
        }
    }.getOrNull()
    folderCover?.let { f -> decodeFull { opts -> BitmapFactory.decodeFile(f.path, opts) } }?.let { return it }

    return runCatching {
        context.contentResolver.loadThumbnail(song.uri, Size(MAX_ART_PX, MAX_ART_PX), null)
    }.getOrNull()
}

/** Decodes at native size (no downsampling unless the image is absurdly large). */
private fun decodeFull(decode: (BitmapFactory.Options?) -> Bitmap?): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_ART_PX) sample *= 2
    decode(BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    })
}.getOrNull()

@Composable
fun AlbumArt(song: Song?, modifier: Modifier = Modifier, big: Boolean = false, corner: Dp = 4.dp) {
    val art = rememberArt(song, big)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF3A443A), Color(0xFF1C221C)))),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                art, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote, contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
    }
}

// ---------------------------------------------------------------- small shared pieces

@Composable
fun BladeHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, color = BladeColors.TextDim, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun PillButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = BladeColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SearchField(query: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            focusedContainerColor = Color.Black.copy(alpha = 0.25f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.25f),
        ),
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun CenterMessage(text: String, loading: Boolean = false) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator()
            Spacer(Modifier.size(12.dp))
        }
        Text(text, color = BladeColors.TextDim, fontSize = 15.sp)
    }
}

@Composable
fun LeadingNumber(text: String) {
    Text(
        text, color = BladeColors.TextDim, fontSize = 14.sp,
        modifier = Modifier.width(30.dp), maxLines = 1,
    )
}
