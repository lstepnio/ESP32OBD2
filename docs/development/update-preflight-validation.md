# Pixel update preflight, 2026-09-25

## Observed on hardware

- Built the Android app and ESP32 firmware from `feat/owned-gauge-control`, installed the APK on the paired Pixel 10 Pro, and flashed the application image to the gauge at `0x60000`. Both builds succeeded; the flash tool verified the written image hash.
- The app discovered the gauge and read its new owner-protected running image identity. It showed version `0.1.0-baseline`, OTA state `Confirmed valid`, partition `0x60000`, and ELF SHA-256 prefix `6b933b9e46838eaa`.
- Generated a signed development ZIP from that firmware image using the local development key, copied it to the Pixel Downloads folder, and selected it through Android's system file picker. The app reported `Development signature valid`, image length `1054528` bytes, and raw binary SHA-256 prefix `7b42c4803841`.

## Limits

The ELF SHA-256 is an identity for the running build, while the package SHA-256 covers the raw `.bin` file. They are intentionally different digests. This observation verified an authenticated identity read and on-phone package import with integrity and development signature checks. At this preflight stage the Android install action was disabled. The later live update is recorded in [live update validation](update-live-validation.md). No OBD adapters were present.
