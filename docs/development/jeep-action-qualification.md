# Parked Jeep action qualification

This is the next-session runbook for Air up, Winch and combined ABS/TCS.
[Current state](../current-state.md) owns the installed baseline;
[vehicle actions](../architecture/vehicle-actions.md#shared-command-owner) owns
command behavior. Use the existing workers, typed intents and shared alerts.
Do not add a second command transport or enable a mask from a reference capture.

## Readiness and the first trip

Use the installed baseline in [current state](../current-state.md) and its linked
indoor evidence. The signed App/Wi-Fi installation, protected readbacks, saved setup
and physical action navigation are checked. No unresolved command journal was found.
The next session can combine fixed read-only input capture with supervised idle
Start/Stop; automatic Park/brake qualification and ABS/TCS remain separate.

### One-visit idle test candidate

The owner approved a temporary local-confirmation policy on 2026-10-26 so the
missing automatic Park/brake mapping need not force a second Jeep visit. The
[candidate contract](../architecture/vehicle-actions.md#owner-supervised-parked-idle-candidate)
owns exact identity, speed/RPM, coolant, timer and restoration limits. This is a
separate build, now installed with indoor readiness checks passed. ABS/TCS stays disabled.

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
read-only labels separately. Do not enable ABS/TCS or remove Unknown markers to
get another attempt. If this candidate fails, preserve the trace and use the
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

Use the separately matched controller definition after idle acceptance. Keep the
Jeep parked, capture baseline module state/dash, then one explicit Disable.
Record the accepted reply and owner-observed warnings independently. Return can
leave warnings active; the captured Jeep required a full ignition cycle. Keep
Restart required until actual reset/state proof, then verify both warnings clear.
Do not run road/brake-performance tests or claim an acknowledgment means restoration.
Line Lock and ABS-only remain outside scope.

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
actual countdown at the next opportunity. The display fix is not installed yet.
Website impact: no new public qualification claim or publication is warranted.
