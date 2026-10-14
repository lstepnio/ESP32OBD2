# Second-adapter setup investigation

> Historical evidence. This dated review records its own scope; [current state](../current-state.md) and [backlog](../backlog.md) own current support and remaining work.

Reviewed 2026-10-14. Scope: Android optional transmission-adapter setup within one
logical vehicle. Firmware, wire protocol and saved schema remain unchanged.

## Observed Pixel and gauge behavior

The owner reports two working adapters of the same model. The Pixel contained a
primary ECM adapter and an existing but unbound transmission child; dual mode was
Off. The App initially showed Searching. After an App restart it connected and a
protected gauge-based discovery completed with one compatible BLE advertisement.
Its address differs from the saved primary adapter. Advertising acceptance is not
qualification of simultaneous links or vehicle values.

Existing UI hid discovery until Child adapter was selected. Disabled dual mode
combined missing adapter selection, unknown connection/capabilities and old firmware
into one misleading instruction to choose adapters and update the gauge. The owner
left for a meeting before installing a fix or configuring the second adapter.
No adapter binding, dual-mode setting, gauge configuration or firmware was written.
Phone debug wake settings were restored before offline work continued.

## Implementation

- Expert opens a named **Second adapter** sheet directly. Creating a missing child
  opens the same flow. The primary adapter links back to Car.
- The sheet starts one bounded search when the gauge is ready; current operations,
  disconnection and unsupported discovery have distinct explanations. An explicit
  rescan remains available, without a repeated scan loop on every poll/busy change.
- The primary radio is marked already in use and cannot be selected as the child.
  Child selection captures vehicle identity, requires a real child and rejects the
  primary by radio address or binding identity. It never falls back to a primary write.
- Shared serialized setup persistence and the existing operation coordinator remain
  the owners. Pages, alerts and primary adapter are preserved; source editing does
  not filter the vehicle dashboard. Obsolete generic adapter-selection entry point
  was removed after verifying it had no callers.
- Dual enablement requires fresh supported firmware and distinct complete bindings.
  Missing selection does not request a firmware update, and disconnected capability
  checks do not mislabel the installed image as obsolete.

## Software qualification

Android App/test build and lint passed, with 177 JVM tests including five new setup
policy/preservation cases. Eleven focused emulator tests passed, covering direct discovery from the
primary editor context, primary-radio rejection, one search after reconnection,
no repeated scans on busy transitions, vehicle-change dismissal, explicit removal,
dual-mode gating and existing universal status/settings behavior. These are fixtures,
not simultaneous radio qualification.

Repository documentation/contracts and local whitespace checks passed. Private Pixel captures and preference backup are ignored, not committed.
The fixed APK is prepared but not installed on the owner Pixel while the owner is away.

## Resume at the vehicle

1. Reconnect/unlock the Pixel and keep the gauge powered. Install the data-preserving
   debug App update; this fix needs no firmware release.
2. Open Expert > Second adapter; inspect the automatic discovery result and select
   the unit in the separate TCM connector. Confirm the primary binding is unchanged.
3. Enable both adapters, then review/send the complete vehicle setup. Require exact
   protected running configuration revision/hash and cleared trial before success.
4. Check ECM and TCM values concurrently, independently attributed faults and the
   shared status pill. Keep one vehicle and the same mixed dashboard pages.
5. Remove/reconnect each adapter separately: its values must expire while the sibling
   continues, then return without manual refresh. Continue the [dual recovery matrix](../development/dual-adapter-recovery.md)
   before promoting any public capability or broader coexistence claim.
