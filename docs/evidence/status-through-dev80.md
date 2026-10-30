# Status snapshots through dev.80

> Historical evidence. Exact pre-consolidation current-state and backlog prose retained
> on 2026-10-29. These snapshots contain superseded claims and pending instructions.
> [Current state](../current-state.md) and [backlog](../backlog.md) are authoritative.
> No new device or vehicle observation is established by this archive.

## Earlier current-state snapshot

# Current implementation and evidence

Updated 2026-10-29. This owns implemented/installed facts and their evidence limits.
The [backlog](../backlog.md) owns task status; the [roadmap](../roadmap.md) owns outcome order.
App dev.57/code57 and signed dev.80 are installed on the Pixel/reference gauge.
The App used replacement installation; firmware used App/Wi-Fi. The
[current delivery evidence](dev80-action-entry.md) records exact signed
source/image, healthy OTA, indoor checks and preserved setup/history/uncertainty.
Actions enter at the first configured page. Offline status is one neutral Connecting
label, without a generic unknown verdict. The owner confirmed first-page entry
and consistent Connecting styling; [dev.79](dev79-action-ui.md) retains the
owner's prior clean display/navigation observation. Burnout stays paused. Actual
ABS/TCS restoration and swapped-adapter roles remain parked qualification. PR #127
is stacked on unmerged PR #126; no main merge occurred.
PRs #122/#123 and the [prior dev.75 delivery](dev75-indoor-rollout.md) retain
foreground/history/radio checks and the earlier owner navigation observation.
Live Jeep recovery and loaded presentation remain unqualified.
[Earlier display evidence](display-evolution.md) retains the stack/sensor
repairs and separate orientation qualification limits.
The opt-in [parked idle candidate](../architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate)
is installed and ready for supervised Air up/Winch checks using the
[one-visit runbook](../development/jeep-action-qualification.md#one-visit-idle-test-candidate).
The owner subsequently reported successful Air up operation, with an unexpected
“Coolant unavailable” note. The UI flag mismatch is corrected in installed dev.63; countdown duration
and actual coolant availability during that run are not yet confirmed.
On 2026-10-28 the owner reported Air up remaining at “Check idle / Status unknown”
after restarting the vehicle and gauge, with normal physical idle and live RPM.
The source retains a durable uncertainty marker; its parked adapter previously
blocked identity acquisition in Unknown and had no reboot recovery verifier.
The read-only [idle recovery source](../architecture/vehicle-actions.md#read-only-recovery-of-an-uncertain-idle-operation)
now prepares exact-ECM routine-status plus settled-RPM reconciliation, without
replaying controls or erasing evidence. Dev.72 installed this path, but the owner reports it still remained Unknown with
normal physical idle and live RPM. Offline replay now reproduces a missing
diagnostic-session prerequisite already recorded in the parked reference. The
follow-up source opens that captured session before recovery status, requires its
positive acknowledgement and retains all inactive/settled-RPM evidence gates.
This correction is retained in signed dev.74 after the dev.73 USB delivery;
actual ECM recovery remains parked qualification.
Website impact: corrective recovery behavior needs no new marketing claim or image.
Ordinary builds still disable vehicle controls; both qualification masks remain zero
and brake actions require the separate explicit owner candidate described below.

## Action presentation follow-up

The dev.80/App dev.57 source follow-up removes recovery-driven entry jumps: entry
starts at the first configured action, including after reorder. All offline action
faces share a neutral centered **Connecting** label. Generic “Not verified” copy is
omitted without clearing command uncertainty or enabling Start. The [signed indoor delivery](dev80-action-entry.md) passed; physical and
parked qualification remain separate.

Dev.79/App dev.56 source implements the owner-approved compact action review.
The [presentation contract](../architecture/vehicle-actions.md#approved-compact-action-presentation)
owns the single hold, quiet transitions, connection and recovery wording. The [signed indoor delivery](dev79-action-ui.md) passed; physical UI/navigation was confirmed by the owner. Jeep qualification
remains separate. No new controller
sequence, brake-state proof or restart-free restoration is introduced.

## Installed foreground, fault and idle-target follow-up

PR #122 merged at `e166fa0`; PR #123 merged at `b3f679f` after integrating it.
All four CI lanes passed on final source `62d1e4b`. One combined App/dev.75
installation and final indoor checks passed; [delivery evidence](dev75-indoor-rollout.md)
separates successful checks from intermittent recovery failures and remaining Jeep gates.

One [presentation owner](../development/reading-catalog-alerts.md#gauge-presentation-smoothing)
combines foreground warmup/preflight priority, bounded 150 ms analog easing and
independent RPM text stabilization, with original-age accounting and continuity
fences across source retirement, invalidation and missed stale gaps. Raw readings,
trend history, alert and control ages remain authoritative. A separate debounced
[Auto boot-angle checkpoint](../architecture/hardware-and-transports.md#automatic-display-orientation)
retains manual fallback independently. These changes are installed; loaded smoothing and cold-boot checkpoint behavior remain unqualified.

The App removes the misleading last-drive episode total, groups repeated warning
history without inventing codes, keeps last-known code detail reachable and uses
explicit code-unavailable wording. The [shared alert contract](../architecture/alerts-and-diagnostics.md#app-fault-and-history-presentation)
owns grouping and evidence semantics. [Offline verification](fault-reading-continuity.md)
separates original branch checks from combined integration checks and installation.

The App reuses nullable preset RPM for an [Air up target and Winch minimum floor](../architecture/vehicle-actions.md),
preserves it during timer edits and keeps numeric drafts phone-only. Local semantic
validation permits other valid edits; transfer admission alone rejects unsupported
numeric action intent rather than stripping it into a fixed target. No new wire,
firmware target mapping or live throttle behavior is enabled. The next
[parked session](../development/jeep-action-qualification.md#next-session-configurable-idle-and-pedal-investigation)
separates variable targets, ECU pedal behavior and the existing guard.
Website impact: defer new idle claims; review current Car/circular-gauge captures
for the normal daily window after qualification.

## Continuity update

The owner approved PERF-01 on 2026-10-27. Dev.65 source adds page-cache continuity,
cycle-pause recovery, prioritized/shared polling, display-only rate classes and
stable CEL presentation. It includes the approved action UI from published dev.64.
[Implementation and qualification](../development/gauge-continuity-plan.md) records
scope, offline checks and the at-home/parked sequence. Signed installation, five
Wi-Fi lifecycles, automatic/Bluetooth recovery and settings persistence passed.
On dev.65, the owner confirmed clean pages, action navigation, no CEL question mark
and ten-second cycling/resume after touch. Loaded ECM/TCM reading continuity and
control/thermal timing still require the Jeep.

Dev.66 introduced a fix for a concrete startup retry delay: an unanswered first
code-list scan now retries from two seconds with bounded exponential backoff,
instead of immediately waiting its steady 60/120-second interval. It also logs
changes in MIL/filter presentation for attribution. Warning and filter validation
remain strict. At that version MIL checks had a 60-second floor; gauge/shared-diagnostic
last-successful presentation bridges refresh/backoff gaps within the
[diagnostic retention limits](../architecture/alerts-and-diagnostics.md#mil-code-and-coverage-semantics).
Raw phone/history freshness remains unchanged. The owner's later in-vehicle startup CEL
observation is not yet attributed by a raw capture; [investigation evidence](action-pages-indoor.md#2026-10-27-startup-cel-investigation)
separates that report from the installed source fix.
The same image removes decorative play/stop glyphs from shared action progress
states at the owner's request; colored progress rings and action controls remain.

The subsequent owner report on Jeep connection still described a startup CEL.
The follow-up source change defers the face's MIL icon until code identity is usable
or coverage reconciled, keeping unresolved filter acquisition quiet without claiming
ECU Off. Routine MIL/stored/pending checks target five minutes, permanent checks ten;
initial category retry and explicit-clear verification remain responsive. Raw
freshness/packets are unchanged. This is installed in dev.72 and passed indoor checks; Jeep qualification remains.
[diagnostic semantics](../architecture/alerts-and-diagnostics.md#mil-code-and-coverage-semantics)
own the evidence and latency tradeoff. Website review: no new marketing claim or
visual asset is needed for this corrective behavior.

## Smart page cycling

FEATURE-11 is installed with the approved defaults: one opt-in Settings
switch, existing interval selection, alert-driven/thermal/context priorities,
minimum viewing/manual protection, bounded priority insertions and saved-page
fairness. The [runtime contract](../architecture/smart-page-cycling.md) owns behavior
and tuning, and [offline evidence](smart-page-cycling.md) records executed
checks. The additive `sp:1` protected settings path and revision-matched sidecars
preserve legacy display compatibility. Existing public capability flags remain off.
The combined source is installed; indoor results are recorded in the
[display rollout evidence](display-evolution.md). Matched loaded timing, comfort and resource qualification
remain in the [milestone procedure](../development/smart-page-cycling-plan.md).

## Display and adaptive sampling

Dev.66 / App dev.53 introduced ten saved pages, numeric Dual + arc,
shared explicit gear colors, an icon-only boot/no-data surface and reviewed
PID-specific stability backoff. [Display contract](../architecture/display-experience.md)
and [sampling contract](../development/reading-catalog-alerts.md#display-and-polling-classes)
own defaults and boundaries. The Auto orientation candidate is implemented behind
default-off probe/physical-qualification gates; the normal image does not offer it.
The [display evidence](display-evolution.md) separates offline checks,
indoor persistence and remaining physical/loaded qualification. App local profile
schema is 18; its migration changed only the schema version, retaining all other
profile fields. Gauge associations remain schema 2.

The extended ten-page round trip passed with exact original five-page restoration
at revision 63. The owner confirmed 10/10, wrap and clean labels without freezing.
Populated Dual + arc also passed visual review. Hold-exit/navigation was reported,
but a later active preview state makes explicit physical stopped-state proof pending.

Dev.70 introduced verified sensor control-register readback and uses normal-power
31.25 Hz sampling, resolving dev.69's pinned X symptom without calibration offsets
or relaxed gravity guards. The guarded 4 KiB internal sensor stack survived the
initial NACK/retry path; dev.70 observed worker/main margins of 1680/1080 bytes.
Earlier withdrawn stack-failure candidates and the saturated-axis diagnosis remain
in [dated evidence](display-evolution.md).

Protected four-angle reads, Auto/Wi-Fi suspension/recovery and same-setup restart
persistence passed on dev.70, retaining the current Smart On preference. The owner
reported incorrect text orientation. Two labelled poses establish a fixed LCD
quarter-turn offset; the historically installed dev.71 includes mapping version 2 and the larger muted-grey logo. The owner
confirmed the grey icon and tap-to-wake. Corrected all-angle/touch
and explicit hold-exit qualification remain pending. Normal qualification and public
flags stay off under the [indoor procedure](../development/display-indoor-qualification.md).

## Installed baseline

| Item | Last recorded state | Evidence |
| --- | --- | --- |
| Pixel | App dev.53/versionCode 53, consolidated Android source `62d1e4b` (PRs #122/#123); profile schema 19 / associations 2 | [Dev.75 delivery](dev75-indoor-rollout.md): installed APK hash matches build, profiles/settings/history retained, original phone policy restored |
| Gauge | Signed parked-idle/orientation candidate dev.75, generation 56; healthy state 2 at `0x360000`, configuration revision 65 | [Dev.75 delivery](dev75-indoor-rollout.md): full App/Wi-Fi installation, radio recovery, setup and owner display/navigation checks passed; Jeep recovery pending |
| Physical display/touch | Owner confirmed dev.75 clean display and navigation; requested checks covered touch wake, normal reading swipes and action entry/return. | [Dev.75 delivery](dev75-indoor-rollout.md); [earlier display rollout](display-evolution.md); all ten pages subsequently owner-confirmed; new-arc fit owner-confirmed; hold-exit was reported but later active state requires explicit stopped-state proof; loaded legibility and Auto sensor/touch alignment remain separate gates |
| Hardware | Waveshare ESP32-S3-Touch-LCD-1.28, 240 × 240 GC9A01, CST816S, 16 MB flash, 2 MB PSRAM | Qualified toolchain baseline ESP-IDF 5.4.1 |
| Vehicle | Modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / PCS TCM-2800; two same-model adapters | [Dev.53 live simultaneous sources and independent recovery](gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification) |
| Saved setup | One whole-vehicle dashboard, distinct ECM/child-TCM bindings, dual mode On | Five pages, four TCM CEL filters, thresholds and action settings retained; OTA preserved revision 60 and exact document bytes; the targeted same-setup test then advanced to 61 with only baseRevision changing. [Current preservation check](action-pages-indoor.md#2026-10-27-combined-dev66-rollout) |

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
| Car workspace | Installed from consolidated PR #119 source `c8ab21a`: one attention list, direct fault/alert detail, Current/History, focused settings and shared hidden-code projection | [Pixel installation and indoor checks](car-ux-review.md#2026-10-28-pixel-installation-and-indoor-checks); existing phone setup and raw history preserved | Live vehicle alert/fault UX qualification |
| Shared alert/history framework | Threshold/MIL/synthetic producers share lifecycle, attention, bounded gauge context/journal, phone history/export and opt-in monitoring | Dev.48 Pixel/gauge synthetic Entry/Expired import and shared context/banner; 162 JVM tests, eight earlier emulator fixtures | Real vehicle alerts/context, physical attention, background/OEM and storage/power-loss matrix |
| Pages/catalog/alerts | Six renderers including numeric Dual + arc; negotiated ten-page support; one catalog with 53 Mode 01 and two existing JSS readings; every selectable reading can alert | Production fixtures and [combined rollout](action-pages-indoor.md#2026-10-27-combined-dev66-rollout) retain the owner's five-page dashboard | Loaded layout/alert timing, units, dwell/stale behavior and legibility remain separate |
| Configuration | Coupled reviewed payload, dual storage generations, running revision/hash, trial/fallback | Prior protected sends and restart/recovery readback | Power/interruption and all-layout hardware matrix |
| Firmware updates | Signed catalog/bundle, owner bootstrap, private Wi-Fi, A/B boot health and durable journal | [Dev.66 full upload and five maintenance cycles](action-pages-indoor.md#2026-10-27-combined-dev66-rollout); exact healthy alternate image, empty journal, retained setup and automatic/Bluetooth recovery. Dev.60 interruption checkpoints remain separate evidence | Trial-boot rollback, rail-power loss and loaded live-adapter qualification remain |
| Phone persistence | Installed schema 19 retains disabled alert drafts; schema 2 assignments; serialized IO, cancellation-safe writes and typed pairing failures | PR #115 App dev.53 migration changed schema 18 to 19 and added one enabled alert default; profile values/order, assignments, preferences and raw history preserved | Process/storage failure and broader OEM cases; later child removal cause remains unconfirmed but cleanup was accepted |
| Multiple adapters | One logical vehicle, optional Expert child, independent firmware workers and source freshness; direct child picker and scoped selection fix installed on Pixel | [Setup review](second-adapter-setup.md); [dev.53 live reads/resume, independent loss/recovery and phone off/on](gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification); owner confirmed live ECM/TCM display and independent expiry/return | Startup absence, loaded maintenance, rates and sustained soak remain open |
| Health measurements | Fixed worker records, queue drops, internal/DMA/PSRAM lifecycle logs, allocation failure hook and sampled stack minima | [Dev.66 indoor measurements](action-pages-indoor.md#2026-10-27-combined-dev66-rollout); zero observed allocation failures/queue drops across upload and maintenance checks | Longer loaded soak, radio coexistence, complete stack/progress coverage and unobserved vendor-private/transient failures |

## Approved Car implementation, not yet installed

The approved Car source replaces overlapping Vehicle health/recent Alerts cards with
one attention list, direct issue detail, Current/History and focused Alert settings.
Saved hidden-code intent projects current/history/context/Expert/notifications and
ordinary reports through one owner; immutable raw evidence remains internal. Isolated
connected edits reuse the existing transaction; unrelated pending setup keeps review.
Phone schema 19 retains disabled alert drafts while excluding them from gauge payloads.
Debug/release JVM checks, build/lint and 73 managed-emulator checks passed;
[verification evidence](car-ux-review.md#2026-10-28-approved-car-and-hidden-code-implementation)
records scope and the refreshed production Car capture. PR #115 was merged and its
App installed on the Pixel; [indoor checks](car-ux-review.md#2026-10-28-pixel-installation-and-indoor-checks)
passed with saved setup/history retained. The Car update kept firmware/wire unchanged; the subsequent firmware delivery is recorded above.
The [shared contract](../architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters)
and [design rules](../design/design-system.md#current-component-and-messaging-rules) own behavior.

## Installed Car fault experience

Merged PR #63 implements inline decoded code summaries under Car > Vehicle
health, one whole-vehicle list and focused explanations. Saved alert context and
notifications share a 55-code offline lookup with named manufacturer references; unknown/controller-specific codes
remain explicit. Technical category coverage stays in Expert. The matching APK is
installed on the Pixel: Gauge ready, all four saved TCM explanations and historical
context were observed. Live vehicle filtering and real notification appearance
remain unqualified. Lookup extension rules and reference provenance are in the
[shared alert contract](../architecture/alerts-and-diagnostics.md#offline-code-explanations).

## Raised-idle coolant guard

The shared command path uses the running primary-ECM high Coolant alert as its
stop limit. Missing protection blocks Start; a known high temperature latches
Return. Unavailable coolant now permits a locally confirmed, at-most-60-second
session; data loss during idle shortens the session to the earlier of its original
expiry or 60 seconds after first loss. Recovery never extends it, and another
fallback session requires a fresh physical-gauge Start. See the
[authoritative coolant policy](../architecture/vehicle-actions.md#coolant-protection-for-raised-idle).
The App displays the timer and retains Stop. The 24-byte request and 32-byte status
sizes are unchanged; status adds idle-only reason/flags. The matching App and firmware are installed; parked fallback/thermal-stop
qualification is still outstanding.
Public vehicle-command qualification/capability stays disabled.

## Prepared four-tire Air up estimate

The App supports a per-vehicle pressure/time calibration, starting/target PSI and
an optional duration override for all four tires together. The resolved timer uses
existing command/configuration fields; there is no pressure telemetry or firmware
buffer/poller. [The timer policy](../architecture/vehicle-actions.md#four-tire-air-up-timer)
owns the calculation and limits. The matching App and firmware are installed; actual compressor/tire calibration
and enabled eGauge control are not established.

## Compatibility and public boundaries

- Source phone profiles: schema 19 reads schemas 1..19; the installed App uses schema 19. Gauge associations use schema 2. Older Apps reject new saved state;
  downgrades require an explicit compatible migration. Legacy association files remain
  read-only migration inputs. See [Android storage](../architecture/android-runtime.md#storage-and-recovery).
- Gauge configuration remains schema 2. Installed development `cfg:7`, `va:1`, `da:1`, `ad:1`
  and display settings `ds:3` negotiate implemented slices. Wire layouts belong in
  [protocol documents](../README.md#protocols), not this status snapshot.
- Public `configWrite:false`, `ota:false` and qualified link capacity one remain.
  Three NimBLE slots and independent workers do not establish physical coexistence.
- Phone gauge values remain labelled previews; live companion telemetry, code clearing
  are not enabled. Brake controls are available only in the explicit owner candidate
  described below. High idle is limited to the installed owner-supervised parked
  candidate. Local page-jump gestures are implemented.
- TCM temperature interprets only the first byte as A−40 °C. Current/target candidates
  compare P=0D, R=0B, N=00 and stationary Drive=01. Pressure `225034` remains raw only.
  Drive is not a distinct selector value. The diagnostic endpoint/PCS bridge topology
  remains unresolved. Definitions are calibration-specific; standard catalog membership
  is not vehicle support.

Auto orientation is specified in the [hardware policy](../architecture/hardware-and-transports.md#automatic-display-orientation)
and tracked as FEATURE-07. The installed candidate has a live, bounded IMU path but remains unqualified
because of the pinned X reading documented above. Manual rotation and timed page
cycling remain independent implemented controls.

FEATURE-04 now has a [temporary-idle delivery proposal](../architecture/vehicle-actions.md#first-vehicle-command-temporary-high-idle)
for Air up and Winch presets using the existing gesture/profile foundation.
The owner [reports successful high idle through JScan on this Hemi swap](../vehicles/jeep/jk-handler-analysis.md#owner-reference-confirmation-2026-10-19).
Prepared source now wires App controls and the gauge's confirmed actions menu
through one typed protected command interface (`vc:1` in document mode), one primary
worker controller, bounded controller drivers, uncertainty journal and event ring.
Idle uses the captured fixed setting; numeric target drafts never become commands.
ABS/TCS Return remains Restart required. Both qualification masks are zero: no
generally qualified profile can execute controls. The shared path is installed in dev.62;
the fully automatic Park/brake evidence adapter remains unimplemented. Installed
dev.62 adds only the explicitly built, owner-supervised idle exception linked above.
The [shared owner](../architecture/vehicle-actions.md#shared-command-owner) records
memory, cancellation, persistence, admission and remaining evidence requirements.
The [offline foundation](../architecture/vehicle-actions.md#implemented-offline-foundation)
records the execution boundary. The [parked follow-up](jeep-actions-live-qualification.md)
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
  controlled resets recovered ownership. See [pairing diagnosis](gatt-long-read-recovery.md#jeep-fresh-pairing-regression-diagnosis).
- [Indoor memory/recovery evidence](esp32-memory-hardening.md) records stable
  repeated Wi-Fi lifecycles and signed upload margins. One owner preflight failed and
  fresh protected reads recovered without bond repair. This does not establish immunity
  to OOM, deadlock, power loss or loaded three-link failure.
- Engine clearing/readback is implemented behind a default-off gate. TCM clearing,
  freeze frames and live clearing semantics remain unqualified. No live provider is
  enabled; the synthetic provider seam remains, while its former Expert UI was removed.
- Exact controller-scoped CEL filtering preserves raw evidence/history. Live filter
  qualification remains pending; [shared policy](../architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters)
  owns reconciliation semantics.

## Resume

Select a [backlog](../backlog.md) gate. Use [TCM resume](../development/tcm-session-resume.md)
for parked vehicle work and App/Wi-Fi for field updates. Keep private captures and
working setup. Android and firmware are implemented; basic iOS and browser-USB
configuration are [planned scope](../roadmap.md#product-boundaries), not available
runtime features.

## Shared platform interface foundation

The [reference board/display seam](../architecture/platforms-and-releases.md#implemented-reference-interfaces)
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
[dated maintenance evidence](repository-maintenance.md#2026-10-20-android-variant-gates).
This is offline test coverage, not a new installed APK or physical observation.

## Approved action pages

Owner approved the review 4 simplification on 2026-10-27. Merged source now uses
900 ms hold-to-start, one-tap Stop from either idle page, pending-state rings and
verified direct return to Ready, with explicit high-coolant blocking. Android uses
compact action rows and a focused editor. The [implementation evidence](action-pages-indoor.md#2026-10-27-approved-review-4-implementation)
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
[Runtime contract](../architecture/vehicle-actions.md#ordered-action-pages) and
[backlog FEATURE-10](../backlog.md) own behavior and remaining acceptance.

The release gate exposed modal-editor fixture races in two CI attempts (saved-alert
callback missing, then duration-editor scroll target absent). The test-only keyboard
and destination synchronization from the pending UI-stability work is included,
with the same boundary applied before Alert save. Real taps and exact value/disabled
control assertions remain; no production App behavior or dependency changes.

## Assessment remediation prepared in source

CODE-03 source fixes strengthen trial UI liveness, browser retry selection, intake
backup/readiness/parsing/proxy trust/consent, release evidence and APK identity checks,
catalog diagnostics and continuous accessibility coverage. The [maintenance record](assessment-remediation.md)
owns software verification and remaining deployment/physical limits. Installed App,
gauge and host services are unchanged by this work; existing QUAL gates remain open.

## Preserved PR work reconciled in source, 2026-10-28

The unique work from PRs #41/#59/#74/#77 is reconciled against main `690439b`.
The signed update bootstrap shares one owner session and retains bounded uncertain
startup cleanup. Gesture motion arbitration and pointer regressions retain the
current cache/attention/action-arrow policies; waiting-logo redraw tests retain the
current neutral-grey design. Configured alert rules, explicit Add alert, lazy full
history and eligible Current actions preserve newer Car navigation, filters and
schema 19 disabled drafts. Their topic contracts own behavior and qualification.

The source-consolidation task did not itself deploy; subsequent device delivery is
recorded above. It did not publish the website/backend, promote capability flags or
close remaining vehicle gates.

## Remaining PR source preservation

The [remaining consolidation](final-pr-consolidation.md) preserves the
inactive companion proposal/probe and shared protected owner factory, bounded trend
rendering/fallback and opt-in Jeep coverage/capture research. Normal owner operations,
current display/Car/gesture behavior and capability gates stay authoritative. Trend
fixtures and fake owner/probe sessions do not prove physical rendering/radio/provider
behavior. The dev.71 installation record above is historical evidence retained from
PR #112; this cleanup did not interact with the phone, gauge or vehicle.

## Website and public download refresh

The reviewed website source now provides a direct mobile install path, distinct
Android/desktop recovery entry points, source-traced gallery/status data, current
App/circular-gauge images and precise development boundaries. The hosted App inventory
references dev.53/code 53 from unchanged Android production source at `083b001`,
with the existing signer retained and an isolated dev.51-to-dev.53 preservation check.
The reviewed USB fallback stays dev.60; the dynamic firmware selector continues to
offer signed published candidates, including dev.75. No new firmware runtime, control
qualification or stable channel is implied. [Review evidence](../../review/REPORT.md)
records baseline checks; final publication and comparison are recorded separately in
[delivery results](../../review/IMPLEMENTATION.md). Normal website cadence still applies.


## Installed owner-supervised brake candidate

App dev.54/code54 adds an explicit fourth **Burnout Mode** action using negotiated
`vc:1` + optional `bm:1`; legacy menus/gauges retain their previous behavior. Firmware integrates
its captured distinct ABS route through the shared worker, physical hold-to-run,
bounded release request, durable uncertainty and independent release-proof gate.
Combined **Disable ABS + TCS** retains ignition-cycle restoration. The new default-off
`CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST` enables both actions on the captured
Jeep ECM match, with physical Start, fresh stationary normal-idle admission and
manual brake/reset confirmation. Authenticated `bt:1` identifies that policy.
Burnout allows motion/throttle after Start with fresh data and retains its
ten-second release-initiation limit. Human confirmation is recorded separately
from automatic controller proof. Both ordinary qualification masks remain zero;
public capabilities remain disabled. Signed dev.76 and App dev.54 are now installed;
[delivery evidence](dev76-indoor-rollout.md) separates indoor verification
from actual vehicle behavior. The four-page menu is healthy at revision 66 with
existing readings, bindings, timers, filters and settings preserved. [Action contract](../architecture/vehicle-actions.md#burnout-mode-and-brake-actions)
and [parked follow-up](../development/jeep-action-qualification.md#brake-actions-follow-up)
define the remaining physical evidence. Website impact: retain qualification
language; defer working-brake marketing claims until the parked checks.


### Front Lock follow-up

The owner specified Front Lock for Burnout. [Offline selector analysis](../architecture/vehicle-actions.md#front-lock-selection-captured-jk-versus-jljt)
found that the captured JScan JK handler uses fixed rear-outlet control and does
not read the setting; selectable Lock Front/Unlock Rear belongs to a separate
JL/JT ABSPN path in that build. A fresh full capture merely to change the selector
is therefore unnecessary for that JK path. The owner is comparing physical behavior;
actual front-hold/release semantics remain unqualified. Dev.76's enabled owner
function and request bytes have not changed. A different observed behavior/path
needs a short labelled capture before any command change.


### Action recovery presentation follow-up

The owner reported a persistent Burnout **Action in progress / Hold brake pedal**
face after gauge and Jeep restarts. Protected dev.76 readback at revision 66
identified ABS/TCS operation 2 as Unknown/Context with restart-required retained;
Burnout and Idle both had operation 0. No qualified ECM context was present during
the readback. The owner reported normal Jeep/brake behavior after restarting.
This is a retained ABS reset-confirmation record, not evidence of active Burnout.

The UI-only dev.77 source now identifies the actual blocker and adds Review
navigation, removes the misleading ready instruction, and distinguishes missing
ECM from unmet stationary confirmation input. It also scopes coolant flag handling
to idle actions: the local brake-readiness bit previously caused a hidden second
Burnout hold and a false coolant note. Actual runtime-flag regression now requires
one hold and preserves **Release to stop** while active. Existing command gates, bytes,
records and independent physical confirmation are unchanged. Real-LVGL normal and
fallback regressions reproduce the cross-action blocker and preserve independent
recovery. The [recovery contract](../architecture/vehicle-actions.md#reviewing-a-different-actions-retained-state)
and [parked procedure](../development/jeep-action-qualification.md#brake-actions-follow-up)
own the behavior. Signed dev.77 is installed through App/Wi-Fi;
[delivery evidence](dev77-action-recovery.md) separates the retained
ABS record and offline verification from remaining parked Jeep checks.
Website impact: corrective UI copy/navigation only; defer new qualification claims.


## Unreleased field-recovery follow-up

The owner reports ABS/TCS stayed disabled across ignition restarts until JScan
was toggled and the Jeep restarted. The source exposed only reset confirmation,
omitting the explicit Return used in the successful reference. The follow-up adds
**Restore** before ignition reset, explicit same-vehicle Unknown recovery and a
durable acknowledged-Return receipt, keeping acceptance distinct from physical
restoration. See the [recovery contract](../architecture/vehicle-actions.md#abs-return-receipt-and-paused-burnout-menus).
These changes are installed in signed dev.78; actual Jeep restoration is not yet qualified.

The owner also requested removing Burnout/Line Lock for now. Normal App action
menus and firmware configuration omit it; older pending records retain release
recovery. App fixtures pass offline, while a physical menu/configuration check is
still pending. Burnout actuation and front-lock qualification are deferred.

A field report that unplugging TCM allowed a blocked ECM to connect prompted
shared BLE discovery/admission and callback-lifetime hardening. The observation
is consistent with contention, not proof of its cause. Offline starvation and
late-callback regressions must be separated from loaded dual-adapter qualification.
Website impact: remove any available-Burnout claim in the next eligible batch;
make no new restoration or loaded-recovery claim before physical qualification.


The owner approved automatic swapped-port recovery. The unreleased role verifier
uses the captured ECM/TCM calibration+CVN pairs through bounded fixed read-only
requests. An opposite-only positive pair retires both sources before exchanging
runtime transport bindings, preserving raw configuration and logical source roles.
Ambiguous/missing evidence retains the working map; same-session ECM proof survives
mere refresh loss. The [role contract](../architecture/vehicle-connections.md#role-verification-for-accidentally-swapped-adapters)
owns admission, budgets and limits. Policy/transport regressions pass offline;
physical swapped-port recovery remains unqualified; signed dev.78 installs this path.


Firmware dev.78 / App dev.55 are installed with the evidence linked above. The combined
owner-candidate firmware builds locally; 185 host and 14 review-server tests,
341 JVM tests, 28 managed-emulator action UI checks, firmware normal/fallback/parked
rendering and configuration fixtures pass. Android lint and format checks pass.
The production transport bodies run under ASan/UBSan fixtures, and the role policy
covers opposite/both/missing identities, stale epochs, two-worker retirement,
inverse-map continuity and transient identity loss. Published/installed evidence is recorded separately above; these offline results
do not supply loaded Jeep or physical controller qualification.


## Earlier backlog snapshot

# Prioritized backlog

Updated 2026-10-29. This is the only current task/status list. Outcome order belongs
in [roadmap](../roadmap.md); supported/installed behavior belongs in [current state](../current-state.md).
Evidence files retain historical pending notes and do not create additional tasks.

## How to choose work

Choose the highest-priority ready item matching the owner's request. Preserve its
acceptance boundary and dependencies. Work offline while hardware is absent. Do not
start a vehicle session, enable a control, publish a release or merge main merely
because a task appears here. The owner's existing authorization governs each action.

Statuses: **Ready** means useful work can begin; **Needs device** means physical
qualification is required; **Blocked** names an unavailable dependency; **Deferred**
means out of current delivery scope. Priority is delivery order, not a date promise.
**Awaiting approval** means a concrete proposal is ready for owner review.
No task is In progress until a session actually starts it. Completed implementations
are in current state and evidence, with remaining qualification listed separately.

## Active tasks

| ID | Priority | Status | Task | Dependency / acceptance |
| --- | --- | --- | --- | --- |
| CODE-06 | P1 | Ready | Preserve per-action observation freshness across App/gauge state | [Review](product-coherence-review.md): update one action without refreshing siblings; fake-clock tests across stale age, context change and partial replies; shared scenario expectations through App presentation and firmware admission. |
| CODE-07 | P1 | Ready | Close persistence and command-admission recovery test gaps | [Review](product-coherence-review.md): staged/committed NVS fake, independent commit failures and reboot boundaries; queue rejection health counters and saturation/drain regression. Use QUAL-08 for measured end-to-end loaded budgets; no physical pass inferred from doubles. |
| CODE-08 | P1 | Ready | Reduce current-context drift | [Delivery framework](../documentation.md#product-quality-and-public-story): compact current-state table, one current next-outcome backlog row per task, exact historical evidence preserved with incoming anchors; reconcile paused Burnout and stacked source/installed status. Add a fast consistency gate after the migration, without a second task registry. |
| CODE-09 | P2 | Ready | Make public claims and visual refresh source-aware | Extend existing website content/provenance and builder, not a new CMS: audience/availability/evidence per claim; relevant input hashes and changed-screen review; one shared App/gauge asset set for GitHub/site; remove repeated manual availability strings. Apply [editorial direction](../../website/README.md#brand-and-editorial-direction) in the next eligible daily batch, with mobile/desktop review. |
| PERF-01 | P1 | Installed; parked qualification pending | Reading continuity, polling priorities and cycling recovery | Dev.66 and App dev.53 passed the [combined rollout](action-pages-indoor.md#2026-10-27-combined-dev66-rollout). Foreground warmup preference and bounded display-only analog/RPM smoothing are installed in dev.75; [delivery evidence](dev75-indoor-rollout.md) records final indoor checks and retained intermittent failures. Loaded qualification remains pending. Combined source adds original-observation age accounting and between-frame source/dropout continuity fences to the single presentation owner. Scoped cache/deadline scheduling, adaptive hidden-PID backoff, five-minute MIL polling (installed dev.72; [indoor evidence](dev72-indoor-rollout.md)), quiet unresolved startup, bounded filter/shared-alert continuity and diagnostic attribution logs are implemented. Upload, five maintenance cycles, automatic/resume/Bluetooth/picker recovery and setup preservation passed indoors. [Policy and parked acceptance](../development/gauge-continuity-plan.md) remain authoritative; capture loaded ECM/TCM freshness, startup MIL/filter evidence and control/thermal timing. |
| UX-01 | P1 | Installed; parked checks pending | Approved Car simplification and complete hidden-code presentation | The consolidated App installed with dev.75 removes false Last drive totals, groups repeated controller/code history and clarifies missing-code evidence ([delivery](dev75-indoor-rollout.md)). One attention list, direct detail, Current/History, focused alert settings and Advanced rules. Phone schema 19 retains disabled drafts, while firmware wire schema stays 2. [Design record](../design/car-ui-proposal.md); 73 managed checks passed; [Pixel usability, migration and connection recovery passed](car-ux-review.md#2026-10-28-pixel-installation-and-indoor-checks). Next: parked filtering and per-gauge acknowledgement checks. Website visual refreshed from the production fixture for the next normal daily publication. |
| QUALITY-01 | P2 | Ready | Stabilize intermittent emulator UI interactions | Merged PR #101 quality attempts failed in two different UI fixtures: preset-save callback observation, then duration-editor scrolling. The reviewed source had all four checks green and the same tree as main. Reproduce and fix synchronization/interaction causes with repeated isolated evidence; do not retry blindly, weaken assertions or run fixture suites on the owner phone. [Rollout evidence](action-pages-indoor.md#2026-10-27-dev65-continuity-and-action-ui-rollout) |
| FEATURE-04 | P1 | Owner candidate installed; full qualification pending | Vehicle shortcuts: idle and combined ABS/TCS; Burnout paused | [Shared owner](../architecture/vehicle-actions.md#shared-command-owner) wires confirmed gauge menu/App intents, protected typed wire, primary-worker arbitration, captured fixed-idle and ABS/TCS drivers, bounded uncertainty storage and independent action states. Offline replay/UI/migration checks cover the path; no current profile supplies live identity/guard/reset proofs and both qualification masks remain zero. [Parked reference](jeep-actions-live-qualification.md) establishes fixed-target JScan behavior and ABS/TCS ignition-cycle warnings. The [parked runbook](../development/jeep-action-qualification.md) and fixed read-only input capture are prepared. An owner-approved [parked idle candidate](../architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate) now supports local-only supervised admission with exact identity, zero speed/RPM, five-minute fresh-coolant Air up and the one-minute missing-coolant fallback; ordinary builds keep it off. Build/CI and signed dev.62 App/Wi-Fi installation passed; [indoor readiness](action-pages-indoor.md#2026-10-27-signed-dev62-parked-idle-candidate) retained revision 55, presets/protection/bindings/filters and confirmed no unresolved command state. Read-only recovery for a durable Unknown after reboot is prepared in source; exact-ECM inactive-routine and settled-RPM checks require parked qualification. The owner reported dev.72 still stuck; recovery session setup was missing. The session prerequisite/rejection replay is retained in signed dev.74 through App/Wi-Fi; [delivery evidence](dev74-indoor-rollout.md) records healthy readback, radio recovery and owner navigation checks. Live Jeep recovery remains pending. Owner reported successful Air up operation on dev.62, with a coolant-unavailable note; the UI flag mismatch is corrected in source. Confirm actual coolant availability/countdown and complete Stop, thermal/fallback and recovery checks. Next: capture fresh primary Park/Neutral/engine/movement inputs and restoration proof, qualify immediate post-Return readback, cadence/stack and child continuity on the actual eGauge path. Air up target/Winch minimum RPM editors and transfer fences are installed in the consolidated App using existing nullable presets; numeric drafts remain phone-only. Arbitrary RPM mapping and throttle-above-floor behavior remain unqualified; the [next-session plan](../development/jeep-action-qualification.md#next-session-configurable-idle-and-pedal-investigation) separates target encoding, ECM cancellation and the active-RPM guard. Burnout Mode now has a fourth curated action, distinct captured driver procedure, local hold-to-run, bounded release requests and durable uncertainty; the independent default-off [owner brake candidate](../architecture/vehicle-actions.md#explicit-owner-supervised-brake-candidate) now enables both brake actions only on the captured ECM match with physical Start and recorded manual confirmation. [Signed dev.76/App dev.54 delivery](dev76-indoor-rollout.md) now installs that candidate and its four-page menu with retained setup. Full controller/release qualification remains open; ordinary qualification masks stay zero. Coolant guard now reuses the running high-temperature alert, refuses unprotected Start, latches Return on heat, and caps missing/stale coolant sessions to one minute with physical-gauge restart; App setup/send warnings are implemented offline. Four-tire pressure/calibration estimates and time overrides are prepared in the App, resolving to the existing bounded timer. Parked thermal-stop latency/stack checks and actual four-tire calibration remain before enablement. |
| FEATURE-10 | P1 | Qualification | Gauge action-page branch and App management | Owner-approved [review 4](../design/gauge-ui-proposal/action-pages/index.html) is installed in App dev.53 / signed dev.66. Deliberate idle hold, direct Stop, compact App setup and progress arcs remain; decorative play/stop glyphs were removed on request. [Current rollout](action-pages-indoor.md#2026-10-27-combined-dev66-rollout) retains pages/actions and protected readback; historical design/owner checks are linked from that evidence. Next: current physical touch/cycle confirmation, then parked Start/Stop, coolant/fallback and source-continuity qualification. Ordinary control masks remain off; captured-Jeep parked idle is a separate local-only option. |
| FEATURE-09 | P1 | App visibility implemented; device qualification pending | Advanced exact CEL code filters | Up to 16 controller-scoped rules; raw evidence retained internally, complete count reconciliation and bounded gauge/shared-alert continuity. Approved Car source now hides codes across current/history/context/Expert/notifications/reports using saved vehicle intent; gauge truth remains acknowledged. Emulator/source evidence and live filtering remain separate. Dev.66 App/Wi-Fi install retained all four owner TCM rules. Native fixtures cover stale/failed refresh, same-count unfiltered replacement and true invalidation; live filtered/unfiltered/partial ECM+TCM capture remains. [Contract](../architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters) |
| FEATURE-08 | P1 | Implemented subset qualified | Gauge instrument UI and custom-PID display contract | [Revision 4](../design/gauge-ui-proposal.md) applies four Round 3 centering requests and retains 36 unchanged approvals. Prepared dev.55 compositor and [temporary showcase](../development/display-showcase.md) have production LVGL fixtures; signed dev.55 installed through App/Wi-Fi with setup preserved; owner approved demo appearance/cycling, hold exit and normal pages/swipes on 2026-10-16. App-started showcase footer removal is installed in signed dev.59 through App/Wi-Fi; native frames passed and owner confirmed no Demo footer, hold exit and normal pages/swipes. Real vehicle alert presentation and future inspection/provider/maintenance/custom metadata remain separate scope |
| QUAL-13 | P1 | Needs device | Gesture and browsing qualification | [Retained acceptance matrix](gauge-browsing-reliability.md#device-acceptance-inputs-for-qual-13) covers vibration/loaded-source timing, taps/holds/diagonal/cooldown arbitration, pairing/reset safety and cycling. Native pointer/cache/redraw regressions are software evidence only; no new physical pass is claimed. |
| QUAL-12 | P1 | Qualification | Fresh pairing and owner retention recovery | Dev.53 neutral global policy/compile guard installed via approved signed USB repair; fresh App pairing, protected reads and owner retention after one physical power cycle passed; dev.59 controlled serial reset plus same-App foreground resume/protected owner/setup readback passed; original key-loss cause, longer retention and bond/rail-power-loss matrix remain unresolved |
| QUAL-02 | P0 | Qualification | Configuration/OTA interruption and uncertain outcome | Signed dev.60 matrix passed serial reset checkpoints at 10/50/90% and verified-before-activation, plus App process loss during transfer, automatic previous-image reconciliation and final healthy installation. Remaining: configuration stage/commit interruption, explicit expiry, trial-boot rollback and physical rail-power loss. [Recovery matrix](../development/ota-recovery-matrix.md); exact post-boot identity before any retry |
| QUAL-03 | P1 | Needs device | One adapter moved between ECM/TCM ports | Whole vehicle pages stay editable; unavailable values clear; correct attributed values recover; [logical vehicle evidence](logical-vehicle-review.md) |
| QUAL-05 | P1 | Needs device | ECU-scoped CEL/MIL and full vehicle faults | [ADR-015 Phase 1](../architecture/alerts-and-diagnostics.md#implementation-sequence-and-file-ownership): Dev.48 endpoint routing, fair polling and global presentation implemented with production fixtures; sequential ECM/TCM long-read/coverage qualification remains |
| FEATURE-05 | P1 | Qualification | Shared alert, notification and diagnostic-history framework | [ADR-015 Phases 2..4](../architecture/alerts-and-diagnostics.md): Dev.48 threshold/MIL/synthetic adapters, shared attention/UI, bounded context/journal, transactional history, export and opt-in monitoring implemented; QUAL-11 owns physical/OEM/storage qualification |
| FEATURE-03 | P1 | Qualification | Explicit diagnostic code clearing | [ADR-015 Phase 5](../architecture/alerts-and-diagnostics.md#clearing-codes-and-preserving-evidence): Dev.48 confirmed token/journal/addressed Engine clear and readback implemented behind default-off gate; offline policy tests passed, live Engine/JSS semantics require qualification |
| QUAL-11 | P1 | Needs device | Shared alerts, context and notification qualification | [ADR-015 matrix](../architecture/alerts-and-diagnostics.md#focused-acceptance-matrix): Dev.48 protected synthetic entry/expiry and App context passed; remaining autonomous gauge attention, hidden pages, Pixel lifecycle, power/storage loss, clear before/after; actual vehicle clearing only explicit parked owner action |
| QUAL-06 | P1 | Blocked | Expand/validate JSS TCM meanings | Independent controller/calibration reference needed for sensor scale, gears 2..8, current/target divergence, shaft speeds/slip; [bounded research plan](../development/tcm-values-research-plan.md); pressure remains raw |
| QUAL-07 | P1 | Needs device | New readings/alerts, units and circular legibility | Decimal and gear rendering, hidden-page TCM alerts, dwell/stale/recovery, unit labels, arc clearance, vivid colors, daylight/night; no new support inferred from vectors |
| QUAL-04 | P1 | Qualification | Two physical adapters plus phone | Dev.53 revision 50 live simultaneous reads/resume, ECM/TCM loss/recovery and phone Bluetooth off/on passed; sibling sessions stayed unchanged and only the interrupted session advanced; owner confirmed both physical sources and independent expiry/return. Startup with missing source, loaded maintenance/interruption, rates and two-hour soak remain open; [dual matrix](../development/dual-adapter-recovery.md) |
| QUAL-08 | P1 | Qualification | Controlled recovery/performance soak | [Dev.66 indoor rollout](action-pages-indoor.md#2026-10-27-combined-dev66-rollout) adds a signed full Wi-Fi upload, five authenticated lifecycles, automatic/resume/Bluetooth recovery and retained setup with measured memory/stack margins. Earlier serial-reset/App-loss interruption evidence remains in the [memory report](esp32-memory-hardening.md). Remaining: single/dual loaded comparison, rates, power/thermal, 100 reconnect cycles, sustained soak and unresolved historical preflight/GATT-reset causes; bounded indoor checks do not establish OOM immunity. |
| FEATURE-07 | P1 | Candidate installed; sensor/physical qualification pending | Capability-gated Auto display orientation | Follow-up source checkpoints the stable applied Auto angle for startup, separately from manual fallback; cold-boot physical verification remains. [Orientation policy](../architecture/hardware-and-transports.md#automatic-display-orientation): bounded IMU identity/health and axis mapping, persisted mode distinct from applied angle, negotiated settings and coordinated touch transform; candidate sensor worker, health, App/state protocol and persistence implemented with normal probe/qualification defaults off. The published dev.67 trial overflowed the main startup stack and rolled back to healthy dev.66; dev.67 is withdrawn. Dev.68 cleared startup with 1104 bytes of main headroom but its 2 KiB sensor worker overflowed and rolled back; it is withdrawn. Installed dev.69 adds a guarded 4 KiB internal sensor stack; NACK/recovery and boot remain stable, but a near-2 g X-axis reading blocks plausible orientation even after full power removal. Installed dev.70 verifies register/direct-X evidence and normal-mode sampling, restoring plausible X. Four protected angles, Auto/Wi-Fi recovery and restart persistence passed; owner text orientation failed. Installed dev.71 includes the measured reference-board quarter-turn correction as mapping v2 and the larger grey waiting icon. Owner confirmed the icon/wake; physical all-angle/touch qualification remains pending. The [indoor procedure](../development/display-indoor-qualification.md) tracks the remaining live sensor/angle/touch/recovery checks; normal board qualification remains off |
| FEATURE-12 | P2 | Installed; extended layout/loaded qualification pending | Ten pages, Dual + arc and icon-only waiting | [Display contract](../architecture/display-experience.md) and [evidence](display-evolution.md). App migration/capability preflight, native bounded-memory screens and normal/parked firmware builds. Signed dev.66/App dev.53 installation, capabilities and final protected setup readback passed. Owner confirmed the clean text-free logo, wake and reading/action navigation on 2026-10-27; The additional ten-page/wrap/label/no-freeze observation and exact original-setup restoration passed on dev.66 (revision 63). Populated new-arc fit and hold-exit/navigation passed owner review on dev.69. Loaded memory/transport remain. |
| FEATURE-11 | P2 | Installed; loaded qualification pending | Smart modifier for automatic page cycling | Owner approved defaults and implementation on 2026-10-27. [Contract](../architecture/smart-page-cycling.md) and [offline evidence](smart-page-cycling.md): App toggle, additive protected settings/storage, deterministic selection and prefetch integration implemented with replay, storage fault injection, native UI and managed emulator checks. Signed dev.66/App dev.53 rollout and protected Smart persistence across same-setup restart passed ([rollout evidence](display-evolution.md)); the original interval/Smart preference was restored. Remaining: matched fixed/Smart loaded comparison after PERF-01 under the [qualification procedure](../development/smart-page-cycling-plan.md). Indoor installation is verified; no loaded physical performance claim. |
| PLATFORM-01 | P2 | Ready | Basic iOS companion | Define essential setup/use scope and Android-only boundaries; reuse authenticated configuration/state contracts. No Android parity or police/road-alert availability promise; require iOS lifecycle/reconnect and physical qualification before release. Planning scope approved; no iOS implementation started by the website update. |
| PLATFORM-02 | P2 | Ready | Browser-over-USB core gauge configuration | Configure core functions from a supported desktop browser without a smartphone App. Reuse validated configuration, ownership and acknowledged-state rules through a defined application transport; keep ROM/stub recovery separate. Current recovery flashes firmware only; USB configuration is not implemented. |
| PLATFORM-03 | P1 | In progress | Shared hardware platform and private release candidates | [ADR-016](../architecture/platforms-and-releases.md): distribution guard/private integration skeleton and reference board/surface interfaces implemented; reference render parity and sanitizer geometry/delegation checks passed. Shared native geometry/font provider prepared with reference frame parity; private native-size profiles are offline-only. Private dependency profiles/board bring-up, exact-target signed metadata, private RC delivery and virtual-display telemetry remain staged work. Original board remains public; no new hardware qualification or runtime behavior change. |
| CODE-05 | P1 | Implemented in source; qualification pending | Complete remaining PR preservation | [Consolidation record](final-pr-consolidation.md) preserves #57/#58/#64/#72/#112: inactive typed companion proposal/probe, protected owner transactions, bounded trend smoothing/fallback, opt-in Jeep research and historical installation evidence. Current product/runtime policies remain; no provider, public capability, deployment or physical gate is enabled by cleanup. |
| CODE-04 | P1 | Implemented in source; qualification pending | Preserve unique PR work | PRs #41/#59/#74/#77: one-session Wi-Fi startup, safe gesture arbitration/pointer and logo redraw regressions, configured-rule and history/Current workflows. Preserve schema 19, hidden-code policy, existing cache fences and current Car/waiting design. Software CI and physical/deployment qualification remain separate. |
| CODE-03 | P1 | Software verified; deployment pending | Assessment remediation | Source fixes for trial UI liveness, recovery selection, intake backup/readiness/parsing/proxy trust/consent, artifact identity/provenance, catalog diagnostics, CI accessibility and documented policies. Meaningful software regressions and builds required; deployment/edge checks and physical QUAL gates remain separate. |
| CODE-01 | P2 | Ready | Continue typed failure migration | Replace remaining control-flow message matching at specific transport boundaries; preserve cancellation, protocol copy compatibility and verified outcomes; no broad rewrite |
| CODE-02 | P2 | Ready | Extract the next concrete coordinator owner | AppModel/transport extraction only with a feature or measured maintenance benefit; keep one lifecycle/operation lease, coupled saves and scoped state; independently test failure boundaries |
| QUAL-09 | P2 | Needs device | Accessibility and adaptive/OEM matrix | TalkBack, keyboard/switch, 200% text, RTL, fold/hinge and alternate Android generation; fixture success is not user/device qualification |
| QUAL-10 | P2 | Needs device | Adapter/vehicle expansion | One alternate BLE/GATT profile and another vehicle with captured identity, header/parser vectors and field loss/recovery evidence. [Owner-accessible candidates and customer-led priorities](../roadmap.md#vehicle-profile-expansion) follow Jeep qualification; scope guided remote captures per setup. [Capture runbook](../development/vehicle-capture-runbook.md) |
| RELEASE-01 | P2 | Ready | Production trust and Play readiness | Review signing/provisioning, release threat model and compatibility; follow [release runbook](../development/firmware-release-runbook.md); owner handles consequential Play actions; no production claim from prereleases |

## Deferred capabilities

| ID | Status | Scope | Entry gate |
| --- | --- | --- | --- |
| FEATURE-01 | Deferred | Broader manufacturer packs and compound/oxygen-sensor readings | Licensed provenance, exact decoder/status semantics, bounded source routing and vectors; [catalog rules](../development/reading-catalog-alerts.md) |
| FEATURE-02 | Deferred | Live companion telemetry and optional physical sensors | Measured radio/resource budget, bounded freshness and protected protocol; [hardware strategy](../architecture/hardware-and-transports.md) |
| FEATURE-06 | Deferred | Authorized third-party alert providers | Inactive [ADR-018 synthetic harness](../architecture/phone-companion-content.md) and Dev.48 synthetic provider seam and [integration guide](../architecture/phone-alert-providers.md) are implemented; live feed requires documented access, licensing, TTL/attribution and qualification under [ADR-015](../architecture/alerts-and-diagnostics.md#future-external-provider-seam) |

## Updating a task

Before implementation record its ID, scope, dependency and intended evidence in the
session/PR. A source change and its pending physical gate may have separate status.
After verification update the task, current state and focused evidence in the same
commit. Avoid append-only milestone summaries or repeated copies of this table.
If a new task supersedes an old one, preserve its acceptance requirement and link
that relationship. Do not call an unavailable measurement completed or turn an old
pending note into current authorization.


### 2026-10-29 field follow-up

- ABS/TCS: qualify captured Return followed by ignition reset and physical check;
  ignition alone did not restore the owner's Jeep. Preserve the acknowledged-Return
  receipt across gauge power loss and retain failed/unknown outcomes.
- Burnout/Line Lock: removed from normal menus at owner request; retain only legacy
  compatibility and pending-release recovery. Front-lock qualification is deferred.
- Dual adapters: qualify fair discovery admission and callback fencing with both
  adapters powered, independently absent, flapping and rejoining. The TCM must not
  starve ECM recovery, and ECM retries must preserve a healthy child connection.

- Automatic role correction is prepared for the exact captured Jeep ECM/TCM pair.
  Qualify opposite port detection, atomic retirement, unchanged configuration,
  inverse-map reconnect/reboot and no control replay using the expanded matrix.

- Dev.78/App dev.55 are installed after exact green CI; [indoor delivery](dev78-indoor-rollout.md)
  preserved setup, pending ABS operation and history. Physical panel/navigation
  passed by owner confirmation; ABS Restore/reset and swapped-port/loaded tests remain.
- P2 action availability copy: `ActionPagesSheet.kt` still presents “Not supported
  yet” for the owner-supervised ABS entry without live ECM context. Use the shared
  unavailable/reconnecting wording without weakening execution admission.

- FEATURE-04 compact action presentation: all 14 review states approved. Dev.79/App
  dev.56 source replaces the reconnect squares, modal ABS/fallback confirmation
  and stacked recovery wording. The [signed indoor delivery](dev79-action-ui.md)
  passed; the owner confirmed clean physical UI/navigation and removal of the yellow
  icon. Actual Jeep restoration remains unqualified; admission and durable uncertainty
  are unchanged.

- FEATURE-04 entry follow-up: dev.80/App dev.57 source restores first-configured-page
  entry and one Connecting presentation across action states. Pending records and
  admissible recovery controls remain intact. The [signed indoor delivery](dev80-action-entry.md)
  passed. The owner confirmed first-page entry and matching Connecting styling;
  parked checks remain separate.
