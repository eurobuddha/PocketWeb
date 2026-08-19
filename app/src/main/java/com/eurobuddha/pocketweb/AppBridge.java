package com.eurobuddha.pocketweb;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PRIVILEGED bridge for the app's own UI (window.PocketWebApp), exposed ONLY to the trusted app
 * origin. Backs the native mds.js shim: it maps the full MDS surface the original pocketWeb +
 * pocketFS pages use onto the native engine so those pages run unchanged.
 *
 *   MDS.cmd(maxima/random/hash/convert/maxsign/maxverify/checkmode) -> PocketProtocol/PocketKeys
 *   MDS.cmd(sendpoll/coins/...)  -> the real node via NodeApi (async)
 *   MDS.sql                      -> PocketStore's SQLite (shared filepackets + bookmarks)
 *   MDS.file / api / keypair / net / util / notify / comms / dapplink -> native equivalents
 */
public class AppBridge {

    public interface Host {
        void pickPublishZip();
        void pickImportKey();
        String status();       // {enabled, syncing, count}
        void refreshSync();
    }

    private final Context ctx;
    private final WebView web;
    private final PocketStore store;
    private final PocketKeys keys;
    private final NodeApi node;
    private final Host host;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final File mdsDir, webDir;

    public AppBridge(Context ctx, WebView web, PocketStore store, PocketKeys keys, NodeApi node, Host host) {
        this.ctx = ctx.getApplicationContext();
        this.web = web;
        this.store = store;
        this.keys = keys;
        this.node = node;
        this.host = host;
        this.mdsDir = new File(this.ctx.getFilesDir(), "mdsfiles");
        this.webDir = new File(this.ctx.getFilesDir(), "webfiles");
        mdsDir.mkdirs(); webDir.mkdirs();
    }

    // ═══════════════════ MDS.cmd (synchronous crypto/local commands) ═══════════════════
    @JavascriptInterface
    public String cmd(String command) {
        try {
            String c = command == null ? "" : command.trim();
            String name = c.split("\\s+", 2)[0].toLowerCase();
            Map<String, String> p = params(c);
            switch (name) {
                // pocketWeb checks resp.response.writemode (wants false); pocketFS checks
                // resp.response.mode=="WRITE". Different fields, so this satisfies both dapps at once.
                case "checkmode": return resp(kv("writemode", false, "mode", "WRITE", "untrustedmdsuid", "app"));
                case "maxima":    return resp(kv("publickey", keys.activeOwnerHex()));
                case "random": {
                    int size = 32;
                    try { if (p.containsKey("size")) size = Integer.parseInt(p.get("size")); } catch (Exception ignored) {}
                    byte[] b = new byte[size]; new java.security.SecureRandom().nextBytes(b);
                    return resp(kv("random", PocketProtocol.hexEncode(b)));
                }
                case "hash": {
                    byte[] data = bytesParam(p.get("data"));
                    return resp(kv("hash", PocketProtocol.hexEncode(PocketProtocol.sha3(data))));
                }
                case "convert": return resp(kv("conversion", convert(p)));
                case "maxsign": {
                    byte[] data = bytesParam(p.get("data"));
                    PrivateKey priv = p.containsKey("privatekey")
                            ? PocketProtocol.privateKeyFromHex(p.get("privatekey")) : keys.activePrivateKey();
                    return resp(kv("signature", PocketProtocol.hexEncode(PocketProtocol.sign(data, priv))));
                }
                case "maxverify": {
                    byte[] data = bytesParam(p.get("data"));
                    boolean ok = PocketProtocol.verify(data,
                            PocketProtocol.publicKeyFromHex(p.get("publickey")), PocketProtocol.hexDecode(p.get("signature")));
                    return ok ? okJson() : failJson("verify failed");
                }
                case "mds": return okJson();   // install/uninstall not applicable natively
                default:   return failJson("cmd not supported: " + name);
            }
        } catch (Exception e) {
            android.util.Log.w("PocketWebApp", "cmd error for [" + command + "]", e);
            return failJson("cmd error: " + e);
        }
    }

    // ═══════════════════ MDS.cmd async (real node round-trips: sendpoll/coins) ═══════════════════
    @JavascriptInterface
    public void cmdAsync(String command, String cbId) {
        node.cmd(command, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) { resolve(cbId, json.toString()); }
            @Override public void onError(String message) { resolve(cbId, failJson(message)); }
        });
    }

    // ═══════════════════ MDS.sql (shared PocketStore db) ═══════════════════
    @JavascriptInterface
    public synchronized String sql(String rawSql) {
        try {
            SQLiteDatabase db = store.getWritableDatabase();
            String s = normalizeSql(rawSql);
            String head = s.trim().toLowerCase();
            JSONObject out = new JSONObject();
            if (head.startsWith("select") || head.startsWith("pragma")) {
                JSONArray rows = new JSONArray();
                try (Cursor cur = db.rawQuery(s, null)) {
                    String[] cols = cur.getColumnNames();
                    while (cur.moveToNext()) {
                        JSONObject row = new JSONObject();
                        for (int i = 0; i < cols.length; i++) row.put(cols[i].toUpperCase(), cur.getString(i));
                        rows.put(row);
                    }
                }
                out.put("status", true); out.put("rows", rows); out.put("count", rows.length());
            } else {
                db.execSQL(s);   // single statement — stacked injection throws rather than executes
                out.put("status", true); out.put("rows", new JSONArray()); out.put("count", 0);
            }
            return out.toString();
        } catch (Exception e) { return failJson("sql: " + e.getMessage()); }
    }

    // ═══════════════════ MDS.keypair ═══════════════════
    @JavascriptInterface
    public String keypairGet(String key) {
        String v = prefs().getString("kp_" + key, null);
        JSONObject o = new JSONObject();
        try { o.put("status", v != null); o.put("value", v); } catch (Exception ignored) {}
        return o.toString();
    }
    @JavascriptInterface
    public String keypairSet(String key, String value) {
        prefs().edit().putString("kp_" + key, value).apply();
        return okJson();
    }

    // ═══════════════════ MDS.api.call("pocketfs", …) ═══════════════════
    @JavascriptInterface
    public String apiCall(String dapp, String jsondata) {
        if (dapp == null || !dapp.equalsIgnoreCase("pocketfs")) return failJson("unknown dapp: " + dapp);
        try {
            JSONObject req = new JSONObject(jsondata == null || jsondata.isEmpty() ? "{}" : jsondata);
            String action = req.optString("action", "");
            String data = req.optString("data", "");
            switch (action) {
                case "SEARCH":   return apiOk(payloadResults(store.searchFull(data, 200), true));
                case "LISTALL":  return apiOk(payloadResults(store.listAll(), false));
                case "LISTMINE": return apiOk(payloadResults(mineAndOwned(), true));
                case "DELETE":   store.delete(data); return apiOk(new JSONObject());
                case "COPY":     return copy(data);
                default:         return failJson("unknown action: " + action);
            }
        } catch (Exception e) { return failJson("apiCall: " + e.getMessage()); }
    }

    // ═══════════════════ MDS.file (native file area; browse itself is handled by the site viewer) ═══════════════════
    @JavascriptInterface
    public String fileSave(String name, String data, boolean binary) {
        try {
            File f = safe(mdsDir, name);
            byte[] bytes = binary ? PocketProtocol.hexDecode(data) : data.getBytes(StandardCharsets.UTF_8);
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), bytes);
            return okJson();
        } catch (Exception e) { return failJson("save: " + e.getMessage()); }
    }
    @JavascriptInterface
    public String fileLoad(String name, boolean binary) {
        try {
            File f = safe(mdsDir, name);
            if (!f.isFile()) return failJson("not found");
            byte[] bytes = Files.readAllBytes(f.toPath());
            String val = binary ? PocketProtocol.hexEncode(bytes) : new String(bytes, StandardCharsets.UTF_8);
            JSONObject load = new JSONObject(); load.put("data", val);
            JSONObject response = new JSONObject(); response.put("load", load);
            return resp(response);   // -> {status:true, response:{load:{data}}}
        } catch (Exception e) { return failJson("load: " + e.getMessage()); }
    }
    @JavascriptInterface
    public String fileDelete(String name, boolean web) {
        try { deleteRec(safe(web ? webDir : mdsDir, name)); return okJson(); }
        catch (Exception e) { return failJson("delete: " + e.getMessage()); }
    }
    @JavascriptInterface
    public String fileCopyToWeb(String mdsfile, String webfile) {
        try {
            File src = safe(mdsDir, mdsfile), dst = safe(webDir, webfile);
            dst.getParentFile().mkdirs();
            if (src.isFile()) Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return okJson();
        } catch (Exception e) { return failJson("copytoweb: " + e.getMessage()); }
    }
    @JavascriptInterface
    public String fileList(String folder, boolean web) {
        try { File f = safe(web ? webDir : mdsDir, folder); return resp(kv("exists", f.exists())); }
        catch (Exception e) { return resp(kv("exists", false)); }
    }
    @JavascriptInterface
    public void fileDownload(String url, String cbId) { resolve(cbId, failJson("download not supported in app")); }

    // ═══════════════════ MDS.net.GET (host-guarded) ═══════════════════
    @JavascriptInterface
    public void netGet(String url, String cbId) {
        io.execute(() -> {
            if (!HostGuard.allow(url)) { resolve(cbId, failJson("Only public HTTPS URLs allowed")); return; }
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String body = readCapped(in, 256 * 1024);
                resolve(cbId, resp(kv("code", code, "body", body)));
            } catch (Exception e) { resolve(cbId, failJson("fetch: " + e.getMessage())); }
            finally { if (c != null) c.disconnect(); }
        });
    }

    // ═══════════════════ misc MDS surface ═══════════════════
    @JavascriptInterface public String dapplink(String name) {
        // MDS.dapplink's callback reads resp.base (a URL). Point pocketWeb's "browse" at /browse.html,
        // which AppServer intercepts to open the site in the sandboxed viewer.
        JSONObject o = new JSONObject();
        try { o.put("status", true); o.put("base", "/index.html"); o.put("uid", "app"); } catch (Exception ignored) {}
        return o.toString();
    }
    @JavascriptInterface public void   commsBroadcast(String msg) { /* local; native sync drives the real events */ }
    @JavascriptInterface public void   notify(String text) { /* could raise an Android notification */ }

    // ═══════════════════ app chrome (status / pickers / refresh) ═══════════════════
    @JavascriptInterface public void publishPick()   { if (host != null) host.pickPublishZip(); }
    @JavascriptInterface public void importKeyPick() { if (host != null) host.pickImportKey(); }
    @JavascriptInterface public String status()      { return host != null ? host.status() : "{}"; }
    @JavascriptInterface public void refresh()       { if (host != null) host.refreshSync(); }

    // ═══════════════════ helpers ═══════════════════

    private String convert(Map<String, String> p) {
        String from = p.getOrDefault("from", "").toLowerCase();
        String to = p.getOrDefault("to", "").toLowerCase();
        String data = p.getOrDefault("data", "");
        byte[] bytes = from.equals("hex") ? PocketProtocol.hexDecode(data)
                : from.equals("base64") ? Base64.decode(data, Base64.DEFAULT)
                : data.getBytes(StandardCharsets.UTF_8);   // "string"
        switch (to) {
            case "hex": return PocketProtocol.hexEncode(bytes);
            case "mx":  return PocketProtocol.makeMxAddress(bytes);
            case "base64": return Base64.encodeToString(bytes, Base64.NO_WRAP);
            default:    return new String(bytes, StandardCharsets.UTF_8);   // "string"
        }
    }

    /** hash/maxsign/maxverify data param: 0x-prefixed -> hex bytes, else UTF-8 bytes (matches the node). */
    private static byte[] bytesParam(String v) {
        if (v == null) return new byte[0];
        return (v.startsWith("0x") || v.startsWith("0X")) ? PocketProtocol.hexDecode(v) : v.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> params(String command) {
        Map<String, String> m = new HashMap<>();
        String[] toks = command.trim().split("\\s+");
        for (int i = 1; i < toks.length; i++) {
            int c = toks[i].indexOf(':');
            if (c > 0) m.put(toks[i].substring(0, c).toLowerCase(), toks[i].substring(c + 1));
        }
        return m;
    }

    private List<PocketStore.Row> mineAndOwned() {
        List<PocketStore.Row> mine = store.getMine();
        java.util.Set<String> owners = new java.util.HashSet<>(keys.owners());
        if (!owners.isEmpty()) for (PocketStore.Row r : store.listAll()) if (owners.contains(r.data.owner)) mine.add(r);
        return mine;
    }

    private String copy(String name) {
        try {
            PocketStore.Row row = store.get(name);
            if (row == null || row.data.file == null) return failJson("site not found: " + name);
            PocketWire.ParsedPacket p = PocketPublisher.createNew(keys,
                    PocketProtocol.hexDecode(row.data.file), row.data.description == null ? "" : row.data.description);
            store.upsertIfNewer(p.data, p.signature, PocketStore.PUB_LOCAL);
            JSONObject inner = new JSONObject(); inner.put("name", p.data.name);
            JSONObject results = new JSONObject(); results.put("data", inner);
            JSONObject payload = new JSONObject(); payload.put("results", results);
            return apiOk(payload);
        } catch (Exception e) { return failJson("copy: " + e.getMessage()); }
    }

    private JSONObject payloadResults(List<PocketStore.Row> rows, boolean withFile) throws Exception {
        JSONArray arr = new JSONArray();
        for (PocketStore.Row r : rows) {
            JSONObject d = new JSONObject();
            d.put("name", r.data.name); d.put("owner", r.data.owner); d.put("randid", r.data.randid);
            d.put("description", r.data.description); d.put("version", r.data.version);
            d.put("published", r.published);
            if (withFile && r.data.file != null) d.put("file", r.data.file);
            JSONObject fp = new JSONObject(); fp.put("data", d); fp.put("signature", r.signature);
            arr.put(fp);
        }
        JSONObject payload = new JSONObject(); payload.put("results", arr);
        return payload;
    }

    private SharedPreferences prefs() { return ctx.getSharedPreferences("pocketweb_appui", Context.MODE_PRIVATE); }

    /** Translate the H2-isms in the reused MDS SQL to SQLite. */
    private static String normalizeSql(String sql) {
        String s = sql.replace("`", "");
        s = s.replaceAll("(?i)bigint\\s+auto_increment", "INTEGER PRIMARY KEY AUTOINCREMENT");
        s = s.trim();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    private static File safe(File base, String name) throws Exception {
        String rel = SiteExtractor.normalizeRel(name);
        if (rel == null) rel = name.replaceAll("[^A-Za-z0-9._/\\-]", "_").replace("..", "_");
        File f = new File(base, rel).getCanonicalFile();
        if (!f.getPath().startsWith(base.getCanonicalPath())) throw new Exception("path escape");
        return f;
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] k = f.listFiles(); if (k != null) for (File c : k) deleteRec(c);
        f.delete();
    }

    private static String readCapped(InputStream in, int max) throws Exception {
        if (in == null) return "";
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int total = 0, n;
        while ((n = in.read(buf)) != -1) { total += n; if (total > max) { bos.write(buf, 0, n - (total - max)); break; } bos.write(buf, 0, n); }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    private void resolve(String cbId, String json) {
        final String js = "window.__app_resolve && window.__app_resolve(" + jsStr(cbId) + "," + json + ");";
        web.post(() -> web.evaluateJavascript(js, null));
    }

    // ---- JSON builders (Android org.json.put throws checked exceptions) ----
    private static String resp(JSONObject response) {
        JSONObject o = new JSONObject();
        try { o.put("status", true); o.put("response", response); } catch (Exception ignored) {}
        return o.toString();
    }
    private static JSONObject kv(Object... kvs) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i + 1 < kvs.length; i += 2) o.put((String) kvs[i], kvs[i + 1]); } catch (Exception ignored) {}
        return o;
    }
    private static String okJson() { JSONObject o = new JSONObject(); try { o.put("status", true); } catch (Exception ignored) {} return o.toString(); }
    private static String failJson(String m) { JSONObject o = new JSONObject(); try { o.put("status", false); o.put("error", m); } catch (Exception ignored) {} return o.toString(); }
    private static String apiOk(JSONObject payload) { JSONObject o = new JSONObject(); try { o.put("status", true); o.put("data", payload.toString()); } catch (Exception ignored) {} return o.toString(); }
    private static String jsStr(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c == '"' || c == '\\') b.append('\\').append(c); else if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
        return b.append('"').toString();
    }

    public void close() { io.shutdownNow(); }
}
