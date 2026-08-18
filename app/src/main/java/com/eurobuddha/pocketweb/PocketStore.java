package com.eurobuddha.pocketweb;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local filepackets cache — the native replacement for pocketFS's MDS.sql `filepackets` table.
 * The IPC API only runs node commands (no MDS.sql), so the app owns this store and derives it from
 * the chain via `coins` + a request/serve loop.
 *
 * Improvements over the JS original (all backward-compatible — this is a private local cache):
 *  - Parameterized statements everywhere (the JS version concatenated on-chain-controlled strings
 *    into SQL; a crafted site name/description could inject — fixed here by construction).
 *  - UNIQUE(name) with a version-aware upsert (the JS table had no key on name and relied on
 *    ORDER BY id LIMIT 1, allowing duplicate rows).
 *  - `lastaccess` is actually maintained (the JS column was written once and never used) so an LRU
 *    can evict cold sites while exempting bookmarked / owned ones.
 *  - FTS4 index over name+description for real search instead of a LIKE scan.
 */
public class PocketStore extends SQLiteOpenHelper {

    public static final String DB_NAME = "pocketweb.db";
    public static final int DB_VERSION = 1;

    public static final int PUB_LOCAL = 0;   // authored here, not yet broadcast
    public static final int PUB_ONCHAIN = 1; // published / received from the network

    public PocketStore(Context ctx) { super(ctx, DB_NAME, null, DB_VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE filepackets (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  name TEXT NOT NULL UNIQUE," +
            "  owner TEXT NOT NULL," +
            "  randid TEXT NOT NULL," +
            "  description TEXT NOT NULL," +
            "  version INTEGER NOT NULL," +
            "  data TEXT NOT NULL," +          // "0x" + zip hex
            "  signature TEXT NOT NULL," +
            "  lastaccess INTEGER NOT NULL," +
            "  published INTEGER NOT NULL," +
            "  bookmarked INTEGER NOT NULL DEFAULT 0)");
        // FTS4 (universally available since API 11; FTS5 is not guaranteed on all API-28 devices).
        db.execSQL("CREATE VIRTUAL TABLE filepackets_fts USING fts4(name, description, tokenize=unicode61)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // v1 is the first shipped schema; future migrations use additive ALTERs wrapped in try/catch.
    }

    // ---- writes ----

    /**
     * Insert a packet, or overwrite an existing one ONLY if the new version is strictly greater
     * (mirrors pocketFS service.js ingest policy). Returns true if the store changed.
     * Caller is responsible for having verified the signature first.
     */
    public boolean upsertIfNewer(PocketProtocol.Data d, String signature, int published) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Integer existing = versionOf(db, d.name);
            if (existing != null && d.version <= existing) { db.setTransactionSuccessful(); return false; }

            ContentValues cv = new ContentValues();
            cv.put("name", d.name);
            cv.put("owner", d.owner);
            cv.put("randid", d.randid);
            cv.put("description", d.description);
            cv.put("version", d.version);
            cv.put("data", d.file);
            cv.put("signature", signature);
            cv.put("lastaccess", now());
            cv.put("published", published);
            // INSERT OR REPLACE keeps a single row per name; bookmarked flag is re-applied below.
            int wasBookmarked = existing != null && isBookmarked(db, d.name) ? 1 : 0;
            cv.put("bookmarked", wasBookmarked);
            db.insertWithOnConflict("filepackets", null, cv, SQLiteDatabase.CONFLICT_REPLACE);

            db.delete("filepackets_fts", "name = ?", new String[]{ d.name });
            ContentValues fts = new ContentValues();
            fts.put("name", d.name);
            fts.put("description", d.description);
            db.insert("filepackets_fts", null, fts);

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public void setBookmarked(String name, boolean on) {
        ContentValues cv = new ContentValues();
        cv.put("bookmarked", on ? 1 : 0);
        getWritableDatabase().update("filepackets", cv, "name = ?", new String[]{ name });
    }

    public void markPublished(String name) {
        ContentValues cv = new ContentValues();
        cv.put("published", PUB_ONCHAIN);
        getWritableDatabase().update("filepackets", cv, "name = ?", new String[]{ name });
    }

    public void touch(String name) {
        ContentValues cv = new ContentValues();
        cv.put("lastaccess", now());
        getWritableDatabase().update("filepackets", cv, "name = ?", new String[]{ name });
    }

    public void delete(String name) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("filepackets", "name = ?", new String[]{ name });
            db.delete("filepackets_fts", "name = ?", new String[]{ name });
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    // ---- reads ----

    /** Full packet incl. the zip hex, and bumps lastaccess. Null if absent. */
    public Row get(String name) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("filepackets", COLS_FULL, "name = ?", new String[]{ name },
                null, null, null)) {
            if (!c.moveToFirst()) return null;
            Row r = readRow(c, true);
            touch(name);
            return r;
        }
    }

    /** All on-chain packets, WITHOUT the heavy data blob (for catalogue listing). */
    public List<Row> listAll() {
        SQLiteDatabase db = getReadableDatabase();
        List<Row> out = new ArrayList<>();
        try (Cursor c = db.query("filepackets", COLS_LIGHT, "published = ?",
                new String[]{ String.valueOf(PUB_ONCHAIN) }, null, null, "id DESC")) {
            while (c.moveToNext()) out.add(readRow(c, false));
        }
        return out;
    }

    /** Locally-authored packets not yet broadcast. */
    public List<Row> getMine() {
        SQLiteDatabase db = getReadableDatabase();
        List<Row> out = new ArrayList<>();
        try (Cursor c = db.query("filepackets", COLS_LIGHT, "published = ?",
                new String[]{ String.valueOf(PUB_LOCAL) }, null, null, "id DESC")) {
            while (c.moveToNext()) out.add(readRow(c, false));
        }
        return out;
    }

    /**
     * Search by name prefix (Mx…) or FTS over name+description. Blob-stripped, capped.
     * Uses bound parameters — no string-concatenated SQL (fixes the JS injection class).
     */
    public List<Row> search(String term, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<Row> out = new ArrayList<>();
        String t = term == null ? "" : term.trim();
        if (t.isEmpty()) return out;

        if (t.startsWith("Mx")) {
            try (Cursor c = db.query("filepackets", COLS_LIGHT, "name LIKE ?",
                    new String[]{ t + "%" }, null, null, "id DESC", String.valueOf(limit))) {
                while (c.moveToNext()) out.add(readRow(c, false));
            }
            return out;
        }
        // FTS: match against the fts table, join back for the light columns.
        String sql = "SELECT " + join(COLS_LIGHT, "f.") +
                " FROM filepackets f JOIN filepackets_fts x ON x.name = f.name" +
                " WHERE filepackets_fts MATCH ? AND f.published = ? ORDER BY f.id DESC LIMIT ?";
        try (Cursor c = db.rawQuery(sql, new String[]{ ftsQuery(t), String.valueOf(PUB_ONCHAIN), String.valueOf(limit) })) {
            while (c.moveToNext()) out.add(readRow(c, false));
        }
        return out;
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM filepackets", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** LRU prune: keep the newest `keep` by lastaccess; never evict bookmarked or locally-authored. */
    public int pruneTo(int keep) {
        return getWritableDatabase().delete("filepackets",
                "bookmarked = 0 AND published = " + PUB_ONCHAIN + " AND name NOT IN " +
                "(SELECT name FROM filepackets WHERE bookmarked = 1 OR published = " + PUB_LOCAL +
                " UNION SELECT name FROM filepackets ORDER BY lastaccess DESC LIMIT ?)",
                new String[]{ String.valueOf(keep) });
    }

    // ---- helpers ----

    public static final class Row {
        public long id;
        public PocketProtocol.Data data;   // .file is null in light rows
        public String signature;
        public int published;
        public boolean bookmarked;
        public long lastaccess;
        public boolean verified() {
            return data.file != null && PocketProtocol.verifyPacket(data, signature);
        }
    }

    private static final String[] COLS_FULL =
            { "id","name","owner","randid","description","version","data","signature","published","bookmarked","lastaccess" };
    private static final String[] COLS_LIGHT =
            { "id","name","owner","randid","description","version","signature","published","bookmarked","lastaccess" };

    private static Row readRow(Cursor c, boolean full) {
        Row r = new Row();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        String file = full ? c.getString(c.getColumnIndexOrThrow("data")) : null;
        r.data = new PocketProtocol.Data(
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getString(c.getColumnIndexOrThrow("owner")),
                c.getString(c.getColumnIndexOrThrow("randid")),
                c.getString(c.getColumnIndexOrThrow("description")),
                c.getInt(c.getColumnIndexOrThrow("version")),
                file);
        r.signature = c.getString(c.getColumnIndexOrThrow("signature"));
        r.published = c.getInt(c.getColumnIndexOrThrow("published"));
        r.bookmarked = c.getInt(c.getColumnIndexOrThrow("bookmarked")) != 0;
        r.lastaccess = c.getLong(c.getColumnIndexOrThrow("lastaccess"));
        return r;
    }

    private static Integer versionOf(SQLiteDatabase db, String name) {
        try (Cursor c = db.query("filepackets", new String[]{ "version" }, "name = ?",
                new String[]{ name }, null, null, null)) {
            return c.moveToFirst() ? c.getInt(0) : null;
        }
    }

    private static boolean isBookmarked(SQLiteDatabase db, String name) {
        try (Cursor c = db.query("filepackets", new String[]{ "bookmarked" }, "name = ?",
                new String[]{ name }, null, null, null)) {
            return c.moveToFirst() && c.getInt(0) != 0;
        }
    }

    /** Sanitise a free-text term into a safe FTS4 prefix query (bound as a parameter regardless). */
    private static String ftsQuery(String t) {
        String cleaned = t.replaceAll("[\"*^]", " ").trim();
        if (cleaned.isEmpty()) return "\"\"";
        StringBuilder b = new StringBuilder();
        for (String tok : cleaned.split("\\s+")) if (!tok.isEmpty()) b.append('"').append(tok).append("\"* ");
        return b.toString().trim();
    }

    private static String join(String[] cols, String prefix) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < cols.length; i++) { if (i > 0) b.append(','); b.append(prefix).append(cols[i]); }
        return b.toString();
    }

    private static long now() { return System.currentTimeMillis(); }
}
