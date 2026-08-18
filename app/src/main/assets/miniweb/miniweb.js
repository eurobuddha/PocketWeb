/**
 * miniweb.js — native PocketWeb build.
 *
 * Exposes the SAME miniweb_* functions on-chain sites already call, but routes them to the native
 * @JavascriptInterface bridge (window.PocketWebNative) instead of postMessage-to-parent. Drop-in
 * compatible: existing sites keep working unchanged. Results come back via __miniweb_resolve(id,obj).
 * (The original MDS postMessage build is preserved in the pocketWeb MiniDapp source + git history.)
 */
(function () {
  "use strict";

  var callbacks = {};
  var seq = 0;

  function register(cb) {
    var id = "cb" + (++seq);
    callbacks[id] = typeof cb === "function" ? cb : function () {};
    return id;
  }

  // Called by the native side with the result object for a pending callback.
  window.__miniweb_resolve = function (id, result) {
    var cb = callbacks[id];
    if (!cb) return;
    delete callbacks[id];
    try { cb(result); } catch (e) { /* site callback threw; ignore */ }
  };

  function bridge() { return window.PocketWebNative; }

  // Build a miniweb_* function. Supports both (data, cb) and (cb) call shapes seen across sites.
  function call(action, wrap) {
    var b = bridge();
    return function (arg, cb) {
      if (typeof arg === "function") { cb = arg; arg = ""; }
      var id = register(function (result) { if (cb) cb(wrap ? wrap(result) : result); });
      if (!b) { window.__miniweb_resolve(id, { status: false, error: "native bridge unavailable" }); return; }
      try { b.call(action, arg == null ? "" : String(arg), id); }
      catch (e) { window.__miniweb_resolve(id, { status: false, error: String(e) }); }
    };
  }

  // ---- public API (same names/signatures as the MDS miniweb.js) ----

  window.miniweb_Init = function () { /* no-op: navigation state is tracked natively */ };

  window.miniweb_GetDataURI = call("GETDATAURI", function (r) { return r && r.datauri; });
  window.miniweb_ListAll    = call("LISTALL",    function (r) { return r && r.results; });
  window.miniweb_Search     = call("SEARCH",     function (r) { return { term: r && r.term, results: r && r.results }; });
  window.miniweb_JumpToURL  = call("JUMPTOURL",  function (r) { return r; });
  window.miniweb_NetGET     = call("NET_GET",    function (r) { return r; });
  window.miniweb_MdsCmd     = call("MDS_CMD",    function (r) { return r && r.response; });

  window.miniweb_InstallDapp = function (fileUrl, name, cb) {
    var id = register(function (r) { if (cb) cb(r); });
    window.__miniweb_resolve(id, { status: false, error: "install is not supported in the native app" });
  };
})();
