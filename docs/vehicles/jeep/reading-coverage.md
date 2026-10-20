# Hemi-swapped JK reading coverage

Updated 2026-10-19. This scopes the 2010 JK / Hemi ECM `68059434AD` and separate
JSS/PCS transmission setup. It is not a stock-JK or donor-Challenger compatibility
profile. `contracts/reading-catalog.json` remains the executable catalog; the
[backlog](../../backlog.md) owns delivery status.

## Captured standard coverage

The sanitized [ECM bitmap fixture](../../../firmware/gauge/tests/fixtures/jeep-ecm-support.json)
comes from the engine-port discovery recorded 2026-10-06. The
[generated coverage report](../../../data/vehicle-definitions/vehicles/jeep/wrangler-jk-standard-coverage.json)
accounts for every advertised bit. Advertisement proves a declared PID, not a
correct measurement, installed sensor or fitted accessory.

- 42 advertised identifiers: 30 already selectable scalar readings, four missing
  compound oxygen-sensor readings, two unimplemented named statuses, two diagnostic
  items, one identity item, one sensor-layout item and two continuation maps.
- The compound gaps are `01 14`, `15`, `18`, `19`. Each reply contains oxygen voltage
  and a trim component; one whole-word number is not a correct decode. The two
  ordinary named-state gaps are fuel-system status `01 03` and PTO status `01 1E`.
- MIL/monitor data `01 01` already uses shared diagnostics. Drive-cycle monitor
  status `01 41` needs its own diagnostic interpretation. Layout `01 13`, standard
  `01 1C` and continuation `01 20/40` are not missing dashboard sensors.
- No transfer-case position is advertised by these standard bitmaps. Enhanced
  module coverage must be audited separately. An absent enhanced request is not
  proof that its signal is unavailable anywhere in the vehicle.

Recompute after any catalog change or new capture, without copying private IDs:

```sh
python tools/research/audit_vehicle_readings.py \
  firmware/gauge/tests/fixtures/jeep-ecm-support.json \
  --output data/vehicle-definitions/vehicles/jeep/wrangler-jk-standard-coverage.json
```

## Transfer case first

The local archive contains a Front Control/TIPM `620` → `504`, service `21`,
identifier `11` candidate. Its encoder-state label can distinguish 2WD, 4Hi,
4Lo and Neutral in some variants. The
[variant inventory](../../../data/vehicle-definitions/vehicles/jeep/wrangler-jk-transfer-case-candidates.json)
preserves the exact definitions and referenced state labels. Crucially, the same
request has conflicting enums: one family maps 0 to 2WD, another to Shift in
Progress; 1, 2 and 3 also differ. Its bit-position convention and matching module
variant must be established from actual responses. Never choose the first match.

Other leads are partial or different signals:

- TCM `22 502C`: candidate High / Neutral / Low / SNA, not full 2HI / 4HI / 4LOW.
  High cannot establish whether the front axle is engaged.
- PCM `22 02A1` or `1B0D`: variant-specific low-range bits, not full position.
- PCM `22 F011`: variant-specific AWDSysStat bitfields; no match to this swapped
  ECM has been established.
- TIPM `21 40` “T-Case Position State” can describe encoder movement/braking.
  That label must not become a selected drive-range display.

Test the Front Control candidate through the **primary engine diagnostic port**;
reachability is not yet confirmed on this swap. It should not require a third
adapter or belong under the optional TCM child.
Keep one logical vehicle and preserve independent source-loss behavior.

### Quick parked capture

`obd_explore.py --jeep-transfer-case` adds only three reads of the single `2111`
candidate. It checks engine-port/11-bit discovery, retains full headered replies,
stops on a negative/no-data response, refuses ambiguous replies and restores the
adapter header/filter before normal use. No diagnostic session change, control,
actuator, clearing request or identifier sweep is added. The candidate phase has
a 20-second execution budget and four-second route cleanup budget; normal explorer
discovery and final adapter cleanup retain their existing separate bounds.

At the Jeep, with the gauge off and JScan/other OBD apps disconnected:

```sh
python tools/obd_explore.py --source engine --adapter PHYSICALLY_CONFIRMED_ID \
  --jeep-transfer-case 2HI
```

Repeat separately for owner-confirmed 4HI, 4LOW and, if practical, transfer-case N.
The Jeep must remain stationary; the owner operates its transfer lever using the
vehicle's normal procedure. The tool does not command a shift. Capture returning
to 2HI too, plus the TIPM identification/JScan reference needed to select the
variant. Save negative responses as evidence; do not add a session or alternate
identifier automatically. Raw bytes and owner labels remain private. The tool
never publishes a decoded drive range or treats an owner label as decoder proof.

Then add the matched read end to end: bounded Mode 21/module routing, named-state
decoding, one ordinary Transfer case reading, Numeric/Dual presentation and shared
Equals alerts. Missing, transition, reserved/fault and stale states must be distinct;
never map missing data to 2HI. Do not enable Arc/Bar/Trend for unordered states.
Retain the same stable reading identity when the optional TCM adapter is changed.

## Whole-vehicle usefulness matrix

Inventory each module and fitment, then validate reads as a batch rather than
adding one isolated PID at a time. These are **coverage targets, not enabled PIDs**.

| Priority | Useful signals | Source / qualification needed |
| --- | --- | --- |
| First | Transfer-case position and transition/fault state | TIPM candidate above; identify variant and correlate actual ranges |
| First | Four wheel speeds, brake pedal/pressure, ABS/TCS state | ABS module variant; correct units/status bits; useful for shortcuts and recovery |
| First | Fuel-loop state and fitted oxygen-sensor voltage/trim | Captured standard gaps; bounded component extraction and sensor layout |
| Next | Engine oil pressure/temperature, fan demand/state | Actual Hemi ECM definitions and fitted sensors, not stock 3.8 L assumptions |
| Next | TCM actual/target gear, shaft speeds, converter slip, pressure | Existing TCM evidence/identity; do not promote unnamed raw values or guessed units |
| Next | Tire pressure/temperature and sensor availability | Fitted TPMS module and wheel association; unavailable sensors stay unavailable |
| If fitted | Front/rear locker and sway-bar state | Actual module presence and decoded physical states, not adaptation settings |
| Supporting | Controller identity, readiness, voltage and diagnostic state | Shared diagnostics; technical context in Expert, not duplicated gauge readings |

Promotion requires matched ECU/module software, raw replies, independently checked
units/enum values, stale/no-data behavior, decoder vectors, App selection/alerts,
firmware routing and bounded polling under both adapters. A catalog row or a model
year match is insufficient. Do not poll every catalog entry: preserve the existing
selected-page/alert union, transaction ownership and memory limits.

The current generator's `Mode 22 = TCM` inference and the compiler's restricted
Mode 22/whole-scalar paths cannot be reused blindly for body/4WD readings. Route
by logical reading/controller metadata, not by diagnostic service number. Extend
that shared path with capability negotiation after matching responses are captured.

## Research search

The archived live-data rows store translation IDs, not English names. The research
query now resolves the module's English label resource while retaining the ID,
variant and raw record. This fixes searches that previously returned no candidates:

```sh
python tools/research/query_vehicle_catalog.py search --category LiveData \
  --module TIPMCGW --name 'T-Case Encoder Sensor' --raw
```

Neither this search nor the candidate inventory enables imported definitions.
The [archive scope and rights](../catalog-research.md) continue to apply.
