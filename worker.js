// SportzfyLive Worker
// Streamed-only match/source architecture.
// Legacy TV-channel storage is migrated away on the first sync.

const SNAPSHOT_ALL_TODAY = "snapshot:all-today";
const SNAPSHOT_FEATURED = "snapshot:featured";
const MIGRATION_KEY = "channel_system_migrated";

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

  const finalMatches = [...manual, ...fresh];
  await env.SPORTZFY_DB.put("matches", JSON.stringify(finalMatches));
  await rebuildSnapshots(env, finalMatches);

  return {
    success: true,
    streamedCount: fresh.length,
    totalMatches: finalMatches.length,
    removed: removed.length
  };
}

async function apiHandler(request, env) {
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
    return json(await readArray(env, "matches"));
  }

  if (request.method === "GET" && url.pathname === "/api/matches/all-today") {
    const snapshot = await env.SPORTZFY_DB.get(SNAPSHOT_ALL_TODAY, "text");
    if (snapshot) return new Response(snapshot, { headers: jsonHeaders });

    await migrateLegacyChannelSystem(env);
    const built = await rebuildSnapshots(env);
    return new Response(built.allTodayJson, { headers: jsonHeaders });
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
      featuredMatch = matches.find(match => String(match.id) === String(featuredId)) || null;
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

  return new Response("SportzfyLive API Online", { headers: jsonHeaders });
}

export default {
  async fetch(request, env, ctx) {
    const response = await apiHandler(request, env);

    if (
      request.method !== "GET" &&
      request.method !== "HEAD" &&
      request.method !== "OPTIONS" &&
      response.status < 300 &&
      !new URL(request.url).pathname.startsWith("/api/admin/login")
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
      console.error("Failed to queue scheduled sync:", error);
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
