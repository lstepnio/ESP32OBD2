# eGauge website alignment and UX review

Phases 1–3, recorded 2026-10-29. Base source:
`083b0013363a697ab832179ee1af71b4456ca181`. **Historical approval gate:** product fixes/commits had not started when this report
was presented. The owner subsequently approved all9 items and delivery steps.
[Implementation and delivery](IMPLEMENTATION.md) owns follow-up results.

[Context](context.md) · [65-row feature matrix](feature-matrix.md) ·
[Visual gallery](index.html) · [Capture manifest](screens/manifest.json) ·
[Android capture limits](android-capture-notes.md) · [APK inspection](android-apk-preparation.md)

## Recommendation and confidence

Proceed with the nine bounded fix groups below. The largest gains come from a
current downloadable App, an obvious installation path and precise availability
language. Preserve the existing editorial design, round gauges, shared product
architecture, recovery trust boundaries, API/consent behavior and no-index policy.

Strengths: the core autonomous-gauge story matches code, most future concepts are
clearly qualified, responsive layouts avoid horizontal overflow, contact failures
retain input, and the desktop custom-project grid already scans well. The strongest
case against a broad rewrite is that the site passes the sampled accessibility
checks and the main problems are content/distribution/workflow gaps. A large redesign
would add regression risk without addressing those gaps first.

Confidence is high for source/download mismatch and the measured navigation path;
moderate for visual recommendations. Screens use synthetic readings and controlled
states. They do not qualify live controls, real fault semantics, vehicle compatibility,
physical legibility or complete assistive-technology support. Those boundaries could
change what is safe to advertise, but do not prevent the website corrections below.
No P0 defect was established in this scope.

## Evidence and execution

- **Android:** 72 production-composable composition PNGs on the existing managed
  API 35 emulator. Ten routes, phone/large layouts, explicit light/dark, plus selected
  empty/loading/error/permission and Advanced cases. Focused capture test passed
  1/1 in 57 seconds. The large layout is not a physical-tablet observation. Full
  navigation, dialogs, complete scroll depth, 200% text, TalkBack, RTL and folds
  were not covered by this capture. Some fixture states are intentionally static.
- **Gauge:** 64 production-LVGL/font states at 240 × 240 with RGB565 render buffers,
  exported as expanded RGB PNGs, plus 64 circular SVG-framed variants. Both the
  default 32-bit host path and new opt-in RGB565 path passed existing rendering,
  geometry, gesture, stale/source-loss and action assertions. Only host test files
  changed; no firmware runtime or default capture format changed. An illustrated
  bezel is not a product photograph. Calibration and injected action states are
  evidence fixtures, not available/qualified consumer functions.
- **Website:** home and recovery at 375, 768 and 1440 px, plus section captures and
  mocked form/release-feed errors. At those widths, and a supplementary 320 px
  reflow check, document width equalled viewport width. All images loaded after
  scrolling lazy images into view. Six layout choices updated image, caption and
  pressed state. No baseline preview console errors or HTTP failures were recorded.
- **Interactions:** eight form cases (two forms × success/error × mobile/desktop)
  were fulfilled locally through browser interception. Success reset the form;
  failure retained entered values. No real API submissions occurred. Failed release
  metadata retained the reviewed firmware/APK fallback and local-package path.
- **Checks:** full website build verified all six pinned public download files by
  size/hash; host lint/link/policy checks passed; 42 browser recovery Node tests
  passed. Capture helper paths/hashes/dimensions were checked. No physical device
  was used. No fresh-clone end-to-end setup or new ESP-IDF application build was
  performed; existing repository setup and the documented host/Android capture
  paths were exercised with installed toolchains.

The phase-wide [manifest](screens/manifest.json) identifies source, commit, theme,
resolution, rendering device/method and real/simulated status for every packaged
capture. Website screenshots are actual local browser renders; their embedded App
and gauge examples still have synthetic/software provenance. Mocked response screens
are identified as simulated. Raw captures remain alongside framed variants.

## Findings, ranked by impact

### F01 · P1 · Downloaded App does not match current advertised development UI

**Evidence:** `website/downloads/manifest.json:10` pins debug dev.51/source `414cedd`.
Current App code is dev.53/code 53 (`android/app/build.gradle.kts:17`), including later
settings/action/history changes. Latest firmware is dev.75. The live release listing
has no APK assets, so the dynamic selector correctly has no additional APK to offer.
See [recovery mobile](screens/web/web_recovery_light_375_before.png) and
[artifact inspection](android-apk-preparation.md).

**Why:** a visitor can follow the supported install path and receive an older App
than the page describes. Updating the selector alone cannot create missing releases.

**Fix:** prepare a content-addressed current debug APK using the same verified signer,
record source/version/code/hash/certificate independently of firmware, and verify an
in-place synthetic-profile upgrade on a disposable emulator before offering it.
Retain old URLs and the reviewed dev.60 recovery fallback. Make “bundled recovery,”
“selected firmware prerelease” and “Android App download” distinct. Do not silently
change signing identity, fabricate APK release assets or call it a stable channel.
**Effort M; risk M** (artifact/version/migration compatibility, bounded by verification).

### F02 · P1 · Recovery advice promises a full backup workflow that is not available

**Evidence:** `website/recovery/index.html:77` says “Export a backup from eGauge first.”
`MainActivity.kt:96` and `AlertReportExporter.kt:11` export a diagnostic ZIP; no matching
full setup archive export/import workflow was found. Gauge-based profile recovery is
a separate mechanism and does not establish a user-restorable full backup.

**Why:** a user facing a signer/downgrade failure could mistake a diagnostic report for
a safe restore path and lose saved setup if they uninstall.

**Fix:** rewrite that advice around keeping the existing App installed, using the
same signing identity and asking for help when upgrades are blocked. Explain that a
diagnostic report is for troubleshooting, not complete setup restoration. Keep the
existing warning against uninstalling to bypass a failure. Do not add a new backup
feature as part of website work. **Effort S; risk L.**

### F03 · P1 · Mobile installation path is hidden far below the primary CTA

**Evidence:** at 375 px, `styles.css:5` hides the desktop navigation. Header CTA is
Get updates and hero CTA is Explore the display (`website/index.html:18,27`). The
first visible recovery link appears at approximately y=6,224 CSS px; the explicit
firmware/App download CTA is around y=9,404. See [mobile home](screens/web/web_home_light_375_before.png)
and [measured entry flow](screens/web/diagnostics/entry-flow.json).

**Why:** the principal DIY task requires substantial scanning; mailing-list signup
has greater prominence than getting the working software.

**Fix:** put a clear “Try it free” / “Get started” route beside the existing display
exploration CTA and keep it reachable on mobile. Link to existing `/recovery/` or
`#hardware`; retain updates and custom-project links as secondary actions. No new
page, slug, menu framework or checkout. **Effort S; risk L.**

### F04 · P1 · Action availability is both delayed and too broad

**Evidence:** `website/index.html:84–99` introduces action cards before the “Not enabled
in current public firmware” qualifier. On mobile it appears below all three cards.
See [off-road section](screens/web/web_home_offroad-actions_light_375_before.png).
`Kconfig.projbuild:3` and the dev.75 release evidence distinguish a supervised fixed-idle
candidate from ordinary disabled builds. `IdleTargetEditor.kt:15` explicitly keeps
numeric RPM drafts on the phone; ABS/TCS execution and code clearing remain gated off.

**Why:** polished action cards can imply usable controls, while a blanket public-disabled
statement obscures the actual candidate scope and does not describe the newest
publicly downloadable development artifact precisely.

**Fix:** put a compact status next to the heading and make each action's state clear:
fixed-setting Air up/Winch in limited captured-Jeep testing; arbitrary RPM/minimum-floor
and throttle behavior not enabled; ABS/TCS not enabled. Keep test/load/compatibility
limits, avoid promising extra controller support, and identify Auto orientation as a
candidate where mentioned. **Effort S; risk M** (semantic accuracy requires exact
artifact/options, with no flag or runtime changes).

### F05 · P2 · Recovery mixes desktop flashing and phone installation before routing users

**Evidence:** `website/recovery/index.html:19–64`; mobile APK section begins around
y=3,399 CSS px. Desktop Chrome/Edge requirements sit in a later preparation card.
See [recovery mobile](screens/web/web_recovery_light_375_before.png).

**Why:** a phone visitor seeking an APK first encounters a long USB workflow that
cannot run in that browser. A desktop visitor needs browser/cable requirements
before evaluating the install control.

**Fix:** near the title, offer two explicit choices using existing anchors: “Install
firmware on a computer” and “Get the Android App.” Surface desktop Chrome/Edge + USB
data-cable requirements at the start of the firmware branch. Preserve exact-board
confirmation, signatures/hashes, local-file verification, failure handling and all
write boundaries. **Effort S; risk L.**

### F06 · P2 · Product gallery and feature summary omit current behavior

**Evidence:** `website/index.html:41,48,63–65` and `website/app.js:2` show five reading
layouts plus Alerts. Current `GaugeModels.kt:53` and `ui.c:1099` implement Dual + arc.
`SettingsScreen.kt:131` includes Smart cycling; latest App captures differ from the
older website cards/navigation content. Compare [website App gallery](screens/web/web_home_app_light_1440_before.png)
with [current Settings](screens/android/settings-default-phone-dark.png) and
[native Dual + arc](screens/esp32/raw/esp32_dual-arc-gear-rpm_dark_240.png).

**Why:** new users cannot discover implemented display choices, and screenshots set
inaccurate expectations about current controls. The presence/absence of Expert alone
is not a defect: that destination depends on the advanced-tools preference.

**Fix:** select representative current Gauge/Car/Settings captures using ordinary
three-tab mode by default; show Advanced only when explaining it. Add Dual + arc,
brief Smart cycling guidance and a compact compatibility/limits block with Android
10+, exact board/BLE adapter and selected-reading constraints. Defer detail-heavy
omissions to linked help instead of expanding the homepage into 65 feature cards.
Use source-rendered screens with example data and retain explicit provenance; do not
present them as real Jeep readings. **Effort M; risk L.**

### F07 · P2 · Mobile hardware copy repeats and signup labels are poorly placed

**Evidence:** adjacent hardware blocks and the availability section repeat the board
purchase/software-value pitch (`website/index.html:103–105,124`). The signup email
label precedes the optional priority textarea rather than its email field. See
[hardware mobile](screens/web/web_home_hardware_light_375_before.png) and
[signup state](screens/web/web_interest_error_light_375_before.png).

**Why:** repeated prose delays the install action; an intervening control weakens the
visual relationship between Email and its label. Programmatic naming exists, so
this is not asserted as an established WCAG label failure.

**Fix:** retain one short requirements/value summary above the download CTA, move
secondary custom-service detail to its existing section, and place the email label
immediately beside its field. Preserve form action, field names, consent text/version,
retained-input behavior and APIs. Check live vendor pricing before repeating “about
US$22”; the official product page rejected automated access during this review, so
that price was not reconfirmed. **Effort S; risk L.**

### F08 · P2 · Image payload and native-resolution presentation need separate treatment

**Evidence:** two future-concept PNGs total 3,624,511 bytes, about 95% of the 3,798,305
bytes of authored site images ([asset inventory](screens/web/diagnostics/asset-sizes.json)).
They are already lazy-loaded. Lighthouse mobile flags the 240×240 hero gauge image,
rendered larger than its native pixel dimensions. App images are also relatively
small rasters, though not flagged in the sampled run.

**Why:** scrolling to future content incurs avoidable image transfer. Enlarging native
panel imagery softens type, but merely upscaling it creates no real detail.

**Fix:** optimize concept/gallery delivery with browser-encoded WebP and appropriate
fallbacks/dimensions, retain raw provenance, and use higher-density Compose captures
where useful. Keep gauge imagery circular. Bound native gauge enlargement or retain
an explicitly enlarged native-pixel illustration; do not invent a higher-resolution
panel or use AI-generated screens. **Effort M; risk L.** A perfect image-quality score
is not a reason to misrepresent the 240-pixel panel.

### F09 · P3 · Availability, labels and gallery metadata drift across hand-edited sources

**Evidence:** `website/content.json:2` has an old review date and only authority links;
feature/availability prose and layout lists are embedded separately in HTML/JS.
`styles.css:2,4–6` retains `.demo-gauge` rules with no corresponding HTML/JS element,
and several successive overrides obscure the final tokens. `index.html:3–11` has
useful title/description/no-index but no Open Graph image/description.

**Why:** independent edits can drift from runtime and artifacts again; unused legacy
presentation rules make small future changes harder to assess. Social previews are
less informative, while indexing policy is intentionally restrictive.

**Fix:** extend the existing build-time content data only for statuses, gallery labels,
source references and compatibility metadata; render escaped static HTML/JS from it
with the existing Python builder. Keep authored marketing prose where clearer.
Remove only proven-unused legacy selectors and organize remaining rules without a
visual redesign. Add self-hosted social-preview metadata while preserving no-index.
Use canonical terms from the matrix and link existing topic owners instead of
creating duplicate permanent feature docs. **Effort M; risk L–M** (selector/build
refactor needs before/after screenshots and regression checks).

## Accessibility and interaction assessment

This is a sampled review against WCAG 2.2 AA, not a conformance certification.
[Lighthouse results](lighthouse/baseline-summary.json) scored accessibility 100 for
both pages/presets. Additional checks found:

- 186 home-page text samples with straightforward solid backgrounds met the relevant
  4.5:1 or 3:1 threshold. Complex backgrounds, image text and hidden/disabled/logotype
  content were excluded. This alone is not a full contrast audit.
- Both pages retained visible focus through 45 sequential Tab steps each, after
  bounded scroll settling. Focus outlines were present. An initial immediate-after-Tab
  probe falsely saw offscreen elements during smooth scrolling; the corrected settled
  check passed. It is not reported as an unresolved product defect.
- Reduced-motion preference changed scroll behavior to auto and button transitions
  to zero. The six layout buttons expose pressed state and captions use a status
  announcement. Images have alt text; headings are hierarchical in the sampled DOM.
- Document reflow did not horizontally overflow at 320/375/768/1440 px. Small inline
  links and standalone targets were inventoried; size alone was not called a violation
  without checking spacing/inline exceptions. Touch comfort can still be improved.
- Labels, form state and honest success/failure messages work in the tested cases.
  F07 remains a visual association/flow improvement.

Criteria used: [contrast 1.4.3](https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html),
[target size 2.5.8](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html),
[focus visibility 2.4.11](https://www.w3.org/WAI/WCAG22/Understanding/focus-not-obscured-minimum.html)
and [labels 3.3.2](https://www.w3.org/WAI/WCAG22/Understanding/labels-or-instructions.html).
Screen-reader announcements, zoom/text-only resizing, forced colors, keyboard paths
beyond these samples, real Android permission dialogs and physical touch remain
unverified. Do not infer full AA compliance from an automated 100 score.

## Lighthouse baseline and verification table

Owner approved temporary Lighthouse use. Pinned **Lighthouse 13.0.1**, Chrome 156,
Node 26.10.0, isolated headless browser profile, default mobile/desktop presets and
all four categories. No project dependency was added. Runs are sequential single
local-preview observations with simulated throttling, not production distributions.
The preview lacks production TLS/cache/compression/CSP/proxy behavior; those headers
were separately read from HTTPS. Configuration follows the
[official Lighthouse guidance](https://github.com/GoogleChrome/lighthouse/blob/main/docs/configuration.md).

| Page / preset | Performance before | Accessibility before | Best practices before | SEO before | After |
| --- | --- | --- | --- | --- | --- |
| Home / mobile | 100 | 100 | 96 | 66 | 100 / 100 / 96 / 66 |
| Home / desktop | 100 | 100 | 100 | 66 | 100 / 100 / 100 / 66 |
| Recovery / mobile | 92 | 100 | 100 | 63 | 93 / 100 / 100 / 63 |
| Recovery / desktop | 99 | 100 | 100 | 63 | 99 / 100 / 100 / 63 |

[Summary metrics](lighthouse/baseline-summary.json) accompany the complete JSON/HTML
runs in [raw tool evidence](raw-tool-evidence.zip), with a [hash inventory](raw-tool-evidence-index.json). No run warnings were reported. SEO's failed crawlability audit is expected:
`robots.txt`, meta and production headers intentionally prohibit indexing. Preserve
that policy; do not chase a score by changing it. Mobile hero native resolution
explains the one best-practice failure. Recovery mobile also reports unused JavaScript
from the bundled esptool stack and unused shared CSS. Those are startup-opportunity
estimates, not permission to split a reviewed flash tool before separate regression
coverage. No broad recovery-bundle restructuring is proposed in this pass.

## Proposed Phase 4 change list for approval

| Item | Scope / files | Effort | Risk | Acceptance |
| --- | --- | --- | --- | --- |
| 1 | Current same-signer APK inventory/package and accurate download versions (`website/downloads/manifest.json`, recovery copy, package tooling only as needed); F01 | M | M | Verify actual old/new metadata, disposable-emulator upgrade/preservation, all hashes, source/signing provenance; retain immutable old URLs and fallback. |
| 2 | Replace unsupported full-backup advice in `website/recovery/index.html` and matching help; F02 | S | L | User is never told a diagnostic ZIP restores full setup; do not add App backup behavior. |
| 3 | Put feature availability beside the relevant claim; F04 | S | M | Distinguish fixed-idle candidate, phone-only RPM drafts, disabled ABS/TCS/clearing and candidate Auto. No firmware flag/control changes. |
| 4 | Refresh App/circular-gauge gallery, Dual + arc/Smart and compact compatibility guidance; F06 | M | L | Source-traced captures, explicit example provenance, default nav represented honestly, no generic vehicle support claims. |
| 5 | Clear mobile “Try it free” path and recovery phone/computer branch links using existing routes/anchors; F03/F05 | S | L | Both install paths reachable without scanning the long page, keyboard/reflow checks and 375/768/1440 captures. |
| 6 | Tighten repeated hardware copy, canonical labels and email-field association; F07 | S | L | Preserve business positioning, APIs, consent and error behavior; vendor pricing verified or clearly qualified. |
| 7 | Optimize large image delivery, preserve raw files and sensible native gauge sizing; F08 | M | L | Smaller published assets, no distortion/square gauges, meaningful alt/dimensions, no fake resolution claim. |
| 8 | Minimal build-time content/gallery/status metadata, proven dead CSS cleanup, social metadata and canonical docs links; F09 | M | L–M | One authority per topic, escaped outputs, validator/tests, unchanged no-index/security boundaries; screenshot comparison. |
| 9 | Final source/feature reconciliation, before/after captures, Lighthouse comparison, links/console/a11y/forms/build checks; all findings | M | L | Every matrix row aligned or explicitly deferred; list all limits. Prepare reviewable source, not an unapproved deployment. |

**Planned removals/rewrites:** the unsupported backup sentence; stale availability and
repeated hardware prose; obsolete selected gallery assets; proven unused `.demo-gauge`
CSS/selectors. Raw audit captures and immutable download URLs are retained. No pages,
URLs/slugs, runtime modules, customer data, credentials or device settings are deleted.
No new project dependency is proposed. The approved temporary Lighthouse tool stays
outside package/lockfiles. Full scan/scroll/dialog capture expansion and dynamic live
telemetry are not silently added to this website scope.

After approval, use a new `codex/` branch and small content/screens/accessibility/build
commits with matching docs. **The request separately requires approval before
committing/pushing:** include that authorization if you want a pushed review PR.
Main merge, new firmware releases, device installation and public website publication
remain separate concrete delivery gates. Daily website publication policy still
applies unless the owner explicitly grants an exception.

## Remaining limits and changes already made

Only `review/` outputs, a new Android instrumentation capture test and the optional
RGB565 host-capture seam changed. Android production and firmware runtime files are
untouched. Raw Node/host/capture logs retain the successful checks and failed fixture
attempts. The Chrome DevTools contrast command was unavailable; Lighthouse and the
separate measured text sample were used rather than inventing results. A browser
framed-SVG navigation briefly timed out before the same artifact loaded; its only
console error was the local review server's missing favicon, not a product-page error.

At the initial report gate, no website changes, publication, page deletion, source
commits/pushes, release or physical-device operations had occurred. The owner then
approved the fix list and delivery. Follow-up verification and identity are recorded
in [IMPLEMENTATION.md](IMPLEMENTATION.md), without changing this baseline evidence.

After values are Performance / Accessibility / Best practices / SEO. Final source
passed the repeated sampled contrast check after an initial low-contrast action
caption was corrected. That failed intermediate measurement is retained separately.
Physical qualification and full assistive-technology limits remain unchanged.
