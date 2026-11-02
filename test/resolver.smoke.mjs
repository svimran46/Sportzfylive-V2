// Functional smoke test for the Streamed source resolver in worker.js.
// Mocks KV, fetch, Response and timer globals, then drives the real request
// flows: cron -> queue -> sync, public GET endpoints, and the on-demand
// resolver endpoint. Verifies the no-embedUrl guarantee at every layer.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

// ---------------------------------------------------------------------------
// Globals worker.js expects, installed BEFORE the dynamic import below.
// ---------------------------------------------------------------------------

// setTimeout is only used for the per-source fetch timeout; we never fire it.
globalThis.setTimeout = (fn) => 0;
globalThis.clearTimeout = () => {};

class PolyfillResponse {
  constructor(body, init = {}) {
    this.body = body;
    this.status = init.status ?? 200;
    this.headers = init.headers ?? {};
  }
  static json(data, init = {}) {
    return new PolyfillResponse(JSON.stringify(data), init);
  }
  async json() {
    return JSON.parse(this.body);
  }
  async text() {
    return this.body;
  }
}
globalThis.Response = PolyfillResponse;

// Scriptable fetch: tests assign `fetchHandler(url)`; every call is logged.
const okPaths = new Set();
const fetchLog = [];
let fetchHandler = async () => {
  throw new Error("unexpected fetch (no handler installed)");
};
globalThis.fetch = async (url) => {
  fetchLog.push(String(url));
  return fetchHandler(String(url));
};

// ---------------------------------------------------------------------------
// Mock KV namespace.
// ---------------------------------------------------------------------------

function makeKV() {
  const store = new Map();
  return {
    store,
    async get(key, type) {
      const value = store.get(key);
      if (value === undefined || value === null) return null;
      return type === "json" ? JSON.parse(value) : value;
    },
    async put(key, value) {
      store.set(key, value);
    },
    async delete(key) {
      store.delete(key);
    }
  };
}

// ---------------------------------------------------------------------------
// Load the worker.
// ---------------------------------------------------------------------------

const worker = await import("../worker.js");

const kv = makeKV();
const queueLog = [];
const waitLog = [];
const env = {
  SPORTZFY_DB: kv,
  ADMIN_TOKEN: "test-token",
  SPORTZFY_SYNC_QUEUE: {
    async send(message) {
      queueLog.push(message);
    }
  }
};
const ctx = {
  waitUntil(promise) {
    waitLog.push(promise);
  }
};
async function drainWaitUntil() {
  const pending = [...waitLog];
  waitLog.length = 0;
  for (const promise of pending) await promise.catch(() => {});
}

const BASE = "https://worker.test";
function req(path, options = {}) {
  const method = options.method ?? "GET";
  const headers = new Map();
  for (const [key, value] of Object.entries(options.headers ?? {})) {
    headers.set(key.toLowerCase(), value);
  }
  return {
    url: BASE + path,
    method,
    headers: {
      get: (name) => headers.get(name.toLowerCase()) ?? null
    },
    json: async () => {
      if (options.body === undefined) {
        throw new SyntaxError("Unexpected end of JSON input");
      }
      return options.body;
    }
  };
  // NOTE: a request with no JSON body mimics workerd behavior: request.json()
  // rejects, which safeJson() in the worker must convert into a 400.
}

let passed = 0;
function ok(name) {
  passed += 1;
  console.log("  ok " + passed + " - " + name);
}
function streamFetchCount() {
  return fetchLog.filter((url) => url.includes("/api/stream/")).length;
}

// ---------------------------------------------------------------------------
// Fixture: raw ppv.st catalog (same shape api.ppv.st/api/streams returns).
// One grouped list; every event carries its schedule + substreams inline, so
// a sync is a single fetch with no per-source resolution pass.
// ---------------------------------------------------------------------------

const NOW = Date.now();
const sec = (seconds) => Math.floor(NOW / 1000) + seconds;
const PPV_EMBED_HOST = "https://taifood-blog.asia/embed/";

const PPV_GROUPS = [
  {
    category: "Football",
    id: 34,
    always_live: false,
    streams: [
      {
        id: 31164,
        name: "Valencia vs. Real Sociedad",
        tag: "LaLiga",
        source_tag: "HD | StreamFutbol",
        poster: "https://static.ppvservices.st/assets/thumb/val-thumb.jpg",
        blurhash: "L6AA,*of",
        colors: ["#01326a", "#01326a"],
        uri_name: "laliga/2026-11-02/val-rso",
        starts_at: sec(3600),
        ends_at: sec(7800),
        always_live: false,
        locale: "en",
        category_name: "Football",
        iframe: PPV_EMBED_HOST + "laliga/2026-11-02/val-rso",
        substreams: [
          {
            id: 1031164,
            name: "Valencia vs. Real Sociedad",
            tag: "LaLiga",
            uri_name: "laliga/2026-11-02/val-rso_540p",
            source_tag: "SD | StreamFutbol",
            locale: "es",
            iframe: PPV_EMBED_HOST + "laliga/2026-11-02/val-rso_540p"
          },
          // Junk upstream row: same identity as the main stream, the
          // summary builder must dedupe it away.
          {
            id: 1031165,
            name: "",
            uri_name: "",
            source_tag: "SD | StreamFutbol",
            locale: "es",
            iframe: PPV_EMBED_HOST + "laliga/2026-11-02/val-rso"
          }
        ],
        viewers: "0"
      }
    ]
  },
  {
    category: "Basketball",
    id: 35,
    always_live: false,
    streams: [
      {
        id: 31165,
        name: "Lakers vs Celtics",
        tag: "NBA",
        source_tag: "HD | CourtVision",
        poster: "https://static.ppvservices.st/assets/thumb/lal-thumb.jpg",
        uri_name: "nba/2026-11-02/lal-bos",
        starts_at: sec(-3600),
        ends_at: sec(3600),
        always_live: false,
        locale: "en",
        category_name: "Basketball",
        iframe: PPV_EMBED_HOST + "nba/2026-11-02/lal-bos",
        substreams: [],
        viewers: "0"
      }
    ]
  },
  {
    category: "Tennis",
    id: 36,
    always_live: false,
    streams: [
      {
        id: 31166,
        name: "ATP Masters",
        tag: "ATP",
        poster: "",
        uri_name: "atp/masters",
        starts_at: 0,
        ends_at: 0,
        always_live: true,
        locale: "en",
        category_name: "Tennis",
        iframe: "",
        substreams: [],
        viewers: "0"
      }
    ]
  }
];

// Detail-payload for the on-demand ppv resolution (matches
// api.ppv.st/api/streams/<id>).
const PPV_DETAIL_31164 = {
  success: true,
  data: {
    id: 31164,
    name: "Valencia vs. Real Sociedad",
    tag: "LaLiga",
    source_tag: "HD | StreamFutbol",
    poster: "https://api.ppv.st/assets/thumb/val-detail-thumb.jpg",
    uri: "laliga/2026-11-02/val-rso",
    locale: "en",
    sources: [
      { name: "External Player Frame", default: true, type: "iframe", data: PPV_EMBED_HOST + "laliga/2026-11-02/val-rso" }
    ],
    substreams: [
      {
        id: 1031164,
        name: "Valencia vs. Real Sociedad",
        tag: "LaLiga",
        uri: "laliga/2026-11-02/val-rso_540p",
        source_tag: "SD | StreamFutbol",
        locale: "es",
        sources: [
          { name: "External Player Frame", default: true, type: "iframe", data: PPV_EMBED_HOST + "laliga/2026-11-02/val-rso_540p" }
        ]
      }
    ]
  }
};

// Sensitized stream payloads include embedUrl fields on purpose: the test
// asserts those never reach KV or public responses. (Legacy STREAMED source
// groups still resolve on demand — the Android fallback route speaks this
// protocol unchanged.)
const STREAM_PAYLOADS = {
  "StreamedSoccerHD:src-101-a": [
    { streamNo: 1, language: "English", hd: true, embedUrl: "https://evil.example/hls1.m3u8" },
    { streamNo: 2, language: "Spanish", hd: true, embedUrl: "https://evil.example/hls2.m3u8" },
    { streamNo: 2, language: "Spanish", hd: true, embedUrl: "https://evil.example/dup.m3u8" }
  ]
};

function jsonResponse(status, data) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => data
  };
}

function installMatchHandler() {
  fetchHandler = async (url) => {
    const pathname = new URL(url).pathname;
    if (pathname === "/api/streams") return jsonResponse(200, {
      success: true,
      timestamp: 1,
      READ_ME: "Interested in using our API? Contact us for more information.",
      performance: 0.05,
      streams: PPV_GROUPS
    });
    if (pathname === "/api/streams/31164") return jsonResponse(200, PPV_DETAIL_31164);
    if (pathname === "/api/streams/ghost") return jsonResponse(404, { success: false });
    if (pathname === "/api/streams/down") return jsonResponse(503, { success: false });
    if (pathname.startsWith("/api/stream/")) {
      // Production URL shape: /api/stream/{source}/{id} (path, not query).
      const parts = pathname.split("/");
      const key = parts[3] + ":" + parts[4];
      if (key === "GoalPass:src-101-b") return jsonResponse(429, { error: "rate limited" });
      if (key === "StreamedBasket:src-102-a") return jsonResponse(503, { error: "down" });
  if (key === "StreamedSoccerHD:src-101-a") {
    // Assert the path-form URL the worker must use.
    okPaths.add(url);
  }
      if (key === "HindiSrc:n1") {
        return jsonResponse(200, [{ streamNo: 1, language: "Hindi", hd: false }]);
      }
      if (STREAM_PAYLOADS[key]) return jsonResponse(200, STREAM_PAYLOADS[key]);
      return jsonResponse(404, { error: "not found" });
    }
    throw new Error("unexpected fetch: " + url);
  };
}

// ---------------------------------------------------------------------------
// 1. Sync (production path: cron -> queue -> syncStreamedMatches)
// ---------------------------------------------------------------------------

console.log("# sync via queue consumer");
installMatchHandler();
await worker.default.queue(
  {
    messages: [
      {
        body: { type: "scheduled-sync" },
        ack() {},
        retry() {
          throw new Error("queue message retried unexpectedly");
        }
      }
    ]
  },
  env
);
ok("queue consumer ran without retries");

const storedMatches = JSON.parse(kv.store.get("matches"));
assert.equal(storedMatches.length, 3);
ok("sync stored 3 matches");

assert.equal(streamFetchCount(), 0, "sync is one catalog fetch: no per-source resolution calls");

const valencia = storedMatches.find((match) => match.id === "ppv-31164");
assert.ok(valencia, "ppv-31164 present");
assert.equal(valencia.source, "ppv");
assert.equal(valencia.title, "Valencia vs. Real Sociedad");
assert.equal(valencia.category, "FOOTBALL");
assert.equal(valencia.competition, "LaLiga");
assert.equal(valencia.status, "scheduled");
assert.equal(Date.parse(valencia.startTime), sec(3600) * 1000, "kickoff from starts_at");
assert.equal(valencia.poster, "https://static.ppvservices.st/assets/thumb/val-thumb.jpg");
assert.deepEqual(valencia.teams, { home: { name: "Valencia" }, away: { name: "Real Sociedad" } });
// The live window mirrors the feed's own end timestamp.
assert.equal(Date.parse(valencia.expiresAt), sec(7800) * 1000, "expiry at ends_at");
assert.deepEqual(valencia.sources, [{ source: "ppv", id: "31164" }]);

const valenciaStreams = valencia.streamedSummary.streams;
assert.equal(valencia.streamedSummary.sourceCount, 1);
// Main HD + Spanish SD substream survive; the junk duplicate identity row is deduped.
assert.equal(valencia.streamedSummary.streamCount, 2);
assert.equal(valenciaStreams.length, 2);
const ppvMain = valenciaStreams[0];
assert.equal(ppvMain.streamNo, 1);
assert.equal(ppvMain.language, "en");
assert.ok(ppvMain.hd, "main stream read as HD from the source_tag");
assert.equal(ppvMain.embed, PPV_EMBED_HOST + "laliga/2026-11-02/val-rso");
const ppvSub = valenciaStreams[1];
assert.equal(ppvSub.streamNo, 2);
assert.equal(ppvSub.language, "es");
assert.ok(!ppvSub.hd);
assert.equal(ppvSub.embed, PPV_EMBED_HOST + "laliga/2026-11-02/val-rso_540p");
ok("multi-substream event mapped with dedupe (2 streams)");

const ppvGroup = valencia.streamedSummary.sources[0];
assert.equal(ppvGroup.source, "ppv");
assert.equal(ppvGroup.id, "31164");
assert.equal(ppvGroup.streamCount, 2);
assert.ok(!ppvGroup.stale && !ppvGroup.error);
ok("ppv group carries the inline stream summary");

const lakers = storedMatches.find((match) => match.id === "ppv-31165");
assert.equal(lakers.status, "live", "event already started reads live");
assert.equal(lakers.streamedSummary.streamCount, 1);
ok("started event is live with its single stream");

const tennis = storedMatches.find((match) => match.id === "ppv-31166");
assert.equal(tennis.status, "live", "always_live reads live");
assert.equal(tennis.startTime, "", "24/7 entry has no kickoff");
assert.ok(!tennis.expiresAt, "no kickoff -> never expires");
assert.equal(tennis.streamedSummary.streamCount, 1);
assert.equal(tennis.streamedSummary.streams[0].embed, "", "posterless detailless event still yields one stream");
ok("always_live ppv channel stays live and unexpired");

const storedBlob = kv.store.get("matches");
assert.ok(!storedBlob.includes("embedUrl"), "embedUrl must never be persisted");
ok("no embedUrl persisted in KV");

// A manual match to verify preservation + public shaping.
{
  const login = await worker.default.fetch(
    req("/api/admin/login", { method: "POST", headers: { "X-Admin-Token": "test-token" } }),
    env,
    ctx
  );
  const loginData = await login.json();
  assert.ok(loginData.session, "login issues session");

  const created = await worker.default.fetch(
    req("/api/admin/matches", {
      method: "POST",
      headers: { "X-Admin-Session": loginData.session },
      body: {
        title: "Manual Cup Final",
        category: "FOOTBALL",
        url: "/matches/manual-secret-path",
        liveWindow: 999,
        streams: [
          { id: "s1", name: "Sky Sports", language: "English", league: "", quality: "HD" },
          // http stream URLs are accepted now (the Worker proxies them) but
          // must stay just as private as https ones.
          { id: "s2", name: "IPTV", language: "English", league: "", quality: "HD", url: "http://iptv.example:8080/live/user/pass/1001.ts" }
        ]
      }
    }),
    env,
    ctx
  );
  const createdData = await created.json();
  assert.ok(createdData.success, "manual match created");
  globalThis.manualMatchId = createdData.match.id;
}
ok("admin login + manual match creation still work");

// ---------------------------------------------------------------------------
// 2. Public endpoints: enriched, sanitized, no admin fields.
// ---------------------------------------------------------------------------

console.log("# public endpoints");

const allTodayRes = await worker.default.fetch(req("/api/matches/all-today"), env, ctx);
const allToday = await allTodayRes.json();
assert.ok(Array.isArray(allToday) && allToday.length === 4);

const publicValencia = allToday.find((match) => match.id === "ppv-31164");
assert.equal(publicValencia.source, "ppv");
assert.equal(publicValencia.competition, "LaLiga");
assert.equal(publicValencia.streamed.sourceCount, 1);
assert.equal(publicValencia.streamed.streamCount, 2);
assert.equal(publicValencia.streamed.sources.length, 1);
for (const stream of publicValencia.streamed.streams) {
  // ppv streams carry their (public) frame link; it is whitelist-shaped.
  assert.deepEqual(Object.keys(stream).sort(), ["embed", "hd", "language", "source", "streamNo"]);
  assert.ok(/^https:\/\/taifood-blog\.asia\/embed\//.test(stream.embed), "embed is the whitelisted ppv frame");
}
for (const group of publicValencia.streamed.sources) {
  for (const stream of group.streams) {
    assert.deepEqual(Object.keys(stream).sort(), ["embed", "hd", "language", "source", "streamNo"]);
  }
}
ok("/api/matches/all-today carries the inline ppv summary (with public frame links)");

const publicBlob = JSON.stringify(allToday);
for (const forbidden of ["embedUrl", "liveWindow", "createdAt", "updatedAt", "streamedSummary", "manual-secret-path", "iptv.example", "user/pass"]) {
  assert.ok(!publicBlob.includes(forbidden), "public response must not contain " + forbidden);
}
assert.ok(!("url" in publicValencia));
assert.ok(!("streams" in publicValencia));
const manualPublic = allToday.find((match) => match.id === globalThis.manualMatchId);
assert.deepEqual(Object.keys(manualPublic).sort(), [
  "category", "competition", "description", "externalId", "featured", "id", "manual",
  "popular", "poster", "source", "sources", "startTime", "status", "streamed", "teams", "title"
]);
assert.equal(manualPublic.manual.streamCount, 2);
assert.deepEqual(manualPublic.manual.streams.map((stream) => [stream.id, stream.kind]), [["s2", "ts"]],
  "only playable streams are listed; an Xtream live path plays as MPEG-TS");
assert.equal(manualPublic.streamed.sourceCount, 0);
assert.equal(manualPublic.streamed.streamCount, 0);
ok("whitelist shaping: no admin fields or playback URLs in public responses");

const matchesRes = await worker.default.fetch(req("/api/matches"), env, ctx);
const matchesPublic = await matchesRes.json();
assert.ok(!JSON.stringify(matchesPublic).includes("embedUrl"));
assert.ok(matchesPublic.every((match) => match.streamed));
ok("/api/matches also enriched + sanitized");

// Featured match path.
await kv.put("site_featured_match", "ppv-31164");
const contentRes = await worker.default.fetch(req("/api/site-content"), env, ctx);
const content = await contentRes.json();
assert.equal(content.featuredMatch.id, "ppv-31164");
assert.ok(!("url" in content.featuredMatch));
assert.ok(!("streamedSummary" in content.featuredMatch));
ok("featured match sanitized in /api/site-content");

// ---------------------------------------------------------------------------
// 3. On-demand resolver endpoint with SWR semantics.
// ---------------------------------------------------------------------------

console.log("# GET /api/streamed/streams");

// Drain snapshot rebuilds queued by earlier admin POSTs so waitUntil
// assertions below only see resolver-triggered work.
await drainWaitUntil();

// Missing params -> 400.
{
  const res = await worker.default.fetch(req("/api/streamed/streams?source=only-source"), env, ctx);
  assert.equal(res.status, 400);
}
ok("missing params rejected with 400");

// Cold miss (sync no longer resolves groups): inline fetch, path-form URL
// against the upstream, cached, fresh response.
{
  const before = streamFetchCount();
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=StreamedSoccerHD&id=src-101-a"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.streamCount, 2);
  assert.ok(!data.stale);
  assert.ok(data.resolvedAt);
  assert.ok(!JSON.stringify(data).includes("embedUrl"));
  assert.equal(streamFetchCount(), before + 1, "cold miss resolves inline");
  assert.equal(waitLog.length, 0, "cold miss resolves inline");
}
ok("cold miss fetched inline and cached (path-form upstream URL)");
assert.ok(
  [...okPaths].every((u) => /\/api\/stream\/StreamedSoccerHD\/src-101-a$/.test(u)) && okPaths.size > 0,
  "resolver must call /api/stream/{source}/{id} (path form)"
);
ok("resolver uses path-form /api/stream/{source}/{id} URL");

// Fresh cache hit: no new Streamed fetch.
{
  const before = streamFetchCount();
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=StreamedSoccerHD&id=src-101-a"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.streamCount, 2);
  assert.ok(!data.stale);
  assert.ok(data.resolvedAt);
  assert.equal(streamFetchCount(), before, "fresh hit must not refetch");
}
ok("fresh cache hit served without refetch");

// Stale entry: served immediately, refreshed via waitUntil.
{
  const cacheKey = "streamed:streams:StreamedSoccerHD:src-101-a";
  const record = JSON.parse(kv.store.get(cacheKey));
  record.resolvedAt = Date.now() - 50 * 60 * 1000; // > 45m fresh window
  await kv.put(cacheKey, JSON.stringify(record));
  const oldResolvedAt = record.resolvedAt;

  const before = streamFetchCount();
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=StreamedSoccerHD&id=src-101-a"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.streamCount, 2);
  assert.equal(data.stale, true);
  assert.equal(waitLog.length, 1, "stale serve queues a background refresh");

  await drainWaitUntil();
  const refreshed = JSON.parse(kv.store.get(cacheKey));
  assert.ok(refreshed.resolvedAt > oldResolvedAt, "background refresh rewrote cache");
  assert.equal(streamFetchCount(), before + 1, "exactly one refresh fetch");
}
ok("stale served immediately + SWR background refresh");

// Hindi source group via the same machinery.
{
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=HindiSrc&id=n1"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.streamCount, 1);
  assert.equal(data.streams[0].language, "Hindi");
  assert.ok(!data.stale);
}
ok("second-group cold miss served inline");

// ppv groups resolve from the ppv detail endpoint, and the response carries
// the whitelisted frame link (a public page URL — the same class ppv's own
// catalog publishes).
{
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=ppv&id=31164"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.source, "ppv");
  assert.equal(data.id, "31164");
  assert.equal(data.streamCount, 2);
  assert.equal(data.streams[0].embed, PPV_EMBED_HOST + "laliga/2026-11-02/val-rso");
  assert.equal(data.streams[1].embed, PPV_EMBED_HOST + "laliga/2026-11-02/val-rso_540p");
  assert.ok(!JSON.stringify(data).includes("embedUrl"), "the legacy playback field stays stripped");
  assert.ok(!data.stale);
}
ok("ppv groups resolve via the ppv detail endpoint with frame links");

// ppv-hostile paths: unknown id and upstream failure surface cleanly.
{
  const missingRes = await worker.default.fetch(
    req("/api/streamed/streams?source=ppv&id=ghost"),
    env,
    ctx
  );
  const missing = await missingRes.json();
  assert.equal(missing.streamCount, 0);
  assert.ok(!missing.error);

  const failedRes = await worker.default.fetch(
    req("/api/streamed/streams?source=ppv&id=down"),
    env,
    ctx
  );
  const failed = await failedRes.json();
  assert.equal(failed.error, "ppv-503");
  assert.equal(failed.streamCount, 0);
  assert.equal(kv.store.get("streamed:streams:ppv:down"), undefined, "nothing cached on upstream failure");
}
ok("ppv 404 and 5xx mirror the streamed error contract");

// Path traversal can never reach the ppv upstream.
{
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=ppv&id=../evil"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.error, "bad-id");
  assert.equal(data.streamCount, 0);
}
ok("ppv ids restricted to the [A-Za-z0-9_/-] shape");

// ---------------------------------------------------------------------------
// 4. Repeat sync: manual streams preserved, ids stable, caches honored.
// ---------------------------------------------------------------------------

console.log("# repeat sync");
{
  const matchesBefore = JSON.parse(kv.store.get("matches"));
  await worker.default.queue(
    {
      messages: [{ body: { type: "scheduled-sync" }, ack() {}, retry() {} }]
    },
    env
  );
  const matchesAfter = JSON.parse(kv.store.get("matches"));

  assert.equal(matchesAfter.length, 4, "manual match survives re-sync");
  const manual = matchesAfter.find((match) => match.id === globalThis.manualMatchId);
  assert.equal(manual.streams.length, 2, "manual streams preserved");
  assert.equal(manual.streams[0].name, "Sky Sports");

  const valenciaAfter = matchesAfter.find((match) => match.id === "ppv-31164");
  assert.equal(valenciaAfter.streamedSummary.streamCount, 2, "inline summary stable across syncs");
  const valenciaBefore = matchesBefore.find((match) => match.id === "ppv-31164");
  assert.equal(valenciaAfter.createdAt, valenciaBefore.createdAt, "createdAt stable");
  assert.equal(valenciaAfter.updatedAt >= valenciaBefore.updatedAt, true, "updatedAt advances");
}
ok("re-sync preserves manual streams, ids and summaries");

// ---------------------------------------------------------------------------
// 5. Same-origin image proxy: feed-host images are re-served, anything else
//    is rejected. Allow-list = streamed.pk (legacy previews) + ppv hosts.
// ---------------------------------------------------------------------------

console.log("# image proxy");
{
  const imageResponse = (status, type, body) => ({
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => (name.toLowerCase() === "content-type" ? type : null) },
    body
  });
  const imageFetches = [];
  fetchHandler = async (url) => {
    imageFetches.push(String(url));
    if (String(url).indexOf("https://streamed.pk/api/images/") === 0 ||
        String(url).indexOf("https://api.ppv.st/") === 0 ||
        String(url).indexOf("https://static.ppvservices.st/") === 0) {
      return imageResponse(200, "image/webp", "webp-bytes");
    }
    throw new Error("unexpected image fetch: " + url);
  };

  const offHost = await worker.default.fetch(
    req("/api/images?url=" + encodeURIComponent("https://evil.example/poster.webp")),
    env,
    ctx
  );
  assert.equal(offHost.status, 400, "off-host image url rejected");

  const insecure = await worker.default.fetch(
    req("/api/images?url=" + encodeURIComponent("http://streamed.pk/api/images/x.webp")),
    env,
    ctx
  );
  assert.equal(insecure.status, 400, "non-https image url rejected");

  const missing = await worker.default.fetch(req("/api/images"), env, ctx);
  assert.equal(missing.status, 400, "missing url rejected");

  const closer = await worker.default.fetch(
    req("/api/images?url=" + encodeURIComponent("https://static.ppvservices.st/assets/thumb/val-thumb.webp")),
    env,
    ctx
  );
  assert.equal(closer.status, 200, "ppv static host allowed");
  assert.equal(closer.body, "webp-bytes");

  const apiPoster = await worker.default.fetch(
    req("/api/images?url=" + encodeURIComponent("https://api.ppv.st/assets/thumb/val-detail-thumb.webp")),
    env,
    ctx
  );
  assert.equal(apiPoster.status, 200, "ppv api host allowed");

  const streamedPoster = await worker.default.fetch(
    req("/api/images?url=" + encodeURIComponent("https://streamed.pk/api/images/badge/real-madrid.webp")),
    env,
    ctx
  );
  assert.equal(streamedPoster.status, 200, "legacy streamed host still allowed (old previews)");
  assert.equal(streamedPoster.headers["Content-Type"], "image/webp");
  assert.equal(streamedPoster.headers["Access-Control-Allow-Origin"], "*");
  assert.equal(streamedPoster.body, "webp-bytes");
}
ok("image proxy whitelist = streamed.pk + ppv hosts, https only");

// ---------------------------------------------------------------------------
// 8. Unknown paths: the assets layer owns the site, so a request that falls
//    through to the Worker is a real miss. It used to be answered with
//    200 "SportzfyLive API Online", which turned every 404 (crawler and icon
//    probes, typos) into a cached text/plain 200.
// ---------------------------------------------------------------------------

console.log("# unknown paths");
{
  const unknownApi = await worker.default.fetch(req("/api/does-not-exist"), env, ctx);
  assert.equal(unknownApi.status, 404, "unknown /api path is 404");
  assert.equal((await unknownApi.json()).error, "Unknown endpoint");

  const unknownPath = await worker.default.fetch(req("/favicon.ico"), env, ctx);
  assert.equal(unknownPath.status, 404, "non-asset path is not answered with 200");
}
ok("unknown paths return 404 instead of a 200 placeholder");

// ---------------------------------------------------------------------------
// 9. Admin security hardening: login throttle, timing-safe comparison, clean
//    400 on malformed bodies, and hardened headers on admin responses.
// ---------------------------------------------------------------------------

console.log("# admin hardening");

// Login rate limit: the 11th consecutive wrong token from one IP gets 429,
// and the correct token is also refused while the block is active.
{
  const login = async (token) => worker.default.fetch(
    req("/api/admin/login", {
      method: "POST",
      headers: {
        "X-Admin-Token": token,
        "cf-connecting-ip": "203.0.113.9"
      }
    }),
    env,
    ctx
  );

  for (let attempt = 0; attempt < 10; attempt++) {
    const res = await login("wrong-" + attempt);
    assert.equal(res.status, 401, "failure " + attempt + " is 401 before the limit");
  }
  const limited = await login("wrong-again");
  assert.equal(limited.status, 429, "11th failure is rate limited");
  const blocked = await login("test-token");
  assert.equal(blocked.status, 429, "even the correct token is blocked once limited");
  assert.ok(kv.store.get("login_fail:" + encodeURIComponent("203.0.113.9")));

  // A different IP is unaffected.
  const other = await worker.default.fetch(
    req("/api/admin/login", {
      method: "POST",
      headers: {
        "X-Admin-Token": "test-token",
        "cf-connecting-ip": "198.51.100.7"
      }
    }),
    env,
    ctx
  );
  assert.equal(other.status, 200, "another IP can still log in");
}
ok("admin login rate limited per IP after repeated failures");

// Correct token still logs in from an unthrottled IP and admin responses carry
// the hardened headers.
{
  const res = await worker.default.fetch(
    req("/api/admin/login", {
      method: "POST",
      headers: { "X-Admin-Token": "test-token", "cf-connecting-ip": "198.51.100.7" }
    }),
    env,
    ctx
  );
  assert.equal(res.status, 200);
  assert.equal(res.headers["X-Frame-Options"], "DENY");
  assert.equal(res.headers["Content-Security-Policy"], "frame-ancestors 'none'");
  assert.equal(res.headers["X-Content-Type-Options"], "nosniff");
}
ok("admin login response carries hardened headers");

// Malformed JSON body on admin routes: clean 400 instead of an exception 500.
{
  const session = (await (
    await worker.default.fetch(
      req("/api/admin/login", {
        method: "POST",
        headers: { "X-Admin-Token": "test-token", "cf-connecting-ip": "198.51.100.8" }
      }),
      env,
      ctx
    )
  ).json()).session;

  for (const [path, method] of [
    ["/api/admin/matches", "POST"],
    // PUT targets an existing match: resource-not-found (404) is checked before
    // body parsing, so a real id is required to reach the malformed-body branch.
    ["/api/admin/matches/" + encodeURIComponent(globalThis.manualMatchId), "PUT"],
    ["/api/admin/site-content", "PUT"]
  ]) {
    const res = await worker.default.fetch(
      req(path, {
        method,
        headers: {
          "X-Admin-Session": session,
          "Content-Type": "application/json",
          "cf-connecting-ip": "198.51.100.8"
        },
        body: undefined
      }),
      env,
      ctx
    );
    assert.equal(res.status, 400, path + " with a malformed body is a 400");
    assert.equal((await res.json()).error, "Invalid JSON body");
  }
}
ok("malformed JSON bodies on admin routes return a clean 400");

console.log("\nAll " + passed + " assertions groups passed.");
