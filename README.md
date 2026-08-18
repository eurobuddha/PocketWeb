# PocketWeb — native Minima companion app

Native Android port of the PocketWeb (on-chain website browser) + pocketFS (file-packet
publish/verify) MiniDapps. Java + classic Views, talks to Minima Core only via `minimaapi.aar`
broadcast IPC. One combined app (the MDS `pocketFS`/`pocketWeb` split existed only to cross the
MDS sandbox). **Backward compatible** with every PocketWeb site already published on-chain.

Package `com.eurobuddha.pocketweb`. Scaffolded from `apks/utxo` (shell) + `apks/filez` (NodeApi /
FILE bridge). Vendors the newest AAR from `apks/base/dist/minimaapi.aar`.

## Status

**Built and PROVEN (this increment):**
- `PocketProtocol` — identity (SHA3-256 → 16-byte id → Minima `Mx` base32 address), signing
  (`SHA256withRSA` over `encodeURIComponent(JSON.stringify(data))`, `'`→`%27`), verification, and
  RSA-1024 key handling. Byte-for-byte reimplementation of the node primitives (no BouncyCastle;
  SHA3-256 from the platform provider, API 28+).
- `PocketWire` — on-chain coin-state encode/decode, the two magic addresses, `sendpoll`
  publish/request command builders.
- **Golden-vector acceptance gate** (`ProtocolSelfTest` runnable + `ProtocolGoldenTest` JUnit):
  validates the two **real on-chain `Mx` names** in `mds/PocketWeb/*.zip` against their embedded
  SHA3 checksums, cross-checks JSON/encodeURIComponent against a JS oracle
  (`protocol-vectors/gen-vectors.js`), and round-trips full sign→verify→wire→parse. All pass.
- App scaffold: gradle/manifest/res, `NodeApi`, minimal `MainActivity` (pairing banner + live
  `status` probe), FileProvider, version-bump guardrail hook, backup lockdown.

**Next increments (see `~/.claude/plans/i-would-like-pocketweb-curious-canyon.md`):**
1. `PocketStore` — SQLite (`SQLiteOpenHelper`) filepackets cache + FTS5 (replaces MDS.sql).
2. Chain poller/ingest — poll `coins simplestate:true address:0x4D494E4957454220524F434B5321` on
   debounced NEWBLOCK (NOT `coinnotify` on the fat address — that pushes ~200KB parcels through the
   node's unguarded broadcast and kills every companion app). `coinnotify` the tiny request address.
3. `SiteServer` — WebView + `WebViewClient.shouldInterceptRequest` serving `miniweb://<Mx>/path`
   per-site origins + the shipped `../../miniweb/` asset tree; `MiniwebBridge` JS API with the
   hardened NET_GET (host allowlist incl. 169.254/172.16) + command allowlist (no wallet-reading cmds).
4. UI tabs: Browse / Catalog+Search / My Sites / Build / Publish; key generation + Maxima-key import.

## Backward-compat invariants (do not change — see plan spec)

Name = `Mx(SHA3-256(randbytes ++ ownerbytes)[0..16])`; signed payload key order
`{name,owner,randid,description,version,file}`; publish = dust `sendpoll` to
`0x4D494E4957454220524F434B5321`, 7 state slots (0 & 3 bracketed, 3 DB-encoded, 4 bare int); flat
ZIP ≤ 51200 bytes, one site = one coin; version-strictly-greater wins.

## Build & test

```sh
# Golden-vector gate, no device/SDK needed (bare JDK 17/21 — the Android Studio JBR works):
JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
node protocol-vectors/gen-vectors.js          # regenerate JS oracle vectors (GoldenData.java)
"$JBR/bin/javac" -d /tmp/pw-out \
  app/src/main/java/com/eurobuddha/pocketweb/PocketProtocol.java \
  app/src/main/java/com/eurobuddha/pocketweb/PocketWire.java \
  app/src/test/java/com/eurobuddha/pocketweb/GoldenData.java \
  app/src/test/java/com/eurobuddha/pocketweb/ProtocolSelfTest.java
"$JBR/bin/java" -cp /tmp/pw-out com.eurobuddha.pocketweb.ProtocolSelfTest

# Full build + JUnit (needs Android SDK; JDK 17/21):
JAVA_HOME="$JBR" ./gradlew assembleDebug test

# Install the version-bump guardrail once:
sh .githooks/install.sh
```
