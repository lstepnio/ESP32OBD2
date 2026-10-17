# Offline TCM work completed

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

2026-10-06, America/Denver. No physical device observation, vehicle connection,
BLE adapter connection, firmware flash or phone installation was made during this work.

## Changes

- Firmware diagnostics state stores all bounded stored, pending and permanent codes,
  distinguishes unknown/success/failed/unsupported, and preserves last checked evidence
  across failure. New adapter sessions clear old evidence. The existing scheduler now
  checks the active Transmission source. Gauge badges identify Transmission faults.
- Protected BLE command 39 and version 15 snapshot carry source, ECU, revision,
  session, observation age and full lists. Legacy Engine-only version 5 stays supported.
  Public BLE capabilities remain disabled pending physical qualification.
- Android validates source/revision and displays separate Engine/Transmission sections
  using existing styling. Unchecked sources remain Not checked. Categories retain
  last checked codes while clearly identifying stale/unavailable evidence. Older
  firmware has a partial-list notice. Manufacturer-specific meanings are not invented.
- Offline reports compare requests across owner-labelled sessions and flag transport
  failures or unsuccessful adapter restoration. The research plan records remaining
  definition/reference gaps for shaft speeds, pressure, converter slip and lockup.

Key files: `firmware/gauge/main/src/diagnostics_state.c`, `ble_obd.c`, `ble_companion.c`,
`elm_response.c`, `main.c`, `ui.c`; Android `VehicleDiagnostics.kt`,
`GaugeProtocolCodec.kt`, `GaugeConfigTransferClient.kt`, `AppModel.kt`,
`DiagnosticPresentation.kt`, `PresentationMapper.kt`, `ScreenStates.kt`, `CarScreen.kt`;
`tools/obd_tcm_values.py`. Associated headers, fixtures and tests are included.

## Verification

- 63 Android unit tests passed, debug APK built, Android lint passed.
- 70 offline host tests passed, including fake adapter workflows, partial captures,
  restoration failures, actual sanitized Jeep replies and firmware state/wire parity.
- Production C response parser and diagnostics state passed address/undefined-behavior
  sanitizers. Coverage includes full lists, duplicate suppression, unknown versus empty,
  wrong source, malformed evidence, unsupported requests, expiry, reconnect and time wrap.
- ESP-IDF dev.38-faults built, 52% application partition space free.
- Contract/examples, documentation links and shared design tokens validated.

Private prepared files are under `artifacts/offline-prepared/dev.38-faults/`:
`egauge-dev.38-faults.bin`, `egauge-dev.38-faults-debug.apk`, and hash manifest.
The firmware binary SHA-256 is
`8f444c2f0eabbfd78171571537dc8cd2ac18ffa960fb760c47da37950361d96c`.

## Resume

Follow the [saved session instructions](../development/tcm-session-resume.md). Install through the
agreed App/Wi-Fi path when the owner reconnects the hardware. Verify the 248-byte
long read on Pixel, all available categories, source isolation and adapter removal/
reconnection while checking that Gear + Temperature continue working. Initial
category polling takes time; refreshing the App reads a cached snapshot.

The new fault path is built and verified offline, not physically qualified.
The previous dev.37 combined-page owner observations remain the hardware baseline.
Code clearing, simultaneous dual-adapter support, independent temperature sensor
meaning, higher gears and additional drivetrain signals are not qualified by this work.
