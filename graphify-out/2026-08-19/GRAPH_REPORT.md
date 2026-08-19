# Graph Report - pocketweb  (2026-08-18)

## Corpus Check
- 49 files · ~445,040 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 815 nodes · 1596 edges · 56 communities (40 shown, 16 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 176 edges (avg confidence: 0.72)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `1b3ae8d8`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- NodeApi
- SiteExtractor
- org.junit.Test
- jquery-3.7.1.min.js
- gen-vectors.js
- PocketStore
- gradlew
- C
- install.sh
- pre-commit
- SiteServer
- decimal.min.js
- jquery-ui.js
- jquery.js
- decimal.js
- package.json
- find
- done
- Changelog.md
- domManip
- libs/mds.js
- miniweb/mds.js
- finalise
- Icons for jQuery-UI #
- call
- digitsToString
- parseOther
- nodeName
- getPi
- cosine
- computeStyleTests
- maxOrMin
- log
- datepicker_bindHover
- animComplete
- bindRemoveEvent
- camelCase
- complete
- getDimensions
- Data
- MainActivity
- Override
- PocketNode
- NodeApi
- MiniwebBridge
- .allow
- Override
- PocketStore
- SuppressWarnings
- .publishNew
- SyncListener
- PocketWeb — native Minima companion app

## God Nodes (most connected - your core abstractions)
1. `MainActivity` - 35 edges
2. `PocketProtocol` - 25 edges
3. `PocketStore` - 25 edges
4. `MiniwebBridge` - 22 edges
5. `SiteServer` - 20 edges
6. `re()` - 16 edges
7. `ne()` - 14 edges
8. `$e()` - 14 edges
9. `NodeApi` - 14 edges
10. `e()` - 13 edges

## Surprising Connections (you probably didn't know these)
- `F()` --indirect_call--> `R()`  [INFERRED]
  app/src/main/assets/miniweb/libs/jquery/jquery-3.7.1.min.js → app/src/main/assets/miniweb/libs/decimal.min.js
- `winnow()` --indirect_call--> `i()`  [INFERRED]
  app/src/main/assets/miniweb/libs/jquery/jquery-ui-1.14.1/external/jquery/jquery.js → app/src/main/assets/miniweb/libs/jquery/jquery-ui-1.14.1/jquery-ui.min.js
- `call()` --indirect_call--> `e()`  [INFERRED]
  app/src/main/assets/miniweb/miniweb.js → app/src/main/assets/miniweb/libs/purify.min.js
- `setDocument()` --indirect_call--> `b()`  [INFERRED]
  app/src/main/assets/miniweb/libs/jquery/jquery-ui-1.14.1/external/jquery/jquery.js → app/src/main/assets/miniweb/libs/decimal.min.js
- `J()` --indirect_call--> `E()`  [INFERRED]
  app/src/main/assets/miniweb/libs/jquery/jquery-3.7.1.min.js → app/src/main/assets/miniweb/libs/decimal.min.js

## Import Cycles
- None detected.

## Communities (56 total, 16 thin omitted)

### Community 0 - "NodeApi"
Cohesion: 0.19
Nodes (9): android.content.Context, android.os.Handler, Cb, NodeApi, PairingListener, MinimaAPI, MinimaAPIListener, org.minimarex.minimaapi.MinimaAPI (+1 more)

### Community 1 - "SiteExtractor"
Cohesion: 0.13
Nodes (4): SiteExtractor, UnsafeZipException, SiteExtractorSelfTest, SiteExtractorTest

### Community 2 - "org.junit.Test"
Cohesion: 0.05
Nodes (22): PocketKeys, Data, PocketProtocol, PocketKeys, PocketPublisher, ParsedPacket, PocketWire, MiniwebUrlTest (+14 more)

### Community 3 - "jquery-3.7.1.min.js"
Cohesion: 0.07
Nodes (59): Zn(), Ae(), B(), Be(), c(), $e(), ee(), F() (+51 more)

### Community 4 - "gen-vectors.js"
Cohesion: 0.18
Nodes (6): cases, dir, fs, jdir, out, path

### Community 5 - "PocketStore"
Cohesion: 0.13
Nodes (6): android.database.Cursor, android.database.sqlite.SQLiteDatabase, android.database.sqlite.SQLiteOpenHelper, Override, PocketStore, Row

### Community 6 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 13 - "SiteServer"
Cohesion: 0.06
Nodes (20): android.annotation.SuppressLint, android.webkit.WebResourceRequest, android.webkit.WebResourceResponse, android.webkit.WebViewClient, MimeTypes, Kind, ASSET, BLOCKED (+12 more)

### Community 14 - "decimal.min.js"
Cohesion: 0.06
Nodes (33): a(), b(), D(), dn(), E(), F(), G(), hn() (+25 more)

### Community 15 - "jquery-ui.js"
Cohesion: 0.04
Nodes (22): TODO: determine which cases actually cause this to happen, TODO: Unwrap at same DOM position, TODO: make renderAxis a prototype function, TODO: Seems like a bug to cache this.outerDimensions, TODO: remove after 1.12, TODO: Remove hack when datepicker implements, TODO: switch return back to widget declaration at top of file when this is…, TODO: Remove in 1.14 along with call to it below (+14 more)

### Community 16 - "jquery.js"
Cohesion: 0.06
Nodes (12): dataAttr(), finalPropName(), getData(), Identity(), leverageNative(), NOTE: This can be skipped if there are no unmatched elements (i.e.,…, TODO: Now that all calls to _data and _removeData have been replaced, resolve() (+4 more)

### Community 18 - "package.json"
Cohesion: 0.08
Nodes (25): author, bugs, url, description, homepage, keywords, license, main (+17 more)

### Community 19 - "find"
Cohesion: 0.18
Nodes (17): addCombinator(), assert(), compile(), condense(), createPositionalPseudo(), elementMatcher(), find(), markFunction() (+9 more)

### Community 20 - "done"
Cohesion: 0.14
Nodes (15): adoptValue(), ajaxConvert(), ajaxHandleResponses(), Animation(), camelCase(), createFxNow(), createTween(), defaultPrefilter() (+7 more)

### Community 21 - "Changelog.md"
Cohesion: 0.15
Nodes (12): Deleted ####, Deleted ####, Deprecated ####, New Aliases ####, New Icons ####, Replaced ####, Version 2.1 #, Version 2.2 # (+4 more)

### Community 22 - "domManip"
Cohesion: 0.20
Nodes (12): buildFragment(), buildParams(), cloneCopyEvent(), disableScript(), DOMEval(), domManip(), getAll(), isArrayLike() (+4 more)

### Community 23 - "libs/mds.js"
Cohesion: 0.27
Nodes (5): httpPostAsync(), httpPostAsyncPoll(), MDSPostMessage(), PollListener(), postMDSFail()

### Community 24 - "miniweb/mds.js"
Cohesion: 0.27
Nodes (5): httpPostAsync(), httpPostAsyncPoll(), MDSPostMessage(), PollListener(), postMDSFail()

### Community 25 - "finalise"
Cohesion: 0.24
Nodes (10): ceil(), checkRoundingDigits(), finalise(), floor(), getLn10(), naturalExponential(), naturalLogarithm(), round() (+2 more)

### Community 26 - "Icons for jQuery-UI #"
Cohesion: 0.22
Nodes (8): Bugs, features and feedback ###, Demo ###, Example Include #####, How to use an icon in HTML-markup ####, Icons for jQuery-UI #, Include the Icon Font ####, Screenshot ###, Usage ###

### Community 27 - "call"
Cohesion: 0.83
Nodes (3): bridge(), call(), register()

### Community 28 - "digitsToString"
Cohesion: 0.32
Nodes (8): checkInt32(), convertBase(), digitsToString(), finiteToString(), getZeroString(), nonFiniteToString(), random(), toStringBinary()

### Community 29 - "parseOther"
Cohesion: 0.33
Nodes (7): clone(), getBase10Exponent(), intPow(), isDecimalInstance(), parseDecimal(), parseOther(), truncate()

### Community 30 - "nodeName"
Cohesion: 0.29
Nodes (7): boxModelAdjustment(), createButtonPseudo(), createInputPseudo(), curCSS(), getWidthOrHeight(), manipulationTarget(), nodeName()

### Community 31 - "getPi"
Cohesion: 0.40
Nodes (5): atan(), atan2(), getPi(), isOdd(), toLessThanHalfPi()

### Community 32 - "cosine"
Cohesion: 0.67
Nodes (4): cosine(), sine(), taylorSeries(), tinyPow()

### Community 33 - "computeStyleTests"
Cohesion: 0.67
Nodes (3): div(), computeStyleTests(), roundPixelMeasures()

### Community 34 - "maxOrMin"
Cohesion: 0.67
Nodes (3): min(), max(), maxOrMin()

### Community 35 - "log"
Cohesion: 0.67
Nodes (3): log(), log10(), log2()

### Community 36 - "datepicker_bindHover"
Cohesion: 0.67
Nodes (3): Datepicker(), datepicker_bindHover(), datepicker_handleMouseover()

### Community 44 - "MainActivity"
Cohesion: 0.14
Nodes (15): android.content.BroadcastReceiver, android.os.Bundle, android.view.Menu, android.view.MenuItem, android.widget.EditText, android.widget.TextView, androidx.activity.result.ActivityResultLauncher, androidx.appcompat.app.AppCompatActivity (+7 more)

### Community 45 - "Override"
Cohesion: 0.21
Nodes (4): ServerListener, Listener, Override, SuppressWarnings

### Community 46 - "PocketNode"
Cohesion: 0.24
Nodes (4): CmdListener, Data, PocketNode, NodeApi

### Community 48 - "MiniwebBridge"
Cohesion: 0.20
Nodes (9): android.webkit.JavascriptInterface, android.webkit.WebView, NodeApi, PocketStore, MiniwebBridge, Navigator, JSONObject, org.json.JSONObject (+1 more)

### Community 49 - ".allow"
Cohesion: 0.19
Nodes (4): HostGuard, HostGuardSelfTest, HostGuardTest, java.net.InetAddress

### Community 55 - "PocketWeb — native Minima companion app"
Cohesion: 0.40
Nodes (4): Backward-compat invariants (do not change — see plan spec), Build & test, PocketWeb — native Minima companion app, Status

## Knowledge Gaps
- **49 isolated node(s):** `Status`, `Backward-compat invariants (do not change — see plan spec)`, `Build & test`, `Version 2.3.2 #`, `Version 2.3.1 #` (+44 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **16 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `computeStyleTests()` connect `computeStyleTests` to `jquery.js`?**
  _High betweenness centrality (0.062) - this node is a cross-community bridge._
- **Why does `div()` connect `computeStyleTests` to `decimal.js`?**
  _High betweenness centrality (0.061) - this node is a cross-community bridge._
- **Why does `done()` connect `done` to `jquery.js`, `jquery-3.7.1.min.js`?**
  _High betweenness centrality (0.060) - this node is a cross-community bridge._
- **What connects `Status`, `Backward-compat invariants (do not change — see plan spec)`, `Build & test` to the rest of the system?**
  _49 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `SiteExtractor` be split into smaller, more focused modules?**
  _Cohesion score 0.13405797101449277 - nodes in this community are weakly interconnected._
- **Should `org.junit.Test` be split into smaller, more focused modules?**
  _Cohesion score 0.05181787291879035 - nodes in this community are weakly interconnected._
- **Should `jquery-3.7.1.min.js` be split into smaller, more focused modules?**
  _Cohesion score 0.0673274094326726 - nodes in this community are weakly interconnected._