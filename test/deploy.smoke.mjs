// Deploy-configuration smoke test for the Cloudflare-only hosting setup.
// Guards the things that break a deploy silently rather than a page render:
//   1. ./public is the directory the Worker publishes (wrangler.jsonc), so the
//      pages the repo ships must exist there and stay byte-identical to their
//      source copies at the repo root.
//   2. The Worker's catch-all must 404 (functional check in resolver.smoke.mjs,
//      string check here) instead of the old 200 "API Online" placeholder.
//   3. No leftover Netlify config, and the canonical origin used by the social
//      cards, robots.txt and sitemap.xml is the Worker host.
//   4. The Cloudflare deploy pieces exist: _headers (ported from netlify.toml),
//      robots.txt, sitemap.xml and the GitHub Actions workflow.

import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";

let passed = 0;
const ok = (name) => { passed += 1; console.log("  ok " + passed + " - " + name); };
const repoFile = (name) => new URL("../" + name, import.meta.url);
const read = (name) => readFileSync(repoFile(name));

const CANONICAL = "https://sportzfylive.svimranmy.workers.dev";

// ---------------------------------------------------------------------------
// 1. Published assets mirror their sources.
// ---------------------------------------------------------------------------

console.log("# published assets");

const MIRRORED = ["index.html", "admin.html", "admin.js", "og-card.png"];
for (const file of MIRRORED) {
  assert.ok(existsSync(repoFile(file)), file + " exists at the repo root");
  assert.ok(existsSync(repoFile("public/" + file)), "public/" + file + " exists");
  assert.deepEqual(
    read("public/" + file),
    read(file),
    "public/" + file + " must stay in sync with " + file
  );
  ok("public/" + file + " mirrors " + file);
}

assert.ok(existsSync(repoFile("public/fonts/dm-sans-latin.woff2")), "fonts published");
assert.ok(existsSync(repoFile("public/fonts/dm-sans-latin-ext.woff2")), "extended font published");
ok("self-hosted fonts are published");

// ---------------------------------------------------------------------------
// 2. Worker config: one host, assets from ./public.
// ---------------------------------------------------------------------------

console.log("# worker config");

const wrangler = read("wrangler.jsonc").toString("utf8");
assert.match(wrangler, /"main":\s*"worker\.js"/, "worker entrypoint configured");
assert.match(wrangler, /"directory":\s*"\.\/public"/, "assets directory is ./public");
assert.match(wrangler, /"binding":\s*"SPORTZFY_DB"/, "KV binding configured");
assert.match(wrangler, /"crons":\s*\["\*\/10 \* \* \* \*"\]/, "10-minute sync cron configured");
ok("wrangler.jsonc publishes ./public with KV, queue and cron bindings");

const worker = read("worker.js").toString("utf8");
assert.ok(
  !worker.includes("SportzfyLive API Online"),
  "worker.js no longer answers every unknown path with a 200 placeholder"
);
assert.match(worker, /Unknown endpoint/, "worker.js 404s unknown API routes");
ok("worker.js 404s unknown paths");

// ---------------------------------------------------------------------------
// 3. No Netlify leftovers; canonical origin is the Worker.
// ---------------------------------------------------------------------------

console.log("# hosting migration");

assert.ok(!existsSync(repoFile("netlify.toml")), "netlify.toml is gone");
assert.ok(!existsSync(repoFile(".wranglerignore")), "legacy .wranglerignore is gone");
ok("Netlify configuration removed");

const html = read("index.html").toString("utf8");
assert.ok(
  html.includes('<meta property="og:url" content="' + CANONICAL + '/">'),
  "og:url points at the Worker origin"
);
assert.ok(
  html.includes('<link rel="canonical" href="' + CANONICAL + '/">'),
  "canonical link points at the Worker origin"
);
assert.equal(
  (html.match(new RegExp(CANONICAL.replace(/\./g, "\\.") + "/og-card\\.png", "g")) || []).length,
  2,
  "og:image and twitter:image use the Worker-hosted card"
);
assert.ok(!/netlify/i.test(html), "index.html has no Netlify references");
assert.ok(!/netlify/i.test(read("public/index.html").toString("utf8")), "mirror has no Netlify references");
assert.ok(!/netlify/i.test(read("README.md").toString("utf8")), "README has no Netlify references");
ok("canonical origin is the Worker host, with no Netlify leftovers");

const robots = read("public/robots.txt").toString("utf8");
assert.ok(robots.includes("Sitemap: " + CANONICAL + "/sitemap.xml"), "robots.txt advertises the sitemap");
assert.match(robots, /Disallow: \/api\//, "robots.txt keeps crawlers out of the API");
ok("robots.txt published with sitemap + API disallow");

const sitemap = read("public/sitemap.xml").toString("utf8");
assert.ok(sitemap.includes("<loc>" + CANONICAL + "/</loc>"), "sitemap lists the canonical origin");
ok("sitemap.xml published for the canonical origin");

// ---------------------------------------------------------------------------
// 4. Header rules and the deploy workflow.
// ---------------------------------------------------------------------------

console.log("# deploy pipeline");

const headers = read("public/_headers").toString("utf8");
assert.match(headers, /\/fonts\/\*[\s\S]*max-age=31536000, immutable/, "font caching rule kept");
assert.match(headers, /X-Content-Type-Options: nosniff/, "nosniff rule kept");
assert.match(headers, /Referrer-Policy: strict-origin-when-cross-origin/, "referrer policy kept");
ok("_headers carries the rules netlify.toml used to set");

const workflow = read(".github/workflows/deploy.yml").toString("utf8");
assert.match(workflow, /cloudflare\/wrangler-action@v3/, "workflow deploys with wrangler-action");
assert.match(workflow, /secrets\.CLOUDFLARE_API_TOKEN/, "workflow reads the Cloudflare API token secret");
assert.match(workflow, /secrets\.CLOUDFLARE_ACCOUNT_ID/, "workflow reads the Cloudflare account id secret");
assert.match(workflow, /branches: \[main\]/, "workflow deploys on pushes to main");
assert.match(
  workflow,
  /Missing repository secret\(s\)/,
  "workflow names missing secrets instead of failing obscurely"
);
ok("GitHub Actions deploys the Worker on push to main");

// Secrets must never be committable: the workspace env files and wrangler's
// local secret file are all gitignored.
const ignore = read(".gitignore").toString("utf8");
for (const entry of [".env", ".env.local", ".dev.vars"]) {
  assert.ok(
    new RegExp("^" + entry.replace(/\./g, "\\.") + "$", "m").test(ignore),
    entry + " is gitignored"
  );
}
ok("env and wrangler secret files stay out of git");

// The README must state where secrets belong, so a future contributor does not
// reach for a committed file.
assert.match(read("README.md").toString("utf8"), /wrangler secret put ADMIN_TOKEN/, "README documents Worker secrets");
ok("README documents Worker-side secrets");

console.log("\nAll " + passed + " deploy assertions passed.");
