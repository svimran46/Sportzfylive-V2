export default {
  async fetch(request, env) {

    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    };


    // Handle CORS preflight
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: corsHeaders
      });
    }


    const url = new URL(request.url);



    // GET CHANNELS
    if (
      request.method === "GET" &&
      url.pathname === "/api/channels"
    ) {

      const channels = await env.SPORTZFY_DB.get(
        "channels",
        "json"
      );

      return Response.json(
        channels || [],
        {
          headers: corsHeaders
        }
      );
    }



    // SAVE CHANNELS
    if (
      request.method === "POST" &&
      url.pathname === "/api/channels"
    ) {

      const data = await request.json();


      await env.SPORTZFY_DB.put(
        "channels",
        JSON.stringify(data)
      );


      return Response.json(
        {
          success:true,
          count:data.length
        },
        {
          headers:corsHeaders
        }
      );
    }




    // IMPORT CHANNELS
    if (
      request.method === "POST" &&
      url.pathname === "/api/import"
    ) {

      const channels = await request.json();


      await env.SPORTZFY_DB.put(
        "channels",
        JSON.stringify(channels)
      );


      return Response.json(
        {
          success:true,
          imported:channels.length
        },
        {
          headers:corsHeaders
        }
      );
    }




    // STATUS
    if (
      request.method === "GET" &&
      url.pathname === "/api/status"
    ) {

      return Response.json(
        {
          status:"online",
          worker:"sportzfylive"
        },
        {
          headers:corsHeaders
        }
      );
    }



    return new Response(
      "SportzfyLive API Online",
      {
        headers:corsHeaders
      }
    );

  }
};
