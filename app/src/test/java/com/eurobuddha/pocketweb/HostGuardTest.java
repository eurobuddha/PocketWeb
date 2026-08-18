package com.eurobuddha.pocketweb;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;

/** CI gate for the SSRF guard — the ranges the JS substring blocklist missed. */
public class HostGuardTest {

    private static boolean blocked(String ip) throws Exception {
        return HostGuard.isBlockedAddress(InetAddress.getByName(ip));
    }

    @Test public void blocksInternalAndMetadataRanges() throws Exception {
        assertTrue("cloud metadata", blocked("169.254.169.254"));
        assertTrue("172.16/12 start", blocked("172.16.0.1"));
        assertTrue("172.16/12 end", blocked("172.31.255.255"));
        assertTrue("loopback", blocked("127.0.0.1"));
        assertTrue("10/8", blocked("10.1.2.3"));
        assertTrue("192.168/16", blocked("192.168.0.1"));
        assertTrue("cgnat", blocked("100.64.0.1"));
        assertTrue("ipv6 loopback", blocked("::1"));
        assertTrue("ipv6 ula", blocked("fc00::1"));
        assertTrue("ipv6 linklocal", blocked("fe80::1"));
    }

    @Test public void allowsPublic() throws Exception {
        assertFalse(blocked("8.8.8.8"));
        assertFalse(blocked("1.1.1.1"));
    }

    @Test public void allowRejectsNonHttpsAndLoopback() {
        assertFalse(HostGuard.allow("http://example.com/x"));
        assertFalse(HostGuard.allow("https://127.0.0.1/x"));
        assertFalse(HostGuard.allow("ftp://example.com"));
        assertFalse(HostGuard.allow("garbage"));
    }
}
