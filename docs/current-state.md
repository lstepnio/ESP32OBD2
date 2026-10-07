# Current implementation and evidence

Updated 2026-10-06. This is the authoritative status summary for this checkout.
A source build, a simulated response and an owner observation are different evidence.
Recheck Git, installed firmware and connected devices before a new hardware session.

## Baseline

- Board: Waveshare ESP32-S3-Touch-LCD-1.28, GC9A01 240 × 240 LCD, CST816S touch,
  16 MB flash and 2 MB PSRAM. ESP-IDF 5.4.1 with pinned components.
- App: native Kotlin/Jetpack Compose Android, package `com.lstepnio.egauge`.
  iOS work is deferred. Normal app gauge values are labelled previews, not live telemetry.
- Repository: public `lstepnio/ESP32OBD2`, verified 2026-10-06. Release discovery
  verifies signatures and compatibility; visibility alone does not prove feed availability.
- Latest recorded physical gauge: `0.2.0-dev.37-tcm`, configuration revision 28.
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
| Settings | Brightness, orientation, Metric/Imperial units and automatic saved-page interval persisted through existing settings path | Physical units/persistence and automatic-cycle checks remain separate from protected readback |
| Dashboards/alerts | Numeric, Arc, Bar, Trend and Dual renderers; bounded page/alert configuration, stronger palette and enlarged arc; host logic tests | Daylight/color review, alert transitions and hidden-page behavior on real vehicle |
| Configuration | Atomic slots, exact review/projection, revision/hash conflict checks, trial/fallback and stored/running identity | Physical interruption matrix and all supported layouts/alerts on hardware |
| Updates | Signed development catalog/bundle verification, owner BLE bootstrap, authenticated private Wi-Fi transfer, A/B trial confirmation and recovery journal; App/Wi-Fi installs recorded | Hardware power-loss/expiry matrix, production trust and performance measurements |
| Multiple adapters | Source-specific model and legacy second-slot scaffolding; normal runtime uses one active source | Simultaneous adapters plus phone are unqualified; public link capacity is one |

## Public and experimental boundaries

Public capability JSON keeps `configWrite:false` and `ota:false`. The bounded
extensions (`cfg:3`, `ad:1`, `ds:3`, hardware and optional Wi-Fi features) expose
specific development paths; they are not general production support claims.
Public flags must follow implementation and physical evidence.

TCM temperature request 2204FE uses only the first byte as A−40 °C. Other bytes
remain uninterpreted. Captured current/target gear requests 225503/225504 compare
P=0D, R=0B, N=00 and stationary Drive=01; Drive is not a distinct selector value.
Pressure 225034 remains raw only. Shaft speed, converter slip/lockup and complete
OEM fault inventory lack matching validated definitions. Code clearing is a design,
not an implemented action. Simulated data never establishes vehicle compatibility.

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
