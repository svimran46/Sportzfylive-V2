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
    const slugify = (value) => String(value || "").trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
    const getChannels = async () => {
      const channels = await readArray("channels");
      return channels.map(channel => ({ ...channel, id: channel.id || slugify(channel.name) || slugify(channel.url) }));
    };
    const getMatches = async () => readArray("matches");
    const getClientIP = () => request.headers.get("CF-Connecting-IP") || request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() || "unknown";

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
        const upstream = await fetch("https://streamed.pk/api/matches/all-today");
        if (!upstream.ok) return json({ error: "Streamed API returned " + upstream.status }, 502);
        const streamed = await upstream.json();
        const matches = Array.isArray(streamed) ? streamed : [];
        return json(matches.map(match => ({
          id: "streamed-" + String(match.id || crypto.randomUUID()),
          externalId: match.id || null,
          title: String(match.title || ""),
          category: String(match.category || "other").toUpperCase(),
          startTime: match.date ? new Date(Number(match.date)).toISOString() : "",
          status: "scheduled",
          poster: match.poster ? (String(match.poster).startsWith("http") ? String(match.poster) : "https://streamed.pk" + String(match.poster)) : "",
          description: "",
          channelIds: [],
          teams: match.teams || null,
          popular: Boolean(match.popular),
          source: "streamed"
        })));
      } catch (error) {
        return json({ error: "Unable to fetch Streamed matches" }, 502);
      }
    }

    if (request.method === "GET" && url.pathname === "/api/matches") {
      return json(await expandMatches(await getMatches()));
    }

    if (request.method === "GET" && url.pathname === "/api/matches/all-today") {
      const matches = await expandMatches(await getMatches());
      const now = new Date();
      return json(matches.filter(match => {
        if (!match.startTime) return true;
        const d = new Date(match.startTime);
        return Number.isNaN(d.getTime()) ||
          (d.getFullYear() === now.getFullYear() &&
           d.getMonth() === now.getMonth() &&
           d.getDate() === now.getDate());
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
          channelIds: Array.isArray(body.channelIds) ? [...new Set(body.channelIds.map(String))] : (current.channelIds || []),
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
  }
};