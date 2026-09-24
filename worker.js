// SportzfyLive Worker
// Streamed-only match/source architecture.
// Legacy TV-channel storage is migrated away on the first sync.

const SNAPSHOT_ALL_TODAY = "snapshot:all-today";
const SNAPSHOT_FEATURED = "snapshot:featured";
const MIGRATION_KEY = "channel_system_migrated";
const STREAMED_STREAM_ENDPOINT = "https://streamed.pk/api/stream";
const STREAMED_STREAMS_KEY_PREFIX = "streamed:streams:";
const STREAMED_FRESH_TTL_MS = 45 * 60 * 1000;    // serve-from-cache window per source
const STREAMED_STALE_TTL_SECONDS = 6 * 60 * 60;  // KV retention (stale ceiling) per source
const STREAMED_EMPTY_TTL_SECONDS = 15 * 60;      // empty results re-check quickly (lineups appear near go-live)
const STREAMED_SOURCE_TIMEOUT_MS = 8000;         // per-source fetch timeout
const MAX_SYNC_RESOLVE_OPS = 40;                 // subrequest budget for source resolution per sync
const SYNC_CURSOR_KEY = "sync:resolve-cursor";   // rotates which matches resolve first

const jsonHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, X-Admin-Token, X-Admin-Session"
};

function json(data, status = 200) {
  return Response.json(data, { status, headers: jsonHeaders });
}

async function readArray(env, key) {
  const value = await env.SPORTZFY_DB.get(key, "json");
  return Array.isArray(value) ? value : [];
}

async function rebuildSnapshots(env, matchesIn) {
  const matches = matchesIn || await readArray(env, "matches");
  const seen = new Set();
  const allToday = [];

  for (const match of matches) {
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
    await env.SPORTZFY_DB.put(SNAPSHOT_ALL_TODAY, allTodayJson);
  } catch (error) {
    console.error("Snapshot write failed:", String(error?.message || error));
    try { await env.SPORTZFY_DB.delete(SNAPSHOT_ALL_TODAY); } catch (_) {}
  }

  const previousFeatured = await env.SPORTZFY_DB.get(SNAPSHOT_FEATURED, "text");
  if (previousFeatured !== featuredJson) {
    await env.SPORTZFY_DB.put(SNAPSHOT_FEATURED, featuredJson);
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

async function requireAdmin(request, env) {
  const session = request.headers.get("X-Admin-Session");
  if (!session) return false;

  const record = await env.SPORTZFY_DB.get("admin_session:" + session, "json");
  if (!record) return false;
  if (record.expiresAt <= Date.now()) return false;

  return true;
}

async function createAdminSession(request, env) {
  const supplied = request.headers.get("X-Admin-Token");
  if (!env.ADMIN_TOKEN || !supplied || supplied !== env.ADMIN_TOKEN) return null;

  const session = crypto.randomUUID();
  const expiresAt = Date.now() + 24 * 60 * 60 * 1000;

  await env.SPORTZFY_DB.put(
    "admin_session:" + session,
    JSON.stringify({ expiresAt }),
    { expirationTtl: 24 * 60 * 60 }
  );

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

/*
 * Public response shaping. Builds a fresh object from a whitelist, so stored
 * admin fields (match URL, live window, manual stream rows) and any unknown
 * fields — including playback URLs — can never leak into a public response.
 */
function enrichMatchForPublic(match) {
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
    status: String(match.status ?? ""),
    poster: String(match.poster ?? ""),
    description: String(match.description ?? ""),
    teams: match.teams && typeof match.teams === "object" ? match.teams : null,
    competition: String(match.competition ?? ""),
    popular: Boolean(match.popular),
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
    enriched.manual = { streamCount: match.streams.length };
  }

  return enriched;
}

async function syncStreamedMatches(env) {
  await migrateLegacyChannelSystem(env);

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
      liveWindow: Number(previous?.liveWindow ?? 210),
      url: previous?.url || "",
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
  let resolveCount = 0;
  const budget = { remaining: MAX_SYNC_RESOLVE_OPS };
  let cursor = 0;
  try { cursor = Number(await env.SPORTZFY_DB.get(SYNC_CURSOR_KEY, "text")) || 0; } catch (_) {}
  const rotateBy = fresh.length ? cursor % fresh.length : 0;

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

  if (fresh.length) {
    try {
      await env.SPORTZFY_DB.put(SYNC_CURSOR_KEY, String((rotateBy + 1) % fresh.length));
    } catch (_) {}
  }
  resolveCount = fresh.reduce(
    (total, match) => total + (Number(match.streamedSummary?.streamCount) || 0),
    0
  );

  const finalMatches = [...manual, ...fresh];
  await env.SPORTZFY_DB.put("matches", JSON.stringify(finalMatches));
  await rebuildSnapshots(env, finalMatches);

  return {
    success: true,
    streamedCount: fresh.length,
    totalMatches: finalMatches.length,
    removed: removed.length,
    resolvedCount: resolveCount
  };
}

async function apiHandler(request, env, ctx) {
  const url = new URL(request.url);

  if (request.method === "OPTIONS") {
    return new Response(null, { headers: jsonHeaders });
  }

  if (request.method === "POST" && url.pathname === "/api/admin/login") {
    const result = await createAdminSession(request, env);
    return result
      ? json(result)
      : json({ error: "Invalid admin token" }, 401);
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
    return json({ success: true });
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
    return json((await readArray(env, "matches")).map(enrichMatchForPublic));
  }

  if (request.method === "GET" && url.pathname === "/api/matches/all-today") {
    const snapshot = await env.SPORTZFY_DB.get(SNAPSHOT_ALL_TODAY, "text");
    if (snapshot) {
      try {
        const parsed = JSON.parse(snapshot);
        if (Array.isArray(parsed)) {
          return new Response(JSON.stringify(parsed.map(enrichMatchForPublic)), { headers: jsonHeaders });
        }
      } catch (_) {}
    }

    await migrateLegacyChannelSystem(env);
    const built = await rebuildSnapshots(env);
    let body = "[]";
    try {
      const parsed = JSON.parse(built.allTodayJson);
      body = JSON.stringify((Array.isArray(parsed) ? parsed : []).map(enrichMatchForPublic));
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

  if (request.method === "POST" && url.pathname === "/api/admin/sync-streamed") {
    if (!await requireAdmin(request, env)) return json({ error: "Admin authentication required" }, 401);
    try {
      const result = await syncStreamedMatches(env);
      return json(result);
    } catch (error) {
      console.error("Admin Streamed sync failed:", error);
      return json({ error: "Streamed sync failed" }, 502);
    }
  }

  if (request.method === "GET" && url.pathname === "/api/admin/matches") {
    if (!await requireAdmin(request, env)) return json({ error: "Admin authentication required" }, 401);
    return json(await readArray(env, "matches"));
  }

  if (url.pathname === "/api/admin/matches" && request.method === "POST") {
    if (!await requireAdmin(request, env)) return json({ error: "Admin authentication required" }, 401);

    const body = await request.json();
    const now = new Date().toISOString();
    const match = {
      id: String(body.id || crypto.randomUUID()),
      title: String(body.title || "Untitled match").trim(),
      category: String(body.category || "OTHER").trim(),
      startTime: body.startTime || "",
      status: String(body.status || "scheduled"),
      poster: String(body.poster || ""),
      description: String(body.description || ""),
      competition: String(body.competition || ""),
      home: String(body.home || ""),
      away: String(body.away || ""),
      teams: body.teams && typeof body.teams === "object" ? body.teams : {
        home: String(body.home || ""),
        away: String(body.away || "")
      },
      liveWindow: Number(body.liveWindow ?? 210),
      url: String(body.url || ""),
      streams: Array.isArray(body.streams) ? body.streams : [],
      createdAt: now,
      updatedAt: now
    };

    const matches = await readArray(env, "matches");
    matches.push(match);
    await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
    await rebuildSnapshots(env, matches);
    return json({ success: true, match }, 201);
  }

  const matchPath = url.pathname.match(/^\/api\/admin\/matches\/([^/]+)$/);
  if (matchPath) {
    if (!await requireAdmin(request, env)) return json({ error: "Admin authentication required" }, 401);

    const id = decodeURIComponent(matchPath[1]);
    const matches = await readArray(env, "matches");
    const index = matches.findIndex(match => String(match.id) === id);
    if (index === -1) return json({ error: "Match not found" }, 404);

    if (request.method === "PUT") {
      const body = await request.json();
      const current = matches[index];

      matches[index] = {
        ...current,
        title: String(body.title ?? current.title).trim(),
        category: String(body.category ?? current.category).trim() || "OTHER",
        startTime: body.startTime ?? current.startTime ?? "",
        status: String(body.status ?? current.status ?? "scheduled"),
        poster: String(body.poster ?? current.poster ?? ""),
        description: String(body.description ?? current.description ?? ""),
        competition: String(body.competition ?? current.competition ?? ""),
        home: String(body.home ?? current.home ?? ""),
        away: String(body.away ?? current.away ?? ""),
        teams: body.teams && typeof body.teams === "object"
          ? body.teams
          : (current.teams || { home: String(body.home ?? current.home ?? ""), away: String(body.away ?? current.away ?? "") }),
        liveWindow: Number.isFinite(Number(body.liveWindow))
          ? Number(body.liveWindow)
          : Number(current.liveWindow ?? 210),
        url: String(body.url ?? current.url ?? ""),
        streams: Array.isArray(body.streams) ? body.streams : (Array.isArray(current.streams) ? current.streams : []),
        updatedAt: new Date().toISOString()
      };

      await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
      await rebuildSnapshots(env, matches);
      return json({ success: true, match: matches[index] });
    }

    if (request.method === "DELETE") {
      matches.splice(index, 1);
      await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
      await rebuildSnapshots(env, matches);
      return json({ success: true });
    }
  }

  if (url.pathname === "/api/site-content" && request.method === "GET") {
    const [notice, banner, featuredId] = await Promise.all([
      env.SPORTZFY_DB.get("site_notice", "json"),
      env.SPORTZFY_DB.get("site_banner", "json"),
      env.SPORTZFY_DB.get("site_featured_match", "text")
    ]);

    let featuredMatch = null;
    if (featuredId) {
      const matches = await readArray(env, "matches");
      const found = matches.find(match => String(match.id) === String(featuredId));
      featuredMatch = found ? enrichMatchForPublic(found) : null;
    }

    return json({
      notice: notice || { enabled: false, text: "", link: "" },
      banner: banner || { enabled: false, image: "", link: "", alt: "" },
      featuredMatchId: featuredId || "",
      featuredMatch
    });
  }

  if (url.pathname === "/api/admin/site-content") {
    if (!await requireAdmin(request, env)) return json({ error: "Admin authentication required" }, 401);

    if (request.method === "GET") {
      const [notice, banner, featuredId] = await Promise.all([
        env.SPORTZFY_DB.get("site_notice", "json"),
        env.SPORTZFY_DB.get("site_banner", "json"),
        env.SPORTZFY_DB.get("site_featured_match", "text")
      ]);
      return json({
        notice: notice || { enabled: false, text: "", link: "" },
        banner: banner || { enabled: false, image: "", link: "", alt: "" },
        featuredMatchId: featuredId || ""
      });
    }

    if (request.method === "PUT") {
      const body = await request.json();
      const notice = {
        enabled: Boolean(body.notice?.enabled),
        text: String(body.notice?.text || "").slice(0, 500),
        link: String(body.notice?.link || "").slice(0, 1000)
      };
      const banner = {
        enabled: Boolean(body.banner?.enabled),
        image: String(body.banner?.image || "").slice(0, 2000),
        link: String(body.banner?.link || "").slice(0, 1000),
        alt: String(body.banner?.alt || "Banner").slice(0, 200)
      };
      const featuredId = String(body.featuredMatchId || "");
      if (featuredId && !(await readArray(env, "matches")).some(m => String(m.id) === featuredId)) {
        return json({ error: "Featured match not found" }, 400);
      }

      await Promise.all([
        env.SPORTZFY_DB.put("site_notice", JSON.stringify(notice)),
        env.SPORTZFY_DB.put("site_banner", JSON.stringify(banner)),
        env.SPORTZFY_DB.put("site_featured_match", featuredId)
      ]);

      await rebuildSnapshots(env);
      return json({ success: true });
    }
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

export default {
  async fetch(request, env, ctx) {
    const response = await apiHandler(request, env, ctx);

    if (
      request.method !== "GET" &&
      request.method !== "HEAD" &&
      request.method !== "OPTIONS" &&
      response.status < 300 &&
      !new URL(request.url).pathname.startsWith("/api/admin/login") &&
      !new URL(request.url).pathname.startsWith("/api/admin/logout")
    ) {
      ctx.waitUntil(rebuildSnapshots(env).catch(error => console.error("Snapshot refresh failed:", error)));
    }

    return response;
  },

  async scheduled(controller, env) {
    if (controller.cron !== "*/10 * * * *") return;
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
        const result = await syncStreamedMatches(env);
        console.log("Inline scheduled sync completed:", JSON.stringify(result));
      } catch (inlineError) {
        console.error("Inline scheduled sync failed:", inlineError);
      }
    }
  },

  async queue(batch, env) {
    for (const message of batch.messages) {
      try {
        const type = message.body?.type || "scheduled-sync";
        if (type === "scheduled-sync" || type === "streamed-sync") {
          const result = await syncStreamedMatches(env);
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
