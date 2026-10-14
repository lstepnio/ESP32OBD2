# GATT long-read recovery investigation

> Historical evidence. This dated review records its own scope; [current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Observed 2026-10-14 during second-adapter setup. Source candidate: dev.49.

## Phone, gauge and vehicle observations

The owner confirmed original adapter in ECM, new adapter in TCM, ignition ON/RUN
and other OBD Apps closed. The Pixel discovered and saved the distinct second
adapter under the existing transmission child. Private saved-state comparison
confirmed unchanged primary binding and both dashboard drafts. Dual mode was enabled
on the phone; no gauge configuration was sent before connection recovery failed.
The owner reported live engine RPM and responsive swipes on the physical gauge.

App restart, Pixel Bluetooth off/on, gauge restart and removal of only the ECM
adapter did not produce a stable protected connection. Mac advertising checks alone
were inconclusive: a connected peripheral stops advertising. Subsequent USB logs
showed repeated Phone link connected/disconnected events and healthy UI/config
worker progress. Thus a frozen display or permanently absent radio was not established.
The owner returned indoors with gauge and Pixel on USB before simultaneous vehicle
qualification. Engine-link timeout logs with its adapter absent are expected absence,
not evidence of a deadlock.

Pixel debug logs identified protected reads and owner-state reads failing with
GATT 7. Installed ESP-IDF 5.4.1 NimBLE `ble_gatts_val_access` calls the application
callback, then calculates `full_value_length - offset` and copies from that offset.
The existing companion callbacks already appended a sliced tail, applying the
same offset twice. A 360-byte event response at negotiated MTU 256 reaches offset
255 with only 105 bytes appended and fails INVALID_OFFSET. The default SDK preferred
MTU is 256. This affects long status/capability/readback values, not OBD decoder bytes.

## Corrective boundary

All 21 companion response paths use one full-value append helper. Bounded immutable
snapshots remain cached across positive-offset continuations. The native host owns
ATT slicing; application configuration/document offsets remain unchanged. Offset
past end and allocation/length failure retain their error semantics. Wire payloads,
owner protection, configuration schema and public capability flags are unchanged.

Android performs at most one encrypted/authenticated read-only STATUS command when
an owner read receives ATT 7 on older firmware. It then requires a recognized protected
frame before proceeding. Authentication and unrelated failures never use the reset;
repeated offset failure fails closed. The existing operation deadline still applies.
This permits the field App/Wi-Fi repair path without USB flashing or bond deletion.

## Verification and deployment boundary

- Sanitized production C helper reconstructs values of 4, 10, 56, 140, 248, 360 and
  512 bytes at MTUs 23, 185, 256 and 517. Exact-end, past-end and append-failure cases
  pass, and the former 360/256 double-offset failure is reproduced.
- All 89 host tests passed. Android build/lint and 179 JVM tests passed, including
  one-reset/read-only opcode policy and rejection of unrelated/authentication failures.
- ESP-IDF 5.4.1 dev.49 firmware build passed. No firmware was installed over USB.
- Updated dev.49 debug App installed with data preserved. It restored the saved
  gauge review/send flow; the universal pill reports Car reconnecting with adapters
  absent instead of losing the phone/gauge management path. Exact App/FW qualification
  remains separate from offline checks.
- Signed dev.49 firmware publication/installation requires explicit owner authorization,
  green CI, verified signed assets, App/Wi-Fi transfer and exact post-boot identity/health.
  Firmware remains dev.48 until that path is completed. The gauge still has revision 49
  and the previous single transport; two bindings are saved on the phone pending send.

After firmware repair, send the complete dual setup and require protected stored/running
revision/hash and cleared trial. Confirm physical display/swipes separately. Live ECM/TCM
reads, independent loss/recovery and soak remain QUAL-04; no public qualification increase.
Private logs, identifiers and preference backups remain ignored artifacts.
