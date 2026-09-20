// ---------------------------------------------------------------------------
// Shared performance helpers
// ---------------------------------------------------------------------------

// channel-source lookup: slug -> [channel ids], built once per job instead of
// re-slugifying every channel alias for every (match x source x channel).
function buildSourceIndex(channels, slugify) {
  const index = new Map();
  for (const channel of channels) {
    const id = String(channel.id);
    const keys = new Set([
      channel.streamedSource,
      ...(Array.isArray(channel.streamedSources) ? channel.streamedSources : []),
      ...(Array.isArray(channel.streamedAliases) ? channel.streamedAliases : []),
      channel.id,
      channel.name,
      channel.slug
    ].map(value => slugify(value)).filter(Boolean));

    for (const key of keys) {
      let list = index.get(key);
      if (!list) { list = []; index.set(key, list); }
      if (!list.includes(id)) list.push(id);
    }
  }
  return index;
}

function idsForSources(index, sources, slugify) {
  const ids = [];
  for (const source of sources) {
    const list = index.get(slugify(source));
    if (list) ids.push(...list);
  }
  return ids;
}

function prepareStreamedForLinking(streamedRaw, normalizeText) {
  return streamedRaw.map(raw => {
    const title = normalizeText(raw.title);
    return { raw, title, words: new Set(title.split(" ").filter(word => word.length > 2)) };
  });
}

// ---------------------------------------------------------------------------
// Public snapshots
//
// The public endpoints used to parse the full "matches" + "channels" JSON (and
// call the Streamed API) on every request. Sync jobs and admin writes now
// pre-build the response, and public GETs just return the stored string.
// ---------------------------------------------------------------------------
const SNAPSHOT_ALL_TODAY = "snapshot:all-today";
const SNAPSHOT_FEATURED = "snapshot:featured";

async function rebuildSnapshots(env, matchesIn, channelsIn) {
  const readArray = async (key) => {
    const value = await env.SPORTZFY_DB.get(key, "json");
    return Array.isArray(value) ? value : [];
  };
  const slug = (value) => String(value || "").trim().toLowerCase()
    .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");

  const matches = matchesIn || await readArray("matches");
  const channels = channelsIn || (await readArray("channels")).map(channel => ({
    ...channel,
    id: channel.id || slug(channel.name) || slug(channel.url)
  }));

  const byId = new Map(channels.map(channel => [String(channel.id), channel]));
  const seen = new Set();
  const allToday = [];

  for (const match of matches) {
    const key = String(match.externalId || match.id || "").toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    allToday.push({
      ...match,
      channels: Array.isArray(match.channelIds)
        ? match.channelIds.map(id => byId.get(String(id))).filter(Boolean)
        : []
    });
  }

  const featuredId = await env.SPORTZFY_DB.get("site_featured_match", "text");
  const featured = featuredId
    ? (matches.find(match => String(match.id) === String(featuredId)) || null)
    : null;

  const allTodayJson = JSON.stringify(allToday);
  const featuredJson = JSON.stringify({ id: featuredId || "", match: featured });

  try {
    await env.SPORTZFY_DB.put(SNAPSHOT_ALL_TODAY, allTodayJson);
  } catch (error) {
    // e.g. value larger than KV's 25 MiB limit: drop the stale snapshot so
    // public GETs fall back to building the response on demand.
    console.error("Snapshot write failed:", String(error?.message || error));
    try { await env.SPORTZFY_DB.delete(SNAPSHOT_ALL_TODAY); } catch (_) {}
  }
  const previousFeatured = await env.SPORTZFY_DB.get(SNAPSHOT_FEATURED, "text");
  if (previousFeatured !== featuredJson) {
    await env.SPORTZFY_DB.put(SNAPSHOT_FEATURED, featuredJson);
  }

  return { allTodayJson };
}

async function apiHandler(request, env, ctx) {
    const url = new URL(request.url);
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, X-Admin-Token, X-Admin-Session"
    };

    if (request.method === "OPTIONS") return new Response(null, { headers: corsHeaders });
    const json = (data, status = 200) => Response.json(data, { status, headers: corsHeaders });

    const readArray = async (key) => {
      const value = await env.SPORTZFY_DB.get(key, "json");
      return Array.isArray(value) ? value : [];
    };

    const slugify = (value) => String(value || "")
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "");

    const normalizeText = (value) => String(value || "")
      .toLowerCase()
      .normalize("NFKD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/\b(vs\.?|versus|v)\b/g, " ")
      .replace(/[^a-z0-9]+/g, " ")
      .replace(/\s+/g, " ")
      .trim();

    const getChannels = async () => {
      const channels = await readArray("channels");
      return channels.map(channel => ({
        ...channel,
        id: channel.id || slugify(channel.name) || slugify(channel.url)
      }));
    };

    const getMatches = async () => readArray("matches");

    const getClientIP = () =>
      request.headers.get("CF-Connecting-IP") ||
      request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ||
      "unknown";

    const createAdminSession = async () => {
      const supplied = request.headers.get("X-Admin-Token");
      if (!env.ADMIN_TOKEN || !supplied || supplied !== env.ADMIN_TOKEN) return null;

      const session = crypto.randomUUID();
      const expiresAt = Date.now() + 24 * 60 * 60 * 1000;

      await env.SPORTZFY_DB.put(
        "admin_session:" + session,
        JSON.stringify({ ip: getClientIP(), expiresAt }),
        { expirationTtl: 24 * 60 * 60 }
      );

      return { session, expiresAt };
    };

    const requireAdmin = async () => {
      const session = request.headers.get("X-Admin-Session");
      if (!session) return false;

      const raw = await env.SPORTZFY_DB.get("admin_session:" + session, "json");
      if (!raw || raw.expiresAt <= Date.now() || raw.ip !== getClientIP()) return false;
      return true;
    };

    const expandMatches = async (matches) => {
      const channels = await getChannels();
      const map = new Map(channels.map(channel => [String(channel.id), channel]));

      return matches.map(match => ({
        ...match,
        channels: Array.isArray(match.channelIds)
          ? match.channelIds.map(id => map.get(String(id))).filter(Boolean)
          : []
      }));
    };

    const fetchStreamedMatches = async () => {
      const upstream = await fetch("https://streamed.pk/api/matches/all-today", {
        headers: { "Accept": "application/json" }
      });

      if (!upstream.ok) {
        throw new Error("Streamed API returned " + upstream.status);
      }

      const data = await upstream.json();
      return Array.isArray(data) ? data : [];
    };

    const toStreamedMatch = (match) => ({
      id: "streamed-" + String(match.id || crypto.randomUUID()),
      externalId: String(match.id || ""),
      title: String(match.title || ""),
      category: String(match.category || "other").toUpperCase(),
      startTime: match.date ? new Date(Number(match.date)).toISOString() : "",
      status: "scheduled",
      poster: match.poster
        ? (String(match.poster).startsWith("http") ? String(match.poster) : "https://streamed.pk" + String(match.poster))
        : "",
      description: "",
      channelIds: [],
      teams: match.teams || null,
      popular: Boolean(match.popular),
      sources: Array.isArray(match.sources)
        ? match.sources.map(source => ({
            source: String(source?.source || ""),
            id: String(source?.id || "")
          })).filter(source => source.source)
        : [],
      source: "streamed"
    });

    /*
      Streamed exposes provider/source identifiers (for example alpha/bravo),
      not TV-channel names. We therefore only attach a local channel when the
      channel itself explicitly declares a matching Streamed source alias.

      Supported channel fields:
        streamedSource: "alpha"
        streamedSources: ["alpha", "bravo"]
        streamedAliases: ["alpha", "bravo"]

      This prevents the automation from guessing that a provider/source is a
      television channel.
    */
    const channelSourceAliases = (channel) => {
      const values = [
        channel.streamedSource,
        ...(Array.isArray(channel.streamedSources) ? channel.streamedSources : []),
        ...(Array.isArray(channel.streamedAliases) ? channel.streamedAliases : [])
      ];

      return [...new Set(values
        .map(value => slugify(value))
        .filter(Boolean))];
    };

    const sourceMatchesChannel = (source, channel) => {
      const wanted = slugify(source);
      if (!wanted) return false;

      const aliases = channelSourceAliases(channel);
      if (aliases.includes(wanted)) return true;

      const identityFields = [channel.id, channel.name, channel.slug]
        .map(value => slugify(value))
        .filter(Boolean);

      return identityFields.includes(wanted);
    };

    const findStreamedMatch = (manualMatch, streamedMatches) => {
      const manualTitle = normalizeText(manualMatch.title);
      if (!manualTitle) return null;

      const manualTime = manualMatch.startTime ? Date.parse(manualMatch.startTime) : NaN;
      let best = null;
      let bestScore = 0;

      const manualWords = new Set(manualTitle.split(" ").filter(word => word.length > 2));

      // streamedMatches here is the pre-normalized list from prepareStreamedForLinking()
      for (const { raw, title, words: streamedWords } of streamedMatches) {
        if (!title) continue;

        let score = 0;
        if (title === manualTitle) score += 100;
        else if (title.includes(manualTitle) || manualTitle.includes(title)) score += 70;

        const common = [...manualWords].filter(word => streamedWords.has(word)).length;
        const total = Math.max(manualWords.size, streamedWords.size, 1);
        score += (common / total) * 50;

        if (manualTime === manualTime && raw.date) {
          const diff = Math.abs(Number(raw.date) - manualTime);
          if (diff <= 15 * 60 * 1000) score += 35;
          else if (diff <= 60 * 60 * 1000) score += 15;
        }

        if (score > bestScore) {
          bestScore = score;
          best = raw;
        }
      }

      return best && bestScore >= 80 ? { match: best, score: Math.round(bestScore) } : null;
    };

    const syncStreamedMatches = async () => {
      const [existingMatches, channels, streamedRaw] = await Promise.all([
        getMatches(),
        getChannels(),
        fetchStreamedMatches()
      ]);

      const now = new Date().toISOString();
      const sourceIndex = buildSourceIndex(channels, slugify);
      const channelIdsForSources = (sources) =>
        [...new Set(idsForSources(sourceIndex, sources, slugify))];

      /*
        Streamed is the source of truth for Streamed-origin matches.

        Every sync rebuilds the Streamed portion of the stored match list from
        the current Streamed /api/matches/all-today response:
          - matches still present are updated
          - new matches are added
          - Streamed matches no longer returned are removed

        Manual matches (anything without source === "streamed") are preserved.
        For an existing Streamed match with the same externalId, we preserve
        local/admin metadata such as manually attached channelIds and broadcast
        metadata, then refresh the actual match data from Streamed.
      */
      const existingStreamedByExternalId = new Map(
        existingMatches
          .filter(item => item.source === "streamed" && item.externalId)
          .map(item => [String(item.externalId), item])
      );

      const manualMatches = existingMatches.filter(item => item.source !== "streamed");
      const freshStreamedMatches = [];
      const created = [];
      const updated = [];

      for (const raw of streamedRaw) {
        const streamed = toStreamedMatch(raw);
        const externalId = String(raw.id || "");
        const previous = externalId
          ? existingStreamedByExternalId.get(externalId)
          : null;

        const sourceNames = streamed.sources.map(source => source.source);
        const autoIds = channelIdsForSources(sourceNames);

        if (previous) {
          const currentIds = Array.isArray(previous.channelIds)
            ? previous.channelIds.map(String)
            : [];

          const mergedIds = [...new Set([
            ...currentIds,
            ...autoIds
          ])];

          const refreshed = {
            ...streamed,
            id: previous.id || streamed.id,
            channelIds: mergedIds,
            createdAt: previous.createdAt || now,
            updatedAt: now,

            // Preserve locally generated/broadcast metadata until the
            // broadcast-sync job refreshes it for this fixture.
            broadcasts: Array.isArray(previous.broadcasts)
              ? previous.broadcasts
              : undefined,
            broadcastMatchId: previous.broadcastMatchId || undefined,
            broadcastMatchScore: previous.broadcastMatchScore ?? undefined,
            broadcastStatus: previous.broadcastStatus || undefined,
            broadcastSyncedAt: previous.broadcastSyncedAt || undefined,
            autoLinkStatus: autoIds.length
              ? "linked"
              : (previous.autoLinkStatus || "matched-no-channel-map"),
            autoLinkedAt: now
          };

          if (!refreshed.broadcasts) delete refreshed.broadcasts;
          if (!refreshed.broadcastMatchId) delete refreshed.broadcastMatchId;
          if (refreshed.broadcastMatchScore === undefined) delete refreshed.broadcastMatchScore;
          if (!refreshed.broadcastStatus) delete refreshed.broadcastStatus;
          if (!refreshed.broadcastSyncedAt) delete refreshed.broadcastSyncedAt;

          freshStreamedMatches.push(refreshed);
          updated.push(refreshed.id);
        } else {
          const match = {
            ...streamed,
            channelIds: autoIds,
            createdAt: now,
            updatedAt: now,
            autoLinkStatus: autoIds.length
              ? "linked"
              : "matched-no-channel-map",
            autoLinkedAt: now
          };

          freshStreamedMatches.push(match);
          created.push(match.id);
        }
      }

      // The stored Streamed section is replaced by the fresh Streamed dataset.
      // This is what removes fixtures that disappeared from Streamed.
      const freshExternalIds = new Set(
        freshStreamedMatches.map(fresh => String(fresh.externalId || ""))
      );
      const removed = existingMatches.filter(item =>
        item.source === "streamed" &&
        !freshExternalIds.has(String(item.externalId || ""))
      );

      const finalMatches = [...manualMatches, ...freshStreamedMatches];

      await env.SPORTZFY_DB.put("matches", JSON.stringify(finalMatches));

      return {
        success: true,
        streamedCount: streamedRaw.length,
        created: created.length,
        updated: updated.length,
        removed: removed.length,
        removedMatchIds: removed.map(match => String(match.id)),
        manualMatches: manualMatches.length,
        totalMatches: finalMatches.length,
        linkedMatches: finalMatches.filter(match => match.autoLinkStatus === "linked").length
      };
    };
    const autoLinkMatches = async () => {
      const [matches, channels, streamedRaw] = await Promise.all([
        getMatches(),
        getChannels(),
        fetchStreamedMatches()
      ]);

      const streamedMatches = streamedRaw.map(toStreamedMatch);
      const channelMap = new Map(channels.map(channel => [String(channel.id), channel]));
      const sourceIndex = buildSourceIndex(channels, slugify);
      const preparedStreamed = prepareStreamedForLinking(streamedRaw, normalizeText);
      const results = [];

      for (const [matchIndex, match] of matches.entries()) {
        const found = findStreamedMatch(match, preparedStreamed);

        if (!found) {
          results.push({
            id: match.id,
            title: match.title,
            status: "no-streamed-match"
          });
          continue;
        }

        const streamed = toStreamedMatch(found.match);
        const sourceNames = streamed.sources.map(source => source.source);
        const autoChannelIds = idsForSources(sourceIndex, sourceNames, slugify);

        const currentIds = Array.isArray(match.channelIds) ? match.channelIds.map(String) : [];
        const mergedIds = [...new Set([...currentIds, ...autoChannelIds])];

        const index = matchIndex;
        if (index !== -1) {
          matches[index] = {
            ...matches[index],
            channelIds: mergedIds,
            streamedMatchId: streamed.externalId,
            streamedSources: sourceNames,
            autoLinkScore: found.score,
            autoLinkStatus: autoChannelIds.length ? "linked" : "matched-no-channel-map",
            autoLinkedAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
          };
        }

        results.push({
          id: match.id,
          title: match.title,
          streamedMatchId: streamed.externalId,
          score: found.score,
          sources: sourceNames,
          addedChannelIds: autoChannelIds
        });
      }

      await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
      return { processed: matches.length, results };
    };

    if (request.method === "POST" && url.pathname === "/api/admin/login") {
      const result = await createAdminSession();
      if (!result) return json({ error: "Invalid admin token" }, 401);
      return json(result);
    }

    if (request.method === "GET" && url.pathname === "/api/channels") {
      return json(await getChannels());
    }

    if (request.method === "GET" && url.pathname === "/api/streamed/matches") {
      try {
        return json((await fetchStreamedMatches()).map(toStreamedMatch));
      } catch (_) {
        return json({ error: "Unable to fetch Streamed matches" }, 502);
      }
    }

    if (request.method === "GET" && url.pathname === "/api/matches") {
      return json(await expandMatches(await getMatches()));
    }

    if (request.method === "GET" && url.pathname === "/api/matches/all-today") {
      const jsonHeaders = { ...corsHeaders, "Content-Type": "application/json" };
      const snapshot = await env.SPORTZFY_DB.get(SNAPSHOT_ALL_TODAY, "text");
      if (snapshot) return new Response(snapshot, { headers: jsonHeaders });

      // First request after deploy (or if the snapshot key was deleted).
      const built = await rebuildSnapshots(env);
      return new Response(built.allTodayJson, { headers: jsonHeaders });
    }

    if (request.method === "POST" &&
        (url.pathname === "/api/channels" || url.pathname === "/api/import")) {
      if (!await requireAdmin()) return json({ error: "Admin authentication required" }, 401);

      const data = await request.json();
      if (!Array.isArray(data)) return json({ error: "Expected an array" }, 400);

      await env.SPORTZFY_DB.put("channels", JSON.stringify(data));
      return json({ success: true, count: data.length });
    }

    if (url.pathname === "/api/admin/sync-streamed") {
      if (!await requireAdmin()) return json({ error: "Admin authentication required" }, 401);

      if (request.method === "POST") {
        try {
          return json(await syncStreamedMatches());
        } catch (error) {
          return json({ error: "Streamed sync failed: " + String(error?.message || error) }, 502);
        }
      }

      if (request.method === "GET") {
        try {
          const streamedRaw = await fetchStreamedMatches();
          const matches = await getMatches();
          return json({
            streamedCount: streamedRaw.length,
            storedCount: matches.length,
            lastSync: matches.reduce((latest, match) =>
              match.source === "streamed" && match.updatedAt > latest ? match.updatedAt : latest, ""
            )
          });
        } catch (error) {
          return json({ error: "Unable to check Streamed sync: " + String(error?.message || error) }, 502);
        }
      }
    }

    if (url.pathname === "/api/admin/auto-link") {
      if (!await requireAdmin()) return json({ error: "Admin authentication required" }, 401);

      if (request.method === "POST") {
        try {
          return json(await autoLinkMatches());
        } catch (error) {
          return json({ error: "Auto-link failed: " + String(error?.message || error) }, 502);
        }
      }

      if (request.method === "GET") {
        const [matches, streamedRaw] = await Promise.all([getMatches(), fetchStreamedMatches()]);
        return json({
          matches: matches.map(match => ({
            id: match.id,
            title: match.title,
            streamedMatchId: match.streamedMatchId || null,
            streamedSources: match.streamedSources || [],
            autoLinkStatus: match.autoLinkStatus || "never-run",
            autoLinkScore: match.autoLinkScore || 0
          })),
          streamedMatchCount: streamedRaw.length
        });
      }
    }

    if (url.pathname === "/api/admin/matches") {
      if (!await requireAdmin()) return json({ error: "Admin authentication required" }, 401);

      if (request.method === "GET") return json(await getMatches());

      if (request.method === "POST") {
        const body = await request.json();
        const title = String(body.title || "").trim();
        if (!title) return json({ error: "Title is required" }, 400);

        const now = new Date().toISOString();
        const match = {
          id: crypto.randomUUID(),
          title,
          category: String(body.category || "OTHER").trim() || "OTHER",
          startTime: body.startTime || "",
          status: String(body.status || "scheduled"),
          poster: String(body.poster || ""),
          description: String(body.description || ""),
          competition: String(body.competition || ""),
          home: String(body.home || ""),
          away: String(body.away || ""),
          teams: body.teams && typeof body.teams === "object"
            ? body.teams
            : { home: String(body.home || ""), away: String(body.away || "") },
          liveWindow: Number.isFinite(Number(body.liveWindow)) ? Number(body.liveWindow) : 210,
          url: String(body.url || ""),
          streams: Array.isArray(body.streams) ? body.streams : [],
          channelIds: Array.isArray(body.channelIds) ? [...new Set(body.channelIds.map(String))] : [],
          createdAt: now,
          updatedAt: now
        };

        const matches = await getMatches();
        matches.push(match);
        await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
        return json({ success: true, match }, 201);
      }
    }

    const matchPath = url.pathname.match(/^\/api\/admin\/matches\/([^/]+)$/);
    if (matchPath) {
      if (!await requireAdmin()) return json({ error: "Admin authentication required" }, 401);

      const id = decodeURIComponent(matchPath[1]);
      const matches = await getMatches();
      const index = matches.findIndex(match => String(match.id) === id);
      if (index === -1) return json({ error: "Match not found" }, 404);

      if (request.method === "PUT") {
        const body = await request.json();
        const current = matches[index];

        const updatedAt = new Date().toISOString();
        const incomingStreams = Array.isArray(body.streams) ? body.streams : current.streams;
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
          streams: Array.isArray(incomingStreams) ? incomingStreams : [],
          channelIds: Array.isArray(body.channelIds)
            ? [...new Set(body.channelIds.map(String))]
            : (current.channelIds || []),
          updatedAt
        };

        await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
        return json({ success: true, match: matches[index] });
      }

      if (request.method === "DELETE") {
        matches.splice(index, 1);
        await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
        return json({ success: true });
      }
    }

    if (url.pathname === "/api/admin/sync-broadcasts") {
      if (!await requireAdmin()) return json({ error: "Admin authentication required" }, 401);

      if (request.method === "POST") {
        try {
          return json(await syncBroadcastData(env));
        } catch (error) {
          return json({ error: "Broadcast sync failed: " + String(error?.message || error) }, 502);
        }
      }

      if (request.method === "GET") {
        return json({
          configured: Boolean(env.GOALDIR_TOKEN),
          countries: String(env.BROADCAST_COUNTRIES || "").split(",").map(x => x.trim()).filter(Boolean)
        });
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
        const snapshot = await env.SPORTZFY_DB.get(SNAPSHOT_FEATURED, "json");
        if (snapshot && String(snapshot.id) === String(featuredId)) {
          featuredMatch = snapshot.match || null;
        } else {
          const matches = await getMatches();
          featuredMatch = matches.find(m => String(m.id) === String(featuredId)) || null;
        }
      }
      return json({
        notice: notice || { enabled:false, text:"", link:"" },
        banner: banner || { enabled:false, image:"", link:"", alt:"" },
        featuredMatchId: featuredId || "",
        featuredMatch
      });
    }

    if (url.pathname === "/api/admin/site-content") {
      if (!await requireAdmin()) return json({ error: "Admin authentication required" }, 401);

      if (request.method === "GET") {
        const [notice, banner, featuredId] = await Promise.all([
          env.SPORTZFY_DB.get("site_notice", "json"),
          env.SPORTZFY_DB.get("site_banner", "json"),
          env.SPORTZFY_DB.get("site_featured_match", "text")
        ]);
        return json({
          notice: notice || { enabled:false, text:"", link:"" },
          banner: banner || { enabled:false, image:"", link:"", alt:"" },
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
        if (featuredId && !(await getMatches()).some(m => String(m.id) === featuredId)) {
          return json({ error: "Featured match not found" }, 400);
        }
        await Promise.all([
          env.SPORTZFY_DB.put("site_notice", JSON.stringify(notice)),
          env.SPORTZFY_DB.put("site_banner", JSON.stringify(banner)),
          env.SPORTZFY_DB.put("site_featured_match", featuredId)
        ]);
        return json({ success:true });
      }
    }

    if (request.method === "GET" && url.pathname === "/api/status") {
      return json({ status: "online", worker: "sportzfylive" });
    }

    return new Response("SportzfyLive API Online", { headers: corsHeaders });
}

export default {
  async fetch(request, env, ctx) {
    const response = await apiHandler(request, env, ctx);

    // Any successful admin write can change what the public endpoints return,
    // so refresh the snapshots in the background.
    const path = new URL(request.url).pathname;
    if (
      request.method !== "GET" && request.method !== "HEAD" && request.method !== "OPTIONS" &&
      response.status < 300 && path !== "/api/admin/login"
    ) {
      ctx.waitUntil(
        rebuildSnapshots(env).catch(error => console.error("Snapshot refresh failed:", error))
      );
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
      const type = message.body?.type || "scheduled-sync";

      try {
        if (type === "scheduled-sync" || type === "streamed-sync") {
          console.log("Starting streamed sync");

          const result = await syncStreamedMatches(env);

          console.log("Streamed sync completed:", JSON.stringify({
            streamedCount: result.streamedCount,
            totalMatches: result.totalMatches
          }));

          // Broadcast matching is deliberately a separate Queue invocation.
          // This keeps the CPU-heavy jobs isolated from each other.
          await env.SPORTZFY_SYNC_QUEUE.send({
            type: "broadcast-sync",
            queuedAt: Date.now()
          });

          message.ack();
          continue;
        }

        if (type === "broadcast-sync") {
          console.log("Starting broadcast sync");

          const result = await syncBroadcastData(env);

          console.log("Broadcast sync completed:", JSON.stringify({
            processed: result.processed || 0,
            remaining: result.remaining || 0,
            eventCount: result.eventCount || 0,
            broadcastCount: result.broadcastCount || 0,
            matchedMatches: result.matchedMatches || 0,
            linkedMatches: result.linkedMatches || 0,
            cycleComplete: Boolean(result.cycleComplete)
          }));

          message.ack();
          continue;
        }

        // Unknown message types should never be retried forever.
        console.warn("Ignoring unknown sync queue message:", type);
        message.ack();
      } catch (error) {
        console.error("Sync queue job failed:", type, error);
        message.retry();
      }
    }
  }
};

async function syncStreamedMatches(env) {
  const readArray = async (key) => {
    const value = await env.SPORTZFY_DB.get(key, "json");
    return Array.isArray(value) ? value : [];
  };

  const slugify = (value) => String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

  const normalizeText = (value) => String(value || "")
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\b(vs\.?|versus|v)\b/g, " ")
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  const channels = (await readArray("channels")).map(channel => ({
    ...channel,
    id: channel.id || slugify(channel.name) || slugify(channel.url)
  }));
  const matches = await readArray("matches");

  const upstream = await fetch("https://streamed.pk/api/matches/all-today", {
    headers: { "Accept": "application/json" }
  });
  if (!upstream.ok) throw new Error("Streamed API returned " + upstream.status);

  const streamedRaw = await upstream.json();
  const streamedMatches = Array.isArray(streamedRaw) ? streamedRaw : [];

  const sourceIndex = buildSourceIndex(channels, slugify);
  const now = new Date().toISOString();

  // Lookup tables so each upstream match is an O(1) lookup instead of a scan
  // (with regex/unicode normalisation) over every stored match.
  const byExternalId = new Map();       // externalId -> first index
  const byTitle = new Map();            // normalised title -> [indexes] (streamed only)
  const normalizedAt = [];              // index -> normalised title
  const indexTitle = (i, normalized) => {
    normalizedAt[i] = normalized;
    let list = byTitle.get(normalized);
    if (!list) { list = []; byTitle.set(normalized, list); }
    list.push(i);
  };

  matches.forEach((item, i) => {
    const ext = String(item.externalId || "");
    if (ext && !byExternalId.has(ext)) byExternalId.set(ext, i);
    if (item.source === "streamed") indexTitle(i, normalizeText(item.title));
  });

  const touched = new Set();

  for (const raw of streamedMatches) {
    const externalId = String(raw.id || "");
    const title = String(raw.title || "");
    const normalizedTitle = normalizeText(title);
    const rawDate = raw.date ? Number(raw.date) : NaN;

    let index = externalId && byExternalId.has(externalId) ? byExternalId.get(externalId) : -1;

    if (index === -1) {
      const candidates = byTitle.get(normalizedTitle);
      if (candidates) {
        for (const i of candidates) {
          if (normalizedAt[i] !== normalizedTitle) continue;
          const item = matches[i];
          if (item.source !== "streamed") continue;
          if (rawDate && item.startTime) {
            const existingDate = Date.parse(item.startTime);
            if (!(existingDate === existingDate &&
                  Math.abs(existingDate - rawDate) <= 60 * 60 * 1000)) continue;
          }
          if (index === -1 || i < index) index = i;
        }
      }
    }

    const sourceNames = Array.isArray(raw.sources)
      ? raw.sources.map(source => String(source?.source || "")).filter(Boolean)
      : [];

    const autoIds = [...new Set(idsForSources(sourceIndex, sourceNames, slugify))];

    const streamed = {
      id: "streamed-" + (externalId || crypto.randomUUID()),
      externalId,
      title,
      category: String(raw.category || "other").toUpperCase(),
      startTime: raw.date ? new Date(Number(raw.date)).toISOString() : "",
      status: "scheduled",
      poster: raw.poster
        ? (String(raw.poster).startsWith("http") ? String(raw.poster) : "https://streamed.pk" + String(raw.poster))
        : "",
      description: "",
      channelIds: [],
      teams: raw.teams || null,
      popular: Boolean(raw.popular),
      sources: sourceNames.map(source => {
        const original = (Array.isArray(raw.sources) ? raw.sources : []).find(item => String(item?.source || "") === source);
        return { source, id: String(original?.id || "") };
      }),
      source: "streamed"
    };

    if (index === -1) {
      index = matches.push({
        ...streamed,
        channelIds: autoIds,
        createdAt: now,
        updatedAt: now,
        autoLinkStatus: autoIds.length ? "linked" : "matched-no-channel-map",
        autoLinkedAt: now
      }) - 1;
      if (externalId && !byExternalId.has(externalId)) byExternalId.set(externalId, index);
      indexTitle(index, normalizedTitle);
    } else {
      const current = matches[index];
      const currentIds = Array.isArray(current.channelIds) ? current.channelIds.map(String) : [];
      matches[index] = {
        ...current,
        ...streamed,
        id: current.id,
        channelIds: [...new Set([...currentIds, ...autoIds])],
        createdAt: current.createdAt || now,
        updatedAt: now,
        autoLinkStatus: autoIds.length ? "linked" : (current.autoLinkStatus || "matched-no-channel-map"),
        autoLinkedAt: now
      };
      if (normalizedAt[index] !== normalizedTitle) indexTitle(index, normalizedTitle);
    }

    touched.add(index);
  }

  // Streamed is the source of truth for streamed-origin fixtures: anything not
  // returned by this run has dropped out of the feed, so remove it (same rule
  // as the admin "sync streamed" endpoint). Manual matches are never touched.
  // Guard: an empty upstream response is treated as a glitch, not "delete all".
  let finalMatches = matches;
  if (streamedMatches.length > 0) {
    finalMatches = matches.filter((item, i) => item.source !== "streamed" || touched.has(i));
  }

  await env.SPORTZFY_DB.put("matches", JSON.stringify(finalMatches));
  await rebuildSnapshots(env, finalMatches, channels);

  return {
    success: true,
    streamedCount: streamedMatches.length,
    totalMatches: finalMatches.length,
    removed: matches.length - finalMatches.length
  };
}

async function autoLinkMatches(env) {
  const readArray = async (key) => {
    const value = await env.SPORTZFY_DB.get(key, "json");
    return Array.isArray(value) ? value : [];
  };

  const slugify = (value) => String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");

  const normalizeText = (value) => String(value || "")
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\b(vs\.?|versus|v)\b/g, " ")
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  const channels = (await readArray("channels")).map(channel => ({
    ...channel,
    id: channel.id || slugify(channel.name) || slugify(channel.url)
  }));
  const matches = await readArray("matches");

  const upstream = await fetch("https://streamed.pk/api/matches/all-today", {
    headers: { "Accept": "application/json" }
  });
  if (!upstream.ok) throw new Error("Streamed API returned " + upstream.status);

  const streamedRaw = await upstream.json();
  const streamedMatches = Array.isArray(streamedRaw) ? streamedRaw : [];

  const channelSourceAliases = (channel) => [
    channel.streamedSource,
    ...(Array.isArray(channel.streamedSources) ? channel.streamedSources : []),
    ...(Array.isArray(channel.streamedAliases) ? channel.streamedAliases : [])
  ].map(value => slugify(value)).filter(Boolean);

  const sourceMatchesChannel = (source, channel) => {
    const wanted = slugify(source);
    const aliases = channelSourceAliases(channel);
    if (aliases.includes(wanted)) return true;
    return [channel.id, channel.name, channel.slug]
      .map(value => slugify(value))
      .filter(Boolean)
      .includes(wanted);
  };

  const findStreamedMatch = (manualMatch) => {
    const manualTitle = normalizeText(manualMatch.title);
    if (!manualTitle) return null;
    const manualTime = manualMatch.startTime ? Date.parse(manualMatch.startTime) : NaN;

    let best = null;
    let bestScore = 0;

    for (const raw of streamedMatches) {
      const title = normalizeText(raw.title);
      if (!title) continue;

      let score = 0;
      if (title === manualTitle) score += 100;
      else if (title.includes(manualTitle) || manualTitle.includes(title)) score += 70;

      const manualWords = new Set(manualTitle.split(" ").filter(word => word.length > 2));
      const streamedWords = new Set(title.split(" ").filter(word => word.length > 2));
      const common = [...manualWords].filter(word => streamedWords.has(word)).length;
      const total = Math.max(manualWords.size, streamedWords.size, 1);
      score += (common / total) * 50;

      if (manualTime === manualTime && raw.date) {
        const diff = Math.abs(Number(raw.date) - manualTime);
        if (diff <= 15 * 60 * 1000) score += 35;
        else if (diff <= 60 * 60 * 1000) score += 15;
      }

      if (score > bestScore) {
        bestScore = score;
        best = raw;
      }
    }

    return best && bestScore >= 80 ? { match: best, score: Math.round(bestScore) } : null;
  };

  for (const match of matches) {
    const found = findStreamedMatch(match);
    if (!found) continue;

    const sourceNames = Array.isArray(found.match.sources)
      ? found.match.sources.map(source => String(source?.source || "")).filter(Boolean)
      : [];

    const autoChannelIds = [];
    for (const source of sourceNames) {
      for (const channel of channels) {
        if (sourceMatchesChannel(source, channel)) autoChannelIds.push(String(channel.id));
      }
    }

    const currentIds = Array.isArray(match.channelIds) ? match.channelIds.map(String) : [];
    const mergedIds = [...new Set([...currentIds, ...autoChannelIds])];

    match.channelIds = mergedIds;
    match.streamedMatchId = String(found.match.id || "");
    match.streamedSources = sourceNames;
    match.autoLinkScore = found.score;
    match.autoLinkStatus = autoChannelIds.length ? "linked" : "matched-no-channel-map";
    match.autoLinkedAt = new Date().toISOString();
    match.updatedAt = new Date().toISOString();
  }

  await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
}


async function syncBroadcastData(env) {
  const readArray = async (key) => {
    const value = await env.SPORTZFY_DB.get(key, "json");
    return Array.isArray(value) ? value : [];
  };

  if (!env.GOALDIR_TOKEN) {
    return {
      success: false,
      configured: false,
      message: "GOALDIR_TOKEN is not configured",
      processed: 0,
      remaining: 0,
      matchedMatches: 0,
      linkedMatches: 0
    };
  }

  const normalizeText = (value) => String(value || "")
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/&/g, " and ")
    .replace(/\b(vs\.?|versus|v)\b/g, " ")
    .replace(/[^a-z0-9]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();

  const slugify = (value) => normalizeText(value).replace(/\s+/g, "-");

  const pickName = (value) => {
    if (typeof value === "string") return value;
    if (!value || typeof value !== "object") return "";
    return value.name || value.long_name || value.longName ||
      value.short_name || value.shortName || "";
  };

  const teamNamesFromMatch = (match) => {
    const teams = match?.teams || {};
    const home = pickName(teams.home || teams.home_team || teams.homeTeam);
    const away = pickName(teams.away || teams.away_team || teams.awayTeam);

    if (home && away) return [home, away];

    const parts = String(match.title || "")
      .split(/\s+(?:vs\.?|versus|v)\s+/i)
      .map(part => part.trim())
      .filter(Boolean);

    return parts.length >= 2
      ? [parts[0], parts.slice(1).join(" vs ")]
      : [];
  };

  const eventTeams = (event) => {
    const home = pickName(
      event?.home_team ??
      event?.home ??
      event?.homeTeam ??
      event?.home_team_name
    );
    const away = pickName(
      event?.away_team ??
      event?.away ??
      event?.awayTeam ??
      event?.away_team_name
    );
    return [home, away];
  };

  const eventTime = (event) =>
    event?.event_date ||
    event?.date ||
    event?.start_at ||
    event?.starting_at ||
    event?.kickoff ||
    event?.startTime ||
    event?.datetime ||
    event?.utcTime ||
    "";

  const dateKey = (value) => {
    const time = Date.parse(value || "");
    if (!Number.isFinite(time)) return "";
    return new Date(time).toISOString().slice(0, 10);
  };

  const responseItems = (payload) => {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload?.results)) return payload.results;
    if (Array.isArray(payload?.data)) return payload.data;
    if (Array.isArray(payload?.broadcasts)) return payload.broadcasts;
    if (Array.isArray(payload?.events)) return payload.events;
    return [];
  };

  const goalDirFetch = async (path) => {
    const response = await fetch("https://sports.bzzoiro.com/api/v2/" + path, {
      headers: {
        "Accept": "application/json",
        "Authorization": "Token " + env.GOALDIR_TOKEN
      }
    });

    if (!response.ok) {
      throw new Error("GoalDir returned " + response.status + " for " + path);
    }

    return response.json();
  };

  const matches = await readArray("matches");
  const channels = (await readArray("channels")).map(channel => ({
    ...channel,
    id: channel.id || slugify(channel.name) || slugify(channel.url)
  }));

  // Broadcast Sync can create matchable TV-source records automatically.
  // These records contain broadcaster/channel metadata only; they never
  // contain or fetch video stream URLs.
  const channelKey = (name, country, channelId) => {
    const idPart = channelId !== null && channelId !== undefined && String(channelId).trim()
      ? String(channelId).trim()
      : slugify(name);
    return "bsd:" + String(country || "global").toUpperCase() + ":" + idPart;
  };

  // Normalised aliases/countries are computed once per channel (and refreshed
  // only when that channel is modified) instead of on every comparison.
  const channelMeta = new Map();
  const buildMeta = (channel) => {
    const meta = {
      aliases: [
        channel.name,
        channel.broadcastName,
        ...(Array.isArray(channel.broadcastNames) ? channel.broadcastNames : []),
        ...(Array.isArray(channel.broadcastAliases) ? channel.broadcastAliases : [])
      ].map(normalizeText).filter(Boolean),
      countries: [
        channel.countryCode,
        channel.country_code,
        channel.regionCode,
        channel.broadcastCountry
      ].map(x => String(x || "").trim().toUpperCase()).filter(Boolean)
    };
    channelMeta.set(channel, meta);
    return meta;
  };
  for (const channel of channels) buildMeta(channel);

  const autoCreateBroadcastChannel = (broadcast) => {
    const name = String(broadcast.name || "").trim();
    const country = String(broadcast.country || "").trim().toUpperCase();
    const rawId = broadcast.channelId === null || broadcast.channelId === undefined
      ? ""
      : String(broadcast.channelId).trim();

    if (!name) return null;

    const wantedName = normalizeText(name);

    const existing = channels.find(channel => {
      if (rawId && String(channel.broadcastChannelId || "") === rawId &&
          String(channel.broadcastCountry || channel.countryCode || "").toUpperCase() === country) {
        return true;
      }

      const meta = channelMeta.get(channel);
      const sameCountry = !country || !meta.countries.length || meta.countries.includes(country);
      return sameCountry && meta.aliases.some(alias =>
        alias === wantedName ||
        alias.includes(wantedName) ||
        wantedName.includes(alias)
      );
    });

    if (existing) {
      if (rawId && !existing.broadcastChannelId) existing.broadcastChannelId = rawId;
      if (country && !existing.broadcastCountry) existing.broadcastCountry = country;
      if (!existing.broadcastSource) existing.broadcastSource = "bsd";
      if (!Array.isArray(existing.broadcastAliases)) existing.broadcastAliases = [];
      if (name && !existing.broadcastAliases.includes(name)) existing.broadcastAliases.push(name);
      buildMeta(existing);
      return { channel: existing, created: false };
    }

    const id = channelKey(name, country, rawId);
    const created = {
      id,
      name,
      logo: "",
      genre: "Sports",
      country: country || "",
      countryCode: country || "",
      broadcastName: name,
      broadcastNames: [name],
      broadcastAliases: [name],
      broadcastChannelId: rawId,
      broadcastCountry: country || "",
      broadcastSource: "bsd",
      autoGenerated: true,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    channels.push(created);
    buildMeta(created);
    return { channel: created, created: true };
  };

  const now = Date.now();
  const fromDate = new Date(now - 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const toDate = new Date(now + 48 * 60 * 60 * 1000).toISOString().slice(0, 10);

  const configuredCountries = String(env.BROADCAST_COUNTRIES || "")
    .split(",")
    .map(x => x.trim().toUpperCase())
    .filter(Boolean);

  const channelCountries = channels.flatMap(channel => [
    channel.countryCode,
    channel.country_code,
    channel.regionCode
  ])
    .map(x => String(x || "").trim().toUpperCase())
    .filter(Boolean);

  const defaultCountries = [
    "MY","SG","ID","TH","PH","IN","AU","NZ","JP","KR",
    "GB","IE","FR","DE","ES","IT","NL","PT","BE","CH",
    "AT","TR","GR","SA","AE","QA","US","CA","MX","BR",
    "AR","CL","CO"
  ];

  const countries = [...new Set([
    ...(configuredCountries.length ? configuredCountries : defaultCountries),
    ...channelCountries
  ])];

  // BSD's events endpoint supports team_name filtering. Querying by the
  // actual teams avoids the previous global 200-event ceiling and lets us
  // identify the exact fixture before requesting its broadcasts.
  const eligible = matches.filter(match => {
    const category = String(match.category || "").toUpperCase();
    if (category && !["FOOTBALL", "SOCCER"].includes(category)) return false;

    const time = Date.parse(match.startTime || "");
    return !Number.isFinite(time) ||
      (time >= now - 24 * 60 * 60 * 1000 &&
       time <= now + 48 * 60 * 60 * 1000);
  });

  // Keep each Queue invocation very small. The current Worker
  // environment has a tight CPU budget, so two fixtures per invocation
  // is safer than doing a large parallel batch.
  const batchSize = 2;
  let cursor = Number(await env.SPORTZFY_DB.get("broadcast_sync_cursor"));
  if (!Number.isFinite(cursor) || cursor < 0 || cursor >= eligible.length) cursor = 0;

  const batch = eligible.slice(cursor, cursor + batchSize);

  if (!batch.length) {
    await env.SPORTZFY_DB.put("broadcast_sync_cursor", "0");
    return {
      success: true,
      configured: true,
      source: "bsd",
      countries,
      processed: 0,
      totalEligible: eligible.length,
      remaining: 0,
      cursor: 0,
      cycleComplete: true,
      eventCount: 0,
      broadcastCount: 0,
      matchedMatches: 0,
      linkedMatches: 0,
      message: "Broadcast sync cycle complete; cursor reset."
    };
  }

  const findEvent = async (match) => {
    const [homeRaw, awayRaw] = teamNamesFromMatch(match);
    const home = normalizeText(homeRaw);
    const away = normalizeText(awayRaw);
    if (!home || !away) return null;

    const matchTime = Date.parse(match.startTime || "");
    const matchDate = dateKey(match.startTime);

    // Search each side independently. This is intentionally small: two
    // team-filtered requests per match instead of one huge global request.
    const queryTeam = async (team) => {
      const path =
        "events/?team_name=" + encodeURIComponent(team) +
        "&date_from=" + encodeURIComponent(fromDate) +
        "&date_to=" + encodeURIComponent(toDate) +
        "&limit=50";
      try {
        return responseItems(await goalDirFetch(path));
      } catch (_) {
        return [];
      }
    };

    const [homeEvents, awayEvents] = await Promise.all([
      queryTeam(homeRaw),
      queryTeam(awayRaw)
    ]);

    const candidates = new Map();
    for (const event of [...homeEvents, ...awayEvents]) {
      const id = String(event?.id ?? event?.event_id ?? "");
      if (id) candidates.set(id, event);
    }

    let best = null;
    let bestScore = 0;

    for (const event of candidates.values()) {
      const [candidateHome, candidateAway] = eventTeams(event).map(normalizeText);
      if (!candidateHome || !candidateAway) continue;

      let score = 0;

      if (candidateHome === home) score += 50;
      else if (candidateHome.includes(home) || home.includes(candidateHome)) score += 35;

      if (candidateAway === away) score += 50;
      else if (candidateAway.includes(away) || away.includes(candidateAway)) score += 35;

      const candidateDate = dateKey(eventTime(event));
      if (matchDate && candidateDate === matchDate) score += 20;

      const candidateTime = Date.parse(eventTime(event));
      if (Number.isFinite(matchTime) && Number.isFinite(candidateTime)) {
        const diff = Math.abs(matchTime - candidateTime);
        if (diff <= 10 * 60 * 1000) score += 35;
        else if (diff <= 30 * 60 * 1000) score += 25;
        else if (diff <= 90 * 60 * 1000) score += 10;
      }

      if (score > bestScore) {
        bestScore = score;
        best = event;
      }
    }

    // Both teams must match; the date/time can then resolve same-team
    // duplicates. 100 = exact home + exact away.
    return best && bestScore >= 100
      ? { event: best, score: Math.round(bestScore) }
      : null;
  };

  const channelAliases = (channel) => [
    channel.name,
    channel.broadcastName,
    ...(Array.isArray(channel.broadcastNames) ? channel.broadcastNames : []),
    ...(Array.isArray(channel.broadcastAliases) ? channel.broadcastAliases : [])
  ]
    .map(normalizeText)
    .filter(Boolean);

  const channelMatches = (broadcastName, country) => {
    const wanted = normalizeText(broadcastName);
    if (!wanted) return [];

    return channels.filter(channel => {
      const countryFields = [
        channel.countryCode,
        channel.country_code,
        channel.regionCode
      ]
        .map(x => String(x || "").trim().toUpperCase())
        .filter(Boolean);

      if (
        countryFields.length &&
        country &&
        !countryFields.includes(country)
      ) {
        return false;
      }

      return channelAliases(channel).some(alias =>
        alias === wanted ||
        alias.includes(wanted) ||
        wanted.includes(alias)
      );
    }).map(channel => String(channel.id));
  };

  let matchedMatches = 0;
  let linkedMatches = 0;
  let broadcastCount = 0;
  let eventCount = 0;
  let createdChannels = 0;
  const diagnostics = [];
  const syncedAt = new Date().toISOString();

  // Resolve the 8 event IDs first. This keeps the batch small and avoids
  // the CPU-heavy global event/broadcast comparison that caused timeouts.
  const resolved = await Promise.all(
    batch.map(async match => ({
      match,
      found: await findEvent(match)
    }))
  );

  // Then request broadcasts only for events that actually matched.
  const broadcastResults = await Promise.all(
    resolved.map(async ({ match, found }) => {
      if (!found) return { match, found: null, broadcasts: [], error: null };

      try {
        const payload = await goalDirFetch(
          "events/" + encodeURIComponent(
            String(found.event?.id ?? found.event?.event_id)
          ) + "/broadcasts/"
        );

        const rows = responseItems(payload);
        const broadcasts = rows.map(row => ({
          name: String(
            row?.channel_name ??
            row?.channelName ??
            row?.channel?.name ??
            row?.tv_channel?.name ??
            row?.broadcaster_name ??
            ""
          ).trim(),
          country: String(
            row?.country_code ??
            row?.countryCode ??
            row?.country ??
            ""
          ).trim().toUpperCase(),
          channelId: row?.channel_id ?? row?.channelId ?? null
        })).filter(row => row.name);

        return { match, found, broadcasts, error: null };
      } catch (error) {
        return {
          match,
          found,
          broadcasts: [],
          error: String(error?.message || error)
        };
      }
    })
  );

  for (const result of broadcastResults) {
    const { match, found, broadcasts, error } = result;

    if (!found) {
      match.broadcastStatus = "no-event-match";
      match.broadcastSyncedAt = syncedAt;
      diagnostics.push({
        id: match.id,
        title: match.title,
        status: "no-event-match"
      });
      continue;
    }

    eventCount++;

    if (error) {
      match.broadcastStatus = "broadcast-request-failed";
      match.broadcastError = error;
      match.broadcastMatchId = String(found.event?.id ?? found.event?.event_id ?? "");
      match.broadcastMatchScore = found.score;
      match.broadcastSyncedAt = syncedAt;
      diagnostics.push({
        id: match.id,
        title: match.title,
        status: "broadcast-request-failed",
        eventId: match.broadcastMatchId,
        score: found.score,
        error
      });
      continue;
    }

    const ensured = broadcasts.map(item => autoCreateBroadcastChannel(item)).filter(Boolean);
    const createdHere = ensured.filter(item => item.created).length;
    createdChannels += createdHere;

    const addedIds = ensured.map(item => String(item.channel.id));

    const currentIds = Array.isArray(match.channelIds)
      ? match.channelIds.map(String)
      : [];

    const mergedIds = [...new Set([
      ...currentIds,
      ...addedIds
    ])];

    if (broadcasts.length) matchedMatches++;
    if (addedIds.length) linkedMatches++;
    broadcastCount += broadcasts.length;

    match.channelIds = mergedIds;
    match.broadcasts = broadcasts.map(item => ({
      name: item.name,
      country: item.country,
      channelId: item.channelId,
      source: "bsd"
    }));
    match.broadcastMatchId = String(found.event?.id ?? found.event?.event_id ?? "");
    match.broadcastMatchScore = found.score;
    match.broadcastStatus = addedIds.length
      ? "linked"
      : (broadcasts.length
        ? "broadcast-found-no-local-channel"
        : "no-broadcast-listing");
    match.broadcastSyncedAt = syncedAt;
    delete match.broadcastError;
    match.updatedAt = syncedAt;

    diagnostics.push({
      id: match.id,
      title: match.title,
      status: match.broadcastStatus,
      eventId: match.broadcastMatchId,
      score: found.score,
      broadcasts: broadcasts.map(item => ({
        name: item.name,
        country: item.country
      })),
      addedChannelIds: [...new Set(addedIds)]
    });
  }

  const nextCursor = cursor + batch.length;
  const cycleComplete = nextCursor >= eligible.length;

  await env.SPORTZFY_DB.put("matches", JSON.stringify(matches));
  await env.SPORTZFY_DB.put("channels", JSON.stringify(channels));
  await env.SPORTZFY_DB.put(
    "broadcast_sync_cursor",
    String(cycleComplete ? 0 : nextCursor)
  );
  await rebuildSnapshots(env, matches, channels);

  return {
    success: true,
    configured: true,
    source: "bsd",
    countries,
    processed: batch.length,
    totalEligible: eligible.length,
    cursor: cycleComplete ? 0 : nextCursor,
    remaining: cycleComplete ? 0 : eligible.length - nextCursor,
    cycleComplete,
    eventCount,
    broadcastCount,
    matchedMatches,
    linkedMatches,
    createdChannels,
    totalChannels: channels.length,
    diagnostics
  };
}
