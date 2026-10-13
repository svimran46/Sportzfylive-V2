// SportzfyLive Worker
// Streamed match/source sync, the Live TV channel catalog, site settings and
// the admin API. Legacy match-linked TV-channel storage is migrated away on
// the first sync; the Live TV catalog lives under its own key.

const SNAPSHOT_ALL_TODAY = "snapshot:all-today";
const SNAPSHOT_FEATURED = "snapshot:featured";
const MIGRATION_KEY = "channel_system_migrated";
const STREAMED_STREAM_ENDPOINT = "https://streamed.pk/api/stream";
const STREAMED_STREAMS_KEY_PREFIX = "streamed:streams:";
const STREAMED_FRESH_TTL_MS = 45 * 60 * 1000;    // serve-from-cache window per source
const STREAMED_STALE_TTL_SECONDS = 6 * 60 * 60;  // KV retention (stale ceiling) per source
const STREAMED_EMPTY_TTL_SECONDS = 15 * 60;      // empty results re-check quickly (lineups appear near go-live)
const STREAMED_SOURCE_TIMEOUT_MS = 8000;         // per-source fetch timeout
const MAX_SYNC_RESOLVE_OPS = 6;                  // default subrequest budget for source resolution per sync (stays within 1k/day KV free quota)
const SYNC_STATE_KEY = "sync:state";             // resolve cursor + recent sync runs, written once per sync
const SYNC_LOG_LIMIT = 30;                       // sync runs kept for the admin Fetch Logs page

const SETTINGS_KEY = "site_settings";            // every admin-editable setting, one record
const TV_CHANNELS_KEY = "tv_channels";           // Live TV catalog (channels + playlist sync state)
const PLAY_INDEX_KEY = "play_index";             // matchId -> streamId -> playback URL, for /api/play redirects
const ADMIN_CREDENTIAL_KEY = "admin_credential"; // salted hash of a password set from the admin panel

const DEFAULT_TV_SOURCE_URL = "https://raw.githubusercontent.com/svimran46/playlist.m3u/main/channels.json";
const TV_AUTO_SYNC_EVERY_MS = 6 * 60 * 60 * 1000; // cron re-pulls the playlist this often
const TV_AUTO_RETRY_MS = 60 * 60 * 1000;          // ...and waits this long after a failed pull
const TV_CHECK_TIMEOUT_MS = 8000;                 // per-URL health check timeout
const TV_CHECK_BATCH = 25;                        // URLs per health-check request (subrequest budget)
const MAX_PLAYLIST_ENTRIES = 2000;
const MAX_PLAYLIST_BYTES = 5 * 1024 * 1024;
const CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

// PBKDF2 cost for admin passwords. Kept low enough to fit the Workers free
// plan CPU budget per request; online guessing is already rate limited, and
// the hash is only readable by whoever owns the Cloudflare account.
const PASSWORD_ITERATIONS = 10000;
const PASSWORD_MIN_LENGTH = 10;

const jsonHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Admin-Token, X-Admin-Session",
  "X-Content-Type-Options": "nosniff"
};

function json(data, status = 200) {
  return Response.json(data, { status, headers: jsonHeaders });
}

// Public JSON that browsers may cache briefly (channel list).
function cachedJson(data, maxAgeSeconds) {
  return Response.json(data, {
    status: 200,
    headers: { ...jsonHeaders, "Cache-Control": "public, max-age=" + maxAgeSeconds }
  });
}

// Admin responses: anti-clickjacking framing protection and CORS.
// Uses standard jsonHeaders CORS so modern browser fetch/Origin requests
// are not blocked by null-origin / privacy shield restrictions.
const adminJsonHeaders = {
  ...jsonHeaders,
  "X-Frame-Options": "DENY",
  "Content-Security-Policy": "frame-ancestors 'none'"
};

function adminJson(data, status = 200) {
  return Response.json(data, { status, headers: adminJsonHeaders });
}

// Parse a JSON request body defensively. A malformed body (or non-JSON content)
// previously threw inside route handlers and surfaced as a Worker exception
// (HTTP 500 + stack details with observability enabled); this turns that into
// a clean 400 and keeps admin endpoints from being a cheap error oracle.
async function safeJson(request) {
  try {
    return await request.json();
  } catch (_) {
    return undefined;
  }
}

async function readArray(env, key) {
  const value = await env.SPORTZFY_DB.get(key, "json");
  return Array.isArray(value) ? value : [];
}

/*
 * Input validation
 *
 * Admin input is validated field by field; a bad value raises InputError,
 * which the fetch handler turns into a 400 carrying the message, so the admin
 * panel can show exactly what to fix.
 */
class InputError extends Error {}

function cleanText(value, max) {
  return String(value ?? "").replace(/[\u0000-\u001f\u007f]+/g, " ").trim().slice(0, max);
}

function safeHttpsUrl(value) {
  const text = String(value ?? "").trim();
  if (!text || /\s/.test(text)) return "";
  try {
    const url = new URL(text);
    return url.protocol === "https:" && url.hostname ? url.href : "";
  } catch (_) {
    return "";
  }
}

// An https URL, or (allowPath) a same-origin path like "/images/x.webp".
// Empty input is allowed. With lenient set, a bad value becomes "" instead of
// an error — used when normalizing records already in storage.
function optionalUrl(value, field, { allowPath = false, max = 2000, lenient = false } = {}) {
  const text = String(value ?? "").trim();
  if (!text) return "";
  const fail = (message) => {
    if (lenient) return "";
    throw new InputError(message);
  };
  if (text.length > max) return fail(field + " is too long");
  if (allowPath && text.startsWith("/") && !text.startsWith("//") && !/\s/.test(text)) return text;
  const url = safeHttpsUrl(text);
  if (!url) {
    return fail(field + (allowPath ? " must be an https URL or a path starting with /" : " must be an https URL"));
  }
  return url;
}

function clampNumber(value, min, max, fallback) {
  if (value === "" || value === null || value === undefined) return fallback;
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(max, Math.max(min, n));
}

function roundTo(value, places) {
  const factor = 10 ** places;
  return Math.round(value * factor) / factor;
}

function slugify(value) {
  return String(value ?? "")
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function escapeRegex(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// Undoes HTML escaping, including the repeated `&amp;amp;…` chains upstream
// channel logos carry.
function decodeEntities(value) {
  let current = String(value ?? "");
  for (let round = 0; current.includes("&amp;") && round < 32; round++) {
    current = current.replace(/&amp;/g, "&");
  }
  return current
    .replace(/&quot;/g, "\"")
    .replace(/&#0?39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

/*
 * Site settings
 *
 * Every admin-editable setting lives in one KV record, split into sections.
 * Each section has a cleaner that validates admin input (strict) and repairs
 * stored data (lenient), so readers always get a complete, safe shape.
 */
const SPORT_CATEGORIES = [
  { key: "FOOTBALL", label: "Football" },
  { key: "BASKETBALL", label: "Basketball" },
  { key: "AMERICAN FOOTBALL", label: "American Football" },
  { key: "FIGHT", label: "UFC & MMA" },
  { key: "TENNIS", label: "Tennis" },
  { key: "HOCKEY", label: "Hockey" },
  { key: "RUGBY", label: "Rugby" },
  { key: "MOTOR-SPORTS", label: "Motorsport" },
  { key: "CRICKET", label: "Cricket" },
  { key: "BASEBALL", label: "Baseball" },
  { key: "GOLF", label: "Golf" },
  { key: "OTHER", label: "Other" }
];

// The sports the Android app knows, keyed exactly as its SportCategory.apiKey.
// "Hide a sport" in the app dashboard stores these keys.
const APP_SPORTS = [
  { key: "football", label: "Football" },
  { key: "cricket", label: "Cricket" },
  { key: "basketball", label: "Basketball" },
  { key: "tennis", label: "Tennis" },
  { key: "motor-sports", label: "Motorsport" },
  { key: "american-football", label: "American Football" },
  { key: "hockey", label: "Hockey" },
  { key: "baseball", label: "Baseball" },
  { key: "fight", label: "Fight" },
  { key: "rugby", label: "Rugby" },
  { key: "golf", label: "Golf" },
  { key: "darts", label: "Darts" },
  { key: "billiards", label: "Billiards" },
  { key: "afl", label: "AFL" },
  { key: "other", label: "Other" }
];

const DEFAULT_SETTINGS = {
  ingest: { autoSync: true, resolveBudget: MAX_SYNC_RESOLVE_OPS },
  tvSource: { url: DEFAULT_TV_SOURCE_URL, timeoutSec: 120, concurrency: 6, autoSync: true },
  liveWindows: {
    defaultHours: 3,
    categories: {
      FOOTBALL: 3.5,
      BASKETBALL: 3.33,
      "AMERICAN FOOTBALL": 3.5,
      FIGHT: 5.5,
      TENNIS: 5,
      HOCKEY: 3,
      "MOTOR-SPORTS": 4,
      CRICKET: 8
    },
    types: [
      { name: "Cricket T20", keywords: ["t20", "t20i", "ipl", "big bash", "bbl"], hours: 5 },
      { name: "Cricket ODI", keywords: ["odi", "one day international", "one-day international"], hours: 9 },
      { name: "MotoGP", keywords: ["motogp"], hours: 2.5 }
    ]
  },
  notice: { enabled: false, text: "", link: "", newTab: false, bg: "", marquee: false, image: "", imageMobile: "" },
  banner: { enabled: false, image: "", link: "", alt: "" },
  analytics: { gaEnabled: false, gaId: "" },
  identity: { siteName: "", tagline: "", contactEmail: "" },
  app: {
    announcementMessage: "",
    announcementUrl: "",
    featuredMatchId: "",
    hiddenCategories: [],
    adBlocking: false,
    extraBlockedHosts: [],
    playerMode: "direct",
    minVersionCode: 0,
    updateUrl: "",
    maintenanceEnabled: false,
    maintenanceMessage: "",
    channelsUrl: "",
    streamProxyEnabled: true,
    streamProxyUrl: ""
  }
};

const truthy = (value) => value === true || value === "true" || value === 1 || value === "1" || value === "on";

function cleanKeywords(value) {
  const list = Array.isArray(value) ? value : String(value ?? "").split(",");
  return [...new Set(list.map(item => cleanText(item, 40).toLowerCase()).filter(Boolean))].slice(0, 20);
}

const SETTINGS_SECTIONS = {
  ingest(input) {
    return {
      autoSync: input.autoSync !== false && input.autoSync !== "false",
      resolveBudget: Math.round(clampNumber(input.resolveBudget, 1, 20, MAX_SYNC_RESOLVE_OPS))
    };
  },

  tvSource(input, options) {
    return {
      url: optionalUrl(input.url, "Playlist URL", options) || DEFAULT_TV_SOURCE_URL,
      timeoutSec: Math.round(clampNumber(input.timeoutSec, 5, 300, 120)),
      concurrency: Math.round(clampNumber(input.concurrency, 1, 10, 6)),
      autoSync: input.autoSync !== false && input.autoSync !== "false"
    };
  },

  liveWindows(input, options) {
    const hours = (value, label, fallback) => {
      if (value === "" || value === null || value === undefined) return fallback;
      const n = Number(value);
      if (!Number.isFinite(n) || n < 0.5 || n > 168) {
        if (options.lenient) return fallback;
        throw new InputError(label + " must be between 0.5 and 168 hours");
      }
      return roundTo(n, 2);
    };
    const defaultHours = hours(input.defaultHours, "Default window", 3);
    const categories = {};
    const source = input.categories && typeof input.categories === "object" ? input.categories : {};
    for (const { key, label } of SPORT_CATEGORIES) {
      const value = hours(source[key], label + " window", null);
      if (value !== null) categories[key] = value;
    }
    const types = (Array.isArray(input.types) ? input.types : [])
      .slice(0, 30)
      .map(type => ({
        name: cleanText(type?.name, 60),
        keywords: cleanKeywords(type?.keywords),
        hours: hours(type?.hours, cleanText(type?.name, 60) || "Match type", defaultHours)
      }))
      .filter(type => type.name && type.keywords.length);
    return { defaultHours, categories, types };
  },

  notice(input, options) {
    let bg = String(input.bg ?? "").trim().toLowerCase();
    if (bg && !/^#[0-9a-f]{6}$/.test(bg)) {
      if (!options.lenient) throw new InputError("Background colour must be a hex value like #1d4ed8");
      bg = "";
    }
    return {
      enabled: truthy(input.enabled),
      text: cleanText(input.text, 500),
      link: optionalUrl(input.link, "Link", { ...options, allowPath: true, max: 1000 }),
      newTab: truthy(input.newTab),
      bg,
      marquee: truthy(input.marquee),
      image: optionalUrl(input.image, "Image banner", { ...options, allowPath: true }),
      imageMobile: optionalUrl(input.imageMobile, "Mobile image banner", { ...options, allowPath: true })
    };
  },

  banner(input, options) {
    return {
      enabled: truthy(input.enabled),
      image: optionalUrl(input.image, "Banner image", { ...options, allowPath: true }),
      link: optionalUrl(input.link, "Banner link", { ...options, allowPath: true, max: 1000 }),
      alt: cleanText(input.alt, 200)
    };
  },

  analytics(input, options) {
    const gaEnabled = truthy(input.gaEnabled);
    let gaId = cleanText(input.gaId, 24).toUpperCase();
    if (gaId && !/^G-[A-Z0-9]{4,20}$/.test(gaId)) {
      if (!options.lenient) throw new InputError("GA4 measurement ID must look like G-XXXXXXX");
      gaId = "";
    }
    if (gaEnabled && !gaId && !options.lenient) throw new InputError("Enter a GA4 measurement ID to enable Google Analytics");
    return { gaEnabled: gaEnabled && Boolean(gaId), gaId };
  },

  identity(input, options) {
    let contactEmail = cleanText(input.contactEmail, 120);
    if (contactEmail && !/^[^\s@<>"']+@[^\s@<>"']+\.[^\s@<>"']+$/.test(contactEmail)) {
      if (!options.lenient) throw new InputError("Contact email is not a valid address");
      contactEmail = "";
    }
    return {
      siteName: cleanText(input.siteName, 60),
      tagline: cleanText(input.tagline, 160),
      contactEmail
    };
  },

  app(input, options) {
    const lenient = Boolean(options.lenient);
    const items = (value) => (Array.isArray(value) ? value : String(value ?? "").split(/[\s,]+/));
    const knownSports = new Set(APP_SPORTS.map(sport => sport.key));
    const hiddenCategories = [];
    for (const raw of items(input.hiddenCategories)) {
      const key = cleanText(raw, 40).toLowerCase().replace(/_/g, "-");
      if (!key) continue;
      if (!knownSports.has(key)) {
        if (lenient) continue;
        throw new InputError("Unknown sport: " + key);
      }
      if (!hiddenCategories.includes(key)) hiddenCategories.push(key);
    }
    const extraBlockedHosts = [];
    for (const raw of items(input.extraBlockedHosts)) {
      const host = cleanText(raw, 100).toLowerCase().replace(/^\*\./, "").replace(/\.$/, "");
      if (!host) continue;
      if (!/^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/.test(host)) {
        if (lenient) continue;
        throw new InputError(host + " is not a valid host name (use something like ads.example.com)");
      }
      if (!extraBlockedHosts.includes(host)) extraBlockedHosts.push(host);
    }
    const minVersionCode = Math.round(clampNumber(input.minVersionCode, 0, 100000000, 0));
    const updateUrl = optionalUrl(input.updateUrl, "Update link", options);
    if (minVersionCode > 0 && !updateUrl && !lenient) {
      throw new InputError("Add an update link, or the forced-update screen has nowhere to send people");
    }
    return {
      announcementMessage: cleanText(input.announcementMessage, 300),
      announcementUrl: optionalUrl(input.announcementUrl, "Announcement link", { ...options, max: 1000 }),
      featuredMatchId: cleanText(input.featuredMatchId, 120),
      hiddenCategories: hiddenCategories.slice(0, APP_SPORTS.length),
      adBlocking: truthy(input.adBlocking),
      extraBlockedHosts: extraBlockedHosts.slice(0, 200),
      playerMode: String(input.playerMode ?? "").trim().toLowerCase() === "iframe" ? "iframe" : "direct",
      minVersionCode,
      updateUrl,
      maintenanceEnabled: truthy(input.maintenanceEnabled),
      maintenanceMessage: cleanText(input.maintenanceMessage, 300),
      channelsUrl: optionalUrl(input.channelsUrl, "Channel list URL", options),
      streamProxyEnabled: input.streamProxyEnabled !== false && input.streamProxyEnabled !== "false",
      streamProxyUrl: optionalUrl(input.streamProxyUrl, "Stream proxy URL", options)
    };
  }
};

function normalizeSettings(stored) {
  const source = stored && typeof stored === "object" ? stored : {};
  const settings = {};
  for (const [name, clean] of Object.entries(SETTINGS_SECTIONS)) {
    const saved = source[name] && typeof source[name] === "object" ? source[name] : {};
    settings[name] = clean({ ...DEFAULT_SETTINGS[name], ...saved }, { lenient: true });
  }
  settings.updated = {};
  if (source.updated && typeof source.updated === "object") {
    for (const name of Object.keys(SETTINGS_SECTIONS)) {
      if (typeof source.updated[name] === "string") settings.updated[name] = source.updated[name];
    }
  }
  return settings;
}

async function readSettings(env) {
  let stored = null;
  try {
    stored = await env.SPORTZFY_DB.get(SETTINGS_KEY, "json");
  } catch (_) {}
  if (!stored || typeof stored !== "object") {
    // Before the first settings save, the notice and banner still live under
    // the keys the original admin panel wrote.
    const [notice, banner] = await Promise.all([
      env.SPORTZFY_DB.get("site_notice", "json").catch(() => null),
      env.SPORTZFY_DB.get("site_banner", "json").catch(() => null)
    ]);
    stored = { ...(notice ? { notice } : {}), ...(banner ? { banner } : {}) };
  }
  return normalizeSettings(stored);
}

async function saveSettingsSection(env, section, input) {
  const clean = SETTINGS_SECTIONS[section];
  if (!clean) throw new InputError("Unknown settings section");
  const settings = await readSettings(env);
  settings[section] = clean({ ...settings[section], ...(input || {}) }, { lenient: false });
  settings.updated[section] = new Date().toISOString();
  await env.SPORTZFY_DB.put(SETTINGS_KEY, JSON.stringify(settings));
  return settings;
}

// The parts of the settings the public pages read.
function publicSettings(settings) {
  const notice = settings.notice;
  return {
    notice: { ...notice, enabled: notice.enabled && Boolean(notice.text || notice.image) },
    banner: settings.banner,
    identity: settings.identity,
    analytics: { gaId: settings.analytics.gaEnabled ? settings.analytics.gaId : "" }
  };
}

// What the Android app reads: the shape of android/remote-config.json, built
// from the "app" settings section. Empty means off or the app's own default.
function appConfigPayload(settings) {
  const app = settings.app;
  const payload = {
    announcement: { message: app.announcementMessage, url: app.announcementUrl },
    featuredMatchId: app.featuredMatchId,
    hiddenCategories: app.hiddenCategories,
    adBlocking: app.adBlocking,
    extraBlockedHosts: app.extraBlockedHosts,
    playerMode: app.playerMode,
    minVersionCode: app.minVersionCode,
    updateUrl: app.updateUrl,
    maintenance: { enabled: app.maintenanceEnabled, message: app.maintenanceMessage },
    channelsUrl: app.channelsUrl
  };
  // Left out, the app uses the default Worker; "" switches the fallback off.
  if (!app.streamProxyEnabled) payload.streamProxyUrl = "";
  else if (app.streamProxyUrl) payload.streamProxyUrl = app.streamProxyUrl;
  return payload;
}

/*
 * Live windows
 *
 * How long after kickoff a match stays on the site. The first rule that
 * applies wins: per-match override -> match type keywords -> sport category
 * -> site default. Each match stores its resulting expiresAt, so public reads
 * can hide finished matches without reading the settings.
 */
function sportKey(category) {
  const value = String(category ?? "").trim().toUpperCase().replace(/_/g, " ");
  const aliases = {
    "AMERICAN-FOOTBALL": "AMERICAN FOOTBALL",
    "MOTORSPORTS": "MOTOR-SPORTS",
    "MOTORSPORT": "MOTOR-SPORTS",
    "MOTOR SPORTS": "MOTOR-SPORTS",
    "UFC": "FIGHT",
    "MMA": "FIGHT",
    "BOXING": "FIGHT",
    "ICE HOCKEY": "HOCKEY"
  };
  return aliases[value] || value || "OTHER";
}

function keywordHit(text, keyword) {
  const word = String(keyword ?? "").trim().toLowerCase();
  if (!word) return false;
  return new RegExp("(^|[^a-z0-9])" + escapeRegex(word) + "($|[^a-z0-9])").test(text);
}

function liveWindowFor(match, rules) {
  const override = Number(match?.liveWindowOverride);
  if (match?.liveWindowOverride !== null && match?.liveWindowOverride !== undefined &&
      match?.liveWindowOverride !== "" && Number.isFinite(override) && override > 0) {
    return { minutes: Math.round(override), source: "match override" };
  }
  const text = (String(match?.title ?? "") + " " + String(match?.competition ?? "")).toLowerCase();
  for (const type of rules.types || []) {
    if ((type.keywords || []).some(keyword => keywordHit(text, keyword))) {
      return { minutes: Math.round(type.hours * 60), source: type.name };
    }
  }
  const category = sportKey(match?.category);
  const hours = rules.categories ? rules.categories[category] : undefined;
  if (Number.isFinite(hours)) return { minutes: Math.round(hours * 60), source: "sport default" };
  return { minutes: Math.round((rules.defaultHours || 3) * 60), source: "site default" };
}

const EARLIEST_REAL_START = Date.UTC(2000, 0, 1);

function expiryFor(match, rules) {
  const start = Date.parse(match?.startTime || "");
  // No (or a placeholder epoch) kickoff time: 24/7 streams never expire.
  if (!Number.isFinite(start) || start < EARLIEST_REAL_START) return "";
  return new Date(start + liveWindowFor(match, rules).minutes * 60 * 1000).toISOString();
}

function withExpiry(matches, rules) {
  let changed = false;
  const result = matches.map(match => {
    if (!match || typeof match !== "object") return match;
    const expiresAt = expiryFor(match, rules);
    if ((match.expiresAt || "") === expiresAt) return match;
    changed = true;
    return { ...match, expiresAt };
  });
  return { matches: result, changed };
}

// A match the feed (or the admin) marks live is never expired.
function isExpired(match, now = Date.now()) {
  if (!match || String(match.status ?? "").toLowerCase() === "live") return false;
  const end = Date.parse(match.expiresAt || "");
  return Number.isFinite(end) && end <= now;
}

function isPubliclyVisible(match, now = Date.now()) {
  return Boolean(match && typeof match === "object" && match.active !== false && !isExpired(match, now));
}

// Status for manual matches left on "auto": derived from kickoff + window.
function publicStatus(match, now = Date.now()) {
  const status = String(match?.status ?? "").toLowerCase();
  if (status && status !== "auto") return status;
  const start = Date.parse(match?.startTime || "");
  if (!Number.isFinite(start) || now < start) return "scheduled";
  const end = Date.parse(match?.expiresAt || "");
  return !Number.isFinite(end) || now < end ? "live" : "finished";
}

/*
 * Sport detection for "Auto (from event text)". Most specific sports first;
 * football is last because its club words ("city", "united") are generic.
 */
const SPORT_KEYWORDS = [
  ["CRICKET", ["cricket", "t20", "t20i", "odi", "ipl", "bbl", "big bash", "psl", "test match", "ashes"]],
  ["AMERICAN FOOTBALL", ["nfl", "ncaaf", "american football", "super bowl", "college football"]],
  ["BASKETBALL", ["nba", "wnba", "basketball", "euroleague", "ncaab"]],
  ["BASEBALL", ["mlb", "baseball", "world series"]],
  ["HOCKEY", ["nhl", "hockey", "khl"]],
  ["FIGHT", ["ufc", "mma", "boxing", "bellator", "wwe", "pfl", "fight night", "contender series", "one championship"]],
  ["TENNIS", ["tennis", "atp", "wta", "wimbledon", "roland garros", "davis cup"]],
  ["RUGBY", ["rugby", "six nations", "nrl", "super rugby"]],
  ["MOTOR-SPORTS", ["f1", "formula 1", "formula one", "motogp", "nascar", "indycar", "grand prix", "rally", "superbike"]],
  ["GOLF", ["golf", "pga", "lpga", "ryder cup", "liv golf"]],
  ["FOOTBALL", [
    "football", "soccer", "premier league", "la liga", "laliga", "serie a", "bundesliga", "ligue 1",
    "champions league", "europa league", "conference league", "uefa", "fifa", "mls", "fa cup", "eredivisie",
    "copa", "fc", "cf", "afc", "sc", "united", "city", "rovers", "albion", "wanderers", "athletic", "real",
    "inter", "milan", "juventus", "barcelona", "madrid", "arsenal", "chelsea", "liverpool", "tottenham",
    "bayern", "psg", "sporting", "benfica", "porto", "celtic", "rangers", "slavia", "ajax", "dortmund"
  ]]
];

function detectSport(text) {
  const haystack = String(text ?? "").toLowerCase();
  for (const [category, keywords] of SPORT_KEYWORDS) {
    if (keywords.some(keyword => keywordHit(haystack, keyword))) return category;
  }
  return "OTHER";
}

/*
 * Snapshots
 *
 * The public match list is served from a pre-built snapshot of the active
 * matches. Writes are skipped when nothing changed, since KV's free tier
 * allows 1,000 writes a day and this runs after every sync and admin edit.
 */
function buildPlayIndex(matches) {
  const index = {};
  for (const match of matches) {
    if (!match || match.active === false) continue;
    for (const stream of Array.isArray(match.streams) ? match.streams : []) {
      const url = safeHttpsUrl(stream?.url);
      if (!url || !stream.id) continue;
      (index[String(match.id)] ||= {})[String(stream.id)] = url;
    }
  }
  return index;
}

async function putIfChanged(env, key, value) {
  let previous = null;
  try {
    previous = await env.SPORTZFY_DB.get(key, "text");
  } catch (_) {}
  if (previous === value) return false;
  await env.SPORTZFY_DB.put(key, value);
  return true;
}

async function rebuildSnapshots(env, matchesIn) {
  const matches = matchesIn || await readArray(env, "matches");
  const seen = new Set();
  const allToday = [];

  for (const match of matches) {
    if (!match || typeof match !== "object" || match.active === false) continue;
    const key = String(match.externalId || match.id || "").toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    allToday.push(match);
  }

  const featuredId = await env.SPORTZFY_DB.get("site_featured_match", "text");
  const featured = featuredId
    ? matches.find(match => String(match.id) === String(featuredId)) || null
    : null;

  const allTodayJson = JSON.stringify(allToday);
  const featuredJson = JSON.stringify({ id: featuredId || "", match: featured });

  try {
    await putIfChanged(env, SNAPSHOT_ALL_TODAY, allTodayJson);
  } catch (error) {
    console.error("Snapshot write failed:", String(error?.message || error));
    try { await env.SPORTZFY_DB.delete(SNAPSHOT_ALL_TODAY); } catch (_) {}
  }

  try {
    await putIfChanged(env, SNAPSHOT_FEATURED, featuredJson);
  } catch (error) {
    console.error("Featured snapshot write failed:", String(error?.message || error));
  }

  try {
    await putIfChanged(env, PLAY_INDEX_KEY, JSON.stringify(buildPlayIndex(matches)));
  } catch (error) {
    console.error("Play index write failed:", String(error?.message || error));
  }

  return { allTodayJson };
}

async function migrateLegacyChannelSystem(env) {
  if (await env.SPORTZFY_DB.get(MIGRATION_KEY, "text")) return;

  const matches = await readArray(env, "matches");
  const cleaned = matches.map(match => {
    const copy = { ...(match || {}) };
    delete copy.channelIds;
    delete copy.channels;
    delete copy.autoLinkStatus;
    delete copy.autoLinkScore;
    delete copy.autoLinkedAt;
    return copy;
  });

  await env.SPORTZFY_DB.put("matches", JSON.stringify(cleaned));
  await env.SPORTZFY_DB.delete("channels");
  await rebuildSnapshots(env, cleaned);
  await env.SPORTZFY_DB.put(MIGRATION_KEY, new Date().toISOString());
}

async function generateHmacSession(expiresAt, secret) {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(String(secret).trim()),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const data = enc.encode("admin_session:" + expiresAt);
  const sig = await crypto.subtle.sign("HMAC", key, data);
  const sigHex = Array.from(new Uint8Array(sig)).map(b => b.toString(16).padStart(2, "0")).join("");
  return expiresAt + "." + sigHex;
}

async function verifyHmacSession(sessionStr, secret) {
  if (!sessionStr || typeof sessionStr !== "string" || !secret) return false;
  const dotIndex = sessionStr.indexOf(".");
  if (dotIndex === -1) return false;
  const expiresAtStr = sessionStr.slice(0, dotIndex);
  const sigHex = sessionStr.slice(dotIndex + 1);
  const expiresAt = Number(expiresAtStr);
  if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) return false;
  if (!/^[0-9a-f]{64}$/i.test(sigHex)) return false;

  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(String(secret).trim()),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["verify"]
  );
  const sigBytes = new Uint8Array(sigHex.match(/.{2}/g).map(byte => parseInt(byte, 16)));
  const data = enc.encode("admin_session:" + expiresAt);
  return await crypto.subtle.verify("HMAC", key, sigBytes, data);
}

async function requireAdmin(request, env) {
  const session = request.headers.get("X-Admin-Session");
  if (!session) return false;

  // 1. Stateless HMAC session verification (zero KV operations, immune to quota exhaustion)
  try {
    if (env.ADMIN_TOKEN && await verifyHmacSession(session, env.ADMIN_TOKEN)) {
      return true;
    }
  } catch (_) {}

  // 2. Fallback to KV session check for existing / legacy sessions
  try {
    const record = await env.SPORTZFY_DB.get("admin_session:" + session, "json");
    if (record && record.expiresAt > Date.now()) return true;
  } catch (_) {}

  return false;
}

//
// Admin login rate limiting
//
// /api/admin/login compares a caller-supplied header against ADMIN_TOKEN. That
// endpoint used to be unthrottled, so a public Worker was an open oracle for
// offline guessing of the admin token. Failures are now charged per client IP
// with a KV sliding window. Only failures count, so the real admin is never
// locked out; once the window fills, guessing stops until it drains.
const LOGIN_WINDOW_SECONDS = 15 * 60; // 15-minute sliding window
const LOGIN_MAX_FAILURES = 10;        // 10 bad tokens per window per IP
const LOGIN_BLOCK_SECONDS = 30 * 60;  // hardened block once the window fills

function clientIp(request) {
  return (
    request.headers.get("cf-connecting-ip") ||
    request.headers.get("x-forwarded-for")?.split(",")[0].trim() ||
    "unknown"
  );
}

function loginFailKey(ip) {
  // Encode the IP to keep the KV key on a safe alphabet.
  return "login_fail:" + encodeURIComponent(ip);
}

async function countLoginFailure(env, request, ctx) {
  const key = loginFailKey(clientIp(request));
  const now = Date.now();
  let record = null;
  try {
    record = await env.SPORTZFY_DB.get(key, "json");
  } catch (_) {}
  if (!record || !Array.isArray(record.failures)) record = { failures: [] };

  // Sliding window: drop failures older than LOGIN_WINDOW_SECONDS.
  const cutoff = now - LOGIN_WINDOW_SECONDS * 1000;
  record.failures = record.failures.filter(ts => Number(ts) > cutoff);
  record.failures.push(now);

  try {
    await env.SPORTZFY_DB.put(
      key,
      JSON.stringify(record),
      { expirationTtl: LOGIN_BLOCK_SECONDS }
    );
  } catch (_) {}

  return record.failures.length;
}

async function loginThrottled(env, request) {
  try {
    const record = await env.SPORTZFY_DB.get(loginFailKey(clientIp(request)), "json");
    if (!record || !Array.isArray(record.failures)) return false;
    const cutoff = Date.now() - LOGIN_WINDOW_SECONDS * 1000;
    const recent = record.failures.filter(ts => Number(ts) > cutoff);
    return recent.length >= LOGIN_MAX_FAILURES;
  } catch (_) {
    return false;
  }
}

// Length-independent comparison so a response cannot leak how much of the
// token prefix matched. PKCS#7-style padding: normalize both sides to 64-byte
// digests before comparing bytes.
function timingSafeEqualStr(a, b) {
  const enc = new TextEncoder();
  const da = crypto.subtle.digest("SHA-256", enc.encode(String(a ?? "")));
  const db = crypto.subtle.digest("SHA-256", enc.encode(String(b ?? "")));
  return Promise.all([da, db]).then(([ba, bb]) => {
    const va = new Uint8Array(ba);
    const vb = new Uint8Array(bb);
    let diff = 0;
    for (let i = 0; i < va.length; i++) diff |= va[i] ^ vb[i];
    return diff === 0;
  });
}

/*
 * Admin password
 *
 * ADMIN_TOKEN (a Worker secret) is the bootstrap credential and the key that
 * signs sessions. The admin panel can set a different login password; its
 * salted PBKDF2 hash is stored in KV and replaces ADMIN_TOKEN for logins
 * until it is reset. Locked out? Delete the admin_credential KV key.
 */
const toHex = (bytes) => Array.from(new Uint8Array(bytes)).map(b => b.toString(16).padStart(2, "0")).join("");

async function hashPassword(password, saltHex, iterations) {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", enc.encode(password), "PBKDF2", false, ["deriveBits"]);
  const salt = new Uint8Array(saltHex.match(/.{2}/g).map(byte => parseInt(byte, 16)));
  const bits = await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt, iterations }, key, 256);
  return toHex(bits);
}

async function readAdminCredential(env) {
  try {
    const record = await env.SPORTZFY_DB.get(ADMIN_CREDENTIAL_KEY, "json");
    if (record && /^[0-9a-f]{64}$/.test(record.hash) && /^[0-9a-f]{32}$/.test(record.salt)) return record;
  } catch (_) {}
  return null;
}

async function verifyAdminSecret(env, supplied) {
  const secret = String(supplied ?? "").trim();
  if (!secret) return false;
  const credential = await readAdminCredential(env);
  if (credential) {
    const iterations = Number(credential.iterations) || PASSWORD_ITERATIONS;
    return timingSafeEqualStr(await hashPassword(secret, credential.salt, iterations), credential.hash);
  }
  if (!env.ADMIN_TOKEN) return false;
  return timingSafeEqualStr(secret, String(env.ADMIN_TOKEN).trim());
}

async function createAdminSession(request, env, ctx) {
  const supplied = request.headers.get("X-Admin-Token");

  // Charge every failed attempt (missing token, no secret configured, wrong
  // token) before any comparison so throttling cannot be bypassed. Sessions
  // are signed with ADMIN_TOKEN, so it must exist even when a password set
  // from the admin panel is what the admin types.
  if (!env.ADMIN_TOKEN || !supplied) {
    await countLoginFailure(env, request, ctx);
    return null;
  }
  if (!await verifyAdminSecret(env, supplied)) {
    await countLoginFailure(env, request, ctx);
    return null;
  }

  const expiresAt = Date.now() + 24 * 60 * 60 * 1000;
  const session = await generateHmacSession(expiresAt, env.ADMIN_TOKEN);

  // Best-effort KV write; if KV write quota is exceeded on free tier, session remains 100% valid via HMAC.
  try {
    await env.SPORTZFY_DB.put(
      "admin_session:" + session,
      JSON.stringify({ expiresAt }),
      { expirationTtl: 24 * 60 * 60 }
    );
  } catch (err) {
    console.warn("KV admin session write skipped (stateless HMAC active):", String(err?.message || err));
  }

  return { session, expiresAt };
}

async function fetchStreamedMatches(path = "all-today") {
  const response = await fetch("https://streamed.pk/api/matches/" + path, {
    headers: { "Accept": "application/json" }
  });
  if (!response.ok) throw new Error("Streamed API returned " + response.status);

  const data = await response.json();
  return Array.isArray(data) ? data : [];
}

function toStreamedMatch(match, liveIds = new Set()) {
  const externalId = String(match?.id || "");
  return {
    id: "streamed-" + (externalId || crypto.randomUUID()),
    externalId,
    title: String(match?.title || ""),
    category: String(match?.category || "other").toUpperCase(),
    startTime: match?.date ? new Date(Number(match.date)).toISOString() : "",
    status: liveIds.has(externalId) ? "live" : "scheduled",
    poster: match?.poster
      ? (String(match.poster).startsWith("http")
        ? String(match.poster)
        : "https://streamed.pk" + String(match.poster))
      : "",
    description: "",
    teams: match?.teams || null,
    popular: Boolean(match?.popular),
    sources: Array.isArray(match?.sources)
      ? match.sources.map(source => ({
          source: String(source?.source || ""),
          id: String(source?.id || "")
        })).filter(source => source.source && source.id)
      : [],
    source: "streamed"
  };
}

/*
 * Streamed source resolver
 *
 * Resolves per-source stream metadata (language / HD / stream number) from the
 * Streamed /api/stream endpoint with an SWR cache keyed per source group.
 * Playback URLs (embedUrl) are stripped before anything is cached or returned:
 * only public-safe metadata is ever persisted or emitted.
 */

function streamedCacheKey(source, id) {
  return STREAMED_STREAMS_KEY_PREFIX + source + ":" + id;
}

async function fetchWithTimeout(url, options, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

/*
 * Same-origin image proxy
 *
 * Posters and team crests are hotlinked from streamed.pk, a DDoS-Guard fronted
 * host. Browsers, extensions and filtered networks that block those third-party
 * requests leave every card with an empty frame, so the public site retries a
 * failed image through /api/images?url=... and the Worker re-serves it from the
 * site's own origin, cached at the edge. Only https images on streamed.pk are
 * allowed, so the route cannot be turned into an open proxy.
 */
const IMAGE_PROXY_HOST = "streamed.pk";
const IMAGE_CACHE_SECONDS = 24 * 60 * 60;

function allowedImageUrl(value) {
  try {
    const url = new URL(String(value ?? ""));
    if (url.protocol !== "https:") return null;
    const host = url.hostname.toLowerCase();
    if (host !== IMAGE_PROXY_HOST && !host.endsWith("." + IMAGE_PROXY_HOST)) return null;
    return url;
  } catch (_) {
    return null;
  }
}

async function fetchProxiedImage(target) {
  const upstream = await fetchWithTimeout(
    target.href,
    { headers: { "Accept": "image/*" }, redirect: "follow" },
    STREAMED_SOURCE_TIMEOUT_MS
  );
  if (!upstream.ok || !upstream.body) return null;

  return new Response(upstream.body, {
    status: 200,
    headers: {
      "Content-Type": upstream.headers.get("Content-Type") || "image/webp",
      "Cache-Control": "public, max-age=" + IMAGE_CACHE_SECONDS + ", immutable",
      "Access-Control-Allow-Origin": "*"
    }
  });
}

async function serveProxiedImage(request) {
  const target = allowedImageUrl(new URL(request.url).searchParams.get("url"));
  if (!target) return json({ error: "Image url is not allowed" }, 400);

  // Edge cache: each poster/crest is fetched from streamed.pk only once.
  const cache = typeof caches === "object" && caches ? caches.default : null;
  const cacheKey = cache
    ? new Request("https://sportzfylive-images.internal/" + encodeURIComponent(target.href), { method: "GET" })
    : null;

  if (cache) {
    const hit = await cache.match(cacheKey);
    if (hit) return hit;
  }

  let response = null;
  try {
    response = await fetchProxiedImage(target);
  } catch (_) {
    response = null;
  }
  if (!response) return json({ error: "Image unavailable" }, 502);

  if (cache && typeof response.clone === "function") {
    try { await cache.put(cacheKey, response.clone()); } catch (_) {}
  }

  return response;
}

// Extract public-safe stream metadata from any plausible Streamed payload
// shape and drop every other field — playback URLs must never survive.
function sanitizeStreamedStreams(data, sourceName) {
  let entries;
  if (Array.isArray(data)) entries = data;
  else if (data && typeof data === "object" && Array.isArray(data.streams)) entries = data.streams;
  else if (data && typeof data === "object" && Array.isArray(data.data)) entries = data.data;
  else entries = [];

  const streams = [];
  const seen = new Set();

  entries.forEach((entry, index) => {
    if (!entry || typeof entry !== "object") {
      // Primitive payloads still count as a stream, with no metadata.
      streams.push({ streamNo: index + 1, language: "", hd: false, source: sourceName });
      return;
    }
    const qualityText = String(entry.quality || entry.label || "");
    const language = String(entry.language ?? entry.lang ?? "").trim();
    const hd = entry.hd === true || entry.hd === "true" || entry.isHd === true ||
      /(\bhd\b|fhd|4k)/i.test(qualityText);
    const streamNo = Number(entry.streamNo ?? entry.stream_no ?? index + 1) || index + 1;
    const key = streamNo + "|" + language + "|" + (hd ? "1" : "0");
    if (seen.has(key)) return;
    seen.add(key);
    streams.push({ streamNo, language, hd, source: sourceName });
  });

  return streams;
}

// Fetch one source group's streams with a hard timeout. Returns
// { ok: true, streams } or { ok: false, error }. Never throws.
async function fetchStreamedSourceStreams(source, id) {
  // Streamed exposes per-source streams as a path, not query params:
  // /api/stream/{source}/{id} (query-param form returns 404 for everything).
  const endpoint = STREAMED_STREAM_ENDPOINT +
    "/" + encodeURIComponent(source) +
    "/" + encodeURIComponent(id);

  try {
    const response = await fetchWithTimeout(
      endpoint,
      { headers: { "Accept": "application/json" } },
      STREAMED_SOURCE_TIMEOUT_MS
    );

    // Source group exists but has no streams — cache the empty result.
    if (response.status === 404) return { ok: true, streams: [] };
    if (response.status === 429) return { ok: false, error: "streamed-429" };
    if (!response.ok) return { ok: false, error: "streamed-503" };

    let data;
    try {
      data = await response.json();
    } catch (_) {
      return { ok: false, error: "fetch-failed" };
    }
    return { ok: true, streams: sanitizeStreamedStreams(data, source) };
  } catch (error) {
    return { ok: false, error: error?.name === "AbortError" ? "timeout" : "fetch-failed" };
  }
}

// Cache read. Returns { streams, resolvedAt, fresh } or null.
async function readStreamedSourceCache(env, source, id) {
  try {
    const raw = await env.SPORTZFY_DB.get(streamedCacheKey(source, id), "json");
    if (!raw || typeof raw !== "object" || !Array.isArray(raw.streams)) return null;
    const resolvedAt = Number(raw.resolvedAt) || 0;
    return {
      streams: raw.streams,
      resolvedAt,
      fresh: Date.now() - resolvedAt < STREAMED_FRESH_TTL_MS
    };
  } catch (_) {
    return null;
  }
}

// Fetch + sanitize + write-through on success only. A failing or rate-limited
// source never overwrites an existing cache entry.
async function refreshStreamedSource(env, source, id) {
  const fetched = await fetchStreamedSourceStreams(source, id);
  if (!fetched.ok) return { ok: false, error: fetched.error };

  const record = { streams: fetched.streams, resolvedAt: Date.now() };
  try {
    await env.SPORTZFY_DB.put(
      streamedCacheKey(source, id),
      JSON.stringify(record),
      // Empty results expire fast so newly published lineups are picked up
      // without waiting out the full stale window.
      { expirationTtl: fetched.streams.length ? STREAMED_STALE_TTL_SECONDS : STREAMED_EMPTY_TTL_SECONDS }
    );
  } catch (_) {}
  return { ok: true, record };
}

// Sync-path resolver: cache-first, inline refresh of stale entries, cold fetch
// on miss. The optional budget (a mutable { remaining } counter) keeps each
// sync invocation inside Worker subrequest limits — groups that would exceed
// it are reported as deferred and retried on later syncs.
async function resolveStreamedSource(env, source, id, budget = null) {
  if (budget) {
    if (budget.remaining < 1) return { streams: [], resolvedAt: null, deferred: true };
    budget.remaining -= 1;
  }

  const cached = await readStreamedSourceCache(env, source, id);

  if (cached) {
    if (cached.fresh) {
      return { streams: cached.streams, resolvedAt: cached.resolvedAt, stale: false };
    }
    if (budget && budget.remaining < 2) {
      return { streams: cached.streams, resolvedAt: cached.resolvedAt, stale: true };
    }
    const refreshed = await refreshStreamedSource(env, source, id);
    return refreshed.ok
      ? { streams: refreshed.record.streams, resolvedAt: refreshed.record.resolvedAt, stale: false }
      : { streams: cached.streams, resolvedAt: cached.resolvedAt, stale: true, error: refreshed.error };
  }

  if (budget && budget.remaining < 2) return { streams: [], resolvedAt: null, deferred: true };
  const refreshed = await refreshStreamedSource(env, source, id);
  if (refreshed.ok) {
    return { streams: refreshed.record.streams, resolvedAt: refreshed.record.resolvedAt, stale: false };
  }
  return { streams: [], resolvedAt: null, error: refreshed.error };
}

function emptyStreamedSummary(groups = []) {
  return { sourceCount: groups.length, streamCount: 0, sources: [], streams: [] };
}

// Resolve every source group of a streamed match into a public-safe summary.
// Uses all-settled semantics per group: one failing source never breaks the
// match. Groups the budget could not cover keep their previously resolved
// data, if any.
async function buildStreamedSummary(env, match, previousSummary, budget) {
  const groups = Array.isArray(match.sources) ? match.sources : [];
  if (!groups.length) return emptyStreamedSummary();

  const previousGroups = new Map();
  for (const group of (previousSummary && Array.isArray(previousSummary.sources) ? previousSummary.sources : [])) {
    if (group && group.source && group.id) {
      previousGroups.set(group.source + ":" + group.id, group);
    }
  }

  const resolvedGroups = [];
  for (const group of groups) {
    if (!group || !group.source || !group.id) continue;
    const prior = previousGroups.get(group.source + ":" + group.id);
    const result = await resolveStreamedSource(env, group.source, group.id, budget);
    if (result.deferred) {
      if (prior) resolvedGroups.push(prior);
      continue;
    }
    resolvedGroups.push({
      source: group.source,
      id: group.id,
      streamCount: result.streams.length,
      streams: result.streams,
      ...(result.stale ? { stale: true } : {}),
      ...(result.error ? { error: result.error } : {})
    });
  }

  const streams = [];
  const seen = new Set();
  for (const group of resolvedGroups) {
    for (const stream of (group.streams || [])) {
      const key = stream.streamNo + "|" + stream.language + "|" + (stream.hd ? "1" : "0");
      if (seen.has(key)) continue;
      seen.add(key);
      streams.push(stream);
    }
  }

  return {
    sourceCount: groups.length,
    streamCount: streams.length,
    sources: resolvedGroups,
    streams
  };
}

function publicStreamMeta(stream) {
  return {
    streamNo: Number(stream?.streamNo) || 0,
    language: String(stream?.language ?? ""),
    hd: Boolean(stream?.hd),
    source: String(stream?.source ?? "")
  };
}

// Embed pages play in an iframe; .m3u8 manifests need a video player.
function streamKind(url) {
  try {
    return /\.m3u8$/i.test(new URL(url).pathname) ? "hls" : "embed";
  } catch (_) {
    return "embed";
  }
}

function publicTeams(teams) {
  if (!teams || typeof teams !== "object") return null;
  const side = (value) => {
    if (value && typeof value === "object") return value;
    const name = String(value ?? "").trim();
    return name ? { name } : null;
  };
  const home = side(teams.home);
  const away = side(teams.away);
  return home || away ? { home: home || { name: "" }, away: away || { name: "" } } : null;
}

/*
 * Public response shaping. Builds a fresh object from a whitelist, so stored
 * admin fields (match URL, live window, manual stream URLs) and any unknown
 * fields — including playback URLs — can never leak into a public response.
 * Manual streams are listed by id only; /api/play redirects to them.
 */
function enrichMatchForPublic(match, now = Date.now()) {
  if (!match || typeof match !== "object") return match;

  const groups = Array.isArray(match.sources)
    ? match.sources
        .filter(group => group && group.source && group.id)
        .map(group => ({ source: String(group.source), id: String(group.id) }))
    : [];

  const summary = match.streamedSummary && typeof match.streamedSummary === "object"
    ? match.streamedSummary
    : emptyStreamedSummary(groups);
  const summarySources = Array.isArray(summary.sources) ? summary.sources : [];

  const enriched = {
    id: String(match.id ?? ""),
    externalId: String(match.externalId ?? ""),
    title: String(match.title ?? ""),
    category: String(match.category ?? ""),
    startTime: String(match.startTime ?? ""),
    status: publicStatus(match, now),
    poster: String(match.image || match.poster || ""),
    description: String(match.description ?? ""),
    teams: publicTeams(match.teams),
    competition: String(match.competition ?? ""),
    popular: Boolean(match.popular),
    featured: Boolean(match.featured),
    source: String(match.source ?? ""),
    sources: groups,
    streamed: {
      sourceCount: Number(summary.sourceCount) || groups.length,
      streamCount: Number(summary.streamCount) || 0,
      sources: summarySources.map(group => ({
        source: String(group?.source ?? ""),
        id: String(group?.id ?? ""),
        streamCount: Number(group?.streamCount) || 0,
        streams: (Array.isArray(group?.streams) ? group.streams : []).map(publicStreamMeta),
        ...(group?.stale ? { stale: true } : {}),
        ...(group?.error ? { error: String(group.error) } : {})
      })),
      streams: (Array.isArray(summary.streams) ? summary.streams : []).map(publicStreamMeta)
    }
  };

  if (Array.isArray(match.streams) && match.streams.length) {
    enriched.manual = {
      streamCount: match.streams.length,
      streams: match.streams
        .filter(stream => stream && stream.id && safeHttpsUrl(stream.url))
        .map(stream => ({
          id: String(stream.id),
          name: String(stream.name ?? ""),
          language: String(stream.language ?? ""),
          quality: String(stream.quality ?? ""),
          kind: streamKind(stream.url),
          sandbox: Boolean(stream.sandbox)
        }))
    };
  }

  return enriched;
}

/*
 * Sync state: the rotating resolve cursor plus the most recent sync runs
 * (newest first) for the admin Fetch Logs page. Both live in one record that
 * is written once per sync.
 */
async function readSyncState(env) {
  try {
    const state = await env.SPORTZFY_DB.get(SYNC_STATE_KEY, "json");
    if (state && typeof state === "object") {
      return {
        cursor: Number(state.cursor) || 0,
        runs: Array.isArray(state.runs) ? state.runs.slice(0, SYNC_LOG_LIMIT) : []
      };
    }
  } catch (_) {}
  return { cursor: 0, runs: [] };
}

async function writeSyncState(env, state) {
  try {
    await env.SPORTZFY_DB.put(SYNC_STATE_KEY, JSON.stringify({
      cursor: state.cursor,
      runs: state.runs.slice(0, SYNC_LOG_LIMIT)
    }));
  } catch (error) {
    console.warn("Sync state write skipped:", String(error?.message || error));
  }
}

async function syncStreamedMatches(env, options = {}) {
  const trigger = options.trigger || "cron";
  const startedAt = Date.now();
  const state = await readSyncState(env);
  try {
    const result = await runStreamedSync(env, state);
    state.runs.unshift({
      at: new Date(startedAt).toISOString(),
      trigger,
      ok: true,
      ms: Date.now() - startedAt,
      streamed: result.streamedCount,
      total: result.totalMatches,
      removed: result.removed,
      expired: result.expired,
      resolved: result.resolvedCount
    });
    await writeSyncState(env, state);
    return result;
  } catch (error) {
    state.runs.unshift({
      at: new Date(startedAt).toISOString(),
      trigger,
      ok: false,
      ms: Date.now() - startedAt,
      error: String(error?.message || error).slice(0, 300)
    });
    await writeSyncState(env, state);
    throw error;
  }
}

async function runStreamedSync(env, state) {
  await migrateLegacyChannelSystem(env);

  const settings = await readSettings(env);
  const existing = await readArray(env, "matches");
  const streamedRaw = await fetchStreamedMatches("all-today");

  let liveIds = new Set();
  try {
    const liveRaw = await fetchStreamedMatches("live");
    liveIds = new Set(liveRaw.map(match => String(match?.id || "")).filter(Boolean));
  } catch (_) {}

  const existingStreamed = new Map(
    existing
      .filter(match => match.source === "streamed" && match.externalId)
      .map(match => [String(match.externalId), match])
  );

  const manual = existing.filter(match => match.source !== "streamed");
  const now = new Date().toISOString();

  // The feed owns title, category, kickoff, status and poster; everything the
  // admin edits (teams, streams, flags, featured image, window) is carried over.
  const fresh = streamedRaw.map(raw => {
    const previous = existingStreamed.get(String(raw.id || ""));
    const base = toStreamedMatch(raw, liveIds);

    return {
      ...base,
      id: previous?.id || base.id,
      createdAt: previous?.createdAt || now,
      updatedAt: now,
      competition: previous?.competition || "",
      home: previous?.home || "",
      away: previous?.away || "",
      teams: previous?.teams || base.teams || null,
      description: previous?.description || "",
      liveWindowOverride: previous?.liveWindowOverride ?? null,
      url: previous?.url || "",
      image: previous?.image || "",
      featured: Boolean(previous?.featured),
      active: previous ? previous.active !== false : true,
      streams: Array.isArray(previous?.streams) ? previous.streams : []
    };
  });

  const freshIds = new Set(fresh.map(match => String(match.externalId || "")));
  const removed = existing.filter(match =>
    match.source === "streamed" &&
    !freshIds.has(String(match.externalId || ""))
  );

  // Resolve Streamed source groups through the SWR cache. The resolve budget
  // keeps each sync invocation inside Worker subrequest limits (50 on the free
  // plan); groups it cannot cover are deferred and retried on later syncs via
  // a rotating cursor so no match is starved.
  const budget = { remaining: settings.ingest.resolveBudget };
  const rotateBy = fresh.length ? state.cursor % fresh.length : 0;

  for (let index = 0; index < fresh.length; index++) {
    const match = fresh[(index + rotateBy) % fresh.length];
    const previous = existingStreamed.get(String(match.externalId || ""));
    match.streamedSummary = await buildStreamedSummary(
      env,
      match,
      previous?.streamedSummary,
      budget
    );
  }

  if (fresh.length) state.cursor = (rotateBy + 1) % fresh.length;
  const resolveCount = fresh.reduce(
    (total, match) => total + (Number(match.streamedSummary?.streamCount) || 0),
    0
  );

  // Matches past their live window are cleaned up on every sync.
  const timed = withExpiry([...manual, ...fresh], settings.liveWindows).matches;
  const nowMs = Date.now();
  const finalMatches = timed.filter(match => !isExpired(match, nowMs));
  const expired = timed.length - finalMatches.length;

  try {
    await env.SPORTZFY_DB.put("matches", JSON.stringify(finalMatches));
    await rebuildSnapshots(env, finalMatches);
  } catch (err) {
    console.warn("Matches sync KV write skipped (quota or transient error):", String(err?.message || err));
  }

  return {
    success: true,
    streamedCount: fresh.length,
    totalMatches: finalMatches.length,
    removed: removed.length,
    expired,
    resolvedCount: resolveCount
  };
}

async function runCleanup(env) {
  const settings = await readSettings(env);
  const matches = await readArray(env, "matches");
  const timed = withExpiry(matches, settings.liveWindows);
  const now = Date.now();
  const kept = timed.matches.filter(match => !isExpired(match, now));
  const removed = matches.length - kept.length;
  if (removed || timed.changed) {
    await env.SPORTZFY_DB.put("matches", JSON.stringify(kept));
    await rebuildSnapshots(env, kept);
  }
  return { success: true, removed, remaining: kept.length };
}

/*
 * Admin match editing
 */
const MATCH_STATUSES = ["auto", "scheduled", "live", "finished"];
const STREAM_QUALITIES = ["HD", "SD", "FHD", "4K"];

function cleanStreams(list) {
  if (!Array.isArray(list)) return [];
  return list.slice(0, 60).map((stream, index) => {
    const name = cleanText(stream?.name, 120) || "Stream " + (index + 1);
    const rawUrl = String(stream?.url ?? "").trim();
    const url = rawUrl ? safeHttpsUrl(rawUrl) : "";
    if (rawUrl && !url) throw new InputError("Stream URL for \"" + name + "\" must be an https link");
    const quality = String(stream?.quality || "HD").toUpperCase();
    return {
      id: cleanText(stream?.id, 64) || crypto.randomUUID(),
      name,
      language: cleanText(stream?.language, 40) || "English",
      league: cleanText(stream?.league, 80),
      quality: STREAM_QUALITIES.includes(quality) ? quality : "HD",
      url,
      channelId: cleanText(stream?.channelId, 120),
      sandbox: sandboxFlag(stream?.sandbox)
    };
  });
}

function cleanStartTime(value) {
  if (value === "" || value === null || value === undefined) return "";
  const time = Date.parse(String(value));
  if (!Number.isFinite(time)) throw new InputError("Date and time are not valid");
  return new Date(time).toISOString();
}

function mergeTeams(current, home, away) {
  const side = (value, name) => {
    const base = value && typeof value === "object" ? value : {};
    return name ? { ...base, name } : (Object.keys(base).length ? base : { name: "" });
  };
  const source = current && typeof current === "object" ? current : {};
  const teams = { home: side(source.home, home), away: side(source.away, away) };
  return teams.home.name || teams.away.name || teams.home.badge || teams.away.badge ? teams : null;
}

// Applies the admin-editable fields present in body over current.
function editableMatchFields(body, current = {}) {
  const has = (key) => Object.prototype.hasOwnProperty.call(body, key);
  const home = has("home") ? cleanText(body.home, 120) : String(current.home ?? "");
  const away = has("away") ? cleanText(body.away, 120) : String(current.away ?? "");
  const title = (has("title") ? cleanText(body.title, 200) : String(current.title ?? "")) ||
    (home && away ? home + " vs " + away : "Untitled match");
  const competition = has("competition") ? cleanText(body.competition, 120) : String(current.competition ?? "");

  let category = has("category") ? cleanText(body.category, 40).toUpperCase() : String(current.category ?? "");
  if (!category || category === "AUTO") category = detectSport(title + " " + competition);

  let status = String(current.status || "auto");
  if (has("status")) {
    status = String(body.status ?? "").toLowerCase() || "auto";
    if (!MATCH_STATUSES.includes(status)) throw new InputError("Status must be auto, scheduled, live or finished");
  }

  let liveWindowOverride = current.liveWindowOverride ?? null;
  if (has("liveWindowOverride")) {
    const raw = body.liveWindowOverride;
    if (raw === "" || raw === null || raw === undefined) {
      liveWindowOverride = null;
    } else {
      const minutes = Number(raw);
      if (!Number.isFinite(minutes) || minutes < 1 || minutes > 10080) {
        throw new InputError("Live window override must be between 1 and 10080 minutes");
      }
      liveWindowOverride = Math.round(minutes);
    }
  }

  return {
    title,
    category,
    startTime: has("startTime") ? cleanStartTime(body.startTime) : String(current.startTime ?? ""),
    status,
    poster: has("poster") ? cleanText(body.poster, 2000) : String(current.poster ?? ""),
    image: has("image") ? optionalUrl(body.image, "Featured image", { allowPath: true }) : String(current.image ?? ""),
    description: has("description") ? String(body.description ?? "").slice(0, 2000) : String(current.description ?? ""),
    competition,
    home,
    away,
    teams: has("home") || has("away") ? mergeTeams(current.teams, home, away) : (current.teams ?? mergeTeams(null, home, away)),
    featured: has("featured") ? truthy(body.featured) : Boolean(current.featured),
    active: has("active") ? body.active !== false && body.active !== "false" : current.active !== false,
    liveWindowOverride,
    url: has("url") ? cleanText(body.url, 300) : String(current.url ?? ""),
    streams: has("streams") ? cleanStreams(body.streams) : (Array.isArray(current.streams) ? current.streams : [])
  };
}

async function saveMatches(env, matches) {
  await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
  await rebuildSnapshots(env, matches);
}

// Un-featuring the match picked by the original single-featured selector
// also clears that selector, so the star in the admin list is the one truth.
async function clearLegacyFeatured(env, ids) {
  const legacy = await env.SPORTZFY_DB.get("site_featured_match", "text");
  if (legacy && ids.some(id => String(id) === legacy)) {
    await env.SPORTZFY_DB.delete("site_featured_match");
  }
}

/*
 * Live TV catalog
 *
 * Channels come from a playlist (the channels.json list the Android app uses,
 * any JSON list, or an M3U file) plus channels the admin adds by hand. A
 * playlist sync replaces the synced channels and keeps manual ones; status,
 * order and an admin-picked category survive re-syncs. Stream URLs never
 * appear in public responses: the player goes through /api/play/tv/:id.
 */
const TV_CATEGORIES = ["Sports", "Football", "Cricket", "Entertainment", "Kids", "News", "Movies"];

const TV_CATEGORY_RULES = [
  ["Cricket", /\b(cricket|willow)\b/],
  ["Football", /\b(football|fussball|fußball|soccer|footy|laliga|la liga|bundesliga|premier league|serie a|ligue 1|eredivisie|tudn)\b/],
  ["Sports", /(sport|espn|dazn|bein|eleven|golf|\bnba\b|\bnfl\b|\bnhl\b|\bmlb\b|tennis|motogp|racer|racing|\bufc\b|fight|\btsn\d*\b|\btyc\b|deportes|\bf1\b|premiere|sec network|acc network|big ten)/],
  ["Kids", /(cartoon|nick|disney|boomerang|cbeebies|kids|junior|baby)/],
  ["News", /(news|cnbc|\bcnn\b|weather|al jazeera)/],
  ["Movies", /(hbo|showtime|starz|cinema|movies|\bfxm\b|\bamc\b|\btcm\b|cinemax)/]
];

function channelCategory(explicit, name) {
  const wanted = String(explicit ?? "").trim().toLowerCase();
  const direct = TV_CATEGORIES.find(category => category.toLowerCase() === wanted);
  if (direct) return direct;
  if (wanted === "soccer") return "Football";
  const lower = String(name ?? "").toLowerCase();
  for (const [category, pattern] of TV_CATEGORY_RULES) {
    if (pattern.test(lower)) return category;
  }
  return "Entertainment";
}

// Finds the first comma outside double quotes: the channel name follows it.
function m3uNameStart(line) {
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    if (line[i] === "\"") quoted = !quoted;
    else if (line[i] === "," && !quoted) return i + 1;
  }
  return -1;
}

function parseM3u(text) {
  const entries = [];
  let pending = null;
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) continue;
    if (/^#EXTINF/i.test(line)) {
      const attr = (name) => (line.match(new RegExp(name + "=\"([^\"]*)\"", "i")) || [])[1] || "";
      const start = m3uNameStart(line);
      pending = {
        name: (start > 0 ? line.slice(start) : "").trim() || attr("tvg-name"),
        logo: attr("tvg-logo"),
        category: attr("group-title")
      };
      continue;
    }
    if (line.startsWith("#")) continue;
    if (pending) {
      entries.push({ ...pending, url: line });
      pending = null;
    }
  }
  return entries;
}

// Accepts M3U, a JSON array, an object wrapping one, or the JavaScript
// snippet form (`const channels = [...];`) channels.json uses.
function parsePlaylist(raw) {
  const text = String(raw ?? "");
  if (/^\s*#EXTM3U/i.test(text) || /#EXTINF/i.test(text)) return parseM3u(text);
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start < 0 || end <= start) throw new InputError("No channel list found — expected a JSON list or an M3U playlist");
  let data;
  try {
    data = JSON.parse(text.slice(start, end + 1));
  } catch (_) {
    throw new InputError("The channel list is not valid JSON");
  }
  if (!Array.isArray(data)) throw new InputError("The channel list is not a JSON array");
  return data.map(entry => ({
    name: entry?.name ?? entry?.title ?? "",
    logo: entry?.logo ?? entry?.tvg_logo ?? entry?.image ?? "",
    url: entry?.url ?? entry?.stream ?? entry?.link ?? "",
    category: entry?.category ?? entry?.group ?? "",
    sandbox: entry?.sandbox
  }));
}

// `"sandbox": true` in channels.json marks a channel whose page must run in a
// sandboxed frame (no pop-ups, no top-level navigation). Read loosely, like
// the Android app: only true or "true" count; anything else leaves the channel
// unsandboxed, because some channels render a black frame inside a sandbox.
function sandboxFlag(value) {
  return value === true || (typeof value === "string" && value.trim().toLowerCase() === "true");
}

// Entries without a name or an https stream URL are skipped, as are repeated
// stream URLs. `genre` is ignored on purpose: upstream marks every channel
// (cartoons included) "Sports", so categories come from `category` or the name.
function cleanPlaylistEntries(entries) {
  const seen = new Set();
  const out = [];
  let skipped = 0;
  for (const entry of Array.isArray(entries) ? entries : []) {
    if (out.length >= MAX_PLAYLIST_ENTRIES) {
      skipped++;
      continue;
    }
    const name = cleanText(decodeEntities(entry?.name), 120);
    const url = safeHttpsUrl(decodeEntities(entry?.url));
    if (!name || !url || seen.has(url)) {
      skipped++;
      continue;
    }
    seen.add(url);
    out.push({
      name,
      logo: safeHttpsUrl(decodeEntities(entry?.logo)),
      url,
      category: channelCategory(entry?.category, name),
      sandbox: sandboxFlag(entry?.sandbox)
    });
  }
  return { entries: out, skipped };
}

async function fetchPlaylist(url, timeoutMs) {
  let response;
  try {
    response = await fetchWithTimeout(
      url,
      { headers: { "Accept": "application/json, audio/x-mpegurl, text/plain, */*", "User-Agent": CHROME_UA } },
      timeoutMs
    );
  } catch (error) {
    throw new InputError(error?.name === "AbortError" ? "The playlist took too long to download" : "The playlist could not be downloaded");
  }
  if (!response.ok) throw new InputError("The playlist URL returned HTTP " + response.status);
  const text = await response.text();
  if (text.length > MAX_PLAYLIST_BYTES) throw new InputError("The playlist is larger than 5 MB");
  return cleanPlaylistEntries(parsePlaylist(text));
}

function emptyTvState() {
  return { channels: [], syncedAt: "", source: "", lastSync: null, lastAttemptAt: "" };
}

// null when the catalog has never been created (seeded on first use).
async function readTvState(env) {
  let raw = null;
  try {
    raw = await env.SPORTZFY_DB.get(TV_CHANNELS_KEY, "json");
  } catch (_) {}
  if (!raw || typeof raw !== "object") return null;
  if (Array.isArray(raw)) raw = { channels: raw };
  return {
    ...emptyTvState(),
    ...raw,
    channels: Array.isArray(raw.channels)
      ? raw.channels.filter(channel => channel && channel.id && channel.url)
      : []
  };
}

async function writeTvState(env, state) {
  await env.SPORTZFY_DB.put(TV_CHANNELS_KEY, JSON.stringify(state));
}

function uniqueChannelId(name, taken) {
  const base = slugify(name) || "channel";
  let id = base;
  for (let n = 2; taken.has(id); n++) id = base + "-" + n;
  taken.add(id);
  return id;
}

// Explicit sort numbers first (ascending), then catalog order.
function orderedChannels(channels) {
  const rank = (channel) => (Number.isFinite(channel.sort) ? channel.sort : Infinity);
  return channels
    .map((channel, index) => ({ channel, index }))
    .sort((a, b) => (rank(a.channel) - rank(b.channel)) || (a.index - b.index))
    .map(entry => entry.channel);
}

async function syncTvChannels(env, tvSource, options = {}) {
  const existing = await readTvState(env);
  const now = new Date().toISOString();
  let entries;
  let skipped;
  try {
    ({ entries, skipped } = await fetchPlaylist(tvSource.url, tvSource.timeoutSec * 1000));
    if (!entries.length) throw new InputError("The playlist has no channels with an https stream URL");
  } catch (error) {
    // Record the failure on an existing catalog; a first-time seed that fails
    // leaves nothing behind so the next request tries again.
    if (existing) {
      existing.lastAttemptAt = now;
      existing.lastSync = { at: now, ok: false, trigger: options.trigger || "admin", error: String(error?.message || error).slice(0, 300) };
      try { await writeTvState(env, existing); } catch (_) {}
    }
    throw error;
  }

  const state = existing || emptyTvState();
  const manual = state.channels.filter(channel => channel.origin !== "synced");
  const previousSynced = state.channels.filter(channel => channel.origin === "synced");
  const byUrl = new Map(previousSynced.map(channel => [channel.url, channel]));
  const bySlug = new Map();
  for (const channel of previousSynced) {
    const key = slugify(channel.name);
    if (!bySlug.has(key)) bySlug.set(key, channel);
  }
  const manualUrls = new Set(manual.map(channel => channel.url));
  const taken = new Set(manual.map(channel => channel.id));
  const matched = new Set();
  let added = 0;
  let updated = 0;
  const synced = [];

  for (const entry of entries) {
    if (manualUrls.has(entry.url)) continue; // a hand-made copy of the same stream wins
    let previous = byUrl.get(entry.url);
    if (!previous || matched.has(previous.id)) previous = bySlug.get(slugify(entry.name));
    if (previous && matched.has(previous.id)) previous = null;
    if (previous) matched.add(previous.id);

    const id = previous && !taken.has(previous.id) ? previous.id : uniqueChannelId(entry.name, taken);
    taken.add(id);
    const category = previous?.categoryLocked ? previous.category : entry.category;
    const changed = !previous || previous.name !== entry.name || previous.url !== entry.url ||
      previous.logo !== entry.logo || previous.category !== category || Boolean(previous.sandbox) !== entry.sandbox;
    if (!previous) added++;
    else if (changed) updated++;

    synced.push({
      id,
      name: entry.name,
      logo: entry.logo,
      url: entry.url,
      category,
      categoryLocked: Boolean(previous?.categoryLocked),
      sandbox: entry.sandbox,
      sort: Number.isFinite(previous?.sort) ? previous.sort : null,
      active: previous ? previous.active !== false : true,
      origin: "synced",
      createdAt: previous?.createdAt || now,
      updatedAt: changed ? now : (previous?.updatedAt || now)
    });
  }

  const removed = previousSynced.filter(channel => !matched.has(channel.id)).length;
  const next = {
    channels: [...synced, ...manual],
    syncedAt: now,
    source: tvSource.url,
    lastAttemptAt: now,
    lastSync: { at: now, ok: true, trigger: options.trigger || "admin", added, updated, removed, skipped, total: synced.length + manual.length }
  };
  await writeTvState(env, next);
  return next;
}

async function maybeAutoSyncTv(env, settings) {
  if (!settings.tvSource.autoSync) return null;
  const state = await readTvState(env);
  const now = Date.now();
  if (state) {
    const syncedAt = Date.parse(state.syncedAt || "") || 0;
    const attemptAt = Date.parse(state.lastAttemptAt || "") || 0;
    if (now - syncedAt < TV_AUTO_SYNC_EVERY_MS || now - attemptAt < TV_AUTO_RETRY_MS) return null;
  }
  return syncTvChannels(env, settings.tvSource, { trigger: "cron" });
}

// Reachability check for one stream. A dead host, a timeout, 404/410 or a
// server error fails; 401/403/405/429 still count as alive, because embed
// hosts commonly answer bots (and Cloudflare's own egress) that way while
// playing fine in a browser.
async function checkStreamUrl(url, timeoutMs = TV_CHECK_TIMEOUT_MS) {
  const started = Date.now();
  const target = safeHttpsUrl(url);
  if (!target) return { url: String(url ?? ""), ok: false, status: 0, ms: 0, error: "not an https URL" };
  try {
    const response = await fetchWithTimeout(
      target,
      { method: "GET", redirect: "follow", headers: { "User-Agent": CHROME_UA, "Accept": "text/html,application/vnd.apple.mpegurl,*/*" } },
      timeoutMs
    );
    // Only the status matters; drop the body without reading it.
    try {
      const cancelled = response.body?.cancel?.();
      if (cancelled && typeof cancelled.catch === "function") cancelled.catch(() => {});
    } catch (_) {}
    const status = Number(response.status) || 0;
    const ok = (status >= 200 && status < 400) || [401, 403, 405, 429].includes(status);
    return { url: target, ok, status, ms: Date.now() - started, ...(ok ? {} : { error: "HTTP " + status }) };
  } catch (error) {
    return { url: target, ok: false, status: 0, ms: Date.now() - started, error: error?.name === "AbortError" ? "timeout" : "unreachable" };
  }
}

async function checkStreamUrls(urls, concurrency) {
  const results = new Array(urls.length);
  let next = 0;
  const workers = Array.from({ length: Math.max(1, Math.min(concurrency, urls.length)) }, async () => {
    while (next < urls.length) {
      const index = next++;
      results[index] = await checkStreamUrl(urls[index]);
    }
  });
  await Promise.all(workers);
  return results;
}

function publicChannel(channel) {
  return {
    id: String(channel.id),
    name: String(channel.name ?? ""),
    logo: String(channel.logo ?? ""),
    category: TV_CATEGORIES.includes(channel.category) ? channel.category : channelCategory("", channel.name),
    kind: streamKind(channel.url),
    sandbox: Boolean(channel.sandbox)
  };
}

function adminChannel(channel) {
  return { ...channel, kind: streamKind(channel.url) };
}

// Fields an admin can set on a channel, applied over current.
function editableChannelFields(body, current = {}) {
  const has = (key) => Object.prototype.hasOwnProperty.call(body, key);
  const name = has("name") ? cleanText(decodeEntities(body.name), 120) : String(current.name ?? "");
  if (!name) throw new InputError("Channel name is required");
  const url = has("url") ? safeHttpsUrl(body.url) : String(current.url ?? "");
  if (!url) throw new InputError("Stream URL must be an https link — an m3u8 manifest or an embed page");
  const logo = has("logo") ? optionalUrl(body.logo, "Logo URL") : String(current.logo ?? "");

  let category = current.category;
  let categoryLocked = Boolean(current.categoryLocked);
  if (has("category")) {
    const wanted = TV_CATEGORIES.find(item => item.toLowerCase() === String(body.category ?? "").trim().toLowerCase());
    categoryLocked = Boolean(wanted);
    category = wanted || channelCategory("", name);
  } else if (!categoryLocked) {
    category = channelCategory("", name);
  }

  let sort = Number.isFinite(current.sort) ? current.sort : null;
  if (has("sort")) {
    if (body.sort === "" || body.sort === null || body.sort === undefined) {
      sort = null;
    } else {
      const n = Number(body.sort);
      if (!Number.isFinite(n)) throw new InputError("Sort order must be a number, or empty for Auto");
      sort = Math.round(Math.min(100000, Math.max(-100000, n)));
    }
  }

  return {
    name,
    url,
    logo,
    category,
    categoryLocked,
    sort,
    sandbox: has("sandbox") ? sandboxFlag(body.sandbox) : Boolean(current.sandbox),
    active: has("active") ? body.active !== false && body.active !== "false" : current.active !== false
  };
}

/*
 * Playback redirects. Public lists carry ids only; the player frame points
 * here and is sent on to the stream. Errors answer as sandboxed plain text so
 * nothing can run on this origin inside a player frame.
 */
function redirectTo(url) {
  return new Response(null, {
    status: 302,
    headers: {
      "Location": url,
      "Cache-Control": "no-store",
      "Referrer-Policy": "no-referrer",
      "Access-Control-Allow-Origin": "*",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

function playUnavailable() {
  return new Response("This stream is not available.", {
    status: 404,
    headers: {
      "Content-Type": "text/plain; charset=utf-8",
      "Cache-Control": "no-store",
      "Content-Security-Policy": "sandbox",
      "Access-Control-Allow-Origin": "*",
      "X-Content-Type-Options": "nosniff"
    }
  });
}

/*
 * Data storage report for the admin panel: which records exist in the KV
 * namespace and how large they are. Read-only.
 */
const STORAGE_RECORDS = [
  ["matches", "Matches"],
  [SNAPSHOT_ALL_TODAY, "Public match snapshot"],
  [TV_CHANNELS_KEY, "Live TV channels"],
  [SETTINGS_KEY, "Site settings"],
  [SYNC_STATE_KEY, "Sync cursor & fetch log"],
  [PLAY_INDEX_KEY, "Stream playback index"],
  [SNAPSHOT_FEATURED, "Featured snapshot"],
  [ADMIN_CREDENTIAL_KEY, "Admin password (hash)"]
];

async function storageReport(env) {
  if (!env.SPORTZFY_DB) return { ok: false, binding: "SPORTZFY_DB", records: [], error: "KV binding SPORTZFY_DB is missing" };
  const records = await Promise.all(STORAGE_RECORDS.map(async ([key, label]) => {
    try {
      const text = await env.SPORTZFY_DB.get(key, "text");
      if (text === null || text === undefined) return { key, label, present: false };
      let items = null;
      if (key === "matches" || key === SNAPSHOT_ALL_TODAY) {
        try { items = JSON.parse(text).length; } catch (_) {}
      } else if (key === TV_CHANNELS_KEY) {
        try { items = JSON.parse(text).channels.length; } catch (_) {}
      }
      return { key, label, present: true, bytes: text.length, ...(items !== null ? { items } : {}) };
    } catch (error) {
      return { key, label, present: false, error: String(error?.message || error) };
    }
  }));
  return { ok: true, binding: "SPORTZFY_DB", records, checkedAt: new Date().toISOString() };
}

/*
 * Route handlers
 */
async function handlePublicTv(request, env, url) {
  if (request.method === "GET" && url.pathname === "/api/tv/channels") {
    let state = await readTvState(env);
    if (!state) {
      // First use: seed the catalog from the configured playlist.
      try {
        state = await syncTvChannels(env, (await readSettings(env)).tvSource, { trigger: "seed" });
      } catch (error) {
        console.warn("Live TV seed failed:", String(error?.message || error));
        state = emptyTvState();
      }
    }
    const channels = orderedChannels(state.channels.filter(channel => channel.active !== false)).map(publicChannel);
    const present = new Set(channels.map(channel => channel.category));
    return cachedJson({
      channels,
      categories: TV_CATEGORIES.filter(category => present.has(category)),
      updatedAt: state.syncedAt || null
    }, 60);
  }

  const readable = request.method === "GET" || request.method === "HEAD";
  const tvPlay = url.pathname.match(/^\/api\/play\/tv\/([^/]+)$/);
  if (readable && tvPlay) {
    const id = decodeURIComponent(tvPlay[1]);
    const state = await readTvState(env);
    const channel = state?.channels.find(item => String(item.id) === id && item.active !== false);
    const target = channel ? safeHttpsUrl(channel.url) : "";
    return target ? redirectTo(target) : playUnavailable();
  }

  const streamPlay = url.pathname.match(/^\/api\/play\/stream\/([^/]+)\/([^/]+)$/);
  if (readable && streamPlay) {
    const matchId = decodeURIComponent(streamPlay[1]);
    const streamId = decodeURIComponent(streamPlay[2]);
    let index = null;
    try {
      index = await env.SPORTZFY_DB.get(PLAY_INDEX_KEY, "json");
    } catch (_) {}
    if (!index) index = buildPlayIndex(await readArray(env, "matches"));
    const target = safeHttpsUrl(index?.[matchId]?.[streamId]);
    return target ? redirectTo(target) : playUnavailable();
  }

  return null;
}

async function handleAdminTv(request, env, url) {
  const path = url.pathname;
  const method = request.method;

  if (path === "/api/admin/tv/channels" && method === "GET") {
    const state = await readTvState(env) || emptyTvState();
    return adminJson({
      channels: orderedChannels(state.channels).map(adminChannel),
      categories: TV_CATEGORIES,
      syncedAt: state.syncedAt || null,
      source: state.source || "",
      lastSync: state.lastSync || null
    });
  }

  if (path === "/api/admin/tv/channels" && method === "POST") {
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const state = await readTvState(env) || emptyTvState();
    const fields = editableChannelFields(body, {});
    if (state.channels.some(channel => channel.url === fields.url)) {
      return adminJson({ error: "A channel with this stream URL already exists" }, 409);
    }
    const now = new Date().toISOString();
    const taken = new Set(state.channels.map(channel => channel.id));
    const channel = { id: uniqueChannelId(fields.name, taken), ...fields, origin: "manual", createdAt: now, updatedAt: now };
    state.channels.push(channel);
    await writeTvState(env, state);
    return adminJson({ success: true, channel: adminChannel(channel) }, 201);
  }

  if (path === "/api/admin/tv/channels" && method === "DELETE") {
    if (url.searchParams.get("confirm") !== "all") return adminJson({ error: "Add ?confirm=all to delete every channel" }, 400);
    const state = await readTvState(env) || emptyTvState();
    const removed = state.channels.length;
    state.channels = [];
    await writeTvState(env, state);
    return adminJson({ success: true, removed });
  }

  if (path === "/api/admin/tv/channels/bulk" && method === "POST") {
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const ids = new Set((Array.isArray(body.ids) ? body.ids : []).map(String));
    const action = String(body.action || "");
    if (!["delete", "activate", "deactivate"].includes(action)) return adminJson({ error: "Unknown bulk action" }, 400);
    const state = await readTvState(env) || emptyTvState();
    const now = new Date().toISOString();
    let affected = 0;
    if (action === "delete") {
      const before = state.channels.length;
      state.channels = state.channels.filter(channel => !ids.has(String(channel.id)));
      affected = before - state.channels.length;
    } else {
      for (const channel of state.channels) {
        if (!ids.has(String(channel.id))) continue;
        channel.active = action === "activate";
        channel.updatedAt = now;
        affected++;
      }
    }
    if (affected) await writeTvState(env, state);
    return adminJson({ success: true, affected });
  }

  const channelPath = path.match(/^\/api\/admin\/tv\/channels\/([^/]+)$/);
  if (channelPath && (method === "PUT" || method === "DELETE")) {
    const id = decodeURIComponent(channelPath[1]);
    const state = await readTvState(env) || emptyTvState();
    const index = state.channels.findIndex(channel => String(channel.id) === id);
    if (index === -1) return adminJson({ error: "Channel not found" }, 404);

    if (method === "DELETE") {
      state.channels.splice(index, 1);
      await writeTvState(env, state);
      return adminJson({ success: true });
    }

    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const current = state.channels[index];
    const fields = editableChannelFields(body, current);
    if (state.channels.some((channel, i) => i !== index && channel.url === fields.url)) {
      return adminJson({ error: "Another channel already uses this stream URL" }, 409);
    }
    state.channels[index] = { ...current, ...fields, updatedAt: new Date().toISOString() };
    await writeTvState(env, state);
    return adminJson({ success: true, channel: adminChannel(state.channels[index]) });
  }

  if (path === "/api/admin/tv/sync" && method === "POST") {
    const settings = await readSettings(env);
    const state = await syncTvChannels(env, settings.tvSource, { trigger: "admin" });
    return adminJson({ success: true, ...state.lastSync, syncedAt: state.syncedAt });
  }

  if (path === "/api/admin/tv/parse" && method === "POST") {
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const playlistUrl = safeHttpsUrl(body.url);
    if (!playlistUrl) return adminJson({ error: "Paste an https link to an M3U or JSON playlist" }, 400);
    const settings = await readSettings(env);
    const { entries, skipped } = await fetchPlaylist(playlistUrl, settings.tvSource.timeoutSec * 1000);
    const state = await readTvState(env) || emptyTvState();
    const known = new Set(state.channels.map(channel => channel.url));
    return adminJson({
      entries: entries.map(entry => ({ ...entry, exists: known.has(entry.url) })),
      skipped,
      total: entries.length
    });
  }

  if (path === "/api/admin/tv/check" && method === "POST") {
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const urls = (Array.isArray(body.urls) ? body.urls : []).map(String);
    if (!urls.length) return adminJson({ results: [] });
    if (urls.length > TV_CHECK_BATCH) return adminJson({ error: "Check at most " + TV_CHECK_BATCH + " URLs per request" }, 400);
    const settings = await readSettings(env);
    return adminJson({ results: await checkStreamUrls(urls, settings.tvSource.concurrency) });
  }

  if (path === "/api/admin/tv/import" && method === "POST") {
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const { entries } = cleanPlaylistEntries(body.channels);
    const state = await readTvState(env) || emptyTvState();
    const known = new Set(state.channels.map(channel => channel.url));
    const taken = new Set(state.channels.map(channel => channel.id));
    const now = new Date().toISOString();
    let added = 0;
    for (const entry of entries) {
      if (known.has(entry.url)) continue;
      known.add(entry.url);
      state.channels.push({
        id: uniqueChannelId(entry.name, taken),
        name: entry.name,
        logo: entry.logo,
        url: entry.url,
        category: entry.category,
        categoryLocked: false,
        sandbox: entry.sandbox,
        sort: null,
        active: true,
        origin: "manual",
        createdAt: now,
        updatedAt: now
      });
      added++;
    }
    if (added) await writeTvState(env, state);
    return adminJson({ success: true, added, skipped: entries.length - added, total: state.channels.length });
  }

  return null;
}

async function handleAdminSystem(request, env, url, ctx) {
  const path = url.pathname;
  const method = request.method;

  if (path === "/api/admin/session" && method === "GET") {
    return adminJson({ ok: true });
  }

  if (path === "/api/admin/settings" && method === "GET") {
    const [settings, credential] = await Promise.all([readSettings(env), readAdminCredential(env)]);
    return adminJson({
      settings,
      sportCategories: SPORT_CATEGORIES,
      appSports: APP_SPORTS,
      tvCategories: TV_CATEGORIES,
      security: {
        passwordSource: credential ? "custom" : "env",
        passwordUpdatedAt: credential?.updatedAt || null
      }
    });
  }

  const settingsPath = path.match(/^\/api\/admin\/settings\/([A-Za-z]+)$/);
  if (settingsPath && method === "PUT") {
    const section = settingsPath[1];
    if (!SETTINGS_SECTIONS[section]) return adminJson({ error: "Unknown settings section" }, 404);
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const settings = await saveSettingsSection(env, section, body);
    let matchesUpdated = 0;
    if (section === "liveWindows") {
      // New rules move every match's expiry; stored expiries follow at once.
      const matches = await readArray(env, "matches");
      const timed = withExpiry(matches, settings.liveWindows);
      if (timed.changed) {
        matchesUpdated = timed.matches.filter((match, i) => match !== matches[i]).length;
        await saveMatches(env, timed.matches);
      }
    }
    return adminJson({ success: true, settings, ...(section === "liveWindows" ? { matchesUpdated } : {}) });
  }

  if (path === "/api/admin/sync-log" && method === "GET") {
    const [state, tv] = await Promise.all([readSyncState(env), readTvState(env)]);
    return adminJson({
      runs: state.runs,
      tv: tv ? { syncedAt: tv.syncedAt || null, lastSync: tv.lastSync || null, total: tv.channels.length } : null
    });
  }

  if (path === "/api/admin/cleanup" && method === "POST") {
    return adminJson(await runCleanup(env));
  }

  if (path === "/api/admin/storage" && method === "GET") {
    return adminJson(await storageReport(env));
  }

  if (path === "/api/admin/password" && (method === "POST" || method === "DELETE")) {
    if (await loginThrottled(env, request)) {
      return adminJson({ error: "Too many failed attempts. Try again later." }, 429);
    }
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    if (!await verifyAdminSecret(env, body.current)) {
      await countLoginFailure(env, request, ctx);
      return adminJson({ error: "Current password is incorrect" }, 403);
    }
    if (method === "DELETE") {
      await env.SPORTZFY_DB.delete(ADMIN_CREDENTIAL_KEY);
      return adminJson({ success: true, passwordSource: "env" });
    }
    const next = String(body.next ?? "").trim();
    if (next.length < PASSWORD_MIN_LENGTH) {
      return adminJson({ error: "New password must be at least " + PASSWORD_MIN_LENGTH + " characters" }, 400);
    }
    if (next.length > 200) return adminJson({ error: "New password is too long" }, 400);
    const salt = toHex(crypto.getRandomValues(new Uint8Array(16)));
    const updatedAt = new Date().toISOString();
    await env.SPORTZFY_DB.put(ADMIN_CREDENTIAL_KEY, JSON.stringify({
      salt,
      hash: await hashPassword(next, salt, PASSWORD_ITERATIONS),
      iterations: PASSWORD_ITERATIONS,
      updatedAt
    }));
    return adminJson({ success: true, passwordSource: "custom", passwordUpdatedAt: updatedAt });
  }

  return null;
}

async function handleAdminMatches(request, env, url) {
  const path = url.pathname;
  const method = request.method;

  if (method === "GET" && path === "/api/admin/matches") {
    return adminJson(await readArray(env, "matches"));
  }

  if (path === "/api/admin/matches" && method === "POST") {
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const matches = await readArray(env, "matches");
    const id = cleanText(body.id, 100) || crypto.randomUUID();
    if (matches.some(match => String(match.id) === id)) return adminJson({ error: "A match with this id already exists" }, 409);
    const settings = await readSettings(env);
    const now = new Date().toISOString();
    const match = { id, source: "manual", ...editableMatchFields({ status: "auto", ...body }, {}), createdAt: now, updatedAt: now };
    match.expiresAt = expiryFor(match, settings.liveWindows);

    matches.push(match);
    await saveMatches(env, matches);
    return adminJson({ success: true, match }, 201);
  }

  if (path === "/api/admin/matches" && method === "DELETE") {
    if (url.searchParams.get("confirm") !== "all") return adminJson({ error: "Add ?confirm=all to delete every match" }, 400);
    const matches = await readArray(env, "matches");
    await saveMatches(env, []);
    await env.SPORTZFY_DB.delete("site_featured_match");
    return adminJson({ success: true, removed: matches.length });
  }

  if (path === "/api/admin/matches/bulk" && method === "POST") {
    const body = await safeJson(request);
    if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
    const ids = new Set((Array.isArray(body.ids) ? body.ids : []).map(String));
    const action = String(body.action || "");
    const updates = {
      activate: { active: true },
      deactivate: { active: false },
      feature: { featured: true },
      unfeature: { featured: false }
    };
    if (action !== "delete" && !updates[action]) return adminJson({ error: "Unknown bulk action" }, 400);
    const matches = await readArray(env, "matches");
    const now = new Date().toISOString();
    let affected = 0;
    let next;
    if (action === "delete") {
      next = matches.filter(match => !ids.has(String(match.id)));
      affected = matches.length - next.length;
    } else {
      next = matches.map(match => {
        if (!ids.has(String(match.id))) return match;
        affected++;
        return { ...match, ...updates[action], updatedAt: now };
      });
    }
    if (affected) {
      await saveMatches(env, next);
      if (action === "delete" || action === "unfeature") await clearLegacyFeatured(env, [...ids]);
    }
    return adminJson({ success: true, affected });
  }

  const matchPath = path.match(/^\/api\/admin\/matches\/([^/]+)$/);
  if (matchPath && (method === "PUT" || method === "DELETE")) {
    const id = decodeURIComponent(matchPath[1]);
    const matches = await readArray(env, "matches");
    const index = matches.findIndex(match => String(match.id) === id);
    if (index === -1) return adminJson({ error: "Match not found" }, 404);

    if (method === "PUT") {
      const body = await safeJson(request);
      if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
      const current = matches[index];
      const settings = await readSettings(env);
      const updated = { ...current, ...editableMatchFields(body, current), updatedAt: new Date().toISOString() };
      updated.expiresAt = expiryFor(updated, settings.liveWindows);
      matches[index] = updated;

      await saveMatches(env, matches);
      if (current.featured && !updated.featured) await clearLegacyFeatured(env, [id]);
      return adminJson({ success: true, match: updated });
    }

    matches.splice(index, 1);
    await saveMatches(env, matches);
    await clearLegacyFeatured(env, [id]);
    return adminJson({ success: true });
  }

  return null;
}

async function apiHandler(request, env, ctx) {
  const url = new URL(request.url);

  if (request.method === "OPTIONS") {
    return new Response(null, { headers: jsonHeaders });
  }

  if (request.method === "POST" && url.pathname === "/api/admin/login") {
    if (await loginThrottled(env, request)) {
      return adminJson({ error: "Too many failed attempts. Try again later." }, 429);
    }
    const result = await createAdminSession(request, env, ctx);
    return result
      ? adminJson(result)
      : adminJson({ error: "Invalid admin password" }, 401);
  }

  if (request.method === "POST" && url.pathname === "/api/admin/logout") {
    const session = request.headers.get("X-Admin-Session");
    if (session) {
      try {
        await env.SPORTZFY_DB.delete("admin_session:" + session);
      } catch (error) {
        console.error("Admin logout failed:", String(error?.message || error));
      }
    }
    return adminJson({ success: true });
  }

  if (request.method === "GET" && url.pathname === "/api/streamed/matches") {
    try {
      const raw = await fetchStreamedMatches("all-today");
      let liveIds = new Set();
      try {
        const live = await fetchStreamedMatches("live");
        liveIds = new Set(live.map(match => String(match?.id || "")).filter(Boolean));
      } catch (_) {}
      return json(raw.map(match => toStreamedMatch(match, liveIds)));
    } catch (error) {
      return json({ error: "Unable to fetch Streamed matches" }, 502);
    }
  }

  if (request.method === "GET" && url.pathname === "/api/matches") {
    const now = Date.now();
    return json((await readArray(env, "matches"))
      .filter(match => isPubliclyVisible(match, now))
      .map(match => enrichMatchForPublic(match, now)));
  }

  if (request.method === "GET" && url.pathname === "/api/matches/all-today") {
    const now = Date.now();
    const shape = (list) => (Array.isArray(list) ? list : [])
      .filter(match => isPubliclyVisible(match, now))
      .map(match => enrichMatchForPublic(match, now));

    const snapshot = await env.SPORTZFY_DB.get(SNAPSHOT_ALL_TODAY, "text");
    if (snapshot) {
      try {
        const parsed = JSON.parse(snapshot);
        if (Array.isArray(parsed)) {
          return new Response(JSON.stringify(shape(parsed)), { headers: jsonHeaders });
        }
      } catch (_) {}
    }

    await migrateLegacyChannelSystem(env);
    const built = await rebuildSnapshots(env);
    let body = "[]";
    try {
      body = JSON.stringify(shape(JSON.parse(built.allTodayJson)));
    } catch (_) {}
    return new Response(body, { headers: jsonHeaders });
  }

  // On-demand single-source resolver with SWR semantics: stale entries are
  // served immediately and refreshed in the background via ctx.waitUntil.
  if (request.method === "GET" && url.pathname === "/api/streamed/streams") {
    const source = url.searchParams.get("source") || "";
    const id = url.searchParams.get("id") || "";
    if (!source || !id) return json({ error: "Missing source or id parameter" }, 400);

    const cached = await readStreamedSourceCache(env, source, id);
    let payload;
    if (cached && cached.fresh) {
      payload = { streams: cached.streams, resolvedAt: cached.resolvedAt, stale: false };
    } else if (cached) {
      payload = { streams: cached.streams, resolvedAt: cached.resolvedAt, stale: true };
      if (ctx) ctx.waitUntil(refreshStreamedSource(env, source, id).catch(() => {}));
    } else {
      const refreshed = await refreshStreamedSource(env, source, id);
      payload = refreshed.ok
        ? { streams: refreshed.record.streams, resolvedAt: refreshed.record.resolvedAt, stale: false }
        : { streams: [], resolvedAt: null, error: refreshed.error };
    }

    return json({
      source,
      id,
      streamCount: payload.streams.length,
      streams: payload.streams.map(publicStreamMeta),
      resolvedAt: payload.resolvedAt ? new Date(payload.resolvedAt).toISOString() : null,
      ...(payload.stale ? { stale: true } : {}),
      ...(payload.error ? { error: payload.error } : {})
    });
  }

  if (request.method === "GET" && url.pathname === "/api/images") {
    return serveProxiedImage(request);
  }

  if (request.method === "GET" && url.pathname === "/api/status") {
    return json({ status: "online", worker: "sportzfylive" });
  }

  const publicTv = await handlePublicTv(request, env, url);
  if (publicTv) return publicTv;

  // The Android app's remote config, edited from the admin "App" page.
  if (request.method === "GET" && url.pathname === "/api/app/config") {
    return cachedJson(appConfigPayload(await readSettings(env)), 30);
  }

  if (url.pathname === "/api/site-content" && request.method === "GET") {
    const [settings, legacyFeaturedId, snapshot] = await Promise.all([
      readSettings(env),
      env.SPORTZFY_DB.get("site_featured_match", "text"),
      env.SPORTZFY_DB.get(SNAPSHOT_ALL_TODAY, "text")
    ]);

    let matches = null;
    try {
      matches = snapshot ? JSON.parse(snapshot) : null;
    } catch (_) {}
    if (!Array.isArray(matches)) matches = await readArray(env, "matches");

    // Featured: the original single selection first, then every match the
    // admin starred, soonest kickoff first.
    const now = Date.now();
    const visible = matches.filter(match => isPubliclyVisible(match, now));
    const legacy = legacyFeaturedId ? visible.find(match => String(match.id) === String(legacyFeaturedId)) : null;
    const starred = visible
      .filter(match => match.featured && match !== legacy)
      .sort((a, b) => (Date.parse(a.startTime) || 0) - (Date.parse(b.startTime) || 0));
    const featuredMatches = [legacy, ...starred].filter(Boolean).slice(0, 12).map(match => enrichMatchForPublic(match, now));

    return json({
      ...publicSettings(settings),
      featuredMatchId: featuredMatches[0]?.id || "",
      featuredMatch: featuredMatches[0] || null,
      featuredMatches
    });
  }

  if (url.pathname.startsWith("/api/admin/")) {
    if (!await requireAdmin(request, env)) return adminJson({ error: "Admin authentication required" }, 401);

    if (request.method === "POST" && url.pathname === "/api/admin/sync-streamed") {
      try {
        const result = await syncStreamedMatches(env, { trigger: "admin" });
        return adminJson(result);
      } catch (error) {
        console.error("Admin Streamed sync failed:", error);
        return adminJson({ error: "Streamed sync failed" }, 502);
      }
    }

    const handled =
      await handleAdminMatches(request, env, url) ||
      await handleAdminTv(request, env, url) ||
      await handleAdminSystem(request, env, url, ctx);
    if (handled) return handled;

    // The original admin panel's notice/banner/featured editor. Kept so older
    // clients keep working; it writes into the same settings record.
    if (url.pathname === "/api/admin/site-content") {
      if (request.method === "GET") {
        const [settings, featuredId] = await Promise.all([
          readSettings(env),
          env.SPORTZFY_DB.get("site_featured_match", "text")
        ]);
        return adminJson({ notice: settings.notice, banner: settings.banner, featuredMatchId: featuredId || "" });
      }

      if (request.method === "PUT") {
        const body = await safeJson(request);
        if (!body || typeof body !== "object") return adminJson({ error: "Invalid JSON body" }, 400);
        const featuredId = String(body.featuredMatchId || "");
        if (featuredId && !(await readArray(env, "matches")).some(m => String(m.id) === featuredId)) {
          return adminJson({ error: "Featured match not found" }, 400);
        }
        const settings = await readSettings(env);
        settings.notice = SETTINGS_SECTIONS.notice({ ...settings.notice, ...(body.notice || {}) }, { lenient: false });
        settings.banner = SETTINGS_SECTIONS.banner({ ...settings.banner, ...(body.banner || {}) }, { lenient: false });
        settings.updated.notice = settings.updated.banner = new Date().toISOString();

        await Promise.all([
          env.SPORTZFY_DB.put(SETTINGS_KEY, JSON.stringify(settings)),
          env.SPORTZFY_DB.put("site_featured_match", featuredId)
        ]);

        await rebuildSnapshots(env);
        return adminJson({ success: true });
      }
    }

    return adminJson({ error: "Unknown endpoint" }, 404);
  }

  // Anything that reaches this point is not an API route, and the static assets
  // layer has already had its chance to answer. Answering 200 here masked real
  // misses (crawler/icon probes, typo'd paths) as "API Online" text.
  const pathname = new URL(request.url).pathname;
  if (pathname === "/api" || pathname.startsWith("/api/")) {
    return json({ error: "Unknown endpoint" }, 404);
  }

  return new Response("Not found", {
    status: 404,
    headers: { "Content-Type": "text/plain; charset=utf-8" }
  });
}

// Admin writes that change what the public match list shows.
const SNAPSHOT_ROUTES = /^\/api\/admin\/(matches|site-content|sync-streamed|cleanup)/;

export default {
  async fetch(request, env, ctx) {
    try {
      const response = await apiHandler(request, env, ctx);

      if (
        response &&
        request.method !== "GET" &&
        request.method !== "HEAD" &&
        request.method !== "OPTIONS" &&
        response.status < 300 &&
        SNAPSHOT_ROUTES.test(new URL(request.url).pathname)
      ) {
        ctx.waitUntil(rebuildSnapshots(env).catch(error => console.error("Snapshot refresh failed:", error)));
      }

      return response;
    } catch (err) {
      if (err instanceof InputError) return adminJson({ error: err.message }, 400);
      console.error("Worker unhandled exception:", err?.stack || err?.message || err);
      const isKvQuota = String(err?.message || "").includes("free usage limit") || String(err?.message || "").includes("10048");
      return adminJson({
        error: isKvQuota
          ? "Cloudflare KV daily write limit reached (1,000 writes/day free tier). Resets at 00:00 UTC."
          : (err?.message || "Internal server error")
      }, isKvQuota ? 429 : 500);
    }
  },

  async scheduled(controller, env) {
    if (controller.cron !== "*/10 * * * *") return;
    let settings;
    try {
      settings = await readSettings(env);
    } catch (_) {
      settings = normalizeSettings({});
    }

    if (settings.ingest.autoSync) {
      try {
        await env.SPORTZFY_SYNC_QUEUE.send({
          type: "scheduled-sync",
          scheduledAt: controller.scheduledTime
        });
      } catch (error) {
        // No queue binding (a plan or config without Queues, or a queue outage):
        // run the same sync inline so the 10-minute refresh keeps working instead
        // of stopping silently.
        console.error("Failed to queue scheduled sync, syncing inline:", error);
        try {
          const result = await syncStreamedMatches(env, { trigger: "cron" });
          console.log("Inline scheduled sync completed:", JSON.stringify(result));
        } catch (inlineError) {
          console.error("Inline scheduled sync failed:", inlineError);
        }
      }
    }

    try {
      const tv = await maybeAutoSyncTv(env, settings);
      if (tv) console.log("Live TV playlist synced:", JSON.stringify(tv.lastSync));
    } catch (error) {
      console.error("Live TV auto-sync failed:", String(error?.message || error));
    }
  },

  async queue(batch, env) {
    for (const message of batch.messages) {
      try {
        const type = message.body?.type || "scheduled-sync";
        if (type === "scheduled-sync" || type === "streamed-sync") {
          const result = await syncStreamedMatches(env, { trigger: "cron" });
          console.log("Streamed sync completed:", JSON.stringify(result));
        }
        message.ack();
      } catch (error) {
        console.error("Streamed sync failed:", error);
        message.retry();
      }
    }
  }
};

