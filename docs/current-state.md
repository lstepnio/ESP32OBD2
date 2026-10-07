# Current implementation and evidence

Updated 2026-10-07. This is the authoritative status summary for this checkout.
A source build, a simulated response and an owner observation are different evidence.
Recheck Git, installed firmware and connected devices before a new hardware session.

## Baseline

- Board: Waveshare ESP32-S3-Touch-LCD-1.28, GC9A01 240 × 240 LCD, CST816S touch,
  16 MB flash and 2 MB PSRAM. ESP-IDF 5.4.1 with pinned components.
- App: native Kotlin/Jetpack Compose Android, package `com.lstepnio.egauge`.
  iOS work is deferred. Normal app gauge values are labelled previews, not live telemetry.
- Repository: public `lstepnio/ESP32OBD2`, verified 2026-10-06. Release discovery
  verifies signatures and compatibility; visibility alone does not prove feed availability.
- Latest recorded physical gauge: `0.2.0-dev.40`, configuration revision 29.
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
| Settings | Brightness, orientation, Metric/Imperial units and automatic saved-page interval persisted through existing settings path; initial app units follow locale region, saved choice/confirmed gauge wins | Physical units/persistence and automatic-cycle checks remain separate from protected readback |
| Dashboards/alerts | Numeric, Arc, Bar, Trend and Dual renderers; bounded page/alert configuration, stronger palette and enlarged arc; host logic tests | Daylight/color review, alert transitions and hidden-page behavior on real vehicle |
| Configuration | Atomic slots, exact review/projection, revision/hash conflict checks, trial/fallback and stored/running identity | Physical interruption matrix and all supported layouts/alerts on hardware |
| Updates | Signed development catalog/bundle verification, owner BLE bootstrap, authenticated private Wi-Fi transfer, A/B trial confirmation and recovery journal; App/Wi-Fi installs recorded | Hardware power-loss/expiry matrix, production trust and performance measurements |
| Multiple adapters | Android vehicles have an optional Expert TCM child, profile schema 7; remembered gauges retain independent vehicle/source contexts. Source dev.42 adds independent ECM/TCM workers and the debug app combined send/status path | Not installed; two-adapter plus phone and recovery matrix pending; public link capacity is one |

## Public and experimental boundaries

Public capability JSON keeps `configWrite:false` and `ota:false`. The bounded
extensions (`cfg:4`, `ad:1`, `ds:3`, hardware and optional Wi-Fi features) expose
specific development paths; they are not general production support claims.
Public flags must follow implementation and physical evidence.

TCM temperature request 2204FE uses only the first byte as A−40 °C. Other bytes
remain uninterpreted. Captured current/target gear requests 225503/225504 compare
P=0D, R=0B, N=00 and stationary Drive=01; Drive is not a distinct selector value.
Pressure 225034 remains raw only. Shaft speed, converter slip/lockup and complete
OEM fault inventory lack matching validated definitions. Code clearing is a design,
not an implemented action. [Off-road profile actions and gesture triggers](architecture/vehicle-actions.md)
have a source-only local page-action foundation; high idle and ABS/ESC control are not enabled. Simulated data never establishes vehicle compatibility.

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

## Automatic connection and settings UX update

Source implements one shared connection widget across native pages, an expandable
phone/gauge/active-adapter summary, concise active-source Car faults and automatic
foreground settings/adapter/diagnostics reads. Read failures back off independently;
ordinary Settings rows no longer require a read tap. Multiple-link aggregation is
tested; source dev.42 adds the gated two-source runtime and readback. Installed
dev.40 still executes one source.

Firmware candidate `0.2.0-dev.40` hardening separates display snapshots from NVS persistence, bounds writer
admission, makes busy status reads retryable, and bounds prompt deadline arithmetic
and RX draining. See [interaction/recovery policy](architecture/interaction-recovery.md)
for exact behavior, simulated screenshots and remaining physical checks. These
changes are installed on the Pixel and physical gauge through the signed App/Wi-Fi path.
The owner confirmed a normal physical page and working swipes.

Verification: 78 Android unit tests, debug APK and instrumentation compilation,
Android lint, eight isolated Pixel UI tests including 76 screenshot comparisons,
72 offline host tests, repository validation and the ESP-IDF firmware build passed.
The physical post-update test passed automatic protected reads on open/resume,
Bluetooth off/on recovery and Add gauge cancellation recovery, retaining settings,
vehicle profile contents and configuration revision/hash within that test. Exact
firmware version/ELF identity and healthy OTA state were asserted before reconnect.
Wire packet layouts, opcodes and public capacity flags are unchanged; busy snapshot
reads now return a retryable ATT resource error. See the focused
[rollout evidence](development/car-connection-rollout.md) for evidence boundaries
and remaining hardware qualification.

## Dual-source development follow-up

Source candidate `0.2.0-dev.42` implements simultaneous source workers, independent
fault/status snapshots and source-scoped recovery. Android uses one atomic combined
projection behind an explicit per-gauge Expert choice and development `da:1` gate.
This candidate has not been published, installed or physically qualified. The owner
still has one adapter. Software verification: 84 Android unit tests, 73 offline
host tests, production C compiler/core sanitizer fixtures, firmware/debug builds,
lint, instrumentation compilation and repository validation passed. Native UI and
dual hardware harness execution remain pending. Follow the [dual-adapter recovery plan](development/dual-adapter-recovery.md)
for exact software bounds and the parked/bench matrix; installed dev.40 evidence
above does not establish dev.42 physical behavior.

## Profile action foundation

Source dev.42 adds `Car > Actions`, persisted profile bindings and a local page
shortcut using repeated upward gauge swipes (default three within five seconds).
The existing atomic config/readback path carries the choice through `cfg:4` and
optional schema-2 `actions`. Installed dev.40 has no gesture-action support. No
vehicle commands are enabled; high idle and ABS/ESC remain controller research and
physical qualification work. See [ADR-013](architecture/vehicle-actions.md) for bounds,
interaction/recovery tests and the remaining execution contract.

Action foundation verification: 89 Android unit tests, three isolated native emulator
UI tests, 73 offline host tests, production C configuration/gesture sanitizer fixtures,
debug/test APK builds, lint, firmware build and contract/link checks passed. The native
example and exact limits are recorded in ADR-013. Physical gesture tests, signed
publication and device installation have not occurred; installed dev.40 stays the
last qualified image. Earlier dual-source test totals describe that development
increment, not the latest action increment.

The matching source App is `0.2.0-dev.35` (version code 35). Install it before
a dev.42 gauge candidate: older Apps reject the new development `cfg:4` version.
No signed dev.42 release/catalog entry has been published.

Four Car screenshot references were updated and compared successfully on the API-36
emulator. The full Pixel golden suite remains pending: unchanged compact Settings
references show small arrow-glyph differences on this emulator. Original Settings
references and strict comparison tolerance are retained; ADR-013 records the limits.
