# Metric and imperial display units validation

> Historical evidence. Versions, measurements and unfinished steps below describe that session.
> Current support is in [current state](../current-state.md); remaining work is in
> [the backlog](../backlog.md). These notes are not standing implementation instructions.

> Reference record: implementation/evidence from its recorded session. Current status and next work are maintained in [current state](../current-state.md) and [roadmap](../roadmap.md). Do not treat old pending steps or tool instructions as the current plan.

Recorded 2026-09-29 for Android companion commit `2177827` and signed development firmware `0.2.0-dev.30`.

## Source and release checks

- Android debug build and unit tests, firmware ESP-IDF build, and sanitizer-backed C logic fixtures passed locally. PR #49 Android, firmware, and contracts checks passed for the same commit.
- The protected release workflow published [`dev-v0.2.0-dev.30`](https://github.com/lstepnio/ESP32OBD2/releases/tag/dev-v0.2.0-dev.30) from commit `2177827`, catalog generation 20.
- A fresh download independently verified the catalog P-256 signature, bundle hash and length, application image hash and length, application image P-256 signature, and embedded app descriptor version `0.2.0-dev.30`.
- Canonical OBD values and alert limits stay metric. The companion and gauge convert Celsius and kilometers per hour only when displaying values. The owner display setting uses state version `0B` and save opcode `37`; older state version `0A` and save opcode `36` remain supported.

## Pixel 10 Pro observations

- The updated app read the existing dev.29 display state and showed 100% brightness. It showed Units as unavailable while the gauge advertised `ds:1`.
- The app downloaded dev.30 from the hosted release and transferred it over gauge Wi-Fi. It showed 100% sent, then reported the gauge confirmed the new running image. A later installed-version check showed `0.2.0-dev.30` and up to date.
- On dev.30, Settings read Metric, 100% brightness, and 90° rotation. Saving Imperial completed with a gauge confirmation. The home coolant preview changed from `92 °C` to `198 °F`.
- After force stopping and reopening the Android app, a fresh protected display-settings read returned Imperial, 100% brightness, and 90° rotation.
- Expert device data continued to report stored and running configuration revision 20 after the firmware update.
- After the Imperial test, the app saved Metric again and confirmed the gauge readback. The gauge was left at its original Metric setting, with brightness 100% and rotation 90°.

## Physical gauge checks

The firmware ran and confirmed its health during the update, and the Pixel read back the saved units. Visual confirmation of `°F` or `mph` on the physical LCD and a gauge power-cycle persistence check remain pending.
