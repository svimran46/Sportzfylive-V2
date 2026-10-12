# Sportzfy for Android

A standalone Kotlin and Jetpack Compose client for live sports streams. It reads the Streamed API directly and needs no Cloudflare Worker.

## Stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.2 (coroutines, Flow) |
| UI | Jetpack Compose and Material 3, dark only |
| Architecture | Clean Architecture (data → domain → presentation), MVVM |
| DI | Koin |
| Networking | Retrofit 3, OkHttp 4, kotlinx.serialization |
| Images | Coil 3 |
| Playback | Media3 ExoPlayer for HLS, sandboxed WebView for `embed.st` |

## Layout

```
app/src/main/java/com/sportzfy/live/
├── core/network/   Retrofit service, browser-header interceptor, error mapping
├── core/player/    URL policy, ad-host list, HLS vs embed detection
├── core/theme/     colors, typography, shapes
├── core/time/      injectable clock
├── data/model/     wire DTOs
├── data/mapper/    DTO → domain, today/live merge, stream sanitizing
├── data/repository/ cached match feed, per-source stream resolver
├── domain/model/   Match, Team, StreamItem, SportCategory, EmbedUrl, MatchFeed
├── domain/repository/
├── presentation/watch/player/  StreamPlayer: sandboxed WebView + Media3 HLS
├── presentation/   catalog, watch, components (Phase 4)
└── SportzfyApp.kt
```

## Build

Requires JDK 17+ and the Android SDK (platform 36).

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Data flow

- **Matches:** `MatchRepository` fetches `all-today` and `live` from `https://streamed.pk/api/matches/` at the same time and tags the matches in the live feed as `LIVE`. Results are cached in memory for 60 seconds.
  - If the live feed fails, a match is treated as live when it kicked off within the last 3 hours.
  - If `all-today` fails, the last good copy is merged with the fresh live feed.
- **Streams:** `StreamRepository` resolves each source group from `/api/stream/{source}/{id}` in parallel and caches each one for 60 seconds.
  - A group that fails (429, 5xx or a timeout) is reported back as failed. It never takes down the other groups, and its last good result is served while it is failing.
- **Playback:** stream URLs are always built as `https://embed.st/embed/{source}/{id}/{streamNo}`. Any `embedUrl` sent by upstream is ignored.

## Playback

`StreamPlayer(url)` chooses an engine from the URL. `.m3u8` links play in Media3 ExoPlayer, which fetches segments through the app's OkHttp client. Everything else, which means the `embed.st` pages, plays in a locked-down WebView:

- **Pop-ups:** `setSupportMultipleWindows(false)`, `javaScriptCanOpenWindowsAutomatically = false`, and `onCreateWindow` returns `false`.
- **Navigation:** handled by `PlayerUrlPolicy`.
  - The main frame can only go to `embed.st`, or follow an HTTP redirect from a page that was already allowed.
  - Subframes can load any https page except known ad networks.
  - Every scheme other than https is refused, including `intent:` and `market:`.
- **Ad networks:** requests to hosts in `AdHosts` get an empty response.
- **Dialogs and permissions:** JavaScript dialogs, permission requests, downloads, file access and long-press menus are all disabled.
- **Fullscreen:** `onShowCustomView` and `onHideCustomView` move the video into a fullscreen landscape view with the system bars hidden. Back exits fullscreen.
- **Renderer crashes:** if the renderer dies, the player rebuilds its WebView instead of crashing the app.
