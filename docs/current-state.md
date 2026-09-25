# Current state and evidence

Recorded 2026-09-25. This separates observed behavior from design targets.

| Component | Observed state | Remaining evidence |
| --- | --- | --- |
| Hardware | Waveshare ESP32-S3-Touch-LCD-1.28; GC9A01, 240 × 240; CST816S touch; S3 rev 0.2, 2 MB PSRAM initialized and memory checked at boot, 16 MB flash | Confirm enclosure, power behavior, daylight legibility |
| Firmware | Exact-board upstream commit `e1f4d8ffbb2bfe0fb38369e44d532319770ddc00`; built/flashed with IDF 5.4.1; user confirmed display works | M1 branch flashed and UI observed on serial; vehicle/adapter session not verified |
| Board startup | Serial showed LCD, LVGL, touch, UI, and BLE controller startup; touch changed PID | No simultaneous phone and adapter session observed |
| BLE adapter | M1 has ECM and TCM connection contexts for `18F0` / `2AF1` / `2AF0`; ECM can auto-select or bind a MAC, TCM requires a distinct MAC and remains disabled by default | Live dual-link operation, durable/bonded adapter identity, alternate GATT profiles |
| PIDs | Fixed RPM, speed, engine load, coolant and fuel requests use one bounded numeric decoder in firmware | Capability discovery, per-ECU attribution, activation of validated custom definitions |
| Configuration | NVS stores selected PID and rotation. Two 128 KiB configuration partitions are reserved; a bounded validator checks a staged document before commit and on boot selection | Authenticated transfer, device use of pages/PIDs/alerts, trial activation, app Apply and fixture coverage |
| Updates | USB migration installed a 16 MB layout with two 3 MiB OTA app slots; the development image boots from `ota_0` | Signed update transfer, activation, rollback and recovery |
| UI/app | Pixel 10 Pro completed passkey bonding, authenticated reading selection and 90-degree rotation with saved readback after gauge reboot; user confirmed rotated display and touch | Full configuration and vehicle operations remain unverified |

The integration branch now runs authenticated protocol 0 reading selection and display rotation in firmware and Android. The firmware was uploaded to the USB gauge and esptool verified the flash hashes. After correcting an ESP-IDF 5.4.1 NimBLE pairing-response path and enabling NimBLE bond persistence in NVS, the Pixel completed system passkey bonding. Selection and rotation readback survived reboot without another pairing prompt. The [pairing review](development/paired-control-validation.md) records the observations and remaining cases. The two-slot store and version 1 document validator are firmware foundations only. Full configuration transactions and activation remain unimplemented.

## Baseline provenance

`firmware/gauge` imports board support, firmware sources, fonts, simulator, CMake and sdkconfig from [Janos Kutscherauer's repository](https://gitlab.com/janoskut/esp32-obd2-meter) at the commit above. The main branch initially preserved upstream source behavior. The M1 branch changes response handling and freshness; see [M1 implementation status](development/m1-transport-status.md). Local dependency changes pin esp_lvgl_port 2.7.2 alongside LVGL 9.2.2 and the other registry components resolved during the successful build. The initially resolved display port 2.9.0 referenced `LV_COLOR_FORMAT_RGB565_SWAPPED`, which is absent from LVGL 9.2.2.

The earlier display-test firmware has been replaced. It is not a recovery dependency for this project. The previous Arduino instructions are superseded by [ESP-IDF setup](development/setup.md). The reference clone and retired sketches are local ignored files.

The baseline is a starting point, not evidence of a robust parser: notifications are parsed individually, the PID list is static, and the BLE manager has a single shared context. New transport code must assemble complete ELM responses and model central/peripheral sessions separately. `sdkconfig` enables both roles and three possible connections, but that alone does not implement the companion link.
