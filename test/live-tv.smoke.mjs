// DOM-level smoke test for live-tv.html: extracts the inline <script>, runs it
// in a stubbed DOM (same approach as dom-ux.smoke.mjs) against a mocked
// /api/tv/channels catalog, and drives the page like a visitor would.
// Targets:
//   1. Incremental grid: first batch only, "Show more" + scroll sentinel append.
//   2. Filters: country/group select, category chips, search over name,
//      category and group, empty state + reset.
//   3. Playback routing: embed → /api/play/tv/:id (sandbox per flag); every
//      other kind → player.html?src=… with &type= unless HLS.
//   4. Worker logo paths are prefixed with the API origin; http logos dropped.
//   5. #ch= deep links play channels far beyond the first batch.
// All stream data is synthetic: the page never sees upstream URLs anyway.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const html = readFileSync(new URL("../live-tv.html", import.meta.url), "utf8");
const scripts = [...html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)].map(m => m[1]);
assert.equal(scripts.length, 1, "live-tv.html has one inline script");

const PROXY_URL = "https://sportzfylive.svimranmy.workers.dev";

// ---------------------------------------------------------------------------
// Synthetic catalog shaped like GET /api/tv/channels.
// ---------------------------------------------------------------------------

const GROUPS = ["Albania", "Italy", "United Kingdom", "World", "Cricket"];
const CATEGORIES = ["Sports", "Football", "Cricket", "Motorsport"];
const KINDS = ["ts", "hls", "embed", "dash"];

function makeCatalog({ withGroups = true } = {}) {
  const channels = [];
  for (let i = 0; i < 320; i++) {
    // Contiguous blocks per group (catalog order is A–Z by group).
    const group = withGroups ? GROUPS[Math.floor(i / 64)] : "";
    channels.push({
      id: (group ? group.toLowerCase().replace(/\s+/g, "-") + "-" : "") + "ch-" + i,
      name: "Channel " + i + " HD",
      logo: i % 5 === 0 ? "/api/tv/logo/ch-" + i + "?v=abc" : (i % 5 === 1 ? "https://logos.example/" + i + ".png" : (i % 5 === 2 ? "http://203.0.113.9/" + i + ".png" : "")),
      category: CATEGORIES[i % CATEGORIES.length],
      group,
      kind: KINDS[i % KINDS.length],
      sandbox: i % 8 === 2
    });
  }
  // A grouped catalog with one ungrouped channel at the end.
  if (withGroups) channels.push({ id: "loose", name: "Loose Feed", logo: "", category: "Sports", group: "", kind: "video", sandbox: false });
  return {
    channels,
    categories: ["Sports", "Football", "Cricket", "Motorsport", "Kids"],
    groups: withGroups ? GROUPS.concat("Empty Land") : [],
    updatedAt: "2026-10-15T00:00:00.000Z"
  };
}

// ---------------------------------------------------------------------------
// Stub DOM.
// ---------------------------------------------------------------------------

const unescapeAttr = (s) => String(s || "")
  .replace(/&quot;/g, '"').replace(/&#039;/g, "'")
  .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&");

function makeClassList(initial = []) {
  const set = new Set(initial);
  return {
    _set: set,
    add(c) { set.add(c); },
    remove(c) { set.delete(c); },
    toggle(c, force) {
      const on = force === undefined ? !set.has(c) : Boolean(force);
      on ? set.add(c) : set.delete(c);
      return on;
    },
    contains(c) { return set.has(c); }
  };
}

function makeElement(id = "", tagName = "div") {
  const handlers = {};
  const el = {
    id,
    tagName,
    dataset: {},
    textContent: "",
    hidden: false,
    value: "",
    title: "",
    scrollTop: 0,
    style: {},
    classList: makeClassList(),
    children: [],
    _attrs: {},
    _html: "",
    setAttribute(name, value) { el._attrs[name] = String(value); },
    getAttribute(name) { return name in el._attrs ? el._attrs[name] : null; },
    addEventListener(type, handler) { (handlers[type] ||= []).push(handler); },
    dispatch(type, event = {}) {
      for (const handler of handlers[type] || []) handler({ target: el, preventDefault() {}, ...event });
    },
    appendChild(child) { el.children.push(child); child.parentNode = el; return child; },
    remove() {
      if (el.parentNode) el.parentNode.children = el.parentNode.children.filter(c => c !== el);
    },
    scrollIntoView() { el.scrolledIntoView = true; },
    focus() {},
    // Matches ".a, .b" / "#id" against this element only (targets in this
    // test are always the parsed element itself).
    closest(selector) {
      return selector.split(",").map(s => s.trim()).some(s =>
        s.startsWith("#") ? el.id === s.slice(1) : el.classList.contains(s.slice(1))) ? el : null;
    },
    querySelectorAll(selector) {
      if (selector === "iframe") return el.children.filter(c => c.tagName === "iframe");
      return [];
    },
    insertAdjacentHTML(position, markup) {
      assert.equal(position, "beforeend");
      el._html += markup;
      el.children.push(...parseMarkup(markup));
    }
  };
  Object.defineProperty(el, "innerHTML", {
    get() { return el._html; },
    set(value) {
      el._html = String(value);
      el.children = parseMarkup(el._html);
    }
  });
  return el;
}

// Materializes the buttons / options this page renders as child elements.
// Not a general HTML parser.
function parseMarkup(markup) {
  const out = [];
  const optionRe = /<option value="([^"]*)">([^<]*)<\/option>/g;
  let m;
  while ((m = optionRe.exec(markup)) !== null) {
    const option = makeElement("", "option");
    option.value = unescapeAttr(m[1]);
    option.textContent = unescapeAttr(m[2]);
    out.push(option);
  }
  const starts = [...markup.matchAll(/<button /g)].map(x => x.index);
  starts.forEach((start, index) => {
    const segment = markup.slice(start, starts[index + 1] ?? markup.length);
    const open = segment.slice(0, segment.indexOf(">") + 1);
    const button = makeElement((open.match(/ id="([^"]*)"/) || [])[1] || "", "button");
    const cls = (open.match(/class="([^"]*)"/) || [])[1] || "";
    for (const c of cls.split(/\s+/).filter(Boolean)) button.classList.add(c);
    const channelId = open.match(/data-channel-id="([^"]*)"/);
    if (channelId) button.dataset.channelId = unescapeAttr(channelId[1]);
    const category = open.match(/data-category="([^"]*)"/);
    if (category) button.dataset.category = unescapeAttr(category[1]);
    const pressed = open.match(/aria-pressed="([^"]*)"/);
    if (pressed) button._attrs["aria-pressed"] = pressed[1];
    const img = segment.match(/<img src="([^"]*)"/);
    button.logoSrc = img ? unescapeAttr(img[1]) : "";
    const meta = segment.match(/class="tv-(?:card|row)-cat">([^<]*)</);
    button.meta = meta ? unescapeAttr(meta[1]) : "";
    const badge = segment.match(/<span class="tv-badge"><i aria-hidden="true"><\/i>([^<]*)</);
    const badgeText = { textContent: badge ? badge[1] : "" };
    button.querySelector = (selector) => selector === ".tv-badge" ? { lastChild: badgeText } : null;
    button.badge = badgeText;
    out.push(button);
  });
  return out;
}

function boot({ catalog = makeCatalog(), hash = "", failFetch = false } = {}) {
  const elements = new Map();
  const docHandlers = {};
  const winHandlers = {};
  let observerCallback = null;
  const observed = [];

  const getElementById = (id) => {
    if (!elements.has(id)) elements.set(id, makeElement(id));
    return elements.get(id);
  };
  const allCards = () => getElementById("tv-grid").children.filter(c => c.classList.contains("tv-card"));

  const location = {
    hostname: "site.test",
    port: "",
    origin: "https://site.test",
    pathname: "/live-tv",
    search: "",
    hash
  };
  const documentStub = {
    title: "Live TV - Sportzfylive",
    head: makeElement("head"),
    getElementById,
    createElement: (tag) => makeElement("", String(tag).toLowerCase()),
    addEventListener(type, handler) { (docHandlers[type] ||= []).push(handler); },
    querySelector(selector) {
      const byId = /^\.tv-card\[data-channel-id="(.*)"\]$/.exec(selector);
      if (byId) return allCards().find(c => c.dataset.channelId === byId[1]) || null;
      if (selector === "#tv-share span") return getElementById("tv-share-label");
      return null;
    },
    querySelectorAll(selector) {
      if (selector === ".tv-card.playing") return allCards().filter(c => c.classList.contains("playing"));
      return [];
    }
  };
  const fetchCalls = [];
  const fetchStub = async (url) => {
    fetchCalls.push(url);
    if (url === PROXY_URL + "/api/tv/channels" && !failFetch) {
      return { ok: true, status: 200, json: async () => JSON.parse(JSON.stringify(catalog)) };
    }
    return { ok: false, status: 503, json: async () => ({}) };
  };
  class IntersectionObserverStub {
    constructor(callback) { observerCallback = callback; }
    observe(target) { observed.push(target); }
    unobserve() {}
    disconnect() {}
  }
  const windowStub = { addEventListener(type, handler) { (winHandlers[type] ||= []).push(handler); } };
  const context = {
    document: documentStub,
    window: windowStub,
    location,
    history: {
      replaceState(state, title, url) {
        const value = String(url);
        location.hash = value.startsWith("#") ? value : "";
      }
    },
    navigator: { clipboard: { async writeText() {} } },
    fetch: fetchStub,
    AbortController,
    IntersectionObserver: IntersectionObserverStub,
    CSS: { escape: (value) => String(value).replace(/["\\]/g, "\\$&") },
    setTimeout: () => 0,
    clearTimeout: () => {},
    console, URL, Date, Math, JSON, Map, Set, Promise, Number, String, Array, Object, Boolean, Error,
    encodeURIComponent, decodeURIComponent
  };
  context.globalThis = context;
  vm.createContext(context);
  vm.runInContext(scripts[0], context, { filename: "live-tv.html#script" });

  return {
    $: getElementById,
    cards: allCards,
    fetchCalls,
    observed,
    triggerScroll(isIntersecting = true) { observerCallback([{ isIntersecting }]); },
    click(target) { for (const h of docHandlers.click || []) h({ target, preventDefault() {} }); },
    chip(category) {
      return getElementById("tv-chips").children.find(c => c.dataset.category === category);
    },
    card(id) { return allCards().find(c => c.dataset.channelId === id); },
    frame() { return getElementById("tv-player").children.find(c => c.tagName === "iframe"); },
    hashchange(value) {
      location.hash = value;
      for (const h of winHandlers.hashchange || []) h({});
    },
    document: documentStub
  };
}

const settle = async () => { for (let i = 0; i < 5; i++) await new Promise(resolve => setImmediate(resolve)); };

let passed = 0;
const ok = (name) => { passed += 1; console.log("  ok " + passed + " - " + name); };

// ---------------------------------------------------------------------------
// 1. Incremental grid.
// ---------------------------------------------------------------------------

const page = boot();
await settle();
const catalog = makeCatalog();
const total = catalog.channels.length; // 321
assert.deepEqual(page.fetchCalls.slice(0, 1), [PROXY_URL + "/api/tv/channels"], "the catalog comes from the API origin");
assert.equal(page.cards().length, 96, "only the first batch renders");
assert.equal(page.$("tv-count").textContent, "321 channels", "the count covers the whole list, not the batch");
assert.equal(page.$("tv-more").hidden, false, "more channels remain → the Show more block is visible");
assert.ok(page.$("tv-more-btn").textContent.includes("225 left"), "the button says how many are left");
assert.equal(page.observed[0], page.$("tv-more"), "the sentinel block is observed for infinite scroll");
assert.equal(page.$("tv-sub").textContent,
  "321 live sports channels from 3 countries — streaming around the clock. Pick one to start watching.",
  "hero counts channels and countries (World and category-named groups are not countries)");

page.$("tv-more-btn").dispatch("click");
assert.equal(page.cards().length, 192, "Show more appends the next batch");
page.triggerScroll(false);
assert.equal(page.cards().length, 192, "an observer report without intersection renders nothing");
page.triggerScroll(true);
assert.equal(page.cards().length, 288, "scrolling the sentinel into view appends a batch");
page.triggerScroll(true);
assert.equal(page.cards().length, total, "the final batch renders the remainder");
assert.equal(page.$("tv-more").hidden, true, "Show more hides once everything is rendered");
page.triggerScroll(true);
assert.equal(page.cards().length, total, "no duplicate cards past the end");
assert.equal(new Set(page.cards().map(c => c.dataset.channelId)).size, total, "every card is distinct");
ok("grid renders 96 cards first; Show more and the scroll sentinel append the rest");

// ---------------------------------------------------------------------------
// 2. Filters.
// ---------------------------------------------------------------------------

const select = page.$("tv-group");
assert.equal(select.hidden, false, "the country select shows when the catalog has groups");
const options = select.children.map(o => [o.value, o.textContent]);
assert.deepEqual(options, [
  ["", "All countries"],
  ["Albania", "Albania (64)"],
  ["Italy", "Italy (64)"],
  ["United Kingdom", "United Kingdom (64)"],
  ["World", "World (64)"],
  ["Cricket", "Cricket (64)"]
], "options: All countries + each group with its count (empty groups dropped)");
assert.ok(html.includes('<label class="sr-only" for="tv-group">'), "the select has a label");
assert.ok(html.includes('id="tv-count" role="status" aria-live="polite"'), "the count is announced");

select.value = "Italy";
select.dispatch("change");
assert.equal(page.cards().length, 64, "group filter: only Italy's channels");
assert.ok(page.cards().every(c => c.dataset.channelId.startsWith("italy-")), "every card is an Italian channel");
assert.equal(page.$("tv-count").textContent, "64 of 321 channels");
assert.equal(page.$("tv-more").hidden, true, "a list under one batch has no Show more");
assert.ok(select.classList.contains("active"), "an active country filter is highlighted");

const chipLabels = page.$("tv-chips").children.map(c => c.dataset.category);
assert.deepEqual(chipLabels, ["All", "Sports", "Football", "Cricket", "Motorsport"],
  "chips: All first, then only categories that hold channels");
page.click(page.chip("Cricket"));
assert.equal(page.chip("Cricket").getAttribute("aria-pressed"), "true", "the chip shows as pressed");
assert.equal(page.cards().length, 16, "category chip narrows the group further");
assert.ok(page.cards().every(c => c.meta === "Italy · Cricket"), "cards show group · category");

select.value = "";
select.dispatch("change");
assert.equal(page.cards().length, 80, "all countries + Cricket");
page.click(page.chip("All"));
assert.equal(page.cards().length, 96, "back to everything, first batch again");

page.$("tv-search").dispatch("input", { target: { value: "united KINGDOM" } });
assert.equal(page.cards().length, 64, "search matches group names");
assert.ok(page.cards().every(c => c.dataset.channelId.startsWith("united-kingdom-")));
page.$("tv-search").dispatch("input", { target: { value: "channel 7 hd" } });
assert.deepEqual(page.cards().map(c => c.dataset.channelId), ["albania-ch-7"], "search matches names");
page.$("tv-search").dispatch("input", { target: { value: "motorsport" } });
assert.equal(page.cards().length, 80, "search matches categories");
page.$("tv-search").dispatch("input", { target: { value: "no such channel" } });
assert.equal(page.cards().length, 0, "nothing matches");
assert.ok(page.$("tv-grid").innerHTML.includes("No channels found"), "empty state");
assert.equal(page.$("tv-more").hidden, true);
const clear = page.$("tv-grid").children.find(c => c.id === "tv-clear");
assert.ok(clear, "the empty state offers a reset");
page.click(clear);
assert.equal(page.$("tv-search").value, "", "reset clears the search box");
assert.equal(page.cards().length, 96, "reset shows the whole catalog again");
assert.equal(page.$("tv-count").textContent, "321 channels");
ok("country select, category chips and search (name/category/group) filter the grid; empty state resets");

// ---------------------------------------------------------------------------
// 3. Playback routing.
// ---------------------------------------------------------------------------

const playPath = (id) => "/api/play/tv/" + encodeURIComponent(id);

page.click(page.card("albania-ch-0")); // ts, Worker logo path
let frame = page.frame();
assert.equal(frame.src, PROXY_URL + "/player.html?src=" + encodeURIComponent(playPath("albania-ch-0")) + "&type=ts",
  "MPEG-TS channels play in the player page with &type=ts");
assert.equal(frame.getAttribute("sandbox"), null, "the first-party player page is never sandboxed");
assert.equal(frame.allowFullscreen, true);
assert.equal(page.$("tv-stage").hidden, false, "the stage opens");
assert.equal(page.$("tv-now-name").textContent, "Channel 0 HD");
assert.equal(page.$("tv-now-cat").textContent, "Albania · Sports · 24/7", "now-playing line: group · category · 24/7");
assert.ok(page.$("tv-now-logo").innerHTML.includes('src="' + PROXY_URL + "/api/tv/logo/ch-0?v=abc" + '"'),
  "now-playing logo is prefixed with the API origin");
assert.ok(page.card("albania-ch-0").classList.contains("playing"), "the playing card is highlighted");
assert.equal(page.card("albania-ch-0").badge.textContent, "PLAYING");
assert.equal(page.document.title, "Channel 0 HD — Live TV");

page.click(page.card("albania-ch-1")); // hls
frame = page.frame();
assert.equal(page.$("tv-player").children.filter(c => c.tagName === "iframe").length, 1, "one player at a time");
assert.equal(frame.src, PROXY_URL + "/player.html?src=" + encodeURIComponent(playPath("albania-ch-1")),
  "HLS channels play in the player page without &type");
assert.ok(!page.card("albania-ch-0").classList.contains("playing"), "highlight moves off the old card");
assert.equal(page.card("albania-ch-0").badge.textContent, "LIVE");

page.click(page.card("albania-ch-2")); // embed, sandbox flagged (2 % 8 === 2)
frame = page.frame();
assert.equal(frame.src, PROXY_URL + playPath("albania-ch-2"), "embed channels frame /api/play directly");
assert.equal(frame.getAttribute("sandbox"), "allow-scripts allow-same-origin allow-forms allow-modals allow-presentation",
  "flagged embed channels are sandboxed");

page.click(page.card("albania-ch-6")); // embed, not flagged
frame = page.frame();
assert.equal(frame.src, PROXY_URL + playPath("albania-ch-6"));
assert.equal(frame.getAttribute("sandbox"), null, "unflagged embed channels load directly");

page.click(page.card("albania-ch-3")); // dash
assert.equal(page.frame().src, PROXY_URL + "/player.html?src=" + encodeURIComponent(playPath("albania-ch-3")) + "&type=dash");
ok("kind routing: ts/dash → player.html&type, hls → player.html, embed → /api/play (sandbox per flag)");

// ---------------------------------------------------------------------------
// 4. Logos.
// ---------------------------------------------------------------------------

assert.equal(page.card("albania-ch-0").logoSrc, PROXY_URL + "/api/tv/logo/ch-0?v=abc", "Worker logo paths get the API origin");
assert.equal(page.card("albania-ch-1").logoSrc, "https://logos.example/1.png", "https logos are used as-is");
assert.equal(page.card("albania-ch-2").logoSrc, "", "a raw http logo is never requested (mixed content)");
assert.equal(page.card("albania-ch-3").logoSrc, "", "no logo → initials");
assert.ok(page.$("tv-grid").innerHTML.includes('<span class="tv-initials">C3</span>'), "initials fallback renders");
ok("logos: /api paths prefixed with the API origin, https as-is, http and empty fall back to initials");

// ---------------------------------------------------------------------------
// 5. Up next + prev/next over the filtered list.
// ---------------------------------------------------------------------------

page.click(page.card("albania-ch-1"));
const rail = page.$("tv-side-list").children;
assert.equal(rail.length, 40, "Up next is capped at 40");
assert.ok(rail.every(r => r.dataset.channelId !== "albania-ch-1"), "never lists the playing channel");
assert.ok(rail.every(r => r.dataset.channelId.startsWith("albania-")), "same group first (Albania has 63 others)");
assert.ok(page.$("tv-side-list").innerHTML.includes(PROXY_URL + "/api/tv/logo/ch-5?v=abc"), "rail logos are prefixed too");

// A channel whose group is small: group, then same category, then the rest.
page.click(page.card("albania-ch-0"));
const looseCatalog = boot({ catalog: {
  channels: [
    { id: "a", name: "A", logo: "", category: "Football", group: "Italy", kind: "hls", sandbox: false },
    { id: "b", name: "B", logo: "", category: "News", group: "Spain", kind: "hls", sandbox: false },
    { id: "c", name: "C", logo: "", category: "Football", group: "Spain", kind: "hls", sandbox: false },
    { id: "d", name: "D", logo: "", category: "News", group: "Italy", kind: "hls", sandbox: false },
    { id: "e", name: "E", logo: "", category: "Football", group: "", kind: "hls", sandbox: false }
  ],
  categories: ["Football", "News"],
  groups: ["Italy", "Spain"]
} });
await settle();
looseCatalog.click(looseCatalog.card("a"));
assert.deepEqual(looseCatalog.$("tv-side-list").children.map(r => r.dataset.channelId), ["d", "c", "e", "b"],
  "Up next: same group, then same category, then the rest");
looseCatalog.click(looseCatalog.card("e"));
assert.equal(looseCatalog.$("tv-now-cat").textContent, "Football · 24/7", "no empty group in the now-playing line");
ok("Up next: same group → same category → rest (cap 40); now-playing line omits empty parts");

select.value = "Italy";
select.dispatch("change");
page.click(page.card("italy-ch-64"));
page.$("tv-next").dispatch("click");
assert.equal(page.$("tv-now-name").textContent, "Channel 65 HD", "next steps through the filtered list");
page.$("tv-prev").dispatch("click");
page.$("tv-prev").dispatch("click");
assert.equal(page.$("tv-now-name").textContent, "Channel 127 HD", "prev wraps to the end of the filtered list");
page.$("tv-next").dispatch("click");
assert.equal(page.$("tv-now-name").textContent, "Channel 64 HD", "next wraps to the start");
// Playing channel outside the filter: next starts at the top, prev at the end.
select.value = "World";
select.dispatch("change");
page.$("tv-next").dispatch("click");
assert.equal(page.$("tv-now-name").textContent, "Channel 192 HD");
select.value = "Albania";
select.dispatch("change");
page.$("tv-prev").dispatch("click");
assert.equal(page.$("tv-now-name").textContent, "Channel 63 HD");
ok("prev/next step through the currently filtered list and wrap");

// Closing keeps the rendered batches and only drops the highlight.
page.$("tv-more-btn").dispatch("click"); // nothing more for 64 channels
const before = page.cards().length;
page.$("tv-close").dispatch("click");
assert.equal(page.$("tv-stage").hidden, true);
assert.equal(page.cards().length, before, "closing the player does not reset the grid");
assert.ok(page.cards().every(c => !c.classList.contains("playing")), "no card stays highlighted");
assert.equal(page.frame(), undefined, "the player frame is removed");
ok("closing the player clears the highlight without re-rendering");

// ---------------------------------------------------------------------------
// 6. Deep links beyond the first batch.
// ---------------------------------------------------------------------------

const deep = boot({ hash: "#ch=" + encodeURIComponent("world-ch-251") });
await settle();
assert.equal(deep.cards().length, 96, "deep link does not force the whole list to render");
assert.equal(deep.$("tv-now-name").textContent, "Channel 251 HD", "the deep-linked channel plays");
assert.equal(deep.frame().src, PROXY_URL + "/player.html?src=" + encodeURIComponent(playPath("world-ch-251")) + "&type=dash");
deep.$("tv-more-btn").dispatch("click");
deep.triggerScroll(true);
assert.ok(deep.card("world-ch-251"), "the channel's card renders with the third batch");
assert.ok(deep.card("world-ch-251").classList.contains("playing"), "and arrives highlighted");
assert.equal(deep.card("world-ch-251").badge.textContent, "PLAYING");
assert.equal(deep.cards().filter(c => c.classList.contains("playing")).length, 1);

deep.hashchange("#ch=loose");
assert.equal(deep.$("tv-now-name").textContent, "Loose Feed", "hashchange plays another channel");
assert.equal(deep.frame().src, PROXY_URL + "/player.html?src=" + encodeURIComponent(playPath("loose")) + "&type=video");
assert.equal(deep.$("tv-now-cat").textContent, "Sports · 24/7");
deep.hashchange("#ch=%E0%A4%A"); // malformed escape: ignored, no throw
deep.hashchange("#ch=nope");
assert.equal(deep.$("tv-now-name").textContent, "Loose Feed", "unknown ids leave the player alone");
ok("#ch= deep links play channels far down the list; the card is highlighted when it renders");

// ---------------------------------------------------------------------------
// 7. Catalog without groups, and a failed load.
// ---------------------------------------------------------------------------

const flat = boot({ catalog: makeCatalog({ withGroups: false }) });
await settle();
assert.equal(flat.$("tv-group").hidden, true, "no groups → no country select");
assert.equal(flat.cards().length, 96);
assert.equal(flat.$("tv-sub").textContent,
  "320 live sports channels — streaming around the clock. Pick one to start watching.");
flat.click(flat.card("ch-0"));
assert.equal(flat.$("tv-now-cat").textContent, "Sports · 24/7");

const failed = boot({ failFetch: true });
await settle();
assert.ok(failed.$("tv-grid").innerHTML.includes("Channels could not be loaded"), "load errors show a retry");
assert.equal(failed.$("tv-more").hidden, true, "Show more stays hidden on failure");
ok("no groups hides the select; a failed load shows the retry state");

// ---------------------------------------------------------------------------
// 8. Static guarantees.
// ---------------------------------------------------------------------------

assert.ok(html.includes('if (channel.sandbox) frame.setAttribute("sandbox", SANDBOX)'));
assert.ok(!/allow-popups|allow-top-navigation/.test(html), "no pop-ups / top navigation in any frame");
assert.ok(html.includes("<title>Live TV - Sportzfylive</title>"));
assert.ok(/<meta name="description" content="[^"]*sports TV channels from around the world/.test(html), "sports copy in the meta description");
assert.ok(/<meta name="twitter:description" content="[^"]*sports TV channels/.test(html), "twitter description present");
ok("sandbox literal, no pop-up permissions, sports copy");

console.log("\nAll " + passed + " Live TV assertions passed.");
