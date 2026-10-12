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
├── core/theme/     colors, typography, shapes
├── core/time/      injectable clock
├── data/model/     wire DTOs
├── data/mapper/    DTO → domain, today/live merge, stream sanitizing
├── data/repository/ cached match feed, per-source stream resolver
├── domain/model/   Match, Team, StreamItem, SportCategory, EmbedUrl, MatchFeed
├── domain/repository/
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
