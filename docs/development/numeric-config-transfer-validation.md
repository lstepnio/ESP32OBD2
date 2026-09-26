# Pixel numeric configuration transfer, 2026-09-25

## Hardware and software

- Gauge: Waveshare ESP32-S3-Touch-LCD-1.28 over USB-C, firmware from `feat/owned-gauge-control` with `experimentalNumericConfig:true`.
- Phone: Pixel 10 Pro running the Android debug app built from commit `3705fa5`, connected through Wi-Fi ADB.
- The previously paired owner bond was reused. No OBD adapters or vehicle were present.

## Observed sequence

1. The first public capability read returned Android GATT disconnect status 147. Retrying succeeded, and the app identified the experimental numeric ECM capability.
2. An owner-only `STATUS` read reported built-in readings, active document revision 0, and transfer phase 0.
3. The app sent its restricted version 1 document with RPM, coolant, and speed numeric pages. Its coolant alert fields came from the local draft: warning 105 °C, critical 115 °C, hysteresis 3 °C, 1000 ms trigger dwell, and 2000 ms clear dwell. The active RPM page was selected first.
4. The app reported commit and reboot readback at revision 1, SHA-256 prefix `7aec9b1f886b`.
5. A separate owner-only `STATUS` read returned active revision 1, the same SHA-256 prefix, phase 0, and result 0. Phase 0 indicates a fresh transfer session after reboot.
6. The user confirmed that the gauge displayed the numeric RPM page and that one touch navigated to coolant or speed.
7. After the owner-link read handshake fix was flashed and installed, the first protected status read after discovery succeeded. Android read the protected diagnostic snapshot and displayed no fresh vehicle evidence in all three code categories. With no adapter connected, this is a successful transport read, not a vehicle all-clear.
8. The app sent the same numeric pages and thresholds again. It reported active revision 2 and SHA-256 prefix `6689a8922875` after reboot. A separate status read returned the same revision and hash, phase 0 and result 0. Design labeled the local draft as sent in revision 2.

## Evidence boundary

The phone and gauge completed two authenticated configuration transfers and durable status readbacks. The user confirmed numeric page display and touch navigation. A protected diagnostic snapshot read worked with no adapter, so it carried no fresh vehicle evidence. No live PID values, local alert transitions, DTC responses, dual-adapter behavior, or OTA transfer were exercised. The sender remains limited to numeric ECM Mode 01 pages; the public general `configWrite` capability stays false.
