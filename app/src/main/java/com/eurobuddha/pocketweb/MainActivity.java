package com.eurobuddha.pocketweb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;
import org.minimarex.minimaapi.MinimaAPI;
import org.minimarex.minimaapi.MinimaAPIMessages;

import java.util.List;

/**
 * Browse screen: enter/pick an Mx site, sync it from the chain, render it in the WebView.
 * Wires NodeApi (IPC) → PocketStore (cache) → PocketNode (chain sync) → SiteServer (render).
 */
public class MainActivity extends AppCompatActivity {

    public static final String NODE_PKG = "org.minimarex.minimacore";

    private NodeApi node;
    private PocketStore store;
    private PocketNode chain;
    private SiteServer server;
    private MiniwebBridge bridge;

    private WebView web;
    private EditText address;
    private TextView status, banner;

    private BroadcastReceiver notifyReceiver;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean enabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, b.top, b.right, Math.max(b.bottom, ime.bottom));
            return insets;
        });

        web = findViewById(R.id.web);
        address = findViewById(R.id.address);
        status = findViewById(R.id.status);
        banner = findViewById(R.id.pairingBanner);
        ImageButton back = findViewById(R.id.back);
        Button go = findViewById(R.id.go);
        Button sites = findViewById(R.id.sites);

        banner.setOnClickListener(v -> openMinimaCore());
        go.setOnClickListener(v -> loadFromAddressBar());
        sites.setOnClickListener(v -> showSitePicker());
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        address.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { loadFromAddressBar(); return true; }
            return false;
        });

        store = new PocketStore(this);
        server = new SiteServer(this, web, store);
        server.setListener(new ServerListener());

        node = new NodeApi(this, this::onPaired);
        chain = new PocketNode(node, store);

        // Native site API: sites' miniweb_* calls route here (hardened command + host allowlists).
        bridge = new MiniwebBridge(web, store, node, (mx, path) -> server.loadSite(mx, path));
        server.attachBridge(bridge);

        registerNotify();
        setStatus("Cached sites: " + store.count());
    }

    // ---- pairing / node events ----

    private void onPaired(boolean isEnabled) {
        enabled = isEnabled;
        banner.setVisibility(isEnabled ? View.GONE : View.VISIBLE);
        if (isEnabled) {
            chain.subscribeRequests();
            sync("Connected. Syncing sites…");
        }
    }

    private void registerNotify() {
        notifyReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                // exported receiver — fail closed if the minima id doesn't match ours (TERM-1/5)
                if (!MinimaAPI.checkMinimaID(MainActivity.this, intent)) return;
                String data = intent.getStringExtra(MinimaAPIMessages.MINIMA_API_NOTIFY_DATA);
                if (data == null) return;
                try {
                    String event = new JSONObject(data).optString("event", "");
                    if ("NEWBLOCK".equals(event)) requestSync();
                } catch (Exception ignored) {}
            }
        };
        ContextCompat.registerReceiver(this, notifyReceiver,
                new IntentFilter(MinimaAPIMessages.MINIMA_API_NOTIFY), ContextCompat.RECEIVER_EXPORTED);
    }

    private final Runnable syncTask = () -> sync(null);
    /** Coalesce NEWBLOCK bursts into a single sync. */
    private void requestSync() { ui.removeCallbacks(syncTask); ui.postDelayed(syncTask, 400); }

    private void sync(String note) {
        if (!enabled) return;
        if (note != null) setStatus(note);
        chain.syncFromChain(new PocketNode.SyncListener() {
            @Override public void onSynced(int changed) {
                setStatus("Cached sites: " + store.count() + (changed > 0 ? "  (+" + changed + " new)" : ""));
            }
            @Override public void onError(String message) { setStatus("Sync error: " + message); }
        });
    }

    // ---- navigation ----

    private void loadFromAddressBar() {
        String input = address.getText().toString().trim();
        if (input.isEmpty()) return;
        String norm = input;
        if (norm.startsWith("miniweb://")) norm = norm.substring("miniweb://".length());
        while (norm.startsWith("/")) norm = norm.substring(1);
        int slash = norm.indexOf('/');
        String mx = slash >= 0 ? norm.substring(0, slash) : norm;
        String path = slash >= 0 ? norm.substring(slash + 1) : "index.html";
        if (!mx.startsWith("Mx")) { toast("Enter an Mx… site address"); return; }
        setStatus("Opening " + mx + " …");
        server.loadSite(mx, path.isEmpty() ? "index.html" : path);
    }

    private void showSitePicker() {
        List<PocketStore.Row> rows = store.listAll();
        if (rows.isEmpty()) { toast(enabled ? "No sites cached yet — syncing." : "Enable the app to sync sites."); return; }
        String[] labels = new String[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            PocketStore.Row r = rows.get(i);
            String d = r.data.description;
            labels[i] = (d == null || d.isEmpty() ? "(no description)" : d) + "\n" + r.data.name;
        }
        new AlertDialog.Builder(this)
                .setTitle("Cached sites (" + rows.size() + ")")
                .setItems(labels, (dlg, which) -> {
                    address.setText(rows.get(which).data.name);
                    server.loadSite(rows.get(which).data.name);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private final class ServerListener implements SiteServer.Listener {
        @Override public void onPageStarted(String mx, String url) { if (mx != null) address.setText(mx); }
        @Override public void onTitle(String title) { setTitle(title); }
        @Override public void onSiteMissing(String mx) {
            setStatus("Not cached — syncing to find " + mx + " …");
            chain.syncFromChain(new PocketNode.SyncListener() {
                @Override public void onSynced(int changed) {
                    if (store.get(mx) != null) server.loadSite(mx);
                    else setStatus("Site not found on-chain" + (enabled ? "" : " (enable the app first)")
                            + ". If your node pruned it, granting Admin lets PocketWeb request it.");
                }
                @Override public void onError(String message) { setStatus("Sync error: " + message); }
            });
        }
        @Override public void onError(String message) { setStatus(message); }
    }

    // ---- helpers ----

    private void setStatus(String s) { ui.post(() -> status.setText(s)); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void openMinimaCore() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(NODE_PKG);
        if (launch != null) startActivity(launch);
        else toast("Minima Core is not installed.");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (notifyReceiver != null) try { unregisterReceiver(notifyReceiver); } catch (Exception ignored) {}
        if (bridge != null) bridge.shutdown();
        if (node != null) node.onDestroy();
        if (store != null) store.close();
        super.onDestroy();
    }
}
