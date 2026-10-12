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
├── presentation/
│   ├── catalog/      MatchCatalogScreen + CatalogViewModel
│   ├── watch/        WatchStudioScreen + WatchViewModel
│   │   └── player/   StreamPlayer: sandboxed WebView + Media3 HLS
│   ├── components/   MatchCard, HeroSpotlight, CategoryPills, StreamChip, LiveBadge, TeamCrest
│   ├── model/        UI state + pure logic (filtering, labels, related matches)
│   └── navigation/   type-safe routes and NavHost
└── SportzfyApp.kt
```

## Build

Requires JDK 17+ and the Android SDK (platform 36).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # JVM unit tests + Robolectric Compose UI tests
./gradlew assembleRelease      # R8-shrunk release APK (runs lint vital)
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

## Screens

- **Catalog**
  - **Header:** the Sportzfy wordmark, a live-match counter with a pulsing dot, and instant search over team names and sports.
  - **Spotlight banner:** shows the featured match, preferring popular live matches, with a Watch button.
  - **Sport pills:** All, Football, Cricket, Basketball, Tennis and Motorsport, plus any other sport in today's feed. Each pill shows its match count.
  - **Match list:** an adaptive grid with Live now, Upcoming and Earlier today sections. Pull down to refresh; the list also refreshes every 60 seconds while the screen is on.
- **Watch Studio**
  - **Top bar:** Back, plus the sport and match title.
  - **Player:** a 16:9 player that stays pinned above the rest of the screen.
  - **Match details:** both teams with their crests.
  - **Stream pills:** labelled like "Stream 1 HD • English". Picking a pill reloads only the player. The screen also shows how many pop-ups were blocked.
  - **More matches:** a rail of other live and upcoming matches, which switch in place.
  - **Wide screens:** on screens 840dp or wider, the rail becomes a side column. The player instance is kept when the layout changes, so rotating the device doesn't restart the stream.

## Search, saved matches and Android TV

- **Search:** results update as you type. A match is shown when every word you type appears in its title (which is where upstream puts league names, e.g. "Premier League: …"), its team names, its sport, or a common nickname for that sport ("soccer", "F1", "UFC", "NBA", "IPL" and so on).
- **Saved matches:** tap the star on a card, or in the Watch top bar, to save a match. Saved IDs are kept on the device with DataStore, newest first and capped at 200. The **Saved** pill filters the catalog down to them.
- **Android TV:**
  - The app appears in the TV launcher (`LEANBACK_LAUNCHER`) with its own banner, and neither touch nor leanback support is required to install it.
  - Anything you can select gets a 2dp rose border and a slight scale-up when the D-pad focuses it (`Modifier.tvFocus`).
  - Inside the player, Up/Down always move focus out of the WebView, so the remote can't get stuck in the page.

## Testing

All tests run on the JVM with `./gradlew testDebugUnitTest`. No emulator is needed.

| Layer | What is covered |
|---|---|
| Network | Request paths, URL encoding and browser headers, against MockWebServer; mapping error codes to user-facing errors |
| Data | Merging the today and live feeds and setting match status, the stream-data cleanup, the 60-second caches, and each failure-isolation path in both repositories |
| Domain / player | The `embed.st` URL builder, the WebView navigation and ad rules, and choosing HLS or embed |
| Presentation logic | Filtering, search with aliases, the Saved filter, the spotlight pick, related matches, the default stream, and time labels |
| ViewModels | `CatalogViewModel` (sport filter, search, saving matches) and `WatchViewModel` (default stream, stream picker, switching match, missing match, failed sources, favourites) |
| UI (Robolectric) | Tapping a sport pill filters the rendered list; search narrows it; tapping a stream chip makes it the selected stream |

## Release builds

`assembleRelease` shrinks the code and resources with R8. Media3, Coil, OkHttp, Retrofit, kotlinx.serialization and Koin all ship their own keep rules. `app/proguard-rules.pro` adds only the app-specific ones: the generated serializers, the Retrofit service, and keeping line numbers so crash traces can be mapped back.

**Signing.** Set `SPORTZFY_KEYSTORE_FILE`, `SPORTZFY_KEYSTORE_PASSWORD`, `SPORTZFY_KEY_ALIAS` and `SPORTZFY_KEY_PASSWORD` to sign with your own key. Without them, the release APK is signed with the debug key so it can still be installed for testing.

**CI.** CI builds the release APK the same way. To sign with your own key there, add these repository secrets:
- `SPORTZFY_KEYSTORE_BASE64` (the base64-encoded `.jks` file)
- `SPORTZFY_KEYSTORE_PASSWORD`
- `SPORTZFY_KEY_ALIAS`
- `SPORTZFY_KEY_PASSWORD`

Each run uploads three artifacts: `sportzfy-release-apk` (the release APK plus `mapping.txt`), `sportzfy-debug-apk`, and, if the run fails, `test-reports`.
