# Hemi transmission candidates and next capture

Researched 2026-10-06. Current evidence: transmission responder 7E9 reports calibration 68274867AE; the engine connector separately exposed 7E8 / 68059434AD. Neither connector has supplied a validated enhanced temperature or actual-gear reading. [Direct exploration results](jeep-direct-exploration-results.md).

## A published temperature candidate

ScanGauge lists transmission-fluid temperature for 2015 to 2019 Dodge/Chrysler/Jeep Hemi applications. Its individual entry supplies TXD 07E0229110, RXF C46205913610, RXD 3010 and MTH 000100400000. These are vendor-documented values, not a promise of compatibility with this JSS swap. The page navigation labels the group Dodge Ram while its section heading covers Dodge/Chrysler/Jeep, so the breadth of the listing must not be treated as calibration-specific qualification. [Vendor application list](https://www.scangauge.com/support/x-gauge-commands/dodge-chrysler-jeep/), [individual temperature entry](https://www.scangauge.com/xgauge/transmission-fluid-temperature-f-4/).

The manufacturer's coding guide describes CAN identifier treatment, bit extraction, and multiplier/divisor/offset. Translating the entry gives this candidate interpretation:

| Property | Candidate interpretation |
| --- | --- |
| Physical request ID | 7E0 |
| Expected reply ID | 7E8, to be confirmed during capture |
| Diagnostic request | 22 91 10 |
| Positive prefix | 62 91 10 |
| Data | Two bytes after the positive prefix, big endian |
| Fahrenheit | Raw / 64 |
| Celsius | (Raw / 64 - 32) × 5 / 9 |
| Qualification | Not tested on this Jeep |

The reply-ID expectation follows the usual paired address and our observed engine responder, rather than being a new observation of this request. RXD 3010 refers to ScanGauge's internal layout with a three-byte CAN identifier. It is not byte offset six into the already stripped diagnostic payload. After removing the positive prefix, extraction starts at data offset zero. [Manufacturer coding guide, pages 3 and 4](https://www.scangauge.com/wp-content/uploads/dlm_uploads/2016/09/XGaugeCoding.pdf).

This temperature request addresses the engine controller. Do not change it to 7E1 simply because the value concerns a transmission. Our 7E9 calibration is a useful TCM lead, but does not prove that the separately identified engine controller implements this newer Hemi request or receives the underlying sensor data. A negative reply is an expected possible outcome.

## Gear remains unresolved

ScanGauge's 22 3C 22 current-gear entry is explicitly for 3.0 L EcoDiesel. Its 22 28 52 present-gear entry belongs to the Cherokee/Renegade section. Those scopes do not qualify either definition for our Challenger-calibrated Hemi 8HP70. Neither is included in the new probe. [EcoDiesel gear entry](https://www.scangauge.com/xgauge/3-0l-ecodiesel-current-gear/), [vendor application list](https://www.scangauge.com/support/x-gauge-commands/dodge-chrysler-jeep/).

The existing CAN sample contains traffic but no temperature/gear labels or changing reference states. One short, partial parked sample cannot establish actual gear, commanded gear, sensor scaling, invalid codes or freshness. The old PCS six-speed enum remains unsuitable without a matching JSS definition.

## Offline implementation

`tools/obd_temperature_candidate.py` interprets only complete single-frame replies from the expected route. It rejects headerless data, other responders, wrong identifiers, truncated payloads, unsupported multiframes, duplicate replies and values outside an engineering sanity bound. NO DATA and negative replies produce no numeric reading. Unknown sensor invalid-value codes remain a qualification gap. Every numeric result is explicitly `candidate_only`, with `qualified: false`.

Synthetic tests exercise the published arithmetic at 70, 149 and 212 F, along with malformed input and route rejection. These test parser behavior and conversion only. The saved Jeep capture contains no 22 91 10 request, so it cannot validate this decoder against physical temperature.

The host explorer adds opt-in `--hemi-temperature`: exactly three 22 91 10 reads on 7E0, one second apart, with a five-second timeout per read. It restores the functional header on a clean prompt. Missing prompts stop further commands and require reconnecting/power cycling as reported by the tool. There are no alternate identifier sweeps, controller session changes, tester-present requests, adaptation routines or vehicle writes. The normal capture policy remains unchanged. Firmware, Android, presets and public capability flags are unchanged.

## Short validation session

1. Use a parked vehicle, ignition on, gauge powered off and other adapter clients closed. Put the Vgate in the engine connector for the published 7E0 route. Record the connector and controller identity again.
2. Run the prepared probe without CAN monitoring:

   ```sh
   .venv/bin/python tools/obd_explore.py --source engine --adapter PREVIOUSLY_IDENTIFIED_MAC_BLE_ID --hemi-temperature
   ```

3. Keep raw replies private. NO DATA or a negative response ends this candidate route; do not automatically retarget it to the transmission controller. A supported reply remains unqualified until its meaning is checked.
4. Compare it to transmission-fluid temperature from the matching PCS/JSS live monitor or a supported scan tool. Identify the reference channel and units. With a single adapter, sequential readings need timestamps and stable conditions; identical adapters cannot share one connection simultaneously. Cold plausibility alone does not prove sensor identity.
5. After support is established, record a second temperature condition and verify stale/loss behavior. Promotion to the product requires reference agreement, invalid-value rules and a demonstrated refresh rate.
6. For gear, first obtain the applicable JSS/PCS diagnostic table or CAN map. Then capture actual/commanded gear against a trusted reference in separately recorded states. Stationary selector position is not proof of all eight actual gears.

## Information needed from JSS/PCS

Ask for the diagnostic or DBC definitions applicable to calibration 68274867AE and the installed JSS module: fluid-temperature channel, actual versus commanded gear, addresses, requests, byte layout, scaling, invalid values and update rates. Confirm whether their OBD connector exposes the original TCM directly or through a bridge, and which PCS live channel is an appropriate reference. No vendor message has been sent.

## Physical probe: ignition on, engine off

On 2026-10-06, the owner moved the Vgate to the ECM connector and confirmed readiness after instructions to use ON/RUN with the engine off and gauge off. The Mac connected directly over BLE. Standard replies came from 7E8; the calibration/CVN were again 68059434AD / CA36378C, and the selected protocol was A6 (11-bit CAN, 500 kbit/s).

All three requests on the fixed 7E0 route returned `7E8 03 7F 22 80`. These are complete negative responses, not timeouts, and contain no temperature. The meaning of code 80 for this calibration has not been established. It does not by itself prove that starting the engine will fix the request or that every possible transmission-temperature interface is unsupported. The adapter recovered its prompts, restored the functional header, automatic formatting and protocol selection, and disconnected cleanly.

Private evidence: `artifacts/vehicle-captures/20261007T030521.066747Z-engine-mac_ble/`, including raw request/reply records, identity and observation files. A single comparison with the engine idling has been requested; its outcome is pending. Firmware and the app remain unchanged.
