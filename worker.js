export default {
  async fetch(request, env) {

    const url = new URL(request.url);


    // ==============================
    // GET CHANNELS
    // ==============================
    if (
      request.method === "GET" &&
      url.pathname === "/api/channels"
    ) {

      const channels = await env.SPORTZFY_DB.get(
        "channels",
        "json"
      );

      return Response.json(
        channels || []
      );
    }



    // ==============================
    // SAVE CHANNELS
    // ==============================
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
        success: true,
        count: data.length
      });
    }



    // ==============================
    // INITIAL CHANNEL IMPORT
    // ==============================
    if (
      request.method === "POST" &&
      url.pathname === "/api/import"
    ) {

      const channels = await request.json();


      await env.SPORTZFY_DB.put(
        "channels",
        JSON.stringify(channels)
      );


      return Response.json({
        success: true,
        imported: channels.length
      });
    }



    // ==============================
    // HEALTH CHECK
    // ==============================
    if (
      request.method === "GET" &&
      url.pathname === "/api/status"
    ) {

      return Response.json({
        status: "online",
        worker: "sportzfylive"
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
