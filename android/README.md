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
  - Only the live feed can mark a match as `LIVE`; kick-off time never does. If the live feed fails, its last good copy is reused for up to 5 minutes. After that, nothing is marked live, matches that have kicked off show as **Started**, and the app says it couldn't check live matches instead of claiming there are none.
  - A forced refresh that was queued behind a running one is dropped, so repeated taps on Refresh never stack requests.
  - If every feed fails, the last matches stay on screen with an error banner. Once the last live list is more than 5 minutes old, LIVE badges are dropped too.
  - A match fetched as upcoming whose kick-off has since passed shows as **Started** until the next refresh. It never shows as Live.
  - The 60-second auto-refresh is silent: spinners only show for refreshes you start.
  - If `all-today` fails, the last good copy is merged with the fresh live feed.
- **Streams:** `StreamRepository` resolves each source group from `/api/stream/{source}/{id}` in parallel and caches each one for 60 seconds. If that direct call fails or comes back blocked, the group is fetched again through the website's Worker (`/api/streamed/streams`, see `streamProxyUrl`).
  - A group that fails (429, 5xx or a timeout) is reported back as failed. It never takes down the other groups, and its last good result is served while it is failing.
- **Playback:** stream URLs are always built as `https://embed.st/embed/{source}/{id}/{streamNo}`. Any `embedUrl` sent by upstream is ignored.

## Playback

`StreamPlayer(url)` chooses an engine from the URL. `.m3u8` links play in Media3 ExoPlayer, which fetches segments through the app's OkHttp client. Everything else, which means the `embed.st` pages, plays in a WebView.

By default (`playerMode: "direct"`), the embed page is loaded as the WebView's own page, like a browser tab, with streamed.pk as the referer (as streamed.pk's own embed iframe sends). Match streams use this, and it works for embeds that show only a black frame when nested in an iframe inside the WebView. Live TV channels run in a **sandboxed iframe** instead (`sandbox="allow-scripts allow-same-origin allow-forms allow-modals allow-presentation"`, no pop-ups, no top-level navigation, no referer), so a channel cannot move the app's page. With `playerMode: "iframe"`, the embed instead runs inside a **plain iframe with no `sandbox` attribute**, the same way the website plays it (`allow="autoplay; fullscreen; encrypted-media; picture-in-picture"`, `allowfullscreen`, `referrerpolicy="no-referrer"`). Each match also has an **Open in browser** button for a stream that won't play in the app.

- **Pop-ups:** `setSupportMultipleWindows(true)` together with `onCreateWindow` returning `false`, so every `window.open` or `target=_blank` is dropped instead of loading over the player. `javaScriptCanOpenWindowsAutomatically` is `false`.
- **Navigation:** handled by `PlayerUrlPolicy`.
  - The outer page can only go to `embed.st`, or follow an HTTP redirect from a page that was already allowed. This stops `top.location` hijacks.
  - Subframes can load any https page except known ad networks.
  - Every scheme other than https is refused, including `intent:` and `market:`.
- **Ad networks:** requests to hosts in `AdHosts` (plus any `extraBlockedHosts`) get an empty response. You can switch the built-in list off remotely with `adBlocking: false`.
- **Dialogs and permissions:** JavaScript dialogs, permission requests, downloads, file access and long-press menus are all disabled.
- **Fullscreen:** `onShowCustomView` and `onHideCustomView` move the video into a fullscreen landscape view with the system bars hidden. Back exits fullscreen.
- **Renderer crashes:** if the renderer dies, the player rebuilds its WebView instead of crashing the app.

## Navigation and dates

A fixed bottom bar has four destinations: **Today** (the start screen), **Tomorrow**, **Live TV** and **All Matches**. It sits above the system navigation bar, hides while the keyboard is open, and isn't shown on the full-screen player. Back from any tab returns to Today; back from Today leaves the app.

- **One feed:** matches come from Streamed's `all-today`, `all` (later days) and `live` endpoints, merged into one list with duplicates removed.
- **Daylight saving:** days where daylight saving skips midnight (Chile, Cairo, Havana) start at 01:00, and that never shifts the neighbouring days. The tabs re-check every minute and exactly at local midnight.
- **Timezone:** "Today" and "Tomorrow" mean the **device's local calendar day** (`ZoneProvider`, `TimeZone.getDefault()`). All the day logic is in `MatchDays`, built on calendar arithmetic, so month and year changes and daylight-saving days are handled. The day boundaries are re-checked every minute, so the tabs roll over at midnight.
- **Today (Home):** today's matches, meaning those live right now (even if they started yesterday), kicking off today, or with no announced time. A **Live | Upcoming | All** switch picks what's shown, and it opens on **Live**:
  - **Live:** only matches in Streamed's live feed. The heading reads "Live Matches" with a live count, which is hidden if the live feed couldn't be checked.
  - **Upcoming:** today's matches that haven't kicked off yet, or have no announced time.
  - **All:** everything for today, grouped as Live now, Started, Upcoming and Earlier today. The All Matches tab is the same idea across every day.
  - **Refresh** reloads the feed, shows a spinner and can't be tapped again until it finishes. The switch, sport filter and search stay as they were.
  - Each empty view suggests where to look next, e.g. "No live matches right now" offers Upcoming and All today, and an empty Upcoming offers Tomorrow.
- **Tomorrow:** matches that kick off on the next local day and aren't live.
- **All Matches:** everything, with live matches first, then one section per day (Yesterday, Today, Tomorrow, then dated days), then matches with no announced time. Every match appears once.
- **Search and filters:** one `CatalogViewModel` serves all three fixture tabs, so search and the sport and Saved filters carry across them.
- **Live TV:** a channel directory, separate from match fixtures. See [Live TV](#live-tv).

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

## Drawer, Settings and About

- **Drawer:** opened by the menu button at the top left of every tab. It covers about 80% of the screen width, up to 320dp. The page behind it dims, it scrolls on short screens, and it stays clear of the status and navigation bars. Tapping outside it, the ✕ or Back closes it, and so does picking an entry. It can't be swiped open, so swipes on sport pills and rails never pull it out by mistake.
  - **Browse:** Live Matches (Home on Live, filters cleared), Live TV, Fixtures (All Matches, filters cleared), Saved Matches (All Matches filtered to saved), and Search (All Matches with the search field open).
  - **Sports:** Football and Cricket filter All Matches to that sport. Other Sports shows everything except football and cricket.
  - **Footer:** Settings and About.
  - **Selected entry:** it shows a pink accent bar and icon on a subtle surface. Filters live in the same shared catalog state as the tabs, so the drawer and the bottom bar always agree.
- **Settings** (stored on the device):
  - **Preferred stream language** (default English, or "Any language"). The player starts on a stream in that language, HD first, when one exists. Otherwise it falls back to any HD stream, then the first stream.
  - **Time format:** System, 12-hour or 24-hour. Applies to every kick-off time.
  - **Clear saved matches:** asks for confirmation first.
  - **App version.**
- **About:** version, where matches, streams and channels come from, and a note that saved matches and settings never leave the device.

## Live TV

- **Channel list:** loaded from [`channels.json`](https://github.com/svimran46/playlist.m3u/blob/main/channels.json) in the playlist repo. You can point the app at another file with `channelsUrl` in the remote config.
  - The file can be the JavaScript snippet it is today (`const channels = [...];`) or a plain JSON array. Each entry needs a `name` and an https `url`. `logo` is optional.
  - Entries without a name or an https stream are skipped, and so are repeated streams. Logo URLs have their repeated `&amp;` escaping undone, and non-https logos show the channel's initials instead.
  - The list is kept for 30 minutes. If a reload fails, the last good list stays on screen with a banner.
- **Groups:** **Football**, **Cricket** and **Others**. Every entry upstream has `"genre": "Sports"`, cartoon channels included, so genre can't be used to group them. Instead:
  - An entry's optional `category` field (`football`, `cricket` or `others`) decides its group.
  - Otherwise the name decides. "Cricket" or "Willow" means Cricket. "Football", "Fussball", "LaLiga", "Bundesliga", "Premier League" and similar mean Football. Everything else is Others.
  - A chip only appears for a group that has channels.
- **Search:** matches every word you type against the channel's name and group.
- **No LIVE badges:** the directory doesn't say whether a channel is on air, so the app doesn't claim it.
- **Playback:** a channel opens like a browser tab.
  - Its page is loaded directly, not inside a frame (many channel pages refuse to run in someone else's frame).
  - It may hand off to its real player on another https site.
  - No streamed.pk referrer is sent.
  - Pop-up windows and known ad hosts stay blocked, and fullscreen works as for matches.
  - The stream host is shown as the channel's source, with an **Open in browser** button as a fallback.
  - Other channels from the same group sit below the player and switch in place.

## Search, saved matches and Android TV

- **Search:** results update as you type. A match is shown when every word you type appears in its title (which is where upstream puts league names, e.g. "Premier League: …"), its team names, its sport, or a common nickname for that sport ("soccer", "F1", "UFC", "NBA", "IPL" and so on).
- **Saved matches:** tap the star on a card, or in the Watch top bar, to save a match. Saved IDs are kept on the device with DataStore, newest first and capped at 200. The **Saved** pill filters the catalog down to them.
- **Android TV:**
  - The app appears in the TV launcher (`LEANBACK_LAUNCHER`) with its own banner, and neither touch nor leanback support is required to install it.
  - Anything you can select gets a 2dp rose border and a slight scale-up when the D-pad focuses it (`Modifier.tvFocus`).
  - Inside the player, Up/Down always move focus out of the WebView, so the remote can't get stuck in the page.

## Remote control

Every installed app reads its config from the Worker (`GET /api/app/config`) when it starts, and again every 5 minutes while it is open. Change it from the admin panel's **Android app** page (`/admin#/app`): maintenance mode, forced updates, the announcement banner, the featured match, hidden sports, player options and data sources. Saves reach installed apps within a minute, with no new APK needed. The last good config is saved on each device, so settings like maintenance mode still apply when the user is offline. If the Worker can't be reached, apps keep the previous config.

The page edits the fields below, and the Worker serves them in this shape. [`remote-config.json`](remote-config.json) holds the same shape. Builds released before the Worker config (version code 2 and earlier) still read that file from GitHub, so edit it too if you need to reach them.

| Field | Effect |
|---|---|
| `announcement.message` | A banner at the top of the catalog. Leave it `""` to hide the banner. |
| `announcement.url` | Optional https link that opens when the banner is tapped. |
| `featuredMatchId` | Pins this match into the spotlight while it's in today's feed. Match IDs are in the Streamed API (`/api/matches/all-today`). |
| `hiddenCategories` | Sports to hide completely, e.g. `["fight", "motor-sports"]`. Uses the Streamed category keys. |
| `adBlocking` | `false` turns off the built-in ad-network blocking in the player. Pop-up windows stay blocked either way. |
| `extraBlockedHosts` | More hosts for the player to refuse, e.g. `["newads.example"]`. Subdomains are included. |
| `playerMode` | `"direct"` (the default; the embed page is the WebView itself, like a browser tab) or `"iframe"` (an unsandboxed iframe, like the website). |
| `minVersionCode` | Builds with a lower `versionCode` (in `app/build.gradle.kts`) show an "Update required" screen. |
| `updateUrl` | Where that screen's button sends the user (https only). |
| `maintenance.enabled` / `maintenance.message` | `true` replaces the whole app with a maintenance screen showing the message. |
| `streamProxyUrl` | The website's Worker, which the app asks for a match's streams when streamed.pk blocks the phone (it sits behind DDoS-Guard). Defaults to `https://sportzfylive.svimranmy.workers.dev`; `""` turns the fallback off. |
| `channelsUrl` | Where Live TV loads its channel list from (https only). Defaults to `channels.json` in the playlist repo. |

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
