// Static UI regression test: locks the neon + polish design layers in place.
// Guards against future edits that drop the layers, reorder them before the
// base styles (losing the cascade), or reintroduce unconditional stream chips.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

let passed = 0;
const ok = (name) => { passed += 1; console.log("  ok " + passed + " - " + name); };

// ---------------------------------------------------------------------------
// index.html: neon layer, polish layer, typography, chips
// ---------------------------------------------------------------------------

const html = readFileSync(new URL("../index.html", import.meta.url), "utf8");

const styleBlocks = [...html.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/g)].map(m => m[0]);
assert.ok(styleBlocks.length >= 2, "index.html has multiple style blocks");
ok("style blocks present (" + styleBlocks.length + ")");

// SportzfyPlay design tokens — canvas + pitch green + pill system.
const css = styleBlocks.map(b => b.replace(/<\/?style[^>]*>/g, "")).join("\n");
assert.ok(css.includes("#121212"), "canvas token #121212 present");
assert.ok(css.includes("#1ed760"), "pitch green #1ed760 present");
assert.ok(css.includes("border-radius:var(--radius-pill)"), "pill radii via --radius-pill token");
assert.ok(css.includes("prefers-reduced-motion"), "reduced-motion guard present");
ok("SportzfyPlay design tokens present (canvas, pitch, pills, motion guard)");

// Layout: SportzfyPlay top-header shell — no sidebar dashboard.
assert.ok(html.includes('class="sfy-header"'), "sticky top header present");
assert.ok(!html.includes("sfy-sidebar"), "sidebar dashboard removed");
assert.ok(html.includes("sfy-hero"), "hero section present");
assert.ok(html.includes("sfy-tiles"), "category tiles present");
assert.ok(html.includes("sfy-chips"), "category chips present");
assert.ok(html.includes("sfy-footer"), "site footer present");
ok("SportzfyPlay layout structure (header, hero, tiles, chips, footer)");

// Stream chips: conditional markup shared by both card templates (no "0 streams" noise).
const chipCount = (html.match(/sfy-stream-chip/g) || []).length;
assert.ok(chipCount >= 3, "stream chip class used in CSS + shared template variable (found " + chipCount + ")");
assert.ok(!/sfy-stream-chip"[^>]*>\$\{match\.streamed\?\.streamCount \|\| 0\}/.test(html),
  "no unconditional zero-count chips");
ok("stream chips conditional in both card templates");

// Fonts: self-hosted variable DM Sans (preloaded woff2, zero external font CSS).
assert.ok(html.includes('href="/fonts/dm-sans-latin.woff2"'), "latin woff2 is preloaded");
assert.ok(html.includes('rel="preload"'), "font preload hint present");
assert.ok(html.includes("@font-face"), "DM Sans self-hosted via @font-face");
assert.ok(!html.includes("fonts.googleapis.com"), "no external Google Fonts CSS (render-blocking removed)");
assert.ok(!html.includes("cdnjs.cloudflare.com"), "no Font Awesome CDN (icons are inline SVG)");
assert.ok(!html.includes("<i class=\"fas"), "no <i> icon tags remain (XSS-safe inline SVG only)");
ok("DM Sans self-hosted with preload; all icon fonts removed");

// CSS brace balance across all blocks.
let open = 0, close = 0;
for (const block of styleBlocks) {
  const body = block.replace(/<\/?style[^>]*>/g, "");
  open += (body.match(/{/g) || []).length;
  close += (body.match(/}/g) || []).length;
}
assert.equal(open, close, "CSS braces balanced (" + open + "/" + close + ")");
ok("CSS braces balanced");

// ---------------------------------------------------------------------------
// admin.html: neon layer flattened into the single main block
// ---------------------------------------------------------------------------

const admin = readFileSync(new URL("../admin.html", import.meta.url), "utf8");

const adminStyles = admin.match(/<style[^>]*>/g) || [];
assert.equal(adminStyles.length, 1, "admin.html has exactly one style block (no invalid nesting)");
ok("admin style structure valid (single block)");

assert.equal((admin.match(/<\/style>/g) || []).length, 1, "admin.html has exactly one style closer");
ok("admin style closers valid");

assert.ok(admin.includes("border-radius:999px"), "admin neon pills present");
assert.ok(admin.includes("DM Sans"), "admin DM Sans present");
assert.ok(admin.includes("tabular-nums"), "admin tabular numerals present");
assert.ok(admin.includes(":focus-visible"), "admin keyboard focus rings present");
assert.ok(admin.includes("family=DM+Sans"), "admin DM Sans stylesheet linked");
ok("admin polish layer complete");

console.log("\nAll " + passed + " UI polish assertions passed.");
