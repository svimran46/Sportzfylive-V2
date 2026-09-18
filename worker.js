export default {
  async fetch(request, env, ctx) {
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

    const autoLinkMatches = async () => {
      const [matches, channels, streamedRaw] = await Promise.all([
        getMatches(),
        getChannels(),
        fetchStreamedMatches()
      ]);

      const streamedMatches = streamedRaw.map(toStreamedMatch);
      const channelMap = new Map(channels.map(channel => [String(channel.id), channel]));
      const results = [];

      for (const match of matches) {
        const found = findStreamedMatch(match, streamedRaw);

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
        const autoChannelIds = [];

        for (const source of sourceNames) {
          for (const channel of channels) {
            if (sourceMatchesChannel(source, channel)) autoChannelIds.push(String(channel.id));
          }
        }

        const currentIds = Array.isArray(match.channelIds) ? match.channelIds.map(String) : [];
        const mergedIds = [...new Set([...currentIds, ...autoChannelIds])];

        const index = matches.findIndex(item => String(item.id) === String(match.id));
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
      const manualMatches = await expandMatches(await getMatches());
      let streamedMatches = [];

      try {
        streamedMatches = (await fetchStreamedMatches()).map(toStreamedMatch);
      } catch (_) {}

      const merged = [...manualMatches, ...streamedMatches];
      const seen = new Set();

      return json(merged.filter(match => {
        const key = String(match.externalId || match.id || "").toLowerCase();
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
      }));
    }

    if (request.method === "POST" &&
        (url.pathname === "/api/channels" || url.pathname === "/api/import")) {
      if (!await requireAdmin()) return json({ error: "Admin authentication required" }, 401);

      const data = await request.json();
      if (!Array.isArray(data)) return json({ error: "Expected an array" }, 400);

      await env.SPORTZFY_DB.put("channels", JSON.stringify(data));
      return json({ success: true, count: data.length });
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

        matches[index] = {
          ...current,
          title: String(body.title ?? current.title).trim(),
          category: String(body.category ?? current.category).trim() || "OTHER",
          startTime: body.startTime ?? current.startTime ?? "",
          status: String(body.status ?? current.status ?? "scheduled"),
          poster: String(body.poster ?? current.poster ?? ""),
          description: String(body.description ?? current.description ?? ""),
          channelIds: Array.isArray(body.channelIds)
            ? [...new Set(body.channelIds.map(String))]
            : (current.channelIds || []),
          updatedAt: new Date().toISOString()
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

    if (request.method === "GET" && url.pathname === "/api/status") {
      return json({ status: "online", worker: "sportzfylive" });
    }

    return new Response("SportzfyLive API Online", { headers: corsHeaders });
  },

  async scheduled(controller, env, ctx) {
    if (controller.cron !== "*/10 * * * *") return;

    ctx.waitUntil((async () => {
      try {
        await autoLinkMatches(env);
      } catch (error) {
        console.error("Scheduled auto-link failed:", error);
      }
    })());
  }
};

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
