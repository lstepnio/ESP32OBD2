# Current implementation and evidence

Updated 2026-10-19. This is the current support and installed-device snapshot.
[Backlog](backlog.md) owns unfinished work; [roadmap](roadmap.md) orders outcomes.
Dated [evidence](evidence/README.md) preserves measurements without defining today's plan.

## Verified baseline

| Concern | Current record | Evidence limit |
| --- | --- | --- |
| Source/App/firmware version | App `0.2.0-dev.51` with bounded protected-session cleanup/owner-read recovery; firmware `0.2.0-dev.60` installed at 240 MHz | Verify `android/app/build.gradle.kts`, `firmware/gauge/version.txt` and Git at session start |
| Installed Pixel and gauge | Pixel App dev.51 with the app-wide UX cleanup installed without data clearing; gauge signed dev.60 from `860dc3f`, generation 41 | Full hosted App/Wi-Fi dev.60 transfer passed; exact ELF/health 2, alternate slot, cleared journal, setup preservation and two subsequent maintenance cycles verified. Physical no-footer/hold/swipe confirmation was on dev.59; no new dev.60 pixel/touch observation was collected |
| Gauge dashboard | Revision 51; saved whole-vehicle dashboard retained | Exact revision/digest preservation passed; successful readback does not qualify every reading or alert on a vehicle |
| Current setup | Gauge and phone have distinct ECM/TCM bindings with dual mode On in one whole dashboard | Exact revision/hash and cleared trial confirmed; primary and both dashboard drafts preserved; [recovery](evidence/gatt-long-read-recovery.md#authorized-appbluetooth-recovery-and-dual-send) passed indoors; dev.53 live both-source reads/resume and each adapter loss/recovery passed |
| Devices | Waveshare ESP32-S3-Touch-LCD-1.28, GC9A01 240 × 240 LCD, CST816S; Pixel companion | 16 MB flash, 2 MB PSRAM; ESP-IDF 5.4.1 baseline |
| Vehicle/adapters | Modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / confirmed PCS TCM-2800; two same-model adapters reported by owner | Dev.53 live simultaneous source reads and independent sibling continuity/recovery passed; sustained-load qualification remains open |
| Repository | Public `lstepnio/ESP32OBD2`; app-wide cleanup merged through PR #53; indoor test foundations/roadmap and version-only dev.60 merged through PR #55 | Recheck branch, worktree, PR and CI live; never infer main merge from a branch push |

UI rollout: [Round 3 feedback and native rendering evidence](evidence/gauge-performance-review.md#2026-10-14-prepared-dev54-screen-compositor-and-showcase). Pixel dev.51 is installed with a bounded ATT resource-pressure read retry. Signed dev.55 from `9c6c85c`, generation 36, passed full App/Wi-Fi installation and post-boot preservation checks. The 21-frame showcase start/advance/stop/preservation journey passed and playback was restarted for owner review. Owner confirmed the demo looks good, cycles correctly, exits on hold and returns to normal pages with working swipes. Live vehicle CEL/alert qualification remains open.

Latest App cleanup: [screen/function audit and verification](evidence/product-optimization.md#2026-10-18-app-wide-ux-and-functionality-cleanup).
The updated debug App is installed on Pixel; protected connection, focused settings/
pairing-help/update checks and unchanged phone setup passed. Build/lint, 201 JVM tests
and 64 distinct isolated UI/history cases passed. Signed dev.59 now hides Demo text during
explicit gauge showcase playback. Its hosted App/Wi-Fi rollout and two maintenance
cycles passed; see the [deployment record](evidence/product-optimization.md#dev59-signed-wi-fi-rollout).

Earlier indoor CPU/display trial: signed dev.58 was installed through App/Wi-Fi.
Boot logging confirms 240 MHz versus the dev.57 160 MHz baseline. Two maintenance
cycles passed at each setting; dev.58 protected AP opens measured 1,607/1,634 ms
versus 1,837/1,936 ms, an insufficient sample for a reliable performance claim.
The untouched 21-frame showcase/stop/readback passed; the owner reports no
clipping/corruption and normal page/swipes. See the
[dated trial](evidence/esp32-memory-hardening.md#2026-10-18-dev58-240-mhz-bench-trial).
Dev.58 subsequently received the complete signed dev.59 App/Wi-Fi upload at 240 MHz.
Loaded/thermal qualification remains open.

Latest indoor reliability: thirty dev.59 authenticated Wi-Fi open/read/close cycles
passed with unchanged boot/image/setup and no accumulating close-memory loss.
Recorded allocation failures and queue drops stayed zero; internal heap low watermark
was 94,179 bytes and the smallest sampled largest block was 31,744 bytes. A deliberate
phone Wi-Fi loss failed the old socket in 191 ms and authenticated a fresh session
with unchanged state. App resume, Bluetooth off/on, cancelled gauge selection and
App process restart also passed. After a controlled serial reset, same-App foreground
resume restored protected connection/settings in 3,501 ms with owner/setup retained.
See [scope and measurements](evidence/esp32-memory-hardening.md#2026-10-18-dev59-indoor-recovery-and-memory-qualification).
Signed dev.60 then passed controlled reset checkpoints at 10/50/90% and verified
before activation, App process loss during reception, automatic previous-image
reconciliation and a final healthy installation. Two dev.60 maintenance cycles and
fresh protected App/settings/hardware checks passed. See [OTA observations and limits](evidence/esp32-memory-hardening.md#2026-10-18-dev60-signed-ota-interruption-qualification).
One owner preflight failed before a reset boundary; fresh protected reads regained
access without bond repair. Its cause remains unresolved. Loaded dual-adapter,
thermal, configuration-commit interruption, trial rollback and rail-power loss
remain separate gates. Completed CODE-03 implementation/bench work is consolidated
here; its remaining loaded acceptance is owned by QUAL-08.

Latest vehicle qualification: [dev.53 fresh pairing recovery](evidence/gatt-long-read-recovery.md#dev53-authorized-pairing-recovery-outcome).
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
| Expert workspace | Compact task menu; useful support facts and recovery actions; optional adapter and code-filter sheets; obsolete/raw/duplicate data and synthetic notice UI removed | Updated debug App installed on Pixel; protected Gauge ready, focused menu/sheet checks and unchanged profile/association/preferences backups; 199 JVM and nine emulator UI tests passed | Broader accessibility and live-vehicle fault workflows remain separate |
| Car workspace | Selected car, compact health/recent alerts, focused code/history sheets; exact coverage in Expert | [Pixel visual review and fifteen focused emulator fixtures](evidence/car-ux-review.md); existing phone setup preserved | Live vehicle alert/fault UX qualification |
| Shared alert/history framework | Threshold/MIL/synthetic producers share lifecycle, attention, bounded gauge context/journal, phone history/export and opt-in monitoring | Dev.48 Pixel/gauge synthetic Entry/Expired import and shared context/banner; 162 JVM tests, eight earlier emulator fixtures | Real vehicle alerts/context, physical attention, background/OEM and storage/power-loss matrix |
| Pages/catalog/alerts | Five renderers; one catalog with 53 Mode 01 and two existing JSS readings; any selectable reading can alert, including named gear conditions | Software vectors, editor fixtures and preserved installed dashboard | New decimals/rendering, hidden-page alerts, dwell/stale behavior on real vehicle |
| Configuration | Coupled reviewed payload, dual storage generations, running revision/hash, trial/fallback | Prior protected sends and restart/recovery readback | Power/interruption and all-layout hardware matrix |
| Firmware updates | Signed catalog/bundle, owner bootstrap, private Wi-Fi, A/B boot health and durable journal | Full signed dev.60 App/Wi-Fi upload at 240 MHz; exact healthy alternate image, cleared journal and setup preserved; 10/50/90% and verified/preactivation serial-reset recovery, App process-loss recovery and subsequent maintenance passed | One owner preflight failure recovered through fresh reads; original cause, trial-boot rollback, rail-power loss and loaded live-adapter qualification remain |
| Phone persistence | Schema 12 coupled profiles/assignments; serialized IO; cancellation-safe writes and typed pairing failures | 156 JVM tests, 18 emulator fixtures; initial Pixel migration retained data | Process/storage failure and broader OEM cases; later child removal cause remains unconfirmed but cleanup was accepted |
| Multiple adapters | One logical vehicle, optional Expert child, independent firmware workers and source freshness; direct child picker and scoped selection fix installed on Pixel | [Setup review](evidence/second-adapter-setup.md); [dev.53 live reads/resume, independent loss/recovery and phone off/on](evidence/gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification); owner confirmed live ECM/TCM display and independent expiry/return | Startup absence, loaded maintenance, rates and sustained soak remain open |
| Health measurements | Fixed worker records, queue drops, explicit internal/DMA/PSRAM lifecycle logs, allocation failure hook and sampled stack minima | [Bench and upload evidence](evidence/esp32-memory-hardening.md): zero recorded allocation failures/queue drops; 30 dev.59 close samples stable within 144 bytes after warm-up; dev.60 receiver internal minimum 93,975 bytes, largest block 31,744 bytes and OTA stack margin 1,340 bytes | Longer controlled loaded soak, missed transient/vendor-private failures and complete stack/progress-path coverage |

Dev.57 [memory hardening](evidence/esp32-memory-hardening.md) retains dev.56's relocation of the
fixed LVGL arena and SDK-supported BSS to PSRAM, reducing linked internal static
use by 84,664 bytes. It adds build budgets, checked Wi-Fi service creation, stronger
admission, immutable status snapshots and allocation/stack diagnostics. Local/CI,
signed App/Wi-Fi installation and saved-state checks passed. Thirty dev.56 cycles
passed in 284.124 seconds without a reboot or accumulating close-memory loss.
The complete upload/signature verification received by optimized dev.56 retained
94,991 bytes minimum internal heap and a 31,744-byte largest block, with zero
recorded allocation failures. Dev.57 adds 1 KiB application stack after earlier
488..552-byte margins; two subsequent Wi-Fi cycles passed and sampled application
headroom reached 1,512 bytes. All 21 protected showcase frame indices, stop and
actual saved-state readback passed on dev.57; owner separately confirmed a normal
page, clean text and working swipes. The USB logger is closed and the shared wake helper is inactive.
Early Bluetooth failures and one duplicate-chat force-stop preceded the successful
upload, which required a Pixel Bluetooth reset. The App now has bounded protected
GATT cleanup and owner-read retries; the original failure cause and broader automatic
recovery remain open. Loaded dual-adapter/phone soak is still pending.

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
The [paired-phone provider guide](architecture/phone-alert-providers.md) documents the
synthetic fixture/relay seam and future authorized crowd-alert integrations. The
former synthetic Expert button was removed; no live feed is enabled. The
[companion-content architecture](architecture/phone-companion-content.md) has an
initial [removable, inactive experiment](development/companion-session-experiment.md):
shared authenticated session extraction and scripted synthetic road-alert tests.
Normal App operations still use one-shot sessions. No background content stream,
navigation/media integration, new permissions, firmware change or device installation
is part of this slice; physical session reuse remains unqualified. The owner permits
the clearly marked experimental package in both debug/release APKs; packaging does
not activate it or add a device/UI entry point. Local JVM tests, debug/release
assembly, lint, instrumentation compilation and documentation checks passed;
APK inspection confirmed the marked harness in both ordinary application packages.

Engine clearing code and readback exist behind a default-off firmware gate. TCM clearing,
freeze-frame collection and hardware qualification remain open. The shared framework shipped
in signed dev.48; that rollout passed protected image identity and owner display/swipe checks.
The installed firmware is tracked in the baseline table above. See [implementation evidence](evidence/shared-alerts-implementation.md)
for the distinct offline, Pixel and gauge scopes. The backlog owns remaining tasks.

Advanced CEL filtering: exact vehicle/controller rules and complete-check MIL/count reconciliation were introduced in dev.55 and remain in installed dev.60. Raw evidence and history remain unchanged. Live vehicle qualification remains pending; [shared policy](architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters) owns semantics.
