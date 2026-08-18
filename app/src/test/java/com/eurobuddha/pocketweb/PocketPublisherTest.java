package com.eurobuddha.pocketweb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;

/** CI gate for the publish/update packet builder. */
public class PocketPublisherTest {

    private PocketKeys keys() throws Exception {
        return new PocketKeys(Files.createTempDirectory("pw-pub").toFile());
    }

    private static byte[] zip(int n) { byte[] b = new byte[n]; for (int i = 0; i < n; i++) b[i] = (byte) i; return b; }

    @Test public void createNewProducesVerifiablePacket() throws Exception {
        PocketKeys keys = keys();
        PocketWire.ParsedPacket p = PocketPublisher.createNew(keys, zip(2048), "my site");
        assertEquals(1, p.data.version);
        assertTrue(p.data.name.startsWith("Mx"));
        assertEquals(keys.ownOwnerHex(), p.data.owner);
        assertTrue("published packet verifies", PocketProtocol.verifyPacket(p.data, p.signature));
        // name must commit to (randid, owner)
        assertEquals(PocketProtocol.deriveName(p.data.randid, p.data.owner), p.data.name);
    }

    @Test public void updateBumpsVersionKeepsIdentityAndVerifies() throws Exception {
        PocketKeys keys = keys();
        PocketWire.ParsedPacket v1 = PocketPublisher.createNew(keys, zip(1000), "v1");
        PocketWire.ParsedPacket v2 = PocketPublisher.update(keys, v1.data, zip(1500), "v2");
        assertEquals(2, v2.data.version);
        assertEquals("same name across versions", v1.data.name, v2.data.name);
        assertEquals(v1.data.randid, v2.data.randid);
        assertEquals(v1.data.owner, v2.data.owner);
        assertNotEquals(v1.signature, v2.signature);
        assertTrue(PocketProtocol.verifyPacket(v2.data, v2.signature));
    }

    @Test public void rejectsOversizeAndForeignKey() throws Exception {
        PocketKeys keys = keys();
        try { PocketPublisher.createNew(keys, zip(PocketPublisher.MAX_ZIP_BYTES + 1), "big"); fail("expected size rejection"); }
        catch (Exception expected) { /* good */ }

        // updating a site whose owner key we do not hold must fail
        String foreignOwner = PocketProtocol.ownerHex(PocketProtocol.generateRsa1024().getPublic());
        PocketProtocol.Data foreign = new PocketProtocol.Data("MxX", foreignOwner, "0x00", "x", 3, "0x00");
        try { PocketPublisher.update(keys, foreign, zip(100), "y"); fail("expected foreign-key rejection"); }
        catch (Exception expected) { /* good */ }
    }
}
