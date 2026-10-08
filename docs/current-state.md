# Current implementation and evidence

Updated 2026-10-07. This is the authoritative status summary for this checkout.
A source build, a simulated response and an owner observation are different evidence.
Recheck Git, installed firmware and connected devices before a new hardware session.

## Baseline

- Latest installed Android and signed gauge firmware: `0.2.0-dev.47`. Authorized App/Wi-Fi installation and exact protected image/OTA health 2 verified; revision 49 and display settings retained. Owner display/touch confirmation remains pending. The owner accepted the clean single-primary-adapter setup until a second adapter arrives. See [dev.47 rollout](development/offline-hardening.md#authorized-pixelapp-wi-fi-rollout). Prior dev.46 owner normal-page/swipe evidence remains historical. See [catalog rollout evidence](development/reading-catalog-alerts.md#signed-appwi-fi-rollout-2026-10-07) and [recovery evidence](development/recovery-hardening.md) for remaining interruption gates.

- Board: Waveshare ESP32-S3-Touch-LCD-1.28, GC9A01 240 × 240 LCD, CST816S touch,
  16 MB flash and 2 MB PSRAM. ESP-IDF 5.4.1 with pinned components.
- App: native Kotlin/Jetpack Compose Android, package `com.lstepnio.egauge`.
  iOS work is deferred. Normal app gauge values are labelled previews, not live telemetry.
- Repository: public `lstepnio/ESP32OBD2`, verified 2026-10-06. Release discovery
  verifies signatures and compatibility; visibility alone does not prove feed availability.
- Latest recorded physical gauge: `0.2.0-dev.47`, configuration revision 49, protected running confirmation and cleared trial.
  Source version is in `firmware/gauge/version.txt`; it may be newer than the installed image.
- The owner has one Vgate, swapped between separate ECM and TCM connectors on a
  2010 Wrangler with 5.7 L Hemi / JSS ZF 8HP70 swap and confirmed PCS TCM-2800.
  Diagnostic endpoint/bridge topology is not fully identified.

## Feature status

| Area | Implemented and evidence | Remaining qualification |
| --- | --- | --- |
| Pairing | Foreground Android discovery, system passkey dialog, protected owner readback and firmware owner persistence; subsequent Jeep work used the paired phone | Broader bond-loss/recovery and alternate phone matrix |
| Engine telemetry | Gauge reads attributed 7E8 replies. Owner reported RPM agrees with dash, disappears after unplugging adapter and returns after reconnect | Vehicle/adapter matrix, long soak and achieved polling rates |
| Transmission | One active TCM profile routes 7E1/7E9; combined Gear + Temperature page. Owner confirmed page worked and both readings cleared/returned on adapter loss/recovery | Independent temperature sensor/scale reference, gears 2..8, current/target divergence |
| Faults | Mac captured full stored/pending/permanent TCM lists. Version 15 protected full snapshots and Android source/category groups implemented and tested offline | New long-read transfer on Pixel and full fault polling alongside Gear + Temperature |
| Settings | Brightness, orientation, Metric/Imperial units and automatic saved-page interval persisted through existing settings path; initial app units follow locale region, saved choice/confirmed gauge wins | App dev.39 protected post-restart settings persistence passed; owner confirmed five-second physical page cycling; physical unit rendering remains separate |
| Dashboards/alerts | Numeric, Arc, Bar, Trend and Dual renderers; bounded page/alert configuration, stronger palette and enlarged arc; host logic tests | Daylight/color review, alert transitions and hidden-page behavior on real vehicle |
| Configuration | Atomic slots, exact review/projection, revision/hash conflict checks, trial/fallback and stored/running identity | Physical interruption matrix and all supported layouts/alerts on hardware |
| Updates | Signed development catalog/bundle verification, owner BLE bootstrap, authenticated private Wi-Fi transfer, A/B trial confirmation and recovery journal; App/Wi-Fi installs recorded | Hardware power-loss/expiry matrix, production trust and performance measurements |
| Multiple adapters | Android vehicles have an optional Expert TCM child, profile schema 12 in source; remembered gauges retain independent vehicle/source contexts. Firmware dev.45 retains independent ECM/TCM workers; App dev.45 uses one complete vehicle dashboard and independently scoped status/faults | Installed; two-adapter plus phone and recovery matrix pending; public link capacity is one |

## Offline hardening candidate

Source App/FW dev.47 adds coupled profile/assignment commits, serialized off-main
writes, typed pairing failures, cancellation-safe cleanup, extracted persistence
boundaries and bounded worker/memory measurements. Historical runtime records were
consolidated and stale active architecture guidance corrected. The owner-authorized signed candidate is now
installed on Pixel/gauge through App/Wi-Fi. See [all seven priorities and test
evidence](development/offline-hardening.md). Exact healthy dev.47 readback and automatic resume passed; owner display/touch,
longer recovery/soak gates remain pending. The owner accepted the single-adapter
cleanup: zero optional children/dual selections, one running ECM source, combined
reading pages retained.

## Expanded readings and alerts

Installed dev.46 adds a shared catalog of 55 readings (53 Mode 01 and two
existing JSS TCM definitions), searchable reading/alert choices, decimal thresholds
and named gear conditions. Only selected pages/alerts are polled, bounded to 32
readings. TCM alerts follow the same logical vehicle and optional child routing.
App and signed firmware dev.46 are installed through App/Wi-Fi. New readings/TCM
alerts use development `cfg:5`. Exact healthy image, opening/resume, setup/settings
preservation and owner display/swipe observations passed. New reading rendering
and real-vehicle alert transitions remain unqualified. See [catalog, compatibility and evidence](development/reading-catalog-alerts.md).

## Public and experimental boundaries

Public capability JSON keeps `configWrite:false` and `ota:false`. The bounded
extensions (`cfg:5`, `ad:1`, `ds:3`, hardware and optional Wi-Fi features) expose
specific development paths; they are not general production support claims.
Public flags must follow implementation and physical evidence.

TCM temperature request 2204FE uses only the first byte as A−40 °C. Other bytes
remain uninterpreted. Captured current/target gear requests 225503/225504 compare
P=0D, R=0B, N=00 and stationary Drive=01; Drive is not a distinct selector value.
Pressure 225034 remains raw only. Shaft speed, converter slip/lockup and complete
OEM fault inventory lack matching validated definitions. Code clearing is a design,
not an implemented action. [Off-road profile actions and gesture triggers](architecture/vehicle-actions.md)
have an installed local page-action foundation; high idle and ABS/ESC control are not enabled. Simulated data never establishes vehicle compatibility.

## One vehicle dashboard and optional child route

The owner's second vehicle report showed that App dev.42/43 only unified editing
when a child existed; single sends and legacy profiles still selected source subsets.
Source App dev.44 and firmware dev.45 now send the whole vehicle dashboard through
one primary adapter, or route transmission definitions through an explicitly enabled
second adapter. Expert binding selection cannot filter pages. Mixed-controller Dual
pages work; removing a child preserves pages/gestures. Missing readings back off
independently, and each physical adapter keeps its independent worker/recovery state.

123 JVM tests, firmware build/sanitizer fixtures, ten final native Pixel tests and
protected existing-firmware readback/resume passed; phone preferences retained.
Signed dev.45 was owner-authorized, published from `07c436a` with catalog
generation 27 and installed through App/Wi-Fi. Protected readback confirmed exact
ELF identity and healthy boot. The complete six-page dashboard is now confirmed
as revision 46 on one primary ECM transport, with settings and phone profiles
retained. Display/swipe confirmation subsequently passed on dev.46; physical port-switch/dual qualification
remains pending.
See [detailed review and test plan](development/logical-vehicle-review.md).

## Phone deletion controls

App dev.43 adds visible page deletion in Customize/Manage pages and vehicle deletion
in Car > Your car, with confirmation. Keep one page per vehicle and one vehicle
profile. Last-controller page deletion is allowed; schema 9 persists empty internal
drafts while retaining bindings/alerts. Vehicle deletion leaves assigned remembered
gauges needing explicit reassignment and does not change their installed setup.
115 JVM tests and six native UI/stored-profile tests passed; firmware remains dev.44.
See [deletion evidence](development/vehicle-dashboard-review.md#app-dev43-deletion-follow-up).

## Product UX and resilience optimization

Android source dev.45 refines the shared title/status/dialog patterns, removes
routine connection duplication, preserves independent combined fault status and
provides accessible Home page actions. Pairing waits for ordinary owner readback
and leads directly to Car adapter setup. Gauge health has its own polling cadence;
read retries use bounded jitter. Hosted HTTP cancellation retires its resource and
awaits cleanup, with per-response and whole-catalog deadlines. Firmware dev.45 and
protocol/capability flags are unchanged by this increment. 136 JVM tests, 28 native
UI tests, 12 navigation/editing journey tests and 76 reviewed screenshot comparisons passed. Pixel/gauge read-only opening,
resume, Bluetooth off/on and gauge-picker cancellation passed with revision 46 and
settings retained; phone profile/association/presentation preferences were unchanged.
The final APK from `2f5b95e`, including update-verification copy, is installed.
A second protected opening/resume check passed after unlocking Android; revision 46,
display settings and phone preferences were retained. Original debug wake settings
were restored. Implementation GitHub checks passed.
See the
[assessment, prioritized backlog and verification](development/product-optimization.md).

## Next work

Follow [TCM resume instructions](development/tcm-session-resume.md) for the next
parked session. The owner deferred vehicle/BLE work; do not start it unattended.
Install through App/Wi-Fi, confirm exact firmware identity, check full fault lists
and reconnect behavior, and retain the working combined page.

[Roadmap](roadmap.md) tracks remaining priorities. [Evidence index](README.md)
links historical records. New observations update this file and the relevant
focused evidence record, rather than repeating status in every document.

## Provenance

Firmware board support originated from Janos Kutscherauer's esp32-obd2-meter at
`e1f4d8ffbb2bfe0fb38369e44d532319770ddc00`. Upstream MIT and font OFL notices remain
in the repository. Runtime, transport, ownership and UI have since changed;
the imported baseline behavior is not the current architecture.

## Historical evidence

Older runtime and device records are in the [runtime history](development/runtime-history.md).
Focused reports retain their own dated evidence. Current status belongs here.
