// DOM-level smoke test for index.html: extracts the inline <script> blocks,
// runs them in a stubbed DOM environment, and simulates user interactions.
// Primary regression target: the Enter/Space keyboard handler on match cards
// must open the stream-sources modal (no ReferenceError: openManualStream).

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const html = readFileSync(new URL("../index.html", import.meta.url), "utf8");

// ---------------------------------------------------------------------------
// Extract inline scripts (skip external ones like hls.js).
// ---------------------------------------------------------------------------

const scripts = [];
const scriptRe = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi;
let match;
while ((match = scriptRe.exec(html)) !== null) {
  scripts.push(match[1]);
}
assert.ok(scripts.length >= 2, "expected the main inline script blocks to exist");

// ---------------------------------------------------------------------------
// Stub DOM environment.
// ---------------------------------------------------------------------------

function makeElement(id = "") {
  return {
    id,
    dataset: {},
    textContent: "",
    innerHTML: "",
    hidden: false,
    disabled: false,
    style: { display: "", width: "", transform: "" },
    classList: {
      _set: new Set(),
      add(c) { this._set.add(c); },
      remove(c) { this._set.delete(c); },
      toggle(c) { this._set.has(c) ? this._set.delete(c) : this._set.add(c); },
      contains(c) { return this._set.has(c); }
    },
    replaceChildren() {},
    querySelector() { return null; },
    querySelectorAll() { return []; },
    addEventListener() {},
    closest() { return null; },
    children: [],
    getBoundingClientRect() { return { width: 800 }; }
  };
}

const documentStub = {
  _handlers: {},
  body: makeElement("body"),
  documentElement: makeElement("html"),
  _elements: new Map(),
  getElementById(id) {
    if (!this._elements.has(id)) this._elements.set(id, makeElement(id));
    return this._elements.get(id);
  },
  querySelector() { return null; },
  querySelectorAll() { return []; },
  addEventListener(type, handler) {
    (this._handlers[type] ||= []).push(handler);
  },
  createElement() { return makeElement(); },
  dispatch(type, event) {
    for (const handler of this._handlers[type] || []) handler(event);
  }
};

const windowStub = {
  __SPORTZFY_MATCHES: new Map(),
  addEventListener() {},
  innerWidth: 1280,
  location: { href: "https://site.test/", origin: "https://site.test", pathname: "/" }
};

const context = {
  document: documentStub,
  window: windowStub,
  navigator: { clipboard: { async writeText() {} } },
  location: windowStub.location,
  localStorage: { getItem: () => null, setItem() {}, removeItem() {} },
  setTimeout: () => 0,
  clearTimeout: () => {},
  fetch: async () => { throw new Error("network disabled in test"); },
  console,
  URL,
  Date,
  Math,
  JSON,
  Map,
  Set,
  Promise,
  Number,
  String,
  Array,
  Object,
  Boolean,
  isNaN
};
context.globalThis = context;
windowStub.document = documentStub;
vm.createContext(context);

for (const [index, script] of scripts.entries()) {
  vm.runInContext(script, context, { filename: `index.html#script-${index}` });
}

let passed = 0;
const ok = (name) => { passed += 1; console.log("  ok " + passed + " - " + name); };

// ---------------------------------------------------------------------------
// 1. Keyboard handler opens the stream modal (the openManualStream regression).
// ---------------------------------------------------------------------------

// Seed one match so openStreamSources has data to render.
windowStub.__SPORTZFY_MATCHES.set("streamed-101", {
  id: "streamed-101",
  title: "Valencia vs Real Sociedad",
  sources: [{ source: "StreamedSoccerHD", id: "src-1" }]
});

const card = makeElement("card");
card.classList.add("sfy-match-item");
card.dataset.matchId = "streamed-101";
// Match cards are found via event.target.closest(".sfy-match-item") for
// keyboard events and closest("[data-match-id]") for click events.
card.closest = (selector) =>
  selector === ".sfy-match-item" || selector === "[data-match-id]" ? card : null;

for (const key of ["Enter", " "]) {
  let prevented = false;
  documentStub.dispatch("keydown", {
    key,
    target: card,
    preventDefault() { prevented = true; }
  });

  const modal = documentStub.getElementById("stream-modal");
  const subtitle = documentStub.getElementById("stream-subtitle");
  assert.equal(prevented, true, key + " should preventDefault");
  assert.equal(modal.style.display, "flex", key + " should open the modal");
  assert.ok(
    subtitle.textContent.includes("Valencia vs Real Sociedad"),
    key + " should populate the modal subtitle"
  );
  assert.ok(
    subtitle.textContent.includes("1 Streamed source"),
    key + " should show the Streamed source count"
  );
  ok('"' + key + '" on a match card opens the stream sources modal');

  // Close for the next iteration (Escape path).
  documentStub.dispatch("keydown", { key: "Escape", target: makeElement() });
  assert.equal(documentStub.getElementById("stream-modal").style.display, "none");
}
ok("Escape closes the modal between activations");

// ---------------------------------------------------------------------------
// 2. Deep-link hash opens the right match's dialog content is out of scope
//    here (mouse path), but assert the click path still works after the fix.
// ---------------------------------------------------------------------------

documentStub.dispatch("click", { target: card });
assert.equal(
  documentStub.getElementById("stream-modal").style.display,
  "flex",
  "mouse click should still open the modal"
);
ok("mouse click path unchanged");

// ---------------------------------------------------------------------------
// 3. Static guarantee: no dangling identifier in the shipped page.
// ---------------------------------------------------------------------------

assert.ok(!html.includes("openManualStream"), "openManualStream must be gone");
ok("no references to the removed openManualStream remain");

console.log("\nAll " + passed + " DOM assertions passed.");
