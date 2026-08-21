package com.eurobuddha.pocketweb;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chain-side of the store: discovers, verifies and ingests file packets, and publishes.
 *
 * Discovery is POLL-based by deliberate design. `coinnotify` on the site address would push a
 * NOTIFYCOIN carrying the full ~100K-hex site to every enabled companion app through the node's
 * unguarded broadcast — the documented uncatchable TransactionTooLargeException app-killer. So we
 * poll `coins ... address:<site>` (that reply rides the content:// hand-off safely) on debounced
 * NEWBLOCK, and only `coinnotify` the tiny REQUEST address.
 *
 * The Android node has no Maxima, so there is no faster side-channel — discovery is chain-only.
 */
public class PocketNode {

    public interface SyncListener { void onSynced(int changed); void onError(String message); }
    public interface CmdListener { void onOk(JSONObject response); void onError(String message); }
    public interface ArchiveListener { void onFetched(PocketProtocol.Data d); void onMiss(); void onError(String message); }

    /**
     * Always-on archive endpoint that serves any site's verified packet by address. The whole site is
     * permanently on-chain, so an archive/indexer can hand it over directly — reliable and
     * owner-independent, unlike the Maxima "ask your contacts" fallback which fails when the owner is
     * offline. User-configurable; default is the eurobuddha.com indexer.
     */
    public static volatile String ARCHIVE_BASE = "https://eurobuddha.com/pocketweb/";

    private final NodeApi node;
    private final PocketStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public PocketNode(NodeApi node, PocketStore store) { this.node = node; this.store = store; }

    /**
     * Fetch a site's packet directly from the archive indexer, VERIFY it (name commits to
     * (randid,owner) + signature), and cache it. This is the reliable retrieval path used on a
     * cache-miss, before the chain request / Maxima fallback.
     */
    public void fetchFromArchive(String mx, ArchiveListener cb) {
        io.execute(() -> {
            HttpURLConnection c = null;
            try {
                String base = ARCHIVE_BASE;
                if (!base.endsWith("/")) base += "/";
                String url = base + mx + ".json";
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                int code = c.getResponseCode();
                android.util.Log.i("PocketWebArchive", "GET " + url + " -> " + code);
                if (code == 404) { cb.onMiss(); return; }
                if (code >= 400) { cb.onError("archive http " + code); return; }
                JSONObject o = new JSONObject(readAll(c.getInputStream()));
                PocketProtocol.Data d = new PocketProtocol.Data(
                        o.getString("name"), o.getString("owner"), o.getString("randid"),
                        o.optString("description", ""), o.getInt("version"), o.getString("file"));
                String sig = o.getString("signature");
                if (!PocketProtocol.verifyPacket(d, sig)) { cb.onError("archive packet failed verification"); return; }
                store.upsertIfNewer(d, sig, PocketStore.PUB_ONCHAIN);
                cb.onFetched(d);
            } catch (Exception e) {
                android.util.Log.w("PocketWebArchive", "fetch " + mx + " failed", e);
                cb.onError("archive fetch: " + e.getMessage());
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    public void close() { io.shutdownNow(); }

    /** Register live notifications for the REQUEST address only (small payload — safe to broadcast). */
    public void subscribeRequests() {
        node.cmd("coinnotify action:add address:" + PocketWire.REQUEST_ADDRESS, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {}
            @Override public void onError(String message) {}
        });
    }

    /**
     * Pull all site coins, verify each, ingest newer versions. Safe at "enabled" (read-only `coins`).
     * Verification is mandatory here AND re-checked at read time (PocketStore.Row.verified()), closing
     * the JS gap where the serving path returned rows unverified.
     */
    public void syncFromChain(SyncListener cb) {
        node.cmd("coins simplestate:true address:" + PocketWire.FILE_ADDRESS, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) {
                int changed = 0, seen = 0;
                JSONArray coins = json.optJSONArray("response");
                if (coins != null) {
                    for (int i = 0; i < coins.length(); i++) {
                        JSONObject coin = coins.optJSONObject(i);
                        if (coin == null) continue;
                        String[] state = coinState(coin);
                        PocketWire.ParsedPacket pp = PocketWire.fromCoinState(state);
                        if (pp == null) continue;
                        seen++;
                        // name must commit to (randid,owner) AND signature must verify — reject otherwise.
                        if (!PocketProtocol.verifyPacket(pp.data, pp.signature)) continue;
                        if (store.upsertIfNewer(pp.data, pp.signature, PocketStore.PUB_ONCHAIN)) changed++;
                    }
                }
                cb.onSynced(changed);
            }
            @Override public void onError(String message) { cb.onError(message); }
        });
    }

    /** Publish a request coin so owners re-broadcast a site we don't have. Needs ADMIN (sendpoll is write). */
    public void requestSite(String name, CmdListener cb) {
        run(PocketWire.buildRequestCommand(name), cb);
    }

    /** Publish/replace a site on-chain. Needs ADMIN. On (pending) the node retries via sendpoll. */
    public void publish(PocketProtocol.Data d, String signatureHex, CmdListener cb) {
        run(PocketWire.buildPublishCommand(d, signatureHex), cb);
    }

    private void run(String command, CmdListener cb) {
        node.cmd(command, new NodeApi.Cb() {
            @Override public void onResult(JSONObject json) { cb.onOk(json); }
            @Override public void onError(String message) { cb.onError(message); }
        });
    }

    /**
     * Extract state slots 0..6 from a coin. With simplestate:true the node returns `state` either as a
     * JSON array of strings or a port-keyed object; handle both. Object elements may be a bare string
     * or {data:...}. Returns a 7-length array (may contain nulls; PocketWire validates completeness).
     */
    static String[] coinState(JSONObject coin) {
        String[] out = new String[7];
        JSONArray arr = coin.optJSONArray("state");
        if (arr != null) {
            for (int i = 0; i < 7 && i < arr.length(); i++) out[i] = slotValue(arr.opt(i));
            return out;
        }
        JSONObject obj = coin.optJSONObject("state");
        if (obj != null) {
            for (int i = 0; i < 7; i++) out[i] = slotValue(obj.opt(String.valueOf(i)));
        }
        return out;
    }

    private static String slotValue(Object v) {
        if (v == null) return null;
        if (v instanceof JSONObject) {
            String d = ((JSONObject) v).optString("data", null);
            return d;
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }
}
