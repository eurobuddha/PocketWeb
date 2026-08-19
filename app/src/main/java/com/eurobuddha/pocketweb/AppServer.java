package com.eurobuddha.pocketweb;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * Serves the app's OWN UI (your ported pocketWeb/pocketFS HTML + design) from assets at the trusted
 * origin app.pocketweb.local, with the privileged AppBridge attached. Kept strictly separate from
 * the untrusted-site WebView: this origin only ever loads bundled app-ui assets, so exposing the
 * powerful bridge here can never reach a visited site.
 *
 * A navigation to browse.html?mxsite=<mx> is intercepted and handed to the Navigator, which opens
 * that site in the sandboxed SiteServer WebView instead of loading anything here.
 */
public class AppServer {

    public static final String APP_HOST = "app.pocketweb.local";
    public static final String APP_ORIGIN = "https://" + APP_HOST;

    public interface Navigator { void openSite(String mx, String path); }
    /** Handles a WebView <input type=file> by opening SAF and delivering the picked uri(s). */
    public interface FileChooser { boolean onChoose(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params); }

    private final Context ctx;
    private final WebView web;
    private final Navigator navigator;
    private FileChooser fileChooser;

    public void setFileChooser(FileChooser fc) { this.fileChooser = fc; }

    public AppServer(Context ctx, WebView web, AppBridge bridge, Navigator navigator) {
        this.ctx = ctx.getApplicationContext();
        this.web = web;
        this.navigator = navigator;
        configure(bridge);
    }

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    private void configure(AppBridge bridge) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage: theme + client state
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        web.addJavascriptInterface(bridge, "PocketWebApp");
        web.setWebViewClient(new Client());
        // JS console + errors -> logcat (tag PocketWebJS) so rendering can be diagnosed remotely.
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("PocketWebJS", "[app] " + m.message() + " @" + m.sourceId() + ":" + m.lineNumber());
                return true;
            }
            @Override public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileChooser != null) return fileChooser.onChoose(cb, params);
                cb.onReceiveValue(null);
                return false;
            }
        });
    }

    public void loadHome() { web.loadUrl(APP_ORIGIN + "/index.html"); }
    public void load(String page) { web.loadUrl(APP_ORIGIN + "/" + page); }

    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isBrowseNav(uri)) {
                String mx = uri.getQueryParameter("mxsite");
                if (mx != null && mx.startsWith("Mx")) { navigator.openSite(mx, "index.html"); return true; }
            }
            // Only allow same-origin app-ui navigation; block anything else.
            return !(APP_HOST.equals(uri.getHost()) && MiniwebUrl.SCHEME.equals(uri.getScheme()));
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
            Log.w("PocketWebJS", "[app] resource error " + err.getErrorCode() + " for " + req.getUrl());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (!APP_HOST.equals(uri.getHost()) || !MiniwebUrl.SCHEME.equals(uri.getScheme())) return denied();
            if (isBrowseNav(uri)) return denied();   // handled by shouldOverrideUrlLoading
            String path = MiniwebUrl.decodeAndNormalize(uri.getEncodedPath());
            if (path == null) return denied();
            if (path.equals("/")) path = "/index.html";
            try {
                String assetPath = path.startsWith("/miniweb/")
                        ? "miniweb/" + path.substring("/miniweb/".length())   // shared tree
                        : "app-ui" + path;                                    // app UI files
                return serveAsset(assetPath, path);
            } catch (IOException e) {
                return notFound();
            }
        }
    }

    private static boolean isBrowseNav(Uri uri) {
        String p = uri.getPath();
        return p != null && p.endsWith("/browse.html") && uri.getQueryParameter("mxsite") != null;
    }

    private WebResourceResponse serveAsset(String assetPath, String logicalPath) throws IOException {
        AssetManager am = ctx.getAssets();
        String mime = MimeTypes.forPath(logicalPath);
        InputStream in = am.open(assetPath);   // AssetManager refuses traversal escapes itself
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
}
