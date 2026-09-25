# OBD eGauge: reference projects and initial architecture

Research checked 2026-09-25. Repository status and project descriptions can change; recheck before adopting code or dependencies.

## Best references

| Project | Match | What to study | Limits and reuse notes |
| --- | --- | --- | --- |
| [janoskut/esp32-obd2-meter](https://gitlab.com/janoskut/esp32-obd2-meter) | Closest match: exact Waveshare ESP32-S3-Touch-LCD-1.28 board, BLE ELM327, and Vgate iCar Pro BLE 4.0 are named in its README. | Its Waveshare board support package, NimBLE service discovery and notifications, ELM text parsing, standard PID conversion, LVGL setup, touch interaction, NVS persistence, and BLE peripheral simulator. It uses ESP-IDF 5.4.1 and MIT-licensed code. | Current UI is a single large value. Tap cycles RPM, speed, load, coolant, and fuel; long-press rotates the display. Polling and UI are good starter patterns, but the multi-layout dashboard and Android configuration link still need design and implementation. Last repository activity reported by GitLab API: 2026-02-07. |
| [steveEcode/obd_brz_gauge](https://github.com/steveEcode/obd_brz_gauge) | Same Waveshare ESP32-S3 round touch family, BLE ELM327, LVGL. Its target is the 1.85-inch model, not this 1.28-inch board. | More mature vehicle profiles, reconnection behavior, data cache, warnings, multiple dashboard pages, settings persistence, and an ESP-NOW multi-gauge pattern. It reports ESP-IDF 5.5.3 and LVGL 8. | Vehicle profiles and display board support are specific to its target vehicles and 1.85-inch board. Use as an architecture reference. Verify the repository license before copying code. |
| [fbiego/car-hud](https://github.com/fbiego/car-hud) | BLE ELM327, ESP32 display, LVGL. | A compact example of dividing BLE OBD communication and display/UI code. | Uses a different smart-ring display and has a small feature set. It is a useful focused example, not a board port. MIT license. |
| [themelisx/ESP32CarODB](https://github.com/themelisx/ESP32CarODB) | OBD gauge concepts with a 240×240 GC9A01 round display. | Multiple gauge views, dual-display handling, warning colors, settings saved to EEPROM. | Does not target this board, and its Bluetooth/adapter implementation needs independent BLE compatibility review. GPL-3.0 license. |
| [antoxa2584x/esp32-c3-obd-gauge](https://github.com/antoxa2584x/esp32-c3-obd-gauge) | Round 1.28-inch GC9A01 + CST816 touch and ELM327 gauge UI. | Gauge pages, themes, touch navigation, settings persistence, data/OBD/UI separation. | Uses an ESP32-C3 and Wi-Fi ELM327 adapter, not the S3 and BLE adapter in this build. Treat only as a UI and module-boundary reference. |

The exact-board project is the best place to start for BLE and board behavior. The BRZ project is the stronger reference for a larger dashboard feature set. Neither should be copied wholesale without reconciling board support, vehicle assumptions, adapter behavior, and license obligations.

## Proposed system shape

```text
Vehicle ECU -> OBD-II BLE adapter -> ESP32-S3 gauge -> GC9A01 round display
                                      ^
                                      | BLE configuration and optional live status
                                  Android app
```

The ESP32 connects outward as a BLE central to the OBD adapter. For the companion app it acts as a BLE peripheral. ESP-IDF NimBLE supports multi-role and multiple simultaneous connections on ESP32-S3, but we should validate one adapter connection plus one phone connection on this board rather than treating the documented maximum as a performance guarantee. The OBD adapter may allow only one client, so the phone should configure the gauge rather than connect to the adapter directly.

Keep these parts separate so display work, BLE work, and the future app can evolve independently:

1. **Board support:** display/backlight, touch, storage, and power state for this exact Waveshare revision.
2. **OBD link:** adapter scan and selection, GATT discovery, ELM command/response transport, connection recovery, and adapter-specific quirks.
3. **Vehicle data:** PID catalog, support discovery, conversion, freshness, units, and optional vehicle-specific extensions.
4. **Dashboard:** layouts and renderers driven by the same canonical value cache. Initial renderer types: large numeric, arc gauge, bar, trend, and multi-value grid.
5. **Configuration:** validated settings and dashboard definitions stored on-device, with defaults that work without a phone.
6. **Android companion:** versioned BLE configuration service for reading/writing settings and optionally reading live status. Treat configuration updates as validated transactions and reject unknown schema versions.

## Development sequence

1. The test firmware has been replaced with the exact-board ESP-IDF baseline. Its retention is not required.
2. Start from the exact-board ESP-IDF/NimBLE reference, or port its board and BLE pieces into the current Arduino setup. Decide the firmware framework before building the UI and app protocol; the reference is ESP-IDF, while the current workspace is Arduino CLI.
3. Add an adapter simulator and fake OBD values so dashboard layouts can be developed without a vehicle or live adapter.
4. Connect to the actual BLE adapter, confirm its advertised name, service and characteristic UUIDs, notify/write behavior, and ELM initialization sequence. Do not assume every BLE ELM327 clone uses the same GATT profile.
5. Read a small standard PID set, timestamp each value, show stale/unsupported/disconnected states, and recover after adapter or ignition disconnects.
6. Add selectable dashboard pages and renderers over simulated and real values.
7. Define and test the ESP32-to-Android configuration GATT contract, then build the companion app against that contract.

## Workspace state

Superseded by [current state](current-state.md). The exact-board ESP-IDF reference built, flashed, and was confirmed working on 2026-09-25. The active design is in [system architecture](architecture/system.md), including Android configuration, PID discovery, diagnostics, alerts and OTA. The earlier Arduino display test is no longer the development baseline.
