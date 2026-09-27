# Hardware capabilities and integration strategy

Owner direction: exploit Wi-Fi and other ESP32/board capabilities where they improve reliability and user experience. Capability availability, implemented support and measured performance are distinct.

## Hardware inventory and intended use

| Capability | Proposed use | Constraints/evidence |
| --- | --- | --- |
| ESP32-S3 Wi-Fi, 2.4 GHz 802.11 b/g/n | Faster local OTA and profile/diagnostic transfer; optional local integrations | Shares radio resources with BLE; no 5 GHz assumption |
| BLE central + peripheral | One/two OBD adapters; phone association/config/control/telemetry | Three-link feasibility is unresolved; limits advertised |
| Dual-core CPU / RTOS | Isolate rendering from transaction/parser work; measured task priorities | Do not fix core affinity without workload measurement |
| 16 MB flash configuration | A/B app slots, bounded profiles, diagnostic records | Verify actual flash geometry before migration; firmware files currently use 16 MB |
| 2 MB embedded QSPI PSRAM | Frame buffers, bounded trends, larger profile cache | Enabled in the current build; keep radio, DMA and latency-critical allocations in internal RAM |
| 240 × 240 GC9A01 SPI LCD | Bright readable templates, attention overlays, update/recovery status | Board pins/bus bandwidth and partial DMA buffer constraints |
| CST816S touch | Page navigation, owner confirmation, alert acknowledgment and local diagnostics | Small circular area; broad targets and deliberate destructive-action confirmation |
| QMI8658 IMU | Installation orientation assistant, wake/tap experiments and parked-movement event markers | Shares I2C with touch; verify identity, interrupt routing and enclosure calibration before use |
| LiPo connector, charger and GPIO1 battery ADC | Battery-backed shutdown, parked mode, update preflight and device-power diagnostics | Measures the attached device battery, not vehicle voltage; calibrate the 200 kΩ/100 kΩ divider per unit/revision |
| GPIO2 LCD backlight control | Smooth brightness, manual night mode and alert emphasis | A 20 kHz PWM baseline now applies the validated configuration value of 80%; app control, safe-minimum characterization, flicker, thermal behavior and boot-state qualification remain open |
| GPIO4/GPIO5 switched contacts | Candidate low-current haptic or indicator output | Exact board population and load limits require inspection. GPIO5 is already the touch interrupt in the current BSP, so it is not available without a verified pin/revision change |
| USB-C through CH343P USB-to-UART | Flash/recovery, serial logs and deterministic bench development | The fitted connector is routed as USB-to-UART. ESP32-S3 native USB features are not exposed through this connector as a product interface |
| Six-pin SH1.0 expansion connector | Future ambient-light input, ignition sense, protected external sensor or future accessory | Verify the exact revision pinout, occupied pins, voltage, current and automotive protection before connecting hardware |

ESP32-S3 feature reference: [Espressif datasheet](https://documentation.espressif.com/esp32_s3_datasheet_en.pdf). Board details are from the [Waveshare board documentation](https://docs.waveshare.com/ESP32-S3-Touch-LCD-1.28), its linked Rev3 schematic and the checked-out BSP. The product must still record and probe the exact assembled revision because Waveshare lists two SKUs and more than one schematic. Native Bluetooth Classic is not a fallback for this S3 design. An onboard CAN transceiver, GNSS, ambient light sensor, buzzer and vehicle-grade power protection are not present in the documented design.

## Product opportunity review

The following order maximizes driver value and reliability while keeping unverified hardware claims out of the product.

| Priority | Capability | Product behavior | Implementation gate |
| --- | --- | --- | --- |
| P0 | PWM backlight and night mode | Controlled backlight, a low-bloom night palette and visible alert emphasis | The 20 kHz PWM path and baseline night palette are implemented; characterize the usable range, flicker, current and boot behavior before exposing app control |
| P0 | Production security hardware | Secure Boot v2, flash/NVS encryption, signed OTA, anti-rollback and device identity backed by ESP32-S3 security primitives | Design manufacturing keys, recovery and irreversible eFuse procedure before enabling production fuses |
| P0 | Device power awareness | Report device battery/source health, reject unsafe updates and perform graceful low-power shutdown | Calibrate GPIO1 ADC, distinguish USB/device battery/vehicle states and measure update current margins |
| P1 | IMU-assisted setup and events | Guide mounting orientation, verify installation movement and create bounded impact/movement event markers | Add shared-I2C ownership, low-rate sampling, interrupt handling, calibration and health reporting |
| P1 | Haptic feedback | Confirm touch actions and reinforce critical alerts when the display is outside the driver's direct view | Inspect output circuitry and current limits; prototype on GPIO4 first because GPIO5 conflicts with touch interrupt |
| P1 | Wi-Fi maintenance transport | Faster OTA, diagnostic export and PID catalog/profile transfer through an automatic private gauge network authorized by BLE | Measure Wi-Fi/BLE coexistence; use one random WPA2 SoftAP session with application-layer AEAD and a short expiry |
| P1 | Bounded event storage | Store alert transitions, resets, update results and redacted support records in unused flash | Allocate a versioned wear-leveled partition with retention and privacy limits |
| P1 | PSRAM-backed trends | Smooth graphs, short bounded histories and richer rendering without starving protocol tasks | Set memory budgets and keep BLE, DMA and critical queues in internal memory |
| P2 | Protected accessory interface | Optional ambient light, ignition sense or external haptic/buzzer module | Define a protected connector contract and validate voltage, ESD, transients and pin ownership |
| Future hardware | Direct vehicle CAN | A Pro model can avoid third-party BLE adapter variability and improve transport diagnostics | Requires an external automotive CAN transceiver, protected power front end, harness and vehicle validation |

The IMU is not a trustworthy source for vehicle speed, crash detection or performance timing without a separately validated sensing design. GPIO1 must never be labeled as vehicle voltage. Bare ESP32 pins must never connect directly to OBD CAN or vehicle 12 V. The current USB-C connector should remain a development and recovery serial path.

## Recommended delivery slices

1. Complete the read-only hardware capability probe. The authenticated capacity snapshot now reports processor, reset, uptime, Wi-Fi state, flash, internal RAM, PSRAM, and initialized display/touch/backlight/BLE state. QMI8658 identity and health, calibrated battery ADC samples, and the occupied-pin report remain open. See the [hardware probe protocol](../protocol/hardware-probe.md).
2. Qualify the implemented PWM baseline and round-display-safe night palette, then add app-controlled presets and a local color/backlight self-test page.
3. Add bounded event storage and export for alert transitions, resets, update outcomes and hardware faults.
4. Prototype the IMU installation assistant and GPIO4 haptic feedback, with a compile-time board-revision gate until electrical limits are verified.
5. Qualify the automatic private Wi-Fi bulk transport, then measure OBD BLE latency while transferring firmware and logs.
6. Define production key provisioning, encrypted storage, secure boot and rollback policy before manufacturing builds.
7. Treat direct CAN and automotive power/ignition sensing as a future board variant with its own schematic, protection and validation plan.

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
2. Owner starts a bulk operation. The gauge creates a one-client WPA2 SoftAP with random credentials and a separate AES-256-GCM session key. It returns them only over the protected BLE owner characteristic.
3. Android requests the temporary network with `WifiNetworkSpecifier` and binds only the bulk socket to it. No user-entered network credentials, permanent device access point or phone-wide route change is required.
4. Bind every encrypted frame to the random session ID, a strictly increasing sequence, direction and authenticated header. The session expires after ten minutes and all secrets remain in RAM.
5. Send the existing configuration or OTA commands through the encrypted socket. The existing transfer gate, hashes, signatures, offsets and activation state remain authoritative and prevent BLE/Wi-Fi writer races.
6. On Wi-Fi loss, query accepted offset through BLE and open a fresh Wi-Fi session before continuing the same safe transfer policy. If the device rebooted, follow the v1 restart-from-zero OTA policy.

Android local Wi-Fi routing must respect OS consent and lifecycle. The network intentionally lacks internet; bind only the transfer socket to it and retain any available internet route for unrelated downloads. The app may trigger Android's standard network approval sheet, but it does not ask the user for an SSID or password.

## Integrations and limits

Optional future LAN read-only telemetry endpoint or MQTT publisher can consume the typed telemetry cache; publish source/ECU/age/quality and use explicit opt-in credentials. Do not expose raw vehicle writes to the LAN. ESP-NOW multi-gauge sharing is an exploratory option, subject to shared-channel/coexistence constraints and authenticated source/freshness semantics; it is not committed product support.

Power saving: use bounded reconnect scans, optional screen dimming and explicit sleep policy after sustained loss of vehicle activity. BLE disconnect alone does not prove ignition off. Validate wake behavior with both adapter types and any fitted battery. Night dimming can be manual/time-based before adding ambient sensing; do not invent ambient readings from nonexistent sensors.

## Tracked work

- **HW-001:** authenticated runtime capacity polling is implemented and [observed on the Pixel and gauge](../development/hardware-capacity-validation.md). Exact board revision, sensor identity, battery ADC calibration and available pin map remain open.
- **RADIO-001:** ECM + TCM + phone coexistence with Wi-Fi off/on and in maintenance, including power measurements.
- **WIFI-001:** automatic temporary-AP authorization, Android network routing, measured transfer throughput and recovery; promote the capability only from device evidence.
- **POWER-001:** sleep/dimming/wake policy, ignition inference limits, permanent automotive power design.

These are implementation gates. The Wi-Fi source path is implemented behind a disabled capability gate; qualification is still required. No extra sensors or permanent radio mode changes are installed by this design milestone.
