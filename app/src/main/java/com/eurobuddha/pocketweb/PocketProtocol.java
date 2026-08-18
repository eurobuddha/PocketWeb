package com.eurobuddha.pocketweb;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Byte-for-byte reimplementation of the Minima node primitives that PocketWeb's
 * pocketFS/js/auth.js relies on, so the native app stays backward compatible with
 * every site already published on-chain.
 *
 * Traced from core/Minima: BaseConverter, Address.makeMinimaAddress, Crypto.hashData
 * (BouncyCastle SHA3Digest(256) == FIPS-202 SHA3-256), SignVerify (SHA256withRSA over
 * RSA-1024, X.509 pub / PKCS#8 priv, deterministic PKCS#1 v1.5).
 *
 * Pure Java (java.security + BigInteger) — no Android APIs — so it compiles and runs
 * under a bare JDK for the golden-vector test AND on-device unchanged.
 */
public final class PocketProtocol {

    private PocketProtocol() {}

    // ---- filepacket data (insertion order IS the signed order) ----
    public static final class Data {
        public final String name, owner, randid, description, file;
        public final int version;
        public Data(String name, String owner, String randid, String description, int version, String file) {
            this.name = name; this.owner = owner; this.randid = randid;
            this.description = description; this.version = version; this.file = file;
        }
    }

    // ===================== hex (BaseConverter.encode16/decode16) =====================
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /** bytes -> "0x" + UPPERCASE hex (empty bytes -> ""). */
    public static String hexEncode(byte[] b) {
        if (b.length == 0) return "";
        char[] c = new char[b.length * 2];
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xFF;
            c[i * 2] = HEX[v >>> 4];
            c[i * 2 + 1] = HEX[v & 0x0F];
        }
        return "0x" + new String(c);
    }

    /** "0x.."/"0X.."/bare hex -> bytes; odd length left-padded with a leading 0 (matches decode16). */
    public static byte[] hexDecode(String s) {
        String h = s;
        if (h.startsWith("0x") || h.startsWith("0X")) h = h.substring(2);
        h = h.toUpperCase();
        if (h.isEmpty()) return new byte[0];
        if ((h.length() & 1) == 1) h = "0" + h;
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) ((Character.digit(h.charAt(i * 2), 16) << 4)
                    | Character.digit(h.charAt(i * 2 + 1), 16));
        }
        return out;
    }

    // ===================== SHA3-256 (Crypto.hashData) =====================
    /** FIPS-202 SHA3-256, 32 bytes. On Android (minSdk 28) the platform provider supplies it;
     *  identical output to core's BouncyCastle SHA3Digest(256). */
    public static byte[] sha3(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA3-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA3-256 unavailable", e);
        }
    }

    private static byte[] utf8(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    // ===================== Base32 "Mx" (BaseConverter.encode32/decode32) =====================
    /** big-endian unsigned int -> radix-32 (0-9a-v), swap i->w l->y o->z, uppercase, "Mx" prefix. */
    public static String base32(byte[] data) {
        BigInteger big = new BigInteger(1, data);
        String s = big.toString(32).toLowerCase();
        s = s.replace("i", "w").replace("l", "y").replace("o", "z");
        return "Mx" + s.toUpperCase();
    }

    /** reverse of base32(): "Mx.."/bare -> bytes. */
    public static byte[] base32decode(String mx) {
        String b = mx.toLowerCase();
        if (b.startsWith("mx")) b = b.substring(2);
        b = b.replace("w", "i").replace("y", "l").replace("z", "o");
        BigInteger big = new BigInteger(b, 32);
        return hexDecode("0x" + big.toString(16));
    }

    // ===================== Address.makeMinimaAddress / convertMinimaAddress =====================
    /** data -> "Mx" address: [0x01][u16-be len][data][SHA3-256(data)[0..4]] then base32. */
    public static String makeMxAddress(byte[] data) {
        byte[] hash = sha3(data);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(1);                          // non-zero leader (prevents base32 truncation)
        bos.write((data.length >>> 8) & 0xFF); // writeShort big-endian
        bos.write(data.length & 0xFF);
        bos.write(data, 0, data.length);
        bos.write(hash, 0, 4);                 // 4-byte checksum
        return base32(bos.toByteArray());
    }

    /** parse + validate an Mx address; returns the embedded data. Throws on bad structure/checksum. */
    public static byte[] parseMxAddress(String mx) {
        byte[] raw = base32decode(mx);
        if (raw.length < 7 || (raw[0] & 0xFF) != 1)
            throw new IllegalArgumentException("bad Mx leader");
        int len = ((raw[1] & 0xFF) << 8) | (raw[2] & 0xFF);
        if (raw.length != 3 + len + 4)
            throw new IllegalArgumentException("bad Mx length: field=" + len + " raw=" + raw.length);
        byte[] data = new byte[len];
        System.arraycopy(raw, 3, data, 0, len);
        byte[] hash = sha3(data);
        for (int i = 0; i < 4; i++)
            if (hash[i] != raw[3 + len + i]) throw new IllegalArgumentException("bad Mx checksum");
        return data;
    }

    // ===================== name derivation (auth.js createNewFilePacket) =====================
    /**
     * name = Mx( SHA3-256( randbytes ++ ownerbytes )[0..16] ).
     * In auth.js: tot = randid("0x..") + owner.substring(2); "hash data:tot" starts with 0x so it is
     * parsed as HEX -> randbytes++ownerbytes. shorthash = first 16 bytes; convert to:mx.
     */
    public static String deriveName(String randidHex, String ownerHex) {
        byte[] rand = hexDecode(randidHex);
        byte[] owner = hexDecode(ownerHex);
        byte[] tot = new byte[rand.length + owner.length];
        System.arraycopy(rand, 0, tot, 0, rand.length);
        System.arraycopy(owner, 0, tot, rand.length, owner.length);
        byte[] h = sha3(tot);
        byte[] first16 = new byte[16];
        System.arraycopy(h, 0, first16, 0, 16);
        return makeMxAddress(first16);
    }

    // ===================== JSON.stringify (JS semantics, fixed key order) =====================
    public static String jsonStringify(Data d) {
        StringBuilder b = new StringBuilder();
        b.append('{');
        b.append("\"name\":").append(jsonString(d.name)).append(',');
        b.append("\"owner\":").append(jsonString(d.owner)).append(',');
        b.append("\"randid\":").append(jsonString(d.randid)).append(',');
        b.append("\"description\":").append(jsonString(d.description)).append(',');
        b.append("\"version\":").append(d.version).append(',');   // bare number
        b.append("\"file\":").append(jsonString(d.file));
        b.append('}');
        return b.toString();
    }

    /** JS JSON.stringify string escaping: " \ \b \t \n \f \r, other <0x20 as \\u00xx; '/' NOT escaped; non-ASCII raw. */
    private static String jsonString(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\b': b.append("\\b"); break;
                case '\t': b.append("\\t"); break;
                case '\n': b.append("\\n"); break;
                case '\f': b.append("\\f"); break;
                case '\r': b.append("\\r"); break;
                default:
                    if (c < 0x20) {
                        b.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int k = hex.length(); k < 4; k++) b.append('0');
                        b.append(hex);
                    } else {
                        b.append(c);   // includes '/' and all non-ASCII, emitted raw
                    }
            }
        }
        b.append('"');
        return b.toString();
    }

    // ===================== encodetext (encodeURIComponent + ' -> %27) =====================
    // Unreserved for encodeURIComponent: A-Za-z0-9 and - _ . ! ~ * ' ( ). auth.js then escapes ' to %27,
    // so here ' is NOT in the unreserved set (it becomes %27 directly).
    private static boolean unreserved(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_' || c == '.' || c == '!' || c == '~' || c == '*'
                || c == '(' || c == ')';
    }

    public static String encodetext(String s) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp <= 0x7F && unreserved((char) cp)) {
                b.append((char) cp);
            } else {
                byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                for (byte by : bytes) {
                    b.append('%');
                    b.append(HEX[(by >>> 4) & 0x0F]);
                    b.append(HEX[by & 0x0F]);
                }
            }
        }
        return b.toString();
    }

    // ===================== signing pre-image =====================
    /** The exact bytes maxsign signs: UTF-8 of encodetext(JSON.stringify(data)). */
    public static byte[] signingPreimage(Data d) {
        return utf8(encodetext(jsonStringify(d)));
    }

    // ===================== RSA (GenerateKey / SignVerify) =====================
    public static final String SIGN_ALGO = "SHA256withRSA";

    public static KeyPair generateRsa1024() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(1024, new SecureRandom());
            return g.generateKeyPair();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** owner hex ("0x"+X.509 DER) -> PublicKey. */
    public static PublicKey publicKeyFromHex(String ownerHex) {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(hexDecode(ownerHex)));
        } catch (Exception e) { throw new IllegalArgumentException("bad owner key", e); }
    }

    public static PrivateKey privateKeyFromHex(String privHex) {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(hexDecode(privHex)));
        } catch (Exception e) { throw new IllegalArgumentException("bad private key", e); }
    }

    /** owner field for a keypair we hold: "0x"+hex of X.509 DER (matches maxima publickey). */
    public static String ownerHex(PublicKey pub) { return hexEncode(pub.getEncoded()); }

    public static byte[] sign(byte[] preimage, PrivateKey priv) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALGO);
            sig.initSign(priv, new SecureRandom());
            sig.update(preimage);
            return sig.sign();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static boolean verify(byte[] preimage, PublicKey pub, byte[] signature) {
        try {
            Signature sig = Signature.getInstance(SIGN_ALGO);
            sig.initVerify(pub);
            sig.update(preimage);
            return sig.verify(signature);
        } catch (Exception e) { return false; }
    }

    // ---- high-level: sign a packet with a held key, and verify a received packet ----

    /** Produce the "0x" signature string for a packet, signing with priv. */
    public static String signPacket(Data d, PrivateKey priv) {
        return hexEncode(sign(signingPreimage(d), priv));
    }

    /**
     * Full verification as pocketFS does it: (1) name commits to (randid, owner),
     * (2) SHA256withRSA signature over the canonical pre-image checks out.
     */
    public static boolean verifyPacket(Data d, String signatureHex) {
        if (!deriveName(d.randid, d.owner).equals(d.name)) return false;
        return verify(signingPreimage(d), publicKeyFromHex(d.owner), hexDecode(signatureHex));
    }
}
