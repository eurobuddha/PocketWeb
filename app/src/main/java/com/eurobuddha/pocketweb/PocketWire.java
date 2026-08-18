package com.eurobuddha.pocketweb;

/**
 * On-chain wire format for PocketWeb file packets — byte-compatible with pocketFS/js/txns.js.
 *
 * A site publishes as a dust sendpoll to a fixed "magic" address carrying the packet in
 * coin state slots 0..6. Getting the bracketing/encoding of the slots wrong breaks discovery
 * of, and by, every existing MiniDapp peer, so this mirrors txns.js exactly.
 */
public final class PocketWire {

    private PocketWire() {}

    // ASCII "MINIWEB ROCKS!" and "MINIWEB ROCK\xFF\xFF" — well-known broadcast buckets.
    public static final String FILE_ADDRESS = "0x4D494E4957454220524F434B5321";
    public static final String REQUEST_ADDRESS = "0x4D494E4957454220524F434BFFFF";
    public static final String DUST_AMOUNT = "0.000000000001";

    // ---- description DB-encoding (sql.js encodeStringForDB/decodeStringFromDB == encodetext/decodetext) ----
    public static String encodeStringForDB(String s) { return PocketProtocol.encodetext(s); }

    public static String decodeStringFromDB(String s) { return urlDecode(s.replace("%27", "'")); }

    // ---- one leading '[' and one trailing ']' removed, after trim (txns.js stripBrackets) ----
    public static String stripBrackets(String in) {
        String s = in.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** JSON for the 7 state slots, keys "0".."6" ascending (JS numeric-key order). */
    public static String stateJson(PocketProtocol.Data d, String signatureHex) {
        StringBuilder b = new StringBuilder();
        b.append('{');
        b.append("\"0\":").append(jstr("[" + d.name + "]")).append(',');
        b.append("\"1\":").append(jstr(d.owner)).append(',');
        b.append("\"2\":").append(jstr(d.randid)).append(',');
        b.append("\"3\":").append(jstr("[" + encodeStringForDB(d.description) + "]")).append(',');
        b.append("\"4\":").append(d.version).append(',');                 // bare int
        b.append("\"5\":").append(jstr(d.file)).append(',');
        b.append("\"6\":").append(jstr(signatureHex));
        b.append('}');
        return b.toString();
    }

    public static String buildPublishCommand(PocketProtocol.Data d, String signatureHex) {
        return "sendpoll amount:" + DUST_AMOUNT + " address:" + FILE_ADDRESS
                + " storestate:true state:" + stateJson(d, signatureHex);
    }

    public static String buildRequestCommand(String name) {
        return "sendpoll amount:" + DUST_AMOUNT + " address:" + REQUEST_ADDRESS
                + " storestate:true state:{\"0\":" + jstr("[" + name + "]") + "}";
    }

    /** A coin's simplestate array (index 0..6) -> packet, or null if any slot is empty/missing. */
    public static ParsedPacket fromCoinState(String[] state) {
        if (state == null || state.length < 7) return null;
        for (int i = 0; i < 7; i++) if (state[i] == null || state[i].isEmpty()) return null;
        String name = stripBrackets(state[0]);
        String owner = state[1];
        String randid = state[2];
        String description = decodeStringFromDB(stripBrackets(state[3]));
        int version;
        try { version = Integer.parseInt(state[4].trim()); } catch (NumberFormatException e) { return null; }
        String file = state[5];
        String signature = state[6];
        return new ParsedPacket(new PocketProtocol.Data(name, owner, randid, description, version, file), signature);
    }

    public static final class ParsedPacket {
        public final PocketProtocol.Data data;
        public final String signature;
        public ParsedPacket(PocketProtocol.Data data, String signature) { this.data = data; this.signature = signature; }
    }

    // ---- helpers ----
    private static String jstr(String s) {
        // slot values here are hex, Mx, ints or already-DB-encoded text — safe chars only, but
        // escape defensively the same way JSON.stringify would for a string.
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') b.append("\\\"");
            else if (c == '\\') b.append("\\\\");
            else b.append(c);
        }
        return b.append('"').toString();
    }

    /** decodeURIComponent equivalent: %XX (UTF-8) -> chars, others verbatim. */
    static String urlDecode(String s) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                bos.write((Character.digit(s.charAt(i + 1), 16) << 4) | Character.digit(s.charAt(i + 2), 16));
                i += 3;
            } else {
                for (byte b : String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8)) bos.write(b);
                i++;
            }
        }
        return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
