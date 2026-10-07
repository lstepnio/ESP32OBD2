# Direct Mac exploration results

Recorded 2026-10-06 America/Denver (2026-10-07 UTC), directly over Mac BLE to the physically identified Vgate. The owner prepared the transmission connector with ignition on and gauge powered off. Engine running state was not independently confirmed.

## Placement correction and identity

The first recording was labelled transmission but returned 7E8, matching the earlier engine capture. The owner checked placement and replied "fixed". The corrected recording returned 7E9. Keep the initial recording as placement-mismatch evidence.

| Field | Corrected transmission connector | Initial placement mismatch |
| --- | --- | --- |
| Responder | 7E9 | 7E8 |
| Calibration ID, Mode 09 PID 04 | 68274867AE | 68059434AD |
| Calibration verification number, PID 06 | 8240DAE8 | CA36378C |
| ECU name, PID 0A | TCM - TransmisCtrl | Not advertised |
| Diagnostic protocol | 11-bit CAN, 500 kbit/s | 11-bit CAN, 500 kbit/s |

Identity fields were manually decoded from retained headered ISO-TP replies using declared payload lengths. The calibration reply declares 19 bytes: 49 04 01 plus a 16-byte calibration field. The name declares 23 bytes: 49 0A 01 plus a 20-byte name field. Its displayed name replaces an embedded NUL separator with a space and removes trailing NUL padding. The CVN reply declares seven bytes, ending in four CVN bytes. Firmware has no new identity decoder.

FCA's catalog associates 68274867AE with a 2016 LA 5.7 L TCM / 8HP70 NAFTA calibration and bulletin 21-026-16. That FCA bulletin identifies LA as Dodge Challenger. This supports an FCA controller diagnostic path, but does not prove the physical donor vehicle, unmodified calibration, or JSS/PCS bridge topology. The catalog table text was accessible; its screenshot endpoint returned 403. [FCA catalog, printed page 01057](https://kb.fcawitech.com/assets/FedWorldReport.pdf#page=1057), [FCA bulletin hosted by NHTSA](https://static.nhtsa.gov/odi/tsbs/2016/MC-10121695-9999.pdf).

Corrected standard support maps: 98180001, 80018001, 40800000 for bases 00, 20, 40. Mode 09 map 14400000 advertised the identity fields above. Standard coolant PID 05 remains coolant and is not verified transmission-fluid temperature.

## CAN monitoring limits

The adapter acknowledged silent-monitor setup before each bounded attempt. Temporary protocol selection, a separate CAN trace channel, and stop/prompt recovery were used. The adapter's internal implementation has not been checked with another bus instrument. [ELM327 reference](https://www.elmelectronics.com/wp-content/uploads/2020/05/ELM327DSL.pdf).

- Corrected 11-bit sample: 2,422 serial bytes, 126 syntactically valid frame lines across 32 identifiers. It ended with BUFFER FULL. This is a partial sample, not a complete five-second recording or loss-free timing evidence.
- Corrected 29-bit attempt: no observed frame lines during the bounded five-second attempt. This does not prove 29-bit traffic never appears or fully qualify that adapter mode.
- Initial placement-mismatch recording: no observed frames in either attempt.
- Both sessions recovered their prompts, restored automatic protocol selection and CAN automatic formatting, and released Mac BLE. Physical gauge reconnection afterward was not checked.

The 11-bit identifiers do not establish a match to the legacy PCS 29-bit schema. Its temperature offsets and six-speed gear enum must not be applied to these bytes.

## Private evidence and tooling

Ignored local capture directories:

- artifacts/vehicle-captures/20261007T025040.365055Z-transmission-mac_ble: initial placement mismatch.
- artifacts/vehicle-captures/20261007T025157.747651Z-transmission-mac_ble: corrected connector.

Each retains exploration.json, diagnostic-discovery.json, capture.jsonl, gatt.json and observation.json. The observations record placement and buffer limits without rewriting raw capture. Adapter identifiers and raw traffic are excluded from Git.

The new tools/obd_explore.py provides opt-in advertised identity reads and bounded monitoring. AdapterSession retains its original request policy by default; Mode 09 support parsing is opt-in. Tests cover the allowlist, service separation, unfinished transactions, rejected silent setup, host capture limits and adapter BUFFER FULL. Firmware, Android and public capability flags are unchanged.

## Next step

Obtain read-only enhanced diagnostic definitions or the matching CAN map for this controller/calibration. Then compare only documented temperature and actual-gear requests against JSS/PCS or a supported scan-tool reference. Saved bytes allow offline parser work now. Enhanced transmission temperature and actual gear remain unverified. Blind identifier sweeps are not part of the explorer.

Related: [JSS research](jss-transmission-research.md), [Session B](jeep-session-b-results.md), [capture runbook](vehicle-capture-runbook.md).
