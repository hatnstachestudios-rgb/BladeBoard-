package com.bladeboard.launcher.games

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.bladeboard.launcher.data.AppEntry
import com.bladeboard.launcher.data.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides which apps belong on the Games blade and stores the custom box art for each.
 * Covers are copied into Bladeboard's private storage, so they survive the original photo
 * being moved or deleted.
 */
class GameLibrary(private val context: Context, private val settings: LauncherSettings) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    /** Bumped whenever a cover changes so views reload it. */
    var coverVersion by mutableIntStateOf(0)
        private set

    /** Detected automatically, before the user's own include/exclude edits. */
    fun autoDetected(app: AppEntry): Boolean =
        app.isGame || app.component.packageName in KnownGamePackages

    fun isGame(app: AppEntry): Boolean =
        app.key !in settings.gameExcludes && (autoDetected(app) || app.key in settings.gameIncludes)

    fun games(apps: List<AppEntry>): List<AppEntry> = apps.filter(::isGame)

    fun setInGames(app: AppEntry, inGames: Boolean) {
        val auto = autoDetected(app)
        val inc = settings.gameIncludes.toMutableSet()
        val exc = settings.gameExcludes.toMutableSet()
        inc.remove(app.key); exc.remove(app.key)
        if (inGames && !auto) inc += app.key
        if (!inGames && auto) exc += app.key
        settings.setGameLists(inc, exc)
    }

    private fun fileFor(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".jpg")

    fun hasCover(key: String): Boolean = fileFor(key).exists()

    suspend fun loadCover(key: String): ImageBitmap? = withContext(Dispatchers.IO) {
        cache[key] ?: fileFor(key).takeIf { it.exists() }?.let { f ->
            BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { cache[key] = it }
        }
    }

    /** Copies the picked image in, scaled down to a sensible size. */
    suspend fun saveCover(key: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / (sample * 2) >= MAX_COVER_PX) sample *= 2
            val bmp = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("couldn't read image")
            val scale = minOf(1f, MAX_COVER_PX.toFloat() / maxOf(bmp.width, bmp.height))
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            fileFor(key).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            cache.remove(key)
            true
        }.getOrDefault(false).also { if (it) withContext(Dispatchers.Main) { coverVersion++ } }
    }

    fun removeCover(key: String) {
        fileFor(key).delete()
        cache.remove(key)
        coverVersion++
    }

    companion object {
        private const val MAX_COVER_PX = 1000

        /** Emulators and game-streaming apps that don't mark themselves as games. */
        val KnownGamePackages = setOf(
            "com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32",
            "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold",
            "org.dolphinemu.dolphinemu", "org.citra.citra_emu", "org.citra.emu",
            "com.github.stenzek.duckstation", "xyz.aethersx2.android", "xyz.aethersx2.android2",
            "skyline.emu", "dev.eden.eden_emulator", "io.github.lime3ds.android",
            "com.explusalpha.Snes9xPlus", "com.explusalpha.GbaEmu", "com.explusalpha.MdEmu", "com.explusalpha.NesEmu",
            "me.magnum.melonds", "com.dsemu.drastic", "it.dbtecno.pizzaboypro", "it.dbtecno.pizzaboy",
            "org.yuzu.yuzu_emu", "info.cemu.cemu", "com.flycast.emulator", "io.recompiled.redream",
            "com.winlator", "com.vectras.vm",
            "com.valvesoftware.steamlink", "com.valvesoftware.android.steam.community",
            "com.limelight", "com.limelight.noir", "com.nvidia.geforcenow",
            "com.microsoft.xboxone.smartglass", "com.microsoft.xcloud",
            "com.playstation.remoteplay", "com.scee.psxandroid",
        )
    }
}

/** The custom cover for [key], or null to draw the default card. Reloads when covers change. */
@Composable
fun rememberCover(library: GameLibrary, key: String): ImageBitmap? {
    val cover by produceState<ImageBitmap?>(null, key, library.coverVersion) {
        value = library.loadCover(key)
    }
    return cover
}
