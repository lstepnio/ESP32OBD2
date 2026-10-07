# ADR-013: Profile actions and gauge gesture triggers

**Status:** Accepted for the local page-action foundation; vehicle controls proposed
**Date:** 2026-10-07
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

Installed dev.40 handles clicks and holds, with clicks advancing pages and holds
opening pairing. Source dev.42 adds upward stroke recognition for the local page
action. Recognition is tested offline; physical gesture ergonomics remain unqualified.

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

Each request has a unique ID and bounded duplicate suppression. The state model is:

`Ready -> Confirming -> Executing -> Verifying -> Confirmed | Rejected | Unknown`

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

Source dev.42 adds `Car > Actions`, phone profile schema 7 (schemas 1..6 migrate
with no bindings), exact review/readback comparison and a single local `jumpPage`
action. Removing its target page removes that binding. Each saved parent/child
keeps its own draft; the combined payload prefixes child page identities and rejects
conflicting upward bindings. Configuration is sent to the selected gauge through
the existing atomic flow, not a second settings or persistence channel.

Development capability `cfg:4` extends schema-2 documents with optional `actions`;
see [development protocol](../protocol/experimental-firmware-transfers.md). Configs
without actions retain their existing bytes/shape. New Android blocks sending a
binding to older firmware. Older Android rejects the unfamiliar development version,
so candidate installation must first install App dev.35 (version code 35). Public `configWrite`, `ota`
and link-capacity claims stay unchanged; no vehicle action capability is advertised.

Firmware compiles only `jumpPage`, gives gesture recognition to LVGL and reuses
normal page selection/persistence. Input uses 80..800 ms strokes, at least 32 pixels
up, at most 24 pixels sideways, an upward-to-sideways ratio of at least 2, and rejects
reverse/side excursions. A successful sequence has a five-second cooldown. Progress
and completion appear on the gauge. Pairing/calibration/transfer context, page change
and orientation changes reset partial sequences. A recognized swipe suppresses its
trailing click; a moving upward stroke does not turn into a pairing hold. Touch
activity pauses automatic page cycling. This path sends no adapter commands.

Vehicle execution, named confirmation, live preconditions, controller result/state
readback and Stop/Restore remain unimplemented. They require a versioned bounded
request/result contract and a verified controller definition, rather than extending
`jumpPage` to carry arbitrary commands. The existing static research inputs are
insufficient to enable high idle or ABS on this Jeep.

## Action items and quick test plan

1. [x] Build profile configuration and an executable local page action, exercising
   gesture recognition and feedback before any vehicle writes. Host tests cover time boundaries,
   noisy/diagonal input, orientation, click suppression, holds/pairing, cooldown,
   conflicting bindings, overlays, config changes and reboot with a partial sequence.
2. [ ] Define typed controller actions and request/result vectors in the existing
   config/transport architecture. Test malformed parameters, unsupported definitions,
   wrong source/ECU, stale config/session, missing/disagreeing preconditions, duplicate
   requests, queue saturation and polling fairness with one or two sources.
3. [ ] Complete the petrol high-idle research procedure for the actual swapped PCM.
   Establish encoding, stop/refresh timing, response/readback and loss-of-power
   behavior before an owner-authorized stationary control test. Validate Stop,
   normal idle restoration, disconnect and uncertain outcomes against observation.
4. [ ] Qualify ABS/ESC enable/disable separately against the actual controller,
   including state readback, restoration and key-cycle behavior, in an explicitly
   authorized controlled off-road/bench session. Do not infer support from the
   static handler fragments or use a moving test to discover command meaning.
5. [ ] Measure gesture usability and command latency on the physical gauge. Verify
   no write is replayed after interruption, failed writes do not stall touch or
   polling, and changing vehicles/gauges cannot carry an armed action across contexts.
   Promote only definitions with complete evidence; preserve unverified candidates
   as research entries in Expert.

## Software verification, 2026-10-07

- 89 Android unit tests passed, including profile migration, action projection,
  exact readback, capability gating, invalid commands/parameters and child routing.
- Three isolated native Compose tests passed on the existing Television AVD with
  a forced 390 × 844 portrait viewport: save defaults/edits, older-firmware draft
  behavior and busy-state rejection. This is emulator evidence, not a Pixel test.
- 73 offline host tests passed; production config compiler/validator and pure
  gesture fixtures passed with AddressSanitizer/UndefinedBehaviorSanitizer.
- Debug APK/test APK, Android lint, ESP-IDF 5.4.1 build and contract/link checks passed.
- Emulator wake/display overrides were restored and the emulator was stopped.
  No physical device installation, BLE session or vehicle command occurred.

Native example of the action sheet with simulated profile data:

![Action sheet, simulated native emulator example](../design/profile-actions/action-sheet-example.png)

Physical gauge gesture recognition, touch/pairing regression, orientation, automatic
page cycling and restart persistence still need owner observations after an explicitly
qualified App/Wi-Fi candidate installation. Idle/ABS procedures still lack matching
controller identity, complete sequences, response semantics and restoration evidence.
