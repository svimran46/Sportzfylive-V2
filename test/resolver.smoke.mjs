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
    json: async () => options.body ?? {}
  };
}

let passed = 0;
function ok(name) {
  passed += 1;
  console.log("  ok " + passed + " - " + name);
}
function streamFetchCount() {
  return fetchLog.filter((url) => url.includes("/api/stream?")).length;
}

// ---------------------------------------------------------------------------
// Fixture: raw Streamed matches (same shape streamed.pk returns).
// ---------------------------------------------------------------------------

const NOW = Date.now();
const RAW_MATCHES = [
  {
    id: "101",
    title: "Valencia vs Real Sociedad",
    category: "football",
    date: String(NOW),
    poster: "/posters/101.jpg",
    popular: true,
    teams: { home: { name: "Valencia" }, away: { name: "Real Sociedad" } },
    sources: [
      { source: "StreamedSoccerHD", id: "src-101-a" },
      { source: "GoalPass", id: "src-101-b" }
    ]
  },
  {
    id: "102",
    title: "Lakers vs Celtics",
    category: "basketball",
    date: String(NOW),
    sources: [{ source: "StreamedBasket", id: "src-102-a" }]
  },
  {
    id: "103",
    title: "No sources match",
    category: "tennis",
    date: String(NOW),
    sources: []
  }
];

// Sensitized stream payloads include embedUrl fields on purpose: the test
// asserts those never reach KV or public responses.
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
    if (url.includes("/api/matches/all-today")) return jsonResponse(200, RAW_MATCHES);
    if (url.includes("/api/matches/live")) return jsonResponse(200, [{ id: "102" }]);
    if (url.includes("/api/stream?")) {
      const params = new URL(url).searchParams;
      const key = params.get("source") + ":" + params.get("id");
      if (key === "GoalPass:src-101-b") return jsonResponse(429, { error: "rate limited" });
      if (key === "StreamedBasket:src-102-a") return jsonResponse(503, { error: "down" });
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

const valencia = storedMatches.find((match) => match.id === "streamed-101");
assert.ok(valencia, "streamed-101 present");
assert.equal(valencia.streamedSummary.sourceCount, 2);
// 2 unique streams survive; the duplicate (2|Spanish|1) was deduped.
assert.equal(valencia.streamedSummary.streamCount, 2);
assert.equal(valencia.streamedSummary.streams.length, 2);
const soccerGroup = valencia.streamedSummary.sources.find(
  (group) => group.source === "StreamedSoccerHD"
);
assert.equal(soccerGroup.streamCount, 2);
assert.equal(soccerGroup.streams.length, 2);
assert.ok(!soccerGroup.stale && !soccerGroup.error);
ok("multi-source match resolved with dedupe (2 streams)");

const goalGroup = valencia.streamedSummary.sources.find(
  (group) => group.source === "GoalPass"
);
assert.equal(goalGroup.error, "streamed-429");
assert.equal(goalGroup.streamCount, 0);
ok("429 source isolated with error flag, match still built");

const lakers = storedMatches.find((match) => match.id === "streamed-102");
assert.equal(lakers.status, "live");
assert.equal(lakers.streamedSummary.sources[0].error, "streamed-503");
assert.equal(lakers.streamedSummary.streamCount, 0);
ok("5xx source degraded to 0 streams with error flag");

const tennis = storedMatches.find((match) => match.id === "streamed-103");
assert.equal(tennis.streamedSummary.sourceCount, 0);
assert.equal(tennis.streamedSummary.streamCount, 0);
ok("match without sources gets empty summary");

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
        streams: [{ id: "s1", name: "Sky Sports", language: "English", league: "", quality: "HD" }]
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

const publicValencia = allToday.find((match) => match.id === "streamed-101");
assert.equal(publicValencia.streamed.sourceCount, 2);
assert.equal(publicValencia.streamed.streamCount, 2);
assert.equal(publicValencia.streamed.sources.length, 2);
for (const stream of publicValencia.streamed.streams) {
  assert.deepEqual(Object.keys(stream).sort(), ["hd", "language", "source", "streamNo"]);
}
for (const group of publicValencia.streamed.sources) {
  for (const stream of group.streams) {
    assert.deepEqual(Object.keys(stream).sort(), ["hd", "language", "source", "streamNo"]);
  }
}
ok("/api/matches/all-today carries resolved streamed summary");

const publicBlob = JSON.stringify(allToday);
for (const forbidden of ["embedUrl", "liveWindow", "createdAt", "updatedAt", "streamedSummary", "manual-secret-path"]) {
  assert.ok(!publicBlob.includes(forbidden), "public response must not contain " + forbidden);
}
assert.ok(!("url" in publicValencia));
assert.ok(!("streams" in publicValencia));
const manualPublic = allToday.find((match) => match.id === globalThis.manualMatchId);
assert.deepEqual(Object.keys(manualPublic).sort(), [
  "category", "competition", "description", "externalId", "id", "manual",
  "popular", "poster", "source", "sources", "startTime", "status", "streamed", "teams", "title"
]);
assert.equal(manualPublic.manual.streamCount, 1);
assert.equal(manualPublic.streamed.sourceCount, 0);
assert.equal(manualPublic.streamed.streamCount, 0);
ok("whitelist shaping: no admin fields or playback URLs in public responses");

const matchesRes = await worker.default.fetch(req("/api/matches"), env, ctx);
const matchesPublic = await matchesRes.json();
assert.ok(!JSON.stringify(matchesPublic).includes("embedUrl"));
assert.ok(matchesPublic.every((match) => match.streamed));
ok("/api/matches also enriched + sanitized");

// Featured match path.
await kv.put("site_featured_match", "streamed-101");
const contentRes = await worker.default.fetch(req("/api/site-content"), env, ctx);
const content = await contentRes.json();
assert.equal(content.featuredMatch.id, "streamed-101");
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
  assert.ok(!JSON.stringify(data).includes("embedUrl"));
}
ok("fresh cache hit served without refetch, no embedUrl");

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

// Cold miss: inline fetch, cached, fresh response.
{
  const before = streamFetchCount();
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=HindiSrc&id=n1"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.streamCount, 1);
  assert.equal(data.streams[0].language, "Hindi");
  assert.ok(!data.stale);
  assert.equal(waitLog.length, 0, "cold miss resolves inline");
  assert.equal(streamFetchCount(), before + 1);
  const cached = JSON.parse(kv.store.get("streamed:streams:HindiSrc:n1"));
  assert.equal(cached.streams.length, 1);
}
ok("cold miss fetched inline and cached");

// 404 source: empty result, no error flag.
{
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=Ghost&id=none"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.streamCount, 0);
  assert.ok(!data.error);
}
ok("404 cached as empty without error flag");

// 5xx source: error surfaced, nothing cached.
{
  const res = await worker.default.fetch(
    req("/api/streamed/streams?source=StreamedBasket&id=src-102-a"),
    env,
    ctx
  );
  const data = await res.json();
  assert.equal(data.error, "streamed-503");
  assert.equal(data.streamCount, 0);
  assert.equal(kv.store.get("streamed:streams:StreamedBasket:src-102-a"), undefined);
}
ok("5xx surfaces error and writes no cache entry");

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
  assert.equal(manual.streams.length, 1, "manual streams preserved");
  assert.equal(manual.streams[0].name, "Sky Sports");

  const valenciaAfter = matchesAfter.find((match) => match.id === "streamed-101");
  assert.equal(valenciaAfter.streamedSummary.streamCount, 2, "streamed summary stable via cache");
  const valenciaBefore = matchesBefore.find((match) => match.id === "streamed-101");
  assert.equal(valenciaAfter.createdAt, valenciaBefore.createdAt, "createdAt stable");
}
ok("re-sync preserves manual streams, ids and cached summaries");

console.log("\nAll " + passed + " assertions groups passed.");
