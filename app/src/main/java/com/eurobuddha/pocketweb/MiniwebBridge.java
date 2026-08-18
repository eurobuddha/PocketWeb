package com.eurobuddha.pocketweb;

import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Native replacement for the miniweb.js ↔ browse.html postMessage bridge. The shipped
 * assets/miniweb/miniweb.js exposes the same miniweb_* functions sites already call, and routes
 * them here through a single @JavascriptInterface entry point; results are delivered back into the
 * page by resolving the JS-side callback by id.
 *
 * Security posture (fixes the two browse.html findings):
 *  - MDS_CMD runs a strict read-only allowlist that EXCLUDES wallet/identity commands
 *    (balance, coins, history, keys, getaddress, maxima) — bug #2 — so a hostile site cannot
 *    enumerate the user's wallet or identity.
 *  - NET_GET is gated by HostGuard (host-resolved, blocks loopback/link-local/site-local incl.
 *    169.254.169.254 and 172.16/12) — bug #1 — https-only, with a body cap and timeouts.
 *  - INSTALL_DAPP is refused (the native node has no MDS to install into).
 */
public class MiniwebBridge {

    public interface Navigator { void jumpTo(String mx, String path); }

    // Read-only, non-sensitive commands only. Deliberately NO balance/coins/history/keys/getaddress/maxima.
    private static final Set<String> SAFE_COMMANDS = new HashSet<>(Arrays.asList(
            "block", "status", "tokenvalidate", "random", "convert", "hash", "coincheck"));

    private static final int NET_MAX_BYTES = 256 * 1024;
    private static final int NET_TIMEOUT_MS = 10000;

    private final WebView web;
    private final PocketStore store;
    private final NodeApi node;
    private final Navigator navigator;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public MiniwebBridge(WebView web, PocketStore store, NodeApi node, Navigator navigator) {
        this.web = web; this.store = store; this.node = node; this.navigator = navigator;
    }

    /** Single entry point: window.PocketWebNative.call(action, dataJson, callbackId). */
    @JavascriptInterface
    public void call(String action, String data, String id) {
        try {
            switch (action) {
                case "LISTALL":     listAll(id); break;
                case "SEARCH":      search(data, id); break;
                case "GETDATAURI":  getDataUri(data, id); break;
                case "JUMPTOURL":   jumpTo(data, id); break;
                case "NET_GET":     netGet(data, id); break;
                case "MDS_CMD":     mdsCmd(data, id); break;
                case "INSTALL_DAPP": reject(id, "install is not supported in the native app"); break;
                default:            reject(id, "unknown action: " + action);
            }
        } catch (Exception e) {
            reject(id, "bridge error: " + e.getMessage());
        }
    }

    // ---- actions ----

    private void listAll(String id) {
        JSONArray arr = new JSONArray();
        for (PocketStore.Row r : store.listAll()) arr.put(rowJson(r));
        resolve(id, obj("results", arr));
    }

    private void search(String term, String id) {
        JSONArray arr = new JSONArray();
        for (PocketStore.Row r : store.search(term, 200)) arr.put(rowJson(r));
        resolve(id, obj("term", term == null ? "" : term, "results", arr));
    }

    private void getDataUri(String minifile, String id) {
        String norm = minifile == null ? "" : minifile.trim();
        if (norm.startsWith("miniweb://")) norm = norm.substring("miniweb://".length());
        while (norm.startsWith("/")) norm = norm.substring(1);
        int slash = norm.indexOf('/');
        final String mx = slash >= 0 ? norm.substring(0, slash) : norm;
        final String path = slash >= 0 ? norm.substring(slash + 1) : "index.html";
        io.execute(() -> {
            PocketStore.Row row = store.get(mx);
            if (row == null || row.data.file == null) { reject(id, "site not cached: " + mx); return; }
            if (!PocketProtocol.verifyPacket(row.data, row.signature)) { reject(id, "site failed verification"); return; }
            try {
                byte[] bytes = SiteExtractor.readEntry(PocketProtocol.hexDecode(row.data.file), path);
                if (bytes == null) { reject(id, "file not found: " + path); return; }
                String uri = "data:" + MimeTypes.forPath(path) + ";base64,"
                        + Base64.encodeToString(bytes, Base64.NO_WRAP);
                resolve(id, obj("datauri", uri));
            } catch (Exception e) { reject(id, "read failed: " + e.getMessage()); }
        });
    }

    private void jumpTo(String minifile, String id) {
        String norm = minifile == null ? "" : minifile.trim();
        if (norm.startsWith("miniweb://")) norm = norm.substring("miniweb://".length());
        while (norm.startsWith("/")) norm = norm.substring(1);
        int slash = norm.indexOf('/');
        String mx = slash >= 0 ? norm.substring(0, slash) : norm;
        String path = slash >= 0 ? norm.substring(slash + 1) : "index.html";
        if (!mx.startsWith("Mx")) { reject(id, "not an Mx address"); return; }
        final String fmx = mx, fpath = path.isEmpty() ? "index.html" : path;
        web.post(() -> { navigator.jumpTo(fmx, fpath); });
        resolve(id, obj());
    }

    private void netGet(String url, String id) {
        io.execute(() -> {
            if (!HostGuard.allow(url)) { reject(id, "Only public HTTPS URLs allowed"); return; }
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);   // a redirect could point at an internal host
                c.setConnectTimeout(NET_TIMEOUT_MS);
                c.setReadTimeout(NET_TIMEOUT_MS);
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String body = in == null ? "" : readCapped(in, NET_MAX_BYTES);
                resolve(id, obj("code", code, "body", body));
            } catch (Exception e) {
                reject(id, "fetch failed: " + e.getMessage());
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private void mdsCmd(String command, String id) {
        String cmd = command == null ? "" : command.trim();
        String name = cmd.split("\\s+", 2)[0].toLowerCase();
        String lower = cmd.toLowerCase();
        if (!SAFE_COMMANDS.contains(name)) { reject(id, "command not allowed: " + name); return; }
        // even within allowed commands, refuse state-changing sub-actions
        for (String bad : new String[]{"action:install", "action:uninstall", "action:remove", "action:send", "action:sendall"}) {
            if (lower.contains(bad)) { reject(id, "sub-action not allowed"); return; }
        }
        node.cmd(cmd, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) { resolve(id, obj("response", json.opt("response"))); }
            @Override public void onError(String message) { reject(id, message); }
        });
    }

    // ---- plumbing ----

    private static JSONObject rowJson(PocketStore.Row r) {
        JSONObject o = new JSONObject();
        try {
            o.put("name", r.data.name);
            o.put("description", r.data.description);
            o.put("version", r.data.version);
        } catch (Exception ignored) {}
        return o;
    }

    /** {status:true, k0:v0, k1:v1, …} built without leaking the checked JSONException. */
    private static JSONObject obj(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            o.put("status", true);
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]);
        } catch (Exception ignored) {}
        return o;
    }

    private void resolve(String id, JSONObject result) {
        deliver(id, result.toString());
    }

    private void reject(String id, String message) {
        JSONObject o = new JSONObject();
        try { o.put("status", false); o.put("error", message); } catch (Exception ignored) {}
        deliver(id, o.toString());
    }

    private void deliver(String id, String json) {
        // Route back into the page's callback registry; id is our own numeric token (safe to inline).
        final String js = "window.__miniweb_resolve && window.__miniweb_resolve(" + jsStr(id) + "," + json + ");";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    private static String jsStr(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }

    private static String readCapped(InputStream in, int max) throws java.io.IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0, n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > max) { bos.write(buf, 0, n - (total - max)); break; }
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    public void shutdown() { io.shutdownNow(); }
}
