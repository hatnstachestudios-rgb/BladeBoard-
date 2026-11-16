# BladeTunes

An offline music player that is also an Android launcher, styled after the Xbox 360 "Blades" dashboard.
Separate from BladeBoard: this branch (`bladetunes`) shares no code or history with `main`.

- **Strictly offline.** The app has no INTERNET permission at all. It plays the music files stored on the phone.
- **App or launcher.** Open it like any app, or pick it as your home screen (System blade → *Use as home screen*). In launcher mode the System blade becomes your app drawer and Home always returns to Now Playing.

## Blades

| Blade | What it does |
|-------|--------------|
| **now playing** | Classic-iPod style screen stretched for tall phones: "Now Playing" title bar with play state and real battery, tilted album art with reflection, title / artist / album, tap-to-rate stars, "3 of 12", glossy seek bar with elapsed and remaining time. Silver media controls (shuffle, previous, play/pause, next, repeat) sit underneath. |
| **songs** | Every song, searchable. Shuffle all. |
| **albums** | Cover grid → album page with tracks in order. |
| **artists** | Artist list → songs grouped by album. |
| **playlists** | Spotify-style folders: folders can hold playlists and other folders. Create, rename, move, delete. Import / export `.m3u`. Reorder or remove songs. |
| **system** | Clock, battery, library stats, rescan, permissions, home-screen setting, and (in launcher mode) the app drawer. |

Long-press any song for **Play next**, **Add to queue**, **Add to playlist…**, **Edit tags…**. Long-press an album (or use ⋮ on album / artist / playlist pages) to edit tags for many songs at once.

## Tag editing

Edits title, artist, album, album artist, genre, year, track and disc straight into the file (MP3, FLAC, M4A, OGG…) using jaudiotagger, then rescans so the change shows up everywhere. Star ratings are kept by the app and also written into the file's rating tag where the format supports it.

Needs **All files access** once (the app asks the first time you edit, or use System → *Allow file access*).

## Building

Every push to this branch builds a debug APK in GitHub Actions. The newest one is in **Releases → "BladeTunes build N"** as `BladeTunes.apk`. Builds are signed with the same key as BladeBoard (repo secret `KEYSTORE_PASSWORD`), so each one installs over the last.

Or open the project in Android Studio and run it (Android 11 / API 30 or newer).

## Code map

```
app/src/main/java/com/hatnstache/bladetunes/
├── MainActivity.kt           permissions, Home-button handling
├── AppViewModel.kt           library, player, playlists, ratings, tag saving
├── data/
│   ├── Library.kt            MediaStore scan → songs, albums, artists
│   ├── PlaylistStore.kt      playlist folder tree (JSON) + M3U import/export
│   └── TagEditor.kt          jaudiotagger read/write, rating tags, RatingStore
├── playback/
│   ├── PlaybackService.kt    Media3 ExoPlayer + MediaSession (notification, lock screen, headset)
│   └── PlayerConnection.kt   MediaController wrapper exposing PlayerState
└── ui/
    ├── BladesApp.kt          blade shell: tabs stacked left/right, pager, dialogs
    ├── NowPlayingBlade.kt    iPod screen + controls
    ├── LibraryBlades.kt      songs, albums, artists, song row + menus
    ├── PlaylistsBlade.kt     folders, playlist pages
    ├── SystemBlade.kt        clock, settings, app drawer
    ├── Dialogs.kt            tag editor, add-to-playlist, move, rename
    └── Common.kt             colors, album art loading/cache, shared widgets
```
