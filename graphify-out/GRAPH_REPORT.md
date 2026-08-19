# Graph Report - pocketweb  (2026-08-19)

## Corpus Check
- 69 files · ~465,835 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1070 nodes · 2177 edges · 76 communities (59 shown, 17 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 239 edges (avg confidence: 0.7)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `3907fd3b`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- AppBridge
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
- NodeApi
- Override
- app-ui/js/purify.min.js
- MainActivity
- app-ui/js/gui.js
- .allow
- jslib.js
- jszip.min.js
- SuppressWarnings
- android.widget.EditText
- PocketWeb — native Minima companion app
- PocketStore
- fs/js/sql.js
- fs/js/gui.js
- generate.js
- Override
- PocketNode
- MainActivity.java
- auth.js
- fs/js/jslib.js
- gensite/jslib.js
- SyncListener
- android.webkit.ValueCallback
- txns.js
- PocketKeys
- PocketKeys

## God Nodes (most connected - your core abstractions)
1. `AppBridge` - 51 edges
2. `MainActivity` - 40 edges
3. `PocketStore` - 26 edges
4. `PocketProtocol` - 25 edges
5. `MiniwebBridge` - 22 edges
6. `SiteServer` - 20 edges
7. `PocketKeys` - 19 edges
8. `AppServer` - 17 edges
9. `re()` - 16 edges
10. `ne()` - 14 edges

## Surprising Connections (you probably didn't know these)
- `F()` --indirect_call--> `R()`  [INFERRED]
  app/src/main/assets/miniweb/libs/jquery/jquery-3.7.1.min.js → app/src/main/assets/miniweb/libs/decimal.min.js
- `winnow()` --indirect_call--> `i()`  [INFERRED]
  app/src/main/assets/miniweb/libs/jquery/jquery-ui-1.14.1/external/jquery/jquery.js → app/src/main/assets/miniweb/libs/jquery/jquery-ui-1.14.1/jquery-ui.min.js
- `V()` --indirect_call--> `b()`  [INFERRED]
  app/src/main/assets/app-ui/fs/js/jszip.min.js → app/src/main/assets/miniweb/libs/decimal.min.js
- `call()` --indirect_call--> `e()`  [INFERRED]
  app/src/main/assets/miniweb/miniweb.js → app/src/main/assets/miniweb/libs/purify.min.js
- `setDocument()` --indirect_call--> `b()`  [INFERRED]
  app/src/main/assets/miniweb/libs/jquery/jquery-ui-1.14.1/external/jquery/jquery.js → app/src/main/assets/miniweb/libs/decimal.min.js

## Import Cycles
- None detected.

## Communities (76 total, 17 thin omitted)

### Community 0 - "AppBridge"
Cohesion: 0.11
Nodes (8): android.content.SharedPreferences, android.webkit.JavascriptInterface, AppBridge, Host, NodeApi, PocketStore, JSONObject, org.json.JSONObject

### Community 1 - "SiteExtractor"
Cohesion: 0.13
Nodes (4): SiteExtractor, UnsafeZipException, SiteExtractorSelfTest, SiteExtractorTest

### Community 2 - "org.junit.Test"
Cohesion: 0.06
Nodes (20): PocketKeys, Data, PocketProtocol, PocketKeys, PocketPublisher, ParsedPacket, PocketWire, PocketKeysSelfTest (+12 more)

### Community 3 - "jquery-3.7.1.min.js"
Cohesion: 0.07
Nodes (59): Zn(), Ae(), B(), Be(), c(), $e(), ee(), F() (+51 more)

### Community 4 - "gen-vectors.js"
Cohesion: 0.18
Nodes (6): cases, dir, fs, jdir, out, path

### Community 5 - "PocketStore"
Cohesion: 0.09
Nodes (12): android.database.Cursor, android.database.sqlite.SQLiteDatabase, android.database.sqlite.SQLiteOpenHelper, NodeApi, PocketStore, MiniwebBridge, Navigator, Override (+4 more)

### Community 6 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 13 - "SiteServer"
Cohesion: 0.05
Nodes (28): android.annotation.SuppressLint, android.content.Context, android.webkit.WebResourceError, android.webkit.WebResourceRequest, android.webkit.WebResourceResponse, android.webkit.WebView, android.webkit.WebViewClient, AppServer (+20 more)

### Community 14 - "decimal.min.js"
Cohesion: 0.06
Nodes (34): V(), a(), b(), D(), dn(), E(), F(), G() (+26 more)

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

### Community 44 - "NodeApi"
Cohesion: 0.17
Nodes (8): android.os.Handler, Cb, NodeApi, PairingListener, MinimaAPI, MinimaAPIListener, org.minimarex.minimaapi.MinimaAPI, org.minimarex.minimaapi.MinimaAPIListener

### Community 46 - "app-ui/js/purify.min.js"
Cohesion: 0.40
Nodes (9): a(), C(), e(), i(), l(), n(), o(), r() (+1 more)

### Community 47 - "MainActivity"
Cohesion: 0.23
Nodes (8): android.net.Uri, NodeApi, PocketStore, MainActivity, BroadcastReceiver, MiniwebBridge, PocketNode, SiteServer

### Community 48 - "app-ui/js/gui.js"
Cohesion: 0.23
Nodes (10): applyTheme(), jumpToCatalog(), jumpToDev(), jumpToFS(), jumpToHelp(), jumpToHome(), jumpToMySite(), loadTheme() (+2 more)

### Community 49 - ".allow"
Cohesion: 0.19
Nodes (4): HostGuard, HostGuardSelfTest, HostGuardTest, java.net.InetAddress

### Community 50 - "jslib.js"
Cohesion: 0.38
Nodes (8): addDomains(), convertDomainToInternal(), convertDomainToURL(), extractFilePacketToWeb(), getBaseDomain(), loadDataURI(), loadFilePacket(), webFolderExists()

### Community 51 - "jszip.min.js"
Cohesion: 0.15
Nodes (32): A(), c(), d(), f(), G(), h(), i(), j() (+24 more)

### Community 55 - "PocketWeb — native Minima companion app"
Cohesion: 0.40
Nodes (4): Backward-compat invariants (do not change — see plan spec), Build & test, PocketWeb — native Minima companion app, Status

### Community 57 - "fs/js/sql.js"
Cohesion: 0.21
Nodes (14): copyFilePacket(), decodeStringFromDB(), deleteFilePacket(), encodeStringForDB(), getAllFilePackets(), getAllUnpublishedFilePackets(), getFilePacket(), getMyFilePackets() (+6 more)

### Community 58 - "fs/js/gui.js"
Cohesion: 0.14
Nodes (5): deleteSite(), jumpToHelp(), jumpToMyFiles(), jumpToSearch(), startupButtons()

### Community 59 - "generate.js"
Cohesion: 0.29
Nodes (13): createBasePages(), createBlog(), createGenSite(), createLinksPage(), downloadGenSite(), generateSiteAsZip(), lineDecode(), loadWebFile() (+5 more)

### Community 60 - "Override"
Cohesion: 0.21
Nodes (5): android.view.MenuItem, Override, ServerListener, Listener, SuppressWarnings

### Community 61 - "PocketNode"
Cohesion: 0.24
Nodes (4): CmdListener, Data, PocketNode, NodeApi

### Community 62 - "MainActivity.java"
Cohesion: 0.22
Nodes (7): android.content.BroadcastReceiver, android.os.Bundle, android.view.Menu, android.widget.TextView, androidx.activity.result.ActivityResultLauncher, androidx.appcompat.app.AppCompatActivity, EditText

### Community 63 - "auth.js"
Cohesion: 0.36
Nodes (4): checkFilePacket(), encodetext(), signFilepacket(), verifyFilepacket()

### Community 64 - "fs/js/jslib.js"
Cohesion: 0.54
Nodes (7): addDomains(), convertDomainToInternal(), convertDomainToURL(), extractDomain(), extractDomainToWeb(), extractFilePacketToWeb(), getBaseDomain()

### Community 65 - "gensite/jslib.js"
Cohesion: 0.54
Nodes (6): jumpBlog(), jumpExtra(), jumpHome(), jumpLinks(), jumpPage(), startupButtons()

### Community 67 - "android.webkit.ValueCallback"
Cohesion: 0.29
Nodes (4): android.webkit.ValueCallback, FileChooser, FileChooserParams, FileChooserParams

### Community 68 - "txns.js"
Cohesion: 0.38
Nodes (4): convertToFilePacket(), publishFilePacket(), sendFilePacket(), stripBrackets()

## Knowledge Gaps
- **49 isolated node(s):** `Status`, `Backward-compat invariants (do not change — see plan spec)`, `Build & test`, `Version 2.3.2 #`, `Version 2.3.1 #` (+44 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **17 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `computeStyleTests()` connect `computeStyleTests` to `jquery.js`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **Why does `div()` connect `computeStyleTests` to `decimal.js`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **Why does `b()` connect `decimal.min.js` to `find`?**
  _High betweenness centrality (0.041) - this node is a cross-community bridge._
- **What connects `Status`, `Backward-compat invariants (do not change — see plan spec)`, `Build & test` to the rest of the system?**
  _49 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `AppBridge` be split into smaller, more focused modules?**
  _Cohesion score 0.1099290780141844 - nodes in this community are weakly interconnected._
- **Should `SiteExtractor` be split into smaller, more focused modules?**
  _Cohesion score 0.13405797101449277 - nodes in this community are weakly interconnected._
- **Should `org.junit.Test` be split into smaller, more focused modules?**
  _Cohesion score 0.055382485289027346 - nodes in this community are weakly interconnected._