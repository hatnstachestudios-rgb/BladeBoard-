package com.bladeboard.launcher.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String,
    val art: ImageBitmap?,
    val durationMs: Long,
    val isPlaying: Boolean,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val packageName: String,
) {
    /** Live position, extrapolated from the last update while playing. */
    fun currentPositionMs(): Long {
        val p = if (isPlaying) positionMs + ((SystemClock.elapsedRealtime() - positionUpdatedAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Follows whichever app is playing media and exposes it, plus transport controls, as Compose state. */
class MediaRepository(private val context: Context) {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())

    var hasAccess by mutableStateOf(false)
        private set
    var nowPlaying by mutableStateOf<NowPlaying?>(null)
        private set

    private var controller: MediaController? = null
    private var listening = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    /** Re-check permission and re-scan sessions. Call on resume. */
    fun refresh() {
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        if (!hasAccess) {
            release()
            return
        }
        try {
            if (!listening) {
                sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
                listening = true
            }
            pick(sessionManager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            hasAccess = false
            release()
        }
    }

    private fun pick(sessions: List<MediaController>) {
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller ?: run { nowPlaying = null; return }
        val m = c.metadata
        val s = c.playbackState
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.description?.iconBitmap
        nowPlaying = NowPlaying(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: m?.description?.title?.toString() ?: "Unknown track",
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            art = art?.asImageBitmap(),
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            isPlaying = s?.state == PlaybackState.STATE_PLAYING,
            positionMs = s?.position ?: 0L,
            positionUpdatedAt = s?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            speed = s?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            packageName = c.packageName,
        )
    }

    fun playPause() {
        val t = controller?.transportControls ?: return
        if (nowPlaying?.isPlaying == true) t.pause() else t.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }

    private fun release() {
        if (listening) {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
            listening = false
        }
        controller?.unregisterCallback(controllerCallback)
        controller = null
        nowPlaying = null
    }

    fun stop() = release()
}
