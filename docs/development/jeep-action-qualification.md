# Parked Jeep action qualification

This is the next-session runbook for Air up, Winch and the explicit owner-supervised
ABS/TCS and Burnout candidate.
[Current state](../current-state.md) owns the installed baseline;
[vehicle actions](../architecture/vehicle-actions.md#shared-command-owner) owns
command behavior. Use the existing workers, typed intents and shared alerts.
Do not add a second command transport or enable a mask from a reference capture.

## Readiness and the first trip

Use the installed baseline in [current state](../current-state.md) and its linked
indoor evidence. The signed App/Wi-Fi installation, protected readbacks, saved setup
and physical action navigation are checked. The latest indoor delivery retains an Unknown idle journal; resolve it using fresh ECM evidence before another Start.
The next session can combine fixed read-only input capture with supervised idle
Start/Stop; full automatic Park/brake and brake-controller qualification remain
separate from the owner-supervised exceptions.

### One-visit idle test candidate

The owner approved a temporary local-confirmation policy on 2026-10-26 so the
missing automatic Park/brake mapping need not force a second Jeep visit. The
[candidate contract](../architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate)
owns exact identity, speed/RPM, coolant, timer and restoration limits. This is a
separate build, now installed with indoor readiness checks passed. Its idle switch
alone does not enable brake actions; the independent owner brake candidate is
described [below](#brake-actions-follow-up).

Before leaving, check the installed identity and indoor acceptance in current state.
Future candidate replacements follow the [release procedure](firmware-release-runbook.md)
and require their own authorization. No adapter indoors means no Start. Keep Air up
at **5:00** and Winch at **1:00**. Preserve the dashboard, bindings and four CEL filters.

At the Jeep, use one session for the fixed phase-1 capture and idle checks below.
Keep the gauge off while the Mac owns the adapter, then release the Mac connection
before powering the gauge. Start the eGauge test in Park with parking brake set
and owner at the ignition. On the first Air up attempt use Stop within about five
seconds of accepted raised idle, verify ordinary RPM, then repeat for Winch. Only
after successful Stop/restoration check the five-minute Air up timer. If coolant
is missing/stale, the displayed duration is limited to one minute; fresh coolant
does not shorten Air up. Capture device logs, owner observations and the original
read-only labels separately. Keep brake actions out of this idle-only sequence
and never remove Unknown markers merely to get another attempt. If this candidate fails, preserve the trace and use the
existing Stop/engine-off cleanup; no additional trip is required just to gather inputs.

Bring the Mac, unlocked Pixel, gauge, both Vgates and USB data cables. Work parked
outdoors with parking brake set. No compressor, winch load, driving, Line Lock,
DTC clearing or persistent coding is needed for this qualification.

## Phase 1: fixed read-only primary inputs

1. Leave the original adapter in the ECM connector. Temporarily unplug the TCM
   adapter so the identical devices cannot be confused. Gauge off, JScan and other
   adapter clients closed. Use the Mac's physically identified ECM BLE identifier.
2. Start idling in Park with the brake held. Run the command below for
   `idle-P-brake`; then capture `idle-P-released` while staying in Park with the
   parking brake set. We operate the Mac; the owner changes only the named state.
3. If the owner is comfortable, hold the brake and capture `idle-N-brake`, then
   `idle-D-brake` without moving. Return to Park immediately afterward. Never
   change gear during raised-idle control. `ignition-P` is a separate optional
   engine-off comparison. Stop the capture if the vehicle cannot remain stationary.
4. Stop on a wrong calibration/CVN or responder. Do not edit the expected identity
   to get past the check. Failure/NO DATA is retained evidence, not a zero or a
   verified Park state. Confirm the adapter is released before opening JScan or
   powering the gauge. If cleanup fails, unplug/replug that adapter first.

```sh
.venv/bin/python tools/obd_capture.py scan
.venv/bin/python tools/obd_explore.py --source engine \
  --adapter OBSERVED_MAC_BLE_ID --jeep-action-state idle-P-brake
```

The new flag runs separately from all other probes. It verifies 7E8 on 11-bit
500 kbit/s CAN and the previously captured calibration/CVN before enhanced reads.
It records standard RPM/speed/coolant, one module-identification read and three
passes over four fixed read candidates, at most 22 requests after discovery.
Unsupported/negative reads are not repeated. A whole-request deadline covers
stalled writes; discovery/probing has a 90-second budget, then up to five bounded
cleanup requests. No diagnostic-session entry, tester present, actuator command,
security access, arbitrary request or VIN read is exposed.

[Candidate provenance](../../data/vehicle-definitions/vehicles/jeep/command-guard-candidates.json)
records the local JScan resource and its hash. Variant 08 / diagnostic version
75 suggests `PCM_08_117` **only if** 75 is hexadecimal. Record module identification
and verify that selection; other variants use different bit positions. Preserve
raw replies and owner labels. The tool does not interpret candidate bits or
promote a runtime guard. Read latency through the Mac is not ESP32 timing proof.

### Required decisions after phase 1

- Select a primary-vehicle Park/Neutral and brake definition from labelled positive
  and negative states. If the swapped ECM never sees usable transmission state,
  investigate a validated associated-TCM input or physical input; do not fabricate
  primary evidence or silently drop the guard.
- Match current ECM identity, not the vehicle name or selected adapter. Define
  link/revision/identity fencing, validity and the existing two-second freshness
  budget. Known moving, missing, stale and ambiguous remain distinct.
- Implement the evidence adapter on the primary worker. Preserve independent child
  polling, bounded IO/cleanup, the shared transfer gate and zero masks until the
  applicable definition and test conditions are reviewed.
- Resolve same-session post-Return proof and settled RPM. A later Mac read after
  reopening JScan's session cannot prove the driver's immediate Return sequence.
- ABS/TCS needs its own controller identity and reset/state evidence. An accepted
  Return or green JScan button never proves restored braking/stability functions.

## Phase 2: scoped gauge idle candidate

The owner-supervised exception above permits only the idle tests in this phase
before automatic Park/brake mapping. The following requirements apply to promoting
the fully automatic vehicle definition; neither test mode nor its fixtures do that.

Only after those input mappings and the runtime adapter are tested, build a signed
candidate for the exact controller. Publication/installation follows the
[release runbook](firmware-release-runbook.md) and requires owner authorization.
Back up the complete phone/gauge setup and compare semantic fields before a send;
the prior phone/gauge filter divergence must not recur.

| Order | Parked observation | Acceptance |
| --- | --- | --- |
| Baseline | Fresh identity, guards, coolant limit, ordinary RPM; no control active | Exact expected controller, coherent revision/link, valid guard age |
| Air up | Local named confirmation; brief Start then immediate Stop | Accepted report plus physical raised idle; immediate same-session Return report and settled ordinary RPM |
| Uncertain reboot recovery | Preserve a controlled unresolved marker; restart gauge/vehicle, retain normal idle and live primary RPM | Exact identity, read-only inactive report, three later settled samples, durable marker cleared; capture outgoing trace proving captured session entry/acknowledgement precedes status and no Start/Return is sent. Active/negative/missing report must remain Unknown without a control replay. |
| Winch | Repeat Start/Stop using the same fixed-idle driver | Independent preset/countdown; no duplicate Start or arbitrary RPM claim |
| Timer | Short bounded duration | One automatic Return, settled RPM, no restart without new confirmation |
| Coolant | Temporary threshold crossed during ordinary warm-up, with owner present | Return latency recorded without deliberately overheating; original alert restored |
| Stale coolant | Controlled missing-data fixture after ordinary Stop works | At most one-minute fallback, recovery cannot extend deadline, local restart required |
| Recovery | One at a time: phone loss, then controlled source loss/reset | Known Stop first; never replay uncertain writes or clear a marker without fresh proof |
| Child continuity | Both original adapters reconnected | TCM telemetry stays independent; ECM cleanup restores ordinary polling |

Record exact image, App source, config hash/revision, command replies, monotonic
latencies, heap/largest block, queue drops, stack minima and owner observations.
Air-up PSI estimates require a later measured four-tire/compressor calibration;
a timer is not pressure sensing. No loaded air-up/winch test is needed here.

## Phase 3: combined ABS/TCS

For the explicit owner candidate, follow [the brake procedure](#brake-actions-follow-up).
For full controller qualification, use the separately matched definition after
idle acceptance. Keep the
Jeep parked, capture baseline module state/dash, then one explicit Disable.
Record the accepted reply and owner-observed warnings independently. Return can
leave warnings active; the captured Jeep required a full ignition cycle. Keep
Restart required until actual reset/state proof, then verify both warnings clear.
Do not run road/brake-performance tests or claim an acknowledgment means restoration.
ABS-only remains outside scope. Burnout Mode has a separate gated follow-up below.

## Stop and cleanup

If idle persists, behavior is unexpected or outcome is uncertain: remain parked,
use the normal Stop if available, and switch the engine off if normal idle does
not return. Do not retry Start. Preserve the unresolved marker and raw evidence.
Never erase bonds/configuration to recover a control test.

Finish in Park at ordinary idle or engine off, with ABS/TCS restored and both
warning lights cleared. Restore original coolant thresholds, phone monitoring and
wake settings; retain the three action pages, dashboard, bindings and four CEL
filters. Raw recordings stay under ignored `artifacts/vehicle-captures/`.

## 2026-10-27 Air up owner observation

The owner reported Air up worked well on the installed candidate, but the active
page displayed “Coolant unavailable.” Source inspection found that the active
page tested the local-gauge Start requirement (status flag bit 3), which the
parked candidate always sets, instead of coolant unavailable (bit 4). The UI fix
uses bit 4; it changes no command admission, temperature protection or timer.
Production-renderer cases cover fresh coolant with local-only Start, missing
coolant, and recovered coolant with a retained fallback deadline.

This observation does not establish the run duration, coolant freshness, thermal
Stop or complete restoration/recovery behavior. The owner confirmed no coolant page is configured. Coolant protection polls the
primary ECM independently of display pages; a coolant page is not required. The
runtime replay now explicitly uses an RPM-only page and still checks fresh coolant,
five-minute admission, thermal Stop and missing-coolant fallback. Confirm the
actual countdown at the next opportunity. The display fix is installed in signed dev.63 through App/Wi-Fi; see the
[rollout evidence](../evidence/action-pages-indoor.md#2026-10-27-dev63-coolant-status-correction).
The owner confirmed physical dev.63 normal pages, swipes and action-page entry.
Vehicle coolant/countdown and remaining protection/recovery checks remain.
Website impact: no new public qualification claim or publication is warranted.

## Read-only gauge diagnostic snapshot

`DiagnosticReadbackJourneyTest` requires explicit `allowDiagnosticReadback=true`
and may pin `expectedFirmwareVersion` / `expectedFirmwareElf`. Stop phone alert
monitoring first, then run only this class on the authorized paired phone. It saves
exact running setup and protected ECM/TCM diagnostic, adapter and command state in
the App's private external `diagnostic-readback/` output. Unknown and stale fields
are retained, not asserted fresh. These are gauge-cache reads, not forced ECU
acquisition; retain original ages, session and revision when attributing an icon.
No clearing, control, filter edit or configuration write is performed. Treat the
output as private vehicle evidence and restore monitoring/wake preferences afterward.

## Next-session configurable idle and pedal investigation

Prepared offline on 2026-10-28. This is a qualification plan, not permission to
transmit a new actuator value. The current driver supports one captured Start word
(`06 80`, observed around 1,700 RPM); it does not establish a general RPM encoding.
The [reference](../evidence/jeep-high-idle-reference.md) owns captured bytes and limits.

The App already saves an optional Air up target and Winch minimum RPM as phone-only
drafts, preserving values during timer edits. A numeric draft cannot Start through
the App or be projected as a fixed-target action page; unrelated valid local alert
edits remain available. Blank RPM selects the captured legacy operation. The gauge
keeps its acknowledged fixed operation until a supported setup is applied. A saved
phone value is not an installed ECU target and has no new wire representation.

Desired product behavior: independently validated absolute Air up target and Winch
minimum floor, retaining separate timers/coolant protection. Winch should permit
pedal input above its floor and return to it on release only if the matched ECM
supports that behavior. Keep local Start, direct Stop, uncertainty/recovery and
coolant ownership. Never label an arbitrary target while issuing the fixed word.
The [static encoding research](../vehicles/jeep/jk-handler-analysis.md) is a lead,
not permission to substitute untested bytes into the captured live command.

Before leaving, preserve current setup, filters and exact installed build, prepare
a timestamped trace and confirm the primary ECM identity. Bring Mac, Pixel, gauge
and both adapters. Use one connection owner at a time. Stay parked in Park with
parking brake set, engine at normal idle and no compressor/winch load for this
first capability session. Keep source-level and owner observations separate.

| Order | Test | Decision/evidence |
| --- | --- | --- |
| 1 | Resolve retained Unknown through the installed read-only recovery | Positive inactive report and settled RPM; no Start replay or manual marker deletion |
| 2 | Capture a known fixed-target JScan Start/Stop again | Exact ECM, raw command/report, actual RPM and normal restoration establish the reference |
| 3 | Inspect the applicable JScan target-setting path | Capture a documented selectable setting if one exists. If unavailable, do not guess parameter bytes or scan arbitrary RPM values |
| 4 | Brief gentle pedal input during fixed-target idle, then pedal release | Record requested routine state, actual RPM, speed, guard reason and whether the ECU relinquishes/retains idle. The current parked runtime stops above 2,500 RPM, which must be distinguished from ECU cancellation |
| 5 | Repeat as separate Air up and Winch observations | Determine whether mode-specific pedal behavior can be implemented or the ECU uses one common cancellation behavior |
| 6 | Only with an established target definition, validate a conservative bounded second setting | Exact encoded target, acceptance/report, measured RPM, allowed range/steps and explicit Stop. Stop on rejection; no blind retry |
| 7 | Return and verify ordinary idle; restore previous setup | Preserve timestamps, guard results, source loss and negative responses; reconnect child TCM and confirm independent readings |

Acceptance before adjustable targets ship: validated encoding/range/steps for the
exact ECM; target versus actual distinction; unsupported-controller behavior;
parameter-boundary replay tests; fresh-input and thermal stops; bounded timer;
explicit Stop/restoration; no automatic Start on pedal release or reconnect.
Winch may tolerate pedal input only where the controller and approved guard policy
support it. Never simply raise/remove the current 2,500 RPM guard to mask a stop.
If the ECU cancels its routine on throttle, report that limitation and design a
clear deliberate Resume rather than silently reasserting high idle.

After capability proof, the existing App draft editors can offer executable targets
validated to the controller's supported range through the existing preset/command
path. Until then the editable numbers remain phone-only preparation.
The gauge keeps the compact ready/running/Stop layout. No extra confirmation page,
PID page requirement, command transport or notification framework is proposed.

In the same session, record fast-PID arrival timestamps and foreground deadline
counters while tapping/cycling RPM, changing to a cached page and running both
adapters. Compare stable, rising/falling and rapid-reversal readings with easing;
check that categorical gear and critical attention remain immediate, source loss
clears only its own readings, and a valid cached value paints the first frame.
RPM text retains its separate bounded cadence/quantization. Measure source cadence
separately from presentation settling: the existing 250 ms visible fast-class target
is not an adapter throughput guarantee. A new measured analog target settles over
the 150 ms window plus its next ordinary render frame; task stalls and missed
samples must remain visible in timing evidence.

## Brake actions follow-up

The owner authorized an enabled best-effort candidate and firmware/App installation
on 2026-10-29. Use only the explicit
`CONFIG_EGAUGE_JEEP_BRAKE_ACTIONS_OWNER_TEST` build on the captured Jeep with matching
ECM CalID/CVN. This enables both actions independently of the parked-idle switch;
it does not establish ABS-controller identity or full qualification. Verify the
installed signed image and saved setup before operating. Ordinary/public
qualification masks and public BLE capability flags remain disabled.

1. Connect the matched Jeep and wait for fresh speed/RPM. Start stationary at
   normal idle (400..1200 RPM); admission requires zero speed. The operator checks
   the actual brake state and surroundings. Use the physical gauge to Start;
   the phone cannot Start either action. Keep other commands inactive.
2. **Disable ABS + TCS:** use the explicit local confirmation. Record replies and
   observed warnings separately. Restore by the captured ignition-cycle procedure,
   then check the warnings and actual brakes. At stationary normal idle use
   **Hold to confirm** only after the reset is checked. No additional Return is
   required after the ignition reset; confirmation sends no vehicle control.
   A gauge reboot does not acknowledge this persisted record. If another action
   shows it as a blocker, use **Review** (or swipe to **ABS + TCS** on older images).
   With the matched ECM, parked normal idle and the reset personally checked,
   use the existing **Hold to confirm** control. Indoors, **Connect Jeep** means
   that live confirmation context is absent; do not clear NVS to bypass it.
3. **Burnout:** the requested mode is **Front Lock**. Read the
   [JK/JL selector finding](../architecture/vehicle-actions.md#front-lock-selection-captured-jk-versus-jljt)
   first: the captured JK handler ignores that selector, so another full capture
   solely to change it is unnecessary in the inspected build. Compare observed
   behavior with the required front-hold behavior; if it differs, obtain one short,
   labelled JScan Front Lock Start/Return capture for the actual app/profile/controller.
   Do not substitute the JL/JT route or infer valve bytes. For the existing
   supervised function, hold the brake pedal and use **Hold to run**. Releasing the control,
   leaving its touch area or losing UI/worker ownership requests Return; the
   ten-second deadline also initiates release. Motion/throttle after Start is
   allowed while speed/RPM remain fresh. The timer does not guarantee hydraulic
   release or a controller-side timeout. Stop if behavior is unexpected.
4. After Burnout Return completes or is attempted, stop at normal idle and check
   actual brakes. Use **Hold to confirm** only when brakes are normal. Unknown
   Burnout requires a recorded release attempt before confirmation is admitted.
   Keep uncertainty if the observation is missing or doubtful. Never repeat Start
   to clear a warning or recover a lost reply.
5. Retain logs, operation/revision, exact image/App identities, raw replies and
   owner observations. Manual confirmation records human provenance and clears
   that operation's marker; it is not automatic restoration proof. Finish parked
   with normal brakes and warnings cleared. Preserve setup and unrelated data.

Full qualification still requires read-only identification of the captured
front-controller route620/504 and ABS route784/785, exact controller/software,
labelled pedal/state inputs and independent restoration/reset readback. The static
selector scope is resolved for the captured JScan build; requested physical
Front Lock behavior remains unverified. Measure release after
ordinary touch-up, UI/link loss, lost replies and reboot, including controller-side
expiry. ECM identity, accepted replies and dash lamps do not prove module identity
or brake force. Use an appropriate controlled setup for physical holding/release
qualification. Integrate proven evidence through the shared context owner; retain
local-only Start, durable uncertainty, independent TCM telemetry and signed-image
provenance. No arbitrary valve command, automatic retry or restart is authorized.
