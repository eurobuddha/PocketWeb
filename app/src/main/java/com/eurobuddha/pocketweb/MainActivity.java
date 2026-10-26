package com.eurobuddha.pocketweb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;
import com.eurobuddha.minimaapi.MinimaAPI;
import com.eurobuddha.minimaapi.MinimaAPIMessages;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Host for the two WebViews: the app's own designed UI (appWeb, your ported pocketWeb front-end on
 * the privileged AppBridge) and the sandboxed on-chain-site viewer (siteWeb, locked-down bridge).
 * appWeb is shown by default; opening a site swaps to siteWeb.
 */
public class MainActivity extends AppCompatActivity {


    private NodeApi node;
    private PocketStore store;
    private PocketNode chain;
    private PocketKeys keys;

    private WebView appWeb, siteWeb;
    private AppServer appServer;
    private AppBridge appBridge;
    private SiteServer server;
    private MiniwebBridge bridge;

    private TextView banner;
    private BroadcastReceiver notifyReceiver;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean enabled = false;
    private volatile boolean syncing = false;
    private boolean siteMode = false;

    private ActivityResultLauncher<String[]> pickZip;
    private ActivityResultLauncher<String[]> pickKey;
    private ActivityResultLauncher<String> fileChooserLauncher;
    private ValueCallback<Uri[]> fileChooserCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, b.top, b.right, b.bottom);   // keep content out from under the system bars
            return insets;
        });

        appWeb = findViewById(R.id.appWeb);
        siteWeb = findViewById(R.id.siteWeb);
        banner = findViewById(R.id.pairingBanner);
        banner.setOnClickListener(v -> openMinimaCore());

        store = new PocketStore(this);
        keys = new PocketKeys(getFilesDir());

        node = new NodeApi(this, this::onPaired);
        chain = new PocketNode(node, store);

        // Sandboxed site viewer + its locked-down bridge
        server = new SiteServer(this, siteWeb, store);
        server.setListener(new ServerListener());
        bridge = new MiniwebBridge(siteWeb, store, node, this::openSite);
        server.attachBridge(bridge);

        // The app's own designed UI + its privileged bridge
        appBridge = new AppBridge(this, appWeb, store, keys, node, new AppBridge.Host() {
            @Override public void pickPublishZip() { ui.post(() -> pickZip.launch(new String[]{"application/zip", "application/octet-stream"})); }
            @Override public void pickImportKey() { ui.post(() -> pickKey.launch(new String[]{"*/*"})); }
            @Override public String status() { return statusJson(); }
            @Override public void refreshSync() { ui.post(MainActivity.this::sync); }
        });
        appServer = new AppServer(this, appWeb, appBridge, this::openSite);
        appServer.setFileChooser(this::openFileChooser);
        appServer.loadHome();

        pickZip = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) onZipPicked(uri);
        });
        pickKey = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) onKeyPicked(uri);
        });
        // WebView <input type=file> (pocketFS New File upload) -> SAF -> back into the page
        fileChooserLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            ValueCallback<Uri[]> cb = fileChooserCallback;
            fileChooserCallback = null;
            if (cb != null) cb.onReceiveValue(uri != null ? new Uri[]{ uri } : null);
        });

        registerNotify();
    }

    // ---- navigation between app UI and site viewer ----

    private void openSite(String mx, String path) {
        ui.post(() -> {
            siteMode = true;
            appWeb.setVisibility(View.GONE);
            siteWeb.setVisibility(View.VISIBLE);
            server.loadSite(mx, path == null || path.isEmpty() ? "index.html" : path);
        });
    }

    /** WebView file input -> SAF picker; result delivered back to the page's ValueCallback. */
    private boolean openFileChooser(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams params) {
        if (fileChooserCallback != null) { fileChooserCallback.onReceiveValue(null); }
        fileChooserCallback = cb;
        String mime = "*/*";
        try {
            String[] types = params != null ? params.getAcceptTypes() : null;
            if (types != null && types.length > 0 && types[0] != null && !types[0].isEmpty()) mime = types[0];
        } catch (Exception ignored) {}
        try { fileChooserLauncher.launch(mime); }
        catch (Exception e) { fileChooserCallback = null; cb.onReceiveValue(null); return false; }
        return true;
    }

    private void showHome() {
        siteMode = false;
        siteWeb.setVisibility(View.GONE);
        appWeb.setVisibility(View.VISIBLE);
    }

    // ---- pairing / node events ----

    private void onPaired(boolean isEnabled) {
        enabled = isEnabled;
        ui.post(() -> banner.setVisibility(isEnabled ? View.GONE : View.VISIBLE));
        pushStatus();
        if (isEnabled) { chain.subscribeRequests(); sync(); }
    }

    private void registerNotify() {
        notifyReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (!MinimaAPI.checkMinimaID(MainActivity.this, intent)) return;   // fail closed
                String data = MinimaAPI.notifyData(intent);
                if (data == null) return;
                try {
                    if ("NEWBLOCK".equals(new JSONObject(data).optString("event", ""))) requestSync();
                } catch (Exception ignored) {}
            }
        };
        ContextCompat.registerReceiver(this, notifyReceiver,
                MinimaAPI.notifyFilter(), ContextCompat.RECEIVER_EXPORTED);
    }

    private final Runnable syncTask = this::sync;
    private void requestSync() { ui.removeCallbacks(syncTask); ui.postDelayed(syncTask, 400); }

    private void sync() {
        if (!enabled) { pushStatus(); return; }
        syncing = true; pushStatus();
        chain.syncFromChain(new PocketNode.SyncListener() {
            @Override public void onSynced(int changed) { syncing = false; pushStatus(); }
            @Override public void onError(String message) { syncing = false; pushStatus(); }
        });
    }

    /** Current pairing/sync state for the UI status line. */
    private String statusJson() {
        int count = 0;
        try { count = store.count(); } catch (Exception ignored) {}
        return "{\"enabled\":" + enabled + ",\"syncing\":" + syncing + ",\"count\":" + count + "}";
    }

    /** Push a STATUS event into the app UI so it can render the connection/sync line. */
    private void pushStatus() {
        ui.post(() -> {
            if (siteMode || appWeb == null) return;
            appWeb.evaluateJavascript(
                "window.MDS && MDS._emit && MDS._emit({event:'STATUS',data:" + statusJson() + "});", null);
        });
    }

    // ---- publish / keys (native menu; will move into the app UI's My Site screen) ----

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Publish a site…");
        menu.add(0, 2, 1, "Import signing key…");
        menu.add(0, 3, 2, "Home");
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1: pickZip.launch(new String[]{"application/zip", "application/octet-stream"}); return true;
            case 2: pickKey.launch(new String[]{"*/*"}); return true;
            case 3: showHome(); appServer.loadHome(); return true;
            default: return super.onOptionsItemSelected(item);
        }
    }

    private void onZipPicked(Uri uri) {
        byte[] bytes = readUri(uri, PocketPublisher.MAX_ZIP_BYTES + 64);
        if (bytes == null) { toast("Could not read file"); return; }
        if (bytes.length > PocketPublisher.MAX_ZIP_BYTES) {
            toast("Site too big: " + bytes.length + " bytes (max " + PocketPublisher.MAX_ZIP_BYTES + ")"); return;
        }
        final byte[] zip = bytes;
        final EditText input = new EditText(this);
        input.setHint("Short description");
        new AlertDialog.Builder(this)
                .setTitle("Publish site (" + zip.length + " bytes)")
                .setView(input)
                .setPositiveButton("Publish", (d, w) -> publishNew(zip, input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void publishNew(byte[] zip, String description) {
        try {
            PocketWire.ParsedPacket p = PocketPublisher.createNew(keys, zip, description);
            store.upsertIfNewer(p.data, p.signature, PocketStore.PUB_LOCAL);
            toast("Publishing " + p.data.name + " …");
            chain.publish(p.data, p.signature, new PocketNode.CmdListener() {
                @Override public void onOk(JSONObject resp) {
                    store.markPublished(p.data.name);
                    toast("Published (mining): " + p.data.name);
                    reloadAppUi();
                }
                @Override public void onError(String message) {
                    boolean needsAdmin = message != null && (message.contains("write")
                            || message.contains("Admin") || message.contains(NodeApi.ERR_NOT_ENABLED));
                    toast("Publish failed: " + message + (needsAdmin ? " — grant PocketWeb Admin in Minima Core → Apps." : ""));
                }
            });
        } catch (Exception e) { toast("Publish error: " + e.getMessage()); }
    }

    private void onKeyPicked(Uri uri) {
        byte[] bytes = readUri(uri, 64 * 1024);
        if (bytes == null) { toast("Could not read key"); return; }
        try {
            String owner = keys.importPrivateKeyHex(keyTextToHex(new String(bytes, StandardCharsets.UTF_8).trim()));
            toast("Imported key for\n" + owner);
            reloadAppUi();
        } catch (Exception e) { toast("Import failed: " + e.getMessage()); }
    }

    /** Reload the app UI (e.g. after publish/import) so My Site reflects the change. */
    private void reloadAppUi() { ui.post(() -> { if (!siteMode && appWeb != null) appWeb.reload(); }); }

    private String keyTextToHex(String text) {
        String t = text.trim();
        if (t.startsWith("0x") || t.matches("(?s)[0-9a-fA-F\\s]+")) return t.replaceAll("\\s", "");
        String b64 = t.contains("BEGIN") ? t.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "") : t.replaceAll("\\s", "");
        return PocketProtocol.hexEncode(android.util.Base64.decode(b64, android.util.Base64.DEFAULT));
    }

    private final class ServerListener implements SiteServer.Listener {
        @Override public void onPageStarted(String mx, String url) {}
        @Override public void onTitle(String title) {}
        @Override public void onSiteMissing(String mx) {
            // Reliable path first: fetch the verified packet straight from the archive indexer.
            // Works even with no node/pairing, and doesn't depend on the owner being online.
            toast("Fetching " + mx + " …");
            chain.fetchFromArchive(mx, new PocketNode.ArchiveListener() {
                @Override public void onFetched(PocketProtocol.Data d) { ui.post(() -> server.loadSite(mx)); }
                @Override public void onMiss() { syncFallback(mx); }
                @Override public void onError(String message) { syncFallback(mx); }
            });
        }
        @Override public void onError(String message) { toast(message); }
    }

    /** Site not in the archive (e.g. brand new): fall back to a chain sync, then the request/Maxima path. */
    private void syncFallback(String mx) {
        if (!enabled) { toast("Site not in archive; enable the app to search the chain."); return; }
        chain.syncFromChain(new PocketNode.SyncListener() {
            @Override public void onSynced(int changed) {
                if (store.get(mx) != null) server.loadSite(mx);
                else toast("Site not found in archive or on-chain.");
            }
            @Override public void onError(String message) { toast("Sync error: " + message); }
        });
    }

    // ---- helpers ----

    private byte[] readUri(Uri uri, int cap) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int total = 0, n;
            while ((n = in.read(buf)) != -1) { total += n; bos.write(buf, 0, n); if (total > cap) break; }
            return bos.toByteArray();
        } catch (Exception e) { return null; }
    }

    private void toast(String s) { ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show()); }

    private void openMinimaCore() {
        Intent launch = MinimaAPI.launchNode(this);
        if (launch != null) startActivity(launch);
        else toast("Minima Core is not installed.");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (siteMode) {
            if (siteWeb.canGoBack()) siteWeb.goBack();
            else showHome();
        } else if (appWeb != null && appWeb.canGoBack()) {
            appWeb.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (notifyReceiver != null) try { unregisterReceiver(notifyReceiver); } catch (Exception ignored) {}
        if (bridge != null) bridge.shutdown();
        if (appBridge != null) appBridge.close();
        if (chain != null) chain.close();
        if (node != null) node.onDestroy();
        if (store != null) store.close();
        super.onDestroy();
    }
}
