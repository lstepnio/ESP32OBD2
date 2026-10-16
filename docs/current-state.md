# Current implementation and evidence

Updated 2026-10-16. This is the current support and installed-device snapshot.
[Backlog](backlog.md) owns unfinished work; [roadmap](roadmap.md) orders outcomes.
Dated [evidence](evidence/README.md) preserves measurements without defining today's plan.

## Verified baseline

| Concern | Current record | Evidence limit |
| --- | --- | --- |
| Source/App/firmware version | App `0.2.0-dev.51` with bounded protected-read retry; signed firmware dev.55 installed | Verify `android/app/build.gradle.kts`, `firmware/gauge/version.txt` and Git at session start |
| Installed Pixel and gauge | Pixel App dev.51 installed without data clearing; gauge signed dev.55 from `9c6c85c`, generation 36 | Full hosted App/Wi-Fi transfer, exact signed ELF/health 2, alternate slot and unchanged dashboard/settings/profiles verified; owner confirmed dev.55 demo appearance/cycling, hold-to-exit and normal pages/swipes |
| Gauge dashboard | Revision 50; three mixed-reading pages and one alert retained | Successful readback does not qualify every new reading or alert on a vehicle |
| Current setup | Gauge and phone have distinct ECM/TCM bindings with dual mode On in one whole dashboard | Exact revision/hash and cleared trial confirmed; primary and both dashboard drafts preserved; [recovery](evidence/gatt-long-read-recovery.md#authorized-appbluetooth-recovery-and-dual-send) passed indoors; dev.53 live both-source reads/resume and each adapter loss/recovery passed |
| Devices | Waveshare ESP32-S3-Touch-LCD-1.28, GC9A01 240 × 240 LCD, CST816S; Pixel companion | 16 MB flash, 2 MB PSRAM; ESP-IDF 5.4.1 baseline |
| Vehicle/adapters | Modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / confirmed PCS TCM-2800; two same-model adapters reported by owner | Dev.53 live simultaneous source reads and independent sibling continuity/recovery passed; sustained-load qualification remains open |
| Repository | Public `lstepnio/ESP32OBD2`; continuing review PR #51 | Recheck branch, worktree, PR and CI live; never infer main merge from a branch push |

UI rollout: [Round 3 feedback and native rendering evidence](evidence/gauge-performance-review.md#2026-10-14-prepared-dev54-screen-compositor-and-showcase). Pixel dev.51 is installed with a bounded ATT resource-pressure read retry. Signed dev.55 from `9c6c85c`, generation 36, passed full App/Wi-Fi installation and post-boot preservation checks. The 21-frame showcase start/advance/stop/preservation journey passed and playback was restarted for owner review. Owner confirmed the demo looks good, cycles correctly, exits on hold and returns to normal pages with working swipes. Live vehicle CEL/alert qualification remains open.

Latest rollout: [dev.53 fresh pairing recovery](evidence/gatt-long-read-recovery.md#dev53-authorized-pairing-recovery-outcome).
During the subsequent Jeep session, the gauge booted with its owner key absent while
the Pixel retained its old bond. Fresh pairing failed because dev.52 was compiled with
NimBLE security level 1. Dev.53 uses neutral global level 0 with a production build guard.
Its separately authorized signed USB repair, fresh pairing and one owner-retention
power cycle passed. Live both-source reads/resume, independent ECM/TCM loss/recovery
and a phone Bluetooth off/on journey passed; the wider dual qualification matrix remains open.
The owner-key disappearance remains unexplained; see [field diagnosis](evidence/gatt-long-read-recovery.md#jeep-fresh-pairing-regression-diagnosis).
Original phone wake settings were restored after the Jeep session. Android and firmware are active; iOS is deferred.
No live vehicle session or ECU clear was performed by the dev.48 rollout.

## Support and evidence

| Area | Implemented | Observed qualification | Remaining work |
| --- | --- | --- | --- |
| Pairing/connection | Android system passkey, protected owner checks, persisted owner; automatic foreground reads and bounded retries | Repeated Pixel opening/resume; Bluetooth off/on recovery; paired Jeep sessions | Dev.53 fresh pairing/one power-cycle retention passed; original owner-key loss remains unexplained; additional retention, bond-loss, alternate-phone and interruption matrix |
| Engine readings | Header-attributed replies and independently expiring values | Owner RPM agrees with dash; disappears after adapter removal and returns on reconnect | Adapter/vehicle matrix, rates and soak |
| TCM readings | Captured `2204FE` temperature and `225503` current gear; combined pages | Owner temperature/gear display and loss/recovery checks on the JSS setup | Independent sensor/scale meaning, gears 2..8, current/target divergence |
| Faults | Independent controller endpoints across a primary/child transport, full categories, fair polling and global presentation | Mac captured stored/pending/permanent TCM lists; software fixtures | Live full App/gauge path; TCM faults through one shared primary transport |
| Display settings | Persisted brightness, orientation, Metric/Imperial and page interval; regional first-use units | Protected post-restart reads, owner five-second cycling/cooldown; App Metric save and Imperial restoration | Physical unit rendering, cycling/gesture interaction and daylight readability |
| Car workspace | Selected car, compact health/recent alerts, focused code/history sheets; exact coverage in Expert | [Pixel visual review and fifteen focused emulator fixtures](evidence/car-ux-review.md); existing phone setup preserved | Live vehicle alert/fault UX qualification |
| Shared alert/history framework | Threshold/MIL/synthetic producers share lifecycle, attention, bounded gauge context/journal, phone history/export and opt-in monitoring | Dev.48 Pixel/gauge synthetic Entry/Expired import and shared context/banner; 162 JVM tests, eight earlier emulator fixtures | Real vehicle alerts/context, physical attention, background/OEM and storage/power-loss matrix |
| Pages/catalog/alerts | Five renderers; one catalog with 53 Mode 01 and two existing JSS readings; any selectable reading can alert, including named gear conditions | Software vectors, editor fixtures and preserved installed dashboard | New decimals/rendering, hidden-page alerts, dwell/stale behavior on real vehicle |
| Configuration | Coupled reviewed payload, dual storage generations, running revision/hash, trial/fallback | Prior protected sends and restart/recovery readback | Power/interruption and all-layout hardware matrix |
| Firmware updates | Signed catalog/bundle, owner bootstrap, private Wi-Fi, A/B boot health and durable journal | Dev.48 App/Wi-Fi install and dev.49/dev.50/dev.51 signed App/Bluetooth recovery, exact healthy images and cleared journals; dev.51 two authenticated Wi-Fi open/read/close cycles without reboot; dev.52 full signed hosted Wi-Fi upload/alternate-slot activation with unchanged setup | Wi-Fi interruption and sustained transfer matrix; memory/radio qualification with live dual adapters |
| Phone persistence | Schema 12 coupled profiles/assignments; serialized IO; cancellation-safe writes and typed pairing failures | 156 JVM tests, 18 emulator fixtures; initial Pixel migration retained data | Process/storage failure and broader OEM cases; later child removal cause remains unconfirmed but cleanup was accepted |
| Multiple adapters | One logical vehicle, optional Expert child, independent firmware workers and source freshness; direct child picker and scoped selection fix installed on Pixel | [Setup review](evidence/second-adapter-setup.md); [dev.53 live reads/resume, independent loss/recovery and phone off/on](evidence/gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification); owner confirmed live ECM/TCM display and independent expiry/return | Startup absence, loaded maintenance, rates and sustained soak remain open |
| Health measurements | Fixed worker records, queue drops and free/largest memory in minute logs | Dev.47 sample and dev.53 approximately ten-minute dual field capture; App/UI progress, zero recorded queue drops and internal minimum 44,843 bytes | Longer controlled soak, measured gap/rate and memory limits |

Software checks for the hardening increment: Android build/lint, 156 JVM tests,
18 emulator tests, 75 host tests, sanitized production C fixtures, firmware build,
repository validation and all three GitHub jobs passed. Exact scope and later
phone checks are in the linked evidence, not implied by these counts.

## Compatibility and public boundaries

- Phone profiles: prepared schema 13 reads schemas 1..13. Older Apps reject new saved state;
  downgrades require an explicit compatible migration. Legacy association files remain
  read-only migration inputs. See [Android storage](architecture/android-runtime.md#storage-and-recovery).
- Gauge configuration remains schema 2. Prepared development `cfg:6`, `va:1`, `da:1`, `ad:1`
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

Auto orientation is specified in the [hardware policy](architecture/hardware-and-transports.md#automatic-display-orientation)
and tracked as FEATURE-07. The current IMU path is unimplemented; manual rotation
and timed page cycling remain independent implemented controls.

## Next session

Use [backlog](backlog.md) to select the highest ready item and its acceptance gate.
For parked vehicle work use [TCM resume](development/tcm-session-resume.md); field
firmware installation uses App/Wi-Fi. Keep the working dashboard and private captures.
No task should silently enable controls, promote capabilities or claim physical
verification from source, an emulator, transfer percentage or serial startup alone.

Firmware board support originated from Janos Kutscherauer's esp32-obd2-meter at
`e1f4d8ffbb2bfe0fb38369e44d532319770ddc00`. Upstream MIT/font OFL notices remain.

## Shared alerts and diagnostics baseline

Dev.48 implements [ADR-015](architecture/alerts-and-diagnostics.md): threshold/MIL/fault
producers share severity/lifecycle, autonomous gauge attention and scoped acknowledgment,
bounded transitions/context, transactional phone history/export and opt-in Android
notifications through one connection owner. Threshold dwell/hysteresis remains unchanged.
The [paired-phone provider guide](architecture/phone-alert-providers.md) documents a
synthetic Expert test and future authorized crowd-alert integrations. No live feed is enabled.

Engine clearing code and readback exist behind a default-off firmware gate. TCM clearing,
freeze-frame collection and hardware qualification remain open. The shared framework shipped
in signed dev.48; that rollout passed protected image identity and owner display/swipe checks.
The installed firmware is tracked in the baseline table above. See [implementation evidence](evidence/shared-alerts-implementation.md)
for the distinct offline, Pixel and gauge scopes. The backlog owns remaining tasks.

Prepared Advanced CEL filtering: exact vehicle/controller rules and complete-check MIL/count reconciliation are implemented for dev.55. Raw evidence and history remain unchanged. Dev.55 is signed and installed through App/Wi-Fi; live vehicle qualification remains pending; [shared policy](architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters) owns semantics.
