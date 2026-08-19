package com.eurobuddha.pocketweb;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * PRIVILEGED bridge for the app's own UI (window.PocketWebApp), exposed ONLY to the trusted
 * app origin (app.pocketweb.local) — never to untrusted sites, which use the separate locked-down
 * MiniwebBridge. Backs the native mds.js shim: MDS.cmd / MDS.sql / MDS.api.call.
 *
 * All methods return a JSON string synchronously (they run on the JS-bridge thread; SQLite and the
 * in-memory store are fine there). Node round-trips are avoided: the only cmd the UI needs
 * (checkmode) is answered locally.
 */
public class AppBridge {

    private final Context ctx;
    private final PocketStore store;
    private final PocketKeys keys;
    private SQLiteDatabase appDb;   // local convenience tables (bookmarks) for the reused HTML

    public AppBridge(Context ctx, PocketStore store, PocketKeys keys) {
        this.ctx = ctx.getApplicationContext();
        this.store = store;
        this.keys = keys;
    }

    // ---- MDS.cmd ----
    @JavascriptInterface
    public String cmd(String command) {
        String c = command == null ? "" : command.trim().toLowerCase();
        // The browser UI only asks checkmode (to assert it's not in write mode). Answer locally.
        if (c.equals("checkmode") || c.startsWith("checkmode ")) {
            JSONObject resp = new JSONObject();
            try {
                JSONObject inner = new JSONObject();
                inner.put("writemode", false);
                inner.put("mode", "READ");
                resp.put("status", true);
                resp.put("response", inner);
            } catch (Exception ignored) {}
            return resp.toString();
        }
        return err("command not supported in app UI: " + c);
    }

    // ---- MDS.sql (local convenience DB for the reused HTML, e.g. bookmarks) ----
    @JavascriptInterface
    public synchronized String sql(String rawSql) {
        try {
            if (appDb == null) appDb = ctx.openOrCreateDatabase("appui.db", Context.MODE_PRIVATE, null);
            String s = normalizeSql(rawSql);
            String head = s.trim().toLowerCase();
            JSONObject out = new JSONObject();
            if (head.startsWith("select") || head.startsWith("pragma")) {
                JSONArray rows = new JSONArray();
                try (Cursor cur = appDb.rawQuery(s, null)) {
                    String[] cols = cur.getColumnNames();
                    while (cur.moveToNext()) {
                        JSONObject row = new JSONObject();
                        for (int i = 0; i < cols.length; i++) row.put(cols[i].toUpperCase(), cur.getString(i));
                        rows.put(row);
                    }
                }
                out.put("status", true);
                out.put("rows", rows);
                out.put("count", rows.length());
            } else {
                // execSQL runs a SINGLE statement — a stacked-injection ("…; DROP TABLE…") throws here
                // rather than executing, so a crafted bookmark name can at worst fail its own insert.
                appDb.execSQL(s);
                out.put("status", true);
                out.put("rows", new JSONArray());
                out.put("count", 0);
            }
            return out.toString();
        } catch (Exception e) {
            return err("sql: " + e.getMessage());
        }
    }

    // ---- MDS.api.call("pocketfs", {action,data}) ----
    @JavascriptInterface
    public String apiCall(String dapp, String jsondata) {
        if (dapp == null || !dapp.equalsIgnoreCase("pocketfs")) return err("unknown dapp: " + dapp);
        try {
            JSONObject req = new JSONObject(jsondata == null || jsondata.isEmpty() ? "{}" : jsondata);
            String action = req.optString("action", "");
            String data = req.optString("data", "");
            switch (action) {
                case "SEARCH":  return apiOk(payloadResults(store.searchFull(data, 200), true));
                case "LISTALL": return apiOk(payloadResults(store.listAll(), false));
                case "COPY":    return copy(data);
                default:        return err("unknown action: " + action);
            }
        } catch (Exception e) {
            return err("apiCall: " + e.getMessage());
        }
    }

    @JavascriptInterface
    public String loadBinary(String path) { return err("not available"); }

    // ---- helpers ----

    private String copy(String name) {
        try {
            PocketStore.Row row = store.get(name);
            if (row == null || row.data.file == null) return err("site not found: " + name);
            byte[] zip = PocketProtocol.hexDecode(row.data.file);
            PocketWire.ParsedPacket p = PocketPublisher.createNew(keys, zip,
                    row.data.description == null ? "" : row.data.description);
            store.upsertIfNewer(p.data, p.signature, PocketStore.PUB_LOCAL);
            JSONObject results = new JSONObject();
            JSONObject inner = new JSONObject();
            inner.put("name", p.data.name);
            results.put("data", inner);
            JSONObject payload = new JSONObject();
            payload.put("results", results);
            return apiOk(payload);
        } catch (Exception e) {
            return err("copy: " + e.getMessage());
        }
    }

    /** Build {results:[ {data:{…}, signature} ]} the way pocketfs replied, optionally including the file blob. */
    private JSONObject payloadResults(List<PocketStore.Row> rows, boolean withFile) throws Exception {
        JSONArray arr = new JSONArray();
        for (PocketStore.Row r : rows) {
            JSONObject d = new JSONObject();
            d.put("name", r.data.name);
            d.put("owner", r.data.owner);
            d.put("randid", r.data.randid);
            d.put("description", r.data.description);
            d.put("version", r.data.version);
            if (withFile && r.data.file != null) d.put("file", r.data.file);
            JSONObject fp = new JSONObject();
            fp.put("data", d);
            fp.put("signature", r.signature);
            arr.put(fp);
        }
        JSONObject payload = new JSONObject();
        payload.put("results", arr);
        return payload;
    }

    /** MDS.api.call reply shape: {status:true, data:"<json string>"}. */
    private static String apiOk(JSONObject payload) {
        JSONObject o = new JSONObject();
        try { o.put("status", true); o.put("data", payload.toString()); } catch (Exception ignored) {}
        return o.toString();
    }

    private static String err(String message) {
        JSONObject o = new JSONObject();
        try { o.put("status", false); o.put("error", message); } catch (Exception ignored) {}
        return o.toString();
    }

    /** Translate the H2-isms in the reused HTML's SQL to SQLite. */
    private static String normalizeSql(String sql) {
        String s = sql.replace("`", "");
        s = s.replaceAll("(?i)bigint\\s+auto_increment", "INTEGER PRIMARY KEY AUTOINCREMENT");
        s = s.trim();
        while (s.endsWith(";")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    public synchronized void close() { if (appDb != null) { appDb.close(); appDb = null; } }
}
