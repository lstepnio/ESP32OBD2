# Current state and evidence

Recorded 2026-09-25. This separates observed behavior from design targets.

| Component | Observed state | Remaining evidence |
| --- | --- | --- |
| Hardware | Waveshare ESP32-S3-Touch-LCD-1.28; GC9A01, 240 × 240; CST816S touch; esptool reported S3 rev 0.2, 2 MB embedded PSRAM, and 16 MB flash | Confirm enclosure, power behavior, daylight legibility |
| Firmware | Exact-board upstream commit `e1f4d8ffbb2bfe0fb38369e44d532319770ddc00`; built/flashed with IDF 5.4.1; user confirmed display works | M1 branch flashed and UI observed on serial; vehicle/adapter session not verified |
| Board startup | Serial showed LCD, LVGL, touch, UI, and BLE controller startup; touch changed PID | No simultaneous phone and adapter session observed |
| BLE adapter | M1 has ECM and TCM connection contexts for `18F0` / `2AF1` / `2AF0`; ECM can auto-select or bind a MAC, TCM requires a distinct MAC and remains disabled by default | Live dual-link operation, durable/bonded adapter identity, alternate GATT profiles |
| PIDs | Fixed RPM, speed, engine load, coolant, fuel list in `main/main.c` | Capability discovery, per-ECU attribution, custom decoders |
| Configuration | NVS stores selected PID and display rotation | Transactional versioned configuration service |
| Updates | On-device partition read confirmed 24 KiB NVS and a 1 MiB factory app slot, with no OTA slots or rollback enabled | One USB migration to an OTA-capable image/partition layout; see [storage design](architecture/config-storage.md) |
| UI/app | Pixel 10 Pro read public protocol 0 capabilities, completed passkey bonding, and confirmed authenticated quick selections by state readback, including after a gauge reboot | Rejected-input handling, wider phone compatibility, and full configuration operations remain unverified |

The integration branch now runs an authenticated protocol 0 quick-selection path in firmware and Android. The firmware was uploaded to the USB gauge and esptool verified the flash hashes. After correcting an ESP-IDF 5.4.1 NimBLE pairing-response path and enabling NimBLE bond persistence in NVS, the Pixel completed system passkey bonding. The app confirmed fuel and, on a later connection without another pairing window, engine load through protected state readback; firmware logged both saved selections. A subsequent gauge reboot preserved the bond, and another authenticated selection succeeded without a new pairing prompt. The [pairing review](development/paired-control-validation.md) records the diagnosis and remaining cases. Full configuration transactions remain unimplemented.

## Baseline provenance

`firmware/gauge` imports board support, firmware sources, fonts, simulator, CMake and sdkconfig from [Janos Kutscherauer's repository](https://gitlab.com/janoskut/esp32-obd2-meter) at the commit above. The main branch initially preserved upstream source behavior. The M1 branch changes response handling and freshness; see [M1 implementation status](development/m1-transport-status.md). Local dependency changes pin esp_lvgl_port 2.7.2 alongside LVGL 9.2.2 and the other registry components resolved during the successful build. The initially resolved display port 2.9.0 referenced `LV_COLOR_FORMAT_RGB565_SWAPPED`, which is absent from LVGL 9.2.2.

The earlier display-test firmware has been replaced. It is not a recovery dependency for this project. The previous Arduino instructions are superseded by [ESP-IDF setup](development/setup.md). The reference clone and retired sketches are local ignored files.

The baseline is a starting point, not evidence of a robust parser: notifications are parsed individually, the PID list is static, and the BLE manager has a single shared context. New transport code must assemble complete ELM responses and model central/peripheral sessions separately. `sdkconfig` enables both roles and three possible connections, but that alone does not implement the companion link.
