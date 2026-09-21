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

const lastTwo = styleBlocks.slice(-2).join("");
assert.ok(lastTwo.includes('id="sportzfy-neon-ui"'), "neon layer is in the last two style blocks (cascade wins)");
ok("neon layer ordered last (cascade protected)");

assert.ok(lastTwo.includes('id="sportzfy-polish"'), "polish layer is in the last two style blocks");
ok("polish layer ordered last (cascade protected)");

// Pill radii: the neon promise — every control is a pill.
const neonBlock = styleBlocks.find(b => b.includes('id="sportzfy-neon-ui"'));
assert.ok(neonBlock.includes("border-radius:999px"), "neon layer defines pill radii");
for (const sel of [".sfy-chip", ".sfy-nav button", ".category-arrow", ".sfy-icon-btn", ".stream-embed-chip"]) {
  assert.ok(neonBlock.includes(sel), "neon pill rule covers " + sel);
}
ok("pill radii cover all control groups");

// Polish layer signature rules.
const polishBlock = styleBlocks.find(b => b.includes('id="sportzfy-polish"'));
assert.ok(polishBlock.includes('"DM Sans"'), "polish layer sets DM Sans typography");
assert.ok(polishBlock.includes("sfy-live-pulse"), "polish layer has the LIVE glow animation");
assert.ok(polishBlock.includes(":focus-visible"), "polish layer has keyboard focus rings");
assert.ok(polishBlock.includes("sfy-stream-chip"), "polish layer styles stream chips");
ok("polish layer complete (typography, LIVE glow, focus, chips)");

// Stream chips: conditional markup in BOTH card templates (no "0 streams" noise).
const chipCount = (html.match(/sfy-stream-chip/g) || []).length;
assert.ok(chipCount >= 5, "stream chip class used in CSS + both templates (found " + chipCount + ")");
assert.ok(!/sfy-stream-chip"[^>]*>\$\{match\.streamed\?\.streamCount \|\| 0\}/.test(html),
  "no unconditional zero-count chips");
ok("stream chips conditional in both card templates");

// Fonts: preconnect + non-blocking stylesheet.
assert.ok(html.includes('rel="preconnect" href="https://fonts.gstatic.com"'), "font preconnect present");
assert.ok(html.includes("family=DM+Sans"), "DM Sans stylesheet linked");
assert.ok(html.includes('media="print" onload='), "font loads non-blocking");
ok("DM Sans wired with preconnect + async load");

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
