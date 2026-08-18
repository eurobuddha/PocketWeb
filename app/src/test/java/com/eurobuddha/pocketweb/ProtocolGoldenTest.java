package com.eurobuddha.pocketweb;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

/**
 * Backward-compatibility acceptance gate (JVM unit test — no device/node).
 * Proves the native protocol core is byte-compatible with sites already published on-chain.
 * The runnable ProtocolSelfTest.main mirror prints a human-readable report.
 */
public class ProtocolGoldenTest {

    static final String[] REAL_NAMES = {
            "Mx200GZ47HZKRAZKC99PZQVKUD4ER2G78NN6BT",
            "Mx200GWBDG5JKC3HKQWREZRK8KNR8PKKTE9RRA",
    };

    /** The two real on-chain names decode + checksum-validate + round-trip (base32 + SHA3 + address). */
    @Test public void realOnChainNamesValidateAndRoundTrip() {
        for (String name : REAL_NAMES) {
            byte[] data = PocketProtocol.parseMxAddress(name);   // throws on bad SHA3 checksum
            assertEquals("16-byte id", 16, data.length);
            assertEquals("re-encode round-trips", name, PocketProtocol.makeMxAddress(data));
        }
    }

    @Test public void encodetextMatchesJs() {
        for (String[] u : GoldenData.ENCODE_UNITS) {
            assertEquals("encodetext " + u[0], u[1], PocketProtocol.encodetext(u[0]));
        }
    }

    @Test public void jsonStringifyAndPreimageMatchJs() {
        for (GoldenData.C c : GoldenData.CASES) {
            PocketProtocol.Data d = new PocketProtocol.Data(c.name, c.owner, c.randid, c.description, c.version, c.file);
            assertEquals("json v=" + c.version, c.json, PocketProtocol.jsonStringify(d));
            assertEquals("encodetext v=" + c.version, c.enc, PocketProtocol.encodetext(c.json));
            String pre = PocketProtocol.hexEncode(PocketProtocol.signingPreimage(d));
            String expect = c.preimageHex.equals("0x") ? "" : c.preimageHex;
            assertEquals("preimage v=" + c.version, expect, pre);
        }
    }

    @Test public void rsaSignVerifyIsDeterministicAndTamperEvident() {
        KeyPair kp = PocketProtocol.generateRsa1024();
        byte[] msg = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        byte[] s1 = PocketProtocol.sign(msg, kp.getPrivate());
        byte[] s2 = PocketProtocol.sign(msg, kp.getPrivate());
        assertEquals(128, s1.length);
        assertArrayEquals("PKCS#1 v1.5 deterministic", s1, s2);
        assertTrue(PocketProtocol.verify(msg, kp.getPublic(), s1));
        byte[] bad = msg.clone(); bad[0] ^= 1;
        assertFalse(PocketProtocol.verify(bad, kp.getPublic(), s1));
        assertEquals(kp.getPublic(), PocketProtocol.publicKeyFromHex(PocketProtocol.ownerHex(kp.getPublic())));
    }

    @Test public void fullPacketSignVerifyAndAntiForgery() {
        KeyPair kp = PocketProtocol.generateRsa1024();
        String owner = PocketProtocol.ownerHex(kp.getPublic());
        String randid = "0x8A1F2C3D4E5F60718293A4B5C6D7E8F9";
        String name = PocketProtocol.deriveName(randid, owner);
        assertTrue(name.startsWith("Mx"));
        PocketProtocol.parseMxAddress(name);   // valid checksum

        PocketProtocol.Data d = new PocketProtocol.Data(name, owner, randid, "café ☕ site", 1, "0x504B0304");
        String sig = PocketProtocol.signPacket(d, kp.getPrivate());
        assertTrue("valid packet verifies", PocketProtocol.verifyPacket(d, sig));

        PocketProtocol.Data bumped = new PocketProtocol.Data(name, owner, randid, d.description, 2, d.file);
        assertFalse("unsigned version bump rejected", PocketProtocol.verifyPacket(bumped, sig));

        PocketProtocol.Data spoof = new PocketProtocol.Data(REAL_NAMES[0], owner, randid, d.description, 1, d.file);
        assertFalse("name not committing to (randid,owner) rejected", PocketProtocol.verifyPacket(spoof, sig));
    }

    @Test public void verifyPacketIsTotalOnMalformedInput() {
        // Attacker crafts a name that DOES commit to a deliberately-invalid owner key, so the name
        // check passes and verification reaches publicKeyFromHex — which must not throw out of verifyPacket.
        String badOwner = "0xDEADBEEF";                 // not a valid X.509 RSA key
        String randid = "0x00112233445566778899AABBCCDDEEFF";
        String name = PocketProtocol.deriveName(randid, badOwner);
        PocketProtocol.Data d = new PocketProtocol.Data(name, badOwner, randid, "x", 1, "0x00");
        assertFalse("malformed owner -> false, not exception", PocketProtocol.verifyPacket(d, "0x00"));
        assertFalse("null-ish fields -> false", PocketProtocol.verifyPacket(
                new PocketProtocol.Data("MxX", "0x", "0x", "", 1, "0x"), "0x"));
    }

    @Test public void wireStateRoundTrips() {
        KeyPair kp = PocketProtocol.generateRsa1024();
        String owner = PocketProtocol.ownerHex(kp.getPublic());
        String randid = "0x1122334455667788990011223344556677";
        String name = PocketProtocol.deriveName(randid, owner);
        String desc = "brackets ] [ quote ' pct % café ☕";
        PocketProtocol.Data d = new PocketProtocol.Data(name, owner, randid, desc, 3, "0x504B0304ABCDEF");
        String sig = PocketProtocol.signPacket(d, kp.getPrivate());

        assertEquals("DB-encoded description escapes ']'", -1, PocketWire.encodeStringForDB(desc).indexOf(']'));
        assertEquals("DB round-trip", desc, PocketWire.decodeStringFromDB(PocketWire.encodeStringForDB(desc)));

        String[] slots = {
                "[" + name + "]", owner, randid,
                "[" + PocketWire.encodeStringForDB(desc) + "]", "3", d.file, sig
        };
        PocketWire.ParsedPacket pp = PocketWire.fromCoinState(slots);
        assertNotNull(pp);
        assertEquals(name, pp.data.name);
        assertEquals(desc, pp.data.description);
        assertEquals(3, pp.data.version);
        assertTrue("round-tripped packet verifies", PocketProtocol.verifyPacket(pp.data, pp.signature));
        assertNull(PocketWire.fromCoinState(new String[]{"[x]", "a", "b", "[c]", "1", "d"})); // missing slot 6
    }
}
