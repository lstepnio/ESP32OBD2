# Resume the TCM values session

Saved 2026-10-06, America/Denver. The owner deferred the next vehicle session
until another day. Do not start a live capture until the owner confirms readiness.

## Workspace and completed preparation

- Branch: `codex/egauge-display-update`.
- Managed worktree: `/Users/lukasz.stepniowski/.codex/worktrees/egauge-display-update/ESP32OBD2`.
- Implementation commit: `8468855`, combined TCM capture and offline reports.
- 68 host OBD tests passed. Saved Jeep gear/temperature/fault replies replayed
  successfully. Documentation validation passed. No new live capture was made.
- [Research, commands and full test plan](tcm-values-research-plan.md).
- Current gauge firmware is dev.37-tcm, installed previously through App/Wi-Fi.
  Owner confirmed combined Gear + Temperature display and both clearing/returning
  on adapter removal/reconnection. Exact values were not restated for that check.
- This next Mac capture requires no firmware update or Android change.

## Offline preparation completed after saving

- Full fault lists and category freshness implemented in firmware and Android.
- Verification passed: 63 Android tests, 70 host tests, C parser/state sanitizers,
  Android lint, ESP-IDF build and contract/document validation.
- Exact prepared files are retained privately under `artifacts/offline-prepared/dev.38-faults/`,
  with filenames, sizes and SHA-256 hashes in `manifest.json`. They are uninstalled.
- Protected command 39 / packet version 15 adds source, ECU, configuration revision,
  connection session and bounded full lists. Old firmware has an Engine-only partial fallback.
- No device or phone updated, and no BLE adapter connection made during this work.
- First product check: install the Android build, update dev.38-faults through
  App/Wi-Fi, select/send the Transmission profile and allow initial diagnostic polls.
  Check all categories on Pixel, then unplug/reconnect the adapter and check that
  old evidence is labelled last checked and new-session readings return.
- The previous physically verified dev.37 combined Gear + Temperature page is the
  hardware baseline. Public capability flags remain disabled.

## Next session starts here

Ask the owner to confirm all of the following:

1. Vgate in the separate **TCM** diagnostic connector.
2. Ignition ON/RUN, engine off, selector P.
3. Gauge powered off and other OBD apps closed.
4. Mac available and adapter within BLE range.

Reuse the previously physically identified Mac BLE adapter ID from private local
capture artifacts; do not pick a device by name alone. The combined mode requires
7E9 on 11-bit 500 kbit/s CAN and exact captured identity:
calibration 68274867AE, CVN 8240DAE8, ECU name TCM - TransmisCtrl.
An identity mismatch stops the run.

Run `--tcm-values --tcm-state off-P --duration 30` with `--source transmission`
and the identified adapter. Review the private summary before asking the owner
to start the engine for `idle-P`. Continue to labelled R/N/D/final P only after
owner confirmation, stationary with the brake held. Optional drive capture is
prepared before departure and managed by a passenger or unattended logger.

## Readings and limits

- Capture current/target gear, temperature, stored/pending/permanent fault lists,
  and advertised MIL/count, voltage, engine RPM and vehicle speed.
- `225034` gets at most two accepted parked reads. Preserve raw bytes; published
  pressure scaling is suspect and must not become a gauge value yet.
- Drive mode excludes pressure, fault and MIL probes.
- Gears 2..8, current/target divergence, dash CEL agreement and independent
  temperature meaning/scale remain to be qualified.
- Shaft speeds, converter slip/lockup and complete OEM faults need matching
  definitions/reference evidence. No neighboring-identifier sweep.
- Complete TCM fault lists are now integrated in the uninstalled dev.38-faults
  app/gauge build. Engine and Transmission sources stay distinct. Physical qualification
  of the new protected snapshot is pending.

## End the session

Check `adapter_restored` in `exploration.json` or `tcm-values-summary.json`.
If false, power-cycle the adapter before using the gauge. Otherwise release Mac
BLE and ask the owner to confirm the existing combined page returns. Record
physical observations separately from replay results. Raw captures remain private
under ignored artifacts. Future firmware installation remains App/Wi-Fi only.

Android debugging, if later needed, must start and stop through
`tools/adb_debug_awake.py` to restore the phone's original wake settings. No live
Android debug session was started for this preparation work.
