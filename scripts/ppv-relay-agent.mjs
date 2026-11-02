#!/usr/bin/env node
// ppv.st catalog -> Worker KV relay agent.
//
// DDoS-Guard fronts ppv.st and blocks Cloudflare Workers' egress outright
// ("IP blocked. Please stop harassing our API."), so the site's Worker cannot
// fetch the catalog from the edge. This agent runs on a machine whose own
// egress ppv.st does not block (any normal home/office line) and refreshes the
// KV record `ppv:relay-catalog` through `wrangler kv key put --remote`. The
// Worker's sync prefers that record while it is newer than 20 minutes and
// falls back to its own network paths otherwise.
//
// Schedule it every 5-10 minutes, e.g. (Windows, once):
//   schtasks /Create /SC MINUTE /MO 10 /TN "Sportzfy ppv relay" ^
//     /TR "node C:\path\to\scripts\ppv-relay-agent.mjs"
// Stop it with: schtasks /Change /TN "Sportzfy ppv relay" /DISABLE
//
// The script needs no secrets beyond the wrangler login on this machine
// (npx wrangler whoami) — it writes KV under the account that login grants.

import { spawnSync } from "node:child_process";
import { appendFileSync, mkdirSync, readFileSync, rmSync, truncateSync, writeFileSync, existsSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

const PPV_URL = "https://api.ppv.st/api/streams";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
const KV_NAMESPACE = "bd1be243a4494a369d16617100f09561"; // SPORTZFY_DB in wrangler.jsonc
const KV_KEY = "ppv:relay-catalog";
const RECORD_TTL_SECONDS = 60 * 60; // abs after one hour; freshness check is the fetchedAt stamp

const REPO_ROOT = path.resolve(import.meta.dirname, "..");
const LOG_FILE = path.join(REPO_ROOT, "logs", "ppv-relay-agent.log");
mkdirSync(path.dirname(LOG_FILE), { recursive: true });
if (existsSync(LOG_FILE) && statSync(LOG_FILE).size > 200 * 1024) {
  // Keep enough history to diagnose a stalled run, drop the rest.
  const tail = readFileSync(LOG_FILE, "utf8").split("\n").slice(-400).join("\n");
  writeFileSync(LOG_FILE, tail);
}
const log = (line) => {
  const entry = new Date().toISOString() + " " + line;
  try { appendFileSync(LOG_FILE, entry + "\n"); } catch (_) {}
  (line.startsWith("ERROR") ? console.error : console.log)(entry);
};

const now = Date.now();

let groups = null;
let dir = path.join(tmpdir(), "ppv-relay-" + now);
try {
  const res = await fetch(PPV_URL, {
    headers: { "Accept": "application/json", "User-Agent": UA }
  });
  if (!res.ok) throw new Error("ppv.st answered " + res.status);
  const data = await res.json();
  if (!data || !Array.isArray(data.streams) || !data.streams.length) {
    throw new Error("payload unusable: " + JSON.stringify(data)?.slice(0, 160));
  }
  groups = data.streams;
  const eventCount = groups.reduce((total, group) => total + (group?.streams?.length || 0), 0);
  log("fetched " + groups.length + " groups (" + eventCount + " events)");
} catch (error) {
  log("ERROR fetch failed: " + String(error?.message || error) + (error?.cause ? " / " + String(error.cause) : ""));
  process.exitCode = 1;
}

if (groups) {
  try {
    mkdirSync(dir, { recursive: true });
    const file = path.join(dir, "relay-catalog.json");
    writeFileSync(file, JSON.stringify({ fetchedAt: now, streams: groups }));

    const result = spawnSync(
      "npx",
      ["wrangler", "kv", "key", "put", KV_KEY,
        "--path", file,
        "--namespace-id", KV_NAMESPACE,
        "--remote",
        "--ttl", String(RECORD_TTL_SECONDS)],
      { shell: true, stdio: "pipe", encoding: "utf8", cwd: REPO_ROOT }
    );

    if (result.status !== 0 || result.error) {
      throw new Error("wrangler kv put failed (status " + result.status + (result.error ? ", " + String(result.error.message || result.error) : "") + "): "
        + String(result.stderr || result.stdout || "").replace(/\s+/g, " ").slice(0, 400));
    }
    log("wrote " + KV_KEY + " (" + groups.length + " groups) into KV");
  } catch (error) {
    log("ERROR store failed: " + String(error?.message || error));
    process.exitCode = 1;
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}
