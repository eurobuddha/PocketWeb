package com.eurobuddha.pocketweb;

/** Runnable test for MiniwebUrl routing + MimeTypes (bare JDK). Traversal defence is security-critical. */
public class MiniwebUrlSelfTest {
    static int failures = 0;
    static void check(boolean c, String m) { System.out.println((c ? "  PASS  " : "  FAIL  ") + m); if (!c) failures++; }

    static final String MX = "Mx200GZ47HZKRAZKC99PZQVKUD4ER2G78NN6BT";
    static final String HOST = MX.toLowerCase() + ".miniweb.local";

    public static void main(String[] args) {
        System.out.println("== MiniwebUrl routing ==");

        MiniwebUrl.Route r = MiniwebUrl.route(HOST, "/mxsites/" + MX + "/index.html");
        check(r.kind == MiniwebUrl.Kind.SITE && r.mx.equals(MX) && r.relPath.equals("index.html"), "site index route");

        r = MiniwebUrl.route(HOST, "/mxsites/" + MX + "/sub/page.html?x=1#f");
        check(r.kind == MiniwebUrl.Kind.SITE && r.relPath.equals("sub/page.html"), "site subpath, query+frag stripped");

        r = MiniwebUrl.route(HOST, "/miniweb/libs/jquery/jquery-3.7.1.min.js");
        check(r.kind == MiniwebUrl.Kind.ASSET && r.relPath.equals("libs/jquery/jquery-3.7.1.min.js"), "asset route");

        // the pageUrl a site is loaded at must route back to itself
        String url = MiniwebUrl.pageUrl(MX, null);
        check(url.equals("https://" + HOST + "/mxsites/" + MX + "/index.html"), "pageUrl shape: " + url);

        System.out.println("== traversal / isolation defence ==");
        check(MiniwebUrl.route(HOST, "/mxsites/" + MX + "/../../../etc/passwd").kind == MiniwebUrl.Kind.BLOCKED, "blocks .. traversal");
        check(MiniwebUrl.route(HOST, "/miniweb/%2e%2e/secret").kind == MiniwebUrl.Kind.BLOCKED, "blocks percent-encoded ..");
        check(MiniwebUrl.route(HOST, "/miniweb/..%2fsecret").kind == MiniwebUrl.Kind.BLOCKED, "blocks encoded slash traversal");
        check(MiniwebUrl.route(HOST, "/mxsites/OTHERmx/x.html").kind == MiniwebUrl.Kind.BLOCKED, "blocks cross-origin site (mx != host)");
        check(MiniwebUrl.route("evil.example.com", "/mxsites/" + MX + "/x").kind == MiniwebUrl.Kind.BLOCKED, "blocks non-miniweb host");
        check(MiniwebUrl.route(HOST, "/etc/passwd").kind == MiniwebUrl.Kind.BLOCKED, "blocks arbitrary path");
        check(MiniwebUrl.route(HOST, "/mxsites/" + MX + "/a\\b").kind == MiniwebUrl.Kind.BLOCKED, "blocks backslash");

        System.out.println("== MimeTypes ==");
        check(MimeTypes.forPath("a/b/index.html").equals("text/html"), "html mime");
        check(MimeTypes.forPath("style.css").equals("text/css"), "css mime");
        check(MimeTypes.forPath("logo.svg").equals("image/svg+xml"), "svg mime");
        check(MimeTypes.forPath("bg.jpg").equals("image/jpeg"), "jpg mime");
        check(MimeTypes.forPath("f.woff2").equals("font/woff2"), "woff2 mime");
        check(MimeTypes.forPath("noext").equals("application/octet-stream"), "no-ext default");
        check(MimeTypes.isText("text/html") && !MimeTypes.isText("image/png"), "isText classification");

        System.out.println();
        if (failures == 0) System.out.println("MiniwebUrl/MimeTypes: ALL CHECKS PASSED.");
        else { System.out.println(failures + " FAILURE(S)."); System.exit(1); }
    }
}
