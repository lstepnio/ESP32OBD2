# Additional TCM values: research and quick test plan

Researched 2026-10-06, America/Denver. This is a preparation plan, not a new
vehicle capture or a firmware release. Existing temperature and gear behavior
is recorded in [gear capture](tcm-gear-capture.md) and
[temperature verification](tcm-temperature-goal.md).

## Recommended reference model

Start with **2016 Dodge Challenger LA, 5.7 L, FCA ZF 8HP70** definitions.
The previous [identity investigation](jeep-direct-exploration-results.md)
associated the observed calibration 68274867AE with that application in FCA's
calibration catalog. This is a calibration-family lead, not proof of the donor
vehicle or an unmodified calibration. The installed external PCS TCM-2800 is
owner-confirmed; whether the diagnostic endpoint is original hardware, PCS, or a
bridged path remains unresolved.

FCA bulletin 21-026-16 independently identifies the 2016 Challenger, Charger and
300 with 5.7 L and 8HP70. It does not publish diagnostic signal definitions.
The bulletin was checked again for this report. The FCA catalog initially
opened, but subsequent row lookup and screenshot were blocked with HTTP 403;
the specific calibration association above comes from the prior repository
investigation, not a newly verified catalog row.

Use Challenger 2019/2021 author captures as a second reference for the requests
that already worked here. Charger, Grand Cherokee and RAM provide cross-checks.
Their shared identifiers are not independent proof of this Jeep's sensor meanings.
The stock 2010 Wrangler year should not select this swapped transmission's map.
BMW/Audi ZF maps also require their own validation: rusEFI documents different
Chrysler and BMW CAN dialects despite related transmission hardware.

## What the existing pattern actually supports

Observed diagnostic route: physical request **7E1**, response **7E9**, 11-bit
500 kbit/s CAN. Identity baseline: calibration **68274867AE**, CVN **8240DAE8**,
name **TCM - TransmisCtrl**. Verify these before comparing additional values.

| Value | Request | Jeep evidence | Next action |
| --- | --- | --- | --- |
| Temperature | `22 04FE` | Three-byte payload; first byte minus 40 gives 45 C. Owner verified LCD and loss/recovery. | Record all three bytes during warming; only byte zero has a candidate interpretation. |
| Current gear | `22 5503` | P=0D, R=0B, N=00, stationary D=01; combined LCD and recovery verified. | Record gears 2..8 during normal driving. |
| Target gear | `22 5504` | Same parked sequence as current gear. | Log beside current gear to test divergence during shifts. |
| Stored/pending/permanent faults | `03`, `07`, `0A` | Mac captured category-specific lists from 7E9. | Repeat, then complete source-aware app/gauge integration. |
| MIL status and reported DTC count | `01 01` | Not yet captured/qualified on this route. | Add a bounded supported-PID read; distinguish the ECU's report from the dash lamp. |
| ECU supply voltage | `01 42` | Earlier support map advertises this PID; no reference comparison recorded here. | Read only if 7E9 still advertises it; compare engine-off/idling values with a meter if available. |
| Engine RPM / vehicle speed | `01 0C` / `01 0D` | Earlier 7E9 support map advertises both. | Compare against dash; label as reported through TCM, not turbine/output-shaft speed. |
| Main cylinder pressure candidate | `22 5034` | Not tested on this Jeep. Published two-byte mapping exists, but its fixtures include negative results. | One fixed request, preserve raw bytes; qualify meaning and scaling before displaying pressure. |

The standard definitions for RPM and speed are explained by OBD Solutions.
Decode Mode 01 PID 01 using the applicable SAE J1979 definition: MIL bit and
reported count are separate from the fault lists and do not identify which
individual code requested the lamp. Keep this path separate from PCS logger
status and from a claim that the Jeep's actual dashboard lamp is illuminated.

### Pressure deserves special treatment

OBDb publishes `22 5034` at 7E1/7E9 with a big-endian 16-bit field and the
candidate conversion `raw / 10 - 40` bar. Its 2021 Challenger fixtures include
`0000 -> -40`, `004D -> -32.3` and `026C -> 22`. Charger and Grand Cherokee
fixtures also contain negative conversions. These fixtures establish parser
expectations, not measured hydraulic-pressure validation. Zero may be an invalid
or inactive value; the published scaling may also be wrong for our calibration.
Do not clamp negative values to zero, silently change scaling, or enable pressure
alerts from this definition. A nonnegative changing value still needs a matching
scan-tool/reference measurement before calling it actual line pressure. It could
instead be a commanded quantity or another signal.

### Values still lacking a matching public definition

Input/turbine shaft speed, output shaft speed, torque-converter slip and lockup,
actual versus commanded pressure, clutch states, torque, selector D, adaptation
values, and the complete OEM fault inventory remain research targets.

The reviewed OBDb 7E1 definitions do not provide usable matching requests for
those fields. ScanGauge's slip/pressure examples include other routes and diesel
applications; their existence does not qualify them for this calibration. PCS's
block diagram separates serial logging and CAN interfaces, with programmable
bridging. The laptop screenshot's channels cannot simply be assigned neighboring
55xx identifiers. Keep `04FE` bytes one and two unlabelled until compared with a
reference. Do not calculate converter slip from generic engine RPM and vehicle
speed: the required turbine-speed signal is missing.

## Fast vehicle session

Target **10 to 15 minutes parked**, excluding optional drive and a scan-tool
reference session. This is a scheduling estimate, not a measured test duration.

### 1. Establish the route, about two minutes

- Vgate in the separate TCM connector; gauge off and other OBD clients closed.
- Ignition ON/RUN, engine off. Reuse the existing Mac BLE recorder and framing.
- Require only 7E9 on protocol 6, then compare advertised identity to the baseline.
- Store raw replies, exact commands, monotonic timestamps, response latency,
  vehicle state and owner observations. Keep adapter identifiers/raw captures
  private under ignored artifacts.
- Wrong connector, identity mismatch or incomplete prompt ends the session.

### 2. One support check, about two minutes

Read `0100`, `0120`, `0140` and the known temperature/current/target requests.
Read advertised `0101`, `010C`, `010D`, `0142`; run `03`, `07`, `0A` once.
Try **only one new enhanced identifier, `225034`**, twice. Retain unsupported,
negative and timeout responses as results. Previously rejected `2208DF` and
`223C22` are excluded; `225043` returned a zero placeholder and is excluded.

Do not treat the support bitmap as enhanced Mode 22 discovery. Record a request
as supported only after its own correct positive reply. Keep no-data,
unsupported, malformed, unavailable and valid zero distinct.

### 3. Compare controlled states, about five minutes

Start the engine and remain parked. Capture approximately 30 seconds in P,
then brief labelled R, N, D and final P, with the vehicle stationary and brake
held. Use the already established owner-confirmation flow between positions.
Poll one request at a time, beginning at no more than two requests per second
overall. Prioritize current/target gear; interleave temperature once per second
and voltage/pressure less often. Measure achieved sample intervals rather than
assuming the published definition's frequency is an achievable rate.

For pressure, repeat in engine-off P and idling P/N/D only if the positive reply
has the same prefix/length. Save the raw difference and compare with a suitable
reference later. A constant value or engine-off zero does not prove sensor meaning.
For temperature, observe several minutes of warming; gradual agreement is useful
but not an independent thermometer/scan-tool calibration.

### 4. Restore and confirm, about one minute

Restore functional header/automatic protocol/CAN formatting on a clean prompt,
release Mac BLE, then power the gauge back on. If restoration fails, power-cycle
the adapter before reconnecting. Confirm the existing combined page still works.
Record that physical observation separately from the Mac results.

### 5. Optional recorded drive, five to ten minutes

Only after the parked reads are stable, prepare unattended logging before
departure. A passenger handles test equipment; the driver has no UI tasks.
Record ordinary gentle acceleration, steady cruising and deceleration, without
forcing a particular gear or unsafe speed. Gears not reached remain unverified.
Capture current/target gear together, temperature slowly, and already validated
standard signals where useful. No CAN-monitor experiment while driving.
Return to P before interacting with the equipment. Analyze transitions offline.

## If the first pass does not expose shaft speeds or lockup

The fastest useful fallback is an **independent FCA enhanced scan-tool reference**,
not a larger guessed identifier sweep. AlfaOBD documents transmission live data
and ECU-specific fault reading, and lists Challenger LA support. Its public page
does not supply our needed identifier table or guarantee this swapped setup or
the present Vgate's compatibility. Verify adapter support before spending money.

Select the Challenger LA / matching ZF eight-speed TCM family where offered,
connect read-only, compare ECU identification, then export visible parameter names,
units and timestamped measurements. Stop a mismatched identification rather than
cycling models and applying configuration. Initially compare the known gear and
temperature values to establish whether that tool is a useful reference.

If the reference reads input/output speed, lockup or richer fault status, capture
its diagnostic exchange with a suitable passive bus logger in a later session.
The Vgate is occupied by the scan tool and is not assumed to simultaneously log
that traffic. Our previous Vgate monitor ended in BUFFER FULL; it is not a
loss-free substitute. A PCS serial logger/cable or JSS-supplied read-only map is
the alternative if the sought channels belong to the external PCS module.

## Capture mode prepared for the session

`tools/obd_explore.py --tcm-values` now uses state labels, a fixed request list,
per-responder support, exact baseline identity and a short duration cap. The new
`tools/obd_tcm_values.py` provides bounded decoding and an offline summary.
No new live vehicle session has been performed with this mode yet.

Run one labelled capture per state, with the gauge off and the Mac using the
already physically identified adapter. Substitute its Mac BLE ID below:

```sh
.venv/bin/python tools/obd_explore.py --source transmission --adapter MAC_BLE_ID --tcm-values --tcm-state off-P --duration 30
.venv/bin/python tools/obd_explore.py --source transmission --adapter MAC_BLE_ID --tcm-values --tcm-state idle-P --duration 30
```

Repeat with `idle-R`, `idle-N`, `idle-D` only after owner confirmation and while
stationary with the brake held. Use `drive` only for prestarted unattended or
passenger-managed recording; it excludes pressure, fault and MIL probes. Polling
defaults to 30 seconds and permits 3..120 seconds. Discovery plus polling is
capped at polling duration plus 60 seconds after BLE connection; restoration
allows at most three additional five-second requests. BLE discovery/connection
time is separate. Actual cadence and any unavailable values appear in the report.

Each private directory contains `capture.jsonl`, `exploration.json` and
`tcm-values-summary.json`. Regenerate a summary offline, or combine several
labelled sessions without reconnecting to the Jeep:

```sh
.venv/bin/python tools/obd_tcm_values.py PATH_TO_SESSION/exploration.json
.venv/bin/python tools/obd_tcm_values.py PATH_TO_FIRST/exploration.json PATH_TO_SECOND/exploration.json
```

The summary separates statuses, distinct values, fault categories, request
latency and adapter restoration. Reports compare session labels; they do not
infer sensor semantics from correlations. A rejected/unsupported candidate is
retired for that invocation. Pressure receives two reads only when accepted,
and retains bytes/raw unsigned value without applying the suspect conversion.

- Added only `0101` and `225034` as new sensor requests in this mode; reuse already permitted
  standard reads and fixed gear/temperature/fault requests.
- Require baseline identity and per-responder support before the run. Keep a
  one-at-a-time request budget, five-second request timeout, and bounded total
  session time. Stop after transport/prompt loss; skip a confirmed unsupported
  candidate rather than repeating it throughout the session.
- Validate response ID, service/identifier, declared ISO-TP length and ordering.
  Preserve all raw bytes; the first temperature byte/current gear retain their
  existing decoders. New pressure starts as candidate bytes, never a qualified
  reading. Route fault lists to the existing bounded DTC assembler.
- Replay author pressure fixtures, our actual gear/temperature/fault captures,
  and wrong-ECU, wrong-prefix, short/oversized, reserved-code, padding, timeout,
  interrupted-session and restoration-failure cases before connecting.
- Produce one offline report: accepted requests, rejection reason, payload size,
  repeated values, observed state changes, latencies and remaining reference gaps.

Verification: 68 host OBD tests pass, covering identity mismatch, full fault
lists through the production C DTC assembler, fragmented notifications,
per-responder support, pressure fixture bytes, rate/duration bounds, partial
capture retention, failed restoration and drive exclusions. Offline replay also
accepted the retained Jeep evidence: ten current-gear, ten target-gear, three
temperature and three fault-category replies. These are replay/test results,
not new physical readings. The host replay entry point now uses the production
ECU-specific DTC parser when an explicit response address is provided.

This work needs no new app profile or firmware update. After qualification, add
definitions to the existing Transmission profile and scheduler, keeping consumer
labels simple and provenance/status in Expert. Complete full TCM fault-list
transport separately; a successful Mac read is not an app feature. Firmware
installation remains App/Wi-Fi, and public capability flags remain unchanged.

## Acceptance

A positive diagnostic response establishes request support. Repeated correctly
framed responses plus state correlation establish a useful candidate. A matching
independent reference establishes meaning/scale. Product completion additionally
requires parser tests, source-isolated configuration, persisted units, stale and
invalid-value behavior, and physical app/gauge display and recovery verification.
Each value advances independently; a model-family match is not blanket approval.

## Sources checked

- [FCA calibration catalog](https://kb.fcawitech.com/assets/FedWorldReport.pdf):
  prior association, current row recheck blocked; see identity investigation.
- [FCA bulletin 21-026-16](https://static.nhtsa.gov/odi/tsbs/2016/MC-10121695-9999.pdf):
  2016 application/model and transmission family.
- [OBDb Challenger definitions, d2d9fda](https://github.com/OBDb/Dodge-Challenger/blob/d2d9fda9119e1c61a806e32c657415e8e3ffc0bb/signalsets/v3/default.json)
  and [2021 pressure fixtures](https://github.com/OBDb/Dodge-Challenger/blob/d2d9fda9119e1c61a806e32c657415e8e3ffc0bb/tests/test_cases/2021/commands/7E1.7E9.225034.yaml).
- [OBDb Charger, b7eed844](https://github.com/OBDb/Dodge-Charger/blob/b7eed844066afcd524cc1ef8fb4b00947c1c60fa/signalsets/v3/default.json),
  [Grand Cherokee, c707ef21](https://github.com/OBDb/Jeep-Grand-Cherokee/blob/c707ef219b80586b04acb33411c05edc1eb2c4eb/signalsets/v3/default.json),
  [Chrysler 300, 7af8e20b](https://github.com/OBDb/Chrysler-300/blob/7af8e20b0c5affb17554e45a38fadb77c89ca4d2/signalsets/v3/default.json),
  and [Wrangler, 45f577bc](https://github.com/OBDb/Jeep-Wrangler/blob/45f577bcf6d4bd593ab7bdcbcef41d0c71d38ed9/signalsets/v3/default.json):
  shared-route cross-checks. These are community author definitions, not OEM
  qualification; the prior gear report records their CC-BY-SA-4.0 provenance.
- [ScanGauge Dodge/Chrysler/Jeep definitions](https://www.scangauge.com/support/x-gauge-commands/dodge-chrysler-jeep/):
  other application/route candidates require validation.
- [AlfaOBD features](https://www.alfaobd.com/) and
  [supported applications](https://www.alfaobd.com/supported_cars.html): potential
  enhanced reference tool, compatibility must be checked.
- [PCS block diagram](https://www.powertraincontrolsolutions.com/download/Released/Public/Technical_Documents/blockdiagram.pdf):
  logging/diagnostic paths and CAN bridging.
- [rusEFI 8HP research](https://github.com/rusefi/rusefi/wiki/8hp): different OEM CAN dialects.
- [OBD Solutions standard reads](https://www.obdsol.com/knowledgebase/obd-software-development/reading-real-time-data/):
  standard RPM/speed request meanings.

## Offline follow-up, 2026-10-06

The [current OBDb Challenger definitions](https://github.com/OBDb/Dodge-Challenger/blob/main/signalsets/v3/default.json)
were rechecked. They retain 04FE, 5503/5504 and raw 5034 candidates. They also list
output shaft speed in Engine request 21CA, with model filters and a multi-value
payload. This is a separate ECM candidate, not a validated TCM identifier. It is
excluded from the bounded transmission probe. No matching input-shaft, converter
slip or lockup identifier was established by this follow-up.

The [opendbc Stellantis definitions](https://github.com/commaai/opendbc/blob/master/opendbc/dbc/generator/chrysler/_stellantis_common.dbc)
include broadcast engine RPM and transmission gear state. These CAN frames do not
establish diagnostic identifiers, PCS bridge routing or compatibility with this
swap. Keep them as a future passive-capture reference. No broadcast decoder or
new undocumented request has been added.

Prioritize the next short parked test: complete stored/pending/permanent lists,
then advertised MIL/count, voltage, RPM and speed. Compare off-P and idle-P, then
owner-labelled R/N/D only if needed. Save raw 5034 alongside those observations.
Independent reference data is still required before naming pressure, shaft speed,
converter slip or lockup. Do not derive converter slip from engine RPM and vehicle
speed alone. Unknown manufacturer-specific code descriptions remain unavailable.

Offline multi-recording reports now compare each request across labelled sessions
in input order and identify incomplete or unrestored sessions. Use:

```sh
.venv/bin/python tools/obd_tcm_values.py <off-P/exploration.json> <idle-P/exploration.json>
```

The reports retain raw candidate meaning limits. Failed adapter restoration requires
an adapter power cycle before gauge use. No vehicle connection is needed to generate
these comparisons from saved files.
