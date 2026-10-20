# ADR-013: Profile actions and gauge gesture triggers

**Status:** Shared command path implemented; vehicle execution gated pending parked qualification
**Date:** 2026-10-07
**Updated:** 2026-10-19; idle and combined ABS/TCS share the command path, live qualification disabled
**Deciders:** Project owner for interaction choices; implementation and physical
qualification determine which controller definitions become available.

## Context

The owner wants off-road vehicle profiles to offer actions such as high idle and
ABS/ESC control, assigned to gestures such as three upward swipes within five
seconds. Keep configuration and everyday use simple within the existing workspace.
Off-road scope does not remove accidental input, wrong-controller routing or
interrupted-command risks. Protections should address those failures without
repeated warnings or a separate operating mode for every command.

The [JK handler inventory](../vehicles/jeep/jk-handler-analysis.md) contains static
research leads for these examples. They are incomplete procedures and do not
establish compatibility with the swapped Jeep. Reading TCM temperature and gear
does not establish control support. ABS targets must not be assumed to be the TCM.

The development runtime handles clicks and holds, with clicks advancing pages and holds
opening pairing, and upward stroke recognition for the local page action. The owner
confirmed that three upward swipes jump to the saved TRANSMISSION page. Protected
settings/setup persistence passed on App dev.39; the owner also confirmed five-second
page cycling and the physical cooldown/later-repeat sequence. Cycling during a pending
gesture and broader gesture ergonomics remain unqualified. See the
[restart and persistence record](../evidence/restart-and-dashboard-rollout.md).

## Decision

### Profile model and availability

- Add an Actions section to the existing vehicle profile editor. Show supported
  actions by readable name, bounded parameters, assigned trigger and current state.
  Put controller research and technical reasons in Expert.
- A curated action definition identifies controller/calibration compatibility,
  source role, target ECU, required session, parameters, preconditions, command
  sequence, verification, timeout and restoration semantics. No raw command or
  arbitrary script editor is part of ordinary UI.
- Availability requires a verified matching definition and required current
  inputs. Unsupported examples stay out of the normal action picker. Profile
  selection alone does not prove compatibility.
- Action settings belong to the vehicle profile and its optional child context.
  Each gauge assignment selects its own enabled bindings. Resolve commands against
  the confirmed running configuration, explicit adapter/source and target ECU.
  Reject stale assignments and cross-vehicle or cross-gauge requests.
- Persist action configuration through the existing profile/config stores and
  atomic apply flow. Do not persist an armed gesture or automatically replay a
  command after boot, reconnect or profile change.
- Use explicit desired states such as an absolute idle RPM or ABS enabled/disabled,
  rather than relative increments or blind toggles. Parameters and ranges come
  from the matched definition, not guessed universal limits.

### Lightweight interaction

Initial implementation uses physical gauge gestures; Android configures them.
The proposed default is three complete upward swipes, with the third completed
within five seconds of the first. Source bounds are 2..5 swipes and 2..10 seconds
in whole-second increments. The
initial implementation permits one upward binding across the transmitted profile,
including its child pages; additional trigger families remain future work.

Recognize direction in screen coordinates after orientation, require real separate
press/release cycles, reject diagonal/noisy movement and suppress the click generated
by a recognized swipe. Use monotonic time, reset on expiry, contradictory input,
configuration change or leaving the normal page context. Vehicle controls must
also reset on loss of their required controller/session state. A local page jump
remains useful when an adapter is disconnected. Pairing, calibration,
updates and other overlays must consume input without arming vehicle actions.
Maintain existing click/hold behavior and test it explicitly.

Show a small progress indicator during recognition and a named result after execution.
One-shot bindings execute at most once per sequence, have a cooldown and cannot overlap
another action on the same source. Actions requiring continuous refresh use an explicit
active state and Stop operation, rather than repeated gestures generating new jobs.

Benign local actions can run immediately. For vehicle controls, use a compact named
confirmation by default, such as “Set idle to 1,200 RPM”. Definitions may allow direct
gesture execution only after that controller's behavior, applicable conditions and
recovery have been qualified. High idle and ABS are candidates, not promises of a
direct gesture path. This is one interaction policy, not an expanding set of modes.

Firmware checks required fresh vehicle state both at confirmation and immediately
before sending. Preconditions are action-specific; stationary/parked conditions must
be established where required, not inferred from absent data or zero RPM. Unknown
state means unavailable. The owner cannot remove definition-required checks by
changing a gesture. No repeated legal notices or blanket confirmation for local UI
actions are proposed.

### Execution and recovery

The existing per-source worker owns the adapter transaction stream. Submit bounded
action jobs into that ownership boundary; do not run concurrent ELM requests from
LVGL, phone or BLE callbacks. Keep read polling and other sources responsive, using
defined scheduling, time budgets and cancellation points. Firmware independently
validates the action, parameters, owner/config context and required state.

Each request has a unique ID and bounded duplicate suppression. The one-shot
request state model is:

`Ready -> Confirming -> Executing -> Verifying -> Confirmed | Rejected | Unknown`

Temporary controls retain an active operation after Start is confirmed; a confirmed
Start does not establish eventual release. Their ongoing state is specified below.

Unavailable actions cannot enter the execution path. Adapter send success is not
controller state confirmation. Readback or a definition-approved controller result
establishes success. Retry reads with bounded backoff; never blindly retry a control
write after timeout or loss of its reply. Show an uncertain outcome and reconcile
state before another request. Boot does not resume an unfinished action.

Temporary high idle needs a documented refresh/stop procedure and verified behavior
if the adapter or gauge loses power. A gauge cannot promise restoration after it has
lost contact. ABS restoration and key-cycle persistence must be established for the
specific controller. Do not enable a definition while those behaviors are unknown.
Keep explicit Restore/Stop accessible, show last confirmed state with freshness, and
store private qualification evidence separately from public profile data.

## Options considered

| Option | Complexity | Cost | Expansion | Fit with current code |
| --- | --- | --- | --- | --- |
| Curated actions plus bounded gesture bindings | Moderate | Definition and hardware qualification per controller | Reuses common execution/gesture engine | Uses existing profiles, atomic config and source workers |
| Generic macros/raw diagnostic commands | High | Broad interpreter and recovery burden | Flexible but applicability is difficult to prove | Adds another execution/configuration surface |
| App-only manual action buttons | Lower initially | Phone required in use | Same controller work still required | Reuses Android UI but misses autonomous gauge requirement |

Curated actions offer consistent parameters, routing and recovery while preserving
standalone gauge operation. Generic macros are flexible but leave incomplete
procedures and uncertain outcomes to the user. App-only buttons simplify gesture
work but do not meet the requested gauge interaction.

## Consequences and protocol impact

One engine can serve multiple vehicles and both primary/child sources. Each new
controller action still needs its own support evidence. Gesture ergonomics on the
round screen may require a different default after hands-on testing.

### Implemented local foundation

`Car > Actions` stores a single local `jumpPage` binding in the vehicle dashboard.
Removing its target removes that binding. Parent/child pages project into one payload;
conflicting upward bindings are rejected. Current profile schema/migration lives in
[Android storage](android-runtime.md#storage-and-recovery). Configuration uses the
existing atomic apply flow, not another settings/persistence channel.

Development capability `cfg:4` extends schema-2 documents with optional `actions`;
see [development protocol](../protocol/experimental-firmware-transfers.md). Configs
without actions retain their existing bytes/shape. New Android blocks sending a
binding to older firmware. Install a compatible App first; older Android rejects unfamiliar development versions. Public `configWrite`, `ota`
and link-capacity claims stay unchanged; no vehicle action capability is advertised.

Firmware compiles only `jumpPage`, gives gesture recognition to LVGL and reuses
normal page selection/persistence. Input uses 80..800 ms strokes, at least 32 pixels
up, at most 24 pixels sideways, an upward-to-sideways ratio of at least 2, and rejects
reverse/side excursions. A successful sequence has a five-second cooldown. Progress
and completion appear on the gauge. Pairing/calibration/transfer context, page change
and orientation changes reset partial sequences. A recognized swipe suppresses its
trailing click; a moving upward stroke does not turn into a pairing hold. Touch
activity and an active gesture sequence pause automatic page cycling, including
when the cycle interval is shorter than the recognition window. This path sends no adapter commands.

Vehicle execution, named confirmation, live preconditions, controller result/state
readback and Stop/Restore remain unimplemented. They require a versioned bounded
request/result contract and a verified controller definition, rather than extending
`jumpPage` to carry arbitrary commands. The existing static research inputs are
insufficient to enable high idle or ABS on this Jeep.

## First vehicle command: temporary high idle

**Offline foundation implemented; vehicle execution proposed and disabled.** Reuse ADR-013 for configurable commands. The first
scope is temporary engine idle for air-up and winching. The owner-selected next
command is combined ABS/TCS shutdown through the same framework, with its separate
controller route and ignition-cycle restoration semantics. Fourth-button Line Lock
work is deferred; persistent PCM adaptations remain outside this increment. The command does not operate the winch
or compressor. Its remaining work is owned by FEATURE-04, not a separate backlog.

### Compatibility and research gate

The installed vehicle is a 2010 JK with a swapped 5.7 L Hemi, not the stock 2007
vehicle used by the original research inventory. The [captured engine endpoint](../evidence/jeep-direct-exploration-results.md#placement-correction-and-identity)
reported responder `7E8`, calibration `68059434AD` and CVN `CA36378C`. These identify
the first qualification target, not permission to execute the stock JK handler.
Refresh identity from the selected primary connection before qualification; never
choose an engine command from the chassis label, profile name or TCM identity.

The owner has [confirmed successful raised idle in JScan on this Hemi swap](../vehicles/jeep/jk-handler-analysis.md#owner-reference-confirmation-2026-10-19).
Treat this as the working reference. The next gate is matching the complete
procedure to eGauge and the current PCM, not asking whether a stock-JK feature
exists. This report does not supply the test's RPM, wire sequence or release timing.

The [detailed offline review](../vehicles/jeep/jk-handler-analysis.md#detailed-offline-control-flow-review-2026-10-19)
now establishes two-byte RPM encoding, session/refresh call order and a Stop that
ends the refresh loop. It does not establish accepted replies or the PCM's release
timeout. `33 05` is not a discovered Stop packet, and `22 0122` is not the complete
high-idle command. The owner's working reference is now identified as Trail View,
a separate path with an explicit return-control definition. Its [parked record](../evidence/jeep-high-idle-reference.md)
now confirms accepted Start/Report/Return traffic and observed RPM restoration.
Fresh identity, target conversion/range and lost-refresh release still need
qualification before admitting a real driver; do not copy the adaptation screen's
discarded-result or Stop behavior.

JScan's [FAQ](https://jscan.net/faq/) describes its raised-idle feature as temporary
and returning to normal when its screen is left or the App disconnects. This is
reference-product behavior, not a measured timeout for our PCM. Likewise the
[Superchips TD3 guide, Crawl page](https://documents.holley.com/3df8c1fb78ec99ee1ad91f9a6d5cf5335f99e4d4.pdf#page=20)
documents its own Park/Neutral idle control; its RPM range must not become a
universal eGauge range. No cross-product claim establishes swapped-Hemi support.

### Presets and everyday interaction

- Implement one typed `temporaryIdle` operation with **Air up** and **Winch** named
  presets. Each selects a controller-supported target RPM and a finite duration.
  Proposed first-use duration is five minutes, explicitly a product choice; the
  qualified definition caps duration and RPM. Do not preselect an unverified RPM.
- Car > Actions owns preset configuration and Start/Stop. Expert owns controller
  identity, qualification and support reasons. Only matching qualified commands
  appear in ordinary controls; no raw packets, macros or scripting UI.
- Keep the existing direct page-jump binding and its saved representation working.
  Add an alternative upward binding that opens a compact Quick actions menu. Named
  `Start Air up` / `Start Winch` choices show target RPM and duration; selecting one
  is the explicit start confirmation. Cancel has no vehicle effect.
- Do not assign three swipes to Air up and four to Winch: the first sequence is a
  prefix of the second. Retain one upward binding and use the menu to select a
  preset. Direct-start gestures can follow controller and interaction qualification.
- While active, show the preset, target and remaining time using existing compositor
  slots, with an accessible named Stop control on the gauge and in Car. Critical
  alerts retain display priority; the command must remain stoppable. Menu interaction
  consumes ordinary page clicks/holds and pauses cycling without changing pairing.
- Configuration persists; active state and remaining time do not. Phone disconnect
  alone does not stop a gauge-owned session. Gauge/adapter loss must release control
  through the qualified ECU behavior, not depend on the phone being present.

### Minimum implementation boundary

| Boundary | Extend existing ownership | Required behavior |
| --- | --- | --- |
| Saved model | `ProfileActions.kt`, `GaugeModels.kt`, existing profile codec/store | Separate presets from the single gesture binding; retain old `jumpPage` data and fail closed on unknown definitions/parameters. No migration until the contract is agreed. |
| Configuration and owner control | Existing schema-2 compile/apply path, protected BLE codecs | Negotiate a new command capability before sending new fields or Start/Stop/status. Bind requests to gauge, running revision, vehicle, definition, endpoint and link generation; allocate wire IDs only with C/Kotlin rejection vectors. |
| Gesture/UI | `page_action.c`, `ui.c`, `PageActionsSheet.kt` | Reuse recognition, rotation mapping, click suppression and cooldown; dispatch a typed intent after recognition. Do not embed packets in `pageId` or add another gesture recognizer. Review pending PR #59's loaded-swipe fixes before deriving action UX from that branch. |
| Execution | Existing primary `source_worker_t` / `obd_task` ownership in `main.c` | One bounded active control slot, a pure lifecycle policy and a curated controller driver. No new task, callback IO or unbounded command queue. Preserve ordinary polling and the child worker. |
| Status and history | Existing UI state and shared alert/history framework | Distinguish Requested, Starting, Active, Stopping, Restored, Rejected and Unknown. Store scoped transitions, not heartbeat samples. Normal completion is quiet; actionable uncertainty uses shared attention rather than a second notification system. |

Allow only a matching fresh engine identity and definition-required inputs at both
admission and send. Minimum inputs must establish engine running and stationary
Park/Neutral where required by the procedure. Unknown or stale is not zero/parked.
The experimental TCM gear reading is not by itself qualified proof of Park/Neutral.
A missing child adapter is relevant only when the chosen definition requires a
validated input from it; high idle always routes to the primary engine endpoint.

Use a unique operation ID and a monotonic deadline. Start is sent at most once
until its outcome is reconciled. Recurring traffic is allowed only when explicitly
defined as refresh/status for that active operation; it is not an automatic retry
of an uncertain Start. Stop has scheduling priority. Budget adapter transaction
latency against refresh, guard and release deadlines before admission; do not put a
keepalive loop in front of normal polling. Refuse unsupported timing rather than
starve readings, fault checks or Stop. Configuration/update transitions must stop
and establish release first or remain unavailable, not silently pause control IO.

Duration expiry, failed required guards and owner Stop enter the release procedure.
Return control to the ECU's normal regulator; do not write the sampled pre-start RPM
as a supposed restore value. Report Restored only after definition-approved release
evidence. Link loss, reset, missing reply or expired session cannot manufacture this
evidence: mark Unknown, cease stale-session traffic and reconcile without replaying
Start. A controllable, bounded ECU timeout after lost refresh is required before
enablement; software cannot promise a Stop packet after power or transport loss.

### Small delivery slices and acceptance

| Slice | Work | Exit evidence |
| --- | --- | --- |
| 1. Controller procedure | Reproduce the owner's working JScan operation, confirm current PCM identity and resolve the matching handler; record allowed RPMs, setup, replies, release, timing and ECU timeout. | Complete matching procedure/reference trace, not another generic JK compatibility check; unresolved fields remain explicit and no eGauge control is enabled. A parked comparison is needed to close behavior claims. |
| 2. Offline vertical slice | Add the negotiated contract, bounded lifecycle/driver seam and preset/menu UI, initially using a fixture-only driver. | C/Kotlin contract parity; old-profile/old-firmware compatibility; two presets, one gesture and visible Stop; no fixture driver selectable by a real production profile. |
| 3. Parked qualification | Introduce the matched driver, start with explicit Start/Stop, then bind the existing gesture/menu. | Owner confirms RPM and normal-control return; verify repeat Start, Stop, expiry, brake/gear/movement guard behavior, lost replies, adapter removal, gauge reset and independent TCM continuity. No moving or winch-load test is needed for the first idle-only check. |
| 4. Enable the qualified pack | Enable only the evidenced controller scope through ordinary profile/configuration flow. | Loaded polling/refresh/Stop latency and internal heap/stack margins; App/gauge/radio loss, maintenance transitions, replay suppression and physical operation recorded separately from fixtures. |

Offline fault tests include stale guard/identity, wrong controller, old config/link
generation, duplicate Start, lost Start/Stop replies, timer wrap, timeout during a
diagnostic request, cancellation, queue pressure, profile changes and reboot. A
forced disconnect test may run on a real PCM only after its documented timeout and
recovery procedure are established. These slices do not authorize release or flashing.

### Implemented offline foundation

The earlier `temporary_idle.c` numeric policy and its inner codec remain portable
regression fixtures. Live intents now use the typed shared command path below;
there is no implicit numeric-RPM conversion or second live command interface.
`IdlePreset` stores the parent's Air up/Winch drafts. Numeric targets remain drafts;
the captured idle command accepts only the fixed setting and a bounded duration.

### Captured Jeep driver candidate

`jeep_idle_driver.c` implements the [Trail View reference](../evidence/jeep-high-idle-reference.md)
as a pure, fixed-size stepper. `START_CAPTURED` uses only the captured `06 80`
word. There is no API for arbitrary bytes or a numeric RPM conversion. A caller
cannot treat a saved target RPM as a qualified mapping to this operation. The
candidate is selected internally by the shared command controller below. Its
live qualification mask is still zero; no current vehicle profile can execute it.

| Job | Required protocol evidence |
| --- | --- |
| Captured Start | Engine route setup, positive diagnostic-session reply, pre-Start report 01, one Start acknowledgment, active report 02 |
| Report | Engine route setup and exactly one known report 01 or 02 |
| Return | Engine route setup, one Return acknowledgment, then inactive report 01 |

The post-Return report is a new verification requirement exercised synthetically
with captured pre-Start report bytes. It was not read after Stop in the Jeep.
Report meanings remain scoped inferences; unknown codes cannot establish success.
An explicit Return acknowledgment alone does not complete the job. Even
`RETURN_CONFIRMED` is protocol evidence only: Start remains fenced until the
qualified identity/state/settled-RPM owner calls reconciliation. The current
candidate does not implement that live evidence owner or promote the lifecycle's
Release to verified automatically. A status read never clears Unknown on its own.
Reconciliation preserves operation and ticket replay fences.

The primary source worker must own one exclusive route/transaction lease for the
whole bounded job. It executes each returned command with its existing transport,
adds CR once, feeds only that ticket's bytes and advances the monotonic clock.
No transaction or route-changing normal poll may overlap. Normal polling and the
independent child worker retain their existing ownership; normal primary polling
runs between jobs. The candidate has no IO callback, loop, new task or allocation.
The host fixture measures a 592-byte object, not device heap/stack qualification.

Each transaction has a 1200 ms candidate deadline and the entire job a 9000 ms
budget, including cancellation/Return. Neither fragmented data nor NRC 78 extends
these budgets. They are scheduling limits, not a measured ECU lease timeout.
Admission must account for the entire job plus normal polling and freshness before
the lifecycle can enable this driver. It must refuse timing that would starve
readings or miss release deadlines; this stepper does not qualify a refresh cadence.
It does not copy the reference's unrelated polling or functional keepalive loop.

Cancel/failed guards before Start prevent the control write. After Start, a known,
prompt-completed transaction allows prioritized Return; an in-flight request first
finishes or times out. A lost reply or context becomes Unknown and does not issue
blind Start/Return retries. Return can still proceed when an ordinary Start guard
fails, but cannot target a different source, operation, revision or link. Retired
completion tickets and ticket exhaustion cannot advance a job. After uncertainty,
the external owner must resynchronize the adapter and verify the same controller
and normal control independently; reinitializing the driver is not recovery.

The new strict ELM control-frame decoder accepts one 11-bit headered single-frame
reply from the exact engine ECU and validates declared length before dispatch.
The driver checks service, parameter, exact data length and known state. Wrong
ECU/service, headerless or duplicate replies, malformed/truncated/oversized data,
negative replies and unsupported response-pending outcomes never count as success.
Existing read decoders are unchanged. Transport prompt recovery and stale-RX
quarantine remain responsibilities of the adapter owner before a later job.

Offline tests generate their temporary C fixture from the sanitized JSON capture,
then exercise the production stepper/decoder under address and undefined-behavior
sanitizers. They cover both captured Start/Return pairs plus synthetic inactive
post-Return reports, guard/cancel boundaries, duplicate/retired operations, scoped
links, lost replies, bounded waits, timer wrap, reconciliation and malformed inputs.
Run `python -m unittest tools.tests.test_jeep_idle_driver` or the normal host suite.

The shared controller now supplies protected dispatch, primary-worker scheduling,
bounded event history and the physical actions menu. The remaining integration is
the independently qualified controller identity/guard/restoration evidence owner. The [parked follow-up](../evidence/jeep-actions-live-qualification.md)
confirms fresh identity, inactive status after session re-entry and one combined
JScan process/Bluetooth-loss restoration. Target conversion, immediate same-session
post-Return readback and broader interruption behavior still need qualification.
This delivers the offline driver without claiming a complete vehicle-control path.

### Shared command owner

`vehicle_commands.c` owns two independent action slots and one driver lease. Idle
can remain active while ABS/TCS needs restart; neither operation can run concurrent
adapter transactions. Start/Stop, countdown expiry, guard loss and cancellation use
one typed admission/state path. `jeep_idle_driver.c` and `jeep_abs_tcs_driver.c`
contain only their captured controller procedures. The latter addresses 620/504,
enters the diagnostic session for each job, and never equates an acknowledgment
with restored ABS/TCS. Fourth-button valve commands are not included.

`vehicle_commands_runtime.c` runs on the existing primary adapter worker. Its
fixed queue has four entries; callbacks only enqueue and snapshots use a short
critical section. Driver buffers, IO scratch and the sixteen-record event ring
are fixed external BSS in PSRAM. No task or control-time heap allocation is added.
The child worker keeps its independent transport and scheduler.

A versioned NVS uncertainty marker is committed before any control leaves the
worker. Failed/malformed/newer journal reads refuse execution without overwriting
the source. A restart exposes Unknown and never resumes control. A marker clears
only after same-identity/revision/operation proof sampled after the current state
transition and within two seconds. Guard evidence also expires after two seconds.
Identity is a profile-owner proof, not a client-provided byte or an adapter name.
No profile currently supplies these proofs: both live qualification masks stay zero.

Every IO has a 1,200 ms budget including pre-IO journal time, and each driver job
has a 9,000 ms budget. Fragmented data and pending responses do not extend them.
After a job the worker restores filter/flow/header defaults under a separate
1,200 ms cleanup budget before releasing normal polling. Failure disconnects and
requires route cleanup during initialization. Neither cleanup nor reconnect
replays a control. Real cadence, stack margin and guard freshness under these
budgets still need parked measurement before qualification.

The App uses its existing authenticated session and operation coordinator. Current
status polls automatically, faster during active/uncertain work, with bounded
backoff. Retired vehicle/gauge/revision results are discarded. Wire bytes and the
schema-2 menu action are owned by the [command protocol](../protocol/experimental-firmware-transfers.md#shared-vehicle-commands-v1).
The existing upward-swipe recognizer now targets either a page or the actions
menu; it never toggles a controller directly. Menu confirmation, context loss and
pairing/critical-alert preemption use the existing UI owner. Idle durations are
stored in the menu configuration so App and gauge use the same chosen values.

Maintenance uses the existing atomic transfer gate, held across active command jobs
and gaps between them. Configuration and OTA cannot claim the adapter while a control
is active. Wi-Fi startup briefly claims the same gate before pausing vehicle links.
Configuration additionally waits for uncertainty or restart-required state to reconcile;
firmware recovery remains available once no active control job remains. Cached UI
status alone is not a mutual-exclusion primitive. The shortcut menu remains available
for Stop while the command gate is held.

### Next parked Jeep increment

The [parked follow-up](../evidence/jeep-actions-live-qualification.md) closes the
fresh-identity gap and supplies a fixed-target reference plus one loss/recovery
observation. The shared path is now wired and tested offline. Before enabling a
profile, the next parked increment must close the remaining evidence inputs:

1. Preserve the captured target as a distinct fixed operation. Do not convert
   arbitrary saved RPMs without a qualified mapping and supported range.
2. Identify fresh primary-vehicle Park/Neutral, movement, engine and controller
   identity evidence, plus ABS/TCS reset/state proof. Bind that evidence owner to
   the existing primary-worker controller; do not promote a mask from fixture results.
3. On the next parked run, verify the immediate same-session report after Return,
   settled RPM and cancellation. The delayed inactive read after session re-entry
   does not qualify that sequence.
4. Extend failure/recovery and independent TCM checks using explicit reconciliation.
   One observed return after process/link loss does not define the ECU timeout.

No new release, installation or vehicle-control authorization follows from these
notes. Combined ABS/TCS is the next active command after idle integration and
needs its own controller qualification. Fourth-button Line Lock is explicitly
deferred by owner; preserve the reference evidence without implementing or exposing
it in the current command UI. Neither path shares the idle driver implicitly.

## Remaining qualification

Controller execution and its negative scenarios belong to **FEATURE-04** in the
[backlog](../backlog.md). Matching controller identity, complete commands, readback,
restoration and interruption evidence are required before enabling an action.
[Local fixture results](../evidence/profile-action-fixtures.md) and
[physical gesture observations](../evidence/profile-actions-rollout.md) are historical
records; [current state](../current-state.md) owns current qualification.
