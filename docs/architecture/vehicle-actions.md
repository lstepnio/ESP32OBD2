# ADR-013: Profile actions and gauge gesture triggers

**Status:** Shared command path implemented; vehicle execution gated pending parked qualification
**Date:** 2026-10-07
**Updated:** 2026-10-29; idle, combined ABS/TCS and Burnout Mode share the command owner; explicit owner-supervised brake candidate enabled; ordinary brake profiles remain unqualified
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
controller route and ignition-cycle restoration semantics. Fourth-button Line Lock was deferred in the initial increment and now uses the
separate [Burnout Mode path](#burnout-mode-and-brake-actions). Persistent PCM adaptations remain outside scope. The command does not operate the winch
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

### Coolant protection for raised idle

Idle uses the existing vehicle **high-temperature Coolant alert** as its stop limit;
there is no second preset temperature, protocol field or separate persistence path.
The compiled primary ECM Mode 01 PID 05 / `7E8` definition and canonical Celsius
thresholds are authoritative. Choose the lowest configured high limit (normally
Warning; a critical-only document uses Critical). Low/equality rules, unrelated
readings, simulated sources and child-TCM data do not qualify.

- App shortcut setup and preset editing warn when that limit is absent. Saving the
  local shortcut remains possible. The send review repeats the warning; it is not
  permission to start. Only the confirmed running gauge document enables App Start.
- The shared firmware worker is authoritative for App and physical-menu commands.
  Fresh coolant means a valid sample no older than 2,000 ms, scoped to the current
  primary link/configuration. At or above the limit, prioritize Return immediately,
  without waiting for alert dwell/hysteresis. Failed reads retain the last valid
  sample and its original age, so missing data cannot erase a known high reading.
- Without fresh coolant, a confirmed physical-gauge Start may run for at most
  **60 seconds**, or the configured duration if shorter. App Start is refused.
  If an existing session loses fresh coolant, latch a deadline 60 seconds after
  the first loss; the original session expiry can only shorten it. Recovery or
  repeated loss never extends either deadline. All other vehicle guards still apply.
- After a fallback session, another Start requires explicit physical-gauge input,
  even if coolant recovered. Fresh local Start may use the normal configured timer;
  unavailable coolant retains the one-minute cap. There is no automatic restart.
  The gauge shows the shortened countdown; App Stop remains available. Local
  confirmation is trusted queue metadata, not a caller-settable wire field.
- Timer expiry and thermal Stop use the existing Return and independent normal-idle
  verification path. Lost links and negative replies never become a false success.
- A primary-worker-owned fixed snapshot uses bounded 300 ms header-attributed
  coolant reads, targeted every 500 ms **between complete control jobs** while idle
  needs them. No new task, heap buffer or concurrent ELM session is introduced.
  A queued Stop skips the guard read. Long jobs age the snapshot into the same
  latched timer; absence of any other required guard still cancels/Returns.
- Loss of the adapter/link still means Unknown, not a fabricated successful Stop.
  The ECU's qualified lost-refresh behavior remains the fallback. Measure actual
  sampling/Return latency and loaded-worker stack margin before enabling controls.

Offline acceptance includes equality/over-limit, missing rule, wrong source, stale/
invalid/nonfinite samples, tick wrap, guard loss before control, latched release,
one-minute fallback, non-extending deadlines, local-only restart,
no auto-restart, independent ABS behavior, warning-before-save and unsent App limits.
For parked qualification, use a temporary threshold within a normal warm-up range
so a small ordinary temperature rise can demonstrate automatic Stop without an
actual overheat. Verify Start refusal while already above the limit and independent
normal-idle readback, then restore the owner's intended alert. Do not use a hot
engine or coolant-data manipulation as a substitute for these controlled checks.

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

`vehicle_commands.c` owns three independent action slots and one driver lease. Idle
can remain active while ABS/TCS needs restart; neither operation can run concurrent
adapter transactions. Start/Stop, countdown expiry, guard loss and cancellation use
one typed admission/state path. `jeep_idle_driver.c` and `jeep_abs_tcs_driver.c`
contain only their captured controller procedures. The latter addresses 620/504,
enters the diagnostic session for each job, and never equates an acknowledgment
with restored ABS/TCS. Burnout Mode uses a distinct typed procedure and never the combined-shutdown route.

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
The explicitly built [parked idle candidate](#owner-supervised-parked-idle-candidate)
has a separate temporary admission policy; it does not promote either mask.

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

### Owner-supervised parked idle candidate

Owner approved preparing this exception on 2026-10-26 to combine idle testing and
input capture in one parked visit. It substitutes deliberate physical-gauge
confirmation of Park and parking brake for the still-unmapped automatic Park/brake
inputs. It is not a production vehicle definition or driving mode.

- `CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST` defaults off. Build only an explicit signed
  candidate using `python tools/dev.py build --target firmware --parked-idle-test`;
  its separate build/config output cannot change the ordinary build. The release
  workflow has an opt-in `parked_idle_test` input. CI compiles both variants.
- Only a bound, real primary ECM is eligible. The primary worker checks exactly
  one `7E8` Mode 09 calibration `68059434AD` and CVN `CA36378C` per link/revision,
  refreshing while idle every 30 seconds. Wrong/missing/malformed identity disables
  admission. Link/configuration changes discard identity, samples and baseline.
- Fresh primary speed must be zero and RPM must show a running engine. Both expire
  after two seconds. Admission requires ordinary idle (400..1,200 RPM); active tests
  allow the captured raised setting up to a conservative 2,500 RPM ceiling. These
  are test-policy limits, not a qualified Park/brake interpretation. Failed input
  reads invalidate their sample; movement/guard loss latches Stop before later reads.
- Each Start requires a deliberate physical-gauge gesture under the
  [ordered action-page contract](#ordered-action-pages). Review 4 replaces the ordinary
  repeated Park/brake confirmation screen with hold-to-start. The App can monitor
  and Stop but cannot Start, including by
  sending raw authenticated command bytes. This idle switch alone does not enable ABS/TCS.
- Air up can run **five minutes** with fresh coolant; Winch keeps its saved one-minute
  preset. Longer test drafts are capped at five minutes in both confirmation and
  admission. Missing/stale coolant retains the existing at-most-one-minute fallback,
  never extends on recovery, and still requires a configured high-coolant threshold.
- The existing captured driver, queue, transfer gate and uncertainty journal are
  reused. Fixed primary input reads retain `7E0` during jobs so a broadcast header
  cannot redirect a later control step. Stop skips input reads on its next worker
  turn. IO still has bounded deadlines; there is no extra task or control-time heap.
- Restored requires an accepted Return/inactive report followed by three fresh RPM
  observations spanning at least 500 ms, at/below 1,200 RPM and within 150 RPM above
  that operation's pre-Start baseline. Samples must follow the Return transition.
  Unknown/reboot/link loss never becomes Restored merely because RPM is low.

#### Read-only recovery of an uncertain idle operation

A durable idle marker can survive restarting both gauge and vehicle. Normal RPM
alone never clears it, but Unknown must not block the identity reads needed to
recover. The parked candidate reacquires its exact ECM calibration/CVN and current
link, then rate-limits recovery attempts to five seconds. Admission requires the
saved operation/revision/identity, fresh zero speed and ordinary-idle RPM. Coolant
and a new Start's duration eligibility do not gate this read-only check.

Recovery uses a distinct driver job: addressed adapter routing, the captured
`10 92` diagnostic-session entry with an exact positive reply, then `33 05` status.
The [parked reference](../evidence/jeep-actions-live-qualification.md#raised-idle)
recorded NRC 80 before session entry and inactive status after entry. Dev.72 omitted
this prerequisite, so session expiry/restart could leave recovery permanently Unknown.
The correction requires session setup on every recovery attempt; negative, wrong-ECU,
malformed or missing session replies cannot advance to status or verification.
Session setup is diagnostic state, not an actuator command. Recovery never sends
Start or Return, even on cancellation,
movement, timeout or an active response. It rebinds only the in-memory link; the
original durable marker remains unchanged. A positive inactive response begins
verification: one fresh normal-idle sample establishes a recovery baseline, then
three distinct later samples must span at least 500 ms, within 150 RPM above that
baseline and at/below 1,200 RPM. The whole proof must finish within five seconds
of the inactive response; otherwise retry read-only. Recovery identity stays age-bounded. Failed, malformed, active,
wrong-scope or stale evidence cannot clear the marker; persistence failure also
keeps Unknown. Completion logs retain the recovery result, error and NRC for the
next capture. No automatic actuator control or blanket journal reset is introduced.

This source path needs parked verification on the captured ECM after installation.
It does not establish that the reported vehicle incident had a particular timeout,
and does not promote the ordinary controller qualification mask. Normal Stop still
uses its original pre-Start baseline and prioritized Return path.

The pure policy is `jeep_idle_parked_test.c`; its only runtime adapter is inside
the build-switched blocks in `vehicle_commands_runtime.c`. Removal means disabling
the build option, removing those blocks/policy and the small UI duration/note branch.
There is no saved test-mode configuration, new wire opcode, public capability,
qualification-mask promotion or special App migration. Existing status flag 8
requires local gauge Start. The normal release path remains unchanged.

Native tests exercise the actual primary worker with a fixed fake adapter, including
five-minute expiry, thermal/fallback Stop, local-only admission, wrong identity,
restoration and unresolved writes. They are offline evidence. A signed installation
and the [parked checks](../development/jeep-action-qualification.md) remain necessary
before calling eGauge vehicle control demonstrated.

### Ordered action pages

The owner approved the [review 4 simplification](../design/gauge-ui-proposal/action-pages/proposal.html)
on 2026-10-27, replacing the earlier review 2 interaction. The frozen proposal remains
design evidence; this section owns the runtime contract. App/FW implementation does
not qualify live controller execution.

- Gauge > Action pages owns configuration. Each remembered gauge has an optional
  action override, including order and independent bounded Air up/Winch durations.
  Null inherits the vehicle's legacy gesture; an empty override turns the gesture
  off, while a vehicle-actions binding with zero pages opens the setup state.
  Reassignment to another vehicle clears the override. Existing page-jump bindings
  retain their meaning. Profiles use schema 18 (schema 17 migrates with unchanged action semantics); gauge associations use schema 2.
- The App saves overrides and vehicle pressure calibration together through
  `LocalSetupTransactions`. It offers typed compatible actions, edit, reorder and
  remove with Undo. Compact rows open an editor with duration, the shared coolant
  limit and optional pressure estimation. Missing protection has one actionable warning;
  the estimate remains explicitly an estimate. Active/unresolved controls lock configuration. Save on phone
  never claims installation; the existing review/send and exact running readback
  are still required. No saved configuration contains a Start intent. One-session
  time overrides remain in the existing command-confirmation flow.
- The gauge reuses one LVGL action face and the existing recognizer. One, two and
  three strokes show arrow geometry without growing the font set; the completed
  cue lasts 350 ms before entry. Returning permits immediate re-entry to reach Stop;
  the legacy page-jump cooldown is unchanged. Left/right
  browses; Down returns to readings and the top chevron cancels a confirmation first.
  Holds do not pair inside actions. Ordinary idle Start requires a continuous 900 ms
  hold on the ready control, replacing the repeated confirmation screen. Releasing
  early, leaving its hit region, moving over 12 logical pixels, attention/context loss,
  or a change in phase, flags, preset, reason or revision/link/boot cancels it. A changed
  and restored status still cancels the hold. Completion consumes the entire touch,
  so its release cannot Stop or issue a second Start. Removing Park/brake display copy
  does not remove any command guard or qualify the owner-supervised parked candidate.
- Missing coolant retains Continue followed by a fresh hold, with No coolant visible
  at commitment and its frozen one-minute maximum. State/context drift cancels that
  confirmation; recovery cannot enlarge a queued request. ABS/TCS retains its named
  Disable confirmation and Restart to restore guidance. Stop is one tap, including
  from the sibling idle page, and always targets the actual running operation.
- Starting, Return/verification and reconnection use green, red and yellow rotating
  edge arcs. Starting and Return have no decorative play triangle or stop square;
  reconnection retains its link symbol. They indicate pending work, not
  percentage complete. Reuse the existing UI timer and LVGL arc; no animation task,
  framebuffer or extra font set. Render timing, cancel/retry policy and command jobs
  remain independent. Unknown keeps Check idle / Status unknown until reconciliation.
  Verified restoration returns directly to Ready with no Done/success screen; known
  high coolant keeps Start blocked. Worker validation remains authoritative.
- Readings remain selected behind the branch; remote selection and page cycling
  wait until it exits. Closing restarts the cycle quiet period and renders the
  cached samples through their existing freshness rules. Thirty idle seconds
  returns to readings; active/uncertain/restart-required controls never time out.
- Critical attention temporarily owns the display and cancels confirmation;
  acknowledgement restores the action branch without resuming a command. Pairing,
  maintenance, assignment/context loss cancel the branch. CEL remains in the upper
  band; one supporting line sits between the value and primary control. The shared
  critical overlay preserves its measured condition and reuses the same red arc during
  protective Return, then restores the action branch without restarting. The tested Jeep's ABS/TCS
  Disabled state offers the captured Return before ignition-cycle guidance; acknowledgement is not restoration proof.
- An unresolved operation is appended to the displayed branch if its configured
  page is absent. This is an ephemeral recovery view, never a configuration write.
  Navigation cannot conceal Stop merely because the phone started the operation.
- One atomic word per kind carries phase, flags, remaining time, reason and preset.
  A local-only snapshot adds high-coolant/reconnection presentation bits and an epoch
  invalidates an in-progress hold across context changes. The BLE status bytes and
  public capability/qualification flags are unchanged.
  One reusable widget tree serves all three definitions; no task, frame buffer,
  font, per-page history or action-specific transport is introduced. Unchanged text
  and layers are reused instead of reallocating/redrawing on each poll. Labels use
  production font metrics and circular bounds; controls are at least 44 pixels.

The [wire extension](../protocol/experimental-firmware-transfers.md#shared-vehicle-commands-v1)
owns validation and compatibility. Offline fixtures cover typed migration,
per-gauge projection, rejected definitions, navigation/cancellation and rendering.
Physical edge/touch, cycle timing, connected source continuity and command outcomes
still require the parked qualification below.

### Numeric idle target preparation

The existing parent-vehicle `IdlePreset.rpm` remains the sole optional numeric
request. For Air up it represents a target idle RPM, with deliberate accelerator
input intended to cancel the operation. For Winch it represents a minimum idle
floor, with normal accelerator control intended above the floor. These are desired
behaviors for future matched controller support, not capabilities of the current
fixed-setting driver. App polling alone cannot promise ECM pedal override or
bounded cancellation latency. No pedal threshold or throttle mapping is assumed.

The existing 400..3000 RPM bounds admit phone drafts only, not a qualified vehicle
range. A blank value retains the distinct captured fixed-setting operation. The
profile schema and serialized fields are unchanged, so legacy null/numeric presets
and four-tire timer calibration retain their existing meaning and data. Action-page
editing uses the same field and preserves it when editing time/calibration. Air up
labels it Target RPM; Winch labels it Minimum RPM. Expanded editing explains that
numeric values are saved on the phone and require controller verification.

Data flows through the existing parent profile, local setup transaction and gauge
assignment projection. App Start rejects numeric presets through the shared command
protocol. Gauge configuration projection also rejects any enabled Air up/Winch page
whose corresponding preset has a numeric value, including per-gauge overrides.
Transfer-only rejection is separate from local draft validation, so unrelated
alert additions/edits remain saveable while a numeric target is prepared.
This prevents old fixed-only firmware from receiving an action after its numeric
intent was stripped. Unused numeric drafts do not block unrelated gauge setup.
Saving a phone draft does not alter an already acknowledged gauge configuration;
that gauge retains its previous fixed setting. Clearing the numeric field explicitly
returns the preset to that fixed operation. No new command, capability or firmware
wire field is added, and no target is inferred from the approximately 1,700 RPM
reference observation.

The [RPM encoding analysis](../vehicles/jeep/jk-handler-analysis.md) is a research
lead only. Controller acceptance, range/step, settled RPM tracking, Air up pedal
cancellation and Winch throttle-above-floor behavior each require separate parked
evidence before a versioned capability can admit numeric requests. Timer, Stop,
coolant/protection, ownership, uncertainty and no-replay rules remain mandatory for
both intents. Unit coverage checks strict bounds, existing-schema round trips and
projection/Start rejection. Managed fixtures cover numeric intent retained through
timer edits, invalid floors, explicit fixed-setting selection and disabled numeric
Start. Review compact and actual Android 200% font-scale dialog layout independently;
an outer Compose density provider does not establish dialog accessibility. Source
verification and pending accessibility evidence belong in current state and the
verification record. Website impact: this phone-only preparation creates no public
control claim or publication requirement.

### Next parked Jeep increment

Execution order, prepared capture commands and acceptance rows are maintained in
the [parked qualification runbook](../development/jeep-action-qualification.md).

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

The owner subsequently authorized an explicit best-effort brake candidate and
firmware/App installation. Its separate policy is recorded below; neither brake
path shares the idle driver implicitly or acquires general qualification from
that authorization or replay tests.

## Remaining qualification

Controller execution and its negative scenarios belong to **FEATURE-04** in the
[backlog](../backlog.md). Matching controller identity, complete commands, readback,
restoration and interruption evidence are required before enabling an action.
[Local fixture results](../evidence/profile-action-fixtures.md) and
[physical gesture observations](../evidence/profile-actions-rollout.md) are historical
records; [current state](../current-state.md) owns current qualification.

Parked fallback qualification must also cover a controlled missing coolant feed,
shortened countdown/Return, local restart, recovered-data deadline retention and
thermal Stop during fallback. Offline vectors do not qualify ECU behavior.

### Four-tire Air up timer

Air up may store a pressure-based timer preset on the parent vehicle. It covers
**all four tires connected together** with the owner's compressor and hose setup.
No default tire pressure, compressor flow rate or presumed tire size is supplied.

The App takes starting PSI, target PSI and one observed reference fill (starting
PSI, ending PSI, elapsed time). It estimates seconds as
`ceil((target - starting) * reference seconds / (reference end - reference start))`.
This linear approximation is a convenience, not pressure telemetry: compressor
flow, tire volume, temperature, leaks and pressure range can change the result.
An estimate outside the measured range is identified in the editor. The same
setup and independently checked tire pressures are required for useful calibration.

- The estimate and optional explicit time override persist with the vehicle's Air
  up preset. No values are read from the ECM, guessed from elapsed time or labelled
  "target reached". Completion means only that the timer expired.
- A manual override replaces the estimate for the saved session; clearing it restores
  calculation. The Start confirmation also accepts a one-session override without
  changing the saved preset, and cancelling sends nothing. Target must exceed starting pressure. Inputs use whole PSI, bounded
  0..150; a reference fill must have a positive pressure increase and 1..7,200 seconds.
  These are input bounds, not recommended tire pressures.
- Effective session duration must remain 10..600 seconds. An out-of-range estimate
  is never silently clamped or split into automatically restarting sessions; the
  owner must enter a shorter override and independently check pressure afterward.
- The ordinary fixed timer remains available. Saving a pressure preset resolves it
  to `IdlePreset.seconds`; the existing App request and gauge action-menu duration
  fields carry only that time. Saving marks an assigned menu unsent until normal
  configuration publication. No firmware allocation, polling or wire change is needed.
- Coolant protection, the one-minute missing-coolant fallback, explicit physical
  restart, Stop, vehicle guards and controller qualification remain authoritative.
  The estimate neither controls a compressor nor establishes measured pressure.

Offline acceptance covers calculation/rounding, invalid inputs, over-limit estimates,
explicit overrides, schema migration, parent-vehicle persistence, duration-only
wire encoding and the editor flow. Parked command qualification and a measured
four-tire calibration with the owner's actual equipment remain separate gates.

## Burnout Mode and brake actions

The 2026-10-29 owner request adds **Burnout Mode**, the captured fourth-button Line
Lock function, alongside **Disable ABS + TCS**. These are distinct controller
procedures, not separate ABS-only/TCS-only switches. The [parked reference](../evidence/jeep-actions-live-qualification.md#fourth-button)
records accepted transactions and uncertain physical semantics. [JScan's manual](https://jscan-docs.readthedocs.io/en/latest/general/user_manual.html#bottom-row-icons)
describes combined shutdown until an ignition cycle and Burnout as releasing rear
brakes while the pedal is held. Do not infer brake force, valve mapping, automatic
expiry or release from a positive diagnostic reply.

The App can explicitly add/order/remove a fourth `burnout` page. Existing menus
retain their three legacy defaults. Configuration/status negotiation uses `vc:1` + optional `bm:1`;
old gauges retain Idle/ABS polling and refuse sending unsupported Burnout drafts.
App dev.54/code54 adds this interface; `bt:1` separately identifies the explicit
owner-supervised brake candidate. Shared
configuration schema2/profile schema19 remain structurally unchanged; strict older
readers preserve unknown input rather than silently dropping the page.

On a qualified controller or the explicit owner candidate below, Burnout has one
physical **Hold to run** control. A tap
cannot activate it, and the phone can only request release. The existing 900ms hold
opens a run; touch release, movement outside the hit area, attention/context loss,
a 300ms stale UI heartbeat or a 500ms stale worker lease requests Return. Status
changes cannot turn that same touch into a second Start. A ten-second software
deadline also requests Return and cannot be extended by continued touch. It is a
release-initiation deadline, **not a guaranteed hydraulic duration**: queued work,
bounded IO, route/session setup, link loss and controller behavior affect actual
release latency. Controller-side expiry and physical release timing need evidence
for full qualification. The owner candidate accepts these unresolved limits
explicitly. No control task or heap allocation is added.

Configuration/OTA/Wi-Fi maintenance waits while a recorded Burnout outcome remains
Unknown, so it cannot retire a still-needed release path. Ordinary malformed-journal
repair behavior is unchanged. Idle/combined shutdown cannot start while Burnout is active or unresolved; Burnout
cannot overlap any other active/unresolved control. One primary-worker lease and
the existing bounded queue own all adapter IO. Lost Start replies remain Unknown;
a physical release latches at most one same-scope Return attempt, never replays
Start. A failed Return is not retried automatically. Explicit Stop after a reboot
or reconnect requires the same scoped identity (captured ECM identity in owner
mode, independently verified controller identity in qualified mode), durable
revision/operation and current boot/link. It remains Verifying after an accepted
Return until independent, fresh release evidence clears the durable marker, or
the explicit owner-mode manual confirmation below records a human observation.
A known durable same-operation marker permits protective Return without another
flash commit; failed persistence never clears uncertainty. ABS/TCS instead retains its existing Restart required semantics and independent
reset/state proof. Reboots never resume either command or manufacture restoration.

The existing NVS `intent_v1` key accepts its original 28-byte version1 record.
Only a live Burnout uncertainty bit needs version2/40 bytes; older firmware refuses
that larger record rather than losing the uncertainty. Once no Burnout uncertainty
remains, saves use version1 again so ordinary idle/ABS rollback stays compatible.
Malformed/newer journals remain retained and refuse execution.

### Reviewing a different action's retained state

A ready action can be blocked by another kind's Active, Verifying, Unknown or
Restart-required state. Its face must name that action and its status, with a
**Review** button leading directly to the existing action page. Review is local
navigation; it must not Start, Stop, acknowledge or clear any operation. A state
change during that tap must not turn it into Start. Do not add an unrecorded
Burnout page to represent a generic journal error.

The ready Burnout instruction **Hold brake pedal** appears only when its ready
control is admissible, never alongside a different action's blocker. The existing
owner recovery control remains independent when more than one record needs review;
requiring every other Unknown record to clear first would create a recovery cycle.
It shows **Connect Jeep** without matched ECM context, **Park and idle** while the
stationary confirmation input is unmet, and the existing physical confirmation
only when its scoped runtime gate passes. This changes presentation, not admission.
The local stationary brake-readiness bit is interpreted only in its brake context.
It must not trigger an idle coolant confirmation, timer clamp or **No coolant**
warning. A ready Burnout control needs one 900 ms hold, not a hidden second hold;
without stationary readiness, show **Park and idle** rather than inviting Start.
Reboot retains unresolved control records; Unknown is missing outcome proof, not
proof that the physical action is running. Downward navigation returns to readings
without acknowledging the retained state.

### Front Lock selection: captured JK versus JL/JT

The owner specified **Front Lock** as the desired Burnout mode on 2026-10-29.
An offline review of the same JScan build used for the capture resolves the
setting's software scope, not the vehicle's hydraulic behavior. Assembly provenance
is retained in the [handler analysis](../vehicles/jeep/jk-handler-analysis.md#detailed-offline-control-flow-review-2026-10-19):
`obd4u.cleversoftware.net.Common.dll`, SHA-256
`aa3b31566a772781149a0c4b65d12e3d5180da6ebba866829739237bc30289df`,
JScan build `v21.09.2026.12:45`.

| JScan path in that build | Mode selection | Evidence and consequence |
| --- | --- | --- |
| `PrepareLiveDataForJK` → `AddABSBurnoutMode` | No `BurnOutType` read | Fixed rear-outlet-valve procedure at `784`/`785`; Start/Return match the captured requests already used by eGauge. Changing the global selector does not select another command in this JK handler. |
| `PrepareLiveDataForJLJT` → `AddABSPNBurnoutMode` | `LockFront=0`, `UnlockRear=1` | Separate ABSPN implementation at `747`/`4C7`. Its Begin/Keep methods read `BurnOutType`; the selector changes this path. These are not interchangeable JK control bytes. |

The getter-reference review also finds the settings UI and Burnout description;
those labels alone do not establish that the JK actuator uses the selection.
Earlier capture notes recorded `Lock front`, but did not independently bind that
selection to the accepted on/off sequence. Preserve those observations as history;
do not claim selection-specific Front Lock qualification from them.

A new full capture solely to change this selector is not justified for the
inspected JK build. First compare the installed function with the owner's intended
behavior. Rear-brake release with pedal-applied front braking must not be silently
equated with independently holding front pressure. If behavior differs, or a newer
JScan/another actual controller path honors the selector on this Jeep, capture only
that labelled Front Lock Start/Return sequence with exact app/profile/controller
identity and observed release behavior. The installed dev.76 request and enabled
owner mode remain unchanged by this research; no alternate valve bytes are inferred.

### Explicit owner-supervised brake candidate

`CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST` defaults off and is independent of
`CONFIG_EGAUGE_JEEP_IDLE_PARKED_TEST`. The owner-authorized build enables both
**Disable ABS + TCS** and **Burnout Mode** only for the captured Jeep's matching
ECM CalID/CVN through the existing idle identity policy. That match identifies the
captured vehicle, not the ABS controller. Ordinary profile qualification masks
remain zero and public BLE capability flags stay disabled.
The App allows offline setup of owner-supervised brake action pages from `bt:1` capability without a live ECM connection; execution still requires the physical gauge and current vehicle guards.

Start is physical-gauge-only with fresh zero speed and normal idle (400..1200 RPM).
After Start, fresh running RPM/speed remain required; motion or throttle alone no
longer cancels the supervised run. Burnout retains hold-to-run and its ten-second
release-initiation bound. Neither this policy nor manual pedal confirmation
establishes automatic brake/Park sensing, brake force, controller-side expiry or
independent restoration proof.

For Burnout, complete or attempt Return, then stop at normal idle and inspect the
actual brake state. For ABS/TCS, first use **Restore** to send captured `32 A4`,
then perform the ignition reset and check that the warnings and brakes are normal.
Ignition alone is not established restoration: the owner reported it remained
disabled until JScan toggled the function and the Jeep restarted. Only then use
the physical **Hold to confirm** control while
stationary at normal idle. Confirmation sends no vehicle control. It records
human provenance in command history/logs and clears that operation's uncertainty;
it is not decoded controller proof. Unchecked or unexpected behavior stays
unresolved. The [operator procedure](../development/jeep-action-qualification.md#brake-actions-follow-up)
owns the test sequence and remaining controller/release qualification.
Native/Compose fixtures cover protocol, routing, hold loss, uncertainty and legacy
menus; vehicle braking behavior still requires physical observation.

Website impact: current public ABS/TCS-disabled language remains accurate. Queue
Burnout screenshots/status for the next eligible daily batch after a qualified
availability change; do not market a working control from offline fixtures.


### ABS Return receipt and paused Burnout menus

The ABS/TCS restore sequence is captured Return, ignition reset, then a physical
normal-state check. Active or uncertain ABS presents **Restore**; Unknown accepts
only an explicit same-operation Return against the retained vehicle identity and
configuration. A new link/boot may rebind that Return, never Start. There is no
automatic control replay. A positive `72 A4` acknowledgement retains uncertainty
and writes a version-3 Return receipt before offering reset confirmation.

This receipt uses the existing 40-byte NVS envelope: version 3, byte 2 = 1 only
with ABS bit 1 still pending. It survives gauge power loss with the ignition,
returns to Restart required and does not resend Return. It proves only request
acceptance, never actual ABS/TCS operation. Failed receipt storage stays Unknown.
The receipt clears only with independently verified restoration or the existing
explicit owner reset confirmation. Versions 1/2 still decode without invented
receipts. Older firmware rejects version 3, preserving the record; do not downgrade
while this receipt is pending. Normal saves return to legacy format once resolved.
BLE control/status layouts and actuator bytes are unchanged.

Burnout/Line Lock is paused at the owner's request. App menu decoding/editing and
firmware runtime configuration omit its page, preserving remaining order, timers
and gestures. No new normal-menu Start is exposed. The typed wire, driver and
pending-record recovery remain for older configurations and uncertain releases;
a pending record may still surface its recovery face. This is compatibility, not
an available normal action. Existing raw gauge configuration is not rewritten or
its uncertainty erased as a side effect; a subsequent permitted App save removes
the old menu entry. Front-lock qualification remains deferred.
