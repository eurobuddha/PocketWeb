package com.eurobuddha.pocketweb;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Renders a verified on-chain site in a WebView, each site under its own https origin
 * (&lt;mx&gt;.miniweb.local). shouldInterceptRequest serves the site's own files and the shipped
 * /miniweb/ asset tree from local storage and refuses everything else — sites cannot read the
 * filesystem, other sites, or the app. Replaces the MDS extract→copytoweb→iframe machinery.
 *
 * A packet is verified (name commits to (randid,owner) AND signature) before extraction; only
 * verified sites are ever served (closing the JS "verify-absent-at-read" gap end to end).
 */
public class SiteServer {

    public interface Listener {
        void onPageStarted(String mx, String url);
        void onTitle(String title);
        /** The requested site is not in the store yet — caller may request it over the chain. */
        void onSiteMissing(String mx);
        void onError(String message);
    }

    private final Context ctx;
    private final WebView web;
    private final PocketStore store;
    private final File sitesDir;
    private Listener listener;
    private volatile String currentMx;
    private volatile File currentSiteDir;   // extraction dir of the currently loaded site

    public SiteServer(Context ctx, WebView web, PocketStore store) {
        this.ctx = ctx.getApplicationContext();
        this.web = web;
        this.store = store;
        this.sitesDir = new File(ctx.getCacheDir(), "sites");
        //noinspection ResultOfMethodCallIgnored
        this.sitesDir.mkdirs();
        configure();
    }

    public void setListener(Listener l) { this.listener = l; }

    @SuppressWarnings("SetJavaScriptEnabled")
    private void configure() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);                 // sites are apps; isolated by origin
        s.setDomStorageEnabled(true);                 // per-origin localStorage
        s.setAllowFileAccess(false);                  // no file:// reach
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setGeolocationEnabled(false);
        web.setWebViewClient(new Client());
    }

    /** Extract (if needed) and load a verified site. Reports onSiteMissing if we don't hold it. */
    public void loadSite(String mx) {
        PocketStore.Row row = store.get(mx);
        if (row == null || row.data.file == null) {
            if (listener != null) listener.onSiteMissing(mx);
            return;
        }
        if (!PocketProtocol.verifyPacket(row.data, row.signature)) {
            if (listener != null) listener.onError("Site failed verification: " + mx);
            return;
        }
        try {
            File dir = siteDir(mx, row.data.version);
            if (!dir.isDirectory()) {
                pruneOldVersions(mx, row.data.version);     // clear stale version dirs BEFORE staging tmp
                File tmp = new File(sitesDir, mx + ".v" + row.data.version + ".tmp");
                deleteRec(tmp);
                SiteExtractor.extractHex(row.data.file, tmp);
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(dir);
            }
            currentMx = mx;
            currentSiteDir = dir;
            web.loadUrl(MiniwebUrl.pageUrl(mx, "index.html"));
        } catch (SiteExtractor.UnsafeZipException e) {
            if (listener != null) listener.onError("Unsafe site content rejected: " + e.getMessage());
        } catch (IOException e) {
            if (listener != null) listener.onError("Could not extract site: " + e.getMessage());
        }
    }

    public String currentMx() { return currentMx; }

    // ---- request interception ----

    private final class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!MiniwebUrl.SCHEME.equals(uri.getScheme()) || !MiniwebUrl.isMiniwebHost(uri.getHost())) {
                // Not one of our origins — let the stack handle it (external https is governed elsewhere).
                return null;
            }
            MiniwebUrl.Route r = MiniwebUrl.route(uri.getHost(), uri.getEncodedPath());
            try {
                switch (r.kind) {
                    case SITE:
                        File dir = currentSiteDir;
                        if (dir == null || !r.mx.equals(currentMx)) return notFound();
                        return serveFile(new File(dir, r.relPath), r.relPath);
                    case ASSET: return serveAsset(r.relPath);
                    default:    return denied();
                }
            } catch (IOException e) {
                return notFound();
            }
        }

        @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
            if (listener != null) listener.onPageStarted(currentMx, url);
        }
    }

    private File siteDir(String mx, int version) { return new File(sitesDir, mx + ".v" + version); }

    /** Remove any extracted dirs / tmp for this mx other than the current version. */
    private void pruneOldVersions(String mx, int keepVersion) {
        File[] all = sitesDir.listFiles((d, n) -> n.startsWith(mx + ".v") || n.equals(mx));
        if (all == null) return;
        String keep = mx + ".v" + keepVersion;
        for (File f : all) if (!f.getName().equals(keep)) deleteRec(f);
    }

    private WebResourceResponse serveFile(File f, String relPath) throws IOException {
        File canon = f.getCanonicalFile();
        // defence in depth: never serve outside sitesDir even if routing were bypassed
        if (!canon.getPath().startsWith(sitesDir.getCanonicalPath() + File.separator) || !canon.isFile())
            return notFound();
        String mime = MimeTypes.forPath(relPath);
        return response(mime, new FileInputStream(canon));
    }

    private WebResourceResponse serveAsset(String relPath) throws IOException {
        AssetManager am = ctx.getAssets();
        String mime = MimeTypes.forPath(relPath);
        InputStream in = am.open("miniweb/" + relPath);   // AssetManager itself refuses ../ escapes
        return response(mime, in);
    }

    private WebResourceResponse response(String mime, InputStream in) {
        String enc = MimeTypes.isText(mime) ? "UTF-8" : null;
        WebResourceResponse resp = new WebResourceResponse(mime, enc, in);
        resp.setResponseHeaders(Collections.singletonMap("Cache-Control", "no-store"));
        return resp;
    }

    private WebResourceResponse denied() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                Collections.emptyMap(), new ByteArrayInputStream("blocked".getBytes(StandardCharsets.UTF_8)));
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
