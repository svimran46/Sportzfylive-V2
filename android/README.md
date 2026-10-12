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
├── core/network/   upstream endpoints, HTTP client (Phase 2)
├── core/theme/     colors, typography, shapes
├── data/model/     wire DTOs
├── data/mapper/    DTO → domain (Phase 2)
├── data/repository/
├── domain/model/   Match, Team, StreamItem, SportCategory, EmbedUrl
├── domain/repository/
├── presentation/   catalog, watch, components (Phase 4)
└── SportzfyApp.kt
```

## Build

Requires JDK 17+ and the Android SDK (platform 36).

```bash
./gradlew assembleDebug
```
