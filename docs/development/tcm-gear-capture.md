# JSS TCM gear capture

Prepared 2026-10-06 America/Denver. Target: the owner's JSS/PCS 8HP70,
calibration 68274867AE, separate TCM diagnostic connector at 7E1/7E9.
Temperature already displays experimentally; selected gear remains unimplemented.

## Published candidates

The OBDb authors publish three requests on the observed physical TCM route:

| Request | Published meaning | Interpretation retained for comparison |
| --- | --- | --- |
| 22 3C22 | Current gear, debug candidate | First byte divided by 10; no selector enum inferred |
| 22 5503 | Current gear | Low nibble: 0 neutral, 1..10 forward gear, 11 reverse, 13 park |
| 22 5504 | Target gear | Same published enum, kept distinct from current gear |

Source: [OBDb Challenger definition at reviewed revision](https://github.com/OBDb/Dodge-Challenger/blob/df9d74e070b7bb38dda080d20780027331bc6c86/signalsets/v3/default.json),
with [2019 current gear fixtures](https://github.com/OBDb/Dodge-Challenger/blob/df9d74e070b7bb38dda080d20780027331bc6c86/tests/test_cases/2019/commands/7E1.7E9.225503%7Cfc%3D1%2Cf%3D2018-.yaml)
and [2021 target gear fixtures](https://github.com/OBDb/Dodge-Challenger/blob/df9d74e070b7bb38dda080d20780027331bc6c86/tests/test_cases/2021/commands/7E1.7E9.225504%7Cfc%3D1%2Cf%3D2018-.yaml).
Published definitions are community authored under CC-BY-SA-4.0, not a JSS
qualification. The vehicle year and transmission donor year are not interchangeable.
These are current/target gear candidates, not documented selector-position reads.
The decoder preserves raw bytes and uninterpreted high bits, rejects duplicates,
wrong source/prefix, invalid lengths, multi-frame replies and adapter failures.
No decoded result is marked qualified.

## Parked comparison

1. Park securely with the parking brake set, engine off and ignition ON/RUN.
   Connect the Vgate to the separate TCM connector. Power off the gauge and close
   other OBD clients so the Mac has exclusive adapter access.
2. Capture P first. The tool confirms only 7E9 on 11-bit 500 kbit/s CAN, records
   controller identity, and reads each fixed candidate twice with 5-second limits.
   No arbitrary identifier, session, security access or controller write is exposed.
3. Only after the previous capture finishes and the owner confirms the next
   position, repeat in R, N and D with the service brake held and vehicle stationary.
   Keep the engine off if the selector can be moved normally. Do not bypass an
   interlock. Return to P after comparison.
4. Compare repeated values across positions, including the initial P baseline
   and a final P repeat. Constant, unknown or rejected responses do not establish
   selector position. Engine-off gear may represent a target rather than engagement.
5. The tool restores the functional header and normal adapter formatting/protocol
   when the prompt is clean. If it reports restoration failure, power cycle the
   adapter before powering the gauge back on. Raw evidence stays private.

Example invocation, substituting the already physically identified Mac BLE ID:

```sh
.venv/bin/python tools/obd_explore.py --source transmission --adapter MAC_BLE_ID --tcm-gear-position P
```

## Acceptance and next implementation

No physical gear capture has been performed at this checkpoint. All 50 host OBD
tests pass, including the gear candidate parser, fixed request order, owner labels,
route restoration, partial timeout evidence and CLI exclusions. These tests use
synthetic/author fixture data and are not live vehicle observations.

Selected position requires a repeatable matching P/R/N/D reference. Actual engaged
gear 1..8 and commanded gear need separate validation; stationary D does not qualify
all eight gears. Once a request is verified, add a typed discrete reading rather
than treating enum values as temperature or ordinary numeric scaling. Preserve
source identity, unavailable/unknown states and freshness in firmware, configuration
and App UI. No firmware change or capability flag is enabled by this capture tool.
