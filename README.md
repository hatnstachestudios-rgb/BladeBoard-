# Bladeboard

A customizable Android home screen built around sliding "blades": full-height panels with a colored tab edge that slide over one another.

## Blades

| Blade | What it does |
|-------|--------------|
| **Home** | Big clock + date (tap for alarms), grid of pinned apps |
| **Music** | Now playing from any player (Musicolet, Spotify…): artwork, seek bar, play/pause/skip, default player launcher. Needs Notification access |
| **Apps** | Searchable app drawer; Enter launches the top match; long-press for Pin / App info / Uninstall |
| **Quick** | Shortcut tiles: Wi-Fi, Bluetooth, display, sound, battery, wallpaper, default home, settings |
| **Style** | Every customization option, applied live |

Navigate by swiping left/right or tapping a blade's tab. Back and Home both return to the Home blade.

## Customization (Style blade)

- 5 theme palettes (Aurora, Ember, Tide, Neon, Graphite)
- Background: animated gradient, system wallpaper, or solid
- Per-blade accent color (13 swatches or palette default)
- Icon size, grid columns, tab width
- App labels, blade depth effect, 24-hour clock

## Running it

1. Open the `bladeboard` folder in Android Studio (Ladybug or newer). It will download Gradle 8.11.1 and the SDK pieces it needs.
2. Run on a device or emulator (API 26+).
3. Press Home and pick **Bladeboard**, or use **Style → Make Bladeboard my home screen**.

## Code map

```
app/src/main/java/com/bladeboard/launcher/
  MainActivity.kt          Home-screen activity, Home-button handling
  data/AppRepository.kt    Loads apps via LauncherApps, live updates on install/uninstall
  data/LauncherSettings.kt All settings + pinned apps, persisted and Compose-observable
  ui/BladeDashboard.kt     Blade stack layout, slide animation, swipe gestures
  ui/Components.kt         Blade, vertical tab text, background, AppTile
  ui/blades/               Home, Apps, Quick, Style
```

## Ideas for next steps

- Icon pack support
- Reorder / add / remove blades (e.g. a widgets blade via AppWidgetHost)
- Notification badges, work-profile apps, haptics and UI sounds
