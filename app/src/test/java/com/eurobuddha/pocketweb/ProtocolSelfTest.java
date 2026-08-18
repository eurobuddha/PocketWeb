package com.eurobuddha.pocketweb;

import java.security.KeyPair;
import java.util.Arrays;

/**
 * Backward-compat acceptance gate for PocketProtocol. Runnable with a bare JDK:
 *   javac -d out .../PocketProtocol.java GoldenData.java ProtocolSelfTest.java
 *   java  -cp out com.eurobuddha.pocketweb.ProtocolSelfTest
 * Exits non-zero on any failure. Mirrored as a JUnit test for CI.
 */
public class ProtocolSelfTest {

    // Two REAL published site names present in the repo (mds/PocketWeb/Mx*.zip).
    // Their Mx encoding carries a self-validating SHA3-256 checksum, so decoding them
    // proves base32 + SHA3 + address format against genuine on-chain data.
    static final String[] REAL_NAMES = {
            "Mx200GZ47HZKRAZKC99PZQVKUD4ER2G78NN6BT",
            "Mx200GWBDG5JKC3HKQWREZRK8KNR8PKKTE9RRA",
    };

    static int failures = 0;
    static void check(boolean cond, String msg) {
        if (cond) { System.out.println("  PASS  " + msg); }
        else { System.out.println("  FAIL  " + msg); failures++; }
    }

    public static void main(String[] args) {
        System.out.println("== T1/T2: Mx base32 + SHA3 + address vs REAL on-chain names ==");
        for (String name : REAL_NAMES) {
            try {
                byte[] data = PocketProtocol.parseMxAddress(name);   // throws on bad checksum
                check(data.length == 16, name + " embeds 16-byte id (len=" + data.length + ")");
                String reenc = PocketProtocol.makeMxAddress(data);
                check(reenc.equals(name), "re-encode round-trips: " + name);
            } catch (Exception e) {
                check(false, name + " parse/validate: " + e.getMessage());
            }
        }

        System.out.println("== T3a: encodeURIComponent (encodetext) unit cases vs JS ==");
        for (String[] u : GoldenData.ENCODE_UNITS) {
            String got = PocketProtocol.encodetext(u[0]);
            check(got.equals(u[1]), "encodetext(" + jshow(u[0]) + ") = " + jshow(u[1]) + " (got " + jshow(got) + ")");
        }

        System.out.println("== T3b: JSON.stringify + encodetext + preimage-hex vs JS golden ==");
        for (GoldenData.C c : GoldenData.CASES) {
            PocketProtocol.Data d = new PocketProtocol.Data(c.name, c.owner, c.randid, c.description, c.version, c.file);
            String json = PocketProtocol.jsonStringify(d);
            check(json.equals(c.json), "jsonStringify matches (v=" + c.version + ")");
            String enc = PocketProtocol.encodetext(json);
            check(enc.equals(c.enc), "encodetext matches (v=" + c.version + ")");
            String pre = PocketProtocol.hexEncode(PocketProtocol.signingPreimage(d));
            String expected = c.preimageHex.isEmpty() ? "" : c.preimageHex;
            // hexEncode("") == "" but golden preimage for empty is "0x" of empty-> "" ; normalise
            check(pre.equalsIgnoreCase(expected) || (pre.isEmpty() && expected.equals("0x")),
                    "preimage hex matches (v=" + c.version + ")");
        }

        System.out.println("== T4: RSA-1024 SHA256withRSA sign/verify/determinism/tamper ==");
        try {
            KeyPair kp = PocketProtocol.generateRsa1024();
            check(kp.getPublic().getAlgorithm().equals("RSA"), "generated RSA key");
            byte[] msg = "the quick brown fox".getBytes("UTF-8");
            byte[] s1 = PocketProtocol.sign(msg, kp.getPrivate());
            byte[] s2 = PocketProtocol.sign(msg, kp.getPrivate());
            check(s1.length == 128, "signature is 128 bytes (RSA-1024): " + s1.length);
            check(Arrays.equals(s1, s2), "PKCS#1 v1.5 signature is deterministic");
            check(PocketProtocol.verify(msg, kp.getPublic(), s1), "verify accepts good signature");
            byte[] bad = msg.clone(); bad[0] ^= 1;
            check(!PocketProtocol.verify(bad, kp.getPublic(), s1), "verify rejects tampered message");
            // owner hex round-trips through the key factory
            String ownerHex = PocketProtocol.ownerHex(kp.getPublic());
            check(PocketProtocol.publicKeyFromHex(ownerHex).equals(kp.getPublic()), "owner X.509 hex round-trips");
        } catch (Exception e) {
            check(false, "RSA block threw: " + e);
        }

        System.out.println("== T5: deriveName -> valid Mx (checksum) + T6 full packet round-trip ==");
        try {
            KeyPair kp = PocketProtocol.generateRsa1024();
            String owner = PocketProtocol.ownerHex(kp.getPublic());
            String randid = "0x8A1F2C3D4E5F60718293A4B5C6D7E8F9";
            String name = PocketProtocol.deriveName(randid, owner);
            check(name.startsWith("Mx"), "derived name has Mx prefix: " + name);
            PocketProtocol.parseMxAddress(name);   // throws if checksum/structure invalid
            check(true, "derived name passes address checksum");

            PocketProtocol.Data d = new PocketProtocol.Data(name, owner, randid,
                    "my first pocket site — café ☕", 1, "0x504B0304"); // 'PK\3\4' zip magic
            String sig = PocketProtocol.signPacket(d, kp.getPrivate());
            check(PocketProtocol.verifyPacket(d, sig), "self-signed packet verifies end-to-end");

            // tamper: bump version without re-sign -> must fail
            PocketProtocol.Data d2 = new PocketProtocol.Data(name, owner, randid, d.description, 2, d.file);
            check(!PocketProtocol.verifyPacket(d2, sig), "signature rejects unsigned version bump");
            // impersonation: wrong name for (randid,owner) -> must fail on name check
            PocketProtocol.Data d3 = new PocketProtocol.Data("Mx200GZ47HZKRAZKC99PZQVKUD4ER2G78NN6BT",
                    owner, randid, d.description, 1, d.file);
            check(!PocketProtocol.verifyPacket(d3, sig), "rejects name not committing to (randid,owner)");
        } catch (Exception e) {
            check(false, "derive/packet block threw: " + e);
        }

        System.out.println("== T7: PocketWire on-chain state encode/parse round-trip ==");
        try {
            check(PocketWire.FILE_ADDRESS.equals("0x4D494E4957454220524F434B5321"), "file address constant");
            check(PocketWire.REQUEST_ADDRESS.equals("0x4D494E4957454220524F434BFFFF"), "request address constant");

            // description DB-encode/decode round-trips through nasty characters
            String desc = "brackets ] [ quote ' pct % unicode café ☕ newline\n";
            check(PocketWire.decodeStringFromDB(PocketWire.encodeStringForDB(desc)).equals(desc),
                    "description DB-encode/decode round-trips");
            check(!PocketWire.encodeStringForDB(desc).contains("]"), "DB-encoded description has no ']' to break state");

            KeyPair kp = PocketProtocol.generateRsa1024();
            String owner = PocketProtocol.ownerHex(kp.getPublic());
            String randid = "0x1122334455667788990011223344556677";
            String name = PocketProtocol.deriveName(randid, owner);
            PocketProtocol.Data d = new PocketProtocol.Data(name, owner, randid, desc, 3, "0x504B0304ABCDEF");
            String sig = PocketProtocol.signPacket(d, kp.getPrivate());

            // build the state JSON, then parse it back as a node would from simplestate
            String stateJson = PocketWire.stateJson(d, sig);
            check(stateJson.startsWith("{\"0\":\"[" + name + "]\""), "state slot 0 bracketed name");
            check(stateJson.contains("\"4\":3,"), "state slot 4 bare int version");

            String[] slots = extractSlots(stateJson);
            PocketWire.ParsedPacket pp = PocketWire.fromCoinState(slots);
            check(pp != null, "coin state parses");
            if (pp != null) {
                check(pp.data.name.equals(name), "parsed name matches");
                check(pp.data.description.equals(desc), "parsed description matches original");
                check(pp.data.version == 3, "parsed version matches");
                check(pp.signature.equals(sig), "parsed signature matches");
                check(PocketProtocol.verifyPacket(pp.data, pp.signature), "round-tripped packet still verifies");
            }
            // missing slot -> rejected
            check(PocketWire.fromCoinState(new String[]{"[x]","a","b","[c]","1","d"}) == null, "incomplete state rejected");

            String pub = PocketWire.buildPublishCommand(d, sig);
            check(pub.startsWith("sendpoll amount:0.000000000001 address:0x4D494E4957454220524F434B5321 storestate:true state:{"),
                    "publish command shape");
            check(PocketWire.buildRequestCommand(name).contains("address:0x4D494E4957454220524F434BFFFF"),
                    "request command shape");
        } catch (Exception e) {
            check(false, "wire block threw: " + e);
        }

        System.out.println();
        if (failures == 0) { System.out.println("ALL GOLDEN VECTORS PASSED — protocol core is byte-compatible."); }
        else { System.out.println(failures + " FAILURE(S) — encoding not yet byte-compatible."); System.exit(1); }
    }

    /** Parse our stateJson {"0":..,"6":..} into raw slot values 0..6, mimicking a simplestate coin
     *  (string values keep their brackets; slot 4 comes back as its integer text). */
    static String[] extractSlots(String json) {
        String[] out = new String[7];
        for (int k = 0; k < 7; k++) {
            String key = "\"" + k + "\":";
            int p = json.indexOf(key);
            if (p < 0) continue;
            p += key.length();
            if (json.charAt(p) == '"') {
                StringBuilder b = new StringBuilder();
                p++;
                while (p < json.length()) {
                    char c = json.charAt(p++);
                    if (c == '\\') { char n = json.charAt(p++); b.append(n == 'n' ? '\n' : n); }
                    else if (c == '"') break;
                    else b.append(c);
                }
                out[k] = b.toString();
            } else {
                int e = p;
                while (e < json.length() && json.charAt(e) != ',' && json.charAt(e) != '}') e++;
                out[k] = json.substring(p, e);
            }
        }
        return out;
    }

    static String jshow(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c > 0x7E) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
}
