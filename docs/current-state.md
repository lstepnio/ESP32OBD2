# Current implementation and evidence

Updated 2026-10-27. This owns implemented/installed facts and their evidence limits.
The [backlog](backlog.md) owns task status; the [roadmap](roadmap.md) owns outcome order.
PR #101 is merged, App dev.52 remains installed on the Pixel, and signed dev.65
is installed through App/Wi-Fi. [Current rollout evidence](evidence/action-pages-indoor.md#2026-10-27-dev65-continuity-and-action-ui-rollout)
separates exact healthy-image/setup readbacks, indoor tests and owner display/touch
confirmation from the remaining loaded Jeep checks.
The opt-in [parked idle candidate](architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate)
is installed and ready for supervised Air up/Winch checks using the
[one-visit runbook](development/jeep-action-qualification.md#one-visit-idle-test-candidate).
The owner subsequently reported successful Air up operation, with an unexpected
“Coolant unavailable” note. The UI flag mismatch is corrected in installed dev.63; countdown duration
and actual coolant availability during that run are not yet confirmed.
Ordinary builds still disable vehicle controls; both qualification masks remain zero
and ABS/TCS execution remains disabled.

## Continuity update

The owner approved PERF-01 on 2026-10-27. Dev.65 source adds page-cache continuity,
cycle-pause recovery, prioritized/shared polling, display-only rate classes and
stable CEL presentation. It includes the approved action UI from published dev.64.
[Implementation and qualification](development/gauge-continuity-plan.md) records
scope, offline checks and the at-home/parked sequence. Signed installation, five
Wi-Fi lifecycles, automatic/Bluetooth recovery and settings persistence passed.
The owner confirmed clean pages, action navigation, no CEL question mark and
ten-second cycling/resume after touch. Loaded ECM/TCM reading continuity and
control/thermal timing still require the Jeep.

Prepared dev.66 addresses a concrete startup retry delay: an unanswered first
code-list scan now retries from two seconds with bounded exponential backoff,
instead of immediately waiting its steady 60/120-second interval. It also logs
changes in MIL/filter presentation for attribution. Warning and filter validation
remain strict. Automatic MIL status checks now have a 60-second floor; gauge-only
last-successful presentation bridges refresh/backoff gaps within the
[diagnostic retention limits](architecture/alerts-and-diagnostics.md#mil-code-and-coverage-semantics).
Raw phone/history freshness remains unchanged. The owner's later in-vehicle startup CEL
observation is not yet attributed by a raw capture; [investigation evidence](evidence/action-pages-indoor.md#2026-10-27-startup-cel-investigation)
separates that report from the source fix. Dev.65 remains installed.
The same candidate removes decorative play/stop glyphs from shared action progress
states at the owner's request; colored progress rings and action controls remain.

## Smart page cycling source milestone

FEATURE-11 is implemented offline with the approved defaults: one opt-in Settings
switch, existing interval selection, alert-driven/thermal/context priorities,
minimum viewing/manual protection, bounded priority insertions and saved-page
fairness. The [runtime contract](architecture/smart-page-cycling.md) owns behavior
and tuning, and [offline evidence](evidence/smart-page-cycling.md) records executed
checks. The additive `sp:1` protected settings path and revision-matched sidecars
preserve legacy display compatibility. Existing public capability flags remain off.
This source milestone is not installed on the Pixel/gauge; the baseline below
remains authoritative. Matched loaded timing, comfort and resource qualification
remain in the [milestone procedure](development/smart-page-cycling-plan.md).

## Installed baseline

| Item | Last recorded state | Evidence |
| --- | --- | --- |
| Pixel | App `0.2.0-dev.52` from `a33cda4`, included in merged PR #99; compact action list/editor, profile schema 17 / associations 2 preserved | [Dev.64 rollout](evidence/action-pages-indoor.md#2026-10-27-dev64-action-ui-rollout); focused action UI and automatic/Bluetooth recovery passed against dev.63 |
| Gauge | Signed parked-idle `0.2.0-dev.65` from `71400f8`, catalog generation 46, 240 MHz, configuration revision 57 | [Dev.65 App/Wi-Fi rollout](evidence/action-pages-indoor.md#2026-10-27-dev65-continuity-and-action-ui-rollout); exact healthy alternate image, cleared journal, preserved setup; same-setup restart intentionally advanced revision 56 to 57 |
| Physical display/touch | Owner confirmed clean normal pages, swipes, three-upward-swipe action entry, action browse/downward return, no CEL question mark and ten-second cycling/resume after touch on dev.65 | [Dev.65 owner observations](evidence/action-pages-indoor.md#2026-10-27-dev65-continuity-and-action-ui-rollout); loaded readings, glove/edge and sustained touch qualification remain |
| Hardware | Waveshare ESP32-S3-Touch-LCD-1.28, 240 × 240 GC9A01, CST816S, 16 MB flash, 2 MB PSRAM | Qualified toolchain baseline ESP-IDF 5.4.1 |
| Vehicle | Modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / PCS TCM-2800; two same-model adapters | [Dev.53 live simultaneous sources and independent recovery](evidence/gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification) |
| Saved setup | One whole-vehicle dashboard, distinct ECM/child-TCM bindings, dual mode On | Five pages, four exact TCM CEL filters and action settings retained at revision 57. Other running settings and phone profiles preserved. [Current preservation check](evidence/action-pages-indoor.md#2026-10-27-dev65-continuity-and-action-ui-rollout) |

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
| Phone persistence | Schema 17 profiles / schema 2 assignments; serialized IO; cancellation-safe writes and typed pairing failures | 156 JVM tests, 18 emulator fixtures; initial Pixel migration retained data | Process/storage failure and broader OEM cases; later child removal cause remains unconfirmed but cleanup was accepted |
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
sizes are unchanged; status adds idle-only reason/flags. The matching App and firmware are installed; parked fallback/thermal-stop
qualification is still outstanding.
Public vehicle-command qualification/capability stays disabled.

## Prepared four-tire Air up estimate

The App supports a per-vehicle pressure/time calibration, starting/target PSI and
an optional duration override for all four tires together. The resolved timer uses
existing command/configuration fields; there is no pressure telemetry or firmware
buffer/poller. [The timer policy](architecture/vehicle-actions.md#four-tire-air-up-timer)
owns the calculation and limits. The matching App and firmware are installed; actual compressor/tire calibration
and enabled eGauge control are not established.

## Compatibility and public boundaries

- Phone profiles: schema 17 reads schemas 1..17; installed Pixel migrated from 13. Gauge associations use schema 2. Older Apps reject new saved state;
  downgrades require an explicit compatible migration. Legacy association files remain
  read-only migration inputs. See [Android storage](architecture/android-runtime.md#storage-and-recovery).
- Gauge configuration remains schema 2. Installed development `cfg:7`, `va:1`, `da:1`, `ad:1`
  and display settings `ds:3` negotiate implemented slices. Wire layouts belong in
  [protocol documents](README.md#protocols), not this status snapshot.
- Public `configWrite:false`, `ota:false` and qualified link capacity one remain.
  Three NimBLE slots and independent workers do not establish physical coexistence.
- Phone gauge values remain labelled previews; live companion telemetry, code clearing
  and ABS/ESC control are not enabled. High idle is limited to the installed owner-supervised parked candidate. Local page-jump gestures are implemented.
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
generally qualified profile can execute controls. The shared path is installed in dev.62;
the fully automatic Park/brake evidence adapter remains unimplemented. Installed
dev.62 adds only the explicitly built, owner-supervised idle exception linked above.
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
not eGauge execution or braking-performance qualification.

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

## Approved action pages

Owner approved the review 4 simplification on 2026-10-27. Merged source now uses
900 ms hold-to-start, one-tap Stop from either idle page, pending-state rings and
verified direct return to Ready, with explicit high-coolant blocking. Android uses
compact action rows and a focused editor. The [implementation evidence](evidence/action-pages-indoor.md#2026-10-27-approved-review-4-implementation)
records offline checks. App dev.52 is installed and checked; signed firmware dev.65
includes the simplified UI and is installed through App/Wi-Fi. Owner action navigation
and the indoor protocol/preservation checks passed. Wire formats, guards and qualification flags are unchanged.

The approved review 2 action pages were introduced in dev.61 and are refined by the installed review 4 UI. Gauge-tab configuration stores per-gauge typed
page order and timers; firmware uses one reusable face with deliberate Start,
direct Stop, attention return and bounded coolant fallback. Existing live command
qualification masks remain zero. The App/Wi-Fi installation preserved the dashboard; the owner then requested replacing the jump binding with ordered action pages at revision 55.

Offline verification: 235 debug and 210 release JVM tests; 15 focused managed-emulator
UI tests; Android lint/debug APK; 118 host Python and 11 review-server tests plus
native control/recovery fixtures; production configuration validation/runtime
fixtures and 23 action-related LVGL frames with production fonts/circular glyph
checks; ESP-IDF 5.4.1 firmware build. Touch fixtures include empty-space/hold/drag
rejection, confirmation drift, critical return, idle timeout, unresolved-operation
retention, and a stable widget count across 30 entries. These are not physical
memory/latency/gesture or vehicle qualification.

Next: complete the action-branch physical touch, edge, orientation and cycling
matrix; use the parked Jeep for the primary identity,
Park/stationary/engine proofs, actual command/Stop/restoration, coolant/fallback
latency and independent child continuity. Keep the masks disabled until those proofs
are implemented and verified.
[Runtime contract](architecture/vehicle-actions.md#ordered-action-pages) and
[backlog FEATURE-10](backlog.md) own behavior and remaining acceptance.
