package com.eurobuddha.pocketweb;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyPair;
import java.util.List;

/** Runnable test for PocketKeys (bare JDK, temp dir). */
public class PocketKeysSelfTest {
    static int failures = 0;
    static void check(boolean c, String m) { System.out.println((c ? "  PASS  " : "  FAIL  ") + m); if (!c) failures++; }

    public static void main(String[] args) throws Exception {
        File base = Files.createTempDirectory("pw-keys").toFile();
        PocketKeys keys = new PocketKeys(base);

        System.out.println("== own key ==");
        String own = keys.ownOwnerHex();
        check(own.startsWith("0x"), "own owner hex generated");
        check(own.equals(keys.ownOwnerHex()), "own owner is stable across calls (persisted)");
        check(keys.has(own), "own key is held");

        // sign a real packet under the own key and verify end-to-end
        String randid = "0xAABBCCDDEEFF00112233445566778899";
        String name = PocketProtocol.deriveName(randid, own);
        PocketProtocol.Data d = new PocketProtocol.Data(name, own, randid, "own site", 1, "0x504B0304");
        String sig = keys.signPacket(d);
        check(PocketProtocol.verifyPacket(d, sig), "packet signed by own key verifies");

        System.out.println("== import Maxima key ==");
        KeyPair maxima = PocketProtocol.generateRsa1024();          // simulate an existing Maxima keypair
        String expectedOwner = PocketProtocol.ownerHex(maxima.getPublic());
        String pkcs8Hex = PocketProtocol.hexEncode(maxima.getPrivate().getEncoded());
        String imported = keys.importPrivateKeyHex(pkcs8Hex);
        check(imported.equals(expectedOwner), "imported owner == original public key's owner hex (derive-public matches)");
        check(keys.has(imported), "imported key is held");

        // now we can sign/edit a site under the imported Maxima identity
        String mname = PocketProtocol.deriveName(randid, imported);
        PocketProtocol.Data md = new PocketProtocol.Data(mname, imported, randid, "edited legacy site", 4, "0x504B0304");
        String msig = keys.signPacket(md);
        check(PocketProtocol.verifyPacket(md, msig), "packet signed by imported key verifies");

        List<String> owners = keys.owners();
        check(owners.contains(own) && owners.contains(imported), "owners() lists both identities (" + owners.size() + ")");

        // reject junk import
        boolean rejected = false;
        try { keys.importPrivateKeyHex("0xDEADBEEF"); } catch (Exception e) { rejected = true; }
        check(rejected, "rejects a non-PKCS8 blob");

        System.out.println();
        if (failures == 0) System.out.println("PocketKeys: ALL CHECKS PASSED.");
        else { System.out.println(failures + " FAILURE(S)."); System.exit(1); }
    }
}
