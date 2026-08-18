# PocketWeb — native Minima companion app

Native Android port of the PocketWeb (on-chain website browser) + pocketFS (file-packet
publish/verify) MiniDapps. Java + classic Views, talks to Minima Core only via `minimaapi.aar`
broadcast IPC. One combined app (the MDS `pocketFS`/`pocketWeb` split existed only to cross the
MDS sandbox). **Backward compatible** with every PocketWeb site already published on-chain.

Package `com.eurobuddha.pocketweb`. Scaffolded from `apks/utxo` (shell) + `apks/filez` (NodeApi /
FILE bridge). Vendors the newest AAR from `apks/base/dist/minimaapi.aar`.

## Status

**Built + tested (functional MVP — browse, sync, render, publish):**
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
- `PocketStore` — SQLite filepackets cache (parameterized statements, `UNIQUE(name)` version-aware
  upsert, FTS4 search, maintained-`lastaccess` LRU). Replaces MDS.sql.
- `PocketNode` — poll-based chain sync: polls `coins ... address:<site>` on debounced NEWBLOCK and
  `coinnotify`s ONLY the tiny request address (never the site address — its fat NOTIFYCOIN would kill
  every companion app). Verifies at ingest AND read. `publish`/`requestSite` command builders.
- `SiteServer` + `MiniwebUrl` + `MimeTypes` — per-origin WebView rendering via
  `shouldInterceptRequest`, serving the site's extracted files + the shipped `/miniweb/` tree and
  denying everything else (external loads blocked; traversal/cross-origin refused).
- `MiniwebBridge` + `HostGuard` + native `miniweb.js` — the site API (`miniweb_*`) as an
  `@JavascriptInterface`, with the hardened NET_GET (host-resolved SSRF block incl. 169.254/172.16)
  and a read-only command allowlist excluding wallet/identity commands.
- `PocketKeys` + `PocketPublisher` — own RSA-1024 publishing key + Maxima-key import; create/update
  signed packets (50KB limit), publish via `sendpoll`.
- `MainActivity` — browse screen (address bar, site picker, sync on NEWBLOCK) + Publish / My sites /
  Import-key menu. Full debug APK builds (~20MB); 24 JVM unit tests pass.

**Remaining (on-device + polish):**
1. **On-device/testnet verification** — confirm SHA3-256 is available via the platform provider on
   API 28 (else add a BouncyCastle 1.69 `SHA3Digest` fallback); confirm a real site renders and a
   published site is discovered/rendered by the existing MDS MiniDapp (interop acceptance).
2. Polish: gensite visual builder (Build tab), update notifications for bookmarked sites, an `mds.js`
   asset shim (route site `MDS.cmd` through the allowlisted bridge), WebChromeClient (page titles),
   community mirroring/pinning.

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
