# Current implementation and evidence

Updated 2026-10-07. This is the current support and installed-device snapshot.
[Backlog](backlog.md) owns unfinished work; [roadmap](roadmap.md) orders outcomes.
Dated [evidence](evidence/README.md) preserves measurements without defining today's plan.

## Verified baseline

| Concern | Current record | Evidence limit |
| --- | --- | --- |
| Source/App/firmware version | `0.2.0-dev.47` | Verify `android/app/build.gradle.kts`, `firmware/gauge/version.txt` and Git at session start |
| Installed Pixel and gauge | Signed dev.47, published from `ec97641`, generation 29; App/Wi-Fi installed | Exact hosted ELF and OTA health 2 passed protected readback; dev.47 owner display/touch confirmation remains pending |
| Gauge dashboard | Revision 49; three mixed-reading pages and one alert retained | Successful readback does not qualify every new reading or alert on a vehicle |
| Current setup | One primary ECM transport; zero optional children or dual-adapter selections | Owner chose to re-add the child when a second adapter arrives; combined pages remain available |
| Devices | Waveshare ESP32-S3-Touch-LCD-1.28, GC9A01 240 × 240 LCD, CST816S; Pixel companion | 16 MB flash, 2 MB PSRAM; ESP-IDF 5.4.1 baseline |
| Vehicle/adapters | Modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / confirmed PCS TCM-2800; one Vgate | Adapter is moved between separate ports; dual-radio operation has not been measured |
| Repository | Public `lstepnio/ESP32OBD2`; continuing review PR #51 | Recheck branch, worktree, PR and CI live; never infer main merge from a branch push |

Latest rollout: [dev.47 hardening and device evidence](evidence/offline-hardening.md#authorized-pixelapp-wi-fi-rollout).
Original phone wake settings were restored. Android and firmware are active; iOS is deferred.
No live vehicle session was performed by the dev.47 rollout.

## Support and evidence

| Area | Implemented | Observed qualification | Remaining work |
| --- | --- | --- | --- |
| Pairing/connection | Android system passkey, protected owner checks, persisted owner; automatic foreground reads and bounded retries | Repeated Pixel opening/resume; Bluetooth off/on recovery; paired Jeep sessions | Bond-loss, alternate-phone and interruption matrix |
| Engine readings | Header-attributed replies and independently expiring values | Owner RPM agrees with dash; disappears after adapter removal and returns on reconnect | Adapter/vehicle matrix, rates and soak |
| TCM readings | Captured `2204FE` temperature and `225503` current gear; combined pages | Owner temperature/gear display and loss/recovery checks on the JSS setup | Independent sensor/scale meaning, gears 2..8, current/target divergence |
| Faults | Full source/category snapshots, cached protected long reads and independently scoped presentation | Mac captured stored/pending/permanent TCM lists; software fixtures | Live full App/gauge path; TCM faults through one shared primary transport |
| Display settings | Persisted brightness, orientation, Metric/Imperial and page interval; regional first-use units | Protected post-restart reads, owner five-second cycling/cooldown; App Metric save and Imperial restoration | Physical unit rendering, cycling/gesture interaction and daylight readability |
| Pages/catalog/alerts | Five renderers; one catalog with 53 Mode 01 and two existing JSS readings; any selectable reading can alert, including named gear conditions | Software vectors, editor fixtures and preserved installed dashboard | New decimals/rendering, hidden-page alerts, dwell/stale behavior on real vehicle |
| Configuration | Coupled reviewed payload, dual storage generations, running revision/hash, trial/fallback | Prior protected sends and restart/recovery readback | Power/interruption and all-layout hardware matrix |
| Firmware updates | Signed catalog/bundle, owner bootstrap, private Wi-Fi, A/B boot health and durable journal | Dev.47 authorized App/Wi-Fi install, exact healthy image, cleared journal | Power-loss, expiry, rollback and broader recovery matrix |
| Phone persistence | Schema 12 coupled profiles/assignments; serialized IO; cancellation-safe writes and typed pairing failures | 156 JVM tests, 18 emulator fixtures; initial Pixel migration retained data | Process/storage failure and broader OEM cases; later child removal cause remains unconfirmed but cleanup was accepted |
| Multiple adapters | One logical vehicle, optional Expert child, independent firmware workers and source freshness | Software/replay qualification | Second adapter plus phone coexistence, sibling continuity and sustained load |
| Health measurements | Fixed worker records, queue drops and free/largest memory in minute logs | One dev.47 minute log set; continued App/UI and adapter retry progress | Longer controlled soak, measured gap/rate and memory limits |

Software checks for the hardening increment: Android build/lint, 156 JVM tests,
18 emulator tests, 75 host tests, sanitized production C fixtures, firmware build,
repository validation and all three GitHub jobs passed. Exact scope and later
phone checks are in the linked evidence, not implied by these counts.

## Compatibility and public boundaries

- Phone profiles: schema 12 reads schemas 1..12. Older Apps reject new saved state;
  downgrades require an explicit compatible migration. Legacy association files remain
  read-only migration inputs. See [Android storage](architecture/android-runtime.md#storage-and-recovery).
- Gauge configuration remains schema 2. Development `cfg:5`, `va:1`, `da:1`, `ad:1`
  and display settings `ds:3` negotiate implemented slices. Wire layouts belong in
  [protocol documents](README.md#protocols), not this status snapshot.
- Public `configWrite:false`, `ota:false` and qualified link capacity one remain.
  Three NimBLE slots and independent workers do not establish physical coexistence.
- Phone gauge values remain labelled previews; live companion telemetry, code clearing,
  high idle and ABS/ESC control are not enabled. Local page-jump gestures are implemented.
- TCM temperature interprets only the first byte as A−40 °C. Current/target candidates
  compare P=0D, R=0B, N=00 and stationary Drive=01. Pressure `225034` remains raw only.
  Drive is not a distinct selector value. The diagnostic endpoint/PCS bridge topology
  remains unresolved. Definitions are calibration-specific; standard catalog membership
  is not vehicle support.

## Next session

Use [backlog](backlog.md) to select the highest ready item and its acceptance gate.
For parked vehicle work use [TCM resume](development/tcm-session-resume.md); field
firmware installation uses App/Wi-Fi. Keep the working dashboard and private captures.
No task should silently enable controls, promote capabilities or claim physical
verification from source, an emulator, transfer percentage or serial startup alone.

Firmware board support originated from Janos Kutscherauer's esp32-obd2-meter at
`e1f4d8ffbb2bfe0fb38369e44d532319770ddc00`. Upstream MIT/font OFL notices remain.

## Proposed shared alerts and diagnostics

[ADR-015](architecture/alerts-and-diagnostics.md) plans ECU-scoped CEL/MIL/faults,
shared threshold/diagnostic/external alert lifecycle, notifications, bounded history
and context, and explicit code clearing. These extensions are not implemented or
physically qualified by the planning change. The backlog owns their task status.
