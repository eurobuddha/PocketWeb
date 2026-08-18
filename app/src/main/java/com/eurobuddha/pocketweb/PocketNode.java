package com.eurobuddha.pocketweb;

import org.json.JSONArray;
import org.json.JSONObject;

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

    private final NodeApi node;
    private final PocketStore store;

    public PocketNode(NodeApi node, PocketStore store) { this.node = node; this.store = store; }

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
