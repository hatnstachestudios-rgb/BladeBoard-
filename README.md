# GPS Auto

A working Android GPS app styled like a GTA minimap: warm gray city blocks in 3D, a glowing pink route, a dark player arrow, round blips for your saved places, and an N marker riding the edge. It runs on your phone and on Android Auto.

## What it does

- **Turn-by-turn navigation** with spoken directions (music ducks while it talks), automatic rerouting, and arrival detection.
- **Three ways to see it:** full-screen map, a rounded GTA-style minimap inside the app, or a **floating minimap** over other apps (drag to move, tap to open).
- **Saved places as blips:** long-press the map, or search an address and tap the bookmark. Pick an icon (home, work, gym, friend, food, gas, shop, favorite) and a color. Off-screen blips stick to the map edge and slide as you turn. Tap a blip to navigate.
- **Map controls:** pinch to zoom (zooming out flattens the tilt into a top-down map), drag to look around, **Preview** to scroll along the route with a slider, **Overview** for the whole trip, and a **Re-center** button that appears whenever you move the map (it also snaps back on its own after 10 s while navigating).
- **Now playing:** title, art and previous / play-pause / next for whatever music app is playing (needs Notification access).
- **Android Auto:** the same map on the car screen, with Google's turn card and ETA on top, pinch/drag/zoom/re-center, a minimap mode, saved places and search.

## Data sources (free, no keys)

- Map tiles: [OpenFreeMap](https://openfreemap.org) (OpenStreetMap data)
- Routing: the public [OSRM](https://project-osrm.org) server
- Search: [Photon](https://photon.komoot.io)

These public servers are meant for light personal use, which is what this app is.

## Installing

Every push to the `gps-auto` branch builds an APK. Grab **GPSAuto.apk** from Releases → "GPS Auto build N" and open it on your phone.

### Android Auto (sideloaded apps)

1. Open the Android Auto settings on your phone, tap **Version** about ten times to unlock developer settings.
2. In the ⋮ menu → **Developer settings**, turn on **Unknown sources**.
3. Open GPS Auto on the phone once to allow location, then plug into the car. It shows up in the Android Auto launcher.

## Code map

```
app/src/main/java/com/hatnstache/gpsauto/
  nav/       NavigationEngine (route progress, voice, rerouting), Routing (OSRM, Photon, instructions),
             LocationHub (shared GPS), NavService (foreground service + floating minimap host), Geo, VoiceGuide
  map/       GtaMap (style, route line, arrow, camera modes), BlipOverlayView (blips, edge blips, N marker)
  overlay/   MinimapOverlay (floating window)
  ui/        GpsScreen (phone UI), Dialogs (search, save place)
  car/       Android Auto: CarMapRenderer (map onto the car surface), NavScreen, Places/Search screens
  media/     Now-playing controls
app/src/main/assets/gta_style.json   the map look
```
