/**
 * mds.js — native shim for the PocketWeb + pocketFS app UI.
 *
 * Implements the full MDS API surface both original dapps use, backed by the native privileged
 * bridge (window.PocketWebApp) which maps every call onto the native engine (PocketProtocol/
 * PocketKeys for maxsign/hash/convert/random/maxima; PocketStore for sql; PocketNode for
 * sendpoll/coins). Served ONLY to the trusted app origin. Lets the original pages run unchanged.
 */
var MDS = (function () {
  "use strict";

  function app() { return window.PocketWebApp; }
  var mainCallback = null;
  var seq = 0, cbs = {};

  function reg(cb) { var id = "c" + (++seq); cbs[id] = (typeof cb === "function") ? cb : function () {}; return id; }
  window.__app_resolve = function (id, result) {
    var cb = cbs[id]; if (!cb) return; delete cbs[id];
    try { cb(result); } catch (e) { try { console.log("cb error " + e); } catch (x) {} }
  };
  function jcall(fn) {
    try { if (!app()) return { status: false, error: "native bridge unavailable" }; var o = fn(); return o ? JSON.parse(o) : { status: false }; }
    catch (e) { return { status: false, error: String(e) }; }
  }

  // commands that need a real node round-trip (async); everything else is instant/local.
  var ASYNC_CMDS = { sendpoll: 1, send: 1, coins: 1, coinnotify: 1, txnpost: 1 };

  var api = {
    minidappuid: "app",
    filehost: "/",

    log: function (msg) { try { console.log(msg); } catch (e) {} },

    init: function (cb) {
      mainCallback = cb;
      function go() { if (cb) cb({ event: "inited" }); }
      if (document.readyState === "complete" || document.readyState === "interactive") setTimeout(go, 0);
      else document.addEventListener("DOMContentLoaded", go);
    },
    load: function (files, cb) { if (cb) cb({ status: true }); },   // scripts already <script>-loaded; background is native
    _emit: function (msg) { if (mainCallback) try { mainCallback(msg); } catch (e) {} },

    cmd: function (command, cb) {
      var name = (command || "").trim().split(/\s+/)[0].toLowerCase();
      if (ASYNC_CMDS[name]) {
        var id = reg(cb);
        try { app().cmdAsync(command, id); } catch (e) { window.__app_resolve(id, { status: false, error: String(e) }); }
      } else {
        var r = jcall(function () { return app().cmd(command); });
        if (cb) cb(r);
      }
    },

    sql: function (sql, cb) { var r = jcall(function () { return app().sql(sql); }); if (cb) cb(r); },

    keypair: {
      get: function (key, cb) { var r = jcall(function () { return app().keypairGet(key); }); if (cb) cb(r); },
      set: function (key, value, cb) { var r = jcall(function () { return app().keypairSet(key, value == null ? "" : String(value)); }); if (cb) cb(r); }
    },

    api: {
      call: function (dapp, data, cb) { var r = jcall(function () { return app().apiCall(dapp, data == null ? "" : String(data)); }); if (cb) cb(r); },
      reply: function (from, id, data) { /* server side — native handles inter-dapp in-process */ }
    },

    file: {
      save: function (f, d, cb) { var r = jcall(function () { return app().fileSave(f, d, false); }); if (cb) cb(r); },
      savebinary: function (f, d, cb) { var r = jcall(function () { return app().fileSave(f, d, true); }); if (cb) cb(r); },
      load: function (f, cb) { var r = jcall(function () { return app().fileLoad(f, false); }); if (cb) cb(r); },
      loadbinary: function (f, cb) { var r = jcall(function () { return app().fileLoad(f, true); }); if (cb) cb(r); },
      delete: function (f, cb) { var r = jcall(function () { return app().fileDelete(f, false); }); if (cb) cb(r); },
      deletefromweb: function (f, cb) { var r = jcall(function () { return app().fileDelete(f, true); }); if (cb) cb(r); },
      copytoweb: function (f, w, cb) { var r = jcall(function () { return app().fileCopyToWeb(f, w); }); if (cb) cb(r); },
      list: function (f, cb) { var r = jcall(function () { return app().fileList(f, false); }); if (cb) cb(r); },
      listweb: function (f, cb) { var r = jcall(function () { return app().fileList(f, true); }); if (cb) cb(r); },
      download: function (url, cb) { var id = reg(cb); try { app().fileDownload(url, id); } catch (e) { window.__app_resolve(id, { status: false }); } }
    },

    net: {
      GET: function (url, cb) { var id = reg(cb); try { app().netGet(url, id); } catch (e) { window.__app_resolve(id, { status: false }); } }
    },

    comms: { broadcast: function (msg, cb) { try { app().commsBroadcast(msg == null ? "" : String(msg)); } catch (e) {} if (cb) cb({ status: true }); } },
    notify: function (text) { try { app().notify(text == null ? "" : String(text)); } catch (e) {} },
    dapplink: function (name, cb) { var r = jcall(function () { return app().dapplink(name); }); if (cb) cb(r); return r; },

    form: {
      getParams: function (name) {
        var q = window.location.search; if (!q) return "";
        var m = new RegExp("[?&]" + name + "=([^&#]*)").exec(q);
        return m ? decodeURIComponent(m[1]) : "";
      }
    },

    util: {
      hexToBase64: function (hex) {
        var h = (hex.indexOf("0x") === 0 || hex.indexOf("0X") === 0) ? hex.substring(2) : hex;
        var bin = ""; for (var i = 0; i < h.length; i += 2) bin += String.fromCharCode(parseInt(h.substr(i, 2), 16));
        return btoa(bin);
      },
      base64ToHex: function (b64) {
        var bin = atob(b64), hex = "";
        for (var i = 0; i < bin.length; i++) { var x = bin.charCodeAt(i).toString(16).toUpperCase(); hex += x.length === 1 ? "0" + x : x; }
        return hex;
      },
      base64ToArrayBuffer: function (b64) {
        var bin = atob(b64), len = bin.length, bytes = new Uint8Array(len);
        for (var i = 0; i < len; i++) bytes[i] = bin.charCodeAt(i);
        return bytes.buffer;
      }
    },

    // native-only helpers used by the app chrome (status line / pickers / refresh)
    status: function (cb) { var r = jcall(function () { return app().status(); }); if (cb) cb(r); },
    refresh: function () { try { app().refresh(); } catch (e) {} },
    pickPublish: function () { try { app().publishPick(); } catch (e) {} },
    pickImportKey: function () { try { app().importKeyPick(); } catch (e) {} }
  };

  return api;
})();
