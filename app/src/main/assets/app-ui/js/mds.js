/**
 * mds.js — native shim for the PocketWeb app UI.
 *
 * The app's own screens are your real pocketWeb/pocketFS HTML, which call the MDS API. This shim
 * implements just enough of that API on top of the native privileged bridge (window.PocketWebApp)
 * and the WebView's own localStorage, so those pages run unchanged. It is served ONLY to the
 * trusted app origin (app.pocketweb.local); untrusted sites get the separate, locked-down bridge.
 */
var MDS = (function () {
  "use strict";

  function app() { return window.PocketWebApp; }
  var mainCallback = null;

  var api = {
    minidappuid: "app",           // pages append this to same-origin nav links; value is irrelevant natively
    filehost: "/",

    log: function (msg) { try { console.log(msg); } catch (e) {} },

    init: function (cb) {
      mainCallback = cb;
      // fire 'inited' once the DOM + bridge are ready
      function go() { if (cb) cb({ event: "inited" }); }
      if (document.readyState === "complete" || document.readyState === "interactive") setTimeout(go, 0);
      else document.addEventListener("DOMContentLoaded", go);
    },

    // Native runtime pushes events (e.g. MDSCOMMS notifications) via this.
    _emit: function (msg) { if (mainCallback) try { mainCallback(msg); } catch (e) {} },

    cmd: function (command, cb) {
      var r = call(function () { return app().cmd(command); });
      if (cb) cb(r);
    },

    sql: function (sql, cb) {
      var r = call(function () { return app().sql(sql); });
      if (cb) cb(r);
    },

    keypair: {
      get: function (key, cb) {
        var v = null;
        try { v = localStorage.getItem(key); } catch (e) {}
        if (cb) cb({ status: v !== null, value: v });
      },
      set: function (key, value, cb) {
        try { localStorage.setItem(key, value); } catch (e) {}
        if (cb) cb({ status: true });
      }
    },

    api: {
      call: function (dapp, jsondata, cb) {
        var r = call(function () { return app().apiCall(dapp, jsondata == null ? "" : String(jsondata)); });
        if (cb) cb(r);
      }
    },

    file: {
      // App UI rarely needs files; provide the reads it might use.
      loadbinary: function (path, cb) {
        var r = call(function () { return app().loadBinary(path); });
        if (cb) cb(r);
      }
    },

    form: {
      getParams: function (name) {
        var q = window.location.search;
        if (!q) return "";
        var m = new RegExp("[?&]" + name + "=([^&#]*)").exec(q);
        return m ? decodeURIComponent(m[1]) : "";
      }
    },

    util: {
      hexToBase64: function (hex) {
        var h = hex.startsWith("0x") || hex.startsWith("0X") ? hex.substring(2) : hex;
        var bytes = [];
        for (var i = 0; i < h.length; i += 2) bytes.push(parseInt(h.substr(i, 2), 16));
        var bin = "";
        for (var j = 0; j < bytes.length; j++) bin += String.fromCharCode(bytes[j]);
        return btoa(bin);
      },
      base64ToHex: function (b64) {
        var bin = atob(b64), hex = "";
        for (var i = 0; i < bin.length; i++) {
          var x = bin.charCodeAt(i).toString(16).toUpperCase();
          hex += x.length === 1 ? "0" + x : x;
        }
        return hex;
      }
    }
  };

  function call(fn) {
    try {
      if (!app()) return { status: false, error: "native bridge unavailable" };
      var out = fn();
      return out ? JSON.parse(out) : { status: false };
    } catch (e) {
      return { status: false, error: String(e) };
    }
  }

  return api;
})();
