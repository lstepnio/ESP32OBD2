# TCM integration: observed faults and remaining gates

Updated 2026-10-06. Target: the owner's JSS 8HP70 setup, TCM calibration 68274867AE, diagnostic responder 7E9. The user now prioritizes transmission temperature, actual gear and faults. No matching PCS/JSS laptop or saved calibration/log files are currently available. The owner subsequently confirmed the external PCS module is TCM-2800; its firmware/calibration and diagnostic-port routing remain unverified. See [updated hardware/interface findings](jss-transmission-research.md).

## Real fault capture

The owner moved the Vgate from ECM to the separate TCM connector, with ignition ON/RUN, engine off and gauge off. The Mac connected directly over BLE. Standard discovery again identified responder 7E9. The fixed fault probe selected physical request header 7E1 and read Modes 03, 07 and 0A without clearing anything.

| Category | Reported codes |
| --- | --- |
| Stored, Mode 03 | U0121, U0140, P1DCA, P1DF3 |
| Pending, Mode 07 | U0121, U0140, P1DCA |
| Permanent, Mode 0A | U0121, U0140, P1DCA |

The stored response declares ten ISO-TP payload bytes: 43, count 04, then four two-byte codes. Pending and permanent each declare eight bytes: their positive service, count 03, then three codes. Padding AA bytes are outside declared message length. These are standard emissions-related categories, not proof of a complete OEM fault inventory, all faults being active now, or a diagnosis of a failed component. Manufacturer-specific descriptions need a controller-specific source. The codes were manually decoded and independently exercised by the new production-parser tests.

Raw private capture: `artifacts/vehicle-captures/20261007T030853.309411Z-transmission-mac_ble/`. Its observation file distinguishes this Mac capture from phone/gauge behavior. The adapter recovered prompts, restored its functional header/automatic protocol/formatting and released BLE. No codes were cleared, controller settings changed or new firmware installed.

## Software work completed

- Host `--tcm-faults` probe is opt-in, requires the transmission label and observed 7E9 on 11-bit 500 kbit/s CAN, and permits only standard fault reads plus the fixed TCM header. It preserves received samples if a later request fails. Missing prompts stop further commands.
- New firmware `elm_response_decode_dtcs_for_ecu` requires an explicit responder, assembles a bounded headered ISO-TP fault response, validates sequence and code count, trims padding and returns normalized code pairs. It handles both single and multiple frames and fails closed on incomplete/duplicate/oversized replies. Mode 01 decoding is unchanged.
- `ble_obd_read_service_ecu` accepts an explicit responder. The existing wrapper retains ECM 7E8, now using the DTC assembler so CAN's count byte is not misinterpreted as part of a fault code.
- Sanitizer tests replay the actual stored/pending/permanent payloads under notification fragmentation. They cover source isolation, count/prefix errors, missing/duplicate/misordered continuations, empty lists, adapter failures and bounds. Host tests cover allowlist, route labelling, fixed requests, restoration and timeout handling.

Verification: 33 host capture tests passed, C parser tests passed with address/undefined-behavior sanitizers, contract/document validation passed, and the existing ESP-IDF firmware target built successfully. This image has not been installed or physically verified. Its SHA-256 is recorded privately with the capture; it is not a qualified TCM release.

## App and firmware integration still required

Firmware `0.2.0-dev.36-tcm` now accepts a single experimental TCM temperature source, selected through a separate debug App profile. Its scheduler connects source zero. The TCM temperature profile suppresses ECM fault jobs, and the Android protected diagnostics message still contains only counts and first codes. This does not expose all four TCM codes in the app. The TCM fault product path is not complete or physically verified.

The next implementation must retain source identity throughout the adapter worker, fault snapshot and companion message. A versioned message should expose the complete bounded list for each category, freshness and the responding ECU. The Android Faults screen should group Engine and Transmission separately and report unavailable/unsupported separately from an empty successful list. An ECM connection must never imply that TCM faults were checked. Diagnostic-only TCM configuration and one-adapter port switching need an explicit supported runtime mode; dual-adapter operation remains a separate qualification.

## Temperature and actual gear

The published newer-Hemi ECM temperature candidate was rejected in both engine-off and idling tests. It does not qualify a temperature read through TCM 7E9. The subsequent TCM-addressed EcoDiesel candidate `7E1 / 22 08 DF` returned `7F 22 31` on all three engine-off reads with the same TCM identity. The TCM CAN sample is partial and unlabelled, and the old PCS map stops at six forward gears. No temperature or actual-gear decoder has been qualified. [Physical temperature goal and actual test](tcm-temperature-goal.md), [ECM candidate investigation](hemi-transmission-candidates.md), [JSS interface research](jss-transmission-research.md).

Subsequent bounded reads of `7E1 / 22 04 FE` returned `7E9 06 62 04 FE 55 54 55` three times while idling. The community OBDb first-byte conversion produces 45 C. A separate experimental temperature profile and firmware parser are implemented, and the image was transferred through App/Wi-Fi. The owner confirmed 45 C on the physical LCD, then dashes after adapter removal. App readback confirms revision 27 healthy, without trial or fallback, and firmware dev.36-tcm with valid OTA state. The owner then confirmed 45 C returned after reconnecting. Independent sensor-meaning validation remains pending. `22 50 43` returned zero even when idling and is not used. See the linked temperature goal for exact evidence and installation state.

Required evidence for qualification: a trusted matching reference for this calibration and JSS bridge. Keep experimental interpretation visible and public capabilities disabled. Actual gear remains unimplemented and unvalidated.

## Concrete vendor information request

Provide JSS/PCS these observed identifiers: TCM calibration 68274867AE, CVN 8240DAE8, ECU name TCM - TransmisCtrl, 11-bit 500 kbit/s diagnostic responder 7E9. Request the applicable read-only definitions for transmission fluid temperature and actual/commanded gear: request addresses, services/identifiers, payload lengths, scaling, valid/invalid values and update rates. If broadcast-only, request the matching DBC/message map and bus routing. Also ask which tool/channel provides a trusted reference and which read-only service returns the complete OEM fault inventory. No message has been sent.
