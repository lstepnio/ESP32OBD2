# ADR-013: Profile actions and gauge gesture triggers

**Status:** Accepted for local page actions and the offline idle foundation; vehicle execution proposed
**Date:** 2026-10-07
**High-idle updated:** 2026-10-19; offline foundation implemented, vehicle execution disabled
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
scope is temporary engine idle for air-up and winching; ABS/ESC and persistent PCM
adaptations remain outside this increment. The command does not operate the winch
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

- `temporary_idle.c` is a pure fixed-size policy with no IO, task or allocation.
  It emits Start/Refresh/Release intents only for an independently qualified
  definition and matching fresh context. Tests connect the existing page-action
  recognizer to the policy without changing its production page-jump path.
- Duplicate/old Starts, overlapping operations, retired completion tickets,
  changed revision/link, missed deadlines, cancellation, expiry and timer wrap are
  covered. Unknown never becomes success implicitly. Explicit release retry needs
  definition-declared idempotence; independent release evidence permits reconciliation.
- `IdlePreset`/schema 14 store two phone-only drafts with no inferred target RPM.
  They use the coupled setup store, stay on the parent, survive gauge restoration,
  and do not enter configuration bytes or alter exact gauge comparisons.
- Car > Air up & winch configures RPM/duration and removal. The presenter has
  countdown/Start/Stop states exercised only by fixture handlers; normal App state
  remains Unavailable and has no transport callbacks. This is not live telemetry.
- The [offline inner codec](../protocol/experimental-firmware-transfers.md#offline-temporary-idle-inner-codec)
  shares a C/Kotlin vector. It is not registered with BLE, advertised as a capability
  or connected to an adapter worker. No real or selectable fixture controller driver
  is installed. The policy object is not instantiated in the firmware runtime.

Remaining integration includes the qualified controller definition, protected
Start/Stop/status dispatch, source-worker scheduling, shared operation history and
physical gauge Quick actions menu. Those boundaries remain gated while the actual
PCM replies and release behavior are unknown. This delivers the reusable offline
parts without pretending the planned vehicle-control vertical slice is complete.

## Remaining qualification

Controller execution and its negative scenarios belong to **FEATURE-04** in the
[backlog](../backlog.md). Matching controller identity, complete commands, readback,
restoration and interruption evidence are required before enabling an action.
[Local fixture results](../evidence/profile-action-fixtures.md) and
[physical gesture observations](../evidence/profile-actions-rollout.md) are historical
records; [current state](../current-state.md) owns current qualification.
