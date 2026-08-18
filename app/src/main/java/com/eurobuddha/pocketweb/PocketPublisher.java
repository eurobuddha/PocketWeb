package com.eurobuddha.pocketweb;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Builds signed file packets for publishing — the create/update half of pocketFS, natively.
 *
 * A new site is owned by the app's own RSA key; an update keeps the same name/owner/randid (the name
 * commits to them) and bumps the version, re-signed with the held key. The 50 KB single-coin limit is
 * enforced here, matching the on-chain constraint (a larger packet would exceed the 64 KB TxPoW cap).
 */
public final class PocketPublisher {

    private PocketPublisher() {}

    public static final int MAX_ZIP_BYTES = 51200;   // 50 KB, matches pocketFS newfile.html

    private static final SecureRandom RNG = new SecureRandom();

    /** Create a brand-new site (version 1) owned by the app's own publishing key. */
    public static PocketWire.ParsedPacket createNew(PocketKeys keys, byte[] zipBytes, String description) throws IOException {
        checkSize(zipBytes);
        String owner = keys.ownOwnerHex();
        String randid = randomHex();
        String name = PocketProtocol.deriveName(randid, owner);
        PocketProtocol.Data d = new PocketProtocol.Data(
                name, owner, randid, description == null ? "" : description, 1, PocketProtocol.hexEncode(zipBytes));
        return new PocketWire.ParsedPacket(d, keys.signPacket(d));
    }

    /** Re-publish a site we own with new content/description at version+1 (same name/owner/randid). */
    public static PocketWire.ParsedPacket update(PocketKeys keys, PocketProtocol.Data existing,
                                                 byte[] newZipBytes, String newDescription) throws IOException {
        if (!keys.has(existing.owner))
            throw new IOException("no signing key held for this site's owner");
        checkSize(newZipBytes);
        PocketProtocol.Data d = new PocketProtocol.Data(
                existing.name, existing.owner, existing.randid,
                newDescription == null ? existing.description : newDescription,
                existing.version + 1, PocketProtocol.hexEncode(newZipBytes));
        return new PocketWire.ParsedPacket(d, keys.signPacket(d));
    }

    private static void checkSize(byte[] zip) throws IOException {
        if (zip == null || zip.length == 0) throw new IOException("empty site");
        if (zip.length > MAX_ZIP_BYTES)
            throw new IOException("site too big: " + zip.length + " bytes (max " + MAX_ZIP_BYTES + ")");
    }

    private static synchronized String randomHex() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return PocketProtocol.hexEncode(b);
    }
}
