package com.sportzfy.live.core.player

/**
 * Document-start script for the player WebView, run in every frame before
 * the page's own scripts.
 *
 * embed.st's admin player treats a `null` from `window.open` as a sandbox:
 * on the first tap it calls `window.open("about:blank", "_blank")`, and a
 * `null` result shows "please remove Sandbox from iframe tab or add
 * allow-popups" and is remembered for an hour. The WebView can answer
 * `null` even though pop-ups are trapped in `onCreateWindow`, so this wraps
 * `window.open`: a real window is passed through untouched, and a `null`
 * becomes an inert stand-in that navigates nowhere and can be closed. No ad
 * is shown either way; the stand-in only satisfies the check.
 */
object PopupShim {
    const val SCRIPT: String = """(function () {
  if (window.__sfyPopupShim) return;
  try { Object.defineProperty(window, "__sfyPopupShim", { value: true }); } catch (e) {}
  var nativeOpen = window.open;
  if (typeof nativeOpen !== "function") return;
  function noop() {}
  function standIn() {
    var win = {
      closed: false,
      opener: window,
      name: "",
      location: { href: "about:blank", assign: noop, replace: noop, reload: noop, toString: function () { return "about:blank"; } },
      document: { write: noop, writeln: noop, open: noop, close: noop, body: null },
      focus: noop,
      blur: noop,
      postMessage: noop,
      addEventListener: noop,
      removeEventListener: noop,
      close: function () { win.closed = true; }
    };
    return win;
  }
  var open = function open() {
    var win = null;
    try { win = nativeOpen.apply(window, arguments); } catch (e) {}
    return win || standIn();
  };
  try { Object.defineProperty(open, "toString", { value: function () { return "function open() { [native code] }"; } }); } catch (e) {}
  window.open = open;
})();"""

    /** Every origin: the check runs inside embed.st's nested cross-domain player frames. */
    val ALLOWED_ORIGINS: Set<String> = setOf("*")
}
