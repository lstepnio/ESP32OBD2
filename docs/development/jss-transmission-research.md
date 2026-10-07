# JSS / PCS transmission integration research

Researched 2026-10-06, America/Denver. Target: owner-reported 2010 Wrangler, 5.7 L Hemi swap, ZF 8HP70, Jeep Speed Shop (JSS) controller/harness, and the physically tested Vgate BLE adapter. This is a research report, not a qualification of enhanced transmission readings.

## Findings

The owner now confirms the installed external module is PCS TCM-2800, after providing a photo whose label names that model. This identifies installed hardware by owner confirmation; the PCS firmware and JSS calibration remain unknown. It does not establish that the 7E9 diagnostic endpoint is the PCS processor itself. PCS's block diagram documents two CAN interfaces, programmable bridging, and a separate serial tuning/logging interface, making the original transmission TCM and PCS interface distinct identification targets. The manufacturer's guide describes a dedicated laptop calibration connector and a calibration label on the controller bottom. [PCS block diagram](https://www.powertraincontrolsolutions.com/download/Released/Public/Technical_Documents/blockdiagram.pdf), [TCM-2800 quick-start guide, printed pages 1, 2 and 6](https://powertraincontrolsolutions.com/download/Released/Public/Manuals/TCM2800_Quick_Start_Guide.pdf).

Next physical evidence: a photograph of the bottom calibration/firmware label if accessible and the harness's laptop communication connector/cable. Use a suitable PCS serial/USB interface for the PCS logger path; the Vgate CAN diagnostic interpreter is not a general RS-232 cable. The old six-speed serial/CAN maps remain unqualified for this eight-speed installation despite the hardware-family match.

Further research found a ScanGauge temperature candidate for newer Hemi applications. It targets 7E0 rather than the observed TCM route and was subsequently rejected in both engine-off and idling tests. A host-only decoder and fixed opt-in probe are retained for evidence; actual gear still needs a matching definition. See [candidate analysis and validation session](hemi-transmission-candidates.md).

Update after direct Mac exploration: corrected transmission placement returned 7E9, calibration 68274867AE, CVN 8240DAE8 and name TCM - TransmisCtrl. A partial 11-bit CAN sample retained 126 frame lines across 32 identifiers before BUFFER FULL. This supplies a concrete FCA diagnostic lead, but no verified fluid-temperature or gear definition. See [direct exploration results](jeep-direct-exploration-results.md) for sources and limits. Legacy PCS mappings below remain unqualified.

The owner now confirms a Jeep Speed Shop setup. JSS explicitly describes its eight-speed gear indicator as compatible with its PCS kit and displaying gears 1 through 8. Its swap kit includes wiring and a module, but the public listing does not specify the module hardware number, firmware, diagnostic requests, or signal map. [JSS gear indicator](https://jeepspeedshop.com/product/gear-indicator/), [JSS swap kit](https://jeepspeedshop.com/product/8-speed-transmission-swap/).

The supplied photo independently shows PCS Universal TCU Software 1.11.4, TCM-2600/2800 Datastream, and Serial monitor type. That identifies the software and selected monitor family. That earlier software photo alone did not identify the installed module; owner confirmation now identifies TCM-2800. Its running firmware and the source of every displayed channel remain unverified. Software version 1.11.4 is not a controller firmware version. Fields visible include transmission temperature, current/commanded gear, lever position, shaft speeds, lockup-related status, and battery voltage. The photo does not validate those fields against our current Jeep recording.

PCS's controller diagram separates CAN1/CAN2, serial tuning/logging, and diagnostic connections. It also depicts a programmable bridge between CAN buses. Therefore the laptop data path and the transmission OBD port must be treated as separate interfaces until traced. [PCS controller block diagram](https://www.powertraincontrolsolutions.com/download/Released/Public/Technical_Documents/blockdiagram.pdf).

Working hypothesis: JSS uses a PCS module for swap integration, possibly alongside the transmission's original controller. The separate diagnostic port may expose that original controller, a PCS diagnostic implementation, or a bridged route. The observed responder 7E9 is not a manufacturer/model fingerprint. Neither the photo nor the public JSS listing settles this topology. Sound German Automotive's own PCS page discusses FCA 8HP calibrations and TCM reprogramming, illustrating the relevance of donor-controller software; it does not establish that JSS supplied an SGA kit. [SGA PCS information](https://www.soundgermanautomotive.com/pcs/).

## What our actual Jeep evidence establishes

See [Session B results](jeep-session-b-results.md) and [first capture results](first-jeep-capture-results.md).

- Engine diagnostic port: accepted engine-route replies from 7E8. Owner confirmed physical RPM agrees with dashboard, swipes work, RPM disappears after adapter loss and returns after reconnecting.
- Transmission diagnostic port: standard support and Mode 01 replies from 7E9. Gauge recorded 331 completed transactions, no timeout events, and rejected those replies under its active engine route. Owner confirmed dashes.
- Moving the adapter back to the engine port restored accepted readings.
- No enhanced transmission-temperature/current-gear definition has been verified. No simultaneous two-adapter qualification was performed.

The captured 7E9 response to 0105 contained byte 89. Standard Mode 01 PID 05 denotes engine coolant temperature, and its usual conversion gives 97 C. An identical final engine-port temperature byte also occurred. This may reflect shared engine information; it does not establish a fluid-temperature sensor. Do not relabel that reading as transmission temperature. [OBD interface manufacturer's PID tutorial](https://www.obdsol.com/knowledgebase/obd-software-development/reading-real-time-data/).

## Three candidate interfaces

| Interface | Fit to current hardware | Evidence needed |
| --- | --- | --- |
| Documented enhanced OBD reads through transmission port | First choice for retaining Vgate and current app/gauge flow | Exact controller identity, request/response addresses, service and identifier, response layout/scaling, access requirements, and measured latency |
| Existing PCS CAN broadcasts | Candidate if exposed on the connected bus and already enabled | Actual broadcast capture, bus/bitrate, protocol revision, frame definitions, source association, and adapter monitoring capability |
| PCS serial monitor/logging interface | Candidate if desired values are only available through laptop connection | Firmware-specific serial protocol, channel validity/mapping, connector/electrical interface, framing and checksum, plus a suitable interface to the gauge |

### Published PCS CAN reference

The manufacturer-hosted file name contains v1_1, but its pages identify Version 1.0, March 13, 2008. It describes 29-bit broadcasts at 500 kbit/s for PCS, with a J1939 alternative. TCU4 identifier 00200200 contains coolant, Fluid Temp 1/2, desired gear, lever position and current gear; fluid bytes 3/4 use raw minus 50 C. TCU1 contains engine/turbine/driveshaft speeds. The gear enum defines six forward gears, then reverse/neutral/park. It is a candidate reference, not the JSS eight-speed schema. Enablement depends on firmware/calibration; obtain the applicable revision from JSS/PCS before decoding. [PCS CAN document, pages 1, 4, 7](https://powertraincontrolsolutions.com/download/Released/Public/Developer_Files/PCS%20Proprietary%20CAN%20Messages%20v1_1.pdf).

A protocol change to enable this output can affect a swap's existing communications. First inspect the saved calibration and capture any broadcasts already present. Research has not established that this stream reaches the owner's transmission OBD connector.

### Published PCS serial reference

PCS's June 30, 2009 communication reference specifies 38,400 baud, 8-N-1. It lists firmware query F and monitor query D separately from calibration writes, reset and test commands. A companion legacy data-stream sheet describes a 68-byte stream with current gear at location 36 and Fluid Temp 1/2 at 45/46, using raw minus 50 C; its gear mapping also stops at six forward gears. These locations are leads for comparison, not validated JSS offsets. Confirm framing and firmware compatibility before implementing a driver. [PCS communication reference](https://pcswebsite.s3.amazonaws.com/resources/TCU%20Communication%20Quick%20Reference%20Guide.pdf), [PCS data-stream sheet](https://pcswebsite.s3.amazonaws.com/resources/TCU%20Data%20Stream%20Summary.pdf).

The separate checksum sheet specifies an 8-bit complemented sum excluding command bytes. The alternate stream describes raw inputs rather than the full derived transmission channels. [PCS checksum](https://pcswebsite.s3.amazonaws.com/resources/TCU%20Checksum%20Calculation.pdf), [PCS alternate stream](https://pcswebsite.s3.amazonaws.com/resources/TCU%20Alternate%20Data%20Stream%20Summary.pdf).

The Vgate's BLE UART connects to its diagnostic interpreter; it is not a general cable to the PCS serial connector. A serial path would require a separate transport and appropriate electrical interface. It should remain a fallback until the existing OBD path is understood.

### Adapter monitoring possibility

Elm Electronics documents monitor mode, CAN filters, and silent monitoring behavior in its ELM327 reference. That makes a bounded passive capture a plausible experiment, not proof that this Vgate implements the commands or can sustain the required BLE throughput. Polling, raw CAN monitoring, and PCS serial packets need separate assemblers. The standard recorder does not expose arbitrary commands or raw monitor mode. The separate explorer now implements bounded silent monitoring and prompt recovery. The actual BUFFER FULL result requires filtered captures before sustained telemetry claims. [ELM327 reference](https://www.elmelectronics.com/wp-content/uploads/2020/05/ELM327DSL.pdf).

## Important limits in the literature

PCS's own aftermarket datalog guide explicitly says clutch-to-clutch transmissions, including 8HP, require different diagnostic systems from the guide's listed conventional applications. This reinforces the need for JSS's applicable definitions. [PCS datalog guide, page 7](https://www.powertraincontrolsolutions.com/download/Released/Public/Manuals/Aftermarket_Datalog_Guide.pdf).

In the public material reviewed, I did not find a JSS-specific enhanced OBD request table for temperature, actual gear, shaft speeds, or lockup. The reviewed public documents do not establish that any generic Chrysler Mode 22 definition applies to this donor/controller/calibration. They also do not prove that a broadcast mode is enabled or routed to the diagnostic connector. Technical conclusions here use manufacturer/vendor documentation and our own captures, not forum PID guesses.

The JSS-linked public build-of-materials sheet lists supported transmission families and swap components but does not identify the installed controller or diagnostic signal definitions. [JSS BOM](https://docs.google.com/spreadsheets/d/1HyV2gL7n_k_O3Io8cAO2GslmVKy1vIEgm_mxwYflYuE/edit?gid=0).

Some public download indexes returned mismatched linked files. The PCS serial documents above were downloaded directly from the PCS-branded resource bucket and their internal titles/manufacturer/date verified. They remain old references. Public-source retrieval dates do not turn 2008/2009 protocol definitions into current JSS specifications.

## Recommended sequence

1. Installed module model is now owner-confirmed TCM-2800. Obtain its calibration/firmware label, donor transmission model/year or TCM part/software numbers, and any existing calibration/log files. Keep identifying raw files private.
2. Ask JSS for the read-only diagnostic and telemetry interface applicable to that exact setup. Prefer enhanced OBD requests on the already tested transmission connector.
3. If JSS confirms such reads, implement a versioned definition with explicit responder/source, bounded length, scaling and validity rules. Capture one documented request and compare fluid temperature/current gear to the matching JSS/PCS live display or a supported scan tool.
4. If only CAN telemetry is available, qualify a bounded filtered capture without changing the controller's CAN configuration. Compare actual frames to the supplied firmware-specific map; identify whether Fluid Temp 1 or 2 is used and which gear field is actual versus commanded.
5. If serial is required, prototype an independent logging bridge and parser offline. Do not assume the legacy stream length, byte offsets or gearing apply to JSS.
6. Replay the verified results offline through parser/source/freshness tests. Revisit the Jeep for one short real-value, restart and loss/recovery verification.
7. Qualify engine plus transmission plus Pixel after a second adapter is available. Preserve the existing public capability flags until the full paths meet their gates.

A 7E9-capable parser already exists. The running vertical slice intentionally uses the engine route. Source selection can be developed offline, but enabling 7E9 alone would expose standard replies without establishing transmission-specific meanings.

## Exact questions for JSS

- Which PCS/JSS module hardware and firmware does this kit use, and is the original 8HP TCM retained?
- Does the separate OBD connector access the original TCM, PCS module, or a bridge? Which CAN bus and bitrate does it expose?
- What are the read-only request/response addresses, services, identifiers, lengths and formulas for fluid temperature, actual/commanded gear, input/output speeds and converter status?
- Are the same channels broadcast on CAN? Please provide the current message map or DBC, validity values and update rates for this JSS eight-speed firmware.
- Can these reads operate continuously alongside the engine adapter and PCS laptop connection?
- Which software/cable can provide an independent reference log, and can an existing calibration be exported for inspection without changing the setup?

JSS lists Todd at todd@jeepspeedshop.com for support. No message has been sent. [JSS support](https://jeepspeedshop.com/support/).

## Development state

This research changes the controller-identification lead to owner-confirmed JSS and photo-supported PCS software. It does not change firmware, Android behavior, bindings, calibration or capability flags. The fresh captured transmission fixture and engine-route rejection tests remain the verified foundation. Enhanced transmission telemetry stays unqualified until the interface and definitions above are established.
