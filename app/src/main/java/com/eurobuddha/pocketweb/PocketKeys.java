package com.eurobuddha.pocketweb;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Identity/key management for publishing.
 *
 * The Android node has no Maxima subsystem, so it cannot sign for us: the app holds its own
 * RSA-1024 key(s) and signs locally (SHA256withRSA, matching maxsign). Verifying/browsing every
 * existing on-chain site needs no key at all — only publishing does.
 *
 * Two kinds of identity live here, both persisted app-private (getFilesDir → excluded from backup):
 *   - the app's OWN generated key (for new sites), and
 *   - imported Maxima private keys (PKCS#8), so a user keeps control of sites they published from
 *     the MiniDapp and can still edit/re-publish them under the same Mx identity.
 *
 * Keys are stored as PKCS#8 hex, one file per identity named by a digest of its owner (X.509) hex.
 * baseDir is injected so this is unit-testable off-device.
 */
public final class PocketKeys {

    private final File dir;

    public PocketKeys(File baseDir) {
        this.dir = new File(baseDir, "keys");
        //noinspection ResultOfMethodCallIgnored
        this.dir.mkdirs();
    }

    /** The app's own publishing key — generated and persisted on first use. Returns its owner hex. */
    public synchronized String ownOwnerHex() throws IOException {
        File marker = new File(dir, "own.owner");
        if (marker.isFile()) {
            return new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
        }
        KeyPair kp = PocketProtocol.generateRsa1024();
        String owner = PocketProtocol.ownerHex(kp.getPublic());
        store(owner, kp.getPrivate());
        Files.write(marker.toPath(), owner.getBytes(StandardCharsets.UTF_8));
        return owner;
    }

    /** The identity new sites are published under: an imported Maxima key if set active, else our own. */
    public synchronized String activeOwnerHex() throws IOException {
        File marker = new File(dir, "active.owner");
        if (marker.isFile()) {
            String o = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
            if (has(o)) return o;
        }
        return ownOwnerHex();
    }

    public synchronized void setActive(String ownerHex) throws IOException {
        if (!has(ownerHex)) throw new IOException("key not held: " + ownerHex);
        Files.write(new File(dir, "active.owner").toPath(), ownerHex.getBytes(StandardCharsets.UTF_8));
    }

    public PrivateKey activePrivateKey() throws IOException { return privateKeyFor(activeOwnerHex()); }

    /**
     * Import a Maxima RSA private key (PKCS#8, as "0x"+hex or bare hex). Returns the owner hex of the
     * corresponding public key, which is what on-chain packets carry. Throws if it is not an RSA key.
     * The imported key becomes the ACTIVE signing identity so sites publish under the user's own Maxima key.
     */
    public synchronized String importPrivateKeyHex(String pkcs8Hex) throws IOException {
        byte[] der = PocketProtocol.hexDecode(pkcs8Hex.trim());
        PrivateKey priv;
        try {
            priv = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IOException("not a valid RSA PKCS#8 private key", e);
        }
        if (!(priv instanceof RSAPrivateCrtKey))
            throw new IOException("private key lacks CRT params — cannot derive its public key");
        PublicKey pub = derivePublic((RSAPrivateCrtKey) priv);
        String owner = PocketProtocol.ownerHex(pub);
        store(owner, priv);
        setActive(owner);
        return owner;
    }

    /** All identities we can sign as (own + imported), by owner hex. */
    public synchronized List<String> owners() {
        List<String> out = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".pk8"));
        if (files == null) return out;
        for (File f : files) {
            try {
                String[] parts = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n", 2);
                if (parts.length == 2) out.add(parts[0]);
            } catch (IOException ignored) {}
        }
        return out;
    }

    public synchronized boolean has(String ownerHex) { return keyFile(ownerHex).isFile(); }

    /** Load the private key for an owner, or null if we don't hold it. */
    public synchronized PrivateKey privateKeyFor(String ownerHex) {
        File f = keyFile(ownerHex);
        if (!f.isFile()) return null;
        try {
            String[] parts = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n", 2);
            return PocketProtocol.privateKeyFromHex(parts[1].trim());
        } catch (Exception e) { return null; }
    }

    /** Sign a packet as its stated owner. Throws if we don't hold that key. */
    public String signPacket(PocketProtocol.Data d) throws IOException {
        PrivateKey priv = privateKeyFor(d.owner);
        if (priv == null) throw new IOException("no private key held for owner " + d.owner);
        return PocketProtocol.signPacket(d, priv);
    }

    // ---- internals ----

    private void store(String ownerHex, PrivateKey priv) throws IOException {
        String body = ownerHex + "\n" + PocketProtocol.hexEncode(priv.getEncoded());
        Files.write(keyFile(ownerHex).toPath(), body.getBytes(StandardCharsets.UTF_8));
    }

    private File keyFile(String ownerHex) {
        // name by a SHA3 digest of the owner hex so the filename is fixed-length and filesystem-safe.
        String id = PocketProtocol.hexEncode(PocketProtocol.sha3(ownerHex.getBytes(StandardCharsets.UTF_8)))
                .substring(2, 18);
        return new File(dir, id + ".pk8");
    }

    private static PublicKey derivePublic(RSAPrivateCrtKey crt) throws IOException {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (Exception e) {
            throw new IOException("cannot derive public key", e);
        }
    }
}
