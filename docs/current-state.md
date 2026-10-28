# Current implementation and evidence

Updated 2026-10-28. This owns implemented/installed facts and their evidence limits.
The [backlog](backlog.md) owns task status; the [roadmap](roadmap.md) owns outcome order.
PRs #103, #104 and #105 are merged. App dev.53 is installed on the Pixel, and
signed dev.71 is installed through App/Wi-Fi after the earlier explicitly
authorized application-only USB dev.69 repair. [Current display evidence](evidence/display-evolution.md#dev69-authorized-usb-installation)
records preservation, stable boot and the remaining sensor/physical gates. The
[previous full App/Wi-Fi rollout](evidence/action-pages-indoor.md#2026-10-27-combined-dev66-rollout)
separates exact healthy-image/setup readbacks and indoor checks from physical
display/touch and remaining loaded Jeep qualification.
The opt-in [parked idle candidate](architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate)
is installed and ready for supervised Air up/Winch checks using the
[one-visit runbook](development/jeep-action-qualification.md#one-visit-idle-test-candidate).
The owner subsequently reported successful Air up operation, with an unexpected
“Coolant unavailable” note. The UI flag mismatch is corrected in installed dev.63; countdown duration
and actual coolant availability during that run are not yet confirmed.
On 2026-10-28 the owner reported Air up remaining at “Check idle / Status unknown”
after restarting the vehicle and gauge, with normal physical idle and live RPM.
The source retains a durable uncertainty marker; its parked adapter previously
blocked identity acquisition in Unknown and had no reboot recovery verifier.
The read-only [idle recovery source](architecture/vehicle-actions.md#read-only-recovery-of-an-uncertain-idle-operation)
now prepares exact-ECM routine-status plus settled-RPM reconciliation, without
replaying controls or erasing evidence. It is not yet installed/physically qualified.
Website impact: corrective recovery behavior needs no new marketing claim or image.
Ordinary builds still disable vehicle controls; both qualification masks remain zero
and ABS/TCS execution remains disabled.

## Continuity update

The owner approved PERF-01 on 2026-10-27. Dev.65 source adds page-cache continuity,
cycle-pause recovery, prioritized/shared polling, display-only rate classes and
stable CEL presentation. It includes the approved action UI from published dev.64.
[Implementation and qualification](development/gauge-continuity-plan.md) records
scope, offline checks and the at-home/parked sequence. Signed installation, five
Wi-Fi lifecycles, automatic/Bluetooth recovery and settings persistence passed.
On dev.65, the owner confirmed clean pages, action navigation, no CEL question mark
and ten-second cycling/resume after touch. Loaded ECM/TCM reading continuity and
control/thermal timing still require the Jeep.

Dev.66 introduced a fix for a concrete startup retry delay: an unanswered first
code-list scan now retries from two seconds with bounded exponential backoff,
instead of immediately waiting its steady 60/120-second interval. It also logs
changes in MIL/filter presentation for attribution. Warning and filter validation
remain strict. Automatic MIL status checks now have a 60-second floor; gauge/shared-diagnostic
last-successful presentation bridges refresh/backoff gaps within the
[diagnostic retention limits](architecture/alerts-and-diagnostics.md#mil-code-and-coverage-semantics).
Raw phone/history freshness remains unchanged. The owner's later in-vehicle startup CEL
observation is not yet attributed by a raw capture; [investigation evidence](evidence/action-pages-indoor.md#2026-10-27-startup-cel-investigation)
separates that report from the installed source fix.
The same image removes decorative play/stop glyphs from shared action progress
states at the owner's request; colored progress rings and action controls remain.

The subsequent owner report on Jeep connection still described a startup CEL.
The follow-up source change defers the face's MIL icon until code identity is usable
or coverage reconciled, keeping unresolved filter acquisition quiet without claiming
ECU Off. Routine MIL/stored/pending checks target five minutes, permanent checks ten;
initial category retry and explicit-clear verification remain responsive. Raw
freshness/packets are unchanged. This change is not yet installed or Jeep-qualified;
[diagnostic semantics](architecture/alerts-and-diagnostics.md#mil-code-and-coverage-semantics)
own the evidence and latency tradeoff. Website review: no new marketing claim or
visual asset is needed for this corrective behavior.

## Smart page cycling

FEATURE-11 is installed with the approved defaults: one opt-in Settings
switch, existing interval selection, alert-driven/thermal/context priorities,
minimum viewing/manual protection, bounded priority insertions and saved-page
fairness. The [runtime contract](architecture/smart-page-cycling.md) owns behavior
and tuning, and [offline evidence](evidence/smart-page-cycling.md) records executed
checks. The additive `sp:1` protected settings path and revision-matched sidecars
preserve legacy display compatibility. Existing public capability flags remain off.
The combined source is installed; indoor results are recorded in the
[display rollout evidence](evidence/display-evolution.md). Matched loaded timing, comfort and resource qualification
remain in the [milestone procedure](development/smart-page-cycling-plan.md).

## Display and adaptive sampling

Dev.66 / App dev.53 introduced ten saved pages, numeric Dual + arc,
shared explicit gear colors, an icon-only boot/no-data surface and reviewed
PID-specific stability backoff. [Display contract](architecture/display-experience.md)
and [sampling contract](development/reading-catalog-alerts.md#display-and-polling-classes)
own defaults and boundaries. The Auto orientation candidate is implemented behind
default-off probe/physical-qualification gates; the normal image does not offer it.
The [display evidence](evidence/display-evolution.md) separates offline checks,
indoor persistence and remaining physical/loaded qualification. App local profile
schema is 18; its migration changed only the schema version, retaining all other
profile fields. Gauge associations remain schema 2.

The extended ten-page round trip passed with exact original five-page restoration
at revision 63. The owner confirmed 10/10, wrap and clean labels without freezing.
Populated Dual + arc also passed visual review. Hold-exit/navigation was reported,
but a later active preview state makes explicit physical stopped-state proof pending.

Dev.70 introduced verified sensor control-register readback and normal-power
31.25 Hz sampling, resolving dev.69's pinned X symptom without calibration offsets
or relaxed gravity guards. The guarded 4 KiB internal sensor stack survived the
initial NACK/retry path; dev.70 observed worker/main margins of 1680/1080 bytes.
Earlier withdrawn stack-failure candidates and the saturated-axis diagnosis remain
in [dated evidence](evidence/display-evolution.md).

Protected four-angle reads, Auto/Wi-Fi suspension/recovery and same-setup restart
persistence passed on dev.70, retaining the current Smart On preference. The owner
reported incorrect text orientation. Two labelled poses establish a fixed LCD
quarter-turn offset; installed dev.71 includes mapping version 2 and the larger muted-grey logo. The owner
confirmed the grey icon and tap-to-wake. Corrected all-angle/touch
and explicit hold-exit qualification remain pending. Normal qualification and public
flags stay off under the [indoor procedure](development/display-indoor-qualification.md).

## Installed baseline

| Item | Last recorded state | Evidence |
| --- | --- | --- |
| Pixel | App `0.2.0-dev.53` / versionCode 53, reviewed source `fd8fae6`, merged PR #105; profile schema 18 / associations 2 | [Combined rollout](evidence/action-pages-indoor.md#2026-10-27-combined-dev66-rollout); profile migration retained all values; final alert policy restored, assignments and presentation byte-identical |
| Gauge | Signed parked-idle/orientation candidate `0.2.0-dev.71` from `e80864d`, generation 52, 240 MHz; configuration revision 65 after a verified same-functional-setup restart | [Dev.71 installation](evidence/display-evolution.md#dev71-installation-and-indoor-checks): exact healthy image and cleared journal, original functional dashboard/preferences retained; owner confirmed larger grey logo/wake. Corrected physical mapping qualification remains pending |
| Physical display/touch | Owner confirmed dev.66 text-free logo, touch wake and normal reading/action navigation: “Logo and all navigation work.” | [Display rollout](evidence/display-evolution.md); all ten pages subsequently owner-confirmed; new-arc fit owner-confirmed; hold-exit was reported but later active state requires explicit stopped-state proof; loaded legibility and Auto sensor/touch alignment remain separate gates |
| Hardware | Waveshare ESP32-S3-Touch-LCD-1.28, 240 × 240 GC9A01, CST816S, 16 MB flash, 2 MB PSRAM | Qualified toolchain baseline ESP-IDF 5.4.1 |
| Vehicle | Modified 2010 Wrangler, 5.7 L Hemi, JSS ZF 8HP70 / PCS TCM-2800; two same-model adapters | [Dev.53 live simultaneous sources and independent recovery](evidence/gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification) |
| Saved setup | One whole-vehicle dashboard, distinct ECM/child-TCM bindings, dual mode On | Five pages, four TCM CEL filters, thresholds and action settings retained; OTA preserved revision 60 and exact document bytes; the targeted same-setup test then advanced to 61 with only baseRevision changing. [Current preservation check](evidence/action-pages-indoor.md#2026-10-27-combined-dev66-rollout) |

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
| Car workspace | Approved source: one attention list, direct fault/alert detail, Current/History, focused settings and shared hidden-code projection. Installed App retains the earlier compact health/recent-alert UI | [Pixel visual review and fifteen focused emulator fixtures](evidence/car-ux-review.md); existing phone setup preserved | Live vehicle alert/fault UX qualification |
| Shared alert/history framework | Threshold/MIL/synthetic producers share lifecycle, attention, bounded gauge context/journal, phone history/export and opt-in monitoring | Dev.48 Pixel/gauge synthetic Entry/Expired import and shared context/banner; 162 JVM tests, eight earlier emulator fixtures | Real vehicle alerts/context, physical attention, background/OEM and storage/power-loss matrix |
| Pages/catalog/alerts | Six renderers including numeric Dual + arc; negotiated ten-page support; one catalog with 53 Mode 01 and two existing JSS readings; every selectable reading can alert | Production fixtures and [combined rollout](evidence/action-pages-indoor.md#2026-10-27-combined-dev66-rollout) retain the owner's five-page dashboard | Loaded layout/alert timing, units, dwell/stale behavior and legibility remain separate |
| Configuration | Coupled reviewed payload, dual storage generations, running revision/hash, trial/fallback | Prior protected sends and restart/recovery readback | Power/interruption and all-layout hardware matrix |
| Firmware updates | Signed catalog/bundle, owner bootstrap, private Wi-Fi, A/B boot health and durable journal | [Dev.66 full upload and five maintenance cycles](evidence/action-pages-indoor.md#2026-10-27-combined-dev66-rollout); exact healthy alternate image, empty journal, retained setup and automatic/Bluetooth recovery. Dev.60 interruption checkpoints remain separate evidence | Trial-boot rollback, rail-power loss and loaded live-adapter qualification remain |
| Phone persistence | Source schema 19 retains disabled alert drafts; installed schema 18 / schema 2 assignments; serialized IO, cancellation-safe writes and typed pairing failures | App dev.53 migration changed only schemaVersion 17 to 18; profile values, assignments and presentation preserved. OTA core preferences remained byte-identical | Process/storage failure and broader OEM cases; later child removal cause remains unconfirmed but cleanup was accepted |
| Multiple adapters | One logical vehicle, optional Expert child, independent firmware workers and source freshness; direct child picker and scoped selection fix installed on Pixel | [Setup review](evidence/second-adapter-setup.md); [dev.53 live reads/resume, independent loss/recovery and phone off/on](evidence/gatt-long-read-recovery.md#dev53-parked-jeep-dual-source-qualification); owner confirmed live ECM/TCM display and independent expiry/return | Startup absence, loaded maintenance, rates and sustained soak remain open |
| Health measurements | Fixed worker records, queue drops, internal/DMA/PSRAM lifecycle logs, allocation failure hook and sampled stack minima | [Dev.66 indoor measurements](evidence/action-pages-indoor.md#2026-10-27-combined-dev66-rollout); zero observed allocation failures/queue drops across upload and maintenance checks | Longer loaded soak, radio coexistence, complete stack/progress coverage and unobserved vendor-private/transient failures |

## Approved Car implementation, not yet installed

The approved Car source replaces overlapping Vehicle health/recent Alerts cards with
one attention list, direct issue detail, Current/History and focused Alert settings.
Saved hidden-code intent projects current/history/context/Expert/notifications and
ordinary reports through one owner; immutable raw evidence remains internal. Isolated
connected edits reuse the existing transaction; unrelated pending setup keeps review.
Phone schema 19 retains disabled alert drafts while excluding them from gauge payloads.
Debug/release JVM checks, build/lint and 73 managed-emulator checks passed;
[verification evidence](evidence/car-ux-review.md#2026-10-28-approved-car-and-hidden-code-implementation)
records scope and the refreshed production Car capture. This implementation does
not change the firmware wire schema or publish/install an APK.
The [shared contract](architecture/alerts-and-diagnostics.md#advanced-vehicle-scoped-cel-code-filters)
and [design rules](design/design-system.md#current-component-and-messaging-rules) own behavior.

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

- Source phone profiles: schema 19 reads schemas 1..19; the last recorded installed App uses schema 18. Gauge associations use schema 2. Older Apps reject new saved state;
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
and tracked as FEATURE-07. The installed candidate has a live, bounded IMU path but remains unqualified
because of the pinned X reading documented above. Manual rotation and timed page
cycling remain independent implemented controls.

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

The release gate exposed modal-editor fixture races in two CI attempts (saved-alert
callback missing, then duration-editor scroll target absent). The test-only keyboard
and destination synchronization from the pending UI-stability work is included,
with the same boundary applied before Alert save. Real taps and exact value/disabled
control assertions remain; no production App behavior or dependency changes.

## Assessment remediation prepared in source

CODE-03 source fixes strengthen trial UI liveness, browser retry selection, intake
backup/readiness/parsing/proxy trust/consent, release evidence and APK identity checks,
catalog diagnostics and continuous accessibility coverage. The [maintenance record](evidence/assessment-remediation.md)
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

These source changes do not update installed App/gauge state above, publish the
website/backend, promote capability flags or close physical/vehicle gates.

## Remaining PR source preservation

The [remaining consolidation](evidence/final-pr-consolidation.md) preserves the
inactive companion proposal/probe and shared protected owner factory, bounded trend
rendering/fallback and opt-in Jeep coverage/capture research. Normal owner operations,
current display/Car/gesture behavior and capability gates stay authoritative. Trend
fixtures and fake owner/probe sessions do not prove physical rendering/radio/provider
behavior. The dev.71 installation record above is historical evidence retained from
PR #112; this cleanup did not interact with the phone, gauge or vehicle.
