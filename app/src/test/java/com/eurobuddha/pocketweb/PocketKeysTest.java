package com.eurobuddha.pocketweb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyPair;

/** CI gate for PocketKeys: own-key persistence, Maxima import, sign/verify. */
public class PocketKeysTest {

    private PocketKeys fresh() throws Exception {
        return new PocketKeys(Files.createTempDirectory("pw-keys").toFile());
    }

    @Test public void ownKeyIsStableAndSigns() throws Exception {
        PocketKeys keys = fresh();
        String own = keys.ownOwnerHex();
        assertEquals("persisted across calls", own, keys.ownOwnerHex());
        assertTrue(keys.has(own));
        String randid = "0xAABBCCDDEEFF00112233445566778899";
        String name = PocketProtocol.deriveName(randid, own);
        PocketProtocol.Data d = new PocketProtocol.Data(name, own, randid, "own site", 1, "0x504B0304");
        assertTrue(PocketProtocol.verifyPacket(d, keys.signPacket(d)));
    }

    @Test public void importedMaximaKeyMatchesOwnerAndSigns() throws Exception {
        PocketKeys keys = fresh();
        KeyPair maxima = PocketProtocol.generateRsa1024();
        String expected = PocketProtocol.ownerHex(maxima.getPublic());
        String imported = keys.importPrivateKeyHex(PocketProtocol.hexEncode(maxima.getPrivate().getEncoded()));
        assertEquals("derived owner matches on-chain owner", expected, imported);
        String randid = "0x1122334455667788990011223344556677";
        String name = PocketProtocol.deriveName(randid, imported);
        PocketProtocol.Data d = new PocketProtocol.Data(name, imported, randid, "legacy edit", 4, "0x504B0304");
        assertTrue(PocketProtocol.verifyPacket(d, keys.signPacket(d)));
    }

    @Test public void rejectsBadImport() throws Exception {
        try { fresh().importPrivateKeyHex("0xDEADBEEF"); fail("expected rejection"); }
        catch (Exception expected) { /* good */ }
    }

    @Test public void doesNotSignForUnheldOwner() throws Exception {
        PocketKeys keys = fresh();
        String owner = PocketProtocol.ownerHex(PocketProtocol.generateRsa1024().getPublic());
        assertFalse(keys.has(owner));
        PocketProtocol.Data d = new PocketProtocol.Data("MxX", owner, "0x00", "x", 1, "0x00");
        try { keys.signPacket(d); fail("should not sign for a key we don't hold"); }
        catch (Exception expected) { /* good */ }
    }
}
