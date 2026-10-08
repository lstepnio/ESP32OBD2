# Resume the TCM values session

Saved 2026-10-06, America/Denver. The owner deferred the next vehicle session
until another day. Do not start a live capture until the owner confirms readiness.

## Ready to resume

- Inspect Git/worktree state and read [current state](../current-state.md) first;
  use its installed image, profile and qualification facts, not an old candidate.
- The owner has one adapter and intentionally removed the optional child setup.
  Use the existing whole vehicle dashboard on the primary transport. Re-add a child
  only when the second adapter arrives; never select a TCM-only page editor.
- Fault integration/primary-route review is **QUAL-05**; meanings/references are
  **QUAL-06** in [the backlog](../backlog.md). Prepare bounded replay/software work
  without a vehicle. Use [research and fixed reads](tcm-values-research-plan.md).
- A Mac capture does not require updating firmware. For an authorized App/gauge
  install, use current reviewed builds and the App/Wi-Fi flow, then record exact
  image/hash and physical behavior separately.

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
- Fault lists retain source/ECU freshness inside one logical vehicle. Check the
  current QUAL-05 implementation review before expecting TCM faults on a shared
  primary transport; protected snapshot vehicle qualification remains separate.

## End the session

Check `adapter_restored` in `exploration.json` or `tcm-values-summary.json`.
If false, power-cycle the adapter before using the gauge. Otherwise release Mac
BLE and ask the owner to confirm the existing combined page returns. Record
physical observations separately from replay results. Raw captures remain private
under ignored artifacts. Future firmware installation remains App/Wi-Fi only.

Android debugging, if later needed, must start and stop through
`tools/adb_debug_awake.py` to restore the phone's original wake settings. No live
Android debug session was started for this preparation work.
