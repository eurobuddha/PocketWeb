package com.eurobuddha.pocketweb;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Minimal shell for the PocketWeb native companion app.
 *
 * This session ships the backward-compat protocol core (PocketProtocol / PocketWire), proven
 * byte-compatible with existing on-chain sites by the golden-vector test. The full UI — Browse
 * (WebView + shouldInterceptRequest), Catalog/Search, My Sites, Build, Publish — and the
 * PocketStore (SQLite+FTS) + chain poller are the next increments (see README + the plan).
 *
 * For now this wires pairing + a live IPC status probe so the scaffold runs end-to-end against
 * a node and shows the approve-me banner (browsing needs "enabled"; publishing needs ADMIN).
 */
public class MainActivity extends AppCompatActivity {

    public static final String NODE_PKG = "org.minimarex.minimacore";

    private NodeApi node;
    private TextView banner;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        banner = findViewById(R.id.pairingBanner);
        status = findViewById(R.id.status);
        banner.setOnClickListener(v -> openMinimaCore());

        node = new NodeApi(this, enabled -> {
            banner.setVisibility(enabled ? View.GONE : View.VISIBLE);
            if (enabled) probeStatus();
        });
    }

    private void probeStatus() {
        node.cmd("status", new NodeApi.Cb() {
            @Override public void onResult(org.json.JSONObject json) {
                org.json.JSONObject r = json.optJSONObject("response");
                String chain = r != null ? r.optString("chain", "?") : "?";
                status.setText("Connected to Minima Core.\nProtocol core loaded — verify/publish ready.\nchain: " + chain);
            }
            @Override public void onError(String message) {
                status.setText("Node error: " + message);
            }
        });
    }

    private void openMinimaCore() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(NODE_PKG);
        if (launch != null) startActivity(launch);
        else Toast.makeText(this, "Minima Core is not installed.", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        if (node != null) node.onDestroy();
        super.onDestroy();
    }
}
