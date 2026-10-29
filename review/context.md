# Website alignment review: discovery context

Phase 1 snapshot, 2026-10-28 (America/Denver). No product/source edits, builds,
device operations, dependency changes, commits, pushes or deployments were made.
Only the requested review documents were created. Capture and usability findings
belong to later phases; this inventory does not establish those results.

## Authority and scope

One public monorepo was identified: [lstepnio/ESP32OBD2](https://github.com/lstepnio/ESP32OBD2).
Default branch is `main`, verified through GitHub; this checkout equals
`083b0013363a697ab832179ee1af71b4456ca181` (merged PR #124).
Firmware, Android, website, recovery tool, contracts and docs live together.
Emviary is an attribution/reference for recovery, not a second product source to
update. The separate private hardware-integration repository is outside this
review; its existence does not establish additional public board support.

Order of authority: executable default-branch/released code, release metadata,
maintained docs, then website copy. Release options and Android build variants
must accompany claims: a source path alone does not mean a feature is enabled.
There is no root CHANGELOG.md. GitHub release notes, signed build evidence and
[dated delivery evidence](../docs/evidence/dev75-indoor-rollout.md) supply the
release trail. Physical observations remain distinct from source and emulation.

### Three different delivery baselines

| Surface | Observed baseline | Consequence |
| --- | --- | --- |
| Current default branch | `083b001`; runtime tree inherited from `62d1e4b` | Audit implementation against current source, not the old public APK label. |
| Latest GitHub firmware prerelease | `dev-v0.2.0-dev.75`, target `62d1e4b`, published October 28 local time | Development candidate, not a stable production release; owner candidate includes parked-idle and orientation options. |
| Live website | Manifest source `2566880c2dd5ea2620ab3d94850449dc16200f45`, content review date `2026-10-19` | Website provenance is older than current source. This is not by itself proof every copy block differs. |
| Bundled browser recovery | dev.60 | Reviewed fallback, not automatically the newest firmware. Dynamic selection also offers current signed packages via local file selection. |
| Website APK | dev.51, debug, attested source `414cedd46a2273dd13435ddce273236b70db7b74` | Current feature screenshots/claims must not imply this older APK contains the latest App functionality. |
| Latest installed owner App | Updated source `62d1e4b`, label dev.53/versionCode 53 | Installation evidence does not make this artifact publicly downloadable or establish stable distribution. |

Live root, `/recovery/`, `/site-manifest.json` and `/downloads/releases.json` were
read over HTTPS with curl after the web reader could not access the site. The live
release listing contains no APK assets, explaining why the selector retains its
single pinned APK. No forms were submitted or recovery controls exercised.

## Product and audience

A standalone touch OBD-II gauge, configured with an Android companion, that reads
vehicle adapters directly after setup. Primary audience today is technically
comfortable DIY users, especially the development Jeep/swap setup. The free public
firmware targets one inexpensive reference board. Custom software/hardware
engagements are a separate offer, not included product capabilities.

Current field setup: 2010 Wrangler, 5.7 L Hemi swap supported by JSS, separate ECM
and PCS TCM-2800 diagnostic ports for the ZF 8HP70 installation. A normal vehicle
uses one adapter; an optional transmission adapter is a child of the same vehicle.
The two Vgate BLE adapters have simultaneous-source and independent-loss evidence
on that Jeep. This is not blanket compatibility for other vehicles/controllers.
No normal-use account or connected phone is required for autonomous gauge readings.
Phone notifications, configuration and firmware transfer require their own links.

## Stack, entry points and commands

Commands are discovered, not newly executed or fresh-clone-qualified in Phase 1.
[Root setup](../README.md#development), [AGENTS](../AGENTS.md),
[quality](../docs/development/quality.md) and [source map](../docs/ai-context.md)
own detailed instructions.

| Area | Stack and entry points | Existing commands |
| --- | --- | --- |
| Firmware | C, ESP-IDF 5.4.1; `firmware/gauge/main/main.c:1185` (`app_main`); LVGL 9.2.2, ESP LVGL port 2.7.2; NimBLE/ELM adapter path | Source ESP-IDF environment; `python tools/dev.py build --target firmware`; `test --target firmware`; `lint --target firmware`. |
| Android | Kotlin, Jetpack Compose, Material 3; `MainActivity.kt:167`, `ui/CompanionApp.kt:51`; min SDK 29 (Android 10), target/compile SDK 36, JDK 17 | `python tools/dev.py build --target android`; `test --target android`; `lint --target android`; managed fixtures: `test --target android-ui`. |
| Website | Static HTML/CSS/JavaScript, Python packager; `website/index.html`, `website/styles.css`, `website/app.js`; no SPA/router/build framework | `python tools/build_website.py --check`; full preview requires verified `--downloads` and fresh `--output`; serve output with `python3 -m http.server 8771 --bind 127.0.0.1 --directory artifacts/website-preview`. |
| Recovery | Web Serial via esptool-js 0.7.0, SparkMD5 3.0.2, esbuild 0.25.12; authored `web-recovery/{recovery.js,core.mjs,releases.mjs}`; page at `website/recovery/index.html` | `npm ci --prefix web-recovery --ignore-scripts`; `npm test --prefix web-recovery`; `npm run build --prefix web-recovery`. No dependency changes proposed in discovery. |
| Contact backend | Python standard-library HTTP/SQLite; `website/interest-api/server.py`, `backup.py` | Preserve existing Nginx/SQLite service, consent and backup boundaries. Do not test using production customer data. |

Shared commands: `python tools/dev.py lint`, `format --check`, `test`, `build`.
Use the existing venv and pinned toolchain. A separate website publication is not
part of the ordinary App/firmware build. Production static files are served from
`/docker/appdata/egauge/site` on `one.majjix.com`; no deployment is authorized in
this discovery phase.

## Display and screen inventory

### ESP32

The device drives an attached round **240 × 240 RGB565** GC9A01 LCD with CST816S
touch, not a device-served browser dashboard. Board source is
`firmware/gauge/bsp/waveshare_lcd_128_round_touch/src/waveshare_lcd_128_round_touch.c:67`.
Wi-Fi maintenance is an authenticated binary TCP service (`src/wifi_bulk.c:37,539`),
not the product website or a browser configuration UI.

Source owner: `firmware/gauge/main/src/ui.c`. Capture families to cover later:

- Icon-only early boot / waiting / touch wake.
- Pairing waiting, ready, six-digit code, owner-saved and reset confirmation.
- Numeric, Arc, Bar, Trend, Dual and Dual + arc, plus discrete gear values.
- Fresh readings, missing/stale/invalid/source-loss data, one missing dual source.
- Threshold attention/severity, CEL/fault icon, combined conditions and filtered codes.
- Action entry cue/menu: Air up, Winch and ABS/TCS; ready/unavailable, active,
  stopping/restoration, uncertain/error, thermal/missing-coolant conditions.
- Temporary showcase start/cycling/hold-exit. Calibration is bench-only.
- Connection/maintenance changes that affect those screens, without inventing a
  standalone on-device update page that is not implemented.

The existing CMake host fixture compiles production `ui.c` and real LVGL/fonts
(`firmware/gauge/tests/ui_host/ui_render_test.c:1`), replacing hardware/time/owners.
`tools/dev.py:247` runs it; `tools/render_gauge_gallery.py:10` exports six selected
240-pixel frames. **Fidelity gap:** host `lv_conf.h:3` uses 32-bit color and exports
RGB888; panel uses RGB565. Existing images are software-rendered source captures,
not physical panel photographs or an exact optical/color match. Resolve the
RGB565 capture option before calling Phase 2 color-depth-exact.

### Android

`ui/CompanionApp.kt:36` owns ten routes: Gauge, Car, Settings, Expert, Set up gauge,
Customize, Alerts, Alert settings, Car Advanced and Updates. Gauge/Car/Settings are
primary navigation; Expert appears when advanced tools are enabled. Navigation
changes from bottom bar to rail at 600 dp; shared panels adapt at 840 dp and fold
occlusion is considered (`CompanionApp.kt:173`; design tokens).

Dialogs/sheets include gauge picker/name/owner transfer, car add/delete/assignment,
primary/child adapter selection, page/layout/reading management, threshold editor,
alert detail/history/options/code filters, action-page/gesture and timer/RPM editors,
display preferences and update progress/recovery. Permission-required, Bluetooth-off,
searching, reconnecting, busy, cached, missing and failed operation states are distinct.

Existing `RepositoryGalleryTest` uses production Compose with synthetic state;
managed UI tests use a disposable API 35 Pixel 2. Available light/dark palettes and
200% text fixtures can be reused. Tablet/large-screen and full state captures need
explicit inventory in Phase 2. Do not use the owner's profiles or USB phone for it.

### Website

Two public HTML routes: `/` and `/recovery/` (slashless `/recovery` redirects).
Root anchors: experience, preview, app, platforms, offroad-actions, hardware,
roadmap, availability, custom-projects and custom-form. Root has layout selection,
update signup and custom inquiry forms. Recovery has firmware selector/local-file
upload, board confirmation, install/progress/errors, APK selector/sideload guide and
older-release instructions. APIs `/api/interest` and `/api/custom` are not pages;
`/downloads/releases.json` and `/downloads/manifest.json` supply metadata.
Only one website theme is implemented: light editorial canvas with dark gauge and
concept sections. Do not invent a second dark theme to satisfy capture coverage.

## Design system and documentation

- `design/tokens.json` owns shared product colors/spacing/type/motion; current gauge
  accent is `#00FF70`, warning `#FF8A00`, critical `#FF1744`.
- Android `core/designsystem/` generates matching light/dark semantic tokens and
  Material themes; dynamic phone colors are optional.
- Website `styles.css:1` has its own editorial paper/ink/serif palette; line 23
  overrides old lime with `#00ef70`. It references product images but does not use
  the shared token generator. This is a separate surface, not proof it needs a
  wholesale redesign. Assess drift/contrast in Phase 3.
- Gauge imagery must remain circular; neutral screen captions may coexist with
  honest capture/data provenance. Never describe synthetic scenes as live evidence.
- Root README is setup/overview; `docs/features.md` and `docs/ai-context.md` route to
  contracts, `docs/current-state.md` owns delivery, roadmap/backlog own future work,
  `docs/evidence/` retains dated proof, and `website/README.md` / `web-recovery/README.md`
  own website and recovery maintenance. `review/` is this audit snapshot, not a new
  permanent feature/status authority.

## Original Phase 1 handoff

[Feature matrix and questions](feature-matrix.md) complete Phase 1. Phase 2 screenshots,
Phase 3 findings/effort report and Phase 4 fixes have not started. No new report is
being passed off as the later expert/a11y/Lighthouse review. Ask for approval before
continuing, committing/pushing, deleting pages, changing URLs or adding dependencies.

## Review progression

The owner approved the Phase 1 recommendations and capture-only RGB565 seam.
Phases 2/3 results, verification limits and the next approval gate are recorded in
[REPORT.md](REPORT.md). The earlier discovery statements remain a Phase 1 snapshot,
not a claim that subsequent captures or checks did not occur. No product fixes or
publication have been authorized by that capture approval.
