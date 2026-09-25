# Hardware capabilities and integration strategy

Owner direction: exploit Wi-Fi and other ESP32/board capabilities where they improve reliability and user experience. Capability availability, implemented support and measured performance are distinct.

## Hardware inventory and intended use

| Capability | Proposed use | Constraints/evidence |
| --- | --- | --- |
| ESP32-S3 Wi-Fi, 2.4 GHz 802.11 b/g/n | Faster local OTA and profile/diagnostic transfer; optional local integrations | Shares radio resources with BLE; no 5 GHz assumption |
| BLE central + peripheral | One/two OBD adapters; phone association/config/control/telemetry | Three-link feasibility is unresolved; limits advertised |
| Dual-core CPU / RTOS | Isolate rendering from transaction/parser work; measured task priorities | Do not fix core affinity without workload measurement |
| 16 MB flash configuration | A/B app slots, bounded profiles, diagnostic records | Verify actual flash geometry before migration; firmware files currently use 16 MB |
| 2 MB embedded QSPI PSRAM | Frame buffers, bounded trends, larger profile cache | esptool observed memory; baseline disables PSRAM; capability/allocation must be measured |
| 240 × 240 GC9A01 SPI LCD | Bright readable templates, attention overlays, update/recovery status | Board pins/bus bandwidth and partial DMA buffer constraints |
| CST816S touch | Page navigation, owner confirmation, alert acknowledgment and local diagnostics | Small circular area; broad targets and deliberate destructive-action confirmation |
| QMI8658 IMU from board demo | Optional installation orientation helper and wake gesture experiment | Check sensor/revision and calibration; no automatic in-motion rotation or claim of accurate vehicle dynamics |
| Board battery/ADC support | Device battery display and power diagnostics where physically fitted | Not a substitute for vehicle voltage or proof of stable update power; calibration/source labels required |
| USB | Flash/recovery, serial logs, deterministic bench development | Actual board USB routing/mode must be checked; no assumption of arbitrary USB host/OTG use through this connector |
| Exposed GPIO/I2C/SPI | Future ambient-light input, external sensor gateway or buzzer | Check occupied pins, level/power budget and protection before hardware design |

ESP32-S3 feature reference: [Espressif datasheet](https://www.espressif.com/sites/default/files/documentation/esp32-s3_datasheet_en.pdf). Board-level parts beyond serial-observed LCD/touch/memory are derived from the existing Waveshare demo and require exact revision verification. Native Bluetooth Classic is not a fallback for this S3 design. An onboard CAN transceiver, GNSS, ambient light sensor, buzzer and vehicle-grade power protection are not assumed present.

## Operating modes

| Mode | Radio/data priority | UI contract |
| --- | --- | --- |
| Driving | ECM/TCM sampling and local alerts first; phone telemetry throttled; Wi-Fi off by default | Freshness per source, no silent sampling changes |
| Configuration | Authenticated BLE control; bounded transfer of drafts/profiles; live polling kept within budget | Apply revision and any temporary sampling pause visible |
| Maintenance/update | Owner enters maintenance; stop diagnostic queries, mark readings unavailable; use best available bulk transport | Explicit progress; no simulated “live” data during update |
| Bench/development | USB logs and replay; optional Wi-Fi tools only when enabled | Visible bench/demo state, bounded/redacted logs |

[Espressif coexistence documentation](https://docs.espressif.com/projects/esp-idf/en/v5.4.1/esp32s3/api-guides/coexist.html) explains shared RF scheduling. The pinned IDF coexistence table marks connected SoftAP plus BLE as supported with unstable performance, so temporary-AP maintenance may need to suspend adapter links and use a controlled phone session. Station mode is the preferred Wi-Fi path to evaluate first. Two adapters + phone + Wi-Fi is a separate worst-case workload, not implied by testing each technology alone. Measure radio airtime effects, response latency, heap, frame deadlines and current consumption.

## Transport-independent operations

The application protocol owns capabilities, request IDs, operation tokens, config hashes and OTA state. BLE and Wi-Fi are transports of the same authenticated operations. Keep BLE as the universal bootstrap/recovery control path. Negotiate `bulkTransports` and transport/session capabilities. Prefer Wi-Fi for a large firmware image only after preflight confirms a usable local path; keep BLE as fallback. Never require an internet connection for an already downloaded update.

1. Associate and establish ownership through authenticated BLE.
2. Owner selects home/local Wi-Fi station mode or an explicit temporary device access point. Verify the provisioning transport can share the chosen NimBLE lifecycle; do not use example cleanup handlers that release Bluetooth memory required by the gauge. Use a device-specific access credential and time-limited enrollment; no open permanent AP.
3. Provision over the authenticated session using established Espressif provisioning security primitives, reviewed for the actual implementation. Do not log network credentials. Store only where needed with a production storage-protection policy.
4. Exchange local endpoint and its device certificate/public-key fingerprint over the trusted BLE channel. Android validates that identity on the TLS connection; never accept arbitrary self-signed certificates or disable hostname/trust checks globally.
5. Bind each bulk transfer token to owner, artifact/config hash and current operation. Switch transports only at an acknowledged offset after closing the previous writer. One writer lease prevents BLE/Wi-Fi races.
6. On Wi-Fi loss, query accepted offset through BLE and resume if the same device boot/session remains alive. If device rebooted, follow the v1 restart-from-zero OTA policy. Do not implement separate conflicting OTA state machines.

Android local Wi-Fi/SoftAP routing must respect OS consent and lifecycle. A local-only network may lack internet; bind only the transfer socket/client to that network and retain any available internet route for unrelated downloads. Resolve background/foreground behavior and permission changes on target Android versions during the spike.

## Integrations and limits

Optional future LAN read-only telemetry endpoint or MQTT publisher can consume the typed telemetry cache; publish source/ECU/age/quality and use explicit opt-in credentials. Do not expose raw vehicle writes to the LAN. ESP-NOW multi-gauge sharing is an exploratory option, subject to shared-channel/coexistence constraints and authenticated source/freshness semantics; it is not committed product support.

Power saving: use bounded reconnect scans, optional screen dimming and explicit sleep policy after sustained loss of vehicle activity. BLE disconnect alone does not prove ignition off. Validate wake behavior with both adapter types and any fitted battery. Night dimming can be manual/time-based before adding ambient sensing; do not invent ambient readings from nonexistent sensors.

## Tracked work

- **HW-001:** exact board revision, flash, QSPI PSRAM allocation, sensor identity, battery ADC calibration and available pin map.
- **RADIO-001:** ECM + TCM + phone coexistence with Wi-Fi off/on and in maintenance, including power measurements.
- **WIFI-001:** station/temporary-AP secure provisioning, Android network routing, measured transfer throughput and recovery; choose preferred production transport from evidence.
- **POWER-001:** sleep/dimming/wake policy, ignition inference limits, permanent automotive power design.

These are implementation gates. No Wi-Fi service, provisioning credentials, extra sensors or radio mode changes are installed by this design milestone.
