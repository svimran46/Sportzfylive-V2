export default {
  async fetch(request, env) {

    const url = new URL(request.url);

    // Get channels
    if (
      request.method === "GET" &&
      url.pathname === "/api/channels"
    ) {

      const channels = await env.SPORTZFY_DB.get(
        "channels",
        "json"
      );

      return Response.json(channels || []);
    }


    // Save channels
    if (
      request.method === "POST" &&
      url.pathname === "/api/channels"
    ) {

      const data = await request.json();

      await env.SPORTZFY_DB.put(
        "channels",
        JSON.stringify(data)
      );

      return Response.json({
        success: true
      });
    }


    return new Response(
      "SportzfyLive API Online",
      {
        status: 200
      }
    );

  }
};
