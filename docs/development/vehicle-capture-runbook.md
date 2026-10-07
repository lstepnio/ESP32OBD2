# First swapped-Jeep capture

Prepared 2026-10-06 for the owner's 2010 Wrangler, 5.7 L Hemi swap, and ZFHP70 transmission. One Vgate iCar Pro BLE 4.0 adapter will be moved between the engine and transmission ports. Actual controller identities and compatible readings remain unverified.

## What to bring

Mac, Pixel, gauge, Vgate adapter, USB data cables, and enough Mac battery power. The Mac records the private files locally; internet is not needed for capture or replay once the environment is installed. Use a parked vehicle. Perform any running-engine observations outside.

Double-click `tools/vehicle_capture.command` on this Mac to open the capture menu. The prepared environment is `.venv`; to reproduce it, create that environment and install `tools/requirements-capture.txt`. `tools/obd_capture.py --help` documents the individual commands.

## Sequence at the vehicle

1. Select **Capture engine port**. Plug the Vgate into the engine port, turn ignition on, and power off the gauge while the Mac connects directly to the Vgate. Close other OBD apps. The menu scans for candidate adapters, asks you to select the physical unit, and saves the complete GATT map, ELM setup responses, supported standard readings per recognized ECU, and short sampling trace. Allow engine idle for RPM/coolant observations when appropriate. It finishes and disconnects automatically.
2. Select **Capture transmission port**. Move the same Vgate to the transmission port. A new recording keeps this port's evidence distinct, even though its adapter identifier is unchanged. This step records adapter and protocol responses plus a bounded standard support query. It does not probe undocumented transmission-temperature identifiers. A `NO DATA` response is useful evidence, not proof that the transmission cannot supply data through another documented protocol.
3. Return the Vgate to the engine port and select **Record gauge USB**. Connect the capture firmware gauge to the Mac with a data cable, open eGauge on the Pixel, and observe the round LCD. Record two minutes. Note physical LCD values, app status, value aging on adapter loss, and recovery separately. App preview numbers are examples and do not establish live readings. The current gauge parser accepts headerless single-responder Mode 01 only; failures with other response forms remain recorded for later parser work.
4. Leave a short observation note in the recording folder: source port, ignition/engine state, observed LCD value and time, Pixel status, comparison instrument where available, and any unplug/reconnect. Avoid VIN, owner codes, and other secrets in shared notes.
`
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

The local capture image is identified as `0.2.0-dev.33cap`, built from the newer `codex/egauge-display-update` checkout including its existing working changes. It enables `EGAUGE_OBD_TRACE` in a private build configuration; the tracked configuration and public capability flags are unchanged. The production default for this trace option is off.

Firmware callbacks enqueue bounded 64-byte trace fragments without waiting for USB output. A dedicated task writes JSON records. Missing sequences, trace loss, RX overflow, or invalid fragment offsets reject replay rather than silently fabricating a valid response. Callback fragments correspond to mbuf pieces, not a guarantee of radio packet boundaries. The trace adds CPU and memory work, so measure sampling/latency again with tracing disabled before a production performance claim.

Private pre-session backups of gauge state partitions and the previous built image and Pixel app/data belong in ignored `artifacts/device-prep/`. These may contain ownership material; keep them local. Installing the capture build should preserve the NVS/configuration partitions. Record the exact image hash, partition table, and actual running build in the preparation evidence before leaving.

## Bench checks and scope

Run `python -m unittest discover -s tools/tests -p 'test_obd_capture.py'` and `python tools/obd_capture.py self-test` inside the prepared environment. Tests cover command policy, distinct ECU bitmaps, fragmented prompts, late old-generation bytes, request timeout boundaries, capture loss, and a fake GATT adapter using the real workflow. The replay tool compiles and invokes the production C assembler and decoder. These results prove selected recorded/simulated behavior only.

No arbitrary AT/vehicle command option, VIN read, DTC clearing, Mode 22 scan, vehicle coding, or controller programming is exposed by the Mac capture tool. The gauge's existing bounded Mode 01 and emissions-DTC polling remains its existing firmware behavior. Manufacturer readings require a documented controller-specific definition and a later targeted fixture.

Related: [integration plan](adapter-vehicle-integration-plan.md), [multi-adapter architecture](../architecture/multi-adapter.md), and [PID discovery](../protocol/pid-discovery.md).

## Preparation status, 2026-10-06

The Mac capture environment, replay checks, capture firmware build, normal firmware build, and Android build/unit tests pass. The Pixel has the development app with signed-package import. Firmware `0.2.0-dev.33cap` was sent through the Pixel app over Wi-Fi. The gauge boot log confirms its trial image healthy, and USB trace capture/replay passes with `trace_start` and adapter connection failures on this bench. With no powered adapter here, there are zero complete OBD transactions; this is recording-path evidence only. The owner confirmed a normal physical reading page and working swipes after installation.

The first Wi-Fi attempts could not associate. A later attempt joined after retry, with 34.4 seconds to the first response and 2.85 seconds for flash preparation. One attempt was interrupted when the phone left eGauge. Keep eGauge open during installation. After activation, the Pixel reported protected-read error 2 from its cached earlier service layout. A targeted development cache refresh followed by rediscovery restored authenticated access without removing the bond. The Pixel now reports Ready, AUTHENTICATED, zero retries, and an up-to-date gauge. Stored and running configuration revision 21 and its hash match the pre-update state; boot restored 100% brightness. A durable production GATT service-change migration remains a follow-up, rather than depending on this development diagnostic. See the [first vehicle capture results](first-jeep-capture-results.md) for subsequent live evidence. Enhanced transmission definitions and simultaneous two-adapter use remain unverified.

## Offline bench simulation

Run `python tools/obd_bench_simulator.py` on a Mac with the capture dependencies. Use the trace firmware, create a separate Bench profile in Android, choose Find adapter, select Bench simulator, and send its setup. The gauge must show SIMULATED. The app labels these as example readings and blocks vehicle fault checks. Stop the server to test connection loss. Return to the real vehicle profile and send its setup afterward.

Options include `--source transmission`, `--chunk-size 1`, `--scenario missing`, `--scenario no-data`, `--scenario malformed`, `--scenario multiple`, and `--scenario delayed`. The server stops after ten minutes by default. It creates only a local BLE peripheral and cannot send vehicle commands. Its separate service is ignored by ordinary adapter discovery. Normal firmware rejects the simulator driver.

The checked-in `firmware/gauge/tests/fixtures/jeep-engine.json` and `jeep-transmission.json` retain bounded standard responses and request latency. Addresses, phone identity, VIN, and raw owner data are omitted. Replaying these bytes is simulation, even when their original capture was physical.

Offline implementation and evidence: [offline integration results](offline-integration-results.md).

## Direct controller exploration

With ignition on, gauge off and other adapter clients closed, use the physically identified adapter:

```sh
.venv/bin/python tools/obd_explore.py --source transmission --adapter PREVIOUSLY_IDENTIFIED_MAC_BLE_ID --monitor
```

This separate tool reads advertised standard support and calibration ID/CVN/ECU name. It excludes VIN, arbitrary commands, enhanced identifier sweeps and vehicle writes. Optional monitoring requests silent mode and temporary 11/29-bit 500 kbit/s protocols for up to five seconds each, with bounded storage and prompt recovery. CAN chunks stay separate from diagnostic transactions. BUFFER FULL marks a partial sample. If restoration fails, unplug/replug before using the gauge. Raw files stay private. Connector labels are owner input; compare responders and record placement corrections.

Actual findings and limits: [direct exploration results](jeep-direct-exploration-results.md).
