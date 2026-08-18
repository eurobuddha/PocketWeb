package com.eurobuddha.pocketweb;

import java.net.InetAddress;

/** Runnable SSRF-range test for HostGuard (bare JDK; IP literals need no DNS). */
public class HostGuardSelfTest {
    static int failures = 0;
    static void check(boolean c, String m) { System.out.println((c ? "  PASS  " : "  FAIL  ") + m); if (!c) failures++; }

    static boolean blocked(String ip) throws Exception { return HostGuard.isBlockedAddress(InetAddress.getByName(ip)); }

    public static void main(String[] args) throws Exception {
        System.out.println("== HostGuard address ranges ==");
        // the exact addresses the JS substring blocklist MISSED
        check(blocked("169.254.169.254"), "blocks cloud metadata 169.254.169.254 (JS missed this)");
        check(blocked("172.16.0.1"), "blocks 172.16/12 (JS missed this)");
        check(blocked("172.31.255.255"), "blocks 172.31 end of range");
        // ranges the JS blocklist did cover
        check(blocked("127.0.0.1"), "blocks loopback");
        check(blocked("10.0.0.1"), "blocks 10/8");
        check(blocked("192.168.1.1"), "blocks 192.168/16");
        check(blocked("0.0.0.0"), "blocks 0.0.0.0");
        check(blocked("::1"), "blocks ipv6 loopback");
        check(blocked("fc00::1"), "blocks ipv6 unique-local");
        check(blocked("fe80::1"), "blocks ipv6 link-local");
        check(blocked("100.64.0.1"), "blocks CGNAT 100.64/10");
        // public addresses allowed
        check(!blocked("8.8.8.8"), "allows public 8.8.8.8");
        check(!blocked("1.1.1.1"), "allows public 1.1.1.1");

        System.out.println("== HostGuard.allow scheme checks ==");
        check(!HostGuard.allow("http://example.com/x"), "rejects http (not https)");
        check(!HostGuard.allow("https://127.0.0.1/x"), "rejects https to loopback literal");
        check(!HostGuard.allow("ftp://example.com"), "rejects ftp");
        check(!HostGuard.allow("not a url"), "rejects garbage");

        System.out.println();
        if (failures == 0) System.out.println("HostGuard: ALL CHECKS PASSED.");
        else { System.out.println(failures + " FAILURE(S)."); System.exit(1); }
    }
}
