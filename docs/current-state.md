# Current implementation and evidence

Updated 2026-10-20. This owns implemented/installed facts and their evidence limits.
The [backlog](backlog.md) owns task status; the [roadmap](roadmap.md) owns outcome order.
Latest source work adds shared native presentation profiles with unchanged reference output.
Latest device update installs the merged Car fault experience on the Pixel; no new
firmware installation, physical gauge display/touch or live-vehicle observation.

## Installed baseline

| Item | Last recorded state | Evidence |
| --- | --- | --- |
| Pixel | App `0.2.0-dev.51` built from merged `b8205f5`, decoded fault UX and shared 55-code lookup; local setup retained | [PR #63 Pixel rollout](evidence/product-optimization.md#pr-63-pixel-rollout) |
| Gauge | Signed `0.2.0-dev.60` from `860dc3f`, catalog generation 41, 240 MHz, configuration revision 52 | [Signed OTA reset/process-loss matrix and full upload](evidence/esp32-memory-hardening.md#2026-10-18-dev60-signed-ota-interruption-qualification); exact healthy alternate image, cleared journal, preserved setup |
| Physical display/touch | No-footer showcase, hold exit, normal pages and swipes confirmed on dev.59 | [Owner confirmation](evidence/product-optimization.md#dev59-signed-wi-fi-rollout); no new dev.60 display/touch observation |
| Hardware | Waveshare ESP32-S3-Touch-LCD-1.28, 240 × 240 GC9A01, CST816S, 16 MB flash, 2 MB PSRAM | Qualified toolchain baseline ESP-IDF 5.4.1 |
| Vehicle | Modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / PCS TCM-2800; two same-model adapters | [Dev.53 live simultaneous sources and independent recovery](evidence/gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification) |
| Saved setup | One whole-vehicle dashboard, distinct ECM/child-TCM bindings, dual mode On | Four exact TCM CEL filters applied at revision 52; other running settings and phone profiles preserved. [Configuration check](evidence/shared-alerts-implementation.md#2026-10-19-tcm-cel-filter-configuration) |

Recheck Git/CI and source version files before work. A branch push is not a merge,
release or installation. Public repository: `lstepnio/ESP32OBD2`.

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
| Phone persistence | Schema 13 coupled profiles/assignments; serialized IO; cancellation-safe writes and typed pairing failures | 156 JVM tests, 18 emulator fixtures; initial Pixel migration retained data | Process/storage failure and broader OEM cases; later child removal cause remains unconfirmed but cleanup was accepted |
| Multiple adapters | One logical vehicle, optional Expert child, independent firmware workers and source freshness; direct child picker and scoped selection fix installed on Pixel | [Setup review](evidence/second-adapter-setup.md); [dev.53 live reads/resume, independent loss/recovery and phone off/on](evidence/gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification); owner confirmed live ECM/TCM display and independent expiry/return | Startup absence, loaded maintenance, rates and sustained soak remain open |
| Health measurements | Fixed worker records, queue drops, explicit internal/DMA/PSRAM lifecycle logs, allocation failure hook and sampled stack minima | [Bench and upload evidence](evidence/esp32-memory-hardening.md): zero recorded allocation failures/queue drops; 30 dev.59 close samples stable within 144 bytes after warm-up; dev.60 receiver internal minimum 93,975 bytes, largest block 31,744 bytes and OTA stack margin 1,340 bytes | Longer controlled loaded soak, missed transient/vendor-private failures and complete stack/progress-path coverage |

## Installed Car fault experience

Merged PR #63 implements inline decoded code summaries under Car > Vehicle
health, one whole-vehicle list and focused explanations. Saved alert context and
notifications share a 55-code offline lookup with named manufacturer references; unknown/controller-specific codes
remain explicit. Technical category coverage stays in Expert. The matching APK is
installed on the Pixel: Gauge ready, all four saved TCM explanations and historical
context were observed. Live vehicle filtering and real notification appearance
remain unqualified. Lookup extension rules and reference provenance are in the
[shared alert contract](architecture/alerts-and-diagnostics.md#offline-code-explanations).

## Raised-idle coolant guard

The shared command path uses the running primary-ECM high Coolant alert as its
stop limit. Missing protection blocks Start; a known high temperature latches
Return. Unavailable coolant now permits a locally confirmed, at-most-60-second
session; data loss during idle shortens the session to the earlier of its original
expiry or 60 seconds after first loss. Recovery never extends it, and another
fallback session requires a fresh physical-gauge Start. See the
[authoritative coolant policy](architecture/vehicle-actions.md#coolant-protection-for-raised-idle).
The App displays the timer and retains Stop. The 24-byte request and 32-byte status
sizes are unchanged; status adds idle-only reason/flags. No device installation
or parked fallback/thermal-stop qualification is established by these offline changes.
Public vehicle-command qualification/capability stays disabled.

## Compatibility and public boundaries

- Phone profiles: prepared schema 15 reads schemas 1..15 (installed Pixel remains schema 13). Older Apps reject new saved state;
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

FEATURE-04 now has a [temporary-idle delivery proposal](architecture/vehicle-actions.md#first-vehicle-command-temporary-high-idle)
for Air up and Winch presets using the existing gesture/profile foundation.
The owner [reports successful high idle through JScan on this Hemi swap](vehicles/jeep/jk-handler-analysis.md#owner-reference-confirmation-2026-10-19).
Prepared source now wires App controls and the gauge's confirmed actions menu
through one typed protected command interface (`vc:1` in document mode), one primary
worker controller, bounded controller drivers, uncertainty journal and event ring.
Idle uses the captured fixed setting; numeric target drafts never become commands.
ABS/TCS Return remains Restart required. Both qualification masks are zero: no
current profile can execute controls, and none of this source is installed.
The [shared owner](architecture/vehicle-actions.md#shared-command-owner) records
memory, cancellation, persistence, admission and remaining evidence requirements.
The [offline foundation](architecture/vehicle-actions.md#implemented-offline-foundation)
records the execution boundary. The [parked follow-up](evidence/jeep-actions-live-qualification.md)
confirmed fresh ECM calibration/CVN, another physical JScan Start/Stop cycle,
inactive status after reopening the diagnostic session, and owner-observed normal
idle after one JScan process/Bluetooth-loss check. The fixed ~1,700 RPM reference
has no qualified arbitrary-target mapping. Combined ABS/traction-control reference
Off/Return replies were captured; dash warnings cleared only after the owner's
ignition cycle. Owner fourth-button Start/Return replies were positive; a later
attempt without fresh session entry failed. These are reference observations,
not enabled eGauge controls or braking-performance qualification.

## Known reliability limits

- Owner-key disappearance preceding dev.53 remains unexplained. Fresh pairing and one
  retention power cycle passed after an explicitly authorized signed USB repair; later
  controlled resets recovered ownership. See [pairing diagnosis](evidence/gatt-long-read-recovery.md#jeep-fresh-pairing-regression-diagnosis).
- [Indoor memory/recovery evidence](evidence/esp32-memory-hardening.md) records stable
  repeated Wi-Fi lifecycles and signed upload margins. One owner preflight failed and
  fresh protected reads recovered without bond repair. This does not establish immunity
  to OOM, deadlock, power loss or loaded three-link failure.
- Engine clearing/readback is implemented behind a default-off gate. TCM clearing,
  freeze frames and live clearing semantics remain unqualified. No live provider is
  enabled; the synthetic provider seam remains, while its former Expert UI was removed.
- Exact controller-scoped CEL filtering preserves raw evidence/history. Live filter
  qualification remains pending; [shared policy](architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters)
  owns reconciliation semantics.

## Resume

Select a [backlog](backlog.md) gate. Use [TCM resume](development/tcm-session-resume.md)
for parked vehicle work and App/Wi-Fi for field updates. Keep private captures and
working setup. Android and firmware are implemented; basic iOS and browser-USB
configuration are [planned scope](roadmap.md#product-boundaries), not available
runtime features.

## Shared platform interface foundation

The [reference board/display seam](architecture/platforms-and-releases.md#implemented-reference-interfaces)
is implemented in source with unchanged reference templates and BSP behavior.
Larger round/rectangular safe-area logic is tested offline; startup admits only an explicitly linked matching presentation profile.
The reference provider remains the default. Native profile/font selection and design-grid
conversion are prepared in source; additional-board providers remain private and unqualified. The extraction retained byte-identical output for all 25 captured production LVGL
frames; the linked static internal-memory count remained 163,898 bytes and the
64 KiB LVGL arena remained in PSRAM. Host tests, formatting/lint and the reference
firmware build passed. No additional board firmware, live virtual telemetry,
release promotion or device installation is established by these checks.

## Android variant quality gates

CODE-04 is completed in prepared source: the shared Android test command runs debug
and release JVM suites. Development-only positive configuration fixtures are explicitly
categorized; variant-specific release tests assert that projection gates remain closed,
standard engine configurations work and saved development data is retained. No runtime
flags or production App source changed. Exact counts and baseline failures belong in
[dated maintenance evidence](evidence/repository-maintenance.md#2026-10-20-android-variant-gates).
This is offline test coverage, not a new installed APK or physical observation.
