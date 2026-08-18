package com.eurobuddha.pocketweb;

import java.net.InetAddress;
import java.net.URI;

/**
 * SSRF guard for the site NET_GET bridge.
 *
 * The JS version (browse.html) blocklisted host SUBSTRINGS ("10.", "192.168." …), which both
 * over-blocked (a "10." anywhere in the URL) and, more dangerously, MISSED the cloud-metadata
 * address 169.254.169.254 and the 172.16/12 private range (bug #1). This resolves the host and
 * rejects the request if ANY resolved address is loopback / link-local / site-local / any-local /
 * multicast — a real address check, not string matching.
 */
public final class HostGuard {

    private HostGuard() {}

    /** True if this address must never be fetched from (internal / metadata / loopback). */
    public static boolean isBlockedAddress(InetAddress a) {
        if (a == null) return true;
        if (a.isLoopbackAddress()      // 127.0.0.0/8, ::1
                || a.isLinkLocalAddress()  // 169.254.0.0/16 (incl. 169.254.169.254 metadata), fe80::/10
                || a.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || a.isAnyLocalAddress()   // 0.0.0.0, ::
                || a.isMulticastAddress())
            return true;
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int o0 = b[0] & 0xFF, o1 = b[1] & 0xFF;
            if (o0 == 127) return true;
            if (o0 == 169 && o1 == 254) return true;            // belt-and-braces link-local
            if (o0 == 100 && o1 >= 64 && o1 <= 127) return true; // 100.64/10 CGNAT
        } else if (b.length == 16) {
            if ((b[0] & 0xFE) == 0xFC) return true;             // fc00::/7 unique-local
        }
        return false;
    }

    /**
     * Whether a site is allowed to fetch this URL: must be https, have a host, and every resolved
     * address must be public. Resolution can block (DNS) so call off the main thread.
     */
    public static boolean allow(String urlStr) {
        if (urlStr == null) return false;
        final String s = urlStr.trim();
        final URI uri;
        try { uri = new URI(s); } catch (Exception e) { return false; }
        if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("https")) return false;
        String host = uri.getHost();
        if (host == null || host.isEmpty()) return false;
        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            if (all.length == 0) return false;
            for (InetAddress a : all) if (isBlockedAddress(a)) return false;   // any bad addr => refuse
            return true;
        } catch (Exception e) {
            return false;   // unresolvable => refuse
        }
    }
}
