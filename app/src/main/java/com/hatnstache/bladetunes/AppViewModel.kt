package com.hatnstache.bladetunes

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hatnstache.bladetunes.data.ArtistImages
import com.hatnstache.bladetunes.data.LibraryData
import com.hatnstache.bladetunes.data.MusicScanner
import com.hatnstache.bladetunes.data.PlaylistNode
import com.hatnstache.bladetunes.data.PlaylistStore
import com.hatnstache.bladetunes.data.RatingStore
import com.hatnstache.bladetunes.data.Song
import com.hatnstache.bladetunes.data.TagEditor
import com.hatnstache.bladetunes.data.TagFields
import com.hatnstache.bladetunes.playback.PlayerConnection
import com.hatnstache.bladetunes.ui.ArtCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val _library = MutableStateFlow(LibraryData())
    val library: StateFlow<LibraryData> = _library.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val player = PlayerConnection(app)
    val playlists = PlaylistStore(app)
    val ratings = RatingStore(app)
    val artistImages = ArtistImages(app)

    fun refresh() {
        viewModelScope.launch {
            ArtCache.clear()
            _library.value = MusicScanner.scan(getApplication())
        }
    }

    fun toast(msg: String) { _message.value = msg }
    fun clearMessage() { _message.value = null }

    // ---- playback shortcuts ----

    fun play(songs: List<Song>, index: Int) = player.playSongs(songs, index)
    fun shuffle(songs: List<Song>) = player.playSongs(songs, 0, shuffle = true)

    fun playPlaylist(node: PlaylistNode, index: Int = 0, shuffle: Boolean = false) {
        val songs = node.songIds.mapNotNull { _library.value.byId[it] }
        player.playSongs(songs, index, shuffle)
    }

    // ---- ratings ----

    fun setRating(song: Song, stars: Int) {
        ratings.set(song.id, stars)
        if (TagEditor.hasFileAccess() && song.path.isNotEmpty()) {
            viewModelScope.launch { TagEditor.writeRating(getApplication(), song.path, stars) }
        }
    }

    // ---- artist photos ----

    fun setArtistPhoto(artistName: String, uri: Uri) {
        viewModelScope.launch {
            toast(if (artistImages.set(artistName, uri)) "Photo set for $artistName" else "Couldn't use that image")
        }
    }

    fun removeArtistPhoto(artistName: String) {
        artistImages.remove(artistName)
        toast("Photo removed")
    }

    // ---- tag editing ----

    suspend fun readTags(song: Song): TagFields? =
        runCatching { TagEditor.read(song.path) }.getOrNull()

    fun saveTags(songs: List<Song>, fields: TagFields) {
        viewModelScope.launch {
            val ok = TagEditor.write(getApplication(), songs.map { it.path }, fields, skipBlank = songs.size > 1)
            toast(if (ok == songs.size) "Saved tags" else "Saved $ok of ${songs.size} files")
            _library.value = MusicScanner.scan(getApplication())
            ArtCache.clear()
        }
    }

    // ---- playlists ----

    fun addToPlaylist(playlistId: String, songs: List<Song>) {
        playlists.addSongs(playlistId, songs.map { it.id })
        toast("Added ${songs.size} song${if (songs.size == 1) "" else "s"}")
    }

    fun exportPlaylist(uri: Uri, node: PlaylistNode) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { playlists.exportM3u(uri, node, _library.value) }.isSuccess
            }
            toast(if (ok) "Exported ${node.name}" else "Couldn't export")
        }
    }

    fun importPlaylist(uri: Uri, parentId: String?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { playlists.importM3u(uri, parentId, _library.value) }.getOrNull()
            }
            toast(
                when {
                    result == null -> "Couldn't read that playlist"
                    result.second > 0 -> "Imported ${result.first.name} (${result.second} songs not found)"
                    else -> "Imported ${result.first.name}"
                }
            )
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
