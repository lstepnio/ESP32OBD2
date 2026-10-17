# Vehicle capture and offline replay

Use the owner's 2010 Wrangler, 5.7 L Hemi/JSS ZFHP70 setup for scoped captures.
Current identities, installed firmware and qualification are in [current state](../current-state.md).
One Vgate is moved between separate ports. Capture placement and session identity
explicitly; prior findings do not validate additional signal meanings.

## What to bring

Mac, Pixel, gauge, Vgate adapter, USB data cables, and enough Mac battery power. The Mac records the private files locally; internet is not needed for capture or replay once the environment is installed. Use a parked vehicle. Perform any running-engine observations outside.

Double-click `tools/vehicle_capture.command` on this Mac to open the capture menu. The prepared environment is `.venv`; to reproduce it, create that environment and install `tools/requirements-capture.txt`. `tools/obd_capture.py --help` documents the individual commands.

## Sequence at the vehicle

1. Select **Capture engine port**. Plug the Vgate into the engine port, turn ignition on, and power off the gauge while the Mac connects directly to the Vgate. Close other OBD apps. The menu scans for candidate adapters, asks you to select the physical unit, and saves the complete GATT map, ELM setup responses, supported standard readings per recognized ECU, and short sampling trace. Allow engine idle for RPM/coolant observations when appropriate. It finishes and disconnects automatically.
2. Select **Capture transmission port**. Move the same Vgate to the transmission port. A new recording keeps this port's evidence distinct, even though its adapter identifier is unchanged. This step records adapter and protocol responses plus a bounded standard support query. It does not probe undocumented transmission-temperature identifiers. A `NO DATA` response is useful evidence, not proof that the transmission cannot supply data through another documented protocol.
3. Return the Vgate to the engine port and select **Record gauge USB**. Connect the capture firmware gauge to the Mac with a data cable, open eGauge on the Pixel, and observe the round LCD. Record two minutes. Note physical LCD values, app status, value aging on adapter loss, and recovery separately. App preview numbers are examples and do not establish live readings. The current gauge validates headered single-responder replies. Preserve raw capture forms and parser rejection reasons; the configured responder/definition must match.
4. Leave a short observation note in the recording folder: source port, ignition/engine state, observed LCD value and time, Pixel status, comparison instrument where available, and any unplug/reconnect. Avoid VIN, owner codes, and other secrets in shared notes.

The two source captures are sequential. They cannot demonstrate simultaneous adapter support. A second physical adapter and its own identity are required for the later three-link acceptance test.

## Private recording contents

Each attempt creates a new directory under ignored `artifacts/vehicle-captures/`, with restricted file permissions. Files are flushed during capture so a failed attempt still leaves useful evidence.

- `capture.jsonl`: schema version, source label, captured/simulated evidence type, command and RX fragments, monotonic times, connection generation, and errors.
- `gatt.json`: actual adapter identifier and discovered services, characteristics, properties, and descriptors for Mac BLE attempts.
- `discovery.json`: only recognized support responses, kept per responder. Missing/unrecognized responses are not marked unsupported.
- `serial.log`: original gauge USB output for serial attempts, including boot/build and connection logs.
- `replay-summary.json`: derived complete transaction count, aborted/incomplete query count, timeouts, and results from the production C parser. Raw headers are retained even when that parser rejects them.

The Mac scanner's identifier is not automatically the address the gauge uses. Do not paste it into firmware MAC configuration. Raw GATT and serial files can contain identifying adapter addresses. Sanitize and review a small fixture before committing it; ignored local recordings are not automatically safe to publish.

## Capture firmware and recovery

A trace build enables `EGAUGE_OBD_TRACE` in a private configuration from the reviewed
current revision. The normal tracked setting is off. Do not reuse a dated capture
image or alternate checkout path as an instruction to install it. Record exact build
identity and use owner-authorized App/Wi-Fi installation when tracing is needed.

Firmware callbacks enqueue bounded 64-byte trace fragments without waiting for USB output. A dedicated task writes JSON records. Missing sequences, trace loss, RX overflow, or invalid fragment offsets reject replay rather than silently fabricating a valid response. Callback fragments correspond to mbuf pieces, not a guarantee of radio packet boundaries. The trace adds CPU and memory work, so measure sampling/latency again with tracing disabled before a production performance claim.

Private pre-session backups of gauge state partitions and the previous built image and Pixel app/data belong in ignored `artifacts/device-prep/`. These may contain ownership material; keep them local. Installing the capture build should preserve the NVS/configuration partitions. Record the exact image hash, partition table, and actual running build in the preparation evidence before leaving.

## Bench checks and scope

Run `python -m unittest discover -s tools/tests -p 'test_obd_capture.py'` and `python tools/obd_capture.py self-test` inside the prepared environment. Tests cover command policy, distinct ECU bitmaps, fragmented prompts, late old-generation bytes, request timeout boundaries, capture loss, and a fake GATT adapter using the real workflow. The replay tool compiles and invokes the production C assembler and decoder. These results prove selected recorded/simulated behavior only.

No arbitrary AT/vehicle command option, VIN read, DTC clearing, Mode 22 scan, vehicle coding, or controller programming is exposed by the Mac capture tool. The gauge's existing bounded Mode 01 and emissions-DTC polling remains its existing firmware behavior. Manufacturer readings require a documented controller-specific definition and a later targeted fixture.

Related: [remaining qualification](../backlog.md), [multi-adapter architecture](../architecture/multi-adapter.md), and [PID discovery](../protocol/pid-discovery.md).

## Recorded preparation

Earlier capture-image preparation is [historical evidence](../evidence/vehicle-capture-preparation.md).
Use [current state](../current-state.md) for the running image and device setup.

## Offline bench simulation

Run `python tools/obd_bench_simulator.py` on a Mac with the capture dependencies. Use the trace firmware, create a separate Bench profile in Android, choose Find adapter, select Bench simulator, and send its setup. The gauge must show SIMULATED. The app labels these as example readings and blocks vehicle fault checks. Stop the server to test connection loss. Return to the real vehicle profile and send its setup afterward.

Options include `--source transmission`, `--chunk-size 1`, `--scenario missing`, `--scenario no-data`, `--scenario malformed`, `--scenario multiple`, and `--scenario delayed`. The server stops after ten minutes by default. It creates only a local BLE peripheral and cannot send vehicle commands. Its separate service is ignored by ordinary adapter discovery. Normal firmware rejects the simulator driver.

The checked-in `firmware/gauge/tests/fixtures/jeep-engine.json` and `jeep-transmission.json` retain bounded standard responses and request latency. Addresses, phone identity, VIN, and raw owner data are omitted. Replaying these bytes is simulation, even when their original capture was physical.

Offline implementation and evidence: [offline integration results](../evidence/offline-integration-results.md).

## Direct controller exploration

With ignition on, gauge off and other adapter clients closed, use the physically identified adapter:

```sh
.venv/bin/python tools/obd_explore.py --source transmission --adapter PREVIOUSLY_IDENTIFIED_MAC_BLE_ID --monitor
```

This separate tool reads advertised standard support and calibration ID/CVN/ECU name. It excludes VIN, arbitrary commands, enhanced identifier sweeps and vehicle writes. Optional monitoring requests silent mode and temporary 11/29-bit 500 kbit/s protocols for up to five seconds each, with bounded storage and prompt recovery. CAN chunks stay separate from diagnostic transactions. BUFFER FULL marks a partial sample. If restoration fails, unplug/replug before using the gauge. Raw files stay private. Connector labels are owner input; compare responders and record placement corrections.

Actual findings and limits: [direct exploration results](../evidence/jeep-direct-exploration-results.md).

The separate opt-in `--hemi-temperature` flag adds exactly three vendor-documented 229110 reads on the published 7E0 route. This is an unqualified test candidate, not a new supported vehicle reading. Use the engine connector and obtain a reference temperature before qualification. Routing, conversion, tests and the short validation session are documented in [Hemi transmission candidates](../evidence/hemi-transmission-candidates.md).

For standard TCM faults, use `--source transmission --tcm-faults` with the gauge off and Vgate in the separate TCM connector. The explorer confirms 7E9 on 11-bit 500 kbit/s CAN before sending the three fixed fault reads on 7E1; it never clears codes. Standard fault lists do not guarantee complete OEM coverage. [Observed TCM faults and integration status](../evidence/tcm-integration-status.md).
