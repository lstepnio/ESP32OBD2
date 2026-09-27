# Hardware capacity live validation

Date: 2026-09-27.

## Scope

This validation used the paired Pixel 10 Pro and the connected Waveshare ESP32-S3-Touch-LCD-1.28 gauge. The Android app and firmware source were built locally, reviewed through pull requests 19 and 20, and passed repository CI. Firmware artifacts were published through the signed GitHub development-release workflow.

## Hosted update and recovery

The Pixel discovered, downloaded, verified, transferred, activated, and confirmed GitHub-hosted `0.2.0-dev.17` over the authenticated private Wi-Fi transport. Android reported the new image healthy at `0x360000`.

The first post-update public capability refresh returned Android GATT status 7. The new capability JSON had crossed an observed Android characteristic-read boundary. The follow-up change uses compact wire key `hw`, omits an optional false field, and adds compile-time 255-byte bounds for both public capability documents.

Because the affected `dev.17` discovery path could not bootstrap another app-managed update, recovery used the immutable GitHub-hosted `0.2.0-dev.18` bundle. The bundle ZIP contained only `metadata.json` and `firmware.bin`; its image length, SHA-256, ESP image descriptor, and version were checked before writing. USB wrote and verified only the active `ota_1` application slot at `0x360000`. It did not write the bootloader, partition table, OTA metadata, NVS, configuration slots, or inactive application slot.

After restart, the Pixel completed public capability discovery. A separate authenticated firmware identity read reported:

- version `0.2.0-dev.18`;
- OTA state confirmed valid;
- partition `0x360000`;
- ELF SHA-256 `6bc9244d27def731049c9dbcf97f3d6a5f2778996b9ea4f6873efb578584ee0f`.

## Protected capacity snapshot

Opening technical details initiated the owner-authenticated hardware read and Android reported `Hardware checked`. The live snapshot showed:

- two cores at 160 MHz;
- chip revision 2;
- 16.0 MiB flash;
- 265.3 KiB total internal heap with 79.9 KiB free at the observation;
- 31.0 KiB largest free internal block;
- 2.0 MiB PSRAM total and approximately 2.0 MiB free at the displayed precision;
- power-on reset and 66 seconds uptime;
- Wi-Fi stack not initialized after normal boot;
- BLE, PSRAM, display, touch, and backlight initialized.

The app separately listed board-declared Wi-Fi, BLE, PSRAM, display, touch, backlight, IMU, battery ADC, expansion, and USB-to-UART capabilities. It explicitly stated that sensor identity and ADC calibration require separate probes.

## Evidence boundary

This proves the protected request, response validation, Android presentation, capability negotiation, and on-demand refresh on this phone and gauge. It does not verify QMI8658 identity or health, battery ADC calibration, board revision identity, occupied expansion pins, continuous polling under live OBD traffic, or compatibility with another Android device.
