# Configurable pages live validation

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

Date: 2026-09-27

## Hardware and software

- Gauge: Waveshare ESP32-S3-Touch-LCD-1.28 on `/dev/cu.usbmodem5C931582021`.
- Phone: Pixel 10 Pro connected to the development host with Wi-Fi ADB.
- Firmware: `0.2.0-dev.19` from GitHub release `dev-v0.2.0-dev.19`.
- Hosted firmware image SHA-256: `3eff3f77e8d592706ffa1627580ef23e03c958b631d9d9aba74b499749b9c8fe`.
- Source merge: `425e40c9f148b4054c8f85503d6bf9ae16474402`.

The hosted image was signature checked, extracted, written to the already active `ota_1` application slot, and verified with `esptool`. Boot output identified `0.2.0-dev.19`, initialized 2 MB PSRAM, loaded the existing revision 2 document, and initialized display, touch, and BLE. This USB recovery proves the hosted image boots; it is separate from phone OTA validation.

## Authenticated dashboard transfer

The installed Android debug app discovered the gauge through the public capability path. An owner-protected document read completed through the existing bond and returned revision 2. The app then reviewed this exact draft:

1. Speed, Trend, vehicle speed.
2. Engine RPM, Arc, engine RPM.
3. Coolant, Numeric, coolant temperature.
4. Engine Load, Bar, calculated load.
5. Fuel Level, Dual, fuel level plus coolant temperature.

The app sent the document through the authenticated configuration transport. After the firmware restart and health checks, Android reported `Revision 3 is healthy and active`.

A new owner-protected document read then reported revision 3 with 11 matching comparison fields, zero differences, and zero unavailable fields. That observation exposed a presentation gap: the transfer client's revision and SHA-256 activation checks covered the complete committed document, but the comparison UI summarized only page count, order, and the first page's PID and renderer.

The comparison now normalizes and compares each page's name, renderer, and PID list. After rebuilding and reinstalling the app, another owner-protected revision 3 read reported 12 matches, zero differences, and zero unavailable fields. The phone and gauge columns both displayed the same readable mapping:

1. `SPEED / Trend / speed`
2. `ENGINE RPM / Arc / rpm`
3. `COOLANT / Numeric / coolant`
4. `ENGINE LOAD / Bar / load`
5. `FUEL LEVEL / Dual / fuel + coolant`

The separate page-order field matched all five stable page IDs.

## Evidence boundary

This run verifies authenticated transfer, activation, persistence after restart, and full document integrity for a five-page mixed-renderer dashboard. No OBD adapter was available, so it does not verify PID responses, renderer timing with live samples, threshold activation from live coolant data, or dual-adapter behavior. The physical screen was not photographed or inspected page by page during this run, so circular readability for each renderer remains a separate observation.
