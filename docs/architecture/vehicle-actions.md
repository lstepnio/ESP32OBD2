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
  sending raw authenticated command bytes. ABS/TCS remains unavailable.
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
  retain their meaning. Profiles use schema 17; gauge associations use schema 2.
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
  Disabled state has ignition-cycle guidance and no misleading Restore control.
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
