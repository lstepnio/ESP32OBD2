# TCM integration: observed faults and remaining gates

Updated 2026-10-06. Target: the owner's JSS 8HP70 setup, TCM calibration 68274867AE, diagnostic responder 7E9. The user now prioritizes transmission temperature, actual gear and faults. No matching PCS/JSS laptop or saved calibration/log files are currently available.

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

The running firmware vertical slice accepts one ECM-role source. Its scheduler connects source zero, its diagnostics snapshot represents one source and the Android protected diagnostics message contains only counts and first codes. The new explicit parser/API does not by itself activate TCM polling or expose all four TCM codes in the app. Those product paths are not complete or physically verified.

The next implementation must retain source identity throughout the adapter worker, fault snapshot and companion message. A versioned message should expose the complete bounded list for each category, freshness and the responding ECU. The Android Faults screen should group Engine and Transmission separately and report unavailable/unsupported separately from an empty successful list. An ECM connection must never imply that TCM faults were checked. Diagnostic-only TCM configuration and one-adapter port switching need an explicit supported runtime mode; dual-adapter operation remains a separate qualification.

## Temperature and actual gear

The published newer-Hemi ECM temperature candidate was rejected in both engine-off and idling tests. It does not qualify a temperature read through TCM 7E9. The TCM CAN sample is partial and unlabelled, and the old PCS map stops at six forward gears. No temperature or actual-gear decoder has been qualified. [Candidate investigation](hemi-transmission-candidates.md), [JSS interface research](jss-transmission-research.md).

Required evidence: the read-only request/response definitions or matching CAN map for this calibration and JSS bridge, plus a trusted reference channel. Until provided, do not expose invented temperature/gear values or enable public capabilities on the basis of the Mac fault capture.

## Concrete vendor information request

Provide JSS/PCS these observed identifiers: TCM calibration 68274867AE, CVN 8240DAE8, ECU name TCM - TransmisCtrl, 11-bit 500 kbit/s diagnostic responder 7E9. Request the applicable read-only definitions for transmission fluid temperature and actual/commanded gear: request addresses, services/identifiers, payload lengths, scaling, valid/invalid values and update rates. If broadcast-only, request the matching DBC/message map and bus routing. Also ask which tool/channel provides a trusted reference and which read-only service returns the complete OEM fault inventory. No message has been sent.
