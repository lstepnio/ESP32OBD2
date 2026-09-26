# Current state and evidence

Recorded 2026-09-25. This separates observed behavior from design targets.

| Component | Observed state | Remaining evidence |
| --- | --- | --- |
| Hardware | Waveshare ESP32-S3-Touch-LCD-1.28; GC9A01, 240 × 240; CST816S touch; S3 rev 0.2, 2 MB PSRAM initialized and memory checked at boot, 16 MB flash | Confirm enclosure, power behavior, daylight legibility |
| Firmware | Exact-board upstream commit `e1f4d8ffbb2bfe0fb38369e44d532319770ddc00`; built/flashed with IDF 5.4.1; user confirmed display works | M1 branch flashed and UI observed on serial; vehicle/adapter session not verified |
| Board startup | Serial showed LCD, LVGL, touch, UI, and BLE controller startup; touch changed PID | No simultaneous phone and adapter session observed |
| BLE adapter | M1 has ECM and TCM connection contexts for `18F0` / `2AF1` / `2AF0`; ECM can auto-select or bind a MAC, TCM requires a distinct MAC and remains disabled by default | Live dual-link operation, durable/bonded adapter identity, alternate GATT profiles |
| PIDs | Five built-in Mode 01 definitions use a bounded decoder; firmware now compiles a restricted active document and schedules its definitions by interval | Live adapter responses, supported-PID discovery, Mode 22, ECU attribution and dual-adapter scheduling |
| Configuration | Owner-only transfer and atomic two-slot commit compile an executable Mode 01 subset. Pixel sent one numeric ECM profile; reboot and a separate protected read confirmed revision 1 and matching SHA-256. User confirmed numeric page display and touch navigation | General Android Apply, full document features, power interruption and configuration trial rollback |
| Diagnostics and alerts | Firmware includes periodic MIL/count and headerless single-responder Mode 03/07/0A reads with a protected snapshot, plus local threshold dwell/hysteresis badges for an active document | Adapter evidence, complete ECU-attributed code history, acknowledgment, safe code clearing |
| Updates | The 16 MB layout has two 3 MiB app slots; the new bootloader has rollback enabled. Firmware has an owner-only inactive-slot transfer, SHA verification and trial activation path | Android delivery, signed release enforcement, live OTA, rollback and recovery evidence |
| UI/app | Pixel 10 Pro completed passkey bonding, authenticated reading selection and 90-degree rotation with saved readback; the restricted numeric sender completed one transfer and reboot readback | General configuration and vehicle operations remain unverified |

The integration branch runs authenticated protocol 0 reading selection and display rotation in firmware and Android. The transfer and scheduler firmware, plus a rollback-enabled bootloader, were uploaded over USB; esptool verified both hashes and the serial log showed normal display, touch, BLE and task startup. The existing owner bond and saved rotation were preserved after the first firmware flash. The [restricted numeric transfer](development/numeric-config-transfer-validation.md) succeeded from the Pixel. No general document feature set or phone-driven OTA transfer has been observed. The [experimental transfer protocol](protocol/experimental-firmware-transfers.md) records the wire format and its restrictions. Public capability flags still report `configWrite:false` and `ota:false`.

## Baseline provenance

`firmware/gauge` imports board support, firmware sources, fonts, simulator, CMake and sdkconfig from [Janos Kutscherauer's repository](https://gitlab.com/janoskut/esp32-obd2-meter) at the commit above. The main branch initially preserved upstream source behavior. The M1 branch changes response handling and freshness; see [M1 implementation status](development/m1-transport-status.md). Local dependency changes pin esp_lvgl_port 2.7.2 alongside LVGL 9.2.2 and the other registry components resolved during the successful build. The initially resolved display port 2.9.0 referenced `LV_COLOR_FORMAT_RGB565_SWAPPED`, which is absent from LVGL 9.2.2.

The earlier display-test firmware has been replaced. It is not a recovery dependency for this project. The previous Arduino instructions are superseded by [ESP-IDF setup](development/setup.md). The reference clone and retired sketches are local ignored files.

The baseline is a starting point, not evidence of a robust parser: notifications are parsed individually, the PID list is static, and the BLE manager has a single shared context. New transport code must assemble complete ELM responses and model central/peripheral sessions separately. `sdkconfig` enables both roles and three possible connections, but that alone does not implement the companion link.
