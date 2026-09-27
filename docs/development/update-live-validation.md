# Pixel signed development update, 2026-09-25

## Setup

- Paired Pixel 10 Pro, owner bond retained, Wi-Fi ADB available for observing the app. Gauge powered over USB-C. No OBD adapter or vehicle present.
- Signed development ZIP imported from Pixel Downloads. Image length `1054528` bytes, raw binary SHA-256 prefix `7b42c4803841`, app descriptor ELF SHA-256 prefix `6b933b9e46838eaa`.
- Before update, protected identity read showed `0.1.0-baseline`, running partition `0x60000`, OTA state `Confirmed valid`, and the same ELF SHA-256 prefix.

## Observed sequence

1. The first live attempt stopped before image delivery because Android received a duplicate GATT service-ready event. The client now emits readiness only once per connection.
2. A later attempt exposed that the saved-bond handshake must accept any protected status variant left selected by a prior connection, including OTA status version 4. That read path was expanded. A stale experimental transfer can be aborted before a new BEGIN.
3. With the corrected client, Android sent the signed image in sequential chunks. The app showed progress through 1%, 7%, 12%, 22%, 32%, 43%, 52%, 61%, 71%, 80%, 89%, and 98% without an offset error.
4. The gauge returned successful full-image verification and activation status. It rebooted. Android reported a valid running image at `0x360000` with ELF SHA-256 prefix `6b933b9e4683`.
5. A separate owner-protected identity read returned `0.1.0-baseline`, partition `0x360000`, OTA state `Confirmed valid`, and ELF SHA-256 prefix `6b933b9e46838eaa`.
6. A separate owner configuration status read after the update still reported active revision 2, SHA-256 prefix `6689a8922875`, idle transfer phase 0, and result 0.

## Evidence boundary

This was one successful owner-authenticated BLE update using the currently running development image, installed into the opposite OTA slot. The phone and gauge verified the signed image, and the post-reboot image identity matched the signed package's ESP app descriptor. It did not exercise a changed firmware build, deliberate power loss, link loss during chunk transfer, rollback after failed trial health, downgrade rejection, secure boot, or production key provisioning. Public `ota` remains false. The Android update client currently runs in a foreground activity; process death requires a new attempt. No OBD functionality was involved.
