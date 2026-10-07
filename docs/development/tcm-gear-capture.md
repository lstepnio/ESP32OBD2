# JSS TCM gear capture

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

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

The first owner-confirmed P baseline was captured over Mac BLE. Current request
`22 5503` returned `7E9 04 62 55 03 0D` twice, and target request
`22 5504` returned `7E9 04 62 55 04 0D` twice. Both agree with the
published P mapping. `22 3C22` returned `7F 22 31` twice. TCM identity
remained 68274867AE/CVN 8240DAE8, adapter settings were restored and Mac
BLE released. The selector could not be moved normally with the engine off,
so the owner started the engine and confirmed stationary R with the brake held.
Current request returned `7E9 04 62 55 03 0B` twice and target request
returned `7E9 04 62 55 04 0B` twice, agreeing with the published R mapping.
The older request again returned NRC 31. Adapter settings were restored.
With the owner-confirmed selector in N, current request returned
`7E9 04 62 55 03 00` twice and target request returned
`7E9 04 62 55 04 00` twice, agreeing with neutral. The older request
again returned NRC 31, and adapter restoration completed. In owner-confirmed stationary D, current request returned
`7E9 04 62 55 03 01` twice and target returned
`7E9 04 62 55 04 01` twice. This agrees with first gear, not a separate
D selector enum. The older request returned NRC 31, and adapter restoration
completed. The final owner-confirmed idling P repeat returned `0D` twice for both
current and target requests. Adapter restoration completed in all five sessions.
The observed comparison is P=0D, R=0B, N=00, stationary D=01, P=0D.
This identifies a useful experimental current-gear read at 22 5503 and a
separate target-gear read at 22 5504. It does not identify a distinct D selector
byte, validate actual gears 2..8, or demonstrate current/target divergence. No physical LCD gear
verification or actual forward-gear qualification has occurred.

All 51 host OBD tests pass, including the gear candidate parser, fixed request order, owner labels,
route restoration, partial timeout evidence and CLI exclusions. These tests use
synthetic/author fixture data and are not live vehicle observations.

A distinct selected D position has not been identified: the captured candidate
reports first gear in stationary D. Do not relabel that byte as D. Actual engaged
gear 1..8 and commanded gear need separate validation; stationary D does not qualify
all eight gears. The capture tool does not itself change firmware or capability flags.

## Cohesive transmission setup

Firmware `0.2.0-dev.37-tcm` adds current gear `22 5503` alongside the existing
`22 04FE` temperature source. The exact requests, response lengths and routing
are validated. Both use the existing scheduler, persisted configuration, unit
settings and stale-data handling. The `gear` unit identifies a discrete value:
only full-byte 0, 1..8, 11 and 13 are accepted, rendered N, 1..8, R and P.
Unknown/reserved values become unavailable immediately; high bits are not masked
away. Target gear is not substituted for current gear.

The App uses one **Transmission** profile and the normal Numeric/Dual page
editor. **Set up transmission** upgrades the existing temperature test profile
in place, preserving profile ID and adapter binding. The original default page
becomes a Dual Gear + Temperature overview. Custom pages are preserved and a
Gear page added when capacity allows; repeated setup is idempotent. Normal labels
are Gear and Transmission temperature. Qualification information stays in Expert
and definition provenance; the single debug gate remains easy to change. Engine
and transmission profiles remain separate because simultaneous adapter operation
is not implemented. Gear does not offer Arc/Bar/Trend layouts or numeric alerts.

58 Android tests pass, including combined projection/readback and idempotent
profile upgrade. Sanitizer fixtures check the actual App asset with the production
configuration compiler, gear rendering/unknown codes/units, and captured replies.
The firmware builds successfully. App/Wi-Fi transfer completed and passive USB
boot identifies dev.37-tcm. The combined setup passed validation and the App
recovered after restart. The owner then confirmed the combined Gear + Temperature page worked on the
physical gauge. Exact values were not restated in that confirmation. The owner also confirmed both readings became unavailable after adapter removal
and returned after reconnecting. App readback reports configuration revision 28
stored and running.
